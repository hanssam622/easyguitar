package com.example.guitarscore.chord

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class ChordQuizTest {
    @Test
    fun levelsCoverEveryChordExactlyOnce() {
        val assigned = quizLevels.flatMap { it.chordNames }
        val all = allChordDefinitions().map { it.name }

        assertEquals("단계에 중복 배정된 코드가 있다", assigned.size, assigned.distinct().size)
        assertEquals(all.toSet(), assigned.toSet())
    }

    @Test
    fun everyLevelCanFillFourChoices() {
        val tooSmall = quizLevels.filter { it.chordNames.size < QUIZ_MINIMUM_POOL }

        assertTrue("선택지를 채울 수 없는 단계: ${tooSmall.map { it.level }}", tooSmall.isEmpty())
    }

    @Test
    fun buildsRequestedNumberOfQuestionsWithFourDistinctOptions() {
        val questions = buildQuiz(quizLevels.first().chordNames, QuizMode.Mixed, 10, Random(7))

        assertEquals(10, questions.size)
        questions.forEach { question ->
            assertEquals(QUIZ_OPTION_COUNT, question.options.size)
            assertEquals(
                "선택지에 같은 코드가 두 번 나온다",
                QUIZ_OPTION_COUNT,
                question.options.map { it.chord.name }.distinct().size
            )
            assertTrue("정답이 선택지에 없다", question.correctIndex in question.options.indices)
        }
    }

    @Test
    fun mixedModeAlternatesBetweenQuestionTypes() {
        val questions = buildQuiz(quizLevels.first().chordNames, QuizMode.Mixed, 6, Random(3))

        assertTrue(questions.any { it.mode == QuizMode.NameFromDiagram })
        assertTrue(questions.any { it.mode == QuizMode.DiagramFromName })
    }

    @Test
    fun singleModeKeepsOneQuestionType() {
        val questions = buildQuiz(quizLevels.first().chordNames, QuizMode.DiagramFromName, 8, Random(1))

        assertTrue(questions.all { it.mode == QuizMode.DiagramFromName })
    }

    @Test
    fun diagramQuestionsNeverShowTwoIdenticalShapes() {
        quizLevels.forEach { level ->
            val questions = buildQuiz(level.chordNames, QuizMode.DiagramFromName, 20, Random(level.level))
            questions.forEach { question ->
                val shapes = question.options.map { it.voicing.frets }
                assertEquals(
                    "${level.title}: 같은 모양이 선택지에 두 번 나와 정답을 고를 수 없다",
                    shapes.size,
                    shapes.distinct().size
                )
            }
        }
    }

    @Test
    fun repeatsChordsWhenPoolIsSmallerThanQuestionCount() {
        val questions = buildQuiz(quizLevels.first().chordNames, QuizMode.NameFromDiagram, 20, Random(11))

        assertEquals(20, questions.size)
    }

    @Test
    fun refusesToBuildQuizFromTooFewChords() {
        assertTrue(buildQuiz(listOf("C", "G", "D"), QuizMode.Mixed, 10, Random(0)).isEmpty())
    }

    @Test
    fun ignoresUnknownChordNames() {
        val questions = buildQuiz(
            listOf("C", "G", "D", "Am", "존재하지않는코드"),
            QuizMode.NameFromDiagram,
            5,
            Random(5)
        )

        assertEquals(5, questions.size)
        assertTrue(questions.all { question -> question.options.all { it.chord.name != "존재하지않는코드" } })
    }
}
