package eu.kanade.tachiyomi.source

import android.content.Context
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
import kotlinx.serialization.Serializable
import nl.adaptivity.xmlutil.AndroidXmlReader
import nl.adaptivity.xmlutil.serialization.XML
import nl.adaptivity.xmlutil.serialization.XmlSerialName
import nl.adaptivity.xmlutil.serialization.XmlValue
import rx.Observable
import timber.log.Timber
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
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

        private val xml by lazy {
            XML {
                autoPolymorphic = true
            }
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
            .firstOrNull { it.extension == "json" }
            ?.apply {
                val reader = this.inputStream().bufferedReader()
                val json = JsonParser.parseReader(reader).asJsonObject

                manga.title = json["title"]?.asString ?: manga.title
                manga.author = json["author"]?.asString ?: manga.author
                manga.artist = json["artist"]?.asString ?: manga.artist
                manga.description = json["description"]?.asString ?: manga.description
                manga.genre = json["genre"]?.asJsonArray?.joinToString(", ") { it.asString }
                    ?: manga.genre
                manga.status = json["status"]?.asInt ?: manga.status
            }

        return Observable.just(manga)
    }

    @Serializable
    @XmlSerialName("ComicInfo", "", "")
    data class ComicInfo(
        val title: ComicInfoTitle,
        val series: ComicInfoSeries,
        val summary: ComicInfoSummary,
        val writer: ComicInfoWriter,
        val penciller: ComicInfoPenciller,
        val inker: ComicInfoInker,
        val letterer: ComicInfoLetterer,
        val coverArtist: ComicInfoCoverArtist,
        val editor: ComicInfoEditor,
        val publisher: ComicInfoPublisher,
        val genre: ComicInfoGenre,
        val web: ComicInfoWeb,
        val languageISO: ComicInfoLanguageISO,
        val translator: ComicInfoTranslator,
        val ageRating: ComicInfoAgeRating,
        val manga: ComicInfoManga,
        val characters: ComicInfoCharacters,
        val pageCount: ComicInfoPageCount,
        val pages: ComicInfoPages
    )

    @Serializable
    @XmlSerialName("Title", "", "")
    data class ComicInfoTitle(@XmlValue(true) val value: String = "")

    @Serializable
    @XmlSerialName("Series", "", "")
    data class ComicInfoSeries(@XmlValue(true) val value: String = "")

    @Serializable
    @XmlSerialName("Summary", "", "")
    data class ComicInfoSummary(@XmlValue(true) val value: String = "")

    @Serializable
    @XmlSerialName("Writer", "", "")
    data class ComicInfoWriter(@XmlValue(true) val value: String = "")

    @Serializable
    @XmlSerialName("Penciller", "", "")
    data class ComicInfoPenciller(@XmlValue(true) val value: String = "")

    @Serializable
    @XmlSerialName("Inker", "", "")
    data class ComicInfoInker(@XmlValue(true) val value: String = "")

    @Serializable
    @XmlSerialName("Letterer", "", "")
    data class ComicInfoLetterer(@XmlValue(true) val value: String = "")

    @Serializable
    @XmlSerialName("CoverArtist", "", "")
    data class ComicInfoCoverArtist(@XmlValue(true) val value: String = "")

    @Serializable
    @XmlSerialName("Editor", "", "")
    data class ComicInfoEditor(@XmlValue(true) val value: String = "")

    @Serializable
    @XmlSerialName("Publisher", "", "")
    data class ComicInfoPublisher(@XmlValue(true) val value: String = "")

    @Serializable
    @XmlSerialName("Genre", "", "")
    data class ComicInfoGenre(@XmlValue(true) val value: String = "")

    @Serializable
    @XmlSerialName("Web", "", "")
    data class ComicInfoWeb(@XmlValue(true) val value: String = "")

    @Serializable
    @XmlSerialName("LanguageISO", "", "")
    data class ComicInfoLanguageISO(@XmlValue(true) val value: String = "")

    @Serializable
    @XmlSerialName("Translator", "", "")
    data class ComicInfoTranslator(@XmlValue(true) val value: String = "")

    @Serializable
    @XmlSerialName("AgeRating", "", "")
    data class ComicInfoAgeRating(@XmlValue(true) val value: String = "")

    @Serializable
    @XmlSerialName("Manga", "", "")
    data class ComicInfoManga(@XmlValue(true) val value: String = "")

    @Serializable
    @XmlSerialName("Characters", "", "")
    data class ComicInfoCharacters(@XmlValue(true) val value: String = "")

    @Serializable
    @XmlSerialName("PageCount", "", "")
    data class ComicInfoPageCount(@XmlValue(true) val value: String = "")

    @Serializable
    @XmlSerialName("Pages", "", "")
    data class ComicInfoPages(val pages: List<ComicInfoPage>)

    @Serializable
    @XmlSerialName("Page", "", "")
    data class ComicInfoPage(
        @XmlSerialName("Image", "", "")
        val image: String,
        @XmlSerialName("ImageSize", "", "")
        val imageSize: String,
        @XmlSerialName("ImageWidth", "", "")
        val imageWidth: String,
        @XmlSerialName("ImageHeight", "", "")
        val imageHeight: String,
        @XmlSerialName("Key", "", "")
        val key: String
    )

    override fun fetchChapterList(manga: SManga): Observable<List<SChapter>> {
        val chapters = getBaseDirectories(context)
            .asSequence()
            .mapNotNull { File(it, manga.url).listFiles()?.toList() }
            .flatten()
            .filter { it.isDirectory || isSupportedFile(it.extension) }
            .map { chapterFile ->
                // TODO its all on you now Loo
                // TODO btw this will throw on non-folder chapter or the file doesn't exist
                val comicInfo = xml.decodeFromReader<ComicInfo>(AndroidXmlReader(File(chapterFile, "ComicInfo.xml").reader()))
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
