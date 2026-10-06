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
}
