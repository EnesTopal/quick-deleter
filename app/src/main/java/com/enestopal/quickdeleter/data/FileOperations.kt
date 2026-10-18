package com.enestopal.quickdeleter.data

import java.io.File
import java.io.IOException

enum class ConflictPolicy { FAIL, REPLACE, KEEP_BOTH }

sealed interface MoveResult {
    data class Moved(val target: File) : MoveResult
    /** A file with the same name already exists in the destination. */
    data class Conflict(val existing: File) : MoveResult
    data object SameLocation : MoveResult
    data class Failed(val reason: String) : MoveResult
}

sealed interface NameResult {
    data class Done(val file: File) : NameResult
    data object InvalidName : NameResult
    data object AlreadyExists : NameResult
    data object Failed : NameResult
}

/** Plain java.io operations. Everything here blocks and must run off the main thread. */
object FileOperations {

    fun listDirectory(
        directory: File,
        showHidden: Boolean,
        hideEmptyFolders: Boolean,
        excluded: Set<String> = emptySet(),
    ): List<FileItem> {
        val children = directory.listFiles() ?: return emptyList()
        return children.asSequence()
            .filter { it.absolutePath !in excluded }
            .map(FileItem::from)
            .filter { showHidden || !it.isHidden }
            .filter { !(hideEmptyFolders && it.isDirectory && it.childCount == 0) }
            .toList()
    }

    /**
     * Moves the single file [source] into [destinationDir].
     *
     * Unlike [File.renameTo], an existing file in the destination is never overwritten silently:
     * with [ConflictPolicy.FAIL] a [MoveResult.Conflict] is returned so the caller can ask the
     * user. When a plain rename is not possible (e.g. another storage volume) the file is
     * copied, verified and only then is the source deleted.
     */
    fun move(source: File, destinationDir: File, policy: ConflictPolicy = ConflictPolicy.FAIL): MoveResult {
        if (!source.isFile) return MoveResult.Failed("Source is not a file")
        if (!destinationDir.isDirectory) return MoveResult.Failed("Destination is not a folder")
        if (source.absoluteFile.parentFile == destinationDir.absoluteFile) return MoveResult.SameLocation

        var target = File(destinationDir, source.name)
        if (target.exists()) {
            when (policy) {
                ConflictPolicy.FAIL -> return MoveResult.Conflict(target)
                ConflictPolicy.KEEP_BOTH -> target = uniqueTarget(destinationDir, source.name)
                ConflictPolicy.REPLACE -> {
                    if (target.isDirectory) return MoveResult.Failed("A folder with this name exists")
                    if (!target.delete()) return MoveResult.Failed("Could not replace existing file")
                }
            }
        }

        if (source.renameTo(target)) return MoveResult.Moved(target)

        // Rename does not work across mount points: fall back to copy + verify + delete.
        return try {
            source.copyTo(target, overwrite = false)
            if (target.length() != source.length()) {
                target.delete()
                MoveResult.Failed("Copy verification failed")
            } else if (!source.delete()) {
                target.delete()
                MoveResult.Failed("Could not remove the original file")
            } else {
                MoveResult.Moved(target)
            }
        } catch (e: IOException) {
            target.delete()
            MoveResult.Failed(e.message ?: "I/O error")
        }
    }

    fun rename(file: File, newName: String): NameResult {
        val name = newName.trim()
        if (!isValidName(name)) return NameResult.InvalidName
        if (name == file.name) return NameResult.Done(file)
        val target = File(file.parentFile, name)
        if (target.exists()) return NameResult.AlreadyExists
        return if (file.renameTo(target)) NameResult.Done(target) else NameResult.Failed
    }

    fun createFolder(parent: File, name: String): NameResult {
        val trimmed = name.trim()
        if (!isValidName(trimmed)) return NameResult.InvalidName
        val folder = File(parent, trimmed)
        if (folder.exists()) return NameResult.AlreadyExists
        return if (folder.mkdir()) NameResult.Done(folder) else NameResult.Failed
    }

    fun isValidName(name: String): Boolean =
        name.isNotBlank() && name != "." && name != ".." && name.none { it in INVALID_CHARS }

    /** Returns `name (1).ext`, `name (2).ext`, … — the first one not present in [dir]. */
    fun uniqueTarget(dir: File, name: String): File {
        val dot = name.lastIndexOf('.')
        val hasExtension = dot > 0
        val stem = if (hasExtension) name.substring(0, dot) else name
        val ext = if (hasExtension) name.substring(dot) else ""
        var index = 1
        var candidate: File
        do {
            candidate = File(dir, "$stem ($index)$ext")
            index++
        } while (candidate.exists())
        return candidate
    }

    fun uniqueTargetOrSame(dir: File, name: String): File =
        File(dir, name).takeIf { !it.exists() } ?: uniqueTarget(dir, name)

    private val INVALID_CHARS = charArrayOf('/', '\u0000', '\\', ':', '*', '?', '"', '<', '>', '|')
}
