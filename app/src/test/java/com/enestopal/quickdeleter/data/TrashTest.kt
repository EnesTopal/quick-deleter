package com.enestopal.quickdeleter.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class TrashTest {

    @get:Rule
    val tmp = TemporaryFolder()

    @Test
    fun `deleted files can be restored`() {
        val dir = tmp.newFolder("dir")
        val a = File(dir, "a.txt").apply { writeText("a") }
        val b = File(dir, "b.txt").apply { writeText("b") }
        val trash = Trash(tmp.newFolder(".trash"))

        val batch = trash.moveToTrash(listOf(a, b), batchId = "1")

        assertEquals(2, batch.entries.size)
        assertTrue(batch.failed.isEmpty())
        assertFalse(a.exists())
        assertFalse(b.exists())

        assertTrue(trash.restore(batch).isEmpty())
        assertEquals("a", a.readText())
        assertEquals("b", b.readText())
        assertFalse(File(trash.directory, "1").exists())
    }

    @Test
    fun `restore does not overwrite a file created in the meantime`() {
        val dir = tmp.newFolder("dir")
        val a = File(dir, "a.txt").apply { writeText("old") }
        val trash = Trash(tmp.newFolder(".trash"))
        val batch = trash.moveToTrash(listOf(a), batchId = "1")
        a.writeText("new")

        trash.restore(batch)

        assertEquals("new", a.readText())
        assertEquals("old", File(dir, "a (1).txt").readText())
    }

    @Test
    fun `purge removes trashed files for good`() {
        val dir = tmp.newFolder("dir")
        val a = File(dir, "a.txt").apply { writeText("a") }
        val trash = Trash(tmp.newFolder(".trash"))
        val batch = trash.moveToTrash(listOf(a), batchId = "1")

        trash.purge(batch)

        assertFalse(File(batch.entries.single().trashedPath).exists())
        assertFalse(a.exists())
    }

    @Test
    fun `folders and missing files are reported as failed`() {
        val dir = tmp.newFolder("dir")
        val folder = File(dir, "folder").apply { mkdir() }
        val missing = File(dir, "missing.txt")
        val trash = Trash(tmp.newFolder(".trash"))

        val batch = trash.moveToTrash(listOf(folder, missing), batchId = "1")

        assertEquals(2, batch.failed.size)
        assertTrue(batch.deletedPaths.isEmpty())
        assertTrue(folder.isDirectory)
    }

    @Test
    fun `purgeAll clears leftovers but keeps the nomedia marker`() {
        val trashDir = tmp.newFolder(".trash")
        File(trashDir, "old/a.txt").apply { parentFile!!.mkdirs(); writeText("a") }
        File(trashDir, ".nomedia").createNewFile()

        Trash(trashDir).purgeAll()

        assertFalse(File(trashDir, "old").exists())
        assertTrue(File(trashDir, ".nomedia").exists())
    }
}
