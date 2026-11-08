package com.thomas.passiveeartraining.training

import com.thomas.passiveeartraining.music.IntervalPresentation
import com.thomas.passiveeartraining.music.MidiNote
import com.thomas.passiveeartraining.training.recipe.SequencePolicy

/**
 * Everything needed to reproduce a generated sequence.
 *
 * The identity records the generator and RNG algorithm, the seed, the normalized definition and,
 * for version 2, the enabled presentations and an optional fixed root. A recorded identity plus its
 * version reproduces the concrete exercises. It is a reproducibility record, not a security token.
 */
data class GeneratorIdentity(
    val generatorId: String,
    val generatorVersion: Int,
    val rngId: String,
    val seed: Long,
    val definition: IntervalDefinition,
    /** Presentations version 2 may draw from, sorted. Version 1 holds the definition's own. */
    val enabledPresentations: List<IntervalPresentation> = listOf(definition.presentation),
    /** Version 2's fixed absolute root, or null when roots are drawn. */
    val fixedRoot: MidiNote? = null
) {
    init {
        if (generatorId.isBlank()) {
            throw InvalidIntervalDefinitionException(
                "Generator id must not be blank"
            )
        }
        if (generatorVersion <= 0) {
            throw InvalidIntervalDefinitionException(
                "Generator version must be positive but was $generatorVersion"
            )
        }
        if (rngId.isBlank()) throw InvalidIntervalDefinitionException("RNG id must not be blank")
        if (enabledPresentations.isEmpty()) {
            throw InvalidIntervalDefinitionException("A generator must enable one presentation")
        }
        if (enabledPresentations.distinct().size != enabledPresentations.size) {
            throw InvalidIntervalDefinitionException(
                "Enabled presentations must not repeat: $enabledPresentations"
            )
        }
    }

    companion object {
        const val INTERVAL_GENERATOR_ID: String = "interval"

        /** Generator version 1: canonical bag, Fisher-Yates shuffle and one pair draw per label. */
        const val INTERVAL_GENERATOR_VERSION: Int = 1

        /** Generator version 2: mixed presentations and optional fixed roots. */
        const val INTERVAL_GENERATOR_VERSION_V2: Int = 2

        fun interval(seed: Long, definition: IntervalDefinition): GeneratorIdentity =
            GeneratorIdentity(
                generatorId = INTERVAL_GENERATOR_ID,
                generatorVersion = INTERVAL_GENERATOR_VERSION,
                rngId = SeededRandomSource.RNG_ID,
                seed = seed,
                definition = definition
            )

        /** The identity of a version 2 sequence, recording the whole reproducibility policy. */
        fun intervalV2(seed: Long, policy: SequencePolicy): GeneratorIdentity = GeneratorIdentity(
            generatorId = INTERVAL_GENERATOR_ID,
            generatorVersion = INTERVAL_GENERATOR_VERSION_V2,
            rngId = SeededRandomSource.RNG_ID,
            seed = seed,
            definition = policy.definition,
            enabledPresentations = policy.presentations,
            fixedRoot = policy.fixedRoot
        )
    }
}
