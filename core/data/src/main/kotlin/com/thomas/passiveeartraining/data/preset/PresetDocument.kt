package com.thomas.passiveeartraining.data.preset

import com.thomas.passiveeartraining.data.migratedDegreeAnswerFormat
import com.thomas.passiveeartraining.data.migratedMelodicAnswerFormat
import com.thomas.passiveeartraining.music.ChordPresentation
import com.thomas.passiveeartraining.music.IntervalPresentation
import com.thomas.passiveeartraining.music.MidiNote
import com.thomas.passiveeartraining.music.ProgressionVoicingMode
import com.thomas.passiveeartraining.music.ScaleDirection
import com.thomas.passiveeartraining.music.SpellingPreference as MusicSpellingPreference
import com.thomas.passiveeartraining.music.Tonality
import com.thomas.passiveeartraining.training.ChordAnswerMode
import com.thomas.passiveeartraining.training.ContextualIntervalAnswerFormat
import com.thomas.passiveeartraining.training.DegreeAnswerFormat
import com.thomas.passiveeartraining.training.MelodicAnswerFormat
import com.thomas.passiveeartraining.training.MelodicDirectionBias
import com.thomas.passiveeartraining.training.ProgressionAnswerFormat
import com.thomas.passiveeartraining.training.WalkDirection
import com.thomas.passiveeartraining.training.context.ContextKind
import com.thomas.passiveeartraining.training.provider.TrainingKind
import com.thomas.passiveeartraining.training.recipe.DurationFinish
import com.thomas.passiveeartraining.training.recipe.RecipeDefinition
import com.thomas.passiveeartraining.training.recipe.RecipeMode
import com.thomas.passiveeartraining.training.recipe.SessionStopPolicy
import com.thomas.passiveeartraining.training.settings.AnswerVisibility
import com.thomas.passiveeartraining.training.settings.CHORDS_CONFIG_VERSION
import com.thomas.passiveeartraining.training.settings.CHORD_PROGRESSIONS_CONFIG_VERSION
import com.thomas.passiveeartraining.training.settings.ChordSetup
import com.thomas.passiveeartraining.training.settings.ChordSetupDraft
import com.thomas.passiveeartraining.training.settings.ChordSetupValidator
import com.thomas.passiveeartraining.training.settings.ContextualIntervalSetup
import com.thomas.passiveeartraining.training.settings.ContextualIntervalSetupDraft
import com.thomas.passiveeartraining.training.settings.ContextualIntervalSetupValidator
import com.thomas.passiveeartraining.training.settings.INTERVALS_CONFIG_VERSION
import com.thomas.passiveeartraining.training.settings.INTERVALS_IN_CONTEXT_CONFIG_VERSION
import com.thomas.passiveeartraining.training.settings.INTERVAL_WALK_CONFIG_VERSION
import com.thomas.passiveeartraining.training.settings.IntervalWalkSetup
import com.thomas.passiveeartraining.training.settings.IntervalWalkSetupDraft
import com.thomas.passiveeartraining.training.settings.IntervalWalkSetupValidator
import com.thomas.passiveeartraining.training.settings.LastSetup
import com.thomas.passiveeartraining.training.settings.MELODIC_DICTATION_CONFIG_VERSION
import com.thomas.passiveeartraining.training.settings.MelodicDictationSetup
import com.thomas.passiveeartraining.training.settings.MelodicDictationSetupDraft
import com.thomas.passiveeartraining.training.settings.MelodicDictationSetupValidator
import com.thomas.passiveeartraining.training.settings.NOTE_IDENTIFICATION_CONFIG_VERSION
import com.thomas.passiveeartraining.training.settings.NoteSetup
import com.thomas.passiveeartraining.training.settings.NoteSetupDraft
import com.thomas.passiveeartraining.training.settings.NoteSetupValidator
import com.thomas.passiveeartraining.training.settings.ProgressionSetup
import com.thomas.passiveeartraining.training.settings.ProgressionSetupDraft
import com.thomas.passiveeartraining.training.settings.ProgressionSetupValidator
import com.thomas.passiveeartraining.training.settings.SCALES_CONFIG_VERSION
import com.thomas.passiveeartraining.training.settings.SCALE_DEGREES_CONFIG_VERSION
import com.thomas.passiveeartraining.training.settings.ScaleDegreeSetup
import com.thomas.passiveeartraining.training.settings.ScaleDegreeSetupDraft
import com.thomas.passiveeartraining.training.settings.ScaleDegreeSetupValidator
import com.thomas.passiveeartraining.training.settings.ScaleSetup
import com.thomas.passiveeartraining.training.settings.ScaleSetupDraft
import com.thomas.passiveeartraining.training.settings.ScaleSetupValidator
import com.thomas.passiveeartraining.training.settings.SetupValidation
import com.thomas.passiveeartraining.training.settings.SetupValidator
import com.thomas.passiveeartraining.training.settings.ThemePreference
import com.thomas.passiveeartraining.training.settings.UserSettings
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** Schema version of the exported document format. */
const val PRESET_SCHEMA_VERSION: Int = 2

/**
 * One preset document, in either supported schema version.
 *
 * Schema 1 stored the interval payload directly; schema 2 adds a stable kind discriminator and the
 * payload's configuration version, so a document cannot be decoded as the wrong mode's setup (SC7).
 */
sealed interface PresetDocument {
    val schemaVersion: Int
    val id: String
    val name: String
    val createdAtEpochMs: Long
    val updatedAtEpochMs: Long
}

/**
 * Stable names for the stored stop kind.
 *
 * They are part of the document contract, so they are written as explicit strings rather than
 * serialized enum names.
 */
internal const val STOP_KIND_UNTIL_STOPPED: String = "UNTIL_STOPPED"
internal const val STOP_KIND_DURATION: String = "DURATION"
internal const val STOP_KIND_COMPLETED_ITEMS: String = "COMPLETED_ITEMS"
internal const val STOP_KIND_AFTER_CYCLE: String = "AFTER_CYCLE"

/**
 * The stored form of one preset.
 *
 * Field names are stable strings and every unit is explicit, so a document written by this build can
 * be read by a later one. A seed is exported as a **signed decimal string**: JSON readers that
 * represent every number as a floating point value would otherwise corrupt a 64-bit seed.
 */
@Serializable
data class PresetDocumentV1(
    override val schemaVersion: Int = 1,
    override val id: String,
    override val name: String,
    @SerialName("created_at_epoch_ms") override val createdAtEpochMs: Long,
    @SerialName("updated_at_epoch_ms") override val updatedAtEpochMs: Long,
    val payload: PresetPayloadV1
) : PresetDocument

/** The musical configuration a preset restores. No paths, no class names, no live state. */
@Serializable
data class PresetPayloadV1(
    @SerialName("interval_ids") val intervalIds: List<String>,
    @SerialName("root_values") val rootValues: List<Int>,
    @SerialName("minimum_note") val minimumNote: Int,
    @SerialName("maximum_note") val maximumNote: Int,
    val presentation: String,
    val mode: String,
    @SerialName("thinking_silence_ms") val thinkingSilenceMs: Long,
    @SerialName("replay_count") val replayCount: Int,
    /** One example note's length in milliseconds; positive. */
    @SerialName("note_duration_ms") val noteDurationMs: Long = 700,
    /** Gap after the answer before a following example; not negative. */
    @SerialName("answer_gap_ms") val answerGapMs: Long = 600,
    /** Gap between repeated examples; not negative. */
    @SerialName("repeat_gap_ms") val repeatGapMs: Long = 400,
    /** Optional fixed absolute root, or null to draw a root from the selected pitch classes. */
    @SerialName("fixed_root") val fixedRoot: Int? = null,
    /**
     * Optional mixed-presentation set as stable names, sorted; absent means the single
     * [presentation] applies and the version 1 draw order is preserved.
     */
    val presentations: List<String>? = null,
    /** The silence between the two notes of one melodic example; not negative. */
    @SerialName("melodic_gap_ms") val melodicGapMs: Long = 100,
    /** The single final silence, once per exercise; not negative. */
    @SerialName("final_silence_ms") val finalSilenceMs: Long = 2000,
    /** Plays the short two-note separator before each exercise. */
    @SerialName("separator_cue") val separatorCue: Boolean = false,
    /** Optional: when absent, Start draws a seed and records it. */
    @SerialName("seed_decimal") val seedDecimal: String? = null,
    @SerialName("music_gain") val musicGain: Float = UserSettings.DEFAULT_MUSIC_GAIN,
    @SerialName("voice_gain") val voiceGain: Float = UserSettings.DEFAULT_VOICE_GAIN,
    /** One selected stop policy; the fields below carry different kinds. */
    @SerialName("stop_kind") val stopKind: String = STOP_KIND_UNTIL_STOPPED,
    /** Positive duration in milliseconds, for [STOP_KIND_DURATION]. */
    @SerialName("stop_duration_ms") val stopDurationMs: Long? = null,
    @SerialName("stop_duration_finish") val stopDurationFinish: String? = null,
    /** Completed traversals, 1..[SessionStopPolicy.MAX_ITEMS], for [STOP_KIND_COMPLETED_ITEMS]. */
    @SerialName("stop_count") val stopCount: Int? = null
) {
    /**
     * The seed as a signed decimal string, or null.
     *
     * Validated on the way in, because a value outside the signed 64-bit range cannot be reproduced.
     */
    fun seedOrNull(): Long? = seedDecimal?.let { text ->
        text.toLongOrNull()
            ?: throw PresetValidationException("seed '$text' is not a 64-bit integer")
    }
}

/**
 * The stored chords payload (M5).
 *
 * It mirrors [PresetPayloadV1]: the same units, the same signed-decimal seed convention and the
 * same stop fields, so both kinds are written and validated by one codec while their typed
 * selections stay distinct.
 */
@Serializable
data class PresetPayloadChordsV1(
    @SerialName("quality_ids") val qualityIds: List<String>,
    val inversions: List<Int>,
    @SerialName("root_values") val rootValues: List<Int>,
    @SerialName("minimum_note") val minimumNote: Int,
    @SerialName("maximum_note") val maximumNote: Int,
    val presentation: String,
    @SerialName("answer_mode") val answerMode: String,
    val mode: String,
    @SerialName("thinking_silence_ms") val thinkingSilenceMs: Long,
    @SerialName("replay_count") val replayCount: Int,
    /** One chord note's length in milliseconds; positive. */
    @SerialName("note_duration_ms") val noteDurationMs: Long = 700,
    /** Gap after the answer before a following example; not negative. */
    @SerialName("answer_gap_ms") val answerGapMs: Long = 600,
    /** Gap between repeated examples; not negative. */
    @SerialName("repeat_gap_ms") val repeatGapMs: Long = 400,
    /** Optional fixed absolute root, or null to draw a root from the selected pitch classes. */
    @SerialName("fixed_root") val fixedRoot: Int? = null,
    /** The silence between one arpeggio note and the next; not negative. */
    @SerialName("melodic_gap_ms") val melodicGapMs: Long = 100,
    /** The single final silence, once per exercise; not negative. */
    @SerialName("final_silence_ms") val finalSilenceMs: Long = 2000,
    /** Plays the short two-note separator before each exercise. */
    @SerialName("separator_cue") val separatorCue: Boolean = false,
    /** Optional: when absent, Start draws a seed and records it. */
    @SerialName("seed_decimal") val seedDecimal: String? = null,
    @SerialName("music_gain") val musicGain: Float = UserSettings.DEFAULT_MUSIC_GAIN,
    @SerialName("voice_gain") val voiceGain: Float = UserSettings.DEFAULT_VOICE_GAIN,
    /** One selected stop policy; the fields below carry different kinds. */
    @SerialName("stop_kind") val stopKind: String = STOP_KIND_UNTIL_STOPPED,
    /** Positive duration in milliseconds, for [STOP_KIND_DURATION]. */
    @SerialName("stop_duration_ms") val stopDurationMs: Long? = null,
    @SerialName("stop_duration_finish") val stopDurationFinish: String? = null,
    /** Completed traversals, 1..[SessionStopPolicy.MAX_ITEMS], for [STOP_KIND_COMPLETED_ITEMS]. */
    @SerialName("stop_count") val stopCount: Int? = null
) {
    /**
     * The seed as a signed decimal string, or null.
     *
     * Validated on the way in, because a value outside the signed 64-bit range cannot be
     * reproduced.
     */
    fun seedOrNull(): Long? = seedDecimal?.let { text ->
        text.toLongOrNull()
            ?: throw PresetValidationException("seed '$text' is not a 64-bit integer")
    }
}

/**
 * The stored scales payload (M6).
 *
 * It mirrors [PresetPayloadChordsV1]: the same units, the same signed-decimal seed convention and
 * the same stop fields, so both kinds are written and validated by one codec while their typed
 * selections stay distinct. `direction` stores the stable enum name.
 */
@Serializable
data class PresetPayloadScalesV1(
    @SerialName("scale_ids") val scaleIds: List<String>,
    val direction: String,
    @SerialName("root_values") val rootValues: List<Int>,
    @SerialName("minimum_note") val minimumNote: Int,
    @SerialName("maximum_note") val maximumNote: Int,
    val mode: String,
    @SerialName("thinking_silence_ms") val thinkingSilenceMs: Long,
    @SerialName("replay_count") val replayCount: Int,
    /** One scale note's length in milliseconds; positive. */
    @SerialName("note_duration_ms") val noteDurationMs: Long = 700,
    /** Gap after the answer before a following example; not negative. */
    @SerialName("answer_gap_ms") val answerGapMs: Long = 600,
    /** Gap between repeated examples; not negative. */
    @SerialName("repeat_gap_ms") val repeatGapMs: Long = 400,
    /** Optional fixed lower tonic, or null to draw a root from the selected pitch classes. */
    @SerialName("fixed_root") val fixedRoot: Int? = null,
    /** The silence between one scale note and the next; not negative. */
    @SerialName("melodic_gap_ms") val melodicGapMs: Long = 100,
    /** The single final silence, once per exercise; not negative. */
    @SerialName("final_silence_ms") val finalSilenceMs: Long = 2000,
    /** Plays the short two-note separator before each exercise. */
    @SerialName("separator_cue") val separatorCue: Boolean = false,
    /** Optional: when absent, Start draws a seed and records it. */
    @SerialName("seed_decimal") val seedDecimal: String? = null,
    @SerialName("music_gain") val musicGain: Float = UserSettings.DEFAULT_MUSIC_GAIN,
    @SerialName("voice_gain") val voiceGain: Float = UserSettings.DEFAULT_VOICE_GAIN,
    /** One selected stop policy; the fields below carry different kinds. */
    @SerialName("stop_kind") val stopKind: String = STOP_KIND_UNTIL_STOPPED,
    /** Positive duration in milliseconds, for [STOP_KIND_DURATION]. */
    @SerialName("stop_duration_ms") val stopDurationMs: Long? = null,
    @SerialName("stop_duration_finish") val stopDurationFinish: String? = null,
    /** Completed traversals, 1..[SessionStopPolicy.MAX_ITEMS], for [STOP_KIND_COMPLETED_ITEMS]. */
    @SerialName("stop_count") val stopCount: Int? = null
) {
    /**
     * The seed as a signed decimal string, or null.
     *
     * Validated on the way in, because a value outside the signed 64-bit range cannot be
     * reproduced.
     */
    fun seedOrNull(): Long? = seedDecimal?.let { text ->
        text.toLongOrNull()
            ?: throw PresetValidationException("seed '$text' is not a 64-bit integer")
    }
}

/**
 * The stored notes payload (M7).
 *
 * It mirrors [PresetPayloadScalesV1]: the same units, the same signed-decimal seed convention and
 * the same stop fields, so every kind is written and validated by one codec while their typed
 * selections stay distinct. `accidental` stores the stable sharp/flat enum name.
 */
