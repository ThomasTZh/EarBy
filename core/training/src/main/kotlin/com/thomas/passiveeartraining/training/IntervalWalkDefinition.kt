package com.thomas.passiveeartraining.training

import com.thomas.passiveeartraining.music.MidiNote
import com.thomas.passiveeartraining.music.PitchClass
import com.thomas.passiveeartraining.music.SimpleInterval
import com.thomas.passiveeartraining.training.provider.VoiceVocabulary
import kotlin.math.abs

/** The permitted direction of every step in an interval walk. */
enum class WalkDirection {
    ASCENDING,
    DESCENDING,
    MIXED
}

/** One possible signed transition inside an interval walk. */
data class SignedIntervalStep(val intervalId: String, val semitones: Int) {
    init {
        require(intervalId.isNotBlank()) { "An interval id must not be blank" }
        require(semitones in -SimpleInterval.OCTAVE..SimpleInterval.OCTAVE) {
            "A walk step must be a simple interval but was $semitones"
        }
        require(SimpleInterval.of(abs(semitones)).id == intervalId) {
            "The interval id $intervalId does not describe $semitones semitones"
        }
    }
}

/**
 * The musical configuration of one finite interval walk.
 *
 * The configured range restricts only the starting note. Every later note is a target of the
 * previous step and must stay inside the packaged MIDI 21..108 samples. The walk length counts
 * interval steps, so a three-step walk contains four sounding notes.
 */
