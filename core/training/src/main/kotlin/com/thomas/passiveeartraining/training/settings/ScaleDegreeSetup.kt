package com.thomas.passiveeartraining.training.settings

import com.thomas.passiveeartraining.music.MidiNote
import com.thomas.passiveeartraining.music.SpellingPreference as MusicSpellingPreference
import com.thomas.passiveeartraining.music.Tonality
import com.thomas.passiveeartraining.training.DegreeAnswerFormat
import com.thomas.passiveeartraining.training.ScaleDegreeDefinition
import com.thomas.passiveeartraining.training.context.ContextKind
import com.thomas.passiveeartraining.training.provider.ModeSetup
import com.thomas.passiveeartraining.training.provider.ProviderConfig
import com.thomas.passiveeartraining.training.recipe.RecipeDefinition
import com.thomas.passiveeartraining.training.recipe.RecipeMode
import com.thomas.passiveeartraining.training.recipe.RecipeTiming
import com.thomas.passiveeartraining.training.recipe.SessionStopPolicy
import com.thomas.passiveeartraining.training.session.StartRequest
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

/**
 * The setup a scale-degree session was last started with (M8).
 *
 * Like [NoteSetup] it is domain configuration rather than live playback, so it is safe to persist
 * and restore. An empty key selection means every key tonic is included, exactly like the scale
 * setup's empty root selection; tonalities and target degrees are explicit selections. The
 * accidental [spelling] selects which chromatic reading key tonics and NOTE_NAME answers use.
 */
data class ScaleDegreeSetup(
    /** Key tonic pitch classes; empty means all twelve, matching the setup screen. */
    val keyValues: List<Int> = emptyList(),
    val tonalities: List<Tonality> = listOf(Tonality.MAJOR),
    /** Target degrees 1..7; empty is invalid, because a session without a target has no answers. */
    val degrees: List<Int> = (1..7).toList(),
    val context: ContextKind = ContextKind.TONIC_TRIAD,
    /** The silence after the complete context and before the target note. */
    val contextGap: Duration = 500.milliseconds,
    val answerFormat: DegreeAnswerFormat = DegreeAnswerFormat.DEGREE,
    /** The sharp or flat chromatic table used to spell key tonics and NOTE_NAME answers. */
    val spelling: MusicSpellingPreference = MusicSpellingPreference.SHARP,
    val minimumNote: Int = 60,
    val maximumNote: Int = 72,
    val mode: RecipeMode = RecipeMode.RECOGNITION,
    val thinkingSilence: Duration = Duration.parse("2s"),
    val replayCount: Int = 0,
    val stopPolicy: SessionStopPolicy = SessionStopPolicy.UntilStopped,
    /** One context or target note's length. */
    val noteDuration: Duration = Duration.parse("700ms"),
    /** Gap between the answer and a following example. */
    val answerGap: Duration = Duration.parse("600ms"),
    /** Gap between repeated examples. */
    val repeatGap: Duration = Duration.parse("400ms"),
    /** The silence between one context group and the next. */
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
        require(
            contextGap >= Duration.ZERO && contextGap <= ScaleDegreeDefinition.MAX_CONTEXT_GAP
        ) {
            "A context gap must be 0..${ScaleDegreeDefinition.MAX_CONTEXT_GAP.inWholeSeconds} s " +
                "but was $contextGap"
        }
        require(noteDuration > Duration.ZERO) { "A note duration must be positive" }
        require(answerGap >= Duration.ZERO) { "An answer gap cannot be negative" }
        require(repeatGap >= Duration.ZERO) { "A repeat gap cannot be negative" }
        require(melodicGap >= Duration.ZERO) { "A melodic gap cannot be negative" }
        require(finalSilence >= Duration.ZERO) { "A final silence cannot be negative" }
    }

    /**
     * Builds the scale-degree definition this setup describes.
     *
     * The setup's own [spelling] selects which chromatic reading key tonics and NOTE_NAME answers
     * use.
     *
     * @throws com.thomas.passiveeartraining.training.InvalidScaleDegreeDefinitionException when the
     *   selection is structurally invalid; callers validate a candidate draft before saving.
     */
    fun toDefinition(): ScaleDegreeDefinition = ScaleDegreeDefinition.of(
        keyValues = keyValues,
        tonalities = tonalities,
        degrees = degrees,
        context = context,
        contextGap = contextGap,
        answerFormat = answerFormat,
        spelling = spelling,
        minimumNote = MidiNote.of(minimumNote),
        maximumNote = MidiNote.of(maximumNote)
    )

    /**
     * Builds the learning recipe this setup describes, including every timing and count.
     *
     * The context gap travels in [RecipeTiming.contextGap], which is what the shared compiler
     * places between the context section and the target section of one example.
     */
    fun toRecipe(): RecipeDefinition = RecipeDefinition(
        mode = mode,
        timing = RecipeTiming(
            noteDuration = noteDuration,
            melodicGap = melodicGap,
            thinkingSilence = thinkingSilence,
            postAnswerSilence = finalSilence,
            answerGap = answerGap,
            repeatGap = repeatGap,
            contextGap = contextGap
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
            provider = ProviderConfig.ScaleDegrees(toDefinition()),
            recipe = toRecipe(),
            seed = seed,
            stopPolicy = stopPolicy
        ),
        musicGain = musicGain,
        voiceGain = voiceGain
    )

    companion object {
        /** The setup a fresh install starts from: C major, all degrees, tonic triad, 500 ms gap. */
        fun default(): ScaleDegreeSetup {
            val definition = ScaleDegreeDefinition.default()
            return ScaleDegreeSetup(
                keyValues = definition.keyValues,
                tonalities = definition.tonalities,
                degrees = definition.degrees,
                context = definition.context,
                contextGap = definition.contextGap,
                answerFormat = definition.answerFormat,
                spelling = definition.spelling,
                minimumNote = definition.minimumNoteValue,
                maximumNote = definition.maximumNoteValue
            )
        }
    }
}

