package exh.md.similar

import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.paging.compose.collectAsLazyPagingItems
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.domain.manga.model.Manga
import eu.kanade.presentation.browse.BrowseSourceContent
import eu.kanade.presentation.browse.components.BrowseSourceSimpleToolbar
import eu.kanade.presentation.components.Scaffold
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.ui.manga.MangaScreen

class MangaDexSimilarScreen(val mangaId: Long, val sourceId: Long) : Screen {

    @Composable
    override fun Content() {
        val screenModel = rememberScreenModel { MangaDexSimilarScreenModel(mangaId, sourceId) }
        val state by screenModel.state.collectAsState()
        val navigator = LocalNavigator.currentOrThrow

        val onMangaClick: (Manga) -> Unit = {
            navigator.push(MangaScreen(it.id, true))
        }

        val snackbarHostState = remember { SnackbarHostState() }

        Scaffold(
            topBar = { scrollBehavior ->
                BrowseSourceSimpleToolbar(
                    navigateUp = navigator::pop,
                    title = stringResource(R.string.similar, screenModel.manga.title),
                    displayMode = screenModel.displayMode,
                    onDisplayModeChange = { screenModel.displayMode = it },
                    scrollBehavior = scrollBehavior,
                )
            },
            snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        ) { paddingValues ->
            val pagingFlow by screenModel.mangaPagerFlowFlow.collectAsState()

            BrowseSourceContent(
                source = screenModel.source,
                mangaList = pagingFlow.collectAsLazyPagingItems(),
                columns = screenModel.getColumnsPreference(LocalConfiguration.current.orientation),
                // SY -->
                ehentaiBrowseDisplayMode = false,
                // SY <--
                displayMode = screenModel.displayMode,
                snackbarHostState = snackbarHostState,
                contentPadding = paddingValues,
                onWebViewClick = null,
                onHelpClick = null,
                onLocalSourceHelpClick = null,
                onMangaClick = onMangaClick,
                onMangaLongClick = onMangaClick,
            )
        }
    }
}
