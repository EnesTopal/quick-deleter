package com.enestopal.quickdeleter.data

import java.io.File

data class TrashEntry(val originalPath: String, val trashedPath: String)

/**
 * Result of a delete action. Files in [entries] can still be restored with [Trash.restore];
 * [permanentlyDeleted] could not be moved to the trash (e.g. another volume) and were removed
 * directly; [failed] could not be deleted at all.
 */
data class TrashBatch(
    val id: String,
    val entries: List<TrashEntry>,
    val permanentlyDeleted: List<String>,
    val failed: List<String>,
) {
    val deletedPaths: List<String> get() = entries.map { it.originalPath } + permanentlyDeleted
}

/**
 * A short-lived trash used to offer "Undo" after deleting. Files are moved into a hidden
 * folder next to the browsed storage root and purged once the undo window has passed.
 */
class Trash(val directory: File) {

    fun moveToTrash(files: List<File>, batchId: String = System.currentTimeMillis().toString()): TrashBatch {
        val batchDir = File(directory, batchId)
        val entries = mutableListOf<TrashEntry>()
        val permanent = mutableListOf<String>()
        val failed = mutableListOf<String>()
        val trashReady = batchDir.isDirectory || batchDir.mkdirs()
        // Keep trashed media out of gallery apps during the undo window.
        if (trashReady) runCatching { File(directory, ".nomedia").createNewFile() }

        for (file in files) {
            if (!file.isFile) {
                failed += file.absolutePath
                continue
            }
            val target = if (trashReady) FileOperations.uniqueTargetOrSame(batchDir, file.name) else null
            when {
                target != null && file.renameTo(target) ->
                    entries += TrashEntry(file.absolutePath, target.absolutePath)
                file.delete() -> permanent += file.absolutePath
                else -> failed += file.absolutePath
            }
        }
        if (entries.isEmpty()) batchDir.delete()
        return TrashBatch(batchId, entries, permanent, failed)
    }

    /** Moves the files of [batch] back. Returns the paths that could not be restored. */
    fun restore(batch: TrashBatch): List<String> {
        val notRestored = mutableListOf<String>()
        for (entry in batch.entries) {
            val trashed = File(entry.trashedPath)
            val original = File(entry.originalPath)
            val parent = original.parentFile
            if (parent == null || !(parent.isDirectory || parent.mkdirs())) {
                notRestored += entry.originalPath
                continue
            }
            val target = if (original.exists()) FileOperations.uniqueTarget(parent, original.name) else original
            if (!trashed.renameTo(target)) notRestored += entry.originalPath
        }
        File(directory, batch.id).deleteRecursively()
        return notRestored
    }

    fun purge(batch: TrashBatch) {
        File(directory, batch.id).deleteRecursively()
    }

    /** Removes leftovers, e.g. when the app was killed while an undo was still possible. */
    fun purgeAll() {
        directory.listFiles()?.filter { it.isDirectory }?.forEach { it.deleteRecursively() }
    }
}
