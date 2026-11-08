package com.thomas.passiveeartraining.training.provider

import com.thomas.passiveeartraining.music.IntervalPresentation
import com.thomas.passiveeartraining.music.SimpleInterval
import com.thomas.passiveeartraining.music.Tonality
import com.thomas.passiveeartraining.training.ContextualIntervalDefinition
import com.thomas.passiveeartraining.training.ContextualIntervalPair
import com.thomas.passiveeartraining.training.MissingVoiceDurationException
import com.thomas.passiveeartraining.training.SeededRandomSource
import com.thomas.passiveeartraining.training.VoiceAssetId
import com.thomas.passiveeartraining.training.VoiceDurationCatalog
import com.thomas.passiveeartraining.training.context.ContextBuilder
import com.thomas.passiveeartraining.training.context.TonalContext
import com.thomas.passiveeartraining.training.recipe.RecipeTiming
import kotlin.time.Duration

/**
 * M9B.1/M9B.2: the contextual-interval provider.
 *
 * Categories are the selected simple interval ids. The label bag shuffles those interval ids with
 * the SC3 contract (Fisher-Yates, `nextInt(i + 1)` from the last index down); for each drawn
 * interval one context-stream draw selects an eligible (key, tonality, anchor, context)
 * construction uniformly over the documented canonical order (key pitch class, tonality ordinal,
 * anchor), then one target-stream draw selects an eligible target pair of that key/tonality
 * uniformly in the stable order `(firstMidi, secondMidi, degree1, degree2)`. Singleton sets still
 * consume exactly one draw, and there is no rejection or retry loop.
 *
 * The separate child streams come from `mode-seed-v1` with ordinal 0 and the SC3 labels, exactly
 * like the M8 provider, so a context choice can never consume the target pair's randomness and
 * vice versa. An interval is an input selection, never classified from a context pitch, and the
 * degree facts stay separate from the semitone facts.
 *
 * Each exercise is the complete M8 context (CONTEXT groups), the context gap and the two target
 * notes: sequential presentations schedule two TARGET groups in presented order, the harmonic
 * presentation one simultaneous TARGET group low to high with the same sounding pitch deduplicated
 * for unison.
 */
class ContextualIntervalExerciseProvider : ExerciseProvider {
    override val kind: TrainingKind = TrainingKind.INTERVALS_IN_CONTEXT

    override val providerVersion: Int = 1

    override fun defaults(): ProviderConfig =
        ProviderConfig.ContextualIntervals(ContextualIntervalDefinition.default())

    override fun requiredVocabulary(config: ProviderConfig): Set<VoiceAssetId> {
        val definition = (config as ProviderConfig.ContextualIntervals).definition
        val tokens = linkedSetOf<VoiceAssetId>()
        for (keyValue in definition.effectiveKeyValues) {
            for (tonality in definition.tonalities) {
                for (interval in definition.intervals) {
                    if (definition.supports(keyValue, tonality, interval)) {
                        definition.eligiblePairs(keyValue, tonality, interval).forEach { pair ->
                            tokens += definition.answerTokensFor(
                                interval,
                                pair.firstDegree,
                                pair.secondDegree
                            )
                        }
                    }
                }
            }
        }
        return tokens
    }

