package com.thomas.passiveeartraining.training.provider

import com.thomas.passiveeartraining.music.ChordVoicing
import com.thomas.passiveeartraining.music.IntervalPresentation
import com.thomas.passiveeartraining.music.MidiNote
import com.thomas.passiveeartraining.music.PitchClass
import com.thomas.passiveeartraining.music.ProgressionVoicingMode
import com.thomas.passiveeartraining.music.ScaleDirection
import com.thomas.passiveeartraining.music.SimpleInterval
import com.thomas.passiveeartraining.music.SpellingPreference
import com.thomas.passiveeartraining.music.Tonality
import com.thomas.passiveeartraining.training.ContextualIntervalAnswerFormat
import com.thomas.passiveeartraining.training.ContextualIntervalDefinition
import com.thomas.passiveeartraining.training.DegreeAnswerFormat
import com.thomas.passiveeartraining.training.GeneratorIdentity
import com.thomas.passiveeartraining.training.MelodicAnswerFormat
import com.thomas.passiveeartraining.training.MelodicDirectionBias
import com.thomas.passiveeartraining.training.NoteDefinition
import com.thomas.passiveeartraining.training.ProgressionAnswerFormat
import com.thomas.passiveeartraining.training.ProgressionDefinition
import com.thomas.passiveeartraining.training.ScaleDegreeDefinition
import com.thomas.passiveeartraining.training.VoiceAssetId
import com.thomas.passiveeartraining.training.WalkDirection
import com.thomas.passiveeartraining.training.context.ContextKind

/** Whether a note group is background context or the trained target (SC2). */
enum class GroupRole {
    /** Establishes key, chord or scale context; never part of the answer accounting. */
    CONTEXT,

    /** The trained material the answer describes. */
    TARGET
}

/**
 * One scheduled group of sounding notes.
 *
 * A group's position in the exercise's list is its scheduling slot: sequential groups start one
 * `noteDuration + sequentialGap` stride apart, and notes inside one group sound together. An empty
 * group is a deliberate rest slot that still occupies its window but produces no event. Notes are
 * sorted and unique so two equal sounding pitches can never become two voices (SC2).
 */
data class NoteGroup(val notes: List<MidiNote>, val role: GroupRole) {
    init {
        require(notes == notes.sortedBy { it.value }) {
            "Note group pitches must be sorted: $notes"
        }
        require(notes.distinct().size == notes.size) {
            "Note group pitches must be unique: $notes"
        }
    }

    /** A rest slot: it reserves scheduling space and sounds nothing. */
    companion object {
        fun rest(role: GroupRole): NoteGroup = NoteGroup(emptyList(), role)
    }
}

/** How one exercise was produced, kept for reproducibility (SC2/SC3). */
data class GeneratorProvenance(
    /** Stable kind id of the producing provider. */
    val providerId: String,
    /** Provider contract or generator version, frozen per provider. */
    val providerVersion: Int,
    /** RNG algorithm id, for example `java-random-48-v1`. */
    val rngId: String,
    /** The effective seed the sequence was drawn from. */
    val seed: Long,
    /**
     * The legacy interval identity, preserved losslessly while interval output must remain
     * reproducible through the adapter (M1.2). Null for providers that never had one.
     */
    val legacyIntervalIdentity: GeneratorIdentity? = null,
    /**
     * The generated chord reading of one chord exercise (M5.1), independent of how many
     * equivalent answers the sound also accepts. Null for non-chord providers.
     */
    val generatedChord: GeneratedChord? = null,
    /**
     * The generated scale of one scale exercise (M6.1), independent of the spoken answer.
     * Null for non-scale providers.
     */
    val generatedScale: GeneratedScale? = null,
    /**
     * The generated absolute note of one note-identification exercise (M7.1) with the accidental
     * style that was spoken at draw time. Null for non-note providers.
     */
    val generatedNote: GeneratedNote? = null,
    /**
     * The generated scale-degree exercise (M8.1/M8.2): the chosen key/tonality, the concrete
     * context construction and the target degree. Null for non-degree providers.
     */
    val generatedScaleDegree: GeneratedScaleDegree? = null,
    /**
     * The generated chord progression of one progression exercise (M9A.1/M9A.2): the drawn
     * template, the concrete key/anchor realization and every sounding chord. Null for
     * non-progression providers.
     */
    val generatedProgression: GeneratedProgression? = null,
    /**
     * The generated contextual-interval exercise (M9B.1/M9B.2): the key/context construction and
     * the two target notes with their degrees. Null for non-contextual-interval providers.
     */
    val generatedContextualInterval: GeneratedContextualInterval? = null,
    /**
     * The generated melodic-dictation phrase (M10.1/M10.2): the key/context construction, the
     * complete phrase as notes and derived steps, the direction bias and the answer format. Null
     * for non-melodic providers.
     */
    val generatedMelody: GeneratedMelody? = null,
    /**
     * The generated finite interval walk (G6): the start, the complete note chain, the signed
     * steps and the selected direction. Null for non-walk providers.
     */
    val generatedIntervalWalk: GeneratedIntervalWalk? = null
)

