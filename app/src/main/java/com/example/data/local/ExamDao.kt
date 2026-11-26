package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ExamDao {

    @Query("SELECT * FROM exams ORDER BY createdAtMillis DESC")
    fun observeAllExams(): Flow<List<ExamEntity>>

    @Query("SELECT * FROM exams WHERE examCode = :code LIMIT 1")
    suspend fun findExamByCode(code: String): ExamEntity?

    @Query("SELECT examCode FROM exams")
    suspend fun getAllExamCodes(): List<String>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertExam(exam: ExamEntity)

    @Query(
        "UPDATE exams SET title = :title, subject = :subject, totalQuestions = :totalQuestions, " +
            "optionStyle = :optionStyle, marksPerCorrect = :marksPerCorrect, negativeMark = :negativeMark, " +
            "answerKey = :answerKey, updatedAtMillis = :updatedAtMillis WHERE id = :examId"
    )
    suspend fun updateExamDetails(
        examId: String,
        title: String,
        subject: String,
        totalQuestions: Long,
        optionStyle: String,
        marksPerCorrect: Double,
        negativeMark: Double,
        answerKey: List<String>,
        updatedAtMillis: Long
    )

    @Query("UPDATE exams SET status = :status, updatedAtMillis = :updatedAtMillis WHERE id = :examId")
    suspend fun updateExamStatus(examId: String, status: String, updatedAtMillis: Long)

    @Query("DELETE FROM exams WHERE id = :examId")
    suspend fun deleteExamById(examId: String)

    @Query("SELECT * FROM submissions ORDER BY submittedAtMillis DESC")
    fun observeAllSubmissions(): Flow<List<SubmissionEntity>>

    @Query("SELECT * FROM submissions WHERE examId = :examId ORDER BY submittedAtMillis DESC")
    fun observeSubmissionsForExam(examId: String): Flow<List<SubmissionEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSubmission(submission: SubmissionEntity)

    @Query("DELETE FROM submissions WHERE id = :submissionId")
    suspend fun deleteSubmissionById(submissionId: String)
}
