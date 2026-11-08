package com.thomas.passiveeartraining.training.provider

import com.thomas.passiveeartraining.music.MidiNote
import com.thomas.passiveeartraining.training.BundledVoiceDurations
import com.thomas.passiveeartraining.training.IntervalWalkDefinition
import com.thomas.passiveeartraining.training.MapVoiceDurationCatalog
import com.thomas.passiveeartraining.training.VoiceAssetId
import com.thomas.passiveeartraining.training.WalkDirection
import kotlin.time.Duration.Companion.milliseconds
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class IntervalWalkExerciseProviderTest {

    private val provider = IntervalWalkExerciseProvider()

    private fun config(definition: IntervalWalkDefinition = IntervalWalkDefinition.default()) =
        ProviderConfig.IntervalWalks(definition)

    @Test
    fun theDefaultConfigurationValidates() {
        val result = provider.validate(config(), BundledVoiceDurations)
        assertTrue("default walk must validate: $result", result is ProviderValidation.Valid)
        val valid = result as ProviderValidation.Valid
        assertEquals(listOf("interval_walk"), valid.categories.map { it.id })
        assertTrue(valid.maximumAnswerDuration > 0.milliseconds)
        assertTrue(valid.maximumExampleDuration > 0.milliseconds)
    }

    @Test
    fun everyGeneratedWalkIsCompleteAndUsesTheConfiguredDirection() {
        val definition = IntervalWalkDefinition.of(
            direction = WalkDirection.MIXED,
            length = 6
        )
        val generator = provider.createGenerator(config(definition), 77L)
        repeat(50) {
            val exercise = generator.next()
            val walk = exercise.provenance.generatedIntervalWalk!!
            assertEquals(TrainingKind.INTERVAL_WALK, exercise.kind)
            assertEquals(7, walk.notes.size)
            assertEquals(6, walk.steps.size)
            assertTrue(walk.steps.all { it in -12..12 })
            assertTrue(walk.notes.all { it in 21..108 })
            assertTrue(exercise.answer.voiceIds.isNotEmpty())
            assertEquals("interval_walk", exercise.categoryId)
        }
    }

    @Test
    fun requiredVocabularyIncludesIntervalPhrasesAndDirectionConnectors() {
        val required = provider.requiredVocabulary(config())
        assertTrue(VoiceAssetId.forInterval("major_third") in required)
        assertTrue(VoiceAssetId.forInterval("unison") in required)
        assertTrue(VoiceVocabulary.ASCENDING.id in required)
        assertTrue(VoiceVocabulary.DESCENDING.id in required)
    }

    @Test
    fun aMissingDirectionClipIsAnAnswerIssue() {
        val partial = MapVoiceDurationCatalog.of(
            "interval.major_third" to 300.milliseconds,
            "interval.minor_third" to 300.milliseconds,
            "interval.perfect_fifth" to 300.milliseconds,
            "interval.ascending" to 300.milliseconds
        )
        val result = provider.validate(
            config(
                IntervalWalkDefinition.of(
                    intervalIds = listOf("major_third", "minor_third", "perfect_fifth")
                )
            ),
            partial
        )
        assertTrue(result is ProviderValidation.Invalid)
        assertTrue(
            (result as ProviderValidation.Invalid).issues.any {
                it.field == "answers" && it.message.contains("connector.descending")
            }
        )
    }

    @Test
    fun aStartRangeWithNoCompleteWalkIsARangeIssue() {
        val definition = IntervalWalkDefinition.of(
            intervalIds = listOf("perfect_fifth"),
            minimumNote = MidiNote.of(108),
            maximumNote = MidiNote.of(108)
        )
        val result = provider.validate(config(definition), BundledVoiceDurations)
        assertTrue(result is ProviderValidation.Invalid)
        assertTrue(
            (result as ProviderValidation.Invalid).issues.any {
                it.field == "range" && it.message.contains("No complete interval walk")
            }
        )
    }

    @Test
    fun theSameSeedReproducesTheSameWalk() {
        val first = provider.createGenerator(config(), 11L)
        val second = provider.createGenerator(config(), 11L)
        repeat(8) {
            assertEquals(first.next(), second.next())
        }
    }
}
