package com.example.data

import android.graphics.Bitmap
import android.graphics.Color
import android.util.Base64
import android.util.Log
import com.example.BuildConfig
import com.example.model.AiConfidence
import com.example.model.OptionStyle
import com.example.model.normalizeOptionToken
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.util.concurrent.TimeUnit
import kotlin.math.max
import kotlin.math.roundToInt

data class OmrScanResult(
    val studentName: String?,
    val studentRoll: String?,
    val confidence: AiConfidence,
    val notes: String?,
    val detectedAnswers: List<String>
)

class OmrAiService {

    private val httpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    suspend fun scanAnswerSheet(
        bitmap: Bitmap,
        totalQuestions: Int,
        optionStyle: OptionStyle
    ): Result<OmrScanResult> {
        val apiKey = BuildConfig.GEMINI_API_KEY.trim()
        val hasGeminiKey = apiKey.isNotEmpty() && apiKey != "MY_GEMINI_API_KEY"

        if (!hasGeminiKey) {
            return scanOnDeviceOptical(bitmap, totalQuestions)
        }

        return try {
            val preparedBitmap = downscaleBitmap(bitmap, maxDimension = 1600)
            val jpegBytes = ByteArrayOutputStream().use { stream ->
                preparedBitmap.compress(Bitmap.CompressFormat.JPEG, 88, stream)
                stream.toByteArray()
            }
            val base64Image = Base64.encodeToString(jpegBytes, Base64.NO_WRAP)

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

            val payload = buildGeminiRequestJson(promptText, base64Image)
            val jsonText = withTimeout(60_000L) {
                withContext(Dispatchers.IO) {
                    val request = Request.Builder()
                        .url("https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent")
                        .addHeader("Content-Type", "application/json")
                        .addHeader("X-goog-api-key", apiKey)
                        .post(payload.toString().toRequestBody("application/json".toMediaType()))
                        .build()

                    httpClient.newCall(request).execute().use { response ->
                        val bodyStr = response.body?.string().orEmpty()
                        if (!response.isSuccessful) {
                            throw IllegalStateException("Gemini API error (${response.code})")
                        }
                        val rootObj = JSONObject(bodyStr)
                        val candidates = rootObj.optJSONArray("candidates")
                            ?: throw IllegalStateException("No candidates returned by Gemini API.")
                        val parts = candidates.getJSONObject(0)
                            .getJSONObject("content")
                            .getJSONArray("parts")
                        parts.getJSONObject(0).getString("text")
                    }
                }
            }

            Result.success(parseOmrResponseJson(jsonText, totalQuestions))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Gemini REST OMR scan failed, falling back to on-device optical scan", e)
            scanOnDeviceOptical(bitmap, totalQuestions)
        }
    }

    private suspend fun scanOnDeviceOptical(
        bitmap: Bitmap,
        totalQuestions: Int
    ): Result<OmrScanResult> = withContext(Dispatchers.Default) {
        try {
            val scaled = downscaleBitmap(bitmap, maxDimension = 960)
            val w = scaled.width
            val h = scaled.height
            val pixels = IntArray(w * h)
            scaled.getPixels(pixels, 0, w, 0, 0, w, h)

            // Compute grayscale luminance (0..255) and Otsu adaptive threshold
            val luma = IntArray(w * h)
            val histogram = IntArray(256)
            for (i in pixels.indices) {
                val p = pixels[i]
                val lum = (Color.red(p) * 30 + Color.green(p) * 59 + Color.blue(p) * 11) / 100
                luma[i] = lum
                histogram[lum]++
            }
            val threshold = computeOtsuThreshold(histogram, pixels.size).coerceIn(60, 185)

            // Find active sheet ink bounds ignoring outer bright margins (top 15% header ignored for bubbles)
            val startX = (w * 0.08f).roundToInt()
            val endX = (w * 0.92f).roundToInt()
            val startY = (h * 0.16f).roundToInt()
            val endY = (h * 0.92f).roundToInt()

            val columns = if (totalQuestions > 25) 2 else 1
            val rowsPerCol = (totalQuestions + columns - 1) / columns
            val colWidth = (endX - startX) / columns
            val rowHeight = max(1, (endY - startY) / rowsPerCol)

            val detected = MutableList(totalQuestions) { "NONE" }
            val optionTokens = listOf("A", "B", "C", "D")

            for (qIdx in 0 until totalQuestions) {
                val col = qIdx / rowsPerCol
                val row = qIdx % rowsPerCol
                val cellX0 = startX + col * colWidth + (colWidth * 0.20f).roundToInt()
                val cellX1 = startX + (col + 1) * colWidth - (colWidth * 0.06f).roundToInt()
                val cellY0 = startY + row * rowHeight + (rowHeight * 0.15f).roundToInt()
                val cellY1 = startY + (row + 1) * rowHeight - (rowHeight * 0.15f).roundToInt()

                val optWidth = max(1, (cellX1 - cellX0) / 4)
                val fillRatios = DoubleArray(4)

                for (opt in 0 until 4) {
                    val ox0 = cellX0 + opt * optWidth
                    val ox1 = (ox0 + optWidth).coerceAtMost(w)
                    var darkCount = 0
                    var totalCount = 0
                    for (y in cellY0.coerceIn(0, h - 1) until cellY1.coerceIn(0, h)) {
                        val rowOffset = y * w
                        for (x in ox0.coerceIn(0, w - 1) until ox1) {
                            if (luma[rowOffset + x] < threshold) darkCount++
                            totalCount++
                        }
                    }
                    fillRatios[opt] = if (totalCount > 0) darkCount.toDouble() / totalCount else 0.0
                }

                val sorted = fillRatios.sortedDescending()
                val bestRatio = sorted[0]
                val secondRatio = sorted[1]
                val baseline = (sorted[2] + sorted[3]) / 2.0

                detected[qIdx] = when {
                    bestRatio < baseline + 0.08 || bestRatio < 0.14 -> "NONE"
                    secondRatio > baseline + 0.08 && secondRatio >= bestRatio * 0.78 -> "MULTIPLE"
                    else -> optionTokens[fillRatios.indices.maxBy { fillRatios[it] }]
                }
            }

            Result.success(
                OmrScanResult(
                    studentName = null,
                    studentRoll = null,
                    confidence = AiConfidence.LOW,
                    notes = "অফলাইন অপটিক্যাল স্ক্যান মোডে পড়া হয়েছে—জমা দেওয়ার আগে অনুগ্রহ করে বৃত্তগুলো যাচাই করে নিন।",
                    detectedAnswers = detected
                )
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "On-device OMR scan failed", e)
            Result.failure(e)
        }
    }

