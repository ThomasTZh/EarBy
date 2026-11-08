package com.thomas.passiveeartraining.music

import kotlin.math.abs

/**
 * The three harmonic functions the progression catalog teaches.
 *
 * The stable [id] is the persisted semantic value and also the spoken answer word; the matching
 * voice entries are `harmony.tonic`, `harmony.predominant` and `harmony.dominant`, so display and
 * speech can never disagree about a chord's function.
 */
enum class HarmonicFunction(val id: String) {
    TONIC("tonic"),
    PREDOMINANT("predominant"),
    DOMINANT("dominant");

    /** The English answer word; the stable id is the exact spoken text. */
    val englishAnswer: String get() = id
}

/**
 * One chord of a progression: a diatonic degree, its quality and its function.
 *
 * The theoretical degree is 1..7. Triads and seventh chords are allowed; the quality decides the
 * closed-position formula. A raised chord member is expressed through [quality] (the natural
 * minor's major V, were minor still catalogued), never by changing the degree.
 */
data class ProgressionStep(
    val degree: Int,
    val quality: ChordQuality,
    val function: HarmonicFunction
) {
    init {
        require(degree in 1..7) { "A progression degree is 1..7 but was $degree" }
        require(quality.memberCount == 3 || quality.memberCount == 4) {
            "A progression chord is a triad or a seventh chord but ${quality.id} has " +
                "${quality.memberCount} members"
        }
    }

    /** True for a four-member seventh chord. */
    val isSeventh: Boolean get() = quality.memberCount == 4

    /** The Roman numeral of this step, preserving case and quality (`I`, `vi`, `V7`, `vii°7`). */
    val romanLabel: String get() = romanNumeral(degree, quality)

    companion object {
        private val NUMERALS = listOf("I", "II", "III", "IV", "V", "VI", "VII")

        /** The conventional Roman numeral of [degree] under [quality]. */
        fun romanNumeral(degree: Int, quality: ChordQuality): String {
            require(degree in 1..7) { "A progression degree is 1..7 but was $degree" }
            val upper = NUMERALS[degree - 1]
            val lower = upper.lowercase()
            return when (quality) {
                ChordQuality.MAJOR -> upper
                ChordQuality.MINOR -> lower
                ChordQuality.DIMINISHED -> "$lower\u00B0"
                ChordQuality.AUGMENTED -> "$upper+"
                ChordQuality.MAJOR_SEVENTH -> "${upper}maj7"
                ChordQuality.DOMINANT_SEVENTH -> "${upper}7"
                ChordQuality.MINOR_SEVENTH -> "${lower}7"
                ChordQuality.HALF_DIMINISHED -> "${lower}\u00F87"
                ChordQuality.DIMINISHED_SEVENTH -> "${lower}\u00B07"
                ChordQuality.SUS2, ChordQuality.SUS4 -> throw IllegalArgumentException(
                    "No Roman numeral convention for ${quality.id}; a diatonic progression " +
                        "uses triads and seventh chords"
                )
            }
        }
    }
}

/**
 * The fourteen selectable chords of the major-key progression pool (M9B).
 *
 * The pool is the seven diatonic degrees as triads and as sevenths: the major seventh on I and
 * IV, minor sevenths on ii, iii and vi, the dominant seventh on V, and the half-diminished
 * seventh on vii. [id] is the persisted stable value, so a stored selection never depends on an
 * enum ordinal.
 *
 * The harmonic-function mapping is the same fixed teaching simplification as the earlier
 * template catalog: iii and vi count as tonic, ii and IV as predominant, V and vii as dominant.
 */
enum class DiatonicChord(
    val id: String,
    val degree: Int,
    val quality: ChordQuality,
    val function: HarmonicFunction
) {
    I("1_major", 1, ChordQuality.MAJOR, HarmonicFunction.TONIC),
    I7("1_major_seventh", 1, ChordQuality.MAJOR_SEVENTH, HarmonicFunction.TONIC),
    II("2_minor", 2, ChordQuality.MINOR, HarmonicFunction.PREDOMINANT),
    II7("2_minor_seventh", 2, ChordQuality.MINOR_SEVENTH, HarmonicFunction.PREDOMINANT),
    III("3_minor", 3, ChordQuality.MINOR, HarmonicFunction.TONIC),
    III7("3_minor_seventh", 3, ChordQuality.MINOR_SEVENTH, HarmonicFunction.TONIC),
    IV("4_major", 4, ChordQuality.MAJOR, HarmonicFunction.PREDOMINANT),
    IV7("4_major_seventh", 4, ChordQuality.MAJOR_SEVENTH, HarmonicFunction.PREDOMINANT),
    V("5_major", 5, ChordQuality.MAJOR, HarmonicFunction.DOMINANT),
    V7("5_dominant_seventh", 5, ChordQuality.DOMINANT_SEVENTH, HarmonicFunction.DOMINANT),
    VI("6_minor", 6, ChordQuality.MINOR, HarmonicFunction.TONIC),
    VI7("6_minor_seventh", 6, ChordQuality.MINOR_SEVENTH, HarmonicFunction.TONIC),
    VII("7_diminished", 7, ChordQuality.DIMINISHED, HarmonicFunction.DOMINANT),
    VII7("7_half_diminished_seventh", 7, ChordQuality.HALF_DIMINISHED, HarmonicFunction.DOMINANT);

    /** The progression step this chord stands for. */
    val step: ProgressionStep
        get() = ProgressionStep(degree, quality, function)

    /** The Roman label, for example `I`, `ii`, `V7` or `vii\u00F87`. */
    val romanLabel: String get() = step.romanLabel

    /** True for a four-member seventh chord. */
    val isSeventh: Boolean get() = quality.memberCount == 4

    /** True for the tonic-degree chords I and Imaj7, the legal opening chords. */
    val isTonic: Boolean get() = degree == 1

    companion object {
        /** The seven triads in degree order, the default pool. */
        val TRIADS: List<DiatonicChord> =
            java.util.Collections.unmodifiableList(entries.filter { !it.isSeventh })

        /** The seven seventh chords in degree order. */
        val SEVENTHS: List<DiatonicChord> =
            java.util.Collections.unmodifiableList(entries.filter { it.isSeventh })

        /** Looks up a chord by its stable id; null for an unknown id instead of guessing. */
        fun ofIdOrNull(id: String): DiatonicChord? = entries.firstOrNull { it.id == id }
    }
}

