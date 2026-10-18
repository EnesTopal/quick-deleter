package com.enestopal.quickdeleter.ui.browser

import android.content.res.Resources
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.enestopal.quickdeleter.R
import com.enestopal.quickdeleter.data.ConflictPolicy
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import com.enestopal.quickdeleter.data.SortOption
import com.enestopal.quickdeleter.ui.components.ConfirmDeleteDialog
import com.enestopal.quickdeleter.ui.components.EmptyState
import com.enestopal.quickdeleter.ui.components.FileDetailsDialog
import com.enestopal.quickdeleter.ui.components.MoveConflictDialog
import com.enestopal.quickdeleter.ui.components.NameInputDialog
import com.enestopal.quickdeleter.ui.components.rememberDateFormat
import com.enestopal.quickdeleter.ui.move.MoveDestinationScreen
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BrowserScreen(viewModel: BrowserViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val resources = LocalContext.current.resources

    LaunchedEffect(viewModel) {
        viewModel.messages.collect { message ->
            showMessage(message, snackbarHostState, resources, viewModel)
        }
    }

    BackHandler(enabled = state.move == null && (state.isSearchActive || !state.isAtRoot || state.isSelectionMode)) {
        viewModel.onBack()
    }

    BrowserDialogs(state, viewModel)

    val move = state.move
    if (move != null) {
        MoveDestinationScreen(
            state = move,
            rootPath = state.rootPath,
            snackbarHostState = snackbarHostState,
            onNavigate = viewModel::moveNavigateTo,
            onBack = viewModel::onMoveBack,
            onCancel = viewModel::cancelMove,
            onCreateFolder = { viewModel.requestCreateFolder(move.currentPath) },
            onConfirm = { viewModel.confirmMove() },
        )
        return
    }

    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    val selectedCount = state.selectedHere.size

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            when {
                state.isSelectionMode -> SelectionTopBar(
                    count = selectedCount,
                    allSelected = state.visibleItems.any { !it.isDirectory } &&
                        state.visibleItems.filterNot { it.isDirectory }.all { state.selection.isSelected(it) },
                    onSelectAll = viewModel::toggleSelectAll,
                    onClose = viewModel::exitSelectionMode,
                )
                state.isSearchActive -> SearchTopBar(
                    query = state.query,
                    onQueryChange = viewModel::onQueryChange,
                    onClose = viewModel::closeSearch,
                )
                else -> BrowserTopBar(state, viewModel, scrollBehavior)
            }
        },
        floatingActionButton = {
            AnimatedVisibility(
                visible = state.isSelectionMode && selectedCount > 0,
                enter = scaleIn(),
                exit = scaleOut(),
            ) {
                ExtendedFloatingActionButton(
                    onClick = viewModel::requestDeleteSelected,
                    icon = { Icon(Icons.Default.Delete, contentDescription = null) },
                    text = { Text(stringResource(R.string.delete_selected, selectedCount)) },
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer,
                )
            }
        },
    ) { padding ->
        Column(Modifier.padding(padding)) {
            Breadcrumbs(state, onNavigate = viewModel::navigateTo)
            SortBar(
                sortOption = state.sortOption,
                ascending = state.ascending,
                itemCount = state.visibleItems.size,
                onSortSelected = viewModel::onSortSelected,
            )
            FileList(state, viewModel)
        }
    }
}

