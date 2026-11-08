package com.thomas.passiveeartraining.training.settings

import com.thomas.passiveeartraining.music.MidiNote
import com.thomas.passiveeartraining.training.IntervalWalkDefinition
import com.thomas.passiveeartraining.training.WalkDirection
import com.thomas.passiveeartraining.training.provider.IntervalWalkExerciseProvider
import com.thomas.passiveeartraining.training.provider.ModeSetup
import com.thomas.passiveeartraining.training.provider.ProviderConfig
import com.thomas.passiveeartraining.training.provider.ProviderValidation
import com.thomas.passiveeartraining.training.recipe.RecipeDefinition
import com.thomas.passiveeartraining.training.recipe.RecipeMode
import com.thomas.passiveeartraining.training.recipe.RecipeTiming
import com.thomas.passiveeartraining.training.recipe.SessionStopPolicy
import com.thomas.passiveeartraining.training.session.StartRequest
import kotlin.time.Duration

/** The persisted musical and timing configuration of an interval-walk session. */
data class IntervalWalkSetup(
    val intervalIds: List<String> = IntervalWalkDefinition.canonicalIntervalIds,
    val rootValues: List<Int> = emptyList(),
    val minimumNote: MidiNote = MidiNote.of(60),
    val maximumNote: MidiNote = MidiNote.of(72),
    val fixedStart: MidiNote? = null,
    val direction: WalkDirection = WalkDirection.ASCENDING,
    val length: Int = IntervalWalkDefinition.DEFAULT_LENGTH,
    val mode: RecipeMode = RecipeMode.RECOGNITION,
    val thinkingSilence: Duration = Duration.parse("3s"),
    val replayCount: Int = 0,
    val stopPolicy: SessionStopPolicy = SessionStopPolicy.UntilStopped,
    val noteDuration: Duration = Duration.parse("700ms"),
    val answerGap: Duration = Duration.parse("600ms"),
    val repeatGap: Duration = Duration.parse("400ms"),
    val melodicGap: Duration = Duration.parse("100ms"),
    val finalSilence: Duration = Duration.parse("2s"),
    val seed: Long? = null,
    val separatorCue: Boolean = false
) {
    init {
        require(minimumNote.value <= maximumNote.value) {
            "A setup range must not be inverted: ${minimumNote.value}..${maximumNote.value}"
        }
        require(noteDuration > Duration.ZERO) { "A note duration must be positive" }
        require(answerGap >= Duration.ZERO) { "An answer gap cannot be negative" }
        require(repeatGap >= Duration.ZERO) { "A repeat gap cannot be negative" }
        require(melodicGap >= Duration.ZERO) { "A melodic gap cannot be negative" }
        require(finalSilence >= Duration.ZERO) { "A final silence cannot be negative" }
    }

    fun toDefinition(): IntervalWalkDefinition = IntervalWalkDefinition.of(
        intervalIds = intervalIds,
        rootValues = rootValues,
        minimumNote = minimumNote,
        maximumNote = maximumNote,
        fixedStart = fixedStart,
        direction = direction,
        length = length
    )

    fun toRecipe(): RecipeDefinition = RecipeDefinition(
        mode = mode,
        timing = RecipeTiming(
            noteDuration = noteDuration,
            melodicGap = melodicGap,
            thinkingSilence = thinkingSilence,
            postAnswerSilence = finalSilence,
            answerGap = answerGap,
            repeatGap = repeatGap
        ),
        replayCount = replayCount,
        separatorCue = separatorCue
    )

    fun toStartRequest(
        seed: Long?,
        musicGain: Float = UserSettings.DEFAULT_MUSIC_GAIN,
        voiceGain: Float = UserSettings.DEFAULT_VOICE_GAIN
    ): StartRequest = StartRequest(
        setup = ModeSetup(
            provider = ProviderConfig.IntervalWalks(toDefinition()),
            recipe = toRecipe(),
            seed = seed,
            stopPolicy = stopPolicy
        ),
        musicGain = musicGain,
        voiceGain = voiceGain
    )

    companion object {
        fun default(): IntervalWalkSetup {
            val definition = IntervalWalkDefinition.default()
            return IntervalWalkSetup(
                intervalIds = definition.intervalIds,
                rootValues = definition.rootValues,
                minimumNote = definition.minimumNote,
                maximumNote = definition.maximumNote,
                fixedStart = definition.fixedStart,
                direction = definition.direction,
                length = definition.length
            )
        }
    }
}

