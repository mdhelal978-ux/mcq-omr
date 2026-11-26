package com.example.data

import android.content.Context
import com.example.model.AiConfidence
import com.example.model.Exam
import com.example.model.ExamStatus
import com.example.model.OptionStyle
import com.example.model.ScannedByRole
import com.example.model.Submission
import com.example.model.normalizeOptionToken
import com.google.firebase.Timestamp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.Date
import java.util.UUID
import kotlin.random.Random

class LocalExamRepository(context: Context) : ExamDataSource {

    private val storageFile = File(context.applicationContext.filesDir, "shikkhascan_local_db.json")
    private val mutex = Mutex()

    private val examsFlow = MutableStateFlow<List<Exam>>(emptyList())
    private val submissionsFlow = MutableStateFlow<List<Submission>>(emptyList())

    init {
        loadFromDisk()
    }

    override fun observeTeacherExams(teacherId: String): Flow<List<Exam>> = examsFlow

    override fun observeMySubmissions(userId: String): Flow<List<Submission>> = submissionsFlow

    override fun observeExamSubmissions(teacherId: String, examId: String): Flow<List<Submission>> =
        submissionsFlow.map { list -> list.filter { it.examId == examId } }

    override suspend fun lookupExamByCode(rawCode: String): Result<Exam> {
        val code = rawCode.trim().uppercase().replace(Regex("[^A-Z0-9\\-]"), "")
        val match = examsFlow.value.firstOrNull { it.examCode.equals(code, ignoreCase = true) }
        return if (match != null) {
            Result.success(match)
        } else {
            Result.failure(NoSuchElementException("Local exam $code not found. Sign in with Google to search cloud exams."))
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
    ): Result<Exam> = mutex.withLock {
        val cleanTitle = title.trim().take(120)
        val cleanSubject = subject.trim().take(80)
        if (cleanTitle.length < 2 || cleanSubject.isEmpty()) {
            return Result.failure(IllegalArgumentException("Title and subject are required."))
        }
        val count = totalQuestions.coerceIn(5, 100)
        val normalizedKey = (0 until count).map { idx ->
            val token = answerKey.getOrNull(idx)?.let(::normalizeOptionToken) ?: "A"
            if (token in listOf("A", "B", "C", "D")) token else "A"
        }
        val now = Timestamp.now()
        val exam = Exam(
            id = "local_${UUID.randomUUID().toString().take(12)}",
            examCode = generateLocalExamCode(cleanSubject),
            teacherId = GUEST_UID,
            teacherName = teacherName.ifBlank { "Local User" },
            title = cleanTitle,
            subject = cleanSubject,
            totalQuestions = count.toLong(),
            optionStyle = optionStyle.rawValue,
            marksPerCorrect = marksPerCorrect.coerceIn(0.25, 10.0),
            negativeMark = negativeMark.coerceIn(0.0, 5.0),
            answerKey = normalizedKey,
            status = ExamStatus.ACTIVE.rawValue,
            createdAt = now,
            updatedAt = now
        )
        examsFlow.value = listOf(exam) + examsFlow.value
        saveToDisk()
        Result.success(exam)
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
    ): Result<Unit> = mutex.withLock {
        val cleanTitle = title.trim().take(120)
        val cleanSubject = subject.trim().take(80)
        if (cleanTitle.length < 2 || cleanSubject.isEmpty()) {
            return Result.failure(IllegalArgumentException("Title and subject are required."))
        }
        val count = totalQuestions.coerceIn(5, 100)
        val normalizedKey = (0 until count).map { idx ->
            val token = answerKey.getOrNull(idx)?.let(::normalizeOptionToken) ?: "A"
            if (token in listOf("A", "B", "C", "D")) token else "A"
        }
        examsFlow.value = examsFlow.value.map { existing ->
            if (existing.id == examId) {
                existing.copy(
                    title = cleanTitle,
                    subject = cleanSubject,
                    totalQuestions = count.toLong(),
                    optionStyle = optionStyle.rawValue,
                    marksPerCorrect = marksPerCorrect.coerceIn(0.25, 10.0),
                    negativeMark = negativeMark.coerceIn(0.0, 5.0),
                    answerKey = normalizedKey,
                    updatedAt = Timestamp.now()
                )
            } else existing
        }
        saveToDisk()
        Result.success(Unit)
    }

    override suspend fun updateExamStatus(
        examId: String,
        examCode: String,
        newStatus: ExamStatus
    ): Result<Unit> = mutex.withLock {
        examsFlow.value = examsFlow.value.map { existing ->
            if (existing.id == examId) {
                existing.copy(status = newStatus.rawValue, updatedAt = Timestamp.now())
            } else existing
        }
        saveToDisk()
        Result.success(Unit)
    }

    override suspend fun deleteExam(examId: String, examCode: String): Result<Unit> = mutex.withLock {
        examsFlow.value = examsFlow.value.filterNot { it.id == examId }
        saveToDisk()
        Result.success(Unit)
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
    ): Result<Submission> = mutex.withLock {
        val cleanName = studentName.trim().take(80)
        val cleanRoll = studentRoll.trim().take(40)
        if (cleanName.isEmpty() || cleanRoll.isEmpty()) {
            return Result.failure(IllegalArgumentException("Student name and roll number are required."))
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
            submittedByUid = GUEST_UID,
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
            submittedAt = Timestamp.now()
        )
        submissionsFlow.value = listOf(submission) + submissionsFlow.value
        saveToDisk()
        Result.success(submission)
    }

    override suspend fun deleteSubmission(submissionId: String): Result<Unit> = mutex.withLock {
        submissionsFlow.value = submissionsFlow.value.filterNot { it.id == submissionId }
        saveToDisk()
        Result.success(Unit)
    }

    private fun generateLocalExamCode(subject: String): String {
        val prefix = subject.uppercase().replace(Regex("[^A-Z]"), "").take(3).ifBlank { "LOC" }
        val existingCodes = examsFlow.value.map { it.examCode }.toSet()
        repeat(5) {
            val candidate = "$prefix${Random.nextInt(100, 999)}"
            if (candidate !in existingCodes) return candidate
        }
        return "$prefix${Random.nextInt(1000, 9999)}"
    }

    private fun loadFromDisk() {
        if (!storageFile.exists()) return
        try {
            val root = JSONObject(storageFile.readText())
            val examsArr = root.optJSONArray("exams") ?: JSONArray()
            val loadedExams = (0 until examsArr.length()).mapNotNull { i ->
                val obj = examsArr.optJSONObject(i) ?: return@mapNotNull null
                val keyArr = obj.optJSONArray("answerKey") ?: JSONArray()
                val keys = (0 until keyArr.length()).map { keyArr.getString(it) }
                val createdMs = obj.optLong("createdAtMs", System.currentTimeMillis())
                Exam(
                    id = obj.getString("id"),
                    examCode = obj.getString("examCode"),
                    teacherId = obj.optString("teacherId", GUEST_UID),
                    teacherName = obj.optString("teacherName", "Local User"),
                    title = obj.getString("title"),
                    subject = obj.getString("subject"),
                    totalQuestions = obj.getLong("totalQuestions"),
                    optionStyle = obj.optString("optionStyle", OptionStyle.ABCD.rawValue),
                    marksPerCorrect = obj.optDouble("marksPerCorrect", 1.0),
                    negativeMark = obj.optDouble("negativeMark", 0.0),
                    answerKey = keys,
                    status = obj.optString("status", ExamStatus.ACTIVE.rawValue),
                    createdAt = Timestamp(Date(createdMs)),
                    updatedAt = Timestamp(Date(createdMs))
                )
            }

            val subsArr = root.optJSONArray("submissions") ?: JSONArray()
            val loadedSubs = (0 until subsArr.length()).mapNotNull { i ->
                val obj = subsArr.optJSONObject(i) ?: return@mapNotNull null
                val detArr = obj.optJSONArray("detectedAnswers") ?: JSONArray()
                val corArr = obj.optJSONArray("correctAnswers") ?: JSONArray()
                val subMs = obj.optLong("submittedAtMs", System.currentTimeMillis())
                Submission(
                    id = obj.getString("id"),
                    examId = obj.getString("examId"),
                    examCode = obj.getString("examCode"),
                    examTitle = obj.getString("examTitle"),
                    subject = obj.getString("subject"),
                    teacherId = obj.optString("teacherId", GUEST_UID),
                    submittedByUid = obj.optString("submittedByUid", GUEST_UID),
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
                    submittedAt = Timestamp(Date(subMs))
                )
            }

            examsFlow.value = loadedExams
            submissionsFlow.value = loadedSubs
        } catch (_: Exception) {
        }
    }

    private suspend fun saveToDisk() = withContext(Dispatchers.IO) {
        val examsArr = JSONArray().apply {
            examsFlow.value.forEach { exam ->
                put(JSONObject().apply {
                    put("id", exam.id)
                    put("examCode", exam.examCode)
                    put("teacherId", exam.teacherId)
                    put("teacherName", exam.teacherName)
                    put("title", exam.title)
                    put("subject", exam.subject)
                    put("totalQuestions", exam.totalQuestions)
                    put("optionStyle", exam.optionStyle)
                    put("marksPerCorrect", exam.marksPerCorrect)
                    put("negativeMark", exam.negativeMark)
                    put("answerKey", JSONArray(exam.answerKey))
                    put("status", exam.status)
                    put("createdAtMs", exam.createdAt?.toDate()?.time ?: System.currentTimeMillis())
                })
            }
        }

        val subsArr = JSONArray().apply {
            submissionsFlow.value.forEach { sub ->
                put(JSONObject().apply {
                    put("id", sub.id)
                    put("examId", sub.examId)
                    put("examCode", sub.examCode)
                    put("examTitle", sub.examTitle)
                    put("subject", sub.subject)
                    put("teacherId", sub.teacherId)
                    put("submittedByUid", sub.submittedByUid)
                    put("scannedByRole", sub.scannedByRole)
                    put("studentName", sub.studentName)
                    put("studentRoll", sub.studentRoll)
                    put("optionStyle", sub.optionStyle)
                    put("detectedAnswers", JSONArray(sub.detectedAnswers))
                    put("correctAnswers", JSONArray(sub.correctAnswers))
                    put("correctCount", sub.correctCount)
                    put("wrongCount", sub.wrongCount)
                    put("unansweredCount", sub.unansweredCount)
                    put("score", sub.score)
                    put("maxScore", sub.maxScore)
                    put("percentage", sub.percentage)
                    put("aiConfidence", sub.aiConfidence)
                    put("aiNotes", sub.aiNotes ?: "")
                    put("submittedAtMs", sub.submittedAt?.toDate()?.time ?: System.currentTimeMillis())
                })
            }
        }

        val root = JSONObject().apply {
            put("exams", examsArr)
            put("submissions", subsArr)
        }
        storageFile.writeText(root.toString())
    }

    companion object {
        const val GUEST_UID = "local_guest"
    }
}
