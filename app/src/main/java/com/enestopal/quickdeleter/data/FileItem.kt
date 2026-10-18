package com.enestopal.quickdeleter.data

import java.io.File

/**
 * Immutable snapshot of a file system entry. Everything the UI shows is read once on a
 * background thread so that composition never touches the disk.
 */
data class FileItem(
    val path: String,
    val name: String,
    val parentPath: String,
    val isDirectory: Boolean,
    val size: Long,
    val lastModified: Long,
    /** Number of children for directories, 0 for files. */
    val childCount: Int,
    val type: FileType,
) {
    val isHidden: Boolean get() = name.startsWith(".")
    val extension: String get() = name.substringAfterLast('.', "").lowercase()

    companion object {
        fun from(file: File): FileItem {
            val isDirectory = file.isDirectory
            return FileItem(
                path = file.absolutePath,
                name = file.name,
                parentPath = file.absoluteFile.parent.orEmpty(),
                isDirectory = isDirectory,
                size = if (isDirectory) 0L else file.length(),
                lastModified = file.lastModified(),
                childCount = if (isDirectory) file.list()?.size ?: 0 else 0,
                type = if (isDirectory) FileType.FOLDER else FileType.fromName(file.name),
            )
        }
    }
}

enum class FileType {
    FOLDER, IMAGE, VIDEO, AUDIO, DOCUMENT, ARCHIVE, APK, OTHER;

    companion object {
        private val image = setOf("jpg", "jpeg", "png", "webp", "gif", "bmp", "heic", "heif")
        private val video = setOf("mp4", "mkv", "webm", "3gp", "mov", "avi")
        private val audio = setOf("mp3", "m4a", "aac", "wav", "ogg", "opus", "flac", "amr")
        private val document = setOf(
            "pdf", "txt", "doc", "docx", "xls", "xlsx", "ppt", "pptx", "odt", "csv", "md", "rtf"
        )
        private val archive = setOf("zip", "rar", "7z", "tar", "gz", "bz2", "xz")

        fun fromName(name: String): FileType = when (name.substringAfterLast('.', "").lowercase()) {
            in image -> IMAGE
            in video -> VIDEO
            in audio -> AUDIO
            in document -> DOCUMENT
            in archive -> ARCHIVE
            "apk" -> APK
            else -> OTHER
        }
    }
}
