package com.thomas.passiveeartraining.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.Density
import com.thomas.passiveeartraining.data.preset.ImportPreview
import com.thomas.passiveeartraining.data.preset.ImportResult
import com.thomas.passiveeartraining.data.preset.Preset
import com.thomas.passiveeartraining.data.preset.PresetConfiguration
import com.thomas.passiveeartraining.data.preset.PresetId
import com.thomas.passiveeartraining.data.preset.PresetStarters
import com.thomas.passiveeartraining.training.provider.TrainingKind
import com.thomas.passiveeartraining.training.settings.ChordSetup
import com.thomas.passiveeartraining.training.settings.ContextualIntervalSetup
import com.thomas.passiveeartraining.training.settings.IntervalWalkSetup
import com.thomas.passiveeartraining.training.settings.LastSetup
import com.thomas.passiveeartraining.training.settings.MelodicDictationSetup
import com.thomas.passiveeartraining.training.settings.ModeEdit
import com.thomas.passiveeartraining.training.settings.ModeWriteResult
import com.thomas.passiveeartraining.training.settings.NoteSetup
import com.thomas.passiveeartraining.training.settings.ProgressionSetup
import com.thomas.passiveeartraining.training.settings.SavedModeSetup
import com.thomas.passiveeartraining.training.settings.ScaleDegreeSetup
import com.thomas.passiveeartraining.training.settings.ScaleSetup
import com.thomas.passiveeartraining.training.settings.SettingsRepository
import com.thomas.passiveeartraining.training.settings.SettingsSnapshot
import com.thomas.passiveeartraining.training.settings.UserSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Host coverage for the three tabs of every implemented mode at 150% and 200% text (M11.2 UI row).
 *
 * The test renders 54 surfaces in total: the nine implemented kinds times the Play tab, the mode's
 * Setup screen and the shared Presets screen, times both text scales. Each render asserts that its
 * primary nodes exist, that the content does not overflow the window horizontally, and that the
 * pinned transport (Play) and the scrolled-to primary controls (Setup/Presets) stay inside the
 * window. The Presets screen is one shared composable parameterised by kind, so no separate screen
 * exists to render per kind; the assertions are behaviour checks, not pixel goldens, and no image
 * is captured.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h800dp-xhdpi")
class AllModesTabsTest {

    @get:Rule
    val compose = createComposeRule()

    /** One render configuration: the mode and the font scale being checked. */
    private data class Surface(val kind: TrainingKind, val fontScale: Float)

    @Test
    fun everyModePlayTabFitsInsideTheWindowAtBothTextScales() {
        var surface by mutableStateOf(Surface(IMPLEMENTED_KINDS.first(), TEXT_SCALES.first()))
        compose.setContent {
            PassiveEarTrainingTheme(UserSettings.DEFAULTS) {
                Scaled(surface.fontScale) {
                    key(surface.kind) {
                        PlayContent(
                            state = PlaybackUiState(
                                connected = true,
                                playing = true,
                                active = true
                            ),
                            setup = LastSetup.default(),
                            chordSetup = ChordSetup.default(),
                            scaleSetup = ScaleSetup.default(),
                            noteSetup = NoteSetup.default(),
                            degreeSetup = ScaleDegreeSetup.default(),
                            progressionSetup = ProgressionSetup.default(),
                            contextualIntervalSetup = ContextualIntervalSetup.default(),
                            melodicDictationSetup = MelodicDictationSetup.default(),
                            intervalWalkSetup = IntervalWalkSetup.default(),
                            mode = surface.kind,
                            musicGain = UserSettings.DEFAULT_MUSIC_GAIN,
                            voiceGain = UserSettings.DEFAULT_VOICE_GAIN,
                            onPlay = {},
                            onStop = {},
                            onSleepChoice = {}
                        )
                    }
                }
            }
        }

        for (kind in IMPLEMENTED_KINDS) {
            for (scale in TEXT_SCALES) {
                surface = Surface(kind, scale)
                compose.waitForIdle()
                assertNothingOverflowsHorizontally()
                // The stored setup summary is the Play tab's primary content for this mode.
                assertFullyInsideWindow(playSummaryLine(kind))
                assertTagFullyInsideWindow("play_stop")
                assertFullyInsideWindow("Sleep timer")
            }
        }
    }

