package com.thomas.passiveeartraining.training.provider

import com.thomas.passiveeartraining.music.MidiNote
import com.thomas.passiveeartraining.training.IntervalDefinition
import com.thomas.passiveeartraining.training.MapVoiceDurationCatalog
import com.thomas.passiveeartraining.training.MissingVoiceDurationException
import com.thomas.passiveeartraining.training.VoiceAssetId
import com.thomas.passiveeartraining.training.VoiceDurationCatalog
import com.thomas.passiveeartraining.training.recipe.RecipeDefinition
import com.thomas.passiveeartraining.training.recipe.RecipeMode
import com.thomas.passiveeartraining.training.recipe.RecipeTiming
import com.thomas.passiveeartraining.training.recipe.RecipeValidationException
import com.thomas.passiveeartraining.training.session.ConfiguredExerciseSource
import kotlin.time.Duration
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * M1-T02/T03/T04: variable example and answer shapes compile with exact scheduling.
 *
 * The exercises here are built directly or by a test-only provider; nothing in this file is
 * registered in the production registry.
 */
class ExerciseCompilerTest {

    private val compiler = ExerciseCompiler()

    private val catalog = MapVoiceDurationCatalog(
        mapOf(
            "answer.one" to Duration.parse("900ms"),
            "answer.two" to Duration.parse("500ms"),
            "answer.three" to Duration.parse("700ms"),
            "answer.long" to Duration.parse("6s")
        ).mapKeys { VoiceAssetId(it.key) }
    )

    private fun noteGroup(vararg notes: Int, role: GroupRole = GroupRole.TARGET) =
        NoteGroup(notes.sorted().map { MidiNote.of(it) }, role)

    private fun exercise(groups: List<NoteGroup>, tokens: List<String> = listOf("answer.one")) =
        ConcreteExercise(
            kind = TrainingKind.INTERVALS,
            provenance = GeneratorProvenance(
                providerId = "test",
                providerVersion = 1,
                rngId = "test-rng",
                seed = 1L
            ),
            categoryId = "test.category",
            sequenceIndex = 0,
            cycle = null,
            groups = groups,
            answer = AnswerDescriptor(
                voiceIds = tokens.map { VoiceAssetId(it) },
                display = AnswerDisplay.Interval("unison")
            )
        )

    @Test
    fun oneNoteExampleLastsOneNoteLength() {
        val compiled = compiler.compile(
            exercise(listOf(noteGroup(60))),
            RecipeDefinition.of(RecipeMode.RECOGNITION),
            catalog
        )
        val notes = compiled.timeline.notes
        assertEquals(1, notes.size)
        assertEquals(Duration.ZERO, notes.single().startOffset)
        // Recognition: example (700 ms) + thinking (2 s) + answer (900 ms) + final silence (2 s).
        assertEquals(Duration.parse("5600ms"), compiled.timeline.totalDuration)
    }

    @Test
    fun twoSequentialNotesUseTheMelodicStride() {
        val compiled = compiler.compile(
            exercise(listOf(noteGroup(60), noteGroup(64))),
            RecipeDefinition.of(RecipeMode.RECOGNITION),
            catalog
        )
        val notes = compiled.timeline.notes.sortedBy { it.startOffset }
        assertEquals(Duration.ZERO, notes[0].startOffset)
        assertEquals(Duration.parse("800ms"), notes[1].startOffset)
        // Example 1500 ms + thinking 2 s + answer 900 ms + final 2 s.
        assertEquals(Duration.parse("6400ms"), compiled.timeline.totalDuration)
        assertEquals(
            "the example window is two notes and one gap",
            Duration.parse("1500ms"),
            compiled.answerStart - Duration.parse("2s")
        )
    }

    @Test
    fun fourSimultaneousNotesShareOneOnset() {
        val compiled = compiler.compile(
            exercise(listOf(noteGroup(60, 64, 67, 71))),
            RecipeDefinition.of(RecipeMode.RECOGNITION),
            catalog
        )
        val notes = compiled.timeline.notes
        assertEquals(4, notes.size)
        assertTrue(notes.all { it.startOffset == Duration.ZERO })
        // A single simultaneous group lasts one note length, not four.
        assertEquals(Duration.parse("5600ms"), compiled.timeline.totalDuration)
    }

