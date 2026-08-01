package com.example.guitarscore.chord

import kotlin.math.max

data class FretPosition(val stringIndex: Int, val fret: Int)

data class ChordMatch(
    val name: String,
    val rootPitchClass: Int,
    val pitchClasses: Set<Int>,
    val exact: Boolean = true,
    val missingNotes: List<String> = emptyList(),
    val extraNotes: List<String> = emptyList()
)

data class ChordBarre(
    val fret: Int,
    val startString: Int,
    val endString: Int,
    val finger: Int = 1
)

data class ChordDefinition(
    val name: String,
    val rootPitchClass: Int,
    val qualityId: String,
    val pitchClasses: Set<Int>,
    val rank: Int = 99
)

enum class VoicingDifficulty(val label: String) {
    Easy("쉬움"),
    Medium("보통"),
    Hard("어려움")
}

data class ChordVoicing(
    val frets: List<Int>,
    val difficulty: VoicingDifficulty,
    val label: String,
    val fingers: List<Int>,
    val barres: List<ChordBarre>
)

private data class ChordQuality(val id: String, val suffix: String, val intervals: Set<Int>, val rank: Int)

/** 흔한 순서대로 둔다. rank 는 동점 후보 중 "더 흔한 해석"을 앞세우는 데 쓴다. */
private val qualities = listOf(
    ChordQuality("major", "", setOf(0, 4, 7), 0),
    ChordQuality("minor", "m", setOf(0, 3, 7), 1),
    ChordQuality("7", "7", setOf(0, 4, 7, 10), 2),
    ChordQuality("m7", "m7", setOf(0, 3, 7, 10), 3),
    ChordQuality("maj7", "maj7", setOf(0, 4, 7, 11), 4),
    ChordQuality("sus4", "sus4", setOf(0, 5, 7), 5),
    ChordQuality("sus2", "sus2", setOf(0, 2, 7), 6),
    ChordQuality("5", "5", setOf(0, 7), 7),
    ChordQuality("6", "6", setOf(0, 4, 7, 9), 8),
    ChordQuality("m6", "m6", setOf(0, 3, 7, 9), 9),
    ChordQuality("add9", "add9", setOf(0, 2, 4, 7), 10),
    ChordQuality("madd9", "madd9", setOf(0, 2, 3, 7), 11),
    ChordQuality("dim", "dim", setOf(0, 3, 6), 12),
    ChordQuality("m7b5", "m7b5", setOf(0, 3, 6, 10), 13),
    ChordQuality("dim7", "dim7", setOf(0, 3, 6, 9), 14),
    ChordQuality("aug", "aug", setOf(0, 4, 8), 15),
    ChordQuality("7sus4", "7sus4", setOf(0, 5, 7, 10), 16),
    ChordQuality("9", "9", setOf(0, 2, 4, 7, 10), 17),
    ChordQuality("m9", "m9", setOf(0, 2, 3, 7, 10), 18),
    ChordQuality("maj9", "maj9", setOf(0, 2, 4, 7, 11), 19),
    ChordQuality("mMaj7", "mMaj7", setOf(0, 3, 7, 11), 20),
    ChordQuality("7#5", "7#5", setOf(0, 4, 8, 10), 21),
    ChordQuality("69", "6/9", setOf(0, 2, 4, 7, 9), 22)
)

private val noteNames = listOf("C", "C#", "D", "Eb", "E", "F", "F#", "G", "Ab", "A", "Bb", "B")
private val openStringPitchClasses = listOf(4, 9, 2, 7, 11, 4)
private val openStringMidis = listOf(40, 45, 50, 55, 59, 64)
private val chordDefinitions: List<ChordDefinition> = buildList {
    for (root in 0..11) {
        qualities.forEach { quality ->
            add(
                ChordDefinition(
                    name = noteNames[root] + quality.suffix,
                    rootPitchClass = root,
                    qualityId = quality.id,
                    pitchClasses = quality.intervals.mapTo(linkedSetOf()) { (root + it) % 12 },
                    rank = quality.rank
                )
            )
        }
    }
}
private val voicingCache = mutableMapOf<String, List<ChordVoicing>>()

fun noteNameAt(position: FretPosition): String =
    noteNames[(openStringPitchClasses[position.stringIndex] + position.fret) % 12]

private fun pitchClassOf(position: FretPosition): Int =
    (openStringPitchClasses[position.stringIndex] + position.fret) % 12

