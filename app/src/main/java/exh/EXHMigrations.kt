@file:Suppress("DEPRECATION")

package exh

import android.content.Context
import androidx.core.content.edit
import androidx.preference.PreferenceManager
import eu.kanade.data.DatabaseHandler
import eu.kanade.data.category.categoryMapper
import eu.kanade.data.chapter.chapterMapper
import eu.kanade.domain.backup.service.BackupPreferences
import eu.kanade.domain.base.BasePreferences
import eu.kanade.domain.chapter.interactor.DeleteChapters
import eu.kanade.domain.chapter.interactor.UpdateChapter
import eu.kanade.domain.chapter.model.ChapterUpdate
import eu.kanade.domain.library.service.LibraryPreferences
import eu.kanade.domain.manga.interactor.GetManga
import eu.kanade.domain.manga.interactor.GetMangaBySource
import eu.kanade.domain.manga.interactor.InsertMergedReference
import eu.kanade.domain.manga.interactor.UpdateManga
import eu.kanade.domain.manga.model.MangaUpdate
import eu.kanade.domain.source.interactor.InsertFeedSavedSearch
import eu.kanade.domain.source.interactor.InsertSavedSearch
import eu.kanade.domain.source.service.SourcePreferences
import eu.kanade.domain.ui.UiPreferences
import eu.kanade.tachiyomi.BuildConfig
import eu.kanade.tachiyomi.core.preference.PreferenceStore
import eu.kanade.tachiyomi.core.security.SecurityPreferences
import eu.kanade.tachiyomi.data.backup.BackupCreatorJob
import eu.kanade.tachiyomi.data.database.models.Manga
import eu.kanade.tachiyomi.data.library.LibraryUpdateJob
import eu.kanade.tachiyomi.data.preference.MANGA_NON_COMPLETED
import eu.kanade.tachiyomi.data.preference.PreferenceValues
import eu.kanade.tachiyomi.data.track.TrackManager
import eu.kanade.tachiyomi.data.updater.AppUpdateJob
import eu.kanade.tachiyomi.extension.ExtensionUpdateJob
import eu.kanade.tachiyomi.network.NetworkPreferences
import eu.kanade.tachiyomi.network.PREF_DOH_CLOUDFLARE
import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.SourceManager
import eu.kanade.tachiyomi.source.online.all.NHentai
import eu.kanade.tachiyomi.ui.reader.setting.OrientationType
import eu.kanade.tachiyomi.ui.reader.setting.ReaderPreferences
import eu.kanade.tachiyomi.util.preference.minusAssign
import eu.kanade.tachiyomi.util.system.DeviceUtil
import eu.kanade.tachiyomi.util.system.logcat
import exh.eh.EHentaiUpdateWorker
import exh.log.xLogE
import exh.merged.sql.models.MergedMangaReference
import exh.savedsearches.models.FeedSavedSearch
import exh.savedsearches.models.SavedSearch
import exh.source.BlacklistedSources
import exh.source.EH_SOURCE_ID
import exh.source.HBROWSE_SOURCE_ID
import exh.source.MERGED_SOURCE_ID
import exh.source.TSUMINO_SOURCE_ID
import exh.util.nullIfBlank
import exh.util.under
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import uy.kohesive.injekt.injectLazy
import java.io.File
import java.net.URI
import java.net.URISyntaxException
import eu.kanade.domain.manga.model.Manga as DomainManga

object EXHMigrations {
    private val handler: DatabaseHandler by injectLazy()
    private val sourceManager: SourceManager by injectLazy()
    private val getManga: GetManga by injectLazy()
    private val getMangaBySource: GetMangaBySource by injectLazy()
    private val updateManga: UpdateManga by injectLazy()
    private val updateChapter: UpdateChapter by injectLazy()
    private val deleteChapters: DeleteChapters by injectLazy()
    private val insertMergedReference: InsertMergedReference by injectLazy()
    private val insertSavedSearch: InsertSavedSearch by injectLazy()
    private val insertFeedSavedSearch: InsertFeedSavedSearch by injectLazy()

