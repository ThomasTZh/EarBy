package com.thomas.passiveeartraining.training.provider

import com.thomas.passiveeartraining.music.MidiNote
import com.thomas.passiveeartraining.music.ScaleDirection
import com.thomas.passiveeartraining.music.ScaleKind
import com.thomas.passiveeartraining.music.ScaleTraversal
import com.thomas.passiveeartraining.music.SpelledPitch
import com.thomas.passiveeartraining.music.SpellingPreference
import com.thomas.passiveeartraining.music.UnrepresentableSpellingException
import com.thomas.passiveeartraining.training.MissingVoiceDurationException
import com.thomas.passiveeartraining.training.PlayableRange
import com.thomas.passiveeartraining.training.ScaleDefinition
import com.thomas.passiveeartraining.training.SeededRandomSource
import com.thomas.passiveeartraining.training.VoiceAssetId
import com.thomas.passiveeartraining.training.VoiceDurationCatalog
import com.thomas.passiveeartraining.training.recipe.RecipeTiming
import kotlin.time.Duration

/**
 * M6.1: the scale provider.
 *
 * Categories are the selected canonical scales in catalog order. A bag shuffles those scales with
 * the SC3 contract and each drawn scale picks one feasible lower tonic uniformly; the answer is
 * the single canonical name, so a scale answer never composes alternatives. The generator is
 * finite and reproducible: no rejection sampling, no hidden state outside the injected stream. A
 * complete octave is never truncated: a range that cannot fit a selected scale fails validation
 * instead.
 */
class ScaleExerciseProvider : ExerciseProvider {
    override val kind: TrainingKind = TrainingKind.SCALES

    override val providerVersion: Int = 1

    override fun defaults(): ProviderConfig = ProviderConfig.Scales(ScaleDefinition.default())

    override fun requiredVocabulary(config: ProviderConfig): Set<VoiceAssetId> {
        val definition = (config as ProviderConfig.Scales).definition
        val tokens = linkedSetOf<VoiceAssetId>()
        definition.selectedScales.forEach {
            tokens += VoiceVocabulary.scaleEntry(it).id
        }
        return tokens
    }

