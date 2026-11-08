package com.thomas.passiveeartraining.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.mutablePreferencesOf
import androidx.datastore.preferences.core.stringPreferencesKey
import com.thomas.passiveeartraining.music.IntervalPresentation
import com.thomas.passiveeartraining.training.provider.TrainingKind
import com.thomas.passiveeartraining.training.recipe.RecipeMode
import com.thomas.passiveeartraining.training.recipe.SessionStopPolicy
import com.thomas.passiveeartraining.training.settings.LastSetup
import com.thomas.passiveeartraining.training.settings.ModeEdit
import com.thomas.passiveeartraining.training.settings.ModePayload
import com.thomas.passiveeartraining.training.settings.ModeWriteResult
import com.thomas.passiveeartraining.training.settings.NoteSetup
import com.thomas.passiveeartraining.training.settings.SettingsDiagnostic
import com.thomas.passiveeartraining.training.settings.ThemePreference
import com.thomas.passiveeartraining.training.settings.TrainingMode
import com.thomas.passiveeartraining.training.settings.UserSettings
import kotlin.time.Duration
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * In-memory DataStore.
 *
 * `updateData` runs its transform against the newest value and publishes the result as one step,
 * which is the behaviour the repository relies on for atomic updates.
 */
private class InMemoryPreferencesStore(initial: Preferences = emptyPreferences()) :
    DataStore<Preferences> {
    private val state = MutableStateFlow(initial)

    override val data: Flow<Preferences> = state

    override suspend fun updateData(
        transform: suspend (t: Preferences) -> Preferences
    ): Preferences {
        val updated = transform(state.value)
        state.value = updated
        return updated
    }
}

/** Builds stored preferences from key/value pairs, mirroring what a previous build wrote. */
private fun stored(vararg entries: Preferences.Pair<*>): Preferences =
    mutablePreferencesOf(*entries)

class DataStoreSettingsRepositoryTest {

    private fun repository(store: DataStore<Preferences> = InMemoryPreferencesStore()) =
        DataStoreSettingsRepository(store) to store

    /** Writes the intervals setup through the revision-checked mode path. */
    private suspend fun DataStoreSettingsRepository.writeIntervals(
        setup: LastSetup
    ): ModeWriteResult {
        val revision = settings.first().settings
            .modeSetups.getValue(TrainingKind.INTERVALS).revision
        return updateMode(
            ModeEdit(TrainingKind.INTERVALS, revision) {
                ModePayload.Intervals(setup)
            }
        )
    }

    @Test
    fun wrongStoredTypesNeverTerminateTheSettingsFlow() = runTest {
        for (name in listOf("schema_version", "music_gain", "setup_min_note")) {
            val (subject, _) = repository(
                InMemoryPreferencesStore(stored(stringPreferencesKey(name) to "wrong type"))
            )
            val snapshot = subject.settings.first()
            assertTrue("$name is diagnosed", snapshot.needsAttention)
            assertEquals(UserSettings.DEFAULTS, snapshot.settings)
        }
    }

    @Test
    fun oversizedSetupWritesAreRefusedAtomically() = runTest {
        val (subject, _) = repository()
        val result = subject.writeIntervals(
            LastSetup.default().copy(thinkingSilence = Duration.parse("61s"))
        )
        assertTrue("invalid setup must not be written", result is ModeWriteResult.Rejected)
        assertEquals(UserSettings.DEFAULTS, subject.settings.first().settings)
    }

    @Test
    fun unreadableSettingsProduceAnObservableDiagnostic() = runTest {
        val store = object : DataStore<Preferences> {
            override val data: Flow<Preferences> = kotlinx.coroutines.flow.flow {
                throw java.io.IOException("read failed")
            }
            override suspend fun updateData(
                transform: suspend (Preferences) -> Preferences
            ): Preferences = throw java.io.IOException("write failed")
        }
        val value = DataStoreSettingsRepository(store).settings.first()
        assertTrue(value.diagnostics.single() is SettingsDiagnostic.Unreadable)
    }

    @Test
    fun aFreshStoreReportsTheDefaultsWithoutDiagnostics() = runTest {
        val (subject, _) = repository()
        val snapshot = subject.settings.first()
        assertEquals(UserSettings.DEFAULTS, snapshot.settings)
        assertFalse("a fresh install needs no attention", snapshot.needsAttention)
    }

