package com.example.guitarscore.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import kotlin.math.PI
import kotlin.math.sin

class YinPitchDetectorTest {
    @Test
    fun detectsA2SineWave() {
        val sampleRate = 44_100
        val frequency = 110.0
        val buffer = ShortArray(4_096) { index ->
            (sin(2.0 * PI * frequency * index / sampleRate) * 8_000).toInt().toShort()
        }

        val estimate = YinPitchDetector.detect(buffer, buffer.size, sampleRate)

        assertEquals(110f, requireNotNull(estimate).frequency, 0.8f)
    }

    @Test
    fun rejectsQuietSignal() {
        val buffer = ShortArray(4_096) { 100 }

        assertNull(YinPitchDetector.detect(buffer, buffer.size, 44_100))
    }
}
