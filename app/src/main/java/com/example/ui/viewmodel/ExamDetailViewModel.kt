package com.example.ui.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.ExamDataSource
import com.example.model.Submission
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

class ExamDetailViewModel(
    repository: ExamDataSource,
    teacherId: String,
    examId: String
) : ViewModel() {

    val submissionsState: StateFlow<UiState<List<Submission>>> =
        repository.observeExamSubmissions(teacherId, examId)
            .map<List<Submission>, UiState<List<Submission>>> { UiState.Success(it) }
            .catch { error ->
                if (error is CancellationException) throw error
                Log.w(TAG, "Error observing exam submissions", error)
                emit(UiState.Error(error.localizedMessage ?: "Failed to load exam submissions"))
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000L),
                initialValue = UiState.Loading
            )

    private companion object {
        const val TAG = "ExamDetailVM"
    }
}
