package com.thomas.passiveeartraining.training.settings

import com.thomas.passiveeartraining.music.MidiNote
import com.thomas.passiveeartraining.music.PitchClass
import com.thomas.passiveeartraining.music.ScaleDirection
import com.thomas.passiveeartraining.training.ScaleDefinition
import com.thomas.passiveeartraining.training.provider.ModeSetup
import com.thomas.passiveeartraining.training.provider.ProviderConfig
import com.thomas.passiveeartraining.training.recipe.RecipeDefinition
import com.thomas.passiveeartraining.training.recipe.RecipeMode
import com.thomas.passiveeartraining.training.recipe.RecipeTiming
import com.thomas.passiveeartraining.training.recipe.SessionStopPolicy
import com.thomas.passiveeartraining.training.session.StartRequest
import kotlin.time.Duration

/**
 * The setup a scale session was last started with (M6).
 *
 * Like [ChordSetup] it is domain configuration rather than live playback, so it is safe to persist
 * and restore. An empty root selection means every root class is included; the direction and the
 * scale selection are explicit, and a fixed root is the lower tonic of the complete octave.
 */
data class ScaleSetup(
    val scaleIds: List<String>,
    val direction: ScaleDirection = ScaleDirection.ASCENDING,
    /** Empty means every root class is included. */
    val rootValues: List<Int> = emptyList(),
    val minimumNote: Int = 48,
    val maximumNote: Int = 84,
    val mode: RecipeMode = RecipeMode.RECOGNITION,
    val thinkingSilence: Duration = Duration.parse("2s"),
    val replayCount: Int = 0,
    val stopPolicy: SessionStopPolicy = SessionStopPolicy.UntilStopped,
    /** One scale note's length. */
    val noteDuration: Duration = Duration.parse("700ms"),
    /** Gap between the answer and a following example. */
    val answerGap: Duration = Duration.parse("600ms"),
    /** Gap between repeated examples. */
    val repeatGap: Duration = Duration.parse("400ms"),
    /** An optional fixed lower tonic, or null to draw a root from the selected classes. */
    val fixedRoot: Int? = null,
    /** The silence between one scale note and the next. */
    val melodicGap: Duration = Duration.parse("100ms"),
    /** The single final silence, once per exercise. */
    val finalSilence: Duration = Duration.parse("2s"),
    /** Optional seed for a reproducible session; null draws and records one at Start. */
    val seed: Long? = null,
    /** Plays the short two-note separator before each exercise. */
    val separatorCue: Boolean = false
) {
    init {
        require(minimumNote <= maximumNote) {
            "A setup range must not be inverted: $minimumNote..$maximumNote"
        }
        require(noteDuration > Duration.ZERO) { "A note duration must be positive" }
        require(answerGap >= Duration.ZERO) { "An answer gap cannot be negative" }
        require(repeatGap >= Duration.ZERO) { "A repeat gap cannot be negative" }
        require(melodicGap >= Duration.ZERO) { "A melodic gap cannot be negative" }
        require(finalSilence >= Duration.ZERO) { "A final silence cannot be negative" }
        require(fixedRoot == null || fixedRoot in MidiNote.MIN_VALUE..MidiNote.MAX_VALUE) {
            "A fixed root must be a MIDI value in ${MidiNote.MIN_VALUE}..${MidiNote.MAX_VALUE}"
        }
    }

    /** The effective root classes: an empty selection means every root class is included. */
    val effectiveRootValues: List<Int>
        get() = rootValues.ifEmpty { PitchClass.ALL.map { it.value } }

    /**
     * Builds the scale definition this setup describes.
     *
     * @throws com.thomas.passiveeartraining.training.InvalidScaleDefinitionException when the
     *   selection is structurally invalid; callers validate a candidate draft before saving.
     */
    fun toDefinition(): ScaleDefinition = ScaleDefinition.of(
        scaleIds = scaleIds,
        direction = direction,
        rootValues = effectiveRootValues,
        fixedRoot = fixedRoot?.let { MidiNote.of(it) },
        minimumNote = MidiNote.of(minimumNote),
        maximumNote = MidiNote.of(maximumNote)
    )

    /** Builds the learning recipe this setup describes, including every timing and count. */
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

    /** The complete Start request this setup describes, for the shared session pipeline. */
    fun toStartRequest(
        seed: Long?,
        musicGain: Float = UserSettings.DEFAULT_MUSIC_GAIN,
        voiceGain: Float = UserSettings.DEFAULT_VOICE_GAIN
    ): StartRequest = StartRequest(
        setup = ModeSetup(
            provider = ProviderConfig.Scales(toDefinition()),
            recipe = toRecipe(),
            seed = seed,
            stopPolicy = stopPolicy
        ),
        musicGain = musicGain,
        voiceGain = voiceGain
    )

    companion object {
        /** The setup a fresh install starts from: major and natural minor, ascending, all roots. */
        fun default(): ScaleSetup {
            val definition = ScaleDefinition.default()
            return ScaleSetup(
                scaleIds = definition.selectedScales.map { it.id },
                direction = definition.direction,
                // An empty root selection means every root class, matching the setup screen.
                rootValues = emptyList(),
                minimumNote = definition.minimumNoteValue,
                maximumNote = definition.maximumNoteValue
            )
        }
    }
}