@Serializable
data class PresetPayloadNotesV1(
    @SerialName("pitch_class_values") val pitchClassValues: List<Int>,
    @SerialName("minimum_note") val minimumNote: Int,
    @SerialName("maximum_note") val maximumNote: Int,
    val accidental: String,
    @SerialName("speak_octave") val speakOctave: Boolean = false,
    /** Legacy fixed-octave field from the first schema; ignored, every octave in range is drawn. */
    @SerialName("fixed_octave") val fixedOctave: Int? = null,
    val mode: String,
    @SerialName("thinking_silence_ms") val thinkingSilenceMs: Long,
    @SerialName("replay_count") val replayCount: Int,
    /** One note's length in milliseconds; positive. */
    @SerialName("note_duration_ms") val noteDurationMs: Long = 700,
    /** Gap after the answer before a following example; not negative. */
    @SerialName("answer_gap_ms") val answerGapMs: Long = 600,
    /** Gap between repeated examples; not negative. */
    @SerialName("repeat_gap_ms") val repeatGapMs: Long = 400,
    /** The silence between the note and a following example; not negative. */
    @SerialName("melodic_gap_ms") val melodicGapMs: Long = 100,
    /** The single final silence, once per exercise; not negative. */
    @SerialName("final_silence_ms") val finalSilenceMs: Long = 2000,
    /** Plays the short two-note separator before each exercise. */
    @SerialName("separator_cue") val separatorCue: Boolean = false,
    /** Optional: when absent, Start draws a seed and records it. */
    @SerialName("seed_decimal") val seedDecimal: String? = null,
    @SerialName("music_gain") val musicGain: Float = UserSettings.DEFAULT_MUSIC_GAIN,
    @SerialName("voice_gain") val voiceGain: Float = UserSettings.DEFAULT_VOICE_GAIN,
    /** One selected stop policy; the fields below carry different kinds. */
    @SerialName("stop_kind") val stopKind: String = STOP_KIND_UNTIL_STOPPED,
    /** Positive duration in milliseconds, for [STOP_KIND_DURATION]. */
    @SerialName("stop_duration_ms") val stopDurationMs: Long? = null,
    @SerialName("stop_duration_finish") val stopDurationFinish: String? = null,
    /** Completed traversals, 1..[SessionStopPolicy.MAX_ITEMS], for [STOP_KIND_COMPLETED_ITEMS]. */
    @SerialName("stop_count") val stopCount: Int? = null
) {
    /**
     * The seed as a signed decimal string, or null.
     *
     * Validated on the way in, because a value outside the signed 64-bit range cannot be
     * reproduced.
     */
    fun seedOrNull(): Long? = seedDecimal?.let { text ->
        text.toLongOrNull()
            ?: throw PresetValidationException("seed '$text' is not a 64-bit integer")
    }
}

/**
 * The stored scale-degrees payload (M8).
 *
 * It mirrors [PresetPayloadNotesV1]: the same units, the same signed-decimal seed convention and
 * the same stop fields, so every kind is written and validated by one codec while their typed
 * selections stay distinct. `context`, `answer_format` and `spelling` store stable enum names and
 * `tonalities` a list of them. A stored answer format that named a removed movable-do or
 * degree-and-syllable variant imports as its numeric equivalent.
 */
@Serializable
data class PresetPayloadScaleDegreesV1(
    /** Key tonic pitch classes; an empty list means every key tonic is included. */
    @SerialName("key_values") val keyValues: List<Int>,
    /** Stable tonality names, for example `MAJOR` and `NATURAL_MINOR`. */
    val tonalities: List<String>,
    /** Target degrees 1..7, at least one. */
    val degrees: List<Int>,
    val context: String,
    /** The silence after the context and before the target, 0..30000 ms. */
    @SerialName("context_gap_ms") val contextGapMs: Long = 500,
    @SerialName("answer_format") val answerFormat: String,
    val spelling: String,
    @SerialName("minimum_note") val minimumNote: Int,
    @SerialName("maximum_note") val maximumNote: Int,
    val mode: String,
    @SerialName("thinking_silence_ms") val thinkingSilenceMs: Long,
    @SerialName("replay_count") val replayCount: Int,
    /** One context or target note's length in milliseconds; positive. */
    @SerialName("note_duration_ms") val noteDurationMs: Long = 700,
    /** Gap after the answer before a following example; not negative. */
    @SerialName("answer_gap_ms") val answerGapMs: Long = 600,
    /** Gap between repeated examples; not negative. */
    @SerialName("repeat_gap_ms") val repeatGapMs: Long = 400,
    /** The silence between one context group and the next; not negative. */
    @SerialName("melodic_gap_ms") val melodicGapMs: Long = 100,
    /** The single final silence, once per exercise; not negative. */
    @SerialName("final_silence_ms") val finalSilenceMs: Long = 2000,
    /** Plays the short two-note separator before each exercise. */
    @SerialName("separator_cue") val separatorCue: Boolean = false,
    /** Optional: when absent, Start draws a seed and records it. */
    @SerialName("seed_decimal") val seedDecimal: String? = null,
    @SerialName("music_gain") val musicGain: Float = UserSettings.DEFAULT_MUSIC_GAIN,
    @SerialName("voice_gain") val voiceGain: Float = UserSettings.DEFAULT_VOICE_GAIN,
    /** One selected stop policy; the fields below carry different kinds. */
    @SerialName("stop_kind") val stopKind: String = STOP_KIND_UNTIL_STOPPED,
    /** Positive duration in milliseconds, for [STOP_KIND_DURATION]. */
    @SerialName("stop_duration_ms") val stopDurationMs: Long? = null,
    @SerialName("stop_duration_finish") val stopDurationFinish: String? = null,
    /** Completed traversals, 1..[SessionStopPolicy.MAX_ITEMS], for [STOP_KIND_COMPLETED_ITEMS]. */
    @SerialName("stop_count") val stopCount: Int? = null
) {
    /**
     * The seed as a signed decimal string, or null.
     *
     * Validated on the way in, because a value outside the signed 64-bit range cannot be reproduced.
     */
    fun seedOrNull(): Long? = seedDecimal?.let { text ->
        text.toLongOrNull()
            ?: throw PresetValidationException("seed '$text' is not a 64-bit integer")
    }
}

/**
 * The stored chord-progressions payload (M9A).
 *
 * It mirrors [PresetPayloadScaleDegreesV1]: the same units, the same signed-decimal seed convention
 * and the same stop fields, so every kind is written and validated by one codec while their typed
 * selections stay distinct. `voicing`, `answer_format` and `spelling` store stable enum names,
 * `chord_ids` the fourteen-chord pool ids and `fixed_root` an optional tonic MIDI note.
 * `template_ids` is the legacy field of the first schema: a document without `chord_ids` reads as
 * the seven default triads, so an old preset stays usable after the pool redesign. Progression
 * answers never speak note names.
 */
@Serializable
data class PresetPayloadProgressionsV1(
    /** Key tonic pitch classes; an empty list means every key tonic is included. */
    @SerialName("key_values") val keyValues: List<Int>,
    /** Stable template ids of the legacy schema; empty in a new document. */
    @SerialName("template_ids") val templateIds: List<String> = emptyList(),
    /** Stable chord ids, for example `1_major` or `5_dominant_seventh`; at least one. */
    @SerialName("chord_ids") val chordIds: List<String> = emptyList(),
    /** A fixed tonic MIDI note, or null to draw the key and anchor inside the range. */
    @SerialName("fixed_root") val fixedRoot: Int? = null,
    val voicing: String,
    /** Allowed inversions 0..3 for the nearest-inversion search; at least one. */
    @SerialName("allowed_inversions") val allowedInversions: List<Int>,
    @SerialName("answer_format") val answerFormat: String,
    val spelling: String,
    @SerialName("minimum_note") val minimumNote: Int,
    @SerialName("maximum_note") val maximumNote: Int,
    val mode: String,
    @SerialName("thinking_silence_ms") val thinkingSilenceMs: Long,
    @SerialName("replay_count") val replayCount: Int,
    /** One chord's length in milliseconds; positive. */
    @SerialName("note_duration_ms") val noteDurationMs: Long = 700,
    /** Gap after the answer before a following example; not negative. */
    @SerialName("answer_gap_ms") val answerGapMs: Long = 600,
    /** Gap between repeated examples; not negative. */
    @SerialName("repeat_gap_ms") val repeatGapMs: Long = 400,
    /** The inter-chord gap inside one progression; not negative. */
    @SerialName("melodic_gap_ms") val melodicGapMs: Long = 100,
    /** The single final silence, once per exercise; not negative. */
    @SerialName("final_silence_ms") val finalSilenceMs: Long = 2000,
    /** Plays the short two-note separator before each exercise. */
    @SerialName("separator_cue") val separatorCue: Boolean = false,
    /** Optional: when absent, Start draws a seed and records it. */
    @SerialName("seed_decimal") val seedDecimal: String? = null,
    @SerialName("music_gain") val musicGain: Float = UserSettings.DEFAULT_MUSIC_GAIN,
    @SerialName("voice_gain") val voiceGain: Float = UserSettings.DEFAULT_VOICE_GAIN,
    /** One selected stop policy; the fields below carry different kinds. */
    @SerialName("stop_kind") val stopKind: String = STOP_KIND_UNTIL_STOPPED,
    /** Positive duration in milliseconds, for [STOP_KIND_DURATION]. */
    @SerialName("stop_duration_ms") val stopDurationMs: Long? = null,
    @SerialName("stop_duration_finish") val stopDurationFinish: String? = null,
    /** Completed traversals, 1..[SessionStopPolicy.MAX_ITEMS], for [STOP_KIND_COMPLETED_ITEMS]. */
    @SerialName("stop_count") val stopCount: Int? = null
) {
    /**
     * The seed as a signed decimal string, or null.
     *
     * Validated on the way in, because a value outside the signed 64-bit range cannot be reproduced.
     */
    fun seedOrNull(): Long? = seedDecimal?.let { text ->
        text.toLongOrNull()
            ?: throw PresetValidationException("seed '$text' is not a 64-bit integer")
    }
}

/**
 * The stored intervals-in-context payload (M9B).
 *
 * It mirrors [PresetPayloadProgressionsV1]: the same units, the same signed-decimal seed convention
 * and the same stop fields, so every kind is written and validated by one codec while their typed
 * selections stay distinct. `presentation`, `context`, `answer_format` and `spelling` store stable
 * enum names and `tonalities` a list of them; contextual-interval answers never speak note names.
 */
@Serializable
data class PresetPayloadContextualIntervalsV1(
    /** Key tonic pitch classes; an empty list means every key tonic is included. */
    @SerialName("key_values") val keyValues: List<Int>,
    /** Stable tonality names, for example `MAJOR` and `NATURAL_MINOR`; at least one. */
    val tonalities: List<String>,
    /** Stable simple interval ids, for example `minor_third`; at least one. */
    @SerialName("interval_ids") val intervalIds: List<String>,
    val presentation: String,
    val context: String,
    /** The silence after the context and before the first target, 0..30000 ms. */
    @SerialName("context_gap_ms") val contextGapMs: Long = 500,
    @SerialName("answer_format") val answerFormat: String,
    val spelling: String,
    @SerialName("minimum_note") val minimumNote: Int,
    @SerialName("maximum_note") val maximumNote: Int,
    val mode: String,
    @SerialName("thinking_silence_ms") val thinkingSilenceMs: Long,
    @SerialName("replay_count") val replayCount: Int,
    /** One context or target note's length in milliseconds; positive. */
    @SerialName("note_duration_ms") val noteDurationMs: Long = 700,
    /** Gap after the answer before a following example; not negative. */
    @SerialName("answer_gap_ms") val answerGapMs: Long = 600,
    /** Gap between repeated examples; not negative. */
    @SerialName("repeat_gap_ms") val repeatGapMs: Long = 400,
    /** The silence between the two target notes of a sequential presentation; not negative. */
    @SerialName("melodic_gap_ms") val melodicGapMs: Long = 100,
    /** The single final silence, once per exercise; not negative. */
    @SerialName("final_silence_ms") val finalSilenceMs: Long = 2000,
    /** Plays the short two-note separator before each exercise. */
    @SerialName("separator_cue") val separatorCue: Boolean = false,
    /** Optional: when absent, Start draws a seed and records it. */
    @SerialName("seed_decimal") val seedDecimal: String? = null,
    @SerialName("music_gain") val musicGain: Float = UserSettings.DEFAULT_MUSIC_GAIN,
    @SerialName("voice_gain") val voiceGain: Float = UserSettings.DEFAULT_VOICE_GAIN,
    /** One selected stop policy; the fields below carry different kinds. */
    @SerialName("stop_kind") val stopKind: String = STOP_KIND_UNTIL_STOPPED,
    /** Positive duration in milliseconds, for [STOP_KIND_DURATION]. */
    @SerialName("stop_duration_ms") val stopDurationMs: Long? = null,
    @SerialName("stop_duration_finish") val stopDurationFinish: String? = null,
    /** Completed traversals, 1..[SessionStopPolicy.MAX_ITEMS], for [STOP_KIND_COMPLETED_ITEMS]. */
    @SerialName("stop_count") val stopCount: Int? = null
) {
    /**
     * The seed as a signed decimal string, or null.
     *
     * Validated on the way in, because a value outside the signed 64-bit range cannot be reproduced.
     */
    fun seedOrNull(): Long? = seedDecimal?.let { text ->
        text.toLongOrNull()
            ?: throw PresetValidationException("seed '$text' is not a 64-bit integer")
    }
}

/**
 * The stored melodic-dictation payload (M10).
 *
 * It mirrors [PresetPayloadContextualIntervalsV1]: the same units, the same signed-decimal seed
 * convention and the same stop fields, so every kind is written and validated by one codec while
 * their typed selections stay distinct. `context`, `bias`, `answer_format` and `spelling` store
 * stable enum names and `tonalities` a list of them; `allowed_steps` holds the signed semitone
 * steps. A stored answer format that named the removed movable-do sequence imports as its numeric
 * equivalent. An absent `start_degree` or `end_degree` means "any degree".
 */
