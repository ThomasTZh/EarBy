package com.thomas.passiveeartraining.training.settings

import com.thomas.passiveeartraining.music.MidiNote
import com.thomas.passiveeartraining.training.InvalidScaleDefinitionException
import com.thomas.passiveeartraining.training.RecognitionRecipe
import com.thomas.passiveeartraining.training.ScaleDefinition
import com.thomas.passiveeartraining.training.provider.ProviderConfig
import com.thomas.passiveeartraining.training.provider.ProviderValidation
import com.thomas.passiveeartraining.training.provider.ScaleExerciseProvider
import com.thomas.passiveeartraining.training.recipe.DurationFinish
import com.thomas.passiveeartraining.training.recipe.RecipeDefinition
import com.thomas.passiveeartraining.training.recipe.RecipeTiming
import com.thomas.passiveeartraining.training.recipe.SessionStopPolicy
import kotlin.time.Duration as TimeDuration

/**
 * Validates a scale setup draft (M6).
 *
 * The UI and the domain use this one implementation, so a screen cannot accept a scale
 * configuration a session would refuse. Structural mistakes are attached to the control that
 * caused them; the provider is the authority on missing clips and complete-octave feasibility, and
 * the complete-duration check uses the same recipe the compiler runs.
 */
object ScaleSetupValidator {