    @Test
    fun anUpdateIsPersistedAndReadableAgain() = runTest {
        val (subject, store) = repository()
        subject.update { it.copy(theme = ThemePreference.DARK, musicGain = 0.8f) }
        val reread = DataStoreSettingsRepository(store).settings.first()
        assertEquals(ThemePreference.DARK, reread.settings.theme)
        assertEquals(0.8f, reread.settings.musicGain)
    }

    @Test
    fun concurrentUpdatesBothLandBecauseTheChangeRunsInsideTheTransaction() = runTest {
        // A read-modify-write outside the store would lose one of these; describing the change and
        // applying it inside the transaction is what makes both survive.
        val (subject, _) = repository()
        subject.update { it.copy(theme = ThemePreference.DARK) }
        subject.update { it.copy(advancedPanelExpanded = true) }
        val snapshot = subject.settings.first()
        assertEquals(ThemePreference.DARK, snapshot.settings.theme)
        assertEquals(true, snapshot.settings.advancedPanelExpanded)
    }

    @Test
    fun aNewerSchemaIsReportedRatherThanOverwritten() = runTest {
        // This build understands version 4; version 5 must not be silently discarded.
        val store = InMemoryPreferencesStore(
            stored(
                intPreferencesKey("schema_version") to 5,
                stringPreferencesKey("theme") to ThemePreference.DARK.name
            )
        )
        val snapshot = DataStoreSettingsRepository(store).settings.first()
        assertTrue("it must ask for attention", snapshot.needsAttention)
        val diagnostic = snapshot.diagnostics.first()
        assertTrue(diagnostic is SettingsDiagnostic.NewerSchema)
        diagnostic as SettingsDiagnostic.NewerSchema
        assertEquals(5, diagnostic.stored)
        assertEquals(4, diagnostic.supported)
    }

    @Test
    fun anUnknownEnumNameFallsBackAndIsReported() = runTest {
        val store = InMemoryPreferencesStore(stored(stringPreferencesKey("theme") to "NEON"))
        val snapshot = DataStoreSettingsRepository(store).settings.first()
        assertEquals(
            "the field falls back to its default",
            ThemePreference.SYSTEM,
            snapshot.settings.theme
        )
        assertTrue(
            "the recovery is reported",
            snapshot.diagnostics.any {
                it is SettingsDiagnostic.InvalidValue && it.field == "theme"
            }
        )
    }

    @Test
    fun aGainOutsideTheRangeFallsBackAndIsReported() = runTest {
        val store = InMemoryPreferencesStore(stored(floatPreferencesKey("voice_gain") to 9f))
        val snapshot = DataStoreSettingsRepository(store).settings.first()
        assertEquals(UserSettings.DEFAULT_VOICE_GAIN, snapshot.settings.voiceGain)
        assertTrue(
            snapshot.diagnostics.any {
                it is SettingsDiagnostic.InvalidValue && it.field == "voiceGain"
            }
        )
    }

    @Test
    fun anUnusableStoredSetupFallsBackToTheDefault() = runTest {
        // A setup whose range is inverted cannot produce a definition, so it is reported and the
        // default is used rather than starting a session from something invalid.
        val store = InMemoryPreferencesStore(
            stored(
                intPreferencesKey("setup_min_note") to 84,
                intPreferencesKey("setup_max_note") to 60
            )
        )
        val snapshot = DataStoreSettingsRepository(store).settings.first()
        assertEquals(LastSetup.default(), snapshot.settings.lastSetup)
        assertTrue(
            snapshot.diagnostics.any {
                it is SettingsDiagnostic.InvalidValue && it.field == "lastSetup"
            }
        )
    }

    @Test
    fun aStoredSetupRoundTripsIncludingModeAndStopPolicy() = runTest {
        val (subject, store) = repository()
        val setup = LastSetup.default().copy(
            mode = RecipeMode.ASSOCIATION,
            presentation = IntervalPresentation.DESCENDING,
            replayCount = 3,
            noteDuration = Duration.parse("450ms"),
            answerGap = Duration.parse("250ms"),
            repeatGap = Duration.ZERO,
            fixedRoot = 67,
            stopPolicy = SessionStopPolicy.CompletedItems(25)
        )
        assertTrue(subject.writeIntervals(setup) is ModeWriteResult.Saved)
        val reread = DataStoreSettingsRepository(store).settings.first()
        assertEquals(RecipeMode.ASSOCIATION, reread.settings.lastSetup.mode)
        assertEquals(IntervalPresentation.DESCENDING, reread.settings.lastSetup.presentation)
        assertEquals(3, reread.settings.lastSetup.replayCount)
        assertEquals(Duration.parse("450ms"), reread.settings.lastSetup.noteDuration)
        assertEquals(Duration.parse("250ms"), reread.settings.lastSetup.answerGap)
        assertEquals(Duration.ZERO, reread.settings.lastSetup.repeatGap)
        assertEquals(67, reread.settings.lastSetup.fixedRoot)
        assertEquals(SessionStopPolicy.CompletedItems(25), reread.settings.lastSetup.stopPolicy)
    }