/**
 * 코드 톤이 빠졌을 때의 감점 크기. 완전5도는 실제 연주에서 흔히 생략하므로 거의 감점하지 않고,
 * 루트·3음·감5도처럼 코드 성격을 결정하는 음은 크게 감점한다.
 */
private fun intervalWeight(interval: Int): Double = when (interval) {
    0 -> 3.0
    3, 4 -> 2.6
    6, 8 -> 2.4
    7 -> 0.5
    10, 11 -> 1.8
    else -> 1.6
}

private data class ScoredChord(
    val chord: ChordDefinition,
    val score: Double,
    val missing: List<Int>,
    val extra: List<Int>,
    val bassPitchClass: Int?
)

/**
 * 누른 운지를 코드 이름으로 해석한다. 완전 일치만 인정하던 이전 구현과 달리 5도 생략처럼 실제 연주에서
 * 흔한 형태에도 점수를 매겨 순위대로 돌려주므로, 결과가 비는 경우가 거의 없다.
 */
fun analyzeChords(positions: Set<FretPosition>, limit: Int = 6): List<ChordMatch> {
    if (positions.isEmpty()) return emptyList()
    val played = positions.mapTo(linkedSetOf(), ::pitchClassOf)
    if (played.size < 2) return emptyList()
    val bassPitchClass = positions
        .minByOrNull { openStringMidis[it.stringIndex] + it.fret }
        ?.let(::pitchClassOf)

    val scored = chordDefinitions.map { chord ->
        val missing = chord.pitchClasses.filter { it !in played }
        val extra = played.filter { it !in chord.pitchClasses }
        val missingCost = missing.sumOf { intervalWeight((it - chord.rootPitchClass + 12) % 12) }
        val inversionCost = if (bassPitchClass != null && bassPitchClass != chord.rootPitchClass) 0.4 else 0.0
        val score = missingCost + extra.size * 3.5 + inversionCost +
            chord.pitchClasses.size * 0.05 + chord.rank * 0.03
        ScoredChord(chord, score, missing, extra, bassPitchClass)
    }

    val plausible = scored.filter { it.extra.size <= 1 && it.missing.size <= 2 && it.score < 4.6 }
    val ranked = plausible.ifEmpty { scored.sortedBy { it.score }.take(3) }

    return ranked
        .sortedWith(compareBy<ScoredChord> { it.score }.thenBy { it.chord.name.length })
        .map { it.toMatch() }
        .distinctBy { it.name }
        .take(limit)
}

private fun ScoredChord.toMatch(): ChordMatch {
    val bass = bassPitchClass
    val displayName = if (bass != null && bass != chord.rootPitchClass && bass in chord.pitchClasses) {
        "${chord.name}/${noteNames[bass]}"
    } else {
        chord.name
    }
    return ChordMatch(
        name = displayName,
        rootPitchClass = chord.rootPitchClass,
        pitchClasses = chord.pitchClasses,
        exact = missing.isEmpty() && extra.isEmpty(),
        missingNotes = missing.map { noteNames[it] },
        extraNotes = extra.map { noteNames[it] }
    )
}

/** 누른 음이 코드 구성음과 정확히 일치하는 해석만. */
fun detectChords(positions: Set<FretPosition>): List<ChordMatch> =
    analyzeChords(positions, limit = Int.MAX_VALUE).filter { it.exact }

/** 정확히 일치하지는 않지만 가능성이 높은 해석. */
fun suggestChords(positions: Set<FretPosition>, limit: Int = 5): List<ChordMatch> =
    analyzeChords(positions, limit = limit + 4).filter { !it.exact }.take(limit)

fun selectedNoteSummary(positions: Set<FretPosition>): String = positions
    .map { noteNameAt(it) }
    .distinct()
    .sortedBy { noteNames.indexOf(it) }
    .joinToString(" · ")

fun searchChordDefinitions(query: String, limit: Int = Int.MAX_VALUE): List<ChordDefinition> {
    val normalizedQuery = normalizeChordName(query)
    return chordDefinitions
        .filter { normalizedQuery.isBlank() || normalizeChordName(it.name).contains(normalizedQuery) }
        .sortedWith(
            compareBy<ChordDefinition> { !normalizeChordName(it.name).startsWith(normalizedQuery) }
                .thenBy { it.rank }
                .thenBy { it.name }
        )
        .take(limit)
}

fun chordDefinition(name: String): ChordDefinition? {
    val normalized = normalizeChordName(name)
    return chordDefinitions.firstOrNull { normalizeChordName(it.name) == normalized }
}

