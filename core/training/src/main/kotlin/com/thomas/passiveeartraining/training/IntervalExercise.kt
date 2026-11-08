package com.thomas.passiveeartraining.training

import com.thomas.passiveeartraining.music.IntervalPresentation
import com.thomas.passiveeartraining.music.MidiNote
import com.thomas.passiveeartraining.music.SimpleInterval

/**
 * One concrete, immutable interval exercise.
 *
 * The concrete pitches are recorded together with the identity that produced them, so a
 * retained item can be replayed through Previous/Repeat without consuming randomness.
 */
class IntervalExercise internal constructor(
    val identity: GeneratorIdentity,
    val sequenceIndex: Int,
    val cycleIndex: Int,
    val positionInCycle: Int,
    val interval: SimpleInterval,
    val root: MidiNote,
    val target: MidiNote,
    /** The concrete presentation of this exercise; version 2 may vary it within one sequence. */
    val presentation: IntervalPresentation = identity.definition.presentation
) {
    val labelId: String get() = interval.id
    val semitones: Int get() = interval.semitones

    /** Semantic answer identifier, for example `interval.perfect_fifth`. */
    val answerId: String get() = interval.answerId

    /** Sounding notes in play order; a melodic unison repeats the pitch, a harmonic unison is one note. */
    val notes: List<MidiNote> get() = presentation.notes(root, interval) ?: emptyList()

    override fun equals(other: Any?): Boolean = other is IntervalExercise &&
        identity == other.identity &&
        sequenceIndex == other.sequenceIndex &&
        cycleIndex == other.cycleIndex &&
        positionInCycle == other.positionInCycle &&
        interval == other.interval &&
        root == other.root &&
        target == other.target &&
        presentation == other.presentation

    override fun hashCode(): Int {
        var result = identity.hashCode()
        result = 31 * result + sequenceIndex
        result = 31 * result + cycleIndex
        result = 31 * result + positionInCycle
        result = 31 * result + interval.hashCode()
        result = 31 * result + root.hashCode()
        result = 31 * result + target.hashCode()
        result = 31 * result + presentation.hashCode()
        return result
    }

    override fun toString(): String = "IntervalExercise(#$sequenceIndex cycle=$cycleIndex " +
        "position=$positionInCycle $interval $root->$target answer=$answerId)"
}
