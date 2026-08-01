package com.example.guitarscore

import android.Manifest
import android.net.Uri
import android.os.Bundle
import android.view.KeyEvent
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Snackbar
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.activity.compose.BackHandler
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.guitarscore.audio.AutoTurnEngine
import com.example.guitarscore.audio.AutoTurnState
import com.example.guitarscore.audio.MetronomeEngine
import com.example.guitarscore.audio.TunerEngine
import com.example.guitarscore.audio.builtInTunings
import com.example.guitarscore.chord.ChordDiagram
import com.example.guitarscore.chord.ChordTrainerScreen
import com.example.guitarscore.chord.ScoreChordHelperSidebar
import com.example.guitarscore.chord.chordDefinition
import com.example.guitarscore.chord.generateVoicings
import com.example.guitarscore.data.FolderEntity
import com.example.guitarscore.data.ScoreChordEntity
import com.example.guitarscore.data.ScoreEntity
import com.example.guitarscore.data.ScoreMetadataEntity
import com.example.guitarscore.data.ScoreRepository
import com.example.guitarscore.data.ScoreWithMetadata
import com.example.guitarscore.data.TurnCueEntity
import com.example.guitarscore.score.PdfPageRenderer
import com.example.guitarscore.score.renderPdfThumbnail
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

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

    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean {
        when (keyCode) {
            KeyEvent.KEYCODE_PAGE_DOWN, KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_SPACE -> {
                viewModel.nextPage()
                return true
            }
            KeyEvent.KEYCODE_PAGE_UP, KeyEvent.KEYCODE_DPAD_LEFT -> {
                viewModel.previousPage()
                return true
            }
        }
        return super.onKeyUp(keyCode, event)
    }
}

data class MainUiState(
    val scores: List<ScoreEntity> = emptyList(),
    val folders: List<FolderEntity> = emptyList(),
    val thumbnails: Map<Long, android.graphics.Bitmap> = emptyMap(),
    val destination: AppDestination = AppDestination.Library,
    val sidebarVisible: Boolean = true,
    val selectedFolderId: Long? = null,
    val favoritesOnly: Boolean = false,
    val searchQuery: String = "",
    val selected: ScoreWithMetadata? = null,
    val cues: List<TurnCueEntity> = emptyList(),
    val scoreChords: List<ScoreChordEntity> = emptyList(),
    val pageIndex: Int = 0,
    val pageCount: Int = 0,
    val pageBitmap: android.graphics.Bitmap? = null,
    val pageAspectRatio: Float = 1.414f,
    val scrollPageBitmaps: Map<Int, android.graphics.Bitmap> = emptyMap(),
    val autoTurnState: AutoTurnState = AutoTurnState(),
    val metronomeRunning: Boolean = false,
    val metronomeMuted: Boolean = false,
    val tunerVisible: Boolean = false,
    val chordHelperVisible: Boolean = false,
    val quickChordName: String? = null,
    val toolbarMode: ToolbarMode = ToolbarMode.Collapsed,
    val progressMode: ProgressMode = ProgressMode.PageTurn,
    val barsPerLine: Int = 4,
    val linesPerPage: Int = 5,
    val lineScrollDp: Int = 180,
    val generatedScrollCues: List<ScrollCue> = emptyList(),
    val message: String? = null
)

enum class AppDestination { Library, Chords }

enum class ProgressMode { PageTurn, Scroll }

/** 악보를 가리지 않도록 도구 모음은 접힌 상태가 기본이다. */
enum class ToolbarMode { Collapsed, Bar, Expanded }

data class ScrollCue(
    val lineIndex: Int,
    val pageIndex: Int,
    val triggerBeat: Float,
    val scrollDp: Int
)

class MainViewModel(private val repository: ScoreRepository) : ViewModel() {
    private val _uiState = MutableStateFlow(MainUiState())
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()
    private val autoTurn = AutoTurnEngine()
    private val metronome = MetronomeEngine()
    private var cueJob: Job? = null
    private var scoreChordJob: Job? = null
    private var pageSaveJob: Job? = null
    private var scrollRenderJob: Job? = null
    private var pdfRenderer: PdfPageRenderer? = null
    private val thumbnailJobs = mutableSetOf<Long>()

    // 썸네일을 한꺼번에 렌더하면 PDF 를 수십 개 동시에 열게 되어 메모리가 터진다.
    private val thumbnailGate = Semaphore(2)

