package com.thomas.passiveeartraining.app

import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import com.thomas.passiveeartraining.training.provider.TrainingKind
import com.thomas.passiveeartraining.training.settings.ModeWriteResult
import com.thomas.passiveeartraining.training.settings.SettingsRepository
import com.thomas.passiveeartraining.training.settings.ThemePreference
import com.thomas.passiveeartraining.training.settings.TrainingMode
import com.thomas.passiveeartraining.training.settings.UserSettings
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable

/** Typed destinations. Serializable keys make the back stack saveable. */
@Serializable
internal data object PlayKey : NavKey

@Serializable
internal data object SetupKey : NavKey

@Serializable
internal data object PresetsKey : NavKey

@Serializable
internal data object SettingsKey : NavKey

@Serializable
internal data object HelpKey : NavKey

/** The Material 3 theme, driven by the stored preference and the dynamic-color flag. */
@Composable
internal fun PassiveEarTrainingTheme(settings: UserSettings, content: @Composable () -> Unit) {
    val dark = when (settings.theme) {
        ThemePreference.SYSTEM -> isSystemInDarkTheme()
        ThemePreference.LIGHT -> false
        ThemePreference.DARK -> true
    }
    val context = LocalContext.current
    val colors = when {
        settings.dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        dark -> brandDarkColorScheme()
        else -> brandLightColorScheme()
    }
    MaterialTheme(colorScheme = colors, content = content)
}

/**
 * The application shell: a bottom bar on the three top-level destinations and typed Navigation 3
 * destinations for the rest. The session itself stays with the media service, so every screen here
 * disconnects without stopping playback.
 */
