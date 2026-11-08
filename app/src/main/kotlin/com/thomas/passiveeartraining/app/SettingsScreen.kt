package com.thomas.passiveeartraining.app

import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.thomas.passiveeartraining.training.settings.AnswerVisibility
import com.thomas.passiveeartraining.training.settings.SettingsRepository
import com.thomas.passiveeartraining.training.settings.ThemePreference
import com.thomas.passiveeartraining.training.settings.UserSettings
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

private const val GAIN_WRITE_DEBOUNCE_MS = 80L

/** Settings as a screen sees them. */
data class SettingsUiState(
    val settings: UserSettings = UserSettings.DEFAULTS,
    val loaded: Boolean = false,
    val diagnostics: List<String> = emptyList()
)

/**
 * Projects the stored settings and applies explicit changes.
 *
 * Every change runs through the repository's atomic update, so two quick presses cannot lose one
 * another, and an invalid value would be refused before it is written.
 */
class SettingsViewModel(private val repository: SettingsRepository) : ViewModel() {

    private val writeError = MutableStateFlow<String?>(null)

    val state: StateFlow<SettingsUiState> = repository.settings
        .combine(writeError) { snapshot, error ->
            SettingsUiState(
                settings = snapshot.settings,
                loaded = true,
                diagnostics = snapshot.diagnostics.map { it.toString() } + listOfNotNull(error)
            )
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, SettingsUiState())

    fun setTheme(value: ThemePreference) = change { it.copy(theme = value) }

    fun setDynamicColor(value: Boolean) = change { it.copy(dynamicColor = value) }

    fun setAnswerVisibility(value: AnswerVisibility) = change { it.copy(answerVisibility = value) }

    private var gainsWrite: Job? = null

    /**
     * Stores gains after a short pause so a drag does not queue one repository write per pixel.
     * The screen updates its slider locally, so this delay is not visible while dragging.
     */
    fun setGains(music: Float, voice: Float) {
        gainsWrite?.cancel()
        gainsWrite = viewModelScope.launch {
            delay(GAIN_WRITE_DEBOUNCE_MS)
            persistGains(music, voice)
        }
    }

    /** Flushes the final value when the user lifts the slider. */
    fun commitGains(music: Float, voice: Float) {
        gainsWrite?.cancel()
        gainsWrite = viewModelScope.launch { persistGains(music, voice) }
    }

    private suspend fun persistGains(music: Float, voice: Float) {
        try {
            repository.update { it.copy(musicGain = music, voiceGain = voice) }
            writeError.value = null
        } catch (cancelled: kotlinx.coroutines.CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            writeError.value = failure.message ?: "Settings could not be saved"
        }
    }

    /** The explicit recovery action for a store that needed attention. */
    fun resetToDefaults() {
        write { repository.resetToDefaults() }
    }

    private fun change(transform: (UserSettings) -> UserSettings) {
        write { repository.update(transform) }
    }
    private fun write(action: suspend () -> Unit) {
        viewModelScope.launch {
            try {
                action()
                writeError.value = null
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                writeError.value = failure.message ?: "Settings could not be saved"
            }
        }
    }
}

/**
 * Appearance, display and sound defaults, plus what lives on the device.
 *
 * The local-data card is the product's honest description: two app-private files, no network
 * permission and five local preset slots.
 */
