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
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.runtime.snapshots.SnapshotStateList
//import androidx.compose.material.icons.filled.ArrowDrop
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.window.Dialog


@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
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
    var isSearchActive by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var sortBy by remember { mutableStateOf("İsim") } // "İsim", "Boyut", "Tarih"
    var isAscending by remember { mutableStateOf(true) } // false = azalan (varsayılan)
    var showSortMenu by remember { mutableStateOf(false) }

    var showMoveMenuForFile by remember { mutableStateOf<File?>(null) }
    var fileToMove by remember { mutableStateOf<File?>(null) }

    fileToMove?.let { file ->
        MoveDestinationScreen(
            fileToMove = file,
            onMoveComplete = {
                selectedFiles.remove(file)
                loadFiles2(currentDir, fileList, sortBy, isAscending)
                fileToMove = null
            },
            onCancelMove = {
                fileToMove = null
                showMoveMenuForFile = null
            }
        )
        return
    }


    val launcher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
            if (isGranted) loadFiles2(currentDir, fileList, sortBy, isAscending)
        }

    LaunchedEffect(currentDir) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (!Environment.isExternalStorageManager()) {
                val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
                intent.data = Uri.parse("package:" + context.packageName)
                context.startActivity(intent)
            } else {
                loadFiles2(currentDir, fileList, sortBy, isAscending)
            }
        } else {
            if (ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.READ_EXTERNAL_STORAGE
                ) != android.content.pm.PackageManager.PERMISSION_GRANTED
            ) {
                launcher.launch(Manifest.permission.READ_EXTERNAL_STORAGE)
            } else {
                loadFiles2(currentDir, fileList, sortBy, isAscending)
            }
        }
    }

    BackHandler(enabled = currentDir != Environment.getExternalStorageDirectory()) {
        isSearchActive = false
        currentDir = currentDir.parentFile ?: currentDir
    }


    if (showPreview && previewImageFile != null) {
        Dialog(onDismissRequest = { showPreview = false }) {
            val file = previewImageFile!!
            val sizeInKB = file.length() / 1024
            val lastModified = java.text.SimpleDateFormat("dd.MM.yyyy HH:mm")
                .format(java.util.Date(file.lastModified()))

            Surface(
                shape = MaterialTheme.shapes.medium,
                tonalElevation = 8.dp,
                modifier = Modifier
                    .fillMaxWidth(0.99f)
                    .fillMaxHeight(0.8f) // boyutu burada kontrol edebilirsin
            ) {
                Column(
                    modifier = Modifier
                        .padding(16.dp)
                        .fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.SpaceEvenly
                ) {
                    Text(
                        "Dosya Adı", style = MaterialTheme.typography.titleLarge.copy(
                            textDecoration = TextDecoration.Underline
                        )
                    )
                    Text(text = file.name)
                    Image(
                        painter = rememberAsyncImagePainter(file),
                        contentDescription = "Fotoğraf Önizleme",
                        modifier = Modifier
                            .fillMaxWidth()
//                            .weight(1f)
                    )
                    Text("Boyut: ${sizeInKB} KB")
                    Text("Son Değişiklik: $lastModified")
                }
            }
        }
    }


    if (showConfirmation) {
        val filesInCurrentDir = selectedFiles.filter { it.parentFile == currentDir }
        ConfirmDeletionScreen(
            selectedFiles = filesInCurrentDir,
            onConfirm = {
                filesInCurrentDir.forEach { it.delete() }
                fileList.removeAll(filesInCurrentDir)
                selectedFiles.removeAll(filesInCurrentDir)
                showConfirmation = false
            },
            onDismiss = {
                showConfirmation = false
            }
        )


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
                    title = {
                        if (isSearchActive) {
                            TextField(
                                value = searchQuery,
                                onValueChange = { searchQuery = it },
                                placeholder = { Text("Ara...") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                        } else {
                            Text(currentDir.name.ifBlank { "Ana Dizin" })
                        }
                    },
                    navigationIcon = {
                        if (currentDir != Environment.getExternalStorageDirectory()) {
                            IconButton(onClick = {
                                currentDir = currentDir.parentFile ?: currentDir
                            }) {
                                Icon(
                                    Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = "Geri"
                                )
                            }
                        }
                    },
                    actions = {
                        IconButton(onClick = {
                            isSearchActive = !isSearchActive; if (!isSearchActive) searchQuery = ""
                        }) {
                            Icon(Icons.Default.Search, contentDescription = "Ara")
                        }
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
            val filteredList = if (searchQuery.isBlank()) fileList
            else fileList.filter { it.name.contains(searchQuery, ignoreCase = true) }
            Column(modifier = Modifier.padding(padding)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(15.dp, 0.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text("Toplam Öğe: ${filteredList.size}")

                    Box(modifier = Modifier.wrapContentSize(Alignment.TopEnd)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.clickable { showSortMenu = true }
                        ) {
                            Text("Sıralama: $sortBy")
                            Icon(
                                imageVector = if (isAscending) Icons.Default.ArrowDropDown else Icons.Default.KeyboardArrowUp,
                                contentDescription = if (isAscending) "Artan" else "Azalan"
                            )
                        }

                        DropdownMenu(
                            expanded = showSortMenu,
                            onDismissRequest = { showSortMenu = false }
                        ) {
                            listOf("İsim", "Boyut", "Tarih").forEach { option ->
                                DropdownMenuItem(
                                    text = { Text(option) },
                                    onClick = {
                                        showSortMenu = false
                                        if (option == sortBy) {
                                            isAscending = !isAscending
                                        } else {
                                            sortBy = option
                                            isAscending = false
                                        }
                                        loadFiles2(currentDir, fileList, sortBy, isAscending)
                                    }
                                )
                            }
                        }
                    }
                }

                LazyColumn() {
//                val filteredList = fileList.filter {
//                    it.name.contains(searchQuery, ignoreCase = true)
//                }
                    items(filteredList) { file ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .combinedClickable(
                                    onClick = {
                                        if (file.isDirectory) {
                                            currentDir = file
                                        } else if (isDeleteMode) {
                                            if (selectedFiles.contains(file)) selectedFiles.remove(
                                                file
                                            )
                                            else selectedFiles.add(file)
                                        } else if (file.extension.lowercase() in listOf(
                                                "jpg",
                                                "png",
                                                "jpeg"
                                            )
                                        ) {
                                            previewImageFile = file
                                            showPreview = true
                                        }
                                    },
                                    onLongClick = {
                                        if (file.isDirectory) {
                                            currentDir = file
                                        } else if (isDeleteMode) {
                                            if (selectedFiles.contains(file)) selectedFiles.remove(
                                                file
                                            )
                                            else selectedFiles.add(file)
                                        } else {
                                            showMoveMenuForFile = file
                                        }
                                    }
                                )
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
                            Column(modifier = Modifier.weight(1f)) {
                                Text(file.name)

                                val infoText = if (file.isDirectory) {
                                    val itemCount = file.listFiles()?.size ?: 0
                                    val lastModified = java.text.SimpleDateFormat("d.MM.yyyy")
                                        .format(java.util.Date(file.lastModified()))
                                    "$itemCount öge | $lastModified"
                                } else {
                                    val sizeInText = if (file.isFile) {
                                        val bytes = file.length()
                                        if (bytes >= 1024 * 1024) {
                                            val mb = bytes / 1024f / 1024f
                                            "%.1f MB".format(mb)
                                        } else {
                                            val kb = bytes / 1024f
                                            "%.1f KB".format(kb)
                                        }
                                    } else {
                                        "0 B"
                                    }
                                    val lastModified = java.text.SimpleDateFormat("d.MM.yyyy")
                                        .format(java.util.Date(file.lastModified()))
                                    "$sizeInText  |  $lastModified"
                                }

                                Text(
                                    text = infoText,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color.Gray
                                )
                            }

                            if (isDeleteMode && selectedFiles.contains(file)) {
                                Text("✓", style = MaterialTheme.typography.titleLarge)
                            }

                            DropdownMenu(
                                expanded = showMoveMenuForFile == file,
                                onDismissRequest = { showMoveMenuForFile = null }
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Move") },
                                    onClick = {
                                        fileToMove = file
                                        showMoveMenuForFile = null
                                    }
                                )
                            }


                        }

                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = 8.dp),
                            thickness = 1.2.dp,
                            color = Color.DarkGray
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ConfirmDeletionScreen(
    selectedFiles: List<File>,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit = {}
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Silme Onayı") },
        text = {
            Text("Seçilen ${selectedFiles.size} dosya kalıcı olarak silinecek. Emin misiniz?")
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text("Sil")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Vazgeç")
            }
        }
    )
}


fun loadFiles2(directory: File, fileList: MutableList<File>, sortBy: String, isAscending: Boolean) {
    Log.d("QuickDeleter", "Scanning: ${directory.absolutePath}")
    val files = directory.listFiles()?.sortedWith(compareBy({ !it.isDirectory }, { it.name }))
        ?: emptyList()
    Log.d("QuickDeleter", "Found ${files.size} files")
    val sortedFiles = sortFiles(files, sortBy, isAscending)
    fileList.clear()
    fileList.addAll(sortedFiles)
}

fun sortFiles(files: List<File>, sortBy: String, ascending: Boolean): List<File> {
    val comparator = when (sortBy) {
        "Boyut" -> compareBy<File> { it.length() }
        "Tarih" -> compareBy<File> { it.lastModified() }
        else -> compareBy<File> { it.name.lowercase() }
    }
    return if (ascending) files.sortedWith(comparator)
    else files.sortedWith(comparator.reversed())
}


//@OptIn(ExperimentalMaterial3Api::class)
//@Composable
//fun MoveDestinationScreen(
//    fileToMove: File,
//    onMoveComplete: () -> Unit
//) {
//    var currentDir by remember { mutableStateOf(Environment.getExternalStorageDirectory()) }
//    val folders = remember { mutableStateListOf<File>() }
//
//    LaunchedEffect(currentDir) {
//        val dirList = currentDir.listFiles()?.filter { it.isDirectory } ?: emptyList()
//        folders.clear()
//        folders.addAll(dirList)
//    }
//
//    BackHandler(enabled = currentDir != Environment.getExternalStorageDirectory()) {
//        currentDir = currentDir.parentFile ?: currentDir
//    }
//
//    Scaffold(
//        topBar = {
//            TopAppBar(
//                title = { Text(currentDir.name.ifBlank { "Ana Dizin" }) },
//                navigationIcon = {
//                    IconButton(onClick = {
//                        currentDir = currentDir.parentFile ?: currentDir
//                    }) {
//                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Geri")
//                    }
//                },
//                actions = {
//                    IconButton(onClick = {
//                        val target = File(currentDir, fileToMove.name)
//                        val moved = fileToMove.renameTo(target)
//                        if (moved) {
//                            onMoveComplete()
//                        } else {
//                            Log.e(
//                                "Move",
//                                "Taşıma başarısız: ${fileToMove.absolutePath} -> ${target.absolutePath}"
//                            )
//                        }
//                    }) {
//                        Icon(Icons.Default.Check, contentDescription = "Taşı")
//                    }
//                }
//            )
//        }
//    ) { padding ->
//        LazyColumn(modifier = Modifier.padding(padding)) {
//            items(folders) { folder ->
//                Row(
//                    modifier = Modifier
//                        .fillMaxWidth()
//                        .clickable { currentDir = folder }
//                        .padding(16.dp),
//                    verticalAlignment = Alignment.CenterVertically
//                ) {
//                    Icon(
//                        painter = painterResource(id = R.drawable.folder_logo),
//                        contentDescription = "Klasör",
//                        modifier = Modifier.size(32.dp),
//                        tint = Color.Unspecified
//                    )
//                    Spacer(modifier = Modifier.width(12.dp))
//                    Text(folder.name)
//                }
//            }
//        }
//    }
//}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MoveDestinationScreen(
    fileToMove: File,
    onMoveComplete: () -> Unit,
    onCancelMove: () -> Unit
) {
    var currentDir by remember { mutableStateOf(Environment.getExternalStorageDirectory()) }
    val folders = remember { mutableStateListOf<File>() }

    LaunchedEffect(currentDir) {
        val dirList = currentDir.listFiles()?.filter { it.isDirectory } ?: emptyList()
        folders.clear()
        folders.addAll(dirList)
    }

    val isAtRoot = currentDir == Environment.getExternalStorageDirectory()

    if (!isAtRoot) {
        BackHandler {
            currentDir = currentDir.parentFile ?: currentDir
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(currentDir.name.ifBlank { "Ana Dizin" }) },
                navigationIcon = {
                    if (!isAtRoot) {
                        IconButton(onClick = {
                            currentDir = currentDir.parentFile ?: currentDir
                        }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Geri")
                        }
                    }
                },
                actions = {
                    IconButton(onClick = { onCancelMove()  }) {
                        Icon(Icons.Default.Close, contentDescription = "İptal Et")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = {
                val target = File(currentDir, fileToMove.name)
                val moved = fileToMove.renameTo(target)
                if (moved) {
                    onMoveComplete()
                } else {
                    Log.e("Move", "Taşıma başarısız: ${fileToMove.absolutePath} -> ${target.absolutePath}")
                }
            }) {
                Icon(Icons.Default.Check, contentDescription = "Taşı")
            }
        }
    ) { padding ->
        LazyColumn(modifier = Modifier.padding(padding)) {
            items(folders) { folder ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { currentDir = folder }
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.folder_logo),
                        contentDescription = "Klasör",
                        modifier = Modifier.size(32.dp),
                        tint = Color.Unspecified
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(folder.name)
                }
            }
        }
    }
}

