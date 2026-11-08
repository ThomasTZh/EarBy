package com.thomas.passiveeartraining.music

/** The two tonalities the current product trains. */
enum class Tonality {
    MAJOR,
    NATURAL_MINOR;

    /** Semitone offsets of degrees 1..7 from the tonic. */
    val degreeOffsets: List<Int>
        get() = when (this) {
            MAJOR -> listOf(0, 2, 4, 5, 7, 9, 11)
            NATURAL_MINOR -> listOf(0, 2, 3, 5, 7, 8, 10)
        }
}

/**
 * A spelled key: a tonic spelling and a tonality.
 *
 * Degrees follow the theoretical letter progression from the tonic, so the written accidental is
 * whatever makes the diatonic letter sound the scale's semitone offset. A degree that cannot be
 * spelled inside -2..2 fails explicitly; it is never relabeled with another letter.
 */
data class Key(val tonic: SpelledPitch, val tonality: Tonality) {

    /** The spelled pitch of diatonic [degree] (1..7) in the tonic's octave region. */
    fun degree(degree: Int): SpelledPitch {
        require(degree in 1..7) { "A degree is 1..7 but was $degree" }
        val targetMidi = tonic.midiValue + tonality.degreeOffsets[degree - 1]
        val letter = letterForDegree(degree)
        return spell(targetMidi, letter)
    }

    /** The diatonic letter of [degree] relative to the tonic letter. */
    fun letterForDegree(degree: Int): Letter {
        require(degree in 1..7) { "A degree is 1..7 but was $degree" }
        val index = (tonic.letter.ordinal + degree - 1) % Letter.entries.size
        return Letter.entries[index]
    }

    /** The sounding MIDI value of [degree]; spelling is not required for this. */
    fun degreeMidi(degree: Int): Int {
        require(degree in 1..7) { "A degree is 1..7 but was $degree" }
        return tonic.midiValue + tonality.degreeOffsets[degree - 1]
    }

    private fun spell(targetMidi: Int, letter: Letter): SpelledPitch =
        SpelledPitch.spellingFor(targetMidi, letter)

    override fun toString(): String = "$tonic $tonality"
}
