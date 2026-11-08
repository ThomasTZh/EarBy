package com.thomas.passiveeartraining.training.provider

import com.thomas.passiveeartraining.music.MidiNote
import com.thomas.passiveeartraining.music.PitchClass
import com.thomas.passiveeartraining.training.MissingVoiceDurationException
import com.thomas.passiveeartraining.training.NoteDefinition
import com.thomas.passiveeartraining.training.SeededRandomSource
import com.thomas.passiveeartraining.training.VoiceAssetId
import com.thomas.passiveeartraining.training.VoiceDurationCatalog
import com.thomas.passiveeartraining.training.recipe.RecipeTiming
import kotlin.time.Duration

/**
 * M7.1: the note-identification provider.
 *
 * Categories are the selected pitch classes in numeric order. A bag shuffles those classes with
 * the SC3 contract and each drawn class picks one feasible absolute note uniformly; a class with
 * several reachable octaves therefore keeps the same weight as a class with one. The generator is
 * finite and reproducible: no rejection sampling, no hidden state outside the injected stream.
 * Required vocabulary covers every reachable name and, when octave speech is on, every spoken
 * octave, not only the first draw.
 */
class NoteExerciseProvider : ExerciseProvider {
    override val kind: TrainingKind = TrainingKind.NOTE_IDENTIFICATION

    override val providerVersion: Int = 1

    override fun defaults(): ProviderConfig = ProviderConfig.Notes(NoteDefinition.default())

    override fun requiredVocabulary(config: ProviderConfig): Set<VoiceAssetId> {
        val definition = (config as ProviderConfig.Notes).definition
        val tokens = linkedSetOf<VoiceAssetId>()
        definition.feasiblePitchClasses.forEach {
            tokens += definition.nameToken(it)
        }
        if (definition.speakOctave) {
            (-1..9).forEach {
                tokens += VoiceVocabulary.octaveEntry(it).id
            }
        }
        return tokens
    }