@Serializable
data class PresetPayloadMelodicDictationV1(
    /** Key tonic pitch classes; an empty list means every key tonic is included. */
    @SerialName("key_values") val keyValues: List<Int>,
    /** Stable tonality names, for example `MAJOR` and `NATURAL_MINOR`; at least one. */
    val tonalities: List<String>,
    val context: String,
    /** The silence after the context and before the phrase, 0..30000 ms. */
    @SerialName("context_gap_ms") val contextGapMs: Long = 500,
    /** The exact phrase note count, including both endpoints, 2..16. */
    @SerialName("phrase_length") val phraseLength: Int,
    /** The required start degree 1..7; absent means any degree. */
    @SerialName("start_degree") val startDegree: Int? = null,
    /** The required end degree 1..7; absent means any degree. */
    @SerialName("end_degree") val endDegree: Int? = null,
    /** The signed transition steps in -12..12; at least one. Zero allows repetitions. */
    @SerialName("allowed_steps") val allowedSteps: List<Int>,
    /** The largest allowed absolute signed step, 0..12. */
    @SerialName("maximum_leap") val maximumLeap: Int,
    /** The stable direction-bias name: `BALANCED`, `ASCENDING` or `DESCENDING`. */
    val bias: String,
    @SerialName("answer_format") val answerFormat: String,
    val spelling: String,
    @SerialName("minimum_note") val minimumNote: Int,
    @SerialName("maximum_note") val maximumNote: Int,
    val mode: String,
    @SerialName("thinking_silence_ms") val thinkingSilenceMs: Long,
    @SerialName("replay_count") val replayCount: Int,
    /** One context or phrase note's length in milliseconds; positive. */
    @SerialName("note_duration_ms") val noteDurationMs: Long = 700,
    /** Gap after the answer before a following example; not negative. */
    @SerialName("answer_gap_ms") val answerGapMs: Long = 600,
    /** Gap between repeated examples; not negative. */
    @SerialName("repeat_gap_ms") val repeatGapMs: Long = 400,
    /** The silence between two adjacent phrase notes; not negative. */
    @SerialName("melodic_gap_ms") val melodicGapMs: Long = 100,
    /** The single final silence, once per exercise; not negative. */
    @SerialName("final_silence_ms") val finalSilenceMs: Long = 2000,
    /** Plays the short two-note separator before each exercise. */
    @SerialName("separator_cue") val separatorCue: Boolean = false,
    /** Optional: when absent, Start draws a seed and records it. */
    @SerialName("seed_decimal") val seedDecimal: String? = null,
    @SerialName("music_gain") val musicGain: Float = UserSettings.DEFAULT_MUSIC_GAIN,
    @SerialName("voice_gain") val voiceGain: Float = UserSettings.DEFAULT_VOICE_GAIN,
    /** One selected stop policy; the fields below carry different kinds. */
    @SerialName("stop_kind") val stopKind: String = STOP_KIND_UNTIL_STOPPED,
    /** Positive duration in milliseconds, for [STOP_KIND_DURATION]. */
    @SerialName("stop_duration_ms") val stopDurationMs: Long? = null,
    @SerialName("stop_duration_finish") val stopDurationFinish: String? = null,
    /** Completed traversals, 1..[SessionStopPolicy.MAX_ITEMS], for [STOP_KIND_COMPLETED_ITEMS]. */
    @SerialName("stop_count") val stopCount: Int? = null
) {
    /**
     * The seed as a signed decimal string, or null.
     *
     * Validated on the way in, because a value outside the signed 64-bit range cannot be reproduced.
     */
    fun seedOrNull(): Long? = seedDecimal?.let { text ->
        text.toLongOrNull()
            ?: throw PresetValidationException("seed '$text' is not a 64-bit integer")
    }
}

/** The interval-walk payload at its frozen configuration version 1. */
@Serializable
data class PresetPayloadIntervalWalkV1(
    @SerialName("interval_ids") val intervalIds: List<String>,
    @SerialName("root_values") val rootValues: List<Int>,
    @SerialName("minimum_note") val minimumNote: Int,
    @SerialName("maximum_note") val maximumNote: Int,
    @SerialName("fixed_start") val fixedStart: Int? = null,
    val direction: String,
    val length: Int,
    val mode: String,
    @SerialName("thinking_silence_ms") val thinkingSilenceMs: Long,
    @SerialName("replay_count") val replayCount: Int,
    @SerialName("note_duration_ms") val noteDurationMs: Long = 700,
    @SerialName("answer_gap_ms") val answerGapMs: Long = 600,
    @SerialName("repeat_gap_ms") val repeatGapMs: Long = 400,
    @SerialName("melodic_gap_ms") val melodicGapMs: Long = 100,
    @SerialName("final_silence_ms") val finalSilenceMs: Long = 2000,
    @SerialName("separator_cue") val separatorCue: Boolean = false,
    @SerialName("seed_decimal") val seedDecimal: String? = null,
    @SerialName("music_gain") val musicGain: Float = UserSettings.DEFAULT_MUSIC_GAIN,
    @SerialName("voice_gain") val voiceGain: Float = UserSettings.DEFAULT_VOICE_GAIN,
    @SerialName("stop_kind") val stopKind: String = STOP_KIND_UNTIL_STOPPED,
    @SerialName("stop_duration_ms") val stopDurationMs: Long? = null,
    @SerialName("stop_duration_finish") val stopDurationFinish: String? = null,
    @SerialName("stop_count") val stopCount: Int? = null
) {
    fun seedOrNull(): Long? = seedDecimal?.let { text ->
        text.toLongOrNull()
            ?: throw PresetValidationException("seed '$text' is not a 64-bit integer")
    }
}

/**
 * One schema-2 preset document.
 *
 * [kind] is the stable training-kind id and [configVersion] is that provider payload's own version;
 * together they type [payload]. This build writes intervals payloads as their unchanged schema-1
 * field set, so a migrated v1 document and a v2 document carry identical musical data; a chords
 * document carries its typed selection at the frozen chords payload version (M5), a scales
 * document its typed selection at the frozen scales payload version (M6), a notes document its
 * typed selection at the frozen notes payload version (M7), a scale-degrees document its typed
 * selection at the frozen scale-degrees payload version (M8), a chord-progressions document its
 * typed selection at the frozen chord-progressions payload version (M9A), an
 * intervals-in-context document its typed selection at the frozen contextual-interval payload
 * version (M9B), and a melodic-dictation document its typed selection at the frozen
 * melodic-dictation payload version (M10).
 */
@Serializable
data class PresetDocumentV2(
    override val schemaVersion: Int = PRESET_SCHEMA_VERSION,
    override val id: String,
    override val name: String,
    @SerialName("created_at_epoch_ms") override val createdAtEpochMs: Long,
    @SerialName("updated_at_epoch_ms") override val updatedAtEpochMs: Long,
    val kind: String,
    @SerialName("config_version") val configVersion: Int,
    val payload: JsonObject
) : PresetDocument

/** The export envelope: elements are decoded per their own schema version. */
@Serializable
data class PresetExportEnvelope(
    val schemaVersion: Int = PRESET_SCHEMA_VERSION,
    val presets: List<JsonElement>
)

/** One preset as the repository and UI see it. */
data class Preset(
    val id: PresetId,
    val name: String,
    val createdAtEpochMs: Long,
    val updatedAtEpochMs: Long,
    /** When the user applied this preset; zero means it was never used here. */
    val lastUsedAtEpochMs: Long = 0,
    /** The kind-specific configuration this preset restores; the subtype fixes its kind. */
    val configuration: PresetConfiguration,
    val seed: Long? = null,
    val musicGain: Float = UserSettings.DEFAULT_MUSIC_GAIN,
    val voiceGain: Float = UserSettings.DEFAULT_VOICE_GAIN,
    /** True for the built-in starters, which are immutable and are copied before editing. */
    val builtIn: Boolean = false,
    /** True for one of the five user slots that has not been filled with a setup yet. */
    val empty: Boolean = false
)

/**
 * The five user slots.
 *
 * A fresh store creates exactly these five editable slots; the user fills them from the current
 * setup. Their identities are fixed so a rename or an update cannot create a new slot, and they can
 * never be deleted: an emptied slot is just filled again.
 */
object PresetSlots {
    /** How many editable slots each mode owns. */
    const val COUNT: Int = 5

    /** The intervals slot identities, in display order; frozen and preserved by every migration. */
    val IDS: List<PresetId> = (1..COUNT).map { index ->
        PresetId("00000000-0000-4000-8000-00000000010$index")
    }

    /**
     * The slot identities of [kind].
     *
     * The intervals slots keep their historical ids; every later kind uses deterministic
     * `mode:<kind>:slot:<n>` ids, frozen here so no migration or UI can invent a different one
     * (SC7/M2.3).
     */
    fun slotIds(kind: TrainingKind): List<PresetId> = if (kind == TrainingKind.INTERVALS) {
        IDS
    } else {
        (1..COUNT).map { index -> PresetId("mode:${kind.id}:slot:$index") }
    }

    /** The slot number (1..[COUNT]) of an [Intervals] slot id, or null for any other identity. */
    fun slotNumber(id: PresetId): Int? = slotNumberOf(TrainingKind.INTERVALS, id)

    /** The slot number (1..[COUNT]) of [id] in [kind]'s slot space, or null for any other id. */
    fun slotNumberOf(kind: TrainingKind, id: PresetId): Int? =
        slotIds(kind).indexOf(id).takeIf { it >= 0 }?.plus(1)

    /** The name a slot starts with, before the user renames it. */
    fun defaultName(index: Int): String = "Preset $index"
}

/** Raised when a preset cannot be represented or read. */
class PresetValidationException(message: String) : IllegalArgumentException(message)

/**
 * The payload configuration version this build reads and writes for [kind], or null for a kind
 * that has no typed codec yet.
 *
 * One place names the supported kinds, so the codec and the store cannot drift apart when a later
 * milestone adds another mode (SC7/M5).
 */
internal fun presetConfigVersionOrNull(kind: TrainingKind): Int? = when (kind) {
    TrainingKind.INTERVALS -> INTERVALS_CONFIG_VERSION
    TrainingKind.CHORDS -> CHORDS_CONFIG_VERSION
    TrainingKind.SCALES -> SCALES_CONFIG_VERSION
    TrainingKind.NOTE_IDENTIFICATION -> NOTE_IDENTIFICATION_CONFIG_VERSION
    TrainingKind.SCALE_DEGREES -> SCALE_DEGREES_CONFIG_VERSION
    TrainingKind.CHORD_PROGRESSIONS -> CHORD_PROGRESSIONS_CONFIG_VERSION
    TrainingKind.INTERVALS_IN_CONTEXT -> INTERVALS_IN_CONTEXT_CONFIG_VERSION
    TrainingKind.MELODIC_DICTATION -> MELODIC_DICTATION_CONFIG_VERSION
    TrainingKind.INTERVAL_WALK -> INTERVAL_WALK_CONFIG_VERSION
    else -> null
}

/**
 * Converts between presets and the stored document.
 *
 * Conversion validates as it goes: a document that names an unknown mode, presentation or interval,
 * or whose musical constraints cannot produce a definition, is rejected with a reason rather than
 * imported into a configuration that would fail later.
 */
object PresetDocumentCodec {

    /** Strict reader: rejects unknown fields so a newer document is never silently misread. */
    private val json = Json {
        ignoreUnknownKeys = false
        isLenient = false
        explicitNulls = false
        encodeDefaults = true
        // A non-finite gain is representable here so validation refuses it with a readable preset
        // error; it can never reach exported text because every document is validated first.
        allowSpecialFloatingPointValues = true
    }

    /** Writes a schema-2 export envelope for [presets]. */
    fun encode(presets: List<Preset>): String {
        if (presets.size > MAX_PRESETS) {
            throw PresetValidationException("An export holds at most $MAX_PRESETS presets")
        }
        val documents = presets.map { toDocument(it) }
        // An export this build writes must be an export this build can read; otherwise the backup
        // path could produce a file that fails on restore.
        documents.forEach(::validate)
        val envelope = PresetExportEnvelope(
            presets = documents.map { document ->
                json.encodeToJsonElement(PresetDocumentV2.serializer(), document)
            }
        )
        val text = json.encodeToString(PresetExportEnvelope.serializer(), envelope)
        // The size bound is checked on the encoded text, which is what a provider stores.
        if (text.toByteArray(Charsets.UTF_8).size > MAX_BYTES) {
            throw PresetValidationException(
                "An export is at most ${MAX_BYTES / 1024} KiB of UTF-8, but this one is " +
                    "${text.toByteArray(Charsets.UTF_8).size / 1024} KiB"
            )
        }
        return text
    }

    /**
     * Reads an export envelope and validates every preset in it.
     *
     * @throws PresetValidationException naming the reason, including a future schema version.
     */
    fun decode(text: String): List<PresetDocument> =
        decodeEnvelope(text).also { documents -> documents.forEach(::validate) }

    /**
     * Reads an export envelope without validating individual presets.
     *
     * An import preview uses this so one unusable preset is reported on its own row instead of
     * failing the whole file; a caller importing directly uses [decode], which validates.
     *
     * @throws PresetValidationException for a file-level problem: structure, schema version or size.
     */
    fun decodeEnvelope(text: String): List<PresetDocument> {
        if (text.toByteArray(Charsets.UTF_8).size > MAX_BYTES) {
            throw PresetValidationException("The document is larger than ${MAX_BYTES / 1024} KiB")
        }
        // Structure first: duplicate keys, nesting depth and string length are bounded before any
        // object is built, so a malformed document cannot reach the typed decoder.
        PresetPreflight.check(text)
        val envelope = try {
            json.decodeFromString(PresetExportEnvelope.serializer(), text)
        } catch (failure: kotlinx.serialization.SerializationException) {
            throw PresetValidationException(
                "The document is not a valid preset export: ${failure.message}"
            )
        }
        if (envelope.schemaVersion > PRESET_SCHEMA_VERSION) {
            throw PresetValidationException(
                "This file was written by a newer version (schema ${envelope.schemaVersion}); " +
                    "this build understands schema ${PRESET_SCHEMA_VERSION}"
            )
        }
        if (envelope.presets.size > MAX_PRESETS) {
            throw PresetValidationException("The document holds more than $MAX_PRESETS presets")
        }
        return envelope.presets.mapIndexed { index, element ->
            decodeDocument(index, element, envelope.schemaVersion)
        }
    }

    /** Decodes one preset element, dispatching on its own schema version. */
    private fun decodeDocument(
        index: Int,
        element: JsonElement,
        envelopeVersion: Int
    ): PresetDocument {
        val obj = element as? JsonObject ?: throw PresetValidationException(
            "Preset at position $index is not an object"
        )
        val version = obj["schemaVersion"]?.jsonPrimitive?.intOrNull ?: envelopeVersion
        return when {
            version <= 1 -> decodeElement(PresetDocumentV1.serializer(), obj, index)
            version == PRESET_SCHEMA_VERSION ->
                decodeElement(PresetDocumentV2.serializer(), obj, index)
            else -> throw PresetValidationException(
                "Preset at position $index was written by a newer version (schema $version); " +
                    "this build understands schema $PRESET_SCHEMA_VERSION"
            )
        }
    }

    private fun <T> decodeElement(
        serializer: kotlinx.serialization.KSerializer<T>,
        obj: JsonObject,
        index: Int
    ): T = try {
        json.decodeFromJsonElement(serializer, obj)
    } catch (failure: kotlinx.serialization.SerializationException) {
        throw PresetValidationException(
            "Preset at position $index is not a valid document: ${failure.message}"
        )
    }

    /** The decoded, version-independent preset fields shared by every schema. */
    private data class DecodedPreset(
        val id: String,
        val name: String,
        val createdAtEpochMs: Long,
        val updatedAtEpochMs: Long,
        val payload: DecodedPayload
    )

    /**
     * One supported document's typed payload.
     *
     * The subtype fixes the kind, so a document can never be decoded as the wrong mode's setup;
     * [shared] exposes the fields every kind stores, so gains, seed, timings and stop policy have
     * one validation path rather than one per mode (SC7/M5).
     */
    private sealed interface DecodedPayload {
        /** The kind this payload describes; fixed by the subtype. */
        val kind: TrainingKind

        /** The fields every stored payload carries with the same meaning. */
        val shared: SharedPayload

        data class Intervals(val payload: PresetPayloadV1) : DecodedPayload {
            override val kind: TrainingKind get() = TrainingKind.INTERVALS
            override val shared: SharedPayload get() = payload.sharedFields()
        }

        data class Chords(val payload: PresetPayloadChordsV1) : DecodedPayload {
            override val kind: TrainingKind get() = TrainingKind.CHORDS
            override val shared: SharedPayload get() = payload.sharedFields()
        }

        data class Scales(val payload: PresetPayloadScalesV1) : DecodedPayload {
            override val kind: TrainingKind get() = TrainingKind.SCALES
            override val shared: SharedPayload get() = payload.sharedFields()
        }

        data class Notes(val payload: PresetPayloadNotesV1) : DecodedPayload {
            override val kind: TrainingKind get() = TrainingKind.NOTE_IDENTIFICATION
            override val shared: SharedPayload get() = payload.sharedFields()
        }

