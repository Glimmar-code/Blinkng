package com.blinkng.shared

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GeneralStudyBankTest {
    @Test
    fun bankHasExpectedCountsAndUniqueIds() {
        val ids = mutableSetOf<String>()
        GeneralStudyDifficulty.entries.forEach { difficulty ->
            repeat(GeneralStudyBank.QUESTIONS_PER_DIFFICULTY) { index ->
                val question = GeneralStudyBank.questionAt(difficulty, index)
                assertTrue(ids.add(question.id), "Duplicate id: " + question.id)
                assertEquals(difficulty, question.difficulty)
                assertTrue(question.category in GeneralStudyBank.categories)
                assertTrue(question.prompt.isNotBlank())
                assertTrue(question.options.size >= 2)
                assertTrue(question.correctIndex in question.options.indices)
                assertTrue(question.answer.isNotBlank())
                assertEquals(question.options.size, question.options.distinct().size)
            }
        }
        assertEquals(GeneralStudyBank.TOTAL_QUESTIONS, ids.size)
    }

    @Test
    fun eachDifficultyProvidesExactlyFiveThousandQuestions() {
        GeneralStudyDifficulty.entries.forEach { difficulty ->
            val first = GeneralStudyBank.questionAt(difficulty, 0)
            val last = GeneralStudyBank.questionAt(
                difficulty,
                GeneralStudyBank.QUESTIONS_PER_DIFFICULTY - 1,
            )
            assertTrue(first.id.startsWith("GS-" + difficulty.code + "-"))
            assertTrue(last.id.startsWith("GS-" + difficulty.code + "-"))
        }
    }

    @Test
    fun roundsAreDeterministicAndDoNotRepeatWithinAStandardRound() {
        GeneralStudyDifficulty.entries.forEach { difficulty ->
            val a = GeneralStudyBank.round(difficulty, seed = 42L)
            val b = GeneralStudyBank.round(difficulty, seed = 42L)
            assertEquals(a.map { it.id }, b.map { it.id })
            assertEquals(GeneralStudyBank.DEFAULT_ROUND_SIZE, a.size)
            assertEquals(a.size, a.map { it.id }.distinct().size)
        }
    }

    @Test
    fun sameYearHistoricalEventsAreNotIncorrectlyOrdered() {
        // History occupies category index 4; slot 94 compares two events from 2002.
        for (difficulty in listOf(GeneralStudyDifficulty.EASY, GeneralStudyDifficulty.HARD, GeneralStudyDifficulty.EXPERT)) {
            val question = GeneralStudyBank.questionAt(difficulty, 94 * 8 + 4)
            assertEquals("They happened in the same year", question.answer)
            assertEquals(4, question.options.distinct().size)
        }
    }

    @Test
    fun generalKnowledgeAbbreviationsIncludeBothAmAndPm() {
        // General Knowledge is category 7; slots 3 and 7 exercise both cases.
        val morning = GeneralStudyBank.questionAt(GeneralStudyDifficulty.MEDIUM, 3 * 8 + 7)
        val evening = GeneralStudyBank.questionAt(GeneralStudyDifficulty.MEDIUM, 7 * 8 + 7)
        assertTrue(morning.prompt.contains("AM."))
        assertEquals("ante meridiem", morning.answer)
        assertTrue(evening.prompt.contains("PM."))
        assertEquals("post meridiem", evening.answer)
    }

    @Test
    fun topicFilteredRoundsContainOnlyRequestedCategoryWithoutRepeats() {
        for (difficulty in GeneralStudyDifficulty.entries) {
            for (category in GeneralStudyBank.categories) {
                val round = GeneralStudyBank.round(
                    difficulty = difficulty,
                    seed = 12345L,
                    count = 50,
                    category = category,
                )
                assertEquals(50, round.size)
                assertTrue(round.all { it.category == category })
                assertEquals(50, round.map { it.id }.distinct().size)
            }
        }
    }
}