    @Test
    fun intervalWalkStoppingLetsTheUserChooseTheCompletedItemCount() {
        val settings = TabsHostSettingsRepository()
        compose.setContent {
            PassiveEarTrainingTheme(UserSettings.DEFAULTS) {
                IntervalWalkSetupScreen(settingsRepository = settings)
            }
        }
        compose.waitUntil(timeoutMillis = 10_000) {
            compose.onAllNodesWithText("Interval walk setup").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("After N items").performScrollTo().performClick()
        compose.onNodeWithText("Stopping").performScrollTo().assertExists()
        compose.onNodeWithText("Completed items").performScrollTo().assertExists()
    }

    @Test
    fun everyModeSetupScreenFitsInsideTheWindowAtBothTextScales() {
        var surface by mutableStateOf(Surface(IMPLEMENTED_KINDS.first(), TEXT_SCALES.first()))
        val settings = TabsHostSettingsRepository()
        compose.setContent {
            PassiveEarTrainingTheme(UserSettings.DEFAULTS) {
                Scaled(surface.fontScale) {
                    key(surface.kind) {
                        when (surface.kind) {
                            TrainingKind.INTERVALS ->
                                SetupScreen(settingsRepository = settings)
                            TrainingKind.INTERVAL_WALK ->
                                IntervalWalkSetupScreen(settingsRepository = settings)
                            TrainingKind.CHORDS ->
                                ChordSetupScreen(settingsRepository = settings)
                            TrainingKind.SCALES ->
                                ScaleSetupScreen(settingsRepository = settings)
                            TrainingKind.NOTE_IDENTIFICATION ->
                                NoteSetupScreen(settingsRepository = settings)
                            TrainingKind.SCALE_DEGREES ->
                                ScaleDegreeSetupScreen(settingsRepository = settings)
                            TrainingKind.CHORD_PROGRESSIONS ->
                                ProgressionSetupScreen(settingsRepository = settings)
                            TrainingKind.INTERVALS_IN_CONTEXT ->
                                ContextualIntervalSetupScreen(settingsRepository = settings)
                            else ->
                                MelodicDictationSetupScreen(settingsRepository = settings)
                        }
                    }
                }
            }
        }

        for (kind in IMPLEMENTED_KINDS) {
            for (scale in TEXT_SCALES) {
                surface = Surface(kind, scale)
                val headline = setupHeadline(kind)
                compose.waitUntil(timeoutMillis = 10_000) {
                    compose.onAllNodesWithText(headline).fetchSemanticsNodes().isNotEmpty()
                }
                assertNothingOverflowsHorizontally()
                assertFullyInsideWindow(headline)
                // A control near the end of the form scrolls into the window instead of
                // overflowing it, and the form's own sections are still present.
                compose.onNodeWithText(setupSection(kind)).performScrollTo()
                assertFullyInsideWindow(setupSection(kind))
                compose.onNodeWithText(setupTailControl(kind)).performScrollTo()
                assertFullyInsideWindow(setupTailControl(kind))
            }
        }
    }

    @Test
    fun thePresetsTabFitsInsideTheWindowForEveryModeAtBothTextScales() {
        var surface by mutableStateOf(Surface(IMPLEMENTED_KINDS.first(), TEXT_SCALES.first()))
        val settings = TabsHostSettingsRepository()
        val presets = TabsHostPresetRepository()
        compose.setContent {
            PassiveEarTrainingTheme(UserSettings.DEFAULTS) {
                Scaled(surface.fontScale) {
                    key(surface.kind) {
                        PresetsScreen(
                            presetRepository = presets,
                            settingsRepository = settings,
                            kind = surface.kind
                        )
                    }
                }
            }
        }

        for (kind in IMPLEMENTED_KINDS) {
            for (scale in TEXT_SCALES) {
                surface = Surface(kind, scale)
                val starter = starterName(kind)
                compose.waitUntil(timeoutMillis = 10_000) {
                    compose.onAllNodesWithText(starter).fetchSemanticsNodes().isNotEmpty()
                }
                assertNothingOverflowsHorizontally()
                assertFullyInsideWindow("Presets")
                // The kind's own starter is the per-mode content of this shared screen.
                compose.onNodeWithText(starter).performScrollTo()
                assertFullyInsideWindow(starter)
            }
        }
    }

    /** A primary text node that must be fully inside the window. */
    private fun assertFullyInsideWindow(text: String) {
        val root = compose.onRoot().fetchSemanticsNode().size
        val bounds = compose.onNodeWithText(text).fetchSemanticsNode().boundsInRoot
        assertTrue(
            "the node must stay horizontally inside the window: $text -> $bounds",
            bounds.left >= -0.5f && bounds.right <= root.width + 0.5f
        )
        assertTrue(
            "the node must stay vertically inside the window: $text -> $bounds " +
                "(window ${root.width}x${root.height})",
            bounds.top >= -0.5f && bounds.bottom <= root.height + 0.5f
        )
    }

    /** The transport is tagged rather than labelled, so it gets the same check by tag. */
    private fun assertTagFullyInsideWindow(tag: String) {
        val root = compose.onRoot().fetchSemanticsNode().size
        val bounds = compose.onNodeWithTag(tag).fetchSemanticsNode().boundsInRoot
        assertTrue(
            "the $tag transport must stay inside the window: $bounds " +
                "(window ${root.width}x${root.height})",
            bounds.left >= -0.5f && bounds.right <= root.width + 0.5f &&
                bounds.top >= -0.5f && bounds.bottom <= root.height + 0.5f
        )
    }

    /** Every rendered node must stay within the window width, merged tree included. */
    private fun assertNothingOverflowsHorizontally() {
        val root = compose.onRoot().fetchSemanticsNode()
        val width = root.size.width.toFloat()
        val offenders = mutableListOf<String>()
        val pending = ArrayDeque<SemanticsNode>()
        pending += root
        while (pending.isNotEmpty()) {
            val node = pending.removeLast()
            val bounds = node.boundsInRoot
            if (bounds.left < -0.5f || bounds.right > width + 0.5f) {
                val text = node.config.getOrNull(SemanticsProperties.Text)
                    ?.joinToString { it.text }
                offenders += "node ${node.id} (text=$text) at $bounds"
            }
            pending += node.children
        }
        assertTrue(
            "every rendered node must stay inside the window width of $width: $offenders",
            offenders.isEmpty()
        )
    }

    /** The setup summary line that identifies each mode's stored default setup. */
    private fun playSummaryLine(kind: TrainingKind): String = when (kind) {
        TrainingKind.INTERVALS -> "Intervals: Minor third, Major third, Perfect fifth"
        TrainingKind.INTERVAL_WALK -> "Direction: ascending · 3 steps"
        TrainingKind.CHORDS -> "Qualities: major, minor"
        TrainingKind.SCALES -> "Scales: major, natural minor"
        TrainingKind.NOTE_IDENTIFICATION ->
            "Notes: C, C\u266F, D, D\u266F, E, F, F\u266F, G, G\u266F, A, A\u266F, B"
        TrainingKind.SCALE_DEGREES -> "Degrees: 1, 2, 3, 4, 5, 6, 7"
        TrainingKind.CHORD_PROGRESSIONS ->
            "Chords: I, ii, iii, IV, V, vi, vii\u00B0"
        TrainingKind.INTERVALS_IN_CONTEXT ->
            "Intervals: Minor third, Major third, Perfect fifth"
        TrainingKind.MELODIC_DICTATION -> "Steps: -5 to 5 · leap 5 · bias balanced"
        else -> error("no Play summary for ${kind.id}")
    }

    /** The Setup screen's own headline, which proves the routed editor rendered. */
    private fun setupHeadline(kind: TrainingKind): String = when (kind) {
        TrainingKind.INTERVALS -> "Interval setup"
        TrainingKind.INTERVAL_WALK -> "Interval walk setup"
        TrainingKind.CHORDS -> "Chord setup"
        TrainingKind.SCALES -> "Scale setup"
        TrainingKind.NOTE_IDENTIFICATION -> "Note setup"
        TrainingKind.SCALE_DEGREES -> "Scale degree setup"
        TrainingKind.CHORD_PROGRESSIONS -> "Chord progression setup"
        TrainingKind.INTERVALS_IN_CONTEXT -> "Intervals in context setup"
        TrainingKind.MELODIC_DICTATION -> "Melodic dictation setup"
        else -> error("no setup screen for ${kind.id}")
    }

    /** One section inside the Setup screen, in the middle of the form. */
    private fun setupSection(kind: TrainingKind): String = when (kind) {
        TrainingKind.INTERVALS -> "Presentation"
        TrainingKind.INTERVAL_WALK -> "Intervals"
        TrainingKind.CHORDS -> "Chord qualities"
        TrainingKind.SCALES -> "Scales"
        TrainingKind.NOTE_IDENTIFICATION -> "Note classes"
        TrainingKind.CHORD_PROGRESSIONS -> "Chords"
        else -> "Key tonics"
    }

    /** A control near the end of each Setup form; it must scroll into the window. */
    private fun setupTailControl(kind: TrainingKind): String = when (kind) {
        TrainingKind.INTERVALS -> "Separator cue before each pair"
        TrainingKind.INTERVAL_WALK -> "Separator cue before each walk"
        TrainingKind.CHORDS -> "Separator cue before each chord"
        TrainingKind.SCALES -> "Separator cue before each scale"
        TrainingKind.NOTE_IDENTIFICATION -> "Separator cue before each note"
        else -> "Separator cue before each example"
    }

    /** The starter card of one kind, as the shared Presets screen labels it. */
    private fun starterName(kind: TrainingKind): String = when (kind) {
        TrainingKind.INTERVALS -> "Major Pentatonic (starter)"
        TrainingKind.INTERVAL_WALK -> "Ascending thirds and fifths (starter)"
        TrainingKind.CHORDS -> "Major and minor chords (starter)"
        TrainingKind.SCALES -> "Major and natural minor scales (starter)"
        TrainingKind.NOTE_IDENTIFICATION -> "All twelve notes (starter)"
        TrainingKind.SCALE_DEGREES -> "C major scale degrees (starter)"
        TrainingKind.CHORD_PROGRESSIONS -> "Major chord progressions (starter)"
        TrainingKind.INTERVALS_IN_CONTEXT -> "Intervals over the tonic triad (starter)"
        TrainingKind.MELODIC_DICTATION -> "Tonic-to-tonic four-note phrases (starter)"
        else -> error("no starter for ${kind.id}")
    }

    private companion object {
        /** The two reviewed large-text scales for the M11.2 UI row. */
        val TEXT_SCALES = listOf(1.5f, 2f)

        /** Every playable mode; exactly the nine production kinds. */
        val IMPLEMENTED_KINDS = listOf(
            TrainingKind.INTERVALS,
            TrainingKind.INTERVAL_WALK,
            TrainingKind.CHORDS,
            TrainingKind.SCALES,
            TrainingKind.NOTE_IDENTIFICATION,
            TrainingKind.SCALE_DEGREES,
            TrainingKind.CHORD_PROGRESSIONS,
            TrainingKind.INTERVALS_IN_CONTEXT,
            TrainingKind.MELODIC_DICTATION
        )
    }
}

/** Renders [content] at [fontScale] while the density itself stays the display's own. */
@Composable
private fun Scaled(fontScale: Float, content: @Composable () -> Unit) {
    val base = LocalDensity.current
    CompositionLocalProvider(
        LocalDensity provides Density(density = base.density, fontScale = fontScale)
    ) {
        content()
    }
}

/** In-memory settings for host rendering; the same revision contract as the real repository. */
private class TabsHostSettingsRepository : SettingsRepository {
    private val state = MutableStateFlow(SettingsSnapshot(UserSettings.DEFAULTS))
    override val settings: Flow<SettingsSnapshot> = state

