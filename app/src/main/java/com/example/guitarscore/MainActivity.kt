package com.example.guitarscore

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.KeyEvent
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.Image
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.graphicsLayer
import kotlin.math.cos
import kotlin.math.sin
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.guitarscore.audio.AutoTurnEngine
import com.example.guitarscore.audio.AutoTurnState
import com.example.guitarscore.audio.MetronomeEngine
import com.example.guitarscore.audio.TunerEngine
import com.example.guitarscore.audio.builtInTunings
import com.example.guitarscore.data.ScoreEntity
import com.example.guitarscore.data.ScoreMetadataEntity
import com.example.guitarscore.data.ScoreRepository
import com.example.guitarscore.data.ScoreWithMetadata
import com.example.guitarscore.data.TurnCueEntity
import com.example.guitarscore.score.PdfPageRenderer
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val viewModel by viewModels<MainViewModel> {
        MainViewModel.factory((application as GuitarScoreApp).repository)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setContent {
            GuitarScoreTheme {
                GuitarScoreAppUi(viewModel)
            }
        }
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_UP) {
            when (event.keyCode) {
                KeyEvent.KEYCODE_PAGE_DOWN, KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_SPACE -> {
                    viewModel.nextPage()
                    return true
                }
                KeyEvent.KEYCODE_PAGE_UP, KeyEvent.KEYCODE_DPAD_LEFT -> {
                    viewModel.previousPage()
                    return true
                }
            }
        }
        return super.dispatchKeyEvent(event)
    }
}

data class MainUiState(
    val scores: List<ScoreEntity> = emptyList(),
    val selected: ScoreWithMetadata? = null,
    val cues: List<TurnCueEntity> = emptyList(),
    val pageIndex: Int = 0,
    val pageCount: Int = 0,
    val pageBitmap: android.graphics.Bitmap? = null,
    val scrollPageBitmaps: Map<Int, android.graphics.Bitmap> = emptyMap(),
    val autoTurnState: AutoTurnState = AutoTurnState(),
    val metronomeRunning: Boolean = false,
    val tunerVisible: Boolean = false,
    val toolbarExpanded: Boolean = false,
    val progressMode: ProgressMode = ProgressMode.PageTurn,
    val barsPerLine: Int = 4,
    val lineScrollDp: Int = 180
)

enum class ProgressMode { PageTurn, Scroll }

class MainViewModel(private val repository: ScoreRepository) : ViewModel() {
    private val _uiState = MutableStateFlow(MainUiState())
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()
    private val autoTurn = AutoTurnEngine()
    private val metronome = MetronomeEngine()
    private var cueJob: Job? = null
    private var pdfRenderer: PdfPageRenderer? = null

    init {
        viewModelScope.launch {
            repository.observeScores().collect { scores ->
                _uiState.value = _uiState.value.copy(scores = scores)
            }
        }
    }

    fun importPdf(context: android.content.Context, uri: Uri) {
        viewModelScope.launch {
            val id = repository.addPdf(context.contentResolver, uri)
            openScore(context, id)
        }
    }

    fun openScore(context: android.content.Context, id: Long) {
        viewModelScope.launch {
            autoTurn.stop()
            metronome.stop()
            cueJob?.cancel()
            pdfRenderer?.close()
            val selected = repository.loadScore(id) ?: return@launch
            val renderer = PdfPageRenderer(context.applicationContext, Uri.parse(selected.score.pdfUri))
            pdfRenderer = renderer
            val pageCount = renderer.open()
            val pageIndex = selected.score.lastOpenedPage.coerceIn(0, (pageCount - 1).coerceAtLeast(0))
            _uiState.value = _uiState.value.copy(
                selected = selected,
                pageIndex = pageIndex,
                pageCount = pageCount,
                scrollPageBitmaps = emptyMap(),
                metronomeRunning = false,
                autoTurnState = AutoTurnState()
            )
            cueJob = viewModelScope.launch {
                repository.observeCues(id).collect { cues -> _uiState.value = _uiState.value.copy(cues = cues) }
            }
            renderCurrentPage()
        }
    }

    fun closeScore() {
        autoTurn.stop()
        metronome.stop()
        pdfRenderer?.close()
        pdfRenderer = null
        _uiState.value = MainUiState(scores = _uiState.value.scores)
    }

    fun nextPage() = movePage(1)
    fun previousPage() = movePage(-1)