        data class ScaleDegrees(val payload: PresetPayloadScaleDegreesV1) : DecodedPayload {
            override val kind: TrainingKind get() = TrainingKind.SCALE_DEGREES
            override val shared: SharedPayload get() = payload.sharedFields()
        }

        data class Progressions(val payload: PresetPayloadProgressionsV1) : DecodedPayload {
            override val kind: TrainingKind get() = TrainingKind.CHORD_PROGRESSIONS
            override val shared: SharedPayload get() = payload.sharedFields()
        }

        data class ContextualIntervals(val payload: PresetPayloadContextualIntervalsV1) :
            DecodedPayload {
            override val kind: TrainingKind get() = TrainingKind.INTERVALS_IN_CONTEXT
            override val shared: SharedPayload get() = payload.sharedFields()
        }

        data class MelodicDictation(val payload: PresetPayloadMelodicDictationV1) :
            DecodedPayload {
            override val kind: TrainingKind get() = TrainingKind.MELODIC_DICTATION
            override val shared: SharedPayload get() = payload.sharedFields()
        }

        data class IntervalWalk(val payload: PresetPayloadIntervalWalkV1) : DecodedPayload {
            override val kind: TrainingKind get() = TrainingKind.INTERVAL_WALK
            override val shared: SharedPayload get() = payload.sharedFields()
        }
    }

    /** The decoded fields both stored payloads carry with the same meaning. */
    private data class SharedPayload(
        val minimumNote: Int,
        val maximumNote: Int,
        val thinkingSilenceMs: Long,
        val replayCount: Int,
        val noteDurationMs: Long,
        val answerGapMs: Long,
        val repeatGapMs: Long,
        val melodicGapMs: Long,
        val finalSilenceMs: Long,
        val seedDecimal: String?,
        val musicGain: Float,
        val voiceGain: Float,
        val stopKind: String,
        val stopDurationMs: Long?,
        val stopDurationFinish: String?,
        val stopCount: Int?
    ) {
        /** The seed as a signed decimal string, or null; rejected outside the signed range. */
        fun seedOrNull(): Long? = seedDecimal?.let { text ->
            text.toLongOrNull()
                ?: throw PresetValidationException("seed '$text' is not a 64-bit integer")
        }
    }

    private fun PresetPayloadV1.sharedFields(): SharedPayload = SharedPayload(
        minimumNote = minimumNote,
        maximumNote = maximumNote,
        thinkingSilenceMs = thinkingSilenceMs,
        replayCount = replayCount,
        noteDurationMs = noteDurationMs,
        answerGapMs = answerGapMs,
        repeatGapMs = repeatGapMs,
        melodicGapMs = melodicGapMs,
        finalSilenceMs = finalSilenceMs,
        seedDecimal = seedDecimal,
        musicGain = musicGain,
        voiceGain = voiceGain,
        stopKind = stopKind,
        stopDurationMs = stopDurationMs,
        stopDurationFinish = stopDurationFinish,
        stopCount = stopCount
    )

    private fun PresetPayloadIntervalWalkV1.sharedFields(): SharedPayload = SharedPayload(
        minimumNote = minimumNote,
        maximumNote = maximumNote,
        thinkingSilenceMs = thinkingSilenceMs,
        replayCount = replayCount,
        noteDurationMs = noteDurationMs,
        answerGapMs = answerGapMs,
        repeatGapMs = repeatGapMs,
        melodicGapMs = melodicGapMs,
        finalSilenceMs = finalSilenceMs,
        seedDecimal = seedDecimal,
        musicGain = musicGain,
        voiceGain = voiceGain,
        stopKind = stopKind,
        stopDurationMs = stopDurationMs,
        stopDurationFinish = stopDurationFinish,
        stopCount = stopCount
    )

    private fun PresetPayloadChordsV1.sharedFields(): SharedPayload = SharedPayload(
        minimumNote = minimumNote,
        maximumNote = maximumNote,
        thinkingSilenceMs = thinkingSilenceMs,
        replayCount = replayCount,
        noteDurationMs = noteDurationMs,
        answerGapMs = answerGapMs,
        repeatGapMs = repeatGapMs,
        melodicGapMs = melodicGapMs,
        finalSilenceMs = finalSilenceMs,
        seedDecimal = seedDecimal,
        musicGain = musicGain,
        voiceGain = voiceGain,
        stopKind = stopKind,
        stopDurationMs = stopDurationMs,
        stopDurationFinish = stopDurationFinish,
        stopCount = stopCount
    )

    private fun PresetPayloadScalesV1.sharedFields(): SharedPayload = SharedPayload(
        minimumNote = minimumNote,
        maximumNote = maximumNote,
        thinkingSilenceMs = thinkingSilenceMs,
        replayCount = replayCount,
        noteDurationMs = noteDurationMs,
        answerGapMs = answerGapMs,
        repeatGapMs = repeatGapMs,
        melodicGapMs = melodicGapMs,
        finalSilenceMs = finalSilenceMs,
        seedDecimal = seedDecimal,
        musicGain = musicGain,
        voiceGain = voiceGain,
        stopKind = stopKind,
        stopDurationMs = stopDurationMs,
        stopDurationFinish = stopDurationFinish,
        stopCount = stopCount
    )

    private fun PresetPayloadNotesV1.sharedFields(): SharedPayload = SharedPayload(
        minimumNote = minimumNote,
        maximumNote = maximumNote,
        thinkingSilenceMs = thinkingSilenceMs,
        replayCount = replayCount,
        noteDurationMs = noteDurationMs,
        answerGapMs = answerGapMs,
        repeatGapMs = repeatGapMs,
        melodicGapMs = melodicGapMs,
        finalSilenceMs = finalSilenceMs,
        seedDecimal = seedDecimal,
        musicGain = musicGain,
        voiceGain = voiceGain,
        stopKind = stopKind,
        stopDurationMs = stopDurationMs,
        stopDurationFinish = stopDurationFinish,
        stopCount = stopCount
    )

    private fun PresetPayloadScaleDegreesV1.sharedFields(): SharedPayload = SharedPayload(
        minimumNote = minimumNote,
        maximumNote = maximumNote,
        thinkingSilenceMs = thinkingSilenceMs,
        replayCount = replayCount,
        noteDurationMs = noteDurationMs,
        answerGapMs = answerGapMs,
        repeatGapMs = repeatGapMs,
        melodicGapMs = melodicGapMs,
        finalSilenceMs = finalSilenceMs,
        seedDecimal = seedDecimal,
        musicGain = musicGain,
        voiceGain = voiceGain,
        stopKind = stopKind,
        stopDurationMs = stopDurationMs,
        stopDurationFinish = stopDurationFinish,
        stopCount = stopCount
    )

    private fun PresetPayloadProgressionsV1.sharedFields(): SharedPayload = SharedPayload(
        minimumNote = minimumNote,
        maximumNote = maximumNote,
        thinkingSilenceMs = thinkingSilenceMs,
        replayCount = replayCount,
        noteDurationMs = noteDurationMs,
        answerGapMs = answerGapMs,
        repeatGapMs = repeatGapMs,
        melodicGapMs = melodicGapMs,
        finalSilenceMs = finalSilenceMs,
        seedDecimal = seedDecimal,
        musicGain = musicGain,
        voiceGain = voiceGain,
        stopKind = stopKind,
        stopDurationMs = stopDurationMs,
        stopDurationFinish = stopDurationFinish,
        stopCount = stopCount
    )

    private fun PresetPayloadContextualIntervalsV1.sharedFields(): SharedPayload = SharedPayload(
        minimumNote = minimumNote,
        maximumNote = maximumNote,
        thinkingSilenceMs = thinkingSilenceMs,
        replayCount = replayCount,
        noteDurationMs = noteDurationMs,
        answerGapMs = answerGapMs,
        repeatGapMs = repeatGapMs,
        melodicGapMs = melodicGapMs,
        finalSilenceMs = finalSilenceMs,
        seedDecimal = seedDecimal,
        musicGain = musicGain,
        voiceGain = voiceGain,
        stopKind = stopKind,
        stopDurationMs = stopDurationMs,
        stopDurationFinish = stopDurationFinish,
        stopCount = stopCount
    )

    private fun PresetPayloadMelodicDictationV1.sharedFields(): SharedPayload = SharedPayload(
        minimumNote = minimumNote,
        maximumNote = maximumNote,
        thinkingSilenceMs = thinkingSilenceMs,
        replayCount = replayCount,
        noteDurationMs = noteDurationMs,
        answerGapMs = answerGapMs,
        repeatGapMs = repeatGapMs,
        melodicGapMs = melodicGapMs,
        finalSilenceMs = finalSilenceMs,
        seedDecimal = seedDecimal,
        musicGain = musicGain,
        voiceGain = voiceGain,
        stopKind = stopKind,
        stopDurationMs = stopDurationMs,
        stopDurationFinish = stopDurationFinish,
        stopCount = stopCount
    )

    /**
     * Validates a document and resolves it to its version-independent fields.
     *
     * A schema-2 kind/configVersion pair this build cannot interpret is refused, never guessed; a
     * document's declared kind selects its own typed payload, so one mode's data can never be read
     * as another's (SC7/M5).
     */
    private fun decode(document: PresetDocument): DecodedPreset = when (document) {
        is PresetDocumentV1 -> DecodedPreset(
            document.id,
            document.name,
            document.createdAtEpochMs,
            document.updatedAtEpochMs,
            DecodedPayload.Intervals(document.payload)
        )
        is PresetDocumentV2 -> {
            val kind = TrainingKind.ofIdOrNull(document.kind)
                ?: throw PresetValidationException(
                    "Preset '${document.name}' names an unknown training kind " +
                        "'${document.kind}'"
                )
            val configVersion = presetConfigVersionOrNull(kind)
                ?: throw PresetValidationException(
                    "The ${document.kind} preset cannot be read by this build yet"
                )
            if (document.configVersion != configVersion) {
                throw PresetValidationException(
                    "Preset '${document.name}' has configuration version " +
                        "${document.configVersion}; this build understands $configVersion"
                )
            }
            val payload = when (kind) {
                TrainingKind.INTERVALS -> DecodedPayload.Intervals(
                    decodePayload(document, PresetPayloadV1.serializer())
                )
                TrainingKind.CHORDS -> DecodedPayload.Chords(
                    decodePayload(document, PresetPayloadChordsV1.serializer())
                )
                TrainingKind.SCALES -> DecodedPayload.Scales(
                    decodePayload(document, PresetPayloadScalesV1.serializer())
                )
                TrainingKind.NOTE_IDENTIFICATION -> DecodedPayload.Notes(
                    decodePayload(document, PresetPayloadNotesV1.serializer())
                )
                TrainingKind.SCALE_DEGREES -> DecodedPayload.ScaleDegrees(
                    decodePayload(document, PresetPayloadScaleDegreesV1.serializer())
                )
                TrainingKind.CHORD_PROGRESSIONS -> DecodedPayload.Progressions(
                    decodePayload(document, PresetPayloadProgressionsV1.serializer())
                )
                TrainingKind.INTERVALS_IN_CONTEXT -> DecodedPayload.ContextualIntervals(
                    decodePayload(document, PresetPayloadContextualIntervalsV1.serializer())
                )
                TrainingKind.MELODIC_DICTATION -> DecodedPayload.MelodicDictation(
                    decodePayload(document, PresetPayloadMelodicDictationV1.serializer())
                )
                TrainingKind.INTERVAL_WALK -> DecodedPayload.IntervalWalk(
                    decodePayload(document, PresetPayloadIntervalWalkV1.serializer())
                )
                else -> throw PresetValidationException(
                    "The ${document.kind} preset cannot be read by this build yet"
                )
            }
            DecodedPreset(
                document.id,
                document.name,
                document.createdAtEpochMs,
                document.updatedAtEpochMs,
                payload
            )
        }
    }

    /** Decodes one stored payload element of a schema-2 document, naming the preset on failure. */
    private fun <T> decodePayload(
        document: PresetDocumentV2,
        serializer: kotlinx.serialization.KSerializer<T>
    ): T = try {
        json.decodeFromJsonElement(serializer, document.payload)
    } catch (failure: kotlinx.serialization.SerializationException) {
        throw PresetValidationException(
            "Preset '${document.name}' has an invalid payload: ${failure.message}"
        )
    }

    /** Validates one document, including that its musical constraints are satisfiable. */
    fun validate(document: PresetDocument) {
        if (document.schemaVersion > PRESET_SCHEMA_VERSION) {
            throw PresetValidationException(
                "Preset schema ${document.schemaVersion} is newer than this build"
            )
        }
        validateDecoded(decode(document))
    }

    /** Validates the version-independent fields, including satisfiable musical constraints. */
    private fun validateDecoded(decoded: DecodedPreset) {
        requireId(decoded.id)
        requireName(decoded.name)
        val payload = decoded.payload.shared
        // An empty interval or root list is deliberate: it means every option is included, exactly
        // as it does in the setup screen; the kind-specific selection is checked below.
        if (payload.minimumNote > payload.maximumNote) {
            throw PresetValidationException("Preset '${decoded.name}' has an inverted note range")
        }
        if (payload.thinkingSilenceMs < 0) {
            throw PresetValidationException(
                "Preset '${decoded.name}' has a negative thinking silence"
            )
        }
        if (payload.noteDurationMs <= 0) {
            throw PresetValidationException(
                "Preset '${decoded.name}' has a note duration of ${payload.noteDurationMs} ms"
            )
        }
        if (payload.answerGapMs < 0 || payload.repeatGapMs < 0) {
            throw PresetValidationException(
                "Preset '${decoded.name}' has a negative answer or repeat gap"
            )
        }
        if (payload.melodicGapMs < 0 || payload.finalSilenceMs < 0) {
            throw PresetValidationException(
                "Preset '${decoded.name}' has a negative melodic gap or final silence"
            )
        }
        (decoded.payload as? DecodedPayload.Intervals)?.let {
            presentationsOf(decoded.name, it.payload)
        }
        if (payload.replayCount !in 0..RecipeDefinition.MAX_REPLAY) {
            throw PresetValidationException(
                "Preset '${decoded.name}' has a replay count of ${payload.replayCount}, " +
                    "outside 0..${RecipeDefinition.MAX_REPLAY}"
            )
        }
        if (payload.musicGain !in UserSettings.MIN_GAIN..UserSettings.MAX_GAIN) {
            throw PresetValidationException(
                "Preset '${decoded.name}' has a music gain outside the range"
            )
        }
        if (payload.voiceGain !in UserSettings.MIN_GAIN..UserSettings.MAX_GAIN) {
            throw PresetValidationException(
                "Preset '${decoded.name}' has a voice gain outside the range"
            )
        }
        if (!payload.musicGain.isFinite() || !payload.voiceGain.isFinite()) {
            throw PresetValidationException("Preset '${decoded.name}' has a non-finite gain")
        }
        payload.seedOrNull()
        // The document must build the domain configuration it claims to describe.
        configurationOf(decoded)
    }

    /** Converts a document into the validated configuration a session can start. */
    fun toConfiguration(document: PresetDocument): PresetConfiguration =
        configurationOf(decode(document))

    /**
     * Converts an intervals document into the validated setup a session can start.
     *
     * @throws PresetValidationException when the document describes another kind.
     */
    fun toSetup(document: PresetDocument): LastSetup {
        val configuration = toConfiguration(document)
        return (configuration as? PresetConfiguration.Intervals)?.setup
            ?: throw PresetValidationException(
                "Preset '${document.name}' is not an intervals preset"
            )
    }

