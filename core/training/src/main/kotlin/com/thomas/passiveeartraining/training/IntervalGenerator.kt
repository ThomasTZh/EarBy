package com.thomas.passiveeartraining.training

import com.thomas.passiveeartraining.music.IntervalPresentation
import com.thomas.passiveeartraining.music.MidiNote
import com.thomas.passiveeartraining.music.SimpleInterval

/**
 * Reproducible generator of concrete interval exercises.
 *
 * A fresh generator owns its own random source and bag cursor; two instances created with the
 * same normalized definition and seed produce the same sequence.
 */
interface IntervalGenerator {
    val identity: GeneratorIdentity

    /** Number of exercises in one cycle (one bag). */
    val cycleLength: Int

    /** Index of the cycle the next exercise belongs to; a sequence index of 0 starts cycle 0. */
    val cycleIndex: Int

    /** Number of exercises already produced. */
    val sequenceIndex: Int

    /** Produces the next concrete exercise and advances the sequence. */
    fun next(): IntervalExercise

    companion object {
        const val GENERATOR_ID: String = GeneratorIdentity.INTERVAL_GENERATOR_ID
        const val GENERATOR_VERSION: Int = GeneratorIdentity.INTERVAL_GENERATOR_VERSION
        const val RNG_ID: String = SeededRandomSource.RNG_ID

        /** Canonical label order used for bags: ascending semitones, then identifier. */
        fun canonicalIntervals(definition: IntervalDefinition): List<SimpleInterval> =
            definition.selectedIntervals.sortedWith(
                compareBy({ it.semitones }, { it.id })
            )
    }
}

/**
 * Feasibility-based generator, algorithm version 1.
 *
 * Feasible root/target pairs are computed once from the definition. Each cycle copies the
 * canonical labels into a bag, shuffles it with Fisher-Yates from the last index down to 1
 * using [RandomSource.nextInt], applies one deterministic boundary correction against the
 * previous cycle's last label, and then draws exactly one feasible pair per label with
 * `nextInt(feasiblePairCount)` - including when that count is 1.
 *
 * This yields reproducible sampling with one occurrence of each label per cycle. The boundary
 * correction deliberately makes the label sequence non-uniform over all permutations, and it
 * consumes no additional randomness.
 */
