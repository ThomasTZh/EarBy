package com.thomas.passiveeartraining.training

import com.thomas.passiveeartraining.music.MidiNote
import com.thomas.passiveeartraining.music.Tonality
import com.thomas.passiveeartraining.training.context.ContextKind
import kotlin.math.abs
import kotlin.time.Duration.Companion.milliseconds
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * M10.1/M10-T03: the finite melody graph and its reachability table against exhaustive tiny-graph
 * enumeration, and the exact path DP against the same enumeration.
 *
 * The oracle enumerates every complete phrase recursively from the documented edge rule and checks
 * endpoints directly; it never consults the graph's reachability table or the DP.
 */
class MelodyGraphTest {

    private fun definition(
        keys: List<Int> = listOf(0),
        tonalities: List<Tonality> = listOf(Tonality.MAJOR),
        minimum: Int = 60,
        maximum: Int = 64,
        length: Int = 3,
        start: Int? = 1,
        end: Int? = 1,
        steps: List<Int> = (-2..2).toList(),
        leap: Int = 2
    ): MelodicDictationDefinition = MelodicDictationDefinition.of(
        keyValues = keys,
        tonalities = tonalities,
        context = ContextKind.TONIC_TRIAD,
        contextGap = 500.milliseconds,
        minimumNote = MidiNote.of(minimum),
        maximumNote = MidiNote.of(maximum),
        phraseLength = length,
        startDegree = start,
        endDegree = end,
        allowedSteps = steps,
        maximumLeap = leap
    )

    /** Every complete phrase, enumerated independently of the graph's reachability table. */
    private fun completePhrases(
        definition: MelodicDictationDefinition,
        keyValue: Int,
        tonality: Tonality
    ): List<List<MelodyNode>> {
        val nodes = definition.scaleNodes(keyValue, tonality)
        val transitions = definition.effectiveSteps.toSet()
        val phrases = mutableListOf<List<MelodyNode>>()
        fun extend(prefix: List<MelodyNode>) {
            if (prefix.size == definition.phraseLength) {
                if (definition.endDegree == null ||
                    prefix.last().degree == definition.endDegree
                ) {
                    phrases += prefix
                }
                return
            }
            for (next in nodes) {
                if (next.value - prefix.last().value in transitions) {
                    extend(prefix + next)
                }
            }
        }
        for (startNode in nodes) {
            if (definition.startDegree == null || startNode.degree == definition.startDegree) {
                extend(listOf(startNode))
            }
        }
        return phrases
    }

    private fun canFinishOracle(
        definition: MelodicDictationDefinition,
        nodes: List<MelodyNode>,
        from: MelodyNode,
        remaining: Int
    ): Boolean {
        if (remaining == 0) {
            return definition.endDegree == null || from.degree == definition.endDegree
        }
        val transitions = definition.effectiveSteps.toSet()
        return nodes.any { next ->
            next.value - from.value in transitions &&
                canFinishOracle(definition, nodes, next, remaining - 1)
        }
    }

    private fun nodeAt(graph: MelodyGraph, value: Int): MelodyNode =
        graph.nodes.single { it.value == value }

    @Test
    fun everyCanFinishEntryMatchesExhaustiveEnumeration() {
        val definitions = listOf(
            definition(length = 3, start = 1, end = 1),
            definition(length = 2, start = null, end = null),
            definition(length = 3, start = 1, end = null),
            definition(length = 4, start = 2, end = 3),
            definition(
                keys = listOf(9),
                tonalities = listOf(Tonality.NATURAL_MINOR),
                minimum = 57,
                maximum = 69,
                length = 3,
                start = 1,
                end = 1,
                steps = listOf(-3, -2, 0, 2, 3),
                leap = 3
            )
        )
        definitions.forEach { definition ->
            for (keyValue in definition.effectiveKeyValues) {
                for (tonality in definition.tonalities) {
                    val graph = definition.graphFor(keyValue, tonality)
                    val nodes = graph.nodes
                    for (remaining in 0 until definition.phraseLength) {
                        val row = graph.canFinishRow(remaining)
                        assertEquals(
                            "$graph row $remaining",
                            nodes.map { canFinishOracle(definition, nodes, it, remaining) },
                            row
                        )
                        nodes.forEach { node ->
                            assertEquals(
                                "$graph canFinish($remaining, $node)",
                                canFinishOracle(definition, nodes, node, remaining),
                                graph.canFinish(remaining, node)
                            )
                        }
                    }
                }
            }
        }
    }

