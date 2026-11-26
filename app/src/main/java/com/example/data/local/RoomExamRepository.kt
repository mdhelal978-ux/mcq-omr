package com.example.data.local

import android.content.Context
import com.example.data.ExamDataSource
import com.example.model.AiConfidence
import com.example.model.Exam
import com.example.model.ExamStatus
import com.example.model.OptionStyle
import com.example.model.ScannedByRole
import com.example.model.Submission
import com.example.model.normalizeOptionToken
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID
import kotlin.random.Random

class RoomExamRepository(
    context: Context,
    private val dao: ExamDao = ShikkhaDatabase.getInstance(context).examDao()
) : ExamDataSource {

    private val appContext = context.applicationContext
    private val migrationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    init {
        migrationScope.launch {
            migrateLegacyJsonIfNeeded()
        }
    }

    override fun observeTeacherExams(teacherId: String): Flow<List<Exam>> =
        dao.observeAllExams().map { list -> list.map { it.toDomain() } }

    override fun observeMySubmissions(userId: String): Flow<List<Submission>> =
        dao.observeAllSubmissions().map { list -> list.map { it.toDomain() } }

    override fun observeExamSubmissions(teacherId: String, examId: String): Flow<List<Submission>> =
        dao.observeSubmissionsForExam(examId).map { list -> list.map { it.toDomain() } }

    override suspend fun lookupExamByCode(rawCode: String): Result<Exam> {
        val code = rawCode.trim().uppercase().replace(Regex("[^A-Z0-9\\-]"), "")
        if (code.length < 4) {
            return Result.failure(IllegalArgumentException("Please enter a valid Exam Code."))
        }
        val match = dao.findExamByCode(code)?.toDomain()
        return if (match != null) {
            Result.success(match)
        } else {
            Result.failure(NoSuchElementException("No exam found locally for code $code."))
        }
    }

    override suspend fun createExam(
        teacherName: String,
        title: String,
        subject: String,
        totalQuestions: Int,
        optionStyle: OptionStyle,
        marksPerCorrect: Double,
        negativeMark: Double,
        answerKey: List<String>
    ): Result<Exam> = runCatching {
        val cleanTitle = title.trim().take(120)
        val cleanSubject = subject.trim().take(80)
        require(cleanTitle.length >= 2 && cleanSubject.isNotEmpty()) {
            "Title and subject are required."
        }
        val count = totalQuestions.coerceIn(5, 100)
        val normalizedKey = (0 until count).map { idx ->
            val token = answerKey.getOrNull(idx)?.let(::normalizeOptionToken) ?: "A"
            if (token in listOf("A", "B", "C", "D")) token else "A"
        }
        val existingCodes = dao.getAllExamCodes().toSet()
        val now = System.currentTimeMillis()
        val exam = Exam(
            id = "exam_${UUID.randomUUID().toString().take(12)}",
            examCode = generateExamCode(cleanSubject, existingCodes),
            teacherId = LOCAL_USER_ID,
            teacherName = teacherName.ifBlank { "Teacher" },
            title = cleanTitle,
            subject = cleanSubject,
            totalQuestions = count.toLong(),
            optionStyle = optionStyle.rawValue,
            marksPerCorrect = marksPerCorrect.coerceIn(0.25, 10.0),
            negativeMark = negativeMark.coerceIn(0.0, 5.0),
            answerKey = normalizedKey,
            status = ExamStatus.ACTIVE.rawValue,
            createdAtMillis = now,
            updatedAtMillis = now
        )
        dao.upsertExam(exam.toEntity())
        exam
    }

    override suspend fun updateExam(
        examId: String,
        examCode: String,
        title: String,
        subject: String,
        totalQuestions: Int,
        optionStyle: OptionStyle,
        marksPerCorrect: Double,
        negativeMark: Double,
        answerKey: List<String>
    ): Result<Unit> = runCatching {
        val cleanTitle = title.trim().take(120)
        val cleanSubject = subject.trim().take(80)
        require(cleanTitle.length >= 2 && cleanSubject.isNotEmpty()) {
            "Title and subject are required."
        }
        val count = totalQuestions.coerceIn(5, 100)
        val normalizedKey = (0 until count).map { idx ->
            val token = answerKey.getOrNull(idx)?.let(::normalizeOptionToken) ?: "A"
            if (token in listOf("A", "B", "C", "D")) token else "A"
        }
        dao.updateExamDetails(
            examId = examId,
            title = cleanTitle,
            subject = cleanSubject,
            totalQuestions = count.toLong(),
            optionStyle = optionStyle.rawValue,
            marksPerCorrect = marksPerCorrect.coerceIn(0.25, 10.0),
            negativeMark = negativeMark.coerceIn(0.0, 5.0),
            answerKey = normalizedKey,
            updatedAtMillis = System.currentTimeMillis()
        )
    }

    override suspend fun updateExamStatus(
        examId: String,
        examCode: String,
        newStatus: ExamStatus
    ): Result<Unit> = runCatching {
        dao.updateExamStatus(examId, newStatus.rawValue, System.currentTimeMillis())
    }

    override suspend fun deleteExam(examId: String, examCode: String): Result<Unit> = runCatching {
        dao.deleteExamById(examId)
    }

    override suspend fun saveSubmission(
        exam: Exam,
        scannedByRole: ScannedByRole,
        studentName: String,
        studentRoll: String,
        detectedAnswers: List<String>,
        correctCount: Int,
        wrongCount: Int,
        unansweredCount: Int,
        score: Double,
        maxScore: Double,
        percentage: Double,
        aiConfidence: AiConfidence,
        aiNotes: String?
    ): Result<Submission> = runCatching {
        val cleanName = studentName.trim().take(80)
        val cleanRoll = studentRoll.trim().take(40)
        require(cleanName.isNotEmpty() && cleanRoll.isNotEmpty()) {
            "Student name and roll number are required."
        }
        val safeDetected = (0 until exam.answerKey.size).map { idx ->
            detectedAnswers.getOrNull(idx)?.let(::normalizeOptionToken) ?: "NONE"
        }
        val submission = Submission(
            id = "sub_${UUID.randomUUID().toString().take(12)}",
            examId = exam.id,
            examCode = exam.examCode,
            examTitle = exam.title,
            subject = exam.subject,
            teacherId = exam.teacherId,
            submittedByUid = LOCAL_USER_ID,
            scannedByRole = scannedByRole.rawValue,
            studentName = cleanName,
            studentRoll = cleanRoll,
            optionStyle = exam.optionStyle,
            detectedAnswers = safeDetected,
            correctAnswers = exam.answerKey,
            correctCount = correctCount.toLong(),
            wrongCount = wrongCount.toLong(),
            unansweredCount = unansweredCount.toLong(),
            score = score,
            maxScore = maxScore,
            percentage = percentage,
            aiConfidence = aiConfidence.rawValue,
            aiNotes = aiNotes?.trim()?.take(300)?.ifBlank { null },
            submittedAtMillis = System.currentTimeMillis()
        )
        dao.upsertSubmission(submission.toEntity())
        submission
    }

    override suspend fun deleteSubmission(submissionId: String): Result<Unit> = runCatching {
        dao.deleteSubmissionById(submissionId)
    }

    private fun generateExamCode(subject: String, existingCodes: Set<String>): String {
        val prefix = subject.uppercase().replace(Regex("[^A-Z]"), "").take(3).ifBlank { "EXM" }
        repeat(5) {
            val candidate = "$prefix${Random.nextInt(100, 999)}"
            if (candidate !in existingCodes) return candidate
        }
        return "$prefix${Random.nextInt(1000, 9999)}"
    }

    private suspend fun migrateLegacyJsonIfNeeded() {
        val legacyFile = File(appContext.filesDir, "shikkhascan_local_db.json")
        if (!legacyFile.exists()) return
        try {
            val root = JSONObject(legacyFile.readText())
            val examsArr = root.optJSONArray("exams") ?: JSONArray()
            for (i in 0 until examsArr.length()) {
                val obj = examsArr.optJSONObject(i) ?: continue
                val keyArr = obj.optJSONArray("answerKey") ?: JSONArray()
                val keys = (0 until keyArr.length()).map { keyArr.getString(it) }
                val createdMs = obj.optLong("createdAtMs", System.currentTimeMillis())
                dao.upsertExam(
                    ExamEntity(
                        id = obj.getString("id"),
                        examCode = obj.getString("examCode"),
                        teacherId = LOCAL_USER_ID,
                        teacherName = obj.optString("teacherName", "Teacher"),
                        title = obj.getString("title"),
                        subject = obj.getString("subject"),
                        totalQuestions = obj.getLong("totalQuestions"),
                        optionStyle = obj.optString("optionStyle", OptionStyle.ABCD.rawValue),
                        marksPerCorrect = obj.optDouble("marksPerCorrect", 1.0),
                        negativeMark = obj.optDouble("negativeMark", 0.0),
                        answerKey = keys,
                        status = obj.optString("status", ExamStatus.ACTIVE.rawValue),
                        createdAtMillis = createdMs,
                        updatedAtMillis = createdMs
                    )
                )
            }
            val subsArr = root.optJSONArray("submissions") ?: JSONArray()
            for (i in 0 until subsArr.length()) {
                val obj = subsArr.optJSONObject(i) ?: continue
                val detArr = obj.optJSONArray("detectedAnswers") ?: JSONArray()
                val corArr = obj.optJSONArray("correctAnswers") ?: JSONArray()
                val subMs = obj.optLong("submittedAtMs", System.currentTimeMillis())
                dao.upsertSubmission(
                    SubmissionEntity(
                        id = obj.getString("id"),
                        examId = obj.getString("examId"),
                        examCode = obj.getString("examCode"),
                        examTitle = obj.getString("examTitle"),
                        subject = obj.getString("subject"),
                        teacherId = LOCAL_USER_ID,
                        submittedByUid = LOCAL_USER_ID,
                        scannedByRole = obj.optString("scannedByRole", ScannedByRole.STUDENT.rawValue),
                        studentName = obj.getString("studentName"),
                        studentRoll = obj.getString("studentRoll"),
                        optionStyle = obj.optString("optionStyle", OptionStyle.ABCD.rawValue),
                        detectedAnswers = (0 until detArr.length()).map { detArr.getString(it) },
                        correctAnswers = (0 until corArr.length()).map { corArr.getString(it) },
                        correctCount = obj.getLong("correctCount"),
                        wrongCount = obj.getLong("wrongCount"),
                        unansweredCount = obj.getLong("unansweredCount"),
                        score = obj.getDouble("score"),
                        maxScore = obj.getDouble("maxScore"),
                        percentage = obj.getDouble("percentage"),
                        aiConfidence = obj.optString("aiConfidence", AiConfidence.MEDIUM.rawValue),
                        aiNotes = obj.optString("aiNotes").ifBlank { null },
                        submittedAtMillis = subMs
                    )
                )
            }
            legacyFile.delete()
        } catch (_: Exception) {
        }
    }

    companion object {
        const val LOCAL_USER_ID = "local_device"
    }
}
