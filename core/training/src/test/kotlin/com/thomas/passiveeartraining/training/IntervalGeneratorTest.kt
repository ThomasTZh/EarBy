package com.thomas.passiveeartraining.training

import com.thomas.passiveeartraining.music.IntervalPresentation
import com.thomas.passiveeartraining.music.MidiNote
import com.thomas.passiveeartraining.music.PitchClass
import com.thomas.passiveeartraining.music.SimpleInterval
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

private fun definition(
    intervals: Collection<String> = listOf("minor_third", "major_third", "perfect_fifth"),
    roots: Collection<Int> = PitchClass.ALL.map { it.value },
    minimum: Int = 60,
    maximum: Int = 72,
    presentation: IntervalPresentation = IntervalPresentation.ASCENDING
): IntervalDefinition = IntervalDefinition.of(
    intervalIds = intervals,
    rootValues = roots,
    minimumNote = MidiNote.of(minimum),
    maximumNote = MidiNote.of(maximum),
    presentation = presentation
)

/** Feasible pair counts of the default fixture, derived by hand from the C4..C5 range. */

/** Feasible pair counts of the default fixture for the bag order the all-zero shuffle produces. */
private const val MINOR_THIRD_PAIRS = 13
private const val MAJOR_THIRD_PAIRS = 13
private const val PERFECT_FIFTH_PAIRS = 13

class IntervalDefinitionTest {

    @Test
    fun selectionsAreNormalizedIntoCanonicalOrder() {
        val shuffled = definition(
            intervals = listOf("perfect_fifth", "minor_third", "perfect_fifth", "major_third"),
            roots = listOf(7, 0, 3, 0, 11)
        )
        assertEquals(
            listOf("minor_third", "major_third", "perfect_fifth"),
            shuffled.selectedIntervalIds
        )
        assertEquals(listOf(0, 3, 7, 11), shuffled.rootValues)
        assertEquals(listOf(3, 4, 7), shuffled.selectedIntervals.map { it.semitones })
        assertEquals(listOf(0, 3, 7, 11), shuffled.selectedRoots.map { it.value })
        assertEquals(
            listOf("minor_third", "major_third", "perfect_fifth"),
            definition(
                intervals = listOf("major_third", "perfect_fifth", "minor_third"),
                roots = listOf(11, 3, 0, 7)
            ).selectedIntervalIds
        )
    }

    @Test
    fun callerCollectionsAreCopiedAndExposedCollectionsAreReadOnly() {
        val intervals = mutableListOf("minor_third", "perfect_fifth")
        val roots = mutableListOf(0, 7)
        val fixed = definition(intervals = intervals, roots = roots)
        intervals.clear()
        roots.clear()
        roots += 5
        assertEquals(listOf("minor_third", "perfect_fifth"), fixed.selectedIntervalIds)
        assertEquals(listOf(0, 7), fixed.rootValues)
        assertEquals(60..72, fixed.noteRange)
        // Exposed collections are read-only, so a caller cannot mutate a definition either.
        @Suppress("UNCHECKED_CAST")
        val exposedIntervals = fixed.selectedIntervalIds as MutableCollection<String>
        assertThrows(UnsupportedOperationException::class.java) { exposedIntervals.clear() }
        assertEquals(listOf("minor_third", "perfect_fifth"), fixed.selectedIntervalIds)
        @Suppress("UNCHECKED_CAST")
        val exposedRoots = fixed.rootValues as MutableCollection<Int>
        assertThrows(UnsupportedOperationException::class.java) { exposedRoots.clear() }
        assertEquals(listOf(0, 7), fixed.rootValues)
    }

    @Test
    fun emptyOrUnknownSelectionsAreRejected() {
        assertThrows(InvalidIntervalDefinitionException::class.java) {
            definition(intervals = emptyList())
        }
        assertThrows(InvalidIntervalDefinitionException::class.java) {
            definition(roots = emptyList())
        }
        assertThrows(InvalidIntervalDefinitionException::class.java) {
            definition(intervals = listOf("compound_ninth"))
        }
        assertThrows(InvalidIntervalDefinitionException::class.java) {
            definition(roots = listOf(12))
        }
        assertThrows(InvalidIntervalDefinitionException::class.java) {
            definition(roots = listOf(-1))
        }
        assertThrows(InvalidIntervalDefinitionException::class.java) {
            definition(minimum = 70, maximum = 60)
        }
    }

