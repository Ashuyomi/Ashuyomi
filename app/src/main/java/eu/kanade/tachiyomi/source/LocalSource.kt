package eu.kanade.tachiyomi.source

import android.content.Context
import android.util.Xml
import com.github.junrar.Archive
import com.google.gson.JsonParser
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.util.chapter.ChapterRecognition
import eu.kanade.tachiyomi.util.lang.compareToCaseInsensitiveNaturalOrder
import eu.kanade.tachiyomi.util.storage.DiskUtil
import eu.kanade.tachiyomi.util.storage.EpubFile
import eu.kanade.tachiyomi.util.system.ImageUtil
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserException
import rx.Observable
import timber.log.Timber
import java.io.*
import java.util.Locale
import java.util.concurrent.TimeUnit
import java.util.zip.ZipFile

class LocalSource(private val context: Context) : CatalogueSource {
    companion object {
        const val ID = 0L
        const val HELP_URL = "https://tachiyomi.org/help/guides/local-manga/"

        private const val COVER_NAME = "cover.jpg"
        private val SUPPORTED_ARCHIVE_TYPES = setOf("zip", "rar", "cbr", "cbz", "epub")

        private val LATEST_THRESHOLD = TimeUnit.MILLISECONDS.convert(7, TimeUnit.DAYS)

        fun updateCover(context: Context, manga: SManga, input: InputStream): File? {
            val dir = getBaseDirectories(context).firstOrNull()
            if (dir == null) {
                input.close()
                return null
            }
            val cover = File("${dir.absolutePath}/${manga.url}", COVER_NAME)

            // It might not exist if using the external SD card
            cover.parentFile?.mkdirs()
            input.use {
                cover.outputStream().use {
                    input.copyTo(it)
                }
            }
            return cover
        }

        private fun getBaseDirectories(context: Context): List<File> {
            val c = context.getString(R.string.app_name) + File.separator + "local"
            return DiskUtil.getExternalStorages(context).map { File(it.absolutePath, c) }
        }
    }

    override val id = ID
    override val name = context.getString(R.string.local_source)
    override val lang = ""
    override val supportsLatest = true

    override fun toString() = context.getString(R.string.local_source)

    override fun fetchPopularManga(page: Int) = fetchSearchManga(page, "", POPULAR_FILTERS)

    override fun fetchSearchManga(page: Int, query: String, filters: FilterList): Observable<MangasPage> {
        val baseDirs = getBaseDirectories(context)

        val time = if (filters === LATEST_FILTERS) System.currentTimeMillis() - LATEST_THRESHOLD else 0L
        var mangaDirs = baseDirs
            .asSequence()
            .mapNotNull { it.listFiles()?.toList() }
            .flatten()
            .filter { it.isDirectory }
            .filterNot { it.name.startsWith('.') }
            .filter { if (time == 0L) it.name.contains(query, ignoreCase = true) else it.lastModified() >= time }
            .distinctBy { it.name }

        val state = ((if (filters.isEmpty()) POPULAR_FILTERS else filters)[0] as OrderBy).state
        when (state?.index) {
            0 -> {
                mangaDirs = if (state.ascending) {
                    mangaDirs.sortedBy { it.name.lowercase(Locale.ENGLISH) }
                } else {
                    mangaDirs.sortedByDescending { it.name.lowercase(Locale.ENGLISH) }
                }
            }
            1 -> {
                mangaDirs = if (state.ascending) {
                    mangaDirs.sortedBy(File::lastModified)
                } else {
                    mangaDirs.sortedByDescending(File::lastModified)
                }
            }
        }

        val mangas = mangaDirs.map { mangaDir ->
            SManga.create().apply {
                title = mangaDir.name
                url = mangaDir.name

                // Try to find the cover
                for (dir in baseDirs) {
                    val cover = File("${dir.absolutePath}/$url", COVER_NAME)
                    if (cover.exists()) {
                        thumbnail_url = cover.absolutePath
                        break
                    }
                }

                val chapters = fetchChapterList(this).toBlocking().first()
                if (chapters.isNotEmpty()) {
                    val chapter = chapters.last()
                    val format = getFormat(chapter)
                    if (format is Format.Epub) {
                        EpubFile(format.file).use { epub ->
                            epub.fillMangaMetadata(this)
                        }
                    }

                    // Copy the cover from the first chapter found.
                    if (thumbnail_url == null) {
                        try {
                            val dest = updateCover(chapter, this)
                            thumbnail_url = dest?.absolutePath
                        } catch (e: Exception) {
                            Timber.e(e)
                        }
                    }
                }
            }
        }

        return Observable.just(MangasPage(mangas.toList(), false))
    }

    override fun fetchLatestUpdates(page: Int) = fetchSearchManga(page, "", LATEST_FILTERS)

