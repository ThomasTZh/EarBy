package com.thomas.passiveeartraining.training.provider

import com.thomas.passiveeartraining.music.MidiNote
import com.thomas.passiveeartraining.music.ScaleDirection
import com.thomas.passiveeartraining.music.ScaleKind
import com.thomas.passiveeartraining.music.ScaleTraversal
import com.thomas.passiveeartraining.music.SpelledPitch
import com.thomas.passiveeartraining.music.SpellingPreference
import com.thomas.passiveeartraining.training.MapVoiceDurationCatalog
import com.thomas.passiveeartraining.training.ScaleDefinition
import kotlin.time.Duration.Companion.milliseconds
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** M6.1/M6-T01..T03: the scale provider's finite draws and complete-octave traversals. */
class ScaleExerciseProviderTest {

    private val provider = ScaleExerciseProvider()

    private val everyScaleClip: MapVoiceDurationCatalog = MapVoiceDurationCatalog.of(
        *ScaleKind.entries.map { VoiceVocabulary.scaleEntry(it).id.value to 900.milliseconds }
            .toTypedArray()
    )

    private fun definition(
        scaleIds: List<String> = listOf("major", "natural_minor"),
        direction: ScaleDirection = ScaleDirection.ASCENDING,
        roots: List<Int> = (0..11).toList(),
        fixedRoot: MidiNote? = null,
        minimumNote: MidiNote = MidiNote.of(48),
        maximumNote: MidiNote = MidiNote.of(84)
    ): ScaleDefinition = ScaleDefinition.of(
        scaleIds = scaleIds,
        direction = direction,
        rootValues = roots,
        fixedRoot = fixedRoot,
        minimumNote = minimumNote,
        maximumNote = maximumNote
    )

    private fun firstExercises(
        seed: Long,
        count: Int,
        config: ProviderConfig = provider.defaults()
    ) = provider.createGenerator(config, seed).let { generator ->
        (1..count).map { generator.next() }
    }

    private fun traversalValues(kind: ScaleKind, tonic: Int, direction: ScaleDirection) =
        ScaleTraversal.pitches(
            kind,
            SpelledPitch.fromMidi(MidiNote.of(tonic), SpellingPreference.SHARP),
            direction
        ).map { it.midiValue }

    @Test
    fun theProviderIdentityIsTheScalesKindAtVersionOne() {
        assertEquals(TrainingKind.SCALES, provider.kind)
        assertEquals(1, provider.providerVersion)
        val exercise = firstExercises(seed = 1L, count = 1).single()
        assertEquals(TrainingKind.SCALES, exercise.kind)
        assertEquals(
            "scale." + exercise.provenance.generatedScale!!.scaleId,
            exercise.categoryId
        )
    }

    @Test
    fun theDefaultConfigurationValidatesWithEveryScaleClip() {
        val result = provider.validate(provider.defaults(), everyScaleClip)
        assertTrue("default scale setup must validate: $result", result is ProviderValidation.Valid)
        val valid = result as ProviderValidation.Valid
        assertEquals(listOf("scale.major", "scale.natural_minor"), valid.categories.map { it.id })
        assertEquals(
            setOf("scale.major", "scale.natural_minor"),
            valid.requiredAssets.map { it.value }.toSet()
        )
        assertEquals(
            "eight ascending notes at 700 ms plus seven 100 ms gaps",
            6300L,
            valid.maximumExampleDuration.inWholeMilliseconds
        )
        assertEquals(
            "a scale answer is one name clip",
            900L,
            valid.maximumAnswerDuration.inWholeMilliseconds
        )
    }

    @Test
    fun aMissingScaleClipIsReportedBeforeTheFirstDraw() {
        val partial = MapVoiceDurationCatalog.of("scale.major" to 900.milliseconds)
        val result = provider.validate(provider.defaults(), partial)
        assertTrue(result is ProviderValidation.Invalid)
        val issues = (result as ProviderValidation.Invalid).issues
        assertTrue(
            "the missing clip must be an answer issue: $issues",
            issues.all {
                it.field == "answers"
            }
        )
        assertTrue(
            "the missing minor clip must be named: $issues",
            issues.any { it.message.contains("scale.natural_minor") }
        )
    }

    @Test
    fun aNarrowRootRangeKeepsCompleteScalesThroughTheirUpperNotes() {
        val config = ProviderConfig.Scales(
            definition(
                scaleIds = listOf("major"),
                minimumNote = MidiNote.of(60),
                maximumNote = MidiNote.of(70)
            )
        )
        val result = provider.validate(config, everyScaleClip)
        assertTrue(result is ProviderValidation.Valid)
        val tonics = ScaleCandidates.tonics(config.definition, ScaleKind.MAJOR)
        assertTrue(tonics.isNotEmpty())
        assertTrue(tonics.all { it.value in 60..70 })
        assertTrue(
            "the complete scale may extend above the root range",
            tonics.all { tonic ->
                traversalValues(ScaleKind.MAJOR, tonic.value, ScaleDirection.ASCENDING)
                    .all { it in 21..108 }
            }
        )
    }