    private fun movePage(delta: Int) {
        val state = _uiState.value
        val next = (state.pageIndex + delta).coerceIn(0, (state.pageCount - 1).coerceAtLeast(0))
        if (next == state.pageIndex) return
        _uiState.value = state.copy(pageIndex = next)
        viewModelScope.launch {
            renderCurrentPage()
            state.selected?.let { repository.updateScore(it.score.copy(lastOpenedPage = next, updatedAt = System.currentTimeMillis())) }
        }
    }

    fun setBpm(bpm: Int) {
        val selected = _uiState.value.selected ?: return
        val metadata = selected.metadata.copy(bpm = bpm)
        _uiState.value = _uiState.value.copy(selected = selected.copy(metadata = metadata))
        viewModelScope.launch { repository.saveMetadata(metadata) }
    }

    fun setTimeSignature(beats: Int) {
        val selected = _uiState.value.selected ?: return
        val metadata = selected.metadata.copy(timeSignature = beats)
        _uiState.value = _uiState.value.copy(selected = selected.copy(metadata = metadata))
        viewModelScope.launch { repository.saveMetadata(metadata) }
    }

    fun toggleFavorite() {
        val selected = _uiState.value.selected ?: return
        viewModelScope.launch {
            val score = selected.score.copy(favorite = !selected.score.favorite, updatedAt = System.currentTimeMillis())
            repository.updateScore(score)
            _uiState.value = _uiState.value.copy(selected = selected.copy(score = score))
        }
    }

    fun recordCue() {
        val state = _uiState.value
        val selected = state.selected ?: return
        val cue = TurnCueEntity(
            scoreId = selected.score.id,
            pageIndex = (state.pageIndex + 1).coerceAtMost((state.pageCount - 1).coerceAtLeast(0)),
            triggerBeat = state.autoTurnState.elapsedBeats.takeIf { state.autoTurnState.running },
            triggerMillis = if (state.autoTurnState.running) null else state.autoTurnState.elapsedMillis
        )
        viewModelScope.launch { repository.addCue(cue) }
    }

    fun startAutoTurn() {
        val state = _uiState.value
        val metadata = state.selected?.metadata ?: return
        autoTurn.start(
            bpm = metadata.bpm,
            cues = state.cues,
            onState = { _uiState.value = _uiState.value.copy(autoTurnState = it) },
            onTurnToPage = { page -> viewModelScope.launch { goToPage(page) } }
        )
        metronome.start(metadata.bpm, metadata.timeSignature) { _, _ -> }
        _uiState.value = state.copy(metronomeRunning = true)
    }

    fun stopAutoTurn() {
        autoTurn.stop()
        metronome.stop()
        _uiState.value = _uiState.value.copy(autoTurnState = AutoTurnState(), metronomeRunning = false)
    }

    fun nudge(deltaMillis: Long) = autoTurn.nudge(deltaMillis)
    fun toggleTuner() = _uiState.run { value = value.copy(tunerVisible = !value.tunerVisible) }
    fun toggleToolbarExpanded() = _uiState.run { value = value.copy(toolbarExpanded = !value.toolbarExpanded) }
    fun setProgressMode(mode: ProgressMode) = _uiState.run { value = value.copy(progressMode = mode) }
    fun setBarsPerLine(bars: Int) = _uiState.run { value = value.copy(barsPerLine = bars.coerceIn(1, 8)) }
    fun setLineScrollDp(dp: Int) = _uiState.run { value = value.copy(lineScrollDp = dp.coerceIn(60, 520)) }
    fun goToPageFromScroll(page: Int) {
        val state = _uiState.value
        val next = page.coerceIn(0, (state.pageCount - 1).coerceAtLeast(0))
        if (next != state.pageIndex) {
            _uiState.value = state.copy(pageIndex = next)
        }
    }
    fun ensureScrollPages() {
        val state = _uiState.value
        if (state.pageCount == 0 || state.scrollPageBitmaps.size == state.pageCount) return
        viewModelScope.launch {
            val renderer = pdfRenderer ?: return@launch
            val rendered = linkedMapOf<Int, android.graphics.Bitmap>()
            for (page in 0 until state.pageCount) {
                rendered[page] = renderer.renderPage(page, 1400)
                _uiState.value = _uiState.value.copy(scrollPageBitmaps = rendered.toMap())
            }
        }
    }

