package com.thomas.passiveeartraining.training.settings

import com.thomas.passiveeartraining.music.ChordPresentation
import com.thomas.passiveeartraining.music.ChordQuality
import com.thomas.passiveeartraining.training.ChordAnswerMode
import com.thomas.passiveeartraining.training.MapVoiceDurationCatalog
import com.thomas.passiveeartraining.training.provider.ChordExerciseProvider
import com.thomas.passiveeartraining.training.provider.ProviderConfig
import com.thomas.passiveeartraining.training.provider.ProviderValidation
import com.thomas.passiveeartraining.training.provider.TrainingKind
import com.thomas.passiveeartraining.training.provider.VoiceVocabulary
import com.thomas.passiveeartraining.training.recipe.DurationFinish
import com.thomas.passiveeartraining.training.recipe.SessionStopPolicy
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** M5-T07/M5-T06: the chord setup, its draft and its validator. */
class ChordSetupValidatorTest {

    private val clipIds: List<String> = buildList {
        ChordQuality.entries.forEach { add(VoiceVocabulary.chordQualityEntry(it).id.value) }
        (0..3).forEach { add(VoiceVocabulary.chordInversionEntry(it).id.value) }
        add(VoiceVocabulary.OR.id.value)
    }

    private fun catalog(clipLength: kotlin.time.Duration = 500.milliseconds) =
        MapVoiceDurationCatalog.of(*clipIds.map { it to clipLength }.toTypedArray())

    private fun draft(
        qualityIds: List<String> = listOf("major", "minor"),
        inversions: List<Int> = listOf(0),
        rootValues: List<Int> = emptyList(),
        minimumNote: Int = 48,
        maximumNote: Int = 84,
        presentation: ChordPresentation = ChordPresentation.HARMONIC,
        answerMode: ChordAnswerMode = ChordAnswerMode.QUALITY,
        fixedRoot: Int? = null,
        stopPolicy: SessionStopPolicy = SessionStopPolicy.UntilStopped
    ) = ChordSetupDraft(
        qualityIds = qualityIds,
        inversions = inversions,
        rootValues = rootValues,
        minimumNote = minimumNote,
        maximumNote = maximumNote,
        presentation = presentation,
        answerMode = answerMode,
        mode = com.thomas.passiveeartraining.training.recipe.RecipeMode.RECOGNITION,
        thinkingSilence = 2.seconds,
        replayCount = 0,
        stopPolicy = stopPolicy,
        fixedRoot = fixedRoot
    )

    @Test
    fun theDefaultChordSetupValidates() {
        val validation = ChordSetupValidator.validate(draft(), catalog())
        assertTrue(
            "default chord draft must validate: $validation",
            validation is SetupValidation.Valid
        )
    }

    @Test
    fun theDefaultSetupBuildsTheChordsProviderConfig() {
        val setup = ChordSetup.default()
        val request = setup.toStartRequest(seed = 42L)
        val provider = request.setup.provider
        assertTrue(provider is ProviderConfig.Chords)
        assertEquals(TrainingKind.CHORDS, provider.kind)
        assertEquals(42L, request.setup.seed)
        assertEquals(
            listOf("major", "minor"),
            (provider as ProviderConfig.Chords).definition.selectedQualities.map {
                it.id
            }
        )
    }

    @Test
    fun anEmptyQualitySelectionIsReportedOnTheQualityField() {
        val validation = ChordSetupValidator.validate(
            draft(qualityIds = emptyList()),
            catalog()
        )
        assertTrue(validation is SetupValidation.Invalid)
        assertTrue(
            (validation as SetupValidation.Invalid).issues
                .any { it.field == SetupField.QUALITIES }
        )
    }

    @Test
    fun anEmptyInversionSelectionIsReportedOnTheInversionField() {
        val validation = ChordSetupValidator.validate(draft(inversions = emptyList()), catalog())
        assertTrue(validation is SetupValidation.Invalid)
        assertTrue(
            (validation as SetupValidation.Invalid).issues
                .any { it.field == SetupField.INVERSIONS }
        )
    }

    @Test
    fun thirdInversionWithTriadsOnlyIsRefused() {
        val validation = ChordSetupValidator.validate(
            draft(qualityIds = listOf("major", "minor"), inversions = listOf(3)),
            catalog()
        )
        assertTrue(validation is SetupValidation.Invalid)
        assertTrue(
            "a third inversion needs a seventh chord",
            (validation as SetupValidation.Invalid).issues
                .any { it.field == SetupField.INVERSIONS || it.field == SetupField.QUALITIES }
        )
    }

    @Test
    fun anInvertedRangeIsRefused() {
        val validation = ChordSetupValidator.validate(
            draft(minimumNote = 84, maximumNote = 48),
            catalog()
        )
        assertTrue(
            (validation as SetupValidation.Invalid).issues.any { it.field == SetupField.RANGE }
        )
    }

    @Test
    fun aFixedRootOutsideTheRangeIsRefused() {
        val validation = ChordSetupValidator.validate(draft(fixedRoot = 96), catalog())
        assertTrue(
            (validation as SetupValidation.Invalid).issues
                .any { it.field == SetupField.FIXED_NOTE }
        )
    }