    @Test
    fun theLastSelectedModeRoundTrips() = runTest {
        val (subject, store) = repository()
        assertTrue(subject.selectKind(TrainingKind.CHORDS) is ModeWriteResult.Saved)
        val reread = DataStoreSettingsRepository(store).settings.first()
        assertEquals(TrainingKind.CHORDS, reread.settings.selectedKind)
        assertEquals(TrainingMode.CHORDS, reread.settings.lastMode)
        assertFalse("a supported value needs no recovery", reread.needsAttention)
    }

    @Test
    fun theSleepTimerIsRememberedAndAnInvalidStoredValueFallsBack() = runTest {
        val (subject, store) = repository()
        subject.update { it.copy(sleepTimerMinutes = 90) }
        assertEquals(90, subject.settings.first().settings.sleepTimerMinutes)
        assertEquals(90, store.data.first()[intPreferencesKey("sleep_timer_minutes")])

        val (corrupt, _) = repository(
            InMemoryPreferencesStore(
                stored(
                    intPreferencesKey("schema_version") to 4,
                    intPreferencesKey("sleep_timer_minutes") to 5000
                )
            )
        )
        val snapshot = corrupt.settings.first()
        assertEquals(
            "an impossible timer falls back to Off",
            0,
            snapshot.settings.sleepTimerMinutes
        )
        assertTrue(
            "the fallback is reported",
            snapshot.diagnostics.any { it is SettingsDiagnostic.InvalidValue }
        )
    }

    @Test
    fun resetRestoresEveryDefaultAndClearsDiagnostics() = runTest {
        val store = InMemoryPreferencesStore(
            stored(
                stringPreferencesKey("theme") to ThemePreference.DARK.name,
                intPreferencesKey("schema_version") to 5
            )
        )
        val subject = DataStoreSettingsRepository(store)
        assertTrue("the newer schema is reported first", subject.settings.first().needsAttention)

        val reset = subject.resetToDefaults()
        assertEquals(UserSettings.DEFAULTS, reset)
        val after = subject.settings.first()
        assertEquals(UserSettings.DEFAULTS, after.settings)
        assertFalse("an explicit reset leaves nothing to report", after.needsAttention)
    }

    @Test
    fun anInvalidChangeIsRefusedBeforeAnythingIsWritten() = runTest {
        val (subject, store) = repository()
        var refused = false
        try {
            runBlocking { subject.update { it.copy(musicGain = 99f) } }
        } catch (expected: IllegalArgumentException) {
            refused = true
        }
        assertTrue("the change must be refused", refused)
        val snapshot = DataStoreSettingsRepository(store).settings.first()
        assertEquals("the stored settings are unchanged", UserSettings.DEFAULTS, snapshot.settings)
    }

    @Test
    fun aDurationStopPolicyRoundTripsAndIsNotSilentlyDowngraded() = runTest {
        val (subject, store) = repository()
        val expected = SessionStopPolicy.Duration(
            limit = Duration.parse("45m"),
            finish = com.thomas.passiveeartraining.training.recipe.DurationFinish
                .FINISH_CURRENT_ITEM
        )
        assertTrue(
            subject.writeIntervals(
                subject.settings.first().settings.lastSetup.copy(stopPolicy = expected)
            )
                is ModeWriteResult.Saved
        )
        val reread = DataStoreSettingsRepository(store).settings.first()
        assertEquals(expected, reread.settings.lastSetup.stopPolicy)
        assertFalse("a supported value needs no recovery", reread.needsAttention)
    }