    @Test
    fun aTwelveSemitoneRootRangeKeepsEverySelectedTonic() {
        val config = ProviderConfig.Scales(
            definition(
                scaleIds = listOf("major"),
                minimumNote = MidiNote.of(60),
                maximumNote = MidiNote.of(72)
            )
        )
        assertTrue(provider.validate(config, everyScaleClip) is ProviderValidation.Valid)
        assertEquals(
            (60..72).toList(),
            ScaleCandidates.tonics(config.definition, ScaleKind.MAJOR).map { it.value }
        )
    }

    @Test
    fun aliasesNormalizeToCanonicalScalesWithOneBagEach() {
        val config = ProviderConfig.Scales(
            definition(scaleIds = listOf("ionian", "aeolian", "major"))
        )
        assertEquals(
            listOf(ScaleKind.MAJOR, ScaleKind.NATURAL_MINOR),
            config.definition.selectedScales
        )
        val exercises = firstExercises(seed = 7L, count = 4, config = config)
        assertEquals(
            "the first bag contains each canonical scale exactly once",
            setOf("scale.major", "scale.natural_minor"),
            exercises.take(2).map { it.categoryId }.toSet()
        )
        assertEquals(listOf(0, 1), exercises.take(2).map { it.cycle!!.positionInCycle })
        assertEquals(
            "aliases add no extra bag slots",
            listOf(0, 0, 1, 1),
            exercises.map { it.cycle!!.cycleIndex }
        )
    }

    @Test
    fun ascendingDescendingAndBothEmitTheDocumentedLengths() {
        val ascending = single(ScaleDirection.ASCENDING)
        assertEquals(8, ascending.targetGroups.size)
        assertEquals(
            listOf(60, 62, 64, 65, 67, 69, 71, 72),
            ascending.provenance.generatedScale!!.soundingValues
        )
        assertTrue(
            "every target is one sequential note",
            ascending.groups.all { it.role == GroupRole.TARGET && it.notes.size == 1 }
        )
        assertTrue("there is no contextual prelude", ascending.contextGroups.isEmpty())

        val descending = single(ScaleDirection.DESCENDING)
        assertEquals(8, descending.targetGroups.size)
        assertEquals(
            "descending starts on the top tonic",
            listOf(72, 71, 69, 67, 65, 64, 62, 60),
            descending.provenance.generatedScale!!.soundingValues
        )

        val both = single(ScaleDirection.BOTH)
        assertEquals(15, both.targetGroups.size)
        val values = both.provenance.generatedScale!!.soundingValues
        assertEquals("the lower tonic sounds at both ends", values.first(), values.last())
        assertEquals("the turning tonic sounds exactly once", 1, values.count { it == 72 })
        assertEquals(72, values[7])
        assertEquals(
            "one note per sequential target group",
            values,
            both.targetGroups.map { it.notes.single().value }
        )
    }

    private fun single(direction: ScaleDirection): ConcreteExercise = firstExercises(
        seed = 1L,
        count = 1,
        config = ProviderConfig.Scales(
            definition(
                scaleIds = listOf("major"),
                direction = direction,
                fixedRoot = MidiNote.of(60)
            )
        )
    ).single()

    @Test
    fun jazzMelodicMinorDescendingUsesTheSameCollection() {
        val config = ProviderConfig.Scales(
            definition(
                scaleIds = listOf("jazz_melodic_minor"),
                direction = ScaleDirection.DESCENDING,
                fixedRoot = MidiNote.of(60)
            )
        )
        val values = firstExercises(seed = 3L, count = 1, config = config).single()
            .provenance.generatedScale!!.soundingValues
        assertEquals(
            listOf(72, 71, 69, 67, 65, 63, 62, 60),
            values
        )
        assertEquals(
            "the descent is the reversed ascent, not a classical variant",
            traversalValues(ScaleKind.JAZZ_MELODIC_MINOR, 60, ScaleDirection.ASCENDING).toSet(),
            values.toSet()
        )
    }

