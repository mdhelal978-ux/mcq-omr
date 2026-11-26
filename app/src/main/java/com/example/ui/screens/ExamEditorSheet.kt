package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.i18n.AppStrings
import com.example.model.Exam
import com.example.model.OptionStyle
import com.example.ui.theme.JetBrainsMonoFontFamily

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExamEditorDialog(
    existingExam: Exam?,
    strings: AppStrings,
    isSaving: Boolean,
    errorMessage: String?,
    onDismiss: () -> Unit,
    onSave: (
        title: String,
        subject: String,
        totalQuestions: Int,
        optionStyle: OptionStyle,
        marksPerCorrect: Double,
        negativeMark: Double,
        answerKey: List<String>
    ) -> Unit
) {
    var title by remember(existingExam) { mutableStateOf(existingExam?.title.orEmpty()) }
    var subject by remember(existingExam) { mutableStateOf(existingExam?.subject.orEmpty()) }
    var questionCount by remember(existingExam) {
        mutableStateOf(existingExam?.questionCountInt ?: 10)
    }
    var optionStyle by remember(existingExam) {
        mutableStateOf(existingExam?.parsedOptionStyle ?: OptionStyle.ABCD)
    }
    var marksInput by remember(existingExam) {
        mutableStateOf((existingExam?.marksPerCorrect ?: 1.0).toString())
    }
    var negativeInput by remember(existingExam) {
        mutableStateOf((existingExam?.negativeMark ?: 0.0).toString())
    }
    var localError by remember { mutableStateOf<String?>(null) }

    val answerKeyState = remember(existingExam) {
        mutableStateListOf<String>().apply {
            val initial = existingExam?.answerKey.orEmpty()
            for (i in 0 until 100) {
                add(initial.getOrNull(i) ?: "A")
            }
        }
    }

    val presetCounts = listOf(5, 10, 15, 20, 25, 30, 40, 50, 100)
    val labels = optionStyle.displayLabels()
    val tokens = listOf("A", "B", "C", "D")

    Dialog(
        onDismissRequest = { if (!isSaving) onDismiss() },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            text = if (existingExam == null) strings.newExamTitle else strings.editExamTitle,
                            style = MaterialTheme.typography.titleLarge
                        )
                    },
                    navigationIcon = {
                        IconButton(
                            onClick = { if (!isSaving) onDismiss() },
                            modifier = Modifier.size(48.dp)
                        ) {
                            Icon(Icons.Default.Close, contentDescription = strings.cancel)
                        }
                    }
                )
            },
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding()
        ) { innerPadding ->
            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                val activeError = localError ?: errorMessage
                if (activeError != null) {
                    item {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.errorContainer
                        ) {
                            Text(
                                text = activeError,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.padding(12.dp)
                            )
                        }
                    }
                }

                item {
                    OutlinedTextField(
                        value = title,
                        onValueChange = {
                            title = it
                            localError = null
                        },
                        label = { Text(strings.examTitleLabel) },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("exam_title_input")
                    )
                }

                item {
                    OutlinedTextField(
                        value = subject,
                        onValueChange = {
                            subject = it
                            localError = null
                        },
                        label = { Text(strings.examSubjectLabel) },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("exam_subject_input")
                    )
                }

                item {
                    Text(
                        text = "${strings.totalQuestionsLabel}: $questionCount",
                        style = MaterialTheme.typography.titleSmall
                    )
                    Spacer(Modifier.height(6.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.horizontalScroll(rememberScrollState())
                    ) {
                        presetCounts.forEach { count ->
                            FilterChip(
                                selected = questionCount == count,
                                onClick = { questionCount = count },
                                label = { Text(count.toString()) },
                                modifier = Modifier.heightIn(min = 44.dp)
                            )
                        }
                    }
                }

                item {
                    Text(
                        text = strings.optionStyleLabel,
                        style = MaterialTheme.typography.titleSmall
                    )
                    Spacer(Modifier.height(6.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        FilterChip(
                            selected = optionStyle == OptionStyle.ABCD,
                            onClick = { optionStyle = OptionStyle.ABCD },
                            label = { Text(strings.optionStyleAbcd) },
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 48.dp)
                        )
                        FilterChip(
                            selected = optionStyle == OptionStyle.BN_KAKH,
                            onClick = { optionStyle = OptionStyle.BN_KAKH },
                            label = { Text(strings.optionStyleBn) },
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 48.dp)
                        )
                    }
                }

                item {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        OutlinedTextField(
                            value = marksInput,
                            onValueChange = { marksInput = it },
                            label = { Text(strings.marksPerCorrectLabel) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = negativeInput,
                            onValueChange = { negativeInput = it },
                            label = { Text(strings.negativeMarkLabel) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                item {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = strings.answerKeySectionTitle,
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = strings.setAllPrefix,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        tokens.forEachIndexed { idx, token ->
                            FilterChip(
                                selected = false,
                                onClick = {
                                    for (q in 0 until questionCount) {
                                        answerKeyState[q] = token
                                    }
                                },
                                label = { Text(labels[idx]) }
                            )
                        }
                    }
                }

                items(questionCount) { idx ->
                    val currentAns = answerKeyState[idx]
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surface,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 8.dp)
                        ) {
                            Text(
                                text = "Q${idx + 1}",
                                style = MaterialTheme.typography.titleSmall.copy(
                                    fontFamily = JetBrainsMonoFontFamily,
                                    fontWeight = FontWeight.Bold
                                ),
                                modifier = Modifier.width(48.dp)
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                tokens.forEachIndexed { optIdx, token ->
                                    val selected = currentAns == token
                                    Box(
                                        contentAlignment = Alignment.Center,
                                        modifier = Modifier
                                            .size(44.dp)
                                            .clip(CircleShape)
                                            .background(
                                                if (selected) MaterialTheme.colorScheme.primary
                                                else MaterialTheme.colorScheme.surfaceVariant
                                            )
                                            .clickable { answerKeyState[idx] = token }
                                            .testTag("key_q${idx + 1}_$token")
                                    ) {
                                        Text(
                                            text = labels[optIdx],
                                            style = MaterialTheme.typography.labelLarge,
                                            color = if (selected) Color.White else MaterialTheme.colorScheme.onSurface,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                item {
                    Spacer(Modifier.height(8.dp))
                    Button(
                        onClick = {
                            val cleanTitle = title.trim()
                            val cleanSubject = subject.trim()
                            val pos = marksInput.toDoubleOrNull() ?: 1.0
                            val neg = negativeInput.toDoubleOrNull() ?: 0.0
                            if (cleanTitle.length < 2 || cleanSubject.isEmpty()) {
                                localError = strings.validationExamFields
                                return@Button
                            }
                            onSave(
                                cleanTitle,
                                cleanSubject,
                                questionCount,
                                optionStyle,
                                pos.coerceIn(0.25, 10.0),
                                neg.coerceIn(0.0, 5.0),
                                answerKeyState.take(questionCount)
                            )
                        },
                        enabled = !isSaving,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 52.dp)
                            .testTag("save_exam_button")
                    ) {
                        if (isSaving) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                color = MaterialTheme.colorScheme.onPrimary,
                                strokeWidth = 2.dp
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(strings.savingExam)
                        } else {
                            Text(strings.saveExamButton)
                        }
                    }
                }
            }
        }
    }
}
