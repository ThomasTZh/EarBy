package com.thomas.passiveeartraining.training

import com.thomas.passiveeartraining.music.MidiNote
import com.thomas.passiveeartraining.music.PitchClass
import com.thomas.passiveeartraining.music.ScaleDirection
import com.thomas.passiveeartraining.music.ScaleKind

/**
 * Immutable, normalized scale configuration (M6).
 *
 * Selections are sets: canonical ids and the documented aliases are normalized once, duplicates
 * are removed, and scales are ordered by the frozen catalog. Root pitch classes are ordered
 * numerically. The fixed root, when present, is the lower tonic of the complete octave and must
 * agree with the selection and the range. A selection whose scales cannot be realized inside the
 * range is rejected by the provider rather than silently truncated here.
 */
class ScaleDefinition private constructor(
    val selectedScales: List<ScaleKind>,
    val direction: ScaleDirection,
    private val selectedRootValues: List<Int>,
    val fixedRoot: MidiNote?,
    val minimumNote: MidiNote,
    val maximumNote: MidiNote
) {
    /** Selected root pitch classes in numeric order. */
    val selectedRoots: List<PitchClass> = java.util.Collections.unmodifiableList(
        selectedRootValues.map { PitchClass.of(it) }
    )

    /** Numeric values of [selectedRoots], in the same order. */
    val rootValues: List<Int> = java.util.Collections.unmodifiableList(
        this.selectedRoots.map { it.value }
    )

    val minimumNoteValue: Int = minimumNote.value
    val maximumNoteValue: Int = maximumNote.value

    /** Inclusive sounding range as a MIDI value pair. */
    val noteRange: IntRange = minimumNoteValue..maximumNoteValue

    override fun equals(other: Any?): Boolean = other is ScaleDefinition &&
        selectedScales == other.selectedScales &&
        direction == other.direction &&
        rootValues == other.rootValues &&
        fixedRoot == other.fixedRoot &&
        minimumNote == other.minimumNote &&
        maximumNote == other.maximumNote

    override fun hashCode(): Int {
        var result = selectedScales.hashCode()
        result = 31 * result + direction.hashCode()
        result = 31 * result + rootValues.hashCode()
        result = 31 * result + (fixedRoot?.hashCode() ?: 0)
        result = 31 * result + minimumNote.hashCode()
        result = 31 * result + maximumNote.hashCode()
        return result
    }

    override fun toString(): String =
        "ScaleDefinition(scales=${selectedScales.map { it.id }}, direction=$direction, " +
            "roots=$rootValues, fixedRoot=$fixedRoot, " +
            "range=$minimumNoteValue..$maximumNoteValue)"

    companion object {
        /** Default fixture: major and natural minor, ascending, all roots, C3..C6. */
        fun default(): ScaleDefinition = of(
            scaleIds = listOf("major", "natural_minor"),
            direction = ScaleDirection.ASCENDING,
            rootValues = PitchClass.ALL.map { it.value },
            fixedRoot = null,
            minimumNote = MidiNote.of(48),
            maximumNote = MidiNote.of(84)
        )

        /**
         * Normalizes and validates a definition.
         *
         * The documented aliases (`ionian`, `aeolian`) resolve to their canonical kinds before
         * deduplication, so one sounding scale never carries double sampling weight.
         *
         * @throws InvalidScaleDefinitionException when a selection is empty, an identifier is
         *   unknown, the range is inverted, or a fixed root contradicts the selection. The
         *   exception names the setup field (`scales`, `roots`, `range` or `fixedRoot`) the failure
         *   belongs to.
         */
        fun of(
            scaleIds: Collection<String>,
            direction: ScaleDirection,
            rootValues: Collection<Int>,
            fixedRoot: MidiNote?,
            minimumNote: MidiNote,
            maximumNote: MidiNote
        ): ScaleDefinition {
            val scales = scaleIds.map { id ->
                ScaleKind.ofIdOrNull(id)
                    ?: throw InvalidScaleDefinitionException(
                        "Unknown scale id '$id'",
                        field = "scales"
                    )
            }.distinct().sortedBy { it.ordinal }
            if (scales.isEmpty()) {
                throw InvalidScaleDefinitionException(
                    "At least one scale must be selected",
                    field = "scales"
                )
            }
            val roots = rootValues.toSet().sorted()
            roots.forEach { value ->
                if (value !in 0 until PitchClass.COUNT) {
                    throw InvalidScaleDefinitionException(
                        "Root pitch class must be in 0..11 but was $value",
                        field = "roots"
                    )
                }
            }
            if (roots.isEmpty()) {
                throw InvalidScaleDefinitionException(
                    "At least one root pitch class must be selected",
                    field = "roots"
                )
            }
            if (minimumNote.value > maximumNote.value) {
                throw InvalidScaleDefinitionException(
                    "Minimum note ${minimumNote.value} must not exceed maximum note " +
                        "${maximumNote.value}",
                    field = "range"
                )
            }
            fixedRoot?.let { root ->
                if (root.value !in minimumNote.value..maximumNote.value) {
                    throw InvalidScaleDefinitionException(
                        "A fixed root $root must be inside the configured range " +
                            "${minimumNote.value}..${maximumNote.value}",
                        field = "fixedRoot"
                    )
                }
                if (root.pitchClass.value !in roots) {
                    throw InvalidScaleDefinitionException(
                        "A fixed root $root must match a selected root class",
                        field = "fixedRoot"
                    )
                }
            }
            return ScaleDefinition(
                selectedScales = scales,
                direction = direction,
                selectedRootValues = roots,
                fixedRoot = fixedRoot,
                minimumNote = minimumNote,
                maximumNote = maximumNote
            )
        }
    }
}

/** Raised when a scale configuration is empty, unknown or internally contradictory. */
class InvalidScaleDefinitionException(
    message: String,
    cause: Throwable? = null,
    /** The setup field the failure belongs to, for form feedback. */
    val field: String? = null
) : IllegalArgumentException(message, cause)
