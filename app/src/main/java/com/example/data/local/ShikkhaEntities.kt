package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.TypeConverter
import com.example.model.Exam
import com.example.model.Submission
import org.json.JSONArray

@Entity(tableName = "exams")
data class ExamEntity(
    @PrimaryKey val id: String,
    val examCode: String,
    val teacherId: String,
    val teacherName: String,
    val title: String,
    val subject: String,
    val totalQuestions: Long,
    val optionStyle: String,
    val marksPerCorrect: Double,
    val negativeMark: Double,
    val answerKey: List<String>,
    val status: String,
    val createdAtMillis: Long,
    val updatedAtMillis: Long
)

@Entity(tableName = "submissions")
data class SubmissionEntity(
    @PrimaryKey val id: String,
    val examId: String,
    val examCode: String,
    val examTitle: String,
    val subject: String,
    val teacherId: String,
    val submittedByUid: String,
    val scannedByRole: String,
    val studentName: String,
    val studentRoll: String,
    val optionStyle: String,
    val detectedAnswers: List<String>,
    val correctAnswers: List<String>,
    val correctCount: Long,
    val wrongCount: Long,
    val unansweredCount: Long,
    val score: Double,
    val maxScore: Double,
    val percentage: Double,
    val aiConfidence: String,
    val aiNotes: String?,
    val submittedAtMillis: Long
)

class RoomConverters {
    @TypeConverter
    fun fromStringList(list: List<String>): String = JSONArray(list).toString()

    @TypeConverter
    fun toStringList(json: String): List<String> {
        if (json.isBlank()) return emptyList()
        val arr = JSONArray(json)
        return (0 until arr.length()).map { arr.optString(it, "") }
    }
}

fun ExamEntity.toDomain(): Exam = Exam(
    id = id,
    examCode = examCode,
    teacherId = teacherId,
    teacherName = teacherName,
    title = title,
    subject = subject,
    totalQuestions = totalQuestions,
    optionStyle = optionStyle,
    marksPerCorrect = marksPerCorrect,
    negativeMark = negativeMark,
    answerKey = answerKey,
    status = status,
    createdAtMillis = createdAtMillis,
    updatedAtMillis = updatedAtMillis
)

fun Exam.toEntity(): ExamEntity = ExamEntity(
    id = id,
    examCode = examCode,
    teacherId = teacherId,
    teacherName = teacherName,
    title = title,
    subject = subject,
    totalQuestions = totalQuestions,
    optionStyle = optionStyle,
    marksPerCorrect = marksPerCorrect,
    negativeMark = negativeMark,
    answerKey = answerKey,
    status = status,
    createdAtMillis = createdAtMillis,
    updatedAtMillis = updatedAtMillis
)

fun SubmissionEntity.toDomain(): Submission = Submission(
    id = id,
    examId = examId,
    examCode = examCode,
    examTitle = examTitle,
    subject = subject,
    teacherId = teacherId,
    submittedByUid = submittedByUid,
    scannedByRole = scannedByRole,
    studentName = studentName,
    studentRoll = studentRoll,
    optionStyle = optionStyle,
    detectedAnswers = detectedAnswers,
    correctAnswers = correctAnswers,
    correctCount = correctCount,
    wrongCount = wrongCount,
    unansweredCount = unansweredCount,
    score = score,
    maxScore = maxScore,
    percentage = percentage,
    aiConfidence = aiConfidence,
    aiNotes = aiNotes,
    submittedAtMillis = submittedAtMillis
)

fun Submission.toEntity(): SubmissionEntity = SubmissionEntity(
    id = id,
    examId = examId,
    examCode = examCode,
    examTitle = examTitle,
    subject = subject,
    teacherId = teacherId,
    submittedByUid = submittedByUid,
    scannedByRole = scannedByRole,
    studentName = studentName,
    studentRoll = studentRoll,
    optionStyle = optionStyle,
    detectedAnswers = detectedAnswers,
    correctAnswers = correctAnswers,
    correctCount = correctCount,
    wrongCount = wrongCount,
    unansweredCount = unansweredCount,
    score = score,
    maxScore = maxScore,
    percentage = percentage,
    aiConfidence = aiConfidence,
    aiNotes = aiNotes,
    submittedAtMillis = submittedAtMillis
)