    /**
     * Performs a migration when the application is updated.
     *
     * @return true if a migration is performed, false otherwise.
     */
    fun upgrade(
        context: Context,
        preferenceStore: PreferenceStore,
        basePreferences: BasePreferences,
        uiPreferences: UiPreferences,
        networkPreferences: NetworkPreferences,
        sourcePreferences: SourcePreferences,
        securityPreferences: SecurityPreferences,
        libraryPreferences: LibraryPreferences,
        readerPreferences: ReaderPreferences,
        backupPreferences: BackupPreferences,
    ): Boolean {
        val lastVersionCode = preferenceStore.getInt("eh_last_version_code", 0)
        val oldVersion = lastVersionCode.get()
        try {
            if (oldVersion < BuildConfig.VERSION_CODE) {
                lastVersionCode.set(BuildConfig.VERSION_CODE)

                if (BuildConfig.INCLUDE_UPDATER) {
                    AppUpdateJob.setupTask(context)
                }
                ExtensionUpdateJob.setupTask(context)
                LibraryUpdateJob.setupTask(context)
                BackupCreatorJob.setupTask(context)
                EHentaiUpdateWorker.scheduleBackground(context)

                // Fresh install
                if (oldVersion == 0) {
                    return false
                }

                val prefs = PreferenceManager.getDefaultSharedPreferences(context)
                if (oldVersion under 4) {
                    updateSourceId(HBROWSE_SOURCE_ID, 6912)
                    // Migrate BHrowse URLs
                    val hBrowseManga = runBlocking { getMangaBySource.await(HBROWSE_SOURCE_ID) }
                    val mangaUpdates = hBrowseManga.map {
                        MangaUpdate(it.id, url = it.url + "/c00001/")
                    }

                    runBlocking {
                        updateManga.awaitAll(mangaUpdates)
                    }
                }
                if (oldVersion under 6) {
                    updateSourceId(NHentai.otherId, 6907)
                }
                if (oldVersion under 7) {
                    val mergedMangas = runBlocking { getMangaBySource.await(MERGED_SOURCE_ID) }

                    if (mergedMangas.isNotEmpty()) {
                        val mangaConfigs = mergedMangas.mapNotNull { mergedManga -> readMangaConfig(mergedManga)?.let { mergedManga to it } }
                        if (mangaConfigs.isNotEmpty()) {
                            val mangaToUpdate = mutableListOf<MangaUpdate>()
                            val mergedMangaReferences = mutableListOf<MergedMangaReference>()
                            mangaConfigs.onEach { mergedManga ->
                                val newFirst = mergedManga.second.children.firstOrNull()?.url?.let {
                                    if (runBlocking { getManga.await(it, MERGED_SOURCE_ID) } != null) return@onEach
                                    mangaToUpdate += MangaUpdate(id = mergedManga.first.id, url = it)
                                    mergedManga.first.copy(url = it)
                                } ?: mergedManga.first
                                mergedMangaReferences += MergedMangaReference(
                                    id = null,
                                    isInfoManga = false,
                                    getChapterUpdates = false,
                                    chapterSortMode = 0,
                                    chapterPriority = 0,
                                    downloadChapters = false,
                                    mergeId = newFirst.id,
                                    mergeUrl = newFirst.url,
                                    mangaId = newFirst.id,
                                    mangaUrl = newFirst.url,
                                    mangaSourceId = MERGED_SOURCE_ID,
                                )
                                mergedManga.second.children.distinct().forEachIndexed { index, mangaSource ->
                                    val load = mangaSource.load() ?: return@forEachIndexed
                                    mergedMangaReferences += MergedMangaReference(
                                        id = null,
                                        isInfoManga = index == 0,
                                        getChapterUpdates = true,
                                        chapterSortMode = 0,
                                        chapterPriority = 0,
                                        downloadChapters = true,
                                        mergeId = newFirst.id,
                                        mergeUrl = newFirst.url,
                                        mangaId = load.manga.id,
                                        mangaUrl = load.manga.url,
                                        mangaSourceId = load.source.id,
                                    )
                                }
                            }
                            runBlocking {
                                updateManga.awaitAll(mangaToUpdate)
                                insertMergedReference.awaitAll(mergedMangaReferences)
                            }

                            val loadedMangaList = mangaConfigs.map { it.second.children }.flatten().mapNotNull { it.load() }.distinct()
                            val chapters = runBlocking { handler.awaitList { ehQueries.getChaptersByMangaIds(mergedMangas.map { it.id }, chapterMapper) } }
                            val mergedMangaChapters = runBlocking { handler.awaitList { ehQueries.getChaptersByMangaIds(loadedMangaList.map { it.manga.id }, chapterMapper) } }

                            val mergedMangaChaptersMatched = mergedMangaChapters.mapNotNull { chapter -> loadedMangaList.firstOrNull { it.manga.id == chapter.id }?.let { it to chapter } }
                            val parsedChapters = chapters.filter { it.read || it.lastPageRead != 0L }.mapNotNull { chapter -> readUrlConfig(chapter.url)?.let { chapter to it } }
                            val chaptersToUpdate = mutableListOf<ChapterUpdate>()
                            parsedChapters.forEach { parsedChapter ->
                                mergedMangaChaptersMatched.firstOrNull { it.second.url == parsedChapter.second.url && it.first.source.id == parsedChapter.second.source && it.first.manga.url == parsedChapter.second.mangaUrl }?.let {
                                    chaptersToUpdate += ChapterUpdate(
                                        it.second.id,
                                        read = parsedChapter.first.read,
                                        lastPageRead = parsedChapter.first.lastPageRead,
                                    )
                                }
                            }
                            runBlocking {
                                deleteChapters.await(mergedMangaChapters.map { it.id })
                                updateChapter.awaitAll(chaptersToUpdate)
                            }
                        }
                    }
                }
                if (oldVersion under 12) {
                    // Force MAL log out due to login flow change
                    val trackManager = Injekt.get<TrackManager>()
                    trackManager.myAnimeList.logout()
                }
                if (oldVersion under 14) {
                    // Migrate DNS over HTTPS setting
                    val wasDohEnabled = prefs.getBoolean("enable_doh", false)
                    if (wasDohEnabled) {
                        prefs.edit {
                            putInt(networkPreferences.dohProvider().key(), PREF_DOH_CLOUDFLARE)
                            remove("enable_doh")
                        }
                    }
                }
                if (oldVersion under 16) {
                    // Reset rotation to Free after replacing Lock
                    if (prefs.contains("pref_rotation_type_key")) {
                        prefs.edit {
                            putInt("pref_rotation_type_key", 1)
                        }
                    }
                    // Disable update check for Android 5.x users
                    // if (BuildConfig.INCLUDE_UPDATER && Build.VERSION.SDK_INT under Build.VERSION_CODES.M) {
                    //   UpdaterJob.cancelTask(context)
                    // }
                }
                if (oldVersion under 17) {
                    // Migrate Rotation and Viewer values to default values for viewer_flags
                    val newOrientation = when (prefs.getInt("pref_rotation_type_key", 1)) {
                        1 -> OrientationType.FREE.flagValue
                        2 -> OrientationType.PORTRAIT.flagValue
                        3 -> OrientationType.LANDSCAPE.flagValue
                        4 -> OrientationType.LOCKED_PORTRAIT.flagValue
                        5 -> OrientationType.LOCKED_LANDSCAPE.flagValue
                        else -> OrientationType.FREE.flagValue
                    }

                    // Reading mode flag and prefValue is the same value
                    val newReadingMode = prefs.getInt("pref_default_viewer_key", 1)

                    prefs.edit {
                        putInt("pref_default_orientation_type_key", newOrientation)
                        remove("pref_rotation_type_key")
                        putInt("pref_default_reading_mode_key", newReadingMode)
                        remove("pref_default_viewer_key")
                    }

                    // Delete old mangadex trackers
                    runBlocking {
                        handler.await { ehQueries.deleteBySyncId(6) }
                    }
                }
                if (oldVersion under 18) {
                    val readerTheme = readerPreferences.readerTheme().get()
                    if (readerTheme == 4) {
                        readerPreferences.readerTheme().set(3)
                    }
                    val updateInterval = libraryPreferences.libraryUpdateInterval().get()
                    if (updateInterval == 1 || updateInterval == 2) {
                        libraryPreferences.libraryUpdateInterval().set(3)
                        LibraryUpdateJob.setupTask(context, 3)
                    }
                }
                if (oldVersion under 20) {
                    try {
                        val oldSortingMode = prefs.getInt(libraryPreferences.librarySortingMode().key(), 0 /* ALPHABETICAL */)
                        val oldSortingDirection = prefs.getBoolean("library_sorting_ascending", true)

                        val newSortingMode = when (oldSortingMode) {
                            0 -> "ALPHABETICAL"
                            1 -> "LAST_READ"
                            2 -> "LAST_MANGA_UPDATE"
                            3 -> "UNREAD_COUNT"
                            4 -> "TOTAL_CHAPTERS"
                            6 -> "LATEST_CHAPTER"
                            7 -> "DRAG_AND_DROP"
                            8 -> "DATE_ADDED"
                            9 -> "TAG_LIST"
                            10 -> "CHAPTER_FETCH_DATE"
                            else -> "ALPHABETICAL"
                        }

                        val newSortingDirection = when (oldSortingDirection) {
                            true -> "ASCENDING"
                            else -> "DESCENDING"
                        }

                        prefs.edit(commit = true) {
                            remove(libraryPreferences.librarySortingMode().key())
                            remove("library_sorting_ascending")
                        }

                        prefs.edit {
                            putString(libraryPreferences.librarySortingMode().key(), newSortingMode)
                            putString("library_sorting_ascending", newSortingDirection)
                        }
                    } catch (e: Exception) {
                        logcat(throwable = e) { "Already done migration" }
                    }
                }
                if (oldVersion under 21) {
                    // Setup EH updater task after migrating to WorkManager
                    EHentaiUpdateWorker.scheduleBackground(context)

                    // if (preferences.lang().get() in listOf("en-US", "en-GB")) {
                    //    preferences.lang().set("en")
                    // }
                }
                if (oldVersion under 22) {
                    // Handle removed every 3, 4, 6, and 8 hour library updates
                    val updateInterval = libraryPreferences.libraryUpdateInterval().get()
                    if (updateInterval in listOf(3, 4, 6, 8)) {
                        libraryPreferences.libraryUpdateInterval().set(12)
                        LibraryUpdateJob.setupTask(context, 12)
                    }
                }
                if (oldVersion under 23) {
                    val oldUpdateOngoingOnly = prefs.getBoolean("pref_update_only_non_completed_key", true)
                    if (!oldUpdateOngoingOnly) {
                        libraryPreferences.libraryUpdateMangaRestriction() -= MANGA_NON_COMPLETED
                    }
                }
                if (oldVersion under 24) {
                    try {
                        sequenceOf(
                            "fav-sync",
                            "fav-sync.management",
                            "fav-sync.lock",
                            "fav-sync.note",
                        ).map {
                            File(context.filesDir, it)
                        }.filter(File::exists).forEach {
                            if (it.isDirectory) {
                                it.deleteRecursively()
                            } else {
                                it.delete()
                            }
                        }
                    } catch (e: Exception) {
                        xLogE("Failed to delete old favorites database", e)
                    }
                }
                if (oldVersion under 27) {
                    val oldSecureScreen = prefs.getBoolean("secure_screen", false)
                    if (oldSecureScreen) {
                        securityPreferences.secureScreen().set(SecurityPreferences.SecureScreenMode.ALWAYS)
                    }
                    if (DeviceUtil.isMiui && basePreferences.extensionInstaller().get() == PreferenceValues.ExtensionInstaller.PACKAGEINSTALLER) {
                        basePreferences.extensionInstaller().set(PreferenceValues.ExtensionInstaller.LEGACY)
                    }
                }
                if (oldVersion under 28) {
                    if (prefs.getString("pref_display_mode_library", null) == "NO_TITLE_GRID") {
                        prefs.edit(commit = true) {
                            putString("pref_display_mode_library", "COVER_ONLY_GRID")
                        }
                    }
                }
                if (oldVersion under 29) {
                    if (prefs.getString("pref_display_mode_catalogue", null) == "NO_TITLE_GRID") {
                        prefs.edit(commit = true) {
                            putString("pref_display_mode_catalogue", "COMPACT_GRID")
                        }
                    }
                }
                if (oldVersion under 30) {
                    BackupCreatorJob.setupTask(context)
                }
                if (oldVersion under 31) {
                    runBlocking {
                        val savedSearch = prefs.getStringSet("eh_saved_searches", emptySet())?.mapNotNull {
                            runCatching {
                                val content = Json.decodeFromString<JsonObject>(it.substringAfter(':'))
                                SavedSearch(
                                    id = -1,
                                    source = it.substringBefore(':').toLongOrNull()
                                        ?: return@runCatching null,
                                    name = content["name"]!!.jsonPrimitive.content,
                                    query = content["query"]!!.jsonPrimitive.contentOrNull?.nullIfBlank(),
                                    filtersJson = Json.encodeToString(content["filters"]!!.jsonArray),
                                )
                            }.getOrNull()
                        }
                        if (!savedSearch.isNullOrEmpty()) {
                            insertSavedSearch.awaitAll(savedSearch)
                        }
                        val feedSavedSearch = prefs.getStringSet("latest_tab_sources", emptySet())?.map {
                            FeedSavedSearch(
                                id = -1,
                                source = it.toLong(),
                                savedSearch = null,
                                global = true,
                            )
                        }
                        if (!feedSavedSearch.isNullOrEmpty()) {
                            insertFeedSavedSearch.awaitAll(feedSavedSearch)
                        }
                    }
                    prefs.edit(commit = true) {
                        remove("eh_saved_searches")
                        remove("latest_tab_sources")
                    }
                }
                if (oldVersion under 32) {
                    val oldReaderTap = prefs.getBoolean("reader_tap", false)
                    if (!oldReaderTap) {
                        readerPreferences.navigationModePager().set(5)
                        readerPreferences.navigationModeWebtoon().set(5)
                    }
                }
                if (oldVersion under 38) {
                    // Handle renamed enum values
                    @Suppress("DEPRECATION")
                    val newSortingMode = when (val oldSortingMode = prefs.getString(libraryPreferences.librarySortingMode().key(), "ALPHABETICAL")) {
                        "LAST_CHECKED" -> "LAST_MANGA_UPDATE"
                        "UNREAD" -> "UNREAD_COUNT"
                        "DATE_FETCHED" -> "CHAPTER_FETCH_DATE"
                        "DRAG_AND_DROP" -> "ALPHABETICAL"
                        else -> oldSortingMode
                    }
                    prefs.edit {
                        putString(libraryPreferences.librarySortingMode().key(), newSortingMode)
                    }
                    runBlocking {
                        handler.await(true) {
                            categoriesQueries.getCategories(categoryMapper).executeAsList()
                                .filter { (it.flags and 0b00111100L) == 0b00100000L }
                                .forEach {
                                    categoriesQueries.update(
                                        categoryId = it.id,
                                        flags = it.flags and 0b00111100L.inv(),
                                        name = null,
                                        order = null,
                                    )
                                }
                        }
                    }
                }
                if (oldVersion under 39) {
                    prefs.edit {
                        val sort = prefs.getString(libraryPreferences.librarySortingMode().key(), null) ?: return@edit
                        val direction = prefs.getString("library_sorting_ascending", "ASCENDING")!!
                        putString(libraryPreferences.librarySortingMode().key(), "$sort,$direction")
                        remove("library_sorting_ascending")
                    }
                }
                if (oldVersion under 40) {
                    if (backupPreferences.numberOfBackups().get() == 1) {
                        backupPreferences.numberOfBackups().set(2)
                    }
                    if (backupPreferences.backupInterval().get() == 0) {
                        backupPreferences.backupInterval().set(12)
                        BackupCreatorJob.setupTask(context)
                    }
                }
                if (oldVersion under 41) {
                    @Suppress("NAME_SHADOWING")
                    val preferences = listOf(
                        libraryPreferences.filterChapterByRead(),
                        libraryPreferences.filterChapterByDownloaded(),
                        libraryPreferences.filterChapterByBookmarked(),
                        libraryPreferences.sortChapterBySourceOrNumber(),
                        libraryPreferences.displayChapterByNameOrNumber(),
                        libraryPreferences.sortChapterByAscendingOrDescending(),
                    )

                    prefs.edit {
                        preferences.forEach { preference ->
                            val key = preference.key()
                            val value = prefs.getInt(key, Int.MIN_VALUE)
                            if (value == Int.MIN_VALUE) return@forEach
                            remove(key)
                            putLong(key, value.toLong())
                        }
                    }
                }
                if (oldVersion under 42) {
                    if (uiPreferences.themeMode().isSet()) {
                        prefs.edit {
                            val themeMode = prefs.getString(uiPreferences.themeMode().key(), null) ?: return@edit
                            putString(uiPreferences.themeMode().key(), themeMode.uppercase())
                        }
                    }
                }
                if (oldVersion under 43) {
                    if (preferenceStore.getBoolean("start_reading_button").get()) {
                        libraryPreferences.showContinueReadingButton().set(true)
                    }
                }
                if (oldVersion under 44) {
                    val trackingQueuePref = context.getSharedPreferences("tracking_queue", Context.MODE_PRIVATE)
                    trackingQueuePref.all.forEach {
                        val (_, lastChapterRead) = it.value.toString().split(":")
                        trackingQueuePref.edit {
                            remove(it.key)
                            putFloat(it.key, lastChapterRead.toFloat())
                        }
                    }
                }
                if (oldVersion under 45) {
                    // Force MangaDex log out due to login flow change
                    val trackManager = Injekt.get<TrackManager>()
                    trackManager.mdList.logout()
                }

                // if (oldVersion under 1) { } (1 is current release version)
                // do stuff here when releasing changed crap

                return true
            }
        } catch (e: Exception) {
            xLogE("Failed to migrate app from $oldVersion -> ${BuildConfig.VERSION_CODE}!", e)
        }
        return false
    }

