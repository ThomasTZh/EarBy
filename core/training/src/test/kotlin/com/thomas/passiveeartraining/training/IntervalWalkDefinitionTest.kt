package com.thomas.passiveeartraining.training

import com.thomas.passiveeartraining.music.MidiNote
import com.thomas.passiveeartraining.music.SimpleInterval
import com.thomas.passiveeartraining.training.provider.VoiceVocabulary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class IntervalWalkDefinitionTest {

    private fun definition(
        intervals: List<String> = listOf("major_third", "minor_third", "perfect_fifth"),
        minimum: Int = 60,
        maximum: Int = 72,
        fixed: Int? = null,
        direction: WalkDirection = WalkDirection.ASCENDING,
        length: Int = 4
    ) = IntervalWalkDefinition.of(
        intervalIds = intervals,
        minimumNote = MidiNote.of(minimum),
        maximumNote = MidiNote.of(maximum),
        fixedStart = fixed?.let(MidiNote::of),
        direction = direction,
        length = length
    )

    @Test
    fun theDefaultWalkIsThreeStepsAndFourNotes() {
        val definition = IntervalWalkDefinition.default()
        assertEquals(3, definition.length)
        val walk = SeededIntervalWalkGenerator(definition, SeededRandomSource(7L)).next()
        assertEquals(3, walk.steps.size)
        assertEquals(4, walk.notes.size)
    }

    @Test
    fun aFiniteWalkHasOneMoreNoteThanSteps() {
        val walk = SeededIntervalWalkGenerator(definition(), SeededRandomSource(7L)).next()
        assertEquals(5, walk.notes.size)
        assertEquals(4, walk.steps.size)
        assertEquals(4, walk.intervalIds.size)
        walk.notes.zipWithNext().forEachIndexed { index, (from, to) ->
            assertEquals(walk.steps[index], to.value - from.value)
            assertEquals(
                SimpleInterval.of(kotlin.math.abs(walk.steps[index])).id,
                walk.intervalIds[index]
            )
        }
    }

    @Test
    fun theStartRangeIsTheOnlyRangeRestriction() {
        val walk = SeededIntervalWalkGenerator(
            definition(
                intervals = listOf("major_second"),
                minimum = 60,
                maximum = 60,
                fixed = 60,
                length = 4
            ),
            SeededRandomSource(1L)
        ).next()
        assertEquals(listOf(60, 62, 64, 66, 68), walk.notes.map { it.value })
        assertTrue(walk.notes.drop(1).all { it.value > 60 })
    }

    @Test
    fun aStartWhoseFirstStepLeavesThePlayableRangeIsInfeasible() {
        val top = definition(
            intervals = listOf("perfect_fifth"),
            minimum = 108,
            maximum = 108,
            fixed = 108,
            length = 2
        )
        assertFalse(top.isFeasible())
        assertThrows(InfeasibleIntervalWalkException::class.java) {
            SeededIntervalWalkGenerator(top, SeededRandomSource(1L)).next()
        }
    }

    @Test
    fun aFixedStartOutsideTheStartRangeIsRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            definition(minimum = 60, maximum = 72, fixed = 84)
        }
    }

    @Test
    fun mixedDirectionUsesBothSignsAndUnisonUsesZero() {
        val mixed = definition(
            intervals = listOf("minor_second", "major_second"),
            direction = WalkDirection.MIXED,
            length = 8
        )
        val steps = (1..10).flatMap { seed ->
            SeededIntervalWalkGenerator(mixed, SeededRandomSource(seed.toLong()))
                .next().steps
        }
        assertTrue("mixed walks can rise", steps.any { it > 0 })
        assertTrue("mixed walks can fall", steps.any { it < 0 })

        val unison = SeededIntervalWalkGenerator(
            definition(intervals = listOf("unison"), direction = WalkDirection.MIXED),
            SeededRandomSource(2L)
        ).next()
        assertEquals(listOf(0, 0, 0, 0), unison.steps)
    }

    @Test
    fun theSameSeedProducesTheSameWalkAndDifferentSeedsCanDiverge() {
        val first = SeededIntervalWalkGenerator(definition(), SeededRandomSource(42L)).next()
        val second = SeededIntervalWalkGenerator(definition(), SeededRandomSource(42L)).next()
        assertEquals(first, second)
    }

    @Test
    fun theAnswerSpeaksOneDirectedPhrasePerStepAndUnisonAlone() {
        val steps = listOf(4, -3, 0)
        val tokens = definition().answerTokensForSteps(steps)
        assertEquals(
            listOf(
                VoiceVocabulary.ASCENDING.id,
                VoiceVocabulary.intervalEntry(SimpleInterval.of(4)).id,
                VoiceVocabulary.DESCENDING.id,
                VoiceVocabulary.intervalEntry(SimpleInterval.of(3)).id,
                VoiceVocabulary.intervalEntry(SimpleInterval.of(SimpleInterval.UNISON)).id
            ),
            tokens
        )
        assertEquals(
            "ascending Major third descending Minor third Unison",
            definition().answerTextForSteps(steps)
        )
    }

    @Test
    fun lengthIsBoundedAndInvalidSelectionsAreRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            definition(length = IntervalWalkDefinition.MIN_LENGTH - 1)
        }
        assertThrows(IllegalArgumentException::class.java) {
            definition(length = IntervalWalkDefinition.MAX_LENGTH + 1)
        }
        assertEquals(
            "an empty interval selection means all intervals",
            IntervalWalkDefinition.canonicalIntervalIds,
            definition(intervals = emptyList()).intervalIds
        )
    }

    @Test
    fun everyStartInTheFeasibleListCanFinish() {
        val definition = definition(
            intervals = listOf("major_second", "major_third", "perfect_fifth"),
            minimum = 21,
            maximum = 108,
            length = 6
        )
        assertTrue(definition.startCandidates().isNotEmpty())
        definition.startCandidates().forEach {
            assertTrue(definition.canFinish(it, definition.length))
        }
    }
}
