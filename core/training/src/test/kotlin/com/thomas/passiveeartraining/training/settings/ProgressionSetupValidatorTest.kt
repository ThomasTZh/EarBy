package com.thomas.passiveeartraining.training.settings

import com.thomas.passiveeartraining.music.DiatonicChord
import com.thomas.passiveeartraining.music.ProgressionVoicingMode
import com.thomas.passiveeartraining.music.SpellingPreference as MusicSpellingPreference
import com.thomas.passiveeartraining.training.BundledVoiceDurations
import com.thomas.passiveeartraining.training.MapVoiceDurationCatalog
import com.thomas.passiveeartraining.training.ProgressionAnswerFormat
import com.thomas.passiveeartraining.training.provider.ProviderConfig
import com.thomas.passiveeartraining.training.provider.TrainingKind
import com.thomas.passiveeartraining.training.provider.VoiceVocabulary
import com.thomas.passiveeartraining.training.recipe.RecipeMode
import com.thomas.passiveeartraining.training.recipe.SessionStopPolicy
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** M9B: the progression setup, its draft, its validator and the provider projection. */
class ProgressionSetupValidatorTest {

    /** Every vocabulary entry has a measured 500 ms clip. */
    private fun catalog(clipLength: Duration = 500.milliseconds): MapVoiceDurationCatalog =
        MapVoiceDurationCatalog(
            VoiceVocabulary.entries.associate { it.id to clipLength }
        )

    /** The same catalog without the harmony clips, for the partial-catalog reporting case. */
    private fun catalogWithoutHarmony(): MapVoiceDurationCatalog = MapVoiceDurationCatalog(
        VoiceVocabulary.entries
            .filterNot { it.id.value.startsWith("harmony.") }
            .associate { it.id to 500.milliseconds }
    )