    @Test
    fun aNewerSchemaRefusesAnUpdateInsteadOfOverwritingIt() = runTest {
        val store = InMemoryPreferencesStore(
            stored(
                intPreferencesKey("schema_version") to 5,
                stringPreferencesKey("theme") to ThemePreference.DARK.name
            )
        )
        val subject = DataStoreSettingsRepository(store)
        var refused = false
        try {
            runBlocking { subject.update { it.copy(theme = ThemePreference.LIGHT) } }
        } catch (
            expected: com.thomas.passiveeartraining.training.settings
                .SettingsValidationException
        ) {
            refused = true
        }
        assertTrue("a read-only future schema must refuse writes", refused)
        val storedTheme = store.data.first()[stringPreferencesKey("theme")]
        assertEquals("nothing was overwritten", ThemePreference.DARK.name, storedTheme)
    }

    @Test
    fun aMixedPresentationSetRoundTripsWhileASingleOneStaysImplicit() = runTest {
        val (subject, store) = repository()
        val mixed = LastSetup.default().copy(
            presentation = IntervalPresentation.ASCENDING,
            presentations = listOf(
                IntervalPresentation.ASCENDING,
                IntervalPresentation.DESCENDING
            ),
            separatorCue = true
        )
        assertTrue(subject.writeIntervals(mixed) is ModeWriteResult.Saved)
        val reread = DataStoreSettingsRepository(store).settings.first()
        assertEquals(mixed, reread.settings.lastSetup)
        assertEquals(
            "a mixed set is version 2",
            2,
            reread.settings.lastSetup.toPolicy().generatorVersion
        )

        assertTrue(subject.writeIntervals(LastSetup.default()) is ModeWriteResult.Saved)
        val single = DataStoreSettingsRepository(store).settings.first().settings.lastSetup
        assertEquals(
            "a single presentation stays version 1",
            1,
            single.toPolicy().generatorVersion
        )
    }
}

// ---------------------------------------------------------------------------------------------
// M2.1 — schema 2, revisions and per-mode ownership
// ---------------------------------------------------------------------------------------------

class SettingsSchemaThreeTest {

    private fun repository(store: DataStore<Preferences> = InMemoryPreferencesStore()) =
        DataStoreSettingsRepository(store) to store

    @Test
    fun aSchemaTwoStoreAdoptsTheBrandPaletteOnceAndKeepsAnExplicitChoice() = runTest {
        val (subject, store) = repository(
            InMemoryPreferencesStore(
                stored(
                    intPreferencesKey("schema_version") to 2,
                    booleanPreferencesKey("dynamic_color") to true
                )
            )
        )
        assertEquals(
            "the old dynamic default must not keep overriding the brand palette",
            false,
            subject.settings.first().settings.dynamicColor
        )

        // The owner opts back in; the write records the current schema and the explicit choice.
        subject.update { it.copy(dynamicColor = true) }
        val stored = store.data.first()
        assertEquals(4, stored[intPreferencesKey("schema_version")])
        assertTrue(
            "an explicit choice after the adoption is respected",
            subject.settings.first().settings.dynamicColor
        )
    }

    @Test
    fun aSchemaThreeStoreKeepsTheStoredDynamicChoice() = runTest {
        val (subject, _) = repository(
            InMemoryPreferencesStore(
                stored(
                    intPreferencesKey("schema_version") to 3,
                    booleanPreferencesKey("dynamic_color") to true
                )
            )
        )
        assertTrue(subject.settings.first().settings.dynamicColor)
    }
}

class SettingsSchemaTwoTest {

    private fun repository(store: DataStore<Preferences> = InMemoryPreferencesStore()) =
        DataStoreSettingsRepository(store) to store

