package com.thomas.passiveeartraining.data.preset

import com.thomas.passiveeartraining.training.provider.TrainingKind
import com.thomas.passiveeartraining.training.settings.ChordSetup
import com.thomas.passiveeartraining.training.settings.ContextualIntervalSetup
import com.thomas.passiveeartraining.training.settings.IntervalWalkSetup
import com.thomas.passiveeartraining.training.settings.LastSetup
import com.thomas.passiveeartraining.training.settings.MelodicDictationSetup
import com.thomas.passiveeartraining.training.settings.NoteSetup
import com.thomas.passiveeartraining.training.settings.ProgressionSetup
import com.thomas.passiveeartraining.training.settings.ScaleDegreeSetup
import com.thomas.passiveeartraining.training.settings.ScaleSetup

/**
 * The musical configuration a preset restores, whichever mode it belongs to (M5).
 *
 * The subtype fixes the training kind, exactly as a stored document's kind/configVersion pair
 * does, so a preset cannot describe one mode's setup while claiming another. The intervals case
 * keeps the [LastSetup] shape and every schema-1 field, so a preset written before chords existed
 * decodes to an identical interval configuration.
 */
sealed interface PresetConfiguration {
    /** The training kind this configuration belongs to; fixed by the subtype. */
    val kind: TrainingKind

    /** The intervals configuration: interval and root selection, presentation and timings. */
    data class Intervals(val setup: LastSetup) : PresetConfiguration {
        override val kind: TrainingKind get() = TrainingKind.INTERVALS
    }

    /** The interval-walk configuration: intervals, direction, length, start range and timings. */
    data class IntervalWalk(val setup: IntervalWalkSetup) : PresetConfiguration {
        override val kind: TrainingKind get() = TrainingKind.INTERVAL_WALK
    }

    /** The chords configuration: qualities, inversions, roots, presentation and timings (M5). */
    data class Chords(val setup: ChordSetup) : PresetConfiguration {
        override val kind: TrainingKind get() = TrainingKind.CHORDS
    }

    /** The scales configuration: scales, direction, roots, range and timings (M6). */
    data class Scales(val setup: ScaleSetup) : PresetConfiguration {
        override val kind: TrainingKind get() = TrainingKind.SCALES
    }

    /** The note-identification configuration: classes, range, accidental style and timings (M7). */
    data class Notes(val setup: NoteSetup) : PresetConfiguration {
        override val kind: TrainingKind get() = TrainingKind.NOTE_IDENTIFICATION
    }

    /** The scale-degree configuration: keys, tonalities, degrees, context and timings (M8). */
    data class ScaleDegrees(val setup: ScaleDegreeSetup) : PresetConfiguration {
        override val kind: TrainingKind get() = TrainingKind.SCALE_DEGREES
    }

    /** The chord-progression configuration: keys, templates, voicing and timings (M9A). */
    data class Progressions(val setup: ProgressionSetup) : PresetConfiguration {
        override val kind: TrainingKind get() = TrainingKind.CHORD_PROGRESSIONS
    }

    /** The contextual-interval configuration: keys, intervals, presentation and timings (M9B). */
    data class ContextualIntervals(val setup: ContextualIntervalSetup) : PresetConfiguration {
        override val kind: TrainingKind get() = TrainingKind.INTERVALS_IN_CONTEXT
    }

    /** The melodic-dictation configuration: keys, phrase shape, steps and timings (M10). */
    data class MelodicDictation(val setup: MelodicDictationSetup) : PresetConfiguration {
        override val kind: TrainingKind get() = TrainingKind.MELODIC_DICTATION
    }
}
