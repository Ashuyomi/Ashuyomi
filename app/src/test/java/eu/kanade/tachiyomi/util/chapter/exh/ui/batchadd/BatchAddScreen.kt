package eu.kanade.tachiyomi.util.chapter.exh.ui.batchadd

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.presentation.components.AppBar
import eu.kanade.presentation.components.Button
import eu.kanade.presentation.components.LazyColumn
import eu.kanade.presentation.components.Scaffold
import eu.kanade.presentation.util.padding
import eu.kanade.presentation.util.plus
import eu.kanade.tachiyomi.R

class BatchAddScreen : Screen {

    @Composable
    override fun Content() {
        val screenModel = rememberScreenModel { BatchAddScreenModel() }
        val state by screenModel.state.collectAsState()
        val navigator = LocalNavigator.currentOrThrow
        val context = LocalContext.current

        Scaffold(
            topBar = { scrollBehavior ->
                AppBar(
                    title = stringResource(R.string.batch_add),
                    navigateUp = navigator::pop,
                    scrollBehavior = scrollBehavior,
                )
            },
        ) { paddingValues ->
            when (state.state) {
                BatchAddScreenModel.State.INPUT -> {
                    Column(
                        Modifier
                            .padding(paddingValues)
                            .verticalScroll(rememberScrollState())
                            .padding(MaterialTheme.padding.medium),
                    ) {
                        Text(text = stringResource(R.string.eh_batch_add_title), style = MaterialTheme.typography.titleLarge)
                        Spacer(Modifier.height(8.dp))
                        TextField(
                            value = state.galleries,
                            onValueChange = screenModel::updateGalleries,
                            modifier = Modifier.fillMaxWidth(),
                            placeholder = {
                                Text(
                                    text = stringResource(R.string.eh_batch_add_description),
                                )
                            },
                            keyboardOptions = KeyboardOptions(autoCorrect = false),
                            textStyle = MaterialTheme.typography.bodyLarge,

                        )
                        Spacer(Modifier.height(8.dp))
                        Button(
                            modifier = Modifier.fillMaxWidth(),
                            onClick = { screenModel.addGalleries(context) },
                        ) {
                            Text(text = stringResource(R.string.eh_batch_add_button))
                        }
                    }
                }
                BatchAddScreenModel.State.PROGRESS -> {
                    LazyColumn(
                        contentPadding = paddingValues + PaddingValues(MaterialTheme.padding.medium),
                    ) {
                        item(key = "top") {
                            Column {
                                Text(text = stringResource(R.string.eh_batch_add_adding_galleries), style = MaterialTheme.typography.titleLarge)
                                Spacer(modifier = Modifier.height(8.dp))
                                Row(
                                    Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    LinearProgressIndicator(
                                        progress = state.progress.toFloat() / state.progressTotal,
                                        Modifier
                                            .padding(top = 2.dp)
                                            .weight(1f),
                                    )
                                    Text(
                                        text = state.progress.toString() + "/" + state.progressTotal,
                                        modifier = Modifier.weight(0.15f),
                                        textAlign = TextAlign.Center,
                                        style = MaterialTheme.typography.bodyMedium,
                                    )
                                }
                                Spacer(modifier = Modifier.height(12.dp))
                            }
                        }
                        itemsIndexed(
                            state.events,
                            key = { index, text -> index + text.hashCode() },
                        ) { _, text ->
                            Text(
                                text = text,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.fillMaxWidth(),
                                textAlign = TextAlign.Center,
                            )
                        }
                        if (state.progress == state.progressTotal) {
                            item(key = "finish") {
                                Column {
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Button(
                                        modifier = Modifier.fillMaxWidth(),
                                        onClick = screenModel::finish,
                                    ) {
                                        Text(text = stringResource(R.string.eh_batch_add_finish))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        val onDismissRequest = screenModel::dismissDialog
        when (state.dialog) {
            BatchAddScreenModel.Dialog.NoGalleriesSpecified -> AlertDialog(
                onDismissRequest = onDismissRequest,
                confirmButton = {
                    TextButton(onClick = onDismissRequest) {
                        Text(text = stringResource(android.R.string.ok))
                    }
                },
                title = {
                    Text(text = stringResource(R.string.batch_add_no_valid_galleries))
                },
                text = {
                    Text(text = stringResource(R.string.batch_add_no_valid_galleries_message))
                },
            )
            null -> Unit
        }
    }
}
