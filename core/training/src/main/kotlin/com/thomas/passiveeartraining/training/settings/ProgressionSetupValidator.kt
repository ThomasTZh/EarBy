package com.thomas.passiveeartraining.training.settings

import com.thomas.passiveeartraining.training.InvalidProgressionDefinitionException
import com.thomas.passiveeartraining.training.ProgressionDefinition
import com.thomas.passiveeartraining.training.RecognitionRecipe
import com.thomas.passiveeartraining.training.provider.ProgressionExerciseProvider
import com.thomas.passiveeartraining.training.provider.ProviderConfig
import com.thomas.passiveeartraining.training.provider.ProviderValidation
import com.thomas.passiveeartraining.training.recipe.DurationFinish
import com.thomas.passiveeartraining.training.recipe.RecipeDefinition
import com.thomas.passiveeartraining.training.recipe.RecipeTiming
import com.thomas.passiveeartraining.training.recipe.SessionStopPolicy
import kotlin.time.Duration as TimeDuration

/**
 * Validates a chord-progression setup draft (M9A.3).
 *
 * The UI and the domain use this one implementation, so a screen cannot accept a progression
 * configuration a session would refuse. Structural mistakes are attached to the control that
 * caused them; the provider is the authority on missing clips and on the finite realization of
 * every selected key/template pair, and the complete-duration check uses the same recipe the
 * compiler runs. Progression answers never speak note names.
 */
object ProgressionSetupValidator {

    /** Validates a candidate; every failure is reported independently. */
    fun validate(
        draft: ProgressionSetupDraft,
        voiceDurations: com.thomas.passiveeartraining.training.VoiceDurationCatalog =
            com.thomas.passiveeartraining.training.BundledVoiceDurations
    ): SetupValidation {
        val issues = mutableListOf<SetupIssue>()
        if (draft.chordIds.isEmpty()) {
            issues += SetupIssue(
                SetupField.CHORDS,
                "Select at least one chord."
            )
        }
        if (draft.fixedRoot != null && draft.fixedRoot !in draft.minimumNote..draft.maximumNote) {
            issues += SetupIssue(
                SetupField.FIXED_NOTE,
                "The fixed root must be inside the selected range."
            )
        }
        if (draft.allowedInversions.isEmpty()) {
            issues += SetupIssue(
                SetupField.INVERSIONS,
                "Select at least one inversion."
            )
        } else if (draft.allowedInversions.any { it !in 0..3 }) {
            issues += SetupIssue(
                SetupField.INVERSIONS,
                "Allowed inversions are 0, 1, 2 and 3."
            )
        }
        if (draft.minimumNote > draft.maximumNote) {
            issues += SetupIssue(
                SetupField.RANGE,
                "The lowest note (${draft.minimumNote}) must not be above the highest " +
                    "(${draft.maximumNote})."
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
        if (draft.stopPolicy == SessionStopPolicy.AfterCycle) {
            issues += SetupIssue(
                SetupField.STOP_POLICY,
                "Chord progressions have no fixed cycle, so they cannot stop after one cycle."
            )
        }
        if (draft.thinkingSilence.isNegative()) {
            issues += SetupIssue(SetupField.THINKING_SILENCE, "Thinking time cannot be negative.")
        }
        if (draft.noteDuration <= TimeDuration.ZERO) {
            issues += SetupIssue(SetupField.NOTE_DURATION, "A chord must last longer than zero.")
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
            issues += SetupIssue(SetupField.MELODIC_GAP, "The inter-chord gap cannot be negative.")
        }
        if (draft.finalSilence.isNegative()) {
            issues += SetupIssue(SetupField.FINAL_SILENCE, "The final silence cannot be negative.")
        }

        var definition: ProgressionDefinition? = null
        if (issues.isEmpty()) {
            try {
                definition = draft.toSetup().toDefinition()
            } catch (failure: InvalidProgressionDefinitionException) {
                issues += SetupIssue(
                    fieldOf(failure.field),
                    failure.message ?: "The progression selection is invalid."
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
                val validation = ProgressionExerciseProvider().validate(
                    ProviderConfig.Progressions(definition),
                    voiceDurations
                )
            ) {
                is ProviderValidation.Invalid -> validation.issues.forEach { issue ->
                    issues += SetupIssue(providerField(issue.field), issue.message)
                }
                is ProviderValidation.Valid -> {
                    val recipe = draft.toSetup().toRecipe()
                    // Re-time the content bound with the edited chord/gap values: the cap check
                    // must judge the setup the user actually configured. The inter-chord gap
                    // travels in the timing exactly as it does in toRecipe().
                    val example = ProgressionExerciseProvider.maximumExampleDuration(
                        definition,
                        RecipeTiming(
                            noteDuration = draft.noteDuration,
                            melodicGap = draft.melodicGap
                        )
                    )
                    val answer = ProgressionExerciseProvider.maximumAnswerDuration(
                        definition,
                        voiceDurations
                    )
                    val total = recipe.totalDurationOf(example, answer)
                    if (total > RecognitionRecipe.EXERCISE_CAP) {
                        issues += SetupIssue(
                            SetupField.DURATION,
                            "The longest chord-progression exercise lasts " +
                                "${total.inWholeMilliseconds} ms, above the " +
                                "${RecognitionRecipe.EXERCISE_CAP.inWholeSeconds} s cap."
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
            cycleSize = 0,
            issues = issues
        )

        return if (issues.isEmpty()) SetupValidation.Valid else SetupValidation.Invalid(issues)
    }

    private fun validateStopPolicy(
        draft: ProgressionSetupDraft,
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
        "keys" -> SetupField.KEYS
        "chords" -> SetupField.CHORDS
        "voicing" -> SetupField.VOICING
        "inversions" -> SetupField.INVERSIONS
        "range" -> SetupField.RANGE
        "fixedRoot" -> SetupField.FIXED_NOTE
        else -> SetupField.CHORDS
    }

    private fun providerField(hint: String): SetupField = when (hint) {
        "answers" -> SetupField.ANSWERS
        "range" -> SetupField.RANGE
        else -> SetupField.CHORDS
    }
}