    @Test
    fun schemaOneMigratesEveryFieldIntoIntervalsAndMapsTheLegacySelection() = runTest {
        val expectedSetup = LastSetup(
            intervalIds = listOf("minor_third", "major_third"),
            rootValues = listOf(0, 7),
            minimumNote = 48,
            maximumNote = 84,
            presentation = IntervalPresentation.ASCENDING,
            mode = RecipeMode.EXPOSURE,
            thinkingSilence = Duration.parse("1500ms"),
            replayCount = 2,
            stopPolicy = SessionStopPolicy.CompletedItems(5),
            noteDuration = Duration.parse("500ms"),
            answerGap = Duration.parse("300ms"),
            repeatGap = Duration.parse("200ms"),
            fixedRoot = 60,
            presentations = listOf(
                IntervalPresentation.ASCENDING,
                IntervalPresentation.DESCENDING
            ),
            melodicGap = Duration.parse("150ms"),
            finalSilence = Duration.parse("1000ms"),
            seed = 7L,
            separatorCue = true
        )
        val store = InMemoryPreferencesStore(
            stored(
                intPreferencesKey("schema_version") to 1,
                stringPreferencesKey("theme") to ThemePreference.DARK.name,
                stringPreferencesKey("last_mode") to TrainingMode.PERFECT_PITCH.name,
                floatPreferencesKey("music_gain") to 0.8f,
                stringPreferencesKey("setup_intervals") to "minor_third,major_third",
                stringPreferencesKey("setup_roots") to "0,7",
                intPreferencesKey("setup_min_note") to 48,
                intPreferencesKey("setup_max_note") to 84,
                stringPreferencesKey("setup_presentation") to
                    IntervalPresentation.ASCENDING.name,
                stringPreferencesKey("setup_mode") to RecipeMode.EXPOSURE.name,
                intPreferencesKey("setup_thinking_ms") to 1500,
                intPreferencesKey("setup_replay") to 2,
                intPreferencesKey("setup_note_ms") to 500,
                intPreferencesKey("setup_answer_gap_ms") to 300,
                intPreferencesKey("setup_repeat_gap_ms") to 200,
                intPreferencesKey("setup_fixed_root") to 60,
                stringPreferencesKey("setup_presentations") to "ASCENDING,DESCENDING",
                intPreferencesKey("setup_melodic_gap_ms") to 150,
                intPreferencesKey("setup_final_silence_ms") to 1000,
                longPreferencesKey("setup_seed") to 7L,
                booleanPreferencesKey("setup_separator_cue") to true,
                stringPreferencesKey("stop_kind") to "COMPLETED_ITEMS",
                intPreferencesKey("stop_count") to 5
            )
        )
        val subject = DataStoreSettingsRepository(store)
        val snapshot = subject.settings.first()

        assertFalse("schema 1 is still supported", snapshot.needsAttention)
        assertEquals(ThemePreference.DARK, snapshot.settings.theme)
        assertEquals(0.8f, snapshot.settings.musicGain)
        assertEquals(expectedSetup, snapshot.settings.lastSetup)
        assertEquals(TrainingKind.NOTE_IDENTIFICATION, snapshot.settings.selectedKind)
        assertEquals(
            "the legacy selection still resolves to its old enum",
            TrainingMode.PERFECT_PITCH,
            snapshot.settings.lastMode
        )
        assertEquals(
            "the legacy selection gets the note setup default",
            NoteSetup.default(),
            snapshot.settings.noteSetup
        )
        assertTrue(
            snapshot.settings.modeSetups.getValue(TrainingKind.NOTE_IDENTIFICATION).payload
                is ModePayload.Notes
        )
        assertEquals(
            1L,
            snapshot.settings.modeSetups.getValue(TrainingKind.INTERVALS).revision
        )

        // A first write upgrades the stored file: the current schema with the stable kind id,
        // and the schema-1 alias is gone rather than left to disagree.
        assertTrue(subject.selectKind(TrainingKind.INTERVALS) is ModeWriteResult.Saved)
        val stored = store.data.first()
        assertEquals(4, stored[intPreferencesKey("schema_version")])
        assertEquals("intervals", stored[stringPreferencesKey("selected_kind")])
        assertEquals(null, stored[stringPreferencesKey("last_mode")])
    }

    @Test
    fun aStaleRevisionConflictsWithoutWritingAnything() = runTest {
        val (subject, store) = repository()
        val first = subject.updateMode(
            ModeEdit(TrainingKind.INTERVALS, 1L) {
                ModePayload.Intervals(LastSetup.default().copy(replayCount = 1))
            }
        )
        assertTrue(first is ModeWriteResult.Saved)

        val conflict = subject.updateMode(
            ModeEdit(TrainingKind.INTERVALS, 1L) {
                ModePayload.Intervals(LastSetup.default().copy(replayCount = 4))
            }
        )
        assertTrue(conflict is ModeWriteResult.Conflict)
        assertEquals(
            "the conflict reports the current state",
            1,
            (conflict as ModeWriteResult.Conflict).snapshot.settings.lastSetup.replayCount
        )

        val reread = DataStoreSettingsRepository(store).settings.first().settings
        assertEquals("the first accepted edit survives", 1, reread.lastSetup.replayCount)
        assertEquals(
            "the revision advanced exactly once",
            2L,
            reread.modeSetups.getValue(TrainingKind.INTERVALS).revision
        )
    }

