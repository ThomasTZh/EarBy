package com.thomas.passiveeartraining.training.settings

import com.thomas.passiveeartraining.music.IntervalPresentation
import com.thomas.passiveeartraining.music.MidiNote
import com.thomas.passiveeartraining.music.PitchClass
import com.thomas.passiveeartraining.music.SimpleInterval
import com.thomas.passiveeartraining.training.IntervalDefinition
import com.thomas.passiveeartraining.training.provider.TrainingKind
import com.thomas.passiveeartraining.training.provider.toLegacyModeOrNull
import com.thomas.passiveeartraining.training.recipe.RecipeDefinition
import com.thomas.passiveeartraining.training.recipe.RecipeMode
import com.thomas.passiveeartraining.training.recipe.RecipeTiming
import com.thomas.passiveeartraining.training.recipe.SequencePolicy
import com.thomas.passiveeartraining.training.recipe.SessionStopPolicy
import com.thomas.passiveeartraining.training.session.StartRequest
import kotlin.time.Duration
import kotlinx.coroutines.flow.Flow

/** Theme preference. */
enum class ThemePreference { SYSTEM, LIGHT, DARK }

/**
 * The ear-training mode the sidebar has selected.
 *
 * Only [INTERVALS] is implemented; the rest exist so the shell and the persisted selection are
 * ready before their audio and exercise generation are built.
 */
enum class TrainingMode {
    INTERVALS,
    CHORDS,
    SCALES,
    CHORD_PROGRESSIONS,
    PERFECT_PITCH,
    SCALE_DEGREES,
    INTERVALS_IN_CONTEXT,
    MELODIC_DICTATION
}

/** Whether the spoken answer is also shown. */
enum class AnswerVisibility {
    /** Sound only; nothing on screen or in accessibility names the answer. */
    HIDDEN,

    /** Shown once the answer's frame has been consumed. */
    SHOWN_WHEN_CONSUMED
}

/**
 * The setup a session was last started with.
 *
 * It is domain configuration rather than live playback, which is what makes it safe to persist and
 * restore: no position, no timer deadline and no queue state is stored.
 */
