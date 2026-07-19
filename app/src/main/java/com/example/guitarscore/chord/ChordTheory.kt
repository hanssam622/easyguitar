package com.example.guitarscore.chord

import kotlin.math.max

data class FretPosition(val stringIndex: Int, val fret: Int)

data class ChordMatch(
    val name: String,
    val rootPitchClass: Int,
    val pitchClasses: Set<Int>
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
    val pitchClasses: Set<Int>
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

private data class ChordQuality(val id: String, val suffix: String, val intervals: Set<Int>)

private val qualities = listOf(
    ChordQuality("major", "", setOf(0, 4, 7)),
    ChordQuality("minor", "m", setOf(0, 3, 7)),
    ChordQuality("7", "7", setOf(0, 4, 7, 10)),
    ChordQuality("maj7", "maj7", setOf(0, 4, 7, 11)),
    ChordQuality("m7", "m7", setOf(0, 3, 7, 10)),
    ChordQuality("sus2", "sus2", setOf(0, 2, 7)),
    ChordQuality("sus4", "sus4", setOf(0, 5, 7)),
    ChordQuality("dim", "dim", setOf(0, 3, 6)),
    ChordQuality("aug", "aug", setOf(0, 4, 8)),
    ChordQuality("6", "6", setOf(0, 4, 7, 9)),
    ChordQuality("m6", "m6", setOf(0, 3, 7, 9)),
    ChordQuality("add9", "add9", setOf(0, 2, 4, 7))
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
                    pitchClasses = quality.intervals.mapTo(linkedSetOf()) { (root + it) % 12 }
                )
            )
        }
    }
}
private val voicingCache = mutableMapOf<String, List<ChordVoicing>>()

fun noteNameAt(position: FretPosition): String =
    noteNames[(openStringPitchClasses[position.stringIndex] + position.fret) % 12]

fun detectChords(positions: Set<FretPosition>): List<ChordMatch> {
    if (positions.isEmpty()) return emptyList()
    val selectedPitchClasses = positions.mapTo(linkedSetOf()) {
        (openStringPitchClasses[it.stringIndex] + it.fret) % 12
    }
    val bassPitchClass = positions.minByOrNull {
        openStringMidis[it.stringIndex] + it.fret
    }?.let { (openStringPitchClasses[it.stringIndex] + it.fret) % 12 }

    return buildList {
        chordDefinitions.forEach { chord ->
            if (chord.pitchClasses == selectedPitchClasses) {
                add(chord.toMatch(bassPitchClass))
            }
        }
    }.sortedWith(compareBy<ChordMatch> { it.rootPitchClass != bassPitchClass }.thenBy { it.name.length })
}

fun suggestChords(positions: Set<FretPosition>, limit: Int = 5): List<ChordMatch> {
    if (positions.isEmpty()) return emptyList()
    val selectedPitchClasses = positions.mapTo(linkedSetOf()) {
        (openStringPitchClasses[it.stringIndex] + it.fret) % 12
    }
    if (selectedPitchClasses.size < 2) return emptyList()
    val bassPitchClass = positions.minByOrNull {
        openStringMidis[it.stringIndex] + it.fret
    }?.let { (openStringPitchClasses[it.stringIndex] + it.fret) % 12 }

    return chordDefinitions
        .map { chord ->
            val missing = chord.pitchClasses.count { it !in selectedPitchClasses }
            val extra = selectedPitchClasses.count { it !in chord.pitchClasses }
            Triple(chord, missing, extra)
        }
        .filter { (_, missing, extra) -> missing <= 1 && extra <= 1 }
        .sortedWith(
            compareBy<Triple<ChordDefinition, Int, Int>> { it.second * 3 + it.third * 4 }
                .thenBy { it.first.rootPitchClass != bassPitchClass }
                .thenBy { it.first.name.length }
        )
        .map { (chord, _, _) -> chord.toMatch(bassPitchClass) }
        .distinctBy { it.name }
        .take(limit)
}

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
                .thenBy { it.name.length }
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

    compactVoicings(chord).forEachIndexed { index, frets ->
        candidates.putIfAbsent(
            frets,
            createVoicing(chord.name, frets, if (index == 0) "컴팩트 보이싱" else "하이 포지션")
        )
    }

    return candidates.values
        .sortedWith(
            compareBy<ChordVoicing> { it.difficulty.ordinal }
                .thenBy {
                    when (it.label) {
                        "오픈 포지션" -> 0
                        "5번 줄 루트" -> 1
                        "6번 줄 루트" -> 2
                        "컴팩트 보이싱" -> 3
                        else -> 4
                    }
                }
                .thenBy { voicingScore(it.frets) }
        )
        .take(6)
}

