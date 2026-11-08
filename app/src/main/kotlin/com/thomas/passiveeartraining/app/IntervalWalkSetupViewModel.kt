package com.thomas.passiveeartraining.app

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.thomas.passiveeartraining.training.provider.TrainingKind
import com.thomas.passiveeartraining.training.settings.IntervalWalkSetup
import com.thomas.passiveeartraining.training.settings.IntervalWalkSetupDraft
import com.thomas.passiveeartraining.training.settings.IntervalWalkSetupValidator
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
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Everything the interval-walk setup screen edits. */
data class IntervalWalkSetupUiState(
    val draft: IntervalWalkSetupDraft,
    val loaded: Boolean,
    val saved: Boolean,
    val saveError: String? = null
) {
    val validation: SetupValidation
        get() = IntervalWalkSetupValidator.validate(draft)
}

/** Holds the interval-walk setup draft and autosaves every valid edit. */
class IntervalWalkSetupViewModel(
    private val settingsRepository: SettingsRepository,
    private val kind: TrainingKind = TrainingKind.INTERVAL_WALK,
    private val saveScope: kotlinx.coroutines.CoroutineScope? = null
) : ViewModel() {

    init {
        require(kind == TrainingKind.INTERVAL_WALK) {
            "The interval-walk editor is only used for that kind"
        }
    }

    private val editable =
        MutableStateFlow(IntervalWalkSetupDraft.from(IntervalWalkSetup.default()))
    private val loaded = MutableStateFlow(false)
    private val saved = MutableStateFlow(false)
    private val saveError = MutableStateFlow<String?>(null)
    private val saveMutex = Mutex()
    private var edited = false
    private var setupRevision: Long = 1L
    private var autosaveJob: kotlinx.coroutines.Job? = null

    val state: StateFlow<IntervalWalkSetupUiState> =
        combine(editable, loaded, saved, saveError) { draft, isLoaded, isSaved, error ->
            IntervalWalkSetupUiState(
                draft = draft,
                loaded = isLoaded,
                saved = isSaved,
                saveError = error
            )
        }.stateIn(
            viewModelScope,
            SharingStarted.Eagerly,
            IntervalWalkSetupUiState(
                draft = IntervalWalkSetupDraft.from(IntervalWalkSetup.default()),
                loaded = false,
                saved = false
            )
        )

    init {
        viewModelScope.launch {
            settingsRepository.settings.collect { snapshot ->
                val stored = snapshot.settings
                setupRevision = stored.modeSetups.getValue(kind).revision
                val external =
                    IntervalWalkSetupDraft.from(stored.intervalWalkSetup) != editable.value
                if (!loaded.value || (!edited && external)) {
                    editable.value = IntervalWalkSetupDraft.from(stored.intervalWalkSetup)
                    edited = false
                }
                loaded.value = true
            }
        }
    }

    fun editDraft(transform: (IntervalWalkSetupDraft) -> IntervalWalkSetupDraft) {
        edited = true
        saved.value = false
        editable.update(transform)
        scheduleAutosave()
    }

    fun save() {
        autosaveJob?.cancel()
        (saveScope ?: viewModelScope).launch { saveNow() }
    }

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
        if (IntervalWalkSetupValidator.validate(current) !is SetupValidation.Valid) return@withLock
        try {
            when (
                val result = settingsRepository.updateMode(
                    ModeEdit(kind, setupRevision) {
                        ModePayload.IntervalWalk(current.toSetup())
                    }
                )
            ) {
                is ModeWriteResult.Saved -> {
                    setupRevision = result.snapshot.settings
                        .modeSetups.getValue(kind).revision
                    saved.value = true
                    saveError.value = null
                    if (editable.value == current) edited = false
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
        } catch (cancelled: kotlinx.coroutines.CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            saved.value = false
            saveError.value = failure.message ?: "The setup could not be saved"
        }
    }

    private companion object {
        const val AUTOSAVE_DELAY_MILLIS = 600L
    }
}