    override suspend fun update(change: (UserSettings) -> UserSettings): UserSettings {
        val updated = change(state.value.settings)
        state.value = SettingsSnapshot(updated)
        return updated
    }

    override suspend fun updateMode(
        edit: ModeEdit,
        globalChange: ((UserSettings) -> UserSettings)?
    ): ModeWriteResult {
        val current = state.value
        val existing = current.settings.modeSetups[edit.kind]
            ?: return ModeWriteResult.Rejected("no setup")
        if (existing.revision != edit.expectedRevision) {
            return ModeWriteResult.Conflict(current)
        }
        val base = globalChange?.invoke(current.settings) ?: current.settings
        val updated = base.copy(
            modeSetups = base.modeSetups + (
                edit.kind to SavedModeSetup(existing.revision + 1, edit.change(existing))
                )
        )
        state.value = SettingsSnapshot(updated)
        return ModeWriteResult.Saved(SettingsSnapshot(updated))
    }

    override suspend fun saveAndSelect(
        edits: List<ModeEdit>,
        selectedKind: TrainingKind,
        globalChange: ((UserSettings) -> UserSettings)?
    ): ModeWriteResult {
        var snapshot = state.value
        for (edit in edits) {
            val result = updateMode(edit, globalChange)
            if (result !is ModeWriteResult.Saved) return result
            snapshot = result.snapshot
        }
        val updated = snapshot.settings.copy(selectedKind = selectedKind)
        state.value = SettingsSnapshot(updated)
        return ModeWriteResult.Saved(SettingsSnapshot(updated))
    }