    /** The validated configuration a decoded document describes. */
    private fun configurationOf(
        decoded: DecodedPreset,
        checkFeasibility: Boolean = true
    ): PresetConfiguration = when (val payload = decoded.payload) {
        is DecodedPayload.Intervals -> PresetConfiguration.Intervals(
            intervalSetupOf(decoded.name, payload.payload, checkFeasibility)
        )
        is DecodedPayload.Chords -> PresetConfiguration.Chords(
            chordSetupOf(decoded.name, payload.payload, checkFeasibility)
        )
        is DecodedPayload.Scales -> PresetConfiguration.Scales(
            scaleSetupOf(decoded.name, payload.payload, checkFeasibility)
        )
        is DecodedPayload.Notes -> PresetConfiguration.Notes(
            noteSetupOf(decoded.name, payload.payload, checkFeasibility)
        )
        is DecodedPayload.ScaleDegrees -> PresetConfiguration.ScaleDegrees(
            scaleDegreeSetupOf(decoded.name, payload.payload, checkFeasibility)
        )
        is DecodedPayload.Progressions -> PresetConfiguration.Progressions(
            progressionSetupOf(decoded.name, payload.payload, checkFeasibility)
        )
        is DecodedPayload.ContextualIntervals -> PresetConfiguration.ContextualIntervals(
            contextualIntervalSetupOf(decoded.name, payload.payload, checkFeasibility)
        )
        is DecodedPayload.MelodicDictation -> PresetConfiguration.MelodicDictation(
            melodicDictationSetupOf(decoded.name, payload.payload, checkFeasibility)
        )
        is DecodedPayload.IntervalWalk -> PresetConfiguration.IntervalWalk(
            intervalWalkSetupOf(decoded.name, payload.payload, checkFeasibility)
        )
    }

    private fun intervalSetupOf(
        name: String,
        payload: PresetPayloadV1,
        checkFeasibility: Boolean = true
    ): LastSetup {
        val presentation =
            IntervalPresentation.entries.firstOrNull { it.name == payload.presentation }
                ?: throw PresetValidationException("Unknown presentation '${payload.presentation}'")
        val mode = RecipeMode.entries.firstOrNull { it.name == payload.mode }
            ?: throw PresetValidationException("Unknown mode '${payload.mode}'")
        val setup = try {
            LastSetup(
                intervalIds = payload.intervalIds,
                rootValues = payload.rootValues,
                minimumNote = payload.minimumNote,
                maximumNote = payload.maximumNote,
                presentation = presentation,
                mode = mode,
                thinkingSilence = kotlin.time.Duration.parse("${payload.thinkingSilenceMs}ms"),
                replayCount = payload.replayCount,
                stopPolicy = stopPolicyOf(name, payload.sharedFields()),
                noteDuration = kotlin.time.Duration.parse("${payload.noteDurationMs}ms"),
                answerGap = kotlin.time.Duration.parse("${payload.answerGapMs}ms"),
                repeatGap = kotlin.time.Duration.parse("${payload.repeatGapMs}ms"),
                fixedRoot = payload.fixedRoot,
                presentations = presentationsOf(name, payload),
                melodicGap = kotlin.time.Duration.parse("${payload.melodicGapMs}ms"),
                finalSilence = kotlin.time.Duration.parse("${payload.finalSilenceMs}ms"),
                separatorCue = payload.separatorCue
            )
        } catch (failure: IllegalArgumentException) {
            throw PresetValidationException(
                "Preset '$name' has an unusable configuration: ${failure.message}"
            )
        }
        if (!checkFeasibility) return setup
        // A setup that cannot produce a definition, or whose selected intervals cannot be produced
        // in the chosen range, direction and fixed root, is refused here rather than at Start: a
        // preset that would never play one of its intervals is not a usable preset. A fixed root
        // makes the policy version 2, so the version 2 feasibility rules apply.
        validateFeasibility(name, setup)
        return setup
    }

    /** Refuses an intervals setup that cannot produce one of its selected intervals. */
    private fun validateFeasibility(name: String, setup: LastSetup) {
        val policy = try {
            setup.toPolicy()
        } catch (failure: IllegalArgumentException) {
            throw PresetValidationException(
                "Preset '$name' has an unusable configuration: ${failure.message}"
            )
        }
        try {
            if (policy.generatorVersion == 1) {
                com.thomas.passiveeartraining.training.SeededIntervalGenerator
                    .validateFeasibility(policy.definition)
            } else {
                com.thomas.passiveeartraining.training.SeededIntervalGeneratorV2
                    .validateFeasibility(policy)
            }
        } catch (
            failure: com.thomas.passiveeartraining.training.InfeasibleIntervalDefinitionException
        ) {
            throw PresetValidationException(
                "Preset '$name' cannot produce ${failure.infeasibleLabelIds.joinToString(", ")} " +
                    "in its range and direction"
            )
        }
    }

    /**
     * Builds the chord setup a decoded payload describes.
     *
     * The setup structure is checked while it is built; when [checkFeasibility] is set, the one
     * chord validation implementation the setup screen uses is run too, so a preset can never
     * accept a selection a chord session would refuse: unknown qualities, inversions that apply
     * to no selected quality, missing voice clips or an over-long complete exercise (M5).
     */
    private fun chordSetupOf(
        name: String,
        payload: PresetPayloadChordsV1,
        checkFeasibility: Boolean
    ): ChordSetup {
        val presentation = ChordPresentation.entries.firstOrNull { it.name == payload.presentation }
            ?: throw PresetValidationException(
                "Preset '$name' has unknown chord presentation '${payload.presentation}'"
            )
        val answerMode = ChordAnswerMode.entries.firstOrNull { it.name == payload.answerMode }
            ?: throw PresetValidationException(
                "Preset '$name' has unknown chord answer mode '${payload.answerMode}'"
            )
        val mode = RecipeMode.entries.firstOrNull { it.name == payload.mode }
            ?: throw PresetValidationException("Unknown mode '${payload.mode}'")
        val setup = try {
            ChordSetup(
                qualityIds = payload.qualityIds,
                inversions = payload.inversions,
                rootValues = payload.rootValues,
                minimumNote = payload.minimumNote,
                maximumNote = payload.maximumNote,
                presentation = presentation,
                answerMode = answerMode,
                mode = mode,
                thinkingSilence = kotlin.time.Duration.parse("${payload.thinkingSilenceMs}ms"),
                replayCount = payload.replayCount,
                stopPolicy = stopPolicyOf(name, payload.sharedFields()),
                noteDuration = kotlin.time.Duration.parse("${payload.noteDurationMs}ms"),
                answerGap = kotlin.time.Duration.parse("${payload.answerGapMs}ms"),
                repeatGap = kotlin.time.Duration.parse("${payload.repeatGapMs}ms"),
                fixedRoot = payload.fixedRoot,
                melodicGap = kotlin.time.Duration.parse("${payload.melodicGapMs}ms"),
                finalSilence = kotlin.time.Duration.parse("${payload.finalSilenceMs}ms"),
                separatorCue = payload.separatorCue
            )
        } catch (failure: IllegalArgumentException) {
            throw PresetValidationException(
                "Preset '$name' has an unusable chord configuration: ${failure.message}"
            )
        }
        if (!checkFeasibility) return setup
        when (val validation = ChordSetupValidator.validate(ChordSetupDraft.from(setup))) {
            is SetupValidation.Valid -> Unit
            is SetupValidation.Invalid -> throw PresetValidationException(
                "Preset '$name' has an unusable chord configuration: " +
                    validation.issues.joinToString(" ") { it.explanation }
            )
        }
        return setup
    }

    /**
     * Builds the scale setup a decoded payload describes.
     *
     * The setup structure is checked while it is built; when [checkFeasibility] is set, the one
     * scale validation implementation the setup screen uses is run too, so a preset can never
     * accept a selection a scale session would refuse: unknown scales, an infeasible complete
     * octave, missing voice clips or an over-long complete exercise (M6).
     */
    private fun scaleSetupOf(
        name: String,
        payload: PresetPayloadScalesV1,
        checkFeasibility: Boolean
    ): ScaleSetup {
        val direction = ScaleDirection.entries.firstOrNull { it.name == payload.direction }
            ?: throw PresetValidationException(
                "Preset '$name' has unknown scale direction '${payload.direction}'"
            )
        val mode = RecipeMode.entries.firstOrNull { it.name == payload.mode }
            ?: throw PresetValidationException("Unknown mode '${payload.mode}'")
        val setup = try {
            ScaleSetup(
                scaleIds = payload.scaleIds,
                direction = direction,
                rootValues = payload.rootValues,
                minimumNote = payload.minimumNote,
                maximumNote = payload.maximumNote,
                mode = mode,
                thinkingSilence = kotlin.time.Duration.parse("${payload.thinkingSilenceMs}ms"),
                replayCount = payload.replayCount,
                stopPolicy = stopPolicyOf(name, payload.sharedFields()),
                noteDuration = kotlin.time.Duration.parse("${payload.noteDurationMs}ms"),
                answerGap = kotlin.time.Duration.parse("${payload.answerGapMs}ms"),
                repeatGap = kotlin.time.Duration.parse("${payload.repeatGapMs}ms"),
                fixedRoot = payload.fixedRoot,
                melodicGap = kotlin.time.Duration.parse("${payload.melodicGapMs}ms"),
                finalSilence = kotlin.time.Duration.parse("${payload.finalSilenceMs}ms"),
                separatorCue = payload.separatorCue
            )
        } catch (failure: IllegalArgumentException) {
            throw PresetValidationException(
                "Preset '$name' has an unusable scale configuration: ${failure.message}"
            )
        }
        if (!checkFeasibility) return setup
        when (val validation = ScaleSetupValidator.validate(ScaleSetupDraft.from(setup))) {
            is SetupValidation.Valid -> Unit
            is SetupValidation.Invalid -> throw PresetValidationException(
                "Preset '$name' has an unusable scale configuration: " +
                    validation.issues.joinToString(" ") { it.explanation }
            )
        }
        // The accepted selection is normalized to canonical catalog ids, so an imported display
        // alias (ionian/aeolian) can never be re-exported as an alias (M6.2).
        return setup.copy(scaleIds = setup.toDefinition().selectedScales.map { it.id })
    }

    /**
     * Builds the note setup a decoded payload describes.
     *
     * The setup structure is checked while it is built; when [checkFeasibility] is set, the one
     * note validation implementation the setup screen uses is run too, so a preset can never
     * accept a selection a note session would refuse: unknown classes, missing voice clips, a
     * fixed octave that leaves a class unplayable or an over-long complete exercise (M7).
     */
    private fun noteSetupOf(
        name: String,
        payload: PresetPayloadNotesV1,
        checkFeasibility: Boolean
    ): NoteSetup {
        val accidental = MusicSpellingPreference.entries
            .firstOrNull { it.name == payload.accidental }
            ?: throw PresetValidationException(
                "Preset '$name' has unknown accidental style '${payload.accidental}'"
            )
        val mode = RecipeMode.entries.firstOrNull { it.name == payload.mode }
            ?: throw PresetValidationException("Unknown mode '${payload.mode}'")
        val setup = try {
            NoteSetup(
                pitchClassValues = payload.pitchClassValues,
                minimumNote = payload.minimumNote,
                maximumNote = payload.maximumNote,
                accidental = accidental,
                speakOctave = payload.speakOctave,
                mode = mode,
                thinkingSilence = kotlin.time.Duration.parse("${payload.thinkingSilenceMs}ms"),
                replayCount = payload.replayCount,
                stopPolicy = stopPolicyOf(name, payload.sharedFields()),
                noteDuration = kotlin.time.Duration.parse("${payload.noteDurationMs}ms"),
                answerGap = kotlin.time.Duration.parse("${payload.answerGapMs}ms"),
                repeatGap = kotlin.time.Duration.parse("${payload.repeatGapMs}ms"),
                melodicGap = kotlin.time.Duration.parse("${payload.melodicGapMs}ms"),
                finalSilence = kotlin.time.Duration.parse("${payload.finalSilenceMs}ms"),
                separatorCue = payload.separatorCue
            )
        } catch (failure: IllegalArgumentException) {
            throw PresetValidationException(
                "Preset '$name' has an unusable note configuration: ${failure.message}"
            )
        }
        if (!checkFeasibility) return setup
        when (val validation = NoteSetupValidator.validate(NoteSetupDraft.from(setup))) {
            is SetupValidation.Valid -> Unit
            is SetupValidation.Invalid -> throw PresetValidationException(
                "Preset '$name' has an unusable note configuration: " +
                    validation.issues.joinToString(" ") { it.explanation }
            )
        }
        return setup
    }

    /**
     * Builds the scale-degree setup a decoded payload describes.
     *
     * The setup structure is checked while it is built; when [checkFeasibility] is set, the one
     * scale-degree validation implementation the setup screen uses is run too, so a preset can
     * never accept a selection a scale-degree session would refuse: unknown tonalities, a context
     * that cannot fit the range for a selected key and degree, missing voice clips or an over-long
     * complete exercise (M8.3). A stored answer format that named a removed movable-do or
     * degree-and-syllable variant imports as its numeric equivalent.
     */
    private fun scaleDegreeSetupOf(
        name: String,
        payload: PresetPayloadScaleDegreesV1,
        checkFeasibility: Boolean
    ): ScaleDegreeSetup {
        val tonalities = payload.tonalities.map { tonalityName ->
            Tonality.entries.firstOrNull { it.name == tonalityName }
                ?: throw PresetValidationException(
                    "Preset '$name' has unknown tonality '$tonalityName'"
                )
        }
        val context = ContextKind.entries.firstOrNull { it.name == payload.context }
            ?: throw PresetValidationException(
                "Preset '$name' has unknown context '${payload.context}'"
            )
        val answerFormat =
            DegreeAnswerFormat.entries.firstOrNull {
                it.name == migratedDegreeAnswerFormat(payload.answerFormat)
            }
                ?: throw PresetValidationException(
                    "Preset '$name' has unknown answer format '${payload.answerFormat}'"
                )
        val spelling = MusicSpellingPreference.entries.firstOrNull { it.name == payload.spelling }
            ?: throw PresetValidationException(
                "Preset '$name' has unknown spelling '${payload.spelling}'"
            )
        val mode = RecipeMode.entries.firstOrNull { it.name == payload.mode }
            ?: throw PresetValidationException("Unknown mode '${payload.mode}'")
        val setup = try {
            ScaleDegreeSetup(
                keyValues = payload.keyValues,
                tonalities = tonalities,
                degrees = payload.degrees,
                context = context,
                contextGap = kotlin.time.Duration.parse("${payload.contextGapMs}ms"),
                answerFormat = answerFormat,
                spelling = spelling,
                minimumNote = payload.minimumNote,
                maximumNote = payload.maximumNote,
                mode = mode,
                thinkingSilence = kotlin.time.Duration.parse("${payload.thinkingSilenceMs}ms"),
                replayCount = payload.replayCount,
                stopPolicy = stopPolicyOf(name, payload.sharedFields()),
                noteDuration = kotlin.time.Duration.parse("${payload.noteDurationMs}ms"),
                answerGap = kotlin.time.Duration.parse("${payload.answerGapMs}ms"),
                repeatGap = kotlin.time.Duration.parse("${payload.repeatGapMs}ms"),
                melodicGap = kotlin.time.Duration.parse("${payload.melodicGapMs}ms"),
                finalSilence = kotlin.time.Duration.parse("${payload.finalSilenceMs}ms"),
                separatorCue = payload.separatorCue
            )
        } catch (failure: IllegalArgumentException) {
            throw PresetValidationException(
                "Preset '$name' has an unusable scale-degree configuration: ${failure.message}"
            )
        }
        if (!checkFeasibility) return setup
        when (
            val validation = ScaleDegreeSetupValidator.validate(
                ScaleDegreeSetupDraft.from(setup)
            )
        ) {
            is SetupValidation.Valid -> Unit
            is SetupValidation.Invalid -> throw PresetValidationException(
                "Preset '$name' has an unusable scale-degree configuration: " +
                    validation.issues.joinToString(" ") { it.explanation }
            )
        }
        return setup
    }

