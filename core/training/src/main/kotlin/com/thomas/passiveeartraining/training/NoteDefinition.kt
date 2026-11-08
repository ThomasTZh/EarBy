package com.thomas.passiveeartraining.training

import com.thomas.passiveeartraining.music.Letter
import com.thomas.passiveeartraining.music.MidiNote
import com.thomas.passiveeartraining.music.PitchClass
import com.thomas.passiveeartraining.music.SpelledPitch
import com.thomas.passiveeartraining.music.SpellingPreference
import com.thomas.passiveeartraining.training.provider.VoiceEntry
import com.thomas.passiveeartraining.training.provider.VoiceVocabulary

/**
 * The named projection of one pitch class under a note definition's accidental style.
 *
 * The entry's id and words are exactly what the answer speaks, so display and speech can never
 * disagree.
 */
sealed interface NoteName {
    /** The vocabulary entry that names the pitch class. */
    val entry: VoiceEntry

    /** A scientific chromatic name, for example C sharp or D flat. */
    data class Scientific(val letter: Letter, val accidental: Int) : NoteName {
        override val entry: VoiceEntry get() = VoiceVocabulary.noteEntry(letter, accidental)
    }
}

/**
 * Immutable, normalized absolute-note configuration (M7).
 *
 * The selection is a set of pitch classes: duplicates are removed and classes are ordered
 * numerically. The accidental style picks which chromatic spelling of a scientific letter is
 * spoken. A selected class that has no sounding note is rejected by the provider rather than
 * silently dropped here, mirroring the chord and scale definitions.
 */
