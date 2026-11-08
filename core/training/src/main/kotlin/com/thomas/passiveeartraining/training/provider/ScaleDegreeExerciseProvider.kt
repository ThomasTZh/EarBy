package com.thomas.passiveeartraining.training.provider

import com.thomas.passiveeartraining.music.MidiNote
import com.thomas.passiveeartraining.music.Tonality
import com.thomas.passiveeartraining.training.MissingVoiceDurationException
import com.thomas.passiveeartraining.training.ScaleDegreeDefinition
import com.thomas.passiveeartraining.training.SeededRandomSource
import com.thomas.passiveeartraining.training.VoiceAssetId
import com.thomas.passiveeartraining.training.VoiceDurationCatalog
import com.thomas.passiveeartraining.training.context.ContextBuilder
import com.thomas.passiveeartraining.training.context.TonalContext
import com.thomas.passiveeartraining.training.recipe.RecipeTiming
import kotlin.time.Duration

/**
 * M8.1/M8.2: the scale-degree provider.
 *
 * Categories are the selected degrees 1..7. The label bag shuffles those degrees with the SC3
 * contract; for each drawn degree one context-stream draw selects an eligible
 * key/tonality/anchor/context construction uniformly (even when only one is eligible), then one
 * target-stream draw selects an eligible target note of that construction uniformly. The separate
 * child streams come from `mode-seed-v1` with ordinal 0 and the SC3 labels, so the context of a
 * degree can never consume the target's randomness and vice versa.
 *
 * The generator is finite and reproducible: every construction is enumerated before the first
 * draw, impossible roots are never drawn, and there is no rejection or retry loop. Context groups
 * always precede the single target group, and required vocabulary is exactly the answer tokens of
 * the reachable key/tonality/degree combinations; the context itself is never spoken.
 */
class ScaleDegreeExerciseProvider : ExerciseProvider {
    override val kind: TrainingKind = TrainingKind.SCALE_DEGREES

    override val providerVersion: Int = 1

    override fun defaults(): ProviderConfig =
        ProviderConfig.ScaleDegrees(ScaleDegreeDefinition.default())

