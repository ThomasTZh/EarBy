package com.thomas.passiveeartraining.app

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.thomas.passiveeartraining.data.preset.Preset
import com.thomas.passiveeartraining.data.preset.PresetConfiguration
import com.thomas.passiveeartraining.data.preset.PresetRepository
import com.thomas.passiveeartraining.data.preset.PresetStarters
import com.thomas.passiveeartraining.data.preset.SqlitePresetRepository
import com.thomas.passiveeartraining.music.MidiNote
import com.thomas.passiveeartraining.music.SimpleInterval
import com.thomas.passiveeartraining.training.provider.TrainingKind
import com.thomas.passiveeartraining.training.settings.ChordSetup
import com.thomas.passiveeartraining.training.settings.ContextualIntervalSetup
import com.thomas.passiveeartraining.training.settings.IntervalWalkSetup
import com.thomas.passiveeartraining.training.settings.LastSetup
import com.thomas.passiveeartraining.training.settings.MelodicDictationSetup
import com.thomas.passiveeartraining.training.settings.ModeEdit
import com.thomas.passiveeartraining.training.settings.ModePayload
import com.thomas.passiveeartraining.training.settings.ModeWriteResult
import com.thomas.passiveeartraining.training.settings.NoteSetup
import com.thomas.passiveeartraining.training.settings.ProgressionSetup
import com.thomas.passiveeartraining.training.settings.ScaleDegreeSetup
import com.thomas.passiveeartraining.training.settings.ScaleSetup
import com.thomas.passiveeartraining.training.settings.SettingsRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The preset operations a kind-aware screen needs.
 *
 * [PresetRepository.presets] stays intervals-scoped, so a later kind's screen reads its own five
 * slots through [slotsOf] and reloads them after every write; no kind's list can leak into
 * another's (SC7/M5).
 */
interface KindAwarePresetStore : PresetRepository {
    /** The five slots of [kind] in slot order; an empty slot is still returned. */
    suspend fun slotsOf(kind: TrainingKind): List<Preset>

    /** The built-in starter of [kind] as the store has it, its use time included. */
    suspend fun starterOf(kind: TrainingKind): Preset?
}

/** Adapts the application's single preset store to the kind-aware screen contract. */
class SqliteKindAwarePresetStore(private val delegate: SqlitePresetRepository) :
    KindAwarePresetStore,
    PresetRepository by delegate {
    override suspend fun slotsOf(kind: TrainingKind): List<Preset> = delegate.slotsOf(kind)

    override suspend fun starterOf(kind: TrainingKind): Preset? = delegate.starterOf(kind)
}

/** How the user's five slots are ordered. */
enum class PresetSort {
    /** Most recently used first; equal (or never used) times break A to Z. */
    RECENT,

    /** A to Z by name. */
    ALPHABETICAL
}

/** The preset list as a screen sees it. */
data class PresetsUiState(
    val presets: List<Preset> = emptyList(),
    val loaded: Boolean = false,
    val sort: PresetSort = PresetSort.RECENT,
    val showDetails: Boolean = false,
    val message: String? = null
)

