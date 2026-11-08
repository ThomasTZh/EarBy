package com.thomas.passiveeartraining.training.settings

import com.thomas.passiveeartraining.training.provider.TrainingKind

/** Persistable configuration version of the intervals provider payload. */
const val INTERVAL_WALK_CONFIG_VERSION: Int = 1

const val INTERVALS_CONFIG_VERSION: Int = 1

/** Persistable configuration version of the chords provider payload (M5). */
const val CHORDS_CONFIG_VERSION: Int = 1

/** Persistable configuration version of the scales provider payload (M6). */
const val SCALES_CONFIG_VERSION: Int = 1

/** Persistable configuration version of the notes provider payload (M7). */
const val NOTE_IDENTIFICATION_CONFIG_VERSION: Int = 1

/** Persistable configuration version of the scale-degrees provider payload (M8). */
const val SCALE_DEGREES_CONFIG_VERSION: Int = 1

/** Persistable configuration version of the chord-progressions provider payload (M9A). */
const val CHORD_PROGRESSIONS_CONFIG_VERSION: Int = 1

/** Persistable configuration version of the contextual-intervals provider payload (M9B). */
const val INTERVALS_IN_CONTEXT_CONFIG_VERSION: Int = 1

/** Persistable configuration version of the melodic-dictation provider payload (M10). */
const val MELODIC_DICTATION_CONFIG_VERSION: Int = 1

/**
 * The persisted musical setup of one mode.
 *
 * The subtype fixes the kind, so a payload can never claim a kind it does not describe. The
 * intervals payload deliberately keeps the `LastSetup` shape during the v0.2.x migration so every
 * schema-1 field (including an empty interval/root selection and the advanced panel state)
 * migrates exactly; later providers add their own typed payloads as their milestones define them
 * (SC7).
 */
sealed interface ModePayload {
    /** The kind this payload belongs to; fixed by the subtype. */
    val kind: TrainingKind

    /** The payload's own configuration version, frozen per provider. */
    val configVersion: Int

    data class Intervals(val setup: LastSetup) : ModePayload {
        override val kind: TrainingKind get() = TrainingKind.INTERVALS
        override val configVersion: Int get() = INTERVALS_CONFIG_VERSION
    }

    /** The chord setup from M5: qualities, inversions, roots, presentation and shared timings. */

    /** The finite interval-walk setup. */
    data class IntervalWalk(val setup: IntervalWalkSetup) : ModePayload {
        override val kind: TrainingKind get() = TrainingKind.INTERVAL_WALK
        override val configVersion: Int get() = INTERVAL_WALK_CONFIG_VERSION
    }

    data class Chords(val setup: ChordSetup) : ModePayload {
        override val kind: TrainingKind get() = TrainingKind.CHORDS
        override val configVersion: Int get() = CHORDS_CONFIG_VERSION
    }

    /** The scale setup from M6: scales, direction, roots, range and shared timings. */
    data class Scales(val setup: ScaleSetup) : ModePayload {
        override val kind: TrainingKind get() = TrainingKind.SCALES
        override val configVersion: Int get() = SCALES_CONFIG_VERSION
    }

    /** The note-identification setup from M7: classes, range, accidental style and timings. */
    data class Notes(val setup: NoteSetup) : ModePayload {
        override val kind: TrainingKind get() = TrainingKind.NOTE_IDENTIFICATION
        override val configVersion: Int get() = NOTE_IDENTIFICATION_CONFIG_VERSION
    }

    /** The scale-degree setup from M8: keys, tonalities, degrees, context and timings. */
    data class ScaleDegrees(val setup: ScaleDegreeSetup) : ModePayload {
        override val kind: TrainingKind get() = TrainingKind.SCALE_DEGREES
        override val configVersion: Int get() = SCALE_DEGREES_CONFIG_VERSION
    }

    /** The chord-progression setup from M9A: keys, templates, voicing, inversions and timings. */
    data class Progressions(val setup: ProgressionSetup) : ModePayload {
        override val kind: TrainingKind get() = TrainingKind.CHORD_PROGRESSIONS
        override val configVersion: Int get() = CHORD_PROGRESSIONS_CONFIG_VERSION
    }

