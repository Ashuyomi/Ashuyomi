package eu.kanade.tachiyomi.data.preference

import android.content.Context
import android.os.Environment
import androidx.core.content.edit
import androidx.core.net.toUri
import androidx.preference.PreferenceManager
import com.tfcporciuncula.flow.FlowSharedPreferences
import com.tfcporciuncula.flow.Preference
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.database.models.Manga
import eu.kanade.tachiyomi.data.preference.PreferenceValues.DisplayMode
import eu.kanade.tachiyomi.data.track.TrackService
import eu.kanade.tachiyomi.data.track.anilist.Anilist
import eu.kanade.tachiyomi.widget.ExtendedNavigationView
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.onEach
import java.io.File
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.Locale
import eu.kanade.tachiyomi.data.preference.PreferenceKeys as Keys
import eu.kanade.tachiyomi.data.preference.PreferenceValues as Values

fun <T> Preference<T>.asImmediateFlow(block: (value: T) -> Unit): Flow<T> {
    block(get())
    return asFlow()
        .onEach { block(it) }
}

operator fun <T> Preference<Set<T>>.plusAssign(item: T) {
    set(get() + item)
}

operator fun <T> Preference<Set<T>>.minusAssign(item: T) {
    set(get() - item)
}

fun Preference<Boolean>.toggle() {
    set(!get())
}

class PreferencesHelper(val context: Context) {

    private val prefs = PreferenceManager.getDefaultSharedPreferences(context)
    val flowPrefs = FlowSharedPreferences(prefs)

    private val defaultDownloadsDir = File(
        Environment.getExternalStorageDirectory().absolutePath + File.separator +
            context.getString(R.string.app_name),
        "downloads"
    ).toUri()

    private val defaultBackupDir = File(
        Environment.getExternalStorageDirectory().absolutePath + File.separator +
            context.getString(R.string.app_name),
        "backup"
    ).toUri()

    fun startScreen() = prefs.getInt(Keys.startScreen, 1)

    fun confirmExit() = prefs.getBoolean(Keys.confirmExit, false)

    fun hideBottomBar() = flowPrefs.getBoolean(Keys.hideBottomBar, true)

    fun useBiometricLock() = flowPrefs.getBoolean(Keys.useBiometricLock, false)

    fun lockAppAfter() = flowPrefs.getInt(Keys.lockAppAfter, 0)

    fun lastAppUnlock() = flowPrefs.getLong(Keys.lastAppUnlock, 0)

    fun secureScreen() = flowPrefs.getBoolean(Keys.secureScreen, false)

    fun hideNotificationContent() = prefs.getBoolean(Keys.hideNotificationContent, false)

    fun autoUpdateMetadata() = prefs.getBoolean(Keys.autoUpdateMetadata, false)

    fun showLibraryUpdateErrors() = prefs.getBoolean(Keys.showLibraryUpdateErrors, false)

    fun clear() = prefs.edit { clear() }

    fun themeMode() = flowPrefs.getEnum(Keys.themeMode, Values.ThemeMode.system)

    fun themeLight() = flowPrefs.getEnum(Keys.themeLight, Values.LightThemeVariant.default)

    fun themeDark() = flowPrefs.getEnum(Keys.themeDark, Values.DarkThemeVariant.default)

    fun rotation() = flowPrefs.getInt(Keys.rotation, 1)

    fun pageTransitionsPager() = flowPrefs.getBoolean(Keys.enableTransitionsPager, true)

    fun pageTransitionsWebtoon() = flowPrefs.getBoolean(Keys.enableTransitionsWebtoon, true)

    fun doubleTapAnimSpeed() = flowPrefs.getInt(Keys.doubleTapAnimationSpeed, 500)

    fun showPageNumber() = flowPrefs.getBoolean(Keys.showPageNumber, true)

    fun dualPageSplitPaged() = flowPrefs.getBoolean(Keys.dualPageSplitPaged, false)

    fun dualPageSplitWebtoon() = flowPrefs.getBoolean(Keys.dualPageSplitWebtoon, false)

    fun dualPageInvertPaged() = flowPrefs.getBoolean(Keys.dualPageInvertPaged, false)

    fun dualPageInvertWebtoon() = flowPrefs.getBoolean(Keys.dualPageInvertWebtoon, false)

    fun showReadingMode() = prefs.getBoolean(Keys.showReadingMode, true)

