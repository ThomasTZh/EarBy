package com.thomas.passiveeartraining.music

import kotlin.math.abs

/** How a progression's chords are voiced (M9A.1). */
enum class ProgressionVoicingMode {
    /** Every chord is its root-position closed chord at the anchor's diatonic offset. */
    ROOT_POSITION,

    /** The allowed inversions are searched for one global minimum-movement sequence. */
    NEAREST_INVERSION
}

/**
 * The finite, deterministic voicing rules of M9A.2, extended to seventh chords.
 *
 * Root position places each chord root at `anchor + diatonic offset` and applies the quality's
 * formula there. Nearest inversion enumerates each step's closed-chord candidates from the allowed
 * inversions and feasible octave placements, anchors the first chord in root position at the
 * chosen key anchor (or picks the lexicographically first allowed feasible first-chord tuple when
 * root inversion is forbidden), and then runs a dynamic program over that finite candidate graph.
 *
 * The program minimizes the sum of absolute semitone movement between sorted corresponding voices
 * of every adjacent pair and breaks equal total costs by lexicographic comparison of the entire
 * sequence of MIDI tuples, which specifies one global sequence minimum. When the two chords have
 * different member counts (a triad next to a seventh), the common lower voices are compared
 * index-wise and each extra voice is measured to the nearest voice of the other chord, so a mixed
 * pool still has one deterministic cost. The theoretical root and the sounding bass stay separate
 * values on every candidate, so an inverted bass never rewrites a root motion.
 *
 * Every function is pure and consumes no randomness. A combination that cannot be represented in
 * MIDI 0..127 or cannot fit the inclusive range fails with `null`; nothing is silently transposed
 * or clamped.
 */
object ProgressionVoicing {

    /**
     * The root-position closed chord of [step] at `anchor + the tonality offset`.
     *
     * The theoretical root is spelled with the key's letter for the degree, so an accidental is
     * never relabelled. Returns `null` when a member leaves the MIDI range.
     */
    fun rootPositionChord(step: ProgressionStep, key: Key, anchor: MidiNote): ChordVoicing? {
        val root = ProgressionTheory.rootMidi(step, key.tonality, anchor) ?: return null
        return closedChord(
            step.quality,
            inversion = 0,
            root = root,
            letter = key.letterForDegree(step.degree)
        )
    }

    /**
     * Every root-position chord of [steps] at [anchor], or `null` when one chord cannot be
     * represented or does not fit [range].
     */
    fun rootPosition(
        steps: List<ProgressionStep>,
        key: Key,
        anchor: MidiNote,
        range: IntRange
    ): List<ChordVoicing>? {
        require(steps.isNotEmpty()) { "A progression must have at least one chord" }
        require(range.first <= range.last) { "A range must not be inverted: $range" }
        val chords = steps.map { step ->
            rootPositionChord(step, key, anchor) ?: return null
        }
        if (chords.any { chord -> chord.notes.any { it.value !in range } }) return null
        return chords
    }

    /**
     * Every feasible closed-chord placement of [step] under [allowedInversions].
     *
     * The theoretical root keeps the key's diatonic pitch class and spelled letter; its octave
     * placement is free inside the window that could possibly sound inside [range]. Candidates are
     * ordered by inversion ascending, then root placement ascending. Inversions the chord quality
     * does not have (third inversion of a triad) are skipped. A candidate keeps its theoretical
     * root even when the sounding bass is another member.
     */
    fun candidatesFor(
        step: ProgressionStep,
        key: Key,
        allowedInversions: Collection<Int>,
        range: IntRange
    ): List<ChordVoicing> {
        require(range.first <= range.last) { "A range must not be inverted: $range" }
        val inversions = validatedInversions(allowedInversions)
            .filter { it <= step.quality.maximumInversion }
        val tonicPitchClass = key.tonic.midiValue.mod(MidiNote.SEMITONES_PER_OCTAVE)
        val rootPitchClass = (tonicPitchClass + key.tonality.degreeOffsets[step.degree - 1])
            .mod(MidiNote.SEMITONES_PER_OCTAVE)
        val letter = key.letterForDegree(step.degree)
        val candidates = mutableListOf<ChordVoicing>()
        for (inversion in inversions) {
            // The sounding bass of inversion k is the k-th formula member, so the root can sit
            // below the range by exactly that member's offset and still sound inside it.
            val minimumRoot = maxOf(
                MidiNote.MIN_VALUE,
                range.first - step.quality.semitones[inversion]
            )
            for (rootValue in minimumRoot..range.last) {
                val root = MidiNote.of(rootValue)
                if (root.pitchClass.value != rootPitchClass) continue
                val voicing = closedChord(step.quality, inversion, root, letter) ?: continue
                if (voicing.notes.all { it.value in range }) candidates += voicing
            }
        }
        return candidates
    }

