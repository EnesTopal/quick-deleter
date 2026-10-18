package com.enestopal.quickdeleter.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.enestopal.quickdeleter.R
import com.enestopal.quickdeleter.data.FileItem
import com.enestopal.quickdeleter.data.FileType
import com.enestopal.quickdeleter.ui.theme.FileTypeColors
import java.io.File

@Composable
fun FileThumbnail(item: FileItem, modifier: Modifier = Modifier, size: Dp = 44.dp) {
    val shape = RoundedCornerShape(10.dp)
    when (item.type) {
        FileType.FOLDER -> Box(
            modifier = modifier
                .size(size)
                .clip(shape)
                .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(R.drawable.folder_logo),
                contentDescription = stringResource(R.string.folder),
                tint = Color.Unspecified,
                modifier = Modifier.size(size * 0.72f),
            )
        }

        FileType.IMAGE -> {
            val px = with(LocalDensity.current) { size.roundToPx() }
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(File(item.path))
                    .size(px)
                    .crossfade(true)
                    .build(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = modifier
                    .size(size)
                    .clip(shape)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
            )
        }

        else -> TypeBadge(item, modifier.size(size).clip(shape))
    }
}

@Composable
private fun TypeBadge(item: FileItem, modifier: Modifier) {
    val color = when (item.type) {
        FileType.VIDEO -> FileTypeColors.Video
        FileType.AUDIO -> FileTypeColors.Audio
        FileType.DOCUMENT -> FileTypeColors.Document
        FileType.ARCHIVE -> FileTypeColors.Archive
        FileType.APK -> FileTypeColors.Apk
        FileType.IMAGE -> FileTypeColors.Image
        else -> FileTypeColors.Other
    }
    val label = item.extension.take(4).uppercase().ifEmpty { "?" }
    Box(
        modifier = modifier.background(color.copy(alpha = 0.16f)),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = color,
            fontSize = if (label.length > 3) 10.sp else 12.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
        )
    }
}
