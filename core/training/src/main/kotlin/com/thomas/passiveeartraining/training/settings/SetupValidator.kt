package com.thomas.passiveeartraining.training.settings

import com.thomas.passiveeartraining.music.MidiNote
import com.thomas.passiveeartraining.training.InfeasibleIntervalDefinitionException
import com.thomas.passiveeartraining.training.IntervalDefinition
import com.thomas.passiveeartraining.training.InvalidIntervalDefinitionException
import com.thomas.passiveeartraining.training.recipe.RecipeDefinition
import com.thomas.passiveeartraining.training.recipe.RecipeMode
import com.thomas.passiveeartraining.training.recipe.RecipeValidationException
import com.thomas.passiveeartraining.training.recipe.SessionStopPolicy
import com.thomas.passiveeartraining.training.recipe.SessionStopTracker
import kotlin.time.Duration as TimeDuration

/**
 * A setup being edited.
 *
 * Deliberately unvalidated: a form must be able to hold what the user has typed so far, including
 * half-finished or contradictory values, so that it can explain what is wrong. [LastSetup] is the
 * validated form, and a draft is only converted to one after [SetupValidator] accepts it.
 */
data class SetupDraft(
    val intervalIds: List<String>,
    val rootValues: List<Int>,
    val minimumNote: Int,
    val maximumNote: Int,
    val presentation: com.thomas.passiveeartraining.music.IntervalPresentation,
    val mode: com.thomas.passiveeartraining.training.recipe.RecipeMode,
    val thinkingSilence: TimeDuration,
    val replayCount: Int,
    val stopPolicy: SessionStopPolicy = SessionStopPolicy.UntilStopped,
    val noteDuration: TimeDuration = TimeDuration.parse("700ms"),
    val answerGap: TimeDuration = TimeDuration.parse("600ms"),
    val repeatGap: TimeDuration = TimeDuration.parse("400ms"),
    val fixedRoot: Int? = null,
    /**
     * Every enabled presentation when mixed; null means only [presentation] is enabled.
     *
     * Nullable so a caller that changes [presentation] alone cannot leave a stale set behind.
     */
    val presentations: List<com.thomas.passiveeartraining.music.IntervalPresentation>? = null,
    val melodicGap: TimeDuration = TimeDuration.parse("100ms"),
    val finalSilence: TimeDuration = TimeDuration.parse("2s"),
    val seed: Long? = null,
    val separatorCue: Boolean = false
) {
    /** The effective enabled presentations: the explicit set, or just [presentation]. */
    val enabledPresentations: List<com.thomas.passiveeartraining.music.IntervalPresentation>
        get() = presentations ?: listOf(presentation)

    /** Converts to the validated form. Only call this after validation accepted the draft. */
    fun toSetup(): LastSetup = LastSetup(
        intervalIds = intervalIds,
        rootValues = rootValues,
        minimumNote = minimumNote,
        maximumNote = maximumNote,
        presentation = presentation,
        mode = mode,
        thinkingSilence = thinkingSilence,
        replayCount = replayCount,
        stopPolicy = stopPolicy,
        noteDuration = noteDuration,
        answerGap = answerGap,
        repeatGap = repeatGap,
        fixedRoot = fixedRoot,
        presentations = enabledPresentations,
        melodicGap = melodicGap,
        finalSilence = finalSilence,
        seed = seed,
        separatorCue = separatorCue
    )

    companion object {
        /** The draft a fresh install opens with. */
        fun from(setup: LastSetup): SetupDraft = SetupDraft(
            intervalIds = setup.intervalIds,
            rootValues = setup.rootValues,
            minimumNote = setup.minimumNote,
            maximumNote = setup.maximumNote,
            presentation = setup.presentation,
            mode = setup.mode,
            thinkingSilence = setup.thinkingSilence,
            replayCount = setup.replayCount,
            // The setup screen no longer offers a duration stop policy; the sleep timer owns
            // durations. A stored duration setup loads as "until stopped" rather than as an
            // unexplained selection.
            stopPolicy = (setup.stopPolicy as? SessionStopPolicy.Duration)?.let {
                SessionStopPolicy.UntilStopped
            } ?: setup.stopPolicy,
            noteDuration = setup.noteDuration,
            answerGap = setup.answerGap,
            repeatGap = setup.repeatGap,
            fixedRoot = setup.fixedRoot,
            // A single presentation stays implicit so changing it alone cannot leave a stale set.
            presentations = setup.enabledPresentations.takeIf { it.size > 1 },
            melodicGap = setup.melodicGap,
            finalSilence = setup.finalSilence,
            seed = setup.seed,
            separatorCue = setup.separatorCue
        )
    }
}