    fun migrateBackupEntry(manga: Manga) {
        if (manga.source == 6907L) {
            // Migrate the old source to the delegated one
            manga.source = NHentai.otherId
            // Migrate nhentai URLs
            manga.url = getUrlWithoutDomain(manga.url)
        }

        // Migrate Tsumino source IDs
        if (manga.source == 6909L) {
            manga.source = TSUMINO_SOURCE_ID
        }

        if (manga.source == 6912L) {
            manga.source = HBROWSE_SOURCE_ID
            manga.url = manga.url + "/c00001/"
        }

        // Allow importing of EHentai extension backups
        if (manga.source in BlacklistedSources.EHENTAI_EXT_SOURCES) {
            manga.source = EH_SOURCE_ID
        }
    }

    private fun getUrlWithoutDomain(orig: String): String {
        return try {
            val uri = URI(orig)
            var out = uri.path
            if (uri.query != null) {
                out += "?" + uri.query
            }
            if (uri.fragment != null) {
                out += "#" + uri.fragment
            }
            out
        } catch (e: URISyntaxException) {
            orig
        }
    }

    @Serializable
    private data class UrlConfig(
        @SerialName("s")
        val source: Long,
        @SerialName("u")
        val url: String,
        @SerialName("m")
        val mangaUrl: String,
    )

    @Serializable
    private data class MangaConfig(
        @SerialName("c")
        val children: List<MangaSource>,
    ) {
        companion object {
            fun readFromUrl(url: String): MangaConfig? {
                return try {
                    Json.decodeFromString(url)
                } catch (e: Exception) {
                    null
                }
            }
        }
    }

    private fun readMangaConfig(manga: DomainManga): MangaConfig? {
        return MangaConfig.readFromUrl(manga.url)
    }

    @Serializable
    private data class MangaSource(
        @SerialName("s")
        val source: Long,
        @SerialName("u")
        val url: String,
    ) {
        fun load(): LoadedMangaSource? {
            val manga = runBlocking { getManga.await(url, source) } ?: return null
            val source = sourceManager.getOrStub(source)
            return LoadedMangaSource(source, manga)
        }
    }

    private fun readUrlConfig(url: String): UrlConfig? {
        return try {
            Json.decodeFromString(url)
        } catch (e: Exception) {
            null
        }
    }

    private data class LoadedMangaSource(val source: Source, val manga: DomainManga)

    private fun updateSourceId(newId: Long, oldId: Long) {
        runBlocking {
            handler.await { ehQueries.migrateSource(newId, oldId) }
        }
    }
}