/** The concrete chord the generator drew: quality, inversion, theoretical root and sound. */
data class GeneratedChord(
    val qualityId: String,
    val inversion: Int,
    val theoreticalRootMidi: Int,
    val soundingValues: List<Int>
)

/** The concrete scale the generator drew: canonical kind, direction, lower tonic and sound. */
data class GeneratedScale(
    val scaleId: String,
    val direction: ScaleDirection,
    /** The lower tonic of the complete octave. */
    val tonicMidi: Int,
    /** Every emitted note in traversal order, including the repeated tonic. */
    val soundingValues: List<Int>
)

/**
 * The concrete absolute note the generator drew (M7.1).
 *
 * The accidental style and octave-speech choice are captured with the draw, so a later spelling or
 * speech edit cannot rewrite the words of an exercise already heard.
 */
data class GeneratedNote(
    val pitchClass: PitchClass,
    val midiNote: MidiNote,
    val accidental: SpellingPreference,
    val octaveSpoken: Boolean
)

/**
 * The concrete scale-degree exercise the generator drew (M8.1/M8.2).
 *
 * Everything a retained item needs to reconstruct its context, target and spoken answer is
 * captured with the draw: the key pitch class with the spelling that fixed its tonic, the
 * tonality, the context kind and anchor, the sounding context note values, the theoretical degree
 * and the concrete target. A later spelling, naming or context edit cannot rewrite an exercise
 * that was already heard.
 */
data class GeneratedScaleDegree(
    /** The sounding key tonic pitch class, C = 0. */
    val keyPitchClass: Int,
    /** The definition's sharp/flat preference at draw time. */
    val spelling: SpellingPreference,
    val tonality: Tonality,
    val contextKind: ContextKind,
    /** The tonic MIDI anchor the context was built at. */
    val anchorMidi: Int,
    /** Every context sounding value, in group order. */
    val contextValues: List<Int>,
    /** The theoretical target degree 1..7, even when the target octave differs from the anchor. */
    val degree: Int,
    /** The concrete target MIDI note that sounded. */
    val targetMidi: Int,
    val answerFormat: DegreeAnswerFormat
)

/**
 * The concrete chord progression the generator drew (M9A.1/M9A.2).
 *
 * Everything a retained item needs to reconstruct the progression and its answer is captured with
 * the draw: the drawn template, the key pitch class with the spelling that fixed its tonic, the
 * tonality, the anchor, the voicing rules and every sounding chord. The theoretical roots inside
 * [chords] stay separate from the sounding basses, so a root-motion answer can never be rewritten
 * by an inversion. A later spelling or voicing edit cannot rewrite an exercise already heard.
 */