    private suspend fun goToPage(page: Int) {
        val next = page.coerceIn(0, (_uiState.value.pageCount - 1).coerceAtLeast(0))
        _uiState.value = _uiState.value.copy(pageIndex = next)
        renderCurrentPage()
    }

    private suspend fun renderCurrentPage() {
        val renderer = pdfRenderer ?: return
        val bitmap = renderer.renderPage(_uiState.value.pageIndex, 1800)
        _uiState.value = _uiState.value.copy(pageBitmap = bitmap)
    }

    companion object {
        fun factory(repository: ScoreRepository) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = MainViewModel(repository) as T
        }
    }
}

@Composable
fun GuitarScoreTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = androidx.compose.material3.lightColorScheme(
            primary = Color(0xFF2F5D50),
            secondary = Color(0xFFD49A3A),
            surface = Color(0xFFFAF8F3),
            background = Color(0xFFF3F0E8)
        ),
        content = content
    )
}

@Composable
fun GuitarScoreAppUi(viewModel: MainViewModel) {
    val state by viewModel.uiState.collectAsState()
    if (state.selected == null) {
        LibraryScreen(state, viewModel)
    } else {
        ViewerScreen(state, viewModel)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LibraryScreen(state: MainUiState, viewModel: MainViewModel) {
    val context = LocalContext.current
    val pdfPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { viewModel.importPdf(context, it) }
    }
    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Guitar Score", fontWeight = FontWeight.SemiBold) },
                actions = {
                    IconButton(onClick = { pdfPicker.launch(arrayOf("application/pdf")) }) {
                        Icon(Icons.Default.Add, contentDescription = "PDF 가져오기")
                    }
                }
            )
        }
    ) { padding ->
        Row(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(padding)
                .padding(24.dp),
            horizontalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            Column(modifier = Modifier.width(280.dp).fillMaxHeight()) {
                Icon(Icons.Default.LibraryMusic, contentDescription = null, modifier = Modifier.size(44.dp))
                Spacer(Modifier.height(16.dp))
                Text("악보 라이브러리", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text("PDF 악보를 가져오고, 태블릿 연주 모드에서 자동 넘김 큐를 기록하세요.")
                Spacer(Modifier.height(24.dp))
                Button(onClick = { pdfPicker.launch(arrayOf("application/pdf")) }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("PDF 가져오기")
                }
            }
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.weight(1f)) {
                if (state.scores.isEmpty()) {
                    item {
                        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                            Text("아직 가져온 악보가 없습니다.", modifier = Modifier.padding(24.dp))
                        }
                    }
                }
                items(state.scores, key = { it.id }) { score ->
                    ScoreRow(score = score, onClick = { viewModel.openScore(context, score.id) })
                }
            }
        }
    }
}

@Composable
private fun ScoreRow(score: ScoreEntity, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.MusicNote, contentDescription = null)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(score.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(if (score.artist.isBlank()) "PDF score" else score.artist, style = MaterialTheme.typography.bodySmall)
            }
            Icon(if (score.favorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder, contentDescription = null)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ViewerScreen(state: MainUiState, viewModel: MainViewModel) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF171A1F))
    ) {
        PdfPane(state, viewModel, modifier = Modifier.fillMaxSize())
        PerformanceToolbar(
            state = state,
            viewModel = viewModel,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(10.dp)
        )
        PagePill(
            state = state,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 10.dp)
        )
        if (state.tunerVisible) {
            TunerOverlay(onDismiss = viewModel::toggleTuner)
        }
    }
}