    override fun validate(
        config: ProviderConfig,
        catalog: VoiceDurationCatalog
    ): ProviderValidation {
        if (config !is ProviderConfig.Scales) {
            return ProviderValidation.Invalid(
                listOf(FieldIssue("kind", "This provider only accepts scale configurations."))
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
        val infeasible = definition.selectedScales.filter {
            ScaleCandidates.tonics(definition, it).isEmpty()
        }
        if (infeasible.isNotEmpty()) {
            return ProviderValidation.Invalid(
                infeasible.map {
                    FieldIssue(
                        "range",
                        "No complete ${it.id} scale fits the selected range and roots."
                    )
                }
            )
        }
        return ProviderValidation.Valid(
            config = config,
            categories = definition.selectedScales.map {
                AnswerCategory(categoryId(it))
            },
            maximumExampleDuration = maximumExampleDuration(definition),
            maximumAnswerDuration = maximumAnswerDuration(definition, catalog),
            requiredAssets = required
        )
    }

    override fun createGenerator(config: ProviderConfig, seed: Long): ExerciseGenerator {
        val definition = (config as ProviderConfig.Scales).definition
        return ScaleGeneratorAdapter(definition, SeededRandomSource(seed))
    }

    /** Stable answer ids and the provider's content bounds. */
    companion object {

        /** Stable answer-category id: the canonical scale kind, for example `scale.major`. */
        fun categoryId(scale: ScaleKind): String = "scale.${scale.id}"

        /** The longest example window the definition can produce under the default timing. */
        fun maximumExampleDuration(
            definition: ScaleDefinition,
            timing: RecipeTiming = RecipeTiming()
        ): Duration {
            val maxNotes = definition.selectedScales.maxOf {
                emittedNoteCount(it, definition.direction)
            }
            return timing.noteDuration * maxNotes + timing.melodicGap * (maxNotes - 1)
        }

        /** A scale answer is one name clip; the bound is the longest selected name. */
        fun maximumAnswerDuration(
            definition: ScaleDefinition,
            catalog: VoiceDurationCatalog
        ): Duration = definition.selectedScales.maxOf {
            catalog.durationOf(VoiceVocabulary.scaleEntry(it).id)
        }

        /** The emitted note count of one complete traversal; 2k+1 for both directions. */
        private fun emittedNoteCount(kind: ScaleKind, direction: ScaleDirection): Int =
            when (direction) {
                ScaleDirection.ASCENDING, ScaleDirection.DESCENDING -> kind.semitones.size + 1
                ScaleDirection.BOTH -> kind.semitones.size * 2 + 1
            }
    }
}

/**
 * The feasible lower tonics of one scale definition.
 *
 * A tonic is feasible when its complete traversal can be spelled from the sharp chromatic table,
 * its lower tonic fits the configured root range, and every emitted note lies inside the packaged
 * instrument range. A tonic whose theoretical spelling is unrepresentable is excluded, never
 * relabeled; a definition whose selected scale has no feasible tonic at all fails provider
 * validation.
 */
internal object ScaleCandidates {

    /** Every feasible lower tonic of [kind] under [definition], in ascending MIDI order. */
    fun tonics(definition: ScaleDefinition, kind: ScaleKind): List<MidiNote> {
        val roots: List<MidiNote> = definition.fixedRoot?.let {
            listOf(it).filter { tonic -> tonic.value in definition.noteRange }
        } ?: (0..127)
            .map { MidiNote.of(it) }
            .filter { it.value in definition.noteRange }
            .filter { it.pitchClass.value in definition.rootValues }
        return roots.filter { tonic ->
            val traversal = try {
                ScaleTraversal.pitches(
                    kind,
                    SpelledPitch.fromMidi(tonic, SpellingPreference.SHARP),
                    definition.direction
                )
            } catch (_: UnrepresentableSpellingException) {
                return@filter false
            }
            traversal.all { it.midiValue in PlayableRange.VALUES }
        }
    }
}

/**
 * Owns the scale draw stream: SC3 bag of scales with one uniform lower tonic per draw.
 *
 * The bag is a Fisher-Yates shuffle of the canonical selected-scale list; each item then draws
 * exactly one tonic with `nextInt(tonicCount)`, including when that count is one, so adding a
 * feasible tonic later shifts the following draws predictably. A configuration edit discards the
 * remainder of the current bag and continues on the same stream; the sequence index stays
 * monotonic across edits.
 */
private class ScaleGeneratorAdapter(
    initialDefinition: ScaleDefinition,
    private val stream: SeededRandomSource
) : ExerciseGenerator {

    private var definition: ScaleDefinition = initialDefinition
    private var feasible: Map<ScaleKind, List<MidiNote>> = buildFeasible(initialDefinition)
    private var bag: MutableList<ScaleKind> = mutableListOf()
    private var nextPositionInCycle: Int = 0
    private var started: Boolean = false
    private var cycleIndex: Int = 0
    private var sequenceIndex: Int = 0

    init {
        require(feasible.values.none { it.isEmpty() }) {
            "Every enabled scale must have a feasible lower tonic"
        }
    }

    override fun next(): ConcreteExercise {
        if (nextPositionInCycle == bag.size) {
            startNewBag()
        }
        val scale = bag[nextPositionInCycle]
        val tonics = feasible.getValue(scale)
        val tonic = tonics[stream.nextInt(tonics.size)]
        val position = nextPositionInCycle
        nextPositionInCycle += 1
        val exercise = adapt(scale, tonic, position)
        sequenceIndex += 1
        return exercise
    }

    override fun reconfigure(config: ProviderConfig) {
        val updated = (config as ProviderConfig.Scales).definition
        if (updated == definition) return
        val updatedFeasible = buildFeasible(updated)
        require(updatedFeasible.values.none { it.isEmpty() }) {
            "Every enabled scale must have a feasible lower tonic"
        }
        definition = updated
        feasible = updatedFeasible
        // The remainder of the old bag no longer describes the edited setup; the stream continues.
        bag = mutableListOf()
        nextPositionInCycle = 0
    }

    private fun buildFeasible(definition: ScaleDefinition): Map<ScaleKind, List<MidiNote>> =
        definition.selectedScales.associateWith { ScaleCandidates.tonics(definition, it) }

    private fun startNewBag() {
        if (started) cycleIndex += 1
        started = true
        val fresh = definition.selectedScales.toMutableList()
        for (index in fresh.lastIndex downTo 1) {
            val draw = stream.nextInt(index + 1)
            val current = fresh[index]
            fresh[index] = fresh[draw]
            fresh[draw] = current
        }
        bag = fresh
        nextPositionInCycle = 0
    }

    private fun adapt(scale: ScaleKind, tonic: MidiNote, positionInCycle: Int): ConcreteExercise {
        val pitches = ScaleTraversal.pitches(
            scale,
            SpelledPitch.fromMidi(tonic, SpellingPreference.SHARP),
            definition.direction
        )
        val groups = pitches.map {
            NoteGroup(listOf(MidiNote.of(it.midiValue)), GroupRole.TARGET)
        }
        return ConcreteExercise(
            kind = TrainingKind.SCALES,
            provenance = GeneratorProvenance(
                providerId = TrainingKind.SCALES.id,
                providerVersion = 1,
                rngId = SeededRandomSource.RNG_ID,
                seed = stream.seed,
                generatedScale = GeneratedScale(
                    scaleId = scale.id,
                    direction = definition.direction,
                    tonicMidi = tonic.value,
                    soundingValues = pitches.map { it.midiValue }
                )
            ),
            categoryId = ScaleExerciseProvider.categoryId(scale),
            sequenceIndex = sequenceIndex,
            cycle = CyclePosition(cycleIndex, positionInCycle),
            groups = groups,
            answer = AnswerDescriptor(
                voiceIds = listOf(VoiceVocabulary.scaleEntry(scale).id),
                display = AnswerDisplay.Scale(scale.id)
            )
        )
    }
}
