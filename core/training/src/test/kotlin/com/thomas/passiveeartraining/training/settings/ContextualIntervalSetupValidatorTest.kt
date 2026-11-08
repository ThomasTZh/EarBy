package com.thomas.passiveeartraining.training.settings

import com.thomas.passiveeartraining.music.IntervalPresentation
import com.thomas.passiveeartraining.music.SpellingPreference as MusicSpellingPreference
import com.thomas.passiveeartraining.music.Tonality
import com.thomas.passiveeartraining.training.BundledVoiceDurations
import com.thomas.passiveeartraining.training.ContextualIntervalAnswerFormat
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

/** M9B.2: the intervals-in-context setup, its draft, its validator and the provider projection. */
class ContextualIntervalSetupValidatorTest {

    /** Every vocabulary entry has a measured 500 ms clip, including the degree numbers. */
    private fun catalog(clipLength: Duration = 500.milliseconds): MapVoiceDurationCatalog =
        MapVoiceDurationCatalog(
            VoiceVocabulary.entries.associate { it.id to clipLength }
        )

    private fun draft(
        keyValues: List<Int> = listOf(0),
        tonalities: List<Tonality> = listOf(Tonality.MAJOR),
        intervalIds: List<String> = listOf("minor_third", "major_third", "perfect_fifth"),
        presentation: IntervalPresentation = IntervalPresentation.ASCENDING,
        context: ContextKind = ContextKind.TONIC_TRIAD,
        contextGap: Duration = 500.milliseconds,
        answerFormat: ContextualIntervalAnswerFormat = ContextualIntervalAnswerFormat.INTERVAL,
        spelling: MusicSpellingPreference = MusicSpellingPreference.SHARP,
        minimumNote: Int = 60,
        maximumNote: Int = 72,
        mode: RecipeMode = RecipeMode.RECOGNITION,
        noteDuration: Duration = 700.milliseconds,
        replayCount: Int = 0,
        stopPolicy: SessionStopPolicy = SessionStopPolicy.UntilStopped
    ) = ContextualIntervalSetupDraft(
        keyValues = keyValues,
        tonalities = tonalities,
        intervalIds = intervalIds,
        presentation = presentation,
        context = context,
        contextGap = contextGap,
        answerFormat = answerFormat,
        spelling = spelling,
        minimumNote = minimumNote,
        maximumNote = maximumNote,
        mode = mode,
        thinkingSilence = 2.seconds,
        replayCount = replayCount,
        stopPolicy = stopPolicy,
        noteDuration = noteDuration
    )

    @Test
    fun theDefaultContextualIntervalSetupValidatesAgainstTheBundledClips() {
        val validation = ContextualIntervalSetupValidator.validate(
            ContextualIntervalSetupDraft.from(ContextualIntervalSetup.default()),
            BundledVoiceDurations
        )
        assertTrue(
            "the default intervals-in-context draft must validate: $validation",
            validation is SetupValidation.Valid
        )
    }

    @Test
    fun theDefaultSetupBuildsTheContextualIntervalsProviderConfig() {
        val setup = ContextualIntervalSetup.default()
        val request = setup.toStartRequest(seed = 42L, musicGain = 0.5f, voiceGain = 0.25f)
        val provider = request.setup.provider
        assertTrue(provider is ProviderConfig.ContextualIntervals)
        assertEquals(TrainingKind.INTERVALS_IN_CONTEXT, provider.kind)
        assertEquals(42L, request.setup.seed)
        assertEquals(0.5f, request.musicGain, 0f)
        assertEquals(0.25f, request.voiceGain, 0f)
        val definition = (provider as ProviderConfig.ContextualIntervals).definition
        assertEquals(listOf(0), definition.keyValues)
        assertEquals(listOf(Tonality.MAJOR), definition.tonalities)
        assertEquals(
            listOf("minor_third", "major_third", "perfect_fifth"),
            definition.intervalIds
        )
        assertEquals(IntervalPresentation.ASCENDING, definition.presentation)
        assertEquals(ContextKind.TONIC_TRIAD, definition.context)
        assertEquals(500.milliseconds, definition.contextGap)
        assertEquals(ContextualIntervalAnswerFormat.INTERVAL, definition.answerFormat)
        assertEquals(MusicSpellingPreference.SHARP, definition.spelling)
        assertEquals(60, definition.minimumNoteValue)
        assertEquals(72, definition.maximumNoteValue)
        assertEquals(
            "the context gap reaches the compiled timeline through the recipe",
            500.milliseconds,
            setup.toRecipe().timing.contextGap
        )
    }

