package com.ayzen.scan.ui.history

import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.IosShare
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ayzen.scan.MainViewModel
import com.ayzen.scan.R
import com.ayzen.scan.data.HistoryItem
import com.ayzen.scan.data.Source
import com.ayzen.scan.logic.Actions
import com.ayzen.scan.logic.ContentParser
import com.ayzen.scan.logic.formatTime
import com.ayzen.scan.ui.components.Brand
import com.ayzen.scan.ui.components.GradientText
import com.ayzen.scan.ui.components.SoftCard
import com.ayzen.scan.ui.components.TypeBadge
import com.ayzen.scan.ui.components.labelRes
import com.ayzen.scan.ui.result.ResultSheet
import kotlinx.coroutines.launch

private enum class Filter(@StringRes val label: Int) {
    ALL(R.string.filter_all),
    SCANNED(R.string.filter_scanned),
    CREATED(R.string.filter_created),
    FAVORITES(R.string.filter_favorites)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(vm: MainViewModel) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val history by vm.history.collectAsStateWithLifecycle()

    var query by remember { mutableStateOf("") }
    var filterIndex by remember { mutableIntStateOf(0) }
    var selected by remember { mutableStateOf<HistoryItem?>(null) }
    var confirmClear by remember { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }

    val itemDeleted = stringResource(R.string.history_deleted)
    val cleared = stringResource(R.string.history_cleared)
    val undoLabel = stringResource(R.string.undo)

    fun announce(message: String) {
        scope.launch {
            snackbar.currentSnackbarData?.dismiss()
            val r = snackbar.showSnackbar(
                message = message,
                actionLabel = undoLabel,
                duration = SnackbarDuration.Short
            )
            if (r == SnackbarResult.ActionPerformed) vm.undoDelete()
        }
    }

    val filter = Filter.entries[filterIndex]
    val q = query.trim()
    val visible = history.filter { item ->
        val passes = when (filter) {
            Filter.ALL -> true
            Filter.SCANNED -> item.source == Source.SCANNED
            Filter.CREATED -> item.source == Source.CREATED
            Filter.FAVORITES -> item.favorite
        }
        passes && (q.isEmpty() ||
            item.content.contains(q, ignoreCase = true) ||
            item.format.contains(q, ignoreCase = true))
    }

    Box(Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(top = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    GradientText(stringResource(R.string.history_title), MaterialTheme.typography.headlineMedium)
                    Text(
                        stringResource(R.string.history_count, history.size),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (history.isNotEmpty()) {
                    IconButton(onClick = { Actions.shareText(context, vm.exportText()) }) {
                        Icon(
                            Icons.Rounded.IosShare,
                            contentDescription = stringResource(R.string.history_export),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = { confirmClear = true }) {
                        Icon(
                            Icons.Rounded.DeleteSweep,
                            contentDescription = stringResource(R.string.clear_all_confirm),
                            tint = Brand.Danger
                        )
                    }
                }
            }

            // Search
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                placeholder = { Text(stringResource(R.string.history_search)) },
                leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        IconButton(onClick = { query = "" }) {
                            Icon(Icons.Rounded.Close, contentDescription = null)
                        }
                    }
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(),
                shape = RoundedCornerShape(18.dp)
            )

            // Filters
            Row(
                modifier = Modifier
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Filter.entries.forEachIndexed { index, f ->
                    FilterChip(
                        selected = index == filterIndex,
                        onClick = { filterIndex = index },
                        label = { Text(stringResource(f.label)) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.28f),
                            selectedLabelColor = MaterialTheme.colorScheme.onBackground
                        )
                    )
                }
            }

            // List
            if (visible.isEmpty()) {
                EmptyState(Modifier.weight(1f).fillMaxWidth())
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(visible, key = { it.id }) { item ->
                        HistoryRow(
                            item = item,
                            onClick = { selected = item },
                            onFavorite = { vm.toggleFavorite(item.id) },
                            onDelete = {
                                vm.delete(item.id)
                                announce(itemDeleted)
                            }
                        )
                    }
                }
            }
        }

        SnackbarHost(
            hostState = snackbar,
            modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp)
        )
    }

    selected?.let { item ->
        ResultSheet(item = item, vm = vm, onDismiss = { selected = null })
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text(stringResource(R.string.clear_all_title)) },
            text = { Text(stringResource(R.string.clear_all_msg)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmClear = false
                    vm.clearAll()
                    announce(cleared)
                }) {
                    Text(stringResource(R.string.clear_all_confirm), color = Brand.Danger, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmClear = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }
}

@Composable
private fun HistoryRow(
    item: HistoryItem,
    onClick: () -> Unit,
    onFavorite: () -> Unit,
    onDelete: () -> Unit
) {
    SoftCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            TypeBadge(item.type)
            Column(Modifier.weight(1f)) {
                Text(
                    ContentParser.preview(item.content, item.type),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                val src = if (item.source == Source.CREATED) R.string.src_created else R.string.src_scanned
                Text(
                    "${stringResource(item.type.labelRes())} · ${item.format} · ${stringResource(src)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    formatTime(item.timestamp),
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                )
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                IconButton(onClick = onFavorite, modifier = Modifier.size(40.dp)) {
                    Icon(
                        if (item.favorite) Icons.Rounded.Star else Icons.Rounded.StarBorder,
                        contentDescription = stringResource(
                            if (item.favorite) R.string.act_unfavorite else R.string.act_favorite
                        ),
                        tint = if (item.favorite) Brand.Star else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = onDelete, modifier = Modifier.size(40.dp)) {
                    Icon(
                        Icons.Rounded.DeleteOutline,
                        contentDescription = stringResource(R.string.act_delete),
                        tint = Brand.Danger
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptyState(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            Icons.Rounded.History,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
            modifier = Modifier.size(64.dp)
        )
        Text(
            stringResource(R.string.history_empty),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(top = 12.dp)
        )
        Text(
            stringResource(R.string.history_empty_desc),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp)
        )
    }
}
