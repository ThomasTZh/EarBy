package com.thomas.passiveeartraining.music

/**
 * The eleven chord qualities of the frozen WP5.2 vocabulary.
 *
 * [semitones] are the root-relative sounding offsets; [letterSteps] are the theoretical diatonic
 * letter distances from the spelled root, so a chord's members keep their musical roles (a seventh
 * is always a seventh, never a relabeled sixth).
 */
enum class ChordQuality(val id: String, val semitones: List<Int>, val letterSteps: List<Int>) {
    MAJOR("major", listOf(0, 4, 7), listOf(0, 2, 4)),
    MINOR("minor", listOf(0, 3, 7), listOf(0, 2, 4)),
    DIMINISHED("diminished", listOf(0, 3, 6), listOf(0, 2, 4)),
    AUGMENTED("augmented", listOf(0, 4, 8), listOf(0, 2, 4)),
    SUS2("sus2", listOf(0, 2, 7), listOf(0, 1, 4)),
    SUS4("sus4", listOf(0, 5, 7), listOf(0, 3, 4)),
    MAJOR_SEVENTH("major_seventh", listOf(0, 4, 7, 11), listOf(0, 2, 4, 6)),
    MINOR_SEVENTH("minor_seventh", listOf(0, 3, 7, 10), listOf(0, 2, 4, 6)),
    DOMINANT_SEVENTH("dominant_seventh", listOf(0, 4, 7, 10), listOf(0, 2, 4, 6)),
    HALF_DIMINISHED("half_diminished", listOf(0, 3, 6, 10), listOf(0, 2, 4, 6)),
    DIMINISHED_SEVENTH("diminished_seventh", listOf(0, 3, 6, 9), listOf(0, 2, 4, 6));

    /** How many distinct chord members this quality has. */
    val memberCount: Int get() = semitones.size

    /** How many inversions apply: triad 0..2, seventh 0..3. */
    val maximumInversion: Int get() = memberCount - 1

    /** The English answer name used by speech and display. */
    val englishAnswer: String
        get() = when (this) {
            SUS2 -> "sus two"
            SUS4 -> "sus four"
            else -> id.replace('_', ' ')
        }

    companion object {
        fun ofIdOrNull(id: String): ChordQuality? = entries.firstOrNull { it.id == id }
    }
}

/** How a chord example is presented: all notes together, or an ascending arpeggio. */
enum class ChordPresentation {
    HARMONIC,
    ARPEGGIO
}

/** The English inversion labels: 0 = "root position", 1 = "first inversion", and so on. */
object ChordInversions {
    private val ORDINALS = listOf("root", "first", "second", "third")

    /** The answer text of [inversion]. */
    fun answer(inversion: Int): String {
        require(inversion in 0..3) { "An inversion is 0..3 but was $inversion" }
        return if (inversion == 0) "root position" else "${ORDINALS[inversion]} inversion"
    }

    /** The stable suffix used by inversion voice ids: `root`, `first`, `second`, `third`. */
    fun idSuffix(inversion: Int): String {
        require(inversion in 0..3) { "An inversion is 0..3 but was $inversion" }
        return ORDINALS[inversion]
    }
}

/**
 * One closed-position chord voicing.
 *
 * [theoreticalRoot] is the spelled root the quality was applied to; [bass] is the lowest sounding
 * note after the inversion, which can differ from the root. The sounding notes are sorted
 * ascending and contain no duplicates; inversion k raises the first k formula members by an
 * octave rather than using modulo equality.
 */
data class ChordVoicing(
    val quality: ChordQuality,
    val inversion: Int,
    val theoreticalRoot: SpelledPitch,
    val notes: List<MidiNote>
) {
    init {
        require(inversion in 0..quality.maximumInversion) {
            "Inversion $inversion does not apply to ${quality.id}"
        }
        require(notes.isNotEmpty()) { "A voicing must sound at least one note" }
        require(notes == notes.sorted()) { "A voicing's notes must be sorted ascending" }
    }

    /** The lowest sounding note; equal to the theoretical root only in root position. */
    val bass: MidiNote get() = notes.first()

    /**
     * The theoretical chord members in formula order, spelled from the written root.
     *
     * This is independent of the inversion: a raised bass note in [notes] does not change which
     * musical member it is. Diminished sevenths keep their double-flat seventh here.
     */
    fun spelledMembers(): List<SpelledPitch> {
        val rootMidi = theoreticalRoot.midiValue
        return quality.semitones.mapIndexed { index, semitone ->
            val letterIndex =
                (theoreticalRoot.letter.ordinal + quality.letterSteps[index]) % Letter.entries.size
            SpelledPitch.spellingFor(rootMidi + semitone, Letter.entries[letterIndex])
        }
    }

    companion object {
        /**
         * Builds a closed-position voicing from a spelled root's sounding value.
         *
         * [spelling] picks the theoretical root (sharp or flat chromatic spelling); it decides the
         * member letters, so a Db major chord spells F and Ab while a C# major chord spells E# and
         * G#.
         *
         * @throws IllegalArgumentException when a raised/moved member leaves the MIDI range or the
         *   theoretical spelling cannot be represented within the accidental bounds.
         */
        fun of(
            quality: ChordQuality,
            inversion: Int,
            root: MidiNote,
            spelling: SpellingPreference = SpellingPreference.SHARP
        ): ChordVoicing {
            require(inversion in 0..quality.maximumInversion) {
                "Inversion $inversion does not apply to ${quality.id}"
            }
            val original = quality.semitones.map { offset ->
                root.transposedBy(offset)
                    ?: throw IllegalArgumentException(
                        "Chord ${quality.id} on $root leaves the MIDI range at +$offset"
                    )
            }
            val adjustment = if (inversion == 0) {
                original
            } else {
                original.mapIndexed { index, note ->
                    if (index < inversion) {
                        note.transposedBy(MidiNote.SEMITONES_PER_OCTAVE)
                            ?: throw IllegalArgumentException(
                                "Inversion $inversion of ${quality.id} on $root leaves the MIDI range"
                            )
                    } else {
                        note
                    }
                }
            }
            val sounding = adjustment.sorted()
            val rootLetter = SpelledPitch.fromMidi(root, spelling).letter
            val spelledRoot = SpelledPitch.spellingFor(root.value, rootLetter)
            return ChordVoicing(quality, inversion, spelledRoot, sounding)
        }
    }
}

