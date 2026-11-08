package com.thomas.passiveeartraining.app

import androidx.compose.runtime.Composable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.github.takahirom.roborazzi.captureRoboImage
import com.thomas.passiveeartraining.data.preset.ImportPreview
import com.thomas.passiveeartraining.data.preset.ImportResult
import com.thomas.passiveeartraining.data.preset.Preset
import com.thomas.passiveeartraining.data.preset.PresetConfiguration
import com.thomas.passiveeartraining.data.preset.PresetId
import com.thomas.passiveeartraining.data.preset.PresetSlots
import com.thomas.passiveeartraining.training.settings.SettingsRepository
import com.thomas.passiveeartraining.training.settings.SettingsSnapshot
import com.thomas.passiveeartraining.training.settings.TrainingMode
import com.thomas.passiveeartraining.training.settings.UserSettings
import java.io.File
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Candidate screenshots for the setup, settings and presets screens.
 *
 * They render the real screens with fake repositories, so no service, device or database is
 * involved. Images are review candidates, never baselines.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h800dp-xhdpi")
class ScreensScreenshotTest {

    @get:Rule
    val compose = createComposeRule()

    /**
     * Where the images are written.
     *
     * The default is the reviewed baseline folder; a review run redirects it to a candidates
     * folder with -Pear.screenshots.dir=... so a first or new image can be approved before it
     * becomes a baseline. -Pear.screenshots.theme=dark renders the dark review set instead.
     */
    private val screenshotsDir = File(
        System.getProperty("ear.screenshots.dir") ?: "src/test/screenshots"
    )

    @Composable
    private fun theme(content: @Composable () -> Unit) {
        PassiveEarTrainingTheme(reviewCaptureSettings()) { ReviewCaptureSurface { content() } }
    }

    @Test
    fun theSetupScreenRenders() {
        compose.setContent {
            theme {
                SetupScreen(settingsRepository = ScreenshotSettingsRepository())
            }
        }
        compose.onNodeWithText("Interval setup").assertExists()
        compose.onRoot().captureRoboImage(File(screenshotsDir, "setup-basic.png"))
    }

    @Test
    fun theSettingsScreenRenders() {
        compose.setContent {
            theme {
                SettingsScreen(
                    settingsRepository = ScreenshotSettingsRepository()
                )
            }
        }
        compose.onNodeWithText("Theme").assertExists()
        compose.onRoot().captureRoboImage(File(screenshotsDir, "settings.png"))
    }

    @Test
    fun thePresetsScreenRendersStartersAndACustomPreset() {
        compose.setContent {
            theme {
                PresetsScreen(
                    presetRepository = ScreenshotPresetRepository(),
                    settingsRepository = ScreenshotSettingsRepository()
                )
            }
        }
        compose.onNodeWithText("Presets").assertExists()
        compose.onRoot().captureRoboImage(File(screenshotsDir, "presets.png"))
    }

    @Test
    fun theModeDrawerRenders() {
        compose.setContent {
            theme {
                ModeDrawerContent(
                    selected = com.thomas.passiveeartraining.training.provider.TrainingKind
                        .INTERVALS,
                    onSelectMode = {},
                    onOpenSettings = {},
                    settingsSelected = false,
                    onOpenHelp = {},
                    helpSelected = false
                )
            }
        }
        compose.onNodeWithText("Settings").assertExists()
        compose.onRoot().captureRoboImage(File(screenshotsDir, "drawer.png"))
    }

    @Test
    fun theSetupPickerDialogRenders() {
        compose.setContent {
            theme {
                SetupScreen(settingsRepository = ScreenshotSettingsRepository())
            }
        }
        compose.onNodeWithText("2.00 s").performScrollTo().performClick()
        compose.onNodeWithText("OK").assertExists()
        compose.onRoot().captureRoboImage(File(screenshotsDir, "setup-picker.png"))
    }
}

/** In-memory settings for host rendering; nothing is persisted. */
private class ScreenshotSettingsRepository : SettingsRepository {
    private val state = MutableStateFlow(SettingsSnapshot(UserSettings.DEFAULTS))
    override val settings: Flow<SettingsSnapshot> = state

    override suspend fun update(change: (UserSettings) -> UserSettings): UserSettings {
        val next = change(state.value.settings)
        state.value = SettingsSnapshot(next)
        return next
    }

