package eu.kanade.presentation.browse

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import eu.kanade.presentation.components.AppBar
import eu.kanade.presentation.components.AppBarActions
import eu.kanade.presentation.components.Scaffold
import eu.kanade.presentation.components.TabContent

@Composable
fun BrowseTabWrapper(tab: TabContent) {
    Scaffold(
        topBar = { scrollBehavior ->
            AppBar(
                title = stringResource(tab.titleRes),
                actions = {
                    AppBarActions(tab.actions)
                },
                scrollBehavior = scrollBehavior,
            )
        },
    ) { paddingValues ->
        Column(modifier = Modifier.padding(paddingValues)) {
            tab.content()
        }
    }
}