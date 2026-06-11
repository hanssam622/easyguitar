package com.example.guitarscore.audio

import com.example.guitarscore.data.TurnCueEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.max

data class AutoTurnState(
    val running: Boolean = false,
    val elapsedMillis: Long = 0,
    val elapsedBeats: Float = 0f,
    val offsetMillis: Long = 0
)

class AutoTurnEngine {
    private val scope = CoroutineScope(Dispatchers.Default)
    private var job: Job? = null
    private var startedAt = 0L
    private var offsetMillis = 0L

    fun start(
        bpm: Int,
        cues: List<TurnCueEntity>,
        onState: (AutoTurnState) -> Unit,
        onTurnToPage: (Int) -> Unit
    ) {
        stop()
        val sorted = cues.sortedBy { it.triggerBeat ?: ((it.triggerMillis ?: 0L) / 1000f) }
        val fired = mutableSetOf<Long>()
        val beatMs = 60_000f / bpm.coerceIn(30, 260)
        startedAt = System.currentTimeMillis()
        offsetMillis = 0
        job = scope.launch {
            while (true) {
                val elapsed = max(0L, System.currentTimeMillis() - startedAt + offsetMillis)
                val beats = elapsed / beatMs
                sorted.forEach { cue ->
                    val cueId = cue.id
                    val trigger = cue.triggerBeat?.let { (it * beatMs).toLong() } ?: cue.triggerMillis ?: Long.MAX_VALUE
                    if (elapsed >= trigger && fired.add(cueId)) onTurnToPage(cue.pageIndex)
                }
                onState(AutoTurnState(running = true, elapsedMillis = elapsed, elapsedBeats = beats, offsetMillis = offsetMillis))
                delay(100)
            }
        }
    }

    fun nudge(deltaMillis: Long) {
        offsetMillis += deltaMillis
    }

    fun stop() {
        job?.cancel()
        job = null
    }
}
