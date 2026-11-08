package com.thomas.passiveeartraining.app

import com.thomas.passiveeartraining.data.preset.ImportPreview
import com.thomas.passiveeartraining.data.preset.ImportResult
import com.thomas.passiveeartraining.data.preset.Preset
import com.thomas.passiveeartraining.data.preset.PresetConfiguration
import com.thomas.passiveeartraining.data.preset.PresetId
import com.thomas.passiveeartraining.data.preset.PresetSlots
import com.thomas.passiveeartraining.data.preset.PresetStarters
import com.thomas.passiveeartraining.music.ChordPresentation
import com.thomas.passiveeartraining.music.DiatonicChord
import com.thomas.passiveeartraining.music.IntervalPresentation
import com.thomas.passiveeartraining.music.ProgressionVoicingMode
import com.thomas.passiveeartraining.music.ScaleDirection
import com.thomas.passiveeartraining.music.SpellingPreference as MusicSpellingPreference
import com.thomas.passiveeartraining.music.Tonality
import com.thomas.passiveeartraining.training.ChordAnswerMode
import com.thomas.passiveeartraining.training.ContextualIntervalAnswerFormat
import com.thomas.passiveeartraining.training.DegreeAnswerFormat
import com.thomas.passiveeartraining.training.MelodicAnswerFormat
import com.thomas.passiveeartraining.training.MelodicDirectionBias
import com.thomas.passiveeartraining.training.ProgressionAnswerFormat
import com.thomas.passiveeartraining.training.context.ContextKind
import com.thomas.passiveeartraining.training.provider.TrainingKind
import com.thomas.passiveeartraining.training.recipe.RecipeMode
import com.thomas.passiveeartraining.training.recipe.SessionStopPolicy
import com.thomas.passiveeartraining.training.settings.AnswerVisibility
import com.thomas.passiveeartraining.training.settings.ChordSetup
import com.thomas.passiveeartraining.training.settings.ContextualIntervalSetup
import com.thomas.passiveeartraining.training.settings.LastSetup
import com.thomas.passiveeartraining.training.settings.MelodicDictationSetup
import com.thomas.passiveeartraining.training.settings.ModeEdit
import com.thomas.passiveeartraining.training.settings.ModePayload
import com.thomas.passiveeartraining.training.settings.NoteSetup
import com.thomas.passiveeartraining.training.settings.ProgressionSetup
import com.thomas.passiveeartraining.training.settings.SavedModeSetup
import com.thomas.passiveeartraining.training.settings.ScaleDegreeSetup
import com.thomas.passiveeartraining.training.settings.ScaleSetup
import com.thomas.passiveeartraining.training.settings.SettingsRepository
import com.thomas.passiveeartraining.training.settings.SettingsSnapshot
import com.thomas.passiveeartraining.training.settings.SetupField
import com.thomas.passiveeartraining.training.settings.SetupValidation
import com.thomas.passiveeartraining.training.settings.ThemePreference
import com.thomas.passiveeartraining.training.settings.UserSettings
import kotlin.time.Duration
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** An in-memory settings store with the same change contract as the real repository. */
private class FakeSettingsRepository(initial: UserSettings = UserSettings.DEFAULTS) :
    SettingsRepository {
    private val state = MutableStateFlow(SettingsSnapshot(initial))

    val current: UserSettings get() = state.value.settings

    override val settings: Flow<SettingsSnapshot> = state

    var writeFailure: Exception? = null

    override suspend fun update(change: (UserSettings) -> UserSettings): UserSettings {
        writeFailure?.let { throw it }
        val updated = change(state.value.settings)
        state.value = SettingsSnapshot(updated)
        return updated
    }

    override suspend fun updateMode(
        edit: com.thomas.passiveeartraining.training.settings.ModeEdit,
        globalChange: ((UserSettings) -> UserSettings)?
    ): com.thomas.passiveeartraining.training.settings.ModeWriteResult {
        writeFailure?.let { throw it }
        val current = state.value
        val existing = current.settings.modeSetups[edit.kind]
            ?: return com.thomas.passiveeartraining.training.settings.ModeWriteResult.Rejected(
                "no setup"
            )
        if (existing.revision != edit.expectedRevision) {
            return com.thomas.passiveeartraining.training.settings.ModeWriteResult.Conflict(
                current
            )
        }
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
    ): com.thomas.passiveeartraining.training.settings.ModeWriteResult {
        var snapshot = state.value
        for (edit in edits) {
            val result = updateMode(edit, globalChange)
            if (result !is com.thomas.passiveeartraining.training.settings.ModeWriteResult.Saved) {
                return result
            }
            snapshot = result.snapshot
        }
        val updated = snapshot.settings.copy(selectedKind = selectedKind)
        state.value = SettingsSnapshot(updated)
        return com.thomas.passiveeartraining.training.settings.ModeWriteResult.Saved(
            SettingsSnapshot(updated)
        )
    }

    override suspend fun selectKind(
        kind: com.thomas.passiveeartraining.training.provider.TrainingKind
    ): com.thomas.passiveeartraining.training.settings.ModeWriteResult {
        writeFailure?.let { throw it }
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

/** An in-memory preset store recording the operations the screen asked for. */
private class FakePresetRepository(initial: List<Preset>) : KindAwarePresetStore {
    private val state = MutableStateFlow(initial)
    var duplicated: PresetId? = null
    var deleted: PresetId? = null
    var exportedIds: List<PresetId> = emptyList()
    var lastReplaceIds: Set<PresetId> = emptySet()
    var staged: ImportPreview = ImportPreview(emptyList())
    var commitResult: ImportResult = ImportResult(emptyList(), emptyList())
    var created: Pair<String, PresetConfiguration>? = null
    var updated: Preset? = null
    var renamed: Pair<PresetId, String>? = null

    override val presets: Flow<List<Preset>> = state

    var refreshFailure: Exception? = null
    override suspend fun refresh() {
        refreshFailure?.let { throw it }
    }

    val usedIds = mutableListOf<PresetId>()
    override suspend fun markUsed(id: PresetId) {
        usedIds += id
        val next = (state.value.maxOfOrNull { it.lastUsedAtEpochMs } ?: 0L) + 1
        state.value =
            state.value.map { if (it.id == id) it.copy(lastUsedAtEpochMs = next) else it }
    }

    override suspend fun starterOf(kind: TrainingKind): Preset? =
        state.value.firstOrNull { it.configuration.kind == kind && it.builtIn }

    override suspend fun slotsOf(kind: TrainingKind): List<Preset> = state.value.filter {
        it.configuration.kind == kind && it.id in PresetSlots.slotIds(kind)
    }

    override suspend fun create(
        name: String,
        configuration: PresetConfiguration,
        seed: Long?,
        musicGain: Float,
        voiceGain: Float
    ): Preset {
        created = name to configuration
        val preset = Preset(
            id = PresetId("created-${state.value.size}"),
            name = name.trim(),
            createdAtEpochMs = 0L,
            updatedAtEpochMs = 0L,
            configuration = configuration,
            seed = seed,
            musicGain = musicGain,
            voiceGain = voiceGain,
            builtIn = false
        )
        state.value = state.value + preset
        return preset
    }

    override suspend fun update(preset: Preset): Preset {
        updated = preset
        state.value =
            state.value.map { if (it.id == preset.id) preset.copy(builtIn = false) else it }
        return preset
    }

    override suspend fun rename(id: PresetId, name: String): Preset {
        renamed = id to name
        val renamedPreset = state.value.first { it.id == id }.copy(name = name.trim())
        state.value = state.value.map { if (it.id == id) renamedPreset else it }
        return renamedPreset
    }

    override suspend fun duplicate(id: PresetId, name: String?): Preset {
        duplicated = id
        val original = state.value.first { it.id == id }
        val copy = original.copy(
            id = PresetId("copy-${state.value.size}"),
            name = name ?: "${original.name} copy",
            builtIn = false
        )
        state.value = state.value + copy
        return copy
    }

    override suspend fun delete(id: PresetId) {
        deleted = id
        state.value = state.value.filterNot { it.id == id }
    }

    override suspend fun clear(id: PresetId): Preset {
        val cleared = state.value.first { it.id == id }.copy(empty = true)
        state.value = state.value.map { if (it.id == id) cleared else it }
        return cleared
    }

    override suspend fun previewImport(text: String): ImportPreview = staged

    override suspend fun commitImport(
        preview: ImportPreview,
        replaceExistingIds: Set<PresetId>
    ): ImportResult {
        lastReplaceIds = replaceExistingIds
        return commitResult
    }

    override suspend fun export(ids: Collection<PresetId>): String {
        exportedIds = ids.toList()
        return "exported:${ids.size}"
    }
}

private fun samplePreset() = Preset(
    id = PresetId("11111111-2222-3333-4444-555555555555"),
    name = "Thirds",
    createdAtEpochMs = 1L,
    updatedAtEpochMs = 2L,
    configuration = PresetConfiguration.Intervals(LastSetup.default())
)

/** A chord preset this suite applies and lists. */
private fun sampleChordPreset() = Preset(
    id = PresetId("chord-preset-1"),
    name = "Seventh chords",
    createdAtEpochMs = 1L,
    updatedAtEpochMs = 2L,
    configuration = PresetConfiguration.Chords(
        ChordSetup.default().copy(
            qualityIds = listOf("major_seventh"),
            inversions = listOf(0, 1, 2, 3),
            seed = 42L
        )
    ),
    seed = 42L
)

/** A scale preset this suite applies and lists. */
private fun sampleScalePreset() = Preset(
    id = PresetId("scale-preset-1"),
    name = "Modes",
    createdAtEpochMs = 1L,
    updatedAtEpochMs = 2L,
    configuration = PresetConfiguration.Scales(
        ScaleSetup.default().copy(
            scaleIds = listOf("dorian", "mixolydian"),
            direction = ScaleDirection.BOTH,
            seed = 42L
        )
    ),
    seed = 42L
)

/** A note preset this suite applies and lists. */
private fun sampleNotePreset() = Preset(
    id = PresetId("note-preset-1"),
    name = "Upper octave",
    createdAtEpochMs = 1L,
    updatedAtEpochMs = 2L,
    configuration = PresetConfiguration.Notes(
        NoteSetup.default().copy(
            pitchClassValues = listOf(0, 4, 7),
            accidental = MusicSpellingPreference.FLAT,
            speakOctave = true,
            seed = 42L
        )
    ),
    seed = 42L
)

/** A scale-degree preset this suite applies and lists. */
private fun sampleDegreePreset() = Preset(
    id = PresetId("degree-preset-1"),
    name = "A minor degrees",
    createdAtEpochMs = 1L,
    updatedAtEpochMs = 2L,
    configuration = PresetConfiguration.ScaleDegrees(
        ScaleDegreeSetup.default().copy(
            keyValues = listOf(9),
            tonalities = listOf(Tonality.NATURAL_MINOR),
            degrees = listOf(1, 5, 7),
            context = ContextKind.TONIC_DOMINANT_TONIC,
            answerFormat = DegreeAnswerFormat.NOTE_NAME,
            seed = 42L
        )
    ),
    seed = 42L
)

/** A chord-progression preset this suite applies and lists. */
private fun sampleProgressionPreset() = Preset(
    id = PresetId("progression-preset-1"),
    name = "Tonic cadence",
    createdAtEpochMs = 1L,
    updatedAtEpochMs = 2L,
    configuration = PresetConfiguration.Progressions(
        ProgressionSetup.default().copy(
            chordIds = listOf(
                DiatonicChord.I.id,
                DiatonicChord.IV.id,
                DiatonicChord.V.id,
                DiatonicChord.V7.id
            ),
            voicing = ProgressionVoicingMode.NEAREST_INVERSION,
            allowedInversions = listOf(0, 1),
            answerFormat = ProgressionAnswerFormat.ROOT_MOTION,
            seed = 42L
        )
    ),
    seed = 42L
)

/** A contextual-interval preset this suite applies and lists (M9B). */
private fun sampleContextualIntervalPreset() = Preset(
    id = PresetId("context-interval-preset-1"),
    name = "Fifth over the cadence",
    createdAtEpochMs = 1L,
    updatedAtEpochMs = 2L,
    configuration = PresetConfiguration.ContextualIntervals(
        ContextualIntervalSetup.default().copy(
            keyValues = listOf(9),
            tonalities = listOf(Tonality.NATURAL_MINOR),
            intervalIds = listOf("perfect_fifth"),
            presentation = IntervalPresentation.HARMONIC,
            context = ContextKind.TONIC_DOMINANT_TONIC,
            answerFormat = ContextualIntervalAnswerFormat.INTERVAL_AND_DEGREES,
            seed = 42L
        )
    ),
    seed = 42L
)

/** A melodic-dictation preset this suite applies and lists (M10). */
private fun sampleMelodicDictationPreset() = Preset(
    id = PresetId("melodic-dictation-preset-1"),
    name = "Wandering phrase",
    createdAtEpochMs = 1L,
    updatedAtEpochMs = 2L,
    configuration = PresetConfiguration.MelodicDictation(
        MelodicDictationSetup.default().copy(
            keyValues = listOf(2),
            tonalities = listOf(Tonality.MAJOR),
            context = ContextKind.TONIC_TRIAD,
            phraseLength = 6,
            startDegree = 1,
            endDegree = 1,
            allowedSteps = listOf(-4, -2, 0, 2, 4),
            maximumLeap = 4,
            bias = MelodicDirectionBias.ASCENDING,
            answerFormat = MelodicAnswerFormat.DIRECTED_INTERVAL_SEQUENCE,
            seed = 42L
        )
    ),
    seed = 42L
)

/** The intervals setup of a preset this suite inspects. */
private val Preset.intervalsSetup: LastSetup
    get() = (configuration as PresetConfiguration.Intervals).setup

@OptIn(ExperimentalCoroutinesApi::class)
class ViewModelTest {

    @Before
    fun installMainDispatcher() {
        // viewModelScope runs on Dispatchers.Main; a host test supplies one.
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun removeMainDispatcher() {
        Dispatchers.resetMain()
    }

    @Test
    fun presetRefreshFailureIsVisible() = runTest {
        val repository = FakePresetRepository(emptyList())
        repository.refreshFailure = java.io.IOException("preset read failed")
        val viewModel = PresetsViewModel(repository, FakeSettingsRepository())
        advanceUntilIdle()
        assertEquals("preset read failed", viewModel.state.value.message)
    }

    @Test
    fun presetSaveSettingsReadFailureIsVisible() = runTest {
        val settings = object : SettingsRepository by FakeSettingsRepository() {
            override val settings: Flow<SettingsSnapshot> = kotlinx.coroutines.flow.flow {
                throw java.io.IOException("setup read failed")
            }
        }
        val preset = samplePreset()
        val viewModel = PresetsViewModel(FakePresetRepository(listOf(preset)), settings)
        viewModel.saveInto(preset)
        advanceUntilIdle()
        assertEquals("setup read failed", viewModel.state.value.message)
    }

    @Test
    fun cancelledPresetWritesAreNotReportedAsStorageFailures() = runTest {
        val settings = FakeSettingsRepository()
        settings.writeFailure = kotlinx.coroutines.CancellationException("cancelled write")
        val preset = samplePreset()
        val viewModel = PresetsViewModel(FakePresetRepository(listOf(preset)), settings)
        viewModel.use(preset)
        advanceUntilIdle()
        assertEquals(null, viewModel.state.value.message)
    }

    @Test
    fun rejectedModeChangesReportTheErrorAndKeepTheSavedMode() = runTest {
        val settings = FakeSettingsRepository()
        settings.writeFailure = java.io.IOException("mode write failed")
        assertEquals(
            "mode write failed",
            saveTrainingMode(
                settings,
                TrainingKind.CHORDS
            )
        )
        assertEquals(UserSettings.DEFAULTS.lastMode, settings.current.lastMode)
    }

    @Test
    fun settingsWriteFailureIsVisibleAndCanBeRetried() = runTest {
        val repository = FakeSettingsRepository()
        val viewModel = SettingsViewModel(repository)
        repository.writeFailure = IllegalStateException("settings disk full")
        viewModel.setAnswerVisibility(AnswerVisibility.HIDDEN)
        advanceUntilIdle()
        assertTrue(viewModel.state.value.diagnostics.any { it.contains("settings disk full") })
        assertEquals(AnswerVisibility.SHOWN_WHEN_CONSUMED, repository.current.answerVisibility)
        repository.writeFailure = null
        viewModel.setAnswerVisibility(AnswerVisibility.HIDDEN)
        advanceUntilIdle()
        assertEquals(AnswerVisibility.HIDDEN, repository.current.answerVisibility)
    }

    @Test
    fun invalidDraftSurvivesAnUnrelatedSettingsEmission() = runTest {
        val repository = FakeSettingsRepository()
        val viewModel = SetupViewModel(repository)
        viewModel.state.first { it.loaded }
        viewModel.editDraft { it.copy(noteDuration = Duration.ZERO) }
        repository.update { it.copy(musicGain = 0.5f) }
        assertEquals(Duration.ZERO, viewModel.state.value.draft.noteDuration)
    }

    @Test
    fun failedSetupWritesRemainUnsavedAndCanBeRetried() = runTest {
        val repository = FakeSettingsRepository()
        val viewModel = SetupViewModel(repository)
        viewModel.state.first { it.loaded }
        repository.writeFailure = java.io.IOException("disk full")
        viewModel.editDraft { it.copy(replayCount = 1) }
        viewModel.save()
        assertFalse(viewModel.state.value.saved)
        repository.writeFailure = null
        viewModel.save()
        assertTrue(viewModel.state.value.saved)
    }

    @Test
    fun theSetupDraftIsSeededFromTheStoredSettings() = runTest {
        val stored = LastSetup.default().copy(replayCount = 3, mode = RecipeMode.EXPOSURE)
        val repository = FakeSettingsRepository(
            UserSettings.DEFAULTS.copy(
                modeSetups = mapOf(
                    TrainingKind.INTERVALS to SavedModeSetup(
                        1L,
                        ModePayload.Intervals(stored)
                    )
                ),
                advancedPanelExpanded = true
            )
        )
        val viewModel = SetupViewModel(repository)

        val state = viewModel.state.first { it.loaded }

        assertEquals(3, state.draft.replayCount)
        assertEquals(RecipeMode.EXPOSURE, state.draft.mode)
        assertTrue("the advanced panel preference is restored", state.advancedExpanded)
    }

    @Test
    fun savingWritesTheValidatedDraftWithoutTouchingTheGains() = runTest {
        val repository = FakeSettingsRepository()
        val viewModel = SetupViewModel(repository)
        viewModel.state.first { it.loaded }

        viewModel.editDraft { it.copy(thinkingSilence = Duration.parse("3s")) }
        viewModel.save()

        assertEquals(Duration.parse("3s"), repository.current.lastSetup.thinkingSilence)
        assertEquals(
            "sound gains belong to Settings and are not changed by a setup save",
            UserSettings.DEFAULT_MUSIC_GAIN,
            repository.current.musicGain,
            0f
        )
        assertEquals(
            UserSettings.DEFAULT_VOICE_GAIN,
            repository.current.voiceGain,
            0f
        )
        assertTrue("the screen can confirm the save", viewModel.state.value.saved)
    }

    @Test
    fun anInvalidDraftIsNeverSaved() = runTest {
        val repository = FakeSettingsRepository()
        val viewModel = SetupViewModel(repository)
        viewModel.state.first { it.loaded }

        viewModel.editDraft { it.copy(noteDuration = kotlin.time.Duration.ZERO) }

        assertTrue(viewModel.state.value.validation is SetupValidation.Invalid)
        viewModel.save()
        assertEquals(UserSettings.DEFAULTS.lastSetup, repository.current.lastSetup)
    }

    @Test
    fun settingsChangesAndResetUseTheRepository() = runTest {
        val repository = FakeSettingsRepository()
        val viewModel = SettingsViewModel(repository)
        viewModel.state.first { it.loaded }

        viewModel.setTheme(ThemePreference.DARK)

        assertEquals(ThemePreference.DARK, repository.current.theme)

        viewModel.resetToDefaults()
        assertEquals("reset restores every default", UserSettings.DEFAULTS, repository.current)
    }

    @Test
    fun gainWritesAreCoalescedAndTheFinalReleaseIsFlushed() = runTest {
        val repository = FakeSettingsRepository()
        val viewModel = SettingsViewModel(repository)
        viewModel.state.first { it.loaded }

        viewModel.setGains(0.4f, 0.3f)
        advanceTimeBy(40)
        assertEquals(
            "a drag writes only after the debounce interval",
            UserSettings.DEFAULTS.musicGain,
            repository.current.musicGain
        )
        viewModel.setGains(0.6f, 0.5f)
        advanceTimeBy(90)
        assertEquals(0.6f, repository.current.musicGain)
        assertEquals(0.5f, repository.current.voiceGain)

        viewModel.commitGains(0.7f, 0.8f)
        advanceUntilIdle()
        assertEquals(0.7f, repository.current.musicGain)
        assertEquals(0.8f, repository.current.voiceGain)
    }

    @Test
    fun intervalWalkEditsAutosaveIntoTheWalkSetup() = runTest {
        val repository = FakeSettingsRepository()
        val viewModel = IntervalWalkSetupViewModel(repository)
        viewModel.state.first { it.loaded }

        viewModel.editDraft {
            it.copy(
                length = 6,
                direction = com.thomas.passiveeartraining.training.WalkDirection.MIXED
            )
        }
        advanceUntilIdle()

        assertEquals(6, repository.current.intervalWalkSetup.length)
        assertEquals(
            com.thomas.passiveeartraining.training.WalkDirection.MIXED,
            repository.current.intervalWalkSetup.direction
        )
    }

    @Test
    fun applyingAPresetWritesItsConfigurationIntoTheSetup() = runTest {
        val preset = samplePreset()
        val presets = FakePresetRepository(listOf(preset))
        val settings = FakeSettingsRepository()
        val viewModel = PresetsViewModel(presets, settings)
        viewModel.state.first { it.loaded }

        viewModel.use(preset)

        assertEquals(preset.intervalsSetup.copy(seed = null), settings.current.lastSetup)
        assertNull(settings.current.lastSetup.seed)
        assertEquals(preset.musicGain, settings.current.musicGain, 0f)
    }

    @Test
    fun theSortChoiceOrdersEveryCard() = runTest {
        val starter = samplePreset().copy(
            id = PresetId("starter"),
            name = "Starter",
            builtIn = true,
            updatedAtEpochMs = 999L
        )
        val zeta = samplePreset().copy(
            id = PresetId("zeta"),
            name = "Zeta",
            updatedAtEpochMs = 500L,
            lastUsedAtEpochMs = 300L
        )
        val beta = samplePreset().copy(
            id = PresetId("beta"),
            name = "Beta",
            updatedAtEpochMs = 100L,
            lastUsedAtEpochMs = 300L
        )
        val alpha = samplePreset().copy(
            id = PresetId("alpha"),
            name = "Alpha",
            updatedAtEpochMs = 700L,
            lastUsedAtEpochMs = 200L
        )
        val viewModel =
            PresetsViewModel(
                FakePresetRepository(listOf(starter, zeta, beta, alpha)),
                FakeSettingsRepository()
            )
        viewModel.state.first { it.loaded }

        assertEquals(
            "the whole list is most-recently-used first; saved times never reorder it and " +
                "equal use times break A to Z",
            listOf("Beta", "Zeta", "Alpha", "Starter"),
            viewModel.state.value.presets.map { it.name }
        )

        viewModel.setSort(PresetSort.ALPHABETICAL)
        assertEquals(
            "the whole list is alphabetical, starters included",
            listOf("Alpha", "Beta", "Starter", "Zeta"),
            viewModel.state.value.presets.map { it.name }
        )

        viewModel.setShowDetails(true)
        assertTrue(viewModel.state.value.showDetails)
    }

    @Test
    fun usingAPresetMovesItToTheFrontOfRecent() = runTest {
        val first = samplePreset().copy(
            id = PresetId("first"),
            name = "First",
            lastUsedAtEpochMs = 500L
        )
        val second = samplePreset().copy(
            id = PresetId("second"),
            name = "Second",
            lastUsedAtEpochMs = 100L
        )
        val store = FakePresetRepository(listOf(first, second))
        val viewModel = PresetsViewModel(store, FakeSettingsRepository())
        viewModel.state.first { it.loaded }

        assertEquals(
            "the store's use times order the list before anything is used here",
            listOf("First", "Second"),
            viewModel.state.value.presets.map { it.name }
        )

        viewModel.use(second)
        viewModel.state.first { it.presets.firstOrNull()?.id == second.id }

        assertEquals(
            "applying a preset makes it the most recent one",
            listOf("Second", "First"),
            viewModel.state.value.presets.map { it.name }
        )
        assertEquals(listOf(second.id), store.usedIds)
    }

    @Test
    fun aMixedPresentationReadsAsRandomUpDown() {
        val mixed = LastSetup.default().copy(
            presentations = listOf(
                IntervalPresentation.ASCENDING,
                IntervalPresentation.DESCENDING
            )
        )
        assertEquals("random up/down", mixed.presentationLabel())
        assertEquals("ascending", LastSetup.default().presentationLabel())
        assertEquals(
            "descending",
            LastSetup.default()
                .copy(presentation = IntervalPresentation.DESCENDING)
                .presentationLabel()
        )
    }

    @Test
    fun theSleepCountdownFormatsMinutesAndSeconds() {
        assertEquals("0:00", formatCountdown(0))
        assertEquals("0:05", formatCountdown(5_000))
        assertEquals("1:00", formatCountdown(60_000))
        assertEquals("4:59", formatCountdown(299_000))
        assertEquals("180:00", formatCountdown(10_800_000))
        assertEquals("0:00", formatCountdown(-5))
    }

    @Test
    fun anEditIsAutosavedShortlyAfterItBecomesValid() = runTest {
        val repository = FakeSettingsRepository()
        val viewModel = SetupViewModel(repository)
        viewModel.state.first { it.loaded }

        viewModel.editDraft { it.copy(replayCount = 4) }
        advanceUntilIdle()

        assertEquals(
            "the autosave wrote the valid draft",
            4,
            repository.current.lastSetup.replayCount
        )
        assertTrue("the screen reports the automatic save", viewModel.state.value.saved)
    }

    @Test
    fun anExternallyAppliedSetupRefreshesTheDraft() = runTest {
        val repository = FakeSettingsRepository()
        val viewModel = SetupViewModel(repository)
        viewModel.state.first { it.loaded }

        // Applying a preset on another screen changes the stored setup; the draft must follow so
        // the setup page cannot show the previous configuration.
        repository.updateMode(
            ModeEdit(TrainingKind.INTERVALS, 1L) { existing ->
                val current = existing.payload as ModePayload.Intervals
                ModePayload.Intervals(
                    current.setup.copy(replayCount = 5, mode = RecipeMode.EXPOSURE)
                )
            }
        )
        advanceUntilIdle()

        assertEquals(RecipeMode.EXPOSURE, viewModel.state.value.draft.mode)
        assertEquals(5, viewModel.state.value.draft.replayCount)
    }

    @Test
    fun anInvalidEditIsNeverAutosaved() = runTest {
        val repository = FakeSettingsRepository()
        val viewModel = SetupViewModel(repository)
        viewModel.state.first { it.loaded }

        viewModel.editDraft { it.copy(noteDuration = kotlin.time.Duration.ZERO) }
        advanceUntilIdle()

        assertEquals(
            "the stored setup keeps its previous note length",
            LastSetup.default().noteDuration,
            repository.current.lastSetup.noteDuration
        )
    }

    @Test
    fun theReviewedMusicGainIsFiftyPercentOfItsSlider() {
        assertEquals(
            "50%",
            setupGain(UserSettings.DEFAULT_MUSIC_GAIN, UserSettings.DEFAULT_MUSIC_GAIN)
        )
        assertEquals(
            "100%",
            setupGain(2f * UserSettings.DEFAULT_MUSIC_GAIN, UserSettings.DEFAULT_MUSIC_GAIN)
        )
        assertEquals(
            "the reviewed level stays at the slider middle",
            0.5f,
            gainPosition(UserSettings.DEFAULT_MUSIC_GAIN, UserSettings.DEFAULT_MUSIC_GAIN),
            0.001f
        )
    }

    @Test
    fun theReviewedGainsSitAtTheMiddleOfTheirSliders() {
        assertEquals(
            0.5f,
            gainPosition(UserSettings.DEFAULT_MUSIC_GAIN, UserSettings.DEFAULT_MUSIC_GAIN),
            0.001f
        )
        assertEquals(
            0.5f,
            gainPosition(UserSettings.DEFAULT_VOICE_GAIN, UserSettings.DEFAULT_VOICE_GAIN),
            0.001f
        )
        assertEquals(0f, gainPosition(0f, UserSettings.DEFAULT_VOICE_GAIN), 0.001f)
        assertEquals(
            1f,
            gainPosition(UserSettings.MAX_GAIN, UserSettings.DEFAULT_VOICE_GAIN),
            0.001f
        )
        assertEquals(
            UserSettings.DEFAULT_VOICE_GAIN,
            gainFromPosition(0.5f, UserSettings.DEFAULT_VOICE_GAIN),
            0.001f
        )
        assertEquals(0f, gainFromPosition(0f, UserSettings.DEFAULT_VOICE_GAIN), 0.001f)
        assertEquals(
            2f * UserSettings.DEFAULT_VOICE_GAIN,
            gainFromPosition(1f, UserSettings.DEFAULT_VOICE_GAIN),
            0.001f
        )
        assertEquals(
            "the music slider top is twice the reviewed level",
            2f * UserSettings.DEFAULT_MUSIC_GAIN,
            gainFromPosition(1f, UserSettings.DEFAULT_MUSIC_GAIN),
            0.001f
        )
    }

    @Test
    fun everyFilledPresetSlotIsExported() = runTest {
        val intervalSlot = samplePreset().copy(
            id = PresetSlots.slotIds(TrainingKind.INTERVALS).first(),
            empty = false
        )
        val chordSlot = sampleChordPreset().copy(
            id = PresetSlots.slotIds(TrainingKind.CHORDS).first(),
            empty = false
        )
        val presets = FakePresetRepository(listOf(intervalSlot, chordSlot))
        val viewModel = PresetsViewModel(presets, FakeSettingsRepository())
        viewModel.state.first { it.loaded }
        var document: String? = null

        viewModel.exportDocument { document = it }
        advanceUntilIdle()

        assertEquals(setOf(intervalSlot.id, chordSlot.id), presets.exportedIds.toSet())
        assertEquals("exported:2", document)
        assertEquals("Exported 2 preset(s).", viewModel.state.value.message)
    }

    @Test
    fun anEmptyPresetExportIsReportedWithoutAFile() = runTest {
        val presets = FakePresetRepository(emptyList())
        val viewModel = PresetsViewModel(presets, FakeSettingsRepository())
        viewModel.state.first { it.loaded }
        var document: String? = null

        viewModel.exportDocument { document = it }
        advanceUntilIdle()

        assertNull(document)
        assertEquals("There are no filled preset slots to export.", viewModel.state.value.message)
    }

    @Test
    fun importingAnEmptyDocumentReportsAUsefulMessage() = runTest {
        val presets = FakePresetRepository(emptyList())
        val viewModel = PresetsViewModel(presets, FakeSettingsRepository())
        viewModel.state.first { it.loaded }

        viewModel.importDocument("{}")
        advanceUntilIdle()

        assertEquals("The document contains no presets.", viewModel.state.value.message)
    }

    @Test
    fun theCurrentSetupFillsAChosenSlot() = runTest {
        val settings = FakeSettingsRepository()
        val slot = samplePreset().copy(empty = true)
        val presets = FakePresetRepository(listOf(slot))
        val viewModel = PresetsViewModel(presets, settings)
        viewModel.state.first { it.loaded }

        viewModel.saveInto(slot)

        assertEquals(slot.id, presets.updated?.id)
        assertEquals(
            PresetConfiguration.Intervals(settings.current.lastSetup),
            presets.updated?.configuration
        )
        assertFalse("filling a slot clears its empty flag", presets.updated?.empty ?: true)
    }

    @Test
    fun theCurrentSeedIsNotSavedIntoAChosenSlot() = runTest {
        val settings = FakeSettingsRepository()
        val revision = settings.current.modeSetups.getValue(TrainingKind.INTERVALS).revision
        settings.updateMode(
            ModeEdit(TrainingKind.INTERVALS, revision) { existing ->
                val setup = (existing.payload as ModePayload.Intervals).setup
                ModePayload.Intervals(setup.copy(seed = 42L))
            }
        )
        val slot = samplePreset().copy(empty = true)
        val presets = FakePresetRepository(listOf(slot))
        val viewModel = PresetsViewModel(presets, settings)
        viewModel.state.first { it.loaded }

        viewModel.saveInto(slot)

        assertNull("a preset must not store the current run seed", presets.updated?.seed)
    }

    @Test
    fun renameReachesTheRepository() = runTest {
        val settings = FakeSettingsRepository()
        val existing = samplePreset()
        val presets = FakePresetRepository(listOf(existing))
        val viewModel = PresetsViewModel(presets, settings)
        viewModel.state.first { it.loaded }

        viewModel.rename(existing, "New name")

        assertEquals(existing.id to "New name", presets.renamed)
    }

    @Test
    fun chordSetupEditsAreAutosaved() = runTest {
        val repository = FakeSettingsRepository()
        val viewModel = ChordSetupViewModel(repository)
        viewModel.state.first { it.loaded }

        viewModel.editDraft {
            it.copy(
                qualityIds = listOf("major_seventh"),
                inversions = listOf(0, 1, 2, 3),
                answerMode = ChordAnswerMode.QUALITY_AND_INVERSION,
                presentation = ChordPresentation.ARPEGGIO
            )
        }
        advanceUntilIdle()

        val stored = repository.current.chordSetup
        assertEquals(listOf("major_seventh"), stored.qualityIds)
        assertEquals(listOf(0, 1, 2, 3), stored.inversions)
        assertEquals(ChordAnswerMode.QUALITY_AND_INVERSION, stored.answerMode)
        assertEquals(ChordPresentation.ARPEGGIO, stored.presentation)
        assertTrue("the screen confirms the automatic save", viewModel.state.value.saved)
        assertEquals(
            "sound gains belong to Settings and are not changed by a chord setup save",
            UserSettings.DEFAULT_MUSIC_GAIN,
            repository.current.musicGain,
            0f
        )
    }

    @Test
    fun aFailedChordSetupSaveIsVisibleAndCanBeRetried() = runTest {
        val repository = FakeSettingsRepository()
        val viewModel = ChordSetupViewModel(repository)
        viewModel.state.first { it.loaded }

        repository.writeFailure = java.io.IOException("chord disk full")
        viewModel.editDraft { it.copy(replayCount = 2) }
        viewModel.save()
        assertFalse(viewModel.state.value.saved)
        assertEquals("chord disk full", viewModel.state.value.saveError)

        repository.writeFailure = null
        viewModel.save()
        assertTrue(viewModel.state.value.saved)
        assertEquals(2, repository.current.chordSetup.replayCount)
    }

    @Test
    fun anInvalidChordDraftIsKeptAndNeverAutosaved() = runTest {
        val repository = FakeSettingsRepository()
        val viewModel = ChordSetupViewModel(repository)
        viewModel.state.first { it.loaded }

        viewModel.editDraft { it.copy(qualityIds = emptyList()) }
        advanceUntilIdle()

        assertEquals(
            "the invalid selection stays in the editor for correction",
            emptyList<String>(),
            viewModel.state.value.draft.qualityIds
        )
        assertTrue(viewModel.state.value.validation is SetupValidation.Invalid)
        assertEquals(
            "the stored chord setup keeps its previous qualities",
            ChordSetup.default().qualityIds,
            repository.current.chordSetup.qualityIds
        )
    }

    @Test
    fun anExternallyAppliedChordSetupRefreshesTheDraft() = runTest {
        val repository = FakeSettingsRepository()
        val viewModel = ChordSetupViewModel(repository)
        viewModel.state.first { it.loaded }

        repository.updateMode(
            ModeEdit(TrainingKind.CHORDS, 1L) { existing ->
                val current = existing.payload as ModePayload.Chords
                ModePayload.Chords(
                    current.setup.copy(
                        qualityIds = listOf("dominant_seventh"),
                        replayCount = 3
                    )
                )
            }
        )
        advanceUntilIdle()

        assertEquals(listOf("dominant_seventh"), viewModel.state.value.draft.qualityIds)
        assertEquals(3, viewModel.state.value.draft.replayCount)
    }

    @Test
    fun applyingAChordPresetWritesItsChordSetup() = runTest {
        val preset = sampleChordPreset()
        val presets = FakePresetRepository(listOf(preset))
        val settings = FakeSettingsRepository()
        val viewModel = PresetsViewModel(presets, settings, TrainingKind.CHORDS)
        viewModel.state.first { it.loaded }

        viewModel.use(preset)

        assertEquals(
            (preset.configuration as PresetConfiguration.Chords).setup.copy(seed = null),
            settings.current.chordSetup
        )
        assertNull(settings.current.chordSetup.seed)
        assertEquals(preset.musicGain, settings.current.musicGain, 0f)
    }

    @Test
    fun chordSlotsListTheStarterAndClearThroughTheRepository() = runTest {
        val slotIds = PresetSlots.slotIds(TrainingKind.CHORDS)
        val slots = slotIds.mapIndexed { index, id ->
            sampleChordPreset().copy(
                id = id,
                name = if (index == 0) "My chords" else PresetSlots.defaultName(index + 1),
                builtIn = false,
                empty = index != 0
            )
        }
        val presets = FakePresetRepository(slots)
        val viewModel = PresetsViewModel(presets, FakeSettingsRepository(), TrainingKind.CHORDS)
        viewModel.state.first { it.loaded }
        advanceUntilIdle()

        assertTrue(
            "the chords starter is always listed",
            viewModel.state.value.presets.any { it.id == PresetStarters.CHORDS.id }
        )
        val shown = viewModel.state.value.presets.first { it.id == slotIds.first() }
        assertFalse(shown.empty)

        viewModel.clear(shown)
        advanceUntilIdle()

        assertTrue(viewModel.state.value.presets.first { it.id == slotIds.first() }.empty)
        assertEquals(
            "the starter and the five slots fill exactly the chords list",
            PresetSlots.COUNT + 1,
            viewModel.state.value.presets.size
        )
    }

    @Test
    fun scaleSetupEditsAreAutosaved() = runTest {
        val repository = FakeSettingsRepository()
        val viewModel = ScaleSetupViewModel(repository)
        viewModel.state.first { it.loaded }

        viewModel.editDraft {
            it.copy(
                scaleIds = listOf("major", "dorian"),
                direction = ScaleDirection.BOTH,
                rootValues = listOf(0, 7)
            )
        }
        advanceUntilIdle()

        val stored = repository.current.scalesSetup
        assertEquals(listOf("major", "dorian"), stored.scaleIds)
        assertEquals(ScaleDirection.BOTH, stored.direction)
        assertEquals(listOf(0, 7), stored.rootValues)
        assertTrue("the screen confirms the automatic save", viewModel.state.value.saved)
        assertEquals(
            "sound gains belong to Settings and are not changed by a scale setup save",
            UserSettings.DEFAULT_MUSIC_GAIN,
            repository.current.musicGain,
            0f
        )
    }

    @Test
    fun aFailedScaleSetupSaveIsVisibleAndCanBeRetried() = runTest {
        val repository = FakeSettingsRepository()
        val viewModel = ScaleSetupViewModel(repository)
        viewModel.state.first { it.loaded }

        repository.writeFailure = java.io.IOException("scale disk full")
        viewModel.editDraft { it.copy(replayCount = 2) }
        viewModel.save()
        assertFalse(viewModel.state.value.saved)
        assertEquals("scale disk full", viewModel.state.value.saveError)

        repository.writeFailure = null
        viewModel.save()
        assertTrue(viewModel.state.value.saved)
        assertEquals(2, repository.current.scalesSetup.replayCount)
    }

    @Test
    fun anInvalidScaleDraftIsKeptAndNeverAutosaved() = runTest {
        val repository = FakeSettingsRepository()
        val viewModel = ScaleSetupViewModel(repository)
        viewModel.state.first { it.loaded }

        viewModel.editDraft { it.copy(scaleIds = emptyList()) }
        advanceUntilIdle()

        assertEquals(
            "the invalid selection stays in the editor for correction",
            emptyList<String>(),
            viewModel.state.value.draft.scaleIds
        )
        assertTrue(viewModel.state.value.validation is SetupValidation.Invalid)
        assertEquals(
            "the stored scale setup keeps its previous scales",
            ScaleSetup.default().scaleIds,
            repository.current.scalesSetup.scaleIds
        )
    }

    @Test
    fun anExternallyAppliedScaleSetupRefreshesTheDraft() = runTest {
        val repository = FakeSettingsRepository()
        val viewModel = ScaleSetupViewModel(repository)
        viewModel.state.first { it.loaded }

        repository.updateMode(
            ModeEdit(TrainingKind.SCALES, 1L) { existing ->
                val current = existing.payload as ModePayload.Scales
                ModePayload.Scales(
                    current.setup.copy(
                        scaleIds = listOf("harmonic_minor"),
                        direction = ScaleDirection.DESCENDING,
                        replayCount = 3
                    )
                )
            }
        )
        advanceUntilIdle()

        assertEquals(listOf("harmonic_minor"), viewModel.state.value.draft.scaleIds)
        assertEquals(ScaleDirection.DESCENDING, viewModel.state.value.draft.direction)
        assertEquals(3, viewModel.state.value.draft.replayCount)
    }

    @Test
    fun applyingAScalePresetWritesItsScaleSetup() = runTest {
        val preset = sampleScalePreset()
        val presets = FakePresetRepository(listOf(preset))
        val settings = FakeSettingsRepository()
        val viewModel = PresetsViewModel(presets, settings, TrainingKind.SCALES)
        viewModel.state.first { it.loaded }

        viewModel.use(preset)

        assertEquals(
            (preset.configuration as PresetConfiguration.Scales).setup.copy(seed = null),
            settings.current.scalesSetup
        )
        assertNull(settings.current.scalesSetup.seed)
        assertEquals(preset.musicGain, settings.current.musicGain, 0f)
    }

    @Test
    fun scaleSlotsListTheStarterAndClearThroughTheRepository() = runTest {
        val slotIds = PresetSlots.slotIds(TrainingKind.SCALES)
        val slots = slotIds.mapIndexed { index, id ->
            sampleScalePreset().copy(
                id = id,
                name = if (index == 0) "My scales" else PresetSlots.defaultName(index + 1),
                builtIn = false,
                empty = index != 0
            )
        }
        val presets = FakePresetRepository(slots)
        val viewModel = PresetsViewModel(presets, FakeSettingsRepository(), TrainingKind.SCALES)
        viewModel.state.first { it.loaded }
        advanceUntilIdle()

        assertTrue(
            "the scales starter is always listed",
            viewModel.state.value.presets.any { it.id == PresetStarters.SCALES.id }
        )
        val shown = viewModel.state.value.presets.first { it.id == slotIds.first() }
        assertFalse(shown.empty)

        viewModel.clear(shown)
        advanceUntilIdle()

        assertTrue(viewModel.state.value.presets.first { it.id == slotIds.first() }.empty)
        assertEquals(
            "the starter and the five slots fill exactly the scales list",
            PresetSlots.COUNT + 1,
            viewModel.state.value.presets.size
        )
    }

    @Test
    fun noteSetupEditsAreAutosaved() = runTest {
        val repository = FakeSettingsRepository()
        val viewModel = NoteSetupViewModel(repository)
        viewModel.state.first { it.loaded }

        viewModel.editDraft {
            it.copy(
                pitchClassValues = listOf(0, 4, 7),
                accidental = MusicSpellingPreference.FLAT,
                speakOctave = true
            )
        }
        advanceUntilIdle()

        val stored = repository.current.noteSetup
        assertEquals(listOf(0, 4, 7), stored.pitchClassValues)
        assertEquals(MusicSpellingPreference.FLAT, stored.accidental)
        assertTrue(stored.speakOctave)
        assertTrue("the screen confirms the automatic save", viewModel.state.value.saved)
        assertEquals(
            "sound gains belong to Settings and are not changed by a note setup save",
            UserSettings.DEFAULT_MUSIC_GAIN,
            repository.current.musicGain,
            0f
        )
    }

    @Test
    fun aFailedNoteSetupSaveIsVisibleAndCanBeRetried() = runTest {
        val repository = FakeSettingsRepository()
        val viewModel = NoteSetupViewModel(repository)
        viewModel.state.first { it.loaded }

        repository.writeFailure = java.io.IOException("note disk full")
        viewModel.editDraft { it.copy(replayCount = 2) }
        viewModel.save()
        assertFalse(viewModel.state.value.saved)
        assertEquals("note disk full", viewModel.state.value.saveError)

        repository.writeFailure = null
        viewModel.save()
        assertTrue(viewModel.state.value.saved)
        assertEquals(2, repository.current.noteSetup.replayCount)
    }

    @Test
    fun anInvalidNoteDraftIsKeptAndNeverAutosaved() = runTest {
        val repository = FakeSettingsRepository()
        val viewModel = NoteSetupViewModel(repository)
        viewModel.state.first { it.loaded }

        viewModel.editDraft { it.copy(pitchClassValues = emptyList()) }
        advanceUntilIdle()

        assertEquals(
            "the invalid selection stays in the editor for correction",
            emptyList<Int>(),
            viewModel.state.value.draft.pitchClassValues
        )
        assertTrue(viewModel.state.value.validation is SetupValidation.Invalid)
        assertEquals(
            "the stored note setup keeps its previous classes",
            NoteSetup.default().pitchClassValues,
            repository.current.noteSetup.pitchClassValues
        )
    }

    @Test
    fun anExternallyAppliedNoteSetupRefreshesTheDraft() = runTest {
        val repository = FakeSettingsRepository()
        val viewModel = NoteSetupViewModel(repository)
        viewModel.state.first { it.loaded }

        repository.updateMode(
            ModeEdit(TrainingKind.NOTE_IDENTIFICATION, 1L) { existing ->
                val current = existing.payload as ModePayload.Notes
                ModePayload.Notes(
                    current.setup.copy(
                        pitchClassValues = listOf(1),
                        accidental = MusicSpellingPreference.FLAT,
                        replayCount = 3
                    )
                )
            }
        )
        advanceUntilIdle()

        assertEquals(listOf(1), viewModel.state.value.draft.pitchClassValues)
        assertEquals(MusicSpellingPreference.FLAT, viewModel.state.value.draft.accidental)
        assertEquals(3, viewModel.state.value.draft.replayCount)
    }

    @Test
    fun applyingANotePresetWritesItsNoteSetup() = runTest {
        val preset = sampleNotePreset()
        val presets = FakePresetRepository(listOf(preset))
        val settings = FakeSettingsRepository()
        val viewModel = PresetsViewModel(presets, settings, TrainingKind.NOTE_IDENTIFICATION)
        viewModel.state.first { it.loaded }

        viewModel.use(preset)

        assertEquals(
            (preset.configuration as PresetConfiguration.Notes).setup.copy(seed = null),
            settings.current.noteSetup
        )
        assertNull(settings.current.noteSetup.seed)
        assertEquals(preset.musicGain, settings.current.musicGain, 0f)
    }

    @Test
    fun theCurrentNoteSetupFillsAChosenSlot() = runTest {
        val settings = FakeSettingsRepository()
        val slot = sampleNotePreset().copy(empty = true)
        val presets = FakePresetRepository(listOf(slot))
        val viewModel = PresetsViewModel(presets, settings, TrainingKind.NOTE_IDENTIFICATION)
        viewModel.state.first { it.loaded }

        viewModel.saveInto(slot)

        assertEquals(slot.id, presets.updated?.id)
        assertEquals(
            PresetConfiguration.Notes(settings.current.noteSetup),
            presets.updated?.configuration
        )
        assertFalse("filling a slot clears its empty flag", presets.updated?.empty ?: true)
    }

    @Test
    fun noteSlotsListTheStarterAndClearThroughTheRepository() = runTest {
        val slotIds = PresetSlots.slotIds(TrainingKind.NOTE_IDENTIFICATION)
        val slots = slotIds.mapIndexed { index, id ->
            sampleNotePreset().copy(
                id = id,
                name = if (index == 0) "My notes" else PresetSlots.defaultName(index + 1),
                builtIn = false,
                empty = index != 0
            )
        }
        val presets = FakePresetRepository(slots)
        val viewModel =
            PresetsViewModel(presets, FakeSettingsRepository(), TrainingKind.NOTE_IDENTIFICATION)
        viewModel.state.first { it.loaded }
        advanceUntilIdle()

        assertTrue(
            "the notes starter is always listed",
            viewModel.state.value.presets.any { it.id == PresetStarters.NOTES.id }
        )
        val shown = viewModel.state.value.presets.first { it.id == slotIds.first() }
        assertFalse(shown.empty)

        viewModel.clear(shown)
        advanceUntilIdle()

        assertTrue(viewModel.state.value.presets.first { it.id == slotIds.first() }.empty)
        assertEquals(
            "the starter and the five slots fill exactly the notes list",
            PresetSlots.COUNT + 1,
            viewModel.state.value.presets.size
        )
    }

    @Test
    fun degreeSetupEditsAreAutosaved() = runTest {
        val repository = FakeSettingsRepository()
        val viewModel = ScaleDegreeSetupViewModel(repository)
        viewModel.state.first { it.loaded }

        viewModel.editDraft {
            it.copy(
                keyValues = listOf(0, 7),
                tonalities = listOf(Tonality.MAJOR, Tonality.NATURAL_MINOR),
                degrees = listOf(1, 5),
                context = ContextKind.ASCENDING_SCALE,
                contextGap = Duration.parse("750ms"),
                answerFormat = DegreeAnswerFormat.NOTE_NAME,
                // G major's octave reaches G5, so the range widens with the added key; the
                // validator would otherwise (correctly) refuse the edit and never autosave it.
                minimumNote = 55,
                maximumNote = 79
            )
        }
        advanceUntilIdle()

        val stored = repository.current.scaleDegreeSetup
        assertEquals(listOf(0, 7), stored.keyValues)
        assertEquals(listOf(Tonality.MAJOR, Tonality.NATURAL_MINOR), stored.tonalities)
        assertEquals(listOf(1, 5), stored.degrees)
        assertEquals(ContextKind.ASCENDING_SCALE, stored.context)
        assertEquals(Duration.parse("750ms"), stored.contextGap)
        assertEquals(DegreeAnswerFormat.NOTE_NAME, stored.answerFormat)
        assertEquals(55, stored.minimumNote)
        assertEquals(79, stored.maximumNote)
        assertTrue("the screen confirms the automatic save", viewModel.state.value.saved)
        assertEquals(
            "sound gains belong to Settings and are not changed by a scale-degree save",
            UserSettings.DEFAULT_MUSIC_GAIN,
            repository.current.musicGain,
            0f
        )
    }

    @Test
    fun aFailedDegreeSetupSaveIsVisibleAndCanBeRetried() = runTest {
        val repository = FakeSettingsRepository()
        val viewModel = ScaleDegreeSetupViewModel(repository)
        viewModel.state.first { it.loaded }

        repository.writeFailure = java.io.IOException("degree disk full")
        viewModel.editDraft { it.copy(replayCount = 2) }
        viewModel.save()
        assertFalse(viewModel.state.value.saved)
        assertEquals("degree disk full", viewModel.state.value.saveError)

        repository.writeFailure = null
        viewModel.save()
        assertTrue(viewModel.state.value.saved)
        assertEquals(2, repository.current.scaleDegreeSetup.replayCount)
    }

    @Test
    fun anImpossibleDegreeContextRangeKeepsTheDraftAndExplainsItself() = runTest {
        val repository = FakeSettingsRepository()
        val viewModel = ScaleDegreeSetupViewModel(repository)
        viewModel.state.first { it.loaded }

        // A complete ascending octave cannot fit C4..E4, so every key/major/degree-1 combination
        // is infeasible; the provider must explain the context/range connection while the draft
        // stays editable and is never autosaved.
        viewModel.editDraft {
            it.copy(
                context = ContextKind.ASCENDING_SCALE,
                degrees = listOf(1),
                minimumNote = 105,
                maximumNote = 108
            )
        }
        advanceUntilIdle()

        assertEquals(ContextKind.ASCENDING_SCALE, viewModel.state.value.draft.context)
        assertEquals(108, viewModel.state.value.draft.maximumNote)
        val validation = viewModel.state.value.validation
        assertTrue(validation is SetupValidation.Invalid)
        val rangeIssues = (validation as SetupValidation.Invalid).issues
            .filter { it.field == SetupField.RANGE }
        assertTrue(
            "the impossible context/range combination must explain itself: $rangeIssues",
            rangeIssues.any {
                it.explanation.contains("C major") &&
                    it.explanation.contains("105..108")
            }
        )
        assertEquals(
            "the stored degree setup keeps its previous context and range",
            ContextKind.TONIC_TRIAD,
            repository.current.scaleDegreeSetup.context
        )
        assertEquals(72, repository.current.scaleDegreeSetup.maximumNote)
    }

    @Test
    fun anExternallyAppliedDegreeSetupRefreshesTheDraft() = runTest {
        val repository = FakeSettingsRepository()
        val viewModel = ScaleDegreeSetupViewModel(repository)
        viewModel.state.first { it.loaded }

        repository.updateMode(
            ModeEdit(TrainingKind.SCALE_DEGREES, 1L) { existing ->
                val current = existing.payload as ModePayload.ScaleDegrees
                ModePayload.ScaleDegrees(
                    current.setup.copy(
                        keyValues = listOf(9),
                        tonalities = listOf(Tonality.NATURAL_MINOR),
                        degrees = listOf(2),
                        context = ContextKind.TONIC_NOTE,
                        replayCount = 3
                    )
                )
            }
        )
        advanceUntilIdle()

        assertEquals(listOf(9), viewModel.state.value.draft.keyValues)
        assertEquals(listOf(Tonality.NATURAL_MINOR), viewModel.state.value.draft.tonalities)
        assertEquals(listOf(2), viewModel.state.value.draft.degrees)
        assertEquals(ContextKind.TONIC_NOTE, viewModel.state.value.draft.context)
        assertEquals(3, viewModel.state.value.draft.replayCount)
    }

    @Test
    fun applyingADegreePresetWritesItsDegreeSetup() = runTest {
        val preset = sampleDegreePreset()
        val presets = FakePresetRepository(listOf(preset))
        val settings = FakeSettingsRepository()
        val viewModel = PresetsViewModel(presets, settings, TrainingKind.SCALE_DEGREES)
        viewModel.state.first { it.loaded }

        viewModel.use(preset)

        assertEquals(
            (preset.configuration as PresetConfiguration.ScaleDegrees).setup.copy(seed = null),
            settings.current.scaleDegreeSetup
        )
        assertNull(settings.current.scaleDegreeSetup.seed)
        assertEquals(preset.musicGain, settings.current.musicGain, 0f)
    }

    @Test
    fun theCurrentDegreeSetupFillsAChosenSlot() = runTest {
        val settings = FakeSettingsRepository()
        val slot = sampleDegreePreset().copy(empty = true)
        val presets = FakePresetRepository(listOf(slot))
        val viewModel = PresetsViewModel(presets, settings, TrainingKind.SCALE_DEGREES)
        viewModel.state.first { it.loaded }

        viewModel.saveInto(slot)

        assertEquals(slot.id, presets.updated?.id)
        assertEquals(
            PresetConfiguration.ScaleDegrees(settings.current.scaleDegreeSetup),
            presets.updated?.configuration
        )
        assertFalse("filling a slot clears its empty flag", presets.updated?.empty ?: true)
    }

    @Test
    fun degreeSlotsListTheStarterAndClearThroughTheRepository() = runTest {
        val slotIds = PresetSlots.slotIds(TrainingKind.SCALE_DEGREES)
        val slots = slotIds.mapIndexed { index, id ->
            sampleDegreePreset().copy(
                id = id,
                name = if (index == 0) "My degrees" else PresetSlots.defaultName(index + 1),
                builtIn = false,
                empty = index != 0
            )
        }
        val presets = FakePresetRepository(slots)
        val viewModel =
            PresetsViewModel(presets, FakeSettingsRepository(), TrainingKind.SCALE_DEGREES)
        viewModel.state.first { it.loaded }
        advanceUntilIdle()

        assertTrue(
            "the scale-degrees starter is always listed",
            viewModel.state.value.presets.any { it.id == PresetStarters.SCALE_DEGREES.id }
        )
        val shown = viewModel.state.value.presets.first { it.id == slotIds.first() }
        assertFalse(shown.empty)

        viewModel.clear(shown)
        advanceUntilIdle()

        assertTrue(viewModel.state.value.presets.first { it.id == slotIds.first() }.empty)
        assertEquals(
            "the starter and the five slots fill exactly the scale-degrees list",
            PresetSlots.COUNT + 1,
            viewModel.state.value.presets.size
        )
    }

    @Test
    fun progressionSetupEditsAreAutosaved() = runTest {
        val repository = FakeSettingsRepository()
        val viewModel = ProgressionSetupViewModel(repository)
        viewModel.state.first { it.loaded }

        viewModel.editDraft {
            it.copy(
                chordIds = listOf(
                    DiatonicChord.I.id,
                    DiatonicChord.IV.id,
                    DiatonicChord.V7.id
                ),
                voicing = ProgressionVoicingMode.NEAREST_INVERSION,
                allowedInversions = listOf(0, 1),
                answerFormat = ProgressionAnswerFormat.ROOT_MOTION,
                spelling = MusicSpellingPreference.FLAT,
                minimumNote = 48,
                maximumNote = 84,
                mode = RecipeMode.EXPOSURE,
                replayCount = 1
            )
        }
        advanceUntilIdle()

        val stored = repository.current.progressionSetup
        assertEquals(
            listOf(
                DiatonicChord.I.id,
                DiatonicChord.IV.id,
                DiatonicChord.V7.id
            ),
            stored.chordIds
        )
        assertEquals(ProgressionVoicingMode.NEAREST_INVERSION, stored.voicing)
        assertEquals(listOf(0, 1), stored.allowedInversions)
        assertEquals(ProgressionAnswerFormat.ROOT_MOTION, stored.answerFormat)
        assertEquals(MusicSpellingPreference.FLAT, stored.spelling)
        assertEquals(RecipeMode.EXPOSURE, stored.mode)
        assertEquals(1, stored.replayCount)
        assertTrue("the screen confirms the automatic save", viewModel.state.value.saved)
        assertEquals(
            "sound gains belong to Settings and are not changed by a progression save",
            UserSettings.DEFAULT_MUSIC_GAIN,
            repository.current.musicGain,
            0f
        )
    }

    @Test
    fun aFailedProgressionSetupSaveIsVisibleAndCanBeRetried() = runTest {
        val repository = FakeSettingsRepository()
        val viewModel = ProgressionSetupViewModel(repository)
        viewModel.state.first { it.loaded }

        repository.writeFailure = java.io.IOException("progression disk full")
        viewModel.editDraft { it.copy(replayCount = 2) }
        viewModel.save()
        assertFalse(viewModel.state.value.saved)
        assertEquals("progression disk full", viewModel.state.value.saveError)

        repository.writeFailure = null
        viewModel.save()
        assertTrue(viewModel.state.value.saved)
        assertEquals(2, repository.current.progressionSetup.replayCount)
    }

    @Test
    fun anImpossibleProgressionChordKeyRangeKeepsTheDraftAndExplainsItself() = runTest {
        val repository = FakeSettingsRepository()
        val viewModel = ProgressionSetupViewModel(repository)
        viewModel.state.first { it.loaded }

        // A triad cannot fit a three-semitone window, so no key can realize the opening I and
        // the draft must keep itself editable while explaining the range.
        viewModel.editDraft {
            it.copy(
                chordIds = listOf(DiatonicChord.I.id, DiatonicChord.V.id),
                minimumNote = 105,
                maximumNote = 108
            )
        }
        advanceUntilIdle()

        assertEquals(108, viewModel.state.value.draft.maximumNote)
        val validation = viewModel.state.value.validation
        assertTrue(validation is SetupValidation.Invalid)
        val rangeIssues = (validation as SetupValidation.Invalid).issues
            .filter { it.field == SetupField.RANGE }
        assertTrue(
            "the impossible selection/range combination must explain itself: $rangeIssues",
            rangeIssues.any {
                it.explanation.contains("opening I") &&
                    it.explanation.contains("105..108")
            }
        )
        assertEquals(
            "the stored progression setup keeps its previous chords and range",
            DiatonicChord.TRIADS.map { it.id },
            repository.current.progressionSetup.chordIds
        )
        assertEquals(84, repository.current.progressionSetup.maximumNote)
    }

    @Test
    fun anExternallyAppliedProgressionSetupRefreshesTheDraft() = runTest {
        val repository = FakeSettingsRepository()
        val viewModel = ProgressionSetupViewModel(repository)
        viewModel.state.first { it.loaded }

        repository.updateMode(
            ModeEdit(TrainingKind.CHORD_PROGRESSIONS, 1L) { existing ->
                val current = existing.payload as ModePayload.Progressions
                ModePayload.Progressions(
                    current.setup.copy(
                        chordIds = listOf(
                            DiatonicChord.I7.id,
                            DiatonicChord.II7.id,
                            DiatonicChord.V7.id
                        ),
                        voicing = ProgressionVoicingMode.NEAREST_INVERSION,
                        allowedInversions = listOf(1, 2),
                        answerFormat = ProgressionAnswerFormat.FUNCTIONS,
                        replayCount = 3
                    )
                )
            }
        )
        advanceUntilIdle()

        assertEquals(
            listOf(DiatonicChord.I7.id, DiatonicChord.II7.id, DiatonicChord.V7.id),
            viewModel.state.value.draft.chordIds
        )
        assertEquals(
            ProgressionVoicingMode.NEAREST_INVERSION,
            viewModel.state.value.draft.voicing
        )
        assertEquals(listOf(1, 2), viewModel.state.value.draft.allowedInversions)
        assertEquals(ProgressionAnswerFormat.FUNCTIONS, viewModel.state.value.draft.answerFormat)
        assertEquals(3, viewModel.state.value.draft.replayCount)
    }

    @Test
    fun applyingTheProgressionStarterWritesItsDefaultSetup() = runTest {
        val starter = PresetStarters.PROGRESSIONS
        val presets = FakePresetRepository(listOf(starter))
        val settings = FakeSettingsRepository()
        val viewModel = PresetsViewModel(presets, settings, TrainingKind.CHORD_PROGRESSIONS)
        viewModel.state.first { it.loaded }

        viewModel.use(starter)

        assertEquals(ProgressionSetup.default(), settings.current.progressionSetup)
    }

    @Test
    fun applyingAProgressionPresetWritesItsProgressionSetup() = runTest {
        val preset = sampleProgressionPreset()
        val presets = FakePresetRepository(listOf(preset))
        val settings = FakeSettingsRepository()
        val viewModel = PresetsViewModel(presets, settings, TrainingKind.CHORD_PROGRESSIONS)
        viewModel.state.first { it.loaded }

        viewModel.use(preset)

        assertEquals(
            (preset.configuration as PresetConfiguration.Progressions).setup.copy(seed = null),
            settings.current.progressionSetup
        )
        assertNull(settings.current.progressionSetup.seed)
        assertEquals(preset.musicGain, settings.current.musicGain, 0f)
    }

    @Test
    fun theCurrentProgressionSetupFillsAChosenSlot() = runTest {
        val settings = FakeSettingsRepository()
        val slot = sampleProgressionPreset().copy(empty = true)
        val presets = FakePresetRepository(listOf(slot))
        val viewModel = PresetsViewModel(presets, settings, TrainingKind.CHORD_PROGRESSIONS)
        viewModel.state.first { it.loaded }

        viewModel.saveInto(slot)

        assertEquals(slot.id, presets.updated?.id)
        assertEquals(
            PresetConfiguration.Progressions(settings.current.progressionSetup),
            presets.updated?.configuration
        )
        assertFalse("filling a slot clears its empty flag", presets.updated?.empty ?: true)
    }

    @Test
    fun progressionSlotsListTheStarterAndClearThroughTheRepository() = runTest {
        val slotIds = PresetSlots.slotIds(TrainingKind.CHORD_PROGRESSIONS)
        val slots = slotIds.mapIndexed { index, id ->
            sampleProgressionPreset().copy(
                id = id,
                name = if (index == 0) "My progressions" else PresetSlots.defaultName(index + 1),
                builtIn = false,
                empty = index != 0
            )
        }
        val presets = FakePresetRepository(slots)
        val viewModel = PresetsViewModel(
            presets,
            FakeSettingsRepository(),
            TrainingKind.CHORD_PROGRESSIONS
        )
        viewModel.state.first { it.loaded }
        advanceUntilIdle()

        assertTrue(
            "the progressions starter is always listed",
            viewModel.state.value.presets.any { it.id == PresetStarters.PROGRESSIONS.id }
        )
        val shown = viewModel.state.value.presets.first { it.id == slotIds.first() }
        assertFalse(shown.empty)

        viewModel.clear(shown)
        advanceUntilIdle()

        assertTrue(viewModel.state.value.presets.first { it.id == slotIds.first() }.empty)
        assertEquals(
            "the starter and the five slots fill exactly the progressions list",
            PresetSlots.COUNT + 1,
            viewModel.state.value.presets.size
        )
    }

    @Test
    fun contextualIntervalSetupEditsAreAutosaved() = runTest {
        val repository = FakeSettingsRepository()
        val viewModel = ContextualIntervalSetupViewModel(repository)
        viewModel.state.first { it.loaded }

        viewModel.editDraft {
            it.copy(
                keyValues = listOf(0),
                intervalIds = listOf("minor_third"),
                presentation = IntervalPresentation.DESCENDING,
                context = ContextKind.TONIC_NOTE,
                contextGap = Duration.parse("750ms"),
                answerFormat = ContextualIntervalAnswerFormat.DEGREE_PAIR,
                spelling = MusicSpellingPreference.FLAT,
                mode = RecipeMode.EXPOSURE,
                replayCount = 1
            )
        }
        advanceUntilIdle()

        val stored = repository.current.contextualIntervalSetup
        assertEquals(listOf(0), stored.keyValues)
        assertEquals(listOf("minor_third"), stored.intervalIds)
        assertEquals(IntervalPresentation.DESCENDING, stored.presentation)
        assertEquals(ContextKind.TONIC_NOTE, stored.context)
        assertEquals(Duration.parse("750ms"), stored.contextGap)
        assertEquals(ContextualIntervalAnswerFormat.DEGREE_PAIR, stored.answerFormat)
        assertEquals(MusicSpellingPreference.FLAT, stored.spelling)
        assertEquals(RecipeMode.EXPOSURE, stored.mode)
        assertEquals(1, stored.replayCount)
        assertTrue("the screen confirms the automatic save", viewModel.state.value.saved)
        assertEquals(
            "sound gains belong to Settings and are not changed by a context-interval save",
            UserSettings.DEFAULT_MUSIC_GAIN,
            repository.current.musicGain,
            0f
        )
    }

    @Test
    fun aFailedContextualIntervalSetupSaveIsVisibleAndCanBeRetried() = runTest {
        val repository = FakeSettingsRepository()
        val viewModel = ContextualIntervalSetupViewModel(repository)
        viewModel.state.first { it.loaded }

        repository.writeFailure = java.io.IOException("context disk full")
        viewModel.editDraft { it.copy(replayCount = 2) }
        viewModel.save()
        assertFalse(viewModel.state.value.saved)
        assertEquals("context disk full", viewModel.state.value.saveError)

        repository.writeFailure = null
        viewModel.save()
        assertTrue(viewModel.state.value.saved)
        assertEquals(2, repository.current.contextualIntervalSetup.replayCount)
    }

    @Test
    fun anImpossibleContextualIntervalSelectionKeepsTheDraftAndExplainsItself() = runTest {
        val repository = FakeSettingsRepository()
        val viewModel = ContextualIntervalSetupViewModel(repository)
        viewModel.state.first { it.loaded }

        // A descending octave cannot fit C4..E4: every candidate pair needs a note an octave
        // below its first note, which the range excludes; the provider must explain the
        // interval/presentation/key/range connection while the draft stays editable.
        viewModel.editDraft {
            it.copy(
                intervalIds = listOf("octave"),
                presentation = IntervalPresentation.DESCENDING,
                minimumNote = 105,
                maximumNote = 108
            )
        }
        advanceUntilIdle()

        assertEquals(
            IntervalPresentation.DESCENDING,
            viewModel.state.value.draft.presentation
        )
        assertEquals(108, viewModel.state.value.draft.maximumNote)
        val validation = viewModel.state.value.validation
        assertTrue(validation is SetupValidation.Invalid)
        val rangeIssues = (validation as SetupValidation.Invalid).issues
            .filter { it.field == SetupField.RANGE }
        assertTrue(
            "the impossible interval/presentation/range combination must explain itself: " +
                rangeIssues,
            rangeIssues.any {
                it.explanation.contains("C major") &&
                    it.explanation.contains("105..108")
            }
        )
        assertEquals(
            "the stored context-interval setup keeps its previous presentation and range",
            IntervalPresentation.ASCENDING,
            repository.current.contextualIntervalSetup.presentation
        )
        assertEquals(72, repository.current.contextualIntervalSetup.maximumNote)
    }

    @Test
    fun anExternallyAppliedContextualIntervalSetupRefreshesTheDraft() = runTest {
        val repository = FakeSettingsRepository()
        val viewModel = ContextualIntervalSetupViewModel(repository)
        viewModel.state.first { it.loaded }

        repository.updateMode(
            ModeEdit(TrainingKind.INTERVALS_IN_CONTEXT, 1L) { existing ->
                val current = existing.payload as ModePayload.ContextualIntervals
                ModePayload.ContextualIntervals(
                    current.setup.copy(
                        keyValues = listOf(9),
                        tonalities = listOf(Tonality.NATURAL_MINOR),
                        intervalIds = listOf("perfect_fifth"),
                        presentation = IntervalPresentation.HARMONIC,
                        context = ContextKind.SUBDOMINANT_DOMINANT_TONIC,
                        answerFormat = ContextualIntervalAnswerFormat.DEGREE_PAIR,
                        replayCount = 3
                    )
                )
            }
        )
        advanceUntilIdle()

        assertEquals(listOf(9), viewModel.state.value.draft.keyValues)
        assertEquals(listOf(Tonality.NATURAL_MINOR), viewModel.state.value.draft.tonalities)
        assertEquals(listOf("perfect_fifth"), viewModel.state.value.draft.intervalIds)
        assertEquals(IntervalPresentation.HARMONIC, viewModel.state.value.draft.presentation)
        assertEquals(
            ContextKind.SUBDOMINANT_DOMINANT_TONIC,
            viewModel.state.value.draft.context
        )
        assertEquals(
            ContextualIntervalAnswerFormat.DEGREE_PAIR,
            viewModel.state.value.draft.answerFormat
        )
        assertEquals(3, viewModel.state.value.draft.replayCount)
    }

    @Test
    fun applyingTheContextualIntervalStarterWritesItsDefaultSetup() = runTest {
        val starter = PresetStarters.CONTEXTUAL_INTERVALS
        val presets = FakePresetRepository(listOf(starter))
        val settings = FakeSettingsRepository()
        val viewModel =
            PresetsViewModel(presets, settings, TrainingKind.INTERVALS_IN_CONTEXT)
        viewModel.state.first { it.loaded }

        viewModel.use(starter)

        assertEquals(ContextualIntervalSetup.default(), settings.current.contextualIntervalSetup)
    }

    @Test
    fun applyingAContextualIntervalPresetWritesItsContextualIntervalSetup() = runTest {
        val preset = sampleContextualIntervalPreset()
        val presets = FakePresetRepository(listOf(preset))
        val settings = FakeSettingsRepository()
        val viewModel =
            PresetsViewModel(presets, settings, TrainingKind.INTERVALS_IN_CONTEXT)
        viewModel.state.first { it.loaded }

        viewModel.use(preset)

        assertEquals(
            (preset.configuration as PresetConfiguration.ContextualIntervals)
                .setup.copy(seed = null),
            settings.current.contextualIntervalSetup
        )
        assertNull(settings.current.contextualIntervalSetup.seed)
        assertEquals(preset.musicGain, settings.current.musicGain, 0f)
    }

    @Test
    fun theCurrentContextualIntervalSetupFillsAChosenSlot() = runTest {
        val settings = FakeSettingsRepository()
        val slot = sampleContextualIntervalPreset().copy(empty = true)
        val presets = FakePresetRepository(listOf(slot))
        val viewModel =
            PresetsViewModel(presets, settings, TrainingKind.INTERVALS_IN_CONTEXT)
        viewModel.state.first { it.loaded }

        viewModel.saveInto(slot)

        assertEquals(slot.id, presets.updated?.id)
        assertEquals(
            PresetConfiguration.ContextualIntervals(settings.current.contextualIntervalSetup),
            presets.updated?.configuration
        )
        assertFalse("filling a slot clears its empty flag", presets.updated?.empty ?: true)
    }

    @Test
    fun contextualIntervalSlotsListTheStarterAndClearThroughTheRepository() = runTest {
        val slotIds = PresetSlots.slotIds(TrainingKind.INTERVALS_IN_CONTEXT)
        val slots = slotIds.mapIndexed { index, id ->
            sampleContextualIntervalPreset().copy(
                id = id,
                name = if (index == 0) "My contexts" else PresetSlots.defaultName(index + 1),
                builtIn = false,
                empty = index != 0
            )
        }
        val presets = FakePresetRepository(slots)
        val viewModel = PresetsViewModel(
            presets,
            FakeSettingsRepository(),
            TrainingKind.INTERVALS_IN_CONTEXT
        )
        viewModel.state.first { it.loaded }
        advanceUntilIdle()

        assertTrue(
            "the intervals-in-context starter is always listed",
            viewModel.state.value.presets.any {
                it.id == PresetStarters.CONTEXTUAL_INTERVALS.id
            }
        )
        val shown = viewModel.state.value.presets.first { it.id == slotIds.first() }
        assertFalse(shown.empty)

        viewModel.clear(shown)
        advanceUntilIdle()

        assertTrue(viewModel.state.value.presets.first { it.id == slotIds.first() }.empty)
        assertEquals(
            "the starter and the five slots fill exactly the intervals-in-context list",
            PresetSlots.COUNT + 1,
            viewModel.state.value.presets.size
        )
    }

    @Test
    fun melodicDictationSlotsListTheStarterAndClearThroughTheRepository() = runTest {
        val slotIds = PresetSlots.slotIds(TrainingKind.MELODIC_DICTATION)
        val slots = slotIds.mapIndexed { index, id ->
            sampleMelodicDictationPreset().copy(
                id = id,
                name = if (index == 0) "My phrases" else PresetSlots.defaultName(index + 1),
                builtIn = false,
                empty = index != 0
            )
        }
        val presets = FakePresetRepository(slots)
        val viewModel = PresetsViewModel(
            presets,
            FakeSettingsRepository(),
            TrainingKind.MELODIC_DICTATION
        )
        viewModel.state.first { it.loaded }
        advanceUntilIdle()

        assertTrue(
            "the melodic-dictation starter is always listed",
            viewModel.state.value.presets.any {
                it.id == PresetStarters.MELODIC_DICTATION.id
            }
        )
        val shown = viewModel.state.value.presets.first { it.id == slotIds.first() }
        assertFalse(shown.empty)

        viewModel.clear(shown)
        advanceUntilIdle()

        assertTrue(viewModel.state.value.presets.first { it.id == slotIds.first() }.empty)
        assertEquals(
            "the starter and the five slots fill exactly the melodic-dictation list",
            PresetSlots.COUNT + 1,
            viewModel.state.value.presets.size
        )
    }

    @Test
    fun melodicDictationSetupEditsAreAutosaved() = runTest {
        val repository = FakeSettingsRepository()
        val viewModel = MelodicDictationSetupViewModel(repository)
        viewModel.state.first { it.loaded }

        // Every selected key and tonality of this edit has a complete five-note phrase from any
        // start to degree 3 inside C4..C5 with the -3..3 steps, so the autosave must accept it.
        viewModel.editDraft {
            it.copy(
                keyValues = listOf(0, 7),
                tonalities = listOf(Tonality.MAJOR, Tonality.NATURAL_MINOR),
                context = ContextKind.TONIC_NOTE,
                contextGap = Duration.parse("750ms"),
                phraseLength = 5,
                startDegree = null,
                endDegree = 3,
                allowedSteps = (-3..3).toList(),
                maximumLeap = 3,
                bias = MelodicDirectionBias.ASCENDING,
                answerFormat = MelodicAnswerFormat.DIRECTED_INTERVAL_SEQUENCE,
                spelling = MusicSpellingPreference.FLAT,
                mode = RecipeMode.EXPOSURE,
                replayCount = 1
            )
        }
        advanceUntilIdle()

        val stored = repository.current.melodicDictationSetup
        assertEquals(listOf(0, 7), stored.keyValues)
        assertEquals(listOf(Tonality.MAJOR, Tonality.NATURAL_MINOR), stored.tonalities)
        assertEquals(ContextKind.TONIC_NOTE, stored.context)
        assertEquals(Duration.parse("750ms"), stored.contextGap)
        assertEquals(5, stored.phraseLength)
        assertEquals(null, stored.startDegree)
        assertEquals(3, stored.endDegree)
        assertEquals((-3..3).toList(), stored.allowedSteps)
        assertEquals(3, stored.maximumLeap)
        assertEquals(MelodicDirectionBias.ASCENDING, stored.bias)
        assertEquals(MelodicAnswerFormat.DIRECTED_INTERVAL_SEQUENCE, stored.answerFormat)
        assertEquals(MusicSpellingPreference.FLAT, stored.spelling)
        assertEquals(RecipeMode.EXPOSURE, stored.mode)
        assertEquals(1, stored.replayCount)
        assertTrue("the screen confirms the automatic save", viewModel.state.value.saved)
        assertEquals(
            "sound gains belong to Settings and are not changed by a melodic-dictation save",
            UserSettings.DEFAULT_MUSIC_GAIN,
            repository.current.musicGain,
            0f
        )
    }

    @Test
    fun aFailedMelodicDictationSetupSaveIsVisibleAndCanBeRetried() = runTest {
        val repository = FakeSettingsRepository()
        val viewModel = MelodicDictationSetupViewModel(repository)
        viewModel.state.first { it.loaded }

        repository.writeFailure = java.io.IOException("melody disk full")
        viewModel.editDraft { it.copy(replayCount = 2) }
        viewModel.save()
        assertFalse(viewModel.state.value.saved)
        assertEquals("melody disk full", viewModel.state.value.saveError)

        repository.writeFailure = null
        viewModel.save()
        assertTrue(viewModel.state.value.saved)
        assertEquals(2, repository.current.melodicDictationSetup.replayCount)
    }

    @Test
    fun anImpossibleMelodicPhraseKeepsTheDraftAndExplainsItself() = runTest {
        val repository = FakeSettingsRepository()
        val viewModel = MelodicDictationSetupViewModel(repository)
        viewModel.state.first { it.loaded }

        // With step 0 as the only allowed step a phrase can never leave its first pitch class,
        // so a tonic-to-dominant phrase is impossible; the provider must explain the length,
        // endpoints, steps and range at once, and the draft stays editable.
        viewModel.editDraft {
            it.copy(allowedSteps = listOf(0), endDegree = 5)
        }
        advanceUntilIdle()

        assertEquals(listOf(0), viewModel.state.value.draft.allowedSteps)
        assertEquals(5, viewModel.state.value.draft.endDegree)
        val validation = viewModel.state.value.validation
        assertTrue(validation is SetupValidation.Invalid)
        val stepIssues = (validation as SetupValidation.Invalid).issues
            .filter { it.field == SetupField.STEPS }
        assertTrue(
            "the impossible endpoint/step/range combination must explain itself: $stepIssues",
            stepIssues.any {
                it.explanation.contains("No 4-note C major phrase inside 60..72") &&
                    it.explanation.contains("degree 1") &&
                    it.explanation.contains("degree 5") &&
                    it.explanation.contains("maximum leap")
            }
        )
        assertEquals(
            "the stored melodic-dictation setup keeps its previous endpoints and steps",
            1,
            repository.current.melodicDictationSetup.endDegree
        )
        assertEquals(
            (-5..5).toList(),
            repository.current.melodicDictationSetup.allowedSteps
        )
    }

    @Test
    fun aStoredCycleStopPolicyIsRefusedWithTheProviderExplanation() = runTest {
        // A stored or imported cycle policy predates the mode's no-cycle rule; the setup screen
        // must show the provider's explanation instead of silently applying it.
        val stored = MelodicDictationSetup.default().copy(
            stopPolicy = SessionStopPolicy.AfterCycle
        )
        val repository = FakeSettingsRepository(
            UserSettings.DEFAULTS.copy(
                modeSetups = UserSettings.DEFAULTS.modeSetups + (
                    TrainingKind.MELODIC_DICTATION to SavedModeSetup(
                        1L,
                        ModePayload.MelodicDictation(stored)
                    )
                    )
            )
        )
        val viewModel = MelodicDictationSetupViewModel(repository)
        viewModel.state.first { it.loaded }

        assertEquals(SessionStopPolicy.AfterCycle, viewModel.state.value.draft.stopPolicy)
        val validation = viewModel.state.value.validation
        assertTrue(validation is SetupValidation.Invalid)
        val stopIssues = (validation as SetupValidation.Invalid).issues
            .filter { it.field == SetupField.STOP_POLICY }
        assertTrue(
            "the stored cycle policy must show the provider's message: $stopIssues",
            stopIssues.any {
                it.explanation ==
                    "Melodic dictation has no label cycle, so it cannot stop after a cycle. " +
                    "Choose until stopped or a completed-item count."
            }
        )

        // Choosing a supported policy clears the message and saves.
        viewModel.editDraft { it.copy(stopPolicy = SessionStopPolicy.UntilStopped) }
        advanceUntilIdle()
        assertEquals(
            SessionStopPolicy.UntilStopped,
            repository.current.melodicDictationSetup.stopPolicy
        )
        assertTrue(viewModel.state.value.saved)
    }

    @Test
    fun anExternallyAppliedMelodicDictationSetupRefreshesTheDraft() = runTest {
        val repository = FakeSettingsRepository()
        val viewModel = MelodicDictationSetupViewModel(repository)
        viewModel.state.first { it.loaded }

        repository.updateMode(
            ModeEdit(TrainingKind.MELODIC_DICTATION, 1L) { existing ->
                val current = existing.payload as ModePayload.MelodicDictation
                ModePayload.MelodicDictation(
                    current.setup.copy(
                        keyValues = listOf(0),
                        tonalities = listOf(Tonality.MAJOR),
                        context = ContextKind.TONIC_NOTE,
                        phraseLength = 6,
                        startDegree = null,
                        endDegree = 3,
                        allowedSteps = (-5..5).toList(),
                        maximumLeap = 5,
                        bias = MelodicDirectionBias.DESCENDING,
                        answerFormat = MelodicAnswerFormat.DIRECTED_INTERVAL_SEQUENCE,
                        replayCount = 2
                    )
                )
            }
        )
        advanceUntilIdle()

        assertEquals(listOf(0), viewModel.state.value.draft.keyValues)
        assertEquals(ContextKind.TONIC_NOTE, viewModel.state.value.draft.context)
        assertEquals(6, viewModel.state.value.draft.phraseLength)
        assertEquals(null, viewModel.state.value.draft.startDegree)
        assertEquals(3, viewModel.state.value.draft.endDegree)
        assertEquals(MelodicDirectionBias.DESCENDING, viewModel.state.value.draft.bias)
        assertEquals(
            MelodicAnswerFormat.DIRECTED_INTERVAL_SEQUENCE,
            viewModel.state.value.draft.answerFormat
        )
        assertEquals(2, viewModel.state.value.draft.replayCount)
    }

    @Test
    fun applyingTheMelodicDictationStarterWritesItsDefaultSetup() = runTest {
        val starter = PresetStarters.MELODIC_DICTATION
        val presets = FakePresetRepository(listOf(starter))
        val settings = FakeSettingsRepository()
        val viewModel = PresetsViewModel(presets, settings, TrainingKind.MELODIC_DICTATION)
        viewModel.state.first { it.loaded }

        viewModel.use(starter)

        assertEquals(MelodicDictationSetup.default(), settings.current.melodicDictationSetup)
    }

    @Test
    fun applyingAMelodicDictationPresetWritesItsMelodicDictationSetup() = runTest {
        val preset = sampleMelodicDictationPreset()
        val presets = FakePresetRepository(listOf(preset))
        val settings = FakeSettingsRepository()
        val viewModel = PresetsViewModel(presets, settings, TrainingKind.MELODIC_DICTATION)
        viewModel.state.first { it.loaded }

        viewModel.use(preset)

        assertEquals(
            (preset.configuration as PresetConfiguration.MelodicDictation)
                .setup.copy(seed = null),
            settings.current.melodicDictationSetup
        )
        assertNull(settings.current.melodicDictationSetup.seed)
        assertEquals(preset.musicGain, settings.current.musicGain, 0f)
    }

    @Test
    fun theCurrentMelodicDictationSetupFillsAChosenSlot() = runTest {
        val settings = FakeSettingsRepository()
        val slot = sampleMelodicDictationPreset().copy(empty = true)
        val presets = FakePresetRepository(listOf(slot))
        val viewModel = PresetsViewModel(presets, settings, TrainingKind.MELODIC_DICTATION)
        viewModel.state.first { it.loaded }

        viewModel.saveInto(slot)

        assertEquals(slot.id, presets.updated?.id)
        assertEquals(
            PresetConfiguration.MelodicDictation(settings.current.melodicDictationSetup),
            presets.updated?.configuration
        )
        assertFalse("filling a slot clears its empty flag", presets.updated?.empty ?: true)
    }
}
