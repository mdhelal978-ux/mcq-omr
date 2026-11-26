package com.example

import com.example.model.QuestionOutcome
import com.example.model.gradeOmrSheet
import com.example.model.normalizeOptionToken
import org.junit.Assert.assertEquals
import org.junit.Test

class ExampleUnitTest {
    @Test
    fun normalizeOptionToken_handlesBengaliAndEnglish() {
        assertEquals("A", normalizeOptionToken("ক"))
        assertEquals("B", normalizeOptionToken("খ"))
        assertEquals("C", normalizeOptionToken("গ"))
        assertEquals("D", normalizeOptionToken("ঘ"))
        assertEquals("A", normalizeOptionToken("a"))
        assertEquals("MULTIPLE", normalizeOptionToken("multiple"))
        assertEquals("NONE", normalizeOptionToken(""))
    }

    @Test
    fun gradeOmrSheet_computesNetScoreWithNegativeMarking() {
        val key = listOf("A", "B", "C", "D", "A")
        val detected = listOf("A", "B", "D", "NONE", "MULTIPLE")

        val result = gradeOmrSheet(
            answerKey = key,
            detectedAnswers = detected,
            marksPerCorrect = 1.0,
            negativeMark = 0.25
        )

        assertEquals(2, result.correctCount)
        assertEquals(2, result.wrongCount)
        assertEquals(1, result.unansweredCount)
        assertEquals(1.5, result.score, 0.001)
        assertEquals(5.0, result.maxScore, 0.001)
        assertEquals(30.0, result.percentage, 0.01)
        assertEquals(QuestionOutcome.CORRECT, result.breakdown[0].outcome)
        assertEquals(QuestionOutcome.WRONG, result.breakdown[2].outcome)
        assertEquals(QuestionOutcome.UNANSWERED, result.breakdown[3].outcome)
        assertEquals(QuestionOutcome.WRONG, result.breakdown[4].outcome)
    }
}
