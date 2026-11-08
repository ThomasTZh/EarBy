package com.thomas.passiveeartraining.training.settings

import com.thomas.passiveeartraining.music.MidiNote
import com.thomas.passiveeartraining.music.SpellingPreference as MusicSpellingPreference
import com.thomas.passiveeartraining.music.Tonality
import com.thomas.passiveeartraining.training.MelodicAnswerFormat
import com.thomas.passiveeartraining.training.MelodicDictationDefinition
import com.thomas.passiveeartraining.training.MelodicDirectionBias
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
 * The setup a melodic-dictation session was last started with (M10).
 *
 * Like [ContextualIntervalSetup] and the other mode setups it is domain configuration rather than
 * live playback, so it is safe to persist and restore. An empty [keyValues] selection means every
 * key tonic is included; the selected tonalities then decide which of those keys are read as major
 * or natural minor.
 *
 * The phrase is described by its exact [phraseLength], its optional start and end degrees and its
 * signed [allowedSteps] intersected with [maximumLeap]. A null endpoint means "any degree", never
 * tonic; a step of zero explicitly allows a repeated note. [bias] changes only the likelihood
 * among feasible transitions. [answerFormat] selects degree numbers or directed interval phrases,
 * and [spelling] selects the sharp or flat reading of key names in diagnostics; melodic answers
 * never speak note names.
 *
 * The phrase note length and gap are shared recipe timing (700 ms and 100 ms by default), and
 * [contextGap] separates the complete M8 context from the phrase in every example. The mode owns
 * no label cycle: cycle stop policies are refused by [MelodicDictationSetupValidator] with the
 * provider's explanation.
 *
 * @see MelodicDictationDefinition
 */
data class MelodicDictationSetup(
    /** Key tonic pitch classes; empty means all twelve, matching the setup screen. */
    val keyValues: List<Int> = listOf(0),
    val tonalities: List<Tonality> = listOf(Tonality.MAJOR),
    val context: ContextKind = ContextKind.TONIC_TRIAD,
    /** The silence after the complete context and before the first phrase note. */
    val contextGap: Duration = 500.milliseconds,
    val minimumNote: MidiNote = MidiNote.of(60),
    val maximumNote: MidiNote = MidiNote.of(72),
    /** The exact phrase note count, including the start and the end note; 2..16. */
    val phraseLength: Int = 4,
    /** The required start degree 1..7, or null for any degree. */
    val startDegree: Int? = 1,
    /** The required end degree 1..7, or null for any degree. */
    val endDegree: Int? = 1,
    /** Selected signed transition steps in -12..12; at least one. Zero allows repetitions. */
    val allowedSteps: List<Int> = (-5..5).toList(),
    /** Largest allowed absolute signed step, 0..12; a step of exactly this size is allowed. */
    val maximumLeap: Int = 5,
    val bias: MelodicDirectionBias = MelodicDirectionBias.BALANCED,
    val answerFormat: MelodicAnswerFormat = MelodicAnswerFormat.DEGREE_SEQUENCE,
    /** The sharp or flat chromatic table used to spell key names in range errors. */
    val spelling: MusicSpellingPreference = MusicSpellingPreference.SHARP,
    val mode: RecipeMode = RecipeMode.RECOGNITION,
    val thinkingSilence: Duration = Duration.parse("2s"),
    val replayCount: Int = 0,
    val stopPolicy: SessionStopPolicy = SessionStopPolicy.UntilStopped,
    /** One context or phrase note's length. */
    val noteDuration: Duration = Duration.parse("700ms"),
    /** Gap between the answer and a following example. */
    val answerGap: Duration = Duration.parse("600ms"),
    /** Gap between repeated examples. */
    val repeatGap: Duration = Duration.parse("400ms"),
    /** The silence between two adjacent phrase notes. */
    val melodicGap: Duration = Duration.parse("100ms"),
    /** The single final silence, once per exercise. */
    val finalSilence: Duration = Duration.parse("2s"),
    /** Optional seed for a reproducible session; null draws and records one at Start. */
    val seed: Long? = null,
    /** Plays the short two-note separator before each exercise. */
    val separatorCue: Boolean = false
) {
    init {
        require(minimumNote.value <= maximumNote.value) {
            "A setup range must not be inverted: ${minimumNote.value}..${maximumNote.value}"
        }
        require(
            contextGap >= Duration.ZERO &&
                contextGap <= MelodicDictationDefinition.MAX_CONTEXT_GAP
        ) {
            "A context gap must be 0.." +
                "${MelodicDictationDefinition.MAX_CONTEXT_GAP.inWholeSeconds} s but was " +
                "$contextGap"
        }
        require(noteDuration > Duration.ZERO) { "A note duration must be positive" }
        require(answerGap >= Duration.ZERO) { "An answer gap cannot be negative" }
        require(repeatGap >= Duration.ZERO) { "A repeat gap cannot be negative" }
        require(melodicGap >= Duration.ZERO) { "A melodic gap cannot be negative" }
        require(finalSilence >= Duration.ZERO) { "A final silence cannot be negative" }
    }

    /**
     * Builds the melodic-dictation definition this setup describes.
     *
     * @throws com.thomas.passiveeartraining.training.InvalidMelodicDictationDefinitionException
     *   when the selection is structurally invalid; callers validate a candidate draft before
     *   saving it.
     */
    fun toDefinition(): MelodicDictationDefinition = MelodicDictationDefinition.of(
        keyValues = keyValues,
        tonalities = tonalities,
        context = context,
        contextGap = contextGap,
        minimumNote = minimumNote,
        maximumNote = maximumNote,
        phraseLength = phraseLength,
        startDegree = startDegree,
        endDegree = endDegree,
        allowedSteps = allowedSteps,
        maximumLeap = maximumLeap,
        bias = bias,
        answerFormat = answerFormat,
        spelling = spelling
    )

    /**
     * Builds the learning recipe this setup describes, including every timing and count.
     *
     * The context gap travels in [RecipeTiming.contextGap], which is what the shared compiler
     * places between the context section and the phrase section of one example.
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
            provider = ProviderConfig.MelodicDictation(toDefinition()),
            recipe = toRecipe(),
            seed = seed,
            stopPolicy = stopPolicy
        ),
        musicGain = musicGain,
        voiceGain = voiceGain
    )

    companion object {
        /**
         * The setup a fresh install starts from: C major, tonic triad, C4..C5, four notes from
         * tonic to tonic, steps -5..5 including zero, maximum leap 5, balanced, degree answers.
         */
        fun default(): MelodicDictationSetup {
            val definition = MelodicDictationDefinition.default()
            return MelodicDictationSetup(
                keyValues = definition.keyValues,
                tonalities = definition.tonalities,
                context = definition.context,
                contextGap = definition.contextGap,
                minimumNote = definition.minimumNote,
                maximumNote = definition.maximumNote,
                phraseLength = definition.phraseLength,
                startDegree = definition.startDegree,
                endDegree = definition.endDegree,
                allowedSteps = definition.allowedSteps,
                maximumLeap = definition.maximumLeap,
                bias = definition.bias,
                answerFormat = definition.answerFormat,
                spelling = definition.spelling
            )
        }
    }
}