    init {
        viewModelScope.launch {
            repository.observeScores().collect { scores ->
                _uiState.value = _uiState.value.copy(scores = scores)
            }
        }
        viewModelScope.launch {
            repository.observeFolders().collect { folders ->
                val selectedFolderId = _uiState.value.selectedFolderId
                    ?.takeIf { selected -> folders.any { it.id == selected } }
                _uiState.value = _uiState.value.copy(folders = folders, selectedFolderId = selectedFolderId)
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        autoTurn.dispose()
        metronome.stop()
        pdfRenderer?.close()
        pdfRenderer = null
    }

    fun dismissMessage() = _uiState.run { value = value.copy(message = null) }

    fun importPdf(context: android.content.Context, uri: Uri, folderId: Long? = _uiState.value.selectedFolderId) {
        viewModelScope.launch {
            val id = runCatching { repository.addPdf(context.contentResolver, uri, folderId) }.getOrElse {
                _uiState.value = _uiState.value.copy(message = "PDF 를 가져오지 못했습니다.")
                return@launch
            }
            openScore(context, id)
        }
    }

    fun ensureThumbnails(context: android.content.Context) {
        val state = _uiState.value
        state.scores.forEach { score ->
            if (score.id in state.thumbnails || !thumbnailJobs.add(score.id)) return@forEach
            viewModelScope.launch {
                val thumbnail = thumbnailGate.withPermit {
                    runCatching {
                        renderPdfThumbnail(context.applicationContext, Uri.parse(score.pdfUri))
                    }.getOrNull()
                }
                thumbnailJobs.remove(score.id)
                if (thumbnail != null) {
                    _uiState.value = _uiState.value.copy(
                        thumbnails = _uiState.value.thumbnails + (score.id to thumbnail)
                    )
                }
            }
        }
    }

    fun toggleSidebar() = _uiState.run { value = value.copy(sidebarVisible = !value.sidebarVisible) }

    fun selectAllScores() = _uiState.run {
        value = value.copy(selectedFolderId = null, favoritesOnly = false)
    }

    fun selectFavorites() = _uiState.run {
        value = value.copy(selectedFolderId = null, favoritesOnly = true)
    }

    fun selectFolder(folderId: Long) = _uiState.run {
        value = value.copy(selectedFolderId = folderId, favoritesOnly = false)
    }

    fun setSearchQuery(query: String) = _uiState.run { value = value.copy(searchQuery = query) }

    fun showChordTrainer() = _uiState.run { value = value.copy(destination = AppDestination.Chords) }
    fun showLibrary() = _uiState.run { value = value.copy(destination = AppDestination.Library) }

    fun addFolder(name: String) {
        if (name.isBlank()) return
        viewModelScope.launch { repository.addFolder(name) }
    }

    fun renameFolder(folder: FolderEntity, name: String) {
        if (name.isBlank()) return
        viewModelScope.launch {
            repository.updateFolder(folder.copy(name = name.trim(), updatedAt = System.currentTimeMillis()))
        }
    }

    fun deleteFolder(folderId: Long) {
        viewModelScope.launch { repository.deleteFolder(folderId) }
    }

    fun updateScoreDetails(score: ScoreEntity, title: String, artist: String, folderId: Long?) {
        if (title.isBlank()) return
        viewModelScope.launch {
            repository.updateScore(
                score.copy(
                    title = title.trim(),
                    artist = artist.trim(),
                    folderId = folderId,
                    updatedAt = System.currentTimeMillis()
                )
            )
        }
    }

    fun deleteScore(context: android.content.Context, score: ScoreEntity) {
        viewModelScope.launch {
            repository.deleteScore(context.contentResolver, score)
            _uiState.value = _uiState.value.copy(
                thumbnails = _uiState.value.thumbnails - score.id,
                message = "'${score.title}' 을(를) 삭제했습니다."
            )
        }
    }

    fun toggleScoreFavorite(score: ScoreEntity) {
        viewModelScope.launch {
            repository.updateScore(
                score.copy(favorite = !score.favorite, updatedAt = System.currentTimeMillis())
            )
        }
    }

    fun openScore(context: android.content.Context, id: Long) {
        viewModelScope.launch {
            releaseScoreResources()
            val selected = repository.loadScore(id) ?: return@launch
            val renderer = PdfPageRenderer(context.applicationContext, Uri.parse(selected.score.pdfUri))
            val pageCount = runCatching { renderer.open() }.getOrElse {
                renderer.close()
                _uiState.value = _uiState.value.copy(
                    message = "'${selected.score.title}' 을(를) 열 수 없습니다. 파일이 이동되었거나 삭제되었을 수 있습니다."
                )
                return@launch
            }
            pdfRenderer = renderer
            val pageIndex = selected.score.lastOpenedPage.coerceIn(0, (pageCount - 1).coerceAtLeast(0))
            _uiState.value = _uiState.value.copy(
                selected = selected,
                destination = AppDestination.Library,
                pageIndex = pageIndex,
                pageCount = pageCount,
                pageBitmap = null,
                scrollPageBitmaps = emptyMap(),
                generatedScrollCues = emptyList(),
                scoreChords = emptyList(),
                chordHelperVisible = false,
                quickChordName = null,
                toolbarMode = ToolbarMode.Collapsed,
                metronomeRunning = false,
                autoTurnState = AutoTurnState()
            )
            cueJob = viewModelScope.launch {
                repository.observeCues(id).collect { cues -> _uiState.value = _uiState.value.copy(cues = cues) }
            }
            scoreChordJob = viewModelScope.launch {
                repository.observeScoreChords(id).collect { chords ->
                    _uiState.value = _uiState.value.copy(scoreChords = chords)
                }
            }
            renderCurrentPage()
        }
    }

    private fun releaseScoreResources() {
        autoTurn.stop()
        metronome.stop()
        cueJob?.cancel()
        scoreChordJob?.cancel()
        pageSaveJob?.cancel()
        scrollRenderJob?.cancel()
        pdfRenderer?.close()
        pdfRenderer = null
    }

    fun closeScore() {
        releaseScoreResources()
        val state = _uiState.value
        _uiState.value = MainUiState(
            scores = state.scores,
            folders = state.folders,
            thumbnails = state.thumbnails,
            sidebarVisible = state.sidebarVisible,
            selectedFolderId = state.selectedFolderId,
            favoritesOnly = state.favoritesOnly,
            searchQuery = state.searchQuery
        )
    }

    fun nextPage() = movePage(1)
    fun previousPage() = movePage(-1)

    private fun movePage(delta: Int) {
        val state = _uiState.value
        if (state.selected == null) return
        val next = (state.pageIndex + delta).coerceIn(0, (state.pageCount - 1).coerceAtLeast(0))
        if (next == state.pageIndex) return
        _uiState.value = state.copy(pageIndex = next)
        viewModelScope.launch { renderCurrentPage() }
        persistLastPage(next)
    }

    /** 페이지를 넘길 때마다 DB 를 건드리면 연주 중에 끊길 수 있어 잠시 모았다가 저장한다. */
    private fun persistLastPage(page: Int) {
        pageSaveJob?.cancel()
        pageSaveJob = viewModelScope.launch {
            delay(700)
            val score = _uiState.value.selected?.score ?: return@launch
            repository.updateScore(score.copy(lastOpenedPage = page, updatedAt = System.currentTimeMillis()))
        }
    }

    fun setBpm(bpm: Int) {
        val selected = _uiState.value.selected ?: return
        val metadata = selected.metadata.copy(bpm = bpm.coerceIn(40, 220))
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
        viewModelScope.launch {
            repository.addCue(cue)
            _uiState.value = _uiState.value.copy(message = "${state.pageIndex + 2}페이지 넘김 큐를 기록했습니다.")
        }
    }

    fun deleteCue(cueId: Long) {
        viewModelScope.launch { repository.deleteCue(cueId) }
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
        metronome.start(metadata.bpm, metadata.timeSignature, muted = state.metronomeMuted) { _, _ -> }
        // 연주가 시작되면 악보를 최대한 넓게 쓰도록 도구 모음을 접는다.
        _uiState.value = _uiState.value.copy(metronomeRunning = true, toolbarMode = ToolbarMode.Collapsed)
    }

    fun stopAutoTurn() {
        autoTurn.stop()
        metronome.stop()
        _uiState.value = _uiState.value.copy(autoTurnState = AutoTurnState(), metronomeRunning = false)
    }

    fun toggleAutoTurnPause() {
        val state = _uiState.value
        if (!state.autoTurnState.running) return
        val paused = !state.autoTurnState.paused
        autoTurn.setPaused(paused)
        metronome.setMuted(paused || state.metronomeMuted)
        _uiState.value = state.copy(autoTurnState = state.autoTurnState.copy(paused = paused))
    }

    fun toggleMetronomeMuted() {
        val muted = !_uiState.value.metronomeMuted
        metronome.setMuted(muted)
        _uiState.value = _uiState.value.copy(metronomeMuted = muted)
    }

    fun nudge(deltaMillis: Long) = autoTurn.nudge(deltaMillis)
    fun toggleTuner() = _uiState.run { value = value.copy(tunerVisible = !value.tunerVisible) }
    fun toggleChordHelper() = _uiState.run {
        value = value.copy(chordHelperVisible = !value.chordHelperVisible, quickChordName = null)
    }

    fun showQuickChord(name: String?) = _uiState.run {
        value = value.copy(quickChordName = if (value.quickChordName == name) null else name)
    }

    fun addScoreChord(chordName: String) {
        val scoreId = _uiState.value.selected?.score?.id ?: return
        viewModelScope.launch { repository.addScoreChord(ScoreChordEntity(scoreId, chordName)) }
    }

    fun removeScoreChord(chordName: String) {
        val scoreId = _uiState.value.selected?.score?.id ?: return
        viewModelScope.launch { repository.deleteScoreChord(scoreId, chordName) }
        if (_uiState.value.quickChordName == chordName) {
            _uiState.value = _uiState.value.copy(quickChordName = null)
        }
    }

    fun setToolbarMode(mode: ToolbarMode) = _uiState.run { value = value.copy(toolbarMode = mode) }

    fun setProgressMode(mode: ProgressMode) {
        val state = _uiState.value
        if (state.progressMode == mode) return
        _uiState.value = state.copy(progressMode = mode)
        // 모드를 오갈 때 이전 페이지 비트맵이 남아 있으면 엉뚱한 페이지가 보인다.
        viewModelScope.launch {
            if (mode == ProgressMode.PageTurn) renderCurrentPage() else ensureScrollPages(state.pageIndex)
        }
    }

    fun setBarsPerLine(bars: Int) = _uiState.run { value = value.copy(barsPerLine = bars.coerceIn(1, 8)) }
    fun setLinesPerPage(lines: Int) = _uiState.run { value = value.copy(linesPerPage = lines.coerceIn(1, 12)) }
    fun setLineScrollDp(dp: Int) = _uiState.run { value = value.copy(lineScrollDp = dp.coerceIn(60, 520)) }

    fun generateScrollCues() {
        val state = _uiState.value
        val selected = state.selected ?: return
        val totalLines = (state.pageCount * state.linesPerPage).coerceAtLeast(1)
        val beatsPerLine = (selected.metadata.timeSignature * state.barsPerLine).coerceAtLeast(1)
        val cues = (0 until totalLines).map { line ->
            ScrollCue(
                lineIndex = line,
                pageIndex = (line / state.linesPerPage).coerceIn(0, (state.pageCount - 1).coerceAtLeast(0)),
                triggerBeat = (line * beatsPerLine).toFloat(),
                scrollDp = line * state.lineScrollDp
            )
        }
        _uiState.value = state.copy(generatedScrollCues = cues, progressMode = ProgressMode.Scroll)
        viewModelScope.launch { ensureScrollPages(state.pageIndex) }
    }

    fun goToPageFromScroll(page: Int) {
        val state = _uiState.value
        val next = page.coerceIn(0, (state.pageCount - 1).coerceAtLeast(0))
        if (next != state.pageIndex) {
            _uiState.value = state.copy(pageIndex = next)
            persistLastPage(next)
        }
    }

    /**
     * 스크롤 모드에서 보이는 페이지 주변만 렌더한다. 전체 페이지를 한꺼번에 올리면
     * 20페이지짜리 악보에서 수백 MB 를 쓰게 되어 바로 OOM 이 난다.
     */
    fun ensureScrollPages(centerPage: Int) {
        val state = _uiState.value
        if (state.pageCount == 0) return
        val window = (centerPage - 1..centerPage + 1)
            .filter { it in 0 until state.pageCount }
            .toSet()
        if (state.scrollPageBitmaps.keys.containsAll(window)) return
        if (scrollRenderJob?.isActive == true) return
        scrollRenderJob = viewModelScope.launch {
            val renderer = pdfRenderer ?: return@launch
            val rendered = linkedMapOf<Int, android.graphics.Bitmap>()
            window.forEach { page ->
                runCatching { renderer.renderPage(page, PAGE_RENDER_WIDTH) }.getOrNull()?.let { rendered[page] = it }
            }
            runCatching { renderer.trimTo(window) }
            _uiState.value = _uiState.value.copy(
                scrollPageBitmaps = rendered.toMap(),
                pageAspectRatio = rendered.values.firstOrNull()?.let { it.height.toFloat() / it.width }
                    ?: _uiState.value.pageAspectRatio
            )
        }
    }

    private suspend fun goToPage(page: Int) {
        val next = page.coerceIn(0, (_uiState.value.pageCount - 1).coerceAtLeast(0))
        if (next == _uiState.value.pageIndex) return
        _uiState.value = _uiState.value.copy(pageIndex = next)
        renderCurrentPage()
        persistLastPage(next)
    }

    private suspend fun renderCurrentPage() {
        val renderer = pdfRenderer ?: return
        val bitmap = runCatching { renderer.renderPage(_uiState.value.pageIndex, PAGE_RENDER_WIDTH) }
            .getOrElse {
                _uiState.value = _uiState.value.copy(message = "페이지를 그릴 수 없습니다.")
                return
            }
        _uiState.value = _uiState.value.copy(
            pageBitmap = bitmap,
            pageAspectRatio = bitmap.height.toFloat() / bitmap.width
        )
    }

    companion object {
        private const val PAGE_RENDER_WIDTH = 1_400

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
            primary = Color(0xFF3559D9),
            onPrimary = Color.White,
            secondary = Color(0xFF008F95),
            tertiary = Color(0xFFB23A48),
            surface = Color(0xFFFFFFFF),
            surfaceVariant = Color(0xFFE8EBF2),
            surfaceContainerLowest = Color(0xFFFFFFFF),
            surfaceContainerLow = Color(0xFFF8F9FC),
            surfaceContainer = Color(0xFFF1F3F8),
            surfaceContainerHigh = Color(0xFFE9ECF3),
            surfaceContainerHighest = Color(0xFFE2E6EF),
            background = Color(0xFFF4F6FA),
            onSurface = Color(0xFF1B1F2A),
            onSurfaceVariant = Color(0xFF5E6472),
            outline = Color(0xFFC5CAD5)
        ),
        content = content
    )
}

@Composable
fun GuitarScoreAppUi(viewModel: MainViewModel) {
    val state by viewModel.uiState.collectAsState()
    Box(Modifier.fillMaxSize()) {
        when {
            state.selected != null -> ViewerScreen(state, viewModel)
            state.destination == AppDestination.Chords -> {
                BackHandler(onBack = viewModel::showLibrary)
                ChordTrainerScreen(onBack = viewModel::showLibrary)
            }
            else -> LibraryScreen(state, viewModel)
        }
        state.message?.let { message ->
            LaunchedEffect(message) {
                delay(3_000)
                viewModel.dismissMessage()
            }
            Snackbar(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(16.dp)
                    .widthIn(max = 460.dp),
                action = { TextButton(onClick = viewModel::dismissMessage) { Text("닫기") } }
            ) { Text(message) }
        }
    }
}

@Composable
private fun ViewerScreen(state: MainUiState, viewModel: MainViewModel) {
    BackHandler {
        when {
            state.tunerVisible -> viewModel.toggleTuner()
            state.chordHelperVisible -> viewModel.toggleChordHelper()
            state.quickChordName != null -> viewModel.showQuickChord(null)
            state.toolbarMode != ToolbarMode.Collapsed -> viewModel.setToolbarMode(ToolbarMode.Collapsed)
            else -> viewModel.closeScore()
        }
    }
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
                .align(Alignment.TopEnd)
                .padding(8.dp)
        )
        if (!state.chordHelperVisible) {
            QuickChordPanel(
                state = state,
                viewModel = viewModel,
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 4.dp)
            )
        }
        PagePill(
            state = state,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 10.dp)
        )
        if (state.chordHelperVisible) {
            ScoreChordHelperSidebar(
                savedChordNames = state.scoreChords.map { it.chordName },
                onDismiss = viewModel::toggleChordHelper,
                onAdd = viewModel::addScoreChord,
                onRemove = viewModel::removeScoreChord,
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .width(390.dp)
                    .fillMaxHeight()
            )
        }
        if (state.tunerVisible) {
            TunerOverlay(onDismiss = viewModel::toggleTuner)
        }
    }
}