/** The unvalidated form the setup screen edits. */
data class IntervalWalkSetupDraft(
    val intervalIds: List<String>,
    val rootValues: List<Int>,
    val minimumNote: MidiNote,
    val maximumNote: MidiNote,
    val fixedStart: MidiNote?,
    val direction: WalkDirection,
    val length: Int,
    val mode: RecipeMode,
    val thinkingSilence: Duration,
    val replayCount: Int,
    val stopPolicy: SessionStopPolicy = SessionStopPolicy.UntilStopped,
    val noteDuration: Duration = Duration.parse("700ms"),
    val answerGap: Duration = Duration.parse("600ms"),
    val repeatGap: Duration = Duration.parse("400ms"),
    val melodicGap: Duration = Duration.parse("100ms"),
    val finalSilence: Duration = Duration.parse("2s"),
    val seed: Long? = null,
    val separatorCue: Boolean = false
) {
    fun toSetup(): IntervalWalkSetup = IntervalWalkSetup(
        intervalIds = intervalIds,
        rootValues = rootValues,
        minimumNote = minimumNote,
        maximumNote = maximumNote,
        fixedStart = fixedStart,
        direction = direction,
        length = length,
        mode = mode,
        thinkingSilence = thinkingSilence,
        replayCount = replayCount,
        stopPolicy = stopPolicy,
        noteDuration = noteDuration,
        answerGap = answerGap,
        repeatGap = repeatGap,
        melodicGap = melodicGap,
        finalSilence = finalSilence,
        seed = seed,
        separatorCue = separatorCue
    )

    companion object {
        fun from(setup: IntervalWalkSetup): IntervalWalkSetupDraft = IntervalWalkSetupDraft(
            intervalIds = setup.intervalIds,
            rootValues = setup.rootValues,
            minimumNote = setup.minimumNote,
            maximumNote = setup.maximumNote,
            fixedStart = setup.fixedStart,
            direction = setup.direction,
            length = setup.length,
            mode = setup.mode,
            thinkingSilence = setup.thinkingSilence,
            replayCount = setup.replayCount,
            stopPolicy = setup.stopPolicy,
            noteDuration = setup.noteDuration,
            answerGap = setup.answerGap,
            repeatGap = setup.repeatGap,
            melodicGap = setup.melodicGap,
            finalSilence = setup.finalSilence,
            seed = setup.seed,
            separatorCue = setup.separatorCue
        )
    }
}

/** Validates an interval-walk draft against the shared recipe and provider rules. */
object IntervalWalkSetupValidator {

