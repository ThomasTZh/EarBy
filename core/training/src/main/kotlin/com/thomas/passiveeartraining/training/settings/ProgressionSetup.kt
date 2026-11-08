package com.thomas.passiveeartraining.training.settings

import com.thomas.passiveeartraining.music.DiatonicChord
import com.thomas.passiveeartraining.music.MidiNote
import com.thomas.passiveeartraining.music.ProgressionVoicingMode
import com.thomas.passiveeartraining.music.SpellingPreference as MusicSpellingPreference
import com.thomas.passiveeartraining.training.InvalidProgressionDefinitionException
import com.thomas.passiveeartraining.training.ProgressionAnswerFormat
import com.thomas.passiveeartraining.training.ProgressionDefinition
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
 * The setup a chord-progression session was last started with (M9B).
 *
 * Like [ScaleDegreeSetup] it is domain configuration rather than live playback, so it is safe to
 * persist and restore. There is no key selection: every key is used unless [fixedRoot] pins the
 * tonic MIDI note and its key. The selected chords come from the fourteen major-key diatonic
 * chords (seven triads and their seven seventh chords); they are stored as stable catalog ids, so
 * an unknown id survives in an invalid draft instead of being silently dropped. The opening I is
 * fixed and only the three following chords are drawn from this pool. The accidental [spelling]
 * spells key tonics in range errors; progression answers never speak note names.
 */
data class ProgressionSetup(
    /** Selected chord ids from the fourteen-chord pool; the seven triads initially. */
    val chordIds: List<String> = DiatonicChord.TRIADS.map { it.id },
    /** A fixed tonic MIDI note, or null to use every key and draw an anchor inside the range. */
    val fixedRoot: Int? = null,
    val voicing: ProgressionVoicingMode = ProgressionVoicingMode.ROOT_POSITION,
    /** Allowed inversions 0..3 for the nearest-inversion search; all four by default. */
    val allowedInversions: List<Int> = listOf(0, 1, 2),
    val answerFormat: ProgressionAnswerFormat = ProgressionAnswerFormat.ROMAN,
    /** The sharp or flat chromatic table used to spell key tonics in range errors. */
    val spelling: MusicSpellingPreference = MusicSpellingPreference.SHARP,
    val minimumNote: Int = 48,
    val maximumNote: Int = 84,
    val mode: RecipeMode = RecipeMode.RECOGNITION,
    val thinkingSilence: Duration = Duration.parse("2s"),
    val replayCount: Int = 0,
    val stopPolicy: SessionStopPolicy = SessionStopPolicy.UntilStopped,
    /** One chord's length. */
    val noteDuration: Duration = Duration.parse("700ms"),
    /** Gap between the answer and a following example. */
    val answerGap: Duration = Duration.parse("600ms"),
    /** Gap between repeated examples. */
    val repeatGap: Duration = Duration.parse("400ms"),
    /** The inter-chord gap inside one progression. */
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
        require(fixedRoot == null || fixedRoot in MidiNote.MIN_VALUE..MidiNote.MAX_VALUE) {
            "A fixed root must be a MIDI note but was $fixedRoot"
        }
        require(noteDuration > Duration.ZERO) { "A note duration must be positive" }
        require(answerGap >= Duration.ZERO) { "An answer gap cannot be negative" }
        require(repeatGap >= Duration.ZERO) { "A repeat gap cannot be negative" }
        require(melodicGap >= Duration.ZERO) { "A melodic gap cannot be negative" }
        require(finalSilence >= Duration.ZERO) { "A final silence cannot be negative" }
    }

    /**
     * Builds the progression definition this setup describes.
     *
     * @throws InvalidProgressionDefinitionException when a template id is unknown or the selection
     *   is structurally invalid; callers validate a candidate draft before saving it.
     */
    fun toDefinition(): ProgressionDefinition {
        val chords = chordIds.map { id ->
            DiatonicChord.ofIdOrNull(id)
                ?: throw InvalidProgressionDefinitionException(
                    "Unknown progression chord '$id'",
                    field = "chords"
                )
        }
        return ProgressionDefinition.of(
            chords = chords,
            fixedRoot = fixedRoot?.let { MidiNote.of(it) },
            voicing = voicing,
            allowedInversions = allowedInversions,
            answerFormat = answerFormat,
            spelling = spelling,
            minimumNote = MidiNote.of(minimumNote),
            maximumNote = MidiNote.of(maximumNote)
        )
    }

    /**
     * Builds the learning recipe this setup describes, including every timing and count.
     *
     * A progression has no context prelude, so the recipe timing carries no context gap: each
     * chord is a simultaneous target group and the inter-chord silence travels in
     * [RecipeTiming.melodicGap].
     */
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
            provider = ProviderConfig.Progressions(toDefinition()),
            recipe = toRecipe(),
            seed = seed,
            stopPolicy = stopPolicy
        ),
        musicGain = musicGain,
        voiceGain = voiceGain
    )

    companion object {
        /** The setup a fresh install starts from: all seven triads, any key, root position. */
        fun default(): ProgressionSetup {
            val definition = ProgressionDefinition.default()
            return ProgressionSetup(
                chordIds = definition.chords.map { it.id },
                fixedRoot = definition.fixedRoot?.value,
                voicing = definition.voicing,
                allowedInversions = definition.allowedInversions,
                answerFormat = definition.answerFormat,
                spelling = definition.spelling,
                minimumNote = definition.minimumNoteValue,
                maximumNote = definition.maximumNoteValue
            )
        }
    }
}

/**
 * A chord-progression setup being edited (M9A).
 *
 * Deliberately unvalidated, exactly like [ScaleDegreeSetupDraft]: the form holds what the user has
 * touched, including contradictory selection, so the validator can explain what to fix. A draft
 * becomes a [ProgressionSetup] only after [ProgressionSetupValidator] accepts it.
 */
data class ProgressionSetupDraft(
    val chordIds: List<String>,
    val fixedRoot: Int? = null,
    val voicing: ProgressionVoicingMode,
    val allowedInversions: List<Int>,
    val answerFormat: ProgressionAnswerFormat,
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
    fun toSetup(): ProgressionSetup = ProgressionSetup(
        chordIds = chordIds,
        fixedRoot = fixedRoot,
        voicing = voicing,
        allowedInversions = allowedInversions,
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
        fun from(setup: ProgressionSetup): ProgressionSetupDraft = ProgressionSetupDraft(
            chordIds = setup.chordIds,
            fixedRoot = setup.fixedRoot,
            voicing = setup.voicing,
            allowedInversions = setup.allowedInversions,
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