    @Test
    fun anEmptyTonalitySelectionIsReportedOnTheTonalitiesField() {
        val validation = ContextualIntervalSetupValidator.validate(
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
    fun anEmptyIntervalSelectionIsReportedOnTheIntervalsField() {
        val validation = ContextualIntervalSetupValidator.validate(
            draft(intervalIds = emptyList()),
            catalog()
        )
        assertTrue(validation is SetupValidation.Invalid)
        assertTrue(
            (validation as SetupValidation.Invalid).issues
                .any { it.field == SetupField.INTERVALS }
        )
    }

    @Test
    fun anUnknownIntervalIdIsReportedOnTheIntervalsField() {
        val validation = ContextualIntervalSetupValidator.validate(
            draft(intervalIds = listOf("minor_third", "compound_ninth")),
            catalog()
        )
        assertTrue(validation is SetupValidation.Invalid)
        val issues = (validation as SetupValidation.Invalid).issues
        assertTrue(
            "the unknown id must surface against the interval checklist: $issues",
            issues.any {
                it.field == SetupField.INTERVALS && it.explanation.contains("compound_ninth")
            }
        )
    }

    @Test
    fun anUnknownKeyPitchClassIsReportedOnTheKeysField() {
        val validation = ContextualIntervalSetupValidator.validate(
            draft(keyValues = listOf(0, 12)),
            catalog()
        )
        assertTrue(validation is SetupValidation.Invalid)
        assertTrue(
            (validation as SetupValidation.Invalid).issues.any { it.field == SetupField.KEYS }
        )
    }

    @Test
    fun anImpossibleIntervalPresentationCombinationIsReportedOnTheRangeField() {
        val validation = ContextualIntervalSetupValidator.validate(
            draft(
                intervalIds = listOf("major_third"),
                presentation = IntervalPresentation.DESCENDING,
                minimumNote = 105,
                maximumNote = 108
            ),
            catalog()
        )
        assertTrue(validation is SetupValidation.Invalid)
        val issues = (validation as SetupValidation.Invalid).issues
        assertTrue(
            "the impossible combination must surface against the range: $issues",
            issues.any {
                it.field == SetupField.RANGE &&
                    it.explanation.contains("C major") &&
                    it.explanation.contains("descending") &&
                    it.explanation.contains("major third") &&
                    it.explanation.contains("105..108")
            }
        )
    }

    @Test
    fun missingAnswerClipsAreReportedOnTheAnswersField() {
        val partial = MapVoiceDurationCatalog.of("interval.minor_third" to 500.milliseconds)
        val validation = ContextualIntervalSetupValidator.validate(draft(), partial)
        assertTrue(validation is SetupValidation.Invalid)
        val issues = (validation as SetupValidation.Invalid).issues
        assertTrue(
            "every missing clip belongs to the answer checklist: $issues",
            issues.isNotEmpty() && issues.all { it.field == SetupField.ANSWERS }
        )
        listOf("interval.major_third", "interval.perfect_fifth").forEach { id ->
            assertTrue(
                "the missing clip $id must be named: $issues",
                issues.any { it.explanation.contains(id) }
            )
        }
    }

    @Test
    fun anExcessiveNoteDurationIsADurationError() {
        // The default tonic-triad context plus two 30-second target notes far exceeds the 60 s
        // complete-exercise cap, and the draft's own timing must drive the re-timed check.
        val validation = ContextualIntervalSetupValidator.validate(
            draft(noteDuration = 30.seconds),
            catalog()
        )
        assertTrue(validation is SetupValidation.Invalid)
        assertTrue(
            (validation as SetupValidation.Invalid).issues
                .any { it.field == SetupField.DURATION }
        )
    }

    @Test
    fun aContextGapBeyondThirtySecondsIsReportedOnTheContextGapField() {
        val validation = ContextualIntervalSetupValidator.validate(
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
    fun thePresentationContextAndAnswerFormatReachTheDefinitionAndTheDraftRoundTrips() {
        val original = ContextualIntervalSetup.default().copy(
            keyValues = listOf(0, 9),
            tonalities = listOf(Tonality.MAJOR, Tonality.NATURAL_MINOR),
            intervalIds = listOf("minor_third", "perfect_fifth"),
            presentation = IntervalPresentation.HARMONIC,
            context = ContextKind.TONIC_DOMINANT_TONIC,
            contextGap = 750.milliseconds,
            answerFormat = ContextualIntervalAnswerFormat.INTERVAL_AND_DEGREES,
            spelling = MusicSpellingPreference.FLAT,
            minimumNote = 57,
            maximumNote = 84,
            mode = RecipeMode.EXPOSURE,
            replayCount = 2,
            noteDuration = 900.milliseconds,
            seed = 99L,
            separatorCue = true
        )
        assertEquals(original, ContextualIntervalSetupDraft.from(original).toSetup())
        val definition = original.toDefinition()
        assertEquals(IntervalPresentation.HARMONIC, definition.presentation)
        assertEquals(ContextKind.TONIC_DOMINANT_TONIC, definition.context)
        assertEquals(750.milliseconds, definition.contextGap)
        assertEquals(
            ContextualIntervalAnswerFormat.INTERVAL_AND_DEGREES,
            definition.answerFormat
        )
        assertEquals(MusicSpellingPreference.FLAT, definition.spelling)
        assertEquals(57, definition.minimumNoteValue)
        assertEquals(84, definition.maximumNoteValue)
    }

    @Test
    fun aStoredDurationStopPolicyNormalizesToUntilStoppedInTheDraft() {
        val stored = ContextualIntervalSetup.default().copy(
            stopPolicy = SessionStopPolicy.Duration(
                limit = 20.minutes,
                finish = DurationFinish.IMMEDIATE
            )
        )
        assertEquals(
            SessionStopPolicy.UntilStopped,
            ContextualIntervalSetupDraft.from(stored).stopPolicy
        )
    }

    @Test
    fun aSleepLimitBeyondThreeHoursIsRefused() {
        val validation = ContextualIntervalSetupValidator.validate(
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
    fun theUserSettingsDefaultsCarryTheContextualIntervalModeSetup() {
        val settings = UserSettings.DEFAULTS
        assertTrue(settings.modeSetups.containsKey(TrainingKind.INTERVALS_IN_CONTEXT))
        assertEquals(ContextualIntervalSetup.default(), settings.contextualIntervalSetup)
        assertTrue(
            settings.modeSetups.getValue(TrainingKind.INTERVALS_IN_CONTEXT).payload
                is ModePayload.ContextualIntervals
        )
    }
}