@Composable
private fun FileList(state: BrowserUiState, viewModel: BrowserViewModel) {
    val dateFormat = rememberDateFormat()
    var menuForPath by rememberSaveable { mutableStateOf<String?>(null) }
    val listState = rememberLazyListState()
    LaunchedEffect(state.currentPath) { listState.scrollToItem(0) }

    when {
        state.items.isEmpty() && state.isLoading -> Box(Modifier.fillMaxSize(), Alignment.Center) {
            CircularProgressIndicator()
        }

        state.visibleItems.isEmpty() && state.query.isNotBlank() -> EmptyState(
            icon = rememberVectorPainter(Icons.Default.Search),
            tint = MaterialTheme.colorScheme.primary,
            title = stringResource(R.string.empty_search_title),
            body = stringResource(R.string.empty_search_body, state.query),
        )

        state.visibleItems.isEmpty() -> EmptyState(
            icon = painterResource(R.drawable.folder_logo),
            title = stringResource(R.string.empty_folder_title),
            body = stringResource(R.string.empty_folder_body),
        )

        else -> LazyColumn(
            state = listState,
            contentPadding = PaddingValues(bottom = 96.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            items(state.visibleItems, key = { it.path }) { item ->
                FileRow(
                    item = item,
                    dateFormat = dateFormat,
                    isSelectionMode = state.isSelectionMode,
                    isSelected = state.selection.isSelected(item),
                    showMenu = menuForPath == item.path,
                    onClick = {
                        when {
                            item.isDirectory -> viewModel.openFolder(item)
                            state.isSelectionMode -> viewModel.toggleSelection(item)
                            else -> viewModel.showDetails(item)
                        }
                    },
                    onLongClick = {
                        if (item.isDirectory) viewModel.openFolder(item) else menuForPath = item.path
                    },
                    onDismissMenu = { menuForPath = null },
                    onSelect = {
                        menuForPath = null
                        viewModel.enterSelectionMode(item)
                    },
                    onMove = {
                        menuForPath = null
                        viewModel.startMove(item)
                    },
                    onRename = {
                        menuForPath = null
                        viewModel.requestRename(item)
                    },
                    onDelete = {
                        menuForPath = null
                        viewModel.requestDelete(item)
                    },
                    modifier = Modifier.animateItem(),
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BrowserTopBar(
    state: BrowserUiState,
    viewModel: BrowserViewModel,
    scrollBehavior: androidx.compose.material3.TopAppBarScrollBehavior,
) {
    var showOverflow by remember { mutableStateOf(false) }
    TopAppBar(
        scrollBehavior = scrollBehavior,
        title = {
            Text(
                if (state.isAtRoot) stringResource(R.string.internal_storage) else File(state.currentPath).name,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        navigationIcon = {
            if (!state.isAtRoot) {
                IconButton(onClick = { viewModel.onBack() }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                }
            }
        },
        actions = {
            IconButton(onClick = viewModel::openSearch) {
                Icon(Icons.Default.Search, contentDescription = stringResource(R.string.action_search))
            }
            IconButton(onClick = { viewModel.enterSelectionMode() }) {
                Icon(Icons.Default.CheckCircle, contentDescription = stringResource(R.string.action_select_mode))
            }
            Box {
                IconButton(onClick = { showOverflow = true }) {
                    Icon(Icons.Default.MoreVert, contentDescription = stringResource(R.string.action_more))
                }
                DropdownMenu(expanded = showOverflow, onDismissRequest = { showOverflow = false }) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.action_new_folder)) },
                        onClick = {
                            showOverflow = false
                            viewModel.requestCreateFolder(state.currentPath)
                        },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.action_show_hidden)) },
                        trailingIcon = { Checkbox(checked = state.showHidden, onCheckedChange = null) },
                        onClick = viewModel::toggleShowHidden,
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.action_hide_empty)) },
                        trailingIcon = { Checkbox(checked = state.hideEmptyFolders, onCheckedChange = null) },
                        onClick = viewModel::toggleHideEmptyFolders,
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.action_refresh)) },
                        onClick = {
                            showOverflow = false
                            viewModel.refresh()
                        },
                    )
                }
            }
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SelectionTopBar(count: Int, allSelected: Boolean, onSelectAll: () -> Unit, onClose: () -> Unit) {
    TopAppBar(
        colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
        navigationIcon = {
            IconButton(onClick = onClose) {
                Icon(Icons.Default.Close, contentDescription = stringResource(R.string.action_exit_selection))
            }
        },
        title = { Text(pluralStringResource(R.plurals.selected_count, count, count)) },
        actions = {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(end = 8.dp)) {
                Text(stringResource(R.string.action_select_all), style = MaterialTheme.typography.labelLarge)
                Checkbox(checked = allSelected, onCheckedChange = { onSelectAll() })
            }
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SearchTopBar(query: String, onQueryChange: (String) -> Unit, onClose: () -> Unit) {
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }
    TopAppBar(
        navigationIcon = {
            IconButton(onClick = onClose) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_close_search))
            }
        },
        title = {
            TextField(
                value = query,
                onValueChange = onQueryChange,
                placeholder = { Text(stringResource(R.string.search_hint)) },
                singleLine = true,
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                ),
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        IconButton(onClick = { onQueryChange("") }) {
                            Icon(Icons.Default.Close, contentDescription = stringResource(R.string.action_clear))
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester),
            )
        },
    )
}