/** Lists the preset slots and applies explicit operations. */
class PresetsViewModel(
    private val presets: KindAwarePresetStore,
    private val settings: SettingsRepository,
    /** The kind whose starter and slots this screen shows; only implemented kinds arrive here. */
    private val kind: TrainingKind = TrainingKind.INTERVALS
) : ViewModel() {

    private val message = MutableStateFlow<String?>(null)
    private val sort = MutableStateFlow(PresetSort.RECENT)
    private val showDetails = MutableStateFlow(false)

    /** True when this kind keeps its own starter and five slots, as chords, scales, notes, degrees, progressions, intervals in context and melodic dictation do. */
    private val kindAware: Boolean =
        kind == TrainingKind.INTERVAL_WALK || kind == TrainingKind.CHORDS ||
            kind == TrainingKind.SCALES ||
            kind == TrainingKind.NOTE_IDENTIFICATION || kind == TrainingKind.SCALE_DEGREES ||
            kind == TrainingKind.CHORD_PROGRESSIONS ||
            kind == TrainingKind.INTERVALS_IN_CONTEXT ||
            kind == TrainingKind.MELODIC_DICTATION

    /** This kind's slots, reloaded after every write; the shared list stays intervals-scoped. */
    private val slots = MutableStateFlow<List<Preset>>(emptyList())

    /** This kind's starter as the store has it, so its use time orders it with the slots. */
    private val starter = MutableStateFlow(
        PresetStarters.BY_KIND.getOrElse(kind) { emptyList() }.firstOrNull()
    )

    /**
     * The list this screen observes.
     *
     * Intervals keep the repository's own flow exactly as before. A kind-aware kind combines its
     * starter with the five kind-scoped slots, so no other kind's presets can mix in.
     */
    private val source: Flow<List<Preset>> = if (kindAware) {
        combine(starter, slots) { head, loaded -> listOfNotNull(head) + loaded }
    } else {
        presets.presets
    }

    val state: StateFlow<PresetsUiState> = combine(
        source,
        message,
        sort,
        showDetails
    ) { list, note, ordering, details ->
        PresetsUiState(
            presets = displayOrder(list, ordering),
            loaded = true,
            sort = ordering,
            showDetails = details,
            message = note
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, PresetsUiState())

    init {
        operation("The presets could not be loaded") {
            if (kindAware) {
                reloadKind()
            } else {
                presets.refresh()
            }
            null
        }
    }

    /** Reloads this kind's starter and slots after a write; intervals refresh with the store. */
    private suspend fun reloadKind() {
        if (kindAware) {
            starter.value = presets.starterOf(kind)
                ?: PresetStarters.BY_KIND.getOrElse(kind) { emptyList() }.firstOrNull()
            slots.value = presets.slotsOf(kind)
        }
    }

    /** Both starters and the five slots follow the chosen ordering. */
    private fun displayOrder(list: List<Preset>, ordering: PresetSort): List<Preset> =
        when (ordering) {
            PresetSort.ALPHABETICAL -> list.sortedBy { it.name.lowercase() }
            PresetSort.RECENT -> list.sortedWith(
                compareByDescending<Preset> { it.lastUsedAtEpochMs }.thenBy { it.name.lowercase() }
            )
        }

    fun setSort(value: PresetSort) {
        sort.value = value
    }

    fun setShowDetails(value: Boolean) {
        showDetails.value = value
    }

    fun use(preset: Preset) {
        operation("The preset could not be applied") {
            // The revision guard makes a preset race explicit instead of overwriting newer edits.
            val revision = settings.settings.first().settings
                .modeSetups.getValue(kind).revision
            val result = settings.updateMode(
                ModeEdit(kind, revision) {
                    // Presets never carry a seed: every application starts from null so the
                    // session draws a fresh seed and the same preset gives a new sequence.
                    when (kind) {
                        TrainingKind.INTERVAL_WALK -> ModePayload.IntervalWalk(
                            preset.intervalWalkSetup().copy(seed = null)
                        )
                        TrainingKind.CHORDS -> ModePayload.Chords(
                            preset.chordsSetup().copy(seed = null)
                        )
                        TrainingKind.SCALES -> ModePayload.Scales(
                            preset.scalesSetup().copy(seed = null)
                        )
                        TrainingKind.NOTE_IDENTIFICATION -> ModePayload.Notes(
                            preset.notesSetup().copy(seed = null)
                        )
                        TrainingKind.SCALE_DEGREES -> ModePayload.ScaleDegrees(
                            preset.scaleDegreesSetup().copy(seed = null)
                        )
                        TrainingKind.CHORD_PROGRESSIONS -> ModePayload.Progressions(
                            preset.progressionsSetup().copy(seed = null)
                        )
                        TrainingKind.INTERVALS_IN_CONTEXT -> ModePayload.ContextualIntervals(
                            preset.contextualIntervalsSetup().copy(seed = null)
                        )
                        TrainingKind.MELODIC_DICTATION -> ModePayload.MelodicDictation(
                            preset.melodicDictationSetup().copy(seed = null)
                        )
                        TrainingKind.INTERVALS -> ModePayload.Intervals(
                            preset.intervalsSetup().copy(seed = null)
                        )
                        else -> throw IllegalStateException(
                            "Preset kind ${kind.id} has no mode payload; this screen only " +
                                "serves implemented kinds"
                        )
                    }
                },
                globalChange = {
                    it.copy(musicGain = preset.musicGain, voiceGain = preset.voiceGain)
                }
            )
            if (result is ModeWriteResult.Saved) {
                // Applying a preset is what the Recent sort follows; a save or rename never
                // reorders, and a conflict changes nothing.
                presets.markUsed(preset.id)
                reloadKind()
            }
            when (result) {
                is ModeWriteResult.Saved -> "Applied '${preset.name}'. Start from the Play tab."
                is ModeWriteResult.Conflict ->
                    "The setup changed elsewhere; open Presets again to apply."
                is ModeWriteResult.Rejected -> throw IllegalArgumentException(result.message)
            }
        }
    }

    /** Fills a slot with the current setup and gains, keeping the slot's identity and name. */
    fun saveInto(preset: Preset) {
        operation("The preset could not be saved") {
            val current = settings.settings.first().settings
            val updated = if (kind == TrainingKind.CHORDS) {
                presets.update(
                    preset.copy(
                        configuration = PresetConfiguration.Chords(current.chordSetup),
                        seed = null,
                        musicGain = current.musicGain,
                        voiceGain = current.voiceGain,
                        empty = false
                    )
                )
            } else if (kind == TrainingKind.SCALES) {
                presets.update(
                    preset.copy(
                        configuration = PresetConfiguration.Scales(current.scalesSetup),
                        seed = null,
                        musicGain = current.musicGain,
                        voiceGain = current.voiceGain,
                        empty = false
                    )
                )
            } else if (kind == TrainingKind.NOTE_IDENTIFICATION) {
                presets.update(
                    preset.copy(
                        configuration = PresetConfiguration.Notes(current.noteSetup),
                        seed = null,
                        musicGain = current.musicGain,
                        voiceGain = current.voiceGain,
                        empty = false
                    )
                )
            } else if (kind == TrainingKind.SCALE_DEGREES) {
                presets.update(
                    preset.copy(
                        configuration = PresetConfiguration.ScaleDegrees(
                            current.scaleDegreeSetup
                        ),
                        seed = null,
                        musicGain = current.musicGain,
                        voiceGain = current.voiceGain,
                        empty = false
                    )
                )
            } else if (kind == TrainingKind.INTERVAL_WALK) {
                presets.update(
                    preset.copy(
                        configuration = PresetConfiguration.IntervalWalk(
                            current.intervalWalkSetup
                        ),
                        seed = null,
                        musicGain = current.musicGain,
                        voiceGain = current.voiceGain,
                        empty = false
                    )
                )
            } else if (kind == TrainingKind.CHORD_PROGRESSIONS) {
                presets.update(
                    preset.copy(
                        configuration = PresetConfiguration.Progressions(
                            current.progressionSetup
                        ),
                        seed = null,
                        musicGain = current.musicGain,
                        voiceGain = current.voiceGain,
                        empty = false
                    )
                )
            } else if (kind == TrainingKind.INTERVALS_IN_CONTEXT) {
                presets.update(
                    preset.copy(
                        configuration = PresetConfiguration.ContextualIntervals(
                            current.contextualIntervalSetup
                        ),
                        seed = null,
                        musicGain = current.musicGain,
                        voiceGain = current.voiceGain,
                        empty = false
                    )
                )
            } else if (kind == TrainingKind.MELODIC_DICTATION) {
                presets.update(
                    preset.copy(
                        configuration = PresetConfiguration.MelodicDictation(
                            current.melodicDictationSetup
                        ),
                        seed = null,
                        musicGain = current.musicGain,
                        voiceGain = current.voiceGain,
                        empty = false
                    )
                )
            } else if (kind == TrainingKind.INTERVALS) {
                presets.update(
                    preset.copy(
                        configuration = PresetConfiguration.Intervals(current.lastSetup),
                        seed = null,
                        musicGain = current.musicGain,
                        voiceGain = current.voiceGain,
                        empty = false
                    )
                )
            } else {
                // A silent intervals fallback here would replace a preset of another kind with
                // the wrong configuration; refuse instead.
                throw IllegalStateException(
                    "Preset kind ${kind.id} has no configuration; this screen only serves " +
                        "implemented kinds"
                )
            }
            reloadKind()
            "Replaced '${updated.name}' with the current setup."
        }
    }

    /** Renames a slot. */
    fun rename(preset: Preset, name: String) {
        operation("The preset could not be renamed") {
            val renamed = presets.rename(preset.id, name)
            reloadKind()
            "Renamed to '${renamed.name}'."
        }
    }

    /** Empties a filled slot; the slot keeps its place and name and can be filled again (M5). */
    fun clear(preset: Preset) {
        operation("The preset could not be cleared") {
            val cleared = presets.clear(preset.id)
            reloadKind()
            "Cleared '${cleared.name}'; the slot is empty again."
        }
    }

    /** Builds a document containing every filled user slot across all modes. */
    fun exportDocument(onReady: (String) -> Unit) {
        viewModelScope.launch {
            try {
                val ids = TrainingKind.ALL
                    .flatMap { kind -> presets.slotsOf(kind) }
                    .filterNot { it.empty || it.builtIn }
                    .map { it.id }
                require(ids.isNotEmpty()) { "There are no filled preset slots to export." }
                onReady(presets.export(ids))
                message.value = "Exported ${ids.size} preset(s)."
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                message.value = failure.message ?: "The presets could not be exported"
            }
        }
    }

    /** Imports the ready entries and reports rejected rows instead of silently dropping them. */
    fun importDocument(text: String) {
        operation("The presets could not be imported") {
            val preview = presets.previewImport(text)
            if (preview.ready.isEmpty()) {
                val reasons = preview.rejected.joinToString(" ") { entry ->
                    "'${entry.name}': ${entry.reason}"
                }
                throw IllegalArgumentException(
                    if (reasons.isBlank()) "The document contains no presets." else reasons
                )
            }
            val result = presets.commitImport(preview)
            presets.refresh()
            reloadKind()
            buildString {
                append("Imported ${result.imported.size} preset(s)")
                if (result.replaced.isNotEmpty()) {
                    append("; replaced ${result.replaced.size}")
                }
                if (preview.rejected.isNotEmpty()) {
                    append("; skipped ${preview.rejected.size}")
                }
                append(".")
            }
        }
    }

    private fun operation(fallback: String, action: suspend () -> String?) {
        viewModelScope.launch {
            try {
                message.value = action()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                message.value = failure.message ?: fallback
            }
        }
    }
}

/** Compact padding keeps the edit actions on one line without breaking their labels. */
private val COMPACT_BUTTON_PADDING = PaddingValues(horizontal = 12.dp, vertical = 4.dp)

/**
 * The intervals setup this screen shows and applies.
 *
 * The preset list is kind-scoped, so a preset of another kind cannot appear here; a programming
 * error is reported rather than applied as the wrong mode.
 */
private fun Preset.intervalsSetup(): LastSetup =
    (configuration as? PresetConfiguration.Intervals)?.setup
        ?: throw IllegalStateException("Preset '$name' is not an intervals preset")

/** The chords setup this screen shows and applies, on the same terms as [intervalsSetup]. */
private fun Preset.chordsSetup(): ChordSetup = (configuration as? PresetConfiguration.Chords)?.setup
    ?: throw IllegalStateException("Preset '$name' is not a chords preset")

/** The scales setup this screen shows and applies, on the same terms as [intervalsSetup]. */
private fun Preset.scalesSetup(): ScaleSetup = (configuration as? PresetConfiguration.Scales)?.setup
    ?: throw IllegalStateException("Preset '$name' is not a scales preset")

/** The notes setup this screen shows and applies, on the same terms as [intervalsSetup]. */
private fun Preset.notesSetup(): NoteSetup = (configuration as? PresetConfiguration.Notes)?.setup
    ?: throw IllegalStateException("Preset '$name' is not a notes preset")

/** The scale-degrees setup this screen shows and applies, on the same terms as [intervalsSetup]. */
private fun Preset.scaleDegreesSetup(): ScaleDegreeSetup =
    (configuration as? PresetConfiguration.ScaleDegrees)?.setup
        ?: throw IllegalStateException("Preset '$name' is not a scale-degrees preset")

/** The progressions setup this screen shows and applies, on the same terms as [intervalsSetup]. */
private fun Preset.progressionsSetup(): ProgressionSetup =
    (configuration as? PresetConfiguration.Progressions)?.setup
        ?: throw IllegalStateException("Preset '$name' is not a chord-progressions preset")

/** The contextual-interval setup this screen shows and applies, on the same terms (M9B). */
private fun Preset.contextualIntervalsSetup(): ContextualIntervalSetup =
    (configuration as? PresetConfiguration.ContextualIntervals)?.setup
        ?: throw IllegalStateException("Preset '$name' is not an intervals-in-context preset")

/** The melodic-dictation setup this screen shows and applies, on the same terms (M10.4). */

/** The interval-walk setup this screen shows and applies. */
private fun Preset.intervalWalkSetup(): IntervalWalkSetup =
    (configuration as? PresetConfiguration.IntervalWalk)?.setup
        ?: throw IllegalStateException("Preset '$name' is not an interval-walk preset")

private fun Preset.melodicDictationSetup(): MelodicDictationSetup =
    (configuration as? PresetConfiguration.MelodicDictation)?.setup
        ?: throw IllegalStateException("Preset '$name' is not a melodic-dictation preset")

/** Lists saved presets and edits them for the selected kind. */
@Composable
internal fun PresetsScreen(
    presetRepository: KindAwarePresetStore,
    settingsRepository: SettingsRepository,
    kind: TrainingKind = TrainingKind.INTERVALS
) {
    val viewModel: PresetsViewModel = viewModel(
        key = "presets-${kind.id}",
        factory = PresetsViewModelFactory(presetRepository, settingsRepository, kind)
    )
    val state by viewModel.state.collectAsStateWithLifecycle()
    var renameTarget by remember { mutableStateOf<Preset?>(null) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var exportText by remember { mutableStateOf<String?>(null) }
    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        val text = exportText
        exportText = null
        if (uri != null && text != null) {
            scope.launch(Dispatchers.IO) {
                context.contentResolver.openOutputStream(uri)?.bufferedWriter()?.use {
                    it.write(text)
                }
            }
        }
    }
    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                val text = withContext(Dispatchers.IO) {
                    context.contentResolver.openInputStream(uri)?.bufferedReader()?.use {
                        it.readText()
                    }
                }
                if (text != null) viewModel.importDocument(text)
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Presets", style = MaterialTheme.typography.headlineMedium)
        state.message?.let { Text(it) }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                onClick = {
                    viewModel.exportDocument { text ->
                        exportText = text
                        exportLauncher.launch("earby-presets.json")
                    }
                }
            ) { Text("Export presets") }
            OutlinedButton(onClick = { importLauncher.launch(arrayOf("application/json")) }) {
                Text("Import presets")
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = state.sort == PresetSort.ALPHABETICAL,
                    onClick = { viewModel.setSort(PresetSort.ALPHABETICAL) },
                    label = { Text("A-Z") }
                )
                FilterChip(
                    selected = state.sort == PresetSort.RECENT,
                    onClick = { viewModel.setSort(PresetSort.RECENT) },
                    label = { Text("Recent") }
                )
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text("Details", style = MaterialTheme.typography.bodyMedium)
                Switch(
                    checked = state.showDetails,
                    onCheckedChange = viewModel::setShowDetails
                )
            }
        }

        state.presets.forEach { preset ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                // A custom slot is tinted differently from an immutable starter.
                colors = if (preset.builtIn) {
                    CardDefaults.cardColors()
                } else {
                    CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                }
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        preset.name +
                            when {
                                preset.builtIn -> " (starter)"
                                preset.empty -> " (empty)"
                                else -> ""
                            },
                        style = MaterialTheme.typography.titleMedium
                    )
                    if (state.showDetails) {
                        if (preset.empty) {
                            Text(
                                "Empty: use Replace to save the current setup here.",
                                style = MaterialTheme.typography.bodySmall
                            )
                        } else {
                            when (val configuration = preset.configuration) {
                                is PresetConfiguration.Chords -> {
                                    Text(
                                        "Qualities: ${configuration.setup.qualitiesLabel()}",
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                    Text(
                                        "Inversions: ${configuration.setup.inversionsLabel()} · " +
                                            "answer: ${configuration.setup.answerMode.englishLabel()}",
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                    Text(
                                        "Presentation: " +
                                            configuration.setup.presentation.englishLabel() +
                                            " · roots: ${configuration.setup.rootsLabel()}",
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                                is PresetConfiguration.Scales -> {
                                    Text(
                                        "Scales: ${configuration.setup.scalesLabel()} · " +
                                            "direction: " +
                                            configuration.setup.direction.englishLabel(),
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                    Text(
                                        "Roots: ${configuration.setup.rootsLabel()}",
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                                is PresetConfiguration.Notes -> {
                                    Text(
                                        "Notes: ${configuration.setup.notesLabel()}",
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                    Text(
                                        "Range: ${MidiNote.of(
                                            configuration.setup.minimumNote
                                        ).displayName()} to " +
                                            MidiNote.of(
                                                configuration.setup.maximumNote
                                            ).displayName() +
                                            " · accidentals: " +
                                            configuration.setup.accidental.englishLabel(),
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                    Text(
                                        "Octave speech: " +
                                            (
                                                if (configuration.setup.speakOctave) {
                                                    "on"
                                                } else {
                                                    "off"
                                                }
                                                ),
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                                is PresetConfiguration.ScaleDegrees -> {
                                    Text(
                                        "Keys: ${configuration.setup.keysLabel()}",
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                    Text(
                                        "Tonalities: ${configuration.setup.tonalitiesLabel()} · " +
                                            "degrees: ${configuration.setup.degreesLabel()}",
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                    Text(
                                        "Context: ${configuration.setup.context.label} · gap " +
                                            formatSeconds(configuration.setup.contextGap) + " s",
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                    Text(
                                        "Answer format: " +
                                            configuration.setup.answerFormat.englishLabel(),
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                                is PresetConfiguration.Progressions -> {
                                    Text(
                                        if (configuration.setup.fixedRoot != null) {
                                            "Fixed root: ${configuration.setup.fixedRootLabel()}"
                                        } else {
                                            "Keys: all twelve"
                                        },
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                    Text(
                                        "Chords: ${configuration.setup.chordsLabel()}",
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                    Text(
                                        "Voicing: ${configuration.setup.voicingLabel()}",
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                    Text(
                                        "Answer format: " +
                                            configuration.setup.answerFormat.englishLabel(),
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                                is PresetConfiguration.ContextualIntervals -> {
                                    Text(
                                        "Keys: ${configuration.setup.keysLabel()}",
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                    Text(
                                        "Tonalities: ${configuration.setup.tonalitiesLabel()}",
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                    Text(
                                        "Intervals: ${configuration.setup.intervalsLabel()}",
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                    Text(
                                        "Context: ${configuration.setup.context.label} · gap " +
                                            formatSeconds(configuration.setup.contextGap) + " s",
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                    Text(
                                        "Presentation: " +
                                            configuration.setup.presentation.englishLabel() +
                                            " · answer format: " +
                                            configuration.setup.answerFormat.englishLabel(),
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                                is PresetConfiguration.IntervalWalk -> {
                                    Text(
                                        "Intervals: " +
                                            configuration.setup.intervalIds.joinToString(
                                                ", "
                                            ) { id ->
                                                com.thomas.passiveeartraining.music.SimpleInterval
                                                    .ofIdOrNull(id)?.englishAnswer ?: id
                                            },
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                    Text(
                                        "Direction: " +
                                            configuration.setup.direction.name.lowercase() +
                                            " · ${configuration.setup.length} steps",
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                    Text(
                                        "Starting roots: " +
                                            configuration.setup.minimumNote.displayName() +
                                            " to " +
                                            configuration.setup.maximumNote.displayName() +
                                            (
                                                configuration.setup.fixedStart?.let {
                                                    " · fixed ${it.displayName()}"
                                                } ?: " · drawn"
                                                ),
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                    Text(
                                        "Learning mode: " +
                                            modeLabel(configuration.setup.mode),
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                                is PresetConfiguration.MelodicDictation -> {
                                    Text(
                                        "Keys: ${configuration.setup.keysLabel()}",
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                    Text(
                                        "Tonalities: " +
                                            configuration.setup.tonalitiesLabel() +
                                            " · phrase: " +
                                            "${configuration.setup.phraseLength} notes",
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                    Text(
                                        "Context: ${configuration.setup.context.label} · gap " +
                                            formatSeconds(configuration.setup.contextGap) +
                                            " s",
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                    Text(
                                        configuration.setup.endpointsLabel() +
                                            " · steps: " +
                                            configuration.setup.stepsLabel() +
                                            " · leap: ${configuration.setup.maximumLeap}",
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                    Text(
                                        "Bias: " +
                                            configuration.setup.bias.englishLabel() +
                                            " · answer format: " +
                                            configuration.setup.answerFormat.englishLabel(),
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                                is PresetConfiguration.Intervals -> {
                                    val setup = configuration.setup
                                    Text(
                                        if (setup.intervalIds.isEmpty()) {
                                            "Intervals: all (none selected)"
                                        } else {
                                            "Intervals: " +
                                                setup.intervalIds.joinToString { id ->
                                                    SimpleInterval.ofId(id).englishAnswer
                                                }
                                        },
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                    Text(
                                        "Presentation: ${setup.presentationLabel()}, " +
                                            "mode: ${setup.mode.name.lowercase().replace(
                                                '_',
                                                ' '
                                            )}",
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                            }
                        }
                    }
                    // Starters can only be used; a slot is used, renamed or refilled from the
                    // current setup. There is nothing to duplicate or delete.
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (!preset.empty) {
                            OutlinedButton(
                                onClick = { viewModel.use(preset) },
                                contentPadding = COMPACT_BUTTON_PADDING,
                                colors = darkOutlinedButtonColors()
                            ) { Text("Use") }
                        }
                        if (!preset.builtIn) {
                            OutlinedButton(
                                onClick = { renameTarget = preset },
                                contentPadding = COMPACT_BUTTON_PADDING,
                                colors = darkOutlinedButtonColors()
                            ) { Text("Rename") }
                            OutlinedButton(
                                onClick = { viewModel.saveInto(preset) },
                                contentPadding = COMPACT_BUTTON_PADDING,
                                colors = darkOutlinedButtonColors()
                            ) { Text("Replace") }
                            if (kind != TrainingKind.INTERVALS && !preset.empty) {
                                OutlinedButton(
                                    onClick = { viewModel.clear(preset) },
                                    contentPadding = COMPACT_BUTTON_PADDING,
                                    colors = darkOutlinedButtonColors()
                                ) { Text("Clear") }
                            }
                        }
                    }
                }
            }
        }
    }

    renameTarget?.let { preset ->
        NameDialog(
            title = "Rename '${preset.name}'",
            initial = preset.name,
            onConfirm = { name ->
                viewModel.rename(preset, name)
                renameTarget = null
            },
            onCancel = { renameTarget = null }
        )
    }
}

/** A one-field dialog for naming a preset or a copy. */
@Composable
private fun NameDialog(
    title: String,
    initial: String,
    onConfirm: (String) -> Unit,
    onCancel: () -> Unit
) {
    var text by remember(title) { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                label = { Text("Name") },
                singleLine = true
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(text.trim()) },
                enabled = text.isNotBlank(),
                colors = darkTextButtonColors()
            ) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onCancel, colors = darkTextButtonColors()) { Text("Cancel") }
        }
    )
}

private class PresetsViewModelFactory(
    private val presets: KindAwarePresetStore,
    private val settings: SettingsRepository,
    private val kind: TrainingKind
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        @Suppress("UNCHECKED_CAST")
        return PresetsViewModel(presets, settings, kind) as T
    }
}
