package com.thomas.passiveeartraining.training.session

import com.thomas.passiveeartraining.music.MidiNote
import com.thomas.passiveeartraining.training.IntervalDefinition
import com.thomas.passiveeartraining.training.IntervalExercise
import com.thomas.passiveeartraining.training.MapVoiceDurationCatalog
import com.thomas.passiveeartraining.training.SeededIntervalGenerator
import com.thomas.passiveeartraining.training.SeededIntervalGeneratorV2
import com.thomas.passiveeartraining.training.SeededRandomSource
import com.thomas.passiveeartraining.training.VoiceAssetId
import com.thomas.passiveeartraining.training.provider.AnswerDisplay
import com.thomas.passiveeartraining.training.provider.ConcreteExercise
import com.thomas.passiveeartraining.training.provider.ModeSetup
import com.thomas.passiveeartraining.training.provider.ProviderConfig
import com.thomas.passiveeartraining.training.recipe.RecipeDefinition
import com.thomas.passiveeartraining.training.recipe.RecipeMode
import com.thomas.passiveeartraining.training.recipe.SequencePolicy
import kotlin.time.Duration
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The production source must be exactly as reproducible as a directly constructed generator, and it
 * must compile under the requested recipe rather than a hardcoded one.
 */
class ConfiguredExerciseSourceTest {

    private val definition: IntervalDefinition = IntervalDefinition.default()

    private fun catalog(): MapVoiceDurationCatalog = MapVoiceDurationCatalog(
        definition.selectedIntervalIds.associate { id ->
            VoiceAssetId("interval.$id") to Duration.parse("900ms")
        }
    )

    private fun request(
        seed: Long,
        recipe: RecipeDefinition = RecipeDefinition.of(RecipeMode.RECOGNITION),
        fixedRoot: MidiNote? = null
    ) = StartRequest(
        setup = ModeSetup(
            provider = ProviderConfig.Intervals(
                SequencePolicy(definition = definition, fixedRoot = fixedRoot)
            ),
            recipe = recipe,
            seed = seed
        )
    )

    /** True when the shared-form exercise carries exactly the legacy exercise's audible content. */
    private fun ConcreteExercise.matches(legacy: IntervalExercise): Boolean =
        sequenceIndex == legacy.sequenceIndex &&
            provenance.legacyIntervalIdentity == legacy.identity &&
            categoryId == legacy.answerId &&
            answer.display == AnswerDisplay.Interval(legacy.interval.id) &&
            groups.flatMap { it.notes } == legacy.notes

    private fun assertMatches(legacy: IntervalExercise, adapted: ConcreteExercise) {
        assertTrue("adapted exercise must match $legacy but was $adapted", adapted.matches(legacy))
    }

    @Test
    fun anUnconfiguredSourceRefusesToGuessAConfiguration() {
        val source = ConfiguredExerciseSource(voiceDurations = { catalog() })
        assertThrows(IllegalStateException::class.java) { source.next() }
    }

    @Test
    fun theConfiguredSeedProducesTheSameSequenceAsADirectGenerator() {
        val source = ConfiguredExerciseSource(voiceDurations = { catalog() })
        source.configure(request(seed = 42L))
        val direct = SeededIntervalGenerator(definition, SeededRandomSource(42L))

        repeat(6) {
            assertMatches(direct.next(), source.next())
        }
    }

    @Test
    fun aNewAcceptedStartRestartsTheSequence() {
        val source = ConfiguredExerciseSource(voiceDurations = { catalog() })
        source.configure(request(seed = 1L))
        source.next()
        source.configure(request(seed = 2L))

        val direct = SeededIntervalGenerator(definition, SeededRandomSource(2L))
        assertMatches(direct.next(), source.next())
    }

    @Test
    fun reconfiguringWithTheSameRequestKeepsTheSequence() {
        val source = ConfiguredExerciseSource(voiceDurations = { catalog() })
        val direct = SeededIntervalGenerator(definition, SeededRandomSource(7L))
        source.configure(request(seed = 7L))
        assertMatches(direct.next(), source.next())
        source.reconfigure(request(seed = 7L))

        assertMatches(direct.next(), source.next())
    }