private fun ChordDefinition.toMatch(bassPitchClass: Int?): ChordMatch {
    val displayName = if (bassPitchClass != null && bassPitchClass != rootPitchClass) {
        "$name/${noteNames[bassPitchClass]}"
    } else {
        name
    }
    return ChordMatch(displayName, rootPitchClass, pitchClasses)
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
    frets.withIndex()
        .filter { it.value > 0 && result[it.index] == 0 }
        .sortedWith(compareBy<IndexedValue<Int>> { it.value }.thenBy { it.index })
        .forEach { item ->
            result[item.index] = nextFinger.coerceAtMost(4)
            nextFinger = (nextFinger + 1).coerceAtMost(4)
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

private fun compactVoicings(chord: ChordDefinition): List<List<Int>> {
    val stringIndices = listOf(2, 3, 4, 5)
    val options = stringIndices.map { stringIndex ->
        (0..12).filter { fret ->
            (openStringPitchClasses[stringIndex] + fret) % 12 in chord.pitchClasses
        }
    }
    val results = mutableListOf<List<Int>>()
    for (d in options[0]) for (g in options[1]) for (b in options[2]) for (e in options[3]) {
        val played = listOf(d, g, b, e)
        val tones = played.mapIndexedTo(linkedSetOf()) { index, fret ->
            (openStringPitchClasses[stringIndices[index]] + fret) % 12
        }
        if (!tones.containsAll(chord.pitchClasses)) continue
        val fretted = played.filter { it > 0 }
        if (fretted.isNotEmpty() && fretted.max() - fretted.min() > 4) continue
        results += listOf(-1, -1, d, g, b, e)
    }
    return results
        .distinct()
        .sortedBy(::voicingScore)
        .take(4)
}

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
    "sus4" to (listOf(0, 2, 2, 2, 0, 0) to listOf(-1, 0, 2, 2, 3, 0))
)

private val openVoicings = mapOf(
    "C" to listOf(-1, 3, 2, 0, 1, 0),
    "C7" to listOf(-1, 3, 2, 3, 1, 0),
    "Cmaj7" to listOf(-1, 3, 2, 0, 0, 0),
    "Cadd9" to listOf(-1, 3, 2, 0, 3, 3),
    "D" to listOf(-1, -1, 0, 2, 3, 2),
    "Dm" to listOf(-1, -1, 0, 2, 3, 1),
    "D7" to listOf(-1, -1, 0, 2, 1, 2),
    "Dmaj7" to listOf(-1, -1, 0, 2, 2, 2),
    "Dsus2" to listOf(-1, -1, 0, 2, 3, 0),
    "Dsus4" to listOf(-1, -1, 0, 2, 3, 3),
    "E" to listOf(0, 2, 2, 1, 0, 0),
    "Em" to listOf(0, 2, 2, 0, 0, 0),
    "E7" to listOf(0, 2, 0, 1, 0, 0),
    "Emaj7" to listOf(0, 2, 1, 1, 0, 0),
    "Esus2" to listOf(0, 2, 4, 4, 0, 0),
    "Esus4" to listOf(0, 2, 2, 2, 0, 0),
    "F" to listOf(1, 3, 3, 2, 1, 1),
    "Fm" to listOf(1, 3, 3, 1, 1, 1),
    "Fmaj7" to listOf(-1, -1, 3, 2, 1, 0),
    "G" to listOf(3, 2, 0, 0, 0, 3),
    "G7" to listOf(3, 2, 0, 0, 0, 1),
    "Gmaj7" to listOf(3, 2, 0, 0, 0, 2),
    "A" to listOf(-1, 0, 2, 2, 2, 0),
    "Am" to listOf(-1, 0, 2, 2, 1, 0),
    "A7" to listOf(-1, 0, 2, 0, 2, 0),
    "Amaj7" to listOf(-1, 0, 2, 1, 2, 0),
    "Asus2" to listOf(-1, 0, 2, 2, 0, 0),
    "Asus4" to listOf(-1, 0, 2, 2, 3, 0),
    "B7" to listOf(-1, 2, 1, 2, 0, 2)
)
