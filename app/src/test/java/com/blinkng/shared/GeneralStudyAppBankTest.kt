package com.blinkng.shared

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GeneralStudyAppBankTest {
    @Test
    fun allTwentyThousandQuestionsAreValidAndUniquelyIdentified() {
        val ids = HashSet<String>(GeneralStudyBank.TOTAL_QUESTIONS)

        GeneralStudyDifficulty.entries.forEach { difficulty ->
            repeat(GeneralStudyBank.QUESTIONS_PER_DIFFICULTY) { index ->
                val question = GeneralStudyBank.questionAt(difficulty, index)

                assertTrue("Duplicate id: " + question.id, ids.add(question.id))
                assertEquals(difficulty, question.difficulty)
                assertTrue(question.category in GeneralStudyBank.categories)
                assertTrue(question.prompt.isNotBlank())
                assertEquals(4, question.options.size)
                assertEquals(
                    "Duplicate answer choices in " + question.id,
                    question.options.size,
                    question.options.distinct().size,
                )
                assertTrue(question.correctIndex in question.options.indices)
                assertTrue(question.answer.isNotBlank())
            }
        }

        assertEquals(GeneralStudyBank.TOTAL_QUESTIONS, ids.size)
    }

    @Test
    fun everyDifficultyBankContainsFiveThousandAddressableQuestions() {
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
    fun aStandardRoundIsDeterministicAndHasNoRepeatedQuestion() {
        GeneralStudyDifficulty.entries.forEach { difficulty ->
            val first = GeneralStudyBank.round(difficulty, seed = 42L)
            val second = GeneralStudyBank.round(difficulty, seed = 42L)

            assertEquals(first.map { it.id }, second.map { it.id })
            assertEquals(GeneralStudyBank.DEFAULT_ROUND_SIZE, first.size)
            assertEquals(first.size, first.map { it.id }.distinct().size)
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
