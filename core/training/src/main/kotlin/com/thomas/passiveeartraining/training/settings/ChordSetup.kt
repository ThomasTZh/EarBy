package com.thomas.passiveeartraining.training.settings

import com.thomas.passiveeartraining.music.ChordPresentation
import com.thomas.passiveeartraining.music.MidiNote
import com.thomas.passiveeartraining.music.PitchClass
import com.thomas.passiveeartraining.training.ChordAnswerMode
import com.thomas.passiveeartraining.training.ChordDefinition
import com.thomas.passiveeartraining.training.provider.ModeSetup
import com.thomas.passiveeartraining.training.provider.ProviderConfig
import com.thomas.passiveeartraining.training.recipe.RecipeDefinition
import com.thomas.passiveeartraining.training.recipe.RecipeMode
import com.thomas.passiveeartraining.training.recipe.RecipeTiming
import com.thomas.passiveeartraining.training.recipe.SessionStopPolicy
import com.thomas.passiveeartraining.training.session.StartRequest
import kotlin.time.Duration

/**
 * The setup a chord session was last started with (M5).
 *
 * Like [LastSetup] it is domain configuration rather than live playback, so it is safe to persist
 * and restore. An empty root selection means every root class is included; qualities and
 * inversions are explicit selections.
 */
data class ChordSetup(
    val qualityIds: List<String>,
    val inversions: List<Int>,
    val rootValues: List<Int> = emptyList(),
    val minimumNote: Int = 48,
    val maximumNote: Int = 84,
    val presentation: ChordPresentation = ChordPresentation.HARMONIC,
    val answerMode: ChordAnswerMode = ChordAnswerMode.QUALITY,
    val mode: RecipeMode = RecipeMode.RECOGNITION,
    val thinkingSilence: Duration = Duration.parse("2s"),
    val replayCount: Int = 0,
    val stopPolicy: SessionStopPolicy = SessionStopPolicy.UntilStopped,
    /** One chord note's length. */
    val noteDuration: Duration = Duration.parse("700ms"),
    /** Gap between the answer and a following example. */
    val answerGap: Duration = Duration.parse("600ms"),
    /** Gap between repeated examples. */
    val repeatGap: Duration = Duration.parse("400ms"),
    /** An optional fixed theoretical root, or null to draw a root from the selected classes. */
    val fixedRoot: Int? = null,
    /** The silence between one arpeggio note and the next. */
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
     * Builds the chord definition this setup describes.
     *
     * @throws com.thomas.passiveeartraining.training.InvalidChordDefinitionException when the
     *   selection is structurally invalid; callers validate a candidate draft before saving.
     */
    fun toDefinition(): ChordDefinition = ChordDefinition.of(
        qualityIds = qualityIds,
        inversions = inversions,
        rootValues = effectiveRootValues,
        fixedRoot = fixedRoot?.let { MidiNote.of(it) },
        minimumNote = MidiNote.of(minimumNote),
        maximumNote = MidiNote.of(maximumNote),
        presentation = presentation,
        answerMode = answerMode
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
            provider = ProviderConfig.Chords(toDefinition()),
            recipe = toRecipe(),
            seed = seed,
            stopPolicy = stopPolicy
        ),
        musicGain = musicGain,
        voiceGain = voiceGain
    )

    companion object {
        /** The setup a fresh install starts from: major and minor, root position, all roots. */
        fun default(): ChordSetup {
            val definition = ChordDefinition.default()
            return ChordSetup(
                qualityIds = definition.selectedQualities.map { it.id },
                inversions = definition.selectedInversions,
                // An empty root selection means every root class, matching the setup screen.
                rootValues = emptyList(),
                minimumNote = definition.minimumNoteValue,
                maximumNote = definition.maximumNoteValue,
                presentation = definition.presentation,
                answerMode = definition.answerMode
            )
        }
    }
}

/**
 * A chord setup being edited (M5).
 *
 * Deliberately unvalidated, exactly like [SetupDraft]: the form holds what the user has touched,
 * including contradictory selection, so the validator can explain what to fix. A draft becomes a
 * [ChordSetup] only after [ChordSetupValidator] accepts it.
 */
data class ChordSetupDraft(
    val qualityIds: List<String>,
    val inversions: List<Int>,
    val rootValues: List<Int>,
    val minimumNote: Int,
    val maximumNote: Int,
    val presentation: ChordPresentation,
    val answerMode: ChordAnswerMode,
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
    fun toSetup(): ChordSetup = ChordSetup(
        qualityIds = qualityIds,
        inversions = inversions,
        rootValues = rootValues,
        minimumNote = minimumNote,
        maximumNote = maximumNote,
        presentation = presentation,
        answerMode = answerMode,
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
        fun from(setup: ChordSetup): ChordSetupDraft = ChordSetupDraft(
            qualityIds = setup.qualityIds,
            inversions = setup.inversions,
            rootValues = setup.rootValues,
            minimumNote = setup.minimumNote,
            maximumNote = setup.maximumNote,
            presentation = setup.presentation,
            answerMode = setup.answerMode,
            mode = setup.mode,
            thinkingSilence = setup.thinkingSilence,
            replayCount = setup.replayCount,
            // The setup screen no longer offers a duration stop policy; the sleep timer owns
            // durations, exactly as in the interval draft.
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
