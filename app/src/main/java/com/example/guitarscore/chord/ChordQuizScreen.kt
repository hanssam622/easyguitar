package com.example.guitarscore.chord

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

private val correctColor = Color(0xFF16836B)
private val wrongColor = Color(0xFFB23A48)

private sealed interface QuizSource {
    data class Level(val level: QuizLevel) : QuizSource
    data object Custom : QuizSource
}

/** 한 판의 진행 상태. 문제 목록은 시작할 때 한 번 만들고 끝날 때까지 바뀌지 않는다. */
private data class QuizRun(
    val questions: List<QuizQuestion>,
    val index: Int = 0,
    val selectedIndex: Int? = null,
    val correctCount: Int = 0,
    val wrongNames: List<String> = emptyList(),
    val finished: Boolean = false
) {
    val current: QuizQuestion get() = questions[index]
    val answered: Boolean get() = selectedIndex != null
}

@Composable
fun ChordQuizScreen(
    quizChordNames: List<String>,
    onToggleQuizChord: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var source by remember { mutableStateOf<QuizSource>(QuizSource.Level(quizLevels.first())) }
    var mode by remember { mutableStateOf(QuizMode.Mixed) }
    var questionCount by remember { mutableIntStateOf(QUIZ_DEFAULT_LENGTH) }
    var run by remember { mutableStateOf<QuizRun?>(null) }
    var pickerVisible by remember { mutableStateOf(false) }

    val poolNames = when (val current = source) {
        is QuizSource.Level -> current.level.chordNames
        QuizSource.Custom -> quizChordNames
    }

    val startQuiz = {
        val questions = buildQuiz(poolNames, mode, questionCount)
        run = if (questions.isEmpty()) null else QuizRun(questions)
    }

    Box(modifier) {
        val activeRun = run
        when {
            activeRun == null -> QuizSetup(
                source = source,
                onSourceChange = { source = it },
                mode = mode,
                onModeChange = { mode = it },
                questionCount = questionCount,
                onQuestionCountChange = { questionCount = it },
                poolNames = poolNames,
                customCount = quizChordNames.size,
                onEditCustom = { pickerVisible = true },
                onStart = startQuiz
            )
            activeRun.finished -> QuizResult(
                run = activeRun,
                savedNames = quizChordNames.toSet(),
                onSaveWrong = onToggleQuizChord,
                onRetry = startQuiz,
                onChangeRange = { run = null }
            )
            else -> QuizPlay(
                run = activeRun,
                onSelect = { optionIndex ->
                    if (!activeRun.answered) {
                        val question = activeRun.current
                        val isCorrect = optionIndex == question.correctIndex
                        run = activeRun.copy(
                            selectedIndex = optionIndex,
                            correctCount = activeRun.correctCount + if (isCorrect) 1 else 0,
                            wrongNames = if (isCorrect) {
                                activeRun.wrongNames
                            } else {
                                activeRun.wrongNames + question.answer.chord.name
                            }
                        )
                    }
                },
                onNext = {
                    run = if (activeRun.index + 1 >= activeRun.questions.size) {
                        activeRun.copy(finished = true)
                    } else {
                        activeRun.copy(index = activeRun.index + 1, selectedIndex = null)
                    }
                },
                onQuit = { run = null }
            )
        }
    }

    if (pickerVisible) {
        QuizChordPicker(
            selected = quizChordNames.toSet(),
            onToggle = onToggleQuizChord,
            onDismiss = { pickerVisible = false }
        )
    }
}