    private fun computeOtsuThreshold(histogram: IntArray, total: Int): Int {
        var sum = 0L
        for (i in 0..255) sum += i.toLong() * histogram[i]
        var sumB = 0L
        var wB = 0
        var maxVar = 0.0
        var threshold = 128
        for (t in 0..255) {
            wB += histogram[t]
            if (wB == 0) continue
            val wF = total - wB
            if (wF == 0) break
            sumB += t.toLong() * histogram[t]
            val mB = sumB.toDouble() / wB
            val mF = (sum - sumB).toDouble() / wF
            val between = wB.toDouble() * wF.toDouble() * (mB - mF) * (mB - mF)
            if (between > maxVar) {
                maxVar = between
                threshold = t
            }
        }
        return threshold
    }

    private fun parseOmrResponseJson(jsonText: String, totalQuestions: Int): OmrScanResult {
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
        val arr = root.optJSONArray("answers")
        if (arr != null) {
            for (i in 0 until arr.length()) {
                val item = arr.optJSONObject(i) ?: continue
                val qNum = item.optInt("questionNumber", i + 1)
                val opt = normalizeOptionToken(item.optString("selectedOption", "NONE"))
                if (qNum in 1..totalQuestions) {
                    answerMap[qNum] = opt
                }
            }
        }
        val normalizedList = (1..totalQuestions).map { q -> answerMap[q] ?: "NONE" }
        return OmrScanResult(
            studentName = studentName,
            studentRoll = studentRoll,
            confidence = confidence,
            notes = notes,
            detectedAnswers = normalizedList
        )
    }

    private fun buildGeminiRequestJson(promptText: String, base64Jpeg: String): JSONObject {
        val answerItemSchema = JSONObject().apply {
            put("type", "OBJECT")
            put("properties", JSONObject().apply {
                put("questionNumber", JSONObject().put("type", "INTEGER"))
                put(
                    "selectedOption",
                    JSONObject()
                        .put("type", "STRING")
                        .put("enum", JSONArray(listOf("A", "B", "C", "D", "NONE", "MULTIPLE")))
                )
            })
            put("required", JSONArray(listOf("questionNumber", "selectedOption")))
        }

        val responseSchema = JSONObject().apply {
            put("type", "OBJECT")
            put("properties", JSONObject().apply {
                put("studentName", JSONObject().put("type", "STRING").put("nullable", true))
                put("studentRoll", JSONObject().put("type", "STRING").put("nullable", true))
                put(
                    "confidence",
                    JSONObject()
                        .put("type", "STRING")
                        .put("enum", JSONArray(listOf("high", "medium", "low")))
                )
                put("notes", JSONObject().put("type", "STRING").put("nullable", true))
                put(
                    "answers",
                    JSONObject()
                        .put("type", "ARRAY")
                        .put("items", answerItemSchema)
                )
            })
            put("required", JSONArray(listOf("confidence", "answers")))
        }

        return JSONObject().apply {
            put(
                "contents",
                JSONArray().put(
                    JSONObject().put(
                        "parts",
                        JSONArray()
                            .put(
                                JSONObject().put(
                                    "inline_data",
                                    JSONObject()
                                        .put("mime_type", "image/jpeg")
                                        .put("data", base64Jpeg)
                                )
                            )
                            .put(JSONObject().put("text", promptText))
                    )
                )
            )
            put(
                "generationConfig",
                JSONObject()
                    .put("responseMimeType", "application/json")
                    .put("responseSchema", responseSchema)
                    .put("temperature", 0.1)
            )
        }
    }

    private fun downscaleBitmap(source: Bitmap, maxDimension: Int): Bitmap {
        val w = source.width
        val h = source.height
        val longest = max(w, h)
        if (longest <= maxDimension) return source
        val scale = maxDimension.toFloat() / longest.toFloat()
        return Bitmap.createScaledBitmap(
            source,
            (w * scale).toInt().coerceAtLeast(1),
            (h * scale).toInt().coerceAtLeast(1),
            true
        )
    }

    private companion object {
        const val TAG = "OmrAiService"
    }
}
