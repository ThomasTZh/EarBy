package com.thomas.passiveeartraining

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.thomas.passiveeartraining.data.DataStoreSettingsRepository
import com.thomas.passiveeartraining.training.settings.SettingsDiagnostic
import com.thomas.passiveeartraining.training.settings.UserSettings
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ReviewPersistenceDeviceTest {
    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test fun corruptionIsReportedAndOnlyExplicitResetRecoversIt() = runBlocking {
        val name = "review-corrupt-${UUID.randomUUID()}.preferences_pb"
        val file = context.preferencesDataStoreFile(name)
        file.parentFile!!.mkdirs()
        file.writeText("invalid protobuf bytes")
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
            val repository = DataStoreSettingsRepository.create(context, name, scope)
            assertTrue(
                repository.settings.first().diagnostics.single() is SettingsDiagnostic.Unreadable
            )
            assertEquals(
                "unreadable settings are preserved",
                "invalid protobuf bytes",
                file.readText()
            )
            repository.resetToDefaults()
            assertEquals(
                UserSettings.DEFAULTS,
                repository.settings.first {
                    !it.needsAttention
                }.settings
            )
            assertTrue(
                context.noBackupFilesDir.listFiles()!!.any {
                    it.name.startsWith("settings-corrupt-")
                }
            )
        } finally {
            scope.cancel()
            file.delete()
        }
    }

    @Test fun aFutureSchemaIsReadOnlyUntilExplicitReset() = runBlocking {
        val name = "review-future-${UUID.randomUUID()}.preferences_pb"
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
            val store = PreferenceDataStoreFactory.create(scope = scope) {
                context.preferencesDataStoreFile(name)
            }
            store.edit { it[intPreferencesKey("schema_version")] = 999 }
            val repository = DataStoreSettingsRepository(store)
            assertTrue(
                repository.settings.first().diagnostics.single() is SettingsDiagnostic.NewerSchema
            )
            var rejected = false
            try {
                repository.update { it.copy(musicGain = 0.5f) }
            } catch (
                expected: IllegalArgumentException
            ) {
                rejected =
                    true
            }
            assertTrue(rejected)
            assertEquals(999, store.data.first()[intPreferencesKey("schema_version")])
            repository.resetToDefaults()
            assertEquals(UserSettings.DEFAULTS, repository.settings.first().settings)
        } finally {
            scope.cancel()
            context.preferencesDataStoreFile(name).delete()
        }
    }
}