    fun trueColor() = flowPrefs.getBoolean(Keys.trueColor, false)

    fun fullscreen() = flowPrefs.getBoolean(Keys.fullscreen, true)

    fun cutoutShort() = flowPrefs.getBoolean(Keys.cutoutShort, true)

    fun keepScreenOn() = flowPrefs.getBoolean(Keys.keepScreenOn, true)

    fun customBrightness() = flowPrefs.getBoolean(Keys.customBrightness, false)

    fun customBrightnessValue() = flowPrefs.getInt(Keys.customBrightnessValue, 0)

    fun colorFilter() = flowPrefs.getBoolean(Keys.colorFilter, false)

    fun colorFilterValue() = flowPrefs.getInt(Keys.colorFilterValue, 0)

    fun colorFilterMode() = flowPrefs.getInt(Keys.colorFilterMode, 0)

    fun defaultViewer() = prefs.getInt(Keys.defaultViewer, 2)

    fun imageScaleType() = flowPrefs.getInt(Keys.imageScaleType, 1)

    fun zoomStart() = flowPrefs.getInt(Keys.zoomStart, 1)

    fun readerTheme() = flowPrefs.getInt(Keys.readerTheme, 3)

    fun alwaysShowChapterTransition() = flowPrefs.getBoolean(Keys.alwaysShowChapterTransition, true)

    fun cropBorders() = flowPrefs.getBoolean(Keys.cropBorders, false)

    fun cropBordersWebtoon() = flowPrefs.getBoolean(Keys.cropBordersWebtoon, false)

    fun webtoonSidePadding() = flowPrefs.getInt(Keys.webtoonSidePadding, 0)

    fun readWithTapping() = flowPrefs.getBoolean(Keys.readWithTapping, true)

    fun pagerNavInverted() = flowPrefs.getEnum(Keys.pagerNavInverted, Values.TappingInvertMode.NONE)

    fun webtoonNavInverted() = flowPrefs.getEnum(Keys.webtoonNavInverted, Values.TappingInvertMode.NONE)

    fun readWithLongTap() = flowPrefs.getBoolean(Keys.readWithLongTap, true)

    fun readWithVolumeKeys() = flowPrefs.getBoolean(Keys.readWithVolumeKeys, false)

    fun readWithVolumeKeysInverted() = flowPrefs.getBoolean(Keys.readWithVolumeKeysInverted, false)

    fun navigationModePager() = flowPrefs.getInt(Keys.navigationModePager, 0)

    fun navigationModeWebtoon() = flowPrefs.getInt(Keys.navigationModeWebtoon, 0)

    fun showNavigationOverlayNewUser() = flowPrefs.getBoolean(Keys.showNavigationOverlayNewUser, true)

    fun showNavigationOverlayOnStart() = flowPrefs.getBoolean(Keys.showNavigationOverlayOnStart, false)

    fun portraitColumns() = flowPrefs.getInt(Keys.portraitColumns, 0)

    fun landscapeColumns() = flowPrefs.getInt(Keys.landscapeColumns, 0)

    fun jumpToChapters() = prefs.getBoolean(Keys.jumpToChapters, false)

    fun updateOnlyNonCompleted() = prefs.getBoolean(Keys.updateOnlyNonCompleted, false)

    fun autoUpdateTrack() = prefs.getBoolean(Keys.autoUpdateTrack, true)

    fun lastUsedSource() = flowPrefs.getLong(Keys.lastUsedSource, -1)

    fun lastUsedCategory() = flowPrefs.getInt(Keys.lastUsedCategory, 0)

    fun lastVersionCode() = flowPrefs.getInt("last_version_code", 0)

    fun sourceDisplayMode() = flowPrefs.getEnum(Keys.sourceDisplayMode, DisplayMode.COMPACT_GRID)

    fun enabledLanguages() = flowPrefs.getStringSet(Keys.enabledLanguages, setOf("all", "en", Locale.getDefault().language))

    fun trackUsername(sync: TrackService) = prefs.getString(Keys.trackUsername(sync.id), "")

    fun trackPassword(sync: TrackService) = prefs.getString(Keys.trackPassword(sync.id), "")

    fun setTrackCredentials(sync: TrackService, username: String, password: String) {
        prefs.edit {
            putString(Keys.trackUsername(sync.id), username)
            putString(Keys.trackPassword(sync.id), password)
        }
    }

