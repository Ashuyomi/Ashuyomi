package exh.recs

import eu.kanade.domain.manga.interactor.GetManga
import eu.kanade.domain.source.model.SourcePagingSourceType
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.ui.browse.source.browse.BrowseSourceScreenModel
import kotlinx.coroutines.runBlocking
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class RecommendsScreenModel(
    val mangaId: Long,
    sourceId: Long,
    private val getManga: GetManga = Injekt.get(),
) : BrowseSourceScreenModel(sourceId, null) {

    val manga = runBlocking { getManga.await(mangaId) }!!

    override fun createSourcePagingSource(query: String, filters: FilterList): SourcePagingSourceType {
        return RecommendsPagingSource(source, manga)
    }
}
