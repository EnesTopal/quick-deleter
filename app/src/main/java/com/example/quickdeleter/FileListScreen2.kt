package com.example.quickdeleter

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import coil.compose.rememberAsyncImagePainter
import java.io.File
import androidx.activity.compose.BackHandler
import androidx.compose.material.icons.filled.Close
import androidx.compose.ui.graphics.Color


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FileListScreen2() {
    val context = LocalContext.current as Activity
    var currentDir by remember { mutableStateOf(Environment.getExternalStorageDirectory()) }
    val fileList = remember { mutableStateListOf<File>() }
    val selectedFiles = remember { mutableStateListOf<File>() }
    var showConfirmation by remember { mutableStateOf(false) }
    var isDeleteMode by remember { mutableStateOf(false) }

    var previewImageFile by remember { mutableStateOf<File?>(null) }
    var showPreview by remember { mutableStateOf(false) }

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
        if (isGranted) loadFiles2(currentDir, fileList)
    }

    LaunchedEffect(currentDir) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (!Environment.isExternalStorageManager()) {
                val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
                intent.data = Uri.parse("package:" + context.packageName)
                context.startActivity(intent)
            } else {
                loadFiles2(currentDir, fileList)
            }
        } else {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_EXTERNAL_STORAGE) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                launcher.launch(Manifest.permission.READ_EXTERNAL_STORAGE)
            } else {
                loadFiles2(currentDir, fileList)
            }
        }
    }

    BackHandler(enabled = currentDir != Environment.getExternalStorageDirectory()) {
        currentDir = currentDir.parentFile ?: currentDir
    }

    if (showPreview && previewImageFile != null) {
        AlertDialog(
            onDismissRequest = { showPreview = false },
            confirmButton = {},
            dismissButton = {
//                IconButton(onClick = { showPreview = false }) {
//                    Icon(Icons.Default.Close, contentDescription = "Kapat")
//                }
            },
            title = null,
            text = {
                val file = previewImageFile!!
                val sizeInKB = file.length() / 1024
                val lastModified = java.text.SimpleDateFormat("dd.MM.yyyy HH:mm")
                    .format(java.util.Date(file.lastModified()))

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Dosya Adı: ${file.name}")
                    Image(
                        painter = rememberAsyncImagePainter(file),
                        contentDescription = "Fotoğraf Önizleme",
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(1f)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("Boyut: ${sizeInKB} KB")
                    Text("Son Değişiklik: $lastModified")
                }
            },
            modifier = Modifier
                .padding(16.dp)
        )
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
                if (isDeleteMode && selectedFiles.isNotEmpty()) {
                    FloatingActionButton(onClick = { showConfirmation = true }) {
                        Text("Tamamla")
                    }
                }
            },
            topBar = {
                TopAppBar(
                    title = { Text(currentDir.name.ifBlank { "Ana Dizin" }) },
                    navigationIcon = {
                        if (currentDir != Environment.getExternalStorageDirectory()) {
                            IconButton(onClick = {
                                currentDir = currentDir.parentFile ?: currentDir
                            }) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Geri")
                            }
                        }
                    },
                    actions = {
                        IconButton(onClick = { isDeleteMode = !isDeleteMode }) {
                            Icon(
                                imageVector = if (isDeleteMode) Icons.Default.Close else Icons.Default.Delete,
                                contentDescription = if (isDeleteMode) "Silme Modundan Çık" else "Silme Modu"
                            )
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
                                if (file.isDirectory) {
                                    currentDir = file
                                } else if (isDeleteMode) {
                                    if (selectedFiles.contains(file)) selectedFiles.remove(file)
                                    else selectedFiles.add(file)
                                }
                                else if (file.extension.lowercase() in listOf("jpg", "png", "jpeg")) {
                                    previewImageFile = file
                                    showPreview = true
                                }
                                else {
                                    // dosya önizleme işlemi burada olacak (şu an boş)
                                }
                            }
                            .padding(8.dp)
                    ) {
                        if (file.isDirectory) {
                            Icon(
                                painter = painterResource(id = R.drawable.folder_logo),
                                contentDescription = "Klasör",
                                modifier = Modifier.size(48.dp),
                                tint = Color.Unspecified
                            )
                        } else if (file.extension.lowercase() in listOf("jpg", "png", "jpeg")) {
                            Image(
                                painter = rememberAsyncImagePainter(file),
                                contentDescription = null,
                                modifier = Modifier.size(48.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(file.name, modifier = Modifier.weight(1f))
                        if (isDeleteMode && selectedFiles.contains(file)) {
                            Text("✓", style = MaterialTheme.typography.titleLarge)
                        }
                    }
                }
            }
        }
    }
}

fun loadFiles2(directory: File, fileList: MutableList<File>) {
    Log.d("QuickDeleter", "Scanning: ${directory.absolutePath}")
    val files = directory.listFiles()?.sortedWith(compareBy({ !it.isDirectory }, { it.name })) ?: emptyList()
    Log.d("QuickDeleter", "Found ${files.size} files")
    fileList.clear()
    fileList.addAll(files)
}