    fun trackToken(sync: TrackService) = flowPrefs.getString(Keys.trackToken(sync.id), "")

    fun anilistScoreType() = flowPrefs.getString("anilist_score_type", Anilist.POINT_10)

    fun backupsDirectory() = flowPrefs.getString(Keys.backupDirectory, defaultBackupDir.toString())

    fun dateFormat(format: String = flowPrefs.getString(Keys.dateFormat, "").get()): DateFormat = when (format) {
        "" -> DateFormat.getDateInstance(DateFormat.SHORT)
        else -> SimpleDateFormat(format, Locale.getDefault())
    }

    fun downloadsDirectory() = flowPrefs.getString(Keys.downloadsDirectory, defaultDownloadsDir.toString())

    fun downloadOnlyOverWifi() = prefs.getBoolean(Keys.downloadOnlyOverWifi, true)

    fun numberOfBackups() = flowPrefs.getInt(Keys.numberOfBackups, 1)

    fun backupInterval() = flowPrefs.getInt(Keys.backupInterval, 0)

    fun removeAfterReadSlots() = prefs.getInt(Keys.removeAfterReadSlots, -1)

    fun removeAfterMarkedAsRead() = prefs.getBoolean(Keys.removeAfterMarkedAsRead, false)

    fun removeBookmarkedChapters() = prefs.getBoolean(Keys.removeBookmarkedChapters, false)

    fun libraryUpdateInterval() = flowPrefs.getInt(Keys.libraryUpdateInterval, 24)

    fun libraryUpdateRestriction() = prefs.getStringSet(Keys.libraryUpdateRestriction, setOf("wifi"))

    fun libraryUpdateCategories() = flowPrefs.getStringSet(Keys.libraryUpdateCategories, emptySet())

    fun libraryUpdatePrioritization() = flowPrefs.getInt(Keys.libraryUpdatePrioritization, 0)

    fun libraryDisplayMode() = flowPrefs.getEnum(Keys.libraryDisplayMode, DisplayMode.COMPACT_GRID)

    fun downloadBadge() = flowPrefs.getBoolean(Keys.downloadBadge, false)

    fun downloadedOnly() = flowPrefs.getBoolean(Keys.downloadedOnly, false)

    fun unreadBadge() = flowPrefs.getBoolean(Keys.unreadBadge, true)

    fun categoryTabs() = flowPrefs.getBoolean(Keys.categoryTabs, true)

    fun categoryNumberOfItems() = flowPrefs.getBoolean(Keys.categoryNumberOfItems, false)

    fun filterDownloaded() = flowPrefs.getInt(Keys.filterDownloaded, ExtendedNavigationView.Item.TriStateGroup.State.IGNORE.value)

    fun filterUnread() = flowPrefs.getInt(Keys.filterUnread, ExtendedNavigationView.Item.TriStateGroup.State.IGNORE.value)

    fun filterCompleted() = flowPrefs.getInt(Keys.filterCompleted, ExtendedNavigationView.Item.TriStateGroup.State.IGNORE.value)

    fun filterTracking(name: Int) = flowPrefs.getInt("${Keys.filterTracked}_$name", ExtendedNavigationView.Item.TriStateGroup.State.IGNORE.value)

    fun filterStarted() = flowPrefs.getInt(Keys.filterStarted, ExtendedNavigationView.Item.TriStateGroup.State.IGNORE.value)

    fun filterLewd() = flowPrefs.getInt(Keys.filterLewd, ExtendedNavigationView.Item.TriStateGroup.State.IGNORE.value)

    fun librarySortingMode() = flowPrefs.getInt(Keys.librarySortingMode, 0)

    fun librarySortingAscending() = flowPrefs.getBoolean("library_sorting_ascending", true)

    fun automaticExtUpdates() = flowPrefs.getBoolean(Keys.automaticExtUpdates, true)

    fun showNsfwSource() = flowPrefs.getBoolean(Keys.showNsfwSource, true)
    fun showNsfwExtension() = flowPrefs.getBoolean(Keys.showNsfwExtension, true)
    fun labelNsfwExtension() = prefs.getBoolean(Keys.labelNsfwExtension, true)

    fun extensionUpdatesCount() = flowPrefs.getInt("ext_updates_count", 0)

    fun lastExtCheck() = flowPrefs.getLong("last_ext_check", 0)

    fun searchPinnedSourcesOnly() = prefs.getBoolean(Keys.searchPinnedSourcesOnly, false)