    override fun fetchMangaDetails(manga: SManga): Observable<SManga> {
        getBaseDirectories(context)
            .asSequence()
            .mapNotNull { File(it, manga.url).listFiles()?.toList() }
            .flatten()
            .firstOrNull { it.extension == "xml" }
            ?.apply {
                val reader = this.inputStream().bufferedReader()
                parser(reader, manga)
            }

        return Observable.just(manga)
    }


    data class Entry(val title: String?, val writer: String?, val penciller: String?, val summary: String?, val genre: String?)

    private val ns: String? = null

    @Throws(XmlPullParserException::class, IOException::class)
    private fun parser(bufferedReader: BufferedReader, manga: SManga): List<*> {
        bufferedReader.use { inputStream ->
            val parser: XmlPullParser = Xml.newPullParser()
            parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
            parser.setInput(inputStream, null)
            parser.nextTag()
            return readFeed(parser, manga)
        }
    }

    @Throws(XmlPullParserException::class, IOException::class)
    private fun readFeed(parser: XmlPullParser, manga: SManga): List<Entry> {
        val entries = mutableListOf<Entry>()

        parser.require(XmlPullParser.START_TAG, ns, "feed")
        while (parser.next() != XmlPullParser.END_TAG) {
            if (parser.eventType != XmlPullParser.START_TAG) {
                continue
            }
            // Starts by looking for the entry tag
            if (parser.name == "ComicInfo") {
                entries.add(readEntry(parser, manga))
            } else {
                skip(parser)
            }
        }
        return entries
    }

    // Parses the contents of an entry. If it encounters a title, writer, penciller, summary, status,
    //or genre tag, hands them off to their respective "read" methods for processing.
    // Otherwise, skips the tag.
    @Throws(XmlPullParserException::class, IOException::class)
    private fun readEntry(parser: XmlPullParser, manga: SManga): Observable<SManga> {
        parser.require(XmlPullParser.START_TAG, ns, "ComicInfo")
        while (parser.next() != XmlPullParser.END_TAG) {
            if (parser.eventType != XmlPullParser.START_TAG) {
                continue
            }
            when (parser.name) {
                "title" -> manga.title = readTag(parser, "Title")
                "writer" -> manga.author = readTag(parser, "Writer")
                "penciller" -> manga.artist = readTag(parser, "Penciller")
                "summary" -> manga.description = readTag(parser, "Summary")
                "genre" -> manga.genre = readTag(parser, "Genre")
                else -> skip(parser)
            }
        }
        return Observable.just(manga)
    }

    // Processes tags in the feed.
    @Throws(IOException::class, XmlPullParserException::class)
    private fun readTag(parser: XmlPullParser, tag: String): String {
        parser.require(XmlPullParser.START_TAG, ns, tag)
        val tag = readText(parser)
        parser.require(XmlPullParser.END_TAG, ns, tag)
        return tag
    }

    // For the all tags, extracts their text values.
    @Throws(IOException::class, XmlPullParserException::class)
    private fun readText(parser: XmlPullParser): String {
        var result = ""
        if (parser.next() == XmlPullParser.TEXT) {
            result = parser.text
            parser.nextTag()
        }
        return result
    }

    @Throws(XmlPullParserException::class, IOException::class)
    private fun skip(parser: XmlPullParser) {
        if (parser.eventType != XmlPullParser.START_TAG) {
            throw IllegalStateException()
        }
        var depth = 1
        while (depth != 0) {
            when (parser.next()) {
                XmlPullParser.END_TAG -> depth--
                XmlPullParser.START_TAG -> depth++
            }
        }
    }

    override fun fetchChapterList(manga: SManga): Observable<List<SChapter>> {
        val chapters = getBaseDirectories(context)
            .asSequence()
            .mapNotNull { File(it, manga.url).listFiles()?.toList() }
            .flatten()
            .filter { it.isDirectory || isSupportedFile(it.extension) }
            .map { chapterFile ->
                SChapter.create().apply {
                    url = "${manga.url}/${chapterFile.name}"
                    name = if (chapterFile.isDirectory) {
                        chapterFile.name
                    } else {
                        chapterFile.nameWithoutExtension
                    }
                    date_upload = chapterFile.lastModified()

                    val format = getFormat(this)
                    if (format is Format.Epub) {
                        EpubFile(format.file).use { epub ->
                            epub.fillChapterMetadata(this)
                        }
                    }

                    val chapNameCut = stripMangaTitle(name, manga.title)
                    if (chapNameCut.isNotEmpty()) name = chapNameCut
                    ChapterRecognition.parseChapterNumber(this, manga)
                }
            }
            .sortedWith { c1, c2 ->
                val c = c2.chapter_number.compareTo(c1.chapter_number)
                if (c == 0) c2.name.compareToCaseInsensitiveNaturalOrder(c1.name) else c
            }
            .toList()

        return Observable.just(chapters)
    }

