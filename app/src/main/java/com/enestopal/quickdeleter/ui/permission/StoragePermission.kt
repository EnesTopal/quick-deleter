package com.enestopal.quickdeleter.ui.permission

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.provider.Settings
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.enestopal.quickdeleter.R
import com.enestopal.quickdeleter.ui.components.EmptyState

object StoragePermission {
    // minSdk is 30, so "All files access" (MANAGE_EXTERNAL_STORAGE) is the only model needed.
    fun isGranted(): Boolean = Environment.isExternalStorageManager()

    fun openSettings(context: Context) {
        val appSpecific = Intent(
            Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
            Uri.parse("package:${context.packageName}"),
        )
        try {
            context.startActivity(appSpecific)
        } catch (e: ActivityNotFoundException) {
            // Some OEM builds only expose the generic list screen.
            context.startActivity(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION))
        }
    }
}

@Composable
fun PermissionScreen() {
    val context = LocalContext.current
    Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize()) {
        Box(Modifier.safeDrawingPadding()) {
            EmptyState(
                icon = rememberVectorPainter(Icons.Default.Lock),
                tint = MaterialTheme.colorScheme.primary,
                title = stringResource(R.string.permission_title),
                body = stringResource(R.string.permission_body),
                action = {
                    Button(onClick = { StoragePermission.openSettings(context) }) {
                        Text(stringResource(R.string.permission_grant))
                    }
                },
            )
        }
    }
}