data class GeneratedProgression(
    /** The sounding chords in step order. */
    val chords: List<ChordVoicing>,
    /** The exact Roman, function or root-motion label sequence the answer reveals. */
    val sequenceLabel: String,
    /** The sounding key tonic pitch class, C = 0. */
    val keyPitchClass: Int,
    /** The definition's sharp/flat preference at draw time. */
    val spelling: SpellingPreference,
    /** The key's tonality; progressions are major-only. */
    val tonality: Tonality,
    /** The tonic MIDI anchor the chords were built at. */
    val anchorMidi: Int,
    val voicing: ProgressionVoicingMode,
    /** The allowed inversions in effect at draw time, sorted. */
    val allowedInversions: List<Int>,
    val answerFormat: ProgressionAnswerFormat
)

/** Position inside a provider's own bag or cycle, when the provider really owns one (SC2). */
data class CyclePosition(val cycleIndex: Int, val positionInCycle: Int)

/**
 * The concrete contextual-interval exercise the generator drew (M9B.1/M9B.2).
 *
 * Everything a retained item needs to reconstruct its context, its two target notes and the spoken
 * answer is captured with the draw: the key pitch class with the spelling that fixed its tonic, the
 * tonality, the context kind and anchor, the sounding context values, the interval and
 * presentation, both target MIDI notes in presented order, their theoretical degrees and the
 * answer format. The degree facts and the semitone facts stay separate: a later context, spelling
 * or answer edit cannot rewrite an exercise that was already heard, and the degrees are never
 * derived from the sounding distance.
 */
data class GeneratedContextualInterval(
    /** The sounding key tonic pitch class, C = 0. */
    val keyPitchClass: Int,
    /** The definition's sharp/flat preference at draw time. */
    val spelling: SpellingPreference,
    val tonality: Tonality,
    val contextKind: ContextKind,
    /** The tonic MIDI anchor the context was built at. */
    val anchorMidi: Int,
    /** Every context sounding value, in group order. */
    val contextValues: List<Int>,
    /** Stable simple-interval id, for example `perfect_fifth`. */
    val intervalId: String,
    val presentation: IntervalPresentation,
    /** The first target MIDI note in presented order. */
    val firstTargetMidi: Int,
    /** The second target MIDI note in presented order. */
    val secondTargetMidi: Int,
    /** The theoretical degree 1..7 of the first target note relative to the key tonic. */
    val firstDegree: Int,
    /** The theoretical degree 1..7 of the second target note relative to the key tonic. */
    val secondDegree: Int,
    val answerFormat: ContextualIntervalAnswerFormat
)

/**
 * One phrase note of a generated melody (M10.1/M10.2).
 *
 * [midi] is the concrete sounding note and [degree] its theoretical degree 1..7 relative to the
 * key tonic. The degree is a stored fact of the draw, never re-derived from a later definition.
 */
data class MelodyNote(
    /** The concrete sounding MIDI note. */
    val midi: Int,
    /** The theoretical degree 1..7 relative to the key tonic. */
    val degree: Int
)

/**
 * The concrete melodic phrase the generator drew (M10.1/M10.2).
 *
 * Everything a retained item needs to reconstruct its context, its phrase and the spoken answer is
 * captured with the draw: the key pitch class with the spelling that fixed its tonic, the
 * tonality, the context kind and anchor, the sounding context values, the complete phrase as notes
 * with their degrees, the consecutive signed steps, the bias that shaped the path and the answer
 * format. A later spelling, context, bias or answer edit cannot rewrite an exercise that was
 * already heard.
 */
data class GeneratedMelody(
    /** The sounding key tonic pitch class, C = 0. */
    val keyPitchClass: Int,
    /** The definition's sharp/flat preference at draw time. */
    val spelling: SpellingPreference,
    val tonality: Tonality,
    val context: ContextKind,
    /** The tonic MIDI anchor the context was built at. */
    val anchorMidi: Int,
    /** Every context sounding value, in group order. */
    val contextNotes: List<Int>,
    /** The complete phrase in played order. */
    val notes: List<MelodyNote>,
    /** The consecutive signed semitone steps; one fewer than [notes]. */
    val steps: List<Int>,
    /** The transition weighting in effect at draw time. */
    val bias: MelodicDirectionBias,
    val answerFormat: MelodicAnswerFormat
)

