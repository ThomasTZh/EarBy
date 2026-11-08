package com.thomas.passiveeartraining.training.provider

import com.thomas.passiveeartraining.music.ChordInterpretation
import com.thomas.passiveeartraining.music.ChordPresentation
import com.thomas.passiveeartraining.music.ChordQuality
import com.thomas.passiveeartraining.music.MidiNote
import com.thomas.passiveeartraining.training.ChordAnswerMode
import com.thomas.passiveeartraining.training.ChordDefinition
import com.thomas.passiveeartraining.training.MapVoiceDurationCatalog
import kotlin.time.Duration.Companion.milliseconds
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** M5.1/M5-T04/M5-T05: the chord provider's finite draws and equivalent answers. */
class ChordExerciseProviderTest {

    private val provider = ChordExerciseProvider()

    private val everyChordClip: MapVoiceDurationCatalog = MapVoiceDurationCatalog.of(
        *buildList {
            ChordQuality.entries.forEach { add(VoiceVocabulary.chordQualityEntry(it).id.value) }
            (0..3).forEach { add(VoiceVocabulary.chordInversionEntry(it).id.value) }
            add(VoiceVocabulary.OR.id.value)
        }.map { it to 500.milliseconds }.toTypedArray()
    )

    private fun definition(
        qualityIds: List<String> = listOf("major", "minor"),
        inversions: List<Int> = listOf(0),
        roots: List<Int> = (0..11).toList(),
        fixedRoot: MidiNote? = null,
        presentation: ChordPresentation = ChordPresentation.HARMONIC,
        answerMode: ChordAnswerMode = ChordAnswerMode.QUALITY
    ): ChordDefinition = ChordDefinition.of(
        qualityIds = qualityIds,
        inversions = inversions,
        rootValues = roots,
        fixedRoot = fixedRoot,
        minimumNote = MidiNote.of(48),
        maximumNote = MidiNote.of(84),
        presentation = presentation,
        answerMode = answerMode
    )

    private fun firstExercises(
        seed: Long,
        count: Int,
        config: ProviderConfig = provider.defaults()
    ) = provider.createGenerator(config, seed).let { generator ->
        (1..count).map { generator.next() }
    }

    @Test
    fun theDefaultConfigurationValidatesWithEveryChordClip() {
        val result = provider.validate(provider.defaults(), everyChordClip)
        assertTrue("default chord setup must validate: $result", result is ProviderValidation.Valid)
        val valid = result as ProviderValidation.Valid
        assertEquals(listOf("chord.major", "chord.minor"), valid.categories.map { it.id })
        assertEquals(
            setOf("chord.major", "chord.minor", "connector.or"),
            valid.requiredAssets.map { it.value }.toSet()
        )
    }

    @Test
    fun aMissingQualityClipIsReportedBeforeTheFirstDraw() {
        val partial = MapVoiceDurationCatalog.of(
            "chord.major" to 500.milliseconds,
            "connector.or" to 300.milliseconds
        )
        val result = provider.validate(provider.defaults(), partial)
        assertTrue(result is ProviderValidation.Invalid)
        val issues = (result as ProviderValidation.Invalid).issues
        assertTrue(
            "the missing minor clip must be named: $issues",
            issues.any { it.message.contains("chord.minor") }
        )
    }

    @Test
    fun qualityAndInversionModeAlsoRequiresTheInversionClips() {
        val config = ProviderConfig.Chords(
            definition(answerMode = ChordAnswerMode.QUALITY_AND_INVERSION)
        )
        assertEquals(
            setOf(
                "chord.major",
                "chord.minor",
                "chord.inversion.root",
                "connector.or"
            ),
            provider.requiredVocabulary(config).map { it.value }.toSet()
        )
        val partial = MapVoiceDurationCatalog.of(
            "chord.major" to 500.milliseconds,
            "chord.minor" to 500.milliseconds,
            "connector.or" to 300.milliseconds
        )
        val result = provider.validate(config, partial)
        assertTrue(result is ProviderValidation.Invalid)
        assertTrue(
            (result as ProviderValidation.Invalid).issues
                .any { it.message.contains("chord.inversion.root") }
        )
    }

