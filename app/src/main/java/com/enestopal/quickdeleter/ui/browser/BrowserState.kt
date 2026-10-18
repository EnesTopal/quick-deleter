package com.enestopal.quickdeleter.ui.browser

import com.enestopal.quickdeleter.data.FileItem
import com.enestopal.quickdeleter.data.Selection
import com.enestopal.quickdeleter.data.SortOption
import com.enestopal.quickdeleter.data.TrashBatch
import com.enestopal.quickdeleter.data.filterItems

data class BrowserUiState(
    val rootPath: String,
    val currentPath: String,
    val hasPermission: Boolean = false,
    val items: List<FileItem> = emptyList(),
    val isLoading: Boolean = false,
    val selection: Selection = Selection(),
    val isSelectionMode: Boolean = false,
    val sortOption: SortOption = SortOption.NAME,
    val ascending: Boolean = true,
    val isSearchActive: Boolean = false,
    val query: String = "",
    val showHidden: Boolean = false,
    val hideEmptyFolders: Boolean = false,
    val dialog: BrowserDialog? = null,
    val move: MoveState? = null,
) {
    val isAtRoot: Boolean get() = currentPath == rootPath

    val visibleItems: List<FileItem> = filterItems(items, query)

    /** Selected files of the folder on screen; selections in other folders are excluded. */
    val selectedHere: List<FileItem> = selection.inFolder(items)

    /** Path segments below the storage root, used for the breadcrumb bar. */
    val breadcrumbs: List<Pair<String, String>>
        get() {
            val relative = currentPath.removePrefix(rootPath).trim('/')
            if (relative.isEmpty()) return emptyList()
            var path = rootPath
            return relative.split('/').map { segment ->
                path = "$path/$segment"
                segment to path
            }
        }
}

data class MoveState(
    val source: FileItem,
    val currentPath: String,
    val folders: List<FileItem> = emptyList(),
    val isLoading: Boolean = true,
)

sealed interface BrowserDialog {
    data class ConfirmDelete(val items: List<FileItem>) : BrowserDialog
    data class Details(val item: FileItem) : BrowserDialog
    data class Rename(val item: FileItem) : BrowserDialog
    data class CreateFolder(val parentPath: String) : BrowserDialog
    data class MoveConflict(val source: FileItem, val destinationPath: String) : BrowserDialog
}

/** One-off events shown in a snackbar. */
sealed interface BrowserMessage {
    data class Deleted(val batch: TrashBatch) : BrowserMessage
    data class Restored(val count: Int, val failed: Int) : BrowserMessage
    data class Moved(val name: String, val folderName: String) : BrowserMessage
    data object MoveSameLocation : BrowserMessage
    data class MoveFailed(val name: String) : BrowserMessage
    data object NameInvalid : BrowserMessage
    data object NameExists : BrowserMessage
    data object OperationFailed : BrowserMessage
}
