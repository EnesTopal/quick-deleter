package com.enestopal.quickdeleter.data

/**
 * Selected file paths across all folders.
 *
 * Every folder keeps its own selection: actions (count, delete) only ever see the files of the
 * folder currently shown, while selections made in other folders are kept and appear again
 * when the user navigates back there.
 */
data class Selection(val paths: Set<String> = emptySet()) {

    fun inFolder(items: List<FileItem>): List<FileItem> = items.filter { it.path in paths }

    fun countIn(items: List<FileItem>): Int = items.count { it.path in paths }

    fun isSelected(item: FileItem): Boolean = item.path in paths

    fun toggle(item: FileItem): Selection =
        if (item.isDirectory) this
        else Selection(if (item.path in paths) paths - item.path else paths + item.path)

    /** Selects every file of [items], or clears them when they are all selected already. */
    fun toggleAll(items: List<FileItem>): Selection {
        val files = items.filterNot { it.isDirectory }.map { it.path }.toSet()
        return if (files.isNotEmpty() && paths.containsAll(files)) Selection(paths - files)
        else Selection(paths + files)
    }

    fun remove(removed: Collection<String>): Selection = Selection(paths - removed.toSet())

    /**
     * Drops entries of [folderPath] that are no longer present in [items] (deleted or moved
     * elsewhere). Selections of other folders are left untouched.
     */
    fun pruneFolder(folderPath: String, items: List<FileItem>): Selection {
        val existing = items.map { it.path }.toSet()
        val stale = paths.filter { parentOf(it) == folderPath && it !in existing }
        return if (stale.isEmpty()) this else Selection(paths - stale.toSet())
    }

    val isEmpty: Boolean get() = paths.isEmpty()

    private fun parentOf(path: String): String = path.substringBeforeLast('/', "")
}
