package com.example.model

import kotlin.math.roundToInt

enum class OptionStyle(val rawValue: String) {
    ABCD("ABCD"),
    BN_KAKH("BN_KAKH");

    fun displayLabels(): List<String> = when (this) {
        ABCD -> listOf("A", "B", "C", "D")
        BN_KAKH -> listOf("ক", "খ", "গ", "ঘ")
    }

    fun formatOption(normalized: String): String {
        val index = when (normalized.uppercase()) {
            "A" -> 0
            "B" -> 1
            "C" -> 2
            "D" -> 3
            else -> -1
        }
        return if (index in 0..3) displayLabels()[index] else normalized
    }

    companion object {
        fun fromRaw(raw: String?): OptionStyle =
            entries.firstOrNull { it.rawValue == raw || it.name == raw } ?: ABCD
    }
}

enum class ExamStatus(val rawValue: String) {
    ACTIVE("active"),
    CLOSED("closed");

    companion object {
        fun fromRaw(raw: String?): ExamStatus =
            entries.firstOrNull { it.rawValue == raw } ?: ACTIVE
    }
}

enum class ScannedByRole(val rawValue: String) {
    STUDENT("student"),
    TEACHER("teacher");

    companion object {
        fun fromRaw(raw: String?): ScannedByRole =
            entries.firstOrNull { it.rawValue == raw } ?: STUDENT
    }
}

enum class AiConfidence(val rawValue: String) {
    HIGH("high"),
    MEDIUM("medium"),
    LOW("low");

    companion object {
        fun fromRaw(raw: String?): AiConfidence =
            entries.firstOrNull { it.rawValue == raw } ?: MEDIUM
    }
}

data class Exam(
    val id: String = "",
    val examCode: String = "",
    val teacherId: String = "",
    val teacherName: String = "",
    val title: String = "",
    val subject: String = "",
    val totalQuestions: Long = 10L,
    val optionStyle: String = OptionStyle.ABCD.rawValue,
    val marksPerCorrect: Double = 1.0,
    val negativeMark: Double = 0.0,
    val answerKey: List<String> = emptyList(),
    val status: String = ExamStatus.ACTIVE.rawValue,
    val createdAtMillis: Long = System.currentTimeMillis(),
    val updatedAtMillis: Long = System.currentTimeMillis()
) {
    val parsedOptionStyle: OptionStyle get() = OptionStyle.fromRaw(optionStyle)
    val parsedStatus: ExamStatus get() = ExamStatus.fromRaw(status)
    val questionCountInt: Int get() = totalQuestions.toInt().coerceIn(5, 100)
}

data class Submission(
    val id: String = "",
    val examId: String = "",
    val examCode: String = "",
    val examTitle: String = "",
    val subject: String = "",
    val teacherId: String = "",
    val submittedByUid: String = "",
    val scannedByRole: String = ScannedByRole.STUDENT.rawValue,
    val studentName: String = "",
    val studentRoll: String = "",
    val optionStyle: String = OptionStyle.ABCD.rawValue,
    val detectedAnswers: List<String> = emptyList(),
    val correctAnswers: List<String> = emptyList(),
    val correctCount: Long = 0L,
    val wrongCount: Long = 0L,
    val unansweredCount: Long = 0L,
    val score: Double = 0.0,
    val maxScore: Double = 10.0,
    val percentage: Double = 0.0,
    val aiConfidence: String = AiConfidence.MEDIUM.rawValue,
    val aiNotes: String? = null,
    val submittedAtMillis: Long = System.currentTimeMillis()
) {
    val parsedOptionStyle: OptionStyle get() = OptionStyle.fromRaw(optionStyle)
    val parsedRole: ScannedByRole get() = ScannedByRole.fromRaw(scannedByRole)
    val parsedConfidence: AiConfidence get() = AiConfidence.fromRaw(aiConfidence)
}

enum class QuestionOutcome {
    CORRECT,
    WRONG,
    UNANSWERED
}

data class QuestionResult(
    val questionNumber: Int,
    val detectedOption: String,
    val correctOption: String,
    val outcome: QuestionOutcome
)

data class GradedSummary(
    val correctCount: Int,
    val wrongCount: Int,
    val unansweredCount: Int,
    val score: Double,
    val maxScore: Double,
    val percentage: Double,
    val breakdown: List<QuestionResult>
)

fun normalizeOptionToken(raw: String): String {
    return when (raw.trim().uppercase()) {
        "A", "ক", "1" -> "A"
        "B", "খ", "2" -> "B"
        "C", "গ", "3" -> "C"
        "D", "ঘ", "4" -> "D"
        "MULTIPLE", "MULTI" -> "MULTIPLE"
        else -> "NONE"
    }
}

fun gradeOmrSheet(
    answerKey: List<String>,
    detectedAnswers: List<String>,
    marksPerCorrect: Double,
    negativeMark: Double
): GradedSummary {
    var correct = 0
    var wrong = 0
    var unanswered = 0

    val breakdown = answerKey.mapIndexed { index, rawKey ->
        val key = normalizeOptionToken(rawKey).let { if (it in listOf("A", "B", "C", "D")) it else "A" }
        val detected = detectedAnswers.getOrNull(index)?.let(::normalizeOptionToken) ?: "NONE"
        val outcome = when {
            detected == "NONE" -> {
                unanswered++
                QuestionOutcome.UNANSWERED
            }
            detected == key -> {
                correct++
                QuestionOutcome.CORRECT
            }
            else -> {
                wrong++
                QuestionOutcome.WRONG
            }
        }
        QuestionResult(
            questionNumber = index + 1,
            detectedOption = detected,
            correctOption = key,
            outcome = outcome
        )
    }

    val maxScore = (answerKey.size * marksPerCorrect).coerceAtLeast(0.25)
    val rawScore = (correct * marksPerCorrect) - (wrong * negativeMark)
    val roundedScore = (rawScore * 100.0).roundToInt() / 100.0
    val pct = ((rawScore / maxScore) * 100.0).coerceIn(0.0, 100.0)
    val roundedPct = (pct * 10.0).roundToInt() / 10.0

    return GradedSummary(
        correctCount = correct,
        wrongCount = wrong,
        unansweredCount = unanswered,
        score = roundedScore,
        maxScore = (maxScore * 100.0).roundToInt() / 100.0,
        percentage = roundedPct,
        breakdown = breakdown
    )
}
