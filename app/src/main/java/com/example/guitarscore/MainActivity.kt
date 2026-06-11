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
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.graphicsLayer
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
    val autoTurnState: AutoTurnState = AutoTurnState(),
    val metronomeRunning: Boolean = false,
    val tunerVisible: Boolean = false
)

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
    val selected = state.selected ?: return
    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(selected.score.title, maxLines = 1) },
                navigationIcon = {
                    IconButton(onClick = viewModel::closeScore) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "라이브러리")
                    }
                },
                actions = {
                    IconButton(onClick = viewModel::toggleFavorite) {
                        Icon(if (selected.score.favorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder, contentDescription = "즐겨찾기")
                    }
                    IconButton(onClick = viewModel::toggleTuner) {
                        Icon(Icons.Default.Tune, contentDescription = "튜너")
                    }
                }
            )
        },
        bottomBar = { TransportBar(state, viewModel) }
    ) { padding ->
        Row(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF171A1F))
                .padding(padding)
        ) {
            PdfPane(state, viewModel, modifier = Modifier.weight(1f))
            ControlPane(state, viewModel, modifier = Modifier.width(320.dp).fillMaxHeight())
        }
    }
}

@Composable
private fun PdfPane(state: MainUiState, viewModel: MainViewModel, modifier: Modifier = Modifier) {
    var scale by remember { mutableFloatStateOf(1f) }
    Box(modifier = modifier.fillMaxHeight(), contentAlignment = Alignment.Center) {
        state.pageBitmap?.let { bitmap ->
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = "PDF page",
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer(scaleX = scale, scaleY = scale)
                    .pointerInput(Unit) {
                        detectTransformGestures { _, _, zoom, _ ->
                            scale = (scale * zoom).coerceIn(0.75f, 3f)
                        }
                    }
            )
        } ?: Text("PDF 렌더링 중...", color = Color.White)
        Row(Modifier.fillMaxSize()) {
            Box(Modifier.weight(1f).fillMaxHeight().clickable { viewModel.previousPage() })
            Box(Modifier.weight(1f).fillMaxHeight().clickable { viewModel.nextPage() })
        }
    }
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
                    TunerPanel()
                }
            }
        }
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
private fun TunerPanel() {
    val context = LocalContext.current
    val engine = remember { TunerEngine(context.applicationContext) }
    val reading by engine.reading.collectAsState()
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) engine.start()
    }
    LaunchedEffect(Unit) {
        permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        engine.start()
    }
    DisposableEffect(Unit) {
        onDispose { engine.stop() }
    }
    Card(colors = CardDefaults.cardColors(containerColor = Color.White)) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("튜너", fontWeight = FontWeight.SemiBold)
            Text(reading.note, style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold)
            Text("${reading.frequency.toInt()} Hz · ${reading.cents} cents")
            Text(if (reading.inTune) "In tune" else "Adjust")
            Text("프리셋: ${builtInTunings.joinToString { it.name }}", style = MaterialTheme.typography.bodySmall)
        }
    }
}
