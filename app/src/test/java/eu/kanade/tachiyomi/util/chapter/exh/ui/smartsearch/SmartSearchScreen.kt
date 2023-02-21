package eu.kanade.tachiyomi.util.chapter.exh.ui.smartsearch

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.presentation.components.AppBar
import eu.kanade.presentation.components.Scaffold
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.ui.browse.source.SourcesScreen
import eu.kanade.tachiyomi.ui.browse.source.browse.BrowseSourceScreen
import eu.kanade.tachiyomi.ui.manga.MangaScreen
import eu.kanade.tachiyomi.util.system.toast

class SmartSearchScreen(private val sourceId: Long, private val smartSearchConfig: SourcesScreen.SmartSearchConfig) : Screen {

    @Composable
    override fun Content() {
        val screenModel = rememberScreenModel { SmartSearchScreenModel(sourceId, smartSearchConfig) }

        val navigator = LocalNavigator.currentOrThrow
        val context = LocalContext.current
        val state by screenModel.state.collectAsState()
        LaunchedEffect(state) {
            val results = state
            if (results != null) {
                if (results is SmartSearchScreenModel.SearchResults.Found) {
                    navigator.replace(MangaScreen(results.manga.id, true, smartSearchConfig))
                } else {
                    if (results is SmartSearchScreenModel.SearchResults.NotFound) {
                        context.toast(R.string.could_not_find_entry)
                    } else {
                        context.toast(R.string.automatic_search_error)
                    }
                    navigator.push(
                        BrowseSourceScreen(
                            sourceId = screenModel.source.id,
                            listingQuery = smartSearchConfig.origTitle,
                            smartSearchConfig = smartSearchConfig,
                        ),
                    )
                }
            }
        }

        Scaffold(
            topBar = { scrollBehavior ->
                AppBar(
                    title = screenModel.source.name,
                    navigateUp = navigator::pop,
                    scrollBehavior = scrollBehavior,
                )
            },
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(it),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically),
            ) {
                Text(
                    text = stringResource(R.string.searching_source),
                    style = MaterialTheme.typography.titleLarge,
                )
                CircularProgressIndicator(modifier = Modifier.size(56.dp))
            }
        }
    }
}
