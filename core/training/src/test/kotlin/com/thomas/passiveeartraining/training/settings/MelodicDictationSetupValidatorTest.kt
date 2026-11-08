package com.thomas.passiveeartraining.training.settings

import com.thomas.passiveeartraining.music.MidiNote
import com.thomas.passiveeartraining.music.SpellingPreference as MusicSpellingPreference
import com.thomas.passiveeartraining.music.Tonality
import com.thomas.passiveeartraining.training.BundledVoiceDurations
import com.thomas.passiveeartraining.training.MapVoiceDurationCatalog
import com.thomas.passiveeartraining.training.MelodicAnswerFormat
import com.thomas.passiveeartraining.training.MelodicDirectionBias
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

/** M10: the melodic-dictation setup, its draft, its validator and the provider projection. */
class MelodicDictationSetupValidatorTest {

    /** Every vocabulary entry has a measured 500 ms clip, including the degree numbers. */
    private fun catalog(clipLength: Duration = 500.milliseconds): MapVoiceDurationCatalog =
        MapVoiceDurationCatalog(
            VoiceVocabulary.entries.associate { it.id to clipLength }
        )

    private fun draft(
        keyValues: List<Int> = listOf(0),
        tonalities: List<Tonality> = listOf(Tonality.MAJOR),
        context: ContextKind = ContextKind.TONIC_TRIAD,
        contextGap: Duration = 500.milliseconds,
        minimumNote: MidiNote = MidiNote.of(60),
        maximumNote: MidiNote = MidiNote.of(72),
        phraseLength: Int = 4,
        startDegree: Int? = 1,
        endDegree: Int? = 1,
        allowedSteps: List<Int> = (-5..5).toList(),
        maximumLeap: Int = 5,
        bias: MelodicDirectionBias = MelodicDirectionBias.BALANCED,
        answerFormat: MelodicAnswerFormat = MelodicAnswerFormat.DEGREE_SEQUENCE,
        spelling: MusicSpellingPreference = MusicSpellingPreference.SHARP,
        mode: RecipeMode = RecipeMode.RECOGNITION,
        noteDuration: Duration = 700.milliseconds,
        replayCount: Int = 0,
        stopPolicy: SessionStopPolicy = SessionStopPolicy.UntilStopped
    ) = MelodicDictationSetupDraft(
        keyValues = keyValues,
        tonalities = tonalities,
        context = context,
        contextGap = contextGap,
        minimumNote = minimumNote,
        maximumNote = maximumNote,
        phraseLength = phraseLength,
        startDegree = startDegree,
        endDegree = endDegree,
        allowedSteps = allowedSteps,
        maximumLeap = maximumLeap,
        bias = bias,
        answerFormat = answerFormat,
        spelling = spelling,
        mode = mode,
        thinkingSilence = 2.seconds,
        replayCount = replayCount,
        stopPolicy = stopPolicy,
        noteDuration = noteDuration
    )

    private fun issuesOf(validation: SetupValidation): List<SetupIssue> {
        assertTrue(
            "the validation must be invalid but was $validation",
            validation is SetupValidation.Invalid
        )
        return (validation as SetupValidation.Invalid).issues
    }

    @Test
    fun theDefaultMelodicDictationSetupValidatesAgainstTheBundledClips() {
        val validation = MelodicDictationSetupValidator.validate(
            MelodicDictationSetupDraft.from(MelodicDictationSetup.default()),
            BundledVoiceDurations
        )
        assertTrue(
            "the default melodic-dictation draft must validate: $validation",
            validation is SetupValidation.Valid
        )
    }

    @Test
    fun theDefaultSetupBuildsTheMelodicDictationProviderConfig() {
        val setup = MelodicDictationSetup.default()
        val request = setup.toStartRequest(seed = 42L, musicGain = 0.5f, voiceGain = 0.25f)
        val provider = request.setup.provider
        assertTrue(provider is ProviderConfig.MelodicDictation)
        assertEquals(TrainingKind.MELODIC_DICTATION, provider.kind)
        assertEquals(42L, request.setup.seed)
        assertEquals(0.5f, request.musicGain, 0f)
        assertEquals(0.25f, request.voiceGain, 0f)
        val definition = (provider as ProviderConfig.MelodicDictation).definition
        assertEquals(listOf(0), definition.keyValues)
        assertEquals(listOf(Tonality.MAJOR), definition.tonalities)
        assertEquals(ContextKind.TONIC_TRIAD, definition.context)
        assertEquals(500.milliseconds, definition.contextGap)
        assertEquals(60, definition.minimumNoteValue)
        assertEquals(72, definition.maximumNoteValue)
        assertEquals(4, definition.phraseLength)
        assertEquals(1, definition.startDegree)
        assertEquals(1, definition.endDegree)
        assertEquals((-5..5).toList(), definition.allowedSteps)
        assertEquals(5, definition.maximumLeap)
        assertEquals(MelodicDirectionBias.BALANCED, definition.bias)
        assertEquals(MelodicAnswerFormat.DEGREE_SEQUENCE, definition.answerFormat)
        assertEquals(MusicSpellingPreference.SHARP, definition.spelling)
        assertEquals(
            "the context gap reaches the compiled timeline through the recipe",
            500.milliseconds,
            setup.toRecipe().timing.contextGap
        )
    }

