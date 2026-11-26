package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Assignment
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.data.ExamDataSource
import com.example.i18n.AppStrings
import com.example.model.Exam
import com.example.model.ExamStatus
import com.example.model.Submission
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.ScanEmerald
import com.example.ui.theme.WarmAmber
import com.example.ui.viewmodel.DashboardViewModel
import com.example.ui.viewmodel.ExamDetailViewModel
import com.example.ui.viewmodel.UiState

@Composable
fun ExamsScreen(
    currentUserId: String,
    teacherName: String,
    dataSource: ExamDataSource,
    dashboardViewModel: DashboardViewModel,
    strings: AppStrings,
    showCreateTrigger: Boolean,
    onConsumeCreateTrigger: () -> Unit,
    onScanForExam: (Exam) -> Unit,
    onOpenSubmission: (Submission) -> Unit,
    modifier: Modifier = Modifier
) {
    val examsState by dashboardViewModel.teacherExamsState.collectAsStateWithLifecycle()
    val isSaving by dashboardViewModel.isSavingExam.collectAsStateWithLifecycle()
    val actionError by dashboardViewModel.actionError.collectAsStateWithLifecycle()

    var selectedExamId by remember { mutableStateOf<String?>(null) }
    var editingExam by remember { mutableStateOf<Exam?>(null) }
    var showEditor by remember(showCreateTrigger) { mutableStateOf(showCreateTrigger) }

    if (showCreateTrigger) {
        onConsumeCreateTrigger()
    }

    val examsList = (examsState as? UiState.Success)?.data.orEmpty()
    val selectedExam = examsList.firstOrNull { it.id == selectedExamId }

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val isExpanded = maxWidth >= 840.dp

        if (!isExpanded && selectedExam != null) {
            BackHandler { selectedExamId = null }
            val detailVm: ExamDetailViewModel = viewModel(
                key = "${currentUserId}_${selectedExam.id}",
                factory = examDetailViewModelFactory(dataSource, currentUserId, selectedExam.id)
            )
            ExamDetailPane(
                exam = selectedExam,
                detailViewModel = detailVm,
                strings = strings,
                showBackButton = true,
                onBack = { selectedExamId = null },
                onEditExam = {
                    editingExam = selectedExam
                    showEditor = true
                },
                onToggleStatus = { dashboardViewModel.toggleExamStatus(selectedExam) },
                onDeleteExam = {
                    dashboardViewModel.deleteExam(selectedExam) {
                        selectedExamId = null
                    }
                },
                onScanStudentSheet = { onScanForExam(selectedExam) },
                onSelectSubmission = onOpenSubmission
            )
        } else {
            Row(modifier = Modifier.fillMaxSize()) {
                Box(
                    contentAlignment = Alignment.TopCenter,
                    modifier = Modifier
                        .weight(if (isExpanded) 0.44f else 1f)
                        .fillMaxSize()
                ) {
                    LazyColumn(
                        contentPadding = PaddingValues(
                            start = 16.dp,
                            end = 16.dp,
                            top = 16.dp,
                            bottom = 88.dp
                        ),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier
                            .fillMaxSize()
                            .widthIn(max = 680.dp)
                    ) {
                        item {
                            Text(
                                text = strings.teacherExamsTitle,
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        when (val state = examsState) {
                            is UiState.Loading -> {
                                item {
                                    Box(
                                        contentAlignment = Alignment.Center,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(32.dp)
                                    ) {
                                        CircularProgressIndicator()
                                    }
                                }
                            }
                            is UiState.Error -> {
                                item {
                                    Surface(
                                        shape = RoundedCornerShape(12.dp),
                                        color = MaterialTheme.colorScheme.errorContainer
                                    ) {
                                        Text(
                                            text = state.message,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onErrorContainer,
                                            modifier = Modifier.padding(14.dp)
                                        )
                                    }
                                }
                            }
                            is UiState.Success -> {
                                if (state.data.isEmpty()) {
                                    item {
                                        EmptyExamsPlaceholder(strings = strings)
                                    }
                                } else {
                                    items(state.data, key = { it.id }) { exam ->
                                        ExamSummaryCard(
                                            exam = exam,
                                            isSelected = isExpanded && exam.id == selectedExamId,
                                            strings = strings,
                                            onClick = { selectedExamId = exam.id }
                                        )
                                    }
                                }
                            }
                        }
                    }

                    FloatingActionButton(
                        onClick = {
                            editingExam = null
                            dashboardViewModel.clearActionError()
                            showEditor = true
                        },
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(16.dp)
                            .testTag("create_exam_fab")
                    ) {
                        Icon(Icons.Default.Add, contentDescription = strings.newExamTitle)
                    }
                }

                if (isExpanded) {
                    val activeExam = selectedExam ?: examsList.firstOrNull()
                    Box(
                        modifier = Modifier
                            .weight(0.56f)
                            .fillMaxSize()
                    ) {
                        if (activeExam != null) {
                            val detailVm: ExamDetailViewModel = viewModel(
                                key = "${currentUserId}_${activeExam.id}",
                                factory = examDetailViewModelFactory(dataSource, currentUserId, activeExam.id)
                            )
                            ExamDetailPane(
                                exam = activeExam,
                                detailViewModel = detailVm,
                                strings = strings,
                                showBackButton = false,
                                onBack = {},
                                onEditExam = {
                                    editingExam = activeExam
                                    showEditor = true
                                },
                                onToggleStatus = { dashboardViewModel.toggleExamStatus(activeExam) },
                                onDeleteExam = {
                                    dashboardViewModel.deleteExam(activeExam) {
                                        selectedExamId = null
                                    }
                                },
                                onScanStudentSheet = { onScanForExam(activeExam) },
                                onSelectSubmission = onOpenSubmission
                            )
                        } else {
                            EmptyExamsPlaceholder(
                                strings = strings,
                                modifier = Modifier
                                    .align(Alignment.Center)
                                    .padding(24.dp)
                            )
                        }
                    }
                }
            }
        }
    }

    if (showEditor) {
        ExamEditorDialog(
            existingExam = editingExam,
            strings = strings,
            isSaving = isSaving,
            errorMessage = actionError,
            onDismiss = { showEditor = false },
            onSave = { title, subject, totalQuestions, optionStyle, marks, neg, key ->
                val target = editingExam
                if (target == null) {
                    dashboardViewModel.createExam(
                        teacherName = teacherName,
                        title = title,
                        subject = subject,
                        totalQuestions = totalQuestions,
                        optionStyle = optionStyle,
                        marksPerCorrect = marks,
                        negativeMark = neg,
                        answerKey = key,
                        onCreated = { created ->
                            selectedExamId = created.id
                            showEditor = false
                        }
                    )
                } else {
                    dashboardViewModel.updateExam(
                        examId = target.id,
                        examCode = target.examCode,
                        title = title,
                        subject = subject,
                        totalQuestions = totalQuestions,
                        optionStyle = optionStyle,
                        marksPerCorrect = marks,
                        negativeMark = neg,
                        answerKey = key,
                        onUpdated = { showEditor = false }
                    )
                }
            }
        )
    }
}

@Composable
private fun ExamSummaryCard(
    exam: Exam,
    isSelected: Boolean,
    strings: AppStrings,
    onClick: () -> Unit
) {
    val isActive = exam.parsedStatus == ExamStatus.ACTIVE
    Card(
        onClick = onClick,
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
            else MaterialTheme.colorScheme.surface
        ),
        border = BorderStroke(
            width = if (isSelected) 2.dp else 1.dp,
            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
        ),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("exam_card_${exam.id}")
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = exam.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "${exam.subject} • ${exam.totalQuestions} ${strings.questionsShort}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.primaryContainer
                    ) {
                        Text(
                            text = exam.examCode,
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontFamily = JetBrainsMonoFontFamily,
                                fontWeight = FontWeight.Bold
                            ),
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = if (isActive) ScanEmerald else WarmAmber
                    ) {
                        Text(
                            text = if (isActive) strings.statusActive else strings.statusClosed,
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyExamsPlaceholder(
    strings: AppStrings,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(28.dp)
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.Assignment,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(44.dp)
            )
            Text(
                text = strings.noExamsYetTitle,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = strings.noExamsYetSubtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private fun examDetailViewModelFactory(
    dataSource: ExamDataSource,
    teacherId: String,
    examId: String
) = viewModelFactory {
    initializer {
        ExamDetailViewModel(dataSource, teacherId, examId)
    }
}
