package com.example.guitarscore.data

import org.junit.Assert.assertEquals
import org.junit.Test

class ScoreMetadataTest {
    private fun metadata(beats: Int, unit: Int) =
        ScoreMetadataEntity(scoreId = 1, timeSignature = beats, beatUnit = unit)

    @Test
    fun labelsTimeSignatureWithItsBeatUnit() {
        assertEquals("4/4", metadata(4, 4).timeSignatureLabel)
        assertEquals("6/8", metadata(6, 8).timeSignatureLabel)
    }

    @Test
    fun compoundMetersGroupBeatsInThrees() {
        assertEquals(3, metadata(6, 8).beatGroupSize)
        assertEquals(3, metadata(9, 8).beatGroupSize)
        assertEquals(3, metadata(12, 8).beatGroupSize)
    }

    @Test
    fun simpleMetersAccentOnlyTheDownbeat() {
        assertEquals(1, metadata(4, 4).beatGroupSize)
        assertEquals(1, metadata(6, 4).beatGroupSize)
        assertEquals(1, metadata(3, 4).beatGroupSize)
        // 3/8 은 한 마디가 묶음 하나라서 중간 강세가 없다.
        assertEquals(1, metadata(3, 8).beatGroupSize)
    }

    @Test
    fun defaultsToFourFour() {
        val defaults = ScoreMetadataEntity(scoreId = 1)

        assertEquals("4/4", defaults.timeSignatureLabel)
        assertEquals(1, defaults.beatGroupSize)
    }
}
