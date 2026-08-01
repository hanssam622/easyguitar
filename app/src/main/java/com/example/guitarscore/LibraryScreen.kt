package com.example.guitarscore

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PostAdd
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import com.example.guitarscore.data.FolderEntity
import com.example.guitarscore.data.ScoreEntity

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(state: MainUiState, viewModel: MainViewModel) {
    val context = LocalContext.current
    var addFolderVisible by remember { mutableStateOf(false) }
    var folderToRename by remember { mutableStateOf<FolderEntity?>(null) }
    var scoreToEdit by remember { mutableStateOf<ScoreEntity?>(null) }
    var scoreToDelete by remember { mutableStateOf<ScoreEntity?>(null) }
    val pdfPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { viewModel.importPdf(context, it, state.selectedFolderId) }
    }
    val visibleScores = remember(
        state.scores,
        state.selectedFolderId,
        state.favoritesOnly,
        state.searchQuery
    ) {
        state.scores.filter { score ->
            val inLocation = when {
                state.favoritesOnly -> score.favorite
                state.selectedFolderId != null -> score.folderId == state.selectedFolderId
                else -> true
            }
            val query = state.searchQuery.trim()
            inLocation && (query.isBlank() || score.title.contains(query, true) || score.artist.contains(query, true))
        }
    }
    val locationTitle = when {
        state.favoritesOnly -> "즐겨찾기"
        state.selectedFolderId != null -> state.folders.firstOrNull { it.id == state.selectedFolderId }?.name ?: "폴더"
        else -> "모든 악보"
    }

    LaunchedEffect(state.scores.map { it.id }) {
        viewModel.ensureThumbnails(context)
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(locationTitle, fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = viewModel::toggleSidebar) {
                        Icon(
                            Icons.Default.Menu,
                            contentDescription = if (state.sidebarVisible) "사이드바 접기" else "사이드바 펼치기"
                        )
                    }
                },
                actions = {
                    OutlinedTextField(
                        value = state.searchQuery,
                        onValueChange = viewModel::setSearchQuery,
                        modifier = Modifier.width(300.dp).padding(vertical = 4.dp),
                        singleLine = true,
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                        placeholder = { Text("악보 검색") }
                    )
                    Spacer(Modifier.width(8.dp))
                    // 아이콘만으로는 무슨 기능인지 알기 어려워 글자를 함께 둔다.
                    Button(
                        onClick = { pdfPicker.launch(arrayOf("application/pdf")) },
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Icon(Icons.Default.PostAdd, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text("악보 추가")
                    }
                    Spacer(Modifier.width(8.dp))
                }
            )
        }
    ) { padding ->
        Row(
            Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(padding)
        ) {
            AnimatedVisibility(
                visible = state.sidebarVisible,
                enter = expandHorizontally() + fadeIn(),
                exit = shrinkHorizontally() + fadeOut()
            ) {
                LibrarySidebar(
                    state = state,
                    onAll = viewModel::selectAllScores,
                    onFavorites = viewModel::selectFavorites,
                    onFolder = viewModel::selectFolder,
                    onAddFolder = { addFolderVisible = true },
                    onRenameFolder = { folderToRename = it },
                    onDeleteFolder = viewModel::deleteFolder,
                    onChords = viewModel::showChordTrainer,
                    onTuner = viewModel::toggleTuner,
                    onImport = { pdfPicker.launch(arrayOf("application/pdf")) }
                )
            }
            Box(Modifier.fillMaxSize()) {
                if (visibleScores.isEmpty()) {
                    EmptyLibraryState(
                        hasQuery = state.searchQuery.isNotBlank(),
                        onImport = { pdfPicker.launch(arrayOf("application/pdf")) },
                        modifier = Modifier.align(Alignment.Center)
                    )
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(190.dp),
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(22.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalArrangement = Arrangement.spacedBy(18.dp)
                    ) {
                        items(visibleScores, key = { it.id }) { score ->
                            ScoreThumbnailCard(
                                score = score,
                                thumbnail = state.thumbnails[score.id],
                                onOpen = { viewModel.openScore(context, score.id) },
                                onEdit = { scoreToEdit = score },
                                onFavorite = { viewModel.toggleScoreFavorite(score) },
                                onDelete = { scoreToDelete = score }
                            )
                        }
                    }
                }
            }
        }
    }

    if (addFolderVisible) {
        NameDialog(
            title = "새 폴더",
            initialValue = "",
            onDismiss = { addFolderVisible = false },
            onConfirm = {
                viewModel.addFolder(it)
                addFolderVisible = false
            }
        )
    }
    folderToRename?.let { folder ->
        NameDialog(
            title = "폴더 이름 편집",
            initialValue = folder.name,
            onDismiss = { folderToRename = null },
            onConfirm = {
                viewModel.renameFolder(folder, it)
                folderToRename = null
            }
        )
    }
    scoreToEdit?.let { score ->
        ScoreEditDialog(
            score = score,
            folders = state.folders,
            onDismiss = { scoreToEdit = null },
            onConfirm = { title, artist, folderId ->
                viewModel.updateScoreDetails(score, title, artist, folderId)
                scoreToEdit = null
            }
        )
    }
    scoreToDelete?.let { score ->
        AlertDialog(
            onDismissRequest = { scoreToDelete = null },
            title = { Text("악보 삭제") },
            text = { Text("'${score.title}' 을(를) 목록에서 지웁니다. 기록해 둔 넘김 큐와 코드도 함께 삭제됩니다. 원본 PDF 파일은 지워지지 않습니다.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteScore(context, score)
                    scoreToDelete = null
                }) { Text("삭제") }
            },
            dismissButton = { TextButton(onClick = { scoreToDelete = null }) { Text("취소") } }
        )
    }
    if (state.tunerVisible) {
        TunerOverlay(onDismiss = viewModel::toggleTuner)
    }
}