    /** Validates a candidate; every failure is reported independently. */
    fun validate(
        draft: ScaleSetupDraft,
        voiceDurations: com.thomas.passiveeartraining.training.VoiceDurationCatalog =
            com.thomas.passiveeartraining.training.BundledVoiceDurations
    ): SetupValidation {
        val issues = mutableListOf<SetupIssue>()
        if (draft.scaleIds.isEmpty()) {
            issues += SetupIssue(
                SetupField.SCALES,
                "Select at least one scale."
            )
        }
        if (draft.minimumNote > draft.maximumNote) {
            issues += SetupIssue(
                SetupField.RANGE,
                "The lowest note (${draft.minimumNote}) must not be above the highest " +
                    "(${draft.maximumNote})."
            )
        }
        draft.fixedRoot?.let { value ->
            if (value !in MidiNote.MIN_VALUE..MidiNote.MAX_VALUE) {
                issues += SetupIssue(
                    SetupField.FIXED_NOTE,
                    "A fixed root must be between ${MidiNote.MIN_VALUE} and ${MidiNote.MAX_VALUE}."
                )
            } else {
                val note = MidiNote.of(value)
                if (value !in draft.minimumNote..draft.maximumNote) {
                    issues += SetupIssue(
                        SetupField.FIXED_NOTE,
                        "The fixed root ${note.displayName()} is outside the chosen range."
                    )
                } else if (draft.rootValues.isNotEmpty() &&
                    note.pitchClass.value !in draft.rootValues.toSet()
                ) {
                    issues += SetupIssue(
                        SetupField.FIXED_NOTE,
                        "The fixed root ${note.displayName()} must match one of the selected " +
                            "root note classes."
                    )
                }
            }
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
        if (draft.noteDuration <= TimeDuration.ZERO) {
            issues += SetupIssue(SetupField.NOTE_DURATION, "A note must last longer than zero.")
        }
        if (draft.answerGap.isNegative()) {
            issues += SetupIssue(
                SetupField.ANSWER_GAP,
                "The gap after the answer cannot be negative."
            )
        }
        if (draft.repeatGap.isNegative()) {
            issues += SetupIssue(SetupField.REPEAT_GAP, "The repeat gap cannot be negative.")
        }
        if (draft.melodicGap.isNegative()) {
            issues += SetupIssue(SetupField.MELODIC_GAP, "The scale gap cannot be negative.")
        }
        if (draft.finalSilence.isNegative()) {
            issues += SetupIssue(SetupField.FINAL_SILENCE, "The final silence cannot be negative.")
        }

        var definition: ScaleDefinition? = null
        if (issues.isEmpty()) {
            try {
                definition = draft.toSetup().toDefinition()
            } catch (failure: InvalidScaleDefinitionException) {
                issues += SetupIssue(
                    fieldOf(failure.field),
                    failure.message ?: "The scale selection is invalid."
                )
            } catch (failure: IllegalArgumentException) {
                issues += SetupIssue(
                    SetupField.RANGE,
                    failure.message ?: "The range is invalid."
                )
            }
        }

        var measuredWorstCase = TimeDuration.ZERO
        if (definition != null) {
            when (
                val validation = ScaleExerciseProvider().validate(
                    ProviderConfig.Scales(definition),
                    voiceDurations
                )
            ) {
                is ProviderValidation.Invalid -> validation.issues.forEach { issue ->
                    issues += SetupIssue(providerField(issue.field), issue.message)
                }
                is ProviderValidation.Valid -> {
                    val recipe = draft.toSetup().toRecipe()
                    // Re-time the content bound with the edited note/gap values: the cap check
                    // must judge the setup the user actually configured.
                    val example = ScaleExerciseProvider.maximumExampleDuration(
                        definition,
                        RecipeTiming(
                            noteDuration = draft.noteDuration,
                            melodicGap = draft.melodicGap
                        )
                    )
                    val answer = ScaleExerciseProvider.maximumAnswerDuration(
                        definition,
                        voiceDurations
                    )
                    val total = recipe.totalDurationOf(example, answer)
                    if (total > RecognitionRecipe.EXERCISE_CAP) {
                        issues += SetupIssue(
                            SetupField.DURATION,
                            "The longest scale exercise lasts ${total.inWholeMilliseconds} ms, " +
                                "above the ${RecognitionRecipe.EXERCISE_CAP.inWholeSeconds} s cap."
                        )
                    } else {
                        measuredWorstCase = total
                    }
                }
            }
        }

        validateStopPolicy(
            draft = draft,
            worstCaseItemDuration = measuredWorstCase,
            cycleSize = definition?.selectedScales?.size ?: 0,
            issues = issues
        )

        return if (issues.isEmpty()) SetupValidation.Valid else SetupValidation.Invalid(issues)
    }

    /**
     * The fixed lower tonics that can play every selected scale inside the range and roots.
     *
     * The picker offers only these, so a user cannot choose a fixed root the app would refuse.
     */
    fun feasibleFixedRoots(draft: ScaleSetupDraft): List<Int> {
        if (draft.fixedRoot == null) return emptyList()
        return (draft.minimumNote..draft.maximumNote).filter { candidate ->
            validate(draft.copy(fixedRoot = candidate)) is SetupValidation.Valid
        }
    }

    private fun validateStopPolicy(
        draft: ScaleSetupDraft,
        worstCaseItemDuration: TimeDuration,
        cycleSize: Int,
        issues: MutableList<SetupIssue>
    ) {
        val policy = draft.stopPolicy
        if (policy !is SessionStopPolicy.Duration) return
        if (policy.limit <= TimeDuration.ZERO) {
            issues += SetupIssue(SetupField.DURATION, "Choose a duration longer than zero.")
        }
        if (policy.limit > SetupValidator.MAX_DURATION) {
            issues += SetupIssue(
                SetupField.DURATION,
                "A sleep limit is at most ${SetupValidator.MAX_DURATION.inWholeMinutes} minutes."
            )
        }
        if (policy.finish != DurationFinish.IMMEDIATE && cycleSize > 0) {
            val worstCase = worstCaseItemDuration * cycleSize
            if (worstCase > SessionStopPolicy.MAX_DRAIN) {
                issues += SetupIssue(
                    SetupField.STOP_POLICY,
                    "Finishing the cycle could overrun the " +
                        "${SessionStopPolicy.MAX_DRAIN.inWholeMinutes}-minute bound."
                )
            }
        }
    }

    private fun fieldOf(hint: String?): SetupField = when (hint) {
        "scales" -> SetupField.SCALES
        "roots" -> SetupField.ROOTS
        "range" -> SetupField.RANGE
        "fixedRoot" -> SetupField.FIXED_NOTE
        else -> SetupField.SCALES
    }

    private fun providerField(hint: String): SetupField = when (hint) {
        "answers" -> SetupField.ANSWERS
        "range" -> SetupField.RANGE
        else -> SetupField.SCALES
    }
}