    @Test
    fun defaultFixtureMatchesTheRoadmapConfiguration() {
        val default = IntervalDefinition.default()
        assertEquals(
            listOf("minor_third", "major_third", "perfect_fifth"),
            default.selectedIntervalIds
        )
        assertEquals((0..11).toList(), default.rootValues)
        assertEquals(60, default.minimumNoteValue)
        assertEquals(72, default.maximumNoteValue)
        assertEquals(IntervalPresentation.ASCENDING, default.presentation)
        assertEquals(3, SeededIntervalGenerator(default, SeededRandomSource(1L)).cycleLength)
    }

    @Test
    fun normalizedEqualityIgnoresInputOrdering() {
        assertEquals(
            definition(intervals = listOf("perfect_fifth", "minor_third"), roots = listOf(7, 0)),
            definition(intervals = listOf("minor_third", "perfect_fifth"), roots = listOf(0, 7))
        )
        assertNotEquals(
            definition(presentation = IntervalPresentation.ASCENDING),
            definition(presentation = IntervalPresentation.DESCENDING)
        )
    }

    @Test
    fun generatorIdentityRecordsVersionRngAndNormalizedDefinition() {
        val identity = GeneratorIdentity.interval(42L, definition())
        assertEquals("interval", identity.generatorId)
        assertEquals(1, identity.generatorVersion)
        assertEquals("java-random-48-v1", identity.rngId)
        assertEquals(42L, identity.seed)
        assertEquals(definition(), identity.definition)
        assertEquals(
            identity,
            GeneratorIdentity.interval(
                42L,
                definition(intervals = listOf("major_third", "minor_third", "perfect_fifth"))
            )
        )
        assertNotEquals(identity, GeneratorIdentity.interval(43L, definition()))
    }
}

class SeededRandomSourceTest {

    @Test
    fun theSameSeedProducesTheSameSequence() {
        val first = SeededRandomSource(7L)
        val second = SeededRandomSource(7L)
        val draws = List(64) { first.nextInt(11) }
        assertEquals(draws, List(64) { second.nextInt(11) })
        assertTrue(draws.all { it in 0..10 })
    }

    @Test
    fun differentSeedsProduceDifferentSequences() {
        val first = SeededRandomSource(1L)
        val second = SeededRandomSource(2L)
        val firstDraws = List(32) { first.nextInt(1000) }
        val secondDraws = List(32) { second.nextInt(1000) }
        assertNotEquals(firstDraws, secondDraws)
    }

    @Test
    fun boundMustBePositive() {
        assertThrows(InvalidRandomBoundException::class.java) { SeededRandomSource(1L).nextInt(0) }
        assertThrows(InvalidRandomBoundException::class.java) { SeededRandomSource(1L).nextInt(-3) }
        assertEquals(0, SeededRandomSource(1L).nextInt(1))
    }

    @Test
    fun theRecordedSeedIsRetained() {
        assertEquals(42L, SeededRandomSource(42L).seed)
        assertEquals(Long.MIN_VALUE, SeededRandomSource(Long.MIN_VALUE).seed)
    }
}

class IntervalGeneratorFeasibilityTest {

    @Test
    fun everyPresentationProducesLegalPairs() {
        for (presentation in IntervalPresentation.entries) {
            val generator =
                SeededIntervalGenerator(
                    definition(presentation = presentation),
                    SeededRandomSource(3L)
                )
            repeat(12) {
                val exercise = generator.next()
                val notes = presentation.notes(exercise.root, exercise.interval)!!
                assertEquals(presentation, exercise.presentation)
                assertEquals(exercise.root, notes.first())
                assertEquals(exercise.target, notes.last())
                assertEquals(
                    exercise.interval.semitones,
                    SimpleInterval.between(exercise.root, exercise.target)!!.semitones
                )
                assertTrue("root ${exercise.root.value} in range", exercise.root.value in 60..72)
                assertTrue(
                    "target ${exercise.target.value} in playable range",
                    exercise.target.value in 21..108
                )
            }
        }
    }

    @Test
    fun unisonPlaysTheSamePitchTwice() {
        val generator = SeededIntervalGenerator(
            definition(intervals = listOf("unison")),
            SeededRandomSource(5L)
        )
        val exercise = generator.next()
        assertEquals("unison", exercise.labelId)
        assertEquals(0, exercise.semitones)
        assertEquals("both melodic slots sound", 2, exercise.notes.size)
        assertEquals(exercise.root, exercise.target)
        assertEquals(listOf(exercise.root, exercise.root), exercise.notes)
        assertEquals(1, generator.cycleLength)
    }