    @Test
    fun tonicEnumerationIsAscendingAndRestrictedToSelectedRoots() {
        val definition = definition(scaleIds = listOf("major"), roots = listOf(0, 7))
        val tonics = ScaleCandidates.tonics(definition, ScaleKind.MAJOR)
        assertEquals(listOf(48, 55, 60, 67, 72, 79, 84), tonics.map { it.value })
        assertTrue(tonics.all { it.pitchClass.value in setOf(0, 7) })
        assertTrue(
            "every emitted note stays inside the playable samples",
            tonics.all { tonic ->
                traversalValues(ScaleKind.MAJOR, tonic.value, ScaleDirection.ASCENDING)
                    .all { it in 21..108 }
            }
        )
    }

    @Test
    fun aFixedRootIsDeterministicAcrossDraws() {
        val exercises = firstExercises(
            seed = 9L,
            count = 4,
            config = ProviderConfig.Scales(definition(fixedRoot = MidiNote.of(60)))
        )
        assertTrue(
            "the lower tonic is the fixed root in every draw",
            exercises.all { it.provenance.generatedScale!!.tonicMidi == 60 }
        )
        assertTrue(
            "the traversal starts on the fixed root",
            exercises.all { it.provenance.generatedScale!!.soundingValues.first() == 60 }
        )
    }

    @Test
    fun theSameSeedRepeatsTheSequenceAndDifferentSeedsDiverge() {
        fun fingerprint(seed: Long) = firstExercises(seed, 8).map {
            val scale = it.provenance.generatedScale!!
            listOf(it.categoryId, scale.direction, scale.tonicMidi, scale.soundingValues) to
                it.answer.voiceIds.map { token -> token.value }
        }
        assertEquals(fingerprint(11L), fingerprint(11L))
        assertNotEquals(fingerprint(11L), fingerprint(12L))
    }

    @Test
    fun frozenDrawVectorsForSeed42StayStable() {
        val expected = listOf(
            "scale.major|ASCENDING|62|62,64,66,67,69,71,73,74|scale.major",
            "scale.natural_minor|ASCENDING|50|50,52,53,55,57,58,60,62|scale.natural_minor",
            "scale.natural_minor|ASCENDING|59|59,61,62,64,66,67,69,71|scale.natural_minor",
            "scale.major|ASCENDING|56|56,58,60,61,63,65,67,68|scale.major",
            "scale.natural_minor|ASCENDING|62|62,64,65,67,69,70,72,74|scale.natural_minor",
            "scale.major|ASCENDING|68|68,70,72,73,75,77,79,80|scale.major",
            "scale.natural_minor|ASCENDING|75|75,77,78,80,82,83,85,87|scale.natural_minor",
            "scale.major|ASCENDING|69|69,71,73,74,76,78,80,81|scale.major"
        )
        val actual = firstExercises(seed = 42L, count = 8).map { exercise ->
            val scale = exercise.provenance.generatedScale!!
            "${exercise.categoryId}|${scale.direction}|${scale.tonicMidi}|" +
                "${scale.soundingValues.joinToString(",")}|" +
                exercise.answer.voiceIds.joinToString(",") { it.value }
        }
        assertEquals(expected, actual)
    }

    @Test
    fun drawsConsumeOneTonicValueEvenWhenOnlyOneTonicIsFeasible() {
        // A fixed root leaves exactly one feasible tonic per scale; the SC3 stream still spends one
        // draw per item, so the next cycle's bag shuffle continues from the documented position.
        val config = ProviderConfig.Scales(
            definition(
                scaleIds = listOf("major", "whole_tone"),
                roots = listOf(0),
                fixedRoot = MidiNote.of(60)
            )
        )
        val seed = 17L
        val expected = mutableListOf<String>()
        val random = java.util.Random(seed)
        repeat(4) {
            val fresh = listOf(ScaleKind.MAJOR, ScaleKind.WHOLE_TONE).toMutableList()
            for (index in fresh.lastIndex downTo 1) {
                val draw = random.nextInt(index + 1)
                val current = fresh[index]
                fresh[index] = fresh[draw]
                fresh[draw] = current
            }
            fresh.forEach { kind ->
                random.nextInt(1)
                expected += kind.id
            }
        }
        val actual = firstExercises(seed, expected.size, config)
            .map { it.provenance.generatedScale!!.scaleId }
        assertEquals(expected, actual)
    }

    @Test
    fun reconfigureContinuesTheSameStreamWithoutReplayingTheOldBag() {
        val generator = provider.createGenerator(provider.defaults(), 21L)
        generator.next()
        generator.next()
        generator.reconfigure(
            ProviderConfig.Scales(
                definition(scaleIds = listOf("major", "dorian", "whole_tone"))
            )
        )
        val after = generator.next()
        assertTrue(
            "the edited category is reachable",
            after.categoryId in setOf("scale.major", "scale.dorian", "scale.whole_tone")
        )
        assertEquals("the sequence index keeps moving", 2, after.sequenceIndex)
    }

