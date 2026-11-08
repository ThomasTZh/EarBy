package com.thomas.passiveeartraining.training

import com.thomas.passiveeartraining.music.IntervalPresentation
import com.thomas.passiveeartraining.music.MidiNote
import com.thomas.passiveeartraining.music.PitchClass
import com.thomas.passiveeartraining.training.recipe.SequencePolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/** One generated exercise as a comparable row. */
private data class Row(
    val interval: String,
    val presentation: IntervalPresentation,
    val root: Int,
    val target: Int,
    val cycle: Int,
    val position: Int
)

private fun IntervalExercise.row(): Row = Row(
    interval = interval.id,
    presentation = presentation,
    root = root.value,
    target = target.value,
    cycle = cycleIndex,
    position = positionInCycle
)

private fun defaultPolicy(
    presentations: List<IntervalPresentation>,
    fixedRoot: MidiNote? = null,
    mixed: Boolean = true
): SequencePolicy = SequencePolicy(
    definition = IntervalDefinition.default(),
    presentations = presentations,
    fixedRoot = fixedRoot,
    mixedPresentation = mixed
)

/**
 * Version 2's draw order is part of the reproducibility contract: the fixture below was recorded
 * from the implementation and must only change with a new generator version.
 */
class SeededIntervalGeneratorV2Test {

    @Test
    fun theGoldenMixedSequenceReproducesExactly() {
        val policy = defaultPolicy(
            presentations = listOf(
                IntervalPresentation.ASCENDING,
                IntervalPresentation.DESCENDING,
                IntervalPresentation.HARMONIC
            )
        )
        val generator = SeededIntervalGeneratorV2(policy, SeededRandomSource(42L))

        val actual = List(6) { generator.next().row() }

        assertEquals(
            listOf(
                Row("major_third", IntervalPresentation.ASCENDING, 72, 76, 0, 0),
                Row("minor_third", IntervalPresentation.ASCENDING, 64, 67, 0, 1),
                Row("perfect_fifth", IntervalPresentation.HARMONIC, 63, 70, 0, 2),
                Row("minor_third", IntervalPresentation.HARMONIC, 60, 63, 1, 0),
                Row("perfect_fifth", IntervalPresentation.ASCENDING, 66, 73, 1, 1),
                Row("major_third", IntervalPresentation.HARMONIC, 64, 68, 1, 2)
            ),
            actual
        )
    }

    @Test
    fun theGoldenFixedRootSequenceReproducesExactly() {
        val policy = defaultPolicy(
            presentations = listOf(IntervalPresentation.ASCENDING),
            fixedRoot = MidiNote.of(60)
        )
        val generator = SeededIntervalGeneratorV2(policy, SeededRandomSource(42L))

        val actual = List(4) { generator.next().row() }

        assertEquals(
            listOf(
                Row("major_third", IntervalPresentation.ASCENDING, 60, 64, 0, 0),
                Row("minor_third", IntervalPresentation.ASCENDING, 60, 63, 0, 1),
                Row("perfect_fifth", IntervalPresentation.ASCENDING, 60, 67, 0, 2),
                Row("minor_third", IntervalPresentation.ASCENDING, 60, 63, 1, 0)
            ),
            actual
        )
    }

    @Test
    fun everyExerciseCarriesTheDrawnPresentationAndThePolicyIdentity() {
        val policy = defaultPolicy(
            presentations = listOf(
                IntervalPresentation.ASCENDING,
                IntervalPresentation.DESCENDING,
                IntervalPresentation.HARMONIC
            )
        )
        val generator = SeededIntervalGeneratorV2(policy, SeededRandomSource(7L))

        repeat(9) {
            val exercise = generator.next()
            assertTrue(
                "the presentation must be one the policy enabled",
                exercise.presentation in policy.presentations
            )
            assertEquals(generator.identity, exercise.identity)
        }
    }

    @Test
    fun aNewSeedProducesADifferentSequence() {
        val policy = defaultPolicy(
            presentations = listOf(
                IntervalPresentation.ASCENDING,
                IntervalPresentation.HARMONIC
            )
        )
        val firstGenerator = SeededIntervalGeneratorV2(policy, SeededRandomSource(1L))
        val first = List(5) { firstGenerator.next() }
        val secondGenerator = SeededIntervalGeneratorV2(policy, SeededRandomSource(2L))
        val second = List(5) { secondGenerator.next() }
        assertNotEquals(first, second)

        val repeatedGenerator = SeededIntervalGeneratorV2(policy, SeededRandomSource(1L))
        assertEquals(
            "the same seed reproduces the same sequence",
            first,
            List(5) { repeatedGenerator.next() }
        )
    }

