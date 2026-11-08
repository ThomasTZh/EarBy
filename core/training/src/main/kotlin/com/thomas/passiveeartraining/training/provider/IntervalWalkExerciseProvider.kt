package com.thomas.passiveeartraining.training.provider

import com.thomas.passiveeartraining.music.MidiNote
import com.thomas.passiveeartraining.training.IntervalWalk
import com.thomas.passiveeartraining.training.IntervalWalkDefinition
import com.thomas.passiveeartraining.training.MissingVoiceDurationException
import com.thomas.passiveeartraining.training.SeededIntervalWalkGenerator
import com.thomas.passiveeartraining.training.SeededRandomSource
import com.thomas.passiveeartraining.training.VoiceAssetId
import com.thomas.passiveeartraining.training.VoiceDurationCatalog
import com.thomas.passiveeartraining.training.recipe.DurationFinish
import com.thomas.passiveeartraining.training.recipe.RecipeTiming
import com.thomas.passiveeartraining.training.recipe.SessionStopPolicy
import kotlin.time.Duration

/**
 * The finite interval-walk provider.
 *
 * A walk has one target note group per sounding note and a directed interval phrase for every
 * transition. The start note is drawn from the configured start range; the chain may leave that
 * range but never leaves the packaged MIDI 21-108 samples.
 */
class IntervalWalkExerciseProvider : ExerciseProvider {
    override val kind: TrainingKind = TrainingKind.INTERVAL_WALK
    override val providerVersion: Int = 1

    override fun defaults(): ProviderConfig =
        ProviderConfig.IntervalWalks(IntervalWalkDefinition.default())

    override fun requiredVocabulary(config: ProviderConfig): Set<VoiceAssetId> {
        val definition = (config as? ProviderConfig.IntervalWalks)?.definition
            ?: return emptySet()
        val intervals = definition.selectedIntervals.map { VoiceAssetId.forInterval(it.id) }
        val connectors = if (definition.selectedIntervals.any { it.semitones > 0 }) {
            listOf(VoiceVocabulary.ASCENDING.id, VoiceVocabulary.DESCENDING.id)
        } else {
            emptyList()
        }
        return (intervals + connectors).toSet()
    }

    override fun validate(
        config: ProviderConfig,
        catalog: VoiceDurationCatalog
    ): ProviderValidation {
        if (config !is ProviderConfig.IntervalWalks) {
            return ProviderValidation.Invalid(
                listOf(
                    FieldIssue("kind", "This provider only accepts interval-walk configurations.")
                )
            )
        }
        val definition = config.definition
        if (!definition.isFeasible()) {
            return ProviderValidation.Invalid(
                listOf(
                    FieldIssue(
                        "range",
                        "No complete interval walk fits the selected start range " +
                            "${definition.minimumNoteValue}..${definition.maximumNoteValue} " +
                            "with ${definition.direction.name.lowercase()} steps."
                    )
                )
            )
        }
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
        val example = maximumExampleDuration(definition)
        val answer = maximumAnswerDuration(definition, catalog)
        return ProviderValidation.Valid(
            config = config,
            categories = listOf(AnswerCategory("interval_walk")),
            maximumExampleDuration = example,
            maximumAnswerDuration = answer,
            requiredAssets = required
        )
    }

    override fun createGenerator(config: ProviderConfig, seed: Long): ExerciseGenerator {
        val definition = (config as ProviderConfig.IntervalWalks).definition
        return IntervalWalkGeneratorAdapter(definition, SeededRandomSource(seed), seed)
    }

    companion object {
        /** The longest example window at the supplied timing. */
        fun maximumExampleDuration(
            definition: IntervalWalkDefinition,
            timing: RecipeTiming = RecipeTiming()
        ): Duration = timing.noteDuration * (definition.length + 1) +
            timing.melodicGap * definition.length

        /** The longest composed answer for any walk under this definition. */
        fun maximumAnswerDuration(
            definition: IntervalWalkDefinition,
            catalog: VoiceDurationCatalog
        ): Duration {
            val phraseMax = definition.selectedIntervals.maxOf {
                catalog.durationOf(VoiceAssetId.forInterval(it.id))
            }
            val hasDirected = definition.selectedIntervals.any { it.semitones > 0 }
            val stepMax = if (hasDirected) {
                val connectorMax = maxOf(
                    catalog.durationOf(VoiceVocabulary.ASCENDING.id),
                    catalog.durationOf(VoiceVocabulary.DESCENDING.id)
                )
                connectorMax + phraseMax
            } else {
                phraseMax
            }
            val tokenCount = if (hasDirected) definition.length * 2 else definition.length
            return stepMax * definition.length +
                ExerciseCompiler.TOKEN_GAP * (tokenCount - 1)
        }

        /** The stop-policy explanation for a mode with no label cycle. */
        fun stopPolicyIssue(policy: SessionStopPolicy): FieldIssue? = when {
            policy is SessionStopPolicy.AfterCycle -> FieldIssue(
                "stopPolicy",
                "Interval walk has no label cycle, so it cannot stop after a cycle. " +
                    "Choose until stopped or a completed-item count."
            )

            policy is SessionStopPolicy.Duration &&
                policy.finish == DurationFinish.FINISH_CYCLE -> FieldIssue(
                "stopPolicy",
                "Interval walk has no label cycle to finish; the duration limit can stop " +
                    "immediately or after the current item."
            )

            else -> null
        }
    }
}

private class IntervalWalkGeneratorAdapter(
    initialDefinition: IntervalWalkDefinition,
    private val stream: SeededRandomSource,
    private val seed: Long
) : ExerciseGenerator {
    private var definition = initialDefinition
    private val generator = SeededIntervalWalkGenerator(initialDefinition, stream)

    override fun next(): ConcreteExercise = adapt(generator.next())

    override fun reconfigure(config: ProviderConfig) {
        val updated = (config as ProviderConfig.IntervalWalks).definition
        if (updated == definition) return
        definition = updated
        generator.reconfigure(updated)
    }

    private fun adapt(walk: IntervalWalk): ConcreteExercise {
        val tokens = definition.answerTokensForSteps(walk.steps)
        return ConcreteExercise(
            kind = TrainingKind.INTERVAL_WALK,
            provenance = GeneratorProvenance(
                providerId = TrainingKind.INTERVAL_WALK.id,
                providerVersion = 1,
                rngId = "java-random-48-v1",
                seed = seed,
                generatedIntervalWalk = GeneratedIntervalWalk(
                    startMidi = walk.start.value,
                    notes = walk.notes.map { it.value },
                    steps = walk.steps,
                    intervalIds = walk.intervalIds,
                    direction = definition.direction,
                    minimumStartMidi = definition.minimumNoteValue,
                    maximumStartMidi = definition.maximumNoteValue
                )
            ),
            categoryId = "interval_walk",
            sequenceIndex = walk.sequenceIndex,
            cycle = null,
            groups = walk.notes.map { NoteGroup(listOf(it), GroupRole.TARGET) },
            answer = AnswerDescriptor(
                voiceIds = tokens,
                display = AnswerDisplay.IntervalWalk(
                    definition.answerTextForSteps(walk.steps)
                )
            )
        )
    }
}