data class LastSetup(
    val intervalIds: List<String>,
    val rootValues: List<Int>,
    val minimumNote: Int,
    val maximumNote: Int,
    val presentation: IntervalPresentation,
    val mode: RecipeMode,
    val thinkingSilence: Duration,
    val replayCount: Int,
    val stopPolicy: SessionStopPolicy = SessionStopPolicy.UntilStopped,
    /** One example note's length. */
    val noteDuration: Duration = Duration.parse("700ms"),
    /** Gap between the answer and a following example. */
    val answerGap: Duration = Duration.parse("600ms"),
    /** Gap between repeated examples. */
    val repeatGap: Duration = Duration.parse("400ms"),
    /**
     * An optional fixed absolute root, or null to draw a root from the selected pitch classes.
     *
     * A fixed root is an intersecting constraint with the pitch classes and the range: it must be
     * inside the range and match a selected class, and it must be able to produce the interval in
     * the chosen direction.
     */
    val fixedRoot: Int? = null,
    /**
     * Every enabled presentation when mixed; null means only [presentation] is enabled.
     *
     * Nullable so a caller that changes [presentation] alone cannot leave a stale set behind.
     * Single-presentation setups keep the version 1 draw order exactly.
     */
    val presentations: List<IntervalPresentation>? = null,
    /** The silence between the two notes of one melodic example. */
    val melodicGap: Duration = Duration.parse("100ms"),
    /** The single final silence, once per exercise. */
    val finalSilence: Duration = Duration.parse("2s"),
    /** Optional seed for a reproducible session; null draws and records one at Start. */
    val seed: Long? = null,
    /** Plays the short two-note separator before each exercise. */
    val separatorCue: Boolean = false
) {
    init {
        require(minimumNote <= maximumNote) {
            "A setup range must not be inverted: $minimumNote..$maximumNote"
        }
        require(noteDuration > Duration.ZERO) { "A note duration must be positive" }
        require(answerGap >= Duration.ZERO) { "An answer gap cannot be negative" }
        require(repeatGap >= Duration.ZERO) { "A repeat gap cannot be negative" }
        require(melodicGap >= Duration.ZERO) { "A melodic gap cannot be negative" }
        require(finalSilence >= Duration.ZERO) { "A final silence cannot be negative" }
        require(fixedRoot == null || fixedRoot in MidiNote.MIN_VALUE..MidiNote.MAX_VALUE) {
            "A fixed root must be a MIDI value in ${MidiNote.MIN_VALUE}..${MidiNote.MAX_VALUE}"
        }
        require(enabledPresentations.isNotEmpty()) {
            "A setup must enable at least one presentation"
        }
        require(enabledPresentations.distinct().size == enabledPresentations.size) {
            "Presentations must not repeat: $enabledPresentations"
        }
        require(enabledPresentations == enabledPresentations.sortedBy { it.ordinal }) {
            "Presentations must be sorted by ordinal for reproducible draws: $enabledPresentations"
        }
        require(presentations == null || presentations.first() == presentation) {
            "The primary presentation must be the first enabled one"
        }
    }

    /** The effective enabled presentations: the explicit set, or just [presentation]. */
    val enabledPresentations: List<IntervalPresentation> get() = presentations
        ?: listOf(presentation)

    /** True when more than one presentation may be drawn. */
    val mixedPresentation: Boolean get() = enabledPresentations.size > 1

    /** The effective interval selection: an empty selection means every interval is included. */
    val effectiveIntervalIds: List<String>
        get() = intervalIds.ifEmpty { SimpleInterval.ALL.map { it.id } }

    /** The effective root classes: an empty selection means every root class is included. */
    val effectiveRootValues: List<Int>
        get() = rootValues.ifEmpty { PitchClass.ALL.map { it.value } }

    /**
     * Builds the interval definition this setup describes.
     *
     * An empty interval or root selection is treated as "all options included", which is what the
     * setup screen communicates by leaving every chip unselected.
     *
     * @throws IllegalArgumentException when the setup is not a valid definition; callers validate a
     *   candidate setup before saving it, so a stored setup is expected to be valid.
     */
    fun toDefinition(): IntervalDefinition = IntervalDefinition.of(
        intervalIds = effectiveIntervalIds,
        rootValues = effectiveRootValues,
        minimumNote = MidiNote.of(minimumNote),
        maximumNote = MidiNote.of(maximumNote),
        presentation = presentation
    )

    /**
     * Builds the sequence policy this setup describes.
     *
     * The full enabled set travels; it is never collapsed to one direction. The policy reports its
     * generator version from the set and the fixed root, so a mixed or fixed-root setup is always
     * generated by version 2 and can never fall back to the version 1 draw order.
     */
    fun toPolicy(): SequencePolicy = SequencePolicy(
        definition = toDefinition(),
        presentations = enabledPresentations,
        fixedRoot = fixedRoot?.let { MidiNote.of(it) },
        mixedPresentation = mixedPresentation
    )

    /** Builds the learning recipe this setup describes, including every timing and count. */
    fun toRecipe(): RecipeDefinition = RecipeDefinition(
        mode = mode,
        timing = RecipeTiming(
            noteDuration = noteDuration,
            melodicGap = melodicGap,
            thinkingSilence = thinkingSilence,
            postAnswerSilence = finalSilence,
            answerGap = answerGap,
            repeatGap = repeatGap
        ),
        replayCount = replayCount,
        separatorCue = separatorCue
    )

    /**
     * Builds the full Start request for a session: configuration, optional seed and gains.
     *
     * A null seed is resolved exactly once by the session, which publishes the chosen value so the
     * session can be reproduced. The temporary LastSetup→interval conversion lives here until M2
     * replaces it with per-kind saved setups.
     */
    fun toStartRequest(
        seed: Long?,
        musicGain: Float = UserSettings.DEFAULT_MUSIC_GAIN,
        voiceGain: Float = UserSettings.DEFAULT_VOICE_GAIN
    ): StartRequest = StartRequest(
        setup = com.thomas.passiveeartraining.training.provider.ModeSetup(
            provider = com.thomas.passiveeartraining.training.provider.ProviderConfig.Intervals(
                toPolicy()
            ),
            recipe = toRecipe(),
            seed = seed,
            stopPolicy = stopPolicy
        ),
        musicGain = musicGain,
        voiceGain = voiceGain
    )

    companion object {
        /** The setup a fresh install starts from. */
        fun default(): LastSetup {
            val definition = IntervalDefinition.default()
            return LastSetup(
                intervalIds = definition.selectedIntervalIds,
                rootValues = definition.selectedRoots.map { it.value },
                minimumNote = definition.minimumNoteValue,
                maximumNote = definition.maximumNoteValue,
                presentation = definition.presentation,
                mode = RecipeMode.RECOGNITION,
                thinkingSilence = Duration.parse("2s"),
                replayCount = 0
            )
        }
    }
}

