package com.thomas.passiveeartraining.training

import com.thomas.passiveeartraining.music.IntervalPresentation
import com.thomas.passiveeartraining.music.InvalidIntervalException
import com.thomas.passiveeartraining.music.MidiNote
import com.thomas.passiveeartraining.music.PitchClass
import com.thomas.passiveeartraining.music.SimpleInterval

/**
 * Immutable, normalized interval configuration.
 *
 * Selections are treated as sets: duplicates are removed, intervals are ordered by semitone
 * then identifier, and root pitch classes are ordered numerically. Input collections are copied,
 * so later caller mutation cannot change an existing definition.
 */
class IntervalDefinition private constructor(
    selectedIntervalIds: List<String>,
    selectedRootValues: List<Int>,
    val minimumNote: MidiNote,
    val maximumNote: MidiNote,
    val presentation: IntervalPresentation
) {
    /** Selected intervals in canonical semitone order. */
    val selectedIntervals: List<SimpleInterval> = selectedIntervalIds.map {
        SimpleInterval.ofId(it)
    }.readOnly()

    /** Selected root pitch classes in numeric order. */
    val selectedRoots: List<PitchClass> = selectedRootValues.map { PitchClass.of(it) }.readOnly()

    /** Numeric values of [selectedRoots], in the same order. */
    val rootValues: List<Int> = this.selectedRoots.map { it.value }.readOnly()

    /** Stable identifiers of [selectedIntervals], in the same order. */
    val selectedIntervalIds: List<String> = this.selectedIntervals.map { it.id }.readOnly()

    val minimumNoteValue: Int = minimumNote.value
    val maximumNoteValue: Int = maximumNote.value

    /** Inclusive sounding range as a MIDI value pair. */
    val noteRange: IntRange = minimumNoteValue..maximumNoteValue

    override fun equals(other: Any?): Boolean = other is IntervalDefinition &&
        selectedIntervalIds == other.selectedIntervalIds &&
        rootValues == other.rootValues &&
        minimumNote == other.minimumNote &&
        maximumNote == other.maximumNote &&
        presentation == other.presentation

    override fun hashCode(): Int {
        var result = selectedIntervalIds.hashCode()
        result = 31 * result + rootValues.hashCode()
        result = 31 * result + minimumNote.hashCode()
        result = 31 * result + maximumNote.hashCode()
        result = 31 * result + presentation.hashCode()
        return result
    }

    override fun toString(): String =
        "IntervalDefinition(intervals=$selectedIntervalIds, roots=$rootValues, " +
            "range=$minimumNoteValue..$maximumNoteValue, presentation=$presentation)"

    companion object {
        /** Default fixture: C4..C5, all root pitch classes, minor/major third and perfect fifth,
         * ascending. */
        fun default(): IntervalDefinition = of(
            intervalIds = listOf("minor_third", "major_third", "perfect_fifth"),
            rootValues = PitchClass.ALL.map { it.value },
            minimumNote = MidiNote.of(MidiNote.C4),
            maximumNote = MidiNote.of(72),
            presentation = IntervalPresentation.ASCENDING
        )

        /**
         * Normalizes and validates a definition.
         *
         * @throws InvalidIntervalDefinitionException when a selection is empty, an identifier is
         *   unknown, or the range is inverted.
         */
        fun of(
            intervalIds: Collection<String>,
            rootValues: Collection<Int>,
            minimumNote: MidiNote,
            maximumNote: MidiNote,
            presentation: IntervalPresentation
        ): IntervalDefinition {
            val intervals = intervalIds.toSet().map { id ->
                try {
                    SimpleInterval.ofId(id)
                } catch (error: InvalidIntervalException) {
                    throw InvalidIntervalDefinitionException(
                        error.message ?: "Unknown interval id '$id'",
                        error
                    )
                }
            }.sortedWith(compareBy({ it.semitones }, { it.id }))
            if (intervals.isEmpty()) {
                throw InvalidIntervalDefinitionException("At least one interval must be selected")
            }
            val roots = rootValues.toSet().sorted()
            roots.forEach { value ->
                if (value !in 0 until PitchClass.COUNT) {
                    throw InvalidIntervalDefinitionException(
                        "Root pitch class must be in 0..11 but was $value"
                    )
                }
            }
            if (roots.isEmpty()) {
                throw InvalidIntervalDefinitionException(
                    "At least one root pitch class must be selected"
                )
            }
            if (minimumNote.value > maximumNote.value) {
                throw InvalidIntervalDefinitionException(
                    "Minimum note ${minimumNote.value} must not exceed maximum note ${maximumNote.value}"
                )
            }
            return IntervalDefinition(
                selectedIntervalIds = intervals.map { it.id },
                selectedRootValues = roots,
                minimumNote = minimumNote,
                maximumNote = maximumNote,
                presentation = presentation
            )
        }
    }
}

/**
 * Read-only view of a freshly built list.
 *
 * Kotlin's `toList` may return a mutable implementation for some element counts, so every
 * collection a definition exposes is explicitly wrapped. A caller that tries to mutate it
 * gets [UnsupportedOperationException] instead of silently changing a definition.
 */
private fun <T> List<T>.readOnly(): List<T> = java.util.Collections.unmodifiableList(this)

/** Raised when an interval configuration is empty, unknown or internally contradictory. */
class InvalidIntervalDefinitionException(message: String, cause: Throwable? = null) :
    IllegalArgumentException(message, cause)