/**
 * A melodic-dictation setup being edited (M10).
 *
 * Deliberately unvalidated, exactly like [ContextualIntervalSetupDraft]: the form holds what the
 * user has touched, including contradictory selection, so the validator can explain what to fix.
 * A draft becomes a [MelodicDictationSetup] only after [MelodicDictationSetupValidator] accepts it.
 */
data class MelodicDictationSetupDraft(
    val keyValues: List<Int>,
    val tonalities: List<Tonality>,
    val context: ContextKind,
    val contextGap: Duration,
    val minimumNote: MidiNote,
    val maximumNote: MidiNote,
    val phraseLength: Int,
    val startDegree: Int?,
    val endDegree: Int?,
    val allowedSteps: List<Int>,
    val maximumLeap: Int,
    val bias: MelodicDirectionBias,
    val answerFormat: MelodicAnswerFormat,
    val spelling: MusicSpellingPreference,
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
    fun toSetup(): MelodicDictationSetup = MelodicDictationSetup(
        keyValues = keyValues,
        tonalities = tonalities,
        context = context,
        contextGap = contextGap,
        minimumNote = minimumNote,
        maximumNote = maximumNote,
        phraseLength = phraseLength,
        startDegree = startDegree,
        endDegree = endDegree,
        allowedSteps = allowedSteps,
        maximumLeap = maximumLeap,
        bias = bias,
        answerFormat = answerFormat,
        spelling = spelling,
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
        fun from(setup: MelodicDictationSetup): MelodicDictationSetupDraft =
            MelodicDictationSetupDraft(
                keyValues = setup.keyValues,
                tonalities = setup.tonalities,
                context = setup.context,
                contextGap = setup.contextGap,
                minimumNote = setup.minimumNote,
                maximumNote = setup.maximumNote,
                phraseLength = setup.phraseLength,
                startDegree = setup.startDegree,
                endDegree = setup.endDegree,
                allowedSteps = setup.allowedSteps,
                maximumLeap = setup.maximumLeap,
                bias = setup.bias,
                answerFormat = setup.answerFormat,
                spelling = setup.spelling,
                mode = setup.mode,
                thinkingSilence = setup.thinkingSilence,
                replayCount = setup.replayCount,
                // The setup screen no longer offers a duration stop policy; the sleep timer owns
                // durations, exactly as in the other mode drafts. A cycle policy is kept so the
                // validator can explain why melodic dictation cannot honour it.
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