data class IntervalWalkDefinition(
    val intervalIds: List<String>,
    val rootValues: List<Int>,
    val minimumNote: MidiNote,
    val maximumNote: MidiNote,
    val fixedStart: MidiNote? = null,
    val direction: WalkDirection = WalkDirection.ASCENDING,
    val length: Int = DEFAULT_LENGTH
) {
    init {
        require(intervalIds.isNotEmpty()) { "A walk must select at least one interval" }
        require(intervalIds.distinct().size == intervalIds.size) {
            "Walk intervals must not repeat: $intervalIds"
        }
        require(intervalIds == canonicalIntervalIds.filter { it in intervalIds }) {
            "Walk intervals must be in canonical order: $intervalIds"
        }
        require(rootValues.isEmpty() || rootValues == rootValues.distinct().sorted()) {
            "Walk root values must be sorted and unique: $rootValues"
        }
        require(rootValues.all { it in 0 until PitchClass.COUNT }) {
            "Walk root values must be pitch classes: $rootValues"
        }
        require(minimumNote.value in PlayableRange.VALUES) {
            "A walk start range must begin inside the packaged samples: $minimumNote"
        }
        require(maximumNote.value in PlayableRange.VALUES) {
            "A walk start range must end inside the packaged samples: $maximumNote"
        }
        require(minimumNote.value <= maximumNote.value) {
            "A walk start range must not be inverted: $minimumNote..$maximumNote"
        }
        require(length in MIN_LENGTH..MAX_LENGTH) {
            "A walk must contain $MIN_LENGTH..$MAX_LENGTH interval steps but had $length"
        }
        fixedStart?.let { start ->
            require(start.value in minimumNote.value..maximumNote.value) {
                "The fixed walk start $start is outside the start range"
            }
            require(start.pitchClass.value in effectiveRootValues) {
                "The fixed walk start $start must match a selected root class"
            }
        }
    }

    val minimumNoteValue: Int get() = minimumNote.value
    val maximumNoteValue: Int get() = maximumNote.value

    /** All selected simple intervals in canonical semitone order. */
    val selectedIntervals: List<SimpleInterval>
        get() = SimpleInterval.ALL.filter { it.id in intervalIds }

    /** The effective root classes: an empty selection means every class. */
    val effectiveRootValues: List<Int>
        get() = rootValues.ifEmpty { PitchClass.ALL.map { it.value } }

    /** The possible starting notes inside the configured start range and root selection. */
    fun startCandidates(): List<MidiNote> {
        fixedStart?.let { return if (canFinish(it, length)) listOf(it) else emptyList() }
        return (minimumNoteValue..maximumNoteValue)
            .filter { it in PlayableRange.VALUES }
            .map { MidiNote.of(it) }
            .filter { it.pitchClass.value in effectiveRootValues }
            .filter { canFinish(it, length) }
    }

    /** Every signed edge that can leave [note] under this walk's interval and direction rules. */
    fun feasibleEdges(note: MidiNote): List<SignedIntervalStep> =
        selectedIntervals.flatMap { interval ->
            val signs = when (direction) {
                WalkDirection.ASCENDING -> listOf(interval.semitones)
                WalkDirection.DESCENDING -> listOf(-interval.semitones)
                WalkDirection.MIXED ->
                    if (interval.semitones == 0) {
                        listOf(0)
                    } else {
                        listOf(interval.semitones, -interval.semitones)
                    }
            }
            signs.map { SignedIntervalStep(interval.id, it) }
        }.filter { note.value + it.semitones in PlayableRange.VALUES }

    /** True when exactly [steps] transitions can complete from [start]. */
    fun canFinish(start: MidiNote, steps: Int = length): Boolean {
        require(steps in 0..MAX_LENGTH) { "Walk steps must be 0..$MAX_LENGTH but were $steps" }
        if (start.value !in PlayableRange.VALUES) return false
        val memo = Array(steps + 1) { BooleanArray(PlayableRange.HIGHEST + 1) }
        for (value in PlayableRange.VALUES) memo[0][value] = true
        for (remaining in 1..steps) {
            for (value in PlayableRange.VALUES) {
                memo[remaining][value] = feasibleEdges(MidiNote.of(value)).any { edge ->
                    memo[remaining - 1][value + edge.semitones]
                }
            }
        }
        return memo[steps][start.value]
    }

    /** True when at least one complete walk exists. */
    fun isFeasible(): Boolean = startCandidates().isNotEmpty()

    /** The ordered answer tokens of one completed walk. */
    fun answerTokensForSteps(steps: List<Int>): List<VoiceAssetId> =
        steps.flatMap { MelodicDictationDefinition.intervalTokensForStep(it) }

    /** The words [answerTokensForSteps] speaks and the walk answer display shows. */
    fun answerTextForSteps(steps: List<Int>): String =
        answerTokensForSteps(steps).joinToString(" ") {
            VoiceVocabulary.requireEntry(it).spokenText
        }

    override fun equals(other: Any?): Boolean = other is IntervalWalkDefinition &&
        intervalIds == other.intervalIds &&
        rootValues == other.rootValues &&
        minimumNote == other.minimumNote &&
        maximumNote == other.maximumNote &&
        fixedStart == other.fixedStart &&
        direction == other.direction &&
        length == other.length

    override fun hashCode(): Int {
        var result = intervalIds.hashCode()
        result = 31 * result + rootValues.hashCode()
        result = 31 * result + minimumNote.hashCode()
        result = 31 * result + maximumNote.hashCode()
        result = 31 * result + (fixedStart?.hashCode() ?: 0)
        result = 31 * result + direction.hashCode()
        result = 31 * result + length
        return result
    }

    companion object {
        const val MIN_LENGTH: Int = 2
        const val MAX_LENGTH: Int = 8
        const val DEFAULT_LENGTH: Int = 3

        /** Every simple interval in the canonical product order. */
        val canonicalIntervalIds: List<String> = SimpleInterval.ALL.map { it.id }

        /** C4..C5, all root classes, ascending, three steps. */
        fun default(): IntervalWalkDefinition = of(
            intervalIds = canonicalIntervalIds,
            rootValues = emptyList(),
            minimumNote = MidiNote.of(60),
            maximumNote = MidiNote.of(72)
        )

        /**
         * Normalizes a walk configuration.
         *
         * An empty interval selection means every simple interval; an empty root selection means
         * every root class. Both are normalized into canonical order for reproducible draws.
         */
        fun of(
            intervalIds: List<String> = canonicalIntervalIds,
            rootValues: List<Int> = emptyList(),
            minimumNote: MidiNote = MidiNote.of(60),
            maximumNote: MidiNote = MidiNote.of(72),
            fixedStart: MidiNote? = null,
            direction: WalkDirection = WalkDirection.ASCENDING,
            length: Int = DEFAULT_LENGTH
        ): IntervalWalkDefinition {
            val requested = intervalIds.ifEmpty { canonicalIntervalIds }.toSet()
            val unknown = requested.filter { SimpleInterval.ofIdOrNull(it) == null }
            require(unknown.isEmpty()) {
                "A walk selection contains unknown intervals: ${unknown.sorted()}"
            }
            val ids = canonicalIntervalIds.filter { it in requested }
            require(ids.isNotEmpty()) { "A walk must select at least one known interval" }
            val roots = if (rootValues.isEmpty()) {
                emptyList()
            } else {
                rootValues.distinct().sorted()
            }
            return IntervalWalkDefinition(
                intervalIds = ids,
                rootValues = roots,
                minimumNote = minimumNote,
                maximumNote = maximumNote,
                fixedStart = fixedStart,
                direction = direction,
                length = length
            )
        }
    }
}