    @Test
    fun aFixedRootMustMatchTheSelectedClasses() {
        val validation = ChordSetupValidator.validate(
            draft(rootValues = listOf(0), fixedRoot = 62),
            catalog()
        )
        assertTrue(
            (validation as SetupValidation.Invalid).issues
                .any { it.field == SetupField.FIXED_NOTE }
        )
    }

    @Test
    fun missingChordClipsAreReportedOnTheAnswerField() {
        val partial = MapVoiceDurationCatalog.of(
            "chord.major" to 500.milliseconds,
            "connector.or" to 300.milliseconds
        )
        val validation = ChordSetupValidator.validate(draft(), partial)
        assertTrue(validation is SetupValidation.Invalid)
        val issues = (validation as SetupValidation.Invalid).issues
        assertTrue(
            issues.any {
                it.field == SetupField.ANSWERS && it.explanation.contains("chord.minor")
            }
        )
    }

    @Test
    fun aFixedRootAtTheTopIsRejectedWhenItsChordLeavesTheInstrument() {
        val validation = ChordSetupValidator.validate(
            draft(
                qualityIds = listOf("major"),
                minimumNote = 105,
                maximumNote = 108,
                fixedRoot = 108
            ),
            catalog()
        )
        assertTrue(
            "the provider's feasibility verdict must surface",
            (validation as SetupValidation.Invalid).issues
                .any { it.field == SetupField.RANGE || it.field == SetupField.FIXED_NOTE }
        )
    }

    @Test
    fun excessiveComposedSpeechIsADurationError() {
        // Every quality and inversion with three-second clips cannot fit in sixty seconds.
        val validation = ChordSetupValidator.validate(
            draft(
                qualityIds = ChordQuality.entries.map { it.id },
                inversions = (0..3).toList(),
                answerMode = ChordAnswerMode.QUALITY_AND_INVERSION
            ),
            catalog(3.seconds)
        )
        assertTrue(validation is SetupValidation.Invalid)
        assertTrue(
            (validation as SetupValidation.Invalid).issues
                .any { it.field == SetupField.DURATION }
        )
    }

    @Test
    fun aSleepLimitBeyondThreeHoursIsRefused() {
        val validation = ChordSetupValidator.validate(
            draft(
                stopPolicy = SessionStopPolicy.Duration(
                    limit = 200.minutes,
                    finish = DurationFinish.IMMEDIATE
                )
            ),
            catalog()
        )
        assertTrue(
            (validation as SetupValidation.Invalid).issues
                .any { it.field == SetupField.DURATION }
        )
    }

    @Test
    fun aRoundTripThroughTheDraftKeepsEveryField() {
        val original = ChordSetup.default().copy(
            qualityIds = listOf("major_seventh", "minor_seventh", "diminished_seventh"),
            inversions = listOf(0, 1, 2, 3),
            rootValues = listOf(0, 2, 4),
            answerMode = ChordAnswerMode.QUALITY_AND_INVERSION,
            presentation = ChordPresentation.ARPEGGIO,
            fixedRoot = 60,
            noteDuration = 900.milliseconds,
            seed = 99L
        )
        assertEquals(original, ChordSetupDraft.from(original).toSetup())
    }

    @Test
    fun theWorstCaseExampleAndAnswerComeFromTheProvider() {
        val definition = draft(
            qualityIds = ChordQuality.entries.map { it.id },
            inversions = (0..3).toList(),
            presentation = ChordPresentation.ARPEGGIO,
            answerMode = ChordAnswerMode.QUALITY_AND_INVERSION
        ).toSetup().toDefinition()
        val example = ChordExerciseProvider.maximumExampleDuration(definition)
        assertEquals(
            "four notes and three gaps",
            700.milliseconds * 4 + 100.milliseconds * 3,
            example
        )
        val answer = ChordExerciseProvider.maximumAnswerDuration(definition, catalog())
        assertTrue("the all-alternatives answer is long", answer > 10.seconds)
        val validation = ChordExerciseProvider()
            .validate(ProviderConfig.Chords(definition), catalog())
        assertTrue(validation is ProviderValidation.Valid)
        assertEquals(
            (validation as ProviderValidation.Valid).maximumAnswerDuration,
            answer
        )
    }

    @Test
    fun theUserSettingsDefaultsCarryBothModeSetups() {
        val settings = UserSettings.DEFAULTS
        assertTrue(settings.modeSetups.containsKey(TrainingKind.CHORDS))
        assertEquals(
            ChordSetup.default(),
            settings.chordSetup
        )
        assertTrue(
            settings.modeSetups.getValue(TrainingKind.CHORDS).payload
                is ModePayload.Chords
        )
    }

    @Test
    fun aLongNoteDurationArpeggioIsRefusedAtTheCap() {
        // M5-T06 regression for the re-timed content bound: four notes at 15 s each exceed the
        // 60 s cap, and the draft's own timing must drive the check.
        val validation = ChordSetupValidator.validate(
            draft(
                qualityIds = listOf("major_seventh"),
                inversions = (0..3).toList(),
                presentation = ChordPresentation.ARPEGGIO
            ).copy(noteDuration = 15.seconds),
            catalog()
        )
        assertTrue(validation is SetupValidation.Invalid)
        assertTrue(
            (validation as SetupValidation.Invalid).issues
                .any { it.field == SetupField.DURATION }
        )
    }
}