    @Test
    fun anEmptyTonalitySelectionIsReportedOnTheTonalitiesField() {
        val issues = issuesOf(
            MelodicDictationSetupValidator.validate(draft(tonalities = emptyList()), catalog())
        )
        assertTrue(issues.any { it.field == SetupField.TONALITIES })
    }

    @Test
    fun anUnknownKeyPitchClassIsReportedOnTheKeysField() {
        val issues = issuesOf(
            MelodicDictationSetupValidator.validate(draft(keyValues = listOf(0, 12)), catalog())
        )
        assertTrue(issues.any { it.field == SetupField.KEYS })
    }

    @Test
    fun anInvertedRangeIsReportedOnTheRangeField() {
        val issues = issuesOf(
            MelodicDictationSetupValidator.validate(
                draft(minimumNote = MidiNote.of(72), maximumNote = MidiNote.of(60)),
                catalog()
            )
        )
        assertTrue(issues.any { it.field == SetupField.RANGE })
    }

    @Test
    fun anImpossiblePhraseLengthIsReportedOnTheLengthField() {
        listOf(1, 17).forEach { length ->
            val issues = issuesOf(
                MelodicDictationSetupValidator.validate(draft(phraseLength = length), catalog())
            )
            assertTrue(
                "length $length must surface against the length control: $issues",
                issues.any { it.field == SetupField.LENGTH }
            )
        }
    }

    @Test
    fun anImpossibleEndpointDegreeIsReportedOnTheEndpointsField() {
        val issues = issuesOf(
            MelodicDictationSetupValidator.validate(draft(startDegree = 8), catalog())
        )
        assertTrue(
            "an out-of-range start degree belongs to the endpoint control: $issues",
            issues.any { it.field == SetupField.ENDPOINTS }
        )
    }

    @Test
    fun anEmptyStepSelectionIsReportedOnTheStepsField() {
        val issues = issuesOf(
            MelodicDictationSetupValidator.validate(draft(allowedSteps = emptyList()), catalog())
        )
        assertTrue(issues.any { it.field == SetupField.STEPS })
    }

    @Test
    fun aStepOutsideTheSupportedRangeIsReportedOnTheStepsField() {
        val issues = issuesOf(
            MelodicDictationSetupValidator.validate(draft(allowedSteps = listOf(0, 13)), catalog())
        )
        assertTrue(issues.any { it.field == SetupField.STEPS })
    }

    @Test
    fun aMaximumLeapOutsideTheSupportedRangeIsReportedOnTheLeapField() {
        val issues = issuesOf(
            MelodicDictationSetupValidator.validate(draft(maximumLeap = 13), catalog())
        )
        assertTrue(issues.any { it.field == SetupField.LEAP })
    }

    @Test
    fun anImpossibleEndpointStepCombinationIsReportedWithTheProviderExplanation() {
        // Length 2, tonic to tonic, but the only allowed step is the whole tone: no two-note
        // phrase can start and end on degree 1, and the provider suggests allowing repeats.
        val issues = issuesOf(
            MelodicDictationSetupValidator.validate(
                draft(
                    phraseLength = 2,
                    startDegree = 1,
                    endDegree = 1,
                    allowedSteps = listOf(2),
                    maximumLeap = 2
                ),
                catalog()
            )
        )
        assertTrue(
            "the provider must explain the impossible phrase: $issues",
            issues.any {
                it.field == SetupField.STEPS &&
                    it.explanation.contains("No 2-note C major phrase") &&
                    it.explanation.contains("Allow step 0")
            }
        )
    }

