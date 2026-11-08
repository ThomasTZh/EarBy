package com.thomas.passiveeartraining.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStoreFile
import com.thomas.passiveeartraining.music.ChordPresentation
import com.thomas.passiveeartraining.music.IntervalPresentation
import com.thomas.passiveeartraining.music.MidiNote
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
import com.thomas.passiveeartraining.training.WalkDirection
import com.thomas.passiveeartraining.training.context.ContextKind
import com.thomas.passiveeartraining.training.provider.TrainingKind
import com.thomas.passiveeartraining.training.provider.toTrainingKind
import com.thomas.passiveeartraining.training.recipe.DurationFinish
import com.thomas.passiveeartraining.training.recipe.RecipeMode
import com.thomas.passiveeartraining.training.recipe.SessionStopPolicy
import com.thomas.passiveeartraining.training.settings.AnswerVisibility
import com.thomas.passiveeartraining.training.settings.CHORDS_CONFIG_VERSION
import com.thomas.passiveeartraining.training.settings.CHORD_PROGRESSIONS_CONFIG_VERSION
import com.thomas.passiveeartraining.training.settings.ChordSetup
import com.thomas.passiveeartraining.training.settings.ChordSetupDraft
import com.thomas.passiveeartraining.training.settings.ChordSetupValidator
import com.thomas.passiveeartraining.training.settings.ContextualIntervalSetup
import com.thomas.passiveeartraining.training.settings.ContextualIntervalSetupDraft
import com.thomas.passiveeartraining.training.settings.ContextualIntervalSetupValidator
import com.thomas.passiveeartraining.training.settings.INTERVALS_CONFIG_VERSION
import com.thomas.passiveeartraining.training.settings.INTERVALS_IN_CONTEXT_CONFIG_VERSION
import com.thomas.passiveeartraining.training.settings.INTERVAL_WALK_CONFIG_VERSION
import com.thomas.passiveeartraining.training.settings.IntervalWalkSetup
import com.thomas.passiveeartraining.training.settings.IntervalWalkSetupDraft
import com.thomas.passiveeartraining.training.settings.IntervalWalkSetupValidator
import com.thomas.passiveeartraining.training.settings.LastSetup
import com.thomas.passiveeartraining.training.settings.MELODIC_DICTATION_CONFIG_VERSION
import com.thomas.passiveeartraining.training.settings.MelodicDictationSetup
import com.thomas.passiveeartraining.training.settings.MelodicDictationSetupDraft
import com.thomas.passiveeartraining.training.settings.MelodicDictationSetupValidator
import com.thomas.passiveeartraining.training.settings.ModeEdit
import com.thomas.passiveeartraining.training.settings.ModePayload
import com.thomas.passiveeartraining.training.settings.ModeWriteResult
import com.thomas.passiveeartraining.training.settings.NOTE_IDENTIFICATION_CONFIG_VERSION
import com.thomas.passiveeartraining.training.settings.NoteSetup
import com.thomas.passiveeartraining.training.settings.NoteSetupDraft
import com.thomas.passiveeartraining.training.settings.NoteSetupValidator
import com.thomas.passiveeartraining.training.settings.ProgressionSetup
import com.thomas.passiveeartraining.training.settings.ProgressionSetupDraft
import com.thomas.passiveeartraining.training.settings.ProgressionSetupValidator
import com.thomas.passiveeartraining.training.settings.SCALES_CONFIG_VERSION
import com.thomas.passiveeartraining.training.settings.SCALE_DEGREES_CONFIG_VERSION
import com.thomas.passiveeartraining.training.settings.SavedModeSetup
import com.thomas.passiveeartraining.training.settings.ScaleDegreeSetup
import com.thomas.passiveeartraining.training.settings.ScaleDegreeSetupDraft
import com.thomas.passiveeartraining.training.settings.ScaleDegreeSetupValidator
import com.thomas.passiveeartraining.training.settings.ScaleSetup
import com.thomas.passiveeartraining.training.settings.ScaleSetupDraft
import com.thomas.passiveeartraining.training.settings.ScaleSetupValidator
import com.thomas.passiveeartraining.training.settings.SettingsDiagnostic
import com.thomas.passiveeartraining.training.settings.SettingsRepository
import com.thomas.passiveeartraining.training.settings.SettingsSnapshot
import com.thomas.passiveeartraining.training.settings.SettingsValidationException
import com.thomas.passiveeartraining.training.settings.SetupDraft
import com.thomas.passiveeartraining.training.settings.SetupValidation
import com.thomas.passiveeartraining.training.settings.SetupValidator
import com.thomas.passiveeartraining.training.settings.ThemePreference
import com.thomas.passiveeartraining.training.settings.TrainingMode
import com.thomas.passiveeartraining.training.settings.UserSettings
import kotlin.time.Duration
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.retryWhen

/**
 * Settings stored in one DataStore file.
 *
 * Updates are applied inside `edit`, so the change runs against the newest stored value in the same
 * transaction and two concurrent changes cannot lose each other. Every decoded value is validated:
 * a field that cannot be used falls back to its default and is reported rather than silently
 * replaced, and a stored schema newer than this build is reported instead of being overwritten.
 */
