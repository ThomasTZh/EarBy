package com.thomas.passiveeartraining.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.mutablePreferencesOf
import androidx.datastore.preferences.core.stringPreferencesKey
import com.thomas.passiveeartraining.music.ScaleDirection
import com.thomas.passiveeartraining.training.provider.TrainingKind
import com.thomas.passiveeartraining.training.recipe.RecipeMode
import com.thomas.passiveeartraining.training.recipe.SessionStopPolicy
import com.thomas.passiveeartraining.training.settings.ModeEdit
import com.thomas.passiveeartraining.training.settings.ModePayload
import com.thomas.passiveeartraining.training.settings.ModeWriteResult
import com.thomas.passiveeartraining.training.settings.ScaleSetup
import com.thomas.passiveeartraining.training.settings.SettingsDiagnostic
import kotlin.time.Duration
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** In-memory DataStore for the scale persistence tests. */
private class ScaleStore(initial: Preferences = emptyPreferences()) : DataStore<Preferences> {
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

/** M6-T06: the scale setup round-trips through the schema-2 preferences. */
class ScaleSettingsPersistenceTest {

    private fun repository(store: DataStore<Preferences> = ScaleStore()) =
        DataStoreSettingsRepository(store) to store

    private fun customSetup(): ScaleSetup = ScaleSetup.default().copy(
        scaleIds = listOf("dorian", "mixolydian"),
        direction = ScaleDirection.DESCENDING,
        rootValues = listOf(0, 2, 7),
        mode = RecipeMode.EXPOSURE,
        replayCount = 2,
        noteDuration = Duration.parse("500ms"),
        melodicGap = Duration.parse("150ms"),
        stopPolicy = SessionStopPolicy.CompletedItems(15),
        seed = 7L,
        separatorCue = true
    )

    @Test
    fun aFreshStoreCarriesTheDefaultScaleSetup() = runTest {
        val (subject, _) = repository()
        val snapshot = subject.settings.first()
        assertEquals(ScaleSetup.default(), snapshot.settings.scalesSetup)
        assertTrue(snapshot.settings.modeSetups.containsKey(TrainingKind.SCALES))
        assertTrue(
            snapshot.settings.modeSetups.getValue(TrainingKind.SCALES).payload
                is ModePayload.Scales
        )
        assertEquals(emptyList<SettingsDiagnostic>(), snapshot.diagnostics)
    }

    @Test
    fun aScaleSetupRoundTripsThroughTheRevisionCheckedWrite() = runTest {
        val (subject, store) = repository()
        val setup = customSetup()
        val revision = subject.settings.first().settings
            .modeSetups.getValue(TrainingKind.SCALES).revision
        val result = subject.updateMode(
            ModeEdit(TrainingKind.SCALES, revision) { ModePayload.Scales(setup) }
        )
        assertTrue("the scale write must be accepted: $result", result is ModeWriteResult.Saved)

        val reread = DataStoreSettingsRepository(store).settings.first()
        assertEquals(setup, reread.settings.scalesSetup)
        assertEquals(
            "the stop policy round-trips with the setup",
            SessionStopPolicy.CompletedItems(15),
            reread.settings.scalesSetup.stopPolicy
        )
        assertEquals("the seed round-trips with the setup", 7L, reread.settings.scalesSetup.seed)
        assertTrue(reread.diagnostics.isEmpty())
    }

    @Test
    fun anUnusableStoredScaleSetupFallsBackAndIsReported() = runTest {
        val store = ScaleStore(
            mutablePreferencesOf(
                stringPreferencesKey("scales_scale_ids") to "bogus_scale"
            )
        )
        val snapshot = DataStoreSettingsRepository(store).settings.first()
        assertEquals(ScaleSetup.default(), snapshot.settings.scalesSetup)
        assertTrue(
            snapshot.diagnostics.any {
                it is SettingsDiagnostic.InvalidValue && it.field == "scaleSetup"
            }
        )
    }

    @Test
    fun aStoredDisplayAliasIsCanonicalizedAndOnlyCanonicalIdsAreRewritten() = runTest {
        val store = ScaleStore(
            mutablePreferencesOf(
                stringPreferencesKey("scales_scale_ids") to "ionian,aeolian"
            )
        )
        val subject = DataStoreSettingsRepository(store)
        val snapshot = subject.settings.first()
        assertEquals(
            "the stored aliases resolve to their canonical scales",
            listOf("major", "natural_minor"),
            snapshot.settings.scalesSetup.scaleIds
        )

        val revision = snapshot.settings.modeSetups.getValue(TrainingKind.SCALES).revision
        val result = subject.updateMode(
            ModeEdit(TrainingKind.SCALES, revision) {
                ModePayload.Scales(snapshot.settings.scalesSetup.copy(replayCount = 1))
            }
        )
        assertTrue("the canonical setup saves: $result", result is ModeWriteResult.Saved)
        val written = store.data.first()[stringPreferencesKey("scales_scale_ids")]
        assertEquals("major,natural_minor", written)
    }

    @Test
    fun aNewerScaleConfigVersionIsProtectedFromWrites() = runTest {
        val store = ScaleStore(
            mutablePreferencesOf(
                intPreferencesKey("scales_config_version") to 99
            )
        )
        val subject = DataStoreSettingsRepository(store)
        val snapshot = subject.settings.first()
        assertEquals(ScaleSetup.default(), snapshot.settings.scalesSetup)
        assertTrue(
            snapshot.diagnostics.any {
                it is SettingsDiagnostic.NewerModeConfig && it.kind == TrainingKind.SCALES
            }
        )
        val revision = snapshot.settings.modeSetups.getValue(TrainingKind.SCALES).revision
        val result = subject.updateMode(
            ModeEdit(TrainingKind.SCALES, revision) { ModePayload.Scales(customSetup()) }
        )
        assertTrue(
            "a protected payload must refuse writes: $result",
            result is ModeWriteResult.Rejected
        )
    }
}