    @Test
    fun eligibleStartsAreExactlyTheStartDegreeNodesThatFinish() {
        val definition = definition(length = 3, start = 1, end = 1)
        val graph = definition.graphFor(0, Tonality.MAJOR)
        val expected = graph.nodes.filter { node ->
            node.degree == 1 && canFinishOracle(definition, graph.nodes, node, 2)
        }
        assertEquals(expected, graph.eligibleStarts)
        assertTrue(graph.eligibleStarts.isNotEmpty())
        assertTrue(graph.eligibleStarts.all { it.degree == 1 })
        assertTrue(graph.isFeasible)
        graph.eligibleStarts.forEach { start ->
            assertTrue(graph.canFinish(definition.phraseLength - 1, start))
        }

        val anyStart = definition(length = 3, start = null, end = 1)
        val anyGraph = anyStart.graphFor(0, Tonality.MAJOR)
        assertEquals(
            "a null start degree admits every finishing node",
            anyGraph.nodes.filter { canFinishOracle(anyStart, anyGraph.nodes, it, 2) },
            anyGraph.eligibleStarts
        )
    }

    @Test
    fun candidatesAreSortedByMidiAndStillFinish() {
        val definition = definition(length = 3, start = 1, end = 1)
        val graph = definition.graphFor(0, Tonality.MAJOR)
        val middle = nodeAt(graph, 62)
        val candidates = graph.candidatesForNext(1, middle)
        assertEquals(
            "the middle node can still reach the tonic in two transitions",
            listOf(60, 62),
            candidates.map { it.value }
        )
        assertEquals(candidates.map { it.value }.sorted(), candidates.map { it.value })
        candidates.forEach { candidate ->
            assertTrue(
                "a candidate must finish with the remaining transitions",
                graph.canFinish(definition.phraseLength - 2, candidate)
            )
        }
        assertEquals(
            "the last position keeps only degree-1 endings",
            listOf(60),
            graph.candidatesForNext(2, middle).map { it.value }
        )
        candidates.forEach { candidate ->
            assertTrue(
                "every candidate is an outgoing edge",
                graph.outgoingNodes(middle).any { it.value == candidate.value }
            )
        }
    }

    @Test
    fun thePathDpMatchesExhaustivePhraseEnumeration() {
        val definitions = listOf(
            definition(length = 2, start = 1, end = 1),
            definition(length = 2, start = 1, end = 1, steps = listOf(2)),
            definition(length = 3, start = 1, end = null),
            definition(length = 3, start = null, end = null),
            definition(length = 4, start = 2, end = 3),
            definition(length = 3, start = 1, end = 1, steps = listOf(0, 2, -2)),
            definition(
                keys = listOf(9),
                tonalities = listOf(Tonality.NATURAL_MINOR),
                minimum = 57,
                maximum = 69,
                length = 3,
                start = 1,
                end = 1,
                steps = listOf(-3, -2, 0, 2, 3),
                leap = 3
            ),
            definition(length = 2, start = 1, end = 1, steps = listOf(-2, -1, 1, 2))
        )
        val nodeCost: (MelodyNode) -> Long = { it.value * 1_000L }
        val edgeCost: (MelodyNode, MelodyNode) -> Long =
            { from, to -> abs(to.value - from.value) * 1_000L + 7L }
        definitions.forEach { definition ->
            for (keyValue in definition.effectiveKeyValues) {
                for (tonality in definition.tonalities) {
                    val graph = definition.graphFor(keyValue, tonality)
                    val phrases = completePhrases(definition, keyValue, tonality)
                    assertEquals(
                        "$graph feasible",
                        phrases.isNotEmpty(),
                        graph.isFeasible
                    )
                    val expectedNode = phrases.maxOfOrNull { phrase ->
                        phrase.fold(0L) { total, node -> total + nodeCost(node) }
                    }
                    val expectedEdge = phrases.maxOfOrNull { phrase ->
                        phrase.zipWithNext().fold(0L) { total, (from, to) ->
                            total + edgeCost(from, to)
                        }
                    }
                    assertEquals(
                        "$graph node path",
                        expectedNode,
                        graph.maximumNodePathCost(nodeCost)
                    )
                    assertEquals(
                        "$graph edge path",
                        expectedEdge,
                        graph.maximumEdgePathCost(edgeCost)
                    )
                }
            }
        }
    }

