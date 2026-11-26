package com.example.data

import android.graphics.Bitmap
import android.graphics.Color
import android.util.Base64
import android.util.Log
import com.example.BuildConfig
import com.example.model.AiConfidence
import com.example.model.OptionArrangement
import com.example.model.OptionStyle
import com.example.model.QuestionColumnMode
import com.example.model.ScannerEngineMode
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
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

data class OmrScanResult(
    val studentName: String?,
    val studentRoll: String?,
    val confidence: AiConfidence,
    val notes: String?,
    val detectedAnswers: List<String>,
    val engineMode: ScannerEngineMode
)

class OmrAiService {

    private val httpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    fun resolveEffectiveApiKey(userApiKey: String?): String {
        val custom = userApiKey?.trim().orEmpty()
        if (custom.isNotEmpty() && custom != PLACEHOLDER_KEY) return custom
        val builtIn = BuildConfig.GEMINI_API_KEY.trim()
        return if (builtIn.isNotEmpty() && builtIn != PLACEHOLDER_KEY) builtIn else ""
    }

    suspend fun scanAnswerSheet(
        bitmap: Bitmap,
        totalQuestions: Int,
        optionStyle: OptionStyle,
        columnMode: QuestionColumnMode = QuestionColumnMode.AUTO,
        optionArrangement: OptionArrangement = OptionArrangement.AUTO,
        userApiKey: String? = null
    ): Result<OmrScanResult> {
        val apiKey = resolveEffectiveApiKey(userApiKey)
        if (apiKey.isEmpty()) {
            return scanOnDeviceOptical(bitmap, totalQuestions, columnMode, optionArrangement)
        }

        return try {
            val preparedBitmap = downscaleBitmap(bitmap, maxDimension = 1600)
            val jpegBytes = ByteArrayOutputStream().use { stream ->
                preparedBitmap.compress(Bitmap.CompressFormat.JPEG, 88, stream)
                stream.toByteArray()
            }
            val base64Image = Base64.encodeToString(jpegBytes, Base64.NO_WRAP)

            val styleInstruction = when (optionStyle) {
                OptionStyle.ABCD -> "Options are labeled A, B, C, D (or a, b, c, d / 1, 2, 3, 4)."
                OptionStyle.BN_KAKH -> "Options are labeled in Bengali as ক, খ, গ, ঘ (or A, B, C, D). Always map ক -> A, খ -> B, গ -> C, ঘ -> D."
            }
            val columnHint = when (columnMode) {
                QuestionColumnMode.AUTO -> "Questions may be arranged in 1 or 2 columns."
                QuestionColumnMode.ONE_COL -> "Questions are arranged in a single vertical column."
                QuestionColumnMode.TWO_COL -> "Questions are arranged in 2 side-by-side columns (top-to-bottom per column)."
            }
            val layoutHint = when (optionArrangement) {
                OptionArrangement.AUTO -> "Each question's 4 options may be arranged inline horizontally, in a 2x2 grid, or stacked vertically."
                OptionArrangement.HORIZONTAL -> "Each question's 4 options are arranged side-by-side horizontally."
                OptionArrangement.GRID_2X2 -> "Each question's 4 options are arranged in a 2x2 grid (top-left A/ক, top-right B/খ, bottom-left C/গ, bottom-right D/ঘ)."
                OptionArrangement.VERTICAL -> "Each question's 4 options are stacked vertically one below another."
            }

            val promptText = """
                You are a precision MCQ paper & OMR reader for school exams.
                Analyze the attached image carefully. Students may have darkened/filled circles, shaded option bubbles, or circled/ticked answers directly on the printed MCQ question paper beside the options (not necessarily on a separate uniform OMR sheet).
                - Total expected questions: $totalQuestions (numbered 1 through $totalQuestions).
                - $styleInstruction
                - $columnHint
                - $layoutHint
                - Examine each question (1..$totalQuestions) in order and identify which single option circle/mark is darkened or filled by the student.
                - CRITICAL RULE: If there are faint stray marks, printed text noise, shadows, or a light cross-out alongside one clearly darker/heavier filled circle, ALWAYS pick that SINGLE darkest option (`A`, `B`, `C`, or `D`) — do NOT return `MULTIPLE`.
                - Only return `NONE` if a question has clearly NO filled/marked option at all.
                - Only return `MULTIPLE` in the rare case where two separate options are equally and heavily filled with identical dark intensity.
                - Extract studentName and studentRoll if visibly written at the top, otherwise null.
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
            scanOnDeviceOptical(
                bitmap = bitmap,
                totalQuestions = totalQuestions,
                columnMode = columnMode,
                optionArrangement = optionArrangement,
                fallbackNotice = "AI সংযোগ ব্যর্থ হওয়ায় অফলাইন অপটিক্যাল মোডে সবচেয়ে গাঢ় বৃত্তটি শনাক্ত করা হয়েছে—জমা দেওয়ার আগে যাচাই করুন।"
            )
        }
    }

    private suspend fun scanOnDeviceOptical(
        bitmap: Bitmap,
        totalQuestions: Int,
        columnMode: QuestionColumnMode,
        optionArrangement: OptionArrangement,
        fallbackNotice: String? = null
    ): Result<OmrScanResult> = withContext(Dispatchers.Default) {
        try {
            val scaled = downscaleBitmap(bitmap, maxDimension = 1080)
            val w = scaled.width
            val h = scaled.height
            val pixels = IntArray(w * h)
            scaled.getPixels(pixels, 0, w, 0, 0, w, h)

            val luma = IntArray(w * h)
            for (i in pixels.indices) {
                val p = pixels[i]
                luma[i] = (Color.red(p) * 30 + Color.green(p) * 59 + Color.blue(p) * 11) / 100
            }

            val detected = QuestionPaperOpticalDetector.detectAnswersFromLuma(
                luma = luma,
                width = w,
                height = h,
                totalQuestions = totalQuestions,
                columnMode = columnMode,
                optionArrangement = optionArrangement
            )

            val answeredCount = detected.count { it != "NONE" }
            val confidence = when {
                answeredCount >= (totalQuestions * 0.7f).roundToInt() -> AiConfidence.MEDIUM
                else -> AiConfidence.LOW
            }

            Result.success(
                OmrScanResult(
                    studentName = null,
                    studentRoll = null,
                    confidence = confidence,
                    notes = fallbackNotice
                        ?: "অফলাইন প্রশ্নপত্র মোডে প্রতিটি প্রশ্নের সবচেয়ে গাঢ় বৃত্তটিকে একক উত্তর ধরা হয়েছে। জমা দেওয়ার আগে একবার দেখে নিন।",
                    detectedAnswers = detected,
                    engineMode = ScannerEngineMode.OFFLINE_OPTICAL
                )
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "On-device OMR scan failed", e)
            Result.failure(e)
        }
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
            detectedAnswers = normalizedList,
            engineMode = ScannerEngineMode.GEMINI_AI
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
        const val PLACEHOLDER_KEY = "MY_GEMINI_API_KEY"
    }
}

/**
 * Pure Kotlin optical reader designed for MCQ question papers where students fill circles
 * directly next to printed options. Uses integral-image adaptive lighting normalization,
 * thin-text stroke suppression (solid-core filter), and strict single-darkest winner selection.
 */
internal object QuestionPaperOpticalDetector {

    private data class SolidBlob(
        val centerX: Float,
        val centerY: Float,
        val area: Int,
        val strength: Double
    )

    fun detectAnswersFromLuma(
        luma: IntArray,
        width: Int,
        height: Int,
        totalQuestions: Int,
        columnMode: QuestionColumnMode = QuestionColumnMode.AUTO,
        optionArrangement: OptionArrangement = OptionArrangement.AUTO
    ): List<String> {
        if (width < 24 || height < 24 || luma.size < width * height || totalQuestions <= 0) {
            return List(totalQuestions.coerceAtLeast(0)) { "NONE" }
        }

        val stride = width + 1
        val integralLuma = LongArray(stride * (height + 1))
        for (y in 0 until height) {
            var rowSum = 0L
            val yOff = y * width
            val curRow = (y + 1) * stride
            val prevRow = y * stride
            for (x in 0 until width) {
                rowSum += luma[yOff + x].coerceIn(0, 255)
                integralLuma[curRow + x + 1] = integralLuma[prevRow + x + 1] + rowSum
            }
        }

        // 1. Adaptive local thresholding to neutralize uneven classroom lighting & shadows
        val localR = (min(width, height) / 28).coerceIn(10, 32)
        val inkMask = IntArray(width * height)
        val darkness = IntArray(width * height)
        for (y in 0 until height) {
            val y0 = (y - localR).coerceAtLeast(0)
            val y1 = (y + localR).coerceAtMost(height - 1)
            val yOff = y * width
            val r0 = y0 * stride
            val r1 = (y1 + 1) * stride
            for (x in 0 until width) {
                val x0 = (x - localR).coerceAtLeast(0)
                val x1 = (x + localR).coerceAtMost(width - 1)
                val area = (x1 - x0 + 1) * (y1 - y0 + 1)
                val sum = integralLuma[r1 + x1 + 1] - integralLuma[r0 + x1 + 1] -
                    integralLuma[r1 + x0] + integralLuma[r0 + x0]
                val mean = (sum / area).toInt()
                val px = luma[yOff + x].coerceIn(0, 255)
                val threshold = (mean * 0.80f).toInt()
                if (px < threshold && px < 190) {
                    inkMask[yOff + x] = 1
                    darkness[yOff + x] = (mean - px).coerceAtLeast(1)
                }
            }
        }

        // 2. Prefix sum of inkMask to suppress thin printed typography & empty bubble rings
        val integralInk = IntArray(stride * (height + 1))
        for (y in 0 until height) {
            var rowSum = 0
            val yOff = y * width
            val curRow = (y + 1) * stride
            val prevRow = y * stride
            for (x in 0 until width) {
                rowSum += inkMask[yOff + x]
                integralInk[curRow + x + 1] = integralInk[prevRow + x + 1] + rowSum
            }
        }

        val coreR = (min(width, height) / 210).coerceIn(2, 4)
        val solidMask = ByteArray(width * height)
        for (y in 0 until height) {
            val y0 = (y - coreR).coerceAtLeast(0)
            val y1 = (y + coreR).coerceAtMost(height - 1)
            val yOff = y * width
            val r0 = y0 * stride
            val r1 = (y1 + 1) * stride
            for (x in 0 until width) {
                val idx = yOff + x
                if (inkMask[idx] == 0) continue
                val x0 = (x - coreR).coerceAtLeast(0)
                val x1 = (x + coreR).coerceAtMost(width - 1)
                val patchArea = (x1 - x0 + 1) * (y1 - y0 + 1)
                val patchInk = integralInk[r1 + x1 + 1] - integralInk[r0 + x1 + 1] -
                    integralInk[r1 + x0] + integralInk[r0 + x0]
                if (patchInk.toDouble() / patchArea >= 0.60) {
                    solidMask[idx] = 1
                }
            }
        }

        // 3. Connected-component extraction on solid dark blobs
        val blobs = extractSolidBlobs(solidMask, darkness, width, height)

        // 4. Trim vertical active paper ink range (skip empty top/bottom header margins)
        val (topY, bottomY) = detectVerticalContentBounds(integralInk, stride, width, height)

        // 5. Determine column count (1 vs 2 columns)
        val columns = when (columnMode) {
            QuestionColumnMode.ONE_COL -> 1
            QuestionColumnMode.TWO_COL -> 2
            QuestionColumnMode.AUTO -> detectColumnCount(integralInk, stride, width, topY, bottomY, totalQuestions)
        }

        val colWidthFull = width / columns
        val rowsPerCol = (totalQuestions + columns - 1) / columns
        val rowHeight = max(12f, (bottomY - topY).toFloat() / rowsPerCol.toFloat())
        val optionTokens = listOf("A", "B", "C", "D")
        val detected = MutableList(totalQuestions) { "NONE" }

        // Precompute horizontal printed bounds per column so page margins don't skew option bins
        val colBounds = (0 until columns).map { col ->
            val rawX0 = col * colWidthFull
            val rawX1 = if (col == columns - 1) width - 1 else (col + 1) * colWidthFull - 1
            detectColumnHorizontalBounds(integralInk, stride, rawX0, rawX1, topY, bottomY)
        }

        // Assign each solid blob to its closest question band inside its column
        val blobsByQuestion = Array(totalQuestions) { mutableListOf<SolidBlob>() }
        for (blob in blobs) {
            val col = ((blob.centerX / width) * columns).toInt().coerceIn(0, columns - 1)
            val estRow = ((blob.centerY - topY) / rowHeight).toInt().coerceIn(0, rowsPerCol - 1)
            val qIdx = col * rowsPerCol + estRow
            if (qIdx in 0 until totalQuestions) {
                blobsByQuestion[qIdx].add(blob)
            }
        }

        for (qIdx in 0 until totalQuestions) {
            val col = qIdx / rowsPerCol
            val row = qIdx % rowsPerCol
            val bandY0 = (topY + row * rowHeight).roundToInt().coerceIn(0, height - 2)
            val bandY1 = (topY + (row + 1) * rowHeight).roundToInt().coerceIn(bandY0 + 2, height - 1)
            val (colX0, colX1) = colBounds[col]
            val bandBlobs = blobsByQuestion[qIdx]
            if (bandBlobs.isEmpty()) {
                detected[qIdx] = "NONE"
                continue
            }

            val effectiveArrangement = if (optionArrangement == OptionArrangement.AUTO) {
                inferBandArrangement(integralInk, stride, colX0, colX1, bandY0, bandY1)
            } else {
                optionArrangement
            }

            val slotScores = DoubleArray(4)
            val bandH = (bandY1 - bandY0).coerceAtLeast(1)
            val bandW = (colX1 - colX0).coerceAtLeast(1)
            // Options in a question paper sit below a short question stem or span the band
            val optionTopY = when (effectiveArrangement) {
                OptionArrangement.HORIZONTAL -> bandY0
                OptionArrangement.GRID_2X2 -> bandY0 + (bandH * 0.20f).roundToInt()
                OptionArrangement.VERTICAL -> bandY0 + (bandH * 0.16f).roundToInt()
                OptionArrangement.AUTO -> bandY0
            }
            val midY = ((optionTopY + bandY1) / 2f).coerceIn(bandY0 + 1f, bandY1 - 1f)
            val midX = (colX0 + colX1) / 2f

            for (blob in bandBlobs) {
                val slot = when (effectiveArrangement) {
                    OptionArrangement.HORIZONTAL -> {
                        val normX = ((blob.centerX - colX0) / bandW).coerceIn(0f, 0.999f)
                        (normX * 4f).toInt().coerceIn(0, 3)
                    }

                    OptionArrangement.GRID_2X2, OptionArrangement.AUTO -> {
                        val isTop = blob.centerY < midY
                        val isLeft = blob.centerX < midX
                        when {
                            isTop && isLeft -> 0
                            isTop && !isLeft -> 1
                            !isTop && isLeft -> 2
                            else -> 3
                        }
                    }

                    OptionArrangement.VERTICAL -> {
                        val optH = max(1f, (bandY1 - optionTopY).toFloat())
                        val normY = ((blob.centerY - optionTopY) / optH).coerceIn(0f, 0.999f)
                        (normY * 4f).toInt().coerceIn(0, 3)
                    }
                }
                // Keep strongest dark bubble in each option slot
                if (blob.strength > slotScores[slot]) {
                    slotScores[slot] = blob.strength
                }
            }

            val bestIdx = slotScores.indices.maxBy { slotScores[it] }
            // Single-darkest winner rule: always return the single darkest circle, never MULTIPLE
            detected[qIdx] = if (slotScores[bestIdx] > 0.0) optionTokens[bestIdx] else "NONE"
        }

        return detected
    }

    private fun extractSolidBlobs(
        solidMask: ByteArray,
        darkness: IntArray,
        width: Int,
        height: Int
    ): List<SolidBlob> {
        val visited = BooleanArray(width * height)
        val queue = IntArray(width * height)
        val blobs = mutableListOf<SolidBlob>()

        val marginX = (width * 0.025f).toInt()
        val marginY = (height * 0.025f).toInt()
        val minArea = max(6, (width * height) / 45_000)
        val maxW = (width * 0.16f).coerceAtLeast(18f)
        val maxH = (height * 0.08f).coerceAtLeast(18f)

        val dx = intArrayOf(-1, 1, 0, 0)
        val dy = intArrayOf(0, 0, -1, 1)

        for (y in marginY until (height - marginY)) {
            val yOff = y * width
            for (x in marginX until (width - marginX)) {
                val startIdx = yOff + x
                if (solidMask[startIdx].toInt() == 0 || visited[startIdx]) continue

                var head = 0
                var tail = 0
                queue[tail++] = startIdx
                visited[startIdx] = true

                var minX = x
                var maxX = x
                var minY = y
                var maxY = y
                var area = 0
                var darknessSum = 0.0

                while (head < tail) {
                    val cur = queue[head++]
                    val cx = cur % width
                    val cy = cur / width
                    area++
                    darknessSum += darkness[cur]
                    if (cx < minX) minX = cx
                    if (cx > maxX) maxX = cx
                    if (cy < minY) minY = cy
                    if (cy > maxY) maxY = cy

                    for (dir in 0 until 4) {
                        val nx = cx + dx[dir]
                        val ny = cy + dy[dir]
                        if (nx in marginX until (width - marginX) &&
                            ny in marginY until (height - marginY)
                        ) {
                            val nIdx = ny * width + nx
                            if (!visited[nIdx] && solidMask[nIdx].toInt() != 0) {
                                visited[nIdx] = true
                                queue[tail++] = nIdx
                            }
                        }
                    }
                }

                val bw = (maxX - minX + 1).toFloat()
                val bh = (maxY - minY + 1).toFloat()
                val aspect = max(bw, bh) / max(1f, min(bw, bh))
                if (area >= minArea && bw <= maxW && bh <= maxH && aspect <= 3.6f) {
                    blobs.add(
                        SolidBlob(
                            centerX = (minX + maxX) / 2f,
                            centerY = (minY + maxY) / 2f,
                            area = area,
                            strength = darknessSum
                        )
                    )
                }
            }
        }
        return blobs
    }

    private fun detectVerticalContentBounds(
        integralInk: IntArray,
        stride: Int,
        width: Int,
        height: Int
    ): Pair<Int, Int> {
        val x0 = (width * 0.05f).toInt()
        val x1 = (width * 0.95f).toInt().coerceAtLeast(x0 + 1)
        val bandW = x1 - x0 + 1
        var topY = (height * 0.08f).toInt()
        var bottomY = (height * 0.94f).toInt()

        for (y in (height * 0.04f).toInt() until (height * 0.55f).toInt()) {
            val rowInk = rectSum(integralInk, stride, x0, y, x1, y)
            if (rowInk.toFloat() / bandW >= 0.012f) {
                topY = (y - (height * 0.01f).toInt()).coerceAtLeast(0)
                break
            }
        }
        for (y in (height * 0.96f).toInt() downTo (height * 0.45f).toInt()) {
            val rowInk = rectSum(integralInk, stride, x0, y, x1, y)
            if (rowInk.toFloat() / bandW >= 0.012f) {
                bottomY = (y + (height * 0.01f).toInt()).coerceAtMost(height - 1)
                break
            }
        }
        if (bottomY - topY < height * 0.25f) {
            return (height * 0.08f).toInt() to (height * 0.94f).toInt()
        }
        return topY to bottomY
    }

    private fun detectColumnCount(
        integralInk: IntArray,
        stride: Int,
        width: Int,
        topY: Int,
        bottomY: Int,
        totalQuestions: Int
    ): Int {
        if (totalQuestions <= 5) return 1
        val spanH = (bottomY - topY + 1).coerceAtLeast(1)
        val leftX0 = (width * 0.12f).toInt()
        val leftX1 = (width * 0.42f).toInt()
        val midX0 = (width * 0.46f).toInt()
        val midX1 = (width * 0.54f).toInt()
        val rightX0 = (width * 0.58f).toInt()
        val rightX1 = (width * 0.88f).toInt()

        val leftDensity = rectSum(integralInk, stride, leftX0, topY, leftX1, bottomY).toFloat() /
            ((leftX1 - leftX0 + 1) * spanH)
        val midDensity = rectSum(integralInk, stride, midX0, topY, midX1, bottomY).toFloat() /
            ((midX1 - midX0 + 1) * spanH)
        val rightDensity = rectSum(integralInk, stride, rightX0, topY, rightX1, bottomY).toFloat() /
            ((rightX1 - rightX0 + 1) * spanH)

        return if (leftDensity > midDensity * 2.1f && rightDensity > midDensity * 2.1f && rightDensity > 0.008f) {
            2
        } else if (totalQuestions > 30 && rightDensity > 0.01f) {
            2
        } else {
            1
        }
    }

    private fun detectColumnHorizontalBounds(
        integralInk: IntArray,
        stride: Int,
        rawX0: Int,
        rawX1: Int,
        topY: Int,
        bottomY: Int
    ): Pair<Int, Int> {
        val spanH = (bottomY - topY + 1).coerceAtLeast(1)
        val pad = ((rawX1 - rawX0) * 0.04f).toInt()
        var left = (rawX0 + pad).coerceIn(rawX0, rawX1)
        var right = (rawX1 - pad).coerceIn(left + 1, rawX1)

        for (x in rawX0 until rawX1) {
            val colInk = rectSum(integralInk, stride, x, topY, x, bottomY)
            if (colInk.toFloat() / spanH >= 0.012f) {
                left = x
                break
            }
        }
        for (x in rawX1 downTo rawX0) {
            val colInk = rectSum(integralInk, stride, x, topY, x, bottomY)
            if (colInk.toFloat() / spanH >= 0.012f) {
                right = x
                break
            }
        }
        return if (right - left >= (rawX1 - rawX0) * 0.35f) {
            left to right
        } else {
            rawX0 + pad to rawX1 - pad
        }
    }

    private fun inferBandArrangement(
        integralInk: IntArray,
        stride: Int,
        colX0: Int,
        colX1: Int,
        bandY0: Int,
        bandY1: Int
    ): OptionArrangement {
        val bandH = (bandY1 - bandY0 + 1).coerceAtLeast(4)
        val bandW = (colX1 - colX0 + 1).coerceAtLeast(4)
        val qH = max(1, bandH / 4)
        val midX = (colX0 + colX1) / 2

        val quarterDensities = FloatArray(4) { q ->
            val yA = (bandY0 + q * qH).coerceIn(bandY0, bandY1)
            val yB = if (q == 3) bandY1 else (bandY0 + (q + 1) * qH - 1).coerceIn(yA, bandY1)
            rectSum(integralInk, stride, colX0, yA, colX1, yB).toFloat() / (bandW * (yB - yA + 1))
        }
        val rightHalfDensity = rectSum(integralInk, stride, midX, bandY0, colX1, bandY1).toFloat() /
            ((colX1 - midX + 1) * bandH)
        val leftHalfDensity = rectSum(integralInk, stride, colX0, bandY0, midX, bandY1).toFloat() /
            ((midX - colX0 + 1) * bandH)

        val upperInk = quarterDensities[0] + quarterDensities[1]
        val lowerInk = quarterDensities[2] + quarterDensities[3]

        return when {
            lowerInk < upperInk * 0.25f && rightHalfDensity > leftHalfDensity * 0.45f -> OptionArrangement.HORIZONTAL
            rightHalfDensity < leftHalfDensity * 0.22f && quarterDensities.count { it > 0.012f } >= 3 -> OptionArrangement.VERTICAL
            else -> OptionArrangement.GRID_2X2
        }
    }

    private fun rectSum(
        integral: IntArray,
        stride: Int,
        x0: Int,
        y0: Int,
        x1: Int,
        y1: Int
    ): Int {
        val sx0 = x0.coerceAtLeast(0)
        val sy0 = y0.coerceAtLeast(0)
        val sx1 = x1.coerceAtLeast(sx0)
        val sy1 = y1.coerceAtLeast(sy0)
        val r0 = sy0 * stride
        val r1 = (sy1 + 1) * stride
        return integral[r1 + sx1 + 1] - integral[r0 + sx1 + 1] - integral[r1 + sx0] + integral[r0 + sx0]
    }
}