class SeededIntervalGenerator(
    definition: IntervalDefinition,
    private val randomSource: RandomSource
) : IntervalGenerator {

    override val identity: GeneratorIdentity = GeneratorIdentity.interval(
        seed = (randomSource as? SeededRandomSource)?.seed ?: DEFAULT_RECORDED_SEED,
        definition = definition
    )

    private val definition: IntervalDefinition = identity.definition

    /** Each selected interval mapped to its feasible pairs, ordered by root then target. */
    private val feasiblePairs: Map<SimpleInterval, List<Pair<MidiNote, MidiNote>>> =
        buildFeasiblePairs(definition)

    private val canonicalLabels: List<SimpleInterval> = IntervalGenerator.canonicalIntervals(
        definition
    )

    private var bag: MutableList<SimpleInterval> = mutableListOf()
    private var previousCycleLastLabel: SimpleInterval? = null
    private var nextPositionInCycle: Int = 0

    override val cycleLength: Int get() = canonicalLabels.size

    override var cycleIndex: Int = 0
        private set

    override var sequenceIndex: Int = 0
        private set

    /** Rejects any selected label that has no feasible pair in the configured range. */
    init {
        validateFeasibility(definition)
    }

    override fun next(): IntervalExercise {
        if (nextPositionInCycle == bag.size) {
            startNewBag(isFirstBag = bag.isEmpty())
        }
        val interval = bag[nextPositionInCycle]
        val pairs = feasiblePairs.getValue(interval)
        val pair = pairs[randomSource.nextInt(pairs.size)]
        val exercise = IntervalExercise(
            identity = identity,
            sequenceIndex = sequenceIndex,
            cycleIndex = cycleIndex,
            positionInCycle = nextPositionInCycle,
            interval = interval,
            root = pair.first,
            target = pair.second
        )
        nextPositionInCycle += 1
        sequenceIndex += 1
        if (nextPositionInCycle == bag.size) {
            // Recorded for the boundary correction of the following bag; it consumes no randomness.
            previousCycleLastLabel = bag.last()
        }
        return exercise
    }

    private fun startNewBag(isFirstBag: Boolean) {
        val fresh = canonicalLabels.toMutableList()
        // Fisher-Yates from the last index down to 1; the swap is written with an explicit
        // temporary so the two writes cannot alias each other.
        for (index in fresh.lastIndex downTo 1) {
            val draw = randomSource.nextInt(index + 1)
            val current = fresh[index]
            val picked = fresh[draw]
            fresh[index] = picked
            fresh[draw] = current
        }
        val previous = previousCycleLastLabel
        if (fresh.size >= 2 && previous != null && fresh[0] == previous) {
            val replacementIndex = fresh.indexOfFirst { it != previous }
            if (replacementIndex > 0) {
                fresh[0] = fresh[replacementIndex].also { fresh[replacementIndex] = fresh[0] }
            }
        }
        if (!isFirstBag) {
            cycleIndex += 1
        }
        bag = fresh
        nextPositionInCycle = 0
    }

    companion object {
        /**
         * Seed recorded when the caller supplies a custom [RandomSource].
         *
         * A scripted or otherwise injected source has no intrinsic seed; the recorded identity
         * then documents that the definition was reproduced with an external source.
         */
        const val DEFAULT_RECORDED_SEED: Long = 0L

        /** Feasible pairs for every selected label; `null` for labels with no pair in range. */
        fun buildFeasiblePairs(
            definition: IntervalDefinition
        ): Map<SimpleInterval, List<Pair<MidiNote, MidiNote>>> = buildMap {
            for (interval in IntervalGenerator.canonicalIntervals(definition)) {
                put(interval, pairsFor(definition, interval))
            }
        }

        /**
         * Validates that every selected label has at least one feasible pair.
         *
         * @throws InfeasibleIntervalDefinitionException naming every infeasible label and the range.
         */
        fun validateFeasibility(definition: IntervalDefinition) {
            val pairs = buildFeasiblePairs(definition)
            val missing = pairs.filterValues { it.isEmpty() }.keys.map { it.id }
            if (missing.isNotEmpty()) {
                throw InfeasibleIntervalDefinitionException(
                    "No ${definition.presentation} example fits " +
                        "${definition.minimumNoteValue}..${definition.maximumNoteValue} " +
                        "with root classes ${definition.selectedRoots.map {
                            it.value
                        }} for: ${missing.joinToString()}",
                    missing,
                    definition.noteRange
                )
            }
        }

        internal fun pairsFor(
            definition: IntervalDefinition,
            interval: SimpleInterval,
            presentation: IntervalPresentation = definition.presentation
        ): List<Pair<MidiNote, MidiNote>> {
            val selectedRootClasses = definition.selectedRoots.map { it.value }.toSet()
            val offset = when (presentation) {
                IntervalPresentation.ASCENDING, IntervalPresentation.HARMONIC -> interval.semitones
                IntervalPresentation.DESCENDING -> -interval.semitones
            }
            val pairs = mutableListOf<Pair<MidiNote, MidiNote>>()
            // The configured range constrains roots only. Non-root interval tones may leave the
            // root range but must remain inside the packaged instrument's playable samples.
            for (rootValue in definition.minimumNoteValue..definition.maximumNoteValue) {
                val root = MidiNote.of(rootValue)
                if (root.value !in PlayableRange.VALUES) continue
                if (root.pitchClass.value !in selectedRootClasses) continue
                val target = root.transposedBy(offset) ?: continue
                if (target.value !in PlayableRange.VALUES) continue
                if (!presentation.isFeasible(root, interval)) continue
                pairs += root to target
            }
            return pairs.sortedWith(compareBy({ it.first.value }, { it.second.value }))
        }
    }
}

/**
 * Raised when a selected label cannot be produced inside the configured range and roots.
 *
 * All infeasible labels are reported at once; the generator never silently drops a label and
 * never retries until a valid example appears.
 */
class InfeasibleIntervalDefinitionException(
    message: String,
    val infeasibleLabelIds: List<String>,
    val noteRange: IntRange
) : IllegalArgumentException(message)