class NoteDefinition private constructor(
    val selectedPitchClasses: List<PitchClass>,
    val accidental: SpellingPreference,
    val speakOctave: Boolean,
    val minimumNote: MidiNote,
    val maximumNote: MidiNote
) {
    /** Numeric values of [selectedPitchClasses], in the same order. */
    val pitchClassValues: List<Int> = java.util.Collections.unmodifiableList(
        selectedPitchClasses.map { it.value }
    )

    val minimumNoteValue: Int = minimumNote.value
    val maximumNoteValue: Int = maximumNote.value

    /** Inclusive sounding range as a MIDI value pair. */
    val noteRange: IntRange = minimumNoteValue..maximumNoteValue

    /**
     * Selected classes that have at least one note inside [noteRange].
     *
     * Perfect-pitch mode keeps the range as the final note restriction. Classes with no note in
     * the range stay selected in the editor but are excluded from play and listed by the setup
     * warning.
     */
    val feasiblePitchClasses: List<PitchClass> = selectedPitchClasses.filter {
        notesFor(it).isNotEmpty()
    }

    /**
     * Every feasible MIDI note of every playable selected class, ascending.
     *
     * This is the complete reachable material: the generator draws only from it, and the required
     * vocabulary must cover every name and octave token it can speak.
     */
    val reachableNotes: List<MidiNote> = feasiblePitchClasses
        .flatMap { notesFor(it) }
        .sortedBy { it.value }

    /** The name projection of [pitchClass] under this definition's accidental style. */
    fun nameOf(pitchClass: PitchClass): NoteName = nameOf(pitchClass, accidental)

    /** The semantic name token the answer speaks for [pitchClass]. */
    fun nameToken(pitchClass: PitchClass): VoiceAssetId = nameOf(pitchClass).entry.id

    /** The spoken octave token for [note], or null when octave speech is off. */
    fun octaveToken(note: MidiNote): VoiceAssetId? =
        if (speakOctave) VoiceVocabulary.octaveEntry(note.octave).id else null

    /**
     * The ordered answer tokens for [note]: its name, then its octave word when octave speech is
     * on. The sequence always matches the revealed display text.
     */
    fun answerTokensFor(note: MidiNote): List<VoiceAssetId> =
        answerTokensFor(note, accidental, speakOctave)

    /** The exact words the answer displays and speaks for [note]. */
    fun answerTextFor(note: MidiNote): String = answerTextFor(note, accidental, speakOctave)

    /**
     * Every feasible MIDI note of [pitchClass] inside the range, ascending.
     *
     * This is the complete reachable set; every octave inside the range is included.
     */
    fun notesFor(pitchClass: PitchClass): List<MidiNote> = rangeNotesFor(pitchClass)

    /** Every MIDI note of [pitchClass] inside the range; the reachable set. */
    fun rangeNotesFor(pitchClass: PitchClass): List<MidiNote> = (-1..9)
        .mapNotNull { octave -> MidiNote.ofOrNull(pitchClass, octave) }
        .filter { it.value in noteRange }

    override fun equals(other: Any?): Boolean = other is NoteDefinition &&
        selectedPitchClasses == other.selectedPitchClasses &&
        accidental == other.accidental &&
        speakOctave == other.speakOctave &&
        minimumNote == other.minimumNote &&
        maximumNote == other.maximumNote

    override fun hashCode(): Int {
        var result = selectedPitchClasses.hashCode()
        result = 31 * result + accidental.hashCode()
        result = 31 * result + speakOctave.hashCode()
        result = 31 * result + minimumNote.hashCode()
        result = 31 * result + maximumNote.hashCode()
        return result
    }

    override fun toString(): String =
        "NoteDefinition(pitchClasses=$pitchClassValues, accidental=$accidental, " +
            "speakOctave=$speakOctave, range=$minimumNoteValue..$maximumNoteValue)"

    companion object {
        /** Default fixture: all twelve classes, sharp spelling, C4..C5, no octave speech. */
        fun default(): NoteDefinition = of(
            pitchClassValues = PitchClass.ALL.map { it.value },
            accidental = SpellingPreference.SHARP,
            speakOctave = false,
            minimumNote = MidiNote.of(60),
            maximumNote = MidiNote.of(72)
        )

        /**
         * Normalizes and validates a definition.
         *
         * The exception names the setup field (`pitchClasses` or `range`) the failure belongs to.
         * A selected class with no feasible note is deliberately not rejected here: the provider
         * reports it against `range`, so the invalid draft stays constructible for form feedback.
         *
         * @throws InvalidNoteDefinitionException when the selection is empty, a value is not a
         *   pitch class, or the range is inverted.
         */
        fun of(
            pitchClassValues: Collection<Int>,
            accidental: SpellingPreference,
            speakOctave: Boolean,
            minimumNote: MidiNote,
            maximumNote: MidiNote
        ): NoteDefinition {
            val values = pitchClassValues.toSet().sorted()
            values.forEach { value ->
                if (value !in 0 until PitchClass.COUNT) {
                    throw InvalidNoteDefinitionException(
                        "A pitch class must be in 0..${PitchClass.COUNT - 1} but was $value",
                        field = "pitchClasses"
                    )
                }
            }
            if (values.isEmpty()) {
                throw InvalidNoteDefinitionException(
                    "At least one pitch class must be selected",
                    field = "pitchClasses"
                )
            }
            if (minimumNote.value > maximumNote.value) {
                throw InvalidNoteDefinitionException(
                    "Minimum note ${minimumNote.value} must not exceed maximum note " +
                        "${maximumNote.value}",
                    field = "range"
                )
            }
            return NoteDefinition(
                selectedPitchClasses = values.map { PitchClass.of(it) },
                accidental = accidental,
                speakOctave = speakOctave,
                minimumNote = minimumNote,
                maximumNote = maximumNote
            )
        }

        /**
         * Projects [pitchClass] under an explicit accidental style.
         *
         * The frozen sharp or flat chromatic table selects which enharmonic letter is spoken.
         */
        fun nameOf(pitchClass: PitchClass, accidental: SpellingPreference): NoteName {
            // The chromatic table depends only on the pitch class; C4 anchors the lookup
            // inside the representable range.
            val spelled = SpelledPitch.fromMidi(
                MidiNote.of(MidiNote.C4 + pitchClass.value),
                accidental
            )
            return NoteName.Scientific(spelled.letter, spelled.accidental)
        }

        /** The semantic name token for [pitchClass] under an explicit projection. */
        fun nameToken(pitchClass: PitchClass, accidental: SpellingPreference): VoiceAssetId =
            nameOf(pitchClass, accidental).entry.id

        /**
         * The ordered answer tokens for [note] under an explicit projection: the name token, then
         * the octave token exactly when octave speech is on.
         */
        fun answerTokensFor(
            note: MidiNote,
            accidental: SpellingPreference,
            speakOctave: Boolean
        ): List<VoiceAssetId> = buildList {
            add(nameToken(note.pitchClass, accidental))
            if (speakOctave) add(VoiceVocabulary.octaveEntry(note.octave).id)
        }

        /**
         * The exact words a note answer displays and speaks under an explicit projection.
         *
         * The label is the name words plus, when octave speech is on, the octave word; a note
         * answer never shows an octave the speech omits (M7).
         */
        fun answerTextFor(
            note: MidiNote,
            accidental: SpellingPreference,
            speakOctave: Boolean
        ): String = buildString {
            append(nameOf(note.pitchClass, accidental).entry.spokenText)
            if (speakOctave) {
                append(' ')
                append(VoiceVocabulary.octaveEntry(note.octave).spokenText)
            }
        }
    }
}

/** Raised when a note configuration is empty, unknown or internally contradictory. */
class InvalidNoteDefinitionException(
    message: String,
    cause: Throwable? = null,
    /** The setup field the failure belongs to, for form feedback. */
    val field: String? = null
) : IllegalArgumentException(message, cause)
