package com.example.guitarscore.audio

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.roundToInt

data class TunerReading(
    val frequency: Float = 0f,
    val note: String = "--",
    val cents: Int = 0,
    val inTune: Boolean = false
)

data class TuningPreset(val name: String, val notes: List<String>)

val builtInTunings = listOf(
    TuningPreset("Standard", listOf("E2", "A2", "D3", "G3", "B3", "E4")),
    TuningPreset("Drop D", listOf("D2", "A2", "D3", "G3", "B3", "E4")),
    TuningPreset("Eb Standard", listOf("Eb2", "Ab2", "Db3", "Gb3", "Bb3", "Eb4"))
)

class TunerEngine(private val context: Context) {
    private val scope = CoroutineScope(Dispatchers.Default)
    private val _reading = MutableStateFlow(TunerReading())
    val reading: StateFlow<TunerReading> = _reading
    private var job: Job? = null
    private var recorder: AudioRecord? = null

    fun start() {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) return
        stop()
        val sampleRate = 44_100
        val bufferSize = AudioRecord.getMinBufferSize(sampleRate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT) * 2
        recorder = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            sampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            bufferSize
        )
        recorder?.startRecording()
        job = scope.launch {
            val buffer = ShortArray(bufferSize / 2)
            while (true) {
                val read = recorder?.read(buffer, 0, buffer.size) ?: 0
                if (read > 0) detectPitch(buffer, read, sampleRate)?.let { _reading.value = toReading(it) }
            }
        }
    }

    fun stop() {
        job?.cancel()
        job = null
        recorder?.stop()
        recorder?.release()
        recorder = null
    }

    private fun detectPitch(buffer: ShortArray, size: Int, sampleRate: Int): Float? {
        val rms = kotlin.math.sqrt(buffer.take(size).map { it * it.toDouble() }.average()).toFloat()
        if (rms < 250f) return null
        var bestLag = -1
        var best = 0.0
        val minLag = sampleRate / 1_100
        val maxLag = sampleRate / 70
        for (lag in minLag..maxLag) {
            var sum = 0.0
            var i = 0
            while (i + lag < size) {
                sum += buffer[i] * buffer[i + lag].toDouble()
                i++
            }
            if (sum > best) {
                best = sum
                bestLag = lag
            }
        }
        return if (bestLag > 0) sampleRate.toFloat() / bestLag else null
    }

    private fun toReading(frequency: Float): TunerReading {
        val midi = (69 + 12 * ln(frequency / 440.0) / ln(2.0)).roundToInt()
        val target = 440.0 * 2.0.pow((midi - 69) / 12.0)
        val cents = (1200 * ln(frequency / target) / ln(2.0)).roundToInt()
        val names = listOf("C", "C#", "D", "Eb", "E", "F", "F#", "G", "Ab", "A", "Bb", "B")
        val octave = midi / 12 - 1
        val note = "${names[((midi % 12) + 12) % 12]}$octave"
        return TunerReading(frequency, note, cents, abs(cents) <= 5)
    }
}