/** One problem with a candidate setup, attached to the field the user must fix. */
data class SetupIssue(
    val field: SetupField,
    /** Text a screen can show next to the field. */
    val explanation: String
)

/** The fields a setup exposes, so an issue can point at exactly one control. */
enum class SetupField {
    INTERVALS,
    ROOTS,
    RANGE,
    PRESENTATION,
    THINKING_SILENCE,
    REPLAY,
    STOP_POLICY,
    DURATION,
    NOTE_DURATION,
    MELODIC_GAP,
    ANSWER_GAP,
    REPEAT_GAP,
    FINAL_SILENCE,
    FIXED_NOTE,

    /** M5 chord fields. */
    QUALITIES,
    INVERSIONS,
    ANSWERS,

    /** M6 scale fields. */
    SCALES,

    /** M7 note-identification fields. */
    NOTE_CLASSES,
    ACCIDENTALS,

    /** M8 scale-degree fields. */
    KEYS,
    TONALITIES,
    DEGREES,
    CONTEXT,
    CONTEXT_GAP,

    /** M9 chord-progression fields. */
    CHORDS,
    VOICING,

    /** M10 melodic-dictation fields. */
    LENGTH,
    ENDPOINTS,
    STEPS,
    LEAP,
    BIAS
}

/** The result of validating a candidate setup: either usable, or a list of what to fix. */
sealed interface SetupValidation {
    /** The setup can be started. */
    data object Valid : SetupValidation

    /** The setup cannot be started until every issue is resolved. */
    data class Invalid(val issues: List<SetupIssue>) : SetupValidation {
        /** Explanations keyed by field, for rendering beside controls. */
        val byField: Map<SetupField, List<String>>
            get() = issues.groupBy({ it.field }, { it.explanation })
    }
}

/**
 * Validates a candidate setup.
 *
 * The UI and the domain use this one implementation, so a screen cannot accept a configuration a
 * session would refuse. Every check names the field it belongs to and explains the failure in the
 * terms the control uses, and each failure is reported independently so a user sees all the problems
 * at once rather than one at a time.
 */
object SetupValidator {