    fun disabledSources() = flowPrefs.getStringSet("hidden_catalogues", emptySet())

    fun pinnedSources() = flowPrefs.getStringSet("pinned_catalogues", emptySet())

    fun downloadNew() = flowPrefs.getBoolean(Keys.downloadNew, false)

    fun downloadNewCategories() = flowPrefs.getStringSet(Keys.downloadNewCategories, emptySet())

    fun lang() = prefs.getString(Keys.lang, "")

    fun defaultCategory() = prefs.getInt(Keys.defaultCategory, -1)

    fun skipRead() = prefs.getBoolean(Keys.skipRead, false)

    fun skipFiltered() = prefs.getBoolean(Keys.skipFiltered, true)

    fun migrateFlags() = flowPrefs.getInt("migrate_flags", Int.MAX_VALUE)

    fun trustedSignatures() = flowPrefs.getStringSet("trusted_signatures", emptySet())

    fun enableDoh() = prefs.getBoolean(Keys.enableDoh, false)

    fun lastSearchQuerySearchSettings() = flowPrefs.getString("last_search_query", "")

    fun filterChapterByRead() = prefs.getInt(Keys.defaultChapterFilterByRead, Manga.SHOW_ALL)

    fun filterChapterByDownloaded() = prefs.getInt(Keys.defaultChapterFilterByDownloaded, Manga.SHOW_ALL)

    fun filterChapterByBookmarked() = prefs.getInt(Keys.defaultChapterFilterByBookmarked, Manga.SHOW_ALL)

    fun sortChapterBySourceOrNumber() = prefs.getInt(Keys.defaultChapterSortBySourceOrNumber, Manga.SORTING_SOURCE)

    fun displayChapterByNameOrNumber() = prefs.getInt(Keys.defaultChapterDisplayByNameOrNumber, Manga.DISPLAY_NAME)

    fun sortChapterByAscendingOrDescending() = prefs.getInt(Keys.defaultChapterSortByAscendingOrDescending, Manga.SORT_DESC)

    fun incognitoMode() = flowPrefs.getBoolean(Keys.incognitoMode, false)

    fun createLegacyBackup() = flowPrefs.getBoolean(Keys.createLegacyBackup, true)

    fun setChapterSettingsDefault(manga: Manga) {
        prefs.edit {
            putInt(Keys.defaultChapterFilterByRead, manga.readFilter)
            putInt(Keys.defaultChapterFilterByDownloaded, manga.downloadedFilter)
            putInt(Keys.defaultChapterFilterByBookmarked, manga.bookmarkedFilter)
            putInt(Keys.defaultChapterSortBySourceOrNumber, manga.sorting)
            putInt(Keys.defaultChapterDisplayByNameOrNumber, manga.displayMode)
            putInt(Keys.defaultChapterSortByAscendingOrDescending, if (manga.sortDescending()) Manga.SORT_DESC else Manga.SORT_ASC)
        }
    }

    // SY -->

    fun defaultMangaOrder() = flowPrefs.getString("default_manga_order", "")

    fun migrationSources() = flowPrefs.getString("migrate_sources", "")

    fun smartMigration() = flowPrefs.getBoolean("smart_migrate", false)

    fun useSourceWithMost() = flowPrefs.getBoolean("use_source_with_most", false)

    fun skipPreMigration() = flowPrefs.getBoolean(Keys.skipPreMigration, false)

    fun isHentaiEnabled() = flowPrefs.getBoolean(Keys.eh_is_hentai_enabled, true)

    fun enableExhentai() = flowPrefs.getBoolean(Keys.eh_enableExHentai, false)

    fun imageQuality() = flowPrefs.getString(Keys.eh_ehentai_quality, "auto")

    fun useHentaiAtHome() = flowPrefs.getInt(Keys.eh_enable_hah, 0)

    fun useJapaneseTitle() = flowPrefs.getBoolean("use_jp_title", false)

    fun exhUseOriginalImages() = flowPrefs.getBoolean(Keys.eh_useOrigImages, false)

    fun ehTagFilterValue() = flowPrefs.getInt(Keys.eh_tag_filtering_value, 0)

    fun ehTagWatchingValue() = flowPrefs.getInt(Keys.eh_tag_watching_value, 0)

    // EH Cookies
    fun memberIdVal() = flowPrefs.getString("eh_ipb_member_id", "")

