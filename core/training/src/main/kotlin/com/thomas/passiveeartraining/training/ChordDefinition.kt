package com.thomas.passiveeartraining.training

import com.thomas.passiveeartraining.music.ChordInterpretation
import com.thomas.passiveeartraining.music.ChordPresentation
import com.thomas.passiveeartraining.music.ChordQuality
import com.thomas.passiveeartraining.music.MidiNote
import com.thomas.passiveeartraining.music.PitchClass

/** Which answer format a chord setup asks the listener to produce. */
enum class ChordAnswerMode {
    /** Name the quality only. */
    QUALITY,

    /** Name the quality together with the inversion. */
    QUALITY_AND_INVERSION
}

/**
 * Immutable, normalized chord configuration (M5).
 *
 * Selections are sets: duplicates are removed, qualities are ordered by the frozen catalog,
 * inversions and root pitch classes numerically. The categories are the applicable
 * quality/inversion pairs in catalog order; a selection whose pairs cannot all be applied is
 * rejected here rather than silently repaired.
 */
class ChordDefinition private constructor(
    val selectedQualities: List<ChordQuality>,
    val selectedInversions: List<Int>,
    private val selectedRootValues: List<Int>,
    val fixedRoot: MidiNote?,
    val minimumNote: MidiNote,
    val maximumNote: MidiNote,
    val presentation: ChordPresentation,
    val answerMode: ChordAnswerMode
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

    /** Applicable categories in catalog order, then inversion. */
    val categories: List<ChordInterpretation> = java.util.Collections.unmodifiableList(
        selectedQualities.flatMap { quality ->
            selectedInversions
                .filter { it <= quality.maximumInversion }
                .map { ChordInterpretation(quality, it) }
        }
    )

    override fun equals(other: Any?): Boolean = other is ChordDefinition &&
        selectedQualities == other.selectedQualities &&
        selectedInversions == other.selectedInversions &&
        rootValues == other.rootValues &&
        fixedRoot == other.fixedRoot &&
        minimumNote == other.minimumNote &&
        maximumNote == other.maximumNote &&
        presentation == other.presentation &&
        answerMode == other.answerMode

    override fun hashCode(): Int {
        var result = selectedQualities.hashCode()
        result = 31 * result + selectedInversions.hashCode()
        result = 31 * result + rootValues.hashCode()
        result = 31 * result + (fixedRoot?.hashCode() ?: 0)
        result = 31 * result + minimumNote.hashCode()
        result = 31 * result + maximumNote.hashCode()
        result = 31 * result + presentation.hashCode()
        result = 31 * result + answerMode.hashCode()
        return result
    }

    override fun toString(): String =
        "ChordDefinition(qualities=${selectedQualities.map { it.id }}, " +
            "inversions=$selectedInversions, roots=$rootValues, fixedRoot=$fixedRoot, " +
            "range=$minimumNoteValue..$maximumNoteValue, presentation=$presentation, " +
            "answer=$answerMode)"

    companion object {
        /** Default fixture: major and minor, root position, all roots, C3..C6, harmonic. */
        fun default(): ChordDefinition = of(
            qualityIds = listOf("major", "minor"),
            inversions = listOf(0),
            rootValues = PitchClass.ALL.map { it.value },
            fixedRoot = null,
            minimumNote = MidiNote.of(48),
            maximumNote = MidiNote.of(84),
            presentation = ChordPresentation.HARMONIC,
            answerMode = ChordAnswerMode.QUALITY
        )

        /**
         * Normalizes and validates a definition.
         *
         * @throws InvalidChordDefinitionException when a selection is empty, an identifier or
         *   inversion is unknown, the range is inverted, a fixed root contradicts the selection,
         *   or an applicable quality/inversion pair is missing.
         */
        fun of(
            qualityIds: Collection<String>,
            inversions: Collection<Int>,
            rootValues: Collection<Int>,
            fixedRoot: MidiNote?,
            minimumNote: MidiNote,
            maximumNote: MidiNote,
            presentation: ChordPresentation,
            answerMode: ChordAnswerMode
        ): ChordDefinition {
            val qualities = qualityIds.toSet().map { id ->
                ChordQuality.ofIdOrNull(id)
                    ?: throw InvalidChordDefinitionException(
                        "Unknown chord quality id '$id'",
                        field = "qualities"
                    )
            }.sortedBy { it.ordinal }
            if (qualities.isEmpty()) {
                throw InvalidChordDefinitionException(
                    "At least one chord quality must be selected",
                    field = "qualities"
                )
            }
            val inversionList = inversions.toSet().sorted()
            inversionList.forEach { inversion ->
                if (inversion !in 0..3) {
                    throw InvalidChordDefinitionException(
                        "A chord inversion must be 0..3 but was $inversion",
                        field = "inversions"
                    )
                }
            }
            if (inversionList.isEmpty()) {
                throw InvalidChordDefinitionException(
                    "At least one inversion must be selected",
                    field = "inversions"
                )
            }
            qualities.forEach { quality ->
                if (inversionList.none { it <= quality.maximumInversion }) {
                    throw InvalidChordDefinitionException(
                        "Quality '${quality.id}' has no applicable selected inversion",
                        field = "qualities"
                    )
                }
            }
            inversionList.forEach { inversion ->
                if (qualities.none { inversion <= it.maximumInversion }) {
                    throw InvalidChordDefinitionException(
                        "Inversion $inversion does not apply to any selected quality",
                        field = "inversions"
                    )
                }
            }
            val roots = rootValues.toSet().sorted()
            roots.forEach { value ->
                if (value !in 0 until PitchClass.COUNT) {
                    throw InvalidChordDefinitionException(
                        "Root pitch class must be in 0..11 but was $value",
                        field = "roots"
                    )
                }
            }
            if (roots.isEmpty()) {
                throw InvalidChordDefinitionException(
                    "At least one root pitch class must be selected",
                    field = "roots"
                )
            }
            if (minimumNote.value > maximumNote.value) {
                throw InvalidChordDefinitionException(
                    "Minimum note ${minimumNote.value} must not exceed maximum note " +
                        "${maximumNote.value}",
                    field = "range"
                )
            }
            fixedRoot?.let { root ->
                if (root.value !in minimumNote.value..maximumNote.value) {
                    throw InvalidChordDefinitionException(
                        "A fixed root $root must be inside the configured range " +
                            "${minimumNote.value}..${maximumNote.value}",
                        field = "fixedRoot"
                    )
                }
                if (root.pitchClass.value !in roots) {
                    throw InvalidChordDefinitionException(
                        "A fixed root $root must match a selected root class",
                        field = "fixedRoot"
                    )
                }
            }
            return ChordDefinition(
                selectedQualities = qualities,
                selectedInversions = inversionList,
                selectedRootValues = roots,
                fixedRoot = fixedRoot,
                minimumNote = minimumNote,
                maximumNote = maximumNote,
                presentation = presentation,
                answerMode = answerMode
            )
        }
    }
}

/** Raised when a chord configuration is empty, unknown or internally contradictory. */
class InvalidChordDefinitionException(
    message: String,
    cause: Throwable? = null,
    /** The setup field the failure belongs to, for form feedback. */
    val field: String? = null
) : IllegalArgumentException(message, cause)
