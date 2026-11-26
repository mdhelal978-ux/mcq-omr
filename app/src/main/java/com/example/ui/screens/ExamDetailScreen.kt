package com.example.ui.screens

import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.i18n.AppStrings
import com.example.model.Exam
import com.example.model.ExamStatus
import com.example.model.Submission
import com.example.ui.components.SubmissionResultCard
import com.example.ui.components.formatScore
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.ScanEmerald
import com.example.ui.theme.WarmAmber
import com.example.ui.viewmodel.ExamDetailViewModel
import com.example.ui.viewmodel.UiState

@Composable
fun ExamDetailPane(
    exam: Exam,
    detailViewModel: ExamDetailViewModel,
    strings: AppStrings,
    showBackButton: Boolean,
    onBack: () -> Unit,
    onEditExam: () -> Unit,
    onToggleStatus: () -> Unit,
    onDeleteExam: () -> Unit,
    onScanStudentSheet: () -> Unit,
    onSelectSubmission: (Submission) -> Unit,
    modifier: Modifier = Modifier
) {
    val submissionsState by detailViewModel.submissionsState.collectAsStateWithLifecycle()
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current
    var showDeleteConfirm by remember { mutableStateOf(false) }

    val submissions = (submissionsState as? UiState.Success)?.data.orEmpty()
    val avgScore = remember(submissions) {
        if (submissions.isEmpty()) 0.0 else submissions.map { it.score }.average()
    }
    val maxScore = remember(submissions) {
        submissions.maxOfOrNull { it.score } ?: 0.0
    }
    val isActive = exam.parsedStatus == ExamStatus.ACTIVE

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        modifier = modifier.fillMaxSize()
    ) {
        item {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (showBackButton) {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = exam.title,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "${exam.subject} • ${exam.totalQuestions} ${strings.questionsShort}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(
                    onClick = onEditExam,
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("edit_exam_button")
                ) {
                    Icon(Icons.Default.Edit, contentDescription = strings.editExamTitle)
                }
                IconButton(
                    onClick = { showDeleteConfirm = true },
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("delete_exam_button")
                ) {
                    Icon(
                        Icons.Default.DeleteOutline,
                        contentDescription = strings.deleteExam,
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }
        }

        // Exam Code & Status Card
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.padding(16.dp)
                ) {
                    Row(
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column {
                            Text(
                                text = strings.examCodeLabel,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Text(
                                text = exam.examCode,
                                style = MaterialTheme.typography.headlineMedium.copy(
                                    fontFamily = JetBrainsMonoFontFamily,
                                    fontWeight = FontWeight.Bold
                                ),
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }

                        Surface(
                            onClick = onToggleStatus,
                            shape = RoundedCornerShape(50),
                            color = if (isActive) ScanEmerald else WarmAmber
                        ) {
                            Text(
                                text = if (isActive) strings.statusActive else strings.statusClosed,
                                style = MaterialTheme.typography.labelMedium,
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                            )
                        }
                    }

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        OutlinedButton(
                            onClick = {
                                clipboard.setText(AnnotatedString(exam.examCode))
                                Toast.makeText(context, strings.codeCopied, Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 48.dp)
                                .testTag("copy_exam_code_button")
                        ) {
                            Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(strings.copyCode)
                        }
                        OutlinedButton(
                            onClick = {
                                val sendIntent = Intent(Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(
                                        Intent.EXTRA_TEXT,
                                        "${exam.title} (${exam.subject}) — ${strings.examCodeLabel}: ${exam.examCode}"
                                    )
                                }
                                context.startActivity(Intent.createChooser(sendIntent, strings.shareExam))
                            },
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 48.dp)
                                .testTag("share_exam_code_button")
                        ) {
                            Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(strings.shareExam)
                        }
                    }
                }
            }
        }

        // Scan Student Sheet CTA
        item {
            Button(
                onClick = onScanStudentSheet,
                colors = ButtonDefaults.buttonColors(
                    containerColor = ScanEmerald,
                    contentColor = Color.White
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 52.dp)
                    .testTag("exam_scan_student_button")
            ) {
                Icon(Icons.Default.QrCodeScanner, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text(strings.scanStudentSheet)
            }
        }

        // Class Stats Row
        item {
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                ExamStatBox(
                    label = strings.totalSubmissionsLabel,
                    value = submissions.size.toString(),
                    modifier = Modifier.weight(1f)
                )
                ExamStatBox(
                    label = strings.averageScoreLabel,
                    value = formatScore(avgScore),
                    modifier = Modifier.weight(1f)
                )
                ExamStatBox(
                    label = strings.highestScoreLabel,
                    value = formatScore(maxScore),
                    modifier = Modifier.weight(1f)
                )
            }
        }

        item {
            Text(
                text = strings.classSubmissionsTitle,
                style = MaterialTheme.typography.titleMedium
            )
        }

        when (val state = submissionsState) {
            is UiState.Loading -> {
                item {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp)
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
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = strings.noSubmissionsForExam,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(20.dp)
                            )
                        }
                    }
                } else {
                    items(state.data, key = { it.id }) { sub ->
                        SubmissionResultCard(
                            submission = sub,
                            strings = strings,
                            onClick = { onSelectSubmission(sub) }
                        )
                    }
                }
            }
        }
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text(strings.deleteExam) },
            text = { Text(strings.confirmDeleteExam) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteConfirm = false
                        onDeleteExam()
                    }
                ) {
                    Text(strings.confirm, color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text(strings.cancel)
                }
            }
        )
    }
}

@Composable
private fun ExamStatBox(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = modifier
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            Text(
                text = value,
                style = MaterialTheme.typography.titleLarge.copy(
                    fontFamily = JetBrainsMonoFontFamily,
                    fontWeight = FontWeight.Bold
                ),
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