/** The concrete finite interval walk the generator drew. */
data class GeneratedIntervalWalk(
    val startMidi: Int,
    val notes: List<Int>,
    val steps: List<Int>,
    val intervalIds: List<String>,
    val direction: WalkDirection,
    val minimumStartMidi: Int,
    val maximumStartMidi: Int
)

/**
 * Structured answer display data, projected at display time.
 *
 * Nothing here is a persisted string: the semantic IDs are stable, and the projection happens
 * wherever the current global preferences are known (SC2/SC5).
 */
sealed interface AnswerDisplay {
    /** A simple interval, projected through the existing interval name table. */
    data class Interval(val intervalId: String) : AnswerDisplay

    /**
     * One chord reading: the quality, optionally with the inversion.
     *
     * The inversion is null when the selected answer format names the quality only.
     */
    data class Chord(val qualityId: String, val inversion: Int? = null) : AnswerDisplay

    /**
     * One canonical scale name.
     *
     * The projection uses the vocabulary's canonical spoken name; an unknown id falls back to its
     * text form so diagnostics never crash on a stale draft.
     */
    data class Scale(val scaleId: String) : AnswerDisplay

    /**
     * One absolute note answer (M7).
     *
     * The label is projected from the same name entry and octave token the answer speaks, so
     * display and speech agree; [octaveSpoken] false means no octave word is shown either, so a
     * hidden-answer UI can never reveal a register the voice does not say.
     */
    data class Note(
        val pitchClass: PitchClass,
        val midiNote: MidiNote,
        val accidental: SpellingPreference,
        val octaveSpoken: Boolean
    ) : AnswerDisplay

    /**
     * One scale-degree answer (M8).
     *
     * The degree, its tonality and the drawn answer format project the same token sequence the
     * answer speaks, so the revealed text can never disagree with the voice; [pitchClass] is the
     * sounding class of the target degree and fixes the NOTE_NAME projection under the
     * definition's spelling.
     */
    data class ScaleDegree(
        /** The theoretical degree 1..7; never inferred from the last context note. */
        val degree: Int,
        val tonality: Tonality,
        val format: DegreeAnswerFormat,
        /** The sounding pitch class of the degree, C = 0. */
        val pitchClass: PitchClass,
        val spelling: SpellingPreference
    ) : AnswerDisplay

    /**
     * One chord-progression answer (M9B).
     *
     * [label] is the exact text of the token sequence the answer speaks, composed by
     * [ProgressionDefinition.displayLabel] from the same drawn sequence, so the revealed text can
     * never disagree with the voice.
     */
    data class Progression(val label: String) : AnswerDisplay

    /**
     * One contextual-interval answer (M9B).
     *
     * The interval, both degrees and the drawn answer format project the same token sequence the
     * answer speaks, so the revealed text can never disagree with the voice: the interval phrase
     * and the two degree numbers are one shared composition, never a separately formatted string.
     */
    data class ContextualInterval(
        /** Stable simple-interval id, for example `minor_third`. */
        val intervalId: String,
        /** The theoretical degree 1..7 of the first target note in presented order. */
        val firstDegree: Int,
        /** The theoretical degree 1..7 of the second target note in presented order. */
        val secondDegree: Int,
        val answerFormat: ContextualIntervalAnswerFormat
    ) : AnswerDisplay

    /** One finite interval-walk answer: the exact directed interval sequence. */
    data class IntervalWalk(val label: String) : AnswerDisplay

    /**
     * One melodic-dictation answer (M10).
     *
     * [label] is the exact text of the token sequence the answer speaks, composed by
     * [com.thomas.passiveeartraining.training.MelodicDictationDefinition.answerTextFor] from the
     * same tokens, so the revealed text can never disagree with the voice. The mode has no scored
     * answer: the display is the complete composed speech only.
     */
    data class Melody(val label: String) : AnswerDisplay