@Composable
internal fun AppRoot(application: PassiveEarTrainingApp) {
    val settings by application.settings.collectAsStateWithLifecycle()
    PassiveEarTrainingTheme(settings.settings) {
        AppNavigation(application)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AppNavigation(application: PassiveEarTrainingApp) {
    val settingsState by application.settings.collectAsStateWithLifecycle()
    val selectedKind = settingsState.settings.selectedKind
    val backStack = rememberNavBackStack(PlayKey)
    val playback: PlaybackViewModel = viewModel()
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val popBack: () -> Unit = {
        if (backStack.size > 1) {
            backStack.removeLastOrNull()
        }
    }

    // System back closes an open sidebar first and keeps the page underneath; without a sidebar it
    // returns any tab to the home tab, and on Play the default behaviour still exits.
    BackHandler(
        enabled = drawerState.isOpen || backStack.lastOrNull() != PlayKey
    ) {
        if (drawerState.isOpen) {
            scope.launch { drawerState.close() }
        } else {
            backStack.goTo(PlayKey)
        }
    }

    val playbackState by playback.state.collectAsStateWithLifecycle()
    // A running session counts as active even before the periodic state poll catches up: the
    // transport flips from the media callback first, and the drawer's switch path must not miss
    // the window where a session is audibly playing.
    val sessionRunning = playbackState.active || playbackState.playing || playbackState.paused
    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModeDrawerContent(
                selected = selectedKind,
                onSelectMode = { selected ->
                    // The drawer animation and the selection work are independent: the switch is
                    // requested as soon as the selection is saved, never after an animation.
                    scope.launch { drawerState.close() }
                    scope.launch {
                        val failure = saveTrainingMode(application.settingsRepository, selected)
                        if (failure != null) {
                            snackbar.showSnackbar(failure)
                            return@launch
                        }
                        backStack.goTo(PlayKey)
                        // Read the live state at selection time: the drawer content can be retained
                        // from an earlier composition (for example opened while stopped), so a
                        // captured value must never decide whether the running session switches.
                        val selectionState = playback.state.value
                        if (selectionState.active || selectionState.playing ||
                            selectionState.paused
                        ) {
                            // The service resolves and validates the destination; a rejected
                            // switch leaves the audible session untouched (M3).
                            playback.switchMode(selected)
                        }
                    }
                },
                onOpenSettings = {
                    scope.launch { drawerState.close() }
                    backStack.goTo(SettingsKey)
                },
                settingsSelected = backStack.lastOrNull() == SettingsKey,
                onOpenHelp = {
                    scope.launch { drawerState.close() }
                    backStack.goTo(HelpKey)
                },
                helpSelected = backStack.lastOrNull() == HelpKey
            )
        }
    ) {
        Scaffold(
            snackbarHost = { SnackbarHost(snackbar) },
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            when (backStack.lastOrNull()) {
                                SettingsKey -> "Settings"
                                HelpKey -> "Help"
                                else -> selectedKind.label
                            }
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = { scope.launch { drawerState.open() } }) {
                            Icon(
                                Icons.Filled.Menu,
                                contentDescription = "Open the mode sidebar"
                            )
                        }
                    }
                )
            },
            bottomBar = {
                val current = backStack.lastOrNull()
                // The bar is available on every main destination, so the setup page needs no
                // explicit close button and the sidebar can be reached from anywhere.
                val showBar = current == PlayKey || current == PresetsKey ||
                    current == SettingsKey || current == SetupKey
                if (showBar) {
                    NavigationBar {
                        NavigationBarItem(
                            selected = current == PresetsKey,
                            onClick = { backStack.goTo(PresetsKey) },
                            icon = {
                                Icon(
                                    Icons.Filled.Star,
                                    contentDescription = null,
                                    modifier = Modifier.size(24.dp)
                                )
                            },
                            label = { Text("Presets") }
                        )
                        NavigationBarItem(
                            selected = current == PlayKey,
                            onClick = { backStack.goTo(PlayKey) },
                            icon = {
                                // A vector note renders at the same 24 dp size as the star and
                                // the gear on every device, unlike a font glyph.
                                Icon(
                                    AppIcons.MusicNote,
                                    contentDescription = null,
                                    modifier = Modifier.size(24.dp)
                                )
                            },
                            label = { Text("Play") }
                        )
                        NavigationBarItem(
                            selected = current == SetupKey,
                            onClick = { backStack.goTo(SetupKey) },
                            icon = {
                                Icon(
                                    Icons.Filled.Settings,
                                    contentDescription = null,
                                    modifier = Modifier.size(24.dp)
                                )
                            },
                            label = { Text("Setup") }
                        )
                    }
                }
            }
        ) { padding ->
            NavDisplay(
                backStack = backStack,
                onBack = popBack,
                modifier = Modifier.padding(padding),
                entryProvider = entryProvider {
                    entry<PlayKey> {
                        // While audio is active the page describes what is audible, not the
                        // destination that may still be waiting for its boundary.
                        val audibleKind = if (sessionRunning) {
                            playbackState.activeKind ?: selectedKind
                        } else {
                            selectedKind
                        }
                        PlayScreen(playback = playback, mode = audibleKind)
                    }
                    entry<SetupKey> {
                        when {
                            !selectedKind.implemented -> ModeUnavailableScreen(selectedKind)
                            // Chords, scales, notes, scale degrees, chord progressions and
                            // intervals in context have their own editors; the kind-aware routing
                            // keeps each mode's draft and autosave contract separate.
                            selectedKind == TrainingKind.INTERVAL_WALK ->
                                IntervalWalkSetupScreen(
                                    settingsRepository = application.settingsRepository
                                )
                            selectedKind == TrainingKind.CHORDS ->
                                ChordSetupScreen(
                                    settingsRepository = application.settingsRepository
                                )
                            selectedKind == TrainingKind.SCALES ->
                                ScaleSetupScreen(
                                    settingsRepository = application.settingsRepository
                                )
                            selectedKind == TrainingKind.NOTE_IDENTIFICATION ->
                                NoteSetupScreen(
                                    settingsRepository = application.settingsRepository
                                )
                            selectedKind == TrainingKind.SCALE_DEGREES ->
                                ScaleDegreeSetupScreen(
                                    settingsRepository = application.settingsRepository
                                )
                            selectedKind == TrainingKind.CHORD_PROGRESSIONS ->
                                ProgressionSetupScreen(
                                    settingsRepository = application.settingsRepository
                                )
                            selectedKind == TrainingKind.INTERVALS_IN_CONTEXT ->
                                ContextualIntervalSetupScreen(
                                    settingsRepository = application.settingsRepository
                                )
                            selectedKind == TrainingKind.MELODIC_DICTATION ->
                                MelodicDictationSetupScreen(
                                    settingsRepository = application.settingsRepository
                                )
                            else ->
                                SetupScreen(settingsRepository = application.settingsRepository)
                        }
                    }
                    entry<PresetsKey> {
                        if (selectedKind.implemented) {
                            PresetsScreen(
                                presetRepository = application.kindAwarePresetRepository,
                                settingsRepository = application.settingsRepository,
                                kind = selectedKind
                            )
                        } else {
                            ModeUnavailableScreen(selectedKind)
                        }
                    }
                    entry<SettingsKey> {
                        SettingsScreen(settingsRepository = application.settingsRepository)
                    }
                    entry<HelpKey> {
                        HelpScreen()
                    }
                }
            )
        }
    }
}

/** Replaces the back stack with one top-level destination, so tabs do not stack. */
private fun NavBackStack<NavKey>.goTo(key: NavKey) {
    if (lastOrNull() == key) return
    clear()
    add(key)
}

/** Returns a visible write failure; the drawer stays on its saved mode on rejection. */
internal suspend fun saveTrainingMode(repository: SettingsRepository, kind: TrainingKind): String? =
    try {
        when (val result = repository.selectKind(kind)) {
            is ModeWriteResult.Saved -> null
            is ModeWriteResult.Conflict ->
                "The mode changed elsewhere; the newest selection will be shown."
            is ModeWriteResult.Rejected -> result.message
        }
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (failure: Exception) {
        failure.message ?: "The learning mode could not be saved"
    }
