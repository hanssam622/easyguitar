package com.example.guitarscore.audio

import com.example.guitarscore.data.TurnCueEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.max

data class AutoTurnState(
    val running: Boolean = false,
    val paused: Boolean = false,
    val elapsedMillis: Long = 0,
    val elapsedBeats: Float = 0f,
    val offsetMillis: Long = 0
)

/**
 * 곡의 경과 박자를 세면서 저장된 큐 시점에 페이지를 넘긴다.
 *
 * 시계는 [System.nanoTime] 을 쓴다. 벽시계는 사용자가 시간을 바꾸거나 NTP 보정이 들어오면 튀기 때문이다.
 * 되감기([nudge])로 시간이 큐 이전으로 돌아가면 해당 큐는 다시 발동할 수 있게 풀어 준다.
 */
class AutoTurnEngine {
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private var job: Job? = null
    private var startedAtNanos = 0L
    @Volatile private var offsetMillis = 0L
    @Volatile private var paused = false
    private var pausedElapsed = 0L

    fun start(
        bpm: Int,
        cues: List<TurnCueEntity>,
        onState: (AutoTurnState) -> Unit,
        onTurnToPage: (Int) -> Unit
    ) {
        stop()
        val beatMs = 60_000f / bpm.coerceIn(30, 260)
        val triggers = cues
            .map { cue -> cue to (cue.triggerBeat?.let { (it * beatMs).toLong() } ?: cue.triggerMillis) }
            .filter { it.second != null }
            .map { it.first to requireNotNull(it.second) }
            .sortedBy { it.second }
        val fired = mutableSetOf<Long>()
        startedAtNanos = System.nanoTime()
        offsetMillis = 0
        paused = false
        pausedElapsed = 0
        job = scope.launch {
            while (true) {
                val elapsed = currentElapsed()
                triggers.forEach { (cue, trigger) ->
                    if (elapsed >= trigger) {
                        if (fired.add(cue.id)) onTurnToPage(cue.pageIndex)
                    } else {
                        // 되감았으면 다시 발동할 수 있게 되돌린다.
                        fired.remove(cue.id)
                    }
                }
                onState(
                    AutoTurnState(
                        running = true,
                        paused = paused,
                        elapsedMillis = elapsed,
                        elapsedBeats = elapsed / beatMs,
                        offsetMillis = offsetMillis
                    )
                )
                delay(TICK_MILLIS)
            }
        }
    }

    private fun currentElapsed(): Long {
        if (paused) return max(0L, pausedElapsed)
        return max(0L, (System.nanoTime() - startedAtNanos) / 1_000_000 + offsetMillis)
    }

    fun nudge(deltaMillis: Long) {
        if (paused) {
            pausedElapsed = max(0L, pausedElapsed + deltaMillis)
        } else {
            offsetMillis += deltaMillis
        }
    }

    fun setPaused(value: Boolean) {
        if (value == paused) return
        if (value) {
            pausedElapsed = currentElapsed()
            paused = true
        } else {
            // 멈춰 있던 만큼을 보정해 이어서 진행한다.
            paused = false
            startedAtNanos = System.nanoTime()
            offsetMillis = pausedElapsed
        }
    }

    fun stop() {
        job?.cancel()
        job = null
        paused = false
        pausedElapsed = 0
        offsetMillis = 0
    }

    fun dispose() {
        stop()
        scope.cancel()
    }

    private companion object {
        /** 100ms 는 빠른 곡에서 눈에 띄게 늦어서 페이지 넘김 정확도를 위해 더 촘촘히 본다. */
        const val TICK_MILLIS = 33L
    }
}