    @Test
    fun descendingExamplesStayInsideThePlayableRange() {
        val generator = SeededIntervalGenerator(
            definition(
                presentation = IntervalPresentation.DESCENDING,
                minimum = 21,
                maximum = 41
            ),
            SeededRandomSource(9L)
        )
        repeat(20) {
            val exercise = generator.next()
            assertEquals(exercise.root.value, exercise.target.value + exercise.semitones)
            assertTrue("root ${exercise.root.value}", exercise.root.value in 21..41)
            assertTrue("target ${exercise.target.value}", exercise.target.value in 21..108)
        }
    }

    @Test
    fun infeasibleLabelsAreRejectedWithAllMissingLabelsAndTheRange() {
        // At C8 no selected interval above unison can stay inside the A0-C8 samples.
        val error = assertThrows(InfeasibleIntervalDefinitionException::class.java) {
            SeededIntervalGenerator(
                definition(minimum = 108, maximum = 108),
                SeededRandomSource(1L)
            )
        }
        assertEquals(
            listOf("minor_third", "major_third", "perfect_fifth"),
            error.infeasibleLabelIds
        )
        assertEquals(108..108, error.noteRange)
        assertTrue(error.message!!.contains("108..108"))
    }

    @Test
    fun onlyTheGenuinelyInfeasibleLabelsAreNamed() {
        // At C8 only unison stays inside the playable samples; everything above it is excluded.
        val error = assertThrows(InfeasibleIntervalDefinitionException::class.java) {
            SeededIntervalGenerator(
                definition(
                    intervals = listOf("unison", "minor_third", "octave"),
                    minimum = 108,
                    maximum = 108
                ),
                SeededRandomSource(1L)
            )
        }
        assertEquals(listOf("minor_third", "octave"), error.infeasibleLabelIds)
        val unisonOnly = SeededIntervalGenerator(
            definition(intervals = listOf("unison"), minimum = 108, maximum = 108),
            SeededRandomSource(1L)
        )
        assertEquals(
            listOf("unison"),
            List(3) { unisonOnly.next() }.map { it.labelId }.distinct()
        )
    }

    @Test
    fun impossibleRootSelectionsFailImmediately() {
        // A fifth above C8 would exceed the packaged samples.
        assertThrows(InfeasibleIntervalDefinitionException::class.java) {
            SeededIntervalGenerator(
                definition(
                    intervals = listOf("perfect_fifth"),
                    roots = listOf(0),
                    minimum = 108,
                    maximum = 108
                ),
                SeededRandomSource(1L)
            )
        }
        val feasible = SeededIntervalGenerator(
            definition(
                intervals = listOf("perfect_fifth"),
                roots = listOf(0),
                minimum = 60,
                maximum = 72
            ),
            SeededRandomSource(1L)
        )
        assertEquals(1, feasible.cycleLength)
        assertEquals(0, feasible.next().root.pitchClass.value)
    }
}

class IntervalGeneratorSequenceTest {

    @Test
    fun eachLabelOccursExactlyOncePerCycle() {
        val generator = SeededIntervalGenerator(definition(), SeededRandomSource(11L))
        val firstCycle = List(3) { generator.next() }
        val secondCycle = List(3) { generator.next() }
        // Sorted identifier order, which differs from the semitone order of the bag.
        val labels = listOf("major_third", "minor_third", "perfect_fifth")
        assertEquals(labels, firstCycle.map { it.labelId }.sorted())
        assertEquals(labels, secondCycle.map { it.labelId }.sorted())
        assertEquals(listOf(0, 0, 0), firstCycle.map { it.cycleIndex })
        assertEquals(listOf(1, 1, 1), secondCycle.map { it.cycleIndex })
        assertEquals(listOf(0, 1, 2), firstCycle.map { it.positionInCycle })
        assertEquals(listOf(0, 1, 2), secondCycle.map { it.positionInCycle })
        val bothCycles = firstCycle + secondCycle
        assertEquals((0..5).toList(), bothCycles.map { it.sequenceIndex })
        assertEquals(6, generator.sequenceIndex)
        assertEquals(1, generator.cycleIndex)
    }

