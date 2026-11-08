package com.thomas.passiveeartraining.training.provider

import com.thomas.passiveeartraining.music.ChordEquivalence
import com.thomas.passiveeartraining.music.ChordInterpretation
import com.thomas.passiveeartraining.music.ChordPresentation
import com.thomas.passiveeartraining.music.ChordQuality
import com.thomas.passiveeartraining.music.ChordVoicing
import com.thomas.passiveeartraining.music.MidiNote
import com.thomas.passiveeartraining.training.ChordAnswerMode
import com.thomas.passiveeartraining.training.ChordDefinition
import com.thomas.passiveeartraining.training.MissingVoiceDurationException
import com.thomas.passiveeartraining.training.PlayableRange
import com.thomas.passiveeartraining.training.SeededRandomSource
import com.thomas.passiveeartraining.training.VoiceAssetId
import com.thomas.passiveeartraining.training.VoiceDurationCatalog
import com.thomas.passiveeartraining.training.recipe.RecipeTiming
import kotlin.time.Duration

/**
 * M5.1: the chord provider.
 *
 * Categories are the applicable quality/inversion pairs in catalog order. A bag shuffles those
 * categories with the SC3 contract and each drawn category picks one feasible realization
 * uniformly; the answer is the complete D08 alternative set for the played voicing. The
 * generator is finite and reproducible: no rejection sampling, no hidden state outside the
 * injected stream.
 */
class ChordExerciseProvider : ExerciseProvider {
    override val kind: TrainingKind = TrainingKind.CHORDS

    override val providerVersion: Int = 1

    override fun defaults(): ProviderConfig = ProviderConfig.Chords(ChordDefinition.default())

    override fun requiredVocabulary(config: ProviderConfig): Set<VoiceAssetId> {
        val definition = (config as ProviderConfig.Chords).definition
        val tokens = linkedSetOf<VoiceAssetId>()
        definition.selectedQualities.forEach {
            tokens += VoiceVocabulary.chordQualityEntry(it).id
        }
        if (definition.answerMode == ChordAnswerMode.QUALITY_AND_INVERSION) {
            definition.categories.map { it.inversion }.distinct().forEach {
                tokens += VoiceVocabulary.chordInversionEntry(it).id
            }
        }
        if (definition.categories.size > 1) {
            tokens += VoiceVocabulary.OR.id
        }
        return tokens
    }

