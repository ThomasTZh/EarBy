package com.thomas.passiveeartraining.music

/**
 * A simple interval: a distance of 0..12 semitones inside one octave.
 *
 * The stable string [id] is a persistence and asset identifier; [englishAnswer] is the
 * shipped answer text. Neither depends on the sounding spelling of the two notes, so
 * interval quality is never inferred from how a note happens to be written.
 */
@JvmInline
value class SimpleInterval private constructor(val semitones: Int) {

    /** Stable identifier such as `minor_third`; never a serialized enum ordinal. */
    val id: String
        get() = IDS[semitones]

    /** Shipped English answer text, for example `Minor third`. */
    val englishAnswer: String
        get() = ANSWERS[semitones]

    /** Semantic answer identifier used by voice assets, for example `interval.minor_third`. */
    val answerId: String
        get() = "$ANSWER_ID_PREFIX$id"

    override fun toString(): String = id

    companion object {
        const val ANSWER_ID_PREFIX: String = "interval."

        private val IDS = listOf(
            "unison",
            "minor_second",
            "major_second",
            "minor_third",
            "major_third",
            "perfect_fourth",
            "tritone",
            "perfect_fifth",
            "minor_sixth",
            "major_sixth",
            "minor_seventh",
            "major_seventh",
            "octave"
        )

        private val ANSWERS = IDS.map { id ->
            id.replace('_', ' ').replaceFirstChar { it.uppercaseChar() }
        }

        /** All simple intervals in canonical ascending semitone order. */
        val ALL: List<SimpleInterval> = List(IDS.size) { SimpleInterval(it) }

        const val UNISON: Int = 0
        const val MINOR_SECOND: Int = 1
        const val MAJOR_SECOND: Int = 2
        const val MINOR_THIRD: Int = 3
        const val MAJOR_THIRD: Int = 4
        const val PERFECT_FOURTH: Int = 5
        const val TRITONE: Int = 6
        const val PERFECT_FIFTH: Int = 7
        const val MINOR_SIXTH: Int = 8
        const val MAJOR_SIXTH: Int = 9
        const val MINOR_SEVENTH: Int = 10
        const val MAJOR_SEVENTH: Int = 11
        const val OCTAVE: Int = 12

        /** Creates an interval from a semitone count, rejecting values outside 0..12. */
        fun of(semitones: Int): SimpleInterval {
            if (semitones !in IDS.indices) {
                throw InvalidIntervalException(
                    "Simple interval semitones must be in 0..${IDS.lastIndex} but was $semitones"
                )
            }
            return SimpleInterval(semitones)
        }

        /** Creates an interval, returning `null` instead of throwing for unsupported distances. */
        fun ofOrNull(semitones: Int): SimpleInterval? = if (semitones in
            IDS.indices
        ) {
            SimpleInterval(semitones)
        } else {
            null
        }

        /** Looks up an interval by its stable identifier. */
        fun ofId(id: String): SimpleInterval {
            val semitones = IDS.indexOf(id)
            if (semitones < 0) {
                throw InvalidIntervalException("Unknown simple interval id '$id'")
            }
            return SimpleInterval(semitones)
        }

        /** Looks up an interval by identifier, returning `null` when it is not a simple interval. */
        fun ofIdOrNull(id: String): SimpleInterval? =
            IDS.indexOf(id).takeIf { it >= 0 }?.let { SimpleInterval(it) }

        /**
         * Sounding distance from [root] to [target], or `null` when it is not a simple interval.
         *
         * The result is direction-independent, matching the recognition examples where the
         * same label is used for ascending, descending and harmonic presentations.
         */
        fun between(root: MidiNote, target: MidiNote): SimpleInterval? =
            ofOrNull((target.value - root.value).let { if (it < 0) -it else it })
    }
}

/** Raised when an interval semitone count or identifier is not supported. */
class InvalidIntervalException(message: String) : IllegalArgumentException(message)

/**
 * How the two notes of an interval are laid out in the example.
 *
 * The root is always the first melodic note or the lower harmonic note.
 */
enum class IntervalPresentation {
    /** Root, then root + interval. */
    ASCENDING,

    /** Root, then root - interval. */
    DESCENDING,

    /** Root and root + interval sounding together. */
    HARMONIC
    ;

    /**
     * Sounding notes of the example in play order, or `null` when they leave the MIDI range.
     *
     * A melodic unison sounds the same pitch twice, as two separate note onsets, so both example
     * slots are filled. A harmonic unison stays one note, because two simultaneous copies of one
     * pitch cannot be told apart.
     */
    fun notes(root: MidiNote, interval: SimpleInterval): List<MidiNote>? {
        val offset = when (this) {
            ASCENDING, HARMONIC -> interval.semitones
            DESCENDING -> -interval.semitones
        }
        val target = root.transposedBy(offset) ?: return null
        return if (target == root && this == HARMONIC) listOf(root) else listOf(root, target)
    }

    /**
     * Root note for an example whose lowest sounding note is [lowestNote].
     *
     * For a descending interval the root is the higher note, so the requested lowest note
     * is raised by the interval. Returns `null` when the resulting example is not representable.
     */
    fun rootFor(lowestNote: MidiNote, interval: SimpleInterval): MidiNote? {
        val root = when (this) {
            ASCENDING, HARMONIC -> lowestNote
            DESCENDING -> lowestNote.transposedBy(interval.semitones) ?: return null
        }
        return root.takeIf { notes(it, interval) != null }
    }

    /** True when the example is representable for this root and interval. */
    fun isFeasible(root: MidiNote, interval: SimpleInterval): Boolean =
        notes(root, interval) != null
}
