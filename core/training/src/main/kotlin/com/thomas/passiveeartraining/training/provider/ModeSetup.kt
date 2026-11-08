package com.thomas.passiveeartraining.training.provider

import com.thomas.passiveeartraining.training.recipe.SequencePolicy

/**
 * The musical configuration of one provider.
 *
 * The sealed subtype fixes the kind, so a config can never claim a kind it does not describe. Each
 * provider adds its own subtype when it obtains a real consumer; there is deliberately no generic
 * bag-of-fields implementation.
 */
sealed interface ProviderConfig {
    /** The kind this configuration belongs to; fixed by the subtype. */
    val kind: TrainingKind

    /**
     * The accepted interval configuration: the existing [SequencePolicy], unchanged.
     *
     * Wrapping the legacy policy rather than copying its fields keeps generator version detection
     * and validation in exactly one place (SC2).
     */
    data class Intervals(val policy: SequencePolicy) : ProviderConfig {
        override val kind: TrainingKind get() = TrainingKind.INTERVALS
    }

    /** The finite interval-walk configuration. */
    data class IntervalWalks(
        val definition: com.thomas.passiveeartraining.training.IntervalWalkDefinition
    ) : ProviderConfig {
        override val kind: TrainingKind get() = TrainingKind.INTERVAL_WALK
    }

    /** The chord configuration from M5: the normalized [ChordDefinition]. */
    data class Chords(val definition: com.thomas.passiveeartraining.training.ChordDefinition) :
        ProviderConfig {
        override val kind: TrainingKind get() = TrainingKind.CHORDS
    }

    /** The scale configuration from M6: the normalized [ScaleDefinition]. */
    data class Scales(val definition: com.thomas.passiveeartraining.training.ScaleDefinition) :
        ProviderConfig {
        override val kind: TrainingKind get() = TrainingKind.SCALES
    }

    /** The note-identification configuration from M7: the normalized [NoteDefinition]. */
    data class Notes(val definition: com.thomas.passiveeartraining.training.NoteDefinition) :
        ProviderConfig {
        override val kind: TrainingKind get() = TrainingKind.NOTE_IDENTIFICATION
    }

    /** The scale-degree configuration from M8: the normalized [ScaleDegreeDefinition]. */
    data class ScaleDegrees(
        val definition: com.thomas.passiveeartraining.training.ScaleDegreeDefinition
    ) : ProviderConfig {
        override val kind: TrainingKind get() = TrainingKind.SCALE_DEGREES
    }

    /** The chord-progression configuration from M9: the normalized [ProgressionDefinition]. */
    data class Progressions(
        val definition: com.thomas.passiveeartraining.training.ProgressionDefinition
    ) : ProviderConfig {
        override val kind: TrainingKind get() = TrainingKind.CHORD_PROGRESSIONS
    }

    /** The contextual-interval configuration from M9B: the normalized definition. */
    data class ContextualIntervals(
        val definition: com.thomas.passiveeartraining.training.ContextualIntervalDefinition
    ) : ProviderConfig {
        override val kind: TrainingKind get() = TrainingKind.INTERVALS_IN_CONTEXT
    }

    /** The melodic-dictation configuration from M10: the normalized definition. */
    data class MelodicDictation(
        val definition: com.thomas.passiveeartraining.training.MelodicDictationDefinition
    ) : ProviderConfig {
        override val kind: TrainingKind get() = TrainingKind.MELODIC_DICTATION
    }
}

/**
 * Everything a session needs to begin, in provider-neutral form.
 *
 * The provider config is the musical setup; the recipe and stop policy are shared product
 * configuration; the seed is optional and resolved exactly once at Start (SC2/SC3).
 */
data class ModeSetup(
    val provider: ProviderConfig,
    val recipe: com.thomas.passiveeartraining.training.recipe.RecipeDefinition,
    /** Optional seed; null is resolved once by the accepted Start and published. */
    val seed: Long? = null,
    /** The one selected stop policy: duration, count or cycle. */
    val stopPolicy: com.thomas.passiveeartraining.training.recipe.SessionStopPolicy =
        com.thomas.passiveeartraining.training.recipe.SessionStopPolicy.UntilStopped
) {
    /** A copy with a resolved seed, used by the controller before the source is configured. */
    fun withSeed(resolved: Long): ModeSetup = if (seed == resolved) this else copy(seed = resolved)
}