    /**
     * Strips the manga title from a chapter name, matching only based on alphanumeric and whitespace
     * characters.
     */
    private fun stripMangaTitle(chapterName: String, mangaTitle: String): String {
        var chapterNameIndex = 0
        var mangaTitleIndex = 0
        while (chapterNameIndex < chapterName.length && mangaTitleIndex < mangaTitle.length) {
            val chapterChar = chapterName[chapterNameIndex]
            val mangaChar = mangaTitle[mangaTitleIndex]
            if (!chapterChar.equals(mangaChar, true)) {
                val invalidChapterChar = !chapterChar.isLetterOrDigit() && !chapterChar.isWhitespace()
                val invalidMangaChar = !mangaChar.isLetterOrDigit() && !mangaChar.isWhitespace()

                if (!invalidChapterChar && !invalidMangaChar) {
                    return chapterName
                }

                if (invalidChapterChar) {
                    chapterNameIndex++
                }

                if (invalidMangaChar) {
                    mangaTitleIndex++
                }
            } else {
                chapterNameIndex++
                mangaTitleIndex++
            }
        }

        return chapterName.substring(chapterNameIndex).trimStart(' ', '-', '_', ',', ':')
    }

    override fun fetchPageList(chapter: SChapter): Observable<List<Page>> {
        return Observable.error(Exception("Unused"))
    }

    private fun isSupportedFile(extension: String): Boolean {
        return extension.lowercase() in SUPPORTED_ARCHIVE_TYPES
    }

    fun getFormat(chapter: SChapter): Format {
        val baseDirs = getBaseDirectories(context)

        for (dir in baseDirs) {
            val chapFile = File(dir, chapter.url)
            if (!chapFile.exists()) continue

            return getFormat(chapFile)
        }
        throw Exception(context.getString(R.string.chapter_not_found))
    }

    private fun getFormat(file: File): Format {
        val extension = file.extension
        return if (file.isDirectory) {
            Format.Directory(file)
        } else if (extension.equals("zip", true) || extension.equals("cbz", true)) {
            Format.Zip(file)
        } else if (extension.equals("rar", true) || extension.equals("cbr", true)) {
            Format.Rar(file)
        } else if (extension.equals("epub", true)) {
            Format.Epub(file)
        } else {
            throw Exception(context.getString(R.string.local_invalid_format))
        }
    }

    private fun updateCover(chapter: SChapter, manga: SManga): File? {
        return when (val format = getFormat(chapter)) {
            is Format.Directory -> {
                val entry = format.file.listFiles()
                    ?.sortedWith { f1, f2 -> f1.name.compareToCaseInsensitiveNaturalOrder(f2.name) }
                    ?.find { !it.isDirectory && ImageUtil.isImage(it.name) { FileInputStream(it) } }

                entry?.let { updateCover(context, manga, it.inputStream()) }
            }
            is Format.Zip -> {
                ZipFile(format.file).use { zip ->
                    val entry = zip.entries().toList()
                        .sortedWith { f1, f2 -> f1.name.compareToCaseInsensitiveNaturalOrder(f2.name) }
                        .find { !it.isDirectory && ImageUtil.isImage(it.name) { zip.getInputStream(it) } }

                    entry?.let { updateCover(context, manga, zip.getInputStream(it)) }
                }
            }
            is Format.Rar -> {
                Archive(format.file).use { archive ->
                    val entry = archive.fileHeaders
                        .sortedWith { f1, f2 -> f1.fileName.compareToCaseInsensitiveNaturalOrder(f2.fileName) }
                        .find { !it.isDirectory && ImageUtil.isImage(it.fileName) { archive.getInputStream(it) } }

                    entry?.let { updateCover(context, manga, archive.getInputStream(it)) }
                }
            }
            is Format.Epub -> {
                EpubFile(format.file).use { epub ->
                    val entry = epub.getImagesFromPages()
                        .firstOrNull()
                        ?.let { epub.getEntry(it) }

                    entry?.let { updateCover(context, manga, epub.getInputStream(it)) }
                }
            }
        }
    }

    override fun getFilterList() = POPULAR_FILTERS

    private val POPULAR_FILTERS = FilterList(OrderBy(context))
    private val LATEST_FILTERS = FilterList(OrderBy(context).apply { state = Filter.Sort.Selection(1, false) })

    private class OrderBy(context: Context) : Filter.Sort(
        context.getString(R.string.local_filter_order_by),
        arrayOf(context.getString(R.string.title), context.getString(R.string.date)),
        Selection(0, true)
    )

    sealed class Format {
        data class Directory(val file: File) : Format()
        data class Zip(val file: File) : Format()
        data class Rar(val file: File) : Format()
        data class Epub(val file: File) : Format()
    }
}