    @Test
    fun aNarrowRootRangeStillAllowsTheContextAboveIt() {
        val narrow = MelodicDictationSetupValidator.validate(
            draft(
                minimumNote = MidiNote.of(60),
                maximumNote = MidiNote.of(64),
                phraseLength = 2,
                startDegree = 1,
                endDegree = 3,
                allowedSteps = listOf(4),
                maximumLeap = 4
            ),
            catalog()
        )
        assertTrue(narrow is SetupValidation.Valid)

        val issues = issuesOf(
            MelodicDictationSetupValidator.validate(
                draft(
                    minimumNote = MidiNote.of(105),
                    maximumNote = MidiNote.of(108),
                    phraseLength = 2,
                    startDegree = 1,
                    endDegree = 3,
                    allowedSteps = listOf(4),
                    maximumLeap = 4
                ),
                catalog()
            )
        )
        assertTrue(
            "the impossible context must surface against the range: $issues",
            issues.any {
                it.field == SetupField.RANGE &&
                    it.explanation.contains("C major") &&
                    it.explanation.contains("tonic triad") &&
                    it.explanation.contains("105..108")
            }
        )
    }

    @Test
    fun endpointDegreesUseThePlayableRange() {
        val anchored = MelodicDictationSetupValidator.validate(
            draft(minimumNote = MidiNote.of(60), maximumNote = MidiNote.of(64)),
            catalog()
        )
        assertTrue("C4 anchors the context", anchored is SetupValidation.Valid)

        val noTonic = MelodicDictationSetupValidator.validate(
            draft(minimumNote = MidiNote.of(62), maximumNote = MidiNote.of(64)),
            catalog()
        )
        assertTrue("D4..E4 contains no C tonic anchor", noTonic is SetupValidation.Invalid)
    }

    @Test
    fun aRangeWithoutAnyScaleNoteIsReportedOnTheRangeField() {
        // C#4 is not a C major scale note, so no phrase can start or end in this one-note range.
        val issues = issuesOf(
            MelodicDictationSetupValidator.validate(
                draft(
                    minimumNote = MidiNote.of(61),
                    maximumNote = MidiNote.of(61),
                    phraseLength = 2
                ),
                catalog()
            )
        )
        assertTrue(
            "the empty graph must surface against the range: $issues",
            issues.any {
                it.field == SetupField.RANGE &&
                    it.explanation.contains("C major") &&
                    it.explanation.contains("61..61")
            }
        )
    }

    @Test
    fun missingAnswerClipsAreReportedOnTheAnswersField() {
        val partial = MapVoiceDurationCatalog.of("degree.one" to 500.milliseconds)
        val issues = issuesOf(MelodicDictationSetupValidator.validate(draft(), partial))
        assertTrue(
            "every missing clip belongs to the answer checklist: $issues",
            issues.isNotEmpty() && issues.all { it.field == SetupField.ANSWERS }
        )
        listOf("degree.two", "degree.five").forEach { id ->
            assertTrue(
                "the missing clip $id must be named: $issues",
                issues.any { it.explanation.contains(id) }
            )
        }
    }

    @Test
    fun anAfterCycleStopPolicyIsRejectedWithTheCycleExplanation() {
        val issues = issuesOf(
            MelodicDictationSetupValidator.validate(
                draft(stopPolicy = SessionStopPolicy.AfterCycle),
                catalog()
            )
        )
        assertTrue(
            "the mode owns no label cycle, so the policy must be explained: $issues",
            issues.any {
                it.field == SetupField.STOP_POLICY &&
                    it.explanation.contains("no label cycle")
            }
        )
    }

    @Test
    fun aFinishCycleDurationPolicyIsRejectedWithTheCycleExplanation() {
        val issues = issuesOf(
            MelodicDictationSetupValidator.validate(
                draft(
                    stopPolicy = SessionStopPolicy.Duration(
                        limit = 20.minutes,
                        finish = DurationFinish.FINISH_CYCLE
                    )
                ),
                catalog()
            )
        )
        assertTrue(
            "a cycle drain has no cycle to finish: $issues",
            issues.any {
                it.field == SetupField.STOP_POLICY &&
                    it.explanation.contains("no label cycle to finish")
            }
        )
    }

    @Test
    fun completedItemAndDurationStopPoliciesPassTheStopCheck() {
        val policies = listOf(
            SessionStopPolicy.CompletedItems(10),
            SessionStopPolicy.Duration(limit = 20.minutes, finish = DurationFinish.IMMEDIATE),
            SessionStopPolicy.Duration(
                limit = 20.minutes,
                finish = DurationFinish.FINISH_CURRENT_ITEM
            ),
            SessionStopPolicy.UntilStopped
        )
        policies.forEach { policy ->
            val validation = MelodicDictationSetupValidator.validate(
                draft(stopPolicy = policy),
                catalog()
            )
            assertTrue(
                "the policy $policy must be available for melodic dictation: $validation",
                validation is SetupValidation.Valid
            )
        }
    }

