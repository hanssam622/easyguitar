package com.example.guitarscore.audio

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sqrt

data class TunerReading(
    val frequency: Float = 0f,
    val note: String = "--",
    val targetNote: String = "--",
    val cents: Int = 0,
    val inTune: Boolean = false,
    val error: String? = null
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
    @Volatile private var targetMidis = presetToMidis(builtInTunings.first())
    private var smoothedFrequency: Float? = null
    private var pendingFrequency: Float? = null
    private var pendingCount = 0
    private var lockedTargetMidi: Int? = null
    private val recentFrequencies = ArrayDeque<Float>()

    @Synchronized
    fun setTuning(preset: TuningPreset) {
        targetMidis = presetToMidis(preset)
        resetTracking()
        _reading.value = TunerReading()
    }

    fun start() {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) return
        stop()
        val sampleRate = 44_100
        val minBufferSize = AudioRecord.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )
        if (minBufferSize <= 0) {
            _reading.value = TunerReading(error = "마이크 버퍼를 만들 수 없습니다.")
            return
        }
        val bufferSize = maxOf(minBufferSize * 2, 8_192)
        val audioRecord = try {
            AudioRecord(
                MediaRecorder.AudioSource.MIC,
                sampleRate,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                bufferSize
            )
        } catch (error: Throwable) {
            _reading.value = TunerReading(error = "마이크를 열 수 없습니다.")
            return
        }
        if (audioRecord.state != AudioRecord.STATE_INITIALIZED) {
            audioRecord.release()
            _reading.value = TunerReading(error = "마이크 초기화에 실패했습니다.")
            return
        }
        try {
            audioRecord.startRecording()
        } catch (error: Throwable) {
            audioRecord.release()
            _reading.value = TunerReading(error = "마이크 녹음을 시작할 수 없습니다.")
            return
        }
        recorder = audioRecord
        resetTracking()
        job = scope.launch {
            val buffer = ShortArray(bufferSize / 2)
            var missingFrames = 0
            try {
                while (true) {
                    val read = recorder?.read(buffer, 0, buffer.size, AudioRecord.READ_BLOCKING) ?: 0
                    val estimate = if (read > 0) YinPitchDetector.detect(buffer, read, sampleRate) else null
                    if (estimate == null) {
                        missingFrames++
                        if (missingFrames >= 12) {
                            _reading.value = TunerReading()
                            resetTracking()
                        }
                    } else {
                        missingFrames = 0
                        stabilize(estimate.frequency)?.let { _reading.value = toReading(it) }
                    }
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                _reading.value = TunerReading(error = "마이크 입력을 읽을 수 없습니다.")
            }
        }
    }

    fun stop() {
        job?.cancel()
        job = null
        recorder?.let { audioRecord ->
            try {
                if (audioRecord.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                    audioRecord.stop()
                }
            } catch (_: Throwable) {
            } finally {
                audioRecord.release()
            }
        }
        recorder = null
        resetTracking()
    }

    @Synchronized
    private fun stabilize(frequency: Float): Float? {
        val current = smoothedFrequency
        if (current != null && abs(centsBetween(frequency, current)) > 100f) {
            val pending = pendingFrequency
            if (pending == null || abs(centsBetween(frequency, pending)) > 35f) {
                pendingFrequency = frequency
                pendingCount = 1
                return null
            }
            pendingCount++
            pendingFrequency = frequency
            if (pendingCount < 3) return null
            recentFrequencies.clear()
            smoothedFrequency = frequency
            pendingFrequency = null
            pendingCount = 0
            return frequency
        }

        pendingFrequency = null
        pendingCount = 0
        recentFrequencies.addLast(frequency)
        while (recentFrequencies.size > 5) recentFrequencies.removeFirst()
        val median = recentFrequencies.sorted()[recentFrequencies.size / 2]
        val alpha = if (current == null) 1f else if (abs(centsBetween(median, current)) < 12f) 0.18f else 0.32f
        val smoothed = if (current == null) median else current + (median - current) * alpha
        smoothedFrequency = smoothed
        return smoothed
    }

    @Synchronized
    private fun toReading(frequency: Float): TunerReading {
        val nearestTarget = targetMidis.minBy { midi -> abs(centsFromMidi(frequency, midi)) }
        val previousTarget = lockedTargetMidi
        val targetMidi = previousTarget
            ?.takeIf { it in targetMidis && abs(centsFromMidi(frequency, it)) <= 180f }
            ?: nearestTarget
        lockedTargetMidi = targetMidi
        val detectedMidi = (69 + 12 * ln(frequency / 440.0) / ln(2.0)).roundToInt()
        val target = midiToFrequency(targetMidi)
        val cents = (1200 * ln(frequency / target) / ln(2.0)).roundToInt().coerceIn(-50, 50)
        val names = listOf("C", "C#", "D", "Eb", "E", "F", "F#", "G", "Ab", "A", "Bb", "B")
        val detectedOctave = detectedMidi / 12 - 1
        val targetOctave = targetMidi / 12 - 1
        val note = "${names[((detectedMidi % 12) + 12) % 12]}$detectedOctave"
        val targetNote = "${names[((targetMidi % 12) + 12) % 12]}$targetOctave"
        val wasInTune = _reading.value.inTune && _reading.value.targetNote == targetNote
        val inTune = abs(cents) <= if (wasInTune) 8 else 4
        return TunerReading(frequency, note, targetNote, cents, inTune)
    }

    private fun midiToFrequency(midi: Int): Double = 440.0 * 2.0.pow((midi - 69) / 12.0)

    private fun centsFromMidi(frequency: Float, midi: Int): Float =
        (1200 * ln(frequency / midiToFrequency(midi)) / ln(2.0)).toFloat()

    private fun centsBetween(first: Float, second: Float): Float =
        (1200 * ln(first / second) / ln(2.0)).toFloat()

    @Synchronized
    private fun resetTracking() {
        smoothedFrequency = null
        pendingFrequency = null
        pendingCount = 0
        lockedTargetMidi = null
        recentFrequencies.clear()
    }

    private fun presetToMidis(preset: TuningPreset): List<Int> = preset.notes.map(::noteToMidi)

    private fun noteToMidi(note: String): Int {
        val octave = note.last().digitToInt()
        val pitchName = note.dropLast(1)
        val pitchClass = mapOf(
            "C" to 0, "C#" to 1, "Db" to 1, "D" to 2, "D#" to 3, "Eb" to 3,
            "E" to 4, "F" to 5, "F#" to 6, "Gb" to 6, "G" to 7, "G#" to 8,
            "Ab" to 8, "A" to 9, "A#" to 10, "Bb" to 10, "B" to 11
        ).getValue(pitchName)
        return (octave + 1) * 12 + pitchClass
    }
}