@Composable
private fun PdfPane(state: MainUiState, viewModel: MainViewModel, modifier: Modifier = Modifier) {
    var scale by remember { mutableFloatStateOf(1f) }
    if (state.progressMode == ProgressMode.Scroll) {
        ScrollScorePane(state = state, viewModel = viewModel, scale = scale, onScaleChange = { scale = it }, modifier = modifier)
        return
    }
    val scrollState = rememberScrollState()
    LaunchedEffect(state.progressMode, state.autoTurnState.elapsedBeats, state.pageIndex, scrollState.maxValue) {
        if (state.progressMode == ProgressMode.Scroll && state.autoTurnState.running && scrollState.maxValue > 0) {
            val progress = pageScrollProgress(state).coerceIn(0f, 1f)
            scrollState.scrollTo((scrollState.maxValue * progress).toInt())
        }
    }
    Box(modifier = modifier.fillMaxHeight(), contentAlignment = Alignment.Center) {
        state.pageBitmap?.let { bitmap ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(scrollState)
                    .pointerInput(Unit) {
                        detectTransformGestures { _, _, zoom, _ ->
                            scale = (scale * zoom).coerceIn(0.75f, 3f)
                        }
                    }
            ) {
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = "PDF page",
                    contentScale = ContentScale.FillWidth,
                    modifier = Modifier
                        .fillMaxWidth()
                        .graphicsLayer(scaleX = scale, scaleY = scale)
                )
            }
        } ?: Text("PDF 렌더링 중...", color = Color.White)
        if (state.progressMode == ProgressMode.PageTurn) {
            Row(Modifier.fillMaxSize()) {
                Box(Modifier.weight(1f).fillMaxHeight().clickable { viewModel.previousPage() })
                Box(Modifier.weight(1f).fillMaxHeight().clickable { viewModel.nextPage() })
            }
        }
    }
}

@Composable
private fun ScrollScorePane(
    state: MainUiState,
    viewModel: MainViewModel,
    scale: Float,
    onScaleChange: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()
    val density = LocalDensity.current
    val lineScrollPx = with(density) { state.lineScrollDp.dp.roundToPx() }
    val currentLineIndex = autoScrollLineIndex(state)
    LaunchedEffect(state.pageCount, state.progressMode) {
        viewModel.ensureScrollPages()
    }
    LaunchedEffect(currentLineIndex, state.autoTurnState.running, scrollState.maxValue, state.scrollPageBitmaps.size, lineScrollPx) {
        if (state.autoTurnState.running && scrollState.maxValue > 0) {
            val target = (currentLineIndex * lineScrollPx).coerceIn(0, scrollState.maxValue)
            scrollState.animateScrollTo(target)
        }
    }
    LaunchedEffect(scrollState.maxValue) {
        snapshotFlow { scrollState.value }.collect { value ->
            if (scrollState.maxValue > 0 && state.pageCount > 0) {
                val page = ((value.toFloat() / scrollState.maxValue) * state.pageCount).toInt()
                    .coerceIn(0, (state.pageCount - 1).coerceAtLeast(0))
                if (page != state.pageIndex) viewModel.goToPageFromScroll(page)
            }
        }
    }
    Box(modifier = modifier.fillMaxHeight(), contentAlignment = Alignment.TopCenter) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(top = 66.dp, bottom = 44.dp)
                .clickable(enabled = false) {},
            verticalArrangement = Arrangement.spacedBy(14.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (state.scrollPageBitmaps.isEmpty()) {
                Text("스크롤용 악보 렌더링 중...", color = Color.White, modifier = Modifier.padding(36.dp))
            } else {
                for (page in 0 until state.pageCount) {
                    state.scrollPageBitmaps[page]?.let { bitmap ->
                        Image(
                            bitmap = bitmap.asImageBitmap(),
                            contentDescription = "PDF page ${page + 1}",
                            contentScale = ContentScale.FillWidth,
                            modifier = Modifier
                                .fillMaxWidth(0.94f)
                                .background(Color.White)
                                .graphicsLayer(scaleX = scale, scaleY = scale)
                        )
                    }
                }
            }
        }
    }
}

private fun pageScrollProgress(state: MainUiState): Float {
    val currentPage = state.pageIndex
    val currentBeat = state.autoTurnState.elapsedBeats
    val previousBeat = state.cues
        .filter { it.pageIndex <= currentPage }
        .mapNotNull { it.triggerBeat }
        .maxOrNull() ?: (currentPage * 32f)
    val nextBeat = state.cues
        .filter { it.pageIndex == currentPage + 1 }
        .mapNotNull { it.triggerBeat }
        .minOrNull() ?: (previousBeat + 32f)
    val span = (nextBeat - previousBeat).coerceAtLeast(1f)
    return (currentBeat - previousBeat) / span
}

private fun autoScrollLineIndex(state: MainUiState): Int {
    val beatsPerLine = (state.selected?.metadata?.timeSignature ?: 4) * state.barsPerLine
    if (beatsPerLine <= 0) return 0
    return (state.autoTurnState.elapsedBeats / beatsPerLine).toInt().coerceAtLeast(0)
}

