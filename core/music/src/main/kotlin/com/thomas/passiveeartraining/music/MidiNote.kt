package com.thomas.passiveeartraining.music

/**
 * Immutable sounding pitch in the MIDI range 0..127.
 *
 * Sounding value and display spelling are separate concerns: [value] is the only
 * property that affects music, while [PitchClass] spelling preference only changes
 * how a pitch class is written down.
 */
@JvmInline
value class MidiNote private constructor(val value: Int) : Comparable<MidiNote> {

    /** Octave used by scientific pitch notation; C4 = 60 yields octave 4, A4 = 69 yields 4. */
    val octave: Int
        get() = value.floorDiv(SEMITONES_PER_OCTAVE) - 1

    /** Sounding pitch class of this note, C = 0 through B = 11. */
    val pitchClass: PitchClass
        get() = PitchClass.ofMidiValue(value)

    /** Checked transposition. Returns `null` when the result leaves the representable range. */
    fun transposedBy(semitones: Int): MidiNote? {
        val moved = value.toLong() + semitones.toLong()
        return if (moved < MIN_VALUE.toLong() ||
            moved > MAX_VALUE.toLong()
        ) {
            null
        } else {
            of(moved.toInt())
        }
    }

    /** Checked transposition that fails with [InvalidMidiNoteException] instead of returning
     * `null`. */
    fun requireTransposedBy(semitones: Int): MidiNote =
        transposedBy(semitones) ?: throw InvalidMidiNoteException(
            "MIDI note $value cannot be transposed by $semitones semitones without leaving 0..127"
        )

    /** Display name of this pitch such as `C4`; the octave is only valid because [value] is in
     * range. */
    fun displayName(spelling: SpellingPreference = SpellingPreference.SHARP): String =
        "${pitchClass.displayName(spelling)}$octave"

    override fun compareTo(other: MidiNote): Int = value.compareTo(other.value)

    companion object {
        const val MIN_VALUE: Int = 0
        const val MAX_VALUE: Int = 127
        const val SEMITONES_PER_OCTAVE: Int = 12

        /** C4 in scientific pitch notation. */
        const val C4: Int = 60

        /** A4, the 440 Hz reference pitch. */
        const val A4: Int = 69

        /** Creates a note, rejecting values outside 0..127 with [InvalidMidiNoteException]. */
        fun of(value: Int): MidiNote {
            if (value !in MIN_VALUE..MAX_VALUE) {
                throw InvalidMidiNoteException(
                    "MIDI note must be in $MIN_VALUE..$MAX_VALUE but was $value"
                )
            }
            return MidiNote(value)
        }

        /** Creates a note, returning `null` instead of throwing for out-of-range values. */
        fun ofOrNull(value: Int): MidiNote? = if (value in
            MIN_VALUE..MAX_VALUE
        ) {
            MidiNote(value)
        } else {
            null
        }

        /**
         * Builds a note from a scientific octave and a sounding pitch class.
         *
         * Octave 9 is only partially representable: classes above G sharp are rejected.
         * Use [of] when the sounding MIDI value is already known.
         */
        fun of(pitchClass: PitchClass, octave: Int): MidiNote =
            of(octave * SEMITONES_PER_OCTAVE + pitchClass.value + SEMITONES_PER_OCTAVE)
                ?: throw InvalidMidiNoteException(
                    "Pitch class ${pitchClass.value} in octave $octave has no MIDI value in $MIN_VALUE..$MAX_VALUE"
                )

        /** Builds a note from a scientific octave and pitch class, returning `null` when
         * unrepresentable. */
        fun ofOrNull(pitchClass: PitchClass, octave: Int): MidiNote? = ofOrNull(
            octave * SEMITONES_PER_OCTAVE + pitchClass.value + SEMITONES_PER_OCTAVE
        )
    }
}

/** Raised when a MIDI value, octave or transposition result is not representable. */
class InvalidMidiNoteException(message: String) : IllegalArgumentException(message)