    @Test
    fun aSingleLabelBagRepeatsEveryCycle() {
        val generator = SeededIntervalGenerator(
            definition(intervals = listOf("perfect_fifth")),
            ScriptedRandomSource(List(1_000) { 0 })
        )
        val exercises = List(6) { generator.next() }
        assertTrue(exercises.all { it.labelId == "perfect_fifth" })
        // A one-label bag is refilled after every exercise, so the cycle counter advances once
        // per exercise while the position inside the current bag alternates 0, 1.
        assertEquals(listOf(0, 1, 2, 3, 4, 5), exercises.map { it.cycleIndex })
        assertEquals(listOf(0), exercises.map { it.positionInCycle }.distinct())
        assertEquals(1, generator.cycleLength)
        assertEquals(6, generator.sequenceIndex)
    }

    @Test
    fun drawOrderAndBoundsPerCycleAreFixed() {
        // The documented algorithm is: one Fisher-Yates draw per index from lastIndex down to 1,
        // then exactly one pair draw per label with nextInt(feasiblePairCount). The expectation
        // below is derived from that rule and the hand-counted pair counts of the default fixture.
        val source = ScriptedRandomSource(List(1_000) { 0 })
        val generator = SeededIntervalGenerator(definition(), source)
        val firstCycle = List(3) { generator.next() }
        val shuffled = mutableListOf("minor_third", "major_third", "perfect_fifth")
        for (index in shuffled.lastIndex downTo 1) {
            val draw = 0
            val current = shuffled[index]
            val picked = shuffled[draw]
            shuffled[index] = picked
            shuffled[draw] = current
        }
        val pairCounts = mapOf(
            "minor_third" to MINOR_THIRD_PAIRS,
            "major_third" to MAJOR_THIRD_PAIRS,
            "perfect_fifth" to PERFECT_FIFTH_PAIRS
        )
        assertEquals(shuffled, firstCycle.map { it.labelId })
        assertEquals(listOf("major_third", "perfect_fifth", "minor_third"), shuffled)
        assertEquals(listOf(3, 2) + shuffled.map { pairCounts.getValue(it) }, source.calls)

        List(3) { generator.next() }
        val secondBag = mutableListOf("minor_third", "major_third", "perfect_fifth")
        for (index in secondBag.lastIndex downTo 1) {
            val draw = 0
            val current = secondBag[index]
            val picked = secondBag[draw]
            secondBag[index] = picked
            secondBag[draw] = current
        }
        if (secondBag[0] == shuffled.last()) {
            val replacement = secondBag.indexOfFirst { it != secondBag[0] }
            secondBag[0] = secondBag[replacement].also { secondBag[replacement] = secondBag[0] }
        }
        assertEquals(
            listOf(3, 2) + shuffled.map { pairCounts.getValue(it) } +
                listOf(3, 2) + secondBag.map { pairCounts.getValue(it) },
            source.calls
        )
    }

    @Test
    fun pairSelectionUsesTheDocumentedOrderAndBounds() {
        val source = ScriptedRandomSource(listOf(0, 5))
        val generator = SeededIntervalGenerator(
            definition(intervals = listOf("perfect_fifth"), minimum = 60, maximum = 72),
            source
        )
        // A fifth above C4..C5 can extend above C5 while remaining inside A0-C8, so all 13
        // roots produce a pair. A one-label bag has no shuffle draws, so the first draw is the
        // pair index itself.
        val first = generator.next()
        assertEquals(listOf(13), source.calls)
        assertEquals(60, first.root.value)
        assertEquals(67, first.target.value)
        val second = generator.next()
        assertEquals(65, second.root.value)
        assertEquals(72, second.target.value)
        assertEquals(listOf(13, 13), source.calls)
    }

    @Test
    fun theBoundaryCorrectionPreventsALabelFromRepeatingAcrossCycles() {
        // Every draw is 0, so every cycle would start with the same label. Cycle 0 plays
        // [major_third, perfect_fifth, minor_third]; without the correction cycle 1 would start
        // with the same bag again. The anti-repeat rule is asserted for every boundary of a long
        // run, independently of the concrete fixture below.
        val source = ScriptedRandomSource(List(1_000) { 0 })
        val generator = SeededIntervalGenerator(definition(), source)
        val exercises = List(30) { generator.next() }
        val boundaries = (1 until exercises.size).filter { exercises[it].positionInCycle == 0 }
        assertEquals(listOf(3, 6, 9, 12, 15, 18, 21, 24, 27), boundaries)
        assertEquals(
            listOf("major_third", "perfect_fifth", "minor_third"),
            exercises.take(3).map { it.labelId }
        )
        // The fixture repeats the same shuffled bag, so an uncorrected first label would equal
        // the previous label at every boundary; the rule below checks all of them.
        for (boundary in boundaries) {
            assertEquals(
                "cycle ${exercises[boundary].cycleIndex} repeats the previous label",
                false,
                exercises[boundary - 1].labelId == exercises[boundary].labelId
            )
        }
        for (boundary in boundaries) {
            val drawn = exercises[boundary].labelId
            val previous = exercises[boundary - 1].labelId
            assertEquals(
                "cycle boundary at sequence ${exercises[boundary].sequenceIndex}",
                false,
                previous == drawn
            )
        }
        // Each cycle still contains every selected label exactly once.
        exercises.chunked(3).forEach { cycle ->
            assertEquals(
                listOf("major_third", "minor_third", "perfect_fifth"),
                cycle.map { it.labelId }.sorted()
            )
        }
    }

