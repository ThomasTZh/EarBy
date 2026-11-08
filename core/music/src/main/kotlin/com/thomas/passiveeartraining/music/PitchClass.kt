package com.thomas.passiveeartraining.music

/** Chooses which enharmonic spelling of a pitch class is displayed. */
enum class SpellingPreference {
    /** C, C sharp, D, ... */
    SHARP,

    /** C, D flat, D, ... */
    FLAT
}

/**
 * One of the twelve sounding pitch classes, C = 0 through B = 11.
 *
 * Spelling preference changes display text only; it never changes the sounding class.
 * Key-dependent spellings such as B sharp or C flat are deliberately not modelled yet.
 */
@JvmInline
value class PitchClass private constructor(val value: Int) : Comparable<PitchClass> {

    /**
     * Display name using the requested enharmonic spelling, for example `C sharp` or `D flat`.
     */
    fun displayName(spelling: SpellingPreference = SpellingPreference.SHARP): String =
        when (spelling) {
            SpellingPreference.SHARP -> SHARP_NAMES[value]
            SpellingPreference.FLAT -> FLAT_NAMES[value]
        }

    override fun compareTo(other: PitchClass): Int = value.compareTo(other.value)

    companion object {
        const val COUNT: Int = MidiNote.SEMITONES_PER_OCTAVE

        private val SHARP_NAMES = listOf(
            "C", "C\u266F", "D", "D\u266F", "E", "F", "F\u266F", "G", "G\u266F", "A", "A\u266F", "B"
        )
        private val FLAT_NAMES = listOf(
            "C", "D\u266D", "D", "E\u266D", "E", "F", "G\u266D", "G", "A\u266D", "A", "B\u266D", "B"
        )

        val ALL: List<PitchClass> = List(COUNT) { PitchClass(it) }

        /** Creates a pitch class, rejecting values outside 0..11. */
        fun of(value: Int): PitchClass {
            if (value !in 0 until COUNT) {
                throw InvalidMidiNoteException(
                    "Pitch class must be in 0..${COUNT - 1} but was $value"
                )
            }
            return PitchClass(value)
        }

        /** Sounding pitch class of any integer MIDI value, including values outside 0..127. */
        fun ofMidiValue(midiValue: Int): PitchClass = PitchClass(midiValue.mod(COUNT))
    }
}
