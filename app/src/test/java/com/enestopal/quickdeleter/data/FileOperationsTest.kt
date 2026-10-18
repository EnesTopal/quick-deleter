package com.enestopal.quickdeleter.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class FileOperationsTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private fun file(dir: File, name: String, content: String = name): File =
        File(dir, name).apply { writeText(content) }

    @Test
    fun `move puts file into destination`() {
        val src = tmp.newFolder("src")
        val dest = tmp.newFolder("dest")
        val a = file(src, "a.txt")

        val result = FileOperations.move(a, dest)

        assertTrue(result is MoveResult.Moved)
        assertFalse(a.exists())
        assertEquals("a.txt", File(dest, "a.txt").readText())
    }

    @Test
    fun `move never overwrites an existing file silently`() {
        val src = tmp.newFolder("src")
        val dest = tmp.newFolder("dest")
        val a = file(src, "a.txt", "new")
        file(dest, "a.txt", "old")

        val result = FileOperations.move(a, dest)

        assertTrue(result is MoveResult.Conflict)
        assertTrue(a.exists())
        assertEquals("old", File(dest, "a.txt").readText())
    }

    @Test
    fun `move with keep both picks a free name`() {
        val src = tmp.newFolder("src")
        val dest = tmp.newFolder("dest")
        val a = file(src, "a.txt", "new")
        file(dest, "a.txt", "old")
        file(dest, "a (1).txt", "older")

        val result = FileOperations.move(a, dest, ConflictPolicy.KEEP_BOTH)

        assertEquals(File(dest, "a (2).txt"), (result as MoveResult.Moved).target)
        assertEquals("new", result.target.readText())
        assertEquals("old", File(dest, "a.txt").readText())
    }

    @Test
    fun `move with replace overwrites the existing file`() {
        val src = tmp.newFolder("src")
        val dest = tmp.newFolder("dest")
        val a = file(src, "a.txt", "new")
        file(dest, "a.txt", "old")

        val result = FileOperations.move(a, dest, ConflictPolicy.REPLACE)

        assertTrue(result is MoveResult.Moved)
        assertEquals("new", File(dest, "a.txt").readText())
    }

    @Test
    fun `move into the same folder is reported`() {
        val src = tmp.newFolder("src")
        val a = file(src, "a.txt")
        assertEquals(MoveResult.SameLocation, FileOperations.move(a, src))
        assertTrue(a.exists())
    }

    @Test
    fun `move of a folder is rejected`() {
        val folder = tmp.newFolder("folder")
        val dest = tmp.newFolder("dest")
        assertTrue(FileOperations.move(folder, dest) is MoveResult.Failed)
        assertTrue(folder.isDirectory)
    }

    @Test
    fun `unique target handles names without or with leading dot`() {
        val dir = tmp.newFolder("dir")
        file(dir, "notes")
        file(dir, ".hidden")
        assertEquals("notes (1)", FileOperations.uniqueTarget(dir, "notes").name)
        assertEquals(".hidden (1)", FileOperations.uniqueTarget(dir, ".hidden").name)
        assertEquals("a.tar (1).gz", FileOperations.uniqueTarget(dir, "a.tar.gz").name)
    }

    @Test
    fun `rename validates and refuses existing names`() {
        val dir = tmp.newFolder("dir")
        val a = file(dir, "a.txt")
        file(dir, "b.txt")

        assertEquals(NameResult.InvalidName, FileOperations.rename(a, "  "))
        assertEquals(NameResult.InvalidName, FileOperations.rename(a, "x/y"))
        assertEquals(NameResult.AlreadyExists, FileOperations.rename(a, "b.txt"))
        val renamed = FileOperations.rename(a, "c.txt") as NameResult.Done
        assertEquals("c.txt", renamed.file.name)
        assertFalse(a.exists())
    }

    @Test
    fun `create folder`() {
        val dir = tmp.newFolder("dir")
        assertTrue(FileOperations.createFolder(dir, "New") is NameResult.Done)
        assertEquals(NameResult.AlreadyExists, FileOperations.createFolder(dir, "New"))
        assertEquals(NameResult.InvalidName, FileOperations.createFolder(dir, ".."))
    }

    @Test
    fun `list hides hidden files, empty folders and excluded paths`() {
        val dir = tmp.newFolder("dir")
        file(dir, "a.txt")
        file(dir, ".secret")
        File(dir, "empty").mkdir()
        File(dir, "full").mkdir()
        file(File(dir, "full"), "x")
        val trash = File(dir, ".trash").apply { mkdir() }

        val default = FileOperations.listDirectory(dir, showHidden = false, hideEmptyFolders = false)
        assertEquals(setOf("a.txt", "empty", "full"), default.map { it.name }.toSet())

        val all = FileOperations.listDirectory(
            dir, showHidden = true, hideEmptyFolders = true, excluded = setOf(trash.absolutePath)
        )
        assertEquals(setOf("a.txt", ".secret", "full"), all.map { it.name }.toSet())
        assertEquals(1, all.first { it.name == "full" }.childCount)
    }
}