    @Test
    fun infeasibleGraphsHaveNoStartsAndNoPathCost() {
        val withoutZero = definition(length = 2, start = 1, end = 1, steps = listOf(2))
        val graph = withoutZero.graphFor(0, Tonality.MAJOR)
        assertFalse(graph.isFeasible)
        assertTrue(graph.eligibleStarts.isEmpty())
        assertNull(graph.maximumNodePathCost { it.value.toLong() })
        assertNull(graph.maximumEdgePathCost { _, _ -> 1L })
        assertTrue(
            "no canFinish entry survives an impossible phrase",
            graph.canFinishRow(1).none { it }
        )

        val repeatedPitch = definition(
            length = 2,
            start = 1,
            end = 1,
            steps = listOf(0),
            leap = 0
        )
        assertTrue(repeatedPitch.graphFor(0, Tonality.MAJOR).isFeasible)
        val wrongEnd = definition(
            length = 2,
            start = 1,
            end = 2,
            steps = listOf(0),
            leap = 0
        )
        assertFalse(
            "zero alone cannot change the endpoint degree",
            wrongEnd.graphFor(0, Tonality.MAJOR).isFeasible
        )
    }

    @Test
    fun tonicToTonicPhrasesNeedZeroOrAnOctaveAtLengthTwo() {
        val zero = definition(length = 2, start = 1, end = 1, steps = listOf(0), leap = 0)
        val zeroGraph = zero.graphFor(0, Tonality.MAJOR)
        assertEquals(
            "every degree-1 node can start and end a tonic phrase",
            zeroGraph.nodes.filter { it.degree == 1 },
            zeroGraph.eligibleStarts
        )
        assertEquals(
            "the only step repeats the pitch",
            listOf(0),
            zeroGraph.transitions
        )
        completePhrases(zero, 0, Tonality.MAJOR).forEach { phrase ->
            assertEquals(phrase.first().value, phrase.last().value)
        }

        val octave = definition(
            length = 2,
            start = 1,
            end = 1,
            minimum = 60,
            maximum = 72,
            steps = listOf(12, -12),
            leap = 12
        )
        val octaveGraph = octave.graphFor(0, Tonality.MAJOR)
        assertTrue(
            "C4 and C5 can reach each other by an octave",
            octaveGraph.eligibleStarts.map { it.value }.containsAll(listOf(60, 72))
        )
        val octavePhrases = completePhrases(octave, 0, Tonality.MAJOR)
        assertTrue(octavePhrases.isNotEmpty())
        assertTrue(
            "every tonic-to-tonic phrase moves by exactly one octave",
            octavePhrases.all { phrase ->
                phrase.size == 2 &&
                    kotlin.math.abs(phrase[0].value - phrase[1].value) == 12 &&
                    phrase[0].degree == 1 &&
                    phrase[1].degree == 1
            }
        )

        val neither = definition(
            length = 2,
            start = 1,
            end = 1,
            steps = (-5..5).filter { it != 0 },
            leap = 5
        )
        assertFalse(
            "without zero and without the octave no degree-1 phrase exists",
            neither.graphFor(0, Tonality.MAJOR).isFeasible
        )
    }

    @Test
    fun reachableNodesAndStepsCoverExactlyTheCompletePhrases() {
        val definition = definition(length = 3, start = 1, end = 1)
        val graph = definition.graphFor(0, Tonality.MAJOR)
        val phrases = completePhrases(definition, 0, Tonality.MAJOR)
        assertEquals(
            "reachable nodes are exactly the phrase nodes",
            phrases.flatten().map { it.value }.distinct().sorted(),
            graph.reachableNodes().map { it.value }
        )
        assertEquals(
            "reachable steps are exactly the phrase transitions",
            phrases.flatMap { phrase -> phrase.zipWithNext { from, to -> to.value - from.value } }
                .distinct()
                .sorted(),
            graph.reachableSteps()
        )
        assertTrue(
            "every reachable step is an effective transition",
            graph.reachableSteps().all { it in graph.transitions }
        )
    }

    @Test
    fun theMaximumRangeGraphStaysWithinTheDocumentedBounds() {
        val wide = definition(
            keys = emptyList(),
            tonalities = listOf(Tonality.MAJOR, Tonality.NATURAL_MINOR),
            minimum = 0,
            maximum = 127,
            length = 16,
            start = null,
            end = null,
            steps = (-12..12).toList(),
            leap = 12
        )
        for (keyValue in 0..11) {
            for (tonality in Tonality.entries) {
                val graph = wide.graphFor(keyValue, tonality)
                assertTrue(
                    "at most 128 nodes for $keyValue/$tonality: ${graph.nodes.size}",
                    graph.nodes.size <= 128
                )
                assertEquals(
                    "every reachability row is aligned with the nodes",
                    graph.nodes.size,
                    graph.canFinishRow(0).size
                )
                assertTrue(graph.isFeasible)
                assertTrue(
                    "the table stays 16 rows at the maximum length",
                    graph.canFinishRow(15).size == graph.nodes.size
                )
            }
        }
    }
}
