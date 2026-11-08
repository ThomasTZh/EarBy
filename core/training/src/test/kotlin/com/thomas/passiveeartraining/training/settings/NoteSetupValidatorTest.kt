package com.thomas.passiveeartraining.training.settings

import com.thomas.passiveeartraining.music.PitchClass
import com.thomas.passiveeartraining.music.SpellingPreference as MusicSpellingPreference
import com.thomas.passiveeartraining.training.MapVoiceDurationCatalog
import com.thomas.passiveeartraining.training.provider.ProviderConfig
import com.thomas.passiveeartraining.training.provider.TrainingKind
import com.thomas.passiveeartraining.training.provider.VoiceVocabulary
import com.thomas.passiveeartraining.training.recipe.DurationFinish
import com.thomas.passiveeartraining.training.recipe.RecipeDefinition
import com.thomas.passiveeartraining.training.recipe.RecipeMode
import com.thomas.passiveeartraining.training.recipe.SessionStopPolicy
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** M7-T03/M7-T05: the note setup, its draft and its validator. */
class NoteSetupValidatorTest {

    private fun catalog(clipLength: Duration = 500.milliseconds): MapVoiceDurationCatalog =
        MapVoiceDurationCatalog(
            VoiceVocabulary.entries.associate { it.id to clipLength }
        )

    private fun draft(
        pitchClassValues: List<Int> = (0..11).toList(),
        minimumNote: Int = 60,
        maximumNote: Int = 72,
        accidental: MusicSpellingPreference = MusicSpellingPreference.SHARP,
        speakOctave: Boolean = false,
        mode: RecipeMode = RecipeMode.RECOGNITION,
        replayCount: Int = 0,
        stopPolicy: SessionStopPolicy = SessionStopPolicy.UntilStopped
    ) = NoteSetupDraft(
        pitchClassValues = pitchClassValues,
        minimumNote = minimumNote,
        maximumNote = maximumNote,
        accidental = accidental,
        speakOctave = speakOctave,
        mode = mode,
        thinkingSilence = 2.seconds,
        replayCount = replayCount,
        stopPolicy = stopPolicy
    )

    @Test
    fun theDefaultNoteSetupValidates() {
        val validation = NoteSetupValidator.validate(draft(), catalog())
        assertTrue(
            "default note draft must validate: $validation",
            validation is SetupValidation.Valid
        )
    }

    @Test
    fun theDefaultSetupBuildsTheNotesProviderConfig() {
        val setup = NoteSetup.default()
        val request = setup.toStartRequest(
            seed = 42L,
            musicGain = 0.5f,
            voiceGain = 0.25f
        )
        val provider = request.setup.provider
        assertTrue(provider is ProviderConfig.Notes)
        assertEquals(TrainingKind.NOTE_IDENTIFICATION, provider.kind)
        assertEquals(42L, request.setup.seed)
        assertEquals(0.5f, request.musicGain, 0f)
        assertEquals(0.25f, request.voiceGain, 0f)
        val definition = (provider as ProviderConfig.Notes).definition
        assertEquals(PitchClass.ALL, definition.selectedPitchClasses)
        assertEquals(MusicSpellingPreference.SHARP, definition.accidental)
        assertEquals(false, definition.speakOctave)
        assertEquals(60, definition.minimumNoteValue)
        assertEquals(72, definition.maximumNoteValue)
    }

    @Test
    fun anEmptyNoteSelectionIsReportedOnTheNoteClassesField() {
        val validation = NoteSetupValidator.validate(
            draft(pitchClassValues = emptyList()),
            catalog()
        )
        assertTrue(validation is SetupValidation.Invalid)
        assertTrue(
            (validation as SetupValidation.Invalid).issues
                .any { it.field == SetupField.NOTE_CLASSES }
        )
    }

    @Test
    fun anUnknownPitchClassIsReportedOnTheNoteClassesField() {
        val validation = NoteSetupValidator.validate(
            draft(pitchClassValues = listOf(0, 12)),
            catalog()
        )
        assertTrue(
            (validation as SetupValidation.Invalid).issues
                .any { it.field == SetupField.NOTE_CLASSES }
        )
    }

    @Test
    fun anInvertedRangeIsRefused() {
        val validation = NoteSetupValidator.validate(
            draft(minimumNote = 72, maximumNote = 60),
            catalog()
        )
        assertTrue(
            (validation as SetupValidation.Invalid).issues.any { it.field == SetupField.RANGE }
        )
    }

