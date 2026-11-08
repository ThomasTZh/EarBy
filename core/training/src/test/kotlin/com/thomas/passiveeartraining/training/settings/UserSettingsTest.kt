package com.thomas.passiveeartraining.training.settings

import com.thomas.passiveeartraining.music.IntervalPresentation
import com.thomas.passiveeartraining.music.MidiNote
import com.thomas.passiveeartraining.music.PitchClass
import com.thomas.passiveeartraining.music.SimpleInterval
import com.thomas.passiveeartraining.training.IntervalDefinition
import com.thomas.passiveeartraining.training.recipe.RecipeMode
import com.thomas.passiveeartraining.training.recipe.SessionStopPolicy
import kotlin.time.Duration
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class UserSettingsTest {

    @Test
    fun defaultsAreValidAndMatchTheReviewedBalance() {
        val defaults = UserSettings.DEFAULTS
        assertEquals(
            "settings schema 4 removes the solfège naming feature",
            4,
            defaults.schemaVersion
        )
        assertEquals(1f, defaults.musicGain)
        assertEquals("the reviewed voice balance is the default", 0.25f, defaults.voiceGain)
        assertEquals(ThemePreference.SYSTEM, defaults.theme)
        assertEquals("the sleep timer starts off", 0, defaults.sleepTimerMinutes)
        assertFalse(
            "dynamic colour is off by default so the brand palette applies",
            defaults.dynamicColor
        )
        assertEquals(
            "the answer is shown only once consumed",
            AnswerVisibility.SHOWN_WHEN_CONSUMED,
            defaults.answerVisibility
        )
        assertFalse("the advanced panel starts collapsed", defaults.advancedPanelExpanded)
        assertEquals(
            "intervals is the default selected kind",
            com.thomas.passiveeartraining.training.provider.TrainingKind.INTERVALS,
            defaults.selectedKind
        )
        assertEquals(
            "the intervals setup always exists at revision 1",
            LastSetup.default(),
            defaults.lastSetup
        )
        assertEquals(
            1L,
            defaults.modeSetups
                .getValue(com.thomas.passiveeartraining.training.provider.TrainingKind.INTERVALS)
                .revision
        )
        assertEquals(TrainingMode.INTERVALS, defaults.lastMode)
    }

    @Test
    fun aSavedSetupRevisionRefusesToWrap() {
        val payload = ModePayload.Intervals(LastSetup.default())
        val last = SavedModeSetup(Long.MAX_VALUE, payload)
        assertThrows(IllegalStateException::class.java) { last.nextRevision() }
        assertThrows(IllegalArgumentException::class.java) { SavedModeSetup(0L, payload) }
    }

    @Test
    fun theDefaultSetupReproducesTheDomainDefault() {
        // The stored setup and the domain default must describe the same configuration, or a fresh
        // install would start differently from the documented default.
        val fromSetup = LastSetup.default().toDefinition()
        val domain = IntervalDefinition.default()
        assertEquals(domain.selectedIntervalIds, fromSetup.selectedIntervalIds)
        assertEquals(domain.rootValues, fromSetup.rootValues)
        assertEquals(domain.minimumNoteValue, fromSetup.minimumNoteValue)
        assertEquals(domain.maximumNoteValue, fromSetup.maximumNoteValue)
        assertEquals(domain.presentation, fromSetup.presentation)
    }

    @Test
    fun gainsOutsideTheSupportedRangeAreRejected() {
        assertThrows(IllegalArgumentException::class.java) { UserSettings(musicGain = -0.1f) }
        assertThrows(IllegalArgumentException::class.java) { UserSettings(musicGain = 4.1f) }
        assertThrows(IllegalArgumentException::class.java) { UserSettings(voiceGain = -1f) }
        UserSettings(musicGain = UserSettings.MIN_GAIN, voiceGain = UserSettings.MAX_GAIN)
    }

    @Test
    fun aSchemaVersionMustBePositive() {
        assertThrows(IllegalArgumentException::class.java) { UserSettings(schemaVersion = 0) }
    }

    @Test
    fun anEmptySelectionMeansEveryOptionAndAnInvertedRangeIsStillRejected() {
        val empty = LastSetup.default().copy(intervalIds = emptyList(), rootValues = emptyList())
        assertEquals(
            SimpleInterval.ALL.map { it.id }.toSet(),
            empty.toDefinition().selectedIntervalIds.toSet()
        )
        assertEquals(
            PitchClass.ALL.map { it.value }.toSet(),
            empty.toDefinition().selectedRoots.map { it.value }.toSet()
        )
        assertThrows(IllegalArgumentException::class.java) {
            LastSetup.default().copy(minimumNote = 72, maximumNote = 60)
        }
    }

    @Test
    fun aStoredSetupRoundTripsThroughItsDefinition() {
        val setup = LastSetup(
            intervalIds = listOf("minor_third", "octave"),
            rootValues = listOf(0, 7),
            minimumNote = 60,
            maximumNote = 72,
            presentation = IntervalPresentation.DESCENDING,
            mode = RecipeMode.ASSOCIATION,
            thinkingSilence = Duration.parse("3s"),
            replayCount = 2,
            stopPolicy = SessionStopPolicy.CompletedItems(20)
        )
        val definition = setup.toDefinition()
        assertEquals(listOf("minor_third", "octave"), definition.selectedIntervalIds)
        assertEquals(60, definition.minimumNoteValue)
        assertEquals(72, definition.maximumNoteValue)
        assertEquals(IntervalPresentation.DESCENDING, definition.presentation)

        // Everything the definition captures round-trips, so a restored setup is the same setup.
        val restored = setup.copy(
            intervalIds = definition.selectedIntervalIds,
            rootValues = definition.rootValues,
            minimumNote = definition.minimumNoteValue,
            maximumNote = definition.maximumNoteValue,
            presentation = definition.presentation
        )
        assertEquals(setup, restored)
    }

    @Test
    fun aSnapshotWithoutDiagnosticsNeedsNoAttention() {
        val clean = SettingsSnapshot(UserSettings.DEFAULTS)
        assertFalse(clean.needsAttention)

        val recovered = SettingsSnapshot(
            UserSettings.DEFAULTS,
            listOf(SettingsDiagnostic.InvalidValue("musicGain", "not a number"))
        )
        assertTrue("a recovered read must ask for attention", recovered.needsAttention)
    }

    @Test
    fun diagnosticsDescribeEachRecoveryCase() {
        val newer = SettingsDiagnostic.NewerSchema(stored = 2, supported = 1)
        assertTrue(newer.toString().contains("2"))
        val unreadable = SettingsDiagnostic.Unreadable("file truncated")
        assertTrue(unreadable.toString().contains("truncated"))
        assertTrue(SettingsDiagnostic.InvalidValue("theme", "unknown").field == "theme")
    }

    @Test
    fun aFixedRootBuildsAVersionTwoPolicy() {
        val withFixedRoot = LastSetup.default().copy(fixedRoot = 60)
        val policy = withFixedRoot.toPolicy()

        assertEquals("a fixed root is a version 2 draw", 2, policy.generatorVersion)
        assertEquals(MidiNote.of(60), policy.fixedRoot)
        assertEquals(listOf(IntervalPresentation.ASCENDING), policy.presentations)
        assertEquals(
            "without a fixed root the original draw order stays version 1",
            1,
            LastSetup.default().toPolicy().generatorVersion
        )
    }

    @Test
    fun aFixedRootOutsideTheMidiRangeIsRejectedOnConstruction() {
        assertThrows(IllegalArgumentException::class.java) {
            LastSetup.default().copy(fixedRoot = 200)
        }
    }
}