    override suspend fun selectKind(kind: TrainingKind): ModeWriteResult {
        val updated = state.value.settings.copy(selectedKind = kind)
        state.value = SettingsSnapshot(updated)
        return ModeWriteResult.Saved(SettingsSnapshot(updated))
    }

    override suspend fun resetToDefaults(): UserSettings {
        state.value = SettingsSnapshot(UserSettings.DEFAULTS)
        return UserSettings.DEFAULTS
    }
}

/**
 * The read-only preset store the shared Presets screen sees: the real interval starters on the
 * intervals flow and this kind's own starters through the kind-scoped slots, exactly as the
 * production store scopes them.
 */
private class TabsHostPresetRepository : KindAwarePresetStore {
    override val presets: Flow<List<Preset>> = MutableStateFlow(PresetStarters.ALL)

    override suspend fun refresh() = Unit

    override suspend fun slotsOf(kind: TrainingKind): List<Preset> = emptyList()

    override suspend fun starterOf(kind: TrainingKind): Preset? =
        PresetStarters.BY_KIND[kind]?.firstOrNull()

    override suspend fun markUsed(id: PresetId) = Unit

    override suspend fun create(
        name: String,
        configuration: PresetConfiguration,
        seed: Long?,
        musicGain: Float,
        voiceGain: Float
    ): Preset = error("not used by the tabs test")

    override suspend fun update(preset: Preset): Preset = error("not used by the tabs test")

    override suspend fun rename(id: PresetId, name: String): Preset =
        error("not used by the tabs test")

    override suspend fun duplicate(id: PresetId, name: String?): Preset =
        error("not used by the tabs test")

    override suspend fun delete(id: PresetId) = Unit

    override suspend fun clear(id: PresetId): Preset = error("not used by the tabs test")

    override suspend fun previewImport(text: String): ImportPreview = ImportPreview(emptyList())

    override suspend fun commitImport(
        preview: ImportPreview,
        replaceExistingIds: Set<PresetId>
    ): ImportResult = ImportResult(emptyList(), emptyList())

    override suspend fun export(ids: Collection<PresetId>): String = ""
}