    @Test
    fun aFreshStartWithTheSameSeedReplaysTheSequence() {
        val source = ConfiguredExerciseSource(voiceDurations = { catalog() })
        source.configure(request(seed = 7L))
        val firstSequence = List(3) { source.next() }
        // A new accepted Start is not a live edit: the same seed replays from its beginning.
        source.configure(request(seed = 7L))
        assertEquals(firstSequence, List(3) { source.next() })
    }

    @Test
    fun aRecipeOnlyEditKeepsTheRunningSequenceAndUsesTheNewRecipe() {
        val source = ConfiguredExerciseSource(voiceDurations = { catalog() })
        source.configure(request(seed = 7L))
        val first = source.next()
        val direct = SeededIntervalGenerator(definition, SeededRandomSource(7L))
        assertMatches(direct.next(), first)

        source.reconfigure(request(seed = 7L, recipe = RecipeDefinition.of(RecipeMode.EXPOSURE)))

        assertMatches(direct.next(), source.next())
        val exposure = source.compile(source.next(), RecipeDefinition.of(RecipeMode.EXPOSURE))
        assertEquals(
            "an item drawn after the edit compiles under the edited recipe",
            Duration.ZERO,
            exposure.timeline.voices.single().startOffset
        )
    }

    @Test
    fun anEditedPolicyContinuesTheRunningRandomStream() {
        // Replicate the running stream by hand: one version 1 draw, then the edited policy on the
        // same stream. The source must produce exactly those two items, not restart the sequence.
        val stream = SeededRandomSource(7L)
        val firstOnStream = SeededIntervalGenerator(definition, stream).next()
        val fixedRoot = MidiNote.of(60)
        val continuation = SeededIntervalGeneratorV2(
            SequencePolicy(definition = definition, fixedRoot = fixedRoot),
            stream
        )
        val expectedNext = continuation.next()

        val source = ConfiguredExerciseSource(voiceDurations = { catalog() })
        source.configure(request(seed = 7L))
        assertMatches(firstOnStream, source.next())
        source.reconfigure(request(seed = 7L, fixedRoot = fixedRoot))
        assertMatches(expectedNext, source.next())
    }

    @Test
    fun theConfiguredRecipeControlsTheCompiledTimeline() {
        val source = ConfiguredExerciseSource(voiceDurations = { catalog() })
        source.configure(
            request(seed = 3L, recipe = RecipeDefinition.of(RecipeMode.EXPOSURE))
        )
        val exposure = source.compile(
            source.next(),
            RecipeDefinition.of(RecipeMode.EXPOSURE)
        )
        assertEquals(
            "exposure starts with the spoken answer, recognition with the example",
            Duration.ZERO,
            exposure.timeline.voices.single().startOffset
        )
    }

    @Test
    fun explicitRecipesCompileIndependentlyWithoutAnIndexCache() {
        val source = ConfiguredExerciseSource(voiceDurations = { catalog() })
        source.configure(request(seed = 4L))
        val exercise = source.next()

        val recognition = source.compile(exercise, RecipeDefinition.of(RecipeMode.RECOGNITION))
        val exposure = source.compile(exercise, RecipeDefinition.of(RecipeMode.EXPOSURE))
        assertTrue(recognition.timeline.voices.single().startOffset > Duration.ZERO)
        assertEquals(Duration.ZERO, exposure.timeline.voices.single().startOffset)
    }

    @Test
    fun aFixedRootPolicyIsGeneratedByVersionTwo() {
        val source = ConfiguredExerciseSource(voiceDurations = { catalog() })
        source.configure(request(seed = 11L, fixedRoot = MidiNote.of(60)))
        val exercise = source.next()

        assertEquals(MidiNote.of(60), exercise.provenance.legacyIntervalIdentity?.fixedRoot)
        assertEquals(
            "a fixed root is a version 2 sequence",
            2,
            exercise.provenance.providerVersion
        )
    }
}