    /**
     * Validates a candidate.
     *
     * @param availableVoiceIds semantic ids of the answer clips that actually exist, so a setup that
     *   would fail at render time is refused before it is started.
     */
    fun validate(
        setup: SetupDraft,
        worstCaseItemDuration: TimeDuration? = null,
        voiceDurations: com.thomas.passiveeartraining.training.VoiceDurationCatalog =
            com.thomas.passiveeartraining.training.BundledVoiceDurations
    ): SetupValidation {
        val issues = mutableListOf<SetupIssue>()
        // An empty interval or root selection is deliberate: it means every option is included.
        if (setup.minimumNote > setup.maximumNote) {
            issues += SetupIssue(
                SetupField.RANGE,
                "The lowest note (${setup.minimumNote}) must not be above the highest (${setup.maximumNote})."
            )
        }
        val presentationsValid = setup.enabledPresentations.isNotEmpty() &&
            setup.enabledPresentations.distinct().size == setup.enabledPresentations.size &&
            setup.enabledPresentations == setup.enabledPresentations.sortedBy { it.ordinal } &&
            setup.enabledPresentations.first() == setup.presentation
        if (!presentationsValid) {
            issues += SetupIssue(
                SetupField.PRESENTATION,
                "Choose at least one presentation; the selected set must be consistent."
            )
        }
        setup.fixedRoot?.let { value ->
            if (value !in MidiNote.MIN_VALUE..MidiNote.MAX_VALUE) {
                issues += SetupIssue(
                    SetupField.FIXED_NOTE,
                    "A fixed note must be between ${MidiNote.MIN_VALUE} and ${MidiNote.MAX_VALUE}."
                )
            } else {
                val note = MidiNote.of(value)
                if (value !in setup.minimumNote..setup.maximumNote) {
                    issues += SetupIssue(
                        SetupField.FIXED_NOTE,
                        "The fixed note ${note.displayName()} is outside the chosen range."
                    )
                } else if (setup.rootValues.isNotEmpty() &&
                    note.pitchClass.value !in setup.rootValues.toSet()
                ) {
                    issues += SetupIssue(
                        SetupField.FIXED_NOTE,
                        "The fixed note ${note.displayName()} must match one of the selected root note classes."
                    )
                }
            }
        }

        // The domain is the authority on whether a definition is usable; this reports its verdict
        // against the field that caused it rather than duplicating the rules.
        val definition = if (issues.none { it.field in FEASIBILITY_FIELDS }) {
            try {
                setup.toSetup().toDefinition()
            } catch (failure: InvalidIntervalDefinitionException) {
                issues +=
                    SetupIssue(SetupField.INTERVALS, failure.message ?: "The selection is invalid.")
                null
            } catch (failure: IllegalArgumentException) {
                issues += SetupIssue(SetupField.RANGE, failure.message ?: "The range is invalid.")
                null
            }
        } else {
            null
        }

        if (definition != null) {
            // Feasibility means every selected label can actually be produced in the chosen range,
            // direction and fixed root, which is what a user notices as "it never plays the interval
            // I picked". A fixed root makes the policy version 2, so the version 2 rules apply.
            try {
                val policy = setup.toSetup().toPolicy()
                if (policy.generatorVersion == 1) {
                    com.thomas.passiveeartraining.training.SeededIntervalGenerator
                        .validateFeasibility(policy.definition)
                } else {
                    com.thomas.passiveeartraining.training.SeededIntervalGeneratorV2
                        .validateFeasibility(policy)
                }
            } catch (failure: InfeasibleIntervalDefinitionException) {
                val labels = failure.infeasibleLabelIds.joinToString(", ")
                if (setup.fixedRoot != null) {
                    issues += SetupIssue(
                        SetupField.FIXED_NOTE,
                        "The fixed note cannot produce: $labels."
                    )
                } else {
                    issues += SetupIssue(
                        SetupField.INTERVALS,
                        "These intervals cannot be played in the chosen range and direction: $labels."
                    )
                }
            } catch (failure: IllegalArgumentException) {
                // For example a fixed note the domain refuses; the explicit checks above already
                // explain the common cases, so this only adds a fallback.
                if (setup.fixedRoot != null && issues.none { it.field == SetupField.FIXED_NOTE }) {
                    issues += SetupIssue(
                        SetupField.FIXED_NOTE,
                        failure.message ?: "The fixed note is not usable."
                    )
                }
            }
        }

        listOf(
            SetupField.NOTE_DURATION to setup.noteDuration,
            SetupField.THINKING_SILENCE to setup.thinkingSilence,
            SetupField.MELODIC_GAP to setup.melodicGap,
            SetupField.ANSWER_GAP to setup.answerGap,
            SetupField.REPEAT_GAP to setup.repeatGap,
            SetupField.FINAL_SILENCE to setup.finalSilence
        ).forEach { (field, duration) ->
            if (!duration.isFinite() || duration.inWholeNanoseconds % 1000L != 0L) {
                issues += SetupIssue(field, "Choose a finite duration in whole microseconds.")
            }
        }

        // Each bound is checked before anything is constructed from it: a recipe type rejects an
        // out-of-range count, and the validator must explain that rather than let it throw.
        if (setup.replayCount !in 0..RecipeDefinition.MAX_REPLAY) {
            issues += SetupIssue(
                SetupField.REPLAY,
                "Repeats must be between 0 and ${RecipeDefinition.MAX_REPLAY}."
            )
        }
        if (setup.thinkingSilence.isNegative()) {
            issues += SetupIssue(SetupField.THINKING_SILENCE, "Thinking time cannot be negative.")
        }
        if (setup.noteDuration <= TimeDuration.ZERO) {
            issues += SetupIssue(SetupField.NOTE_DURATION, "A note must last longer than zero.")
        }
        if (setup.answerGap.isNegative()) {
            issues += SetupIssue(
                SetupField.ANSWER_GAP,
                "The gap after the answer cannot be negative."
            )
        }
        if (setup.repeatGap.isNegative()) {
            issues += SetupIssue(SetupField.REPEAT_GAP, "The repeat gap cannot be negative.")
        }
        if (setup.melodicGap.isNegative()) {
            issues += SetupIssue(SetupField.MELODIC_GAP, "The melodic gap cannot be negative.")
        }
        if (setup.finalSilence.isNegative()) {
            issues += SetupIssue(SetupField.FINAL_SILENCE, "The final silence cannot be negative.")
        }

        var longest = TimeDuration.ZERO
        if (issues.isEmpty()) {
            val recipe = setup.toSetup().toRecipe()
            for (id in setup.toSetup().effectiveIntervalIds) {
                for (presentation in setup.enabledPresentations) {
                    try {
                        val duration = recipe.totalDuration(
                            voiceDurations,
                            com.thomas.passiveeartraining.training.VoiceAssetId("interval.$id"),
                            presentation
                        )
                        longest = maxOf(longest, duration)
                        recipe.validate(
                            voiceDurations,
                            com.thomas.passiveeartraining.training.VoiceAssetId("interval.$id"),
                            presentation
                        )
                    } catch (failure: IllegalArgumentException) {
                        issues += SetupIssue(SetupField.DURATION, "${failure.message}.")
                    }
                }
            }
        }
        validateStopPolicy(setup, worstCaseItemDuration ?: longest, issues)

        return if (issues.isEmpty()) SetupValidation.Valid else SetupValidation.Invalid(issues)
    }