fun defaultChordDefinition(): ChordDefinition = requireNotNull(chordDefinition("C"))

fun allChordDefinitions(): List<ChordDefinition> = chordDefinitions

fun filterChordDefinitions(query: String, family: String?): List<ChordDefinition> {
    val searched = searchChordDefinitions(query)
    if (family.isNullOrBlank()) return searched
    return searched.filter { noteNames[it.rootPitchClass] == family }
}

fun generateVoicings(chord: ChordDefinition): List<ChordVoicing> =
    voicingCache.getOrPut(chord.name) { buildVoicings(chord) }

private fun buildVoicings(chord: ChordDefinition): List<ChordVoicing> {
    val candidates = linkedMapOf<List<Int>, ChordVoicing>()
    openVoicings[chord.name]?.let { frets ->
        candidates[frets] = createVoicing(chord.name, frets, "오픈 포지션")
    }

    barreShapes[chord.qualityId]?.let { shapes ->
        val eRootFret = (chord.rootPitchClass - 4 + 12) % 12
        val aRootFret = (chord.rootPitchClass - 9 + 12) % 12
        listOf(eRootFret to shapes.first, aRootFret to shapes.second).forEachIndexed { index, (rootFret, shape) ->
            val frets = shape.map { if (it < 0) -1 else it + rootFret }
            candidates.putIfAbsent(
                frets,
                createVoicing(chord.name, frets, if (index == 0) "6번 줄 루트" else "5번 줄 루트")
            )
        }
    }

    searchVoicings(chord).forEachIndexed { index, frets ->
        candidates.putIfAbsent(
            frets,
            createVoicing(chord.name, frets, if (index == 0) "컴팩트 보이싱" else "하이 포지션")
        )
    }

    return candidates.values
        // 손가락이 다섯 개 필요한 모양이 표에 섞여 있어도 사용자에게 보여 주지 않는다.
        .filter { fingerCount(it.frets) <= 4 && it.frets.count { fret -> fret >= 0 } >= 3 }
        // 손이 편한 순서보다 "실제로 많이 쓰는 모양" 순서가 먼저다. 탐색으로 찾은 이상한 모양이
        // 난이도만 낮다는 이유로 대표 운지 자리를 차지하지 않게 한다.
        .sortedWith(
            compareBy<ChordVoicing> {
                when (it.label) {
                    "오픈 포지션" -> 0
                    "5번 줄 루트" -> 1
                    "6번 줄 루트" -> 2
                    "컴팩트 보이싱" -> 3
                    else -> 4
                }
            }
                .thenBy { it.difficulty.ordinal }
                .thenBy { voicingScore(it.frets) }
        )
        .take(6)
}

/**
 * 6줄 전체에서 실제로 손이 닿는 보이싱을 탐색한다. 울리는 줄은 최저음 줄부터 연속이어야 하고,
 * 누르는 프렛의 폭은 4프렛 이내, 손가락은 4개 이내여야 한다. 4음 이상 코드에서는 완전5도 생략을 허용한다.
 */
private fun searchVoicings(chord: ChordDefinition): List<List<Int>> {
    val essential = chord.pitchClasses.filterTo(linkedSetOf()) { pitchClass ->
        val interval = (pitchClass - chord.rootPitchClass + 12) % 12
        interval != 7 || chord.pitchClasses.size <= 3
    }
    val results = linkedSetOf<List<Int>>()

    for (lowestString in 0..3) {
        for (highestString in (lowestString + 2)..5) {
            for (windowStart in 1..11) {
                val frets = MutableList(6) { -1 }

                fun place(stringIndex: Int) {
                    if (stringIndex > highestString) {
                        val played = frets.withIndex().filter { it.value >= 0 }
                        val tones = played.mapTo(linkedSetOf()) { pitchClassOf(FretPosition(it.index, it.value)) }
                        if (!tones.containsAll(essential)) return
                        if (pitchClassOf(FretPosition(lowestString, frets[lowestString])) != chord.rootPitchClass) return
                        val fretted = played.map { it.value }.filter { it > 0 }
                        if (fretted.isNotEmpty() && fretted.max() - fretted.min() > 3) return
                        if (fingerCount(frets) > 4) return
                        results += frets.toList()
                        return
                    }
                    val options = buildList {
                        if (openStringPitchClasses[stringIndex] in chord.pitchClasses) add(0)
                        for (fret in windowStart..windowStart + 3) {
                            if (pitchClassOf(FretPosition(stringIndex, fret)) in chord.pitchClasses) add(fret)
                        }
                    }
                    options.forEach { fret ->
                        frets[stringIndex] = fret
                        place(stringIndex + 1)
                        frets[stringIndex] = -1
                    }
                }

                place(lowestString)
            }
        }
    }

    return results.sortedBy(::voicingScore).take(4)
}

