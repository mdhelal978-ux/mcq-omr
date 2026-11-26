package com.example

import com.example.data.QuestionPaperOpticalDetector
import com.example.model.OptionArrangement
import com.example.model.QuestionColumnMode
import com.example.model.QuestionOutcome
import com.example.model.gradeOmrSheet
import com.example.model.normalizeOptionToken
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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

    @Test
    fun opticalDetector_grid2x2QuestionPaper_picksSingleDarkestAndIgnoresPrintedText() {
        val w = 400
        val h = 600
        val luma = IntArray(w * h) { idx ->
            val x = idx % w
            // Simulate uneven lighting gradient across paper (245 on left -> 205 on right)
            245 - (x * 40 / w)
        }

        val expected = listOf("A", "B", "C", "D", "B")
        val topY = 60
        val rowH = 100

        for (q in expected.indices) {
            val bandY0 = topY + q * rowH
            // Simulate 1-px thin printed question stem lines across the top of the question
            for (x in 30..340) {
                if (x % 3 != 0) {
                    luma[(bandY0 + 10) * w + x] = 40
                    luma[(bandY0 + 12) * w + x] = 45
                }
            }
            // Simulate thin printed option text/rings at all 4 2x2 positions
            val centers = listOf(
                100 to (bandY0 + 42), // A (top-left)
                280 to (bandY0 + 42), // B (top-right)
                100 to (bandY0 + 78), // C (bottom-left)
                280 to (bandY0 + 78)  // D (bottom-right)
            )
            centers.forEach { (cx, cy) ->
                drawThinRing(luma, w, h, cx, cy, radius = 8, valLuma = 55)
            }
            // Add a light stray smudge on another option to verify single-darkest rule
            val strayIdx = (expected[q][0] - 'A' + 1) % 4
            val (sx, sy) = centers[strayIdx]
            drawFilledCircle(luma, w, h, sx, sy, radius = 4, valLuma = 150)

            // Darken the actual chosen bubble solidly
            val chosenIdx = expected[q][0] - 'A'
            val (cx, cy) = centers[chosenIdx]
            drawFilledCircle(luma, w, h, cx, cy, radius = 8, valLuma = 25)
        }

        val detected = QuestionPaperOpticalDetector.detectAnswersFromLuma(
            luma = luma,
            width = w,
            height = h,
            totalQuestions = expected.size,
            columnMode = QuestionColumnMode.ONE_COL,
            optionArrangement = OptionArrangement.GRID_2X2
        )

        assertEquals(expected, detected)
        assertFalse(detected.contains("MULTIPLE"))
    }

    @Test
    fun opticalDetector_twoColumnHorizontalPaper_neverReturnsMultiple() {
        val w = 600
        val h = 500
        val luma = IntArray(w * h) { 240 }

        // 6 questions: 3 in left column, 3 in right column
        val expected = listOf("A", "C", "D", "B", "A", "D")
        val topY = 50
        val rowH = 130
        for (q in expected.indices) {
            val col = q / 3
            val row = q % 3
            val bandY0 = topY + row * rowH
            val colX0 = if (col == 0) 40 else 330
            val colSpan = 230
            // Printed thin horizontal text noise in band
            for (x in colX0..(colX0 + colSpan)) {
                if (x % 4 != 0) luma[(bandY0 + 25) * w + x] = 50
            }
            val slotCentersX = listOf(
                colX0 + 30,
                colX0 + 90,
                colX0 + 150,
                colX0 + 210
            )
            val cy = bandY0 + 65
            slotCentersX.forEach { cx ->
                drawThinRing(luma, w, h, cx, cy, radius = 8, valLuma = 55)
            }
            val chosenIdx = expected[q][0] - 'A'
            drawFilledCircle(luma, w, h, slotCentersX[chosenIdx], cy, radius = 8, valLuma = 20)
        }

        val detected = QuestionPaperOpticalDetector.detectAnswersFromLuma(
            luma = luma,
            width = w,
            height = h,
            totalQuestions = expected.size,
            columnMode = QuestionColumnMode.TWO_COL,
            optionArrangement = OptionArrangement.HORIZONTAL
        )

        assertEquals(expected, detected)
        assertFalse(detected.contains("MULTIPLE"))
    }

    private fun drawFilledCircle(luma: IntArray, w: Int, h: Int, cx: Int, cy: Int, radius: Int, valLuma: Int) {
        for (y in (cy - radius)..(cy + radius)) {
            if (y !in 0 until h) continue
            for (x in (cx - radius)..(cx + radius)) {
                if (x !in 0 until w) continue
                if ((x - cx) * (x - cx) + (y - cy) * (y - cy) <= radius * radius) {
                    luma[y * w + x] = valLuma
                }
            }
        }
    }

    private fun drawThinRing(luma: IntArray, w: Int, h: Int, cx: Int, cy: Int, radius: Int, valLuma: Int) {
        for (y in (cy - radius)..(cy + radius)) {
            if (y !in 0 until h) continue
            for (x in (cx - radius)..(cx + radius)) {
                if (x !in 0 until w) continue
                val d2 = (x - cx) * (x - cx) + (y - cy) * (y - cy)
                if (d2 in ((radius - 1) * (radius - 1))..(radius * radius)) {
                    luma[y * w + x] = valLuma
                }
            }
        }
    }
}