/** Which way the theoretical root moves between two consecutive chords. */
enum class RootMotionDirection(val id: String) {
    ASCENDING("ascending"),
    DESCENDING("descending"),
    UNISON("unison")
}

/**
 * One directed root-motion answer between consecutive theoretical roots.
 *
 * Only the theoretical roots are compared: the sounding bass of an inverted voicing never defines
 * a root motion ([ProgressionVoicing] keeps roots and basses separate).
 */
data class RootMotion(val direction: RootMotionDirection, val interval: SimpleInterval) {
    init {
        require((interval.semitones == 0) == (direction == RootMotionDirection.UNISON)) {
            "A root motion is a unison exactly when its interval is: $direction $interval"
        }
    }

    /** The directed interval label, for example `ascending perfect fourth`. */
    val label: String get() = "${direction.id} ${interval.englishAnswer.lowercase()}"

    companion object {
        /** The motion from [from] to [to], both already placed in one octave. */
        fun between(from: MidiNote, to: MidiNote): RootMotion {
            val delta = to.value - from.value
            val direction = when {
                delta > 0 -> RootMotionDirection.ASCENDING
                delta < 0 -> RootMotionDirection.DESCENDING
                else -> RootMotionDirection.UNISON
            }
            return RootMotion(direction, SimpleInterval.of(abs(delta)))
        }

        /** The motion between two major-key degrees, independent of the anchor octave. */
        fun betweenMajorDegrees(fromDegree: Int, toDegree: Int): RootMotion =
            betweenDegrees(Tonality.MAJOR, fromDegree, toDegree)

        /** The motion between two diatonic degree offsets of [tonality], without an anchor. */
        internal fun betweenDegrees(
            tonality: Tonality,
            fromDegree: Int,
            toDegree: Int
        ): RootMotion {
            require(fromDegree in 1..7) { "A progression degree is 1..7 but was $fromDegree" }
            require(toDegree in 1..7) { "A progression degree is 1..7 but was $toDegree" }
            val delta =
                tonality.degreeOffsets[toDegree - 1] - tonality.degreeOffsets[fromDegree - 1]
            val direction = when {
                delta > 0 -> RootMotionDirection.ASCENDING
                delta < 0 -> RootMotionDirection.DESCENDING
                else -> RootMotionDirection.UNISON
            }
            return RootMotion(direction, SimpleInterval.of(abs(delta)))
        }
    }
}

/**
 * Pure theory helpers over the progression catalog.
 *
 * A root is diatonic: its sounding value is the anchor plus the major scale's degree offset. The
 * spelled root follows the key's theoretical letter for that degree, so an accidental is never
 * relabelled. Nothing here voices a chord; [ProgressionVoicing] owns placement and inversion.
 */
object ProgressionTheory {

    /** The diatonic root of [step] at [anchor] plus the tonality offset, or null outside MIDI. */
    fun rootMidi(step: ProgressionStep, tonality: Tonality, anchor: MidiNote): MidiNote? =
        anchor.transposedBy(tonality.degreeOffsets[step.degree - 1])

    /** The key-aware spelling of [step]'s root at the anchor's octave, or null outside MIDI. */
    fun spelledRoot(step: ProgressionStep, key: Key, anchor: MidiNote): SpelledPitch? {
        val midi = rootMidi(step, key.tonality, anchor) ?: return null
        return SpelledPitch.spellingFor(midi.value, key.letterForDegree(step.degree))
    }

    /**
     * The directed motion between the theoretical roots of two consecutive steps at [anchor].
     *
     * @throws InvalidMidiNoteException when a root cannot be placed at the anchor's octave.
     */
    fun rootMotion(
        from: ProgressionStep,
        to: ProgressionStep,
        tonality: Tonality,
        anchor: MidiNote
    ): RootMotion {
        val first = rootMidi(from, tonality, anchor)
            ?: throw InvalidMidiNoteException(
                "Degree ${from.degree} of $tonality cannot sound at $anchor"
            )
        val second = rootMidi(to, tonality, anchor)
            ?: throw InvalidMidiNoteException(
                "Degree ${to.degree} of $tonality cannot sound at $anchor"
            )
        return RootMotion.between(first, second)
    }

    /** Every directed root motion of [steps] in major, in step order. */
    fun rootMotions(steps: List<ProgressionStep>): List<RootMotion> =
        steps.zipWithNext { from, to ->
            RootMotion.betweenMajorDegrees(from.degree, to.degree)
        }
}
