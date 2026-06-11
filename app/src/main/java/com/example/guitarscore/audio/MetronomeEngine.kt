package com.example.guitarscore.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.PI
import kotlin.math.sin

class MetronomeEngine {
    private val running = AtomicBoolean(false)
    private var worker: Thread? = null

    fun start(bpm: Int, beatsPerBar: Int, onBeat: (Int, Long) -> Unit) {
        stop()
        running.set(true)
        worker = Thread {
            android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_AUDIO)
            runStream(bpm.coerceIn(30, 260), beatsPerBar.coerceAtLeast(1), onBeat)
        }.apply {
            name = "GuitarScoreMetronome"
            start()
        }
    }

    fun stop() {
        running.set(false)
        worker?.interrupt()
        worker = null
    }

    private fun runStream(bpm: Int, beatsPerBar: Int, onBeat: (Int, Long) -> Unit) {
        val sampleRate = 48_000
        val beatSamples = (sampleRate * 60.0 / bpm).toInt().coerceAtLeast(1)
        val bufferFrames = 512
        val minBuffer = AudioTrack.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )
        val track = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(sampleRate)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()
            )
            .setBufferSizeInBytes(maxOf(minBuffer, bufferFrames * 8))
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()

        val regularClick = makeClick(sampleRate, frequency = 1_250.0, gain = 0.42)
        val accentClick = makeClick(sampleRate, frequency = 1_850.0, gain = 0.5)
        val buffer = ShortArray(bufferFrames)
        var sampleCursor = 0L
        var beat = 0

        try {
            track.setVolume(AudioTrack.getMaxVolume())
            track.play()
            while (running.get()) {
                for (i in buffer.indices) {
                    val positionInBeat = (sampleCursor % beatSamples).toInt()
                    if (positionInBeat == 0) {
                        onBeat(beat, System.currentTimeMillis())
                        beat++
                    }
                    val click = if ((beat - 1).floorMod(beatsPerBar) == 0) accentClick else regularClick
                    buffer[i] = if (positionInBeat < click.size) click[positionInBeat] else 0
                    sampleCursor++
                }
                var written = 0
                while (written < buffer.size && running.get()) {
                    val count = track.write(buffer, written, buffer.size - written, AudioTrack.WRITE_BLOCKING)
                    if (count <= 0) break
                    written += count
                }
            }
        } finally {
            track.pause()
            track.flush()
            track.release()
        }
    }

    private fun makeClick(sampleRate: Int, frequency: Double, gain: Double): ShortArray {
        val durationSamples = (sampleRate * 0.045).toInt()
        return ShortArray(durationSamples) { i ->
            val envelope = 1.0 - (i.toDouble() / durationSamples)
            (sin(2.0 * PI * frequency * i / sampleRate) * envelope * Short.MAX_VALUE * gain).toInt().toShort()
        }
    }
}

private fun Int.floorMod(other: Int): Int = ((this % other) + other) % other
