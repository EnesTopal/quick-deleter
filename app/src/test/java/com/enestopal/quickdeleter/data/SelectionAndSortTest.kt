package com.enestopal.quickdeleter.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SelectionAndSortTest {

    private fun item(
        path: String,
        isDirectory: Boolean = false,
        size: Long = 0,
        modified: Long = 0,
        children: Int = 0,
    ) = FileItem(
        path = path,
        name = path.substringAfterLast('/'),
        parentPath = path.substringBeforeLast('/'),
        isDirectory = isDirectory,
        size = size,
        lastModified = modified,
        childCount = children,
        type = if (isDirectory) FileType.FOLDER else FileType.fromName(path),
    )

    private val folderA = listOf(item("/s/A/1.jpg"), item("/s/A/2.jpg"), item("/s/A/sub", isDirectory = true))
    private val folderB = listOf(item("/s/B/3.jpg"), item("/s/B/4.jpg"))

    @Test
    fun `selections of other folders are excluded but kept`() {
        // Select two files in A, then go to B.
        var selection = Selection().toggle(folderA[0]).toggle(folderA[1])

        assertEquals(0, selection.countIn(folderB))
        assertTrue(selection.inFolder(folderB).isEmpty())

        // Select and "delete" one file in B: only B's file is affected.
        selection = selection.toggle(folderB[0])
        val toDelete = selection.inFolder(folderB)
        assertEquals(listOf(folderB[0]), toDelete)
        selection = selection.remove(toDelete.map { it.path })

        // Back in A, the earlier selection is still there.
        assertEquals(listOf(folderA[0], folderA[1]), selection.inFolder(folderA))
    }

    @Test
    fun `folders cannot be selected`() {
        assertTrue(Selection().toggle(folderA[2]).isEmpty)
    }

    @Test
    fun `toggle all selects files only and clears on second call`() {
        val all = Selection().toggleAll(folderA)
        assertEquals(setOf("/s/A/1.jpg", "/s/A/2.jpg"), all.paths)
        assertTrue(all.toggleAll(folderA).isEmpty)
    }

    @Test
    fun `prune drops vanished files of that folder only`() {
        val selection = Selection(setOf("/s/A/1.jpg", "/s/A/gone.jpg", "/s/B/3.jpg"))
        val pruned = selection.pruneFolder("/s/A", folderA)
        assertEquals(setOf("/s/A/1.jpg", "/s/B/3.jpg"), pruned.paths)
    }

    @Test
    fun `folders always come first`() {
        val items = listOf(
            item("/s/b.txt", size = 10),
            item("/s/z", isDirectory = true, children = 1),
            item("/s/a.txt", size = 99),
            item("/s/c", isDirectory = true, children = 5),
        )
        for (option in SortOption.entries) {
            for (ascending in listOf(true, false)) {
                val sorted = sortItems(items, option, ascending)
                assertTrue(sorted.take(2).all { it.isDirectory })
            }
        }
        assertEquals(listOf("c", "z", "a.txt", "b.txt"), sortItems(items, SortOption.NAME, true).map { it.name })
        assertEquals(listOf("c", "z", "a.txt", "b.txt"), sortItems(items, SortOption.SIZE, false).map { it.name })
    }

    @Test
    fun `date sort and name tie break`() {
        val items = listOf(item("/s/b", modified = 1), item("/s/a", modified = 1), item("/s/c", modified = 5))
        assertEquals(listOf("a", "b", "c"), sortItems(items, SortOption.DATE, true).map { it.name })
        assertEquals(listOf("c", "b", "a"), sortItems(items, SortOption.DATE, false).map { it.name })
    }

    @Test
    fun `filter is case insensitive`() {
        val items = listOf(item("/s/Holiday.JPG"), item("/s/notes.txt"))
        assertEquals(listOf("Holiday.JPG"), filterItems(items, " holi ").map { it.name })
        assertEquals(items, filterItems(items, ""))
    }
}