@Composable
private fun PdfPane(state: MainUiState, viewModel: MainViewModel, modifier: Modifier = Modifier) {
    if (state.progressMode == ProgressMode.Scroll) {
        ScrollScorePane(state = state, viewModel = viewModel, modifier = modifier)
        return
    }
    var scale by remember { mutableFloatStateOf(1f) }
    val verticalScrollState = rememberScrollState()
    val horizontalScrollState = rememberScrollState()
    val screenWidth = LocalConfiguration.current.screenWidthDp.dp
    Box(modifier = modifier.fillMaxHeight(), contentAlignment = Alignment.Center) {
        state.pageBitmap?.let { bitmap ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(verticalScrollState)
                    .horizontalScroll(horizontalScrollState)
                    // 화면 절반씩을 탭 영역으로 쓰면 악보를 보다가 실수로 페이지가 넘어간다.
                    // 양쪽 가장자리 20% 만 페이지 넘김에 쓰고 가운데는 비워 둔다.
                    .pointerInput(Unit) {
                        detectTapGestures { offset ->
                            when {
                                offset.x < size.width * 0.2f -> viewModel.previousPage()
                                offset.x > size.width * 0.8f -> viewModel.nextPage()
                            }
                        }
                    }
                    .pointerInput(Unit) {
                        detectTransformGestures { _, _, zoom, _ ->
                            scale = (scale * zoom).coerceIn(1f, 3f)
                        }
                    }
            ) {
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = "악보 ${state.pageIndex + 1}페이지",
                    contentScale = ContentScale.FillWidth,
                    // graphicsLayer 로 키우면 레이아웃 크기는 그대로라 확대 후 가장자리를 볼 수 없다.
                    // 폭 자체를 늘리고 스크롤로 이동하게 한다.
                    modifier = Modifier.width(screenWidth * scale)
                )
            }
        } ?: Text("PDF 렌더링 중...", color = Color.White)
    }
}

