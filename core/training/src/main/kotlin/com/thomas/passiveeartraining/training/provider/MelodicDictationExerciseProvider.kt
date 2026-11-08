package com.thomas.passiveeartraining.training.provider

import com.thomas.passiveeartraining.music.MidiNote
import com.thomas.passiveeartraining.music.Tonality
import com.thomas.passiveeartraining.training.MelodicAnswerFormat
import com.thomas.passiveeartraining.training.MelodicDictationDefinition
import com.thomas.passiveeartraining.training.MelodyGraph
import com.thomas.passiveeartraining.training.MelodyNode
import com.thomas.passiveeartraining.training.MissingVoiceDurationException
import com.thomas.passiveeartraining.training.SeededRandomSource
import com.thomas.passiveeartraining.training.VoiceAssetId
import com.thomas.passiveeartraining.training.VoiceDurationCatalog
import com.thomas.passiveeartraining.training.context.ContextBuilder
import com.thomas.passiveeartraining.training.context.TonalContext
import com.thomas.passiveeartraining.training.recipe.DurationFinish
import com.thomas.passiveeartraining.training.recipe.RecipeDefinition
import com.thomas.passiveeartraining.training.recipe.RecipeTiming
import com.thomas.passiveeartraining.training.recipe.SessionStopPolicy
import kotlin.time.Duration
import kotlin.time.Duration.Companion.nanoseconds

/**
 * M10.1/M10.2: the melodic-dictation provider.
 *
 * The mode has no label bag and no label cycle (D21). An activation uses exactly two derived child
 * streams, `mode-seed-v1` with ordinal 0: the context stream and the target stream. One exercise
 * consumes one context-stream draw over the feasible key/tonality/anchor constructions in the M8
 * canonical order (key pitch class ascending, tonality ordinal, anchor ascending), then one
 * target-stream draw over the eligible starts of the drawn graph, then one target-stream draw for
 * every later phrase position over that position's outgoing candidates, listed ascending by MIDI
 * and bucketed by the direction weights of the definition's bias. The draw is one
 * `nextInt(totalWeight)` even when the candidate list is a singleton, so the frozen v1 draw count
 * is `1 + phraseLength` per exercise. There is no rejection, retry, fix-up note or hidden counter.
 *
 * A phrase is always complete: every drawn position keeps an outgoing candidate that can still
 * reach an allowed ending, so the last note satisfies the requested end degree and no note is
 * ever changed afterwards. The complete M8 context precedes the phrase in every example, and the
 * target is a sequence of one-note TARGET groups in played order.
 *
 * Reconfiguration continues both streams, so an edited setup is heard from the next draw onwards
 * and a same-seed activation reproduces its phrase order independently of coroutine scheduling.
 * The sequence index stays monotonic across an edit.
 */
class MelodicDictationExerciseProvider : ExerciseProvider {
    override val kind: TrainingKind = TrainingKind.MELODIC_DICTATION

    override val providerVersion: Int = 1

    override fun defaults(): ProviderConfig =
        ProviderConfig.MelodicDictation(MelodicDictationDefinition.default())

