package com.thomas.passiveeartraining.app

import android.app.Application
import com.thomas.passiveeartraining.data.DataStoreSettingsRepository
import com.thomas.passiveeartraining.data.preset.SqlitePresetRepository
import com.thomas.passiveeartraining.training.settings.SettingsSnapshot
import com.thomas.passiveeartraining.training.settings.UserSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * The composition root.
 *
 * Hilt is blocked on the current toolchain (no KSP for Kotlin 2.4.20; see STATUS), so the
 * application-scoped repositories are constructed here and shared by the playback service and the
 * screens. One instance per store is the product's contract: one DataStore file and one SQLite
 * database, both owned by the application.
 */
open class PassiveEarTrainingApp : Application() {

    open fun activityBaseContext(base: android.content.Context): android.content.Context = base

    open val playbackServiceClassName: String get() = TrainingPlaybackService::class.java.name

    /** Owns the settings collector for the life of the process. */
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val settingsRepository: DataStoreSettingsRepository by lazy {
        DataStoreSettingsRepository.create(this)
    }

    /** The single preset database. */
    val presetRepository: SqlitePresetRepository by lazy { SqlitePresetRepository(this) }

    /** The preset store as the kind-aware Presets tab sees it, with kind-scoped slot reads (M5). */
    val kindAwarePresetRepository: KindAwarePresetStore by lazy {
        SqliteKindAwarePresetStore(presetRepository)
    }

    val settingsWriteScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val settingsLoaded = kotlinx.coroutines.CompletableDeferred<Unit>()

    suspend fun awaitSettings(): SettingsSnapshot {
        settingsLoaded.await()
        return settings.value
    }

    private val _settings = MutableStateFlow(SettingsSnapshot(UserSettings.DEFAULTS))

    /**
     * The latest settings snapshot.
     *
     * The playback service reads this synchronously when it builds a Start, so a Start always uses
     * the saved configuration without blocking a platform callback on disk.
     */
    val settings: StateFlow<SettingsSnapshot> = _settings.asStateFlow()

    override fun onCreate() {
        super.onCreate()
        scope.launch {
            settingsRepository.settings.collect {
                _settings.value = it
                settingsLoaded.complete(Unit)
            }
        }
    }

    override fun onTerminate() {
        scope.cancel()
        settingsWriteScope.cancel()
        super.onTerminate()
    }
}
