package com.sublime.supersherpa.feature.transcription

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.outlined.MicNone
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.sublime.supersherpa.model.TranscriptionHistoryItem
import java.text.DateFormat
import java.util.Date

private enum class HistorySelectionMode {
    None,
    Multi,
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun HistoryScreen(
    history: List<TranscriptionHistoryItem>,
    onClose: () -> Unit,
    onCopyText: (String) -> Unit,
    onDeleteHistoryItems: (Collection<Long>) -> Unit,
    modifier: Modifier = Modifier,
) {
    var selectedIds by remember { mutableStateOf(emptySet<Long>()) }
    var showDeleteConfirmation by remember { mutableStateOf(false) }
    var selectionMode by remember { mutableStateOf(HistorySelectionMode.None) }
    val inSelectionMode = selectionMode != HistorySelectionMode.None

    BackHandler(enabled = inSelectionMode) {
        selectedIds = emptySet()
        selectionMode = HistorySelectionMode.None
        showDeleteConfirmation = false
    }

    LaunchedEffect(history) {
        val availableIds = history.asSequence().map { it.id }.toSet()
        selectedIds = selectedIds.filterTo(mutableSetOf()) { it in availableIds }
        if (selectedIds.isEmpty()) {
            showDeleteConfirmation = false
        }
    }

    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        text = if (inSelectionMode) {
                            "${selectedIds.size} selected"
                        } else {
                            "History"
                        },
                        style = MaterialTheme.typography.titleLarge,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = {
                        if (inSelectionMode) {
                            selectedIds = emptySet()
                            selectionMode = HistorySelectionMode.None
                            showDeleteConfirmation = false
                        } else {
                            onClose()
                        }
                    }) {
                        Icon(
                            imageVector = if (inSelectionMode) Icons.Filled.Close else Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = if (inSelectionMode) "Clear selection" else "Back",
                        )
                    }
                },
                actions = {
                    if (inSelectionMode) {
                        IconButton(
                            modifier = Modifier.size(32.dp),
                            onClick = {
                                selectedIds = history.mapTo(mutableSetOf()) { it.id }
                                selectionMode = HistorySelectionMode.Multi
                            },
                        ) {
                            Icon(
                                modifier = Modifier.size(18.dp),
                                imageVector = Icons.Filled.SelectAll,
                                contentDescription = "Select all chats",
                            )
                        }
                        IconButton(
                            enabled = selectedIds.isNotEmpty(),
                            onClick = { showDeleteConfirmation = true },
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Delete,
                                contentDescription = "Delete selected chats",
                            )
                        }
                    } else if (history.isNotEmpty()) {
                        IconButton(onClick = {
                            selectionMode = HistorySelectionMode.Multi
                            selectedIds = emptySet()
                        }) {
                            Icon(
                                imageVector = Icons.Filled.SelectAll,
                                contentDescription = "Select chats",
                            )
                        }
                    }
                },
            )
        },
    ) { paddingValues ->
        if (history.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(horizontal = 20.dp),
                contentAlignment = Alignment.TopCenter,
            ) {
                ElevatedCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp),
                    colors = CardDefaults.elevatedCardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                    ),
                ) {
                    Row(
                        modifier = Modifier.padding(20.dp),
                        verticalAlignment = Alignment.Top,
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.MicNone,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(top = 2.dp),
                        )
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Text(
                                text = "No transcription available yet",
                                style = MaterialTheme.typography.titleLarge,
                            )
                            Text(
                                text = "Your completed transcriptions will appear here.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            FilledTonalButton(onClick = onClose) {
                                Text(text = "Back to recorder")
                            }
                        }
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentPadding = PaddingValues(start = 20.dp, top = 16.dp, end = 20.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(
                    items = history,
                    key = { item -> item.id },
                    contentType = { "history_item" },
                ) { item ->
                    HistoryItemCard(
                        item = item,
                        isSelected = item.id in selectedIds,
                        inSelectionMode = inSelectionMode,
                        onSelectionToggle = {
                            selectedIds = when (selectionMode) {
                                HistorySelectionMode.Multi -> if (item.id in selectedIds) {
                                    selectedIds - item.id
                                } else {
                                    selectedIds + item.id
                                }
                                HistorySelectionMode.None -> emptySet()
                            }
                        },
                        onEnterSelectionMode = {
                            selectionMode = HistorySelectionMode.Multi
                            selectedIds = setOf(item.id)
                        },
                        onCopyText = onCopyText,
                    )
                }
            }
        }
    }

    if (showDeleteConfirmation) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmation = false },
            title = { Text(text = "Delete selected chats?") },
            text = {
                Text(
                    text = "This removes ${selectedIds.size} transcript${if (selectedIds.size == 1) "" else "s"} from local history.",
                )
            },
            confirmButton = {
                TextButton(
                    enabled = selectedIds.isNotEmpty(),
                    onClick = {
                        onDeleteHistoryItems(selectedIds)
                        selectedIds = emptySet()
                        selectionMode = HistorySelectionMode.None
                        showDeleteConfirmation = false
                    },
                ) {
                    Text(text = "Delete")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showDeleteConfirmation = false },
                ) {
                    Text(text = "Cancel")
                }
            },
        )
    }
}

@Composable
private fun HistoryItemCard(
    item: TranscriptionHistoryItem,
    isSelected: Boolean,
    inSelectionMode: Boolean,
    onSelectionToggle: () -> Unit,
    onEnterSelectionMode: () -> Unit,
    onCopyText: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val formattedTime = remember(item.createdAtEpochMillis) {
        DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)
            .format(Date(item.createdAtEpochMillis))
    }
    val selectedContentColor = MaterialTheme.colorScheme.onSecondaryContainer

    OutlinedCard(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.outlinedCardColors(
            containerColor = if (isSelected) {
                MaterialTheme.colorScheme.secondaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceContainerLow
            },
        ),
    ) {
        Row(
            modifier = Modifier
                .combinedClickable(
                    onClick = {
                        if (inSelectionMode) {
                            onSelectionToggle()
                        } else {
                            onCopyText(item.text)
                        }
                    },
                    onLongClick = onEnterSelectionMode,
                )
                .padding(horizontal = 12.dp, vertical = 16.dp),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (inSelectionMode) {
                Checkbox(
                    checked = isSelected,
                    onCheckedChange = { onSelectionToggle() },
                )
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = item.text,
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (isSelected) selectedContentColor else MaterialTheme.colorScheme.onSurface,
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = formattedTime,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (isSelected) selectedContentColor else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            if (!inSelectionMode) {
                IconButton(
                    modifier = Modifier.size(48.dp),
                    onClick = { onCopyText(item.text) },
                ) {
                    Icon(
                        imageVector = Icons.Filled.ContentCopy,
                        contentDescription = "Copy history item",
                    )
                }
            } else if (isSelected) {
                Spacer(modifier = Modifier.width(48.dp))
            }
        }
    }
}