/**
 * 필요한 손가락 수. 최저 프렛에 걸친 음들은 바레로 한 손가락에 묶는다.
 * 다만 그 사이에 개방현이나 뮤트된 줄이 끼어 있으면 손가락이 눌러 버리므로 바레로 칠 수 없다.
 */
private fun fingerCount(frets: List<Int>): Int {
    val fretted = frets.withIndex().filter { it.value > 0 }
    if (fretted.isEmpty()) return 0
    val lowest = fretted.minOf { it.value }
    val barreStrings = fretted.filter { it.value == lowest }.map { it.index }
    val barrePossible = barreStrings.size >= 2 &&
        (barreStrings.min()..barreStrings.max()).none { frets[it] < lowest }
    return fretted.size - (if (barrePossible) barreStrings.size - 1 else 0)
}

private fun createVoicing(chordName: String, frets: List<Int>, label: String): ChordVoicing {
    val barres = detectBarres(chordName, frets, label)
    return ChordVoicing(
        frets = frets,
        difficulty = difficultyFor(frets),
        label = label,
        fingers = assignFingers(frets, barres),
        barres = barres
    )
}

private fun detectBarres(chordName: String, frets: List<Int>, label: String): List<ChordBarre> {
    if (label == "오픈 포지션" && chordName !in setOf("F", "Fm")) return emptyList()
    val positive = frets.withIndex().filter { it.value > 0 }
    val minimumFret = positive.minOfOrNull { it.value } ?: return emptyList()
    val minimumStrings = positive.filter { it.value == minimumFret }.map { it.index }
    if (minimumStrings.size < 2) return emptyList()
    val start = minimumStrings.min()
    val end = minimumStrings.max()
    if ((start..end).any { frets[it] < minimumFret }) return emptyList()
    return listOf(ChordBarre(minimumFret, start, end))
}

private fun assignFingers(frets: List<Int>, barres: List<ChordBarre>): List<Int> {
    val result = MutableList(6) { 0 }
    val barre = barres.firstOrNull()
    barre?.let { value ->
        (value.startString..value.endString).forEach { string ->
            if (frets[string] == value.fret) result[string] = value.finger
        }
    }
    var nextFinger = if (barre == null) 1 else 2
    var previousFret = -1
    frets.withIndex()
        .filter { it.value > 0 && result[it.index] == 0 }
        .sortedWith(compareBy<IndexedValue<Int>> { it.value }.thenBy { it.index })
        .forEach { item ->
            if (previousFret >= 0 && item.value != previousFret) nextFinger++
            result[item.index] = nextFinger.coerceAtMost(4)
            previousFret = item.value
        }
    return result
}

private fun normalizeChordName(value: String): String = value
    .trim()
    .replace("♯", "#")
    .replace("♭", "b")
    .replace("major", "maj", ignoreCase = true)
    .replace("minor", "m", ignoreCase = true)
    .lowercase()

private fun difficultyFor(frets: List<Int>): VoicingDifficulty {
    val played = frets.filter { it >= 0 }
    val fretted = played.filter { it > 0 }
    val span = if (fretted.isEmpty()) 0 else fretted.max() - fretted.min()
    val maxFret = fretted.maxOrNull() ?: 0
    return when {
        maxFret <= 4 && span <= 3 && 0 in played -> VoicingDifficulty.Easy
        span <= 4 && maxFret <= 9 -> VoicingDifficulty.Medium
        else -> VoicingDifficulty.Hard
    }
}

private fun voicingScore(frets: List<Int>): Int {
    val played = frets.filter { it >= 0 }
    val fretted = played.filter { it > 0 }
    val span = if (fretted.isEmpty()) 0 else fretted.max() - fretted.min()
    return (fretted.minOrNull() ?: 0) * 3 + span * 4 - played.count { it == 0 } * 2 + max(0, 5 - played.size)
}

