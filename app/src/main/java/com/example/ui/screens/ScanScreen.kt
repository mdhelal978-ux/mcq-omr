package com.example.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.i18n.AppStrings
import com.example.model.AiConfidence
import com.example.model.Exam
import com.example.model.ScannedByRole
import com.example.ui.components.CameraPreviewScanner
import com.example.ui.components.InstantResultContent
import com.example.ui.components.decodeBitmapFromUri
import com.example.ui.components.formatScore
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.ScanEmerald
import com.example.ui.theme.WarmAmber
import com.example.ui.viewmodel.ScanStep
import com.example.ui.viewmodel.ScanUiState
import com.example.ui.viewmodel.ScanViewModel
import com.example.ui.viewmodel.UiState

@Composable
fun ScanScreen(
    scanViewModel: ScanViewModel,
    teacherExamsState: UiState<List<Exam>>,
    strings: AppStrings,
    onDoneToHome: () -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by scanViewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    var cameraErrorMsg by remember { mutableStateOf<String?>(null) }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { granted -> hasCameraPermission = granted }
    )

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
        onResult = { uri ->
            if (uri != null) {
                try {
                    val bmp = decodeBitmapFromUri(context, uri)
                    scanViewModel.analyzeCapturedSheet(bmp)
                } catch (e: Exception) {
                    cameraErrorMsg = e.localizedMessage ?: "Could not load selected image."
                }
            }
        }
    )

    val launchGalleryPicker: () -> Unit = {
        photoPickerLauncher.launch(
            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
        )
    }

    if (uiState.step == ScanStep.CAPTURE_SHEET ||
        uiState.step == ScanStep.VERIFY_AND_SUBMIT ||
        uiState.step == ScanStep.INSTANT_RESULT
    ) {
        BackHandler {
            when (uiState.step) {
                ScanStep.CAPTURE_SHEET -> scanViewModel.changeExam()
                ScanStep.VERIFY_AND_SUBMIT -> scanViewModel.retakeSheet()
                ScanStep.INSTANT_RESULT -> scanViewModel.scanAnotherForSameExam()
                else -> Unit
            }
        }
    }

    Box(
        contentAlignment = Alignment.TopCenter,
        modifier = modifier.fillMaxSize()
    ) {
        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 680.dp)
        ) {
            item {
                Text(
                    text = strings.scanTitle,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(10.dp))
                ScanStepHeader(currentStep = uiState.step, strings = strings)
            }

            if (uiState.errorMessage != null || cameraErrorMsg != null) {
                item {
                    val msg = uiState.errorMessage ?: cameraErrorMsg.orEmpty()
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.errorContainer,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.4f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(12.dp)
                        ) {
                            Text(
                                text = msg,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.weight(1f)
                            )
                            TextButton(onClick = {
                                scanViewModel.clearError()
                                cameraErrorMsg = null
                            }) {
                                Text(strings.cancel)
                            }
                        }
                    }
                }
            }

            when (uiState.step) {
                ScanStep.SELECT_EXAM -> {
                    item {
                        SelectExamStepContent(
                            uiState = uiState,
                            teacherExams = (teacherExamsState as? UiState.Success)?.data.orEmpty(),
                            strings = strings,
                            onRoleChange = scanViewModel::setScanningRole,
                            onCodeChange = scanViewModel::updateExamCodeInput,
                            onLookupCode = { scanViewModel.lookupExamByCode() },
                            onSelectOwnExam = { exam ->
                                scanViewModel.selectExamDirectly(exam, uiState.scannedByRole)
                            }
                        )
                    }
                }

                ScanStep.CAPTURE_SHEET -> {
                    item {
                        val exam = uiState.selectedExam
                        if (exam != null) {
                            SelectedExamBanner(
                                exam = exam,
                                strings = strings,
                                onChangeExam = scanViewModel::changeExam
                            )
                        }
                    }
                    item {
                        if (hasCameraPermission) {
                            CameraPreviewScanner(
                                strings = strings,
                                onBitmapCaptured = { bmp ->
                                    cameraErrorMsg = null
                                    scanViewModel.analyzeCapturedSheet(bmp)
                                },
                                onPickGallery = launchGalleryPicker,
                                onError = { err -> cameraErrorMsg = err }
                            )
                        } else {
                            Card(
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(14.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(24.dp)
                                ) {
                                    Box(
                                        contentAlignment = Alignment.Center,
                                        modifier = Modifier
                                            .size(64.dp)
                                            .clip(CircleShape)
                                            .background(MaterialTheme.colorScheme.primaryContainer)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.CameraAlt,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(30.dp)
                                        )
                                    }
                                    Text(
                                        text = strings.cameraPermissionRequired,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Button(
                                            onClick = {
                                                cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                                            },
                                            modifier = Modifier
                                                .weight(1f)
                                                .heightIn(min = 48.dp)
                                                .testTag("grant_camera_button")
                                        ) {
                                            Icon(Icons.Default.CameraAlt, contentDescription = null, modifier = Modifier.size(18.dp))
                                            Spacer(Modifier.width(6.dp))
                                            Text(strings.grantCameraPermission)
                                        }
                                        OutlinedButton(
                                            onClick = launchGalleryPicker,
                                            modifier = Modifier
                                                .weight(1f)
                                                .heightIn(min = 48.dp)
                                                .testTag("gallery_upload_button")
                                        ) {
                                            Icon(Icons.Default.PhotoLibrary, contentDescription = null, modifier = Modifier.size(18.dp))
                                            Spacer(Modifier.width(6.dp))
                                            Text(strings.galleryUploadButton)
                                        }
                                    }
                                }
                            }
                        }
                    }
                    if (hasCameraPermission) {
                        item {
                            OutlinedButton(
                                onClick = launchGalleryPicker,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 48.dp)
                                    .testTag("gallery_upload_button")
                            ) {
                                Icon(Icons.Default.PhotoLibrary, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text(strings.galleryUploadButton)
                            }
                        }
                    }
                }

                ScanStep.ANALYZING -> {
                    item {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(16.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(28.dp)
                            ) {
                                uiState.capturedBitmap?.let { bmp ->
                                    Image(
                                        bitmap = bmp.asImageBitmap(),
                                        contentDescription = null,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(180.dp)
                                            .clip(RoundedCornerShape(14.dp))
                                    )
                                }
                                CircularProgressIndicator(color = ScanEmerald)
                                Text(
                                    text = strings.analyzingTitle,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = strings.analyzingSubtitle,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                LinearProgressIndicator(
                                    modifier = Modifier.fillMaxWidth(),
                                    color = ScanEmerald
                                )
                            }
                        }
                    }
                }

                ScanStep.VERIFY_AND_SUBMIT -> {
                    val exam = uiState.selectedExam
                    if (exam != null) {
                        item {
                            VerifyAnswersHeaderCard(
                                uiState = uiState,
                                exam = exam,
                                strings = strings,
                                onNameChange = scanViewModel::updateStudentName,
                                onRollChange = scanViewModel::updateStudentRoll
                            )
                        }

                        items(exam.questionCountInt) { idx ->
                            val detected = uiState.detectedAnswers.getOrElse(idx) { "NONE" }
                            VerifyQuestionRow(
                                questionNumber = idx + 1,
                                selectedOption = detected,
                                labels = exam.parsedOptionStyle.displayLabels(),
                                strings = strings,
                                onSelectOption = { opt ->
                                    scanViewModel.updateDetectedAnswer(idx, opt)
                                }
                            )
                        }

                        item {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                OutlinedButton(
                                    onClick = scanViewModel::retakeSheet,
                                    enabled = !uiState.isSubmitting,
                                    modifier = Modifier
                                        .heightIn(min = 52.dp)
                                        .testTag("rescan_sheet_button")
                                ) {
                                    Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text(strings.rescanSheet)
                                }

                                Button(
                                    onClick = {
                                        scanViewModel.submitAndGradeSheet(strings.validationStudentInfo)
                                    },
                                    enabled = !uiState.isSubmitting,
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = ScanEmerald,
                                        contentColor = Color.White
                                    ),
                                    modifier = Modifier
                                        .weight(1f)
                                        .heightIn(min = 52.dp)
                                        .testTag("submit_omr_button")
                                ) {
                                    if (uiState.isSubmitting) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(20.dp),
                                            color = Color.White,
                                            strokeWidth = 2.dp
                                        )
                                        Spacer(Modifier.width(8.dp))
                                        Text(strings.submittingResult)
                                    } else {
                                        Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                                        Spacer(Modifier.width(6.dp))
                                        Text(strings.submitAndViewResult)
                                    }
                                }
                            }
                        }
                    }
                }

                ScanStep.INSTANT_RESULT -> {
                    val result = uiState.submittedResult
                    if (result != null) {
                        item {
                            InstantResultContent(
                                submission = result,
                                strings = strings,
                                onScanAnother = scanViewModel::scanAnotherForSameExam,
                                onDone = {
                                    scanViewModel.changeExam()
                                    onDoneToHome()
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ScanStepHeader(
    currentStep: ScanStep,
    strings: AppStrings
) {
    val activeIdx = when (currentStep) {
        ScanStep.SELECT_EXAM -> 0
        ScanStep.CAPTURE_SHEET, ScanStep.ANALYZING -> 1
        ScanStep.VERIFY_AND_SUBMIT -> 2
        ScanStep.INSTANT_RESULT -> 3
    }
    val labels = listOf(
        strings.stepChooseExam,
        strings.stepCaptureSheet,
        strings.stepVerifyAnswers,
        strings.stepInstantResult
    )

    Row(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        labels.forEachIndexed { index, label ->
            val isCompletedOrCurrent = index <= activeIdx
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = if (isCompletedOrCurrent) {
                    MaterialTheme.colorScheme.primaryContainer
                } else {
                    MaterialTheme.colorScheme.surfaceVariant
                },
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = "${index + 1}. $label",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isCompletedOrCurrent) {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    maxLines = 1,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
                )
            }
        }
    }
}

@Composable
private fun SelectExamStepContent(
    uiState: ScanUiState,
    teacherExams: List<Exam>,
    strings: AppStrings,
    onRoleChange: (ScannedByRole) -> Unit,
    onCodeChange: (String) -> Unit,
    onLookupCode: () -> Unit,
    onSelectOwnExam: (Exam) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.padding(16.dp)
            ) {
                Text(
                    text = strings.scanningAsRoleLabel,
                    style = MaterialTheme.typography.titleSmall
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    FilterChip(
                        selected = uiState.scannedByRole == ScannedByRole.STUDENT,
                        onClick = { onRoleChange(ScannedByRole.STUDENT) },
                        label = { Text(strings.roleStudent) },
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp)
                            .testTag("role_student_chip")
                    )
                    FilterChip(
                        selected = uiState.scannedByRole == ScannedByRole.TEACHER,
                        onClick = { onRoleChange(ScannedByRole.TEACHER) },
                        label = { Text(strings.roleTeacher) },
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp)
                            .testTag("role_teacher_chip")
                    )
                }

                Text(
                    text = strings.enterExamCodePrompt,
                    style = MaterialTheme.typography.titleSmall
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedTextField(
                        value = uiState.examCodeInput,
                        onValueChange = onCodeChange,
                        placeholder = { Text(strings.examCodeHint) },
                        leadingIcon = { Icon(Icons.Default.QrCode2, contentDescription = null) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(
                            capitalization = KeyboardCapitalization.Characters,
                            imeAction = ImeAction.Search
                        ),
                        keyboardActions = KeyboardActions(onSearch = { onLookupCode() }),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("scan_exam_code_input")
                    )
                    Button(
                        onClick = onLookupCode,
                        enabled = !uiState.isLookingUpExam && uiState.examCodeInput.trim().length >= 4,
                        modifier = Modifier
                            .heightIn(min = 52.dp)
                            .testTag("scan_lookup_exam_button")
                    ) {
                        if (uiState.isLookingUpExam) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                color = MaterialTheme.colorScheme.onPrimary,
                                strokeWidth = 2.dp
                            )
                        } else {
                            Text(strings.findExamButton)
                        }
                    }
                }
            }
        }

        if (teacherExams.isNotEmpty()) {
            Text(
                text = strings.orPickYourExam,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                teacherExams.forEach { exam ->
                    Card(
                        onClick = { onSelectOwnExam(exam) },
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("pick_exam_${exam.id}")
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp)
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(exam.title, style = MaterialTheme.typography.titleMedium)
                                Text(
                                    text = "${exam.subject} • ${exam.totalQuestions} ${strings.questionsShort}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.primaryContainer
                            ) {
                                Text(
                                    text = exam.examCode,
                                    style = MaterialTheme.typography.labelMedium.copy(
                                        fontFamily = JetBrainsMonoFontFamily,
                                        fontWeight = FontWeight.Bold
                                    ),
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SelectedExamBanner(
    exam: Exam,
    strings: AppStrings,
    onChangeExam: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.primaryContainer,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp)
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "${strings.loadedExamBanner}: ${exam.examCode}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Text(
                    text = "${exam.title} (${exam.totalQuestions} ${strings.questionsShort})",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
            TextButton(
                onClick = onChangeExam,
                modifier = Modifier.heightIn(min = 48.dp)
            ) {
                Text(strings.changeExam)
            }
        }
    }
}

@Composable
private fun VerifyAnswersHeaderCard(
    uiState: ScanUiState,
    exam: Exam,
    strings: AppStrings,
    onNameChange: (String) -> Unit,
    onRollChange: (String) -> Unit
) {
    val confidenceText = when (uiState.aiConfidence) {
        AiConfidence.HIGH -> strings.confidenceHigh
        AiConfidence.MEDIUM -> strings.confidenceMedium
        AiConfidence.LOW -> strings.confidenceLow
    }
    val hasAmbiguous = uiState.detectedAnswers.any { it == "NONE" || it == "MULTIPLE" }

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                uiState.capturedBitmap?.let { bmp ->
                    Image(
                        bitmap = bmp.asImageBitmap(),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(72.dp)
                            .clip(RoundedCornerShape(12.dp))
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = strings.verifyTitle,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = strings.verifySubtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(4.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = ScanEmerald.copy(alpha = 0.14f)
                        ) {
                            Text(
                                text = "${strings.aiConfidenceLabel}: $confidenceText",
                                style = MaterialTheme.typography.labelSmall,
                                color = ScanEmerald,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                        uiState.livePreviewGrade?.let { preview ->
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.primaryContainer
                            ) {
                                Text(
                                    text = "${formatScore(preview.score)}/${formatScore(preview.maxScore)}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }
                    }
                }
            }

            OutlinedTextField(
                value = uiState.studentName,
                onValueChange = onNameChange,
                label = { Text(strings.studentNameLabel) },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("verify_student_name_input")
            )

            OutlinedTextField(
                value = uiState.studentRoll,
                onValueChange = onRollChange,
                label = { Text(strings.studentRollLabel) },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("verify_student_roll_input")
            )

            if (hasAmbiguous || !uiState.aiNotes.isNullOrBlank()) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = WarmAmber.copy(alpha = 0.14f),
                    border = BorderStroke(1.dp, WarmAmber.copy(alpha = 0.35f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.WarningAmber,
                            contentDescription = null,
                            tint = WarmAmber,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = uiState.aiNotes ?: strings.flaggedQuestionsNote,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun VerifyQuestionRow(
    questionNumber: Int,
    selectedOption: String,
    labels: List<String>,
    strings: AppStrings,
    onSelectOption: (String) -> Unit
) {
    val isFlagged = selectedOption == "NONE" || selectedOption == "MULTIPLE"
    val borderColor = if (isFlagged) WarmAmber.copy(alpha = 0.6f) else MaterialTheme.colorScheme.outlineVariant
    val bgColor = if (isFlagged) WarmAmber.copy(alpha = 0.06f) else MaterialTheme.colorScheme.surface

    val normalizedTokens = listOf("A", "B", "C", "D")

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = bgColor,
        border = BorderStroke(1.dp, borderColor),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            Text(
                text = "Q$questionNumber",
                style = MaterialTheme.typography.titleSmall.copy(
                    fontFamily = JetBrainsMonoFontFamily,
                    fontWeight = FontWeight.Bold
                ),
                modifier = Modifier.width(44.dp)
            )

            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                normalizedTokens.forEachIndexed { idx, token ->
                    val isSelected = selectedOption == token
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(
                                if (isSelected) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.surfaceVariant
                            )
                            .clickable {
                                onSelectOption(if (isSelected) "NONE" else token)
                            }
                            .testTag("verify_q${questionNumber}_opt_$token")
                    ) {
                        Text(
                            text = labels.getOrElse(idx) { token },
                            style = MaterialTheme.typography.labelLarge,
                            color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            Surface(
                shape = RoundedCornerShape(8.dp),
                color = if (selectedOption == "MULTIPLE") WarmAmber.copy(alpha = 0.2f)
                else MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { onSelectOption("NONE") }
            ) {
                Text(
                    text = if (selectedOption == "MULTIPLE") strings.multipleOption else strings.blankOption,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (selectedOption == "NONE") MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
                )
            }
        }
    }
}