    /**
     * Builds the chord-progression setup a decoded payload describes.
     *
     * The setup structure is checked while it is built; when [checkFeasibility] is set, the one
     * progression validation implementation the setup screen uses is run too, so a preset can
     * never accept a selection a progression session would refuse: unknown templates, an
     * infeasible key/template pair in the range, missing voice clips or an over-long complete
     * exercise (M9A.3). Progression answers never speak note names.
     */
    private fun progressionSetupOf(
        name: String,
        payload: PresetPayloadProgressionsV1,
        checkFeasibility: Boolean
    ): ProgressionSetup {
        val voicing = ProgressionVoicingMode.entries.firstOrNull { it.name == payload.voicing }
            ?: throw PresetValidationException(
                "Preset '$name' has unknown progression voicing '${payload.voicing}'"
            )
        val answerFormat =
            ProgressionAnswerFormat.entries.firstOrNull { it.name == payload.answerFormat }
                ?: throw PresetValidationException(
                    "Preset '$name' has unknown answer format '${payload.answerFormat}'"
                )
        val spelling = MusicSpellingPreference.entries.firstOrNull { it.name == payload.spelling }
            ?: throw PresetValidationException(
                "Preset '$name' has unknown spelling '${payload.spelling}'"
            )
        val mode = RecipeMode.entries.firstOrNull { it.name == payload.mode }
            ?: throw PresetValidationException("Unknown mode '${payload.mode}'")
        val setup = try {
            ProgressionSetup(
                // A document from the first schema has no chord pool; it reads as the seven
                // default triads instead of becoming unimportable.
                chordIds = payload.chordIds.ifEmpty {
                    com.thomas.passiveeartraining.music.DiatonicChord.TRIADS.map { it.id }
                },
                fixedRoot = payload.fixedRoot,
                voicing = voicing,
                allowedInversions = payload.allowedInversions,
                answerFormat = answerFormat,
                spelling = spelling,
                minimumNote = payload.minimumNote,
                maximumNote = payload.maximumNote,
                mode = mode,
                thinkingSilence = kotlin.time.Duration.parse("${payload.thinkingSilenceMs}ms"),
                replayCount = payload.replayCount,
                stopPolicy = stopPolicyOf(name, payload.sharedFields()),
                noteDuration = kotlin.time.Duration.parse("${payload.noteDurationMs}ms"),
                answerGap = kotlin.time.Duration.parse("${payload.answerGapMs}ms"),
                repeatGap = kotlin.time.Duration.parse("${payload.repeatGapMs}ms"),
                melodicGap = kotlin.time.Duration.parse("${payload.melodicGapMs}ms"),
                finalSilence = kotlin.time.Duration.parse("${payload.finalSilenceMs}ms"),
                separatorCue = payload.separatorCue
            )
        } catch (failure: IllegalArgumentException) {
            throw PresetValidationException(
                "Preset '$name' has an unusable chord-progression configuration: " +
                    "${failure.message}"
            )
        }
        // The mode has no label cycle now; an old "after one cycle" choice maps to Until stopped
        // instead of rejecting an otherwise usable preset.
        val normalized = if (setup.stopPolicy == SessionStopPolicy.AfterCycle) {
            setup.copy(stopPolicy = SessionStopPolicy.UntilStopped)
        } else {
            setup
        }
        if (!checkFeasibility) return normalized
        when (
            val validation = ProgressionSetupValidator.validate(
                ProgressionSetupDraft.from(normalized)
            )
        ) {
            is SetupValidation.Valid -> Unit
            is SetupValidation.Invalid -> throw PresetValidationException(
                "Preset '$name' has an unusable chord-progression configuration: " +
                    validation.issues.joinToString(" ") { it.explanation }
            )
        }
        return normalized
    }

    /**
     * Builds the intervals-in-context setup a decoded payload describes.
     *
     * The setup structure is checked while it is built; when [checkFeasibility] is set, the one
     * contextual-interval validation implementation the setup screen uses is run too, so a preset
     * can never accept a selection a session would refuse: unknown intervals, a presentation no
     * selected key can realize in the range, missing voice clips or an over-long complete exercise
     * (M9B.2). Contextual-interval answers never speak note names.
     */
    private fun contextualIntervalSetupOf(
        name: String,
        payload: PresetPayloadContextualIntervalsV1,
        checkFeasibility: Boolean
    ): ContextualIntervalSetup {
        val tonalities = payload.tonalities.map { tonalityName ->
            Tonality.entries.firstOrNull { it.name == tonalityName }
                ?: throw PresetValidationException(
                    "Preset '$name' has unknown tonality '$tonalityName'"
                )
        }
        val presentation =
            IntervalPresentation.entries.firstOrNull { it.name == payload.presentation }
                ?: throw PresetValidationException(
                    "Preset '$name' has unknown interval presentation '${payload.presentation}'"
                )
        val context = ContextKind.entries.firstOrNull { it.name == payload.context }
            ?: throw PresetValidationException(
                "Preset '$name' has unknown context '${payload.context}'"
            )
        val answerFormat = ContextualIntervalAnswerFormat.entries
            .firstOrNull { it.name == payload.answerFormat }
            ?: throw PresetValidationException(
                "Preset '$name' has unknown answer format '${payload.answerFormat}'"
            )
        val spelling = MusicSpellingPreference.entries.firstOrNull { it.name == payload.spelling }
            ?: throw PresetValidationException(
                "Preset '$name' has unknown spelling '${payload.spelling}'"
            )
        val mode = RecipeMode.entries.firstOrNull { it.name == payload.mode }
            ?: throw PresetValidationException("Unknown mode '${payload.mode}'")
        val setup = try {
            ContextualIntervalSetup(
                keyValues = payload.keyValues,
                tonalities = tonalities,
                intervalIds = payload.intervalIds,
                presentation = presentation,
                context = context,
                contextGap = kotlin.time.Duration.parse("${payload.contextGapMs}ms"),
                answerFormat = answerFormat,
                spelling = spelling,
                minimumNote = payload.minimumNote,
                maximumNote = payload.maximumNote,
                mode = mode,
                thinkingSilence = kotlin.time.Duration.parse("${payload.thinkingSilenceMs}ms"),
                replayCount = payload.replayCount,
                stopPolicy = stopPolicyOf(name, payload.sharedFields()),
                noteDuration = kotlin.time.Duration.parse("${payload.noteDurationMs}ms"),
                answerGap = kotlin.time.Duration.parse("${payload.answerGapMs}ms"),
                repeatGap = kotlin.time.Duration.parse("${payload.repeatGapMs}ms"),
                melodicGap = kotlin.time.Duration.parse("${payload.melodicGapMs}ms"),
                finalSilence = kotlin.time.Duration.parse("${payload.finalSilenceMs}ms"),
                separatorCue = payload.separatorCue
            )
        } catch (failure: IllegalArgumentException) {
            throw PresetValidationException(
                "Preset '$name' has an unusable intervals-in-context configuration: " +
                    "${failure.message}"
            )
        }
        if (!checkFeasibility) return setup
        when (
            val validation = ContextualIntervalSetupValidator.validate(
                ContextualIntervalSetupDraft.from(setup)
            )
        ) {
            is SetupValidation.Valid -> Unit
            is SetupValidation.Invalid -> throw PresetValidationException(
                "Preset '$name' has an unusable intervals-in-context configuration: " +
                    validation.issues.joinToString(" ") { it.explanation }
            )
        }
        return setup
    }

    /** Builds the interval-walk setup a decoded payload describes. */
    private fun intervalWalkSetupOf(
        name: String,
        payload: PresetPayloadIntervalWalkV1,
        checkFeasibility: Boolean
    ): IntervalWalkSetup {
        val direction = WalkDirection.entries.firstOrNull { it.name == payload.direction }
            ?: throw PresetValidationException(
                "Preset '$name' has unknown walk direction '${payload.direction}'"
            )
        val mode = RecipeMode.entries.firstOrNull { it.name == payload.mode }
            ?: throw PresetValidationException("Unknown mode '${payload.mode}'")
        val draft = try {
            IntervalWalkSetupDraft(
                intervalIds = payload.intervalIds,
                rootValues = payload.rootValues,
                minimumNote = MidiNote.of(payload.minimumNote),
                maximumNote = MidiNote.of(payload.maximumNote),
                fixedStart = payload.fixedStart?.let(MidiNote::of),
                direction = direction,
                length = payload.length,
                mode = mode,
                thinkingSilence = kotlin.time.Duration.parse("${payload.thinkingSilenceMs}ms"),
                replayCount = payload.replayCount,
                stopPolicy = stopPolicyOf(name, payload.sharedFields()),
                noteDuration = kotlin.time.Duration.parse("${payload.noteDurationMs}ms"),
                answerGap = kotlin.time.Duration.parse("${payload.answerGapMs}ms"),
                repeatGap = kotlin.time.Duration.parse("${payload.repeatGapMs}ms"),
                melodicGap = kotlin.time.Duration.parse("${payload.melodicGapMs}ms"),
                finalSilence = kotlin.time.Duration.parse("${payload.finalSilenceMs}ms"),
                seed = payload.seedOrNull(),
                separatorCue = payload.separatorCue
            )
        } catch (failure: IllegalArgumentException) {
            throw PresetValidationException(
                "Preset '$name' has an unusable interval-walk configuration: " +
                    "${failure.message}"
            )
        }
        if (!checkFeasibility) return draft.toSetup()
        when (val validation = IntervalWalkSetupValidator.validate(draft)) {
            is SetupValidation.Valid -> Unit
            is SetupValidation.Invalid -> throw PresetValidationException(
                "Preset '$name' has an unusable interval-walk configuration: " +
                    validation.issues.joinToString(" ") { it.explanation }
            )
        }
        return draft.toSetup()
    }

    /**
     * Builds the melodic-dictation setup a decoded payload describes.
     *
     * The setup structure is checked while it is built; when [checkFeasibility] is set, the one
     * melodic-dictation validation implementation the setup screen uses is run too, so a preset can
     * never accept a selection a session would refuse: an impossible phrase for a selected key, a
     * range that cannot hold the context or an endpoint, missing answer clips, an over-long
     * complete exercise or a cycle stop policy the mode cannot honour (M10). A stored answer format
     * that named the removed movable-do sequence imports as its numeric equivalent.
     */
    private fun melodicDictationSetupOf(
        name: String,
        payload: PresetPayloadMelodicDictationV1,
        checkFeasibility: Boolean
    ): MelodicDictationSetup {
        val tonalities = payload.tonalities.map { tonalityName ->
            Tonality.entries.firstOrNull { it.name == tonalityName }
                ?: throw PresetValidationException(
                    "Preset '$name' has unknown tonality '$tonalityName'"
                )
        }
        val context = ContextKind.entries.firstOrNull { it.name == payload.context }
            ?: throw PresetValidationException(
                "Preset '$name' has unknown context '${payload.context}'"
            )
        val bias = MelodicDirectionBias.entries.firstOrNull { it.name == payload.bias }
            ?: throw PresetValidationException(
                "Preset '$name' has unknown direction bias '${payload.bias}'"
            )
        val answerFormat =
            MelodicAnswerFormat.entries.firstOrNull {
                it.name == migratedMelodicAnswerFormat(payload.answerFormat)
            }
                ?: throw PresetValidationException(
                    "Preset '$name' has unknown answer format '${payload.answerFormat}'"
                )
        val spelling = MusicSpellingPreference.entries.firstOrNull { it.name == payload.spelling }
            ?: throw PresetValidationException(
                "Preset '$name' has unknown spelling '${payload.spelling}'"
            )
        val mode = RecipeMode.entries.firstOrNull { it.name == payload.mode }
            ?: throw PresetValidationException("Unknown mode '${payload.mode}'")
        val setup = try {
            MelodicDictationSetup(
                keyValues = payload.keyValues,
                tonalities = tonalities,
                context = context,
                contextGap = kotlin.time.Duration.parse("${payload.contextGapMs}ms"),
                minimumNote = MidiNote.of(payload.minimumNote),
                maximumNote = MidiNote.of(payload.maximumNote),
                phraseLength = payload.phraseLength,
                startDegree = payload.startDegree,
                endDegree = payload.endDegree,
                allowedSteps = payload.allowedSteps,
                maximumLeap = payload.maximumLeap,
                bias = bias,
                answerFormat = answerFormat,
                spelling = spelling,
                mode = mode,
                thinkingSilence = kotlin.time.Duration.parse("${payload.thinkingSilenceMs}ms"),
                replayCount = payload.replayCount,
                stopPolicy = stopPolicyOf(name, payload.sharedFields()),
                noteDuration = kotlin.time.Duration.parse("${payload.noteDurationMs}ms"),
                answerGap = kotlin.time.Duration.parse("${payload.answerGapMs}ms"),
                repeatGap = kotlin.time.Duration.parse("${payload.repeatGapMs}ms"),
                melodicGap = kotlin.time.Duration.parse("${payload.melodicGapMs}ms"),
                finalSilence = kotlin.time.Duration.parse("${payload.finalSilenceMs}ms"),
                separatorCue = payload.separatorCue
            )
        } catch (failure: IllegalArgumentException) {
            throw PresetValidationException(
                "Preset '$name' has an unusable melodic-dictation configuration: " +
                    "${failure.message}"
            )
        }
        if (!checkFeasibility) return setup
        when (
            val validation = MelodicDictationSetupValidator.validate(
                MelodicDictationSetupDraft.from(setup)
            )
        ) {
            is SetupValidation.Valid -> Unit
            is SetupValidation.Invalid -> throw PresetValidationException(
                "Preset '$name' has an unusable melodic-dictation configuration: " +
                    validation.issues.joinToString(" ") { it.explanation }
            )
        }
        return setup
    }

    /** Converts a preset into its stored schema-2 document, whichever kind it describes. */
    fun toDocument(preset: Preset): PresetDocumentV2 {
        requireName(preset.name)
        val kind = preset.configuration.kind
        return PresetDocumentV2(
            id = preset.id.value,
            name = preset.name.trim(),
            createdAtEpochMs = preset.createdAtEpochMs,
            updatedAtEpochMs = preset.updatedAtEpochMs,
            kind = kind.id,
            configVersion = presetConfigVersionOrNull(kind)
                ?: throw PresetValidationException(
                    "Training kind '${kind.id}' has no stored configuration version"
                ),
            payload = encodePresetPayload(preset)
        )
    }

    /** Encodes one preset's configuration as the stored schema-2 payload, whichever it is. */
    private fun encodePresetPayload(preset: Preset): JsonObject =
        when (val configuration = preset.configuration) {
            is PresetConfiguration.Intervals ->
                encodePayload(intervalPayloadOf(configuration, preset))
            is PresetConfiguration.Chords ->
                encodePayload(chordPayloadOf(configuration, preset))
            is PresetConfiguration.Scales ->
                encodePayload(scalesPayloadOf(configuration, preset))
            is PresetConfiguration.Notes ->
                encodePayload(notesPayloadOf(configuration, preset))
            is PresetConfiguration.ScaleDegrees ->
                encodePayload(scaleDegreesPayloadOf(configuration, preset))
            is PresetConfiguration.Progressions ->
                encodePayload(progressionsPayloadOf(configuration, preset))
            is PresetConfiguration.ContextualIntervals ->
                encodePayload(contextualIntervalsPayloadOf(configuration, preset))
            is PresetConfiguration.MelodicDictation ->
                encodePayload(melodicDictationPayloadOf(configuration, preset))
            is PresetConfiguration.IntervalWalk ->
                encodePayload(intervalWalkPayloadOf(configuration, preset))
        }

