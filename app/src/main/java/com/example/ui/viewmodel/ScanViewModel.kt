package com.example.ui.viewmodel

import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.ExamRepository
import com.example.data.OmrAiService
import com.example.model.AiConfidence
import com.example.model.Exam
import com.example.model.GradedSummary
import com.example.model.ScannedByRole
import com.example.model.Submission
import com.example.model.gradeOmrSheet
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class ScanStep {
    SELECT_EXAM,
    CAPTURE_SHEET,
    ANALYZING,
    VERIFY_AND_SUBMIT,
    INSTANT_RESULT
}

data class ScanUiState(
    val step: ScanStep = ScanStep.SELECT_EXAM,
    val selectedExam: Exam? = null,
    val scannedByRole: ScannedByRole = ScannedByRole.STUDENT,
    val examCodeInput: String = "",
    val isLookingUpExam: Boolean = false,
    val capturedBitmap: Bitmap? = null,
    val studentName: String = "",
    val studentRoll: String = "",
    val detectedAnswers: List<String> = emptyList(),
    val aiConfidence: AiConfidence = AiConfidence.MEDIUM,
    val aiNotes: String? = null,
    val livePreviewGrade: GradedSummary? = null,
    val isSubmitting: Boolean = false,
    val submittedResult: Submission? = null,
    val errorMessage: String? = null
)

