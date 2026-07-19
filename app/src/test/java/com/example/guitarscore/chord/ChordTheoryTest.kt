package com.example.guitarscore.chord

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ChordTheoryTest {
    @Test
    fun detectsCFromOpenPosition() {
        val positions = setOf(
            FretPosition(1, 3),
            FretPosition(2, 2),
            FretPosition(3, 0),
            FretPosition(4, 1),
            FretPosition(5, 0)
        )

        assertEquals("C", detectChords(positions).first().name)
    }

    @Test
    fun detectsMinorChordAndBassInversion() {
        val positions = setOf(
            FretPosition(0, 0),
            FretPosition(1, 3),
            FretPosition(2, 2),
            FretPosition(3, 0),
            FretPosition(4, 1)
        )

        assertTrue(detectChords(positions).any { it.name == "C/E" })
    }

    @Test
    fun createsMultiplePlayableVoicings() {
        val chord = requireNotNull(chordDefinition("F#m"))
        val voicings = generateVoicings(chord)

        assertTrue(voicings.size >= 2)
        assertTrue(voicings.all { it.frets.size == 6 })
    }

    @Test
    fun prefersCommonOpenVoicing() {
        val voicings = generateVoicings(requireNotNull(chordDefinition("C")))

        assertEquals("오픈 포지션", voicings.first().label)
        assertEquals(listOf(-1, 3, 2, 0, 1, 0), voicings.first().frets)
    }

    @Test
    fun searchAcceptsFlatSymbol() {
        assertEquals("Ebmaj7", chordDefinition("E♭maj7")?.name)
    }
}
