package com.thomas.passiveeartraining.training

import com.thomas.passiveeartraining.music.MidiNote
import com.thomas.passiveeartraining.music.ScaleDirection
import com.thomas.passiveeartraining.music.ScaleKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/** M6.1: scale definition normalization and validation, mirroring the chord definition rules. */
class ScaleDefinitionTest {

    private fun definition(
        scaleIds: List<String> = listOf("major", "natural_minor"),
        direction: ScaleDirection = ScaleDirection.ASCENDING,
        roots: List<Int> = (0..11).toList(),
        fixedRoot: MidiNote? = null,
        minimumNote: MidiNote = MidiNote.of(48),
        maximumNote: MidiNote = MidiNote.of(84)
    ): ScaleDefinition = ScaleDefinition.of(
        scaleIds = scaleIds,
        direction = direction,
        rootValues = roots,
        fixedRoot = fixedRoot,
        minimumNote = minimumNote,
        maximumNote = maximumNote
    )

    private fun invalidFieldOf(block: () -> Unit): String? {
        try {
            block()
        } catch (exception: InvalidScaleDefinitionException) {
            return exception.field
        }
        fail("the definition must be rejected")
        return null
    }

    @Test
    fun theDefaultSelectsTheTwoCanonicalScalesAscendingAcrossAllRoots() {
        val default = ScaleDefinition.default()
        assertEquals(listOf(ScaleKind.MAJOR, ScaleKind.NATURAL_MINOR), default.selectedScales)
        assertEquals(ScaleDirection.ASCENDING, default.direction)
        assertEquals((0..11).toList(), default.rootValues)
        assertEquals((0..11).toList(), default.selectedRoots.map { it.value })
        assertEquals(48, default.minimumNoteValue)
        assertEquals(84, default.maximumNoteValue)
        assertEquals(48..84, default.noteRange)
        assertNull(default.fixedRoot)
    }

    @Test
    fun aliasesMergeOnceIntoCanonicalKindsInCatalogOrder() {
        val definition = definition(
            scaleIds = listOf("whole_tone", "aeolian", "ionian", "major", "natural_minor")
        )
        assertEquals(
            listOf(ScaleKind.MAJOR, ScaleKind.NATURAL_MINOR, ScaleKind.WHOLE_TONE),
            definition.selectedScales
        )
    }

    @Test
    fun anUnknownScaleIdIsRejectedOnTheScalesField() {
        assertEquals("scales", invalidFieldOf { definition(scaleIds = listOf("major", "doriann")) })
    }

    @Test
    fun emptySelectionsAreRejected() {
        assertEquals("scales", invalidFieldOf { definition(scaleIds = emptyList()) })
        assertEquals("roots", invalidFieldOf { definition(roots = emptyList()) })
    }

    @Test
    fun rootValuesAreSortedDedupedAndRangeChecked() {
        assertEquals(listOf(0, 7, 11), definition(roots = listOf(7, 0, 7, 11)).rootValues)
        assertEquals("roots", invalidFieldOf { definition(roots = listOf(0, 12)) })
        assertEquals("roots", invalidFieldOf { definition(roots = listOf(-1, 3)) })
    }

    @Test
    fun anInvertedRangeIsRejectedOnTheRangeField() {
        assertEquals(
            "range",
            invalidFieldOf {
                definition(minimumNote = MidiNote.of(84), maximumNote = MidiNote.of(48))
            }
        )
    }

    @Test
    fun aFixedRootMustBeInsideTheRangeAndSelection() {
        val valid = definition(roots = listOf(0, 7), fixedRoot = MidiNote.of(60))
        assertEquals(MidiNote.of(60), valid.fixedRoot)

        assertEquals(
            "fixedRoot",
            invalidFieldOf {
                definition(
                    roots = listOf(0, 7),
                    fixedRoot = MidiNote.of(84),
                    maximumNote = MidiNote.of(83)
                )
            }
        )
        assertEquals(
            "fixedRoot",
            invalidFieldOf { definition(roots = listOf(0), fixedRoot = MidiNote.of(61)) }
        )
    }

    @Test
    fun normalizedDefinitionsCompareByTheirMusicalContent() {
        val left = definition(scaleIds = listOf("ionian", "aeolian"), roots = listOf(7, 0))
        val right = definition(scaleIds = listOf("major", "natural_minor"), roots = listOf(0, 7))
        assertEquals(left, right)
        assertEquals(left.hashCode(), right.hashCode())
        assertTrue(left != definition(roots = listOf(0)))
    }
}