    @Test
    fun everyRealizationUsesSelectedRootsInsideTheConfiguredRange() {
        val limited = definition(qualityIds = listOf("major"), roots = listOf(0, 7))
        val realizations = ChordCandidates.realizations(
            limited,
            ChordInterpretation(ChordQuality.MAJOR, 0)
        )
        assertTrue("C and G roots exist in C3..C6", realizations.isNotEmpty())
        assertTrue(
            "only selected root classes",
            realizations.all { it.theoreticalRoot.midiValue % 12 in setOf(0, 7) }
        )
        assertTrue(
            "every sounding note fits the packaged instrument range",
            realizations.all { voicing -> voicing.notes.all { it.value in 21..108 } }
        )
        assertEquals(
            "roots enumerate in ascending order",
            realizations.map { it.theoreticalRoot.midiValue }.sorted(),
            realizations.map { it.theoreticalRoot.midiValue }
        )
    }

    @Test
    fun aFixedRootAtTheTopIsAcceptedWhenItsChordFits() {
        // A minor triad on C6 extends above the old C3..C6 sounding range but fits C8.
        val config = ProviderConfig.Chords(
            definition(
                qualityIds = listOf("minor"),
                fixedRoot = MidiNote.of(84)
            )
        )
        assertTrue(provider.validate(config, everyChordClip) is ProviderValidation.Valid)
    }

    @Test
    fun aFixedRootWhoseChordLeavesTheInstrumentIsRejected() {
        val config = ProviderConfig.Chords(
            ChordDefinition.of(
                qualityIds = listOf("major"),
                inversions = listOf(0),
                rootValues = (0..11).toList(),
                fixedRoot = MidiNote.of(108),
                minimumNote = MidiNote.of(105),
                maximumNote = MidiNote.of(108),
                presentation = ChordPresentation.HARMONIC,
                answerMode = ChordAnswerMode.QUALITY
            )
        )
        val result = provider.validate(config, everyChordClip)
        assertTrue(result is ProviderValidation.Invalid)
        assertTrue(
            (result as ProviderValidation.Invalid).issues
                .any { it.field == "range" && it.message.contains("major") }
        )
    }

    @Test
    fun oneBagPlaysEveryCategoryExactlyOnce() {
        val exercises = firstExercises(seed = 7L, count = 2)
        assertEquals(
            setOf("chord.major", "chord.minor"),
            exercises.map { it.categoryId }.toSet()
        )
        assertEquals(
            "the two default categories fill one bag",
            listOf(0, 1),
            exercises.map { it.cycle!!.positionInCycle }
        )
        assertEquals(listOf(0, 0), exercises.map { it.cycle!!.cycleIndex })
    }

    @Test
    fun theSameSeedRepeatsTheBinarySequenceAndDifferentSeedsDiverge() {
        fun fingerprint(seed: Long) = firstExercises(seed, 8).map {
            listOf(it.categoryId, it.provenance.generatedChord!!.soundingValues) to
                it.answer.voiceIds.map { token -> token.value }
        }
        assertEquals(fingerprint(11L), fingerprint(11L))
        assertNotEquals(fingerprint(11L), fingerprint(12L))
    }

    @Test
    fun harmonicExamplesSoundOneGroupAndArpeggiosOneNotePerGroup() {
        val harmonic = firstExercises(1L, 1, ProviderConfig.Chords(definition())).single()
        assertTrue("a harmonic chord is one simultaneous group", harmonic.targetGroups.size == 1)
        assertEquals(3, harmonic.targetGroups.single().notes.size)

        val arpeggio = firstExercises(
            1L,
            1,
            ProviderConfig.Chords(
                definition(presentation = ChordPresentation.ARPEGGIO, fixedRoot = MidiNote.of(60))
            )
        ).single()
        assertEquals(listOf(60, 64, 67), arpeggio.targetGroups.map { it.notes.single().value })
    }

    @Test
    fun symmetricAugmentedReadingsBecomeOneSpokenOrAnswer() {
        val config = ProviderConfig.Chords(
            definition(
                qualityIds = listOf("augmented"),
                inversions = (0..2).toList(),
                fixedRoot = MidiNote.of(60),
                answerMode = ChordAnswerMode.QUALITY_AND_INVERSION
            )
        )
        val exercise = firstExercises(seed = 3L, count = 1, config = config).single()
        val tokens = exercise.answer.voiceIds.map { it.value }
        assertEquals(3, tokens.count { it == "chord.augmented" })
        assertEquals(2, tokens.count { it == "connector.or" })
        assertEquals(
            "quality and inversion stay adjacent in every phrase",
            listOf(
                "chord.augmented",
                "chord.inversion.root",
                "chord.augmented",
                "chord.inversion.first",
                "chord.augmented",
                "chord.inversion.second"
            ),
            tokens.filter { it != "connector.or" }
        )
        assertEquals(
            "augmented root position or augmented first inversion or augmented second inversion",
            "augmented root position or augmented first inversion or augmented second inversion",
            exercise.answer.display.label()
        )
    }

