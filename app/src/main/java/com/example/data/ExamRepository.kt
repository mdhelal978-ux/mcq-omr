package com.example.data

import android.content.Context
import com.example.R
import com.example.firebase.OperationType
import com.example.firebase.handleFirestoreError
import com.example.model.AiConfidence
import com.example.model.Exam
import com.example.model.ExamCodeLookup
import com.example.model.ExamStatus
import com.example.model.OptionStyle
import com.example.model.ScannedByRole
import com.example.model.Submission
import com.example.model.normalizeOptionToken
import com.google.firebase.Firebase
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import kotlin.random.Random

class ExamRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth
) {
    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    private fun requireUserId(): String =
        auth.currentUser?.uid
            ?: throw IllegalStateException("User must be signed in with Google before accessing Firestore.")

    fun observeTeacherExams(teacherId: String): Flow<List<Exam>> = flow {
        require(teacherId.isNotBlank()) { "Teacher ID must not be blank" }
        val path = "exams"
        emitAll(
            db.collection("exams")
                .whereEqualTo("teacherId", teacherId)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        Exam::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ).sortedByDescending { it.createdAt ?: Timestamp(0, 0) }
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && auth.currentUser?.uid == teacherId) {
                        handleFirestoreError(exceptionToLog, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    fun observeMySubmissions(userId: String): Flow<List<Submission>> = flow {
        require(userId.isNotBlank()) { "User ID must not be blank" }
        val path = "submissions"
        emitAll(
            db.collection("submissions")
                .whereEqualTo("submittedByUid", userId)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        Submission::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ).sortedByDescending { it.submittedAt ?: Timestamp(0, 0) }
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && auth.currentUser?.uid == userId) {
                        handleFirestoreError(exceptionToLog, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    fun observeExamSubmissions(teacherId: String, examId: String): Flow<List<Submission>> = flow {
        require(teacherId.isNotBlank() && examId.isNotBlank()) { "Teacher ID and Exam ID required" }
        val path = "submissions"
        emitAll(
            db.collection("submissions")
                .whereEqualTo("teacherId", teacherId)
                .whereEqualTo("examId", examId)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        Submission::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ).sortedByDescending { it.submittedAt ?: Timestamp(0, 0) }
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && auth.currentUser?.uid == teacherId) {
                        handleFirestoreError(exceptionToLog, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    suspend fun getExamById(examId: String): Result<Exam> {
        val path = "exams/$examId"
        return try {
            requireUserId()
            require(examId.isNotBlank()) { "Exam ID must not be blank" }
            val snap = db.collection("exams").document(examId).get().await()
            val exam = snap.toObject(Exam::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                ?: return Result.failure(IllegalStateException("Exam not found."))
            Result.success(exam)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.GET, path)
            Result.failure(e)
        }
    }

    suspend fun lookupExamByCode(rawCode: String): Result<Exam> {
        val code = rawCode.trim().uppercase().replace(Regex("[^A-Z0-9\\-]"), "")
        if (code.length < 4 || code.length > 12) {
            return Result.failure(IllegalArgumentException("Invalid Exam Code format."))
        }
        val codePath = "examCodes/$code"
        return try {
            val uid = requireUserId()
            val lookupSnap = db.collection("examCodes").document(code).get().await()
            val lookup = lookupSnap.toObject(
                ExamCodeLookup::class.java,
                DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
            ) ?: return Result.failure(NoSuchElementException("Exam code $code not found."))

            val examSnap = db.collection("exams").document(lookup.examId).get().await()
            val exam = examSnap.toObject(
                Exam::class.java,
                DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
            ) ?: return Result.failure(NoSuchElementException("Exam no longer exists."))

            if (exam.parsedStatus == ExamStatus.CLOSED && exam.teacherId != uid) {
                return Result.failure(IllegalStateException("This exam is currently closed by the teacher."))
            }
            Result.success(exam)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.GET, codePath)
            Result.failure(e)
        }
    }

    suspend fun createExam(
        teacherName: String,
        title: String,
        subject: String,
        totalQuestions: Int,
        optionStyle: OptionStyle,
        marksPerCorrect: Double,
        negativeMark: Double,
        answerKey: List<String>
    ): Result<Exam> {
        var path = "exams"
        return try {
            val uid = requireUserId()
            val cleanTitle = title.trim().take(120)
            val cleanSubject = subject.trim().take(80)
            val cleanTeacher = teacherName.trim().ifBlank { "Teacher" }.take(80)
            val count = totalQuestions.coerceIn(5, 100)
            require(cleanTitle.length >= 2) { "Title must be at least 2 characters." }
            require(cleanSubject.isNotEmpty()) { "Subject is required." }

            val normalizedKey = (0 until count).map { idx ->
                val token = answerKey.getOrNull(idx)?.let(::normalizeOptionToken) ?: "A"
                if (token in listOf("A", "B", "C", "D")) token else "A"
            }

            val safePositive = marksPerCorrect.coerceIn(0.25, 10.0)
            val safeNegative = negativeMark.coerceIn(0.0, 5.0)
            val examCode = generateUniqueExamCode(cleanSubject)

            val examDocRef = db.collection("exams").document()
            val codeDocRef = db.collection("examCodes").document(examCode)
            path = examDocRef.path

            val examPayload = mapOf(
                "examCode" to examCode,
                "teacherId" to uid,
                "teacherName" to cleanTeacher,
                "title" to cleanTitle,
                "subject" to cleanSubject,
                "totalQuestions" to count,
                "optionStyle" to optionStyle.rawValue,
                "marksPerCorrect" to safePositive,
                "negativeMark" to safeNegative,
                "answerKey" to normalizedKey,
                "status" to ExamStatus.ACTIVE.rawValue,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )

            val codePayload = mapOf(
                "examId" to examDocRef.id,
                "teacherId" to uid,
                "title" to cleanTitle,
                "subject" to cleanSubject,
                "status" to ExamStatus.ACTIVE.rawValue,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )

            db.batch().apply {
                set(examDocRef, examPayload)
                set(codeDocRef, codePayload)
            }.commit().await()

            Result.success(
                Exam(
                    id = examDocRef.id,
                    examCode = examCode,
                    teacherId = uid,
                    teacherName = cleanTeacher,
                    title = cleanTitle,
                    subject = cleanSubject,
                    totalQuestions = count.toLong(),
                    optionStyle = optionStyle.rawValue,
                    marksPerCorrect = safePositive,
                    negativeMark = safeNegative,
                    answerKey = normalizedKey,
                    status = ExamStatus.ACTIVE.rawValue
                )
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

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
    ): Result<Unit> {
        val path = "exams/$examId"
        return try {
            requireUserId()
            require(examId.isNotBlank() && examCode.isNotBlank())
            val cleanTitle = title.trim().take(120)
            val cleanSubject = subject.trim().take(80)
            val count = totalQuestions.coerceIn(5, 100)
            require(cleanTitle.length >= 2) { "Title must be at least 2 characters." }
            require(cleanSubject.isNotEmpty()) { "Subject is required." }

            val normalizedKey = (0 until count).map { idx ->
                val token = answerKey.getOrNull(idx)?.let(::normalizeOptionToken) ?: "A"
                if (token in listOf("A", "B", "C", "D")) token else "A"
            }

            val safePositive = marksPerCorrect.coerceIn(0.25, 10.0)
            val safeNegative = negativeMark.coerceIn(0.0, 5.0)

            db.batch().apply {
                update(
                    db.collection("exams").document(examId),
                    mapOf(
                        "title" to cleanTitle,
                        "subject" to cleanSubject,
                        "totalQuestions" to count,
                        "optionStyle" to optionStyle.rawValue,
                        "marksPerCorrect" to safePositive,
                        "negativeMark" to safeNegative,
                        "answerKey" to normalizedKey,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
                update(
                    db.collection("examCodes").document(examCode),
                    mapOf(
                        "title" to cleanTitle,
                        "subject" to cleanSubject,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
            }.commit().await()

            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun updateExamStatus(
        examId: String,
        examCode: String,
        newStatus: ExamStatus
    ): Result<Unit> {
        val path = "exams/$examId"
        return try {
            requireUserId()
            require(examId.isNotBlank() && examCode.isNotBlank())
            db.batch().apply {
                update(
                    db.collection("exams").document(examId),
                    mapOf(
                        "status" to newStatus.rawValue,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
                update(
                    db.collection("examCodes").document(examCode),
                    mapOf(
                        "status" to newStatus.rawValue,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
            }.commit().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun deleteExam(examId: String, examCode: String): Result<Unit> {
        val path = "exams/$examId"
        return try {
            requireUserId()
            require(examId.isNotBlank())
            db.batch().apply {
                delete(db.collection("exams").document(examId))
                if (examCode.isNotBlank()) {
                    delete(db.collection("examCodes").document(examCode))
                }
            }.commit().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, path)
            Result.failure(e)
        }
    }

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
    ): Result<Submission> {
        var path = "submissions"
        return try {
            val uid = requireUserId()
            val cleanName = studentName.trim().take(80)
            val cleanRoll = studentRoll.trim().take(40)
            require(cleanName.isNotEmpty()) { "Student name is required." }
            require(cleanRoll.isNotEmpty()) { "Student roll number is required." }
            require(exam.id.isNotBlank()) { "Valid exam is required." }

            val docRef = db.collection("submissions").document()
            path = docRef.path

            val cleanNotes = aiNotes?.trim()?.take(300)?.ifBlank { null }
            val safeDetected = (0 until exam.answerKey.size).map { idx ->
                detectedAnswers.getOrNull(idx)?.let(::normalizeOptionToken) ?: "NONE"
            }

            val payload = mapOf(
                "examId" to exam.id,
                "examCode" to exam.examCode,
                "examTitle" to exam.title,
                "subject" to exam.subject,
                "teacherId" to exam.teacherId,
                "submittedByUid" to uid,
                "scannedByRole" to scannedByRole.rawValue,
                "studentName" to cleanName,
                "studentRoll" to cleanRoll,
                "optionStyle" to exam.optionStyle,
                "detectedAnswers" to safeDetected,
                "correctAnswers" to exam.answerKey,
                "correctCount" to correctCount,
                "wrongCount" to wrongCount,
                "unansweredCount" to unansweredCount,
                "score" to score,
                "maxScore" to maxScore,
                "percentage" to percentage,
                "aiConfidence" to aiConfidence.rawValue,
                "aiNotes" to cleanNotes,
                "submittedAt" to FieldValue.serverTimestamp()
            ).filterValues { it != null }

            docRef.set(payload).await()

            Result.success(
                Submission(
                    id = docRef.id,
                    examId = exam.id,
                    examCode = exam.examCode,
                    examTitle = exam.title,
                    subject = exam.subject,
                    teacherId = exam.teacherId,
                    submittedByUid = uid,
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
                    aiNotes = cleanNotes,
                    submittedAt = Timestamp.now()
                )
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    suspend fun deleteSubmission(submissionId: String): Result<Unit> {
        val path = "submissions/$submissionId"
        return try {
            requireUserId()
            require(submissionId.isNotBlank())
            db.collection("submissions").document(submissionId).delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, path)
            Result.failure(e)
        }
    }

    private suspend fun generateUniqueExamCode(subject: String): String {
        val prefix = subject.uppercase()
            .replace(Regex("[^A-Z]"), "")
            .take(3)
            .ifBlank { "MCQ" }
        repeat(5) {
            val digits = Random.nextInt(100, 999)
            val candidate = "$prefix$digits"
            val existing = db.collection("examCodes").document(candidate).get().await()
            if (!existing.exists()) return candidate
        }
        val fallback = Random.nextInt(1000, 9999)
        return "${prefix}$fallback"
    }
}