/**
 * The thirteen scale formulas of the frozen WP5.3 vocabulary.
 *
 * Major/Ionian and natural minor/Aeolian are single definitions with display aliases, so their
 * sampling weight is never doubled. [letterSteps] carry the theoretical spelling pattern;
 * whole tone uses the documented six-letter pattern 1,2,3,#4,#5,#6.
 */
enum class ScaleKind(
    val id: String,
    val semitones: List<Int>,
    val letterSteps: List<Int>,
    val aliases: List<String> = emptyList()
) {
    MAJOR("major", listOf(0, 2, 4, 5, 7, 9, 11), listOf(0, 1, 2, 3, 4, 5, 6), listOf("ionian")),
    NATURAL_MINOR(
        "natural_minor",
        listOf(0, 2, 3, 5, 7, 8, 10),
        listOf(0, 1, 2, 3, 4, 5, 6),
        listOf("aeolian")
    ),
    HARMONIC_MINOR("harmonic_minor", listOf(0, 2, 3, 5, 7, 8, 11), listOf(0, 1, 2, 3, 4, 5, 6)),
    JAZZ_MELODIC_MINOR(
        "jazz_melodic_minor",
        listOf(0, 2, 3, 5, 7, 9, 11),
        listOf(0, 1, 2, 3, 4, 5, 6)
    ),
    DORIAN("dorian", listOf(0, 2, 3, 5, 7, 9, 10), listOf(0, 1, 2, 3, 4, 5, 6)),
    PHRYGIAN("phrygian", listOf(0, 1, 3, 5, 7, 8, 10), listOf(0, 1, 2, 3, 4, 5, 6)),
    LYDIAN("lydian", listOf(0, 2, 4, 6, 7, 9, 11), listOf(0, 1, 2, 3, 4, 5, 6)),
    MIXOLYDIAN("mixolydian", listOf(0, 2, 4, 5, 7, 9, 10), listOf(0, 1, 2, 3, 4, 5, 6)),
    LOCRIAN("locrian", listOf(0, 1, 3, 5, 6, 8, 10), listOf(0, 1, 2, 3, 4, 5, 6)),
    MAJOR_PENTATONIC("major_pentatonic", listOf(0, 2, 4, 7, 9), listOf(0, 1, 2, 4, 5)),
    MINOR_PENTATONIC("minor_pentatonic", listOf(0, 3, 5, 7, 10), listOf(0, 2, 3, 4, 6)),
    MINOR_BLUES("minor_blues", listOf(0, 3, 5, 6, 7, 10), listOf(0, 2, 3, 4, 4, 6)),
    WHOLE_TONE("whole_tone", listOf(0, 2, 4, 6, 8, 10), listOf(0, 1, 2, 3, 4, 5));

    /** True when the formula has one member per diatonic letter. */
    val isHeptatonic: Boolean get() = semitones.size == 7

    companion object {
        fun ofIdOrNull(id: String): ScaleKind? =
            entries.firstOrNull { it.id == id || id in it.aliases }
    }
}

/** Which way a scale traversal plays. */
enum class ScaleDirection {
    ASCENDING,
    DESCENDING,

    /** Up then down, with the top tonic sounding exactly once. */
    BOTH
}

/**
 * One complete scale traversal.
 *
 * A complete octave includes the repeated top tonic, so an ascending seven-note scale has eight
 * notes. Descending reverses that traversal; [ScaleDirection.BOTH] joins them without repeating
 * the top tonic. Spelling follows the formula's theoretical letters.
 */
object ScaleTraversal {

    /** The spelled pitches of [kind] from [tonic] in [direction]. */
    fun pitches(
        kind: ScaleKind,
        tonic: SpelledPitch,
        direction: ScaleDirection
    ): List<SpelledPitch> {
        val ascending = ascending(kind, tonic)
        val descending = ascending.reversed()
        return when (direction) {
            ScaleDirection.ASCENDING -> ascending
            ScaleDirection.DESCENDING -> descending
            ScaleDirection.BOTH -> ascending + descending.drop(1)
        }
    }

    private fun ascending(kind: ScaleKind, tonic: SpelledPitch): List<SpelledPitch> {
        val result = mutableListOf(tonic)
        kind.semitones.forEachIndexed { index, semitone ->
            if (index == 0) return@forEachIndexed
            val target = tonic.midiValue + semitone
            val letterIndex = (tonic.letter.ordinal + kind.letterSteps[index]) % Letter.entries.size
            result += SpelledPitch.spellingFor(target, Letter.entries[letterIndex])
        }
        // The repeated top tonic keeps the tonic's spelling one octave up.
        val topTonic = tonic.copy(writtenOctave = tonic.writtenOctave + 1)
        result += topTonic
        return result
    }
}
