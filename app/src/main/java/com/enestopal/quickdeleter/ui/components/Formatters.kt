package com.enestopal.quickdeleter.ui.components

import android.text.format.Formatter
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import com.enestopal.quickdeleter.R
import com.enestopal.quickdeleter.data.FileItem
import java.text.DateFormat
import java.util.Date

@Composable
fun rememberDateFormat(style: Int = DateFormat.MEDIUM): DateFormat {
    val locale = LocalConfiguration.current.locales[0]
    return remember(locale, style) { DateFormat.getDateInstance(style, locale) }
}

@Composable
fun rememberDateTimeFormat(): DateFormat {
    val locale = LocalConfiguration.current.locales[0]
    return remember(locale) { DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT, locale) }
}

@Composable
fun formatSize(bytes: Long): String = Formatter.formatShortFileSize(LocalContext.current, bytes)

/** "12 items · 3 May 2025" for folders, "2.4 MB · 3 May 2025" for files. */
@Composable
fun fileSummary(item: FileItem, dateFormat: DateFormat): String {
    val first = if (item.isDirectory) {
        pluralStringResource(R.plurals.item_count, item.childCount, item.childCount)
    } else {
        formatSize(item.size)
    }
    return "$first  ·  ${dateFormat.format(Date(item.lastModified))}"
}