class ScanViewModel(
    private val repository: ExamRepository,
    private val aiService: OmrAiService,
    private val defaultUserName: String
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        ScanUiState(studentName = defaultUserName)
    )
    val uiState: StateFlow<ScanUiState> = _uiState.asStateFlow()

    fun updateExamCodeInput(code: String) {
        _uiState.update { it.copy(examCodeInput = code.uppercase(), errorMessage = null) }
    }

    fun setScanningRole(role: ScannedByRole) {
        _uiState.update { state ->
            val updatedName = if (role == ScannedByRole.STUDENT && state.studentName.isBlank()) {
                defaultUserName
            } else if (role == ScannedByRole.TEACHER && state.studentName == defaultUserName) {
                ""
            } else {
                state.studentName
            }
            state.copy(scannedByRole = role, studentName = updatedName)
        }
    }

    fun selectExamDirectly(exam: Exam, role: ScannedByRole = ScannedByRole.TEACHER) {
        _uiState.update {
            it.copy(
                selectedExam = exam,
                scannedByRole = role,
                examCodeInput = exam.examCode,
                step = ScanStep.CAPTURE_SHEET,
                studentName = if (role == ScannedByRole.STUDENT) defaultUserName else "",
                studentRoll = "",
                capturedBitmap = null,
                detectedAnswers = emptyList(),
                submittedResult = null,
                errorMessage = null
            )
        }
    }

    fun lookupExamByCode(codeOverride: String? = null) {
        val code = (codeOverride ?: _uiState.value.examCodeInput).trim()
        if (code.length < 4) {
            _uiState.update { it.copy(errorMessage = "Please enter a valid Exam Code (4-12 chars).") }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isLookingUpExam = true, errorMessage = null) }
            val result = repository.lookupExamByCode(code)
            result.onSuccess { exam ->
                _uiState.update {
                    it.copy(
                        isLookingUpExam = false,
                        selectedExam = exam,
                        examCodeInput = exam.examCode,
                        step = ScanStep.CAPTURE_SHEET,
                        errorMessage = null
                    )
                }
            }.onFailure { err ->
                _uiState.update {
                    it.copy(
                        isLookingUpExam = false,
                        errorMessage = err.localizedMessage ?: "Exam not found."
                    )
                }
            }
        }
    }

    fun changeExam() {
        _uiState.update {
            it.copy(
                step = ScanStep.SELECT_EXAM,
                capturedBitmap = null,
                detectedAnswers = emptyList(),
                submittedResult = null,
                errorMessage = null
            )
        }
    }

    fun analyzeCapturedSheet(bitmap: Bitmap) {
        val exam = _uiState.value.selectedExam ?: return
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    step = ScanStep.ANALYZING,
                    capturedBitmap = bitmap,
                    errorMessage = null
                )
            }
            val scanResult = aiService.scanAnswerSheet(
                bitmap = bitmap,
                totalQuestions = exam.questionCountInt,
                optionStyle = exam.parsedOptionStyle
            )
            scanResult.onSuccess { omr ->
                val resolvedName = omr.studentName
                    ?: if (_uiState.value.scannedByRole == ScannedByRole.STUDENT) {
                        _uiState.value.studentName.ifBlank { defaultUserName }
                    } else {
                        ""
                    }
                val resolvedRoll = omr.studentRoll ?: _uiState.value.studentRoll
                val preview = gradeOmrSheet(
                    answerKey = exam.answerKey,
                    detectedAnswers = omr.detectedAnswers,
                    marksPerCorrect = exam.marksPerCorrect,
                    negativeMark = exam.negativeMark
                )
                _uiState.update {
                    it.copy(
                        step = ScanStep.VERIFY_AND_SUBMIT,
                        studentName = resolvedName,
                        studentRoll = resolvedRoll,
                        detectedAnswers = omr.detectedAnswers,
                        aiConfidence = omr.confidence,
                        aiNotes = omr.notes,
                        livePreviewGrade = preview,
                        errorMessage = null
                    )
                }
            }.onFailure { err ->
                _uiState.update {
                    it.copy(
                        step = ScanStep.CAPTURE_SHEET,
                        errorMessage = err.localizedMessage ?: "Failed to scan answer sheet."
                    )
                }
            }
        }
    }

    fun updateStudentName(name: String) {
        _uiState.update { it.copy(studentName = name, errorMessage = null) }
    }

    fun updateStudentRoll(roll: String) {
        _uiState.update { it.copy(studentRoll = roll, errorMessage = null) }
    }

    fun updateDetectedAnswer(questionIndex: Int, newOption: String) {
        val exam = _uiState.value.selectedExam ?: return
        _uiState.update { state ->
            val mutable = state.detectedAnswers.toMutableList()
            if (questionIndex in mutable.indices) {
                mutable[questionIndex] = newOption
            }
            val updatedPreview = gradeOmrSheet(
                answerKey = exam.answerKey,
                detectedAnswers = mutable,
                marksPerCorrect = exam.marksPerCorrect,
                negativeMark = exam.negativeMark
            )
            state.copy(
                detectedAnswers = mutable,
                livePreviewGrade = updatedPreview
            )
        }
    }

    fun retakeSheet() {
        _uiState.update {
            it.copy(
                step = ScanStep.CAPTURE_SHEET,
                capturedBitmap = null,
                detectedAnswers = emptyList(),
                errorMessage = null
            )
        }
    }

    fun submitAndGradeSheet(validationErrorMsg: String) {
        val state = _uiState.value
        val exam = state.selectedExam ?: return
        if (state.studentName.isBlank() || state.studentRoll.isBlank()) {
            _uiState.update { it.copy(errorMessage = validationErrorMsg) }
            return
        }

        val graded = gradeOmrSheet(
            answerKey = exam.answerKey,
            detectedAnswers = state.detectedAnswers,
            marksPerCorrect = exam.marksPerCorrect,
            negativeMark = exam.negativeMark
        )

        viewModelScope.launch {
            _uiState.update { it.copy(isSubmitting = true, errorMessage = null) }
            val result = repository.saveSubmission(
                exam = exam,
                scannedByRole = state.scannedByRole,
                studentName = state.studentName,
                studentRoll = state.studentRoll,
                detectedAnswers = state.detectedAnswers,
                correctCount = graded.correctCount,
                wrongCount = graded.wrongCount,
                unansweredCount = graded.unansweredCount,
                score = graded.score,
                maxScore = graded.maxScore,
                percentage = graded.percentage,
                aiConfidence = state.aiConfidence,
                aiNotes = state.aiNotes
            )
            result.onSuccess { submission ->
                _uiState.update {
                    it.copy(
                        isSubmitting = false,
                        submittedResult = submission,
                        step = ScanStep.INSTANT_RESULT,
                        errorMessage = null
                    )
                }
            }.onFailure { err ->
                _uiState.update {
                    it.copy(
                        isSubmitting = false,
                        errorMessage = err.localizedMessage ?: "Failed to submit result."
                    )
                }
            }
        }
    }

    fun scanAnotherForSameExam() {
        _uiState.update {
            it.copy(
                step = ScanStep.CAPTURE_SHEET,
                capturedBitmap = null,
                studentName = if (it.scannedByRole == ScannedByRole.STUDENT) defaultUserName else "",
                studentRoll = "",
                detectedAnswers = emptyList(),
                aiNotes = null,
                livePreviewGrade = null,
                submittedResult = null,
                errorMessage = null
            )
        }
    }

    fun clearError() {
        _uiState.update { it.copy(errorMessage = null) }
    }
}
