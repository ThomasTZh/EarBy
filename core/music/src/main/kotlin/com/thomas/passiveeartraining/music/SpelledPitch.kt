package com.thomas.passiveeartraining.music

/** A diatonic letter name and its natural semitone inside one octave. */
enum class Letter(val naturalSemitone: Int) {
    C(0),
    D(2),
    E(4),
    F(5),
    G(7),
    A(9),
    B(11)
}

/**
 * A written pitch: a letter, an accidental in -2..2 and a scientific octave.
 *
 * Sounding MIDI and written spelling are separate values. Conversion follows SC5 exactly:
 * `midi = 12 * (writtenOctave + 1) + naturalLetterSemitone + accidental`, with the MIDI bounds
 * checked after the conversion. A written octave is never derived from an enharmonically
 * respelled MIDI value, and accidentals outside -2..2 are refused rather than relabeled.
 */
data class SpelledPitch(val letter: Letter, val accidental: Int, val writtenOctave: Int) {
    init {
        require(accidental in MIN_ACCIDENTAL..MAX_ACCIDENTAL) {
            "An accidental is $MIN_ACCIDENTAL..$MAX_ACCIDENTAL but was $accidental"
        }
    }

    /** The MIDI value this spelling produces; may fall outside 0..127. */
    val midiValue: Int
        get() = 12 * (writtenOctave + 1) + letter.naturalSemitone + accidental

    /** The sounding note, or a failure when the spelling leaves the MIDI range. */
    fun toMidiNote(): MidiNote = MidiNote.of(midiValue)

    /** The sounding note, or null when the spelling leaves the MIDI range. */
    fun toMidiNoteOrNull(): MidiNote? = MidiNote.ofOrNull(midiValue)

    /** This spelling with the same letter and octave but a different accidental. */
    fun withAccidental(value: Int): SpelledPitch = copy(accidental = value)

    override fun toString(): String {
        val marks = when {
            accidental > 0 -> "#".repeat(accidental)
            accidental < 0 -> "b".repeat(-accidental)
            else -> ""
        }
        return "${letter.name}$marks$writtenOctave"
    }

    companion object {
        const val MIN_ACCIDENTAL: Int = -2
        const val MAX_ACCIDENTAL: Int = 2

        fun of(letter: Letter, accidental: Int = 0, octave: Int): SpelledPitch =
            SpelledPitch(letter, accidental, octave)

        /**
         * Finds the octave and accidental that write [targetMidi] with [letter].
         *
         * The search is bounded and deterministic: octaves around the sounding pitch are tried in
         * increasing distance, and the first accidental inside -2..2 wins. A target that cannot be
         * written with the given letter fails explicitly instead of being relabeled.
         */
        fun spellingFor(targetMidi: Int, letter: Letter): SpelledPitch {
            val naturalBase = targetMidi - letter.naturalSemitone
            val centre = naturalBase.floorDiv(MidiNote.SEMITONES_PER_OCTAVE)
            for (distance in 0..3) {
                val offsets = if (distance == 0) listOf(0) else listOf(-distance, distance)
                for (offset in offsets) {
                    val octave = centre + offset - 1
                    val accidental = targetMidi -
                        (MidiNote.SEMITONES_PER_OCTAVE * (octave + 1) + letter.naturalSemitone)
                    if (accidental in MIN_ACCIDENTAL..MAX_ACCIDENTAL) {
                        return SpelledPitch(letter, accidental, octave)
                    }
                }
            }
            throw UnrepresentableSpellingException(
                "MIDI $targetMidi cannot be spelled as ${letter.name} within " +
                    "$MIN_ACCIDENTAL..$MAX_ACCIDENTAL accidentals"
            )
        }

        /**
         * The chromatic sharp or flat spelling of a sounding [note].
         *
         * This is the absolute-note table used by note-identification answers; it never invents a
         * double accidental for a chromatic pitch (those come from key-aware spelling instead).
         */
        fun fromMidi(note: MidiNote, spelling: SpellingPreference): SpelledPitch {
            val octave = note.value.floorDiv(MidiNote.SEMITONES_PER_OCTAVE) - 1
            val pitchClass = note.value.mod(MidiNote.SEMITONES_PER_OCTAVE)
            val (letter, accidental) = if (spelling == SpellingPreference.SHARP) {
                SHARP_TABLE[pitchClass]
            } else {
                FLAT_TABLE[pitchClass]
            }
            return SpelledPitch(letter, accidental, octave)
        }

        /** Sharp chromatic spelling per pitch class, C = 0. */
        private val SHARP_TABLE: List<Pair<Letter, Int>> = listOf(
            Letter.C to 0,
            Letter.C to 1,
            Letter.D to 0,
            Letter.D to 1,
            Letter.E to 0,
            Letter.F to 0,
            Letter.F to 1,
            Letter.G to 0,
            Letter.G to 1,
            Letter.A to 0,
            Letter.A to 1,
            Letter.B to 0
        )

        /** Flat chromatic spelling per pitch class, C = 0. */
        private val FLAT_TABLE: List<Pair<Letter, Int>> = listOf(
            Letter.C to 0,
            Letter.D to -1,
            Letter.D to 0,
            Letter.E to -1,
            Letter.E to 0,
            Letter.F to 0,
            Letter.G to -1,
            Letter.G to 0,
            Letter.A to -1,
            Letter.A to 0,
            Letter.B to -1,
            Letter.B to 0
        )
    }
}

/** Raised when a theoretical spelling cannot be represented within the accidental bounds. */
class UnrepresentableSpellingException(message: String) : IllegalArgumentException(message)
