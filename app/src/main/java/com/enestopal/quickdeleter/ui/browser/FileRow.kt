package com.enestopal.quickdeleter.ui.browser

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.enestopal.quickdeleter.R
import com.enestopal.quickdeleter.data.FileItem
import com.enestopal.quickdeleter.ui.components.FileThumbnail
import com.enestopal.quickdeleter.ui.components.fileSummary
import java.text.DateFormat

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FileRow(
    item: FileItem,
    dateFormat: DateFormat,
    isSelectionMode: Boolean,
    isSelected: Boolean,
    showMenu: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onDismissMenu: () -> Unit,
    onSelect: () -> Unit,
    onMove: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val container by animateColorAsState(
        if (isSelected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent,
        label = "rowContainer",
    )
    Box(modifier.padding(horizontal = 8.dp, vertical = 2.dp)) {
        ListItem(
            modifier = Modifier
                .clip(RoundedCornerShape(16.dp))
                .combinedClickable(onClick = onClick, onLongClick = onLongClick),
            colors = ListItemDefaults.colors(containerColor = container),
            leadingContent = { FileThumbnail(item) },
            headlineContent = {
                Text(item.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
            },
            supportingContent = {
                Text(
                    fileSummary(item, dateFormat),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            },
            trailingContent = {
                when {
                    item.isDirectory -> Icon(
                        Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    else -> AnimatedVisibility(visible = isSelectionMode) {
                        Checkbox(checked = isSelected, onCheckedChange = { onClick() })
                    }
                }
            },
        )

        DropdownMenu(expanded = showMenu, onDismissRequest = onDismissMenu) {
            if (!isSelectionMode) {
                DropdownMenuItem(text = { Text(stringResource(R.string.action_select)) }, onClick = onSelect)
            }
            DropdownMenuItem(text = { Text(stringResource(R.string.action_move)) }, onClick = onMove)
            DropdownMenuItem(text = { Text(stringResource(R.string.action_rename)) }, onClick = onRename)
            DropdownMenuItem(
                text = { Text(stringResource(R.string.action_delete), color = MaterialTheme.colorScheme.error) },
                onClick = onDelete,
            )
        }
    }
}