@Composable
private fun QuizSetup(
    source: QuizSource,
    onSourceChange: (QuizSource) -> Unit,
    mode: QuizMode,
    onModeChange: (QuizMode) -> Unit,
    questionCount: Int,
    onQuestionCountChange: (Int) -> Unit,
    poolNames: List<String>,
    customCount: Int,
    onEditCustom: () -> Unit,
    onStart: () -> Unit
) {
    val enoughChords = poolNames.size >= QUIZ_MINIMUM_POOL
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        Section("출제 범위") {
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                quizLevels.forEach { level ->
                    FilterChip(
                        selected = source is QuizSource.Level && source.level.level == level.level,
                        onClick = { onSourceChange(QuizSource.Level(level)) },
                        label = { Text("${level.level}단계") }
                    )
                }
                FilterChip(
                    selected = source is QuizSource.Custom,
                    onClick = { onSourceChange(QuizSource.Custom) },
                    label = { Text("내 코드 모음 ($customCount)") }
                )
            }
            val description = when (source) {
                is QuizSource.Level -> "${source.level.title} · ${source.level.summary}"
                QuizSource.Custom -> "외우려고 직접 고른 코드로만 문제를 낸다"
            }
            Text(description, style = MaterialTheme.typography.bodyMedium)
            Text(
                "코드 ${poolNames.size}개 · ${poolNames.take(12).joinToString(" ")}${if (poolNames.size > 12) " …" else ""}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            OutlinedButton(onClick = onEditCustom) {
                Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("내 코드 모음 편집")
            }
        }

        Section("출제 방식") {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                QuizMode.entries.forEach { option ->
                    FilterChip(
                        selected = mode == option,
                        onClick = { onModeChange(option) },
                        label = { Text(option.label) }
                    )
                }
            }
            Text(
                mode.summary,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Section("문제 수") {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(5, 10, 20).forEach { count ->
                    FilterChip(
                        selected = questionCount == count,
                        onClick = { onQuestionCountChange(count) },
                        label = { Text("${count}문제") }
                    )
                }
            }
        }

        if (!enoughChords) {
            Text(
                "선택지를 만들려면 코드가 ${QUIZ_MINIMUM_POOL}개 이상 필요합니다. 내 코드 모음에 코드를 더 담아 주세요.",
                color = wrongColor,
                style = MaterialTheme.typography.bodyMedium
            )
        }
        Button(onClick = onStart, enabled = enoughChords) {
            Icon(Icons.Default.PlayArrow, contentDescription = null)
            Spacer(Modifier.width(6.dp))
            Text("시작하기")
        }
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        content()
    }
}

@Composable
private fun QuizPlay(
    run: QuizRun,
    onSelect: (Int) -> Unit,
    onNext: () -> Unit,
    onQuit: () -> Unit
) {
    val question = run.current
    // 운지 그림 네 개까지 들어가면 작은 화면에서는 넘치므로 스크롤을 열어 둔다.
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "${run.index + 1} / ${run.questions.size}",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.width(14.dp))
            Text("맞힘 ${run.correctCount}", color = correctColor, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.weight(1f))
            TextButton(onClick = onQuit) { Text("그만두기") }
        }
        LinearProgressIndicator(
            progress = { (run.index + if (run.answered) 1 else 0).toFloat() / run.questions.size },
            modifier = Modifier.fillMaxWidth()
        )

        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
            Column(
                Modifier.fillMaxWidth().padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    if (question.mode == QuizMode.NameFromDiagram) "이 운지의 코드 이름은?" else "이 코드의 운지는?",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (question.mode == QuizMode.NameFromDiagram) {
                    ChordDiagram(question.answer.voicing, Modifier.width(340.dp).height(170.dp))
                    Text(
                        question.answer.voicing.label,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    Text(
                        question.answer.chord.name,
                        style = MaterialTheme.typography.displayMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }

        QuizOptions(question = question, selectedIndex = run.selectedIndex, onSelect = onSelect)

        if (run.answered) {
            val isCorrect = run.selectedIndex == question.correctIndex
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (isCorrect) "정답입니다" else "정답은 ${question.answer.chord.name} 입니다",
                    color = if (isCorrect) correctColor else wrongColor,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleMedium
                )
                Spacer(Modifier.weight(1f))
                Button(onClick = onNext) {
                    Text(if (run.index + 1 >= run.questions.size) "결과 보기" else "다음 문제")
                }
            }
        }
    }
}

