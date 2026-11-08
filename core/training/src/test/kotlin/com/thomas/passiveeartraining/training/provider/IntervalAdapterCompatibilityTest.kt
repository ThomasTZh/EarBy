package com.thomas.passiveeartraining.training.provider

import com.thomas.passiveeartraining.music.IntervalPresentation
import com.thomas.passiveeartraining.music.MidiNote
import com.thomas.passiveeartraining.music.PitchClass
import com.thomas.passiveeartraining.training.IntervalDefinition
import com.thomas.passiveeartraining.training.IntervalExercise
import com.thomas.passiveeartraining.training.MapVoiceDurationCatalog
import com.thomas.passiveeartraining.training.NoteEvent
import com.thomas.passiveeartraining.training.SeededIntervalGenerator
import com.thomas.passiveeartraining.training.SeededIntervalGeneratorV2
import com.thomas.passiveeartraining.training.SeededRandomSource
import com.thomas.passiveeartraining.training.VoiceAssetId
import com.thomas.passiveeartraining.training.recipe.RecipeDefinition
import com.thomas.passiveeartraining.training.recipe.RecipeExerciseCompiler
import com.thomas.passiveeartraining.training.recipe.RecipeMode
import com.thomas.passiveeartraining.training.recipe.RecipeTiming
import com.thomas.passiveeartraining.training.recipe.SequencePolicy
import kotlin.time.Duration
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * M1.1: the shared adapter must reproduce the legacy interval output exactly.
 *
 * The legacy generator and compiler are the oracle; every comparison uses them unchanged. Any
 * difference in notes, category, provenance, cycle position, answer, config or compiled timeline
 * fails the suite, which is what "unchanged interval behavior" means before the old APIs may be
 * retired (M1-T01).
 */
class IntervalAdapterCompatibilityTest {

    private val catalog = MapVoiceDurationCatalog(
        IntervalDefinition.default().selectedIntervalIds.associate { id ->
            VoiceAssetId("interval.$id") to Duration.parse("900ms")
        }
    )

    private val seeds = listOf(0L, 1L, 7L, 42L, 1234L)
    private val itemsPerSeed = 5

    private fun policies(): List<Pair<String, SequencePolicy>> {
        val definition = IntervalDefinition.default()
        return listOf(
            "default-v1" to SequencePolicy(definition = definition),
            "fixed-root-v2" to SequencePolicy(
                definition = definition,
                fixedRoot = MidiNote.of(60)
            ),
            "mixed-v2" to SequencePolicy(
                definition = definition,
                presentations = listOf(
                    IntervalPresentation.ASCENDING,
                    IntervalPresentation.DESCENDING,
                    IntervalPresentation.HARMONIC
                ),
                mixedPresentation = true
            )
        )
    }

    private fun legacyGenerator(policy: SequencePolicy, seed: Long) =
        if (policy.generatorVersion == 1) {
            SeededIntervalGenerator(policy.definition, SeededRandomSource(seed))
        } else {
            SeededIntervalGeneratorV2(policy, SeededRandomSource(seed))
        }

    private fun assertSameContent(legacy: IntervalExercise, adapted: ConcreteExercise) {
        assertEquals("sequence index", legacy.sequenceIndex, adapted.sequenceIndex)
        assertEquals("category id", legacy.answerId, adapted.categoryId)
        assertEquals(
            "legacy provenance is preserved losslessly",
            legacy.identity,
            adapted.provenance.legacyIntervalIdentity
        )
        assertEquals(
            "provider version follows the generator version",
            legacy.identity.generatorVersion,
            adapted.provenance.providerVersion
        )
        assertEquals(
            "cycle position",
            CyclePosition(legacy.cycleIndex, legacy.positionInCycle),
            adapted.cycle
        )
        assertEquals(
            "answer voice tokens",
            listOf(VoiceAssetId(VoiceAssetId.ANSWER_ID_PREFIX + legacy.interval.id)),
            adapted.answer.voiceIds
        )
        assertEquals(
            "answer display",
            AnswerDisplay.Interval(legacy.interval.id),
            adapted.answer.display
        )
        assertEquals(
            "sounding notes in play order",
            legacy.notes.map { it.value },
            adapted.groups.flatMap { group -> group.notes.map { it.value } }
        )
        assertEquals(
            "rest slots never replace audible notes",
            legacy.notes.isNotEmpty(),
            adapted.groups.isNotEmpty()
        )
    }

    @Test
    fun everyGoldenSeedMatchesTheLegacySequence() {
        for ((label, policy) in policies()) {
            for (seed in seeds) {
                val legacy = legacyGenerator(policy, seed)
                val provider = IntervalExerciseProvider()
                val adapted = provider.createGenerator(
                    ProviderConfig.Intervals(policy),
                    seed
                )
                repeat(itemsPerSeed) { index ->
                    val expected = legacy.next()
                    val actual = adapted.next()
                    assertEquals(
                        "$label seed=$seed item=$index kind",
                        TrainingKind.INTERVALS,
                        actual.kind
                    )
                    assertSameContent(expected, actual)
                }
            }
        }
    }

    private fun recipes(): List<Pair<String, RecipeDefinition>> {
        val variants = mutableListOf<Pair<String, RecipeDefinition>>()
        val timings = listOf(
            "default" to RecipeTiming(),
            "custom" to RecipeTiming(
                noteDuration = Duration.parse("100ms"),
                melodicGap = Duration.parse("300ms"),
                thinkingSilence = Duration.ZERO,
                postAnswerSilence = Duration.ZERO,
                answerGap = Duration.ZERO,
                repeatGap = Duration.ZERO
            )
        )
        for ((timingLabel, timing) in timings) {
            for (mode in RecipeMode.entries) {
                for (replays in listOf(0, 2)) {
                    for (separator in listOf(false, true)) {
                        variants += "$timingLabel-$mode-replays$replays-sep$separator" to
                            RecipeDefinition(
                                mode = mode,
                                timing = timing,
                                replayCount = replays,
                                separatorCue = separator
                            )
                    }
                }
            }
        }
        return variants
    }