    @Test
    fun sixteenNoteExampleUsesFifteenStrides() {
        val groups = (0 until 16).map { noteGroup(60 + it) }
        val compiled = compiler.compile(
            exercise(groups),
            RecipeDefinition.of(RecipeMode.RECOGNITION),
            catalog
        )
        val notes = compiled.timeline.notes.sortedBy { it.startOffset }
        assertEquals(Duration.parse("${15 * 800}ms"), notes.last().startOffset)
        assertEquals(
            "the example ends one note length after the last note",
            Duration.parse("${15 * 800 + 700}ms"),
            compiled.answerStart - Duration.parse("2s")
        )
    }

    @Test
    fun aRestSlotReservesWindowWithoutSounding() {
        val compiled = compiler.compile(
            exercise(listOf(noteGroup(60), NoteGroup.rest(GroupRole.TARGET))),
            RecipeDefinition.of(RecipeMode.RECOGNITION),
            catalog
        )
        assertEquals(1, compiled.timeline.notes.size)
        assertEquals(Duration.parse("1500ms"), compiled.answerStart - Duration.parse("2s"))
    }

    @Test
    fun multipleAnswerTokensUseExactlyEightyMillisecondGaps() {
        val compiled = compiler.compile(
            exercise(
                groups = listOf(noteGroup(60)),
                tokens = listOf("answer.one", "answer.two", "answer.three")
            ),
            RecipeDefinition.of(RecipeMode.EXPOSURE),
            catalog
        )
        val voices = compiled.timeline.voices.sortedBy { it.startOffset }
        assertEquals(3, voices.size)
        assertEquals(Duration.ZERO, voices[0].startOffset)
        assertEquals(Duration.parse("980ms"), voices[1].startOffset)
        assertEquals(Duration.parse("1560ms"), voices[2].startOffset)
        val markers = compiled.timeline.events
            .filterIsInstance<com.thomas.passiveeartraining.training.AnswerStartedEvent>()
        assertEquals("one reveal marker for the whole composed answer", 1, markers.size)
        assertEquals(Duration.ZERO, markers.single().startOffset)
        // Exposure: answer 2260 ms + gap 600 ms + example 700 ms + final silence 2 s.
        assertEquals(
            Duration.parse("5560ms"),
            compiled.timeline.totalDuration
        )
    }

    @Test
    fun aMissingMiddleTokenFailsBeforeCompilationCanComplete() {
        assertThrows(MissingVoiceDurationException::class.java) {
            compiler.compile(
                exercise(
                    groups = listOf(noteGroup(60)),
                    tokens = listOf("answer.one", "answer.missing", "answer.three")
                ),
                RecipeDefinition.of(RecipeMode.RECOGNITION),
                catalog
            )
        }
    }

    @Test
    fun contextPrecedesTargetWithTheContextGap() {
        val context = listOf(
            noteGroup(60, 64, 67, role = GroupRole.CONTEXT),
            noteGroup(67, 71, 74, role = GroupRole.CONTEXT)
        )
        val compiled = compiler.compile(
            exercise(context + listOf(noteGroup(72))),
            RecipeDefinition.of(RecipeMode.RECOGNITION),
            catalog
        )
        val notes = compiled.timeline.notes.sortedBy { it.startOffset }
        // Context: two chords, stride 800 ms, each one note length -> 1500 ms.
        assertEquals(Duration.ZERO, notes[0].startOffset)
        assertEquals(Duration.parse("800ms"), notes[3].startOffset)
        // Target starts after context + context gap (500 ms).
        assertEquals(Duration.parse("2000ms"), notes[6].startOffset)
    }