/** One generated interval walk in domain form. */
data class IntervalWalk(
    val start: MidiNote,
    val notes: List<MidiNote>,
    val steps: List<Int>,
    val intervalIds: List<String>,
    val sequenceIndex: Int
) {
    init {
        require(notes.size == steps.size + 1) { "A walk needs one more note than steps" }
        require(intervalIds.size == steps.size) { "A walk needs one interval id per step" }
        require(notes.first() == start) { "The first walk note must be the start" }
        notes.zipWithNext().forEachIndexed { index, (from, to) ->
            require(to.value - from.value == steps[index]) {
                "Walk step $index does not match its notes: $from -> $to"
            }
            require(SimpleInterval.of(abs(steps[index])).id == intervalIds[index]) {
                "Walk interval $index does not match its signed step ${steps[index]}"
            }
        }
    }
}

/** Raised when no complete walk can be produced for a configuration. */
class InfeasibleIntervalWalkException(message: String) : IllegalArgumentException(message)

/**
 * Draws finite interval walks from one definition and one injected random source.
 *
 * The generator samples only edges that can still complete the remaining length. The sorted edge
 * list and the start list are the complete deterministic draw order; no rejection loop and no
 * ambient randomness is used.
 */
class SeededIntervalWalkGenerator(
    initialDefinition: IntervalWalkDefinition,
    private val source: RandomSource
) {
    private var definition: IntervalWalkDefinition = initialDefinition
    private var sequenceIndex: Int = 0

    /** Applies an edited configuration while continuing the same random stream. */
    fun reconfigure(next: IntervalWalkDefinition) {
        if (next == definition) return
        definition = next
    }

    fun next(): IntervalWalk {
        val starts = definition.startCandidates()
        if (starts.isEmpty()) {
            throw InfeasibleIntervalWalkException(
                "No complete interval walk fits the selected start range " +
                    "${definition.minimumNoteValue}..${definition.maximumNoteValue}."
            )
        }
        val start = definition.fixedStart ?: starts[source.nextInt(starts.size)]
        val notes = mutableListOf(start)
        val steps = mutableListOf<Int>()
        val intervalIds = mutableListOf<String>()
        var current = start
        for (remaining in definition.length downTo 1) {
            val candidates = definition.feasibleEdges(current).filter { edge ->
                definition.canFinish(
                    MidiNote.of(current.value + edge.semitones),
                    remaining - 1
                )
            }
            check(candidates.isNotEmpty()) { "A feasible walk has no edge at $current" }
            val edge = candidates[source.nextInt(candidates.size)]
            steps += edge.semitones
            intervalIds += edge.intervalId
            current = MidiNote.of(current.value + edge.semitones)
            notes += current
        }
        sequenceIndex += 1
        return IntervalWalk(
            start = start,
            notes = notes,
            steps = steps,
            intervalIds = intervalIds,
            sequenceIndex = sequenceIndex
        )
    }
}