@Composable
private fun ScrollScorePane(
    state: MainUiState,
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()
    val density = LocalDensity.current
    val lineScrollPx = with(density) { state.lineScrollDp.dp.roundToPx() }
    val currentLineIndex = autoScrollLineIndex(state)

    LaunchedEffect(state.pageIndex, state.pageCount) {
        viewModel.ensureScrollPages(state.pageIndex)
    }
    LaunchedEffect(currentLineIndex, state.autoTurnState.running, state.autoTurnState.paused, scrollState.maxValue, lineScrollPx) {
        if (state.autoTurnState.running && !state.autoTurnState.paused && scrollState.maxValue > 0) {
            val target = (currentLineIndex * lineScrollPx).coerceIn(0, scrollState.maxValue)
            scrollState.animateScrollTo(target)
        }
    }
    LaunchedEffect(scrollState.maxValue, state.pageCount) {
        if (scrollState.maxValue <= 0 || state.pageCount <= 0) return@LaunchedEffect
        snapshotFlow { scrollState.value }.collect { value ->
            val page = ((value.toFloat() / scrollState.maxValue) * state.pageCount).toInt()
                .coerceIn(0, state.pageCount - 1)
            if (page != state.pageIndex) viewModel.goToPageFromScroll(page)
        }
    }

    Box(modifier = modifier.fillMaxHeight(), contentAlignment = Alignment.TopCenter) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(top = 12.dp, bottom = 44.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // 페이지 자리는 항상 잡아 두고 그림만 창 안에서 채운다. 그래야 스크롤 위치가 흔들리지 않으면서
            // 메모리에 올라가는 비트맵은 3장으로 유지된다.
            for (page in 0 until state.pageCount) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.94f)
                        .aspectRatio(1f / state.pageAspectRatio)
                        .background(Color(0xFF23262C)),
                    contentAlignment = Alignment.Center
                ) {
                    val bitmap = state.scrollPageBitmaps[page]
                    if (bitmap != null) {
                        Image(
                            bitmap = bitmap.asImageBitmap(),
                            contentDescription = "악보 ${page + 1}페이지",
                            contentScale = ContentScale.FillWidth,
                            modifier = Modifier.fillMaxWidth().background(Color.White)
                        )
                    } else {
                        Text("${page + 1}", color = Color(0xFF6C7382), fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

private fun autoScrollLineIndex(state: MainUiState): Int {
    if (state.generatedScrollCues.isNotEmpty()) {
        return state.generatedScrollCues
            .lastOrNull { cue -> state.autoTurnState.elapsedBeats >= cue.triggerBeat }
            ?.lineIndex
            ?: 0
    }
    val beatsPerLine = (state.selected?.metadata?.timeSignature ?: 4) * state.barsPerLine
    if (beatsPerLine <= 0) return 0
    return (state.autoTurnState.elapsedBeats / beatsPerLine).toInt().coerceAtLeast(0)
}

@Composable
private fun PerformanceToolbar(state: MainUiState, viewModel: MainViewModel, modifier: Modifier = Modifier) {
    val selected = state.selected ?: return
    val metadata = selected.metadata
    if (state.toolbarMode == ToolbarMode.Collapsed) {
        CollapsedToolbarButton(
            running = state.autoTurnState.running,
            onClick = { viewModel.setToolbarMode(ToolbarMode.Bar) },
            modifier = modifier
        )
        return
    }
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
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "라이브러리로 나가기")
                }
                IconButton(onClick = viewModel::previousPage) {
                    Icon(Icons.Default.SkipPrevious, contentDescription = "이전 페이지")
                }
                IconButton(
                    onClick = {
                        if (state.autoTurnState.running) viewModel.toggleAutoTurnPause() else viewModel.startAutoTurn()
                    }
                ) {
                    Icon(
                        if (state.autoTurnState.running && !state.autoTurnState.paused) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (state.autoTurnState.running) "일시정지" else "자동 진행 시작"
                    )
                }
                if (state.autoTurnState.running) {
                    IconButton(onClick = viewModel::stopAutoTurn) {
                        Icon(Icons.Default.Stop, contentDescription = "자동 진행 정지")
                    }
                }
                IconButton(onClick = viewModel::nextPage) {
                    Icon(Icons.Default.SkipNext, contentDescription = "다음 페이지")
                }
                Text("${metadata.bpm}", fontWeight = FontWeight.Bold, modifier = Modifier.width(38.dp))
                IconButton(onClick = viewModel::toggleMetronomeMuted) {
                    Icon(
                        if (state.metronomeMuted) Icons.AutoMirrored.Filled.VolumeOff else Icons.AutoMirrored.Filled.VolumeUp,
                        contentDescription = if (state.metronomeMuted) "메트로놈 소리 켜기" else "메트로놈 소리 끄기"
                    )
                }
                IconButton(onClick = viewModel::toggleTuner) {
                    Icon(Icons.Default.GraphicEq, contentDescription = "기타 튜너")
                }
                IconButton(
                    onClick = {
                        viewModel.setToolbarMode(
                            if (state.toolbarMode == ToolbarMode.Expanded) ToolbarMode.Bar else ToolbarMode.Expanded
                        )
                    }
                ) {
                    Icon(Icons.Default.Tune, contentDescription = "상세 설정")
                }
                IconButton(onClick = { viewModel.setToolbarMode(ToolbarMode.Collapsed) }) {
                    Icon(Icons.Default.ExpandLess, contentDescription = "도구 모음 접기")
                }
            }
            if (state.toolbarMode == ToolbarMode.Expanded) {
                ExpandedToolbarPanel(state = state, viewModel = viewModel)
            }
        }
    }
}