    override fun requiredVocabulary(config: ProviderConfig): Set<VoiceAssetId> {
        val definition = (config as ProviderConfig.MelodicDictation).definition
        val tokens = linkedSetOf<VoiceAssetId>()
        for (keyValue in definition.effectiveKeyValues) {
            for (tonality in definition.tonalities) {
                val graph = definition.graphFor(keyValue, tonality)
                if (!graph.isFeasible) continue
                if (definition.eligibleConstructions(keyValue, tonality).isEmpty()) continue
                when (definition.answerFormat) {
                    MelodicAnswerFormat.DEGREE_SEQUENCE -> graph.reachableNodes().forEach { node ->
                        tokens += VoiceVocabulary.degreeNumberEntry(node.degree).id
                    }

                    MelodicAnswerFormat.DIRECTED_INTERVAL_SEQUENCE ->
                        graph.reachableSteps().forEach { step ->
                            tokens += MelodicDictationDefinition.intervalTokensForStep(step)
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
        if (config !is ProviderConfig.MelodicDictation) {
            return ProviderValidation.Invalid(
                listOf(
                    FieldIssue(
                        "kind",
                        "This provider only accepts melodic-dictation configurations."
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
                val graph = definition.graphFor(keyValue, tonality)
                if (!graph.isFeasible) {
                    infeasible += pathIssue(definition, keyValue, tonality, graph)
                } else if (definition.eligibleConstructions(keyValue, tonality).isEmpty()) {
                    infeasible += contextIssue(definition, keyValue, tonality)
                }
            }
        }
        if (infeasible.isNotEmpty()) {
            return ProviderValidation.Invalid(infeasible)
        }
        return ProviderValidation.Valid(
            config = config,
            categories = listOf(AnswerCategory(categoryId(definition.answerFormat))),
            maximumExampleDuration = maximumExampleDuration(definition),
            maximumAnswerDuration = maximumAnswerDuration(definition, catalog),
            requiredAssets = required
        )
    }

    override fun createGenerator(config: ProviderConfig, seed: Long): ExerciseGenerator {
        val definition = (config as ProviderConfig.MelodicDictation).definition
        return MelodicDictationGeneratorAdapter(definition, seed)
    }

    companion object {

        /** Stable answer category id of one format, for example `melodic.degree_sequence`. */
        fun categoryId(format: MelodicAnswerFormat): String = "melodic.${format.name.lowercase()}"

        /**
         * The longest example window over the selected configuration under [timing].
         *
         * The example is the complete M8 context, the context gap and the phrase: `L*d + (L-1)*g`.
         * The gap is the definition's own [MelodicDictationDefinition.contextGap]; the phrase note
         * length and gap come from [timing], exactly like the M8, M9A and M9B bounds, and the setup
         * layer re-times those two values with the edited controls before judging the 60 s cap.
         */
        fun maximumExampleDuration(
            definition: MelodicDictationDefinition,
            timing: RecipeTiming = RecipeTiming()
        ): Duration = ContextBuilder.duration(timing, definition.context) +
            definition.contextGap +
            timing.noteDuration * definition.phraseLength +
            timing.melodicGap * (definition.phraseLength - 1)

        /**
         * The exact maximum reachable answer over every feasible key and tonality (M10.3).
         *
         * Degree answers attach each reachable node's clip cost to that node, so the maximum is
         * the longest complete phrase of node clips plus one exactly-80 ms gap between adjacent
         * clips. Directed-interval answers attach the complete per-edge phrase cost to each
         * transition (two clips for a non-zero step, one for a zero step) and carry the clip count
         * inside that cost, so the connection gap is explicit: the bound is the maximum edge-path
         * cost minus one token gap. Every graph is evaluated; an infeasible key and tonality
         * contributes nothing.
         *
         * The result is measured with the injected catalogue and uses checked integer arithmetic.
         * It is Duration.ZERO only when no selected key and tonality has a complete phrase, which
         * the provider's validation reports as invalid before a session can start.
         */
        fun maximumAnswerDuration(
            definition: MelodicDictationDefinition,
            catalog: VoiceDurationCatalog
        ): Duration {
            var best = Long.MIN_VALUE
            for (keyValue in definition.effectiveKeyValues) {
                for (tonality in definition.tonalities) {
                    val graph = definition.graphFor(keyValue, tonality)
                    if (!graph.isFeasible) continue
                    val path = when (definition.answerFormat) {
                        MelodicAnswerFormat.DEGREE_SEQUENCE ->
                            graph.maximumNodePathCost { node ->
                                voiceNanos(
                                    catalog,
                                    VoiceVocabulary.degreeNumberEntry(node.degree).id
                                )
                            }

                        MelodicAnswerFormat.DIRECTED_INTERVAL_SEQUENCE ->
                            graph.maximumEdgePathCost { from, to ->
                                intervalPhraseNanos(catalog, to.value - from.value)
                            }
                    } ?: continue
                    val answer = when (definition.answerFormat) {
                        MelodicAnswerFormat.DEGREE_SEQUENCE -> Math.addExact(
                            path,
                            Math.multiplyExact(
                                ExerciseCompiler.TOKEN_GAP.inWholeNanoseconds,
                                (definition.phraseLength - 1).toLong()
                            )
                        )

                        MelodicAnswerFormat.DIRECTED_INTERVAL_SEQUENCE ->
                            Math.subtractExact(
                                path,
                                ExerciseCompiler.TOKEN_GAP.inWholeNanoseconds
                            )
                    }
                    if (answer > best) best = answer
                }
            }
            return if (best == Long.MIN_VALUE) Duration.ZERO else best.nanoseconds
        }

        /**
         * The longest complete exercise under [recipe]: its example cost plus its compatible
         * answer cost through the shared recipe, repeat and final-tail calculation.
         *
         * The example cost depends only on the definition's one context kind and the recipe
         * timing, so combining it with the maximum answer over feasible keys and tonalities is
         * exactly the maximum over compatible pairs. This is the number the 60 s cap judges.
         */
        fun maximumExerciseDuration(
            definition: MelodicDictationDefinition,
            recipe: RecipeDefinition,
            catalog: VoiceDurationCatalog
        ): Duration = recipe.totalDurationOf(
            maximumExampleDuration(definition, recipe.timing),
            maximumAnswerDuration(definition, catalog)
        )

        /**
         * The cycle stop-policy issue for [policy], or null when the policy is available here.
         *
         * The mode has no label cycle (D21): AfterCycle has nothing to stop after and a
         * FINISH_CYCLE drain has no cycle to finish, so the setup layer reports `stopPolicy`
         * instead of accepting a policy the generator cannot honour. Until-stopped, completed-item
         * counts and the immediate or current-item duration finishes remain available.
         */
        fun stopPolicyIssue(policy: SessionStopPolicy): FieldIssue? = when {
            policy is SessionStopPolicy.AfterCycle -> FieldIssue(
                "stopPolicy",
                "Melodic dictation has no label cycle, so it cannot stop after a cycle. " +
                    "Choose until stopped or a completed-item count."
            )

            policy is SessionStopPolicy.Duration &&
                policy.finish == DurationFinish.FINISH_CYCLE -> FieldIssue(
                "stopPolicy",
                "Melodic dictation has no label cycle to finish; the duration limit can stop " +
                    "immediately or after the current item."
            )

            else -> null
        }

        /**
         * The field issue for one selected key and tonality with no complete phrase.
         *
         * The report names the key, the range, the length, both endpoint rules, the effective
         * steps and the maximum leap, and adds one concrete suggestion that would make a phrase
         * possible without silently relaxing anything.
         */
        internal fun pathIssue(
            definition: MelodicDictationDefinition,
            keyValue: Int,
            tonality: Tonality,
            graph: MelodyGraph
        ): FieldIssue {
            val keyName = definition.keyDisplayName(keyValue, tonality)
            val range = "${definition.minimumNoteValue}..${definition.maximumNoteValue}"
            if (graph.nodes.isEmpty()) {
                return FieldIssue(
                    "range",
                    "The range $range contains no $keyName scale note, so no phrase can start " +
                        "or end there."
                )
            }
            if (graph.transitions.isEmpty()) {
                return FieldIssue(
                    "leap",
                    "The allowed steps ${definition.allowedSteps} and the maximum leap " +
                        "${definition.maximumLeap} share no usable step, so no $keyName phrase " +
                        "can move; allow a step inside the leap."
                )
            }
            val missingStart = definition.startDegree
                ?.takeIf { degree -> graph.nodes.none { it.degree == degree } }
            if (missingStart != null) {
                return FieldIssue(
                    "endpoints",
                    "The range $range contains no $keyName degree $missingStart note for the " +
                        "start of the phrase."
                )
            }
            val missingEnd = definition.endDegree
                ?.takeIf { degree -> graph.nodes.none { it.degree == degree } }
            if (missingEnd != null) {
                return FieldIssue(
                    "endpoints",
                    "The range $range contains no $keyName degree $missingEnd note for the " +
                        "end of the phrase."
                )
            }
            val startText = definition.startDegree?.toString() ?: "any"
            val endText = definition.endDegree?.toString() ?: "any"
            val suggestion = suggestion(definition, keyValue, tonality)
            return FieldIssue(
                suggestion.first,
                "No ${definition.phraseLength}-note $keyName phrase inside $range starts at " +
                    "degree $startText and ends at degree $endText with steps " +
                    "${graph.transitions} and a maximum leap of ${definition.maximumLeap}. " +
                    suggestion.second
            )
        }

        /** The field issue for a feasible graph whose contexts all leave the range. */
        internal fun contextIssue(
            definition: MelodicDictationDefinition,
            keyValue: Int,
            tonality: Tonality
        ): FieldIssue {
            val keyName = definition.keyDisplayName(keyValue, tonality)
            val range = "${definition.minimumNoteValue}..${definition.maximumNoteValue}"
            return FieldIssue(
                "range",
                "No $keyName ${definition.context.label} context fits the selected range " +
                    "$range for the melodic phrase; the context and every phrase note must all " +
                    "sound inside the range."
            )
        }

        /**
         * One concrete suggestion for an infeasible key and tonality, as `(field, message)`.
         *
         * Each candidate is a bounded what-if check on a variant graph; the first that would make
         * a complete phrase possible is reported, and the controls themselves are never relaxed.
         */
        private fun suggestion(
            definition: MelodicDictationDefinition,
            keyValue: Int,
            tonality: Tonality
        ): Pair<String, String> {
            fun variantFeasible(
                steps: List<Int> = definition.allowedSteps,
                leap: Int = definition.maximumLeap,
                length: Int = definition.phraseLength,
                start: Int? = definition.startDegree,
                end: Int? = definition.endDegree,
                minimum: Int = definition.minimumNoteValue,
                maximum: Int = definition.maximumNoteValue
            ): Boolean = variant(
                definition = definition,
                steps = steps,
                leap = leap,
                length = length,
                start = start,
                end = end,
                minimum = minimum,
                maximum = maximum
            ).graphFor(keyValue, tonality).isFeasible

            val zeroHelps = 0 !in definition.allowedSteps &&
                variantFeasible(steps = (definition.allowedSteps + 0).sorted())
            val octaveHelps = 12 !in definition.allowedSteps &&
                variantFeasible(
                    steps = (definition.allowedSteps + 12).sorted(),
                    leap = maxOf(definition.maximumLeap, 12)
                )
            val stepsHelp = variantFeasible(steps = (-12..12).toList(), leap = 12)
            val endpointsHelp = (
                definition.startDegree != null ||
                    definition.endDegree != null
                ) && variantFeasible(start = null, end = null)
            val lengthHelps = MelodicDictationDefinition.PHRASE_LENGTH_RANGE.any { length ->
                length != definition.phraseLength && variantFeasible(length = length)
            }
            val rangeHelps = variantFeasible(minimum = 0, maximum = MidiNote.MAX_VALUE)
            return when {
                zeroHelps -> "steps" to "Allow step 0 so a note can repeat."
                octaveHelps ->
                    "steps" to "Allow the octave step 12 with a maximum leap of at least 12."
                stepsHelp -> "steps" to "Allow more of the steps inside the maximum leap."
                endpointsHelp -> "endpoints" to "Allow any start or end degree."
                lengthHelps -> "length" to "Choose a phrase length that can complete."
                rangeHelps -> "range" to "Widen the selected range."
                else -> (
                    if (definition.startDegree != null || definition.endDegree != null) {
                        "endpoints"
                    } else {
                        "steps"
                    }
                    ) to
                    "Review the phrase length, endpoints and step selection; no combination " +
                    "inside the current controls completes a phrase."
            }
        }

        /** A definition with replaced controls, for a bounded feasibility what-if check. */
        private fun variant(
            definition: MelodicDictationDefinition,
            steps: List<Int>,
            leap: Int,
            length: Int,
            start: Int?,
            end: Int?,
            minimum: Int,
            maximum: Int
        ): MelodicDictationDefinition = MelodicDictationDefinition.of(
            keyValues = definition.keyValues,
            tonalities = definition.tonalities,
            context = definition.context,
            contextGap = definition.contextGap,
            minimumNote = MidiNote.of(minimum),
            maximumNote = MidiNote.of(maximum),
            phraseLength = length,
            startDegree = start,
            endDegree = end,
            allowedSteps = steps,
            maximumLeap = leap,
            bias = definition.bias,
            answerFormat = definition.answerFormat,
            spelling = definition.spelling
        )

        /** The measured duration of one answer clip in nanoseconds. */
        private fun voiceNanos(catalog: VoiceDurationCatalog, id: VoiceAssetId): Long =
            catalog.durationOf(id).inWholeNanoseconds

        /**
         * The complete cost of one transition phrase: every clip's duration plus one token gap per
         * clip, so a later `- one gap` turns the path total into `clips - 1` connection gaps.
         */
        private fun intervalPhraseNanos(catalog: VoiceDurationCatalog, step: Int): Long {
            val tokens = MelodicDictationDefinition.intervalTokensForStep(step)
            var total = 0L
            for (token in tokens) {
                total = Math.addExact(total, voiceNanos(catalog, token))
            }
            return Math.addExact(
                total,
                Math.multiplyExact(
                    ExerciseCompiler.TOKEN_GAP.inWholeNanoseconds,
                    tokens.size.toLong()
                )
            )
        }
    }
}

/**
 * Owns the two derived draw streams of one melodic-dictation activation.
 *
 * The activation seed is the already-resolved seed from [ModeSetup]; the adapter derives the
 * context and target children with ordinal 0 and consults no other stream. A configuration edit
 * rebuilds the feasible context list and the graphs, continues both streams and leaves the
 * sequence index monotonic. There is no bag to discard, because the mode has no label cycle.
 */
private class MelodicDictationGeneratorAdapter(
    initialDefinition: MelodicDictationDefinition,
    private val activationSeed: Long
) : ExerciseGenerator {

    private val contextStream = SeededRandomSource(
        ModeSeedDerivation.derive(
            activationSeed,
            TrainingKind.MELODIC_DICTATION,
            ACTIVATION_ORDINAL,
            ModeSeedDerivation.CONTEXT
        )
    )
    private val targetStream = SeededRandomSource(
        ModeSeedDerivation.derive(
            activationSeed,
            TrainingKind.MELODIC_DICTATION,
            ACTIVATION_ORDINAL,
            ModeSeedDerivation.TARGET
        )
    )

    private var definition: MelodicDictationDefinition = initialDefinition
    private var feasibleContexts: List<TonalContext> = buildFeasibleContexts(initialDefinition)
    private var graphs: Map<Pair<Int, Tonality>, MelodyGraph> = buildGraphs(initialDefinition)
    private var sequenceIndex: Int = 0

    init {
        require(feasibleContexts.isNotEmpty()) {
            "Every selected key and tonality needs a feasible context and a complete phrase"
        }
    }

    override fun next(): ConcreteExercise {
        val construction = feasibleContexts[contextStream.nextInt(feasibleContexts.size)]
        val graph = graphs.getValue(construction.keyPitchClass to construction.tonality)
        val starts = graph.eligibleStarts
        var current = starts[targetStream.nextInt(starts.size)]
        val notes = ArrayList<MelodyNote>(definition.phraseLength)
        notes += MelodyNote(current.value, current.degree)
        for (position in 1 until definition.phraseLength) {
            val candidates = graph.candidatesForNext(position, current)
            current = pickWeighted(candidates, current)
            notes += MelodyNote(current.value, current.degree)
        }
        val steps = notes.zipWithNext { from, to -> to.midi - from.midi }
        val exercise = adapt(construction, notes, steps)
        sequenceIndex += 1
        return exercise
    }

    override fun reconfigure(config: ProviderConfig) {
        val updated = (config as ProviderConfig.MelodicDictation).definition
        if (updated == definition) return
        val updatedContexts = buildFeasibleContexts(updated)
        require(updatedContexts.isNotEmpty()) {
            "Every selected key and tonality needs a feasible context and a complete phrase"
        }
        definition = updated
        feasibleContexts = updatedContexts
        graphs = buildGraphs(updated)
        // There is no label bag to drop: the edit continues both streams from the next draw.
    }

    /**
     * Every eligible context of every usable key and tonality.
     *
     * Iteration is the documented M8 canonical candidate order: key pitch class ascending,
     * tonality ordinal, then anchor ascending inside
     * [MelodicDictationDefinition.eligibleConstructions]. A key and tonality contribute only when
     * its graph has a complete phrase, so a draw can never select a context with no melody.
     */
    private fun buildFeasibleContexts(definition: MelodicDictationDefinition): List<TonalContext> =
        buildList {
            for (keyValue in definition.effectiveKeyValues) {
                for (tonality in definition.tonalities) {
                    if (!definition.graphFor(keyValue, tonality).isFeasible) continue
                    addAll(definition.eligibleConstructions(keyValue, tonality))
                }
            }
        }

    /** Every feasible graph by key and tonality, key ascending then tonality ordinal. */
    private fun buildGraphs(
        definition: MelodicDictationDefinition
    ): Map<Pair<Int, Tonality>, MelodyGraph> = buildMap {
        for (keyValue in definition.effectiveKeyValues) {
            for (tonality in definition.tonalities) {
                val graph = definition.graphFor(keyValue, tonality)
                if (graph.isFeasible) put(keyValue to tonality, graph)
            }
        }
    }

    /**
     * One weighted draw over [candidates], listed ascending by MIDI.
     *
     * Each candidate's integer bucket is the sign weight of the step from the current note [from]
     * to it under the definition's bias; the cumulative buckets cover `1..totalWeight` and one
     * `nextInt(totalWeight)` selects the bucket. A singleton list still consumes exactly one draw.
     */
    private fun pickWeighted(candidates: List<MelodyNode>, from: MelodyNode): MelodyNode {
        var total = 0
        for (candidate in candidates) {
            total = Math.addExact(total, definition.bias.weightOf(from.stepTo(candidate)))
        }
        val draw = targetStream.nextInt(total)
        var cumulative = 0
        for (candidate in candidates) {
            cumulative += definition.bias.weightOf(from.stepTo(candidate))
            if (draw < cumulative) return candidate
        }
        throw IllegalStateException("The weighted buckets did not cover draw $draw")
    }

    private fun adapt(
        construction: TonalContext,
        notes: List<MelodyNote>,
        steps: List<Int>
    ): ConcreteExercise {
        val tonality = construction.tonality
        val tokens = definition.answerTokensFor(notes, steps)
        val text = definition.answerTextFor(notes, steps)
        return ConcreteExercise(
            kind = TrainingKind.MELODIC_DICTATION,
            provenance = GeneratorProvenance(
                providerId = TrainingKind.MELODIC_DICTATION.id,
                providerVersion = 1,
                rngId = SeededRandomSource.RNG_ID,
                seed = activationSeed,
                generatedMelody = GeneratedMelody(
                    keyPitchClass = construction.keyPitchClass,
                    spelling = definition.spelling,
                    tonality = tonality,
                    context = definition.context,
                    anchorMidi = construction.anchor.value,
                    contextNotes = construction.soundingValues,
                    notes = notes,
                    steps = steps,
                    bias = definition.bias,
                    answerFormat = definition.answerFormat
                )
            ),
            categoryId = MelodicDictationExerciseProvider.categoryId(definition.answerFormat),
            sequenceIndex = sequenceIndex,
            // The mode owns no label cycle (D21), so a null cycle position is the honest value.
            cycle = null,
            groups = construction.groups + notes.map { note ->
                NoteGroup(listOf(MidiNote.of(note.midi)), GroupRole.TARGET)
            },
            answer = AnswerDescriptor(
                voiceIds = tokens,
                display = AnswerDisplay.Melody(text)
            )
        )
    }

    private companion object {
        const val ACTIVATION_ORDINAL: Long = 0L
    }
}
