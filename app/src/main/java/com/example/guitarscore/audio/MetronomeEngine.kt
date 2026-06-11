package com.example.guitarscore.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.sin

class MetronomeEngine {
    private val scope = CoroutineScope(Dispatchers.Default)
    private var job: Job? = null

    fun start(bpm: Int, beatsPerBar: Int, onBeat: (Int, Long) -> Unit) {
        stop()
        val intervalMs = (60_000L / bpm.coerceIn(30, 260))
        job = scope.launch {
            var beat = 0
            while (true) {
                playClick(accent = beat % beatsPerBar == 0)
                onBeat(beat, System.currentTimeMillis())
                beat++
                delay(intervalMs)
            }
        }
    }

    fun stop() {
        job?.cancel()
        job = null
    }

    private fun playClick(accent: Boolean) {
        val sampleRate = 44_100
        val durationSamples = sampleRate / 20
        val frequency = if (accent) 1_760.0 else 1_100.0
        val data = ShortArray(durationSamples) { i ->
            val envelope = 1.0 - (i.toDouble() / durationSamples)
            (sin(2.0 * PI * frequency * i / sampleRate) * envelope * Short.MAX_VALUE * 0.35).toInt().toShort()
        }
        val track = AudioTrack.Builder()
            .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).build())
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(sampleRate)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()
            )
            .setBufferSizeInBytes(data.size * 2)
            .setTransferMode(AudioTrack.MODE_STATIC)
            .build()
        track.write(data, 0, data.size)
        track.play()
        track.release()
    }
}