@Composable
private fun QuizOptions(question: QuizQuestion, selectedIndex: Int?, onSelect: (Int) -> Unit) {
    // 2 x 2 로 놓아야 운지 그림 네 개를 한눈에 비교할 수 있다.
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        listOf(0, 2).forEach { rowStart ->
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                (rowStart until rowStart + 2).forEach { index ->
                    val option = question.options.getOrNull(index)
                    if (option == null) {
                        Spacer(Modifier.weight(1f))
                        return@forEach
                    }
                    QuizOptionCard(
                        option = option,
                        showDiagram = question.mode == QuizMode.DiagramFromName,
                        state = optionState(index, question.correctIndex, selectedIndex),
                        onClick = { onSelect(index) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

private enum class OptionState { Idle, Correct, Wrong, Dimmed }

private fun optionState(index: Int, correctIndex: Int, selectedIndex: Int?): OptionState = when {
    selectedIndex == null -> OptionState.Idle
    index == correctIndex -> OptionState.Correct
    index == selectedIndex -> OptionState.Wrong
    else -> OptionState.Dimmed
}

@Composable
private fun QuizOptionCard(
    option: QuizOption,
    showDiagram: Boolean,
    state: OptionState,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val borderColor = when (state) {
        OptionState.Correct -> correctColor
        OptionState.Wrong -> wrongColor
        else -> MaterialTheme.colorScheme.outline
    }
    val background = when (state) {
        OptionState.Correct -> correctColor.copy(alpha = 0.12f)
        OptionState.Wrong -> wrongColor.copy(alpha = 0.12f)
        else -> MaterialTheme.colorScheme.surface
    }
    Surface(
        modifier = modifier
            .border(if (state == OptionState.Idle) 1.dp else 2.dp, borderColor, RoundedCornerShape(8.dp))
            .clickable(enabled = state == OptionState.Idle, onClick = onClick),
        color = background,
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(
            Modifier.fillMaxWidth().padding(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            if (showDiagram) {
                ChordDiagram(option.voicing, Modifier.fillMaxWidth().height(120.dp), showStringNames = false)
            } else {
                Text(
                    option.chord.name,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(vertical = 10.dp)
                )
            }
            if (state == OptionState.Correct || state == OptionState.Wrong) {
                Text(
                    if (state == OptionState.Correct) "정답" else "선택",
                    color = if (state == OptionState.Correct) correctColor else wrongColor,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun QuizResult(
    run: QuizRun,
    savedNames: Set<String>,
    onSaveWrong: (String) -> Unit,
    onRetry: () -> Unit,
    onChangeRange: () -> Unit
) {
    val wrongNames = run.wrongNames.distinct()
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("결과", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text(
            "${run.questions.size}문제 중 ${run.correctCount}개 정답",
            style = MaterialTheme.typography.displaySmall,
            fontWeight = FontWeight.Bold,
            color = if (run.correctCount == run.questions.size) correctColor else MaterialTheme.colorScheme.onSurface
        )
        if (wrongNames.isEmpty()) {
            Text("전부 맞혔습니다. 다음 단계로 넘어가 보세요.", color = correctColor)
        } else {
            Text("틀린 코드", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(
                "누르면 내 코드 모음에 담깁니다. 모아서 다시 풀면 외우기 좋습니다.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                wrongNames.forEach { name ->
                    val saved = name in savedNames
                    FilterChip(
                        selected = saved,
                        onClick = { onSaveWrong(name) },
                        label = { Text(name) },
                        leadingIcon = {
                            if (saved) Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                        }
                    )
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(onClick = onRetry) {
                Icon(Icons.Default.Refresh, contentDescription = null)
                Spacer(Modifier.width(6.dp))
                Text("다시 풀기")
            }
            OutlinedButton(onClick = onChangeRange) { Text("범위 바꾸기") }
        }
    }
}

@Composable
private fun QuizChordPicker(
    selected: Set<String>,
    onToggle: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var query by remember { mutableStateOf("") }
    var family by remember { mutableStateOf<String?>(null) }
    val results = remember(query, family) { filterChordDefinitions(query, family) }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            modifier = Modifier.fillMaxWidth(0.9f).heightIn(max = 620.dp).fillMaxHeight(0.88f),
            shape = RoundedCornerShape(8.dp),
            color = MaterialTheme.colorScheme.surface
        ) {
            Column(Modifier.fillMaxSize().padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("내 코드 모음", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        Text(
                            "고른 코드 ${selected.size}개 · 카드를 누르면 담기고 다시 누르면 빠집니다",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, contentDescription = "닫기") }
                }
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    placeholder = { Text("코드 이름 검색") }
                )
                ChordFamilyTabs(selected = family, onSelect = { family = it })
                ChordGrid(
                    chords = results,
                    onOpen = { onToggle(it.name) },
                    columns = GridCells.Adaptive(190.dp),
                    savedNames = selected,
                    onToggleSaved = { onToggle(it.name) },
                    modifier = Modifier.fillMaxWidth().weight(1f)
                )
            }
        }
    }
}