    /**
     * The nearest-inversion realization of [steps], or `null` when no complete sequence exists.
     *
     * The first chord is fixed before the search: with root inversion allowed it is the
     * root-position chord at the anchor's diatonic offset, and with root inversion forbidden it is
     * the lexicographically first allowed feasible first-chord MIDI tuple. Every later step is
     * chosen by the global dynamic program described on [ProgressionVoicing].
     */
    fun nearestInversion(
        steps: List<ProgressionStep>,
        key: Key,
        anchor: MidiNote,
        allowedInversions: Collection<Int>,
        range: IntRange
    ): List<ChordVoicing>? {
        require(steps.isNotEmpty()) { "A progression must have at least one chord" }
        require(range.first <= range.last) { "A range must not be inverted: $range" }
        val inversions = validatedInversions(allowedInversions)
        val candidates = steps.map { step -> candidatesFor(step, key, inversions, range) }
        if (candidates.any { it.isEmpty() }) return null

        val firstIndex = if (0 in inversions) {
            val anchored = rootPositionChord(steps.first(), key, anchor) ?: return null
            if (anchored.notes.any { it.value !in range }) return null
            candidates.first().indexOfFirst { it == anchored }.takeIf { it >= 0 } ?: return null
        } else {
            lexicographicallyFirstIndex(candidates.first())
        }

        var states: List<Path> = listOf(
            Path(
                cost = 0,
                midiTuples = listOf(candidates.first()[firstIndex].notes.map { it.value }),
                chords = listOf(candidates.first()[firstIndex])
            )
        )
        for (stepIndex in 1 until steps.size) {
            states = candidates[stepIndex].map { candidate ->
                var best: Path? = null
                states.forEach { previous ->
                    val extended = Path(
                        cost = previous.cost + movement(previous.chords.last(), candidate),
                        midiTuples = previous.midiTuples + listOf(candidate.notes.map { it.value }),
                        chords = previous.chords + candidate
                    )
                    if (best == null || isBetter(extended, best)) best = extended
                }
                best!!
            }
        }
        var winner = states.first()
        states.drop(1).forEach { candidate ->
            if (isBetter(candidate, winner)) winner = candidate
        }
        return winner.chords
    }

    /**
     * The realization of [steps] under [mode], or `null` when no complete sequence exists.
     * [allowedInversions] only affects [ProgressionVoicingMode.NEAREST_INVERSION].
     */
    fun realize(
        steps: List<ProgressionStep>,
        key: Key,
        anchor: MidiNote,
        mode: ProgressionVoicingMode,
        allowedInversions: Collection<Int>,
        range: IntRange
    ): List<ChordVoicing>? = when (mode) {
        ProgressionVoicingMode.ROOT_POSITION -> rootPosition(steps, key, anchor, range)
        ProgressionVoicingMode.NEAREST_INVERSION -> nearestInversion(
            steps,
            key,
            anchor,
            allowedInversions,
            range
        )
    }

    /** One dynamic-programming state: total cost, the concrete sequence and its chords. */
    private data class Path(
        val cost: Int,
        val midiTuples: List<List<Int>>,
        val chords: List<ChordVoicing>
    )