    @Test
    fun everyRecipeCompilesToTheIdenticalTimeline() {
        val legacyCompiler = RecipeExerciseCompiler(catalog)
        val compiler = ExerciseCompiler()
        for ((label, policy) in policies()) {
            for (seed in seeds) {
                val legacy = legacyGenerator(policy, seed)
                val adapted = IntervalExerciseProvider().createGenerator(
                    ProviderConfig.Intervals(policy),
                    seed
                )
                repeat(2) {
                    val legacyExercise = legacy.next()
                    val concrete = adapted.next()
                    for ((recipeLabel, recipe) in recipes()) {
                        val expected = legacyCompiler.compile(legacyExercise, recipe)
                        val actual = compiler.compile(concrete, recipe, catalog)
                        assertEquals(
                            "$label/$recipeLabel seed=$seed",
                            expected,
                            actual.timeline
                        )
                    }
                }
            }
        }
    }

    @Test
    fun compiledBoundariesFollowTheMandatoryUnit() {
        val concrete = IntervalExerciseProvider()
            .createGenerator(
                ProviderConfig.Intervals(SequencePolicy(definition = IntervalDefinition.default())),
                7L
            )
            .next()
        val recipe = RecipeDefinition(
            mode = RecipeMode.RECOGNITION,
            replayCount = 1
        )
        val compiled = ExerciseCompiler().compile(concrete, recipe, catalog)
        val timeline = compiled.timeline
        val answer = timeline.voices.single()
        assertEquals("answer boundary is the first token", answer.startOffset, compiled.answerStart)
        assertEquals(
            "the mandatory unit ends with the answer",
            answer.endOffset,
            compiled.mandatoryEnd
        )
        assertEquals("exactly one optional replay is reported", 1, compiled.replays.size)
        assertEquals(
            "the replay starts at the mandatory end",
            compiled.mandatoryEnd,
            compiled.replays.single().start
        )
        assertEquals(
            "the trailing silence starts after the replay",
            compiled.replays.single().endExclusive,
            compiled.trailingStart
        )
        assertEquals(
            "the timeline ends after the trailing silence",
            timeline.totalDuration,
            compiled.trailingStart + recipe.timing.postAnswerSilence
        )
    }

    @Test
    fun aMelodicUnisonSchedulesTwoNoteOnsetsOfTheSamePitch() {
        val definition = IntervalDefinition.of(
            intervalIds = listOf("unison"),
            rootValues = PitchClass.ALL.map { it.value },
            minimumNote = MidiNote.of(MidiNote.C4),
            maximumNote = MidiNote.of(72),
            presentation = IntervalPresentation.ASCENDING
        )
        val policy = SequencePolicy(definition = definition)
        val seed = 7L
        val concrete = IntervalExerciseProvider()
            .createGenerator(ProviderConfig.Intervals(policy), seed)
            .next()
        assertEquals("both melodic slots sound", 2, concrete.groups.size)
        assertEquals(
            "both slots are target groups",
            listOf(GroupRole.TARGET, GroupRole.TARGET),
            concrete.groups.map { it.role }
        )
        assertEquals(
            "a unison repeats one pitch",
            concrete.groups[0].notes,
            concrete.groups[1].notes
        )
        assertEquals("each slot holds one note", 1, concrete.groups[0].notes.size)

        val unisonCatalog = MapVoiceDurationCatalog(
            mapOf(VoiceAssetId("interval.unison") to Duration.parse("900ms"))
        )
        val recipe = RecipeDefinition(mode = RecipeMode.RECOGNITION)
        val legacy = legacyGenerator(policy, seed).next()
        val expected = RecipeExerciseCompiler(unisonCatalog).compile(legacy, recipe)
        val compiled = ExerciseCompiler().compile(concrete, recipe, unisonCatalog)
        assertEquals("both compilers schedule the unison twice", expected, compiled.timeline)

        val onsets = compiled.timeline.events.filterIsInstance<NoteEvent>()
        assertEquals("the example sounds two note onsets", 2, onsets.size)
        assertEquals(
            "the second onset follows one melodic stride",
            recipe.timing.noteDuration + recipe.timing.melodicGap,
            onsets[1].startOffset
        )
        assertEquals("the onsets share one pitch", onsets[0].note, onsets[1].note)
        assertEquals(
            "the answer voice still names the unison",
            VoiceAssetId("interval.unison"),
            concrete.answer.voiceIds.single()
        )
    }

    @Test
    fun aSeparatorRecipeReportsItsTailInsteadOfAFinalSilence() {
        val concrete = IntervalExerciseProvider()
            .createGenerator(
                ProviderConfig.Intervals(SequencePolicy(definition = IntervalDefinition.default())),
                11L
            )
            .next()
        val recipe = RecipeDefinition(mode = RecipeMode.RECOGNITION, separatorCue = true)
        val compiled = ExerciseCompiler().compile(concrete, recipe, catalog)
        val tail = compiled.timeline.events
            .filter { it.startOffset >= compiled.trailingStart }
            .filterIsInstance<com.thomas.passiveeartraining.training.NoteEvent>()
        assertEquals("the tail is the two-note separator", 2, tail.size)
        assertTrue("a separator recipe has no replay units", compiled.replays.isEmpty())
        assertEquals(compiled.mandatoryEnd, compiled.trailingStart)
    }
}
