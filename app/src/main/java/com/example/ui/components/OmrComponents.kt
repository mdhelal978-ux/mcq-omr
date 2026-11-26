package com.example.ui.components

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.graphics.Matrix
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.RemoveCircleOutline
import androidx.compose.material.icons.outlined.Translate
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.i18n.AppLanguage
import com.example.i18n.AppStrings
import com.example.model.OptionStyle
import com.example.model.QuestionOutcome
import com.example.model.QuestionResult
import com.example.model.Submission
import com.example.model.gradeOmrSheet
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.ScanEmerald
import com.example.ui.theme.WarmAmber
import com.example.ui.theme.WrongCrimson
import java.text.SimpleDateFormat
import java.util.Locale

@Composable
fun LanguageToggleChip(
    language: AppLanguage,
    strings: AppStrings,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onToggle,
        shape = RoundedCornerShape(50),
        color = MaterialTheme.colorScheme.surfaceVariant,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = modifier
            .heightIn(min = 40.dp)
            .testTag("language_toggle_button")
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
        ) {
            Icon(
                imageVector = Icons.Outlined.Translate,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(16.dp)
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = strings.languageToggleLabel,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
fun ScoreRingCanvas(
    percentage: Double,
    scoreText: String,
    maxScoreText: String,
    modifier: Modifier = Modifier
) {
    var targetProgress by remember { mutableStateOf(0f) }
    LaunchedEffect(percentage) {
        targetProgress = (percentage / 100.0).toFloat().coerceIn(0f, 1f)
    }
    val animatedProgress by animateFloatAsState(
        targetValue = targetProgress,
        animationSpec = tween(durationMillis = 750),
        label = "score_ring"
    )
    val ringColor = when {
        percentage >= 60.0 -> ScanEmerald
        percentage >= 40.0 -> WarmAmber
        else -> WrongCrimson
    }
    val trackColor = MaterialTheme.colorScheme.surfaceVariant

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier.size(148.dp)
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val strokeWidth = 14.dp.toPx()
            val inset = strokeWidth / 2f
            val arcSize = Size(size.width - strokeWidth, size.height - strokeWidth)
            val topLeft = Offset(inset, inset)

            drawArc(
                color = trackColor,
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
            )
            drawArc(
                color = ringColor,
                startAngle = -90f,
                sweepAngle = 360f * animatedProgress,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
            )
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "${percentage.toInt()}%",
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontFamily = JetBrainsMonoFontFamily,
                    fontWeight = FontWeight.Bold
                ),
                color = ringColor
            )
            Text(
                text = "$scoreText / $maxScoreText",
                style = MaterialTheme.typography.labelMedium.copy(
                    fontFamily = JetBrainsMonoFontFamily
                ),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun SubmissionResultCard(
    submission: Submission,
    strings: AppStrings,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val pctColor = when {
        submission.percentage >= 60.0 -> ScanEmerald
        submission.percentage >= 40.0 -> WarmAmber
        else -> WrongCrimson
    }
    val dateLabel = remember(submission.submittedAt) {
        val date = submission.submittedAt?.toDate()
        if (date != null) SimpleDateFormat("dd MMM, hh:mm a", Locale.getDefault()).format(date) else ""
    }

    Card(
        onClick = onClick,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = modifier
            .fillMaxWidth()
            .testTag("submission_card_${submission.id}")
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(pctColor.copy(alpha = 0.14f))
                    .border(2.dp, pctColor, CircleShape)
            ) {
                Text(
                    text = "${submission.percentage.toInt()}%",
                    style = MaterialTheme.typography.titleSmall.copy(
                        fontFamily = JetBrainsMonoFontFamily,
                        fontWeight = FontWeight.Bold
                    ),
                    color = pctColor
                )
            }

            Spacer(Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = submission.examTitle,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${submission.studentName} • Roll ${submission.studentRoll}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(4.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "${formatScore(submission.score)} / ${formatScore(submission.maxScore)}",
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontFamily = JetBrainsMonoFontFamily,
                            fontWeight = FontWeight.Bold
                        ),
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "• ✓${submission.correctCount} ✕${submission.wrongCount}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (dateLabel.isNotBlank()) {
                        Text(
                            text = "• $dateLabel",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun InstantResultContent(
    submission: Submission,
    strings: AppStrings,
    onScanAnother: (() -> Unit)? = null,
    onDone: () -> Unit,
    onDelete: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val style = submission.parsedOptionStyle
    val graded = remember(submission) {
        gradeOmrSheet(
            answerKey = submission.correctAnswers,
            detectedAnswers = submission.detectedAnswers,
            marksPerCorrect = 1.0,
            negativeMark = 0.0
        )
    }
    val passed = submission.percentage >= 40.0

    Column(
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = modifier.fillMaxWidth()
    ) {
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(50),
                    color = if (passed) ScanEmerald.copy(alpha = 0.14f) else WrongCrimson.copy(alpha = 0.14f)
                ) {
                    Text(
                        text = if (passed) strings.passedBadge else strings.needsReviewBadge,
                        style = MaterialTheme.typography.labelMedium,
                        color = if (passed) ScanEmerald else WrongCrimson,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                    )
                }

                Spacer(Modifier.height(14.dp))

                ScoreRingCanvas(
                    percentage = submission.percentage,
                    scoreText = formatScore(submission.score),
                    maxScoreText = formatScore(submission.maxScore)
                )

                Spacer(Modifier.height(14.dp))

                Text(
                    text = submission.examTitle,
                    style = MaterialTheme.typography.titleLarge
                )
                Text(
                    text = "${submission.studentName} • Roll: ${submission.studentRoll} • ${submission.examCode}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(Modifier.height(16.dp))

                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    StatCounterPill(
                        label = strings.correctLabel,
                        value = submission.correctCount.toString(),
                        color = ScanEmerald,
                        modifier = Modifier.weight(1f)
                    )
                    StatCounterPill(
                        label = strings.wrongLabel,
                        value = submission.wrongCount.toString(),
                        color = WrongCrimson,
                        modifier = Modifier.weight(1f)
                    )
                    StatCounterPill(
                        label = strings.unansweredLabel,
                        value = submission.unansweredCount.toString(),
                        color = WarmAmber,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        if (!submission.aiNotes.isNullOrBlank()) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.tertiaryContainer
            ) {
                Text(
                    text = submission.aiNotes,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                    modifier = Modifier.padding(12.dp)
                )
            }
        }

        Text(
            text = strings.questionBreakdownTitle,
            style = MaterialTheme.typography.titleMedium
        )

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            graded.breakdown.forEach { qResult ->
                QuestionBreakdownRow(
                    result = qResult,
                    style = style,
                    strings = strings
                )
            }
        }

        Spacer(Modifier.height(4.dp))

        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            if (onScanAnother != null) {
                OutlinedButton(
                    onClick = onScanAnother,
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp)
                        .testTag("scan_another_button")
                ) {
                    Text(strings.scanAnotherSheet)
                }
            }
            Button(
                onClick = onDone,
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 48.dp)
                    .testTag("result_done_button")
            ) {
                Text(strings.doneButton)
            }
        }

        if (onDelete != null) {
            TextButton(
                onClick = onDelete,
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .heightIn(min = 48.dp)
                    .testTag("delete_submission_button")
            ) {
                Icon(Icons.Default.DeleteOutline, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(strings.deleteResult)
            }
        }
    }
}

@Composable
private fun StatCounterPill(
    label: String,
    value: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = color.copy(alpha = 0.11f),
        border = BorderStroke(1.dp, color.copy(alpha = 0.25f)),
        modifier = modifier
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(vertical = 10.dp, horizontal = 8.dp)
        ) {
            Text(
                text = value,
                style = MaterialTheme.typography.titleLarge.copy(
                    fontFamily = JetBrainsMonoFontFamily,
                    fontWeight = FontWeight.Bold
                ),
                color = color
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = color
            )
        }
    }
}