@Composable
private fun LibrarySidebar(
    state: MainUiState,
    onAll: () -> Unit,
    onFavorites: () -> Unit,
    onFolder: (Long) -> Unit,
    onAddFolder: () -> Unit,
    onRenameFolder: (FolderEntity) -> Unit,
    onDeleteFolder: (Long) -> Unit,
    onChords: () -> Unit,
    onTuner: () -> Unit,
    onImport: () -> Unit
) {
    var folderMenuId by remember { mutableStateOf<Long?>(null) }
    var folderToDelete by remember { mutableStateOf<FolderEntity?>(null) }
    Surface(
        modifier = Modifier.width(230.dp).fillMaxHeight(),
        color = Color(0xFF202532),
        contentColor = Color.White
    ) {
        Column(Modifier.fillMaxSize().padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(Modifier.padding(horizontal = 10.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                Surface(color = Color(0xFF3559D9), shape = RoundedCornerShape(6.dp)) {
                    Icon(Icons.Default.MusicNote, contentDescription = null, modifier = Modifier.padding(8.dp))
                }
                Spacer(Modifier.width(10.dp))
                Text("EasyGuitar", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
            SidebarItem(
                text = "모든 악보",
                count = state.scores.size,
                selected = !state.favoritesOnly && state.selectedFolderId == null,
                icon = { Icon(Icons.Default.GridView, contentDescription = null) },
                onClick = onAll
            )
            SidebarItem(
                text = "즐겨찾기",
                count = state.scores.count { it.favorite },
                selected = state.favoritesOnly,
                icon = { Icon(Icons.Default.Star, contentDescription = null) },
                onClick = onFavorites
            )
            SidebarItem(
                text = "코드 학습",
                selected = false,
                icon = { Icon(Icons.Default.School, contentDescription = null) },
                onClick = onChords
            )
            HorizontalDivider(Modifier.padding(vertical = 10.dp), color = Color(0xFF3B4251))
            Row(Modifier.fillMaxWidth().padding(start = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("폴더", style = MaterialTheme.typography.labelLarge, color = Color(0xFFB7BDCA), modifier = Modifier.weight(1f))
                IconButton(onClick = onAddFolder, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Default.Add, contentDescription = "폴더 추가")
                }
            }
            Column(Modifier.weight(1f)) {
                state.folders.forEach { folder ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(5.dp))
                            .background(if (state.selectedFolderId == folder.id) Color(0xFF39445D) else Color.Transparent)
                            .clickable { onFolder(folder.id) }
                            .padding(start = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            if (state.selectedFolderId == folder.id) Icons.Default.FolderOpen else Icons.Default.Folder,
                            contentDescription = null,
                            tint = if (state.selectedFolderId == folder.id) Color(0xFF8EA7FF) else Color(0xFFB7BDCA)
                        )
                        Text(folder.name, modifier = Modifier.padding(start = 10.dp).weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Box {
                            IconButton(onClick = { folderMenuId = folder.id }, modifier = Modifier.size(36.dp)) {
                                Icon(Icons.Default.MoreVert, contentDescription = "폴더 메뉴", modifier = Modifier.size(17.dp))
                            }
                            DropdownMenu(
                                expanded = folderMenuId == folder.id,
                                onDismissRequest = { folderMenuId = null }
                            ) {
                                DropdownMenuItem(
                                    text = { Text("이름 편집") },
                                    onClick = {
                                        folderMenuId = null
                                        onRenameFolder(folder)
                                    },
                                    leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) }
                                )
                                DropdownMenuItem(
                                    text = { Text("폴더 삭제") },
                                    onClick = {
                                        folderMenuId = null
                                        folderToDelete = folder
                                    },
                                    leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null) }
                                )
                            }
                        }
                    }
                }
            }
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onImport, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(6.dp)) {
                    Icon(Icons.Default.PostAdd, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("PDF 악보 추가")
                }
                // 아이콘만 있던 원형 버튼은 무슨 기능인지 알기 어려워 글자를 붙인 버튼으로 바꿨다.
                Surface(
                    modifier = Modifier.fillMaxWidth().clickable(onClick = onTuner),
                    shape = RoundedCornerShape(6.dp),
                    color = Color(0xFF2C3446),
                    contentColor = Color.White
                ) {
                    Row(
                        Modifier.padding(horizontal = 12.dp, vertical = 11.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.GraphicEq, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("기타 튜너", fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }

    folderToDelete?.let { folder ->
        AlertDialog(
            onDismissRequest = { folderToDelete = null },
            title = { Text("폴더 삭제") },
            text = { Text("'${folder.name}' 폴더를 지웁니다. 안에 있던 악보는 지워지지 않고 미분류로 이동합니다.") },
            confirmButton = {
                TextButton(onClick = {
                    onDeleteFolder(folder.id)
                    folderToDelete = null
                }) { Text("삭제") }
            },
            dismissButton = { TextButton(onClick = { folderToDelete = null }) { Text("취소") } }
        )
    }
}

@Composable
private fun SidebarItem(
    text: String,
    selected: Boolean,
    icon: @Composable () -> Unit,
    onClick: () -> Unit,
    count: Int? = null
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(5.dp))
            .background(if (selected) Color(0xFF39445D) else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        icon()
        Text(text, modifier = Modifier.padding(start = 10.dp).weight(1f), fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal)
        count?.let { Text("$it", color = Color(0xFFB7BDCA), style = MaterialTheme.typography.labelMedium) }
    }
}

@Composable
private fun ScoreThumbnailCard(
    score: ScoreEntity,
    thumbnail: android.graphics.Bitmap?,
    onOpen: () -> Unit,
    onEdit: () -> Unit,
    onFavorite: () -> Unit,
    onDelete: () -> Unit
) {
    var menuExpanded by remember { mutableStateOf(false) }
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onOpen),
        shape = RoundedCornerShape(6.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Box(Modifier.fillMaxWidth().aspectRatio(0.76f).background(Color(0xFFE2E5EC))) {
            if (thumbnail != null) {
                Image(
                    bitmap = thumbnail.asImageBitmap(),
                    contentDescription = "${score.title} 미리보기",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize().background(Color.White)
                )
            } else {
                Icon(
                    Icons.Default.MusicNote,
                    contentDescription = null,
                    tint = Color(0xFF9097A6),
                    modifier = Modifier.size(48.dp).align(Alignment.Center)
                )
            }
            Surface(
                modifier = Modifier.align(Alignment.TopEnd).padding(6.dp),
                color = Color(0xDDFFFFFF),
                shape = RoundedCornerShape(5.dp)
            ) {
                Box {
                    IconButton(onClick = { menuExpanded = true }, modifier = Modifier.size(36.dp)) {
                        Icon(Icons.Default.MoreVert, contentDescription = "악보 메뉴")
                    }
                    DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                        DropdownMenuItem(
                            text = { Text("이름 및 폴더 편집") },
                            onClick = {
                                menuExpanded = false
                                onEdit()
                            },
                            leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) }
                        )
                        DropdownMenuItem(
                            text = { Text(if (score.favorite) "즐겨찾기 해제" else "즐겨찾기") },
                            onClick = {
                                menuExpanded = false
                                onFavorite()
                            },
                            leadingIcon = {
                                Icon(if (score.favorite) Icons.Default.Star else Icons.Default.StarBorder, contentDescription = null)
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("삭제") },
                            onClick = {
                                menuExpanded = false
                                onDelete()
                            },
                            leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null) }
                        )
                    }
                }
            }
        }
        Column(Modifier.padding(12.dp)) {
            Text(score.title, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(
                score.artist.ifBlank { "PDF 악보" },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun EmptyLibraryState(hasQuery: Boolean, onImport: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Icon(if (hasQuery) Icons.Default.Search else Icons.Default.FolderOpen, contentDescription = null, modifier = Modifier.size(52.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(if (hasQuery) "검색 결과가 없습니다" else "이 폴더에 악보가 없습니다", style = MaterialTheme.typography.titleMedium)
        if (!hasQuery) TextButton(onClick = onImport) { Text("PDF 가져오기") }
    }
}

@Composable
private fun NameDialog(title: String, initialValue: String, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var value by remember(initialValue) { mutableStateOf(initialValue) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(value = value, onValueChange = { value = it }, singleLine = true, label = { Text("이름") })
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(value) }, enabled = value.isNotBlank()) { Text("저장") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("취소") } }
    )
}

@Composable
private fun ScoreEditDialog(
    score: ScoreEntity,
    folders: List<FolderEntity>,
    onDismiss: () -> Unit,
    onConfirm: (String, String, Long?) -> Unit
) {
    var title by remember(score.id) { mutableStateOf(score.title) }
    var artist by remember(score.id) { mutableStateOf(score.artist) }
    var folderId by remember(score.id) { mutableStateOf(score.folderId) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("악보 편집") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(value = title, onValueChange = { title = it }, modifier = Modifier.fillMaxWidth(), singleLine = true, label = { Text("파일 이름") })
                OutlinedTextField(value = artist, onValueChange = { artist = it }, modifier = Modifier.fillMaxWidth(), singleLine = true, label = { Text("아티스트") })
                Text("폴더", style = MaterialTheme.typography.labelLarge)
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = folderId == null, onClick = { folderId = null }, label = { Text("미분류") })
                    folders.forEach { folder ->
                        FilterChip(selected = folderId == folder.id, onClick = { folderId = folder.id }, label = { Text(folder.name) })
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(title, artist, folderId) }, enabled = title.isNotBlank()) { Text("저장") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("취소") } }
    )
}