    /** Encodes one preset's payload as the stored JSON text, whichever kind it describes. */
    fun payloadJson(preset: Preset): String = encodePresetPayload(preset).toString()

    /**
     * Encodes one intervals preset's payload as the stored JSON text.
     *
     * Kept separate because the schema-9 migration writes its converted legacy rows with exactly
     * this interval payload.
     */
    fun intervalPayloadJson(preset: Preset): String {
        val configuration = preset.configuration as? PresetConfiguration.Intervals
            ?: throw PresetValidationException(
                "Preset '${preset.name}' is not an intervals preset"
            )
        return encodePayload(intervalPayloadOf(configuration, preset)).toString()
    }

    /**
     * Reads a stored schema-2 payload without Start-time feasibility validation.
     *
     * Loading a preset library must not become impossible because a legacy row would fail a
     * validation that only matters when the preset is actually applied; the structure, enum names,
     * durations and stop policy are still checked strictly. The row's own [kind] and
     * [configVersion] select the codec, so a stored row can only ever be read as the kind it was
     * written as (SC7/M5).
     */
    fun storedPreset(
        id: String,
        name: String,
        createdAtEpochMs: Long,
        updatedAtEpochMs: Long,
        payloadJson: String,
        builtIn: Boolean,
        kind: TrainingKind,
        configVersion: Int,
        lastUsedAtEpochMs: Long = 0
    ): Preset {
        val payload = try {
            json.parseToJsonElement(payloadJson).jsonObject
        } catch (failure: kotlinx.serialization.SerializationException) {
            throw PresetValidationException("Stored payload is not valid JSON: ${failure.message}")
        } catch (failure: IllegalArgumentException) {
            throw PresetValidationException("Stored payload is not a JSON object")
        }
        val document = PresetDocumentV2(
            id = id,
            name = name,
            createdAtEpochMs = createdAtEpochMs,
            updatedAtEpochMs = updatedAtEpochMs,
            kind = kind.id,
            configVersion = configVersion,
            payload = payload
        )
        val decoded = decode(document)
        val shared = decoded.payload.shared
        return Preset(
            id = PresetId(decoded.id),
            name = decoded.name.trim(),
            createdAtEpochMs = decoded.createdAtEpochMs,
            updatedAtEpochMs = decoded.updatedAtEpochMs,
            lastUsedAtEpochMs = lastUsedAtEpochMs,
            configuration = configurationOf(decoded, checkFeasibility = false),
            seed = shared.seedOrNull(),
            musicGain = shared.musicGain,
            voiceGain = shared.voiceGain,
            builtIn = builtIn
        )
    }

    /** The stored stop-policy fields both payloads share. */
    private data class StopFields(
        val kind: String,
        val durationMs: Long?,
        val durationFinish: String?,
        val count: Int?
    )

    /** Encodes one selected stop policy as the fields every payload stores. */
    private fun stopFieldsOf(policy: SessionStopPolicy): StopFields = StopFields(
        kind = when (policy) {
            SessionStopPolicy.UntilStopped -> STOP_KIND_UNTIL_STOPPED
            SessionStopPolicy.AfterCycle -> STOP_KIND_AFTER_CYCLE
            is SessionStopPolicy.Duration -> STOP_KIND_DURATION
            is SessionStopPolicy.CompletedItems -> STOP_KIND_COMPLETED_ITEMS
        },
        durationMs = (policy as? SessionStopPolicy.Duration)?.limit?.inWholeMilliseconds,
        durationFinish = (policy as? SessionStopPolicy.Duration)?.finish?.name,
        count = (policy as? SessionStopPolicy.CompletedItems)?.count
    )

    private fun intervalPayloadOf(
        configuration: PresetConfiguration.Intervals,
        preset: Preset
    ): PresetPayloadV1 {
        val setup = configuration.setup
        val stop = stopFieldsOf(setup.stopPolicy)
        return PresetPayloadV1(
            intervalIds = setup.intervalIds,
            rootValues = setup.rootValues,
            minimumNote = setup.minimumNote,
            maximumNote = setup.maximumNote,
            presentation = setup.presentation.name,
            mode = setup.mode.name,
            thinkingSilenceMs = setup.thinkingSilence.inWholeMilliseconds,
            replayCount = setup.replayCount,
            noteDurationMs = setup.noteDuration.inWholeMilliseconds,
            answerGapMs = setup.answerGap.inWholeMilliseconds,
            repeatGapMs = setup.repeatGap.inWholeMilliseconds,
            fixedRoot = setup.fixedRoot,
            presentations = setup.presentations
                ?.takeIf { it.size > 1 }
                ?.map { it.name },
            melodicGapMs = setup.melodicGap.inWholeMilliseconds,
            finalSilenceMs = setup.finalSilence.inWholeMilliseconds,
            separatorCue = setup.separatorCue,
            seedDecimal = preset.seed?.toString(),
            musicGain = preset.musicGain,
            voiceGain = preset.voiceGain,
            stopKind = stop.kind,
            stopDurationMs = stop.durationMs,
            stopDurationFinish = stop.durationFinish,
            stopCount = stop.count
        )
    }

    private fun chordPayloadOf(
        configuration: PresetConfiguration.Chords,
        preset: Preset
    ): PresetPayloadChordsV1 {
        val setup = configuration.setup
        val stop = stopFieldsOf(setup.stopPolicy)
        return PresetPayloadChordsV1(
            qualityIds = setup.qualityIds,
            inversions = setup.inversions,
            rootValues = setup.rootValues,
            minimumNote = setup.minimumNote,
            maximumNote = setup.maximumNote,
            presentation = setup.presentation.name,
            answerMode = setup.answerMode.name,
            mode = setup.mode.name,
            thinkingSilenceMs = setup.thinkingSilence.inWholeMilliseconds,
            replayCount = setup.replayCount,
            noteDurationMs = setup.noteDuration.inWholeMilliseconds,
            answerGapMs = setup.answerGap.inWholeMilliseconds,
            repeatGapMs = setup.repeatGap.inWholeMilliseconds,
            fixedRoot = setup.fixedRoot,
            melodicGapMs = setup.melodicGap.inWholeMilliseconds,
            finalSilenceMs = setup.finalSilence.inWholeMilliseconds,
            separatorCue = setup.separatorCue,
            seedDecimal = preset.seed?.toString(),
            musicGain = preset.musicGain,
            voiceGain = preset.voiceGain,
            stopKind = stop.kind,
            stopDurationMs = stop.durationMs,
            stopDurationFinish = stop.durationFinish,
            stopCount = stop.count
        )
    }

    private fun scalesPayloadOf(
        configuration: PresetConfiguration.Scales,
        preset: Preset
    ): PresetPayloadScalesV1 {
        val setup = configuration.setup
        val stop = stopFieldsOf(setup.stopPolicy)
        return PresetPayloadScalesV1(
            scaleIds = setup.scaleIds,
            direction = setup.direction.name,
            rootValues = setup.rootValues,
            minimumNote = setup.minimumNote,
            maximumNote = setup.maximumNote,
            mode = setup.mode.name,
            thinkingSilenceMs = setup.thinkingSilence.inWholeMilliseconds,
            replayCount = setup.replayCount,
            noteDurationMs = setup.noteDuration.inWholeMilliseconds,
            answerGapMs = setup.answerGap.inWholeMilliseconds,
            repeatGapMs = setup.repeatGap.inWholeMilliseconds,
            fixedRoot = setup.fixedRoot,
            melodicGapMs = setup.melodicGap.inWholeMilliseconds,
            finalSilenceMs = setup.finalSilence.inWholeMilliseconds,
            separatorCue = setup.separatorCue,
            seedDecimal = preset.seed?.toString(),
            musicGain = preset.musicGain,
            voiceGain = preset.voiceGain,
            stopKind = stop.kind,
            stopDurationMs = stop.durationMs,
            stopDurationFinish = stop.durationFinish,
            stopCount = stop.count
        )
    }

    private fun notesPayloadOf(
        configuration: PresetConfiguration.Notes,
        preset: Preset
    ): PresetPayloadNotesV1 {
        val setup = configuration.setup
        val stop = stopFieldsOf(setup.stopPolicy)
        return PresetPayloadNotesV1(
            pitchClassValues = setup.pitchClassValues,
            minimumNote = setup.minimumNote,
            maximumNote = setup.maximumNote,
            accidental = setup.accidental.name,
            speakOctave = setup.speakOctave,
            fixedOctave = null,
            mode = setup.mode.name,
            thinkingSilenceMs = setup.thinkingSilence.inWholeMilliseconds,
            replayCount = setup.replayCount,
            noteDurationMs = setup.noteDuration.inWholeMilliseconds,
            answerGapMs = setup.answerGap.inWholeMilliseconds,
            repeatGapMs = setup.repeatGap.inWholeMilliseconds,
            melodicGapMs = setup.melodicGap.inWholeMilliseconds,
            finalSilenceMs = setup.finalSilence.inWholeMilliseconds,
            separatorCue = setup.separatorCue,
            seedDecimal = preset.seed?.toString(),
            musicGain = preset.musicGain,
            voiceGain = preset.voiceGain,
            stopKind = stop.kind,
            stopDurationMs = stop.durationMs,
            stopDurationFinish = stop.durationFinish,
            stopCount = stop.count
        )
    }

    private fun scaleDegreesPayloadOf(
        configuration: PresetConfiguration.ScaleDegrees,
        preset: Preset
    ): PresetPayloadScaleDegreesV1 {
        val setup = configuration.setup
        val stop = stopFieldsOf(setup.stopPolicy)
        return PresetPayloadScaleDegreesV1(
            keyValues = setup.keyValues,
            tonalities = setup.tonalities.map { it.name },
            degrees = setup.degrees,
            context = setup.context.name,
            contextGapMs = setup.contextGap.inWholeMilliseconds,
            answerFormat = setup.answerFormat.name,
            spelling = setup.spelling.name,
            minimumNote = setup.minimumNote,
            maximumNote = setup.maximumNote,
            mode = setup.mode.name,
            thinkingSilenceMs = setup.thinkingSilence.inWholeMilliseconds,
            replayCount = setup.replayCount,
            noteDurationMs = setup.noteDuration.inWholeMilliseconds,
            answerGapMs = setup.answerGap.inWholeMilliseconds,
            repeatGapMs = setup.repeatGap.inWholeMilliseconds,
            melodicGapMs = setup.melodicGap.inWholeMilliseconds,
            finalSilenceMs = setup.finalSilence.inWholeMilliseconds,
            separatorCue = setup.separatorCue,
            seedDecimal = preset.seed?.toString(),
            musicGain = preset.musicGain,
            voiceGain = preset.voiceGain,
            stopKind = stop.kind,
            stopDurationMs = stop.durationMs,
            stopDurationFinish = stop.durationFinish,
            stopCount = stop.count
        )
    }

    private fun intervalWalkPayloadOf(
        configuration: PresetConfiguration.IntervalWalk,
        preset: Preset
    ): PresetPayloadIntervalWalkV1 {
        val setup = configuration.setup
        val stop = stopFieldsOf(setup.stopPolicy)
        return PresetPayloadIntervalWalkV1(
            intervalIds = setup.intervalIds,
            rootValues = setup.rootValues,
            minimumNote = setup.minimumNote.value,
            maximumNote = setup.maximumNote.value,
            fixedStart = setup.fixedStart?.value,
            direction = setup.direction.name,
            length = setup.length,
            mode = setup.mode.name,
            thinkingSilenceMs = setup.thinkingSilence.inWholeMilliseconds,
            replayCount = setup.replayCount,
            noteDurationMs = setup.noteDuration.inWholeMilliseconds,
            answerGapMs = setup.answerGap.inWholeMilliseconds,
            repeatGapMs = setup.repeatGap.inWholeMilliseconds,
            melodicGapMs = setup.melodicGap.inWholeMilliseconds,
            finalSilenceMs = setup.finalSilence.inWholeMilliseconds,
            separatorCue = setup.separatorCue,
            seedDecimal = preset.seed?.toString(),
            musicGain = preset.musicGain,
            voiceGain = preset.voiceGain,
            stopKind = stop.kind,
            stopDurationMs = stop.durationMs,
            stopDurationFinish = stop.durationFinish,
            stopCount = stop.count
        )
    }

    private fun progressionsPayloadOf(
        configuration: PresetConfiguration.Progressions,
        preset: Preset
    ): PresetPayloadProgressionsV1 {
        val setup = configuration.setup
        val stop = stopFieldsOf(setup.stopPolicy)
        return PresetPayloadProgressionsV1(
            keyValues = emptyList(),
            templateIds = emptyList(),
            chordIds = setup.chordIds,
            fixedRoot = setup.fixedRoot,
            voicing = setup.voicing.name,
            allowedInversions = setup.allowedInversions,
            answerFormat = setup.answerFormat.name,
            spelling = setup.spelling.name,
            minimumNote = setup.minimumNote,
            maximumNote = setup.maximumNote,
            mode = setup.mode.name,
            thinkingSilenceMs = setup.thinkingSilence.inWholeMilliseconds,
            replayCount = setup.replayCount,
            noteDurationMs = setup.noteDuration.inWholeMilliseconds,
            answerGapMs = setup.answerGap.inWholeMilliseconds,
            repeatGapMs = setup.repeatGap.inWholeMilliseconds,
            melodicGapMs = setup.melodicGap.inWholeMilliseconds,
            finalSilenceMs = setup.finalSilence.inWholeMilliseconds,
            separatorCue = setup.separatorCue,
            seedDecimal = preset.seed?.toString(),
            musicGain = preset.musicGain,
            voiceGain = preset.voiceGain,
            stopKind = stop.kind,
            stopDurationMs = stop.durationMs,
            stopDurationFinish = stop.durationFinish,
            stopCount = stop.count
        )
    }

    private fun contextualIntervalsPayloadOf(
        configuration: PresetConfiguration.ContextualIntervals,
        preset: Preset
    ): PresetPayloadContextualIntervalsV1 {
        val setup = configuration.setup
        val stop = stopFieldsOf(setup.stopPolicy)
        return PresetPayloadContextualIntervalsV1(
            keyValues = setup.keyValues,
            tonalities = setup.tonalities.map { it.name },
            intervalIds = setup.intervalIds,
            presentation = setup.presentation.name,
            context = setup.context.name,
            contextGapMs = setup.contextGap.inWholeMilliseconds,
            answerFormat = setup.answerFormat.name,
            spelling = setup.spelling.name,
            minimumNote = setup.minimumNote,
            maximumNote = setup.maximumNote,
            mode = setup.mode.name,
            thinkingSilenceMs = setup.thinkingSilence.inWholeMilliseconds,
            replayCount = setup.replayCount,
            noteDurationMs = setup.noteDuration.inWholeMilliseconds,
            answerGapMs = setup.answerGap.inWholeMilliseconds,
            repeatGapMs = setup.repeatGap.inWholeMilliseconds,
            melodicGapMs = setup.melodicGap.inWholeMilliseconds,
            finalSilenceMs = setup.finalSilence.inWholeMilliseconds,
            separatorCue = setup.separatorCue,
            seedDecimal = preset.seed?.toString(),
            musicGain = preset.musicGain,
            voiceGain = preset.voiceGain,
            stopKind = stop.kind,
            stopDurationMs = stop.durationMs,
            stopDurationFinish = stop.durationFinish,
            stopCount = stop.count
        )
    }