@Composable
private fun ControlPane(state: MainUiState, viewModel: MainViewModel, modifier: Modifier = Modifier) {
    val metadata = state.selected?.metadata ?: return
    Surface(modifier = modifier, color = MaterialTheme.colorScheme.surface) {
        LazyColumn(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            item {
                Text("연주 설정", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text("Page ${state.pageIndex + 1} / ${state.pageCount}")
            }
            item {
                Text("BPM ${metadata.bpm}")
                Slider(
                    value = metadata.bpm.toFloat(),
                    onValueChange = { viewModel.setBpm(it.toInt()) },
                    valueRange = 40f..220f
                )
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(3, 4, 6).forEach { beats ->
                        OutlinedButton(onClick = { viewModel.setTimeSignature(beats) }) {
                            Text("${beats}/4")
                        }
                    }
                }
            }
            item {
                AutoTurnPanel(state, viewModel)
            }
            item {
                CueList(state)
            }
            if (state.tunerVisible) {
                item {
                    TunerOverlay(onDismiss = viewModel::toggleTuner)
                }
            }
        }
    }
}

@Composable
private fun PerformanceToolbar(state: MainUiState, viewModel: MainViewModel, modifier: Modifier = Modifier) {
    val selected = state.selected ?: return
    val metadata = selected.metadata
    Surface(
        modifier = modifier,
        color = Color(0xEE101418),
        contentColor = Color.White,
        tonalElevation = 4.dp,
        shape = MaterialTheme.shapes.medium
    ) {
        Column(Modifier.padding(horizontal = 6.dp, vertical = 4.dp)) {
            Row(
                modifier = Modifier.height(44.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                IconButton(onClick = viewModel::closeScore) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "라이브러리")
                }
                IconButton(onClick = viewModel::previousPage) {
                    Icon(Icons.Default.SkipPrevious, contentDescription = "이전 페이지")
                }
                IconButton(onClick = { if (state.autoTurnState.running) viewModel.stopAutoTurn() else viewModel.startAutoTurn() }) {
                    Icon(if (state.autoTurnState.running) Icons.Default.Pause else Icons.Default.PlayArrow, contentDescription = "자동 진행")
                }
                IconButton(onClick = viewModel::nextPage) {
                    Icon(Icons.Default.SkipNext, contentDescription = "다음 페이지")
                }
                Text("${metadata.bpm}", fontWeight = FontWeight.Bold, modifier = Modifier.width(38.dp))
                IconButton(onClick = viewModel::toggleTuner) {
                    Icon(Icons.Default.Tune, contentDescription = "기타 튜너")
                }
                IconButton(onClick = viewModel::toggleToolbarExpanded) {
                    Icon(Icons.Default.MoreVert, contentDescription = "상세 설정")
                }
            }
            if (state.toolbarExpanded) {
                ExpandedToolbarPanel(state = state, viewModel = viewModel)
            }
        }
    }
}

