package com.thomas.passiveeartraining.training.settings

import com.thomas.passiveeartraining.training.InvalidMelodicDictationDefinitionException
import com.thomas.passiveeartraining.training.MelodicDictationDefinition
import com.thomas.passiveeartraining.training.RecognitionRecipe
import com.thomas.passiveeartraining.training.provider.MelodicDictationExerciseProvider
import com.thomas.passiveeartraining.training.provider.ProviderConfig
import com.thomas.passiveeartraining.training.provider.ProviderValidation
import com.thomas.passiveeartraining.training.recipe.RecipeDefinition
import com.thomas.passiveeartraining.training.recipe.RecipeTiming
import com.thomas.passiveeartraining.training.recipe.SessionStopPolicy
import kotlin.time.Duration as TimeDuration

/**
 * Validates a melodic-dictation setup draft (M10).
 *
 * The UI and the domain use this one implementation, so a screen cannot accept a configuration a
 * session would refuse. Structural mistakes are attached to the control that caused them; the
 * provider is the authority on missing clips and on the finite realization of every selected key,
 * tonality, phrase length, endpoint, step and leap, and the complete-duration check uses the same
 * recipe the compiler runs. Melodic answers never speak note names.
 *
 * The mode owns no label cycle, so [MelodicDictationExerciseProvider.stopPolicyIssue] refuses
 * AfterCycle and a FINISH_CYCLE drain with its explanation while completed-item counts, duration
 * limits and until-stopped stay available.
 */
object MelodicDictationSetupValidator {

    /** Validates a candidate; every failure is reported independently. */
    fun validate(
        draft: MelodicDictationSetupDraft,
        voiceDurations: com.thomas.passiveeartraining.training.VoiceDurationCatalog =
            com.thomas.passiveeartraining.training.BundledVoiceDurations
    ): SetupValidation {
        val issues = mutableListOf<SetupIssue>()
        if (draft.tonalities.isEmpty()) {
            issues += SetupIssue(
                SetupField.TONALITIES,
                "Select at least one tonality."
            )
        }
        if (draft.phraseLength !in MelodicDictationDefinition.PHRASE_LENGTH_RANGE) {
            issues += SetupIssue(
                SetupField.LENGTH,
                "A phrase length must be " +
                    "${MelodicDictationDefinition.MIN_PHRASE_LENGTH} to " +
                    "${MelodicDictationDefinition.MAX_PHRASE_LENGTH} notes."
            )
        }
        listOfNotNull(draft.startDegree, draft.endDegree)
            .filter { degree -> degree !in 1..7 }
            .forEach { _ ->
                issues += SetupIssue(
                    SetupField.ENDPOINTS,
                    "An endpoint degree must be 1 to 7 or any."
                )
            }
        if (draft.allowedSteps.isEmpty()) {
            issues += SetupIssue(
                SetupField.STEPS,
                "Select at least one signed step."
            )
        } else if (
            draft.allowedSteps.any {
                it !in -MelodicDictationDefinition.MAX_STEP..MelodicDictationDefinition.MAX_STEP
            }
        ) {
            issues += SetupIssue(
                SetupField.STEPS,
                "A signed step must be -${MelodicDictationDefinition.MAX_STEP} to " +
                    "${MelodicDictationDefinition.MAX_STEP} semitones."
            )
        }
        if (draft.maximumLeap !in 0..MelodicDictationDefinition.MAX_LEAP) {
            issues += SetupIssue(
                SetupField.LEAP,
                "A maximum leap must be 0 to ${MelodicDictationDefinition.MAX_LEAP} semitones."
            )
        }
        if (draft.minimumNote.value > draft.maximumNote.value) {
            issues += SetupIssue(
                SetupField.RANGE,
                "The lowest note (${draft.minimumNote.value}) must not be above the highest " +
                    "(${draft.maximumNote.value})."
            )
        }
        if (!draft.contextGap.isFinite() ||
            draft.contextGap < TimeDuration.ZERO ||
            draft.contextGap > MelodicDictationDefinition.MAX_CONTEXT_GAP
        ) {
            issues += SetupIssue(
                SetupField.CONTEXT_GAP,
                "The context gap must be 0 to " +
                    "${MelodicDictationDefinition.MAX_CONTEXT_GAP.inWholeMilliseconds} ms."
            )
        }

        listOf(
            SetupField.CONTEXT_GAP to draft.contextGap,
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
            issues += SetupIssue(
                SetupField.MELODIC_GAP,
                "The gap between two phrase notes cannot be negative."
            )
        }
        if (draft.finalSilence.isNegative()) {
            issues += SetupIssue(SetupField.FINAL_SILENCE, "The final silence cannot be negative.")
        }

        var definition: MelodicDictationDefinition? = null
        if (issues.isEmpty()) {
            try {
                definition = draft.toSetup().toDefinition()
            } catch (failure: InvalidMelodicDictationDefinitionException) {
                issues += SetupIssue(
                    fieldOf(failure.field),
                    failure.message ?: "The melodic-dictation selection is invalid."
                )
            } catch (failure: IllegalArgumentException) {
                issues += SetupIssue(
                    SetupField.RANGE,
                    failure.message ?: "The range is invalid."
                )
            }
        }

        if (definition != null) {
            when (
                val validation = MelodicDictationExerciseProvider().validate(
                    ProviderConfig.MelodicDictation(definition),
                    voiceDurations
                )
            ) {
                is ProviderValidation.Invalid -> validation.issues.forEach { issue ->
                    issues += SetupIssue(providerField(issue.field), issue.message)
                }
                is ProviderValidation.Valid -> {
                    val recipe = draft.toSetup().toRecipe()
                    // Re-time the content bound with the edited note/gap values: the cap check
                    // must judge the setup the user actually configured. The context gap travels
                    // in the definition and in the timing exactly as it does in toRecipe().
                    val example = MelodicDictationExerciseProvider.maximumExampleDuration(
                        definition,
                        RecipeTiming(
                            noteDuration = draft.noteDuration,
                            melodicGap = draft.melodicGap,
                            contextGap = draft.contextGap
                        )
                    )
                    val answer = MelodicDictationExerciseProvider.maximumAnswerDuration(
                        definition,
                        voiceDurations
                    )
                    val total = recipe.totalDurationOf(example, answer)
                    if (total > RecognitionRecipe.EXERCISE_CAP) {
                        issues += SetupIssue(
                            SetupField.DURATION,
                            "The longest melodic-dictation exercise lasts " +
                                "${total.inWholeMilliseconds} ms, above the " +
                                "${RecognitionRecipe.EXERCISE_CAP.inWholeSeconds} s cap."
                        )
                    }
                }
            }
        }

        validateStopPolicy(draft, issues)

        return if (issues.isEmpty()) SetupValidation.Valid else SetupValidation.Invalid(issues)
    }