    @Test
    fun theAnswerTokenAndDisplayUseTheCanonicalScaleName() {
        val exercise = firstExercises(
            seed = 1L,
            count = 1,
            config = ProviderConfig.Scales(
                definition(scaleIds = listOf("jazz_melodic_minor"), fixedRoot = MidiNote.of(60))
            )
        ).single()
        assertEquals(listOf("scale.jazz_melodic_minor"), exercise.answer.voiceIds.map { it.value })
        val display = exercise.answer.display
        assertTrue(display is AnswerDisplay.Scale)
        assertEquals("jazz melodic minor", display.label())
        assertEquals("mystery mode", AnswerDisplay.Scale("mystery_mode").label())
    }

    @Test
    fun everyScaleHasAFeasibleTonicForTheDefaultRangeAndRoots() {
        val default = ScaleDefinition.default()
        ScaleKind.entries.forEach { kind ->
            assertTrue(
                "scale ${kind.id} must have a feasible tonic in C3..C6 with all roots",
                ScaleCandidates.tonics(default, kind).isNotEmpty()
            )
        }
        val allScales = ProviderConfig.Scales(
            definition(scaleIds = ScaleKind.entries.map { it.id })
        )
        assertTrue(
            "a full-catalog selection validates",
            provider.validate(allScales, everyScaleClip) is ProviderValidation.Valid
        )
    }

    @Test
    fun everyFormulaAndRootClassHasACompleteFittingOctave() {
        for (kind in ScaleKind.entries) {
            for (root in 0..11) {
                for (direction in ScaleDirection.entries) {
                    val definition = definition(
                        scaleIds = listOf(kind.id),
                        direction = direction,
                        roots = listOf(root)
                    )
                    val tonics = ScaleCandidates.tonics(definition, kind)
                    if (kind == ScaleKind.WHOLE_TONE && root == 10) {
                        assertTrue(
                            "whole tone on A# is the frozen M4 unrepresentable fixture",
                            tonics.isEmpty()
                        )
                        continue
                    }
                    assertTrue("$kind root $root $direction must have a tonic", tonics.isNotEmpty())
                    tonics.forEach { tonic ->
                        assertEquals(root, tonic.pitchClass.value)
                        val values = traversalValues(kind, tonic.value, direction)
                        val expectedFirst = when (direction) {
                            ScaleDirection.ASCENDING, ScaleDirection.BOTH -> tonic.value
                            ScaleDirection.DESCENDING -> tonic.value + 12
                        }
                        assertEquals(
                            "the traversal starts on the direction's first tonic",
                            expectedFirst,
                            values.first()
                        )
                        assertTrue(
                            "both tonics sound",
                            tonic.value in values && tonic.value + 12 in values
                        )
                        assertTrue("$kind from $tonic stays in range", values.all { it in 21..108 })
                    }
                }
            }
        }
    }

    @Test
    fun anUnrepresentableSharpTableTonicIsExcludedRatherThanRelabelled() {
        // The frozen M4 scale fixtures document whole tone on A# as unrepresentable. With only
        // that root class selected the definition is infeasible; nothing is respelled or wrapped.
        val config = ProviderConfig.Scales(
            definition(scaleIds = listOf("whole_tone"), roots = listOf(10))
        )
        val result = provider.validate(config, everyScaleClip)
        assertTrue(result is ProviderValidation.Invalid)
        assertTrue(
            "the excluded tonic is a range issue: " +
                (result as ProviderValidation.Invalid).issues,
            result.issues.all { it.field == "range" }
        )
        assertTrue(ScaleCandidates.tonics(config.definition, ScaleKind.WHOLE_TONE).isEmpty())

        val withC = ProviderConfig.Scales(
            definition(scaleIds = listOf("whole_tone"), roots = listOf(0, 10))
        )
        assertTrue(provider.validate(withC, everyScaleClip) is ProviderValidation.Valid)
        assertTrue(
            "only the representable root class is enumerated",
            ScaleCandidates.tonics(withC.definition, ScaleKind.WHOLE_TONE)
                .all { it.pitchClass.value == 0 }
        )
    }

    @Test
    fun theBothDirectionBoundCoversTheWholeTraversal() {
        val valid = provider.validate(
            ProviderConfig.Scales(definition(direction = ScaleDirection.BOTH)),
            everyScaleClip
        ) as ProviderValidation.Valid
        assertEquals(
            "fifteen notes at 700 ms plus fourteen 100 ms gaps",
            11900L,
            valid.maximumExampleDuration.inWholeMilliseconds
        )
    }
}