    @Test
    fun theBoundaryCorrectionSwapsTheFirstLabelWithTheLast() {
        // Cycle 1 shuffles with draws 1 and 0, producing [minor_third, perfect_fifth, ...]:
        // a different first label, so the correction must not change anything here. Every draw
        // is still exactly one shuffle draw per index plus one pair draw per label.
        val source = ScriptedRandomSource(listOf(0, 0, 0, 0, 0, 1, 0, 0, 0, 0, 0, 0))
        val generator = SeededIntervalGenerator(definition(), source)
        val exercises = List(6) { generator.next() }
        assertEquals(
            listOf("major_third", "perfect_fifth", "minor_third"),
            exercises.take(3).map { it.labelId }
        )
        // Without a correction cycle 1 would repeat the same bag and start with major_third.
        assertEquals("perfect_fifth", exercises[3].labelId)
        assertEquals("minor_third", exercises[4].labelId)
        assertEquals("major_third", exercises[5].labelId)
        assertEquals(listOf(3, 2, 13, 13, 13, 3, 2, 13, 13, 13), source.calls)
        assertEquals(6, generator.sequenceIndex)
    }

    @Test
    fun theSameSeedAndDefinitionReproduceSeveralBagsInAFreshInstance() {
        val definition = definition()
        val first = SeededIntervalGenerator(definition, SeededRandomSource(2024L))
        val second = SeededIntervalGenerator(definition, SeededRandomSource(2024L))
        val firstRun = List(15) { first.next() }
        val secondRun = List(15) { second.next() }
        assertEquals(firstRun, secondRun)
        assertEquals(first.identity, second.identity)
        assertEquals(firstRun.map { it.answerId }, secondRun.map { it.answerId })
    }

    @Test
    fun differentSeedsMayProduceDifferentSequences() {
        val firstGenerator = SeededIntervalGenerator(definition(), SeededRandomSource(1L))
        val secondGenerator = SeededIntervalGenerator(definition(), SeededRandomSource(2L))
        val first = List(12) { firstGenerator.next() }
        val second = List(12) { secondGenerator.next() }
        assertNotEquals(first.map { it.toString() }, second.map { it.toString() })
    }

    @Test
    fun inputCollectionOrderingDoesNotChangeOutput() {
        val first = SeededIntervalGenerator(
            definition(
                intervals = listOf("perfect_fifth", "minor_third", "major_third"),
                roots = listOf(7, 1, 0, 5, 11, 4, 10, 3, 9, 2, 8, 6)
            ),
            SeededRandomSource(99L)
        )
        val second = SeededIntervalGenerator(
            definition(
                intervals = listOf("major_third", "perfect_fifth", "minor_third", "major_third"),
                roots = (11 downTo 0).toList()
            ),
            SeededRandomSource(99L)
        )
        assertEquals(List(18) { first.next() }, List(18) { second.next() })
    }

    @Test
    fun identityIsSharedByEveryProducedExercise() {
        val generator = SeededIntervalGenerator(definition(), SeededRandomSource(8L))
        val exercises = List(5) { generator.next() }
        assertTrue(exercises.all { it.identity === generator.identity })
        assertEquals(
            exercises.first { it.labelId == "perfect_fifth" }.answerId,
            "interval.perfect_fifth"
        )
        // A seeded source records its own seed in the identity.
        assertEquals(GeneratorIdentity.interval(8L, definition()), exercises.first().identity)
    }

