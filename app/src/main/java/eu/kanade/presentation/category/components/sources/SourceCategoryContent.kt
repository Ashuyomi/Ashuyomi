package eu.kanade.presentation.category.components.sources

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import eu.kanade.presentation.components.LazyColumn

@Composable
fun SourceCategoryContent(
    categories: List<String>,
    lazyListState: LazyListState,
    paddingValues: PaddingValues,
    onClickRename: (String) -> Unit,
    onClickDelete: (String) -> Unit,
) {
    LazyColumn(
        state = lazyListState,
        contentPadding = paddingValues,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(categories, key = { it }) { category ->
            SourceCategoryListItem(
                modifier = Modifier.animateItemPlacement(),
                category = category,
                onRename = { onClickRename(category) },
                onDelete = { onClickDelete(category) },
            )
        }
    }
}