    /**
     * Validates the stop policy, including whether a cycle drain could overrun its bound.
     *
     * An overrun is refused up front so a user is not told fifteen minutes in that the session cannot
     * finish the cycle it promised.
     */
    private fun validateStopPolicy(
        setup: SetupDraft,
        worstCaseItemDuration: TimeDuration,
        issues: MutableList<SetupIssue>
    ) {
        when (val policy = setup.stopPolicy) {
            is SessionStopPolicy.Duration -> {
                if (policy.limit <= TimeDuration.ZERO) {
                    issues += SetupIssue(SetupField.DURATION, "Choose a duration longer than zero.")
                }
                if (policy.limit > MAX_DURATION) {
                    issues += SetupIssue(
                        SetupField.DURATION,
                        "A sleep limit is at most ${MAX_DURATION.inWholeMinutes} minutes."
                    )
                }
                if (policy.finish !=
                    com.thomas.passiveeartraining.training.recipe.DurationFinish.IMMEDIATE
                ) {
                    // The worst case is bounded by the items the cycle still holds; a cycle longer
                    // than the drain bound cannot be promised.
                    val worstCase = worstCaseItemDuration * WORST_CASE_CYCLE_ITEMS
                    if (worstCase > SessionStopPolicy.MAX_DRAIN) {
                        issues += SetupIssue(
                            SetupField.STOP_POLICY,
                            "Finishing the cycle could overrun the " +
                                "${SessionStopPolicy.MAX_DRAIN.inWholeMinutes}-minute bound."
                        )
                    }
                }
            }
            is SessionStopPolicy.CompletedItems -> Unit
            SessionStopPolicy.UntilStopped, SessionStopPolicy.AfterCycle -> Unit
        }
    }

    /**
     * The fixed roots that can produce every selected interval in the current range, roots and
     * presentation.
     *
     * A picker offers only these, so a user cannot choose a fixed note that the app would refuse to
     * save. The list is empty when the selection cannot be produced in the range at all, whatever
     * the fixed note is; the full validation then explains what to change.
     */
    fun feasibleFixedRoots(setup: SetupDraft): List<Int> {
        if (setup.fixedRoot == null) return emptyList()
        return (setup.minimumNote..setup.maximumNote).filter { candidate ->
            validate(setup.copy(fixedRoot = candidate)) is SetupValidation.Valid
        }
    }

    /** Validates the optional repeat count so a screen can clamp its control before submitting. */
    fun validateReplayCount(replayCount: Int): List<SetupIssue> {
        val issues = mutableListOf<SetupIssue>()
        if (replayCount !in 0..RecipeDefinition.MAX_REPLAY) {
            issues +=
                SetupIssue(
                    SetupField.REPLAY,
                    "Repeats must be between 0 and ${RecipeDefinition.MAX_REPLAY}."
                )
        }
        return issues
    }

    /** Longest sleep limit a setup may request. */
    val MAX_DURATION: TimeDuration = TimeDuration.parse("180m")

    /**
     * Worst-case cycle length used when validating a finish-cycle drain.
     *
     * A cycle is the generator's bag, which the definition determines; this is the longest bag the
     * product can produce, so the check is an upper bound rather than a guess.
     */
    private const val WORST_CASE_CYCLE_ITEMS = 12

    private val FEASIBILITY_FIELDS = setOf(
        SetupField.INTERVALS,
        SetupField.ROOTS,
        SetupField.RANGE,
        SetupField.PRESENTATION
    )
}