    override suspend fun updateMode(
        edit: com.thomas.passiveeartraining.training.settings.ModeEdit,
        globalChange: ((UserSettings) -> UserSettings)?
    ): com.thomas.passiveeartraining.training.settings.ModeWriteResult {
        val current = state.value
        val existing = current.settings.modeSetups[edit.kind]
            ?: return com.thomas.passiveeartraining.training.settings.ModeWriteResult.Rejected(
                "no setup"
            )
        val base = globalChange?.invoke(current.settings) ?: current.settings
        val updated = base.copy(
            modeSetups = base.modeSetups + (
                edit.kind to com.thomas.passiveeartraining.training.settings.SavedModeSetup(
                    existing.revision + 1,
                    edit.change(existing)
                )
                )
        )
        state.value = SettingsSnapshot(updated)
        return com.thomas.passiveeartraining.training.settings.ModeWriteResult.Saved(
            SettingsSnapshot(updated)
        )
    }

    override suspend fun saveAndSelect(
        edits: List<com.thomas.passiveeartraining.training.settings.ModeEdit>,
        selectedKind: com.thomas.passiveeartraining.training.provider.TrainingKind,
        globalChange: ((UserSettings) -> UserSettings)?
    ): com.thomas.passiveeartraining.training.settings.ModeWriteResult =
        updateMode(edits.first(), globalChange).let {
            if (it is com.thomas.passiveeartraining.training.settings.ModeWriteResult.Saved) {
                com.thomas.passiveeartraining.training.settings.ModeWriteResult.Saved(
                    SettingsSnapshot(it.snapshot.settings.copy(selectedKind = selectedKind))
                )
            } else {
                it
            }
        }

    override suspend fun selectKind(
        kind: com.thomas.passiveeartraining.training.provider.TrainingKind
    ): com.thomas.passiveeartraining.training.settings.ModeWriteResult {
        val updated = state.value.settings.copy(selectedKind = kind)
        state.value = SettingsSnapshot(updated)
        return com.thomas.passiveeartraining.training.settings.ModeWriteResult.Saved(
            SettingsSnapshot(updated)
        )
    }

    override suspend fun resetToDefaults(): UserSettings {
        state.value = SettingsSnapshot(UserSettings.DEFAULTS)
        return UserSettings.DEFAULTS
    }
}

/** One starter and the five user slots, with the first slot filled, for a representative list. */
private class ScreenshotPresetRepository : KindAwarePresetStore {
    private val starter = Preset(
        id = PresetId("builtin:intervals:starter:major_pentatonic:v1"),
        name = "Major Pentatonic",
        createdAtEpochMs = 0L,
        updatedAtEpochMs = 0L,
        configuration = PresetConfiguration.Intervals(
            com.thomas.passiveeartraining.training.settings.LastSetup.default().copy(
                intervalIds = listOf(
                    "unison",
                    "major_second",
                    "major_third",
                    "perfect_fifth",
                    "major_sixth",
                    "octave"
                ),
                minimumNote = 60,
                maximumNote = 72,
                thinkingSilence = kotlin.time.Duration.parse("3s"),
                fixedRoot = 60,
                seed = 776655,
                separatorCue = true
            )
        ),
        seed = 776655,
        builtIn = true
    )
    private val slots = PresetSlots.IDS.mapIndexed { index, id ->
        starter.copy(
            id = id,
            name = if (index == 0) "My practice set" else PresetSlots.defaultName(index + 1),
            builtIn = false,
            empty = index != 0
        )
    }
    private val state = MutableStateFlow(listOf(starter) + slots)

    override val presets: Flow<List<Preset>> = state
    override suspend fun refresh() = Unit
    override suspend fun slotsOf(
        kind: com.thomas.passiveeartraining.training.provider.TrainingKind
    ): List<Preset> = state.value.filter {
        it.configuration.kind == kind && it.id in PresetSlots.slotIds(kind)
    }
    override suspend fun starterOf(
        kind: com.thomas.passiveeartraining.training.provider.TrainingKind
    ): Preset? = state.value.firstOrNull {
        it.configuration.kind == kind && it.builtIn
    }
    override suspend fun markUsed(id: PresetId) = Unit
    override suspend fun create(
        name: String,
        configuration: PresetConfiguration,
        seed: Long?,
        musicGain: Float,
        voiceGain: Float
    ): Preset = error("Not used by the screenshot test")
    override suspend fun update(preset: Preset): Preset = error("Not used by the screenshot test")
    override suspend fun rename(id: PresetId, name: String): Preset =
        error("Not used by the screenshot test")
    override suspend fun duplicate(id: PresetId, name: String?): Preset =
        error("Not used by the screenshot test")
    override suspend fun delete(id: PresetId) = Unit
    override suspend fun clear(id: PresetId): Preset = error("Not used by the screenshot test")
    override suspend fun previewImport(text: String): ImportPreview = ImportPreview(emptyList())
    override suspend fun commitImport(
        preview: ImportPreview,
        replaceExistingIds: Set<PresetId>
    ): ImportResult = ImportResult(emptyList(), emptyList())
    override suspend fun export(ids: Collection<PresetId>): String = ""
}
