package com.thomas.passiveeartraining.training.provider

import com.thomas.passiveeartraining.training.IntervalDefinition
import com.thomas.passiveeartraining.training.IntervalExercise
import com.thomas.passiveeartraining.training.IntervalGenerator
import com.thomas.passiveeartraining.training.MissingVoiceDurationException
import com.thomas.passiveeartraining.training.SeededIntervalGenerator
import com.thomas.passiveeartraining.training.SeededIntervalGeneratorV2
import com.thomas.passiveeartraining.training.SeededRandomSource
import com.thomas.passiveeartraining.training.VoiceAssetId
import com.thomas.passiveeartraining.training.VoiceDurationCatalog
import com.thomas.passiveeartraining.training.recipe.RecipeTiming
import com.thomas.passiveeartraining.training.recipe.SequencePolicy
import kotlin.time.Duration

/**
 * The production interval provider: the accepted interval generator behind the shared contract.
 *
 * The generated [IntervalExercise] values and their draw order are completely unchanged; the
 * adapter only re-expresses them as provider-neutral groups and provenance. The legacy
 * [com.thomas.passiveeartraining.training.GeneratorIdentity] travels losslessly inside the
 * provenance so interval reproducibility is inspectable exactly as before (M1.2).
 */
class IntervalExerciseProvider : ExerciseProvider {
    override val kind: TrainingKind = TrainingKind.INTERVALS

    /** The newest generator version this provider implements (v1 is the historical draw order). */
    override val providerVersion: Int = 2

    override fun defaults(): ProviderConfig =
        ProviderConfig.Intervals(SequencePolicy(definition = IntervalDefinition.default()))

    override fun requiredVocabulary(config: ProviderConfig): Set<VoiceAssetId> {
        val policy = (config as ProviderConfig.Intervals).policy
        return policy.definition.selectedIntervalIds
            .map { VoiceAssetId(VoiceAssetId.ANSWER_ID_PREFIX + it) }
            .toSet()
    }

    override fun validate(
        config: ProviderConfig,
        catalog: VoiceDurationCatalog
    ): ProviderValidation {
        if (config !is ProviderConfig.Intervals) {
            return ProviderValidation.Invalid(
                listOf(FieldIssue("kind", "This provider only accepts interval configurations."))
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
        val longestAnswer = required.maxOf { catalog.durationOf(it) }
        // Content bound under the default timing profile; the shared recipe evaluator re-times
        // this per user timing edits. The melodic shape is the longer one (two note slots and the
        // melodic gap), the harmonic shape one note.
        val longestExample = maxOf(
            RecipeTiming().noteDuration * 2 + RecipeTiming().melodicGap,
            RecipeTiming().noteDuration
        )
        return ProviderValidation.Valid(
            config = config,
            categories = config.policy.definition.selectedIntervalIds
                .map { AnswerCategory(VoiceAssetId.ANSWER_ID_PREFIX + it) },
            maximumExampleDuration = longestExample,
            maximumAnswerDuration = longestAnswer,
            requiredAssets = required
        )
    }

    override fun createGenerator(config: ProviderConfig, seed: Long): ExerciseGenerator {
        val policy = (config as ProviderConfig.Intervals).policy
        return IntervalGeneratorAdapter(policy, SeededRandomSource(seed))
    }
}

/**
 * Adapts the legacy interval generators to the shared [ExerciseGenerator] contract.
 *
 * The adapter owns the running random stream: [reconfigure] rebuilds the internal generator on the
 * same stream, so a sequence edit continues the pairs already heard exactly as the legacy
 * [com.thomas.passiveeartraining.training.session.ConfiguredExerciseSource] did.
 */
private class IntervalGeneratorAdapter(
    initialPolicy: SequencePolicy,
    private val stream: SeededRandomSource
) : ExerciseGenerator {

    private var policy: SequencePolicy = initialPolicy
    private var delegate: IntervalGenerator = build(initialPolicy)

    override fun next(): ConcreteExercise = adapt(delegate.next())

    override fun reconfigure(config: ProviderConfig) {
        val updated = (config as ProviderConfig.Intervals).policy
        if (updated == policy) return
        policy = updated
        delegate = build(updated)
    }

    private fun build(policy: SequencePolicy): IntervalGenerator =
        if (policy.generatorVersion == 1) {
            SeededIntervalGenerator(policy.definition, stream)
        } else {
            SeededIntervalGeneratorV2(policy, stream)
        }

    private fun adapt(exercise: IntervalExercise): ConcreteExercise {
        val notes = exercise.notes
        val targetGroups: List<NoteGroup> = when (exercise.presentation) {
            com.thomas.passiveeartraining.music.IntervalPresentation.HARMONIC ->
                listOf(NoteGroup(notes.sortedBy { it.value }, GroupRole.TARGET))
            // Both melodic slots sound. A unison plays the same pitch twice, so the example
            // always fills its two-note window with audible notes rather than a rest.
            else -> notes.map { NoteGroup(listOf(it), GroupRole.TARGET) }
        }
        val voiceId = VoiceAssetId(VoiceAssetId.ANSWER_ID_PREFIX + exercise.interval.id)
        return ConcreteExercise(
            kind = TrainingKind.INTERVALS,
            provenance = GeneratorProvenance(
                providerId = TrainingKind.INTERVALS.id,
                providerVersion = exercise.identity.generatorVersion,
                rngId = exercise.identity.rngId,
                seed = exercise.identity.seed,
                legacyIntervalIdentity = exercise.identity
            ),
            categoryId = exercise.answerId,
            sequenceIndex = exercise.sequenceIndex,
            cycle = CyclePosition(exercise.cycleIndex, exercise.positionInCycle),
            groups = targetGroups,
            answer = AnswerDescriptor(
                voiceIds = listOf(voiceId),
                display = AnswerDisplay.Interval(exercise.interval.id)
            )
        )
    }
}