    @Test
    fun anExcessiveNoteDurationIsADurationError() {
        // The default tonic-triad context plus four 30-second phrase notes far exceeds the 60 s
        // complete-exercise cap, and the draft's own timing must drive the re-timed check.
        val issues = issuesOf(
            MelodicDictationSetupValidator.validate(draft(noteDuration = 30.seconds), catalog())
        )
        assertTrue(issues.any { it.field == SetupField.DURATION })
    }

    @Test
    fun aContextGapBeyondThirtySecondsIsReportedOnTheContextGapField() {
        val issues = issuesOf(
            MelodicDictationSetupValidator.validate(draft(contextGap = 31.seconds), catalog())
        )
        assertTrue(issues.any { it.field == SetupField.CONTEXT_GAP })
    }

    @Test
    fun aSleepLimitBeyondThreeHoursIsRefused() {
        val issues = issuesOf(
            MelodicDictationSetupValidator.validate(
                draft(
                    stopPolicy = SessionStopPolicy.Duration(
                        limit = 200.minutes,
                        finish = DurationFinish.IMMEDIATE
                    )
                ),
                catalog()
            )
        )
        assertTrue(issues.any { it.field == SetupField.DURATION })
    }

    @Test
    fun thePhraseFieldsReachTheDefinitionAndTheDraftRoundTrips() {
        val original = MelodicDictationSetup.default().copy(
            keyValues = listOf(0, 9),
            tonalities = listOf(Tonality.MAJOR, Tonality.NATURAL_MINOR),
            context = ContextKind.TONIC_NOTE,
            contextGap = 750.milliseconds,
            minimumNote = MidiNote.of(57),
            maximumNote = MidiNote.of(84),
            phraseLength = 5,
            startDegree = null,
            endDegree = 3,
            allowedSteps = listOf(-4, -2, 0, 2, 4),
            maximumLeap = 4,
            bias = MelodicDirectionBias.ASCENDING,
            answerFormat = MelodicAnswerFormat.DIRECTED_INTERVAL_SEQUENCE,
            spelling = MusicSpellingPreference.FLAT,
            mode = RecipeMode.EXPOSURE,
            replayCount = 2,
            noteDuration = 900.milliseconds,
            seed = 99L,
            separatorCue = true
        )
        assertEquals(original, MelodicDictationSetupDraft.from(original).toSetup())
        val definition = original.toDefinition()
        assertEquals(listOf(0, 9), definition.keyValues)
        assertEquals(listOf(Tonality.MAJOR, Tonality.NATURAL_MINOR), definition.tonalities)
        assertEquals(ContextKind.TONIC_NOTE, definition.context)
        assertEquals(750.milliseconds, definition.contextGap)
        assertEquals(57, definition.minimumNoteValue)
        assertEquals(84, definition.maximumNoteValue)
        assertEquals(5, definition.phraseLength)
        assertEquals(null, definition.startDegree)
        assertEquals(3, definition.endDegree)
        assertEquals(listOf(-4, -2, 0, 2, 4), definition.allowedSteps)
        assertEquals(4, definition.maximumLeap)
        assertEquals(MelodicDirectionBias.ASCENDING, definition.bias)
        assertEquals(MelodicAnswerFormat.DIRECTED_INTERVAL_SEQUENCE, definition.answerFormat)
        assertEquals(MusicSpellingPreference.FLAT, definition.spelling)
    }

    @Test
    fun aStoredDurationStopPolicyNormalizesToUntilStoppedInTheDraft() {
        val stored = MelodicDictationSetup.default().copy(
            stopPolicy = SessionStopPolicy.Duration(
                limit = 20.minutes,
                finish = DurationFinish.IMMEDIATE
            )
        )
        assertEquals(
            SessionStopPolicy.UntilStopped,
            MelodicDictationSetupDraft.from(stored).stopPolicy
        )
    }

    @Test
    fun theUserSettingsDefaultsCarryTheMelodicDictationModeSetup() {
        val settings = UserSettings.DEFAULTS
        assertTrue(settings.modeSetups.containsKey(TrainingKind.MELODIC_DICTATION))
        assertEquals(MelodicDictationSetup.default(), settings.melodicDictationSetup)
        assertTrue(
            settings.modeSetups.getValue(TrainingKind.MELODIC_DICTATION).payload
                is ModePayload.MelodicDictation
        )
    }
}