    override fun validate(
        config: ProviderConfig,
        catalog: VoiceDurationCatalog
    ): ProviderValidation {
        if (config !is ProviderConfig.Notes) {
            return ProviderValidation.Invalid(
                listOf(FieldIssue("kind", "This provider only accepts note configurations."))
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
        if (definition.feasiblePitchClasses.isEmpty()) {
            return ProviderValidation.Invalid(
                listOf(
                    FieldIssue(
                        "range",
                        "No selected note class sounds inside " +
                            "${definition.minimumNoteValue}..${definition.maximumNoteValue}."
                    )
                )
            )
        }
        return ProviderValidation.Valid(
            config = config,
            categories = definition.feasiblePitchClasses.map {
                AnswerCategory(categoryId(definition, it))
            },
            maximumExampleDuration = maximumExampleDuration(),
            maximumAnswerDuration = maximumAnswerDuration(definition, catalog),
            requiredAssets = required
        )
    }

    override fun createGenerator(config: ProviderConfig, seed: Long): ExerciseGenerator {
        val definition = (config as ProviderConfig.Notes).definition
        return NoteGeneratorAdapter(definition, SeededRandomSource(seed))
    }

    /** Stable answer ids and the provider's content bounds. */
    companion object {

        /** Stable answer-category id: the class's name token, for example `note.c_sharp`. */
        fun categoryId(definition: NoteDefinition, pitchClass: PitchClass): String =
            definition.nameToken(pitchClass).value

        /** The longest example window: one note sounding for [timing]'s note duration. */
        fun maximumExampleDuration(timing: RecipeTiming = RecipeTiming()): Duration =
            timing.noteDuration

        /**
         * The conservative reachable answer bound: the longest name-plus-octave sequence the
         * definition can speak, including the exactly-80 ms token gaps.
         */
        fun maximumAnswerDuration(
            definition: NoteDefinition,
            catalog: VoiceDurationCatalog
        ): Duration {
            val sequences = definition.feasiblePitchClasses.flatMap { pitchClass ->
                definition.notesFor(pitchClass).map { note -> definition.answerTokensFor(note) }
            }
            return sequences.maxOfOrNull { tokens ->
                tokens.fold(Duration.ZERO) { total, token -> total + catalog.durationOf(token) } +
                    ExerciseCompiler.TOKEN_GAP * (tokens.size - 1)
            } ?: Duration.ZERO
        }
    }
}

/**
 * Owns the note draw stream: SC3 bag of pitch-class categories with one uniform note per draw.
 *
 * The bag is a Fisher-Yates shuffle of the selected-class list, so category weight never depends
 * on how many octaves a class can sound; each item then draws exactly one MIDI note with
 * `nextInt(noteCount)`, including when that count is one. A configuration edit discards the
 * remainder of the current bag and continues on the same stream; the sequence index stays
 * monotonic across edits.
 */
private class NoteGeneratorAdapter(
    initialDefinition: NoteDefinition,
    private val stream: SeededRandomSource
) : ExerciseGenerator {

    private var definition: NoteDefinition = initialDefinition
    private var feasible: Map<PitchClass, List<MidiNote>> = buildFeasible(initialDefinition)
    private var bag: MutableList<PitchClass> = mutableListOf()
    private var nextPositionInCycle: Int = 0
    private var started: Boolean = false
    private var cycleIndex: Int = 0
    private var sequenceIndex: Int = 0

    init {
        require(feasible.isNotEmpty()) {
            "At least one enabled pitch class must have a feasible note"
        }
    }

    override fun next(): ConcreteExercise {
        if (nextPositionInCycle == bag.size) {
            startNewBag()
        }
        val pitchClass = bag[nextPositionInCycle]
        val notes = feasible.getValue(pitchClass)
        val note = notes[stream.nextInt(notes.size)]
        val position = nextPositionInCycle
        nextPositionInCycle += 1
        val exercise = adapt(pitchClass, note, position)
        sequenceIndex += 1
        return exercise
    }

    override fun reconfigure(config: ProviderConfig) {
        val updated = (config as ProviderConfig.Notes).definition
        if (updated == definition) return
        val updatedFeasible = buildFeasible(updated)
        require(updatedFeasible.isNotEmpty()) {
            "At least one enabled pitch class must have a feasible note"
        }
        definition = updated
        feasible = updatedFeasible
        // The remainder of the old bag no longer describes the edited setup; the stream continues.
        bag = mutableListOf()
        nextPositionInCycle = 0
    }

    private fun buildFeasible(definition: NoteDefinition): Map<PitchClass, List<MidiNote>> =
        definition.feasiblePitchClasses.associateWith { definition.notesFor(it) }

    private fun startNewBag() {
        if (started) cycleIndex += 1
        started = true
        val fresh = definition.feasiblePitchClasses.toMutableList()
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
        pitchClass: PitchClass,
        note: MidiNote,
        positionInCycle: Int
    ): ConcreteExercise = ConcreteExercise(
        kind = TrainingKind.NOTE_IDENTIFICATION,
        provenance = GeneratorProvenance(
            providerId = TrainingKind.NOTE_IDENTIFICATION.id,
            providerVersion = 1,
            rngId = SeededRandomSource.RNG_ID,
            seed = stream.seed,
            generatedNote = GeneratedNote(
                pitchClass = pitchClass,
                midiNote = note,
                accidental = definition.accidental,
                octaveSpoken = definition.speakOctave
            )
        ),
        categoryId = NoteExerciseProvider.categoryId(definition, pitchClass),
        sequenceIndex = sequenceIndex,
        cycle = CyclePosition(cycleIndex, positionInCycle),
        groups = listOf(NoteGroup(listOf(note), GroupRole.TARGET)),
        answer = AnswerDescriptor(
            voiceIds = definition.answerTokensFor(note),
            display = AnswerDisplay.Note(
                pitchClass = pitchClass,
                midiNote = note,
                accidental = definition.accidental,
                octaveSpoken = definition.speakOctave
            )
        )
    )
}