class DataStoreSettingsRepository(
    private val store: DataStore<Preferences>,
    private val allowCorruptionRecovery: java.util.concurrent.atomic.AtomicBoolean? = null
) : SettingsRepository {

    override val settings: Flow<SettingsSnapshot> = store.data.map {
        decode(it)
    }.retryWhen { failure, _ ->
        if (failure is java.io.IOException) {
            emit(
                SettingsSnapshot(
                    UserSettings.DEFAULTS,
                    listOf(
                        SettingsDiagnostic.Unreadable(
                            failure.message ?: "Settings could not be read"
                        )
                    )
                )
            )
            kotlinx.coroutines.delay(1000)
            true
        } else {
            false
        }
    }

    override suspend fun update(change: (UserSettings) -> UserSettings): UserSettings {
        var applied: UserSettings? = null
        store.edit { preferences ->
            val current = decode(preferences)
            // A newer on-disk schema or payload is read-only: writing would silently replace fields
            // this build does not understand with defaults. The explicit reset is the only way past
            // it.
            current.diagnostics.firstOrNull { it.blocksWrites }?.let { blocked ->
                throw SettingsValidationException(blocked.readOnlyMessage())
            }
            val updated = change(current.settings)
            require(updated.modeSetups == current.settings.modeSetups) {
                "Mode setups are written through updateMode, never through a global update"
            }
            val validation = SetupValidator.validate(SetupDraft.from(updated.lastSetup))
            if (validation is SetupValidation.Invalid) {
                throw SettingsValidationException(validation.issues.joinToString { it.explanation })
            }
            write(preferences, updated)
            applied = updated
        }
        return applied ?: UserSettings.DEFAULTS
    }

    override suspend fun updateMode(
        edit: ModeEdit,
        globalChange: ((UserSettings) -> UserSettings)?
    ): ModeWriteResult = writeModeChange(listOf(edit), selectedKind = null, globalChange)

    override suspend fun saveAndSelect(
        edits: List<ModeEdit>,
        selectedKind: TrainingKind,
        globalChange: ((UserSettings) -> UserSettings)?
    ): ModeWriteResult = writeModeChange(edits, selectedKind, globalChange)

    override suspend fun selectKind(kind: TrainingKind): ModeWriteResult =
        writeModeChange(emptyList(), selectedKind = kind, globalChange = null)

    /**
     * Applies every edit and the optional selection in one transaction.
     *
     * Nothing is written when any edit conflicts or fails validation, so a preset race or a
     * protected stored version can never leave a half-applied update (SC6).
     */
    private suspend fun writeModeChange(
        edits: List<ModeEdit>,
        selectedKind: TrainingKind?,
        globalChange: ((UserSettings) -> UserSettings)?
    ): ModeWriteResult {
        var outcome: ModeWriteResult? = null
        store.edit { preferences ->
            val current = decode(preferences)
            val blocked = current.diagnostics.firstOrNull { it.blocksWrites }
            if (blocked != null) {
                outcome = ModeWriteResult.Rejected(blocked.readOnlyMessage())
                return@edit
            }
            val base = globalChange?.invoke(current.settings) ?: current.settings
            require(base.modeSetups == current.settings.modeSetups) {
                "A global change must not touch mode setups"
            }
            val setups = base.modeSetups.toMutableMap()
            for (edit in edits) {
                val existing = setups[edit.kind]
                if (existing == null) {
                    outcome = ModeWriteResult.Rejected(
                        "The ${edit.kind.id} mode has no saved setup to edit yet."
                    )
                    return@edit
                }
                if (existing.revision != edit.expectedRevision) {
                    outcome = ModeWriteResult.Conflict(current)
                    return@edit
                }
                val payload = edit.change(existing)
                val problem = validatePayload(payload)
                if (problem != null) {
                    outcome = ModeWriteResult.Rejected(problem)
                    return@edit
                }
                setups[edit.kind] = SavedModeSetup(existing.revision + 1L, payload)
            }
            val updated = base.copy(
                modeSetups = setups,
                selectedKind = selectedKind ?: base.selectedKind
            )
            write(preferences, updated)
            outcome = ModeWriteResult.Saved(SettingsSnapshot(updated, current.diagnostics))
        }
        return outcome ?: ModeWriteResult.Rejected("The write did not run")
    }

    /** Returns a visible problem with a new payload, or null when it is saveable. */
    private fun validatePayload(payload: ModePayload): String? = when (payload) {
        is ModePayload.Intervals -> {
            val validation = SetupValidator.validate(SetupDraft.from(payload.setup))
            (validation as? SetupValidation.Invalid)?.issues?.joinToString { it.explanation }
        }
        is ModePayload.IntervalWalk -> {
            val validation = IntervalWalkSetupValidator.validate(
                IntervalWalkSetupDraft.from(payload.setup)
            )
            (validation as? SetupValidation.Invalid)?.issues?.joinToString { it.explanation }
        }
        is ModePayload.Chords -> {
            val validation = ChordSetupValidator.validate(ChordSetupDraft.from(payload.setup))
            (validation as? SetupValidation.Invalid)?.issues?.joinToString { it.explanation }
        }
        is ModePayload.Scales -> {
            val validation = ScaleSetupValidator.validate(ScaleSetupDraft.from(payload.setup))
            (validation as? SetupValidation.Invalid)?.issues?.joinToString { it.explanation }
        }
        is ModePayload.Notes -> {
            val validation = NoteSetupValidator.validate(NoteSetupDraft.from(payload.setup))
            (validation as? SetupValidation.Invalid)?.issues?.joinToString { it.explanation }
        }
        is ModePayload.ScaleDegrees -> {
            val validation = ScaleDegreeSetupValidator.validate(
                ScaleDegreeSetupDraft.from(payload.setup)
            )
            (validation as? SetupValidation.Invalid)?.issues?.joinToString { it.explanation }
        }
        is ModePayload.Progressions -> {
            val validation =
                ProgressionSetupValidator.validate(ProgressionSetupDraft.from(payload.setup))
            (validation as? SetupValidation.Invalid)?.issues?.joinToString { it.explanation }
        }
        is ModePayload.ContextualIntervals -> {
            // Contextual-interval answers never speak note names; the write is checked
            // exactly as the setup screen and Start will.
            val validation = ContextualIntervalSetupValidator.validate(
                ContextualIntervalSetupDraft.from(payload.setup)
            )
            (validation as? SetupValidation.Invalid)?.issues?.joinToString { it.explanation }
        }
        is ModePayload.MelodicDictation -> {
            // Melodic answers never speak note names either; the write is checked exactly as
            // the setup screen and Start will, including the provider's cycle-policy refusal.
            val validation = MelodicDictationSetupValidator.validate(
                MelodicDictationSetupDraft.from(payload.setup)
            )
            (validation as? SetupValidation.Invalid)?.issues?.joinToString { it.explanation }
        }
    }

    override suspend fun resetToDefaults(): UserSettings {
        allowCorruptionRecovery?.set(true)
        try {
            store.edit { preferences ->
                preferences.clear()
                write(preferences, UserSettings.DEFAULTS)
            }
            return UserSettings.DEFAULTS
        } finally {
            allowCorruptionRecovery?.set(false)
        }
    }

    /** Decodes stored preferences, collecting a diagnostic for every value that needed recovery. */
    private fun decode(preferences: Preferences): SettingsSnapshot = try {
        decodeValues(preferences)
    } catch (failure: ClassCastException) {
        SettingsSnapshot(
            UserSettings.DEFAULTS,
            listOf(
                SettingsDiagnostic.InvalidValue(
                    "settings",
                    failure.message ?: "Stored preference has the wrong type"
                )
            )
        )
    }

    private fun decodeValues(preferences: Preferences): SettingsSnapshot {
        val diagnostics = mutableListOf<SettingsDiagnostic>()
        val storedVersion = preferences[KEY_SCHEMA_VERSION] ?: UserSettings.CURRENT_SCHEMA_VERSION
        if (storedVersion > UserSettings.CURRENT_SCHEMA_VERSION) {
            // Written by a newer build: reported so the user can decide, never overwritten silently.
            return SettingsSnapshot(
                settings = UserSettings.DEFAULTS,
                diagnostics = listOf(
                    SettingsDiagnostic.NewerSchema(
                        storedVersion,
                        UserSettings.CURRENT_SCHEMA_VERSION
                    )
                )
            )
        }

        fun <T> read(key: Preferences.Key<T>, field: String, fallback: T): T = try {
            preferences[key] ?: fallback
        } catch (failure: ClassCastException) {
            diagnostics +=
                SettingsDiagnostic.InvalidValue(field, failure.message ?: "unreadable")
            fallback
        }

        val schemaVersion = UserSettings.CURRENT_SCHEMA_VERSION
        val theme =
            readEnum(
                read(KEY_THEME, "theme", ThemePreference.SYSTEM.name),
                ThemePreference.entries,
                "theme",
                diagnostics
            ) {
                ThemePreference.SYSTEM
            }
        // Schema 4 removed the Scientific/Solfège naming preference; a stored `spelling` key is
        // deliberately not read at all.
        val visibility =
            readEnum(
                read(
                    KEY_ANSWER_VISIBILITY,
                    "answerVisibility",
                    AnswerVisibility.SHOWN_WHEN_CONSUMED.name
                ),
                AnswerVisibility.entries,
                "answerVisibility",
                diagnostics
            ) {
                AnswerVisibility.SHOWN_WHEN_CONSUMED
            }
        val mode =
            readEnum(
                read(KEY_MODE, "mode", RecipeMode.RECOGNITION.name),
                RecipeMode.entries,
                "mode",
                diagnostics
            ) {
                RecipeMode.RECOGNITION
            }
        val presentation =
            readEnum(
                read(KEY_PRESENTATION, "presentation", IntervalPresentation.ASCENDING.name),
                IntervalPresentation.entries,
                "presentation",
                diagnostics
            ) {
                IntervalPresentation.ASCENDING
            }

        val musicGain = read(KEY_MUSIC_GAIN, "musicGain", UserSettings.DEFAULT_MUSIC_GAIN)
            .takeIf { it in UserSettings.MIN_GAIN..UserSettings.MAX_GAIN }
            ?: UserSettings.DEFAULT_MUSIC_GAIN.also {
                diagnostics +=
                    SettingsDiagnostic.InvalidValue("musicGain", "outside the supported range")
            }
        val voiceGain = read(KEY_VOICE_GAIN, "voiceGain", UserSettings.DEFAULT_VOICE_GAIN)
            .takeIf { it in UserSettings.MIN_GAIN..UserSettings.MAX_GAIN }
            ?: UserSettings.DEFAULT_VOICE_GAIN.also {
                diagnostics +=
                    SettingsDiagnostic.InvalidValue("voiceGain", "outside the supported range")
            }

        val intervalsRevision = read(KEY_INTERVALS_REVISION, "intervalsRevision", 1L)
            .takeIf { it >= 1L }
            ?: 1L.also {
                diagnostics += SettingsDiagnostic.InvalidValue(
                    "intervalsRevision",
                    "a revision must be positive"
                )
            }
        val intervalsConfigVersion = read(
            KEY_INTERVALS_CONFIG_VERSION,
            "intervalsConfigVersion",
            INTERVALS_CONFIG_VERSION
        )
        val setup = if (intervalsConfigVersion > INTERVALS_CONFIG_VERSION) {
            diagnostics += SettingsDiagnostic.NewerModeConfig(
                TrainingKind.INTERVALS,
                intervalsConfigVersion,
                INTERVALS_CONFIG_VERSION
            )
            LastSetup.default()
        } else {
            decodeSetup(
                preferences,
                mode,
                presentation,
                decodeStopPolicy(preferences, diagnostics),
                diagnostics
            )
        }
        val chordsRevision = read(KEY_CHORDS_REVISION, "chordsRevision", 1L)
            .takeIf { it >= 1L }
            ?: 1L.also {
                diagnostics += SettingsDiagnostic.InvalidValue(
                    "chordsRevision",
                    "a revision must be positive"
                )
            }
        val chordsConfigVersion = read(
            KEY_CHORDS_CONFIG_VERSION,
            "chordsConfigVersion",
            CHORDS_CONFIG_VERSION
        )
        val chordSetup = if (chordsConfigVersion > CHORDS_CONFIG_VERSION) {
            diagnostics += SettingsDiagnostic.NewerModeConfig(
                TrainingKind.CHORDS,
                chordsConfigVersion,
                CHORDS_CONFIG_VERSION
            )
            ChordSetup.default()
        } else {
            decodeChordSetup(preferences, diagnostics)
        }
        val scalesRevision = read(KEY_SCALES_REVISION, "scalesRevision", 1L)
            .takeIf { it >= 1L }
            ?: 1L.also {
                diagnostics += SettingsDiagnostic.InvalidValue(
                    "scalesRevision",
                    "a revision must be positive"
                )
            }
        val scalesConfigVersion = read(
            KEY_SCALES_CONFIG_VERSION,
            "scalesConfigVersion",
            SCALES_CONFIG_VERSION
        )
        val scaleSetup = if (scalesConfigVersion > SCALES_CONFIG_VERSION) {
            diagnostics += SettingsDiagnostic.NewerModeConfig(
                TrainingKind.SCALES,
                scalesConfigVersion,
                SCALES_CONFIG_VERSION
            )
            ScaleSetup.default()
        } else {
            decodeScaleSetup(preferences, diagnostics)
        }
        val notesRevision = read(KEY_NOTES_REVISION, "notesRevision", 1L)
            .takeIf { it >= 1L }
            ?: 1L.also {
                diagnostics += SettingsDiagnostic.InvalidValue(
                    "notesRevision",
                    "a revision must be positive"
                )
            }
        val notesConfigVersion = read(
            KEY_NOTES_CONFIG_VERSION,
            "notesConfigVersion",
            NOTE_IDENTIFICATION_CONFIG_VERSION
        )
        val noteSetup = if (notesConfigVersion > NOTE_IDENTIFICATION_CONFIG_VERSION) {
            diagnostics += SettingsDiagnostic.NewerModeConfig(
                TrainingKind.NOTE_IDENTIFICATION,
                notesConfigVersion,
                NOTE_IDENTIFICATION_CONFIG_VERSION
            )
            NoteSetup.default()
        } else {
            decodeNoteSetup(preferences, diagnostics)
        }
        val scaleDegreesRevision = read(KEY_SCALE_DEGREES_REVISION, "scaleDegreesRevision", 1L)
            .takeIf { it >= 1L }
            ?: 1L.also {
                diagnostics += SettingsDiagnostic.InvalidValue(
                    "scaleDegreesRevision",
                    "a revision must be positive"
                )
            }
        val scaleDegreesConfigVersion = read(
            KEY_SCALE_DEGREES_CONFIG_VERSION,
            "scaleDegreesConfigVersion",
            SCALE_DEGREES_CONFIG_VERSION
        )
        val scaleDegreeSetup = if (scaleDegreesConfigVersion > SCALE_DEGREES_CONFIG_VERSION) {
            diagnostics += SettingsDiagnostic.NewerModeConfig(
                TrainingKind.SCALE_DEGREES,
                scaleDegreesConfigVersion,
                SCALE_DEGREES_CONFIG_VERSION
            )
            ScaleDegreeSetup.default()
        } else {
            decodeScaleDegreeSetup(preferences, diagnostics)
        }
        val progressionsRevision = read(KEY_PROGRESSIONS_REVISION, "progressionsRevision", 1L)
            .takeIf { it >= 1L }
            ?: 1L.also {
                diagnostics += SettingsDiagnostic.InvalidValue(
                    "progressionsRevision",
                    "a revision must be positive"
                )
            }
        val progressionsConfigVersion = read(
            KEY_PROGRESSIONS_CONFIG_VERSION,
            "progressionsConfigVersion",
            CHORD_PROGRESSIONS_CONFIG_VERSION
        )
        val progressionSetup = if (progressionsConfigVersion > CHORD_PROGRESSIONS_CONFIG_VERSION) {
            diagnostics += SettingsDiagnostic.NewerModeConfig(
                TrainingKind.CHORD_PROGRESSIONS,
                progressionsConfigVersion,
                CHORD_PROGRESSIONS_CONFIG_VERSION
            )
            ProgressionSetup.default()
        } else {
            decodeProgressionSetup(preferences, diagnostics)
        }
        val contextualIntervalsRevision =
            read(
                KEY_CONTEXTUAL_INTERVALS_REVISION,
                "contextualIntervalsRevision",
                1L
            )
                .takeIf { it >= 1L }
                ?: 1L.also {
                    diagnostics += SettingsDiagnostic.InvalidValue(
                        "contextualIntervalsRevision",
                        "a revision must be positive"
                    )
                }
        val contextualIntervalsConfigVersion = read(
            KEY_CONTEXTUAL_INTERVALS_CONFIG_VERSION,
            "contextualIntervalsConfigVersion",
            INTERVALS_IN_CONTEXT_CONFIG_VERSION
        )
        val contextualIntervalSetup =
            if (contextualIntervalsConfigVersion > INTERVALS_IN_CONTEXT_CONFIG_VERSION) {
                diagnostics += SettingsDiagnostic.NewerModeConfig(
                    TrainingKind.INTERVALS_IN_CONTEXT,
                    contextualIntervalsConfigVersion,
                    INTERVALS_IN_CONTEXT_CONFIG_VERSION
                )
                ContextualIntervalSetup.default()
            } else {
                // Contextual-interval answers never speak note names.
                decodeContextualIntervalSetup(preferences, diagnostics)
            }
        val melodicDictationRevision =
            read(
                KEY_MELODIC_DICTATION_REVISION,
                "melodicDictationRevision",
                1L
            )
                .takeIf { it >= 1L }
                ?: 1L.also {
                    diagnostics += SettingsDiagnostic.InvalidValue(
                        "melodicDictationRevision",
                        "a revision must be positive"
                    )
                }
        val melodicDictationConfigVersion = read(
            KEY_MELODIC_DICTATION_CONFIG_VERSION,
            "melodicDictationConfigVersion",
            MELODIC_DICTATION_CONFIG_VERSION
        )
        val melodicDictationSetup =
            if (melodicDictationConfigVersion > MELODIC_DICTATION_CONFIG_VERSION) {
                diagnostics += SettingsDiagnostic.NewerModeConfig(
                    TrainingKind.MELODIC_DICTATION,
                    melodicDictationConfigVersion,
                    MELODIC_DICTATION_CONFIG_VERSION
                )
                MelodicDictationSetup.default()
            } else {
                // Melodic answers never speak note names either.
                decodeMelodicDictationSetup(preferences, diagnostics)
            }
        val intervalWalkRevision =
            read(KEY_INTERVAL_WALK_REVISION, "intervalWalkRevision", 1L)
                .takeIf { it >= 1L }
                ?: 1L.also {
                    diagnostics += SettingsDiagnostic.InvalidValue(
                        "intervalWalkRevision",
                        "a revision must be positive"
                    )
                }
        val intervalWalkConfigVersion = read(
            KEY_INTERVAL_WALK_CONFIG_VERSION,
            "intervalWalkConfigVersion",
            INTERVAL_WALK_CONFIG_VERSION
        )
        val intervalWalkSetup =
            if (intervalWalkConfigVersion > INTERVAL_WALK_CONFIG_VERSION) {
                diagnostics += SettingsDiagnostic.NewerModeConfig(
                    TrainingKind.INTERVAL_WALK,
                    intervalWalkConfigVersion,
                    INTERVAL_WALK_CONFIG_VERSION
                )
                IntervalWalkSetup.default()
            } else {
                decodeIntervalWalkSetup(preferences, diagnostics)
            }

        val dynamicColor = if (storedVersion < 3) {
            // Schema 3 adoption: older stores defaulted to dynamic colour on, which replaced the
            // brand palette; respect the owner's brand-first default once.
            false
        } else {
            read(KEY_DYNAMIC_COLOR, "dynamicColor", UserSettings.DEFAULTS.dynamicColor)
        }
        val storedSleepMinutes = read(
            KEY_SLEEP_TIMER,
            "sleepTimerMinutes",
            UserSettings.DEFAULTS.sleepTimerMinutes
        )
        val sleepTimerMinutes = if (storedSleepMinutes in 0..UserSettings.MAX_SLEEP_TIMER_MINUTES) {
            storedSleepMinutes
        } else {
            diagnostics += SettingsDiagnostic.InvalidValue(
                "sleepTimerMinutes",
                "A sleep timer is 0..${UserSettings.MAX_SLEEP_TIMER_MINUTES} minutes"
            )
            0
        }

        return SettingsSnapshot(
            settings = UserSettings(
                schemaVersion = schemaVersion,
                theme = theme,
                dynamicColor = dynamicColor,
                sleepTimerMinutes = sleepTimerMinutes,
                answerVisibility = visibility,
                musicGain = musicGain,
                voiceGain = voiceGain,
                selectedKind = decodeSelectedKind(preferences, diagnostics),
                modeSetups = mapOf(
                    TrainingKind.INTERVALS to SavedModeSetup(
                        revision = intervalsRevision,
                        payload = ModePayload.Intervals(setup)
                    ),
                    TrainingKind.INTERVAL_WALK to SavedModeSetup(
                        revision = intervalWalkRevision,
                        payload = ModePayload.IntervalWalk(intervalWalkSetup)
                    ),
                    TrainingKind.CHORDS to SavedModeSetup(
                        revision = chordsRevision,
                        payload = ModePayload.Chords(chordSetup)
                    ),
                    TrainingKind.SCALES to SavedModeSetup(
                        revision = scalesRevision,
                        payload = ModePayload.Scales(scaleSetup)
                    ),
                    TrainingKind.NOTE_IDENTIFICATION to SavedModeSetup(
                        revision = notesRevision,
                        payload = ModePayload.Notes(noteSetup)
                    ),
                    TrainingKind.SCALE_DEGREES to SavedModeSetup(
                        revision = scaleDegreesRevision,
                        payload = ModePayload.ScaleDegrees(scaleDegreeSetup)
                    ),
                    TrainingKind.CHORD_PROGRESSIONS to SavedModeSetup(
                        revision = progressionsRevision,
                        payload = ModePayload.Progressions(progressionSetup)
                    ),
                    TrainingKind.INTERVALS_IN_CONTEXT to SavedModeSetup(
                        revision = contextualIntervalsRevision,
                        payload = ModePayload.ContextualIntervals(contextualIntervalSetup)
                    ),
                    TrainingKind.MELODIC_DICTATION to SavedModeSetup(
                        revision = melodicDictationRevision,
                        payload = ModePayload.MelodicDictation(melodicDictationSetup)
                    )
                ),
                advancedPanelExpanded = read(KEY_ADVANCED_EXPANDED, "advancedPanelExpanded", false)
            ),
            diagnostics = diagnostics
        )
    }

    /**
     * Reads the selected kind.
     *
     * Schema 2 stores the stable kind id; schema 1 stored the legacy enum alias, which is mapped
     * here without treating the selection as a new user choice. An unknown stored id is reported
     * and falls back to the legacy value so a corrupt selection cannot make the drawer dead.
     */
    private fun decodeSelectedKind(
        preferences: Preferences,
        diagnostics: MutableList<SettingsDiagnostic>
    ): TrainingKind {
        preferences[KEY_SELECTED_KIND]?.let { stored ->
            TrainingKind.ofIdOrNull(stored)?.let { return it }
            diagnostics += SettingsDiagnostic.InvalidValue(
                "selectedKind",
                "unknown kind '$stored'"
            )
        }
        val legacy = readEnum(
            preferences[KEY_LAST_MODE] ?: TrainingMode.INTERVALS.name,
            TrainingMode.entries,
            "lastMode",
            diagnostics
        ) {
            TrainingMode.INTERVALS
        }
        return legacy.toTrainingKind()
    }

    /**
     * Decodes the last setup, falling back to the default when the stored one is unusable.
     *
     * A stored setup that would not validate is reported rather than loaded, because starting a
     * session from an invalid configuration is worse than starting from the default.
     */
    private fun decodeSetup(
        preferences: Preferences,
        mode: RecipeMode,
        presentation: IntervalPresentation,
        stopPolicy: SessionStopPolicy,
        diagnostics: MutableList<SettingsDiagnostic>
    ): LastSetup {
        val default = LastSetup.default()
        val intervalIds = (
            preferences[KEY_SETUP_INTERVALS] ?: default.intervalIds.joinToString(
                ","
            )
            )
            .split(",").filter { it.isNotBlank() }
        val rootValues = (preferences[KEY_SETUP_ROOTS] ?: default.rootValues.joinToString(","))
            .split(",").mapNotNull { it.trim().toIntOrNull() }
        val candidate = try {
            LastSetup(
                intervalIds = intervalIds,
                rootValues = rootValues,
                minimumNote = preferences[KEY_SETUP_MIN_NOTE] ?: default.minimumNote,
                maximumNote = preferences[KEY_SETUP_MAX_NOTE] ?: default.maximumNote,
                presentation = presentation,
                mode = mode,
                thinkingSilence = Duration.parse("${preferences[KEY_SETUP_THINKING_MS] ?: 2000}ms"),
                replayCount = preferences[KEY_SETUP_REPLAY] ?: 0,
                stopPolicy = stopPolicy,
                noteDuration = Duration.parse("${preferences[KEY_SETUP_NOTE_MS] ?: 700}ms"),
                answerGap = Duration.parse("${preferences[KEY_SETUP_ANSWER_GAP_MS] ?: 600}ms"),
                repeatGap = Duration.parse("${preferences[KEY_SETUP_REPEAT_GAP_MS] ?: 400}ms"),
                fixedRoot = preferences[KEY_SETUP_FIXED_ROOT],
                presentations = decodePresentations(preferences, presentation, diagnostics),
                melodicGap = Duration.parse(
                    "${preferences[KEY_SETUP_MELODIC_GAP_MS] ?: 100}ms"
                ),
                finalSilence = Duration.parse(
                    "${preferences[KEY_SETUP_FINAL_SILENCE_MS] ?: 2000}ms"
                ),
                seed = preferences[KEY_SETUP_SEED],
                separatorCue = preferences[KEY_SETUP_SEPARATOR_CUE] ?: false
            )
        } catch (failure: IllegalArgumentException) {
            diagnostics +=
                SettingsDiagnostic.InvalidValue("lastSetup", failure.message ?: "invalid")
            return default
        }
        // The definition and the sequence policy are what a session actually builds, so a setup
        // that cannot produce one — including a fixed root that cannot play an interval — is
        // unusable even though its fields look plausible.
        return try {
            val validation = SetupValidator.validate(SetupDraft.from(candidate))
            require(validation is SetupValidation.Valid) {
                (validation as SetupValidation.Invalid).issues.joinToString { it.explanation }
            }
            val policy = candidate.toPolicy()
            if (policy.generatorVersion == 1) {
                com.thomas.passiveeartraining.training.SeededIntervalGenerator
                    .validateFeasibility(policy.definition)
            } else {
                com.thomas.passiveeartraining.training.SeededIntervalGeneratorV2
                    .validateFeasibility(policy)
            }
            candidate
        } catch (failure: IllegalArgumentException) {
            diagnostics +=
                SettingsDiagnostic.InvalidValue("lastSetup", failure.message ?: "invalid")
            default
        }
    }

    /**
     * Decodes the chord setup, falling back to the default when the stored one is unusable.
     *
     * The chord validator runs against the packaged durations, so a stored setup that would not
     * start is reported rather than loaded.
     */
    private fun decodeChordSetup(
        preferences: Preferences,
        diagnostics: MutableList<SettingsDiagnostic>
    ): ChordSetup {
        val default = ChordSetup.default()
        val qualityIds = (preferences[KEY_CHORDS_QUALITIES] ?: default.qualityIds.joinToString(","))
            .split(",").filter { it.isNotBlank() }
        val inversions = (
            preferences[KEY_CHORDS_INVERSIONS] ?: default.inversions.joinToString(
                ","
            )
            )
            .split(",").mapNotNull { it.trim().toIntOrNull() }
        val roots = (preferences[KEY_CHORDS_ROOTS] ?: "")
            .split(",").filter { it.isNotBlank() }
            .mapNotNull { it.trim().toIntOrNull() }
        val chordsMode = readEnum(
            preferences[KEY_CHORDS_MODE] ?: RecipeMode.RECOGNITION.name,
            RecipeMode.entries,
            "chordsMode",
            diagnostics
        ) { RecipeMode.RECOGNITION }
        val chordsPresentation = readEnum(
            preferences[KEY_CHORDS_PRESENTATION] ?: default.presentation.name,
            ChordPresentation.entries,
            "chordsPresentation",
            diagnostics
        ) { default.presentation }
        val chordsAnswerMode = readEnum(
            preferences[KEY_CHORDS_ANSWER_MODE] ?: default.answerMode.name,
            ChordAnswerMode.entries,
            "chordsAnswerMode",
            diagnostics
        ) { default.answerMode }
        val candidate = try {
            ChordSetupDraft(
                qualityIds = qualityIds,
                inversions = inversions,
                rootValues = roots,
                minimumNote = preferences[KEY_CHORDS_MIN_NOTE] ?: default.minimumNote,
                maximumNote = preferences[KEY_CHORDS_MAX_NOTE] ?: default.maximumNote,
                presentation = chordsPresentation,
                answerMode = chordsAnswerMode,
                mode = chordsMode,
                thinkingSilence = Duration.parse(
                    "${preferences[KEY_CHORDS_THINKING_MS] ?: 2000}ms"
                ),
                replayCount = preferences[KEY_CHORDS_REPLAY] ?: 0,
                stopPolicy = decodeStopPolicy(
                    preferences,
                    diagnostics,
                    KEY_CHORDS_STOP_KIND,
                    KEY_CHORDS_STOP_COUNT,
                    KEY_CHORDS_STOP_DURATION_MS,
                    KEY_CHORDS_STOP_DURATION_FINISH
                ),
                noteDuration = Duration.parse("${preferences[KEY_CHORDS_NOTE_MS] ?: 700}ms"),
                answerGap = Duration.parse(
                    "${preferences[KEY_CHORDS_ANSWER_GAP_MS] ?: 600}ms"
                ),
                repeatGap = Duration.parse(
                    "${preferences[KEY_CHORDS_REPEAT_GAP_MS] ?: 400}ms"
                ),
                fixedRoot = preferences[KEY_CHORDS_FIXED_ROOT],
                melodicGap = Duration.parse(
                    "${preferences[KEY_CHORDS_MELODIC_GAP_MS] ?: 100}ms"
                ),
                finalSilence = Duration.parse(
                    "${preferences[KEY_CHORDS_FINAL_SILENCE_MS] ?: 2000}ms"
                ),
                seed = preferences[KEY_CHORDS_SEED],
                separatorCue = preferences[KEY_CHORDS_SEPARATOR_CUE] ?: false
            )
        } catch (failure: IllegalArgumentException) {
            diagnostics +=
                SettingsDiagnostic.InvalidValue("chordSetup", failure.message ?: "invalid")
            return default
        }
        return try {
            val validation = ChordSetupValidator.validate(candidate)
            require(validation is SetupValidation.Valid) {
                (validation as SetupValidation.Invalid).issues.joinToString { it.explanation }
            }
            candidate.toSetup()
        } catch (failure: IllegalArgumentException) {
            diagnostics +=
                SettingsDiagnostic.InvalidValue("chordSetup", failure.message ?: "invalid")
            default
        }
    }

    /**
     * Decodes the scale setup, falling back to the default when the stored one is unusable.
     *
     * The scale validator runs against the packaged durations, so a stored setup that would not
     * start is reported rather than loaded.
     */
    private fun decodeScaleSetup(
        preferences: Preferences,
        diagnostics: MutableList<SettingsDiagnostic>
    ): ScaleSetup {
        val default = ScaleSetup.default()
        val scaleIds = (preferences[KEY_SCALES_IDS] ?: default.scaleIds.joinToString(","))
            .split(",").filter { it.isNotBlank() }
        val roots = (preferences[KEY_SCALES_ROOTS] ?: "")
            .split(",").filter { it.isNotBlank() }
            .mapNotNull { it.trim().toIntOrNull() }
        val scalesMode = readEnum(
            preferences[KEY_SCALES_MODE] ?: RecipeMode.RECOGNITION.name,
            RecipeMode.entries,
            "scalesMode",
            diagnostics
        ) { RecipeMode.RECOGNITION }
        val scalesDirection = readEnum(
            preferences[KEY_SCALES_DIRECTION] ?: default.direction.name,
            ScaleDirection.entries,
            "scalesDirection",
            diagnostics
        ) { default.direction }
        val candidate = try {
            ScaleSetupDraft(
                scaleIds = scaleIds,
                direction = scalesDirection,
                rootValues = roots,
                minimumNote = preferences[KEY_SCALES_MIN_NOTE] ?: default.minimumNote,
                maximumNote = preferences[KEY_SCALES_MAX_NOTE] ?: default.maximumNote,
                mode = scalesMode,
                thinkingSilence = Duration.parse(
                    "${preferences[KEY_SCALES_THINKING_MS] ?: 2000}ms"
                ),
                replayCount = preferences[KEY_SCALES_REPLAY] ?: 0,
                stopPolicy = decodeStopPolicy(
                    preferences,
                    diagnostics,
                    KEY_SCALES_STOP_KIND,
                    KEY_SCALES_STOP_COUNT,
                    KEY_SCALES_STOP_DURATION_MS,
                    KEY_SCALES_STOP_DURATION_FINISH
                ),
                noteDuration = Duration.parse("${preferences[KEY_SCALES_NOTE_MS] ?: 700}ms"),
                answerGap = Duration.parse(
                    "${preferences[KEY_SCALES_ANSWER_GAP_MS] ?: 600}ms"
                ),
                repeatGap = Duration.parse(
                    "${preferences[KEY_SCALES_REPEAT_GAP_MS] ?: 400}ms"
                ),
                fixedRoot = preferences[KEY_SCALES_FIXED_ROOT],
                melodicGap = Duration.parse(
                    "${preferences[KEY_SCALES_MELODIC_GAP_MS] ?: 100}ms"
                ),
                finalSilence = Duration.parse(
                    "${preferences[KEY_SCALES_FINAL_SILENCE_MS] ?: 2000}ms"
                ),
                seed = preferences[KEY_SCALES_SEED],
                separatorCue = preferences[KEY_SCALES_SEPARATOR_CUE] ?: false
            )
        } catch (failure: IllegalArgumentException) {
            diagnostics +=
                SettingsDiagnostic.InvalidValue("scaleSetup", failure.message ?: "invalid")
            return default
        }
        return try {
            val validation = ScaleSetupValidator.validate(candidate)
            require(validation is SetupValidation.Valid) {
                (validation as SetupValidation.Invalid).issues.joinToString { it.explanation }
            }
            val setup = candidate.toSetup()
            // Aliases are accepted once and never persisted again; a following write stores only
            // the canonical catalog ids (M6.2).
            setup.copy(scaleIds = setup.toDefinition().selectedScales.map { it.id })
        } catch (failure: IllegalArgumentException) {
            diagnostics +=
                SettingsDiagnostic.InvalidValue("scaleSetup", failure.message ?: "invalid")
            default
        }
    }

    /**
     * Decodes the note setup, falling back to the default when the stored one is unusable.
     *
     * The note validator runs against the packaged durations, so a stored setup that would not
     * start is reported rather than loaded.
     */
    private fun decodeNoteSetup(
        preferences: Preferences,
        diagnostics: MutableList<SettingsDiagnostic>
    ): NoteSetup {
        val default = NoteSetup.default()
        val pitchClasses = (
            preferences[KEY_NOTES_PITCH_CLASSES] ?: default.pitchClassValues.joinToString(
                ","
            )
            )
            .split(",").filter { it.isNotBlank() }
            .mapNotNull { it.trim().toIntOrNull() }
        val notesMode = readEnum(
            preferences[KEY_NOTES_MODE] ?: RecipeMode.RECOGNITION.name,
            RecipeMode.entries,
            "notesMode",
            diagnostics
        ) { RecipeMode.RECOGNITION }
        val notesAccidental = readEnum(
            preferences[KEY_NOTES_ACCIDENTAL] ?: default.accidental.name,
            MusicSpellingPreference.entries,
            "notesAccidental",
            diagnostics
        ) { default.accidental }
        val candidate = try {
            NoteSetupDraft(
                pitchClassValues = pitchClasses,
                minimumNote = preferences[KEY_NOTES_MIN_NOTE] ?: default.minimumNote,
                maximumNote = preferences[KEY_NOTES_MAX_NOTE] ?: default.maximumNote,
                accidental = notesAccidental,
                speakOctave = preferences[KEY_NOTES_SPEAK_OCTAVE] ?: default.speakOctave,
                mode = notesMode,
                thinkingSilence = Duration.parse(
                    "${preferences[KEY_NOTES_THINKING_MS] ?: 2000}ms"
                ),
                replayCount = preferences[KEY_NOTES_REPLAY] ?: 0,
                stopPolicy = decodeStopPolicy(
                    preferences,
                    diagnostics,
                    KEY_NOTES_STOP_KIND,
                    KEY_NOTES_STOP_COUNT,
                    KEY_NOTES_STOP_DURATION_MS,
                    KEY_NOTES_STOP_DURATION_FINISH
                ),
                noteDuration = Duration.parse("${preferences[KEY_NOTES_NOTE_MS] ?: 700}ms"),
                answerGap = Duration.parse(
                    "${preferences[KEY_NOTES_ANSWER_GAP_MS] ?: 600}ms"
                ),
                repeatGap = Duration.parse(
                    "${preferences[KEY_NOTES_REPEAT_GAP_MS] ?: 400}ms"
                ),
                melodicGap = Duration.parse(
                    "${preferences[KEY_NOTES_MELODIC_GAP_MS] ?: 100}ms"
                ),
                finalSilence = Duration.parse(
                    "${preferences[KEY_NOTES_FINAL_SILENCE_MS] ?: 2000}ms"
                ),
                seed = preferences[KEY_NOTES_SEED],
                separatorCue = preferences[KEY_NOTES_SEPARATOR_CUE] ?: false
            )
        } catch (failure: IllegalArgumentException) {
            diagnostics +=
                SettingsDiagnostic.InvalidValue("noteSetup", failure.message ?: "invalid")
            return default
        }
        return try {
            val validation = NoteSetupValidator.validate(candidate)
            require(validation is SetupValidation.Valid) {
                (validation as SetupValidation.Invalid).issues.joinToString { it.explanation }
            }
            candidate.toSetup()
        } catch (failure: IllegalArgumentException) {
            diagnostics +=
                SettingsDiagnostic.InvalidValue("noteSetup", failure.message ?: "invalid")
            default
        }
    }

    /**
     * Decodes the scale-degree setup, falling back to the default when the stored one is unusable.
     *
     * A stored scale-degree answer format removed with the solfège feature decodes as its numeric
     * equivalent, so an upgraded install keeps the rest of the setup. The scale-degree validator
     * runs against the packaged durations, so a stored NOTE_NAME setup that would not start is
     * reported rather than loaded.
     */
    private fun decodeScaleDegreeSetup(
        preferences: Preferences,
        diagnostics: MutableList<SettingsDiagnostic>
    ): ScaleDegreeSetup {
        val default = ScaleDegreeSetup.default()
        // An absent key list means the default C, exactly as a fresh install reads it; an empty
        // stored CSV means "every key is included" and is preserved.
        val keys = (preferences[KEY_SCALE_DEGREES_KEYS] ?: default.keyValues.joinToString(","))
            .split(",").filter { it.isNotBlank() }
            .mapNotNull { it.trim().toIntOrNull() }
        val tonalityNames = (
            preferences[KEY_SCALE_DEGREES_TONALITIES]
                ?: default.tonalities.joinToString(",") { it.name }
            )
            .split(",").filter { it.isNotBlank() }
            .map { it.trim() }
        val tonalities = tonalityNames.mapNotNull { name ->
            Tonality.entries.firstOrNull { it.name == name }
        }
        if (tonalities.size != tonalityNames.size) {
            diagnostics += SettingsDiagnostic.InvalidValue(
                "scaleDegreesTonalities",
                "unknown tonality"
            )
            return default
        }
        val degrees = (preferences[KEY_SCALE_DEGREES_DEGREES] ?: default.degrees.joinToString(","))
            .split(",").filter { it.isNotBlank() }
            .mapNotNull { it.trim().toIntOrNull() }
        val scaleDegreesMode = readEnum(
            preferences[KEY_SCALE_DEGREES_MODE] ?: RecipeMode.RECOGNITION.name,
            RecipeMode.entries,
            "scaleDegreesMode",
            diagnostics
        ) { RecipeMode.RECOGNITION }
        val scaleDegreesContext = readEnum(
            preferences[KEY_SCALE_DEGREES_CONTEXT] ?: default.context.name,
            ContextKind.entries,
            "scaleDegreesContext",
            diagnostics
        ) { default.context }
        val scaleDegreesAnswerFormat = readEnum(
            migratedDegreeAnswerFormat(
                preferences[KEY_SCALE_DEGREES_ANSWER_FORMAT] ?: default.answerFormat.name
            ),
            DegreeAnswerFormat.entries,
            "scaleDegreesAnswerFormat",
            diagnostics
        ) { default.answerFormat }
        val scaleDegreesSpelling = readEnum(
            preferences[KEY_SCALE_DEGREES_SPELLING] ?: default.spelling.name,
            MusicSpellingPreference.entries,
            "scaleDegreesSpelling",
            diagnostics
        ) { default.spelling }
        val candidate = try {
            ScaleDegreeSetupDraft(
                keyValues = keys,
                tonalities = tonalities,
                degrees = degrees,
                context = scaleDegreesContext,
                contextGap = Duration.parse(
                    "${preferences[KEY_SCALE_DEGREES_CONTEXT_GAP_MS] ?: 500}ms"
                ),
                answerFormat = scaleDegreesAnswerFormat,
                spelling = scaleDegreesSpelling,
                minimumNote = preferences[KEY_SCALE_DEGREES_MIN_NOTE] ?: default.minimumNote,
                maximumNote = preferences[KEY_SCALE_DEGREES_MAX_NOTE] ?: default.maximumNote,
                mode = scaleDegreesMode,
                thinkingSilence = Duration.parse(
                    "${preferences[KEY_SCALE_DEGREES_THINKING_MS] ?: 2000}ms"
                ),
                replayCount = preferences[KEY_SCALE_DEGREES_REPLAY] ?: 0,
                stopPolicy = decodeStopPolicy(
                    preferences,
                    diagnostics,
                    KEY_SCALE_DEGREES_STOP_KIND,
                    KEY_SCALE_DEGREES_STOP_COUNT,
                    KEY_SCALE_DEGREES_STOP_DURATION_MS,
                    KEY_SCALE_DEGREES_STOP_DURATION_FINISH
                ),
                noteDuration = Duration.parse(
                    "${preferences[KEY_SCALE_DEGREES_NOTE_MS] ?: 700}ms"
                ),
                answerGap = Duration.parse(
                    "${preferences[KEY_SCALE_DEGREES_ANSWER_GAP_MS] ?: 600}ms"
                ),
                repeatGap = Duration.parse(
                    "${preferences[KEY_SCALE_DEGREES_REPEAT_GAP_MS] ?: 400}ms"
                ),
                melodicGap = Duration.parse(
                    "${preferences[KEY_SCALE_DEGREES_MELODIC_GAP_MS] ?: 100}ms"
                ),
                finalSilence = Duration.parse(
                    "${preferences[KEY_SCALE_DEGREES_FINAL_SILENCE_MS] ?: 2000}ms"
                ),
                seed = preferences[KEY_SCALE_DEGREES_SEED],
                separatorCue = preferences[KEY_SCALE_DEGREES_SEPARATOR_CUE] ?: false
            )
        } catch (failure: IllegalArgumentException) {
            diagnostics +=
                SettingsDiagnostic.InvalidValue("scaleDegreeSetup", failure.message ?: "invalid")
            return default
        }
        return try {
            val validation = ScaleDegreeSetupValidator.validate(candidate)
            require(validation is SetupValidation.Valid) {
                (validation as SetupValidation.Invalid).issues.joinToString { it.explanation }
            }
            candidate.toSetup()
        } catch (failure: IllegalArgumentException) {
            diagnostics +=
                SettingsDiagnostic.InvalidValue("scaleDegreeSetup", failure.message ?: "invalid")
            default
        }
    }

    /**
     * Decodes the chord-progression setup, falling back to the default when the stored one is
     * unusable.
     *
     * The progression validator runs against the packaged durations, so a stored setup whose
     * selected templates or keys cannot start is reported rather than loaded. A FUNCTIONS or
     * ROOT_MOTION setup also fails here until its clips are packaged, exactly as the setup screen
     * would refuse it, and falls back with the diagnostic.
     */
    private fun decodeProgressionSetup(
        preferences: Preferences,
        diagnostics: MutableList<SettingsDiagnostic>
    ): ProgressionSetup {
        val default = ProgressionSetup.default()
        val chordIds = (
            preferences[KEY_PROGRESSIONS_CHORDS] ?: default.chordIds.joinToString(",")
            )
            .split(",").filter { it.isNotBlank() }
        val fixedRoot = preferences[KEY_PROGRESSIONS_FIXED_ROOT]
        val inversions = (
            preferences[KEY_PROGRESSIONS_ALLOWED_INVERSIONS]
                ?: default.allowedInversions.joinToString(
                    ","
                )
            )
            .split(",").filter { it.isNotBlank() }
            .mapNotNull { it.trim().toIntOrNull() }
        val progressionsMode = readEnum(
            preferences[KEY_PROGRESSIONS_MODE] ?: RecipeMode.RECOGNITION.name,
            RecipeMode.entries,
            "progressionsMode",
            diagnostics
        ) { RecipeMode.RECOGNITION }
        val progressionsVoicing = readEnum(
            preferences[KEY_PROGRESSIONS_VOICING] ?: default.voicing.name,
            ProgressionVoicingMode.entries,
            "progressionsVoicing",
            diagnostics
        ) { default.voicing }
        val progressionsAnswerFormat = readEnum(
            preferences[KEY_PROGRESSIONS_ANSWER_FORMAT] ?: default.answerFormat.name,
            ProgressionAnswerFormat.entries,
            "progressionsAnswerFormat",
            diagnostics
        ) { default.answerFormat }
        val progressionsSpelling = readEnum(
            preferences[KEY_PROGRESSIONS_SPELLING] ?: default.spelling.name,
            MusicSpellingPreference.entries,
            "progressionsSpelling",
            diagnostics
        ) { default.spelling }
        val candidate = try {
            ProgressionSetupDraft(
                chordIds = chordIds,
                fixedRoot = fixedRoot,
                voicing = progressionsVoicing,
                allowedInversions = inversions,
                answerFormat = progressionsAnswerFormat,
                spelling = progressionsSpelling,
                minimumNote = preferences[KEY_PROGRESSIONS_MIN_NOTE] ?: default.minimumNote,
                maximumNote = preferences[KEY_PROGRESSIONS_MAX_NOTE] ?: default.maximumNote,
                mode = progressionsMode,
                thinkingSilence = Duration.parse(
                    "${preferences[KEY_PROGRESSIONS_THINKING_MS] ?: 2000}ms"
                ),
                replayCount = preferences[KEY_PROGRESSIONS_REPLAY] ?: 0,
                stopPolicy = decodeStopPolicy(
                    preferences,
                    diagnostics,
                    KEY_PROGRESSIONS_STOP_KIND,
                    KEY_PROGRESSIONS_STOP_COUNT,
                    KEY_PROGRESSIONS_STOP_DURATION_MS,
                    KEY_PROGRESSIONS_STOP_DURATION_FINISH
                ),
                noteDuration = Duration.parse(
                    "${preferences[KEY_PROGRESSIONS_NOTE_MS] ?: 700}ms"
                ),
                answerGap = Duration.parse(
                    "${preferences[KEY_PROGRESSIONS_ANSWER_GAP_MS] ?: 600}ms"
                ),
                repeatGap = Duration.parse(
                    "${preferences[KEY_PROGRESSIONS_REPEAT_GAP_MS] ?: 400}ms"
                ),
                melodicGap = Duration.parse(
                    "${preferences[KEY_PROGRESSIONS_MELODIC_GAP_MS] ?: 100}ms"
                ),
                finalSilence = Duration.parse(
                    "${preferences[KEY_PROGRESSIONS_FINAL_SILENCE_MS] ?: 2000}ms"
                ),
                seed = preferences[KEY_PROGRESSIONS_SEED],
                separatorCue = preferences[KEY_PROGRESSIONS_SEPARATOR_CUE] ?: false
            )
        } catch (failure: IllegalArgumentException) {
            diagnostics +=
                SettingsDiagnostic.InvalidValue("progressionSetup", failure.message ?: "invalid")
            return default
        }
        // The mode has no label cycle now; an old "after one cycle" choice maps to Until stopped
        // instead of discarding all the other stored choices.
        val normalized = if (candidate.stopPolicy == SessionStopPolicy.AfterCycle) {
            candidate.copy(stopPolicy = SessionStopPolicy.UntilStopped)
        } else {
            candidate
        }
        return try {
            val validation = ProgressionSetupValidator.validate(normalized)
            require(validation is SetupValidation.Valid) {
                (validation as SetupValidation.Invalid).issues.joinToString { it.explanation }
            }
            normalized.toSetup()
        } catch (failure: IllegalArgumentException) {
            diagnostics +=
                SettingsDiagnostic.InvalidValue("progressionSetup", failure.message ?: "invalid")
            default
        }
    }

    /**
     * Decodes the intervals-in-context setup, falling back to the default when the stored one is
     * unusable.
     *
     * The contextual-interval validator runs against the packaged durations, so a stored setup
     * whose selected interval, key or presentation cannot start is reported rather than loaded.
     * Contextual-interval answers never speak note names.
     */
    private fun decodeContextualIntervalSetup(
        preferences: Preferences,
        diagnostics: MutableList<SettingsDiagnostic>
    ): ContextualIntervalSetup {
        val default = ContextualIntervalSetup.default()
        // An absent key list means the default C, exactly as a fresh install reads it; an empty
        // stored CSV means "every key is included" and is preserved.
        val keys = (
            preferences[KEY_CONTEXTUAL_INTERVALS_KEYS] ?: default.keyValues.joinToString(",")
            )
            .split(",").filter { it.isNotBlank() }
            .mapNotNull { it.trim().toIntOrNull() }
        val tonalityNames = (
            preferences[KEY_CONTEXTUAL_INTERVALS_TONALITIES]
                ?: default.tonalities.joinToString(",") { it.name }
            )
            .split(",").filter { it.isNotBlank() }
            .map { it.trim() }
        val tonalities = tonalityNames.mapNotNull { name ->
            Tonality.entries.firstOrNull { it.name == name }
        }
        if (tonalities.size != tonalityNames.size) {
            diagnostics += SettingsDiagnostic.InvalidValue(
                "contextualIntervalsTonalities",
                "unknown tonality"
            )
            return default
        }
        val intervalIds = (
            preferences[KEY_CONTEXTUAL_INTERVALS_INTERVALS]
                ?: default.intervalIds.joinToString(",")
            )
            .split(",").filter { it.isNotBlank() }
        val contextualIntervalsMode = readEnum(
            preferences[KEY_CONTEXTUAL_INTERVALS_MODE] ?: RecipeMode.RECOGNITION.name,
            RecipeMode.entries,
            "contextualIntervalsMode",
            diagnostics
        ) { RecipeMode.RECOGNITION }
        val contextualIntervalsPresentation = readEnum(
            preferences[KEY_CONTEXTUAL_INTERVALS_PRESENTATION] ?: default.presentation.name,
            IntervalPresentation.entries,
            "contextualIntervalsPresentation",
            diagnostics
        ) { default.presentation }
        val contextualIntervalsContext = readEnum(
            preferences[KEY_CONTEXTUAL_INTERVALS_CONTEXT] ?: default.context.name,
            ContextKind.entries,
            "contextualIntervalsContext",
            diagnostics
        ) { default.context }
        val contextualIntervalsAnswerFormat = readEnum(
            preferences[KEY_CONTEXTUAL_INTERVALS_ANSWER_FORMAT] ?: default.answerFormat.name,
            ContextualIntervalAnswerFormat.entries,
            "contextualIntervalsAnswerFormat",
            diagnostics
        ) { default.answerFormat }
        val contextualIntervalsSpelling = readEnum(
            preferences[KEY_CONTEXTUAL_INTERVALS_SPELLING] ?: default.spelling.name,
            MusicSpellingPreference.entries,
            "contextualIntervalsSpelling",
            diagnostics
        ) { default.spelling }
        val candidate = try {
            ContextualIntervalSetupDraft(
                keyValues = keys,
                tonalities = tonalities,
                intervalIds = intervalIds,
                presentation = contextualIntervalsPresentation,
                context = contextualIntervalsContext,
                contextGap = Duration.parse(
                    "${preferences[KEY_CONTEXTUAL_INTERVALS_CONTEXT_GAP_MS] ?: 500}ms"
                ),
                answerFormat = contextualIntervalsAnswerFormat,
                spelling = contextualIntervalsSpelling,
                minimumNote = preferences[KEY_CONTEXTUAL_INTERVALS_MIN_NOTE] ?: default.minimumNote,
                maximumNote = preferences[KEY_CONTEXTUAL_INTERVALS_MAX_NOTE] ?: default.maximumNote,
                mode = contextualIntervalsMode,
                thinkingSilence = Duration.parse(
                    "${preferences[KEY_CONTEXTUAL_INTERVALS_THINKING_MS] ?: 2000}ms"
                ),
                replayCount = preferences[KEY_CONTEXTUAL_INTERVALS_REPLAY] ?: 0,
                stopPolicy = decodeStopPolicy(
                    preferences,
                    diagnostics,
                    KEY_CONTEXTUAL_INTERVALS_STOP_KIND,
                    KEY_CONTEXTUAL_INTERVALS_STOP_COUNT,
                    KEY_CONTEXTUAL_INTERVALS_STOP_DURATION_MS,
                    KEY_CONTEXTUAL_INTERVALS_STOP_DURATION_FINISH
                ),
                noteDuration = Duration.parse(
                    "${preferences[KEY_CONTEXTUAL_INTERVALS_NOTE_MS] ?: 700}ms"
                ),
                answerGap = Duration.parse(
                    "${preferences[KEY_CONTEXTUAL_INTERVALS_ANSWER_GAP_MS] ?: 600}ms"
                ),
                repeatGap = Duration.parse(
                    "${preferences[KEY_CONTEXTUAL_INTERVALS_REPEAT_GAP_MS] ?: 400}ms"
                ),
                melodicGap = Duration.parse(
                    "${preferences[KEY_CONTEXTUAL_INTERVALS_MELODIC_GAP_MS] ?: 100}ms"
                ),
                finalSilence = Duration.parse(
                    "${preferences[KEY_CONTEXTUAL_INTERVALS_FINAL_SILENCE_MS] ?: 2000}ms"
                ),
                seed = preferences[KEY_CONTEXTUAL_INTERVALS_SEED],
                separatorCue = preferences[KEY_CONTEXTUAL_INTERVALS_SEPARATOR_CUE] ?: false
            )
        } catch (failure: IllegalArgumentException) {
            diagnostics += SettingsDiagnostic.InvalidValue(
                "contextualIntervalSetup",
                failure.message ?: "invalid"
            )
            return default
        }
        return try {
            val validation = ContextualIntervalSetupValidator.validate(candidate)
            require(validation is SetupValidation.Valid) {
                (validation as SetupValidation.Invalid).issues.joinToString { it.explanation }
            }
            candidate.toSetup()
        } catch (failure: IllegalArgumentException) {
            diagnostics += SettingsDiagnostic.InvalidValue(
                "contextualIntervalSetup",
                failure.message ?: "invalid"
            )
            default
        }
    }

    /**
     * Decodes the melodic-dictation setup, falling back to the default when the stored one is
     * unusable.
     *
     * The melodic-dictation validator runs against the packaged durations, so a stored setup whose
     * selected key, phrase shape or stop policy cannot start is reported rather than loaded. A
     * stored cycle stop policy therefore falls back with the provider's explanation instead of
     * being accepted silently. A stored melodic answer format removed with the solfège feature
     * decodes as its numeric equivalent.
     */
    private fun decodeMelodicDictationSetup(
        preferences: Preferences,
        diagnostics: MutableList<SettingsDiagnostic>
    ): MelodicDictationSetup {
        val default = MelodicDictationSetup.default()
        // An absent key list means the default C, exactly as a fresh install reads it; an empty
        // stored CSV means "every key is included" and is preserved.
        val keys = (
            preferences[KEY_MELODIC_DICTATION_KEYS] ?: default.keyValues.joinToString(",")
            )
            .split(",").filter { it.isNotBlank() }
            .mapNotNull { it.trim().toIntOrNull() }
        val tonalityNames = (
            preferences[KEY_MELODIC_DICTATION_TONALITIES]
                ?: default.tonalities.joinToString(",") { it.name }
            )
            .split(",").filter { it.isNotBlank() }
            .map { it.trim() }
        val tonalities = tonalityNames.mapNotNull { name ->
            Tonality.entries.firstOrNull { it.name == name }
        }
        if (tonalities.size != tonalityNames.size) {
            diagnostics += SettingsDiagnostic.InvalidValue(
                "melodicDictationTonalities",
                "unknown tonality"
            )
            return default
        }
        val steps = (
            preferences[KEY_MELODIC_DICTATION_ALLOWED_STEPS]
                ?: default.allowedSteps.joinToString(",")
            )
            .split(",").filter { it.isNotBlank() }
            .mapNotNull { it.trim().toIntOrNull() }
        val melodicDictationMode = readEnum(
            preferences[KEY_MELODIC_DICTATION_MODE] ?: RecipeMode.RECOGNITION.name,
            RecipeMode.entries,
            "melodicDictationMode",
            diagnostics
        ) { RecipeMode.RECOGNITION }
        val melodicDictationContext = readEnum(
            preferences[KEY_MELODIC_DICTATION_CONTEXT] ?: default.context.name,
            ContextKind.entries,
            "melodicDictationContext",
            diagnostics
        ) { default.context }
        val melodicDictationBias = readEnum(
            preferences[KEY_MELODIC_DICTATION_BIAS] ?: default.bias.name,
            MelodicDirectionBias.entries,
            "melodicDictationBias",
            diagnostics
        ) { default.bias }
        val melodicDictationAnswerFormat = readEnum(
            migratedMelodicAnswerFormat(
                preferences[KEY_MELODIC_DICTATION_ANSWER_FORMAT] ?: default.answerFormat.name
            ),
            MelodicAnswerFormat.entries,
            "melodicDictationAnswerFormat",
            diagnostics
        ) { default.answerFormat }
        val melodicDictationSpelling = readEnum(
            preferences[KEY_MELODIC_DICTATION_SPELLING] ?: default.spelling.name,
            MusicSpellingPreference.entries,
            "melodicDictationSpelling",
            diagnostics
        ) { default.spelling }
        // A stored zero is the explicit "any degree" choice; an absent key reads the default.
        val storedStartDegree = preferences[KEY_MELODIC_DICTATION_START_DEGREE]
        val startDegree = if (storedStartDegree == null) {
            default.startDegree
        } else {
            storedStartDegree.takeIf { it != MELODIC_ANY_DEGREE }
        }
        val storedEndDegree = preferences[KEY_MELODIC_DICTATION_END_DEGREE]
        val endDegree = if (storedEndDegree == null) {
            default.endDegree
        } else {
            storedEndDegree.takeIf { it != MELODIC_ANY_DEGREE }
        }
        val candidate = try {
            MelodicDictationSetupDraft(
                keyValues = keys,
                tonalities = tonalities,
                context = melodicDictationContext,
                contextGap = Duration.parse(
                    "${preferences[KEY_MELODIC_DICTATION_CONTEXT_GAP_MS] ?: 500}ms"
                ),
                minimumNote = MidiNote.of(
                    preferences[KEY_MELODIC_DICTATION_MIN_NOTE] ?: default.minimumNote.value
                ),
                maximumNote = MidiNote.of(
                    preferences[KEY_MELODIC_DICTATION_MAX_NOTE] ?: default.maximumNote.value
                ),
                phraseLength = preferences[KEY_MELODIC_DICTATION_PHRASE_LENGTH]
                    ?: default.phraseLength,
                startDegree = startDegree,
                endDegree = endDegree,
                allowedSteps = steps,
                maximumLeap = preferences[KEY_MELODIC_DICTATION_MAXIMUM_LEAP]
                    ?: default.maximumLeap,
                bias = melodicDictationBias,
                answerFormat = melodicDictationAnswerFormat,
                spelling = melodicDictationSpelling,
                mode = melodicDictationMode,
                thinkingSilence = Duration.parse(
                    "${preferences[KEY_MELODIC_DICTATION_THINKING_MS] ?: 2000}ms"
                ),
                replayCount = preferences[KEY_MELODIC_DICTATION_REPLAY] ?: 0,
                stopPolicy = decodeStopPolicy(
                    preferences,
                    diagnostics,
                    KEY_MELODIC_DICTATION_STOP_KIND,
                    KEY_MELODIC_DICTATION_STOP_COUNT,
                    KEY_MELODIC_DICTATION_STOP_DURATION_MS,
                    KEY_MELODIC_DICTATION_STOP_DURATION_FINISH
                ),
                noteDuration = Duration.parse(
                    "${preferences[KEY_MELODIC_DICTATION_NOTE_MS] ?: 700}ms"
                ),
                answerGap = Duration.parse(
                    "${preferences[KEY_MELODIC_DICTATION_ANSWER_GAP_MS] ?: 600}ms"
                ),
                repeatGap = Duration.parse(
                    "${preferences[KEY_MELODIC_DICTATION_REPEAT_GAP_MS] ?: 400}ms"
                ),
                melodicGap = Duration.parse(
                    "${preferences[KEY_MELODIC_DICTATION_MELODIC_GAP_MS] ?: 100}ms"
                ),
                finalSilence = Duration.parse(
                    "${preferences[KEY_MELODIC_DICTATION_FINAL_SILENCE_MS] ?: 2000}ms"
                ),
                seed = preferences[KEY_MELODIC_DICTATION_SEED],
                separatorCue = preferences[KEY_MELODIC_DICTATION_SEPARATOR_CUE] ?: false
            )
        } catch (failure: IllegalArgumentException) {
            diagnostics += SettingsDiagnostic.InvalidValue(
                "melodicDictationSetup",
                failure.message ?: "invalid"
            )
            return default
        }
        return try {
            val validation = MelodicDictationSetupValidator.validate(candidate)
            require(validation is SetupValidation.Valid) {
                (validation as SetupValidation.Invalid).issues.joinToString { it.explanation }
            }
            candidate.toSetup()
        } catch (failure: IllegalArgumentException) {
            diagnostics += SettingsDiagnostic.InvalidValue(
                "melodicDictationSetup",
                failure.message ?: "invalid"
            )
            default
        }
    }

    /** Decodes the interval-walk setup, falling back to the default when unusable. */
    private fun decodeIntervalWalkSetup(
        preferences: Preferences,
        diagnostics: MutableList<SettingsDiagnostic>
    ): IntervalWalkSetup {
        val default = IntervalWalkSetup.default()
        val intervalNames = (
            preferences[KEY_INTERVAL_WALK_INTERVALS]
                ?: default.intervalIds.joinToString(",")
            ).split(",").filter { it.isNotBlank() }.map { it.trim() }
        val rootValues = (
            preferences[KEY_INTERVAL_WALK_ROOTS]
                ?: default.rootValues.joinToString(",")
            ).split(",").filter { it.isNotBlank() }.mapNotNull { it.trim().toIntOrNull() }
        val direction = readEnum(
            preferences[KEY_INTERVAL_WALK_DIRECTION] ?: default.direction.name,
            WalkDirection.entries,
            "intervalWalkDirection",
            diagnostics
        ) { default.direction }
        val mode = readEnum(
            preferences[KEY_INTERVAL_WALK_MODE] ?: default.mode.name,
            RecipeMode.entries,
            "intervalWalkMode",
            diagnostics
        ) { default.mode }
        val candidate = try {
            IntervalWalkSetupDraft(
                intervalIds = intervalNames,
                rootValues = rootValues,
                minimumNote = MidiNote.of(
                    preferences[KEY_INTERVAL_WALK_MIN_NOTE] ?: default.minimumNote.value
                ),
                maximumNote = MidiNote.of(
                    preferences[KEY_INTERVAL_WALK_MAX_NOTE] ?: default.maximumNote.value
                ),
                fixedStart = preferences[KEY_INTERVAL_WALK_FIXED_START]?.let(MidiNote::of),
                direction = direction,
                length = preferences[KEY_INTERVAL_WALK_LENGTH] ?: default.length,
                mode = mode,
                thinkingSilence = Duration.parse(
                    "${preferences[KEY_INTERVAL_WALK_THINKING_MS] ?: 3000}ms"
                ),
                replayCount = preferences[KEY_INTERVAL_WALK_REPLAY] ?: default.replayCount,
                stopPolicy = decodeStopPolicy(
                    preferences,
                    diagnostics,
                    KEY_INTERVAL_WALK_STOP_KIND,
                    KEY_INTERVAL_WALK_STOP_COUNT,
                    KEY_INTERVAL_WALK_STOP_DURATION_MS,
                    KEY_INTERVAL_WALK_STOP_DURATION_FINISH
                ),
                noteDuration = Duration.parse(
                    "${preferences[KEY_INTERVAL_WALK_NOTE_MS] ?: 700}ms"
                ),
                answerGap = Duration.parse(
                    "${preferences[KEY_INTERVAL_WALK_ANSWER_GAP_MS] ?: 600}ms"
                ),
                repeatGap = Duration.parse(
                    "${preferences[KEY_INTERVAL_WALK_REPEAT_GAP_MS] ?: 400}ms"
                ),
                melodicGap = Duration.parse(
                    "${preferences[KEY_INTERVAL_WALK_MELODIC_GAP_MS] ?: 100}ms"
                ),
                finalSilence = Duration.parse(
                    "${preferences[KEY_INTERVAL_WALK_FINAL_SILENCE_MS] ?: 2000}ms"
                ),
                seed = preferences[KEY_INTERVAL_WALK_SEED],
                separatorCue = preferences[KEY_INTERVAL_WALK_SEPARATOR_CUE] ?: false
            )
        } catch (failure: IllegalArgumentException) {
            diagnostics += SettingsDiagnostic.InvalidValue(
                "intervalWalkSetup",
                failure.message ?: "invalid"
            )
            return default
        }
        return try {
            val validation = IntervalWalkSetupValidator.validate(candidate)
            require(validation is SetupValidation.Valid) {
                (validation as SetupValidation.Invalid).issues.joinToString { it.explanation }
            }
            candidate.toSetup()
        } catch (failure: IllegalArgumentException) {
            diagnostics += SettingsDiagnostic.InvalidValue(
                "intervalWalkSetup",
                failure.message ?: "invalid"
            )
            default
        }
    }

    /**
     * Reads the enabled presentation set.
     *
     * Absent means a single presentation (the version 1 state), so an old settings file is never
     * upgraded into a mixed policy by accident.
     */
    private fun decodePresentations(
        preferences: Preferences,
        presentation: IntervalPresentation,
        diagnostics: MutableList<SettingsDiagnostic>
    ): List<IntervalPresentation>? {
        val stored = preferences[KEY_SETUP_PRESENTATIONS] ?: return null
        val names = stored.split(",").filter { it.isNotBlank() }
        val decoded = names.mapNotNull { name ->
            IntervalPresentation.entries.firstOrNull { it.name == name }
        }
        if (decoded.size != names.size || decoded.isEmpty() ||
            decoded.distinct().size != decoded.size ||
            decoded != decoded.sortedBy { it.ordinal } ||
            decoded.first() != presentation
        ) {
            diagnostics +=
                SettingsDiagnostic.InvalidValue("presentations", "unknown or inconsistent set")
            return null
        }
        return decoded.takeIf { it.size > 1 }
    }

    private fun decodeStopPolicy(
        preferences: Preferences,
        diagnostics: MutableList<SettingsDiagnostic>,
        kindKey: Preferences.Key<String> = KEY_STOP_KIND,
        countKey: Preferences.Key<Int> = KEY_STOP_COUNT,
        durationKey: Preferences.Key<Long> = KEY_STOP_DURATION_MS,
        finishKey: Preferences.Key<String> = KEY_STOP_DURATION_FINISH
    ): SessionStopPolicy {
        val kind = preferences[kindKey] ?: StopKind.UNTIL_STOPPED.name
        return when (kind) {
            StopKind.UNTIL_STOPPED.name -> SessionStopPolicy.UntilStopped
            StopKind.AFTER_CYCLE.name -> SessionStopPolicy.AfterCycle
            StopKind.DURATION.name -> {
                val limitMs = preferences[durationKey]
                val finishName = preferences[finishKey]
                val finish = DurationFinish.entries.firstOrNull { it.name == finishName }
                if (limitMs == null || limitMs <= 0L || finish == null) {
                    diagnostics +=
                        SettingsDiagnostic.InvalidValue(
                            "stopPolicy",
                            "a duration stop is incomplete"
                        )
                    SessionStopPolicy.UntilStopped
                } else {
                    try {
                        SessionStopPolicy.Duration(
                            limit = Duration.parse("${limitMs}ms"),
                            finish = finish
                        )
                    } catch (failure: IllegalArgumentException) {
                        diagnostics += SettingsDiagnostic.InvalidValue(
                            "stopPolicy",
                            failure.message ?: "invalid"
                        )
                        SessionStopPolicy.UntilStopped
                    }
                }
            }
            StopKind.COMPLETED_ITEMS.name -> {
                val count = preferences[countKey] ?: 10
                try {
                    SessionStopPolicy.CompletedItems(count)
                } catch (failure: IllegalArgumentException) {
                    diagnostics +=
                        SettingsDiagnostic.InvalidValue("stopPolicy", failure.message ?: "invalid")
                    SessionStopPolicy.UntilStopped
                }
            }
            else -> {
                diagnostics += SettingsDiagnostic.InvalidValue("stopPolicy", "unknown kind '$kind'")
                SessionStopPolicy.UntilStopped
            }
        }
    }

    /** Writes settings, including the schema version so a future build can migrate them. */
    private fun write(
        preferences: androidx.datastore.preferences.core.MutablePreferences,
        settings: UserSettings
    ) {
        preferences[KEY_SCHEMA_VERSION] = settings.schemaVersion
        preferences[KEY_THEME] = settings.theme.name
        preferences[KEY_DYNAMIC_COLOR] = settings.dynamicColor
        preferences[KEY_SLEEP_TIMER] = settings.sleepTimerMinutes
        preferences[KEY_ANSWER_VISIBILITY] = settings.answerVisibility.name
        preferences[KEY_SELECTED_KIND] = settings.selectedKind.id
        // Schema 2 owns the selection; the schema-1 alias is removed rather than left to disagree.
        preferences.remove(KEY_LAST_MODE)
        preferences[KEY_MUSIC_GAIN] = settings.musicGain
        preferences[KEY_VOICE_GAIN] = settings.voiceGain
        preferences[KEY_ADVANCED_EXPANDED] = settings.advancedPanelExpanded

        val intervals = settings.modeSetups.getValue(TrainingKind.INTERVALS)
        preferences[KEY_INTERVALS_REVISION] = intervals.revision
        preferences[KEY_INTERVALS_CONFIG_VERSION] = intervals.payload.configVersion

        val setup = settings.lastSetup
        preferences[KEY_SETUP_INTERVALS] = setup.intervalIds.joinToString(",")
        preferences[KEY_SETUP_ROOTS] = setup.rootValues.joinToString(",")
        preferences[KEY_SETUP_MIN_NOTE] = setup.minimumNote
        preferences[KEY_SETUP_MAX_NOTE] = setup.maximumNote
        preferences[KEY_PRESENTATION] = setup.presentation.name
        preferences[KEY_MODE] = setup.mode.name
        preferences[KEY_SETUP_THINKING_MS] = setup.thinkingSilence.inWholeMilliseconds.toInt()
        preferences[KEY_SETUP_REPLAY] = setup.replayCount
        preferences[KEY_SETUP_NOTE_MS] = setup.noteDuration.inWholeMilliseconds.toInt()
        preferences[KEY_SETUP_ANSWER_GAP_MS] = setup.answerGap.inWholeMilliseconds.toInt()
        preferences[KEY_SETUP_REPEAT_GAP_MS] = setup.repeatGap.inWholeMilliseconds.toInt()
        preferences[KEY_SETUP_MELODIC_GAP_MS] = setup.melodicGap.inWholeMilliseconds.toInt()
        preferences[KEY_SETUP_FINAL_SILENCE_MS] = setup.finalSilence.inWholeMilliseconds.toInt()
        preferences[KEY_SETUP_SEPARATOR_CUE] = setup.separatorCue
        // A single presentation is the historical state: the set is removed so it stays implicit.
        if (setup.mixedPresentation) {
            preferences[KEY_SETUP_PRESENTATIONS] =
                setup.enabledPresentations.joinToString(",") { it.name }
        } else {
            preferences.remove(KEY_SETUP_PRESENTATIONS)
        }
        setup.seed?.let { preferences[KEY_SETUP_SEED] = it }
            ?: preferences.remove(KEY_SETUP_SEED)
        // A cleared fixed note is removed rather than written as a sentinel, so an absent key and
        // "draw the root" are the same state.
        setup.fixedRoot?.let { preferences[KEY_SETUP_FIXED_ROOT] = it }
            ?: preferences.remove(KEY_SETUP_FIXED_ROOT)

        // The stop policy belongs to the setup, so it round-trips with the setup the user edited.
        writeStopPolicy(preferences, settings.lastSetup.stopPolicy)

        val intervalWalkSetup = settings.intervalWalkSetup
        val intervalWalkRevision =
            settings.modeSetups[TrainingKind.INTERVAL_WALK]?.revision ?: 1L
        preferences[KEY_INTERVAL_WALK_REVISION] = intervalWalkRevision
        preferences[KEY_INTERVAL_WALK_CONFIG_VERSION] = INTERVAL_WALK_CONFIG_VERSION
        preferences[KEY_INTERVAL_WALK_INTERVALS] =
            intervalWalkSetup.intervalIds.joinToString(",")
        preferences[KEY_INTERVAL_WALK_ROOTS] = intervalWalkSetup.rootValues.joinToString(",")
        preferences[KEY_INTERVAL_WALK_MIN_NOTE] = intervalWalkSetup.minimumNote.value
        preferences[KEY_INTERVAL_WALK_MAX_NOTE] = intervalWalkSetup.maximumNote.value
        intervalWalkSetup.fixedStart?.let {
            preferences[KEY_INTERVAL_WALK_FIXED_START] = it.value
        } ?: preferences.remove(KEY_INTERVAL_WALK_FIXED_START)
        preferences[KEY_INTERVAL_WALK_DIRECTION] = intervalWalkSetup.direction.name
        preferences[KEY_INTERVAL_WALK_LENGTH] = intervalWalkSetup.length
        preferences[KEY_INTERVAL_WALK_MODE] = intervalWalkSetup.mode.name
        preferences[KEY_INTERVAL_WALK_THINKING_MS] =
            intervalWalkSetup.thinkingSilence.inWholeMilliseconds.toInt()
        preferences[KEY_INTERVAL_WALK_REPLAY] = intervalWalkSetup.replayCount
        preferences[KEY_INTERVAL_WALK_NOTE_MS] =
            intervalWalkSetup.noteDuration.inWholeMilliseconds.toInt()
        preferences[KEY_INTERVAL_WALK_ANSWER_GAP_MS] =
            intervalWalkSetup.answerGap.inWholeMilliseconds.toInt()
        preferences[KEY_INTERVAL_WALK_REPEAT_GAP_MS] =
            intervalWalkSetup.repeatGap.inWholeMilliseconds.toInt()
        preferences[KEY_INTERVAL_WALK_MELODIC_GAP_MS] =
            intervalWalkSetup.melodicGap.inWholeMilliseconds.toInt()
        preferences[KEY_INTERVAL_WALK_FINAL_SILENCE_MS] =
            intervalWalkSetup.finalSilence.inWholeMilliseconds.toInt()
        intervalWalkSetup.seed?.let { preferences[KEY_INTERVAL_WALK_SEED] = it }
            ?: preferences.remove(KEY_INTERVAL_WALK_SEED)
        preferences[KEY_INTERVAL_WALK_SEPARATOR_CUE] = intervalWalkSetup.separatorCue
        writeStopPolicy(
            preferences,
            intervalWalkSetup.stopPolicy,
            kindKey = KEY_INTERVAL_WALK_STOP_KIND,
            countKey = KEY_INTERVAL_WALK_STOP_COUNT,
            durationKey = KEY_INTERVAL_WALK_STOP_DURATION_MS,
            finishKey = KEY_INTERVAL_WALK_STOP_DURATION_FINISH
        )

        val chordSetup = settings.chordSetup
        val chordsRevision = settings.modeSetups[TrainingKind.CHORDS]?.revision ?: 1L
        preferences[KEY_CHORDS_REVISION] = chordsRevision
        preferences[KEY_CHORDS_CONFIG_VERSION] = CHORDS_CONFIG_VERSION
        preferences[KEY_CHORDS_QUALITIES] = chordSetup.qualityIds.joinToString(",")
        preferences[KEY_CHORDS_INVERSIONS] = chordSetup.inversions.joinToString(",")
        preferences[KEY_CHORDS_ROOTS] = chordSetup.rootValues.joinToString(",")
        preferences[KEY_CHORDS_MIN_NOTE] = chordSetup.minimumNote
        preferences[KEY_CHORDS_MAX_NOTE] = chordSetup.maximumNote
        preferences[KEY_CHORDS_PRESENTATION] = chordSetup.presentation.name
        preferences[KEY_CHORDS_ANSWER_MODE] = chordSetup.answerMode.name
        preferences[KEY_CHORDS_MODE] = chordSetup.mode.name
        preferences[KEY_CHORDS_THINKING_MS] =
            chordSetup.thinkingSilence.inWholeMilliseconds.toInt()
        preferences[KEY_CHORDS_REPLAY] = chordSetup.replayCount
        preferences[KEY_CHORDS_NOTE_MS] = chordSetup.noteDuration.inWholeMilliseconds.toInt()
        preferences[KEY_CHORDS_ANSWER_GAP_MS] =
            chordSetup.answerGap.inWholeMilliseconds.toInt()
        preferences[KEY_CHORDS_REPEAT_GAP_MS] =
            chordSetup.repeatGap.inWholeMilliseconds.toInt()
        preferences[KEY_CHORDS_MELODIC_GAP_MS] =
            chordSetup.melodicGap.inWholeMilliseconds.toInt()
        preferences[KEY_CHORDS_FINAL_SILENCE_MS] =
            chordSetup.finalSilence.inWholeMilliseconds.toInt()
        preferences[KEY_CHORDS_SEPARATOR_CUE] = chordSetup.separatorCue
        chordSetup.seed?.let { preferences[KEY_CHORDS_SEED] = it }
            ?: preferences.remove(KEY_CHORDS_SEED)
        chordSetup.fixedRoot?.let { preferences[KEY_CHORDS_FIXED_ROOT] = it }
            ?: preferences.remove(KEY_CHORDS_FIXED_ROOT)
        writeStopPolicy(
            preferences,
            chordSetup.stopPolicy,
            kindKey = KEY_CHORDS_STOP_KIND,
            countKey = KEY_CHORDS_STOP_COUNT,
            durationKey = KEY_CHORDS_STOP_DURATION_MS,
            finishKey = KEY_CHORDS_STOP_DURATION_FINISH
        )

        val scaleSetup = settings.scalesSetup
        val scalesRevision = settings.modeSetups[TrainingKind.SCALES]?.revision ?: 1L
        preferences[KEY_SCALES_REVISION] = scalesRevision
        preferences[KEY_SCALES_CONFIG_VERSION] = SCALES_CONFIG_VERSION
        preferences[KEY_SCALES_IDS] = scaleSetup.scaleIds.joinToString(",")
        preferences[KEY_SCALES_DIRECTION] = scaleSetup.direction.name
        preferences[KEY_SCALES_ROOTS] = scaleSetup.rootValues.joinToString(",")
        preferences[KEY_SCALES_MIN_NOTE] = scaleSetup.minimumNote
        preferences[KEY_SCALES_MAX_NOTE] = scaleSetup.maximumNote
        preferences[KEY_SCALES_MODE] = scaleSetup.mode.name
        preferences[KEY_SCALES_THINKING_MS] =
            scaleSetup.thinkingSilence.inWholeMilliseconds.toInt()
        preferences[KEY_SCALES_REPLAY] = scaleSetup.replayCount
        preferences[KEY_SCALES_NOTE_MS] = scaleSetup.noteDuration.inWholeMilliseconds.toInt()
        preferences[KEY_SCALES_ANSWER_GAP_MS] =
            scaleSetup.answerGap.inWholeMilliseconds.toInt()
        preferences[KEY_SCALES_REPEAT_GAP_MS] =
            scaleSetup.repeatGap.inWholeMilliseconds.toInt()
        preferences[KEY_SCALES_MELODIC_GAP_MS] =
            scaleSetup.melodicGap.inWholeMilliseconds.toInt()
        preferences[KEY_SCALES_FINAL_SILENCE_MS] =
            scaleSetup.finalSilence.inWholeMilliseconds.toInt()
        preferences[KEY_SCALES_SEPARATOR_CUE] = scaleSetup.separatorCue
        scaleSetup.seed?.let { preferences[KEY_SCALES_SEED] = it }
            ?: preferences.remove(KEY_SCALES_SEED)
        scaleSetup.fixedRoot?.let { preferences[KEY_SCALES_FIXED_ROOT] = it }
            ?: preferences.remove(KEY_SCALES_FIXED_ROOT)
        writeStopPolicy(
            preferences,
            scaleSetup.stopPolicy,
            kindKey = KEY_SCALES_STOP_KIND,
            countKey = KEY_SCALES_STOP_COUNT,
            durationKey = KEY_SCALES_STOP_DURATION_MS,
            finishKey = KEY_SCALES_STOP_DURATION_FINISH
        )

        val noteSetup = settings.noteSetup
        val notesRevision = settings.modeSetups[TrainingKind.NOTE_IDENTIFICATION]?.revision ?: 1L
        preferences[KEY_NOTES_REVISION] = notesRevision
        preferences[KEY_NOTES_CONFIG_VERSION] = NOTE_IDENTIFICATION_CONFIG_VERSION
        preferences[KEY_NOTES_PITCH_CLASSES] = noteSetup.pitchClassValues.joinToString(",")
        preferences[KEY_NOTES_MIN_NOTE] = noteSetup.minimumNote
        preferences[KEY_NOTES_MAX_NOTE] = noteSetup.maximumNote
        preferences[KEY_NOTES_ACCIDENTAL] = noteSetup.accidental.name
        preferences[KEY_NOTES_SPEAK_OCTAVE] = noteSetup.speakOctave
        preferences[KEY_NOTES_MODE] = noteSetup.mode.name
        preferences[KEY_NOTES_THINKING_MS] =
            noteSetup.thinkingSilence.inWholeMilliseconds.toInt()
        preferences[KEY_NOTES_REPLAY] = noteSetup.replayCount
        preferences[KEY_NOTES_NOTE_MS] = noteSetup.noteDuration.inWholeMilliseconds.toInt()
        preferences[KEY_NOTES_ANSWER_GAP_MS] =
            noteSetup.answerGap.inWholeMilliseconds.toInt()
        preferences[KEY_NOTES_REPEAT_GAP_MS] =
            noteSetup.repeatGap.inWholeMilliseconds.toInt()
        preferences[KEY_NOTES_MELODIC_GAP_MS] =
            noteSetup.melodicGap.inWholeMilliseconds.toInt()
        preferences[KEY_NOTES_FINAL_SILENCE_MS] =
            noteSetup.finalSilence.inWholeMilliseconds.toInt()
        preferences[KEY_NOTES_SEPARATOR_CUE] = noteSetup.separatorCue
        noteSetup.seed?.let { preferences[KEY_NOTES_SEED] = it }
            ?: preferences.remove(KEY_NOTES_SEED)
        writeStopPolicy(
            preferences,
            noteSetup.stopPolicy,
            kindKey = KEY_NOTES_STOP_KIND,
            countKey = KEY_NOTES_STOP_COUNT,
            durationKey = KEY_NOTES_STOP_DURATION_MS,
            finishKey = KEY_NOTES_STOP_DURATION_FINISH
        )

        val scaleDegreeSetup = settings.scaleDegreeSetup
        val scaleDegreesRevision = settings.modeSetups[TrainingKind.SCALE_DEGREES]?.revision ?: 1L
        preferences[KEY_SCALE_DEGREES_REVISION] = scaleDegreesRevision
        preferences[KEY_SCALE_DEGREES_CONFIG_VERSION] = SCALE_DEGREES_CONFIG_VERSION
        // An empty key selection means all twelve, matching the setup screen; the empty CSV is the
        // stored form of "nothing is selected because everything is included".
        preferences[KEY_SCALE_DEGREES_KEYS] = scaleDegreeSetup.keyValues.joinToString(",")
        preferences[KEY_SCALE_DEGREES_TONALITIES] =
            scaleDegreeSetup.tonalities.joinToString(",") { it.name }
        preferences[KEY_SCALE_DEGREES_DEGREES] = scaleDegreeSetup.degrees.joinToString(",")
        preferences[KEY_SCALE_DEGREES_CONTEXT] = scaleDegreeSetup.context.name
        preferences[KEY_SCALE_DEGREES_CONTEXT_GAP_MS] =
            scaleDegreeSetup.contextGap.inWholeMilliseconds.toInt()
        preferences[KEY_SCALE_DEGREES_ANSWER_FORMAT] = scaleDegreeSetup.answerFormat.name
        preferences[KEY_SCALE_DEGREES_SPELLING] = scaleDegreeSetup.spelling.name
        preferences[KEY_SCALE_DEGREES_MIN_NOTE] = scaleDegreeSetup.minimumNote
        preferences[KEY_SCALE_DEGREES_MAX_NOTE] = scaleDegreeSetup.maximumNote
        preferences[KEY_SCALE_DEGREES_MODE] = scaleDegreeSetup.mode.name
        preferences[KEY_SCALE_DEGREES_THINKING_MS] =
            scaleDegreeSetup.thinkingSilence.inWholeMilliseconds.toInt()
        preferences[KEY_SCALE_DEGREES_REPLAY] = scaleDegreeSetup.replayCount
        preferences[KEY_SCALE_DEGREES_NOTE_MS] =
            scaleDegreeSetup.noteDuration.inWholeMilliseconds.toInt()
        preferences[KEY_SCALE_DEGREES_ANSWER_GAP_MS] =
            scaleDegreeSetup.answerGap.inWholeMilliseconds.toInt()
        preferences[KEY_SCALE_DEGREES_REPEAT_GAP_MS] =
            scaleDegreeSetup.repeatGap.inWholeMilliseconds.toInt()
        preferences[KEY_SCALE_DEGREES_MELODIC_GAP_MS] =
            scaleDegreeSetup.melodicGap.inWholeMilliseconds.toInt()
        preferences[KEY_SCALE_DEGREES_FINAL_SILENCE_MS] =
            scaleDegreeSetup.finalSilence.inWholeMilliseconds.toInt()
        preferences[KEY_SCALE_DEGREES_SEPARATOR_CUE] = scaleDegreeSetup.separatorCue
        scaleDegreeSetup.seed?.let { preferences[KEY_SCALE_DEGREES_SEED] = it }
            ?: preferences.remove(KEY_SCALE_DEGREES_SEED)
        writeStopPolicy(
            preferences,
            scaleDegreeSetup.stopPolicy,
            kindKey = KEY_SCALE_DEGREES_STOP_KIND,
            countKey = KEY_SCALE_DEGREES_STOP_COUNT,
            durationKey = KEY_SCALE_DEGREES_STOP_DURATION_MS,
            finishKey = KEY_SCALE_DEGREES_STOP_DURATION_FINISH
        )

        val progressionSetup = settings.progressionSetup
        val progressionsRevision =
            settings.modeSetups[TrainingKind.CHORD_PROGRESSIONS]?.revision ?: 1L
        preferences[KEY_PROGRESSIONS_REVISION] = progressionsRevision
        preferences[KEY_PROGRESSIONS_CONFIG_VERSION] = CHORD_PROGRESSIONS_CONFIG_VERSION
        preferences[KEY_PROGRESSIONS_CHORDS] = progressionSetup.chordIds.joinToString(",")
        progressionSetup.fixedRoot?.let { preferences[KEY_PROGRESSIONS_FIXED_ROOT] = it }
            ?: preferences.remove(KEY_PROGRESSIONS_FIXED_ROOT)
        preferences[KEY_PROGRESSIONS_VOICING] = progressionSetup.voicing.name
        preferences[KEY_PROGRESSIONS_ALLOWED_INVERSIONS] =
            progressionSetup.allowedInversions.joinToString(",")
        preferences[KEY_PROGRESSIONS_ANSWER_FORMAT] = progressionSetup.answerFormat.name
        preferences[KEY_PROGRESSIONS_SPELLING] = progressionSetup.spelling.name
        preferences[KEY_PROGRESSIONS_MIN_NOTE] = progressionSetup.minimumNote
        preferences[KEY_PROGRESSIONS_MAX_NOTE] = progressionSetup.maximumNote
        preferences[KEY_PROGRESSIONS_MODE] = progressionSetup.mode.name
        preferences[KEY_PROGRESSIONS_THINKING_MS] =
            progressionSetup.thinkingSilence.inWholeMilliseconds.toInt()
        preferences[KEY_PROGRESSIONS_REPLAY] = progressionSetup.replayCount
        preferences[KEY_PROGRESSIONS_NOTE_MS] =
            progressionSetup.noteDuration.inWholeMilliseconds.toInt()
        preferences[KEY_PROGRESSIONS_ANSWER_GAP_MS] =
            progressionSetup.answerGap.inWholeMilliseconds.toInt()
        preferences[KEY_PROGRESSIONS_REPEAT_GAP_MS] =
            progressionSetup.repeatGap.inWholeMilliseconds.toInt()
        preferences[KEY_PROGRESSIONS_MELODIC_GAP_MS] =
            progressionSetup.melodicGap.inWholeMilliseconds.toInt()
        preferences[KEY_PROGRESSIONS_FINAL_SILENCE_MS] =
            progressionSetup.finalSilence.inWholeMilliseconds.toInt()
        preferences[KEY_PROGRESSIONS_SEPARATOR_CUE] = progressionSetup.separatorCue
        progressionSetup.seed?.let { preferences[KEY_PROGRESSIONS_SEED] = it }
            ?: preferences.remove(KEY_PROGRESSIONS_SEED)
        writeStopPolicy(
            preferences,
            progressionSetup.stopPolicy,
            kindKey = KEY_PROGRESSIONS_STOP_KIND,
            countKey = KEY_PROGRESSIONS_STOP_COUNT,
            durationKey = KEY_PROGRESSIONS_STOP_DURATION_MS,
            finishKey = KEY_PROGRESSIONS_STOP_DURATION_FINISH
        )

        val contextualIntervalSetup = settings.contextualIntervalSetup
        val contextualIntervalsRevision =
            settings.modeSetups[TrainingKind.INTERVALS_IN_CONTEXT]?.revision ?: 1L
        preferences[KEY_CONTEXTUAL_INTERVALS_REVISION] = contextualIntervalsRevision
        preferences[KEY_CONTEXTUAL_INTERVALS_CONFIG_VERSION] = INTERVALS_IN_CONTEXT_CONFIG_VERSION
        // An empty key selection means all twelve, matching the setup screen; the empty CSV is the
        // stored form of "nothing is selected because everything is included".
        preferences[KEY_CONTEXTUAL_INTERVALS_KEYS] =
            contextualIntervalSetup.keyValues.joinToString(",")
        preferences[KEY_CONTEXTUAL_INTERVALS_TONALITIES] =
            contextualIntervalSetup.tonalities.joinToString(",") { it.name }
        preferences[KEY_CONTEXTUAL_INTERVALS_INTERVALS] =
            contextualIntervalSetup.intervalIds.joinToString(",")
        preferences[KEY_CONTEXTUAL_INTERVALS_PRESENTATION] =
            contextualIntervalSetup.presentation.name
        preferences[KEY_CONTEXTUAL_INTERVALS_CONTEXT] = contextualIntervalSetup.context.name
        preferences[KEY_CONTEXTUAL_INTERVALS_CONTEXT_GAP_MS] =
            contextualIntervalSetup.contextGap.inWholeMilliseconds.toInt()
        preferences[KEY_CONTEXTUAL_INTERVALS_ANSWER_FORMAT] =
            contextualIntervalSetup.answerFormat.name
        preferences[KEY_CONTEXTUAL_INTERVALS_SPELLING] = contextualIntervalSetup.spelling.name
        preferences[KEY_CONTEXTUAL_INTERVALS_MIN_NOTE] = contextualIntervalSetup.minimumNote
        preferences[KEY_CONTEXTUAL_INTERVALS_MAX_NOTE] = contextualIntervalSetup.maximumNote
        preferences[KEY_CONTEXTUAL_INTERVALS_MODE] = contextualIntervalSetup.mode.name
        preferences[KEY_CONTEXTUAL_INTERVALS_THINKING_MS] =
            contextualIntervalSetup.thinkingSilence.inWholeMilliseconds.toInt()
        preferences[KEY_CONTEXTUAL_INTERVALS_REPLAY] = contextualIntervalSetup.replayCount
        preferences[KEY_CONTEXTUAL_INTERVALS_NOTE_MS] =
            contextualIntervalSetup.noteDuration.inWholeMilliseconds.toInt()
        preferences[KEY_CONTEXTUAL_INTERVALS_ANSWER_GAP_MS] =
            contextualIntervalSetup.answerGap.inWholeMilliseconds.toInt()
        preferences[KEY_CONTEXTUAL_INTERVALS_REPEAT_GAP_MS] =
            contextualIntervalSetup.repeatGap.inWholeMilliseconds.toInt()
        preferences[KEY_CONTEXTUAL_INTERVALS_MELODIC_GAP_MS] =
            contextualIntervalSetup.melodicGap.inWholeMilliseconds.toInt()
        preferences[KEY_CONTEXTUAL_INTERVALS_FINAL_SILENCE_MS] =
            contextualIntervalSetup.finalSilence.inWholeMilliseconds.toInt()
        preferences[KEY_CONTEXTUAL_INTERVALS_SEPARATOR_CUE] =
            contextualIntervalSetup.separatorCue
        contextualIntervalSetup.seed?.let { preferences[KEY_CONTEXTUAL_INTERVALS_SEED] = it }
            ?: preferences.remove(KEY_CONTEXTUAL_INTERVALS_SEED)
        writeStopPolicy(
            preferences,
            contextualIntervalSetup.stopPolicy,
            kindKey = KEY_CONTEXTUAL_INTERVALS_STOP_KIND,
            countKey = KEY_CONTEXTUAL_INTERVALS_STOP_COUNT,
            durationKey = KEY_CONTEXTUAL_INTERVALS_STOP_DURATION_MS,
            finishKey = KEY_CONTEXTUAL_INTERVALS_STOP_DURATION_FINISH
        )

        val melodicDictationSetup = settings.melodicDictationSetup
        val melodicDictationRevision =
            settings.modeSetups[TrainingKind.MELODIC_DICTATION]?.revision ?: 1L
        preferences[KEY_MELODIC_DICTATION_REVISION] = melodicDictationRevision
        preferences[KEY_MELODIC_DICTATION_CONFIG_VERSION] = MELODIC_DICTATION_CONFIG_VERSION
        // An empty key selection means all twelve, matching the setup screen; the empty CSV is the
        // stored form of "nothing is selected because everything is included".
        preferences[KEY_MELODIC_DICTATION_KEYS] =
            melodicDictationSetup.keyValues.joinToString(",")
        preferences[KEY_MELODIC_DICTATION_TONALITIES] =
            melodicDictationSetup.tonalities.joinToString(",") { it.name }
        preferences[KEY_MELODIC_DICTATION_CONTEXT] = melodicDictationSetup.context.name
        preferences[KEY_MELODIC_DICTATION_CONTEXT_GAP_MS] =
            melodicDictationSetup.contextGap.inWholeMilliseconds.toInt()
        preferences[KEY_MELODIC_DICTATION_MIN_NOTE] = melodicDictationSetup.minimumNote.value
        preferences[KEY_MELODIC_DICTATION_MAX_NOTE] = melodicDictationSetup.maximumNote.value
        preferences[KEY_MELODIC_DICTATION_PHRASE_LENGTH] = melodicDictationSetup.phraseLength
        // A cleared endpoint is stored as the explicit "any degree" sentinel rather than removed,
        // so an absent key can keep meaning "the default" for a fresh install.
        preferences[KEY_MELODIC_DICTATION_START_DEGREE] =
            melodicDictationSetup.startDegree ?: MELODIC_ANY_DEGREE
        preferences[KEY_MELODIC_DICTATION_END_DEGREE] =
            melodicDictationSetup.endDegree ?: MELODIC_ANY_DEGREE
        preferences[KEY_MELODIC_DICTATION_ALLOWED_STEPS] =
            melodicDictationSetup.allowedSteps.joinToString(",")
        preferences[KEY_MELODIC_DICTATION_MAXIMUM_LEAP] = melodicDictationSetup.maximumLeap
        preferences[KEY_MELODIC_DICTATION_BIAS] = melodicDictationSetup.bias.name
        preferences[KEY_MELODIC_DICTATION_ANSWER_FORMAT] =
            melodicDictationSetup.answerFormat.name
        preferences[KEY_MELODIC_DICTATION_SPELLING] = melodicDictationSetup.spelling.name
        preferences[KEY_MELODIC_DICTATION_MODE] = melodicDictationSetup.mode.name
        preferences[KEY_MELODIC_DICTATION_THINKING_MS] =
            melodicDictationSetup.thinkingSilence.inWholeMilliseconds.toInt()
        preferences[KEY_MELODIC_DICTATION_REPLAY] = melodicDictationSetup.replayCount
        preferences[KEY_MELODIC_DICTATION_NOTE_MS] =
            melodicDictationSetup.noteDuration.inWholeMilliseconds.toInt()
        preferences[KEY_MELODIC_DICTATION_ANSWER_GAP_MS] =
            melodicDictationSetup.answerGap.inWholeMilliseconds.toInt()
        preferences[KEY_MELODIC_DICTATION_REPEAT_GAP_MS] =
            melodicDictationSetup.repeatGap.inWholeMilliseconds.toInt()
        preferences[KEY_MELODIC_DICTATION_MELODIC_GAP_MS] =
            melodicDictationSetup.melodicGap.inWholeMilliseconds.toInt()
        preferences[KEY_MELODIC_DICTATION_FINAL_SILENCE_MS] =
            melodicDictationSetup.finalSilence.inWholeMilliseconds.toInt()
        preferences[KEY_MELODIC_DICTATION_SEPARATOR_CUE] = melodicDictationSetup.separatorCue
        melodicDictationSetup.seed?.let { preferences[KEY_MELODIC_DICTATION_SEED] = it }
            ?: preferences.remove(KEY_MELODIC_DICTATION_SEED)
        writeStopPolicy(
            preferences,
            melodicDictationSetup.stopPolicy,
            kindKey = KEY_MELODIC_DICTATION_STOP_KIND,
            countKey = KEY_MELODIC_DICTATION_STOP_COUNT,
            durationKey = KEY_MELODIC_DICTATION_STOP_DURATION_MS,
            finishKey = KEY_MELODIC_DICTATION_STOP_DURATION_FINISH
        )
    }

    /** Writes one setup's stop policy under the given key set. */
    private fun writeStopPolicy(
        preferences: androidx.datastore.preferences.core.MutablePreferences,
        stop: SessionStopPolicy,
        kindKey: Preferences.Key<String> = KEY_STOP_KIND,
        countKey: Preferences.Key<Int> = KEY_STOP_COUNT,
        durationKey: Preferences.Key<Long> = KEY_STOP_DURATION_MS,
        finishKey: Preferences.Key<String> = KEY_STOP_DURATION_FINISH
    ) {
        when (stop) {
            SessionStopPolicy.UntilStopped -> {
                preferences[kindKey] = StopKind.UNTIL_STOPPED.name
                preferences.remove(durationKey)
                preferences.remove(finishKey)
                preferences.remove(countKey)
            }
            SessionStopPolicy.AfterCycle -> {
                preferences[kindKey] = StopKind.AFTER_CYCLE.name
                preferences.remove(durationKey)
                preferences.remove(finishKey)
                preferences.remove(countKey)
            }
            is SessionStopPolicy.CompletedItems -> {
                preferences[kindKey] = StopKind.COMPLETED_ITEMS.name
                preferences[countKey] = stop.count
                preferences.remove(durationKey)
                preferences.remove(finishKey)
            }
            is SessionStopPolicy.Duration -> {
                preferences[kindKey] = StopKind.DURATION.name
                preferences[durationKey] = stop.limit.inWholeMilliseconds
                preferences[finishKey] = stop.finish.name
                preferences.remove(countKey)
            }
        }
    }

    /** Reads an enum by name, reporting an unknown name instead of throwing. */
    private fun <T : Enum<T>> readEnum(
        stored: String,
        values: List<T>,
        field: String,
        diagnostics: MutableList<SettingsDiagnostic>,
        fallback: () -> T
    ): T = values.firstOrNull { it.name == stored } ?: run {
        diagnostics += SettingsDiagnostic.InvalidValue(field, "unknown value '$stored'")
        fallback()
    }

    /** True when a diagnostic means stored data must not be rewritten automatically. */
    private val SettingsDiagnostic.blocksWrites: Boolean
        get() = this is SettingsDiagnostic.NewerSchema ||
            this is SettingsDiagnostic.NewerModeConfig ||
            this is SettingsDiagnostic.Unreadable

    /** The message shown when a protected stored version blocks a write. */
    private fun SettingsDiagnostic.readOnlyMessage(): String = when (this) {
        is SettingsDiagnostic.NewerSchema ->
            "The stored settings were written by a newer version and are read-only"
        is SettingsDiagnostic.NewerModeConfig ->
            "The stored ${kind.id} setup was written by a newer version and is read-only"
        is SettingsDiagnostic.Unreadable ->
            "The stored settings could not be read and are read-only until reset"
        is SettingsDiagnostic.InvalidValue ->
            "The stored settings need recovery before they can be written"
    }

    private enum class StopKind { UNTIL_STOPPED, AFTER_CYCLE, COMPLETED_ITEMS, DURATION }

    companion object {
        /** The app-private settings file name. */
        const val DEFAULT_FILE_NAME: String = "settings.preferences_pb"

        /**
         * Stored form of the any-degree melodic endpoint choice.
         *
         * A degree is 1..7, so zero is unambiguous; an absent key keeps meaning "the default",
         * which is why "any" needs an explicit stored value.
         */
        private const val MELODIC_ANY_DEGREE: Int = 0

        /**
         * Opens the app-private settings store.
         *
         * The file lives inside the application's data directory. DataStore itself refuses a second
         * active instance over one file, which is what keeps the application's single ownership
         * honest.
         */
        fun create(
            context: Context,
            fileName: String = DEFAULT_FILE_NAME,
            scope: kotlinx.coroutines.CoroutineScope = kotlinx.coroutines.CoroutineScope(
                kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.IO
            )
        ): DataStoreSettingsRepository {
            val recovery = java.util.concurrent.atomic.AtomicBoolean(false)
            val file = context.applicationContext.preferencesDataStoreFile(fileName)
            val store = PreferenceDataStoreFactory.create(
                scope = scope,
                corruptionHandler = ReplaceFileCorruptionHandler { failure ->
                    if (!recovery.get()) throw failure
                    // Only an explicit reset allows replacement. Preserve original bytes privately.
                    val backup = java.io.File(
                        context.noBackupFilesDir,
                        "settings-corrupt-${java.util.UUID.randomUUID()}.preferences_pb"
                    )
                    if (file.exists()) file.copyTo(backup)
                    androidx.datastore.preferences.core.emptyPreferences()
                },
                produceFile = { file }
            )
            return DataStoreSettingsRepository(store, recovery)
        }

        val KEY_SCHEMA_VERSION = intPreferencesKey("schema_version")
        val KEY_THEME = stringPreferencesKey("theme")
        val KEY_DYNAMIC_COLOR = booleanPreferencesKey("dynamic_color")
        val KEY_SLEEP_TIMER = intPreferencesKey("sleep_timer_minutes")
        val KEY_ANSWER_VISIBILITY = stringPreferencesKey("answer_visibility")
        val KEY_LAST_MODE = stringPreferencesKey("last_mode")
        val KEY_SELECTED_KIND = stringPreferencesKey("selected_kind")
        val KEY_INTERVALS_REVISION = longPreferencesKey("intervals_revision")
        val KEY_INTERVALS_CONFIG_VERSION = intPreferencesKey("intervals_config_version")
        val KEY_MUSIC_GAIN = floatPreferencesKey("music_gain")
        val KEY_VOICE_GAIN = floatPreferencesKey("voice_gain")
        val KEY_ADVANCED_EXPANDED = booleanPreferencesKey("advanced_panel_expanded")
        val KEY_SETUP_INTERVALS = stringPreferencesKey("setup_intervals")
        val KEY_SETUP_ROOTS = stringPreferencesKey("setup_roots")
        val KEY_SETUP_MIN_NOTE = intPreferencesKey("setup_min_note")
        val KEY_SETUP_MAX_NOTE = intPreferencesKey("setup_max_note")
        val KEY_PRESENTATION = stringPreferencesKey("setup_presentation")
        val KEY_MODE = stringPreferencesKey("setup_mode")
        val KEY_SETUP_THINKING_MS = intPreferencesKey("setup_thinking_ms")
        val KEY_SETUP_REPLAY = intPreferencesKey("setup_replay")
        val KEY_SETUP_NOTE_MS = intPreferencesKey("setup_note_ms")
        val KEY_SETUP_ANSWER_GAP_MS = intPreferencesKey("setup_answer_gap_ms")
        val KEY_SETUP_REPEAT_GAP_MS = intPreferencesKey("setup_repeat_gap_ms")
        val KEY_SETUP_FIXED_ROOT = intPreferencesKey("setup_fixed_root")
        val KEY_SETUP_PRESENTATIONS = stringPreferencesKey("setup_presentations")
        val KEY_SETUP_MELODIC_GAP_MS = intPreferencesKey("setup_melodic_gap_ms")
        val KEY_SETUP_FINAL_SILENCE_MS = intPreferencesKey("setup_final_silence_ms")
        val KEY_SETUP_SEED = longPreferencesKey("setup_seed")
        val KEY_SETUP_SEPARATOR_CUE = booleanPreferencesKey("setup_separator_cue")
        val KEY_CHORDS_REVISION = longPreferencesKey("chords_revision")
        val KEY_CHORDS_CONFIG_VERSION = intPreferencesKey("chords_config_version")
        val KEY_CHORDS_QUALITIES = stringPreferencesKey("chords_qualities")
        val KEY_CHORDS_INVERSIONS = stringPreferencesKey("chords_inversions")
        val KEY_CHORDS_ROOTS = stringPreferencesKey("chords_roots")
        val KEY_CHORDS_MIN_NOTE = intPreferencesKey("chords_min_note")
        val KEY_CHORDS_MAX_NOTE = intPreferencesKey("chords_max_note")
        val KEY_CHORDS_PRESENTATION = stringPreferencesKey("chords_presentation")
        val KEY_CHORDS_ANSWER_MODE = stringPreferencesKey("chords_answer_mode")
        val KEY_CHORDS_MODE = stringPreferencesKey("chords_mode")
        val KEY_CHORDS_THINKING_MS = intPreferencesKey("chords_thinking_ms")
        val KEY_CHORDS_REPLAY = intPreferencesKey("chords_replay")
        val KEY_CHORDS_NOTE_MS = intPreferencesKey("chords_note_ms")
        val KEY_CHORDS_ANSWER_GAP_MS = intPreferencesKey("chords_answer_gap_ms")
        val KEY_CHORDS_REPEAT_GAP_MS = intPreferencesKey("chords_repeat_gap_ms")
        val KEY_CHORDS_MELODIC_GAP_MS = intPreferencesKey("chords_melodic_gap_ms")
        val KEY_CHORDS_FINAL_SILENCE_MS = intPreferencesKey("chords_final_silence_ms")
        val KEY_CHORDS_SEED = longPreferencesKey("chords_seed")
        val KEY_CHORDS_SEPARATOR_CUE = booleanPreferencesKey("chords_separator_cue")
        val KEY_CHORDS_FIXED_ROOT = intPreferencesKey("chords_fixed_root")
        val KEY_CHORDS_STOP_KIND = stringPreferencesKey("chords_stop_kind")
        val KEY_CHORDS_STOP_COUNT = intPreferencesKey("chords_stop_count")
        val KEY_CHORDS_STOP_DURATION_MS = longPreferencesKey("chords_stop_duration_ms")
        val KEY_CHORDS_STOP_DURATION_FINISH =
            stringPreferencesKey("chords_stop_duration_finish")
        val KEY_SCALES_REVISION = longPreferencesKey("scales_revision")
        val KEY_SCALES_CONFIG_VERSION = intPreferencesKey("scales_config_version")
        val KEY_SCALES_IDS = stringPreferencesKey("scales_scale_ids")
        val KEY_SCALES_DIRECTION = stringPreferencesKey("scales_direction")
        val KEY_SCALES_ROOTS = stringPreferencesKey("scales_roots")
        val KEY_SCALES_MIN_NOTE = intPreferencesKey("scales_min_note")
        val KEY_SCALES_MAX_NOTE = intPreferencesKey("scales_max_note")
        val KEY_SCALES_MODE = stringPreferencesKey("scales_mode")
        val KEY_SCALES_THINKING_MS = intPreferencesKey("scales_thinking_ms")
        val KEY_SCALES_REPLAY = intPreferencesKey("scales_replay")
        val KEY_SCALES_NOTE_MS = intPreferencesKey("scales_note_ms")
        val KEY_SCALES_ANSWER_GAP_MS = intPreferencesKey("scales_answer_gap_ms")
        val KEY_SCALES_REPEAT_GAP_MS = intPreferencesKey("scales_repeat_gap_ms")
        val KEY_SCALES_MELODIC_GAP_MS = intPreferencesKey("scales_melodic_gap_ms")
        val KEY_SCALES_FINAL_SILENCE_MS = intPreferencesKey("scales_final_silence_ms")
        val KEY_SCALES_SEED = longPreferencesKey("scales_seed")
        val KEY_SCALES_SEPARATOR_CUE = booleanPreferencesKey("scales_separator_cue")
        val KEY_SCALES_FIXED_ROOT = intPreferencesKey("scales_fixed_root")
        val KEY_SCALES_STOP_KIND = stringPreferencesKey("scales_stop_kind")
        val KEY_SCALES_STOP_COUNT = intPreferencesKey("scales_stop_count")
        val KEY_SCALES_STOP_DURATION_MS = longPreferencesKey("scales_stop_duration_ms")
        val KEY_SCALES_STOP_DURATION_FINISH =
            stringPreferencesKey("scales_stop_duration_finish")
        val KEY_NOTES_REVISION = longPreferencesKey("notes_revision")
        val KEY_NOTES_CONFIG_VERSION = intPreferencesKey("notes_config_version")
        val KEY_NOTES_PITCH_CLASSES = stringPreferencesKey("notes_pitch_classes")
        val KEY_NOTES_MIN_NOTE = intPreferencesKey("notes_min_note")
        val KEY_NOTES_MAX_NOTE = intPreferencesKey("notes_max_note")
        val KEY_NOTES_ACCIDENTAL = stringPreferencesKey("notes_accidental")
        val KEY_NOTES_SPEAK_OCTAVE = booleanPreferencesKey("notes_speak_octave")
        val KEY_NOTES_MODE = stringPreferencesKey("notes_mode")
        val KEY_NOTES_THINKING_MS = intPreferencesKey("notes_thinking_ms")
        val KEY_NOTES_REPLAY = intPreferencesKey("notes_replay")
        val KEY_NOTES_NOTE_MS = intPreferencesKey("notes_note_ms")
        val KEY_NOTES_ANSWER_GAP_MS = intPreferencesKey("notes_answer_gap_ms")
        val KEY_NOTES_REPEAT_GAP_MS = intPreferencesKey("notes_repeat_gap_ms")
        val KEY_NOTES_MELODIC_GAP_MS = intPreferencesKey("notes_melodic_gap_ms")
        val KEY_NOTES_FINAL_SILENCE_MS = intPreferencesKey("notes_final_silence_ms")
        val KEY_NOTES_SEED = longPreferencesKey("notes_seed")
        val KEY_NOTES_SEPARATOR_CUE = booleanPreferencesKey("notes_separator_cue")
        val KEY_NOTES_STOP_KIND = stringPreferencesKey("notes_stop_kind")
        val KEY_NOTES_STOP_COUNT = intPreferencesKey("notes_stop_count")
        val KEY_NOTES_STOP_DURATION_MS = longPreferencesKey("notes_stop_duration_ms")
        val KEY_NOTES_STOP_DURATION_FINISH =
            stringPreferencesKey("notes_stop_duration_finish")
        val KEY_SCALE_DEGREES_REVISION = longPreferencesKey("scale_degrees_revision")
        val KEY_SCALE_DEGREES_CONFIG_VERSION = intPreferencesKey("scale_degrees_config_version")
        val KEY_SCALE_DEGREES_KEYS = stringPreferencesKey("scale_degrees_keys")
        val KEY_SCALE_DEGREES_TONALITIES = stringPreferencesKey("scale_degrees_tonalities")
        val KEY_SCALE_DEGREES_DEGREES = stringPreferencesKey("scale_degrees_degrees")
        val KEY_SCALE_DEGREES_CONTEXT = stringPreferencesKey("scale_degrees_context")
        val KEY_SCALE_DEGREES_CONTEXT_GAP_MS = intPreferencesKey("scale_degrees_context_gap_ms")
        val KEY_SCALE_DEGREES_ANSWER_FORMAT = stringPreferencesKey("scale_degrees_answer_format")
        val KEY_SCALE_DEGREES_SPELLING = stringPreferencesKey("scale_degrees_spelling")
        val KEY_SCALE_DEGREES_MIN_NOTE = intPreferencesKey("scale_degrees_min_note")
        val KEY_SCALE_DEGREES_MAX_NOTE = intPreferencesKey("scale_degrees_max_note")
        val KEY_SCALE_DEGREES_MODE = stringPreferencesKey("scale_degrees_mode")
        val KEY_SCALE_DEGREES_THINKING_MS = intPreferencesKey("scale_degrees_thinking_ms")
        val KEY_SCALE_DEGREES_REPLAY = intPreferencesKey("scale_degrees_replay")
        val KEY_SCALE_DEGREES_NOTE_MS = intPreferencesKey("scale_degrees_note_ms")
        val KEY_SCALE_DEGREES_ANSWER_GAP_MS = intPreferencesKey("scale_degrees_answer_gap_ms")
        val KEY_SCALE_DEGREES_REPEAT_GAP_MS = intPreferencesKey("scale_degrees_repeat_gap_ms")
        val KEY_SCALE_DEGREES_MELODIC_GAP_MS = intPreferencesKey("scale_degrees_melodic_gap_ms")
        val KEY_SCALE_DEGREES_FINAL_SILENCE_MS =
            intPreferencesKey("scale_degrees_final_silence_ms")
        val KEY_SCALE_DEGREES_SEED = longPreferencesKey("scale_degrees_seed")
        val KEY_SCALE_DEGREES_SEPARATOR_CUE = booleanPreferencesKey("scale_degrees_separator_cue")
        val KEY_SCALE_DEGREES_STOP_KIND = stringPreferencesKey("scale_degrees_stop_kind")
        val KEY_SCALE_DEGREES_STOP_COUNT = intPreferencesKey("scale_degrees_stop_count")
        val KEY_SCALE_DEGREES_STOP_DURATION_MS =
            longPreferencesKey("scale_degrees_stop_duration_ms")
        val KEY_SCALE_DEGREES_STOP_DURATION_FINISH =
            stringPreferencesKey("scale_degrees_stop_duration_finish")
        val KEY_PROGRESSIONS_REVISION = longPreferencesKey("progressions_revision")
        val KEY_PROGRESSIONS_CONFIG_VERSION = intPreferencesKey("progressions_config_version")
        val KEY_PROGRESSIONS_CHORDS = stringPreferencesKey("progressions_chords")
        val KEY_PROGRESSIONS_FIXED_ROOT = intPreferencesKey("progressions_fixed_root")
        val KEY_PROGRESSIONS_VOICING = stringPreferencesKey("progressions_voicing")
        val KEY_PROGRESSIONS_ALLOWED_INVERSIONS =
            stringPreferencesKey("progressions_allowed_inversions")
        val KEY_PROGRESSIONS_ANSWER_FORMAT = stringPreferencesKey("progressions_answer_format")
        val KEY_PROGRESSIONS_SPELLING = stringPreferencesKey("progressions_spelling")
        val KEY_PROGRESSIONS_MIN_NOTE = intPreferencesKey("progressions_min_note")
        val KEY_PROGRESSIONS_MAX_NOTE = intPreferencesKey("progressions_max_note")
        val KEY_PROGRESSIONS_MODE = stringPreferencesKey("progressions_mode")
        val KEY_PROGRESSIONS_THINKING_MS = intPreferencesKey("progressions_thinking_ms")
        val KEY_PROGRESSIONS_REPLAY = intPreferencesKey("progressions_replay")
        val KEY_PROGRESSIONS_NOTE_MS = intPreferencesKey("progressions_note_ms")
        val KEY_PROGRESSIONS_ANSWER_GAP_MS = intPreferencesKey("progressions_answer_gap_ms")
        val KEY_PROGRESSIONS_REPEAT_GAP_MS = intPreferencesKey("progressions_repeat_gap_ms")
        val KEY_PROGRESSIONS_MELODIC_GAP_MS = intPreferencesKey("progressions_melodic_gap_ms")
        val KEY_PROGRESSIONS_FINAL_SILENCE_MS =
            intPreferencesKey("progressions_final_silence_ms")
        val KEY_PROGRESSIONS_SEED = longPreferencesKey("progressions_seed")
        val KEY_PROGRESSIONS_SEPARATOR_CUE = booleanPreferencesKey("progressions_separator_cue")
        val KEY_PROGRESSIONS_STOP_KIND = stringPreferencesKey("progressions_stop_kind")
        val KEY_PROGRESSIONS_STOP_COUNT = intPreferencesKey("progressions_stop_count")
        val KEY_PROGRESSIONS_STOP_DURATION_MS =
            longPreferencesKey("progressions_stop_duration_ms")
        val KEY_PROGRESSIONS_STOP_DURATION_FINISH =
            stringPreferencesKey("progressions_stop_duration_finish")
        val KEY_CONTEXTUAL_INTERVALS_REVISION = longPreferencesKey("contextual_intervals_revision")
        val KEY_CONTEXTUAL_INTERVALS_CONFIG_VERSION =
            intPreferencesKey("contextual_intervals_config_version")
        val KEY_CONTEXTUAL_INTERVALS_KEYS = stringPreferencesKey("contextual_intervals_keys")
        val KEY_CONTEXTUAL_INTERVALS_TONALITIES =
            stringPreferencesKey("contextual_intervals_tonalities")
        val KEY_CONTEXTUAL_INTERVALS_INTERVALS =
            stringPreferencesKey("contextual_intervals_intervals")
        val KEY_CONTEXTUAL_INTERVALS_PRESENTATION =
            stringPreferencesKey("contextual_intervals_presentation")
        val KEY_CONTEXTUAL_INTERVALS_CONTEXT = stringPreferencesKey("contextual_intervals_context")
        val KEY_CONTEXTUAL_INTERVALS_CONTEXT_GAP_MS =
            intPreferencesKey("contextual_intervals_context_gap_ms")
        val KEY_CONTEXTUAL_INTERVALS_ANSWER_FORMAT =
            stringPreferencesKey("contextual_intervals_answer_format")
        val KEY_CONTEXTUAL_INTERVALS_SPELLING =
            stringPreferencesKey("contextual_intervals_spelling")
        val KEY_CONTEXTUAL_INTERVALS_MIN_NOTE = intPreferencesKey("contextual_intervals_min_note")
        val KEY_CONTEXTUAL_INTERVALS_MAX_NOTE = intPreferencesKey("contextual_intervals_max_note")
        val KEY_CONTEXTUAL_INTERVALS_MODE = stringPreferencesKey("contextual_intervals_mode")
        val KEY_CONTEXTUAL_INTERVALS_THINKING_MS =
            intPreferencesKey("contextual_intervals_thinking_ms")
        val KEY_CONTEXTUAL_INTERVALS_REPLAY = intPreferencesKey("contextual_intervals_replay")
        val KEY_CONTEXTUAL_INTERVALS_NOTE_MS = intPreferencesKey("contextual_intervals_note_ms")
        val KEY_CONTEXTUAL_INTERVALS_ANSWER_GAP_MS =
            intPreferencesKey("contextual_intervals_answer_gap_ms")
        val KEY_CONTEXTUAL_INTERVALS_REPEAT_GAP_MS =
            intPreferencesKey("contextual_intervals_repeat_gap_ms")
        val KEY_CONTEXTUAL_INTERVALS_MELODIC_GAP_MS =
            intPreferencesKey("contextual_intervals_melodic_gap_ms")
        val KEY_CONTEXTUAL_INTERVALS_FINAL_SILENCE_MS =
            intPreferencesKey("contextual_intervals_final_silence_ms")
        val KEY_CONTEXTUAL_INTERVALS_SEED = longPreferencesKey("contextual_intervals_seed")
        val KEY_CONTEXTUAL_INTERVALS_SEPARATOR_CUE =
            booleanPreferencesKey("contextual_intervals_separator_cue")
        val KEY_CONTEXTUAL_INTERVALS_STOP_KIND =
            stringPreferencesKey("contextual_intervals_stop_kind")
        val KEY_CONTEXTUAL_INTERVALS_STOP_COUNT =
            intPreferencesKey("contextual_intervals_stop_count")
        val KEY_CONTEXTUAL_INTERVALS_STOP_DURATION_MS =
            longPreferencesKey("contextual_intervals_stop_duration_ms")
        val KEY_CONTEXTUAL_INTERVALS_STOP_DURATION_FINISH =
            stringPreferencesKey("contextual_intervals_stop_duration_finish")
        val KEY_MELODIC_DICTATION_REVISION = longPreferencesKey("melodic_dictation_revision")
        val KEY_MELODIC_DICTATION_CONFIG_VERSION =
            intPreferencesKey("melodic_dictation_config_version")
        val KEY_MELODIC_DICTATION_KEYS = stringPreferencesKey("melodic_dictation_keys")
        val KEY_MELODIC_DICTATION_TONALITIES =
            stringPreferencesKey("melodic_dictation_tonalities")
        val KEY_MELODIC_DICTATION_CONTEXT = stringPreferencesKey("melodic_dictation_context")
        val KEY_MELODIC_DICTATION_CONTEXT_GAP_MS =
            intPreferencesKey("melodic_dictation_context_gap_ms")
        val KEY_MELODIC_DICTATION_MIN_NOTE = intPreferencesKey("melodic_dictation_min_note")
        val KEY_MELODIC_DICTATION_MAX_NOTE = intPreferencesKey("melodic_dictation_max_note")
        val KEY_MELODIC_DICTATION_PHRASE_LENGTH =
            intPreferencesKey("melodic_dictation_phrase_length")
        val KEY_MELODIC_DICTATION_START_DEGREE =
            intPreferencesKey("melodic_dictation_start_degree")
        val KEY_MELODIC_DICTATION_END_DEGREE =
            intPreferencesKey("melodic_dictation_end_degree")
        val KEY_MELODIC_DICTATION_ALLOWED_STEPS =
            stringPreferencesKey("melodic_dictation_allowed_steps")
        val KEY_MELODIC_DICTATION_MAXIMUM_LEAP =
            intPreferencesKey("melodic_dictation_maximum_leap")
        val KEY_MELODIC_DICTATION_BIAS = stringPreferencesKey("melodic_dictation_bias")
        val KEY_MELODIC_DICTATION_ANSWER_FORMAT =
            stringPreferencesKey("melodic_dictation_answer_format")
        val KEY_MELODIC_DICTATION_SPELLING = stringPreferencesKey("melodic_dictation_spelling")
        val KEY_MELODIC_DICTATION_MODE = stringPreferencesKey("melodic_dictation_mode")
        val KEY_MELODIC_DICTATION_THINKING_MS =
            intPreferencesKey("melodic_dictation_thinking_ms")
        val KEY_MELODIC_DICTATION_REPLAY = intPreferencesKey("melodic_dictation_replay")
        val KEY_MELODIC_DICTATION_NOTE_MS = intPreferencesKey("melodic_dictation_note_ms")
        val KEY_MELODIC_DICTATION_ANSWER_GAP_MS =
            intPreferencesKey("melodic_dictation_answer_gap_ms")
        val KEY_MELODIC_DICTATION_REPEAT_GAP_MS =
            intPreferencesKey("melodic_dictation_repeat_gap_ms")
        val KEY_MELODIC_DICTATION_MELODIC_GAP_MS =
            intPreferencesKey("melodic_dictation_melodic_gap_ms")
        val KEY_MELODIC_DICTATION_FINAL_SILENCE_MS =
            intPreferencesKey("melodic_dictation_final_silence_ms")
        val KEY_MELODIC_DICTATION_SEED = longPreferencesKey("melodic_dictation_seed")
        val KEY_MELODIC_DICTATION_SEPARATOR_CUE =
            booleanPreferencesKey("melodic_dictation_separator_cue")
        val KEY_MELODIC_DICTATION_STOP_KIND =
            stringPreferencesKey("melodic_dictation_stop_kind")
        val KEY_MELODIC_DICTATION_STOP_COUNT =
            intPreferencesKey("melodic_dictation_stop_count")
        val KEY_MELODIC_DICTATION_STOP_DURATION_MS =
            longPreferencesKey("melodic_dictation_stop_duration_ms")
        val KEY_MELODIC_DICTATION_STOP_DURATION_FINISH =
            stringPreferencesKey("melodic_dictation_stop_duration_finish")
        val KEY_INTERVAL_WALK_REVISION = longPreferencesKey("interval_walk_revision")
        val KEY_INTERVAL_WALK_CONFIG_VERSION = intPreferencesKey("interval_walk_config_version")
        val KEY_INTERVAL_WALK_INTERVALS = stringPreferencesKey("interval_walk_intervals")
        val KEY_INTERVAL_WALK_ROOTS = stringPreferencesKey("interval_walk_roots")
        val KEY_INTERVAL_WALK_MIN_NOTE = intPreferencesKey("interval_walk_min_note")
        val KEY_INTERVAL_WALK_MAX_NOTE = intPreferencesKey("interval_walk_max_note")
        val KEY_INTERVAL_WALK_FIXED_START = intPreferencesKey("interval_walk_fixed_start")
        val KEY_INTERVAL_WALK_DIRECTION = stringPreferencesKey("interval_walk_direction")
        val KEY_INTERVAL_WALK_LENGTH = intPreferencesKey("interval_walk_length")
        val KEY_INTERVAL_WALK_MODE = stringPreferencesKey("interval_walk_mode")
        val KEY_INTERVAL_WALK_THINKING_MS = intPreferencesKey("interval_walk_thinking_ms")
        val KEY_INTERVAL_WALK_REPLAY = intPreferencesKey("interval_walk_replay")
        val KEY_INTERVAL_WALK_NOTE_MS = intPreferencesKey("interval_walk_note_ms")
        val KEY_INTERVAL_WALK_ANSWER_GAP_MS = intPreferencesKey("interval_walk_answer_gap_ms")
        val KEY_INTERVAL_WALK_REPEAT_GAP_MS = intPreferencesKey("interval_walk_repeat_gap_ms")
        val KEY_INTERVAL_WALK_MELODIC_GAP_MS = intPreferencesKey("interval_walk_melodic_gap_ms")
        val KEY_INTERVAL_WALK_FINAL_SILENCE_MS =
            intPreferencesKey("interval_walk_final_silence_ms")
        val KEY_INTERVAL_WALK_SEED = longPreferencesKey("interval_walk_seed")
        val KEY_INTERVAL_WALK_SEPARATOR_CUE =
            booleanPreferencesKey("interval_walk_separator_cue")
        val KEY_INTERVAL_WALK_STOP_KIND = stringPreferencesKey("interval_walk_stop_kind")
        val KEY_INTERVAL_WALK_STOP_COUNT = intPreferencesKey("interval_walk_stop_count")
        val KEY_INTERVAL_WALK_STOP_DURATION_MS =
            longPreferencesKey("interval_walk_stop_duration_ms")
        val KEY_INTERVAL_WALK_STOP_DURATION_FINISH =
            stringPreferencesKey("interval_walk_stop_duration_finish")
        val KEY_STOP_KIND = stringPreferencesKey("stop_kind")
        val KEY_STOP_COUNT = intPreferencesKey("stop_count")
        val KEY_STOP_DURATION_MS = longPreferencesKey("stop_duration_ms")
        val KEY_STOP_DURATION_FINISH = stringPreferencesKey("stop_duration_finish")
    }
}
