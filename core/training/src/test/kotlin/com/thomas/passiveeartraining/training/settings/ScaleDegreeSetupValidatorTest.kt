package com.thomas.passiveeartraining.training.settings

import com.thomas.passiveeartraining.music.SpellingPreference as MusicSpellingPreference
import com.thomas.passiveeartraining.music.Tonality
import com.thomas.passiveeartraining.training.DegreeAnswerFormat
import com.thomas.passiveeartraining.training.MapVoiceDurationCatalog
import com.thomas.passiveeartraining.training.context.ContextKind
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

/** M8.3: the scale-degree setup, its draft and its validator. */
class ScaleDegreeSetupValidatorTest {

    private fun catalog(clipLength: Duration = 500.milliseconds): MapVoiceDurationCatalog =
        MapVoiceDurationCatalog(
            VoiceVocabulary.entries.associate { it.id to clipLength }
        )

    private fun draft(
        keyValues: List<Int> = listOf(0),
        tonalities: List<Tonality> = listOf(Tonality.MAJOR),
        degrees: List<Int> = (1..7).toList(),
        context: ContextKind = ContextKind.TONIC_TRIAD,
        contextGap: Duration = 500.milliseconds,
        answerFormat: DegreeAnswerFormat = DegreeAnswerFormat.DEGREE,
        spelling: MusicSpellingPreference = MusicSpellingPreference.SHARP,
        minimumNote: Int = 60,
        maximumNote: Int = 72,
        mode: RecipeMode = RecipeMode.RECOGNITION,
        replayCount: Int = 0,
        stopPolicy: SessionStopPolicy = SessionStopPolicy.UntilStopped
    ) = ScaleDegreeSetupDraft(
        keyValues = keyValues,
        tonalities = tonalities,
        degrees = degrees,
        context = context,
        contextGap = contextGap,
        answerFormat = answerFormat,
        spelling = spelling,
        minimumNote = minimumNote,
        maximumNote = maximumNote,
        mode = mode,
        thinkingSilence = 2.seconds,
        replayCount = replayCount,
        stopPolicy = stopPolicy
    )

    @Test
    fun theDefaultScaleDegreeSetupValidates() {
        val validation = ScaleDegreeSetupValidator.validate(
            ScaleDegreeSetupDraft.from(ScaleDegreeSetup.default()),
            catalog()
        )
        assertTrue(
            "default scale-degree draft must validate: $validation",
            validation is SetupValidation.Valid
        )
    }

    @Test
    fun theDefaultSetupBuildsTheScaleDegreesProviderConfig() {
        val setup = ScaleDegreeSetup.default()
        val request = setup.toStartRequest(
            seed = 42L,
            musicGain = 0.5f,
            voiceGain = 0.25f
        )
        val provider = request.setup.provider
        assertTrue(provider is ProviderConfig.ScaleDegrees)
        assertEquals(TrainingKind.SCALE_DEGREES, provider.kind)
        assertEquals(42L, request.setup.seed)
        assertEquals(0.5f, request.musicGain, 0f)
        assertEquals(0.25f, request.voiceGain, 0f)
        val definition = (provider as ProviderConfig.ScaleDegrees).definition
        assertEquals(listOf(0), definition.keyValues)
        assertEquals(listOf(Tonality.MAJOR), definition.tonalities)
        assertEquals((1..7).toList(), definition.degrees)
        assertEquals(ContextKind.TONIC_TRIAD, definition.context)
        assertEquals(500.milliseconds, definition.contextGap)
        assertEquals(DegreeAnswerFormat.DEGREE, definition.answerFormat)
        assertEquals(MusicSpellingPreference.SHARP, definition.spelling)
        assertEquals(60, definition.minimumNoteValue)
        assertEquals(72, definition.maximumNoteValue)
    }

    @Test
    fun anEmptyTonalitySelectionIsReportedOnTheTonalitiesField() {
        val validation = ScaleDegreeSetupValidator.validate(
            draft(tonalities = emptyList()),
            catalog()
        )
        assertTrue(validation is SetupValidation.Invalid)
        assertTrue(
            (validation as SetupValidation.Invalid).issues
                .any { it.field == SetupField.TONALITIES }
        )
    }

    @Test
    fun anEmptyDegreeSelectionIsReportedOnTheDegreesField() {
        val validation = ScaleDegreeSetupValidator.validate(draft(degrees = emptyList()), catalog())
        assertTrue(validation is SetupValidation.Invalid)
        assertTrue(
            (validation as SetupValidation.Invalid).issues
                .any { it.field == SetupField.DEGREES }
        )
    }

    @Test
    fun anUnknownKeyPitchClassIsReportedOnTheKeysField() {
        val validation = ScaleDegreeSetupValidator.validate(
            draft(keyValues = listOf(0, 12)),
            catalog()
        )
        assertTrue(validation is SetupValidation.Invalid)
        assertTrue(
            (validation as SetupValidation.Invalid).issues.any { it.field == SetupField.KEYS }
        )
    }