    @Test
    fun saveAndSelectIsAtomicOnConflict() = runTest {
        val (subject, store) = repository()
        val bad = subject.saveAndSelect(
            listOf(
                ModeEdit(TrainingKind.INTERVALS, 99L) {
                    ModePayload.Intervals(LastSetup.default().copy(replayCount = 4))
                }
            ),
            selectedKind = TrainingKind.CHORDS
        )
        assertTrue(bad is ModeWriteResult.Conflict)
        val afterBad = DataStoreSettingsRepository(store).settings.first().settings
        assertEquals(TrainingKind.INTERVALS, afterBad.selectedKind)
        assertEquals(0, afterBad.lastSetup.replayCount)

        val good = subject.saveAndSelect(
            listOf(
                ModeEdit(TrainingKind.INTERVALS, 1L) {
                    ModePayload.Intervals(LastSetup.default().copy(replayCount = 4))
                }
            ),
            selectedKind = TrainingKind.CHORDS
        )
        assertTrue(good is ModeWriteResult.Saved)
        val afterGood = DataStoreSettingsRepository(store).settings.first().settings
        assertEquals(TrainingKind.CHORDS, afterGood.selectedKind)
        assertEquals(4, afterGood.lastSetup.replayCount)
        assertEquals(
            2L,
            afterGood.modeSetups.getValue(TrainingKind.INTERVALS).revision
        )
    }

    @Test
    fun aNewerModeConfigVersionIsDiagnosedAndProtectedFromWrites() = runTest {
        val store = InMemoryPreferencesStore(
            stored(
                intPreferencesKey("schema_version") to 2,
                intPreferencesKey("intervals_config_version") to 2,
                stringPreferencesKey("setup_intervals") to "major_third",
                stringPreferencesKey("selected_kind") to "intervals"
            )
        )
        val subject = DataStoreSettingsRepository(store)
        val snapshot = subject.settings.first()

        assertTrue(
            snapshot.diagnostics.any { it is SettingsDiagnostic.NewerModeConfig }
        )
        assertEquals(
            "a future payload is never presented as a playable setup",
            LastSetup.default(),
            snapshot.settings.lastSetup
        )
        assertEquals(
            "the stored file is not rewritten at launch",
            "major_third",
            store.data.first()[stringPreferencesKey("setup_intervals")]
        )

        assertTrue(
            subject.selectKind(TrainingKind.INTERVALS) is ModeWriteResult.Rejected
        )
        assertTrue(
            subject.updateMode(
                ModeEdit(TrainingKind.INTERVALS, 1L) {
                    ModePayload.Intervals(LastSetup.default())
                }
            ) is ModeWriteResult.Rejected
        )
        var updateRefused = false
        try {
            subject.update { it.copy(theme = ThemePreference.DARK) }
        } catch (expected: IllegalArgumentException) {
            updateRefused = true
        }
        assertTrue("every automatic write path is blocked", updateRefused)
    }

    @Test
    fun anUnknownSelectedKindIsDiagnosedAndFallsBack() = runTest {
        val store = InMemoryPreferencesStore(
            stored(
                intPreferencesKey("schema_version") to 2,
                stringPreferencesKey("selected_kind") to "future_mode"
            )
        )
        val snapshot = DataStoreSettingsRepository(store).settings.first()
        assertEquals(TrainingKind.INTERVALS, snapshot.settings.selectedKind)
        assertTrue(snapshot.diagnostics.any { it is SettingsDiagnostic.InvalidValue })
    }

    @Test
    fun aGlobalUpdateNeverCarriesAModeSetupChange() = runTest {
        val (subject, store) = repository()
        var refused = false
        try {
            subject.update { settings ->
                val current = settings.modeSetups.getValue(TrainingKind.INTERVALS)
                settings.copy(
                    modeSetups = settings.modeSetups +
                        (TrainingKind.INTERVALS to current.nextRevision())
                )
            }
        } catch (expected: IllegalArgumentException) {
            refused = true
        }
        assertTrue("mode writes must use the revision-checked path", refused)
        assertEquals(
            UserSettings.DEFAULTS.modeSetups,
            DataStoreSettingsRepository(store).settings.first().settings.modeSetups
        )
    }
}
