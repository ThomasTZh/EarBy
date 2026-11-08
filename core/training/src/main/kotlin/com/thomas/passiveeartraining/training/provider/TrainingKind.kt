package com.thomas.passiveeartraining.training.provider

import com.thomas.passiveeartraining.training.settings.TrainingMode

/**
 * Stable identifier of a training kind.
 *
 * These IDs are persisted in settings and preset documents, so they are wire values: renaming one
 * is a migration, not a refactor. Display labels and the drawer order live elsewhere and may change
 * freely (SC2).
 */
@JvmInline
value class TrainingKind(val id: String) {
    init {
        require(id.isNotBlank()) { "A training kind id must not be blank" }
        require(id.none { it.isWhitespace() }) { "A training kind id must not contain whitespace" }
    }

    override fun toString(): String = id

    companion object {
        val INTERVALS: TrainingKind = TrainingKind("intervals")
        val INTERVAL_WALK: TrainingKind = TrainingKind("interval_walk")
        val CHORDS: TrainingKind = TrainingKind("chords")
        val SCALES: TrainingKind = TrainingKind("scales")
        val NOTE_IDENTIFICATION: TrainingKind = TrainingKind("note_identification")
        val SCALE_DEGREES: TrainingKind = TrainingKind("scale_degrees")
        val CHORD_PROGRESSIONS: TrainingKind = TrainingKind("chord_progressions")
        val INTERVALS_IN_CONTEXT: TrainingKind = TrainingKind("intervals_in_context")
        val MELODIC_DICTATION: TrainingKind = TrainingKind("melodic_dictation")

        /** Every kind the shell knows, in the drawer's presentation order. */
        val ALL: List<TrainingKind> = listOf(
            INTERVALS,
            INTERVAL_WALK,
            CHORDS,
            SCALES,
            CHORD_PROGRESSIONS,
            NOTE_IDENTIFICATION,
            SCALE_DEGREES,
            INTERVALS_IN_CONTEXT,
            MELODIC_DICTATION
        )

        /** Looks up a kind by its persisted id; returns null for unknown ids instead of guessing. */
        fun ofIdOrNull(id: String): TrainingKind? = ALL.firstOrNull { it.id == id }
    }
}

/**
 * Maps the legacy selection enum onto stable kind IDs.
 *
 * The legacy alias is the persistence bridge for settings schema 1; the enum order is the drawer's
 * presentation order and is not a dependency order.
 */
fun TrainingMode.toTrainingKind(): TrainingKind = when (this) {
    TrainingMode.INTERVALS -> TrainingKind.INTERVALS
    TrainingMode.CHORDS -> TrainingKind.CHORDS
    TrainingMode.SCALES -> TrainingKind.SCALES
    TrainingMode.CHORD_PROGRESSIONS -> TrainingKind.CHORD_PROGRESSIONS
    TrainingMode.PERFECT_PITCH -> TrainingKind.NOTE_IDENTIFICATION
    TrainingMode.SCALE_DEGREES -> TrainingKind.SCALE_DEGREES
    TrainingMode.INTERVALS_IN_CONTEXT -> TrainingKind.INTERVALS_IN_CONTEXT
    TrainingMode.MELODIC_DICTATION -> TrainingKind.MELODIC_DICTATION
}

/** Maps a stable kind ID back onto the legacy selection enum, or null when it has no alias. */
fun TrainingKind.toLegacyModeOrNull(): TrainingMode? = when (this) {
    TrainingKind.INTERVALS -> TrainingMode.INTERVALS
    TrainingKind.CHORDS -> TrainingMode.CHORDS
    TrainingKind.SCALES -> TrainingMode.SCALES
    TrainingKind.CHORD_PROGRESSIONS -> TrainingMode.CHORD_PROGRESSIONS
    TrainingKind.NOTE_IDENTIFICATION -> TrainingMode.PERFECT_PITCH
    TrainingKind.SCALE_DEGREES -> TrainingMode.SCALE_DEGREES
    TrainingKind.INTERVALS_IN_CONTEXT -> TrainingMode.INTERVALS_IN_CONTEXT
    TrainingKind.MELODIC_DICTATION -> TrainingMode.MELODIC_DICTATION
    else -> null
}