    override fun validate(
        config: ProviderConfig,
        catalog: VoiceDurationCatalog
    ): ProviderValidation {
        if (config !is ProviderConfig.Chords) {
            return ProviderValidation.Invalid(
                listOf(FieldIssue("kind", "This provider only accepts chord configurations."))
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
        val infeasible = definition.categories.filter {
            ChordCandidates.realizations(definition, it).isEmpty()
        }
        if (infeasible.isNotEmpty()) {
            return ProviderValidation.Invalid(
                infeasible.map {
                    FieldIssue(
                        "range",
                        "No playing position fits ${it.quality.id} inversion ${it.inversion} " +
                            "in the selected range and roots."
                    )
                }
            )
        }
        return ProviderValidation.Valid(
            config = config,
            categories = definition.categories.map {
                AnswerCategory(categoryId(definition, it))
            },
            maximumExampleDuration = maximumExampleDuration(definition),
            maximumAnswerDuration = maximumAnswerDuration(definition, catalog),
            requiredAssets = required
        )
    }

    override fun createGenerator(config: ProviderConfig, seed: Long): ExerciseGenerator {
        val definition = (config as ProviderConfig.Chords).definition
        return ChordGeneratorAdapter(definition, SeededRandomSource(seed))
    }

    /** Stable answer-category id: quality alone, or quality plus inversion suffix. */
    companion object {
        fun categoryId(definition: ChordDefinition, category: ChordInterpretation): String =
            when (definition.answerMode) {
                ChordAnswerMode.QUALITY -> "chord.${category.quality.id}"
                ChordAnswerMode.QUALITY_AND_INVERSION ->
                    "chord.${category.quality.id}." +
                        com.thomas.passiveeartraining.music.ChordInversions.idSuffix(
                            category.inversion
                        )
            }

        /** The longest example window the definition can produce under [timing]. */
        fun maximumExampleDuration(
            definition: ChordDefinition,
            timing: RecipeTiming = RecipeTiming()
        ): Duration = when (definition.presentation) {
            ChordPresentation.HARMONIC -> timing.noteDuration
            ChordPresentation.ARPEGGIO -> {
                val members = definition.selectedQualities.maxOf { it.memberCount }
                timing.noteDuration * members + timing.melodicGap * (members - 1)
            }
        }

        /**
         * The conservative reachable answer bound: the most alternatives the answer format can
         * ever list, joined with "or", under the injected catalogue.
         */
        fun maximumAnswerDuration(
            definition: ChordDefinition,
            catalog: VoiceDurationCatalog
        ): Duration {
            val alternatives: List<List<VoiceAssetId>> = when (definition.answerMode) {
                ChordAnswerMode.QUALITY ->
                    definition.categories
                        .map { listOf(VoiceVocabulary.chordQualityEntry(it.quality).id) }
                        .distinct()
                ChordAnswerMode.QUALITY_AND_INVERSION -> definition.categories.map {
                    listOf(
                        VoiceVocabulary.chordQualityEntry(it.quality).id,
                        VoiceVocabulary.chordInversionEntry(it.inversion).id
                    )
                }
            }
            val tokens = mutableListOf<VoiceAssetId>()
            alternatives.forEachIndexed { index, alternative ->
                if (index > 0) tokens += VoiceVocabulary.OR.id
                tokens += alternative
            }
            val clips = tokens.filter { it != VoiceVocabulary.OR.id }
            val audio = clips.fold(Duration.ZERO) { total, voiceId ->
                total + catalog.durationOf(voiceId)
            }
            return audio + ExerciseCompiler.TOKEN_GAP * (tokens.size - 1)
        }
    }
}

/**
 * The feasible realizations of one chord definition.
 *
 * A realization is a closed-position voicing whose theoretical root fits the configured root
 * range and whose sounding notes all fit the packaged instrument range. An inversion's silent
 * root may sit outside the sounding notes. Roots are enumerated, never rejection-sampled.
 */
internal object ChordCandidates {

    /** Every realization of [category], ordered by theoretical root then sounding tuple. */
    fun realizations(
        definition: ChordDefinition,
        category: ChordInterpretation
    ): List<ChordVoicing> = realizations(definition, category.quality, category.inversion)

    /** Every realization of ([quality], [inversion]), ordered by theoretical root. */
    fun realizations(
        definition: ChordDefinition,
        quality: ChordQuality,
        inversion: Int
    ): List<ChordVoicing> {
        val roots: List<MidiNote> = definition.fixedRoot?.let {
            listOf(it).filter { root -> root.value in definition.noteRange }
        } ?: (0..127)
            .map { MidiNote.of(it) }
            .filter { it.value in definition.noteRange }
            .filter { it.pitchClass.value in definition.rootValues }
        return roots.mapNotNull { root ->
            val voicing = try {
                ChordVoicing.of(quality, inversion, root)
            } catch (_: IllegalArgumentException) {
                return@mapNotNull null
            }
            if (voicing.notes.all { it.value in PlayableRange.VALUES }) voicing else null
        }
    }
}

/**
 * Owns the chord draw stream: SC3 bag of categories with one uniform realization per draw.
 *
 * The bag is a Fisher-Yates shuffle of the canonical category list; each item then draws exactly
 * one realization with `nextInt(realizationCount)`, including when that count is one, so adding a
 * realization later shifts the following draws predictably. A configuration edit discards the
 * remainder of the current bag and continues on the same stream; the sequence index stays
 * monotonic across edits.
 */
private class ChordGeneratorAdapter(
    initialDefinition: ChordDefinition,
    private val stream: SeededRandomSource
) : ExerciseGenerator {

    private var definition: ChordDefinition = initialDefinition
    private var feasible: Map<ChordInterpretation, List<ChordVoicing>> =
        buildFeasible(initialDefinition)
    private var bag: MutableList<ChordInterpretation> = mutableListOf()
    private var nextPositionInCycle: Int = 0
    private var started: Boolean = false
    private var cycleIndex: Int = 0
    private var sequenceIndex: Int = 0

    init {
        require(feasible.values.none { it.isEmpty() }) {
            "Every enabled category must have a feasible realization"
        }
    }

    override fun next(): ConcreteExercise {
        if (nextPositionInCycle == bag.size) {
            startNewBag()
        }
        val category = bag[nextPositionInCycle]
        val realizations = feasible.getValue(category)
        val voicing = realizations[stream.nextInt(realizations.size)]
        val position = nextPositionInCycle
        nextPositionInCycle += 1
        val exercise = adapt(category, voicing, position)
        sequenceIndex += 1
        return exercise
    }

    override fun reconfigure(config: ProviderConfig) {
        val updated = (config as ProviderConfig.Chords).definition
        if (updated == definition) return
        val updatedFeasible = buildFeasible(updated)
        require(updatedFeasible.values.none { it.isEmpty() }) {
            "Every enabled category must have a feasible realization"
        }
        definition = updated
        feasible = updatedFeasible
        // The remainder of the old bag no longer describes the edited setup; the stream continues.
        bag = mutableListOf()
        nextPositionInCycle = 0
    }

    private fun buildFeasible(
        definition: ChordDefinition
    ): Map<ChordInterpretation, List<ChordVoicing>> =
        definition.categories.associateWith { ChordCandidates.realizations(definition, it) }

    private fun startNewBag() {
        if (started) cycleIndex += 1
        started = true
        val fresh = definition.categories.toMutableList()
        for (index in fresh.lastIndex downTo 1) {
            val draw = stream.nextInt(index + 1)
            val current = fresh[index]
            fresh[index] = fresh[draw]
            fresh[draw] = current
        }
        bag = fresh
        nextPositionInCycle = 0
    }

    private fun adapt(
        category: ChordInterpretation,
        voicing: ChordVoicing,
        positionInCycle: Int
    ): ConcreteExercise {
        val groups = when (definition.presentation) {
            ChordPresentation.HARMONIC ->
                listOf(NoteGroup(voicing.notes, GroupRole.TARGET))
            ChordPresentation.ARPEGGIO ->
                voicing.notes.map { NoteGroup(listOf(it), GroupRole.TARGET) }
        }
        val readings = ChordEquivalence.interpretationsOf(voicing, definition.categories)
        val displayItems: List<AnswerDisplay> = when (definition.answerMode) {
            ChordAnswerMode.QUALITY -> readings.map { it.quality }.distinct()
                .map { AnswerDisplay.Chord(it.id) }
            ChordAnswerMode.QUALITY_AND_INVERSION ->
                readings
                    .map { AnswerDisplay.Chord(it.quality.id, it.inversion) }
        }
        val alternativeTokens: List<List<VoiceAssetId>> = when (definition.answerMode) {
            ChordAnswerMode.QUALITY -> readings.map { it.quality }.distinct()
                .map { listOf(VoiceVocabulary.chordQualityEntry(it).id) }
            ChordAnswerMode.QUALITY_AND_INVERSION -> readings.map {
                listOf(
                    VoiceVocabulary.chordQualityEntry(it.quality).id,
                    VoiceVocabulary.chordInversionEntry(it.inversion).id
                )
            }
        }
        val tokens = mutableListOf<VoiceAssetId>()
        alternativeTokens.forEachIndexed { index, alternative ->
            if (index > 0) tokens += VoiceVocabulary.OR.id
            tokens += alternative
        }
        val display: AnswerDisplay = if (displayItems.size == 1) {
            displayItems.single()
        } else {
            AnswerDisplay.Alternatives(displayItems)
        }
        return ConcreteExercise(
            kind = TrainingKind.CHORDS,
            provenance = GeneratorProvenance(
                providerId = TrainingKind.CHORDS.id,
                providerVersion = 1,
                rngId = SeededRandomSource.RNG_ID,
                seed = stream.seed,
                generatedChord = GeneratedChord(
                    qualityId = voicing.quality.id,
                    inversion = voicing.inversion,
                    theoreticalRootMidi = voicing.theoreticalRoot.midiValue,
                    soundingValues = voicing.notes.map { it.value }
                )
            ),
            categoryId = ChordExerciseProvider.categoryId(definition, category),
            sequenceIndex = sequenceIndex,
            cycle = CyclePosition(cycleIndex, positionInCycle),
            groups = groups,
            answer = AnswerDescriptor(voiceIds = tokens, display = display)
        )
    }
}
