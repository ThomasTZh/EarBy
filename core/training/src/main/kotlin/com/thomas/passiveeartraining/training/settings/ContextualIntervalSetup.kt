package com.thomas.passiveeartraining.training.settings

import com.thomas.passiveeartraining.music.IntervalPresentation
import com.thomas.passiveeartraining.music.MidiNote
import com.thomas.passiveeartraining.music.SimpleInterval
import com.thomas.passiveeartraining.music.SpellingPreference as MusicSpellingPreference
import com.thomas.passiveeartraining.music.Tonality
import com.thomas.passiveeartraining.training.ContextualIntervalAnswerFormat
import com.thomas.passiveeartraining.training.ContextualIntervalDefinition
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
 * The setup an intervals-in-context session was last started with (M9B).
 *
 * Like [ScaleDegreeSetup] and [ProgressionSetup] it is domain configuration rather than live
 * playback, so it is safe to persist and restore. An empty [keyValues] selection means every key
 * tonic is included, exactly like the scale-degree and progression setups; the selected tonalities
 * then decide which of those keys are read as major or natural minor. Interval ids are stored as
 * their stable catalog ids, so an unknown id survives in an invalid draft instead of being silently
 * dropped.
 *
 * [presentation] is a single choice, because one activation plays one layout: ascending, descending
 * or harmonic. The accidental [spelling] spells key tonics in range errors; contextual-interval
 * answers never speak note names.
 *
 * @see ContextualIntervalDefinition
 */
data class ContextualIntervalSetup(
    /** Key tonic pitch classes; empty means all twelve, matching the setup screen. */
    val keyValues: List<Int> = listOf(0),
    val tonalities: List<Tonality> = listOf(Tonality.MAJOR),
    /** Selected simple interval ids; minor third, major third and perfect fifth initially. */
    val intervalIds: List<String> = listOf(
        SimpleInterval.of(SimpleInterval.MINOR_THIRD).id,
        SimpleInterval.of(SimpleInterval.MAJOR_THIRD).id,
        SimpleInterval.of(SimpleInterval.PERFECT_FIFTH).id
    ),
    val presentation: IntervalPresentation = IntervalPresentation.ASCENDING,
    val context: ContextKind = ContextKind.TONIC_TRIAD,
    /** The silence after the complete context and before the first target note. */
    val contextGap: Duration = 500.milliseconds,
    val answerFormat: ContextualIntervalAnswerFormat = ContextualIntervalAnswerFormat.INTERVAL,
    /** The sharp or flat chromatic table used to spell key tonics in range errors. */
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
    /** The silence between the two target notes of a sequential presentation. */
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
            contextGap >= Duration.ZERO &&
                contextGap <= ContextualIntervalDefinition.MAX_CONTEXT_GAP
        ) {
            "A context gap must be 0.." +
                "${ContextualIntervalDefinition.MAX_CONTEXT_GAP.inWholeSeconds} s but was " +
                "$contextGap"
        }
        require(noteDuration > Duration.ZERO) { "A note duration must be positive" }
        require(answerGap >= Duration.ZERO) { "An answer gap cannot be negative" }
        require(repeatGap >= Duration.ZERO) { "A repeat gap cannot be negative" }
        require(melodicGap >= Duration.ZERO) { "A melodic gap cannot be negative" }
        require(finalSilence >= Duration.ZERO) { "A final silence cannot be negative" }
    }

    /**
     * Builds the contextual-interval definition this setup describes.
     *
     * @throws com.thomas.passiveeartraining.training.InvalidContextualIntervalDefinitionException
     *   when a selected interval id is unknown or the selection is structurally invalid; callers
     *   validate a candidate draft before saving it.
     */
    fun toDefinition(): ContextualIntervalDefinition = ContextualIntervalDefinition.of(
        keyValues = keyValues,
        tonalities = tonalities,
        intervalIds = intervalIds,
        presentation = presentation,
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
            provider = ProviderConfig.ContextualIntervals(toDefinition()),
            recipe = toRecipe(),
            seed = seed,
            stopPolicy = stopPolicy
        ),
        musicGain = musicGain,
        voiceGain = voiceGain
    )

    companion object {
        /** The setup a fresh install starts from: C major, tonic triad, three intervals, C4..C5. */
        fun default(): ContextualIntervalSetup {
            val definition = ContextualIntervalDefinition.default()
            return ContextualIntervalSetup(
                keyValues = definition.keyValues,
                tonalities = definition.tonalities,
                intervalIds = definition.intervalIds,
                presentation = definition.presentation,
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
 * An intervals-in-context setup being edited (M9B).
 *
 * Deliberately unvalidated, exactly like [ScaleDegreeSetupDraft] and [ProgressionSetupDraft]: the
 * form holds what the user has touched, including contradictory selection, so the validator can
 * explain what to fix. A draft becomes a [ContextualIntervalSetup] only after
 * [ContextualIntervalSetupValidator] accepts it.
 */
data class ContextualIntervalSetupDraft(
    val keyValues: List<Int>,
    val tonalities: List<Tonality>,
    val intervalIds: List<String>,
    val presentation: IntervalPresentation,
    val context: ContextKind,
    val contextGap: Duration,
    val answerFormat: ContextualIntervalAnswerFormat,
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
    fun toSetup(): ContextualIntervalSetup = ContextualIntervalSetup(
        keyValues = keyValues,
        tonalities = tonalities,
        intervalIds = intervalIds,
        presentation = presentation,
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
        fun from(setup: ContextualIntervalSetup): ContextualIntervalSetupDraft =
            ContextualIntervalSetupDraft(
                keyValues = setup.keyValues,
                tonalities = setup.tonalities,
                intervalIds = setup.intervalIds,
                presentation = setup.presentation,
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
                // durations, exactly as in the other mode drafts.
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
