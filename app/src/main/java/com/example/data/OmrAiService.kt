package com.example.data

import android.graphics.Bitmap
import android.util.Log
import com.example.model.AiConfidence
import com.example.model.OptionStyle
import com.example.model.normalizeOptionToken
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.Schema
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.generationConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import org.json.JSONObject
import kotlin.math.max

data class OmrScanResult(
    val studentName: String?,
    val studentRoll: String?,
    val confidence: AiConfidence,
    val notes: String?,
    val detectedAnswers: List<String>
)

class OmrAiService {

    private val omrSchema = Schema.obj(
        properties = mapOf(
            "studentName" to Schema.string(
                description = "Student name if written or printed on the sheet, otherwise null",
                nullable = true
            ),
            "studentRoll" to Schema.string(
                description = "Student roll or ID number if written or bubbled on the sheet, otherwise null",
                nullable = true
            ),
            "confidence" to Schema.enumeration(
                values = listOf("high", "medium", "low"),
                description = "Overall confidence in reading the filled MCQ bubbles"
            ),
            "notes" to Schema.string(
                description = "Brief note if any part is blurry, crooked, or double-marked, otherwise null",
                nullable = true
            ),
            "answers" to Schema.array(
                items = Schema.obj(
                    properties = mapOf(
                        "questionNumber" to Schema.integer(description = "1-based question index"),
                        "selectedOption" to Schema.enumeration(
                            values = listOf("A", "B", "C", "D", "NONE", "MULTIPLE"),
                            description = "Normalized marked option: map Bengali ক->A, খ->B, গ->C, ঘ->D"
                        )
                    )
                ),
                description = "Detected answer for every question from 1 to totalQuestions"
            )
        ),
        optionalProperties = listOf("studentName", "studentRoll", "notes")
    )

    private val model by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-3.8-flash",
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = omrSchema
                temperature = 0.1f
            }
        )
    }

    suspend fun scanAnswerSheet(
        bitmap: Bitmap,
        totalQuestions: Int,
        optionStyle: OptionStyle
    ): Result<OmrScanResult> {
        return try {
            val preparedBitmap = downscaleBitmap(bitmap, maxDimension = 1600)
            val styleInstruction = when (optionStyle) {
                OptionStyle.ABCD -> "Options are labeled A, B, C, D (or 1, 2, 3, 4)."
                OptionStyle.BN_KAKH -> "Options may be labeled in Bengali as ক, খ, গ, ঘ (or A, B, C, D). Always normalize ক -> A, খ -> B, গ -> C, ঘ -> D."
            }

            val promptText = """
                You are a precision optical OMR answer sheet reader for school MCQ exams.
                Analyze the attached MCQ answer sheet photo carefully.
                - Total expected questions: $totalQuestions (numbered 1 through $totalQuestions).
                - $styleInstruction
                - Look closely at each question row in order (1..$totalQuestions), whether arranged in 1 or multiple columns.
                - Identify which bubble/circle is darkened, filled in, ticked, or clearly marked by the student.
                - If a question has NO filled/marked bubble, return "NONE".
                - If a question has MORE THAN ONE bubble filled/marked, return "MULTIPLE".
                - Ignore crossed-out corrections if another bubble is clearly darkened, unless ambiguous (then "MULTIPLE").
                - Extract studentName and studentRoll if visibly written/bubbled on the header, otherwise null.
                - Return an entry in `answers` for every question from 1 to $totalQuestions.
            """.trimIndent()

            val response = withTimeout(60_000L) {
                model.generateContent(
                    content {
                        image(preparedBitmap)
                        text(promptText)
                    }
                )
            }

            val jsonText = response.text
                ?: return Result.failure(IllegalStateException("AI scanner returned an empty response."))

            val root = JSONObject(jsonText)
            val studentName = root.optString("studentName").takeIf {
                it.isNotBlank() && !it.equals("null", ignoreCase = true)
            }
            val studentRoll = root.optString("studentRoll").takeIf {
                it.isNotBlank() && !it.equals("null", ignoreCase = true)
            }
            val confidence = AiConfidence.fromRaw(root.optString("confidence", "medium"))
            val notes = root.optString("notes").takeIf {
                it.isNotBlank() && !it.equals("null", ignoreCase = true)
            }

            val answerMap = mutableMapOf<Int, String>()
            val answersArray = root.optJSONArray("answers")
            if (answersArray != null) {
                for (i in 0 until answersArray.length()) {
                    val obj = answersArray.optJSONObject(i) ?: continue
                    val qNum = obj.optInt("questionNumber", i + 1)
                    val selected = normalizeOptionToken(obj.optString("selectedOption", "NONE"))
                    if (qNum in 1..totalQuestions) {
                        answerMap[qNum] = selected
                    }
                }
            }

            val orderedAnswers = (1..totalQuestions).map { qNum ->
                answerMap[qNum] ?: "NONE"
            }

            Result.success(
                OmrScanResult(
                    studentName = studentName,
                    studentRoll = studentRoll,
                    confidence = confidence,
                    notes = notes,
                    detectedAnswers = orderedAnswers
                )
            )
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "OMR AI scan timed out", e)
            Result.failure(Exception("Scanning timed out after 60 seconds. Please retry with a clearer photo.", e))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "OMR AI scan failed", e)
            Result.failure(e)
        }
    }

    private fun downscaleBitmap(source: Bitmap, maxDimension: Int): Bitmap {
        val width = source.width
        val height = source.height
        val longest = max(width, height)
        if (longest <= maxDimension) return source
        val scale = maxDimension.toFloat() / longest.toFloat()
        val targetW = (width * scale).toInt().coerceAtLeast(1)
        val targetH = (height * scale).toInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(source, targetW, targetH, true)
    }

    private companion object {
        const val TAG = "OmrAiService"
    }
}
