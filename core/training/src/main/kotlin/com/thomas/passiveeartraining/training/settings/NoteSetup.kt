package com.thomas.passiveeartraining.training.settings

import com.thomas.passiveeartraining.music.MidiNote
import com.thomas.passiveeartraining.music.SpellingPreference as MusicSpellingPreference
import com.thomas.passiveeartraining.training.NoteDefinition
import com.thomas.passiveeartraining.training.provider.ModeSetup
import com.thomas.passiveeartraining.training.provider.ProviderConfig
import com.thomas.passiveeartraining.training.recipe.RecipeDefinition
import com.thomas.passiveeartraining.training.recipe.RecipeMode
import com.thomas.passiveeartraining.training.recipe.RecipeTiming
import com.thomas.passiveeartraining.training.recipe.SessionStopPolicy
import com.thomas.passiveeartraining.training.session.StartRequest
import kotlin.time.Duration

/**
 * The setup a note-identification session was last started with (M7).
 *
 * Like [ChordSetup] and [ScaleSetup] it is domain configuration rather than live playback, so it
 * is safe to persist and restore. The pitch-class selection is explicit: unlike the interval and
 * scale root sets, an empty selection is invalid, because a note session without a target class
 * has no answers. The accidental style selects the sharp or flat chromatic table the scientific
 * note names use.
 */
data class NoteSetup(
    val pitchClassValues: List<Int>,
    val minimumNote: Int = 60,
    val maximumNote: Int = 72,
    /** The sharp or flat chromatic table used to spell the scientific note names. */
    val accidental: MusicSpellingPreference = MusicSpellingPreference.SHARP,
    /** Speaks and shows the octave token after the note name. */
    val speakOctave: Boolean = false,
    val mode: RecipeMode = RecipeMode.RECOGNITION,
    val thinkingSilence: Duration = Duration.parse("2s"),
    val replayCount: Int = 0,
    val stopPolicy: SessionStopPolicy = SessionStopPolicy.UntilStopped,
    /** One note's length. */
    val noteDuration: Duration = Duration.parse("700ms"),
    /** Gap between the answer and a following example. */
    val answerGap: Duration = Duration.parse("600ms"),
    /** Gap between repeated examples. */
    val repeatGap: Duration = Duration.parse("400ms"),
    /** The silence between the note and a following example. */
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
    }

    /**
     * Builds the note definition this setup describes.
     *
     * @throws com.thomas.passiveeartraining.training.InvalidNoteDefinitionException when the
     *   selection is structurally invalid; callers validate a candidate draft before saving.
     */
    fun toDefinition(): NoteDefinition = NoteDefinition.of(
        pitchClassValues = pitchClassValues,
        accidental = accidental,
        speakOctave = speakOctave,
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
            provider = ProviderConfig.Notes(toDefinition()),
            recipe = toRecipe(),
            seed = seed,
            stopPolicy = stopPolicy
        ),
        musicGain = musicGain,
        voiceGain = voiceGain
    )

    companion object {
        /** The setup a fresh install starts from: all twelve classes, C4..C5, scientific sharps. */
        fun default(): NoteSetup {
            val definition = NoteDefinition.default()
            return NoteSetup(
                pitchClassValues = definition.pitchClassValues,
                minimumNote = definition.minimumNoteValue,
                maximumNote = definition.maximumNoteValue,
                accidental = definition.accidental,
                speakOctave = definition.speakOctave
            )
        }
    }
}

/**
 * A note setup being edited (M7).
 *
 * Deliberately unvalidated, exactly like [ChordSetupDraft] and [ScaleSetupDraft]: the form holds
 * what the user has touched, including contradictory selection, so the validator can explain what
 * to fix. A draft becomes a [NoteSetup] only after [NoteSetupValidator] accepts it.
 */
data class NoteSetupDraft(
    val pitchClassValues: List<Int>,
    val minimumNote: Int,
    val maximumNote: Int,
    val accidental: MusicSpellingPreference,
    val speakOctave: Boolean,
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
    fun toSetup(): NoteSetup = NoteSetup(
        pitchClassValues = pitchClassValues,
        minimumNote = minimumNote,
        maximumNote = maximumNote,
        accidental = accidental,
        speakOctave = speakOctave,
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
        fun from(setup: NoteSetup): NoteSetupDraft = NoteSetupDraft(
            pitchClassValues = setup.pitchClassValues,
            minimumNote = setup.minimumNote,
            maximumNote = setup.maximumNote,
            accidental = setup.accidental,
            speakOctave = setup.speakOctave,
            mode = setup.mode,
            thinkingSilence = setup.thinkingSilence,
            replayCount = setup.replayCount,
            // The setup screen no longer offers a duration stop policy; the sleep timer owns
            // durations, exactly as in the chord and scale drafts.
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