    @Test
    fun aRangeOutsideMidiIsRefusedOnTheRangeField() {
        val validation = NoteSetupValidator.validate(
            draft(minimumNote = 128, maximumNote = 130),
            catalog()
        )
        assertTrue(
            (validation as SetupValidation.Invalid).issues.any { it.field == SetupField.RANGE }
        )
    }

    @Test
    fun aRangeThatExcludesOneSelectedClassKeepsTheOtherPlayable() {
        val validation = NoteSetupValidator.validate(
            draft(pitchClassValues = listOf(0, 1), minimumNote = 61, maximumNote = 62),
            catalog()
        )
        assertTrue(
            "a partially feasible selection is valid; the unavailable class is warned in the UI",
            validation is SetupValidation.Valid
        )
    }

    @Test
    fun missingNoteClipsAreReportedOnTheAnswerField() {
        val partial = MapVoiceDurationCatalog.of("note.c" to 500.milliseconds)
        val validation = NoteSetupValidator.validate(draft(), partial)
        assertTrue(validation is SetupValidation.Invalid)
        val issues = (validation as SetupValidation.Invalid).issues
        assertTrue(
            issues.any {
                it.field == SetupField.ANSWERS && it.explanation.contains("note.c_sharp")
            }
        )
    }

    @Test
    fun anExcessiveAssociationWithRepeatsIsADurationError() {
        // Every name clip is 50 s long; the recognition default then fits, but an association
        // with every replay exceeds the 60 s complete-exercise cap.
        val validation = NoteSetupValidator.validate(
            draft(mode = RecipeMode.ASSOCIATION, replayCount = RecipeDefinition.MAX_REPLAY),
            catalog(50.seconds)
        )
        assertTrue(validation is SetupValidation.Invalid)
        assertTrue(
            (validation as SetupValidation.Invalid).issues
                .any { it.field == SetupField.DURATION }
        )

        val withinCap = NoteSetupValidator.validate(
            draft(mode = RecipeMode.ASSOCIATION),
            catalog(50.seconds)
        )
        assertTrue(
            "the same setup without repeats fits: $withinCap",
            withinCap is SetupValidation.Valid
        )
    }

    @Test
    fun aSleepLimitBeyondThreeHoursIsRefused() {
        val validation = NoteSetupValidator.validate(
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
        val original = NoteSetup.default().copy(
            pitchClassValues = listOf(0, 1, 7),
            accidental = MusicSpellingPreference.FLAT,
            speakOctave = true,
            mode = RecipeMode.EXPOSURE,
            replayCount = 2,
            noteDuration = 900.milliseconds,
            seed = 99L,
            separatorCue = true
        )
        assertEquals(original, NoteSetupDraft.from(original).toSetup())
    }

    @Test
    fun aStoredDurationStopPolicyNormalizesToUntilStoppedInTheDraft() {
        val stored = NoteSetup.default().copy(
            stopPolicy = SessionStopPolicy.Duration(
                limit = 20.minutes,
                finish = DurationFinish.IMMEDIATE
            )
        )
        assertEquals(SessionStopPolicy.UntilStopped, NoteSetupDraft.from(stored).stopPolicy)
    }

    @Test
    fun theUserSettingsDefaultsCarryTheNoteModeSetup() {
        val settings = UserSettings.DEFAULTS
        assertTrue(settings.modeSetups.containsKey(TrainingKind.NOTE_IDENTIFICATION))
        assertEquals(NoteSetup.default(), settings.noteSetup)
        assertTrue(
            settings.modeSetups.getValue(TrainingKind.NOTE_IDENTIFICATION).payload
                is ModePayload.Notes
        )
    }

    @Test
    fun aLongNoteDurationIsRefusedAtTheCap() {
        // M7-T05 regression for the re-timed content bound: a 59 s note plus the recipe
        // silences exceeds the 60 s cap.
        val validation = NoteSetupValidator.validate(
            draft().copy(noteDuration = 59.seconds),
            catalog()
        )
        assertTrue(validation is SetupValidation.Invalid)
        assertTrue(
            (validation as SetupValidation.Invalid).issues
                .any { it.field == SetupField.DURATION }
        )
    }
}