internal data class PitchEstimate(val frequency: Float, val confidence: Float, val rms: Float)

internal object YinPitchDetector {
    private const val minFrequency = 65f
    private const val maxFrequency = 380f
    private const val yinThreshold = 0.16
    private const val minimumRms = 450f

    fun detect(buffer: ShortArray, size: Int, sampleRate: Int): PitchEstimate? {
        if (size < 2_048) return null
        var squareSum = 0.0
        for (index in 0 until size) {
            val sample = buffer[index].toDouble()
            squareSum += sample * sample
        }
        val rms = sqrt(squareSum / size).toFloat()
        if (rms < minimumRms) return null

        val minTau = maxOf(2, (sampleRate / maxFrequency).toInt())
        val maxTau = min((sampleRate / minFrequency).toInt(), size / 2 - 1)
        if (maxTau <= minTau) return null
        val windowSize = min(2_048, size - maxTau)
        val difference = DoubleArray(maxTau + 1)
        for (tau in 1..maxTau) {
            var sum = 0.0
            for (index in 0 until windowSize) {
                val delta = buffer[index].toDouble() - buffer[index + tau].toDouble()
                sum += delta * delta
            }
            difference[tau] = sum
        }

        val normalized = DoubleArray(maxTau + 1) { 1.0 }
        var runningSum = 0.0
        for (tau in 1..maxTau) {
            runningSum += difference[tau]
            normalized[tau] = if (runningSum == 0.0) 1.0 else difference[tau] * tau / runningSum
        }

        var tau = minTau
        while (tau <= maxTau) {
            if (normalized[tau] < yinThreshold) {
                while (tau + 1 <= maxTau && normalized[tau + 1] < normalized[tau]) tau++
                break
            }
            tau++
        }
        if (tau > maxTau) return null

        val refinedTau = if (tau > 1 && tau < maxTau) {
            val left = normalized[tau - 1]
            val center = normalized[tau]
            val right = normalized[tau + 1]
            val denominator = 2.0 * (2.0 * center - right - left)
            if (abs(denominator) > 1e-9) tau + (right - left) / denominator else tau.toDouble()
        } else {
            tau.toDouble()
        }
        val frequency = (sampleRate / refinedTau).toFloat()
        val confidence = (1.0 - normalized[tau]).toFloat()
        if (frequency !in minFrequency..maxFrequency || confidence < 0.78f) return null
        return PitchEstimate(frequency, confidence, rms)
    }
}