    fun passHashVal() = flowPrefs.getString("eh_ipb_pass_hash", "")
    fun igneousVal() = flowPrefs.getString("eh_igneous", "")
    fun ehSettingsProfile() = flowPrefs.getInt(Keys.eh_ehSettingsProfile, -1)
    fun exhSettingsProfile() = flowPrefs.getInt(Keys.eh_exhSettingsProfile, -1)
    fun exhSettingsKey() = flowPrefs.getString(Keys.eh_settingsKey, "")
    fun exhSessionCookie() = flowPrefs.getString(Keys.eh_sessionCookie, "")
    fun exhHathPerksCookies() = flowPrefs.getString(Keys.eh_hathPerksCookie, "")

    fun exhShowSyncIntro() = flowPrefs.getBoolean(Keys.eh_showSyncIntro, true)

    fun exhReadOnlySync() = flowPrefs.getBoolean(Keys.eh_readOnlySync, false)

    fun exhLenientSync() = flowPrefs.getBoolean(Keys.eh_lenientSync, false)

    fun exhShowSettingsUploadWarning() = flowPrefs.getBoolean(Keys.eh_showSettingsUploadWarning, true)

    fun expandFilters() = flowPrefs.getBoolean(Keys.eh_expandFilters, false)

    fun readerThreads() = flowPrefs.getInt(Keys.eh_readerThreads, 2)

    fun readerInstantRetry() = flowPrefs.getBoolean(Keys.eh_readerInstantRetry, true)

    fun autoscrollInterval() = flowPrefs.getFloat(Keys.eh_utilAutoscrollInterval, 3f)

    fun cacheSize() = flowPrefs.getString(Keys.eh_cacheSize, "75")

    fun preserveReadingPosition() = flowPrefs.getBoolean(Keys.eh_preserveReadingPosition, false)

    fun autoSolveCaptcha() = flowPrefs.getBoolean(Keys.eh_autoSolveCaptchas, false)

    fun delegateSources() = flowPrefs.getBoolean(Keys.eh_delegateSources, true)

    fun ehLastVersionCode() = flowPrefs.getInt("eh_last_version_code", 0)

    fun savedSearches() = flowPrefs.getStringSet("eh_saved_searches", emptySet())

    fun logLevel() = flowPrefs.getInt(Keys.eh_logLevel, 0)

    fun enableSourceBlacklist() = flowPrefs.getBoolean(Keys.eh_enableSourceBlacklist, true)

    fun exhAutoUpdateFrequency() = flowPrefs.getInt(Keys.eh_autoUpdateFrequency, 1)

    fun exhAutoUpdateRequirements() = flowPrefs.getStringSet(Keys.eh_autoUpdateRestrictions, emptySet())

    fun exhAutoUpdateStats() = flowPrefs.getString(Keys.eh_autoUpdateStats, "")

    fun aggressivePageLoading() = flowPrefs.getBoolean(Keys.eh_aggressivePageLoading, false)

    fun preloadSize() = flowPrefs.getInt(Keys.eh_preload_size, 10)

    fun useAutoWebtoon() = flowPrefs.getBoolean(Keys.eh_use_auto_webtoon, true)

    fun exhWatchedListDefaultState() = flowPrefs.getBoolean(Keys.eh_watched_list_default_state, false)

    fun exhSettingsLanguages() = flowPrefs.getString(Keys.eh_settings_languages, "false*false*false\nfalse*false*false\nfalse*false*false\nfalse*false*false\nfalse*false*false\nfalse*false*false\nfalse*false*false\nfalse*false*false\nfalse*false*false\nfalse*false*false\nfalse*false*false\nfalse*false*false\nfalse*false*false\nfalse*false*false\nfalse*false*false\nfalse*false*false\nfalse*false*false")

    fun exhEnabledCategories() = flowPrefs.getString(Keys.eh_enabled_categories, "false,false,false,false,false,false,false,false,false,false")

    fun latestTabSources() = flowPrefs.getStringSet(Keys.latest_tab_sources, mutableSetOf())

    fun latestTabInFront() = flowPrefs.getBoolean(Keys.latest_tab_position, false)

    fun sourcesTabCategories() = flowPrefs.getStringSet(Keys.sources_tab_categories, mutableSetOf())

    fun sourcesTabCategoriesFilter() = flowPrefs.getBoolean(Keys.sources_tab_categories_filter, false)