    /**
     * Validates the stop policy.
     *
     * The provider is the authority on which policies the mode can honour: it owns no label cycle,
     * so a cycle policy is refused with its explanation rather than accepted and then misapplied.
     * Duration limits keep the shared bounds; there is no cycle drain to bound because a
     * FINISH_CYCLE policy is already refused.
     */
    private fun validateStopPolicy(
        draft: MelodicDictationSetupDraft,
        issues: MutableList<SetupIssue>
    ) {
        val policy = draft.stopPolicy
        MelodicDictationExerciseProvider.stopPolicyIssue(policy)?.let { issue ->
            issues += SetupIssue(SetupField.STOP_POLICY, issue.message)
        }
        if (policy is SessionStopPolicy.Duration) {
            if (policy.limit <= TimeDuration.ZERO) {
                issues += SetupIssue(SetupField.DURATION, "Choose a duration longer than zero.")
            }
            if (policy.limit > SetupValidator.MAX_DURATION) {
                issues += SetupIssue(
                    SetupField.DURATION,
                    "A sleep limit is at most ${SetupValidator.MAX_DURATION.inWholeMinutes} minutes."
                )
            }
        }
    }

    private fun fieldOf(hint: String?): SetupField = when (hint) {
        "keys" -> SetupField.KEYS
        "tonalities" -> SetupField.TONALITIES
        "length" -> SetupField.LENGTH
        "endpoints" -> SetupField.ENDPOINTS
        "steps" -> SetupField.STEPS
        "leap" -> SetupField.LEAP
        "contextGap" -> SetupField.CONTEXT_GAP
        else -> SetupField.RANGE
    }

    private fun providerField(hint: String): SetupField = when (hint) {
        "answers" -> SetupField.ANSWERS
        "range" -> SetupField.RANGE
        "length" -> SetupField.LENGTH
        "endpoints" -> SetupField.ENDPOINTS
        "steps" -> SetupField.STEPS
        "leap" -> SetupField.LEAP
        else -> SetupField.RANGE
    }
}
