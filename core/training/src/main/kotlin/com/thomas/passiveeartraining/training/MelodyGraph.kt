package com.thomas.passiveeartraining.training

import com.thomas.passiveeartraining.music.MidiNote
import com.thomas.passiveeartraining.music.SpelledPitch
import com.thomas.passiveeartraining.music.Tonality

/**
 * One absolute scale note of one selected key and tonality inside a definition's range (M10.1).
 *
 * A node is a concrete MIDI note, not a pitch class: two octaves of one degree are two nodes. The
 * degree is the theoretical 1..7 scale degree relative to the key tonic and is always derived from
 * the key's scale membership, never from a sounding distance. The spelling is the key's theoretical
 * spelling of that degree at the note's octave.
 */
data class MelodyNode(val midi: MidiNote, val degree: Int, val spelled: SpelledPitch) :
    Comparable<MelodyNode> {
    init {
        require(degree in 1..7) { "A melodic degree must be 1..7 but was $degree" }
    }

    /** Sounding MIDI value. */
    val value: Int get() = midi.value

    /** The signed semitone step from this node to [to]; zero when the two notes repeat. */
    fun stepTo(to: MelodyNode): Int = to.value - value

    override fun compareTo(other: MelodyNode): Int = midi.value.compareTo(other.midi.value)
}

/**
 * The finite reachable phrase graph of one selected key and tonality (M10.1).
 *
 * The nodes are the definition's scale nodes inside its range, at most 128 because the MIDI range
 * holds at most that many values. There is an edge from node `p` to node `q` exactly when
 * `q - p` is one of the definition's [transitions]; because [transitions] is the allowed signed
 * steps intersected with the maximum leap, a step of exactly the maximum leap is an edge and a
 * larger one is not. A zero edge exists only when zero is explicitly allowed. A step that would
 * reach a chromatic pitch has no target node, so no chromatic note can enter a phrase.
 *
 * [canFinish] is the reachability table `canFinish[remainingTransitions][node]`: true when a path
 * of exactly that many transitions from the node can end at a node whose degree is the requested
 * end degree (or at any node when the end degree is null). Row zero is the base row; later rows are
 * built backwards from it, and a node is true at row `r` when it has an outgoing edge to a node
 * true at row `r - 1`. The table is bounded by 128 nodes x 16 rows, so no phrase is ever
 * enumerated or retained.
 *
 * The class is pure: it owns no randomness and never mutates. The provider samples over
 * [eligibleStarts] and [candidatesForNext] with its own derived streams.
 */