/** 접힌 상태의 도구 모음. 악보 상단을 가리지 않도록 아이콘 하나만 떠 있는다. */
@Composable
private fun CollapsedToolbarButton(running: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.size(44.dp).clickable(onClick = onClick),
        shape = CircleShape,
        color = if (running) Color(0xCC3559D9) else Color(0x99101418),
        contentColor = Color.White
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(Icons.Default.MoreHoriz, contentDescription = "연주 도구 열기")
        }
    }
}

@Composable
private fun ExpandedToolbarPanel(state: MainUiState, viewModel: MainViewModel) {
    val selected = state.selected ?: return
    val metadata = selected.metadata
    Column(
        modifier = Modifier
            .width(520.dp)
            .heightIn(max = 460.dp)
            .verticalScroll(rememberScrollState())
            .padding(8.dp),
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
                    onValueChange = { viewModel.setBpm(it.roundToInt()) },
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
                Icon(
                    if (selected.score.favorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                    contentDescription = "즐겨찾기"
                )
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
                    onValueChange = { viewModel.setLineScrollDp(it.roundToInt()) },
                    valueRange = 60f..520f
                )
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("페이지", fontWeight = FontWeight.SemiBold)
            IconButton(onClick = { viewModel.setLinesPerPage(state.linesPerPage - 1) }) {
                Icon(Icons.Default.Remove, contentDescription = "페이지당 줄 수 줄이기")
            }
            Text("${state.linesPerPage}줄", modifier = Modifier.width(42.dp), fontWeight = FontWeight.SemiBold)
            IconButton(onClick = { viewModel.setLinesPerPage(state.linesPerPage + 1) }) {
                Icon(Icons.Default.Add, contentDescription = "페이지당 줄 수 늘리기")
            }
            Button(onClick = viewModel::generateScrollCues) {
                Text("스크롤 큐 생성")
            }
        }
        Text(
            "스크롤 모드: ${metadata.timeSignature}/4 기준 ${state.barsPerLine}마디마다 ${state.lineScrollDp}dp 이동 · 페이지당 ${state.linesPerPage}줄",
            style = MaterialTheme.typography.bodySmall
        )
        if (state.generatedScrollCues.isNotEmpty()) {
            Text("생성 큐 ${state.generatedScrollCues.size}개", style = MaterialTheme.typography.bodySmall)
        }
        if (state.cues.isNotEmpty()) {
            Text("저장된 큐 ${state.cues.size}개", fontWeight = FontWeight.SemiBold)
            state.cues.forEach { cue ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val trigger = cue.triggerBeat?.let { "${"%.1f".format(it)} beat" }
                        ?: "${(cue.triggerMillis ?: 0) / 1000}초"
                    Text("$trigger → ${cue.pageIndex + 1}페이지", modifier = Modifier.weight(1f))
                    IconButton(onClick = { viewModel.deleteCue(cue.id) }, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.Close, contentDescription = "큐 삭제", modifier = Modifier.size(16.dp))
                    }
                }
            }
        }
    }
}