/**
 * A scale-degree setup being edited (M8).
 *
 * Deliberately unvalidated, exactly like [NoteSetupDraft]: the form holds what the user has
 * touched, including contradictory selection, so the validator can explain what to fix. A draft
 * becomes a [ScaleDegreeSetup] only after [ScaleDegreeSetupValidator] accepts it.
 */
data class ScaleDegreeSetupDraft(
    val keyValues: List<Int>,
    val tonalities: List<Tonality>,
    val degrees: List<Int>,
    val context: ContextKind,
    val contextGap: Duration,
    val answerFormat: DegreeAnswerFormat,
    val spelling: MusicSpellingPreference,
    val minimumNote: Int,
    val maximumNote: Int,
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
    /** Converts to the validated form. Only call this after validation accepted the draft. */
    fun toSetup(): ScaleDegreeSetup = ScaleDegreeSetup(
        keyValues = keyValues,
        tonalities = tonalities,
        degrees = degrees,
        context = context,
        contextGap = contextGap,
        answerFormat = answerFormat,
        spelling = spelling,
        minimumNote = minimumNote,
        maximumNote = maximumNote,
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
        /** The draft a saved setup opens as. */
        fun from(setup: ScaleDegreeSetup): ScaleDegreeSetupDraft = ScaleDegreeSetupDraft(
            keyValues = setup.keyValues,
            tonalities = setup.tonalities,
            degrees = setup.degrees,
            context = setup.context,
            contextGap = setup.contextGap,
            answerFormat = setup.answerFormat,
            spelling = setup.spelling,
            minimumNote = setup.minimumNote,
            maximumNote = setup.maximumNote,
            mode = setup.mode,
            thinkingSilence = setup.thinkingSilence,
            replayCount = setup.replayCount,
            // The setup screen no longer offers a duration stop policy; the sleep timer owns
            // durations, exactly as in the note and scale drafts.
            stopPolicy = (setup.stopPolicy as? SessionStopPolicy.Duration)?.let {
                SessionStopPolicy.UntilStopped
            } ?: setup.stopPolicy,
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