    /** Alternatives joined with "or" in the order given; used for equivalent chord answers. */
    data class Alternatives(val items: List<AnswerDisplay>) : AnswerDisplay

    /** The single display label for this answer. */
    fun label(): String = when (this) {
        is Interval -> SimpleInterval.ofIdOrNull(intervalId)?.englishAnswer ?: intervalId
        is Chord -> {
            val quality = com.thomas.passiveeartraining.music.ChordQuality.ofIdOrNull(qualityId)
                ?.englishAnswer
                ?: qualityId
            if (inversion == null) {
                quality
            } else {
                "$quality ${com.thomas.passiveeartraining.music.ChordInversions.answer(inversion)}"
            }
        }
        is Alternatives -> items.joinToString(" or ") { it.label() }
        is Scale -> com.thomas.passiveeartraining.music.ScaleKind.ofIdOrNull(scaleId)
            ?.let { VoiceVocabulary.scaleEntry(it).spokenText }
            ?: scaleId.replace('_', ' ')
        is Note -> NoteDefinition.answerTextFor(midiNote, accidental, octaveSpoken)
        is ScaleDegree -> ScaleDegreeDefinition.answerTokens(
            format = format,
            degree = degree,
            pitchClass = pitchClass,
            spelling = spelling
        ).joinToString(" ") { VoiceVocabulary.requireEntry(it).spokenText }
        is Progression -> label
        is ContextualInterval -> {
            val interval = SimpleInterval.ofIdOrNull(intervalId)
            if (interval == null) {
                intervalId
            } else {
                ContextualIntervalDefinition.answerTokens(
                    format = answerFormat,
                    interval = interval,
                    firstDegree = firstDegree,
                    secondDegree = secondDegree
                ).joinToString(" ") { VoiceVocabulary.requireEntry(it).spokenText }
            }
        }
        is Melody -> label
        is IntervalWalk -> label
    }
}

/**
 * The spoken answer: ordered semantic voice tokens plus structured display data.
 *
 * The first token carries the single reveal marker; the compiler inserts exactly 80 ms between
 * clips (SC2/SC4).
 */
data class AnswerDescriptor(val voiceIds: List<VoiceAssetId>, val display: AnswerDisplay) {
    init {
        require(voiceIds.isNotEmpty()) { "An answer must have at least one voice token" }
    }
}

/**
 * One immutable generated exercise in provider-neutral form.
 *
 * Groups keep their generation order; context groups precede target groups. Everything a retained
 * item needs to replay is here: concrete notes, answer, category and provenance. A retained item
 * never redraws randomness (SC2/SC3).
 */
data class ConcreteExercise(
    val kind: TrainingKind,
    val provenance: GeneratorProvenance,
    /** Stable answer category id, for example `interval.perfect_fifth`. */
    val categoryId: String,
    /** Monotonic draw index inside the activation; used for render keys and diagnostics. */
    val sequenceIndex: Int,
    /** The provider's real bag/cycle position, or null when it has no meaningful cycle. */
    val cycle: CyclePosition?,
    val groups: List<NoteGroup>,
    val answer: AnswerDescriptor
) {
    init {
        require(groups.any { it.role == GroupRole.TARGET }) {
            "An exercise must contain at least one target group"
        }
        val firstTarget = groups.indexOfFirst { it.role == GroupRole.TARGET }
        require(groups.drop(firstTarget).all { it.role == GroupRole.TARGET }) {
            "Context groups must precede target groups: $groups"
        }
    }

    /** Context groups in play order. */
    val contextGroups: List<NoteGroup> get() = groups.filter { it.role == GroupRole.CONTEXT }

    /** Target groups in play order. */
    val targetGroups: List<NoteGroup> get() = groups.filter { it.role == GroupRole.TARGET }
}