    private fun validatedInversions(allowedInversions: Collection<Int>): List<Int> {
        val inversions = allowedInversions.toSortedSet().toList()
        require(inversions.isNotEmpty()) {
            "A voicing search needs at least one allowed inversion"
        }
        require(inversions.all { it in 0..3 }) {
            "Allowed inversions are 0..3 but were $allowedInversions"
        }
        return inversions
    }

    private fun lexicographicallyFirstIndex(candidates: List<ChordVoicing>): Int {
        var bestIndex = 0
        candidates.forEachIndexed { index, candidate ->
            val compared = compareTuple(
                candidate.notes.map { it.value },
                candidates[bestIndex].notes.map { it.value }
            )
            if (compared < 0) {
                bestIndex = index
            }
        }
        return bestIndex
    }

    private fun isBetter(candidate: Path, current: Path): Boolean = candidate.cost < current.cost ||
        (
            candidate.cost == current.cost &&
                compareMidiTuples(candidate.midiTuples, current.midiTuples) < 0
            )

    /** Element-wise lexicographic comparison of two sequences of MIDI tuples. */
    private fun compareMidiTuples(a: List<List<Int>>, b: List<List<Int>>): Int {
        val tupleCount = minOf(a.size, b.size)
        for (tupleIndex in 0 until tupleCount) {
            val compared = compareTuple(a[tupleIndex], b[tupleIndex])
            if (compared != 0) return compared
        }
        return a.size.compareTo(b.size)
    }

    /** Element-wise lexicographic comparison of two MIDI tuples. */
    private fun compareTuple(a: List<Int>, b: List<Int>): Int {
        val elementCount = minOf(a.size, b.size)
        for (elementIndex in 0 until elementCount) {
            val compared = a[elementIndex].compareTo(b[elementIndex])
            if (compared != 0) return compared
        }
        return a.size.compareTo(b.size)
    }

    /**
     * The sum of absolute semitone movement between two chords.
     *
     * Equal member counts compare sorted corresponding voices exactly as before. A mixed pair
     * compares the common lower voices index-wise and charges each extra voice its distance to
     * the nearest voice of the other chord, so the cost stays deterministic and small.
     */
    private fun movement(a: ChordVoicing, b: ChordVoicing): Int {
        val common = minOf(a.notes.size, b.notes.size)
        var total = 0
        for (index in 0 until common) {
            total += abs(a.notes[index].value - b.notes[index].value)
        }
        val extra = if (a.notes.size > common) a.notes.drop(common) else b.notes.drop(common)
        val other = if (a.notes.size > common) b.notes else a.notes
        extra.forEach { note ->
            total += other.minOfOrNull { candidate -> abs(note.value - candidate.value) } ?: 0
        }
        return total
    }

    /**
     * One closed chord at [root], applying inversion by raising the first members an octave.
     *
     * The theoretical root is spelled with [letter] and keeps the root's octave placement; an
     * inversion changes only which member sounds lowest. Returns `null` when a sounding member
     * leaves MIDI 0..127.
     */
    private fun closedChord(
        quality: ChordQuality,
        inversion: Int,
        root: MidiNote,
        letter: Letter
    ): ChordVoicing? {
        require(inversion in 0..quality.maximumInversion) {
            "Inversion $inversion does not apply to ${quality.id}"
        }
        val original = quality.semitones.map { offset ->
            root.transposedBy(offset) ?: return null
        }
        val adjusted = if (inversion == 0) {
            original
        } else {
            original.mapIndexed { index, note ->
                if (index < inversion) {
                    note.transposedBy(MidiNote.SEMITONES_PER_OCTAVE) ?: return null
                } else {
                    note
                }
            }
        }
        val spelledRoot = SpelledPitch.spellingFor(root.value, letter)
        return ChordVoicing(quality, inversion, spelledRoot, adjusted.sorted())
    }
}