@Composable
private fun ExpandedToolbarPanel(state: MainUiState, viewModel: MainViewModel) {
    val selected = state.selected ?: return
    val metadata = selected.metadata
    Column(
        modifier = Modifier.width(520.dp).padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            IconButton(onClick = { viewModel.setBpm(metadata.bpm - 1) }) {
                Icon(Icons.Default.Remove, contentDescription = "BPM 낮추기")
            }
            Column(Modifier.weight(1f)) {
                Text("BPM ${metadata.bpm}", fontWeight = FontWeight.SemiBold)
                Slider(
                    value = metadata.bpm.toFloat(),
                    onValueChange = { viewModel.setBpm(it.toInt()) },
                    valueRange = 40f..220f
                )
            }
            IconButton(onClick = { viewModel.setBpm(metadata.bpm + 1) }) {
                Icon(Icons.Default.Add, contentDescription = "BPM 올리기")
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("박자", fontWeight = FontWeight.SemiBold)
            listOf(2, 3, 4, 5, 6, 7, 12).forEach { beats ->
                OutlinedButton(onClick = { viewModel.setTimeSignature(beats) }) {
                    Text(if (metadata.timeSignature == beats) "✓ ${beats}/4" else "${beats}/4")
                }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("진행", fontWeight = FontWeight.SemiBold)
            OutlinedButton(onClick = { viewModel.setProgressMode(ProgressMode.PageTurn) }) {
                Text(if (state.progressMode == ProgressMode.PageTurn) "✓ 페이지" else "페이지")
            }
            OutlinedButton(onClick = { viewModel.setProgressMode(ProgressMode.Scroll) }) {
                Text(if (state.progressMode == ProgressMode.Scroll) "✓ 스크롤" else "스크롤")
            }
            OutlinedButton(onClick = viewModel::recordCue) {
                Text("큐 기록")
            }
            IconButton(onClick = { viewModel.nudge(-1_000) }) {
                Icon(Icons.Default.FastRewind, contentDescription = "1초 당기기")
            }
            IconButton(onClick = { viewModel.nudge(1_000) }) {
                Icon(Icons.Default.FastForward, contentDescription = "1초 늦추기")
            }
            IconButton(onClick = viewModel::toggleFavorite) {
                Icon(if (selected.score.favorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder, contentDescription = "즐겨찾기")
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("한 줄", fontWeight = FontWeight.SemiBold)
            IconButton(onClick = { viewModel.setBarsPerLine(state.barsPerLine - 1) }) {
                Icon(Icons.Default.Remove, contentDescription = "한 줄 마디 수 줄이기")
            }
            Text("${state.barsPerLine}마디", modifier = Modifier.width(54.dp), fontWeight = FontWeight.SemiBold)
            IconButton(onClick = { viewModel.setBarsPerLine(state.barsPerLine + 1) }) {
                Icon(Icons.Default.Add, contentDescription = "한 줄 마디 수 늘리기")
            }
            Column(Modifier.weight(1f)) {
                Text("줄 이동 ${state.lineScrollDp}dp", style = MaterialTheme.typography.bodySmall)
                Slider(
                    value = state.lineScrollDp.toFloat(),
                    onValueChange = { viewModel.setLineScrollDp(it.toInt()) },
                    valueRange = 60f..520f
                )
            }
        }
        Text(
            "스크롤 모드: ${metadata.timeSignature}/4 기준 ${state.barsPerLine}마디마다 ${state.lineScrollDp}dp 이동",
            style = MaterialTheme.typography.bodySmall
        )
        if (state.cues.isNotEmpty()) {
            Text(
                "큐 ${state.cues.size}개 · ${state.cues.take(4).joinToString("  ") { cue -> "${cue.triggerBeat?.let { "%.1f".format(it) } ?: ((cue.triggerMillis ?: 0) / 1000).toString()}→P${cue.pageIndex + 1}" }}",
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

@Composable
private fun PagePill(state: MainUiState, modifier: Modifier = Modifier) {
    Surface(modifier = modifier, color = Color(0xCC101418), contentColor = Color.White, shape = MaterialTheme.shapes.medium) {
        Text(
            "Page ${state.pageIndex + 1} / ${state.pageCount} · ${"%.1f".format(state.autoTurnState.elapsedBeats)} beat · ${if (state.progressMode == ProgressMode.Scroll) "Scroll" else "Page"}",
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

@Composable
private fun AutoTurnPanel(state: MainUiState, viewModel: MainViewModel) {
    Card(colors = CardDefaults.cardColors(containerColor = Color(0xFFFFFFFF))) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("자동 넘김", fontWeight = FontWeight.SemiBold)
            Text("박자 ${"%.1f".format(state.autoTurnState.elapsedBeats)} · ${state.autoTurnState.elapsedMillis / 1000}s")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { if (state.autoTurnState.running) viewModel.stopAutoTurn() else viewModel.startAutoTurn() }) {
                    Icon(if (state.autoTurnState.running) Icons.Default.Pause else Icons.Default.PlayArrow, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text(if (state.autoTurnState.running) "정지" else "시작")
                }
                OutlinedButton(onClick = viewModel::recordCue) {
                    Icon(Icons.Default.SkipNext, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("큐 기록")
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { viewModel.nudge(-1_000) }) {
                    Icon(Icons.Default.FastRewind, contentDescription = null)
                    Text("-1s")
                }
                OutlinedButton(onClick = { viewModel.nudge(1_000) }) {
                    Icon(Icons.Default.FastForward, contentDescription = null)
                    Text("+1s")
                }
            }
        }
    }
}

@Composable
private fun CueList(state: MainUiState) {
    Card(colors = CardDefaults.cardColors(containerColor = Color.White)) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("저장된 큐", fontWeight = FontWeight.SemiBold)
            if (state.cues.isEmpty()) {
                Text("아직 큐가 없습니다.")
            } else {
                state.cues.forEach { cue ->
                    val trigger = cue.triggerBeat?.let { "${"%.1f".format(it)} beat" } ?: "${(cue.triggerMillis ?: 0) / 1000}s"
                    Text("$trigger -> Page ${cue.pageIndex + 1}")
                }
            }
        }
    }
}

@Composable
private fun TransportBar(state: MainUiState, viewModel: MainViewModel) {
    Surface(color = Color(0xFF101418), contentColor = Color.White) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            IconButton(onClick = viewModel::previousPage) {
                Icon(Icons.Default.SkipPrevious, contentDescription = "이전 페이지")
            }
            Text("Page ${state.pageIndex + 1} / ${state.pageCount}", modifier = Modifier.padding(horizontal = 18.dp))
            IconButton(onClick = viewModel::nextPage) {
                Icon(Icons.Default.SkipNext, contentDescription = "다음 페이지")
            }
            Spacer(Modifier.width(24.dp))
            TextButton(onClick = viewModel::recordCue) {
                Text("Cue", color = Color.White)
            }
        }
    }
}

@Composable
private fun TunerOverlay(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val engine = remember { TunerEngine(context.applicationContext) }
    val reading by engine.reading.collectAsState()
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) engine.start()
    }
    LaunchedEffect(Unit) {
        if (androidx.core.content.ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.RECORD_AUDIO
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            engine.start()
        } else {
            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }
    DisposableEffect(Unit) {
        onDispose { engine.stop() }
    }
    Box(
        Modifier
            .fillMaxSize()
            .background(Color(0x99000000))
            .clickable(onClick = onDismiss),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier.width(420.dp).clickable(enabled = false) {},
            colors = CardDefaults.cardColors(containerColor = Color(0xFFFDFBF7))
        ) {
            Column(
                Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("기타 튜너", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    TextButton(onClick = onDismiss) { Text("닫기") }
                }
                TunerGauge(cents = reading.cents, inTune = reading.inTune)
                Text(reading.targetNote, style = MaterialTheme.typography.displayLarge, fontWeight = FontWeight.Bold)
                Text(
                    when {
                        reading.error != null -> reading.error.orEmpty()
                        reading.frequency == 0f -> "현을 하나씩 튕겨 주세요"
                        reading.inTune -> "정확합니다"
                        reading.cents < 0 -> "낮습니다 · 조이세요"
                        else -> "높습니다 · 푸세요"
                    },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = if (reading.inTune) Color(0xFF2F7D4F) else Color(0xFF9A5A12)
                )
                Text("${reading.frequency.toInt()} Hz · ${reading.cents} cents · detected ${reading.note}")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    builtInTunings.forEach { preset ->
                        Surface(color = Color(0xFFECE7DA), shape = MaterialTheme.shapes.small) {
                            Text(preset.name, modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TunerGauge(cents: Int, inTune: Boolean) {
    val normalized = (cents.coerceIn(-50, 50) / 50f)
    Canvas(modifier = Modifier.fillMaxWidth().height(170.dp)) {
        val center = androidx.compose.ui.geometry.Offset(size.width / 2f, size.height * 0.88f)
        val radius = size.minDimension * 0.78f
        for (i in -5..5) {
            val angle = Math.toRadians((270 + i * 12).toDouble())
            val inner = radius * if (i == 0) 0.72f else 0.8f
            val outer = radius * 0.95f
            drawLine(
                color = if (i == 0) Color(0xFF2F7D4F) else Color(0xFF81796A),
                start = androidx.compose.ui.geometry.Offset(center.x + cos(angle).toFloat() * inner, center.y + sin(angle).toFloat() * inner),
                end = androidx.compose.ui.geometry.Offset(center.x + cos(angle).toFloat() * outer, center.y + sin(angle).toFloat() * outer),
                strokeWidth = if (i == 0) 6f else 3f,
                cap = StrokeCap.Round
            )
        }
        val needleAngle = Math.toRadians((270 + normalized * 60).toDouble())
        drawLine(
            color = if (inTune) Color(0xFF2F7D4F) else Color(0xFFD18B22),
            start = center,
            end = androidx.compose.ui.geometry.Offset(
                center.x + cos(needleAngle).toFloat() * radius * 0.72f,
                center.y + sin(needleAngle).toFloat() * radius * 0.72f
            ),
            strokeWidth = 8f,
            cap = StrokeCap.Round
        )
        drawCircle(color = Color(0xFF222222), radius = 11f, center = center)
    }
}
