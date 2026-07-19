package com.example.guitarscore.chord

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChordTrainerScreen(onBack: () -> Unit) {
    var selectedPositions by remember { mutableStateOf<Set<FretPosition>>(emptySet()) }
    var query by remember { mutableStateOf("C") }
    var selectedChord by remember { mutableStateOf(defaultChordDefinition()) }
    val matches = remember(selectedPositions) { detectChords(selectedPositions) }

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
        BoxWithConstraints(
            Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(padding)
                .padding(20.dp)
        ) {
            if (maxWidth >= 840.dp) {
                Row(horizontalArrangement = Arrangement.spacedBy(22.dp)) {
                    ChordBuilderPane(
                        selected = selectedPositions,
                        matches = matches,
                        onToggle = { position ->
                            selectedPositions = if (position in selectedPositions) {
                                selectedPositions - position
                            } else {
                                selectedPositions + position
                            }
                        },
                        onClear = { selectedPositions = emptySet() },
                        modifier = Modifier.weight(1f).fillMaxHeight()
                    )
                    ChordSearchPane(
                        query = query,
                        selectedChord = selectedChord,
                        onQueryChange = { query = it },
                        onSelect = {
                            selectedChord = it
                            query = it.name
                        },
                        modifier = Modifier.width(410.dp).fillMaxHeight()
                    )
                }
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(18.dp)) {
                    item {
                        ChordBuilderPane(
                            selected = selectedPositions,
                            matches = matches,
                            onToggle = { position ->
                                selectedPositions = if (position in selectedPositions) selectedPositions - position else selectedPositions + position
                            },
                            onClear = { selectedPositions = emptySet() }
                        )
                    }
                    item {
                        ChordSearchPane(
                            query = query,
                            selectedChord = selectedChord,
                            onQueryChange = { query = it },
                            onSelect = {
                                selectedChord = it
                                query = it.name
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ChordBuilderPane(
    selected: Set<FretPosition>,
    matches: List<ChordMatch>,
    onToggle: (FretPosition) -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(modifier = modifier, color = MaterialTheme.colorScheme.surface, shape = MaterialTheme.shapes.small) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("지판에서 코드 만들기", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text(
                        when {
                            selected.isEmpty() -> "선택된 음 없음"
                            matches.isNotEmpty() -> matches.take(3).joinToString("  ·  ") { it.name }
                            else -> selectedNoteSummary(selected)
                        },
                        color = if (matches.isNotEmpty()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                IconButton(onClick = onClear, enabled = selected.isNotEmpty()) {
                    Icon(Icons.AutoMirrored.Filled.Backspace, contentDescription = "선택 지우기")
                }
            }
            InteractiveFretboard(selected = selected, onToggle = onToggle)
        }
    }
}

@Composable
private fun InteractiveFretboard(selected: Set<FretPosition>, onToggle: (FretPosition) -> Unit) {
    val strings = listOf("E", "A", "D", "G", "B", "E")
    val cellWidth = 46.dp
    val cellHeight = 50.dp
    Column(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
        Row(Modifier.padding(start = 38.dp)) {
            (0..12).forEach { fret ->
                Box(Modifier.width(cellWidth).height(28.dp), contentAlignment = Alignment.Center) {
                    Text(if (fret == 0) "Open" else "$fret", style = MaterialTheme.typography.labelSmall)
                }
            }
        }
        strings.forEachIndexed { stringIndex, label ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(label, modifier = Modifier.width(38.dp), fontWeight = FontWeight.Bold)
                (0..12).forEach { fret ->
                    val position = FretPosition(stringIndex, fret)
                    val active = position in selected
                    Box(
                        modifier = Modifier
                            .width(cellWidth)
                            .height(cellHeight)
                            .clickable { onToggle(position) },
                        contentAlignment = Alignment.Center
                    ) {
                        Canvas(Modifier.fillMaxSize()) {
                            drawLine(
                                color = Color(0xFF707684),
                                start = Offset(0f, size.height / 2f),
                                end = Offset(size.width, size.height / 2f),
                                strokeWidth = 2f + (5 - stringIndex) * 0.35f
                            )
                            if (fret > 0) {
                                drawLine(
                                    color = if (fret == 1) Color(0xFF303540) else Color(0xFFB2B7C2),
                                    start = Offset(0f, 0f),
                                    end = Offset(0f, size.height),
                                    strokeWidth = if (fret == 1) 5f else 2f
                                )
                            }
                            if (active) {
                                drawCircle(color = Color(0xFF3559D9), radius = 14.dp.toPx(), center = center)
                            }
                        }
                        if (active) {
                            Text(noteNameAt(position), color = Color.White, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ChordSearchPane(
    query: String,
    selectedChord: ChordDefinition,
    onQueryChange: (String) -> Unit,
    onSelect: (ChordDefinition) -> Unit,
    modifier: Modifier = Modifier
) {
    val results = remember(query) { searchChordDefinitions(query) }
    val exact = remember(query) { chordDefinition(query) }
    Surface(modifier = modifier, color = MaterialTheme.colorScheme.surface, shape = MaterialTheme.shapes.small) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text("코드 운지 찾기", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            OutlinedTextField(
                value = query,
                onValueChange = onQueryChange,
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                label = { Text("코드 이름") }
            )
            if (exact == null) {
                LazyColumn(Modifier.height(250.dp)) {
                    items(results, key = { it.name }) { chord ->
                        Row(
                            Modifier.fillMaxWidth().clickable { onSelect(chord) }.padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(chord.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        }
                        HorizontalDivider()
                    }
                }
            } else {
                LaunchedEffect(exact.name) {
                    if (exact.name != selectedChord.name) onSelect(exact)
                }
                ChordVoicingPager(chord = exact)
            }
        }
    }
}

@Composable
private fun ChordVoicingPager(chord: ChordDefinition) {
    val voicings = remember(chord) { generateVoicings(chord) }
    if (voicings.isEmpty()) {
        Text("표시할 운지법이 없습니다.")
        return
    }
    androidx.compose.runtime.key(chord.name) {
        val pagerState = rememberPagerState(pageCount = { voicings.size })
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            HorizontalPager(
                state = pagerState,
                contentPadding = PaddingValues(horizontal = 12.dp),
                pageSpacing = 12.dp,
                modifier = Modifier.fillMaxWidth().height(360.dp)
            ) { page ->
                val voicing = voicings[page]
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                    shape = MaterialTheme.shapes.small
                ) {
                    Column(
                        Modifier.fillMaxSize().padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text(chord.name, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                            Surface(color = difficultyColor(voicing.difficulty), shape = MaterialTheme.shapes.extraSmall) {
                                Text(voicing.difficulty.label, modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp), color = Color.White)
                            }
                        }
                        Text(voicing.label, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        ChordDiagram(voicing.frets, Modifier.fillMaxWidth().weight(1f))
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                voicings.indices.forEach { index ->
                    Canvas(Modifier.size(8.dp)) {
                        drawCircle(if (index == pagerState.currentPage) Color(0xFF3559D9) else Color(0xFFC4C8D1))
                    }
                }
                Spacer(Modifier.width(6.dp))
                Text("${pagerState.currentPage + 1} / ${voicings.size}", style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

@Composable
private fun ChordDiagram(frets: List<Int>, modifier: Modifier = Modifier) {
    val positiveFrets = frets.filter { it > 0 }
    val startFret = if ((positiveFrets.minOrNull() ?: 1) > 4) positiveFrets.min() else 1
    Canvas(modifier.padding(horizontal = 28.dp, vertical = 8.dp)) {
        val left = size.width * 0.12f
        val right = size.width * 0.88f
        val top = size.height * 0.16f
        val bottom = size.height * 0.92f
        val stringGap = (right - left) / 5f
        val fretGap = (bottom - top) / 5f
        for (string in 0..5) {
            val x = left + string * stringGap
            drawLine(Color(0xFF5F6674), Offset(x, top), Offset(x, bottom), strokeWidth = 2f)
        }
        for (fretLine in 0..5) {
            val y = top + fretLine * fretGap
            drawLine(
                Color(0xFF5F6674),
                Offset(left, y),
                Offset(right, y),
                strokeWidth = if (startFret == 1 && fretLine == 0) 7f else 2f
            )
        }
        frets.forEachIndexed { string, fret ->
            val x = left + string * stringGap
            when {
                fret < 0 -> {
                    drawLine(Color(0xFFB23A48), Offset(x - 7f, top - 35f), Offset(x + 7f, top - 21f), 4f, StrokeCap.Round)
                    drawLine(Color(0xFFB23A48), Offset(x + 7f, top - 35f), Offset(x - 7f, top - 21f), 4f, StrokeCap.Round)
                }
                fret == 0 -> drawCircle(Color(0xFF3559D9), 8f, Offset(x, top - 28f), style = Stroke(width = 4f))
                fret in startFret until (startFret + 5) -> {
                    val y = top + (fret - startFret + 0.5f) * fretGap
                    drawCircle(Color(0xFF3559D9), 12.dp.toPx(), Offset(x, y))
                }
            }
        }
    }
    if (startFret > 1) {
        Text("${startFret}fr", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
    }
}

private fun difficultyColor(difficulty: VoicingDifficulty): Color = when (difficulty) {
    VoicingDifficulty.Easy -> Color(0xFF16836B)
    VoicingDifficulty.Medium -> Color(0xFFB26A00)
    VoicingDifficulty.Hard -> Color(0xFFB23A48)
}