    @Test
    fun exercisePitchesMatchTheRecordedIntervalAndRange() {
        val generator = SeededIntervalGenerator(definition(), SeededRandomSource(77L))
        repeat(24) {
            val exercise = generator.next()
            val offset = when (exercise.presentation) {
                IntervalPresentation.ASCENDING, IntervalPresentation.HARMONIC -> exercise.semitones
                IntervalPresentation.DESCENDING -> -exercise.semitones
            }
            assertEquals(exercise.root.value.toLong() + offset, exercise.target.value.toLong())
            assertEquals(exercise.interval, SimpleInterval.between(exercise.root, exercise.target))
            assertEquals("interval.${exercise.labelId}", exercise.answerId)
        }
    }

    @Test
    fun seedsThatDifferOnlyOutsideTheEffectiveStateMayShareASequence() {
        // java.util.Random derives its state from the seed modulo 2^48, so these two seeds draw
        // the same values and produce the same exercise sequence. The recorded identities still
        // differ, because the identity records the seed the caller supplied.
        val lowSeed = 5L
        val equivalentSeed = lowSeed + (1L shl 48)
        val first = SeededIntervalGenerator(definition(), SeededRandomSource(lowSeed))
        val second = SeededIntervalGenerator(definition(), SeededRandomSource(equivalentSeed))
        val firstRun = List(6) { first.next() }
        val secondRun = List(6) { second.next() }
        assertEquals(
            firstRun.map {
                "${it.cycleIndex}/${it.positionInCycle}:${it.labelId}:${it.root.value}->${it.target.value}"
            },
            secondRun.map {
                "${it.cycleIndex}/${it.positionInCycle}:${it.labelId}:${it.root.value}->${it.target.value}"
            }
        )
        assertNotEquals(firstRun.first().identity, secondRun.first().identity)
    }
}

class IntervalGeneratorInvariantTest {

    @Test
    fun tenThousandItemRunKeepsEveryInvariant() {
        val definition = definition()
        val selected = definition.selectedIntervalIds.toSet()
        val rootClasses = definition.rootValues.toSet()
        val generator = SeededIntervalGenerator(definition, SeededRandomSource(123_456_789L))
        val bagContents = mutableListOf<String>()
        var previousLabel: String? = null

        repeat(10_000) { index ->
            val exercise = generator.next()
            assertEquals(index, exercise.sequenceIndex)
            assertTrue("label ${exercise.labelId} is selected", exercise.labelId in selected)
            assertEquals(selected.size, generator.cycleLength)
            assertEquals(
                exercise.interval.semitones,
                SimpleInterval.between(exercise.root, exercise.target)!!.semitones
            )
            assertTrue(
                "root ${exercise.root.value} in range",
                exercise.root.value in definition.noteRange
            )
            assertTrue(
                "target ${exercise.target.value} in playable range",
                exercise.target.value in 21..108
            )
            assertTrue(
                "root class ${exercise.root.pitchClass.value} is selected",
                exercise.root.pitchClass.value in rootClasses
            )
            assertEquals(
                "target follows root for ${exercise.labelId}",
                exercise.root.value.toInt() + exercise.semitones,
                exercise.target.value.toInt()
            )

            if (exercise.positionInCycle == 0 && previousLabel != null) {
                assertNotEquals("boundary at sequence $index", previousLabel, exercise.labelId)
            }
            previousLabel = exercise.labelId
            bagContents += exercise.labelId
            if (bagContents.size == generator.cycleLength) {
                assertEquals(
                    "cycle ${exercise.cycleIndex} must cover every label once",
                    selected.sorted(),
                    bagContents.sorted()
                )
                bagContents.clear()
            }
        }
        // 10,000 is not a whole number of three-label cycles, so the final bag is partially used;
        // its remaining labels must still be distinct members of the selection.
        assertTrue("leftover bag $bagContents", bagContents.size < generator.cycleLength)
        assertEquals(bagContents.size, bagContents.distinct().size)
        assertTrue(bagContents.all { it in selected })
        assertEquals(10_000, generator.sequenceIndex)
    }

    @Test
    fun impossibleConfigurationsTerminateWithABoundedFailure() {
        // There is no retry loop: an infeasible definition fails during construction.
        assertThrows(InfeasibleIntervalDefinitionException::class.java) {
            SeededIntervalGenerator(
                definition(
                    intervals = listOf("major_seventh"),
                    roots = listOf(0),
                    minimum = 0,
                    maximum = 10
                ),
                SeededRandomSource(1L)
            )
        }
        assertNull(SimpleInterval.ofIdOrNull("compound_ninth"))
    }
}