    @Test
    fun contextAndRepeatsAndSeparatorAreIncludedInTheSixtySecondBoundary() {
        // A complete exercise exactly at the 60 s cap is valid.
        val exact = RecipeDefinition(
            mode = RecipeMode.RECOGNITION,
            timing = RecipeTiming(
                noteDuration = Duration.parse("54s"),
                melodicGap = Duration.ZERO,
                thinkingSilence = Duration.ZERO,
                postAnswerSilence = Duration.ZERO
            )
        )
        val compiled = compiler.compile(
            exercise(listOf(noteGroup(60)), tokens = listOf("answer.long")),
            exact,
            catalog
        )
        assertEquals(Duration.parse("60s"), compiled.timeline.totalDuration)

        // One more answer token pushes it above the cap.
        val above = RecipeDefinition(
            mode = RecipeMode.RECOGNITION,
            timing = RecipeTiming(
                noteDuration = Duration.parse("54s"),
                melodicGap = Duration.ZERO,
                thinkingSilence = Duration.ZERO,
                postAnswerSilence = Duration.ZERO
            )
        )
        assertThrows(RecipeValidationException::class.java) {
            compiler.compile(
                exercise(listOf(noteGroup(60)), tokens = listOf("answer.long", "answer.two")),
                above,
                catalog
            )
        }
    }

    @Test
    fun aTestProviderCanDriveTheSourceWithoutEnteringTheProductionRegistry() {
        val catalog = MapVoiceDurationCatalog(
            mapOf(
                VoiceAssetId("interval.unison") to Duration.parse("700ms")
            )
        )
        val registry = ProviderRegistry(listOf(FakeSingleNoteProvider()))
        val source = ConfiguredExerciseSource(registry = registry, voiceDurations = { catalog })
        source.configure(
            com.thomas.passiveeartraining.training.session.StartRequest(
                setup = ModeSetup(
                    provider = ProviderConfig.Intervals(
                        com.thomas.passiveeartraining.training.recipe.SequencePolicy(
                            definition = IntervalDefinition.default()
                        )
                    ),
                    recipe = RecipeDefinition.of(RecipeMode.RECOGNITION),
                    seed = 1L
                )
            )
        )
        val concrete = source.next()
        assertEquals("test", concrete.provenance.providerId)
        val compiled = source.compile(concrete, RecipeDefinition.of(RecipeMode.RECOGNITION))
        assertEquals(1, compiled.timeline.notes.size)
    }
}

/** A test-only provider that yields one fixed single-note exercise; never in production. */
private class FakeSingleNoteProvider : ExerciseProvider {
    override val kind: TrainingKind = TrainingKind.INTERVALS
    override val providerVersion: Int = 1

    override fun defaults(): ProviderConfig = ProviderConfig.Intervals(
        com.thomas.passiveeartraining.training.recipe.SequencePolicy(
            definition = IntervalDefinition.default()
        )
    )

    override fun requiredVocabulary(config: ProviderConfig): Set<VoiceAssetId> =
        setOf(VoiceAssetId("interval.unison"))

    override fun validate(
        config: ProviderConfig,
        catalog: VoiceDurationCatalog
    ): ProviderValidation = ProviderValidation.Valid(
        config = config,
        categories = listOf(AnswerCategory("interval.unison")),
        maximumExampleDuration = Duration.parse("700ms"),
        maximumAnswerDuration = Duration.parse("700ms"),
        requiredAssets = setOf(VoiceAssetId("interval.unison"))
    )

    override fun createGenerator(config: ProviderConfig, seed: Long): ExerciseGenerator =
        object : ExerciseGenerator {
            private var index = 0
            override fun next(): ConcreteExercise = ConcreteExercise(
                kind = TrainingKind.INTERVALS,
                provenance = GeneratorProvenance(
                    providerId = "test",
                    providerVersion = 1,
                    rngId = "test-rng",
                    seed = seed
                ),
                categoryId = "interval.unison",
                sequenceIndex = index++,
                cycle = CyclePosition(0, index - 1),
                groups = listOf(
                    NoteGroup(listOf(MidiNote.of(60)), GroupRole.TARGET)
                ),
                answer = AnswerDescriptor(
                    voiceIds = listOf(VoiceAssetId("interval.unison")),
                    display = AnswerDisplay.Interval("unison")
                )
            )

            override fun reconfigure(config: ProviderConfig) = Unit
        }
}