    override fun requiredVocabulary(config: ProviderConfig): Set<VoiceAssetId> {
        val definition = (config as ProviderConfig.ScaleDegrees).definition
        val tokens = linkedSetOf<VoiceAssetId>()
        for (keyValue in definition.effectiveKeyValues) {
            for (tonality in definition.tonalities) {
                for (degree in definition.degrees) {
                    if (definition.supports(keyValue, tonality, degree)) {
                        tokens += definition.answerTokensFor(keyValue, tonality, degree)
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
        if (config !is ProviderConfig.ScaleDegrees) {
            return ProviderValidation.Invalid(
                listOf(
                    FieldIssue("kind", "This provider only accepts scale-degree configurations.")
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
                for (degree in definition.degrees) {
                    if (!definition.supports(keyValue, tonality, degree)) {
                        infeasible += noCompletionIssue(definition, keyValue, tonality, degree)
                    }
                }
            }
        }
        if (infeasible.isNotEmpty()) {
            return ProviderValidation.Invalid(infeasible)
        }
        return ProviderValidation.Valid(
            config = config,
            categories = definition.degrees.map { AnswerCategory(categoryId(it)) },
            maximumExampleDuration = maximumExampleDuration(definition),
            maximumAnswerDuration = maximumAnswerDuration(definition, catalog),
            requiredAssets = required
        )
    }

    override fun createGenerator(config: ProviderConfig, seed: Long): ExerciseGenerator {
        val definition = (config as ProviderConfig.ScaleDegrees).definition
        return ScaleDegreeGeneratorAdapter(definition, seed)
    }

    /** Stable category ids and the provider's content bounds. */
    companion object {

        /** Stable answer-category id: the target degree number, for example `degree.5`. */
        fun categoryId(degree: Int): String = "degree.$degree"

        /**
         * The longest example window: the complete context, the context gap and the target note.
         *
         * The gap is the definition's own [ScaleDegreeDefinition.contextGap]; the setup layer
         * mirrors it into [RecipeTiming.contextGap], and the shared compiler consumes that timing
         * value, so this bound is measured with the same numbers playback uses.
         */
        fun maximumExampleDuration(
            definition: ScaleDegreeDefinition,
            timing: RecipeTiming = RecipeTiming()
        ): Duration = ContextBuilder.duration(timing, definition.context) +
            definition.contextGap +
            timing.noteDuration

        /**
         * The longest reachable answer: the longest token sequence of any key/tonality/degree
         * combination with a completion, including the exactly-80 ms inter-token gap.
         */
        fun maximumAnswerDuration(
            definition: ScaleDegreeDefinition,
            catalog: VoiceDurationCatalog
        ): Duration {
            val sequences = buildList {
                for (keyValue in definition.effectiveKeyValues) {
                    for (tonality in definition.tonalities) {
                        for (degree in definition.degrees) {
                            if (definition.supports(keyValue, tonality, degree)) {
                                add(definition.answerTokensFor(keyValue, tonality, degree))
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

        /** The field issue for a selected combination with no eligible construction. */
        private fun noCompletionIssue(
            definition: ScaleDegreeDefinition,
            keyValue: Int,
            tonality: Tonality,
            degree: Int
        ): FieldIssue {
            val keyName = definition.keyDisplayName(keyValue, tonality)
            val range = "${definition.minimumNoteValue}..${definition.maximumNoteValue}"
            return if (definition.targetNotesFor(keyValue, tonality, degree).isEmpty()) {
                FieldIssue(
                    "range",
                    "No $keyName degree $degree note sounds inside the selected range $range."
                )
            } else {
                FieldIssue(
                    "range",
                    "No $keyName ${definition.context.label} context fits the selected range " +
                        "$range for degree $degree; the context and the target must all sound " +
                        "inside the range."
                )
            }
        }
    }
}

/**
 * Owns the three derived draw streams of one scale-degree activation.
 *
 * The activation seed is the already-resolved seed from [ModeSetup]; the adapter derives the
 * SC3 labels/context/target children with ordinal 0, so recording the activation seed and the
 * normalized definition reconstructs every draw. A configuration edit discards the remainder of
 * the current label bag and continues all three streams; the sequence index stays monotonic.
 */
private class ScaleDegreeGeneratorAdapter(
    initialDefinition: ScaleDegreeDefinition,
    private val activationSeed: Long
) : ExerciseGenerator {

    private val labelsStream = SeededRandomSource(
        ModeSeedDerivation.derive(
            activationSeed,
            TrainingKind.SCALE_DEGREES,
            ACTIVATION_ORDINAL,
            ModeSeedDerivation.LABELS
        )
    )
    private val contextStream = SeededRandomSource(
        ModeSeedDerivation.derive(
            activationSeed,
            TrainingKind.SCALE_DEGREES,
            ACTIVATION_ORDINAL,
            ModeSeedDerivation.CONTEXT
        )
    )
    private val targetStream = SeededRandomSource(
        ModeSeedDerivation.derive(
            activationSeed,
            TrainingKind.SCALE_DEGREES,
            ACTIVATION_ORDINAL,
            ModeSeedDerivation.TARGET
        )
    )

    private var definition: ScaleDegreeDefinition = initialDefinition
    private var feasible: Map<Int, List<TonalContext>> = buildFeasible(initialDefinition)
    private var bag: MutableList<Int> = mutableListOf()
    private var nextPositionInCycle: Int = 0
    private var started: Boolean = false
    private var cycleIndex: Int = 0
    private var sequenceIndex: Int = 0

    init {
        require(feasible.values.none { it.isEmpty() }) {
            "Every enabled degree must have a feasible context and target"
        }
    }

    override fun next(): ConcreteExercise {
        if (nextPositionInCycle == bag.size) {
            startNewBag()
        }
        val degree = bag[nextPositionInCycle]
        val constructions = feasible.getValue(degree)
        val construction = constructions[contextStream.nextInt(constructions.size)]
        val targets = definition.targetNotesFor(
            construction.keyPitchClass,
            construction.tonality,
            degree
        )
        val target = targets[targetStream.nextInt(targets.size)]
        val position = nextPositionInCycle
        nextPositionInCycle += 1
        val exercise = adapt(degree, construction, target, position)
        sequenceIndex += 1
        return exercise
    }

    override fun reconfigure(config: ProviderConfig) {
        val updated = (config as ProviderConfig.ScaleDegrees).definition
        if (updated == definition) return
        val updatedFeasible = buildFeasible(updated)
        require(updatedFeasible.values.none { it.isEmpty() }) {
            "Every enabled degree must have a feasible context and target"
        }
        definition = updated
        feasible = updatedFeasible
        // The remainder of the old bag no longer describes the edited setup; the streams continue.
        bag = mutableListOf()
        nextPositionInCycle = 0
    }

    /**
     * Every eligible construction of every selected degree.
     *
     * Iteration is the documented candidate order: key pitch class ascending, tonality ordinal,
     * then anchor ascending (inside [ScaleDegreeDefinition.eligibleConstructions]); the flattening
     * therefore needs no second sort.
     */
    private fun buildFeasible(definition: ScaleDegreeDefinition): Map<Int, List<TonalContext>> =
        definition.degrees.associateWith { degree ->
            buildList {
                for (keyValue in definition.effectiveKeyValues) {
                    for (tonality in definition.tonalities) {
                        addAll(definition.eligibleConstructions(keyValue, tonality, degree))
                    }
                }
            }
        }

    private fun startNewBag() {
        if (started) cycleIndex += 1
        started = true
        val fresh = definition.degrees.toMutableList()
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
        degree: Int,
        construction: TonalContext,
        target: MidiNote,
        positionInCycle: Int
    ): ConcreteExercise {
        val groups = construction.groups + NoteGroup(listOf(target), GroupRole.TARGET)
        val keyValue = construction.keyPitchClass
        return ConcreteExercise(
            kind = TrainingKind.SCALE_DEGREES,
            provenance = GeneratorProvenance(
                providerId = TrainingKind.SCALE_DEGREES.id,
                providerVersion = 1,
                rngId = SeededRandomSource.RNG_ID,
                seed = activationSeed,
                generatedScaleDegree = GeneratedScaleDegree(
                    keyPitchClass = keyValue,
                    spelling = definition.spelling,
                    tonality = construction.tonality,
                    contextKind = definition.context,
                    anchorMidi = construction.anchor.value,
                    contextValues = construction.soundingValues,
                    degree = degree,
                    targetMidi = target.value,
                    answerFormat = definition.answerFormat
                )
            ),
            categoryId = ScaleDegreeExerciseProvider.categoryId(degree),
            sequenceIndex = sequenceIndex,
            cycle = CyclePosition(cycleIndex, positionInCycle),
            groups = groups,
            answer = AnswerDescriptor(
                voiceIds = definition.answerTokensFor(keyValue, construction.tonality, degree),
                display = AnswerDisplay.ScaleDegree(
                    degree = degree,
                    tonality = construction.tonality,
                    format = definition.answerFormat,
                    pitchClass = definition.degreePitchClass(
                        keyValue,
                        construction.tonality,
                        degree
                    ),
                    spelling = definition.spelling
                )
            )
        )
    }

    private companion object {
        const val ACTIVATION_ORDINAL: Long = 0L
    }
}
