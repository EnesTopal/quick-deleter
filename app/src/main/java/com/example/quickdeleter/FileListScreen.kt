package com.example.quickdeleter

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.provider.Settings
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.PermissionChecker
import coil.compose.rememberAsyncImagePainter
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FileListScreen() {
    val context = LocalContext.current as Activity
    val fileList = remember { mutableStateListOf<File>() }
    val selectedFiles = remember { mutableStateListOf<File>() }
    var showConfirmation by remember { mutableStateOf(false) }

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
        if (isGranted) {
            loadFiles(context, fileList)
        }
    }

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (!Environment.isExternalStorageManager()) {
                val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
                intent.data = Uri.parse("package:" + context.packageName)
                context.startActivity(intent)
            } else {
                loadFiles(context, fileList)
            }
        } else {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_EXTERNAL_STORAGE) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                launcher.launch(Manifest.permission.READ_EXTERNAL_STORAGE)
            } else {
                loadFiles(context, fileList)
            }
        }
    }

    if (showConfirmation) {
        ConfirmDeletionScreen(selectedFiles) {
            selectedFiles.forEach { it.delete() }
            fileList.removeAll(selectedFiles)
            selectedFiles.clear()
            showConfirmation = false
        }
    } else {
        Scaffold(
            floatingActionButton = {
                if (selectedFiles.isNotEmpty()) {
                    FloatingActionButton(onClick = { showConfirmation = true }) {
                        Text("Tamamla")
                    }
                }
            },
            topBar = {
                TopAppBar(
                    title = { Text("Dosya Silici") },
                    actions = {
                        IconButton(onClick = {}) {
                            Icon(Icons.Default.Delete, contentDescription = "Silme Modu")
                        }
                    }
                )
            }
        ) { padding ->
            LazyColumn(modifier = Modifier.padding(padding)) {
                items(fileList) { file ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                if (selectedFiles.contains(file)) selectedFiles.remove(file)
                                else selectedFiles.add(file)
                            }
                            .padding(8.dp)
                    ) {
                        if (file.extension.lowercase() in listOf("jpg", "png", "jpeg")) {
                            Image(
                                painter = rememberAsyncImagePainter(file),
                                contentDescription = null,
                                modifier = Modifier.size(64.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(file.name)
                        if (selectedFiles.contains(file)) Text(" ✓", style = MaterialTheme.typography.titleLarge)
                    }
                }
            }
        }
    }
}

@Composable
fun ConfirmDeletionScreen(files: List<File>, onConfirm: () -> Unit) {
    AlertDialog(
        onDismissRequest = {},
        confirmButton = {
            Button(onClick = onConfirm) { Text("Evet, Sil") }
        },
        dismissButton = {
            Button(onClick = {}) { Text("İptal") }
        },
        title = { Text("Silme Onayı") },
        text = {
            Column {
                Text("Seçilen dosyaları silmek istediğinize emin misiniz?")
                LazyColumn {
                    items(files) { file ->
                        Row(modifier = Modifier.padding(4.dp)) {
                            if (file.extension.lowercase() in listOf("jpg", "png", "jpeg")) {
                                Image(
                                    painter = rememberAsyncImagePainter(file),
                                    contentDescription = null,
                                    modifier = Modifier.size(48.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                            }
                            Text(file.name)
                        }
                    }
                }
            }
        }
    )
}

//fun loadFiles(context: Context, fileList: MutableList<File>) {
//    Log.e("QuickDeleter", "Loading files...")
//    val dir = Environment.getExternalStorageDirectory() // veya context.getExternalFilesDir(null)
//    Log.d("QuickDeleter", "Scanning: ${dir.absolutePath}")
//
//    val files = dir.listFiles()?.filter { it.isFile } ?: emptyList()
//    Log.d("QuickDeleter", "Found ${files.size} files")
//
//    fileList.clear()
//    fileList.addAll(files)
//}
fun loadFiles(context: Context, fileList: MutableList<File>) {
    val downloadDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
    Log.d("QuickDeleter", "Scanning: ${downloadDir.absolutePath}")

    val files = downloadDir.listFiles()?.filter { it.isFile } ?: emptyList()
    Log.d("QuickDeleter", "Found ${files.size} files")

    fileList.clear()
    fileList.addAll(files)
}


