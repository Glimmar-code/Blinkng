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
}
