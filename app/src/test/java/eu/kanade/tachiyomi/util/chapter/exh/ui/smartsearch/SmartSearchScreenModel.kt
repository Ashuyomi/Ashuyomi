package eu.kanade.tachiyomi.util.chapter.exh.ui.smartsearch

import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.coroutineScope
import eu.kanade.domain.manga.interactor.NetworkToLocalManga
import eu.kanade.domain.manga.model.Manga
import eu.kanade.tachiyomi.source.CatalogueSource
import eu.kanade.tachiyomi.source.SourceManager
import eu.kanade.tachiyomi.ui.browse.source.SourcesScreen
import eu.kanade.tachiyomi.util.lang.launchIO
import exh.smartsearch.SmartSearchEngine
import kotlinx.coroutines.CancellationException
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class SmartSearchScreenModel(
    private val sourceId: Long,
    private val config: SourcesScreen.SmartSearchConfig,
    private val networkToLocalManga: NetworkToLocalManga = Injekt.get(),
    private val sourceManager: SourceManager = Injekt.get(),
) : StateScreenModel<SmartSearchScreenModel.SearchResults?>(null) {
    private val smartSearchEngine = SmartSearchEngine()

    val source = sourceManager.get(sourceId) as CatalogueSource

    init {
        coroutineScope.launchIO {
            val result = try {
                val resultManga = smartSearchEngine.smartSearch(source, config.origTitle)
                if (resultManga != null) {
                    val localManga = networkToLocalManga.await(resultManga)
                    SearchResults.Found(localManga)
                } else {
                    SearchResults.NotFound
                }
            } catch (e: Exception) {
                if (e is CancellationException) {
                    throw e
                } else {
                    SearchResults.Error
                }
            }

            mutableState.value = result
        }
    }

    sealed class SearchResults {
        data class Found(val manga: Manga) : SearchResults()
        object NotFound : SearchResults()
        object Error : SearchResults()
    }
}
