package com.thomas.passiveeartraining.training.settings

import com.thomas.passiveeartraining.music.ScaleDirection
import com.thomas.passiveeartraining.music.ScaleKind
import com.thomas.passiveeartraining.training.MapVoiceDurationCatalog
import com.thomas.passiveeartraining.training.provider.ProviderConfig
import com.thomas.passiveeartraining.training.provider.TrainingKind
import com.thomas.passiveeartraining.training.provider.VoiceVocabulary
import com.thomas.passiveeartraining.training.recipe.DurationFinish
import com.thomas.passiveeartraining.training.recipe.RecipeMode
import com.thomas.passiveeartraining.training.recipe.SessionStopPolicy
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** M6-T04/M6-T05: the scale setup, its draft and its validator. */
class ScaleSetupValidatorTest {

    private val clipIds: List<String> =
        ScaleKind.entries.map { VoiceVocabulary.scaleEntry(it).id.value }

    private fun catalog(clipLength: Duration = 500.milliseconds) =
        MapVoiceDurationCatalog.of(*clipIds.map { it to clipLength }.toTypedArray())

    private fun draft(
        scaleIds: List<String> = listOf("major", "natural_minor"),
        direction: ScaleDirection = ScaleDirection.ASCENDING,
        rootValues: List<Int> = emptyList(),
        minimumNote: Int = 48,
        maximumNote: Int = 84,
        mode: RecipeMode = RecipeMode.RECOGNITION,
        replayCount: Int = 0,
        stopPolicy: SessionStopPolicy = SessionStopPolicy.UntilStopped,
        fixedRoot: Int? = null
    ) = ScaleSetupDraft(
        scaleIds = scaleIds,
        direction = direction,
        rootValues = rootValues,
        minimumNote = minimumNote,
        maximumNote = maximumNote,
        mode = mode,
        thinkingSilence = 2.seconds,
        replayCount = replayCount,
        stopPolicy = stopPolicy,
        fixedRoot = fixedRoot
    )

    @Test
    fun theDefaultScaleSetupValidates() {
        val validation = ScaleSetupValidator.validate(draft(), catalog())
        assertTrue(
            "default scale draft must validate: $validation",
            validation is SetupValidation.Valid
        )
    }

    @Test
    fun theDefaultSetupBuildsTheScalesProviderConfig() {
        val setup = ScaleSetup.default()
        val request = setup.toStartRequest(seed = 42L, musicGain = 0.5f, voiceGain = 0.25f)
        val provider = request.setup.provider
        assertTrue(provider is ProviderConfig.Scales)
        assertEquals(TrainingKind.SCALES, provider.kind)
        assertEquals(42L, request.setup.seed)
        assertEquals(0.5f, request.musicGain, 0f)
        assertEquals(0.25f, request.voiceGain, 0f)
        assertEquals(
            listOf("major", "natural_minor"),
            (provider as ProviderConfig.Scales).definition.selectedScales.map { it.id }
        )
        assertEquals(ScaleDirection.ASCENDING, provider.definition.direction)
        assertEquals((0..11).toList(), provider.definition.rootValues)
        assertEquals(48, provider.definition.minimumNoteValue)
        assertEquals(84, provider.definition.maximumNoteValue)
    }

    @Test
    fun anEmptyScaleSelectionIsReportedOnTheScalesField() {
        val validation = ScaleSetupValidator.validate(draft(scaleIds = emptyList()), catalog())
        assertTrue(validation is SetupValidation.Invalid)
        assertTrue(
            (validation as SetupValidation.Invalid).issues
                .any { it.field == SetupField.SCALES }
        )
    }

    @Test
    fun anInvertedRangeIsRefused() {
        val validation = ScaleSetupValidator.validate(
            draft(minimumNote = 84, maximumNote = 48),
            catalog()
        )
        assertTrue(
            (validation as SetupValidation.Invalid).issues.any { it.field == SetupField.RANGE }
        )
    }

    @Test
    fun aFixedRootOutsideTheRangeIsRefused() {
        val validation = ScaleSetupValidator.validate(draft(fixedRoot = 96), catalog())
        assertTrue(
            (validation as SetupValidation.Invalid).issues
                .any { it.field == SetupField.FIXED_NOTE }
        )
    }

    @Test
    fun aFixedRootOutsideTheMidiRangeIsRefused() {
        val validation = ScaleSetupValidator.validate(draft(fixedRoot = 200), catalog())
        assertTrue(
            (validation as SetupValidation.Invalid).issues
                .any { it.field == SetupField.FIXED_NOTE }
        )
    }

    @Test
    fun aFixedRootMustMatchTheSelectedClasses() {
        val validation = ScaleSetupValidator.validate(
            draft(rootValues = listOf(0), fixedRoot = 62),
            catalog()
        )
        assertTrue(
            (validation as SetupValidation.Invalid).issues
                .any { it.field == SetupField.FIXED_NOTE }
        )
    }

