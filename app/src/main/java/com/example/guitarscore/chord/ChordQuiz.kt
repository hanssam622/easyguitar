package com.example.guitarscore.chord

import kotlin.random.Random

enum class QuizMode(val label: String, val summary: String) {
    NameFromDiagram("이름 맞히기", "운지를 보고 코드 이름을 고른다"),
    DiagramFromName("운지 맞히기", "코드 이름을 보고 운지를 고른다"),
    Mixed("섞어서", "두 방식을 번갈아 낸다")
}

data class QuizLevel(
    val level: Int,
    val title: String,
    val summary: String,
    val chordNames: List<String>
)

/** 오픈 코드에서 텐션 코드로 넘어가는 순서. 앞 단계일수록 실제로 먼저 배우는 코드다. */
private val level1Names = listOf("C", "D", "E", "G", "A", "Am", "Em", "Dm")
private val level2Names = listOf("C7", "D7", "E7", "G7", "A7", "B7", "Am7", "Em7", "Dm7")

val quizLevels: List<QuizLevel> by lazy {
    val basicQualities = setOf("major", "minor")
    val seventhQualities = setOf("7", "m7", "maj7")
    val colorQualities = setOf("sus4", "sus2", "5", "6", "m6", "add9", "madd9")
    val earlyNames = (level1Names + level2Names).toSet()

    listOf(
        QuizLevel(1, "기초 오픈 코드", "가장 먼저 배우는 오픈 코드 8개", level1Names),
        QuizLevel(2, "오픈 세븐스", "오픈 포지션으로 잡는 세븐스 코드", level2Names),
        QuizLevel(
            3,
            "바레 코드",
            "1·2단계에 없는 나머지 메이저·마이너",
            allChordDefinitions()
                .filter { it.qualityId in basicQualities && it.name !in earlyNames }
                .map { it.name }
        ),
        QuizLevel(
            4,
            "세븐스 전체",
            "7 · m7 · maj7 를 모든 조성에서",
            allChordDefinitions()
                .filter { it.qualityId in seventhQualities && it.name !in earlyNames }
                .map { it.name }
        ),
        QuizLevel(
            5,
            "서스 · 애드 · 식스",
            "sus2 · sus4 · add9 · 6 · 파워 코드",
            allChordDefinitions().filter { it.qualityId in colorQualities }.map { it.name }
        ),
        QuizLevel(
            6,
            "텐션 · 특수 코드",
            "dim · m7b5 · aug · 9 · maj9 등",
            allChordDefinitions()
                .filter {
                    it.qualityId !in basicQualities &&
                        it.qualityId !in seventhQualities &&
                        it.qualityId !in colorQualities
                }
                .map { it.name }
        )
    )
}

data class QuizOption(val chord: ChordDefinition, val voicing: ChordVoicing)

data class QuizQuestion(
    /** Mixed 는 문제마다 둘 중 하나로 정해진다. 여기 담기는 값은 항상 실제 출제 방식이다. */
    val mode: QuizMode,
    val answer: QuizOption,
    val options: List<QuizOption>
) {
    val correctIndex: Int get() = options.indexOfFirst { it.chord.name == answer.chord.name }
}

const val QUIZ_OPTION_COUNT = 4
const val QUIZ_DEFAULT_LENGTH = 10

/** 선택지 4개를 만들 수 있는 최소 코드 수. 이보다 적으면 문제를 낼 수 없다. */
const val QUIZ_MINIMUM_POOL = QUIZ_OPTION_COUNT

/**
 * 주어진 코드 목록으로 객관식 문제를 만든다.
 *
 * 오답 선택지는 같은 목록 안에서 고른다. 아무 코드나 섞으면 너무 쉬워져서, 헷갈리는 코드끼리
 * 붙여 놓아야 외우는 데 도움이 되기 때문이다. 운지 문제에서는 답과 운지가 같은 코드를 오답으로
 * 쓰지 않는다(예: 이름만 다르고 같은 모양인 경우).
 */
fun buildQuiz(
    chordNames: List<String>,
    mode: QuizMode,
    questionCount: Int = QUIZ_DEFAULT_LENGTH,
    random: Random = Random.Default
): List<QuizQuestion> {
    val pool = chordNames
        .distinct()
        .mapNotNull { name -> chordDefinition(name) }
        .mapNotNull { chord -> generateVoicings(chord).firstOrNull()?.let { QuizOption(chord, it) } }
    if (pool.size < QUIZ_MINIMUM_POOL) return emptyList()

    val order = buildList {
        // 코드 수가 문제 수보다 적으면 한 바퀴 더 돌되, 매번 순서를 다시 섞는다.
        while (size < questionCount) addAll(pool.shuffled(random))
    }.take(questionCount)

    return order.mapIndexed { index, answer ->
        val questionMode = when (mode) {
            QuizMode.Mixed -> if (index % 2 == 0) QuizMode.NameFromDiagram else QuizMode.DiagramFromName
            else -> mode
        }
        val others = pool.filter { it.chord.name != answer.chord.name }
        val preferred = others
            .filterNot { questionMode == QuizMode.DiagramFromName && it.voicing.frets == answer.voicing.frets }
            .shuffled(random)
        // 모양이 겹치는 코드를 걸러내다 선택지가 모자라면, 남는 코드로 채워서 항상 4개를 만든다.
        val distractors = (preferred + others.shuffled(random))
            .distinctBy { it.chord.name }
            .take(QUIZ_OPTION_COUNT - 1)
        QuizQuestion(
            mode = questionMode,
            answer = answer,
            options = (distractors + answer).shuffled(random)
        )
    }
}
