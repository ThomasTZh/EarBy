package com.thomas.passiveeartraining.data

import com.thomas.passiveeartraining.training.DegreeAnswerFormat
import com.thomas.passiveeartraining.training.MelodicAnswerFormat

/*
 * Schema-4 decode migrations for the answer formats removed with the solfège feature.
 *
 * The stored values are matched by their old enum names, because the enums themselves no longer
 * declare them. A removed value decodes as its numeric equivalent, so an upgraded install keeps
 * the rest of its configuration; any other value is returned unchanged and keeps the existing
 * failure or fallback behavior of the reading caller.
 */

/** Maps a removed stored scale-degree format onto its numeric equivalent. */
internal fun migratedDegreeAnswerFormat(stored: String): String = when (stored) {
    "MOVABLE_DO", "DEGREE_AND_SYLLABLE" -> DegreeAnswerFormat.DEGREE.name
    else -> stored
}

/** Maps a removed stored melodic-dictation format onto its numeric equivalent. */
internal fun migratedMelodicAnswerFormat(stored: String): String = when (stored) {
    "MOVABLE_DO_SEQUENCE" -> MelodicAnswerFormat.DEGREE_SEQUENCE.name
    else -> stored
}