    @Test
    fun aNarrowRootRangeStillAllowsTheScaleAboveIt() {
        val validation = ScaleSetupValidator.validate(
            draft(scaleIds = listOf("major"), minimumNote = 60, maximumNote = 70),
            catalog()
        )
        assertTrue(validation is SetupValidation.Valid)

        val topEdge = ScaleSetupValidator.validate(
            draft(scaleIds = listOf("major"), minimumNote = 105, maximumNote = 108),
            catalog()
        )
        assertTrue(topEdge is SetupValidation.Invalid)
        assertTrue(
            "the infeasible top range must surface: $topEdge",
            (topEdge as SetupValidation.Invalid).issues.any { it.field == SetupField.RANGE }
        )
    }

    @Test
    fun missingScaleClipsAreReportedOnTheAnswerField() {
        val partial = MapVoiceDurationCatalog.of("scale.major" to 500.milliseconds)
        val validation = ScaleSetupValidator.validate(draft(), partial)
        assertTrue(validation is SetupValidation.Invalid)
        val issues = (validation as SetupValidation.Invalid).issues
        assertTrue(
            issues.any {
                it.field == SetupField.ANSWERS && it.explanation.contains("scale.natural_minor")
            }
        )
    }

    @Test
    fun anExcessiveBothDirectionAssociationIsADurationError() {
        // Fifteen notes, the thinking pause, the answer, a second traversal and four repeats with
        // measured one-second clips cannot fit in sixty seconds.
        val validation = ScaleSetupValidator.validate(
            draft(
                scaleIds = listOf("major"),
                direction = ScaleDirection.BOTH,
                mode = RecipeMode.ASSOCIATION,
                replayCount = 4
            ),
            catalog(1.seconds)
        )
        assertTrue(validation is SetupValidation.Invalid)
        assertTrue(
            (validation as SetupValidation.Invalid).issues
                .any { it.field == SetupField.DURATION }
        )

        val withinCap = ScaleSetupValidator.validate(
            draft(
                scaleIds = listOf("major"),
                direction = ScaleDirection.BOTH,
                mode = RecipeMode.ASSOCIATION
            ),
            catalog(1.seconds)
        )
        assertTrue(
            "the same setup without repeats fits: $withinCap",
            withinCap is SetupValidation.Valid
        )
    }

    @Test
    fun aSleepLimitBeyondThreeHoursIsRefused() {
        val validation = ScaleSetupValidator.validate(
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
    fun theFeasibleFixedRootsPickerOffersOnlyTonicsThatFit() {
        val withPicker = draft(
            scaleIds = listOf("major"),
            rootValues = listOf(0),
            fixedRoot = 60
        )
        assertEquals(listOf(48, 60, 72, 84), ScaleSetupValidator.feasibleFixedRoots(withPicker))
        assertEquals(
            "no picker without a fixed root",
            emptyList<Int>(),
            ScaleSetupValidator.feasibleFixedRoots(withPicker.copy(fixedRoot = null))
        )
    }

    @Test
    fun aRoundTripThroughTheDraftKeepsEveryField() {
        val original = ScaleSetup.default().copy(
            scaleIds = listOf("harmonic_minor", "dorian", "whole_tone"),
            direction = ScaleDirection.DESCENDING,
            rootValues = listOf(0, 7),
            fixedRoot = 60,
            replayCount = 2,
            noteDuration = 900.milliseconds,
            seed = 99L,
            separatorCue = true
        )
        assertEquals(original, ScaleSetupDraft.from(original).toSetup())
    }

    @Test
    fun aStoredDurationStopPolicyNormalizesToUntilStoppedInTheDraft() {
        val stored = ScaleSetup.default().copy(
            stopPolicy = SessionStopPolicy.Duration(
                limit = 20.minutes,
                finish = DurationFinish.IMMEDIATE
            )
        )
        assertEquals(SessionStopPolicy.UntilStopped, ScaleSetupDraft.from(stored).stopPolicy)
    }

    @Test
    fun theUserSettingsDefaultsCarryTheScaleModeSetup() {
        val settings = UserSettings.DEFAULTS
        assertTrue(settings.modeSetups.containsKey(TrainingKind.SCALES))
        assertEquals(ScaleSetup.default(), settings.scalesSetup)
        assertTrue(
            settings.modeSetups.getValue(TrainingKind.SCALES).payload
                is ModePayload.Scales
        )
    }

    @Test
    fun aLongNoteDurationBothTraversalIsRefusedAtTheCap() {
        // M6-T05 regression for the re-timed content bound: fifteen notes at 4 s each exceed
        // the 60 s cap, and the draft's own timing must drive the check.
        val validation = ScaleSetupValidator.validate(
            draft(
                direction = ScaleDirection.BOTH
            ).copy(noteDuration = 4.seconds),
            catalog()
        )
        assertTrue(validation is SetupValidation.Invalid)
        assertTrue(
            (validation as SetupValidation.Invalid).issues
                .any { it.field == SetupField.DURATION }
        )
    }
}