    fun sourcesTabSourcesInCategories() = flowPrefs.getStringSet(Keys.sources_tab_source_categories, mutableSetOf())

    fun sourceSorting() = flowPrefs.getInt(Keys.sourcesSort, 0)

    fun recommendsInOverflow() = flowPrefs.getBoolean(Keys.recommendsInOverflow, false)

    fun enhancedEHentaiView() = flowPrefs.getBoolean(Keys.enhancedEHentaiView, true)

    fun webtoonEnableZoomOut() = flowPrefs.getBoolean(Keys.webtoonEnableZoomOut, false)

    fun startReadingButton() = flowPrefs.getBoolean(Keys.startReadingButton, true)

    fun groupLibraryBy() = flowPrefs.getInt(Keys.groupLibraryBy, 0)

    fun continuousVerticalTappingByPage() = flowPrefs.getBoolean(Keys.continuousVerticalTappingByPage, false)

    fun groupLibraryUpdateType() = flowPrefs.getEnum(Keys.groupLibraryUpdateType, Values.GroupLibraryMode.GLOBAL)

    fun useNewSourceNavigation() = flowPrefs.getBoolean(Keys.useNewSourceNavigation, true)

    fun mangaDexForceLatestCovers() = flowPrefs.getBoolean(Keys.mangaDexForceLatestCovers, false)

    fun preferredMangaDexId() = flowPrefs.getString(Keys.preferredMangaDexId, "0")

    fun mangadexSimilarEnabled() = flowPrefs.getBoolean(Keys.mangadexSimilarEnabled, false)

    fun shownMangaDexSimilarAskDialog() = flowPrefs.getBoolean("shown_similar_ask_dialog", false)

    fun mangadexSimilarOnlyOverWifi() = flowPrefs.getBoolean(Keys.mangadexSimilarOnlyOverWifi, true)

    fun mangadexSyncToLibraryIndexes() = flowPrefs.getStringSet(Keys.mangadexSyncToLibraryIndexes, emptySet())

    fun mangadexSimilarUpdateInterval() = flowPrefs.getInt(Keys.mangadexSimilarUpdateInterval, 2)

    fun dataSaver() = flowPrefs.getBoolean(Keys.dataSaver, false)

    fun ignoreJpeg() = flowPrefs.getBoolean(Keys.ignoreJpeg, false)

    fun ignoreGif() = flowPrefs.getBoolean(Keys.ignoreGif, true)

    fun dataSaverImageQuality() = flowPrefs.getInt(Keys.dataSaverImageQuality, 80)

    fun dataSaverImageFormatJpeg() = flowPrefs.getBoolean(Keys.dataSaverImageFormatJpeg, false)

    fun dataSaverServer() = flowPrefs.getString(Keys.dataSaverServer, "")

    fun dataSaverColorBW() = flowPrefs.getBoolean(Keys.dataSaverColorBW, false)

    fun saveChaptersAsCBZ() = flowPrefs.getBoolean(Keys.saveChaptersAsCBZ, false)

    fun saveChaptersAsCBZLevel() = flowPrefs.getInt(Keys.saveChaptersAsCBZLevel, 0)

    fun allowLocalSourceHiddenFolders() = flowPrefs.getBoolean(Keys.allowLocalSourceHiddenFolders, false)

    fun biometricTimeRanges() = flowPrefs.getStringSet(Keys.biometricTimeRanges, mutableSetOf())

    fun sortTagsForLibrary() = flowPrefs.getStringSet(Keys.sortTagsForLibrary, mutableSetOf())

    fun dontDeleteFromCategories() = flowPrefs.getStringSet(Keys.dontDeleteFromCategories, emptySet())

    fun extensionRepos() = flowPrefs.getStringSet(Keys.extensionRepos, emptySet())

    fun cropBordersContinuesVertical() = flowPrefs.getBoolean(Keys.cropBordersContinuesVertical, false)

    fun forceHorizontalSeekbar() = flowPrefs.getBoolean(Keys.forceHorizontalSeekbar, false)

    fun landscapeVerticalSeekbar() = flowPrefs.getBoolean(Keys.landscapeVerticalSeekbar, false)

    fun leftVerticalSeekbar() = flowPrefs.getBoolean(Keys.leftVerticalSeekbar, false)

    fun tachideskUrl() = flowPrefs.getString(Keys.tachideskUrl, "")
}