    /** The contextual-interval setup from M9B: keys, intervals, presentation, context and timings. */
    data class ContextualIntervals(val setup: ContextualIntervalSetup) : ModePayload {
        override val kind: TrainingKind get() = TrainingKind.INTERVALS_IN_CONTEXT
        override val configVersion: Int get() = INTERVALS_IN_CONTEXT_CONFIG_VERSION
    }

    /** The melodic-dictation setup from M10: keys, phrase shape, steps, answer and timings. */
    data class MelodicDictation(val setup: MelodicDictationSetup) : ModePayload {
        override val kind: TrainingKind get() = TrainingKind.MELODIC_DICTATION
        override val configVersion: Int get() = MELODIC_DICTATION_CONFIG_VERSION
    }
}

/**
 * One saved setup plus its revision.
 *
 * The revision increases by exactly one on every accepted write and never wraps; a caller that
 * captured a revision and lost the race gets an explicit conflict instead of silently
 * overwriting a newer preset application (SC6).
 */
data class SavedModeSetup(val revision: Long, val payload: ModePayload) {
    init {
        require(revision >= 1L) { "A saved setup revision must be positive but was $revision" }
    }

    /** Returns the next revision for an accepted write, refusing to wrap. */
    fun nextRevision(): SavedModeSetup {
        check(revision < Long.MAX_VALUE) { "The saved setup revision cannot advance further" }
        return copy(revision = revision + 1L)
    }
}

/** One requested edit of a saved mode setup, guarded by the revision the caller last saw. */
data class ModeEdit(
    val kind: TrainingKind,
    val expectedRevision: Long,
    /** Produces the new payload; the revision is advanced by the repository, never by the edit. */
    val change: (SavedModeSetup) -> ModePayload
)

/** The outcome of a settings write that involves mode state. */
sealed interface ModeWriteResult {
    /** The write committed. */
    data class Saved(val snapshot: SettingsSnapshot) : ModeWriteResult

    /** A captured revision was stale; nothing was written and [snapshot] is the current state. */
    data class Conflict(val snapshot: SettingsSnapshot) : ModeWriteResult

    /** The write was refused (validation or a protected stored version); nothing was written. */
    data class Rejected(val message: String) : ModeWriteResult
}

/**
 * The default mode-setup map: the intervals, chords, scales, notes, scale-degrees,
 * chord-progressions, contextual-interval and melodic-dictation entries exist from revision 1.
 *
 * Later kinds appear here in the same edit that adds their typed payload, so a fresh install and a
 * migrated store agree on which setups exist (SC7).
 */
internal fun defaultModeSetups(): Map<TrainingKind, SavedModeSetup> = mapOf(
    TrainingKind.INTERVALS to SavedModeSetup(
        revision = 1L,
        payload = ModePayload.Intervals(LastSetup.default())
    ),
    TrainingKind.INTERVAL_WALK to SavedModeSetup(
        revision = 1L,
        payload = ModePayload.IntervalWalk(IntervalWalkSetup.default())
    ),
    TrainingKind.CHORDS to SavedModeSetup(
        revision = 1L,
        payload = ModePayload.Chords(ChordSetup.default())
    ),
    TrainingKind.SCALES to SavedModeSetup(
        revision = 1L,
        payload = ModePayload.Scales(ScaleSetup.default())
    ),
    TrainingKind.NOTE_IDENTIFICATION to SavedModeSetup(
        revision = 1L,
        payload = ModePayload.Notes(NoteSetup.default())
    ),
    TrainingKind.SCALE_DEGREES to SavedModeSetup(
        revision = 1L,
        payload = ModePayload.ScaleDegrees(ScaleDegreeSetup.default())
    ),
    TrainingKind.CHORD_PROGRESSIONS to SavedModeSetup(
        revision = 1L,
        payload = ModePayload.Progressions(ProgressionSetup.default())
    ),
    TrainingKind.INTERVALS_IN_CONTEXT to SavedModeSetup(
        revision = 1L,
        payload = ModePayload.ContextualIntervals(ContextualIntervalSetup.default())
    ),
    TrainingKind.MELODIC_DICTATION to SavedModeSetup(
        revision = 1L,
        payload = ModePayload.MelodicDictation(MelodicDictationSetup.default())
    )
)