    fun validate(
        draft: IntervalWalkSetupDraft,
        voiceDurations: com.thomas.passiveeartraining.training.VoiceDurationCatalog =
            com.thomas.passiveeartraining.training.BundledVoiceDurations
    ): SetupValidation {
        val issues = mutableListOf<SetupIssue>()
        if (draft.intervalIds.isEmpty()) {
            issues += SetupIssue(SetupField.INTERVALS, "Select at least one interval.")
        }
        if (draft.length !in IntervalWalkDefinition.MIN_LENGTH..IntervalWalkDefinition.MAX_LENGTH) {
            issues += SetupIssue(
                SetupField.LENGTH,
                "A walk must contain ${IntervalWalkDefinition.MIN_LENGTH} to " +
                    "${IntervalWalkDefinition.MAX_LENGTH} interval steps."
            )
        }
        if (draft.minimumNote.value > draft.maximumNote.value) {
            issues += SetupIssue(
                SetupField.RANGE,
                "The lowest starting root (${draft.minimumNote.value}) must not be above the " +
                    "highest (${draft.maximumNote.value})."
            )
        }
        listOf(
            SetupField.NOTE_DURATION to draft.noteDuration,
            SetupField.THINKING_SILENCE to draft.thinkingSilence,
            SetupField.MELODIC_GAP to draft.melodicGap,
            SetupField.ANSWER_GAP to draft.answerGap,
            SetupField.REPEAT_GAP to draft.repeatGap,
            SetupField.FINAL_SILENCE to draft.finalSilence
        ).forEach { (field, duration) ->
            if (!duration.isFinite() || duration.inWholeNanoseconds % 1000L != 0L) {
                issues += SetupIssue(field, "Choose a finite duration in whole microseconds.")
            }
        }
        if (draft.replayCount !in 0..RecipeDefinition.MAX_REPLAY) {
            issues += SetupIssue(
                SetupField.REPLAY,
                "Repeats must be between 0 and ${RecipeDefinition.MAX_REPLAY}."
            )
        }
        if (draft.thinkingSilence.isNegative()) {
            issues += SetupIssue(SetupField.THINKING_SILENCE, "Thinking time cannot be negative.")
        }
        if (draft.noteDuration <= Duration.ZERO) {
            issues += SetupIssue(SetupField.NOTE_DURATION, "A note must last longer than zero.")
        }
        if (draft.answerGap.isNegative()) {
            issues +=
                SetupIssue(SetupField.ANSWER_GAP, "The gap after the answer cannot be negative.")
        }
        if (draft.repeatGap.isNegative()) {
            issues += SetupIssue(SetupField.REPEAT_GAP, "The repeat gap cannot be negative.")
        }
        if (draft.melodicGap.isNegative()) {
            issues += SetupIssue(SetupField.MELODIC_GAP, "The between-note gap cannot be negative.")
        }
        if (draft.finalSilence.isNegative()) {
            issues += SetupIssue(SetupField.FINAL_SILENCE, "The final silence cannot be negative.")
        }

        var definition: IntervalWalkDefinition? = null
        if (issues.isEmpty()) {
            try {
                definition = draft.toSetup().toDefinition()
            } catch (failure: IllegalArgumentException) {
                issues += SetupIssue(
                    SetupField.RANGE,
                    failure.message ?: "The interval-walk selection is invalid."
                )
            }
        }

        if (definition != null) {
            when (
                val validation = IntervalWalkExerciseProvider().validate(
                    ProviderConfig.IntervalWalks(definition),
                    voiceDurations
                )
            ) {
                is ProviderValidation.Invalid -> validation.issues.forEach { issue ->
                    issues += SetupIssue(providerField(issue.field), issue.message)
                }
                is ProviderValidation.Valid -> {
                    val recipe = draft.toSetup().toRecipe()
                    val example = IntervalWalkExerciseProvider.maximumExampleDuration(
                        definition,
                        RecipeTiming(
                            noteDuration = draft.noteDuration,
                            melodicGap = draft.melodicGap
                        )
                    )
                    val total = recipe.totalDurationOf(example, validation.maximumAnswerDuration)
                    if (total >
                        com.thomas.passiveeartraining.training.RecognitionRecipe.EXERCISE_CAP
                    ) {
                        issues += SetupIssue(
                            SetupField.DURATION,
                            "The longest walk lasts ${total.inWholeMilliseconds} ms, above the " +
                                "${com.thomas.passiveeartraining.training.RecognitionRecipe.EXERCISE_CAP.inWholeSeconds} s cap."
                        )
                    }
                }
            }
            IntervalWalkExerciseProvider.stopPolicyIssue(draft.stopPolicy)?.let { issue ->
                issues += SetupIssue(SetupField.STOP_POLICY, issue.message)
            }
        }

        return if (issues.isEmpty()) SetupValidation.Valid else SetupValidation.Invalid(issues)
    }

    private fun providerField(hint: String): SetupField = when (hint) {
        "range" -> SetupField.RANGE
        "answers" -> SetupField.ANSWERS
        "stopPolicy" -> SetupField.STOP_POLICY
        else -> SetupField.INTERVALS
    }
}
