package com.example.data

import com.example.model.AiConfidence
import com.example.model.Exam
import com.example.model.ExamStatus
import com.example.model.OptionStyle
import com.example.model.ScannedByRole
import com.example.model.Submission
import kotlinx.coroutines.flow.Flow

interface ExamDataSource {
    fun observeTeacherExams(teacherId: String): Flow<List<Exam>>
    fun observeMySubmissions(userId: String): Flow<List<Submission>>
    fun observeExamSubmissions(teacherId: String, examId: String): Flow<List<Submission>>
    suspend fun lookupExamByCode(rawCode: String): Result<Exam>
    suspend fun createExam(
        teacherName: String,
        title: String,
        subject: String,
        totalQuestions: Int,
        optionStyle: OptionStyle,
        marksPerCorrect: Double,
        negativeMark: Double,
        answerKey: List<String>
    ): Result<Exam>

    suspend fun updateExam(
        examId: String,
        examCode: String,
        title: String,
        subject: String,
        totalQuestions: Int,
        optionStyle: OptionStyle,
        marksPerCorrect: Double,
        negativeMark: Double,
        answerKey: List<String>
    ): Result<Unit>

    suspend fun updateExamStatus(
        examId: String,
        examCode: String,
        newStatus: ExamStatus
    ): Result<Unit>

    suspend fun deleteExam(examId: String, examCode: String): Result<Unit>

    suspend fun saveSubmission(
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
    ): Result<Submission>

    suspend fun deleteSubmission(submissionId: String): Result<Unit>
}