    private fun draft(
        chordIds: List<String> = DiatonicChord.TRIADS.map { it.id },
        fixedRoot: Int? = null,
        voicing: ProgressionVoicingMode = ProgressionVoicingMode.ROOT_POSITION,
        allowedInversions: List<Int> = listOf(0, 1, 2, 3),
        answerFormat: ProgressionAnswerFormat = ProgressionAnswerFormat.ROMAN,
        spelling: MusicSpellingPreference = MusicSpellingPreference.SHARP,
        minimumNote: Int = 48,
        maximumNote: Int = 84,
        mode: RecipeMode = RecipeMode.RECOGNITION,
        noteDuration: Duration = 700.milliseconds,
        replayCount: Int = 0,
        stopPolicy: SessionStopPolicy = SessionStopPolicy.UntilStopped
    ) = ProgressionSetupDraft(
        chordIds = chordIds,
        fixedRoot = fixedRoot,
        voicing = voicing,
        allowedInversions = allowedInversions,
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
    fun theDefaultProgressionSetupValidatesAgainstRomanCoverageAndTheBundledClips() {
        val withSynthetic = ProgressionSetupValidator.validate(
            ProgressionSetupDraft.from(ProgressionSetup.default()),
            catalog()
        )
        assertTrue(
            "default progression draft must validate with Roman coverage: $withSynthetic",
            withSynthetic is SetupValidation.Valid
        )
        val withBundled = ProgressionSetupValidator.validate(
            ProgressionSetupDraft.from(ProgressionSetup.default()),
            BundledVoiceDurations
        )
        assertTrue(
            "the default Roman setup must validate against the bundled clips: $withBundled",
            withBundled is SetupValidation.Valid
        )
    }

    @Test
    fun theDefaultSetupBuildsTheProgressionsProviderConfig() {
        val setup = ProgressionSetup.default()
        val request = setup.toStartRequest(
            seed = 42L,
            musicGain = 0.5f,
            voiceGain = 0.25f
        )
        val provider = request.setup.provider
        assertTrue(provider is ProviderConfig.Progressions)
        assertEquals(TrainingKind.CHORD_PROGRESSIONS, provider.kind)
        assertEquals(42L, request.setup.seed)
        assertEquals(0.5f, request.musicGain, 0f)
        assertEquals(0.25f, request.voiceGain, 0f)
        val definition = (provider as ProviderConfig.Progressions).definition
        assertEquals(DiatonicChord.TRIADS, definition.chords)
        assertEquals(null, definition.fixedRoot)
        assertEquals(ProgressionVoicingMode.ROOT_POSITION, definition.voicing)
        assertEquals(listOf(0, 1, 2, 3), definition.allowedInversions)
        assertEquals(ProgressionAnswerFormat.ROMAN, definition.answerFormat)
        assertEquals(MusicSpellingPreference.SHARP, definition.spelling)
        assertEquals(48, definition.minimumNoteValue)
        assertEquals(84, definition.maximumNoteValue)
        assertEquals(
            "the inter-chord gap reaches the compiled timeline through the recipe",
            100.milliseconds,
            setup.toRecipe().timing.melodicGap
        )
    }

    @Test
    fun anEmptyChordSelectionIsReportedOnTheChordsField() {
        val validation = ProgressionSetupValidator.validate(
            draft(chordIds = emptyList()),
            catalog()
        )
        assertTrue(validation is SetupValidation.Invalid)
        assertTrue(
            (validation as SetupValidation.Invalid).issues
                .any { it.field == SetupField.CHORDS }
        )
    }

    @Test
    fun anUnknownChordIdIsReportedOnTheChordsField() {
        val validation = ProgressionSetupValidator.validate(
            draft(chordIds = listOf(DiatonicChord.I.id, "locrian_chord")),
            catalog()
        )
        assertTrue(validation is SetupValidation.Invalid)
        val issues = (validation as SetupValidation.Invalid).issues
        assertTrue(
            "the unknown id must surface against the chord control: $issues",
            issues.any {
                it.field == SetupField.CHORDS && it.explanation.contains("locrian_chord")
            }
        )
    }

    @Test
    fun aPoolWithoutIValidatesBecauseTheOpeningIsFixed() {
        val validation = ProgressionSetupValidator.validate(
            draft(chordIds = listOf(DiatonicChord.II.id, DiatonicChord.V.id)),
            catalog()
        )
        assertTrue(
            "a pool without I is legal now: $validation",
            validation is SetupValidation.Valid
        )
    }

    @Test
    fun aSelectionThatNoKeyCanRealizeIsReportedOnTheRangeField() {
        val validation = ProgressionSetupValidator.validate(
            draft(
                chordIds = listOf(DiatonicChord.I.id, DiatonicChord.V.id),
                minimumNote = 105,
                maximumNote = 108
            ),
            catalog()
        )
        assertTrue(validation is SetupValidation.Invalid)
        val issues = (validation as SetupValidation.Invalid).issues
        assertTrue(
            "the unusable range must explain the opening and the range: $issues",
            issues.any {
                it.field == SetupField.RANGE &&
                    it.explanation.contains("opening I") &&
                    it.explanation.contains("105..108")
            }
        )
    }

    @Test
    fun anInvertedRangeIsRefused() {
        val validation = ProgressionSetupValidator.validate(
            draft(minimumNote = 84, maximumNote = 48),
            catalog()
        )
        assertTrue(
            (validation as SetupValidation.Invalid).issues.any { it.field == SetupField.RANGE }
        )
    }

    @Test
    fun anOutOfRangeAllowedInversionIsReportedOnTheInversionsField() {
        val validation = ProgressionSetupValidator.validate(
            draft(allowedInversions = listOf(0, 4)),
            catalog()
        )
        assertTrue(validation is SetupValidation.Invalid)
        assertTrue(
            (validation as SetupValidation.Invalid).issues
                .any { it.field == SetupField.INVERSIONS }
        )
    }

    @Test
    fun aFixedRootOutsideTheRangeIsReportedOnTheFixedNoteField() {
        val validation = ProgressionSetupValidator.validate(
            draft(fixedRoot = 90),
            catalog()
        )
        assertTrue(validation is SetupValidation.Invalid)
        assertTrue(
            (validation as SetupValidation.Invalid).issues
                .any { it.field == SetupField.FIXED_NOTE }
        )
    }

    @Test
    fun anAfterCycleStopPolicyIsRefused() {
        val validation = ProgressionSetupValidator.validate(
            draft(stopPolicy = SessionStopPolicy.AfterCycle),
            catalog()
        )
        assertTrue(validation is SetupValidation.Invalid)
        assertTrue(
            (validation as SetupValidation.Invalid).issues
                .any { it.field == SetupField.STOP_POLICY }
        )
    }

    @Test
    fun missingHarmonyClipsAreReportedOnTheAnswerField() {
        val validation = ProgressionSetupValidator.validate(
            draft(answerFormat = ProgressionAnswerFormat.FUNCTIONS),
            catalogWithoutHarmony()
        )
        assertTrue(validation is SetupValidation.Invalid)
        val issues = (validation as SetupValidation.Invalid).issues
        assertEquals(3, issues.size)
        assertTrue(
            "every issue belongs to answers: $issues",
            issues.all {
                it.field == SetupField.ANSWERS
            }
        )
        listOf("harmony.tonic", "harmony.predominant", "harmony.dominant").forEach { id ->
            assertTrue(
                "the missing clip $id must be named: $issues",
                issues.any { it.explanation.contains(id) }
            )
        }
    }

    @Test
    fun aCatalogWithTheHarmonyClipsValidatesTheFunctionsFormat() {
        val validation = ProgressionSetupValidator.validate(
            draft(answerFormat = ProgressionAnswerFormat.FUNCTIONS),
            catalog()
        )
        assertTrue(
            "a synthetic catalog with the harmony clips validates: $validation",
            validation is SetupValidation.Valid
        )
        val bundled = ProgressionSetupValidator.validate(
            draft(answerFormat = ProgressionAnswerFormat.FUNCTIONS),
            BundledVoiceDurations
        )
        assertTrue(
            "the accepted harmony clips are in the bundled pack: $bundled",
            bundled is SetupValidation.Valid
        )
    }

    @Test
    fun anExcessiveChordDurationIsADurationError() {
        // Four 20-second chords far exceed the 60 s complete-exercise cap.
        val validation = ProgressionSetupValidator.validate(
            draft(noteDuration = 20.seconds),
            catalog()
        )
        assertTrue(validation is SetupValidation.Invalid)
        assertTrue(
            (validation as SetupValidation.Invalid).issues
                .any { it.field == SetupField.DURATION }
        )
    }

    @Test
    fun thePoolFixedRootVoicingAndFormatReachTheDefinitionAndTheDraftRoundTrips() {
        val original = ProgressionSetup.default().copy(
            chordIds = listOf(
                DiatonicChord.I.id,
                DiatonicChord.II7.id,
                DiatonicChord.V7.id
            ),
            voicing = ProgressionVoicingMode.NEAREST_INVERSION,
            allowedInversions = listOf(0, 1, 3),
            answerFormat = ProgressionAnswerFormat.ROOT_MOTION,
            spelling = MusicSpellingPreference.FLAT,
            minimumNote = 57,
            maximumNote = 84,
            mode = RecipeMode.EXPOSURE,
            replayCount = 2,
            noteDuration = 900.milliseconds,
            melodicGap = 150.milliseconds,
            seed = 99L,
            separatorCue = true
        )
        assertEquals(original, ProgressionSetupDraft.from(original).toSetup())
        val definition = original.toDefinition()
        assertEquals(
            listOf(DiatonicChord.I, DiatonicChord.II7, DiatonicChord.V7),
            definition.chords
        )
        assertEquals(ProgressionVoicingMode.NEAREST_INVERSION, definition.voicing)
        assertEquals(listOf(0, 1, 3), definition.allowedInversions)
        assertEquals(ProgressionAnswerFormat.ROOT_MOTION, definition.answerFormat)
        assertEquals(MusicSpellingPreference.FLAT, definition.spelling)
        assertEquals(57, definition.minimumNoteValue)
        assertEquals(84, definition.maximumNoteValue)
        assertEquals(900.milliseconds, original.toRecipe().timing.noteDuration)
        assertEquals(150.milliseconds, original.toRecipe().timing.melodicGap)
        assertEquals(2, original.toRecipe().replayCount)
        assertEquals(true, original.toRecipe().separatorCue)
    }

    @Test
    fun aStoredDurationStopPolicyNormalizesToUntilStoppedInTheDraft() {
        val stored = ProgressionSetup.default().copy(
            stopPolicy = SessionStopPolicy.Duration(10.minutes)
        )
        assertEquals(
            SessionStopPolicy.UntilStopped,
            ProgressionSetupDraft.from(stored).stopPolicy
        )
    }
}