    @Test
    fun qualityOnlyAnswersDedupeSymmetricReadings() {
        val config = ProviderConfig.Chords(
            definition(
                qualityIds = listOf("augmented"),
                inversions = (0..2).toList(),
                fixedRoot = MidiNote.of(60)
            )
        )
        val exercise = firstExercises(seed = 5L, count = 1, config = config).single()
        assertEquals(listOf("chord.augmented"), exercise.answer.voiceIds.map { it.value })
        assertEquals("augmented", exercise.answer.display.label())
    }

    @Test
    fun aFixedRootCategoryIsDeterministicAcrossDraws() {
        val config = ProviderConfig.Chords(
            definition(
                qualityIds = listOf("major_seventh"),
                inversions = (0..3).toList(),
                fixedRoot = MidiNote.of(60)
            )
        )
        val exercises = firstExercises(seed = 9L, count = 4, config = config)
        assertEquals(
            "one realization exists per inversion",
            exercises.map { it.provenance.generatedChord!! }.map { it.inversion }.toSet(),
            setOf(0, 1, 2, 3)
        )
        assertTrue(
            exercises.all { it.provenance.generatedChord!!.theoreticalRootMidi == 60 }
        )
    }

    @Test
    fun fixedAndClassRootSelectionsFilterRealizations() {
        val config = ProviderConfig.Chords(
            definition(qualityIds = listOf("major"), roots = listOf(0), fixedRoot = MidiNote.of(48))
        )
        // C3 major fits; the root class is explicitly allowed, so validation passes.
        assertTrue(provider.validate(config, everyChordClip) is ProviderValidation.Valid)

        val contradictory = ChordDefinition.of(
            qualityIds = listOf("major"),
            inversions = listOf(0),
            rootValues = listOf(1),
            fixedRoot = null,
            minimumNote = MidiNote.of(48),
            maximumNote = MidiNote.of(84),
            presentation = ChordPresentation.HARMONIC,
            answerMode = ChordAnswerMode.QUALITY
        )
        val realizations = ChordCandidates.realizations(
            contradictory,
            ChordInterpretation(ChordQuality.MAJOR, 0)
        )
        assertTrue("C# roots exist in range; the selection still works", realizations.isNotEmpty())
        assertTrue(realizations.all { it.theoreticalRoot.midiValue % 12 == 1 })
    }

    @Test
    fun frozenDrawVectorsForSeed42StayStable() {
        val expected = listOf(
            "chord.major|62,66,69|chord.major",
            "chord.minor|50,53,57|chord.minor",
            "chord.minor|59,62,66|chord.minor",
            "chord.major|56,60,63|chord.major",
            "chord.minor|62,65,69|chord.minor",
            "chord.major|68,72,75|chord.major",
            "chord.minor|75,78,82|chord.minor",
            "chord.major|69,73,76|chord.major"
        )
        val actual = firstExercises(seed = 42L, count = 8).map { exercise ->
            val chord = exercise.provenance.generatedChord!!
            "${exercise.categoryId}|${chord.soundingValues.joinToString(",")}|" +
                exercise.answer.voiceIds.joinToString(",") { it.value }
        }
        assertEquals(expected, actual)
    }

    @Test
    fun voiceIdsAreSemanticChordTokens() {
        val exercise = firstExercises(1L, 1).single()
        assertTrue(
            "answers stay inside the chord vocabulary",
            exercise.answer.voiceIds.all { it.value.startsWith("chord.") }
        )
        assertEquals(
            "the drawn category is a semantic chord id",
            "chord." + exercise.provenance.generatedChord!!.qualityId,
            exercise.categoryId
        )
    }

    @Test
    fun reconfigureContinuesTheSameStreamWithoutReplayingTheOldBag() {
        val generator = provider.createGenerator(provider.defaults(), 21L)
        generator.next()
        generator.next()
        generator.reconfigure(
            ProviderConfig.Chords(definition(qualityIds = listOf("major", "minor", "sus4")))
        )
        val after = generator.next()
        assertTrue(
            "the edited category is reachable",
            after.categoryId in setOf("chord.major", "chord.minor", "chord.sus4")
        )
        assertTrue("the sequence index keeps moving", after.sequenceIndex == 2)
    }
}
