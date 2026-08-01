package com.example.guitarscore.chord

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog

private val chordFamilies = listOf<String?>(null, "A", "B", "C", "D", "E", "F", "G")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChordTrainerScreen(onBack: () -> Unit) {
    var selectedTab by remember { mutableIntStateOf(0) }
    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("코드 학습", fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "라이브러리")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(padding)
        ) {
            TabRow(selectedTabIndex = selectedTab, containerColor = MaterialTheme.colorScheme.surface) {
                Tab(selected = selectedTab == 0, onClick = { selectedTab = 0 }, text = { Text("코드 만들기") })
                Tab(selected = selectedTab == 1, onClick = { selectedTab = 1 }, text = { Text("코드 운지 찾기") })
            }
            when (selectedTab) {
                0 -> ChordBuilderScreen(Modifier.fillMaxSize().padding(20.dp))
                else -> ChordFinderScreen(Modifier.fillMaxSize().padding(20.dp))
            }
        }
    }
}

@Composable
private fun ChordBuilderScreen(modifier: Modifier = Modifier) {
    var stringFrets by remember { mutableStateOf(List(6) { -1 }) }
    val positions = remember(stringFrets) {
        stringFrets.mapIndexedNotNullTo(linkedSetOf()) { string, fret ->
            fret.takeIf { it >= 0 }?.let { FretPosition(string, it) }
        }
    }
    val interpretations = remember(positions) { analyzeChords(positions) }
    val best = interpretations.firstOrNull()
    val alternatives = interpretations.drop(1).take(3)
    Surface(modifier = modifier, color = MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(6.dp)) {
        Column(Modifier.fillMaxSize().padding(22.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("현재 코드", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        best?.name ?: "--",
                        style = MaterialTheme.typography.displayMedium,
                        fontWeight = FontWeight.Bold,
                        color = when {
                            best == null -> MaterialTheme.colorScheme.onSurface
                            best.exact -> MaterialTheme.colorScheme.primary
                            else -> MaterialTheme.colorScheme.secondary
                        }
                    )
                    // 정확히 맞아떨어지지 않을 때 왜 그런지(빠진 음/추가된 음) 보여 준다.
                    if (best != null && !best.exact) {
                        val reason = buildList {
                            if (best.missingNotes.isNotEmpty()) add("${best.missingNotes.joinToString(", ")} 없음")
                            if (best.extraNotes.isNotEmpty()) add("${best.extraNotes.joinToString(", ")} 추가됨")
                        }.joinToString(" · ")
                        Text("비슷한 코드 · $reason", color = MaterialTheme.colorScheme.secondary)
                    }
                    if (alternatives.isNotEmpty()) {
                        Text("다른 해석  ${alternatives.joinToString(" · ") { it.name }}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (positions.isNotEmpty()) {
                        Text(
                            "누른 음  ${selectedNoteSummary(positions)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                if (best != null) {
                    val voicing = remember(best.name) {
                        chordDefinition(best.name.substringBefore("/"))?.let { generateVoicings(it).firstOrNull() }
                    }
                    voicing?.let { ChordDiagram(it, Modifier.width(230.dp).height(120.dp), showStringNames = false) }
                }
                IconButton(onClick = { stringFrets = List(6) { -1 } }, enabled = positions.isNotEmpty()) {
                    Icon(Icons.AutoMirrored.Filled.Backspace, contentDescription = "운지 초기화")
                }
            }
            HorizontalDivider()
            InteractiveFretboard(
                stringFrets = stringFrets,
                onSelect = { string, fret ->
                    stringFrets = stringFrets.toMutableList().also { values ->
                        values[string] = if (values[string] == fret) -1 else fret
                    }
                },
                modifier = Modifier.fillMaxWidth().weight(1f)
            )
        }
    }
}

@Composable
private fun InteractiveFretboard(
    stringFrets: List<Int>,
    onSelect: (Int, Int) -> Unit,
    modifier: Modifier = Modifier
) {
    // 다이어그램과 같은 방향으로 1번 줄이 맨 위에 오도록 뒤집어 그린다.
    val stringRows = listOf(5 to "1  E", 4 to "2  B", 3 to "3  G", 2 to "4  D", 1 to "5  A", 0 to "6  E")
    val primaryColor = MaterialTheme.colorScheme.primary
    val cellWidth = 48.dp
    val cellHeight = 56.dp
    Column(modifier.horizontalScroll(rememberScrollState())) {
        Row(Modifier.padding(start = 58.dp)) {
            Box(Modifier.width(48.dp).height(28.dp), contentAlignment = Alignment.Center) {
                Text("X/O", style = MaterialTheme.typography.labelSmall)
            }
            (1..12).forEach { fret ->
                Box(Modifier.width(cellWidth).height(28.dp), contentAlignment = Alignment.Center) {
                    Text("$fret", style = MaterialTheme.typography.labelSmall)
                }
            }
        }
        stringRows.forEach { (stringIndex, label) ->
            val selectedFret = stringFrets[stringIndex]
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(label, modifier = Modifier.width(58.dp), fontWeight = FontWeight.Bold)
                Box(
                    Modifier
                        .width(48.dp)
                        .height(cellHeight)
                        .clickable { onSelect(stringIndex, if (selectedFret == 0) -1 else 0) },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        if (selectedFret == 0) "O" else "X",
                        color = if (selectedFret == 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.tertiary,
                        fontWeight = FontWeight.Bold
                    )
                }
                (1..12).forEach { fret ->
                    val active = selectedFret == fret
                    Box(
                        Modifier.width(cellWidth).height(cellHeight).clickable { onSelect(stringIndex, fret) },
                        contentAlignment = Alignment.Center
                    ) {
                        Canvas(Modifier.fillMaxSize()) {
                            drawLine(
                                color = Color(0xFF777E8C),
                                start = Offset(0f, size.height / 2f),
                                end = Offset(size.width, size.height / 2f),
                                strokeWidth = 2f + (5 - stringIndex) * 0.35f
                            )
                            drawLine(
                                color = if (fret == 1) Color(0xFF252A35) else Color(0xFFB7BCC7),
                                start = Offset(0f, 0f),
                                end = Offset(0f, size.height),
                                strokeWidth = if (fret == 1) 5f else 2f
                            )
                            if (active) drawCircle(primaryColor, 14.dp.toPx(), center)
                        }
                        if (active) {
                            Text(
                                noteNameAt(FretPosition(stringIndex, fret)),
                                color = Color.White,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ChordFinderScreen(modifier: Modifier = Modifier) {
    var query by remember { mutableStateOf("") }
    var family by remember { mutableStateOf<String?>(null) }
    var selectedChord by remember { mutableStateOf<ChordDefinition?>(null) }
    val results = remember(query, family) { filterChordDefinitions(query, family) }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            placeholder = { Text("코드 이름 검색") }
        )
        ChordFamilyTabs(selected = family, onSelect = { family = it })
        Text("${results.size}개 코드", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        ChordGrid(
            chords = results,
            onOpen = { selectedChord = it },
            modifier = Modifier.fillMaxWidth().weight(1f)
        )
    }
    selectedChord?.let { chord ->
        ChordDetailDialog(chord = chord, onDismiss = { selectedChord = null })
    }
}

@Composable
private fun ChordFamilyTabs(selected: String?, onSelect: (String?) -> Unit) {
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        chordFamilies.forEach { family ->
            FilterChip(
                selected = selected == family,
                onClick = { onSelect(family) },
                label = { Text(family ?: "전체") }
            )
        }
    }
}

@Composable
private fun ChordGrid(
    chords: List<ChordDefinition>,
    onOpen: (ChordDefinition) -> Unit,
    modifier: Modifier = Modifier,
    columns: GridCells = GridCells.Adaptive(170.dp),
    savedNames: Set<String> = emptySet(),
    onToggleSaved: ((ChordDefinition) -> Unit)? = null
) {
    LazyVerticalGrid(
        columns = columns,
        modifier = modifier,
        contentPadding = PaddingValues(bottom = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(chords, key = { it.name }) { chord ->
            ChordThumbnailCard(
                chord = chord,
                saved = chord.name in savedNames,
                onOpen = { onOpen(chord) },
                onToggleSaved = onToggleSaved?.let { callback -> { callback(chord) } }
            )
        }
    }
}

@Composable
private fun ChordThumbnailCard(
    chord: ChordDefinition,
    saved: Boolean,
    onOpen: () -> Unit,
    onToggleSaved: (() -> Unit)? = null
) {
    val voicing = remember(chord) { generateVoicings(chord).firstOrNull() }
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onOpen),
        shape = RoundedCornerShape(6.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(Modifier.padding(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(chord.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                if (onToggleSaved != null) {
                    IconButton(onClick = onToggleSaved, modifier = Modifier.size(34.dp)) {
                        Icon(if (saved) Icons.Default.Check else Icons.Default.Add, contentDescription = if (saved) "저장 해제" else "악보에 저장")
                    }
                }
            }
            if (voicing != null) {
                Text(voicing.label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                ChordDiagram(voicing, Modifier.fillMaxWidth().aspectRatio(1.5f), showStringNames = false)
            }
        }
    }
}

@Composable
private fun ChordDetailDialog(chord: ChordDefinition, onDismiss: () -> Unit) {
    val voicings = remember(chord) { generateVoicings(chord) }
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier.width(560.dp).height(460.dp),
            shape = RoundedCornerShape(8.dp),
            color = MaterialTheme.colorScheme.surface
        ) {
            Column(Modifier.fillMaxSize().padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(chord.name, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, contentDescription = "닫기") }
                }
                if (voicings.isNotEmpty()) {
                    val pagerState = rememberPagerState(pageCount = { voicings.size })
                    HorizontalPager(state = pagerState, pageSpacing = 12.dp, modifier = Modifier.weight(1f)) { page ->
                        val voicing = voicings[page]
                        Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Text(voicing.label, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                                Text(voicing.difficulty.label, color = difficultyColor(voicing.difficulty))
                            }
                            ChordDiagram(voicing, Modifier.fillMaxWidth().weight(1f))
                        }
                    }
                    Text("${pagerState.currentPage + 1} / ${voicings.size}", modifier = Modifier.align(Alignment.CenterHorizontally))
                }
            }
        }
    }
}

@Composable
fun ScoreChordHelperSidebar(
    savedChordNames: List<String>,
    onDismiss: () -> Unit,
    onAdd: (String) -> Unit,
    onRemove: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var query by remember { mutableStateOf("") }
    var family by remember { mutableStateOf<String?>(null) }
    var selectedChord by remember { mutableStateOf<ChordDefinition?>(null) }
    val savedSet = savedChordNames.toSet()
    val results = remember(query, family) { filterChordDefinitions(query, family) }
    Surface(modifier = modifier, color = MaterialTheme.colorScheme.surface, tonalElevation = 8.dp) {
        Column(Modifier.fillMaxSize().padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onDismiss) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "코드 도우미 닫기")
                }
                Text("코드 도우미", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            }
            if (savedChordNames.isNotEmpty()) {
                Text("이 악보의 코드", style = MaterialTheme.typography.labelLarge)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(savedChordNames.size) { index ->
                        val name = savedChordNames[index]
                        Surface(
                            color = MaterialTheme.colorScheme.primaryContainer,
                            shape = RoundedCornerShape(5.dp),
                            modifier = Modifier.clickable { selectedChord = chordDefinition(name) }
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(name, modifier = Modifier.padding(start = 10.dp, top = 7.dp, bottom = 7.dp), fontWeight = FontWeight.SemiBold)
                                IconButton(onClick = { onRemove(name) }, modifier = Modifier.size(32.dp)) {
                                    Icon(Icons.Default.Close, contentDescription = "$name 제거", modifier = Modifier.size(16.dp))
                                }
                            }
                        }
                    }
                }
            }
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                placeholder = { Text("코드 검색") }
            )
            ChordFamilyTabs(selected = family, onSelect = { family = it })
            ChordGrid(
                chords = results,
                onOpen = { selectedChord = it },
                columns = GridCells.Fixed(2),
                savedNames = savedSet,
                onToggleSaved = { chord -> if (chord.name in savedSet) onRemove(chord.name) else onAdd(chord.name) },
                modifier = Modifier.fillMaxWidth().weight(1f)
            )
        }
    }
    selectedChord?.let { chord -> ChordDetailDialog(chord, onDismiss = { selectedChord = null }) }
}

private fun difficultyColor(difficulty: VoicingDifficulty): Color = when (difficulty) {
    VoicingDifficulty.Easy -> Color(0xFF16836B)
    VoicingDifficulty.Medium -> Color(0xFFB26A00)
    VoicingDifficulty.Hard -> Color(0xFFB23A48)
}
