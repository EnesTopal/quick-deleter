package com.enestopal.quickdeleter.ui.browser

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.enestopal.quickdeleter.data.ConflictPolicy
import com.enestopal.quickdeleter.data.FileItem
import com.enestopal.quickdeleter.data.FileOperations
import com.enestopal.quickdeleter.data.MoveResult
import com.enestopal.quickdeleter.data.NameResult
import com.enestopal.quickdeleter.data.Selection
import com.enestopal.quickdeleter.data.SortOption
import com.enestopal.quickdeleter.data.Trash
import com.enestopal.quickdeleter.data.TrashBatch
import com.enestopal.quickdeleter.data.sortItems
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class BrowserViewModel(
    private val savedState: SavedStateHandle,
    rootDirectory: File,
    private val io: CoroutineDispatcher = Dispatchers.IO,
) : ViewModel() {

    private val rootPath = rootDirectory.absolutePath
    private val trash = Trash(File(rootDirectory, TRASH_FOLDER))
    private var trashPurged = false

    private val _state = MutableStateFlow(restoreState())
    val state: StateFlow<BrowserUiState> = _state.asStateFlow()

    private val _messages = Channel<BrowserMessage>(Channel.BUFFERED)
    val messages = _messages.receiveAsFlow()

    private var loadJob: Job? = null
    private var moveLoadJob: Job? = null

    // region permission & loading

    fun onPermissionChecked(granted: Boolean) {
        _state.update { it.copy(hasPermission = granted) }
        if (!granted) return
        if (!trashPurged) {
            trashPurged = true
            viewModelScope.launch(io) { trash.purgeAll() }
        }
        // Reload on every resume: files may have changed while the app was in the background.
        refresh()
        _state.value.move?.let { loadMoveFolders(it.currentPath) }
    }

    fun refresh() {
        val snapshot = _state.value
        if (!snapshot.hasPermission) return
        loadJob?.cancel()
        _state.update { it.copy(isLoading = true) }
        loadJob = viewModelScope.launch {
            val path = snapshot.currentPath
            val listed = withContext(io) {
                FileOperations.listDirectory(
                    File(path),
                    showHidden = snapshot.showHidden,
                    hideEmptyFolders = snapshot.hideEmptyFolders,
                    excluded = setOf(trash.directory.absolutePath),
                )
            }
            updateAndSave { state ->
                if (state.currentPath != path) return@updateAndSave state
                state.copy(
                    items = sortItems(listed, state.sortOption, state.ascending),
                    isLoading = false,
                    selection = state.selection.pruneFolder(path, listed),
                )
            }
        }
    }

    // endregion

    // region navigation

    fun openFolder(item: FileItem) {
        if (item.isDirectory) navigateTo(item.path)
    }

    fun navigateTo(path: String) {
        if (!path.startsWith(rootPath)) return
        updateAndSave {
            it.copy(currentPath = path, items = emptyList(), isSearchActive = false, query = "")
        }
        refresh()
    }

    /** Handles back: closes the search, then goes up a level, then leaves selection mode. */
    fun onBack(): Boolean {
        val state = _state.value
        when {
            state.isSearchActive -> closeSearch()
            !state.isAtRoot -> navigateTo(File(state.currentPath).parent ?: rootPath)
            state.isSelectionMode -> exitSelectionMode()
            else -> return false
        }
        return true
    }

    // endregion

    // region search & sort & filters

    fun openSearch() = _state.update { it.copy(isSearchActive = true) }

    fun closeSearch() = _state.update { it.copy(isSearchActive = false, query = "") }

    fun onQueryChange(query: String) = _state.update { it.copy(query = query) }

    fun onSortSelected(option: SortOption) = updateAndSave { state ->
        val ascending = if (option == state.sortOption) !state.ascending else true
        state.copy(
            sortOption = option,
            ascending = ascending,
            items = sortItems(state.items, option, ascending),
        )
    }

    fun toggleShowHidden() {
        updateAndSave { it.copy(showHidden = !it.showHidden) }
        refresh()
    }

    fun toggleHideEmptyFolders() {
        updateAndSave { it.copy(hideEmptyFolders = !it.hideEmptyFolders) }
        refresh()
    }

    // endregion

    // region selection

    fun enterSelectionMode(initial: FileItem? = null) = updateAndSave { state ->
        val selection = if (initial != null && !state.selection.isSelected(initial)) {
            state.selection.toggle(initial)
        } else state.selection
        state.copy(isSelectionMode = true, selection = selection)
    }

    /** Leaving selection mode clears the selections of every folder. */
    fun exitSelectionMode() = updateAndSave { it.copy(isSelectionMode = false, selection = Selection()) }

    fun toggleSelection(item: FileItem) = updateAndSave { it.copy(selection = it.selection.toggle(item)) }

    fun toggleSelectAll() = updateAndSave { it.copy(selection = it.selection.toggleAll(it.visibleItems)) }

    // endregion

    // region dialogs

    fun showDetails(item: FileItem) = _state.update { it.copy(dialog = BrowserDialog.Details(item)) }

    fun requestDeleteSelected() {
        val selected = _state.value.selectedHere
        if (selected.isNotEmpty()) _state.update { it.copy(dialog = BrowserDialog.ConfirmDelete(selected)) }
    }

    fun requestDelete(item: FileItem) = _state.update { it.copy(dialog = BrowserDialog.ConfirmDelete(listOf(item))) }

    fun requestRename(item: FileItem) = _state.update { it.copy(dialog = BrowserDialog.Rename(item)) }

    fun requestCreateFolder(parentPath: String) =
        _state.update { it.copy(dialog = BrowserDialog.CreateFolder(parentPath)) }

    fun dismissDialog() = _state.update { it.copy(dialog = null) }

    // endregion

    // region delete & undo

    fun confirmDelete(items: List<FileItem>) {
        dismissDialog()
        viewModelScope.launch {
            val batch = withContext(io) { trash.moveToTrash(items.map { File(it.path) }) }
            updateAndSave { it.copy(selection = it.selection.remove(batch.deletedPaths)) }
            refresh()
            _messages.send(BrowserMessage.Deleted(batch))
        }
    }

    fun undoDelete(batch: TrashBatch) {
        viewModelScope.launch {
            val notRestored = withContext(io) { trash.restore(batch) }
            refresh()
            _messages.send(BrowserMessage.Restored(batch.entries.size - notRestored.size, notRestored.size))
        }
    }

    /** Called once the undo window of [batch] is over. */
    fun finalizeDelete(batch: TrashBatch) {
        viewModelScope.launch(io) { trash.purge(batch) }
    }

    // endregion

    // region rename & create folder

    fun rename(item: FileItem, newName: String) {
        dismissDialog()
        viewModelScope.launch {
            val result = withContext(io) { FileOperations.rename(File(item.path), newName) }
            if (result is NameResult.Done) {
                // Keep the renamed file selected if it was selected before.
                updateAndSave { state ->
                    if (!state.selection.isSelected(item)) state
                    else state.copy(selection = Selection(state.selection.paths - item.path + result.file.absolutePath))
                }
            }
            handleNameResult(result)
            refresh()
        }
    }

    fun createFolder(parentPath: String, name: String) {
        dismissDialog()
        viewModelScope.launch {
            val result = withContext(io) { FileOperations.createFolder(File(parentPath), name) }
            handleNameResult(result)
            refresh()
            // While picking a move destination, open the new folder right away.
            if (_state.value.move != null && result is NameResult.Done) loadMoveFolders(result.file.absolutePath)
        }
    }

    private suspend fun handleNameResult(result: NameResult) {
        when (result) {
            is NameResult.Done -> Unit
            NameResult.AlreadyExists -> _messages.send(BrowserMessage.NameExists)
            NameResult.InvalidName -> _messages.send(BrowserMessage.NameInvalid)
            NameResult.Failed -> _messages.send(BrowserMessage.OperationFailed)
        }
    }

    // endregion

    // region move

    fun startMove(item: FileItem) {
        if (item.isDirectory) return
        dismissDialog()
        updateAndSave { it.copy(move = MoveState(source = item, currentPath = rootPath)) }
        loadMoveFolders(rootPath)
    }

    fun cancelMove() {
        moveLoadJob?.cancel()
        updateAndSave { it.copy(move = null) }
    }

    fun moveNavigateTo(path: String) {
        if (path.startsWith(rootPath)) loadMoveFolders(path)
    }

    /** Back inside the move screen: up one level, or cancel at the root. */
    fun onMoveBack() {
        val move = _state.value.move ?: return
        if (move.currentPath == rootPath) cancelMove()
        else loadMoveFolders(File(move.currentPath).parent ?: rootPath)
    }

    fun confirmMove(policy: ConflictPolicy = ConflictPolicy.FAIL) {
        val move = _state.value.move ?: return
        dismissDialog()
        viewModelScope.launch {
            val destination = File(move.currentPath)
            val result = withContext(io) { FileOperations.move(File(move.source.path), destination, policy) }
            when (result) {
                is MoveResult.Moved -> {
                    updateAndSave { it.copy(move = null, selection = it.selection.remove(listOf(move.source.path))) }
                    refresh()
                    _messages.send(BrowserMessage.Moved(move.source.name, destination.name.ifEmpty { "/" }))
                }
                is MoveResult.Conflict -> _state.update {
                    it.copy(dialog = BrowserDialog.MoveConflict(move.source, move.currentPath))
                }
                MoveResult.SameLocation -> _messages.send(BrowserMessage.MoveSameLocation)
                is MoveResult.Failed -> _messages.send(BrowserMessage.MoveFailed(move.source.name))
            }
        }
    }

    private fun loadMoveFolders(path: String) {
        moveLoadJob?.cancel()
        updateAndSave { state ->
            state.copy(move = state.move?.copy(currentPath = path, isLoading = true, folders = emptyList()))
        }
        moveLoadJob = viewModelScope.launch {
            val showHidden = _state.value.showHidden
            val folders = withContext(io) {
                FileOperations.listDirectory(
                    File(path),
                    showHidden = showHidden,
                    hideEmptyFolders = false,
                    excluded = setOf(trash.directory.absolutePath),
                ).filter { it.isDirectory }
            }
            _state.update { state ->
                val move = state.move ?: return@update state
                if (move.currentPath != path) return@update state
                state.copy(move = move.copy(folders = sortItems(folders, SortOption.NAME, true), isLoading = false))
            }
        }
    }

    // endregion

    // region saved state

    private fun restoreState(): BrowserUiState {
        val current = savedState.get<String>(KEY_PATH)?.takeIf { it.startsWith(rootPath) } ?: rootPath
        val moveSource = savedState.get<String>(KEY_MOVE_SOURCE)?.let(::File)?.takeIf { it.isFile }
        val movePath = savedState.get<String>(KEY_MOVE_PATH)?.takeIf { it.startsWith(rootPath) } ?: rootPath
        return BrowserUiState(
            rootPath = rootPath,
            currentPath = current,
            selection = Selection(savedState.get<ArrayList<String>>(KEY_SELECTION)?.toSet().orEmpty()),
            isSelectionMode = savedState[KEY_SELECTION_MODE] ?: false,
            sortOption = savedState.get<String>(KEY_SORT)
                ?.let { runCatching { SortOption.valueOf(it) }.getOrNull() } ?: SortOption.NAME,
            ascending = savedState[KEY_ASCENDING] ?: true,
            showHidden = savedState[KEY_SHOW_HIDDEN] ?: false,
            hideEmptyFolders = savedState[KEY_HIDE_EMPTY] ?: false,
            move = moveSource?.let { MoveState(FileItem.from(it), movePath) },
        )
    }

    private inline fun updateAndSave(transform: (BrowserUiState) -> BrowserUiState) {
        _state.update(transform)
        val state = _state.value
        savedState[KEY_PATH] = state.currentPath
        savedState[KEY_SELECTION] = ArrayList(state.selection.paths)
        savedState[KEY_SELECTION_MODE] = state.isSelectionMode
        savedState[KEY_SORT] = state.sortOption.name
        savedState[KEY_ASCENDING] = state.ascending
        savedState[KEY_SHOW_HIDDEN] = state.showHidden
        savedState[KEY_HIDE_EMPTY] = state.hideEmptyFolders
        savedState[KEY_MOVE_SOURCE] = state.move?.source?.path
        savedState[KEY_MOVE_PATH] = state.move?.currentPath
    }

    // endregion

    companion object {
        const val TRASH_FOLDER = ".QuickDeleterTrash"

        private const val KEY_PATH = "path"
        private const val KEY_SELECTION = "selection"
        private const val KEY_SELECTION_MODE = "selection_mode"
        private const val KEY_SORT = "sort"
        private const val KEY_ASCENDING = "ascending"
        private const val KEY_SHOW_HIDDEN = "show_hidden"
        private const val KEY_HIDE_EMPTY = "hide_empty"
        private const val KEY_MOVE_SOURCE = "move_source"
        private const val KEY_MOVE_PATH = "move_path"
    }
}