    override fun validate(
        config: ProviderConfig,
        catalog: VoiceDurationCatalog
    ): ProviderValidation {
        if (config !is ProviderConfig.ContextualIntervals) {
            return ProviderValidation.Invalid(
                listOf(
                    FieldIssue(
                        "kind",
                        "This provider only accepts contextual-interval configurations."
                    )
                )
            )
        }
        val definition = config.definition
        val required = requiredVocabulary(config)
        val missing = required.filterNot { voiceId ->
            try {
                catalog.durationOf(voiceId)
                true
            } catch (_: MissingVoiceDurationException) {
                false
            }
        }
        if (missing.isNotEmpty()) {
            return ProviderValidation.Invalid(
                missing.sortedBy { it.value }.map {
                    FieldIssue("answers", "The answer clip '$it' is missing from the voice pack.")
                }
            )
        }
        val infeasible = mutableListOf<FieldIssue>()
        for (keyValue in definition.effectiveKeyValues) {
            for (tonality in definition.tonalities) {
                for (interval in definition.intervals) {
                    if (!definition.supports(keyValue, tonality, interval)) {
                        infeasible += rangeIssue(definition, keyValue, tonality, interval)
                    }
                }
            }
        }
        if (infeasible.isNotEmpty()) {
            return ProviderValidation.Invalid(infeasible)
        }
        return ProviderValidation.Valid(
            config = config,
            categories = definition.intervalIds.map { AnswerCategory(it) },
            maximumExampleDuration = maximumExampleDuration(definition),
            maximumAnswerDuration = maximumAnswerDuration(definition, catalog),
            requiredAssets = required
        )
    }

    override fun createGenerator(config: ProviderConfig, seed: Long): ExerciseGenerator {
        val definition = (config as ProviderConfig.ContextualIntervals).definition
        return ContextualIntervalGeneratorAdapter(definition, seed)
    }

    companion object {
        /**
         * The longest example window over the selected configuration under [timing].
         *
         * The example is the complete M8 context, the context gap and the two target notes: the
         * sequential shape is `2*d + g` and the harmonic shape one note `d`. The gap is the
         * definition's own [ContextualIntervalDefinition.contextGap]; the setup layer mirrors it
         * into [RecipeTiming.contextGap], and the shared compiler consumes that timing value, so
         * this bound is measured with the same numbers playback uses.
         */
        fun maximumExampleDuration(
            definition: ContextualIntervalDefinition,
            timing: RecipeTiming = RecipeTiming()
        ): Duration {
            val targetDuration = when (definition.presentation) {
                IntervalPresentation.ASCENDING,
                IntervalPresentation.DESCENDING -> timing.noteDuration * 2 + timing.melodicGap

                IntervalPresentation.HARMONIC -> timing.noteDuration
            }
            return ContextBuilder.duration(timing, definition.context) +
                definition.contextGap + targetDuration
        }

        /**
         * The longest reachable answer: the longest token sequence of any feasible interval and
         * pair, including the exactly-80 ms inter-token gap.
         */
        fun maximumAnswerDuration(
            definition: ContextualIntervalDefinition,
            catalog: VoiceDurationCatalog
        ): Duration {
            val sequences = buildList {
                for (keyValue in definition.effectiveKeyValues) {
                    for (tonality in definition.tonalities) {
                        for (interval in definition.intervals) {
                            if (definition.supports(keyValue, tonality, interval)) {
                                definition.eligiblePairs(keyValue, tonality, interval)
                                    .forEach { pair ->
                                        add(
                                            definition.answerTokensFor(
                                                interval,
                                                pair.firstDegree,
                                                pair.secondDegree
                                            )
                                        )
                                    }
                            }
                        }
                    }
                }
            }
            return sequences.maxOfOrNull { tokens ->
                tokens.fold(Duration.ZERO) { total, token -> total + catalog.durationOf(token) } +
                    ExerciseCompiler.TOKEN_GAP * (tokens.size - 1)
            } ?: Duration.ZERO
        }

        /**
         * The range issue for one selected interval with no eligible context and pair.
         *
         * An empty pair set and an empty construction set are different musical problems and get
         * separate messages: the pair may need a target note outside the range, or the complete
         * context may already exceed it.
         */
        internal fun rangeIssue(
            definition: ContextualIntervalDefinition,
            keyValue: Int,
            tonality: Tonality,
            interval: SimpleInterval
        ): FieldIssue {
            val keyName = definition.keyDisplayName(keyValue, tonality)
            val range = "${definition.minimumNoteValue}..${definition.maximumNoteValue}"
            val presentation = ContextualIntervalDefinition.presentationLabel(
                definition.presentation
            )
            val intervalText = interval.englishAnswer.lowercase()
            return if (definition.eligiblePairs(keyValue, tonality, interval).isEmpty()) {
                FieldIssue(
                    "range",
                    "No $keyName $presentation $intervalText target pair sounds inside the " +
                        "selected range $range."
                )
            } else {
                FieldIssue(
                    "range",
                    "No $keyName ${definition.context.label} context fits the selected range " +
                        "$range for the $presentation $intervalText pair; the context and both " +
                        "target notes must all sound inside the range."
                )
            }
        }
    }
}

