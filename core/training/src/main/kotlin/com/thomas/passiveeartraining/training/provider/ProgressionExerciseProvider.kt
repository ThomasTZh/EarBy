package com.thomas.passiveeartraining.training.provider

import com.thomas.passiveeartraining.music.DiatonicChord
import com.thomas.passiveeartraining.music.ProgressionVoicingMode
import com.thomas.passiveeartraining.music.Tonality
import com.thomas.passiveeartraining.training.MissingVoiceDurationException
import com.thomas.passiveeartraining.training.ProgressionAnswerFormat
import com.thomas.passiveeartraining.training.ProgressionDefinition
import com.thomas.passiveeartraining.training.SeededRandomSource
import com.thomas.passiveeartraining.training.VoiceAssetId
import com.thomas.passiveeartraining.training.VoiceDurationCatalog
import com.thomas.passiveeartraining.training.recipe.RecipeTiming
import kotlin.time.Duration

/**
 * M9A/M9B: the chord-progression provider.
 *
 * Every exercise plays [ProgressionDefinition.CHORD_COUNT] chords. The first is always the plain
 * tonic triad I; the other three are drawn independently with replacement from the selected pool,
 * so a chord may repeat. One placement (key tonic and anchor) is
 * drawn first from the definition's canonical list of placements that realize the complete pool;
 * a fixed root makes that list exactly one entry.
 *
 * The mode owns no label cycle: the answer is the drawn sequence itself, so [ConcreteExercise.cycle]
 * is null and the sequence index stays monotonic. Each chord is one simultaneous TARGET group and
 * there is no context prelude, because the progression is the example. The answer speaks the
 * selected format's token sequence; the display projection is the matching Roman, function or
 * directed root-motion sequence of the same draw.
 */
class ProgressionExerciseProvider : ExerciseProvider {
    override val kind: TrainingKind = TrainingKind.CHORD_PROGRESSIONS

    override val providerVersion: Int = 2

    override fun defaults(): ProviderConfig =
        ProviderConfig.Progressions(ProgressionDefinition.default())

    override fun requiredVocabulary(config: ProviderConfig): Set<VoiceAssetId> =
        (config as ProviderConfig.Progressions).definition.possibleTokens()

    override fun validate(
        config: ProviderConfig,
        catalog: VoiceDurationCatalog
    ): ProviderValidation {
        if (config !is ProviderConfig.Progressions) {
            return ProviderValidation.Invalid(
                listOf(
                    FieldIssue(
                        "kind",
                        "This provider only accepts chord-progression configurations."
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
        return ProviderValidation.Valid(
            config = config,
            categories = listOf(AnswerCategory(categoryId(definition.answerFormat))),
            maximumExampleDuration = maximumExampleDuration(definition),
            maximumAnswerDuration = maximumAnswerDuration(definition, catalog),
            requiredAssets = required
        )
    }

    override fun createGenerator(config: ProviderConfig, seed: Long): ExerciseGenerator {
        val definition = (config as ProviderConfig.Progressions).definition
        return ProgressionGeneratorAdapter(definition, SeededRandomSource(seed))
    }

    companion object {
        /** The stable answer category of one answer format. */
        fun categoryId(format: ProgressionAnswerFormat): String =
            "progression.${format.name.lowercase()}"

        /**
         * The example window of any exercise under [timing]: four simultaneous chords separated
         * by the inter-chord gap, measured with the same timing the compiler consumes.
         */
        fun maximumExampleDuration(
            definition: ProgressionDefinition,
            timing: RecipeTiming = RecipeTiming()
        ): Duration = definition.maximumExampleDuration(timing)

        /** The conservative longest reachable answer over the selected pool. */
        fun maximumAnswerDuration(
            definition: ProgressionDefinition,
            catalog: VoiceDurationCatalog
        ): Duration = definition.maximumAnswerDuration(catalog)
    }
}

/**
 * Owns the progression draw stream.
 *
 * One placement draw (uniform over the feasible key/anchor list) and four chord draws (the fixed
 * tonic first, then three with replacement) produce one exercise. A configuration edit rebuilds the
 * placement list and continues on the same stream; the sequence index stays monotonic across
 * edits, and there is no bag to discard because the mode has no label cycle.
 */
private class ProgressionGeneratorAdapter(
    initialDefinition: ProgressionDefinition,
    private val stream: SeededRandomSource
) : ExerciseGenerator {

    private var definition: ProgressionDefinition = initialDefinition
    private var placements = initialDefinition.feasiblePlacements()
    private var sequenceIndex = 0

    init {
        require(placements.isNotEmpty()) {
            "A progression definition must have at least one feasible placement"
        }
    }

    override fun next(): ConcreteExercise {
        val placement = placements[stream.nextInt(placements.size)]
        val chords = mutableListOf<DiatonicChord>()
        // The opening is always the plain tonic triad; the selection only feeds the next three
        // slots and may repeat, including I itself.
        chords += DiatonicChord.I
        repeat(ProgressionDefinition.CHORD_COUNT - 1) {
            chords += definition.chords[stream.nextInt(definition.chords.size)]
        }
        val steps = chords.map { it.step }
        val voicings = definition.chordsFor(steps, placement.keyValue, placement.anchor)
            ?: error(
                "Feasible placement ${placement.keyValue}@${placement.anchor} must realize " +
                    "every drawn chord"
            )
        val label = definition.displayLabelFor(steps)
        val exercise = ConcreteExercise(
            kind = TrainingKind.CHORD_PROGRESSIONS,
            provenance = GeneratorProvenance(
                providerId = TrainingKind.CHORD_PROGRESSIONS.id,
                providerVersion = 2,
                rngId = SeededRandomSource.RNG_ID,
                seed = stream.seed,
                generatedProgression = GeneratedProgression(
                    chords = voicings,
                    sequenceLabel = label,
                    keyPitchClass = placement.keyValue,
                    spelling = definition.spelling,
                    tonality = Tonality.MAJOR,
                    anchorMidi = placement.anchor.value,
                    voicing = definition.voicing,
                    allowedInversions = definition.allowedInversions,
                    answerFormat = definition.answerFormat
                )
            ),
            categoryId = ProgressionExerciseProvider.categoryId(definition.answerFormat),
            sequenceIndex = sequenceIndex,
            // The mode owns no label cycle: the drawn sequence is the answer.
            cycle = null,
            groups = voicings.map { NoteGroup(it.notes, GroupRole.TARGET) },
            answer = AnswerDescriptor(
                voiceIds = definition.answerTokensFor(steps),
                display = AnswerDisplay.Progression(label)
            )
        )
        sequenceIndex += 1
        return exercise
    }

    override fun reconfigure(config: ProviderConfig) {
        val updated = (config as ProviderConfig.Progressions).definition
        if (updated == definition) return
        val updatedPlacements = updated.feasiblePlacements()
        require(updatedPlacements.isNotEmpty()) {
            "A progression definition must have at least one feasible placement"
        }
        definition = updated
        placements = updatedPlacements
    }
}