    private fun melodicDictationPayloadOf(
        configuration: PresetConfiguration.MelodicDictation,
        preset: Preset
    ): PresetPayloadMelodicDictationV1 {
        val setup = configuration.setup
        val stop = stopFieldsOf(setup.stopPolicy)
        return PresetPayloadMelodicDictationV1(
            keyValues = setup.keyValues,
            tonalities = setup.tonalities.map { it.name },
            context = setup.context.name,
            contextGapMs = setup.contextGap.inWholeMilliseconds,
            phraseLength = setup.phraseLength,
            startDegree = setup.startDegree,
            endDegree = setup.endDegree,
            allowedSteps = setup.allowedSteps,
            maximumLeap = setup.maximumLeap,
            bias = setup.bias.name,
            answerFormat = setup.answerFormat.name,
            spelling = setup.spelling.name,
            minimumNote = setup.minimumNote.value,
            maximumNote = setup.maximumNote.value,
            mode = setup.mode.name,
            thinkingSilenceMs = setup.thinkingSilence.inWholeMilliseconds,
            replayCount = setup.replayCount,
            noteDurationMs = setup.noteDuration.inWholeMilliseconds,
            answerGapMs = setup.answerGap.inWholeMilliseconds,
            repeatGapMs = setup.repeatGap.inWholeMilliseconds,
            melodicGapMs = setup.melodicGap.inWholeMilliseconds,
            finalSilenceMs = setup.finalSilence.inWholeMilliseconds,
            separatorCue = setup.separatorCue,
            seedDecimal = preset.seed?.toString(),
            musicGain = preset.musicGain,
            voiceGain = preset.voiceGain,
            stopKind = stop.kind,
            stopDurationMs = stop.durationMs,
            stopDurationFinish = stop.durationFinish,
            stopCount = stop.count
        )
    }

    /** Builds a preset from a document, choosing a new identity when [id] is supplied. */
    fun toPreset(document: PresetDocument, id: PresetId? = null, builtIn: Boolean = false): Preset {
        validate(document)
        val decoded = decode(document)
        val shared = decoded.payload.shared
        return Preset(
            id = id ?: PresetId(decoded.id),
            name = decoded.name.trim(),
            createdAtEpochMs = decoded.createdAtEpochMs,
            updatedAtEpochMs = decoded.updatedAtEpochMs,
            configuration = configurationOf(decoded),
            seed = shared.seedOrNull(),
            musicGain = shared.musicGain,
            voiceGain = shared.voiceGain,
            builtIn = builtIn
        )
    }

    /**
     * The typed interval payload of a supported intervals document.
     *
     * Used by fixture builders and previews; every kind/configVersion this build cannot interpret
     * already failed in [validate], and a document of another kind is refused rather than guessed.
     */
    fun intervalPayload(document: PresetDocument): PresetPayloadV1 =
        (decode(document).payload as? DecodedPayload.Intervals)?.payload
            ?: throw PresetValidationException(
                "Preset '${document.name}' is not an intervals document"
            )

    /** The typed chords payload of a supported chords document (M5). */
    fun chordPayload(document: PresetDocument): PresetPayloadChordsV1 =
        (decode(document).payload as? DecodedPayload.Chords)?.payload
            ?: throw PresetValidationException(
                "Preset '${document.name}' is not a chords document"
            )

    /** The typed scales payload of a supported scales document (M6). */
    fun scalesPayload(document: PresetDocument): PresetPayloadScalesV1 =
        (decode(document).payload as? DecodedPayload.Scales)?.payload
            ?: throw PresetValidationException(
                "Preset '${document.name}' is not a scales document"
            )

    /** The typed notes payload of a supported note-identification document (M7). */
    fun notesPayload(document: PresetDocument): PresetPayloadNotesV1 =
        (decode(document).payload as? DecodedPayload.Notes)?.payload
            ?: throw PresetValidationException(
                "Preset '${document.name}' is not a note-identification document"
            )

    /** The typed scale-degrees payload of a supported scale-degrees document (M8). */
    fun scaleDegreesPayload(document: PresetDocument): PresetPayloadScaleDegreesV1 =
        (decode(document).payload as? DecodedPayload.ScaleDegrees)?.payload
            ?: throw PresetValidationException(
                "Preset '${document.name}' is not a scale-degrees document"
            )

    /** The typed chord-progressions payload of a supported progressions document (M9A). */
    fun progressionsPayload(document: PresetDocument): PresetPayloadProgressionsV1 =
        (decode(document).payload as? DecodedPayload.Progressions)?.payload
            ?: throw PresetValidationException(
                "Preset '${document.name}' is not a chord-progressions document"
            )

    /** The typed intervals-in-context payload of a supported contextual-interval document (M9B). */
    fun contextualIntervalsPayload(document: PresetDocument): PresetPayloadContextualIntervalsV1 =
        (decode(document).payload as? DecodedPayload.ContextualIntervals)?.payload
            ?: throw PresetValidationException(
                "Preset '${document.name}' is not an intervals-in-context document"
            )

    /** The typed melodic-dictation payload of a supported melodic-dictation document (M10). */
    fun melodicDictationPayload(document: PresetDocument): PresetPayloadMelodicDictationV1 =
        (decode(document).payload as? DecodedPayload.MelodicDictation)?.payload
            ?: throw PresetValidationException(
                "Preset '${document.name}' is not a melodic-dictation document"
            )

    /** Encodes one typed interval payload as the schema-2 payload element. */
    fun encodePayload(payload: PresetPayloadV1): JsonObject =
        json.encodeToJsonElement(PresetPayloadV1.serializer(), payload).jsonObject

    /** Encodes one typed chords payload as the schema-2 payload element (M5). */
    fun encodePayload(payload: PresetPayloadChordsV1): JsonObject =
        json.encodeToJsonElement(PresetPayloadChordsV1.serializer(), payload).jsonObject

    /** Encodes one typed scales payload as the schema-2 payload element (M6). */
    fun encodePayload(payload: PresetPayloadScalesV1): JsonObject =
        json.encodeToJsonElement(PresetPayloadScalesV1.serializer(), payload).jsonObject

    /** Encodes one typed notes payload as the schema-2 payload element (M7). */
    fun encodePayload(payload: PresetPayloadNotesV1): JsonObject =
        json.encodeToJsonElement(PresetPayloadNotesV1.serializer(), payload).jsonObject

    /** Encodes one typed scale-degrees payload as the schema-2 payload element (M8). */
    fun encodePayload(payload: PresetPayloadScaleDegreesV1): JsonObject =
        json.encodeToJsonElement(PresetPayloadScaleDegreesV1.serializer(), payload).jsonObject

    /** Encodes one typed chord-progressions payload as the schema-2 payload element (M9A). */
    fun encodePayload(payload: PresetPayloadProgressionsV1): JsonObject =
        json.encodeToJsonElement(PresetPayloadProgressionsV1.serializer(), payload).jsonObject

    /** Encodes one typed intervals-in-context payload as the schema-2 payload element (M9B). */
    fun encodePayload(payload: PresetPayloadContextualIntervalsV1): JsonObject =
        json.encodeToJsonElement(
            PresetPayloadContextualIntervalsV1.serializer(),
            payload
        ).jsonObject

    /** Encodes one typed melodic-dictation payload as the schema-2 payload element (M10). */
    fun encodePayload(payload: PresetPayloadMelodicDictationV1): JsonObject =
        json.encodeToJsonElement(
            PresetPayloadMelodicDictationV1.serializer(),
            payload
        ).jsonObject

    /** Encodes one typed interval-walk payload as the schema-2 payload element. */
    fun encodePayload(payload: PresetPayloadIntervalWalkV1): JsonObject = json.encodeToJsonElement(
        PresetPayloadIntervalWalkV1.serializer(),
        payload
    ).jsonObject

    /** Builds a schema-2 interval document with a supplied payload, for fixtures and previews. */
    fun toDocument(preset: Preset, payload: PresetPayloadV1): PresetDocumentV2 =
        toDocument(preset).copy(payload = encodePayload(payload))

    /** Builds a schema-2 chords document with a supplied payload, for fixtures and previews. */
    fun toDocument(preset: Preset, payload: PresetPayloadChordsV1): PresetDocumentV2 =
        toDocument(preset).copy(payload = encodePayload(payload))

    /** Builds a schema-2 scales document with a supplied payload, for fixtures and previews. */
    fun toDocument(preset: Preset, payload: PresetPayloadScalesV1): PresetDocumentV2 =
        toDocument(preset).copy(payload = encodePayload(payload))

    /** Builds a schema-2 notes document with a supplied payload, for fixtures and previews. */
    fun toDocument(preset: Preset, payload: PresetPayloadNotesV1): PresetDocumentV2 =
        toDocument(preset).copy(payload = encodePayload(payload))

    /** Builds a schema-2 scale-degrees document with a supplied payload (M8). */
    fun toDocument(preset: Preset, payload: PresetPayloadScaleDegreesV1): PresetDocumentV2 =
        toDocument(preset).copy(payload = encodePayload(payload))

    /** Builds a schema-2 chord-progressions document with a supplied payload (M9A). */
    fun toDocument(preset: Preset, payload: PresetPayloadProgressionsV1): PresetDocumentV2 =
        toDocument(preset).copy(payload = encodePayload(payload))

    /** Builds a schema-2 intervals-in-context document with a supplied payload (M9B). */
    fun toDocument(preset: Preset, payload: PresetPayloadContextualIntervalsV1): PresetDocumentV2 =
        toDocument(preset).copy(payload = encodePayload(payload))

    /** Builds a schema-2 melodic-dictation document with a supplied payload (M10). */
    fun toDocument(preset: Preset, payload: PresetPayloadMelodicDictationV1): PresetDocumentV2 =
        toDocument(preset).copy(payload = encodePayload(payload))

    /** Builds a schema-2 interval-walk document with a supplied payload. */
    fun toDocument(preset: Preset, payload: PresetPayloadIntervalWalkV1): PresetDocumentV2 =
        toDocument(preset).copy(payload = encodePayload(payload))

    /** Validates a preset as the store would write it, with the rules of its document form. */
    fun validatePreset(preset: Preset) {
        validate(toDocument(preset))
    }

    /** Validates a preset identity: non-blank and within the stored bound. */
    fun requireId(id: String) {
        if (id.isBlank()) {
            throw PresetValidationException("A preset id must not be blank")
        }
        if (id.length > PresetId.MAX_LENGTH) {
            throw PresetValidationException(
                "A preset id is at most ${PresetId.MAX_LENGTH} characters but this one is ${id.length}"
            )
        }
    }

    /** Validates a preset name: trimmed, 1..[MAX_NAME_LENGTH] characters. */
    fun requireName(name: String) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) {
            throw PresetValidationException("A preset name must not be blank")
        }
        if (trimmed.length > MAX_NAME_LENGTH) {
            throw PresetValidationException(
                "A preset name is at most $MAX_NAME_LENGTH characters but this one is ${trimmed.length}"
            )
        }
    }

    /**
     * Decodes the optional mixed-presentation set.
     *
     * Absent means the single `presentation` applies. A present set must be a genuine set: sorted,
     * without repeats, at least two entries and starting with the primary presentation. This keeps
     * one document from describing two different single-presentation states.
     */
    private fun presentationsOf(
        name: String,
        payload: PresetPayloadV1
    ): List<IntervalPresentation>? {
        val names = payload.presentations ?: return null
        val values = names.map { presentationName ->
            IntervalPresentation.entries.firstOrNull { it.name == presentationName }
                ?: throw PresetValidationException(
                    "Preset '$name' has unknown presentation '$presentationName'"
                )
        }
        if (values.size < 2) {
            throw PresetValidationException(
                "Preset '$name' lists a single presentation; omit the set instead"
            )
        }
        if (values.distinct().size != values.size ||
            values != values.sortedBy { it.ordinal } ||
            values.first().name != payload.presentation
        ) {
            throw PresetValidationException(
                "Preset '$name' has an inconsistent presentation set $names"
            )
        }
        return values
    }

    /**
     * Decodes the one selected stop policy.
     *
     * Strict: a kind carries exactly the fields of that kind, a duration is positive and bounded by
     * the longest sleep limit the product allows, and an unknown kind or finish is refused.
     */
    private fun stopPolicyOf(name: String, payload: SharedPayload): SessionStopPolicy {
        fun rejectForeignFields(kind: String) {
            if (payload.stopDurationMs != null ||
                payload.stopDurationFinish != null ||
                payload.stopCount != null
            ) {
                throw PresetValidationException(
                    "Preset '$name' has stop kind $kind carrying fields of another kind"
                )
            }
        }
        return when (payload.stopKind) {
            STOP_KIND_UNTIL_STOPPED -> {
                rejectForeignFields(STOP_KIND_UNTIL_STOPPED)
                SessionStopPolicy.UntilStopped
            }
            STOP_KIND_AFTER_CYCLE -> {
                rejectForeignFields(STOP_KIND_AFTER_CYCLE)
                SessionStopPolicy.AfterCycle
            }
            STOP_KIND_DURATION -> {
                if (payload.stopCount != null) {
                    throw PresetValidationException(
                        "Preset '$name' has a duration stop with a completed-item count"
                    )
                }
                val durationMs = payload.stopDurationMs
                    ?: throw PresetValidationException(
                        "Preset '$name' has a duration stop without a duration"
                    )
                if (durationMs <= 0) {
                    throw PresetValidationException(
                        "Preset '$name' has a stop duration of $durationMs ms"
                    )
                }
                val limit = kotlin.time.Duration.parse("${durationMs}ms")
                if (limit > SetupValidator.MAX_DURATION) {
                    throw PresetValidationException(
                        "Preset '$name' has a stop duration above " +
                            "${SetupValidator.MAX_DURATION.inWholeMinutes} minutes"
                    )
                }
                val finishName = payload.stopDurationFinish
                val finish = DurationFinish.entries.firstOrNull { it.name == finishName }
                    ?: throw PresetValidationException(
                        "Preset '$name' has an unknown duration finish '$finishName'"
                    )
                SessionStopPolicy.Duration(limit = limit, finish = finish)
            }
            STOP_KIND_COMPLETED_ITEMS -> {
                if (payload.stopDurationMs != null || payload.stopDurationFinish != null) {
                    throw PresetValidationException(
                        "Preset '$name' has a completed-item stop with duration fields"
                    )
                }
                val count = payload.stopCount
                    ?: throw PresetValidationException(
                        "Preset '$name' has a completed-item stop without a count"
                    )
                if (count !in 1..SessionStopPolicy.MAX_ITEMS) {
                    throw PresetValidationException(
                        "Preset '$name' has a stop count of $count, outside " +
                            "1..${SessionStopPolicy.MAX_ITEMS}"
                    )
                }
                SessionStopPolicy.CompletedItems(count)
            }
            else -> throw PresetValidationException(
                "Preset '$name' has an unknown stop kind '${payload.stopKind}'"
            )
        }
    }

    const val MAX_NAME_LENGTH: Int = 80
    const val MAX_PRESETS: Int = 100
    const val MAX_BYTES: Int = 1024 * 1024

    /** Schema version this build writes and understands. */
    const val CURRENT_SCHEMA_VERSION: Int = PRESET_SCHEMA_VERSION
}

/** Settings a preset deliberately does not carry: appearance and display preferences. */
data class PresetExcludedSettings(
    val theme: ThemePreference,
    val dynamicColor: Boolean,
    val answerVisibility: AnswerVisibility
) {
    companion object {
        fun from(settings: UserSettings): PresetExcludedSettings = PresetExcludedSettings(
            theme = settings.theme,
            dynamicColor = settings.dynamicColor,
            answerVisibility = settings.answerVisibility
        )
    }
}