/**
 * Owns the three derived draw streams of one contextual-interval activation.
 *
 * The activation seed is the already-resolved seed from [ModeSetup]; the adapter derives the SC3
 * labels/context/target children with ordinal 0, so recording the activation seed and the
 * normalized definition reconstructs every draw. A configuration edit discards the remainder of
 * the current label bag and continues all three streams; the sequence index stays monotonic.
 */
private class ContextualIntervalGeneratorAdapter(
    initialDefinition: ContextualIntervalDefinition,
    private val activationSeed: Long
) : ExerciseGenerator {

    private val labelsStream = SeededRandomSource(
        ModeSeedDerivation.derive(
            activationSeed,
            TrainingKind.INTERVALS_IN_CONTEXT,
            ACTIVATION_ORDINAL,
            ModeSeedDerivation.LABELS
        )
    )
    private val contextStream = SeededRandomSource(
        ModeSeedDerivation.derive(
            activationSeed,
            TrainingKind.INTERVALS_IN_CONTEXT,
            ACTIVATION_ORDINAL,
            ModeSeedDerivation.CONTEXT
        )
    )
    private val targetStream = SeededRandomSource(
        ModeSeedDerivation.derive(
            activationSeed,
            TrainingKind.INTERVALS_IN_CONTEXT,
            ACTIVATION_ORDINAL,
            ModeSeedDerivation.TARGET
        )
    )

    private var definition: ContextualIntervalDefinition = initialDefinition
    private var feasible: Map<String, List<TonalContext>> = buildFeasible(initialDefinition)
    private var bag: MutableList<String> = mutableListOf()
    private var nextPositionInCycle: Int = 0
    private var started: Boolean = false
    private var cycleIndex: Int = 0
    private var sequenceIndex: Int = 0

    init {
        require(feasible.values.none { it.isEmpty() }) {
            "Every enabled interval must have a feasible context and target pair"
        }
    }

    override fun next(): ConcreteExercise {
        if (nextPositionInCycle == bag.size) {
            startNewBag()
        }
        val intervalId = bag[nextPositionInCycle]
        val interval = SimpleInterval.ofId(intervalId)
        val constructions = feasible.getValue(intervalId)
        val construction = constructions[contextStream.nextInt(constructions.size)]
        val pairs = definition.eligiblePairs(
            construction.keyPitchClass,
            construction.tonality,
            interval
        )
        val pair = pairs[targetStream.nextInt(pairs.size)]
        val position = nextPositionInCycle
        nextPositionInCycle += 1
        val exercise = adapt(interval, construction, pair, position)
        sequenceIndex += 1
        return exercise
    }

    override fun reconfigure(config: ProviderConfig) {
        val updated = (config as ProviderConfig.ContextualIntervals).definition
        if (updated == definition) return
        val updatedFeasible = buildFeasible(updated)
        require(updatedFeasible.values.none { it.isEmpty() }) {
            "Every enabled interval must have a feasible context and target pair"
        }
        definition = updated
        feasible = updatedFeasible
        // The remainder of the old bag no longer describes the edited setup; the streams continue.
        bag = mutableListOf()
        nextPositionInCycle = 0
    }

    /**
     * Every eligible construction of every selected interval.
     *
     * Iteration is the documented candidate order: key pitch class ascending, tonality ordinal,
     * then anchor ascending (inside
     * [ContextualIntervalDefinition.eligibleConstructions]); the flattening therefore needs no
     * second sort. A construction is included only for an interval that has at least one eligible
     * pair in its key/tonality, so a draw can never select a context with nothing to pair it with.
     */
    private fun buildFeasible(
        definition: ContextualIntervalDefinition
    ): Map<String, List<TonalContext>> = definition.intervalIds.associateWith { intervalId ->
        val interval = SimpleInterval.ofId(intervalId)
        buildList {
            for (keyValue in definition.effectiveKeyValues) {
                for (tonality in definition.tonalities) {
                    if (definition.eligiblePairs(keyValue, tonality, interval).isEmpty()) {
                        continue
                    }
                    addAll(definition.eligibleConstructions(keyValue, tonality))
                }
            }
        }
    }

    private fun startNewBag() {
        if (started) cycleIndex += 1
        started = true
        val fresh = definition.intervalIds.toMutableList()
        for (index in fresh.lastIndex downTo 1) {
            val draw = labelsStream.nextInt(index + 1)
            val current = fresh[index]
            fresh[index] = fresh[draw]
            fresh[draw] = current
        }
        bag = fresh
        nextPositionInCycle = 0
    }

    private fun adapt(
        interval: SimpleInterval,
        construction: TonalContext,
        pair: ContextualIntervalPair,
        positionInCycle: Int
    ): ConcreteExercise {
        val keyValue = construction.keyPitchClass
        return ConcreteExercise(
            kind = TrainingKind.INTERVALS_IN_CONTEXT,
            provenance = GeneratorProvenance(
                providerId = TrainingKind.INTERVALS_IN_CONTEXT.id,
                providerVersion = 1,
                rngId = SeededRandomSource.RNG_ID,
                seed = activationSeed,
                generatedContextualInterval = GeneratedContextualInterval(
                    keyPitchClass = keyValue,
                    spelling = definition.spelling,
                    tonality = construction.tonality,
                    contextKind = definition.context,
                    anchorMidi = construction.anchor.value,
                    contextValues = construction.soundingValues,
                    intervalId = interval.id,
                    presentation = definition.presentation,
                    firstTargetMidi = pair.first.value,
                    secondTargetMidi = pair.second.value,
                    firstDegree = pair.firstDegree,
                    secondDegree = pair.secondDegree,
                    answerFormat = definition.answerFormat
                )
            ),
            categoryId = interval.id,
            sequenceIndex = sequenceIndex,
            cycle = CyclePosition(cycleIndex, positionInCycle),
            groups = construction.groups + targetGroups(pair),
            answer = AnswerDescriptor(
                voiceIds = definition.answerTokensFor(
                    interval,
                    pair.firstDegree,
                    pair.secondDegree
                ),
                display = AnswerDisplay.ContextualInterval(
                    intervalId = interval.id,
                    firstDegree = pair.firstDegree,
                    secondDegree = pair.secondDegree,
                    answerFormat = definition.answerFormat
                )
            )
        )
    }

    /**
     * The target groups of [pair] under the pair's presentation.
     *
     * Sequential presentations keep two groups in presented order, even for unison, so the example
     * window stays `2*d + g`. The harmonic presentation is one group in low-to-high order; a unison
     * deduplicates the repeated sounding pitch into one voice while the answer keeps both degrees.
     */
    private fun targetGroups(pair: ContextualIntervalPair): List<NoteGroup> =
        when (pair.presentation) {
            IntervalPresentation.ASCENDING,
            IntervalPresentation.DESCENDING -> listOf(
                NoteGroup(listOf(pair.first), GroupRole.TARGET),
                NoteGroup(listOf(pair.second), GroupRole.TARGET)
            )

            IntervalPresentation.HARMONIC -> listOf(
                NoteGroup(
                    listOf(pair.first, pair.second).distinct().sortedBy { it.value },
                    GroupRole.TARGET
                )
            )
        }

    private companion object {
        const val ACTIVATION_ORDINAL: Long = 0L
    }
}