/**
 * Everything the user can configure.
 *
 * Immutable, with validation on construction, so an invalid combination cannot be held in memory and
 * cannot be persisted by accident. Global preferences are separate from each mode's saved musical
 * setup; the selected kind may reference a mode whose provider is not implemented yet, in which
 * case it simply has no saved setup (SC7/D11).
 */
data class UserSettings(
    val schemaVersion: Int = CURRENT_SCHEMA_VERSION,
    val theme: ThemePreference = ThemePreference.SYSTEM,
    /**
     * Wallpaper-derived dynamic colour (Android 12+). Off by default so the brand palette is the
     * app's theme; the owner can opt in from Settings.
     */
    val dynamicColor: Boolean = false,
    /**
     * The sleep timer's remembered choice in minutes; zero means no timer. A fresh session
     * arms this value automatically, so the last choice survives restarts.
     */
    val sleepTimerMinutes: Int = 0,
    val answerVisibility: AnswerVisibility = AnswerVisibility.SHOWN_WHEN_CONSUMED,
    val musicGain: Float = DEFAULT_MUSIC_GAIN,
    val voiceGain: Float = DEFAULT_VOICE_GAIN,
    /** The ear-training kind the sidebar has selected; a known kind may be unavailable. */
    val selectedKind: TrainingKind = TrainingKind.INTERVALS,
    /** Saved musical setups by kind; the intervals entry always exists. */
    val modeSetups: Map<TrainingKind, SavedModeSetup> = defaultModeSetups(),
    /** Whether the advanced panel is expanded; a display preference, never a musical setting. */
    val advancedPanelExpanded: Boolean = false
) {
    init {
        require(schemaVersion > 0) { "A schema version must be positive but was $schemaVersion" }
        require(sleepTimerMinutes in 0..MAX_SLEEP_TIMER_MINUTES) {
            "A sleep timer is 0..$MAX_SLEEP_TIMER_MINUTES minutes but was $sleepTimerMinutes"
        }
        require(musicGain in MIN_GAIN..MAX_GAIN) {
            "A music gain is $MIN_GAIN..$MAX_GAIN but was $musicGain"
        }
        require(voiceGain in MIN_GAIN..MAX_GAIN) {
            "A voice gain is $MIN_GAIN..$MAX_GAIN but was $voiceGain"
        }
        require(modeSetups.containsKey(TrainingKind.INTERVALS)) {
            "The intervals mode setup must always exist"
        }
    }

    /**
     * The intervals setup as the existing screens edit it.
     *
     * A temporary M2 adapter: setup screens are re-keyed by kind in M2.4, and playback already reads
     * [selectedKind]/[modeSetups] directly.
     */
    val lastSetup: LastSetup
        get() = (modeSetups[TrainingKind.INTERVALS]?.payload as? ModePayload.Intervals)
            ?.setup ?: LastSetup.default()

    /** The interval-walk setup as its screen edits it. */
    val intervalWalkSetup: IntervalWalkSetup
        get() = (modeSetups[TrainingKind.INTERVAL_WALK]?.payload as? ModePayload.IntervalWalk)
            ?.setup ?: IntervalWalkSetup.default()

    /** The chords setup as the chord screens edit it (M5). */
    val chordSetup: ChordSetup
        get() = (modeSetups[TrainingKind.CHORDS]?.payload as? ModePayload.Chords)
            ?.setup ?: ChordSetup.default()

    /** The scale setup as the scale screens edit it (M6). */
    val scalesSetup: ScaleSetup
        get() = (modeSetups[TrainingKind.SCALES]?.payload as? ModePayload.Scales)
            ?.setup ?: ScaleSetup.default()

    /** The note setup as the perfect-pitch screens edit it (M7). */
    val noteSetup: NoteSetup
        get() = (modeSetups[TrainingKind.NOTE_IDENTIFICATION]?.payload as? ModePayload.Notes)
            ?.setup ?: NoteSetup.default()

    /** The scale-degree setup as the scale-degree screens edit it (M8). */
    val scaleDegreeSetup: ScaleDegreeSetup
        get() = (modeSetups[TrainingKind.SCALE_DEGREES]?.payload as? ModePayload.ScaleDegrees)
            ?.setup ?: ScaleDegreeSetup.default()

    /** The chord-progression setup as the chord-progression screens edit it (M9A). */
    val progressionSetup: ProgressionSetup
        get() = (modeSetups[TrainingKind.CHORD_PROGRESSIONS]?.payload as? ModePayload.Progressions)
            ?.setup ?: ProgressionSetup.default()

    /** The contextual-interval setup as the intervals-in-context screens edit it (M9B). */
    val contextualIntervalSetup: ContextualIntervalSetup
        get() = (
            modeSetups[TrainingKind.INTERVALS_IN_CONTEXT]?.payload
                as? ModePayload.ContextualIntervals
            )
            ?.setup ?: ContextualIntervalSetup.default()

    /** The melodic-dictation setup as the melodic-dictation screens edit it (M10). */
    val melodicDictationSetup: MelodicDictationSetup
        get() = (
            modeSetups[TrainingKind.MELODIC_DICTATION]?.payload
                as? ModePayload.MelodicDictation
            )
            ?.setup ?: MelodicDictationSetup.default()

    /** The legacy selection alias for screens not yet re-keyed by stable kind IDs (M2.4). */
    val lastMode: TrainingMode
        get() = selectedKind.toLegacyModeOrNull() ?: TrainingMode.INTERVALS

    companion object {
        /**
         * Schema version of the stored settings.
         *
         * A stored version below this is migrated; a version above it means the file was written by a
         * newer build and is reported rather than silently discarded.
         */
        /**
         * Schema 3 turned [dynamicColor] off once for stores written before it: the old default
         * was on, so upgraded installs kept wallpaper colours even though the brand palette is
         * now the app's theme. The Settings switch still lets the owner opt back in.
         *
         * Schema 4 removed the Scientific/Solfège naming preference and the movable-do and
         * degree-and-syllable answer formats. Decoding ignores the stored `spelling` key, and a
         * stored `movable_do`/`degree_and_syllable` scale-degree format or a stored
         * `movable_do_sequence` melodic format decodes as its numeric equivalent (DEGREE or
         * DEGREE_SEQUENCE), so an upgraded install keeps every remaining setting.
         */
        const val CURRENT_SCHEMA_VERSION: Int = 4

        /** The longest selectable sleep timer, one day. */
        const val MAX_SLEEP_TIMER_MINUTES: Int = 1440

        const val MIN_GAIN: Float = 0f

        /**
         * Highest stored gain. The reviewed music normal is 25% of this 4x scale; the upper
         * range is user headroom, and very high music settings can distort on dense material.
         */
        const val MAX_GAIN: Float = 4f

        /** The accepted default, matching the reviewed asset balance. */
        const val DEFAULT_MUSIC_GAIN: Float = 1f
        const val DEFAULT_VOICE_GAIN: Float = 0.25f

        /** Settings a fresh install starts from. */
        val DEFAULTS: UserSettings = UserSettings()
    }
}

