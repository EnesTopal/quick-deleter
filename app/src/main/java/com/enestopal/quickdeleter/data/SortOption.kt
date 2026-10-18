package com.enestopal.quickdeleter.data

enum class SortOption { NAME, SIZE, DATE }

/**
 * Sorts [items] by [option]. Folders are always listed before files, regardless of the
 * direction. Folders have no meaningful byte size, so [SortOption.SIZE] orders them by their
 * item count instead. Ties fall back to the name so the order is stable.
 */
fun sortItems(items: List<FileItem>, option: SortOption, ascending: Boolean): List<FileItem> {
    val byName = compareBy<FileItem, String>(String.CASE_INSENSITIVE_ORDER) { it.name }
    val primary: Comparator<FileItem> = when (option) {
        SortOption.NAME -> byName
        SortOption.SIZE -> compareBy<FileItem> { if (it.isDirectory) it.childCount.toLong() else it.size }
            .then(byName)
        SortOption.DATE -> compareBy<FileItem> { it.lastModified }.then(byName)
    }
    val directed = if (ascending) primary else primary.reversed()
    return items.sortedWith(compareBy<FileItem> { !it.isDirectory }.then(directed))
}

fun filterItems(items: List<FileItem>, query: String): List<FileItem> =
    if (query.isBlank()) items
    else items.filter { it.name.contains(query.trim(), ignoreCase = true) }