private val barreShapes = mapOf(
    "major" to (listOf(0, 2, 2, 1, 0, 0) to listOf(-1, 0, 2, 2, 2, 0)),
    "minor" to (listOf(0, 2, 2, 0, 0, 0) to listOf(-1, 0, 2, 2, 1, 0)),
    "7" to (listOf(0, 2, 0, 1, 0, 0) to listOf(-1, 0, 2, 0, 2, 0)),
    "maj7" to (listOf(0, 2, 1, 1, 0, 0) to listOf(-1, 0, 2, 1, 2, 0)),
    "m7" to (listOf(0, 2, 0, 0, 0, 0) to listOf(-1, 0, 2, 0, 1, 0)),
    "sus2" to (listOf(0, 2, 4, 4, 0, 0) to listOf(-1, 0, 2, 2, 0, 0)),
    "sus4" to (listOf(0, 2, 2, 2, 0, 0) to listOf(-1, 0, 2, 2, 3, 0)),
    "5" to (listOf(0, 2, 2, -1, -1, -1) to listOf(-1, 0, 2, 2, -1, -1)),
    "7sus4" to (listOf(0, 2, 0, 2, 0, 0) to listOf(-1, 0, 2, 0, 3, 0)),
    "m7b5" to (listOf(0, 1, 0, 0, -1, -1) to listOf(-1, 0, 1, 0, 1, -1)),
    "6" to (listOf(0, 2, 2, 1, 2, 0) to listOf(-1, 0, 2, 2, 2, 2)),
    "m6" to (listOf(0, 2, 2, 0, 2, 0) to listOf(-1, 0, 2, 2, 1, 2)),
    "9" to (listOf(0, 2, 0, 1, 2, 0) to listOf(-1, 0, 2, 0, 2, 2)),
    "m9" to (listOf(0, 2, 0, 0, 2, 0) to listOf(-1, 0, 2, 0, 1, 2)),
    "maj9" to (listOf(0, 2, 1, 1, 2, 0) to listOf(-1, 0, 2, 1, 2, 2)),
    "mMaj7" to (listOf(0, 2, 1, 0, 0, 0) to listOf(-1, 0, 2, 1, 1, 0))
)

private val openVoicings = mapOf(
    "C" to listOf(-1, 3, 2, 0, 1, 0),
    "C7" to listOf(-1, 3, 2, 3, 1, 0),
    "Cmaj7" to listOf(-1, 3, 2, 0, 0, 0),
    "Cadd9" to listOf(-1, 3, 2, 0, 3, 3),
    "D" to listOf(-1, -1, 0, 2, 3, 2),
    "Dm" to listOf(-1, -1, 0, 2, 3, 1),
    "D7" to listOf(-1, -1, 0, 2, 1, 2),
    "Dm7" to listOf(-1, -1, 0, 2, 1, 1),
    "Dmaj7" to listOf(-1, -1, 0, 2, 2, 2),
    "Dsus2" to listOf(-1, -1, 0, 2, 3, 0),
    "Dsus4" to listOf(-1, -1, 0, 2, 3, 3),
    "E" to listOf(0, 2, 2, 1, 0, 0),
    "Em" to listOf(0, 2, 2, 0, 0, 0),
    "E7" to listOf(0, 2, 0, 1, 0, 0),
    "Em7" to listOf(0, 2, 0, 0, 0, 0),
    "Emaj7" to listOf(0, 2, 1, 1, 0, 0),
    "Esus2" to listOf(0, 2, 4, 4, 0, 0),
    "Esus4" to listOf(0, 2, 2, 2, 0, 0),
    "E5" to listOf(0, 2, 2, -1, -1, -1),
    "F" to listOf(1, 3, 3, 2, 1, 1),
    "Fm" to listOf(1, 3, 3, 1, 1, 1),
    "Fmaj7" to listOf(-1, -1, 3, 2, 1, 0),
    "G" to listOf(3, 2, 0, 0, 0, 3),
    "G7" to listOf(3, 2, 0, 0, 0, 1),
    "Gmaj7" to listOf(3, 2, 0, 0, 0, 2),
    "G5" to listOf(3, 5, 5, -1, -1, -1),
    "A" to listOf(-1, 0, 2, 2, 2, 0),
    "Am" to listOf(-1, 0, 2, 2, 1, 0),
    "A7" to listOf(-1, 0, 2, 0, 2, 0),
    "Am7" to listOf(-1, 0, 2, 0, 1, 0),
    "Amaj7" to listOf(-1, 0, 2, 1, 2, 0),
    "Asus2" to listOf(-1, 0, 2, 2, 0, 0),
    "Asus4" to listOf(-1, 0, 2, 2, 3, 0),
    "A5" to listOf(-1, 0, 2, 2, -1, -1),
    "B7" to listOf(-1, 2, 1, 2, 0, 2)
)