    @Test
    fun aSingletonPresentationStillConsumesItsDraw() {
        // Version 2 draws the presentation with nextInt even for one entry, so the pair draws of a
        // "mixed" single-presentation policy cannot coincide with version 1's draw order.
        val policy = defaultPolicy(
            presentations = listOf(IntervalPresentation.ASCENDING)
        )
        val versionTwo = SeededIntervalGeneratorV2(policy, SeededRandomSource(42L))
        val versionOne = SeededIntervalGenerator(
            IntervalDefinition.default(),
            SeededRandomSource(42L)
        )
        var differences = 0
        repeat(6) {
            if (versionTwo.next() != versionOne.next()) differences += 1
        }
        assertTrue("the singleton draw must shift the sequence", differences > 0)
    }

    @Test
    fun aCombinationWithoutAFeasibleExampleIsRejectedNamingIt() {
        // A descending octave from A0 would need a note below the packaged samples.
        val definition = IntervalDefinition.of(
            intervalIds = listOf("octave"),
            rootValues = listOf(9),
            minimumNote = MidiNote.of(21),
            maximumNote = MidiNote.of(21),
            presentation = IntervalPresentation.ASCENDING
        )
        val policy = SequencePolicy(
            definition = definition,
            presentations = listOf(IntervalPresentation.DESCENDING),
            mixedPresentation = true
        )

        val failure = assertThrows(InfeasibleIntervalDefinitionException::class.java) {
            SeededIntervalGeneratorV2(policy, SeededRandomSource(1L))
        }
        assertTrue(
            "the failure names the missing combination: ${failure.message}",
            failure.infeasibleLabelIds.any { it.contains("octave") && it.contains("DESCENDING") }
        )
    }

    @Test
    fun aFixedRootThatCannotProduceAnEnabledPresentationIsRejected() {
        val definition = IntervalDefinition.of(
            intervalIds = listOf("minor_third", "major_third", "perfect_fifth"),
            rootValues = PitchClass.ALL.map { it.value },
            minimumNote = MidiNote.of(21),
            maximumNote = MidiNote.of(108),
            presentation = IntervalPresentation.ASCENDING
        )
        val policy = SequencePolicy(
            definition = definition,
            presentations = listOf(IntervalPresentation.ASCENDING, IntervalPresentation.DESCENDING),
            fixedRoot = MidiNote.of(21),
            mixedPresentation = true
        )

        val failure = assertThrows(InfeasibleIntervalDefinitionException::class.java) {
            SeededIntervalGeneratorV2(policy, SeededRandomSource(1L))
        }
        assertTrue(
            "the descending combinations are missing: ${failure.infeasibleLabelIds}",
            failure.infeasibleLabelIds.all { it.contains("DESCENDING") }
        )
    }

    @Test
    fun aFixedRootInAOneSidedRangeKeepsThePolicyFeasible() {
        // C4 as the fixed root with ascending intervals is exactly the configuration the advanced
        // panel will offer: the root is inside the range and matches a selected class.
        val policy = defaultPolicy(
            presentations = listOf(IntervalPresentation.ASCENDING),
            fixedRoot = MidiNote.of(60)
        )
        val generator = SeededIntervalGeneratorV2(policy, SeededRandomSource(3L))
        repeat(6) {
            val exercise = generator.next()
            assertEquals(60, exercise.root.value)
            assertTrue(exercise.target.value <= 72)
        }
    }

    @Test
    fun theIdentityRecordsEnabledPresentationsAndTheFixedRoot() {
        val policy = defaultPolicy(
            presentations = listOf(IntervalPresentation.ASCENDING, IntervalPresentation.HARMONIC),
            fixedRoot = MidiNote.of(60)
        )
        val identity = SeededIntervalGeneratorV2(policy, SeededRandomSource(5L)).identity

        assertEquals(2, identity.generatorVersion)
        assertEquals(policy.presentations, identity.enabledPresentations)
        assertEquals(MidiNote.of(60), identity.fixedRoot)
        assertEquals(5L, identity.seed)
    }

    @Test
    fun aFixedRootAloneIsVersionTwo() {
        val policy = SequencePolicy(
            definition = IntervalDefinition.default(),
            fixedRoot = MidiNote.of(60)
        )
        assertEquals(
            "a fixed root is a version 2 draw even with one presentation",
            2,
            policy.generatorVersion
        )
    }
}
