package com.thomas.passiveeartraining.app

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.thomas.passiveeartraining.training.provider.TrainingKind
import com.thomas.passiveeartraining.training.settings.ChordSetup
import com.thomas.passiveeartraining.training.settings.ChordSetupDraft
import com.thomas.passiveeartraining.training.settings.ChordSetupValidator
import com.thomas.passiveeartraining.training.settings.ModeEdit
import com.thomas.passiveeartraining.training.settings.ModePayload
import com.thomas.passiveeartraining.training.settings.ModeWriteResult
import com.thomas.passiveeartraining.training.settings.SettingsRepository
import com.thomas.passiveeartraining.training.settings.SetupValidation
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.withLock

/** Everything the chord setup screen edits, as one immutable snapshot. */
data class ChordSetupUiState(
    val draft: ChordSetupDraft,
    val advancedExpanded: Boolean,
    val loaded: Boolean,
    val saved: Boolean,
    val saveError: String? = null
) {
    /** The draft is unvalidated by design; the screen explains every issue it gets. */
    val validation: SetupValidation get() = ChordSetupValidator.validate(draft)
}

/**
 * Holds the chord setup draft while it is edited (M5).
 *
 * It mirrors the interval editor exactly: the draft is seeded once from the stored settings and
 * never overwritten while the user is editing, saving validates through the shared chord rules,
 * every valid edit autosaves after the same debounce, and a failed or conflicted save surfaces its
 * message instead of being swallowed. Sound gains belong to the Settings tab.
 */
class ChordSetupViewModel(
    private val settingsRepository: SettingsRepository,
    private val kind: TrainingKind = TrainingKind.CHORDS,
    private val saveScope: kotlinx.coroutines.CoroutineScope? = null
) : ViewModel() {

    init {
        require(kind == TrainingKind.CHORDS) {
            "The chord setup editor is only used for the chords kind"
        }
    }

    private data class Editable(
        val draft: ChordSetupDraft = ChordSetupDraft.from(ChordSetup.default()),
        val advancedExpanded: Boolean = false
    )

    private val editable = MutableStateFlow(Editable())
    private val loaded = MutableStateFlow(false)
    private val saved = MutableStateFlow(false)
    private val saveError = MutableStateFlow<String?>(null)
    private val saveMutex = kotlinx.coroutines.sync.Mutex()
    private var edited = false

    /** The chords revision the last snapshot carried; captured by every autosave. */
    private var setupRevision: Long = 1L

    /** Pending debounced autosave; replaced by every edit while the user keeps changing values. */
    private var autosaveJob: kotlinx.coroutines.Job? = null

    val state: StateFlow<ChordSetupUiState> = combine(editable, loaded, saved, saveError) {
            edit,
            isLoaded,
            isSaved,
            error
        ->
        ChordSetupUiState(
            draft = edit.draft,
            advancedExpanded = edit.advancedExpanded,
            loaded = isLoaded,
            saved = isSaved,
            saveError = error
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.Eagerly,
        ChordSetupUiState(
            draft = ChordSetupDraft.from(ChordSetup.default()),
            advancedExpanded = false,
            loaded = false,
            saved = false
        )
    )

    init {
        viewModelScope.launch {
            settingsRepository.settings.collect { snapshot ->
                val stored = snapshot.settings
                // Seed once, then follow changes made elsewhere: applying a preset must not leave
                // the setup page showing the previous configuration. A pending edit is never
                // clobbered; it publishes its own value shortly.
                setupRevision = stored.modeSetups.getValue(kind).revision
                val external = ChordSetupDraft.from(stored.chordSetup) != editable.value.draft
                if (!loaded.value || (!edited && external)) {
                    editable.value = Editable(
                        draft = ChordSetupDraft.from(stored.chordSetup),
                        advancedExpanded = stored.advancedPanelExpanded
                    )
                    edited = false
                }
                loaded.value = true
            }
        }
    }

    fun editDraft(transform: (ChordSetupDraft) -> ChordSetupDraft) {
        markEdited()
        editable.update { it.copy(draft = transform(it.draft)) }
        scheduleAutosave()
    }

    fun setAdvancedExpanded(expanded: Boolean) {
        markEdited()
        editable.update { it.copy(advancedExpanded = expanded) }
        scheduleAutosave()
    }

    /** Saves the whole edited configuration now when the shared validator accepts it. */
    fun save() {
        autosaveJob?.cancel()
        (saveScope ?: viewModelScope).launch { saveNow() }
    }

    /**
     * Saves the current valid draft shortly after the last edit.
     *
     * A draft that is still invalid is not written, and the screen keeps showing what to fix.
     */
    private fun scheduleAutosave() {
        autosaveJob?.cancel()
        autosaveJob = viewModelScope.launch {
            kotlinx.coroutines.delay(AUTOSAVE_DELAY_MILLIS)
            saveNow()
        }
    }

    private suspend fun saveNow() = saveMutex.withLock {
        if (!loaded.value || !edited) return@withLock
        val current = editable.value
        if (ChordSetupValidator.validate(current.draft) !is SetupValidation.Valid) return@withLock
        try {
            val result = settingsRepository.updateMode(
                ModeEdit(kind, setupRevision) {
                    ModePayload.Chords(current.draft.toSetup())
                },
                globalChange = { it.copy(advancedPanelExpanded = current.advancedExpanded) }
            )
            when (result) {
                is ModeWriteResult.Saved -> {
                    setupRevision = result.snapshot.settings
                        .modeSetups.getValue(kind).revision
                    saved.value = true
                    // The draft now matches the store, so a later external change (an applied
                    // preset) is detected as external and refreshes the page.
                    saveError.value = null
                    if (editable.value == current) {
                        edited = false
                    }
                }
                is ModeWriteResult.Conflict -> {
                    saved.value = false
                    saveError.value =
                        "This setup changed elsewhere; the newest version will be shown."
                    edited = false
                }
                is ModeWriteResult.Rejected -> {
                    saved.value = false
                    saveError.value = result.message
                }
            }
        } catch (
            failure: kotlinx.coroutines.CancellationException
        ) {
            throw failure
        } catch (failure: Exception) {
            saved.value = false
            saveError.value = failure.message ?: "The setup could not be saved"
        }
    }

    private fun markEdited() {
        edited = true
        saved.value = false
    }

    private companion object {
        /** Short enough that leaving the screen never loses an edit, long enough to debounce. */
        const val AUTOSAVE_DELAY_MILLIS = 600L
    }
}
