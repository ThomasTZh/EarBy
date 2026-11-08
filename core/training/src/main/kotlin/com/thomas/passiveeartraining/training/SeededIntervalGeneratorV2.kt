package com.thomas.passiveeartraining.training

import com.thomas.passiveeartraining.music.IntervalPresentation
import com.thomas.passiveeartraining.music.MidiNote
import com.thomas.passiveeartraining.music.SimpleInterval
import com.thomas.passiveeartraining.training.recipe.SequencePolicy

/**
 * Feasibility-based generator, algorithm version 2.
 *
 * Version 1's shuffle is preserved exactly: the canonical labels are copied into a bag, shuffled
 * with Fisher-Yates from the last index down to 1, one deterministic boundary correction is applied
 * against the previous cycle's last label, and no extra randomness is consumed. Version 2 then
 * draws, per label and in this exact order:
 *
 * 1. one presentation from the sorted enabled set with `nextInt(size)`, **even when that set holds
 *    one entry**, so adding a second presentation shifts every following draw predictably;
 * 2. one feasible root/target pair for that label and presentation with `nextInt(pairCount)`,
 *    including when that count is 1.
 *
 * An optional fixed root makes each pair list hold at most one entry; the configured pitch classes
 * and range are intersecting constraints, so a policy whose combination cannot be produced is
 * rejected before the first draw rather than silently expanded.
 */
class SeededIntervalGeneratorV2(policy: SequencePolicy, private val randomSource: RandomSource) :
    IntervalGenerator {

    override val identity: GeneratorIdentity = GeneratorIdentity.intervalV2(
        seed =
        (randomSource as? SeededRandomSource)?.seed
            ?: SeededIntervalGenerator.DEFAULT_RECORDED_SEED,
        policy = policy
    )

    private val definition: IntervalDefinition = identity.definition
    private val presentations: List<IntervalPresentation> = identity.enabledPresentations

    /** Feasible pairs per label and presentation, ordered by root then target. */
    private val feasiblePairs:
        Map<Pair<SimpleInterval, IntervalPresentation>, List<Pair<MidiNote, MidiNote>>> =
        buildFeasiblePairs(policy)

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

    /** Rejects any label and presentation combination that has no feasible pair in the policy. */
    init {
        validateFeasibility(policy)
    }

    override fun next(): IntervalExercise {
        if (nextPositionInCycle == bag.size) {
            startNewBag(isFirstBag = bag.isEmpty())
        }
        val interval = bag[nextPositionInCycle]
        // Both choices use nextInt even for a singleton, so the draw order is part of the contract.
        val presentation = presentations[randomSource.nextInt(presentations.size)]
        val pairs = feasiblePairs.getValue(interval to presentation)
        val pair = pairs[randomSource.nextInt(pairs.size)]
        val exercise = IntervalExercise(
            identity = identity,
            sequenceIndex = sequenceIndex,
            cycleIndex = cycleIndex,
            positionInCycle = nextPositionInCycle,
            interval = interval,
            root = pair.first,
            target = pair.second,
            presentation = presentation
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
        // The shuffle is identical to version 1: Fisher-Yates from the last index down to 1, with
        // an explicit temporary so the two writes cannot alias each other.
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
        /** Feasible pairs for every label and presentation; an empty list is infeasible. */
        fun buildFeasiblePairs(
            policy: SequencePolicy
        ): Map<Pair<SimpleInterval, IntervalPresentation>, List<Pair<MidiNote, MidiNote>>> =
            buildMap {
                for (interval in IntervalGenerator.canonicalIntervals(policy.definition)) {
                    for (presentation in policy.presentations) {
                        put(
                            interval to presentation,
                            pairsFor(policy, interval, presentation)
                        )
                    }
                }
            }

        /**
         * Validates that every selected label can be produced in every enabled presentation.
         *
         * @throws InfeasibleIntervalDefinitionException naming every missing combination.
         */
        fun validateFeasibility(policy: SequencePolicy) {
            val pairs = buildFeasiblePairs(policy)
            val missing = pairs.filterValues { it.isEmpty() }.keys.map { (interval, presentation) ->
                "${interval.id} $presentation"
            }
            if (missing.isNotEmpty()) {
                val fixed = policy.fixedRoot?.let { " and fixed root ${it.value}" } ?: ""
                throw InfeasibleIntervalDefinitionException(
                    "No ${policy.presentations.joinToString("/")} example fits " +
                        "${policy.definition.minimumNoteValue}.." +
                        "${policy.definition.maximumNoteValue} with root classes " +
                        "${policy.definition.selectedRoots.map { it.value }}$fixed for: " +
                        missing.joinToString(),
                    missing,
                    policy.definition.noteRange
                )
            }
        }

        /**
         * The pairs the policy allows for one label and presentation.
         *
         * A fixed root narrows the version 1 enumeration to that root; the root must still be
         * inside the range, match a selected pitch class and produce a feasible example.
         */
        private fun pairsFor(
            policy: SequencePolicy,
            interval: SimpleInterval,
            presentation: IntervalPresentation
        ): List<Pair<MidiNote, MidiNote>> {
            val all = SeededIntervalGenerator.pairsFor(
                policy.definition,
                interval,
                presentation
            )
            val fixed = policy.fixedRoot ?: return all
            return all.filter { it.first == fixed }
        }
    }
}