    @Test
    fun anUnknownTargetDegreeIsReportedOnTheDegreesField() {
        val validation = ScaleDegreeSetupValidator.validate(draft(degrees = listOf(0)), catalog())
        assertTrue(validation is SetupValidation.Invalid)
        assertTrue(
            (validation as SetupValidation.Invalid).issues.any { it.field == SetupField.DEGREES }
        )
    }

    @Test
    fun anInvertedRangeIsRefused() {
        val validation = ScaleDegreeSetupValidator.validate(
            draft(minimumNote = 72, maximumNote = 60),
            catalog()
        )
        assertTrue(
            (validation as SetupValidation.Invalid).issues.any { it.field == SetupField.RANGE }
        )
    }

    @Test
    fun aContextGapBeyondThirtySecondsIsReportedOnTheContextGapField() {
        val validation = ScaleDegreeSetupValidator.validate(
            draft(contextGap = 31.seconds),
            catalog()
        )
        assertTrue(validation is SetupValidation.Invalid)
        assertTrue(
            (validation as SetupValidation.Invalid).issues
                .any { it.field == SetupField.CONTEXT_GAP }
        )
    }

    @Test
    fun aNarrowRootRangeStillAllowsTheContextAboveIt() {
        val validation = ScaleDegreeSetupValidator.validate(
            draft(
                context = ContextKind.ASCENDING_SCALE,
                degrees = listOf(1),
                minimumNote = 60,
                maximumNote = 64
            ),
            catalog()
        )
        assertTrue(validation is SetupValidation.Valid)

        val topEdge = ScaleDegreeSetupValidator.validate(
            draft(
                context = ContextKind.ASCENDING_SCALE,
                degrees = listOf(1),
                minimumNote = 105,
                maximumNote = 108
            ),
            catalog()
        )
        assertTrue(topEdge is SetupValidation.Invalid)
        assertTrue(
            (topEdge as SetupValidation.Invalid).issues.any {
                it.field == SetupField.RANGE && it.explanation.contains("105..108")
            }
        )
    }

    @Test
    fun missingDegreeClipsAreReportedOnTheAnswerField() {
        val partial = MapVoiceDurationCatalog.of("degree.one" to 500.milliseconds)
        val validation = ScaleDegreeSetupValidator.validate(draft(), partial)
        assertTrue(validation is SetupValidation.Invalid)
        val issues = (validation as SetupValidation.Invalid).issues
        assertTrue(
            issues.any {
                it.field == SetupField.ANSWERS && it.explanation.contains("degree.two")
            }
        )
    }

    @Test
    fun anExcessiveNoteDurationIsADurationError() {
        // M8-T05 regression for the re-timed content bound: an eight-note ascending scale at 55 s
        // per note far exceeds the 60 s complete-exercise cap, and the draft's own timing must
        // drive the check.
        val validation = ScaleDegreeSetupValidator.validate(
            draft(context = ContextKind.ASCENDING_SCALE).copy(noteDuration = 55.seconds),
            catalog()
        )
        assertTrue(validation is SetupValidation.Invalid)
        assertTrue(
            (validation as SetupValidation.Invalid).issues
                .any { it.field == SetupField.DURATION }
        )
    }

    @Test
    fun theContextGapReachesTheRecipeTimingAndTheDraftRoundTrips() {
        val original = ScaleDegreeSetup.default().copy(
            keyValues = listOf(0, 9),
            tonalities = listOf(Tonality.MAJOR, Tonality.NATURAL_MINOR),
            degrees = listOf(1, 3, 5),
            context = ContextKind.TONIC_DOMINANT_TONIC,
            contextGap = 750.milliseconds,
            answerFormat = DegreeAnswerFormat.NOTE_NAME,
            spelling = MusicSpellingPreference.FLAT,
            mode = RecipeMode.EXPOSURE,
            replayCount = 2,
            noteDuration = 900.milliseconds,
            seed = 99L,
            separatorCue = true
        )
        assertEquals(original, ScaleDegreeSetupDraft.from(original).toSetup())
        assertEquals(
            "the compiler consumes the setup's context gap through the recipe timing",
            750.milliseconds,
            original.toRecipe().timing.contextGap
        )
    }

    @Test
    fun aStoredDurationStopPolicyNormalizesToUntilStoppedInTheDraft() {
        val stored = ScaleDegreeSetup.default().copy(
            stopPolicy = SessionStopPolicy.Duration(
                limit = 20.minutes,
                finish = DurationFinish.IMMEDIATE
            )
        )
        assertEquals(SessionStopPolicy.UntilStopped, ScaleDegreeSetupDraft.from(stored).stopPolicy)
    }

    @Test
    fun aSleepLimitBeyondThreeHoursIsRefused() {
        val validation = ScaleDegreeSetupValidator.validate(
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
    fun theUserSettingsDefaultsCarryTheScaleDegreeModeSetup() {
        val settings = UserSettings.DEFAULTS
        assertTrue(settings.modeSetups.containsKey(TrainingKind.SCALE_DEGREES))
        assertEquals(ScaleDegreeSetup.default(), settings.scaleDegreeSetup)
        assertTrue(
            settings.modeSetups.getValue(TrainingKind.SCALE_DEGREES).payload
                is ModePayload.ScaleDegrees
        )
    }
}