/** Why stored settings could not be used. */
sealed interface SettingsDiagnostic {
    /** The stored schema version is newer than this build understands. */
    data class NewerSchema(val stored: Int, val supported: Int) : SettingsDiagnostic

    /**
     * A stored mode payload has a configuration version newer than this build understands.
     *
     * The entry is protected from automatic writes, including autosave and migration; the user
     * decides how to recover rather than having a newer setup silently replaced.
     */
    data class NewerModeConfig(val kind: TrainingKind, val stored: Int, val supported: Int) :
        SettingsDiagnostic

    /** A stored value was unusable and the field fell back to its default. */
    data class InvalidValue(val field: String, val detail: String) : SettingsDiagnostic

    /** The stored data could not be read at all. */
    data class Unreadable(val detail: String) : SettingsDiagnostic
}

/** Settings plus any diagnostic from reading them, so recovery is never silent. */
data class SettingsSnapshot(
    val settings: UserSettings,
    val diagnostics: List<SettingsDiagnostic> = emptyList()
) {
    /** True when the stored settings needed recovery or were written by a newer build. */
    val needsAttention: Boolean get() = diagnostics.isNotEmpty()
}

/**
 * Reads and writes user settings.
 *
 * Updates are atomic: a caller describes the change and the repository applies it, so a
 * read-modify-write race cannot lose a concurrent change.
 */