class MelodyGraph private constructor(
    /** The key tonic pitch class this graph was built for. */
    val keyValue: Int,
    /** The tonality this graph was built for. */
    val tonality: Tonality,
    nodeList: List<MelodyNode>,
    transitionList: List<Int>,
    /** The definition's maximum leap; a step of exactly this size is allowed. */
    val maximumLeap: Int,
    /** The exact phrase note count, including both endpoints. */
    val phraseLength: Int,
    /** The required start degree 1..7, or null for any degree. */
    val startDegree: Int?,
    /** The required end degree 1..7, or null for any degree. */
    val endDegree: Int?,
    private val outgoingIndices: List<IntArray>,
    private val canFinishRows: List<BooleanArray>
) {
    /** Every node in ascending MIDI order; a defensive immutable copy. */
    val nodes: List<MelodyNode> = java.util.Collections.unmodifiableList(nodeList.toList())

    /**
     * The effective signed transition steps in ascending order; a defensive immutable copy.
     *
     * These are the definition's allowed steps filtered by the maximum leap.
     */
    val transitions: List<Int> = java.util.Collections.unmodifiableList(transitionList.toList())

    private val indexByValue: Map<Int, Int> =
        nodes.withIndex().associate { (index, node) -> node.value to index }

    /**
     * Every node that has the requested start degree and can still reach an allowed ending,
     * ascending.
     *
     * An empty list means no complete phrase of [phraseLength] notes exists for this key and
     * tonality under the current steps, leap, endpoints and range.
     */
    val eligibleStarts: List<MelodyNode> = nodes.filterIndexed { index, node ->
        (startDegree == null || node.degree == startDegree) &&
            canFinishRows[phraseLength - 1][index]
    }

    /** True when at least one eligible start exists. */
    val isFeasible: Boolean get() = eligibleStarts.isNotEmpty()

    /**
     * True when a path of exactly [remainingTransitions] transitions from [from] can end at an
     * allowed ending-degree node.
     */
    fun canFinish(remainingTransitions: Int, from: MelodyNode): Boolean {
        require(remainingTransitions in 0 until phraseLength) {
            "Remaining transitions must be 0..${phraseLength - 1} but were $remainingTransitions"
        }
        return canFinishRows[remainingTransitions][indexOf(from)]
    }

    /** The complete [canFinish] row for [remainingTransitions], aligned with [nodes]. */
    fun canFinishRow(remainingTransitions: Int): List<Boolean> {
        require(remainingTransitions in 0 until phraseLength) {
            "Remaining transitions must be 0..${phraseLength - 1} but were $remainingTransitions"
        }
        return canFinishRows[remainingTransitions].toList()
    }

    /** Every outgoing edge target of [from], ascending; unfiltered by reachability. */
    fun outgoingNodes(from: MelodyNode): List<MelodyNode> =
        outgoingIndices[indexOf(from)].map { nodes[it] }

    /**
     * Every candidate for the phrase position [position] after [from], ascending.
     *
     * [position] is the zero-based index of the next note, so the call is valid for 1 through
     * `phraseLength - 1`. A candidate is an outgoing edge target that can still reach an allowed
     * ending with the remaining transitions; the returned order is the sampler's documented
     * candidate order.
     */
    fun candidatesForNext(position: Int, from: MelodyNode): List<MelodyNode> {
        require(position in 1 until phraseLength) {
            "A next position must be 1..${phraseLength - 1} but was $position"
        }
        val row = canFinishRows[phraseLength - 1 - position]
        return outgoingIndices[indexOf(from)].filter { row[it] }.map { nodes[it] }
    }

    /**
     * Every node that occurs in at least one complete phrase, ascending.
     *
     * Used to report exactly the answer tokens a configuration can speak. The forward pass walks
     * the eligible starts position by position, keeping only nodes that can still finish.
     */
    fun reachableNodes(): List<MelodyNode> = reachability.nodes

    /** Every signed transition step that occurs in at least one complete phrase, ascending. */
    fun reachableSteps(): List<Int> = reachability.steps

    /**
     * The exact maximum over valid complete phrases of the sum of [nodeCostNanos] over every
     * phrase node, or null when the graph has no complete phrase.
     *
     * The result is in the cost function's own unit (the provider uses nanoseconds) and uses
     * checked integer addition; it never samples or enumerates a phrase.
     */
    fun maximumNodePathCost(nodeCostNanos: (MelodyNode) -> Long): Long? {
        if (!isFeasible) return null
        var previous = LongArray(nodes.size) { index ->
            if (canFinishRows[0][index]) nodeCostNanos(nodes[index]) else IMPOSSIBLE
        }
        for (row in 1 until phraseLength) {
            val current = LongArray(nodes.size) { IMPOSSIBLE }
            for (index in nodes.indices) {
                var best = IMPOSSIBLE
                for (target in outgoingIndices[index]) {
                    val candidate = previous[target]
                    if (candidate != IMPOSSIBLE && candidate > best) best = candidate
                }
                if (best != IMPOSSIBLE) {
                    current[index] = Math.addExact(nodeCostNanos(nodes[index]), best)
                }
            }
            previous = current
        }
        var best = IMPOSSIBLE
        for (start in eligibleStarts) {
            val candidate = previous[indexOf(start)]
            if (candidate > best) best = candidate
        }
        return if (best == IMPOSSIBLE) null else best
    }

    /**
     * The exact maximum over valid complete phrases of the sum of [edgeCostNanos] over every
     * transition, or null when the graph has no complete phrase.
     *
     * The result carries no per-node cost and no connection gap: the caller adds its own clip
     * bookkeeping (for example the exactly-80 ms gaps between the clips of all edge phrases).
     */
    fun maximumEdgePathCost(edgeCostNanos: (MelodyNode, MelodyNode) -> Long): Long? {
        if (!isFeasible) return null
        var previous = LongArray(nodes.size) { index ->
            if (canFinishRows[0][index]) 0L else IMPOSSIBLE
        }
        for (row in 1 until phraseLength) {
            val current = LongArray(nodes.size) { IMPOSSIBLE }
            for (index in nodes.indices) {
                var best = IMPOSSIBLE
                for (target in outgoingIndices[index]) {
                    val candidate = previous[target]
                    if (candidate != IMPOSSIBLE) {
                        val cost = Math.addExact(
                            edgeCostNanos(nodes[index], nodes[target]),
                            candidate
                        )
                        if (cost > best) best = cost
                    }
                }
                current[index] = best
            }
            previous = current
        }
        var best = IMPOSSIBLE
        for (start in eligibleStarts) {
            val candidate = previous[indexOf(start)]
            if (candidate > best) best = candidate
        }
        return if (best == IMPOSSIBLE) null else best
    }

    private fun indexOf(node: MelodyNode): Int = indexByValue[node.value]
        ?: throw IllegalArgumentException("The node $node is not part of this graph")

    private data class Reachability(val nodes: List<MelodyNode>, val steps: List<Int>)

    private val reachability: Reachability by lazy { computeReachability() }

    private fun computeReachability(): Reachability {
        if (!isFeasible) return Reachability(emptyList(), emptyList())
        val positionRows = ArrayList<Set<Int>>(phraseLength)
        positionRows += eligibleStarts.map { indexOf(it) }.toSet()
        val steps = linkedSetOf<Int>()
        for (position in 1 until phraseLength) {
            val previousRow = positionRows[position - 1]
            val currentRow = linkedSetOf<Int>()
            for (from in previousRow) {
                for (target in outgoingIndices[from]) {
                    if (canFinishRows[phraseLength - 1 - position][target]) {
                        currentRow += target
                        steps += nodes[target].value - nodes[from].value
                    }
                }
            }
            positionRows += currentRow
        }
        val reachableIndices = sortedSetOf<Int>()
        positionRows.forEach { reachableIndices += it }
        return Reachability(reachableIndices.map { nodes[it] }, steps.sorted())
    }

    override fun toString(): String =
        "MelodyGraph($keyValue/$tonality, nodes=${nodes.size}, transitions=$transitions, " +
            "length=$phraseLength, start=$startDegree, end=$endDegree, " +
            "eligibleStarts=${eligibleStarts.size})"

    companion object {
        /** Sentinel for a path that cannot finish; costs are always non-negative. */
        private const val IMPOSSIBLE: Long = Long.MIN_VALUE

        /**
         * Builds the graph of ([keyValue], [tonality]) under [definition].
         *
         * Node and edge enumeration is deterministic and bounded: nodes ascend by MIDI, adjacency
         * is sorted by MIDI and the reachability rows are built backwards from the ending rule.
         */
        fun of(
            definition: MelodicDictationDefinition,
            keyValue: Int,
            tonality: Tonality
        ): MelodyGraph {
            val nodes = definition.scaleNodes(keyValue, tonality)
            require(nodes.size <= MidiNote.MAX_VALUE + 1) {
                "A melody graph is bounded by ${MidiNote.MAX_VALUE + 1} nodes but had " +
                    nodes.size
            }
            val indexByValue = nodes.withIndex().associate { (index, node) -> node.value to index }
            val transitions = definition.effectiveSteps
            val outgoing = List(nodes.size) { index ->
                transitions
                    .mapNotNull { step -> indexByValue[nodes[index].value + step] }
                    .sorted()
                    .toIntArray()
            }
            val rows = ArrayList<BooleanArray>(definition.phraseLength)
            rows += BooleanArray(nodes.size) { index ->
                definition.endDegree == null || nodes[index].degree == definition.endDegree
            }
            for (row in 1 until definition.phraseLength) {
                val previous = rows[row - 1]
                rows += BooleanArray(nodes.size) { index ->
                    outgoing[index].any { target -> previous[target] }
                }
            }
            return MelodyGraph(
                keyValue = keyValue,
                tonality = tonality,
                nodeList = nodes,
                transitionList = transitions,
                maximumLeap = definition.maximumLeap,
                phraseLength = definition.phraseLength,
                startDegree = definition.startDegree,
                endDegree = definition.endDegree,
                outgoingIndices = outgoing,
                canFinishRows = rows
            )
        }
    }
}
