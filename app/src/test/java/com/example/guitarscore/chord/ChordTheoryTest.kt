package com.example.guitarscore.chord

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
        assertEquals(listOf(0, 3, 2, 0, 1, 0), voicings.first().fingers)
    }

    @Test
    fun marksFullBarreForFMajor() {
        val voicing = generateVoicings(requireNotNull(chordDefinition("F"))).first()

        assertEquals(ChordBarre(fret = 1, startString = 0, endString = 5), voicing.barres.first())
    }

    @Test
    fun filtersChordGridByQueryAndFamily() {
        val results = filterChordDefinitions(query = "7", family = "A")

        assertTrue(results.isNotEmpty())
        assertTrue(results.all { it.name.startsWith("A") && "7" in it.name })
    }

    @Test
    fun searchAcceptsFlatSymbol() {
        assertEquals("Ebmaj7", chordDefinition("E♭maj7")?.name)
    }

    @Test
    fun readsAm7OpenShape() {
        // x 0 2 0 1 0
        val positions = setOf(
            FretPosition(1, 0),
            FretPosition(2, 2),
            FretPosition(3, 0),
            FretPosition(4, 1),
            FretPosition(5, 0)
        )

        assertEquals("Am7", analyzeChords(positions).first().name)
    }

    @Test
    fun readsPowerChord() {
        val positions = setOf(FretPosition(0, 0), FretPosition(1, 2))

        assertEquals("E5", analyzeChords(positions).first().name)
    }

    @Test
    fun namesSeventhChordWithOmittedFifth() {
        // 5도를 뺀 C7(C, E, Bb). 실제 연주에서 매우 흔한데 완전 일치가 아니라 예전에는 인식하지 못했다.
        val positions = setOf(
            FretPosition(1, 3),
            FretPosition(2, 2),
            FretPosition(3, 3)
        )
        val best = analyzeChords(positions).first()

        assertEquals("C7", best.name)
        assertFalse(best.exact)
        assertEquals(listOf("G"), best.missingNotes)
    }

    @Test
    fun readsHalfDiminishedChord() {
        val positions = setOf(
            FretPosition(1, 2),
            FretPosition(2, 3),
            FretPosition(3, 2),
            FretPosition(4, 3)
        )

        assertEquals("Bm7b5", analyzeChords(positions).first().name)
    }

    @Test
    fun everyChordHasAPlayableVoicing() {
        val missing = allChordDefinitions().filter { generateVoicings(it).isEmpty() }

        assertTrue("운지를 만들지 못한 코드: ${missing.map { it.name }}", missing.isEmpty())
    }

    @Test
    fun voicingsNeverNeedMoreThanFourFingers() {
        val impossible = allChordDefinitions()
            .flatMap { chord -> generateVoicings(chord).map { chord.name to it } }
            .filter { (_, voicing) -> fingersNeeded(voicing.frets) > 4 }

        assertTrue("손가락이 모자란 운지: ${impossible.map { it.first }}", impossible.isEmpty())
    }

    /** 테스트용 손가락 수 계산. 최저 프렛 사이에 개방/뮤트 줄이 없을 때만 바레로 친다. */
    private fun fingersNeeded(frets: List<Int>): Int {
        val fretted = frets.withIndex().filter { it.value > 0 }
        if (fretted.isEmpty()) return 0
        val lowest = fretted.minOf { it.value }
        val barreStrings = fretted.filter { it.value == lowest }.map { it.index }
        val barrePossible = barreStrings.size >= 2 &&
            (barreStrings.min()..barreStrings.max()).none { frets[it] < lowest }
        return fretted.size - (if (barrePossible) barreStrings.size - 1 else 0)
    }
}
