package com.example.ui.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.ExamRepository
import com.example.model.Exam
import com.example.model.ExamStatus
import com.example.model.OptionStyle
import com.example.model.Submission
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class DashboardViewModel(
    private val repository: ExamRepository,
    private val currentUserId: String
) : ViewModel() {

    val teacherExamsState: StateFlow<UiState<List<Exam>>> = repository.observeTeacherExams(currentUserId)
        .map<List<Exam>, UiState<List<Exam>>> { UiState.Success(it) }
        .catch { error ->
            val firestoreError = generateSequence(error) { it.cause }
                .filterIsInstance<FirebaseFirestoreException>()
                .firstOrNull()
            if (firestoreError == null && error is CancellationException) throw error
            val rootError = firestoreError ?: error
            Log.w(TAG, "Error observing teacher exams", rootError)
            emit(UiState.Error(rootError.localizedMessage ?: "Failed to load exams"))
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = UiState.Loading
        )

    val mySubmissionsState: StateFlow<UiState<List<Submission>>> = repository.observeMySubmissions(currentUserId)
        .map<List<Submission>, UiState<List<Submission>>> { UiState.Success(it) }
        .catch { error ->
            val firestoreError = generateSequence(error) { it.cause }
                .filterIsInstance<FirebaseFirestoreException>()
                .firstOrNull()
            if (firestoreError == null && error is CancellationException) throw error
            val rootError = firestoreError ?: error
            Log.w(TAG, "Error observing submissions", rootError)
            emit(UiState.Error(rootError.localizedMessage ?: "Failed to load results"))
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = UiState.Loading
        )

    private val _isSavingExam = MutableStateFlow(false)
    val isSavingExam: StateFlow<Boolean> = _isSavingExam.asStateFlow()

    private val _actionError = MutableStateFlow<String?>(null)
    val actionError: StateFlow<String?> = _actionError.asStateFlow()

    fun clearActionError() {
        _actionError.value = null
    }

    fun createExam(
        teacherName: String,
        title: String,
        subject: String,
        totalQuestions: Int,
        optionStyle: OptionStyle,
        marksPerCorrect: Double,
        negativeMark: Double,
        answerKey: List<String>,
        onCreated: (Exam) -> Unit
    ) {
        viewModelScope.launch {
            _isSavingExam.value = true
            _actionError.value = null
            val result = repository.createExam(
                teacherName = teacherName,
                title = title,
                subject = subject,
                totalQuestions = totalQuestions,
                optionStyle = optionStyle,
                marksPerCorrect = marksPerCorrect,
                negativeMark = negativeMark,
                answerKey = answerKey
            )
            _isSavingExam.value = false
            result.onSuccess(onCreated)
                .onFailure { _actionError.value = it.localizedMessage ?: "Failed to create exam." }
        }
    }

    fun updateExam(
        examId: String,
        examCode: String,
        title: String,
        subject: String,
        totalQuestions: Int,
        optionStyle: OptionStyle,
        marksPerCorrect: Double,
        negativeMark: Double,
        answerKey: List<String>,
        onUpdated: () -> Unit
    ) {
        viewModelScope.launch {
            _isSavingExam.value = true
            _actionError.value = null
            val result = repository.updateExam(
                examId = examId,
                examCode = examCode,
                title = title,
                subject = subject,
                totalQuestions = totalQuestions,
                optionStyle = optionStyle,
                marksPerCorrect = marksPerCorrect,
                negativeMark = negativeMark,
                answerKey = answerKey
            )
            _isSavingExam.value = false
            result.onSuccess { onUpdated() }
                .onFailure { _actionError.value = it.localizedMessage ?: "Failed to update exam." }
        }
    }

    fun toggleExamStatus(exam: Exam) {
        val next = if (exam.parsedStatus == ExamStatus.ACTIVE) ExamStatus.CLOSED else ExamStatus.ACTIVE
        viewModelScope.launch {
            repository.updateExamStatus(exam.id, exam.examCode, next)
                .onFailure { _actionError.value = it.localizedMessage ?: "Failed to update status." }
        }
    }

    fun deleteExam(exam: Exam, onDeleted: () -> Unit = {}) {
        viewModelScope.launch {
            repository.deleteExam(exam.id, exam.examCode)
                .onSuccess { onDeleted() }
                .onFailure { _actionError.value = it.localizedMessage ?: "Failed to delete exam." }
        }
    }

    fun deleteSubmission(submissionId: String) {
        viewModelScope.launch {
            repository.deleteSubmission(submissionId)
                .onFailure { _actionError.value = it.localizedMessage ?: "Failed to delete result." }
        }
    }

    private companion object {
        const val TAG = "DashboardVM"
    }
}