/**
 * A scale setup being edited (M6).
 *
 * Deliberately unvalidated, exactly like [ChordSetupDraft]: the form holds what the user has
 * touched, including contradictory selection, so the validator can explain what to fix. A draft
 * becomes a [ScaleSetup] only after [ScaleSetupValidator] accepts it.
 */
data class ScaleSetupDraft(
    val scaleIds: List<String>,
    val direction: ScaleDirection,
    val rootValues: List<Int>,
    val minimumNote: Int,
    val maximumNote: Int,
    val mode: RecipeMode,
    val thinkingSilence: Duration,
    val replayCount: Int,
    val stopPolicy: SessionStopPolicy = SessionStopPolicy.UntilStopped,
    val noteDuration: Duration = Duration.parse("700ms"),
    val answerGap: Duration = Duration.parse("600ms"),
    val repeatGap: Duration = Duration.parse("400ms"),
    val fixedRoot: Int? = null,
    val melodicGap: Duration = Duration.parse("100ms"),
    val finalSilence: Duration = Duration.parse("2s"),
    val seed: Long? = null,
    val separatorCue: Boolean = false
) {
    /** Converts to the validated form. Only call this after validation accepted the draft. */
    fun toSetup(): ScaleSetup = ScaleSetup(
        scaleIds = scaleIds,
        direction = direction,
        rootValues = rootValues,
        minimumNote = minimumNote,
        maximumNote = maximumNote,
        mode = mode,
        thinkingSilence = thinkingSilence,
        replayCount = replayCount,
        stopPolicy = stopPolicy,
        noteDuration = noteDuration,
        answerGap = answerGap,
        repeatGap = repeatGap,
        fixedRoot = fixedRoot,
        melodicGap = melodicGap,
        finalSilence = finalSilence,
        seed = seed,
        separatorCue = separatorCue
    )

    companion object {
        /** The draft a saved setup opens as. */
        fun from(setup: ScaleSetup): ScaleSetupDraft = ScaleSetupDraft(
            scaleIds = setup.scaleIds,
            direction = setup.direction,
            rootValues = setup.rootValues,
            minimumNote = setup.minimumNote,
            maximumNote = setup.maximumNote,
            mode = setup.mode,
            thinkingSilence = setup.thinkingSilence,
            replayCount = setup.replayCount,
            // The setup screen no longer offers a duration stop policy; the sleep timer owns
            // durations, exactly as in the chord and interval drafts.
            stopPolicy = (setup.stopPolicy as? SessionStopPolicy.Duration)?.let {
                SessionStopPolicy.UntilStopped
            } ?: setup.stopPolicy,
            noteDuration = setup.noteDuration,
            answerGap = setup.answerGap,
            repeatGap = setup.repeatGap,
            fixedRoot = setup.fixedRoot,
            melodicGap = setup.melodicGap,
            finalSilence = setup.finalSilence,
            seed = setup.seed,
            separatorCue = setup.separatorCue
        )
    }
}