@Composable
internal fun SettingsScreen(settingsRepository: SettingsRepository) {
    val viewModel: SettingsViewModel = viewModel(
        factory = SettingsViewModelFactory(settingsRepository)
    )
    val state by viewModel.state.collectAsStateWithLifecycle()
    val settings = state.settings
    var musicPosition by remember {
        mutableFloatStateOf(gainPosition(settings.musicGain, UserSettings.DEFAULT_MUSIC_GAIN))
    }
    var voicePosition by remember {
        mutableFloatStateOf(gainPosition(settings.voiceGain, UserSettings.DEFAULT_VOICE_GAIN))
    }
    var draggingMusic by remember { mutableStateOf(false) }
    var draggingVoice by remember { mutableStateOf(false) }

    // Follow changes made elsewhere, but never fight the finger while a slider is being dragged.
    LaunchedEffect(settings.musicGain, settings.voiceGain) {
        if (!draggingMusic) {
            musicPosition =
                gainPosition(settings.musicGain, UserSettings.DEFAULT_MUSIC_GAIN)
        }
        if (!draggingVoice) {
            voicePosition =
                gainPosition(settings.voiceGain, UserSettings.DEFAULT_VOICE_GAIN)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        if (state.diagnostics.isNotEmpty()) {
            Card {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        "Stored settings needed attention",
                        style = MaterialTheme.typography.titleMedium
                    )
                    state.diagnostics.forEach { Text(it) }
                    Button(onClick = viewModel::resetToDefaults) { Text("Reset to defaults") }
                }
            }
        }

        Text("Theme", style = MaterialTheme.typography.titleMedium)
        ChoiceRow(ThemePreference.entries, settings.theme, viewModel::setTheme) {
            it.name.lowercase()
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("Match the system colors")
            Switch(
                checked = settings.dynamicColor,
                onCheckedChange = viewModel::setDynamicColor,
                enabled = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
            )
        }

        Text("Answer display", style = MaterialTheme.typography.titleMedium)
        ChoiceRow(
            AnswerVisibility.entries,
            settings.answerVisibility,
            viewModel::setAnswerVisibility
        ) {
            when (it) {
                AnswerVisibility.HIDDEN -> "Hide on screen"
                AnswerVisibility.SHOWN_WHEN_CONSUMED -> "Show once spoken"
            }
        }
        Text(
            "The spoken answer is never written into the player, notification or accessibility " +
                "state before it has been consumed.",
            style = MaterialTheme.typography.bodySmall
        )

        Text("Sound", style = MaterialTheme.typography.titleMedium)
        val musicGain = gainFromPosition(musicPosition, UserSettings.DEFAULT_MUSIC_GAIN)
        Text("Music gain: ${setupGain(musicGain, UserSettings.DEFAULT_MUSIC_GAIN)}")
        Slider(
            value = musicPosition,
            onValueChange = {
                draggingMusic = true
                musicPosition = it
                viewModel.setGains(
                    gainFromPosition(it, UserSettings.DEFAULT_MUSIC_GAIN),
                    gainFromPosition(voicePosition, UserSettings.DEFAULT_VOICE_GAIN)
                )
            },
            onValueChangeFinished = {
                draggingMusic = false
                viewModel.commitGains(
                    gainFromPosition(musicPosition, UserSettings.DEFAULT_MUSIC_GAIN),
                    gainFromPosition(voicePosition, UserSettings.DEFAULT_VOICE_GAIN)
                )
            },
            valueRange = 0f..1f,
            modifier = Modifier.semantics { contentDescription = "Music gain" }
        )
        Text(
            "The middle position is the reviewed piano level. Move left to lower it and right to " +
                "raise it; very high settings can distort on dense chords.",
            style = MaterialTheme.typography.bodySmall
        )
        val voiceGain = gainFromPosition(voicePosition, UserSettings.DEFAULT_VOICE_GAIN)
        Text("Voice gain: ${setupGain(voiceGain, UserSettings.DEFAULT_VOICE_GAIN)}")
        Slider(
            value = voicePosition,
            onValueChange = {
                draggingVoice = true
                voicePosition = it
                viewModel.setGains(
                    gainFromPosition(musicPosition, UserSettings.DEFAULT_MUSIC_GAIN),
                    gainFromPosition(it, UserSettings.DEFAULT_VOICE_GAIN)
                )
            },
            onValueChangeFinished = {
                draggingVoice = false
                viewModel.commitGains(
                    gainFromPosition(musicPosition, UserSettings.DEFAULT_MUSIC_GAIN),
                    gainFromPosition(voicePosition, UserSettings.DEFAULT_VOICE_GAIN)
                )
            },
            valueRange = 0f..1f,
            modifier = Modifier.semantics { contentDescription = "Voice gain" }
        )
        Text(
            "The middle position is the reviewed voice level; the upper half can raise it.",
            style = MaterialTheme.typography.bodySmall
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun <T> ChoiceRow(
    choices: List<T>,
    selected: T,
    onSelect: (T) -> Unit,
    label: (T) -> String
) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        choices.forEach { choice ->
            FilterChip(
                selected = choice == selected,
                onClick = { onSelect(choice) },
                label = { Text(label(choice)) }
            )
        }
    }
}

private class SettingsViewModelFactory(private val repository: SettingsRepository) :
    ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        @Suppress("UNCHECKED_CAST")
        return SettingsViewModel(repository) as T
    }
}

/**
 * Slider position that shows [gain] with the reviewed [default] at the middle.
 *
 * Each slider spans 0..100%, with the reviewed normal at 50% and the slider top at twice that
 * normal. The music and voice controls therefore share one readable scale even though their stored
 * gains differ.
 */
internal fun gainPosition(gain: Float, default: Float): Float {
    if (default <= 0f) return 0f
    val fullScale = 2f * default
    return (gain / fullScale).coerceIn(0f, 1f)
}

/** Inverse of [gainPosition]: the gain a slider position selects. */
internal fun gainFromPosition(position: Float, default: Float): Float {
    val fullScale = 2f * default
    return (position.coerceIn(0f, 1f) * fullScale)
        .coerceIn(0f, UserSettings.MAX_GAIN)
}