/**
 * 악보 오른쪽 가장자리의 코드 빠르게 보기. 저장해 둔 코드 이름만 얇게 세워 두고,
 * 이름을 누르면 작은 다이어그램이 옆에 뜬다. 악보를 거의 가리지 않는 것이 목적이다.
 */
@Composable
private fun QuickChordPanel(state: MainUiState, viewModel: MainViewModel, modifier: Modifier = Modifier) {
    val names = state.scoreChords.map { it.chordName }
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        state.quickChordName?.let { name ->
            val voicing = remember(name) { chordDefinition(name)?.let { generateVoicings(it).firstOrNull() } }
            Card(
                modifier = Modifier.width(224.dp).padding(end = 6.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xF2FFFFFF)),
                elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
            ) {
                Column(Modifier.padding(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                        IconButton(onClick = { viewModel.showQuickChord(null) }, modifier = Modifier.size(28.dp)) {
                            Icon(Icons.Default.Close, contentDescription = "닫기", modifier = Modifier.size(16.dp))
                        }
                    }
                    if (voicing != null) {
                        Text(voicing.label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        ChordDiagram(voicing, Modifier.fillMaxWidth().height(112.dp), showStringNames = false)
                    } else {
                        Text("운지 정보를 찾을 수 없습니다.", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
        Surface(
            color = Color(0xCC101418),
            contentColor = Color.White,
            shape = RoundedCornerShape(topStart = 10.dp, bottomStart = 10.dp)
        ) {
            Column(
                modifier = Modifier.width(46.dp).padding(vertical = 6.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                names.take(10).forEach { name ->
                    val active = state.quickChordName == name
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { viewModel.showQuickChord(name) },
                        color = if (active) MaterialTheme.colorScheme.primary else Color.Transparent,
                        contentColor = Color.White,
                        shape = RoundedCornerShape(5.dp)
                    ) {
                        Text(
                            name,
                            modifier = Modifier.padding(vertical = 7.dp),
                            textAlign = TextAlign.Center,
                            maxLines = 1,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                IconButton(onClick = viewModel::toggleChordHelper, modifier = Modifier.size(34.dp)) {
                    Icon(
                        if (names.isEmpty()) Icons.Default.Add else Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                        contentDescription = "코드 도우미 열기",
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun PagePill(state: MainUiState, modifier: Modifier = Modifier) {
    Surface(modifier = modifier, color = Color(0xCC101418), contentColor = Color.White, shape = MaterialTheme.shapes.medium) {
        val suffix = when {
            !state.autoTurnState.running -> ""
            state.autoTurnState.paused -> " · 일시정지"
            else -> " · ${"%.1f".format(state.autoTurnState.elapsedBeats)} beat"
        }
        Text(
            "${state.pageIndex + 1} / ${state.pageCount}$suffix",
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

@Composable
fun TunerOverlay(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val engine = remember { TunerEngine(context.applicationContext) }
    var selectedPreset by remember { mutableStateOf(builtInTunings.first()) }
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
        onDispose { engine.dispose() }
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
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
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
                    color = if (reading.inTune) Color(0xFF16836B) else Color(0xFFB26A00)
                )
                if (reading.permissionRequired) {
                    Button(onClick = { permissionLauncher.launch(Manifest.permission.RECORD_AUDIO) }) {
                        Text("마이크 권한 허용하기")
                    }
                } else {
                    Text("${reading.frequency.toInt()} Hz · ${reading.cents} cents · 감지 ${reading.note}")
                }
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    builtInTunings.forEach { preset ->
                        val selected = selectedPreset == preset
                        Surface(
                            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                            contentColor = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                            shape = MaterialTheme.shapes.small,
                            modifier = Modifier.clickable {
                                selectedPreset = preset
                                engine.setTuning(preset)
                            }
                        ) {
                            Text(preset.name, modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TunerGauge(cents: Int, inTune: Boolean) {
    val animatedCents by animateFloatAsState(
        targetValue = cents.coerceIn(-50, 50).toFloat(),
        animationSpec = tween(durationMillis = 220),
        label = "tuner needle"
    )
    val normalized = animatedCents / 50f
    Canvas(modifier = Modifier.fillMaxWidth().height(170.dp)) {
        val center = androidx.compose.ui.geometry.Offset(size.width / 2f, size.height * 0.88f)
        val radius = size.minDimension * 0.78f
        for (i in -5..5) {
            val angle = Math.toRadians((270 + i * 12).toDouble())
            val inner = radius * if (i == 0) 0.72f else 0.8f
            val outer = radius * 0.95f
            drawLine(
                color = if (i == 0) Color(0xFF16836B) else Color(0xFF757C8A),
                start = androidx.compose.ui.geometry.Offset(center.x + cos(angle).toFloat() * inner, center.y + sin(angle).toFloat() * inner),
                end = androidx.compose.ui.geometry.Offset(center.x + cos(angle).toFloat() * outer, center.y + sin(angle).toFloat() * outer),
                strokeWidth = if (i == 0) 6f else 3f,
                cap = StrokeCap.Round
            )
        }
        val needleAngle = Math.toRadians((270 + normalized * 60).toDouble())
        drawLine(
            color = if (inTune) Color(0xFF16836B) else Color(0xFF3559D9),
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