interface SettingsRepository {
    /** Current settings, updated as they change. */
    val settings: Flow<SettingsSnapshot>

    /**
     * Applies [change] to the stored settings atomically.
     *
     * The change runs against the newest stored value inside the same transaction, so two concurrent
     * changes both land. This entry point is for global preferences; a change that mutates
     * [UserSettings.modeSetups] is refused so every mode write goes through the revision checks.
     */
    suspend fun update(change: (UserSettings) -> UserSettings): UserSettings

    /**
     * Applies one mode edit guarded by [ModeEdit.expectedRevision].
     *
     * On a revision mismatch nothing is written and the current snapshot is returned in
     * [ModeWriteResult.Conflict]; the revision of the changed mode increases by exactly one.
     * An optional [globalChange] (for example an advanced-panel flag or captured preset gains)
     * is applied in the same transaction and must not touch mode setups.
     */
    suspend fun updateMode(
        edit: ModeEdit,
        globalChange: ((UserSettings) -> UserSettings)? = null
    ): ModeWriteResult

    /**
     * Persists the selected kind and commits every valid outgoing edit in one transaction.
     *
     * A failed or conflicted write leaves the stored selection unchanged; nothing is applied
     * partially (SC6).
     */
    suspend fun saveAndSelect(
        edits: List<ModeEdit>,
        selectedKind: TrainingKind,
        globalChange: ((UserSettings) -> UserSettings)? = null
    ): ModeWriteResult

    /** Selects [kind] without touching any mode payload. */
    suspend fun selectKind(kind: TrainingKind): ModeWriteResult

    /**
     * Resets every setting to its default.
     *
     * Used by the explicit recovery action; it is never called automatically, because silently
     * discarding a user's configuration is worse than reporting that it needs attention.
     */
    suspend fun resetToDefaults(): UserSettings
}

/** Raised when a settings update would produce an invalid configuration. */
class SettingsValidationException(message: String) : IllegalArgumentException(message)