@Composable
private fun Breadcrumbs(state: BrowserUiState, onNavigate: (String) -> Unit) {
    val crumbs = listOf(stringResource(R.string.internal_storage) to state.rootPath) + state.breadcrumbs
    val listState = rememberLazyListState()
    LaunchedEffect(crumbs.size) { listState.animateScrollToItem(crumbs.lastIndex) }
    LazyRow(
        state = listState,
        contentPadding = PaddingValues(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth(),
    ) {
        itemsIndexed(crumbs, key = { _, crumb -> crumb.second }) { index, (label, path) ->
            val isLast = index == crumbs.lastIndex
            Row(verticalAlignment = Alignment.CenterVertically) {
                AssistChip(
                    onClick = { if (!isLast) onNavigate(path) },
                    label = {
                        Text(
                            label,
                            maxLines = 1,
                            color = if (isLast) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    },
                    border = null,
                )
                if (!isLast) {
                    Icon(
                        Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.outline,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun SortBar(
    sortOption: SortOption,
    ascending: Boolean,
    itemCount: Int,
    onSortSelected: (SortOption) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SortOption.entries.forEach { option ->
            val selected = option == sortOption
            FilterChip(
                selected = selected,
                onClick = { onSortSelected(option) },
                label = { Text(stringResource(option.labelRes())) },
                trailingIcon = if (selected) {
                    {
                        Icon(
                            if (ascending) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                            contentDescription = stringResource(
                                if (ascending) R.string.sort_ascending else R.string.sort_descending
                            ),
                            modifier = Modifier.size(18.dp),
                        )
                    }
                } else null,
            )
        }
        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) {
            Text(
                pluralStringResource(R.plurals.item_count, itemCount, itemCount),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun BrowserDialogs(state: BrowserUiState, viewModel: BrowserViewModel) {
    when (val dialog = state.dialog) {
        null -> Unit
        is BrowserDialog.ConfirmDelete -> ConfirmDeleteDialog(
            items = dialog.items,
            onConfirm = { viewModel.confirmDelete(dialog.items) },
            onDismiss = viewModel::dismissDialog,
        )
        is BrowserDialog.Details -> FileDetailsDialog(
            item = dialog.item,
            onMove = { viewModel.startMove(dialog.item) },
            onDelete = { viewModel.requestDelete(dialog.item) },
            onDismiss = viewModel::dismissDialog,
        )
        is BrowserDialog.Rename -> NameInputDialog(
            title = stringResource(R.string.rename_title),
            initialName = dialog.item.name,
            confirmLabel = stringResource(R.string.action_rename),
            onConfirm = { viewModel.rename(dialog.item, it) },
            onDismiss = viewModel::dismissDialog,
        )
        is BrowserDialog.CreateFolder -> NameInputDialog(
            title = stringResource(R.string.new_folder_title),
            initialName = "",
            confirmLabel = stringResource(R.string.action_create),
            onConfirm = { viewModel.createFolder(dialog.parentPath, it) },
            onDismiss = viewModel::dismissDialog,
        )
        is BrowserDialog.MoveConflict -> MoveConflictDialog(
            fileName = dialog.source.name,
            onReplace = { viewModel.confirmMove(ConflictPolicy.REPLACE) },
            onKeepBoth = { viewModel.confirmMove(ConflictPolicy.KEEP_BOTH) },
            onDismiss = viewModel::dismissDialog,
        )
    }
}

private suspend fun showMessage(
    message: BrowserMessage,
    host: SnackbarHostState,
    res: Resources,
    viewModel: BrowserViewModel,
) {
    when (message) {
        is BrowserMessage.Deleted -> {
            val batch = message.batch
            val deleted = batch.deletedPaths.size
            if (deleted == 0) {
                host.showSnackbar(res.getQuantityString(R.plurals.msg_delete_failed, batch.failed.size, batch.failed.size))
                return
            }
            var text = res.getQuantityString(R.plurals.msg_deleted, deleted, deleted)
            if (batch.failed.isNotEmpty()) {
                text += " · " + res.getQuantityString(R.plurals.msg_delete_failed, batch.failed.size, batch.failed.size)
            }
            val canUndo = batch.entries.isNotEmpty()
            val result = host.showSnackbar(
                message = text,
                actionLabel = if (canUndo) res.getString(R.string.action_undo) else null,
                duration = if (canUndo) SnackbarDuration.Long else SnackbarDuration.Short,
            )
            if (result == SnackbarResult.ActionPerformed) viewModel.undoDelete(batch)
            else viewModel.finalizeDelete(batch)
        }
        is BrowserMessage.Restored -> host.showSnackbar(
            if (message.failed > 0) res.getString(R.string.msg_restore_failed)
            else res.getQuantityString(R.plurals.msg_restored, message.count, message.count)
        )
        is BrowserMessage.Moved -> host.showSnackbar(res.getString(R.string.msg_moved, message.name, message.folderName))
        BrowserMessage.MoveSameLocation -> host.showSnackbar(res.getString(R.string.msg_move_same))
        is BrowserMessage.MoveFailed -> host.showSnackbar(res.getString(R.string.msg_move_failed, message.name))
        BrowserMessage.NameInvalid -> host.showSnackbar(res.getString(R.string.msg_name_invalid))
        BrowserMessage.NameExists -> host.showSnackbar(res.getString(R.string.msg_name_exists))
        BrowserMessage.OperationFailed -> host.showSnackbar(res.getString(R.string.msg_failed))
    }
}

private fun SortOption.labelRes(): Int = when (this) {
    SortOption.NAME -> R.string.sort_name
    SortOption.SIZE -> R.string.sort_size
    SortOption.DATE -> R.string.sort_date
}