@Composable
private fun QuestionBreakdownRow(
    result: QuestionResult,
    style: OptionStyle,
    strings: AppStrings
) {
    val (bgColor, iconColor, icon) = when (result.outcome) {
        QuestionOutcome.CORRECT -> Triple(
            ScanEmerald.copy(alpha = 0.08f),
            ScanEmerald,
            Icons.Default.CheckCircle
        )
        QuestionOutcome.WRONG -> Triple(
            WrongCrimson.copy(alpha = 0.08f),
            WrongCrimson,
            Icons.Default.Close
        )
        QuestionOutcome.UNANSWERED -> Triple(
            WarmAmber.copy(alpha = 0.09f),
            WarmAmber,
            Icons.Default.RemoveCircleOutline
        )
    }

    val detectedLabel = when (result.detectedOption) {
        "NONE" -> strings.blankOption
        "MULTIPLE" -> strings.multipleOption
        else -> style.formatOption(result.detectedOption)
    }
    val correctLabel = style.formatOption(result.correctOption)

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = bgColor,
        border = BorderStroke(1.dp, iconColor.copy(alpha = 0.22f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp)
        ) {
            Text(
                text = "Q${result.questionNumber}",
                style = MaterialTheme.typography.titleSmall.copy(
                    fontFamily = JetBrainsMonoFontFamily,
                    fontWeight = FontWeight.Bold
                ),
                modifier = Modifier.width(46.dp)
            )
            Text(
                text = "${strings.yourAnswerLabel}: $detectedLabel",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = "${strings.correctKeyLabel}: $correctLabel",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.width(10.dp))
            Icon(
                imageVector = icon,
                contentDescription = result.outcome.name,
                tint = iconColor,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
fun CameraPreviewScanner(
    strings: AppStrings,
    onBitmapCaptured: (Bitmap) -> Unit,
    onPickGallery: () -> Unit,
    onError: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val mainExecutor = remember(context) { ContextCompat.getMainExecutor(context) }

    var flashEnabled by remember { mutableStateOf(false) }
    var cameraRef by remember { mutableStateOf<Camera?>(null) }
    var imageCapture by remember { mutableStateOf<ImageCapture?>(null) }
    val previewView = remember { PreviewView(context) }

    DisposableEffect(lifecycleOwner) {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        val listener = Runnable {
            try {
                val provider = cameraProviderFuture.get()
                val preview = Preview.Builder().build().also {
                    it.surfaceProvider = previewView.surfaceProvider
                }
                val capture = ImageCapture.Builder()
                    .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
                    .build()
                imageCapture = capture
                provider.unbindAll()
                cameraRef = provider.bindToLifecycle(
                    lifecycleOwner,
                    CameraSelector.DEFAULT_BACK_CAMERA,
                    preview,
                    capture
                )
            } catch (e: Exception) {
                Log.e("CameraScanner", "Failed to bind CameraX", e)
                onError("Camera unavailable on this device. Please upload from Gallery.")
            }
        }
        cameraProviderFuture.addListener(listener, mainExecutor)
        onDispose {
            try {
                cameraProviderFuture.get().unbindAll()
            } catch (_: Exception) {
            }
        }
    }

    LaunchedEffect(flashEnabled, cameraRef) {
        cameraRef?.cameraControl?.enableTorch(flashEnabled)
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(440.dp)
            .clip(RoundedCornerShape(22.dp))
            .background(Color.Black)
    ) {
        AndroidView(
            factory = { previewView },
            modifier = Modifier.fillMaxSize()
        )

        // Optical OMR alignment guide overlay
        Canvas(modifier = Modifier.fillMaxSize()) {
            val padX = size.width * 0.1f
            val padY = size.height * 0.12f
            val boxW = size.width - padX * 2f
            val boxH = size.height - padY * 2f
            drawRoundRect(
                color = Color(0xFF34D399).copy(alpha = 0.85f),
                topLeft = Offset(padX, padY),
                size = Size(boxW, boxH),
                cornerRadius = CornerRadius(18.dp.toPx(), 18.dp.toPx()),
                style = Stroke(width = 2.5.dp.toPx())
            )
        }

        // Top hint & Flash button
        Row(
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter)
                .padding(12.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(50),
                color = Color.Black.copy(alpha = 0.6f),
                modifier = Modifier.weight(1f, fill = false)
            ) {
                Text(
                    text = strings.alignSheetHint,
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                )
            }
            Spacer(Modifier.width(8.dp))
            IconButton(
                onClick = { flashEnabled = !flashEnabled },
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.55f))
                    .testTag("camera_flash_button")
            ) {
                Icon(
                    imageVector = if (flashEnabled) Icons.Default.FlashOn else Icons.Default.FlashOff,
                    contentDescription = strings.flashToggleDesc,
                    tint = Color.White
                )
            }
        }

        // Bottom Capture & Gallery controls
        Row(
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .background(Color.Black.copy(alpha = 0.45f))
                .padding(vertical = 14.dp, horizontal = 16.dp)
        ) {
            IconButton(
                onClick = onPickGallery,
                modifier = Modifier
                    .size(52.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.2f))
                    .testTag("camera_gallery_button")
            ) {
                Icon(
                    imageVector = Icons.Default.PhotoLibrary,
                    contentDescription = strings.galleryUploadButton,
                    tint = Color.White
                )
            }

            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(70.dp)
                    .clip(CircleShape)
                    .border(3.dp, Color.White, CircleShape)
                    .padding(5.dp)
                    .clip(CircleShape)
                    .background(ScanEmerald)
                    .clickable {
                        val capture = imageCapture
                        if (capture == null) {
                            onError("Camera is still initializing.")
                            return@clickable
                        }
                        capture.takePicture(
                            mainExecutor,
                            object : ImageCapture.OnImageCapturedCallback() {
                                override fun onCaptureSuccess(image: ImageProxy) {
                                    try {
                                        val bmp = imageProxyToBitmap(image)
                                        image.close()
                                        onBitmapCaptured(bmp)
                                    } catch (e: Exception) {
                                        image.close()
                                        onError(e.localizedMessage ?: "Failed to read captured image.")
                                    }
                                }

                                override fun onError(exception: ImageCaptureException) {
                                    onError(exception.localizedMessage ?: "Capture failed.")
                                }
                            }
                        )
                    }
                    .testTag("camera_shutter_button")
            ) {
                Icon(
                    imageVector = Icons.Default.CameraAlt,
                    contentDescription = strings.captureShutterDesc,
                    tint = Color.White,
                    modifier = Modifier.size(28.dp)
                )
            }

            Spacer(Modifier.size(52.dp))
        }
    }
}

private fun imageProxyToBitmap(image: ImageProxy): Bitmap {
    val buffer = image.planes[0].buffer
    val bytes = ByteArray(buffer.remaining())
    buffer.get(bytes)
    val rawBitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        ?: throw IllegalStateException("Unable to decode camera photo.")
    val rotation = image.imageInfo.rotationDegrees
    if (rotation == 0) return rawBitmap
    val matrix = Matrix().apply { postRotate(rotation.toFloat()) }
    return Bitmap.createBitmap(rawBitmap, 0, 0, rawBitmap.width, rawBitmap.height, matrix, true)
}

fun decodeBitmapFromUri(context: Context, uri: Uri): Bitmap {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        val source = ImageDecoder.createSource(context.contentResolver, uri)
        ImageDecoder.decodeBitmap(source) { decoder, _, _ ->
            decoder.isMutableRequired = true
        }
    } else {
        @Suppress("DEPRECATION")
        android.provider.MediaStore.Images.Media.getBitmap(context.contentResolver, uri)
    }
}

fun formatScore(value: Double): String {
    return if (value % 1.0 == 0.0) {
        value.toInt().toString()
    } else {
        String.format(Locale.US, "%.2f", value).trimEnd('0').trimEnd('.')
    }
}
