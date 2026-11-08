package com.thomas.passiveeartraining.training.provider

import com.thomas.passiveeartraining.training.VoiceAssetId
import com.thomas.passiveeartraining.training.VoiceDurationCatalog

/**
 * Draws concrete exercises from one provider configuration.
 *
 * The generator owns the provider's random stream. [reconfigure] rebuilds the internal draw
 * machinery for an edited configuration while continuing the same stream, which is what preserves
 * the legacy interval behavior on a live setup edit: the pairs already heard are not replayed and
 * the next item follows them.
 */
interface ExerciseGenerator {
    /** Produces the next concrete exercise; called only when a new item is genuinely needed. */
    fun next(): ConcreteExercise

    /** Applies an edited configuration, continuing the same draw position for an unchanged seed. */
    fun reconfigure(config: ProviderConfig)
}

/**
 * One registered training provider.
 *
 * A provider exposes its defaults, required semantic vocabulary, validation and generator
 * creation. Validation takes an injected voice-duration catalogue so it measures the real clips
 * (SC2/M1).
 */
interface ExerciseProvider {
    /** Stable kind this provider implements. */
    val kind: TrainingKind

    /** The provider's own contract version, frozen when its wire format is first persisted. */
    val providerVersion: Int

    /** The configuration a fresh setup of this kind starts from. */
    fun defaults(): ProviderConfig

    /** Every semantic voice token any exercise of [config] can speak. */
    fun requiredVocabulary(config: ProviderConfig): Set<VoiceAssetId>

    /** Validates and normalizes [config]; returns field issues or the normalized config. */
    fun validate(config: ProviderConfig, catalog: VoiceDurationCatalog): ProviderValidation

    /**
     * Creates a generator for [config] with the already-resolved [seed].
     *
     * @throws IllegalArgumentException when [config] is not this provider's kind.
     */
    fun createGenerator(config: ProviderConfig, seed: Long): ExerciseGenerator
}

/**
 * The explicitly registered providers.
 *
 * Known-but-unimplemented kinds stay selectable by the shell but cannot construct a Start request,
 * because the registry returns no provider for them (SC2).
 */
class ProviderRegistry(providers: List<ExerciseProvider>) {
    private val byKind: Map<TrainingKind, ExerciseProvider> = providers.associateBy { it.kind }

    init {
        require(byKind.size == providers.size) {
            "Duplicate provider registration: ${providers.map { it.kind }}"
        }
    }

    /** The provider for [kind], or null when the kind is known but not implemented yet. */
    fun providerFor(kind: TrainingKind): ExerciseProvider? = byKind[kind]

    /** The kinds that can actually start a session, in registration order. */
    val implementedKinds: List<TrainingKind> get() = byKind.keys.toList()

    companion object {
        /**
         * The production registry.
         *
         * Each provider is registered in the same coherent edit that adds its codec, validation
         * and screens: intervals in M1, chords in M5, scales in M6, note identification in M7,
         * scale degrees in M8, chord progressions in M9A and intervals in context in M9B. A kind
         * without a registration stays selectable by the shell but cannot construct a Start
         * request. Melodic dictation is registered in M10.
         */
        val PRODUCTION: ProviderRegistry by lazy {
            ProviderRegistry(
                listOf(
                    IntervalExerciseProvider(),
                    IntervalWalkExerciseProvider(),
                    ChordExerciseProvider(),
                    ScaleExerciseProvider(),
                    NoteExerciseProvider(),
                    ScaleDegreeExerciseProvider(),
                    ProgressionExerciseProvider(),
                    ContextualIntervalExerciseProvider(),
                    MelodicDictationExerciseProvider()
                )
            )
        }
    }
}
