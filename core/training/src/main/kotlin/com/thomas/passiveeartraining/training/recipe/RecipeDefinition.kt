package com.thomas.passiveeartraining.training.recipe

import com.thomas.passiveeartraining.music.IntervalPresentation
import com.thomas.passiveeartraining.music.MidiNote
import com.thomas.passiveeartraining.training.IntervalDefinition
import com.thomas.passiveeartraining.training.MissingVoiceDurationException
import com.thomas.passiveeartraining.training.RecognitionRecipe
import com.thomas.passiveeartraining.training.VoiceAssetId
import com.thomas.passiveeartraining.training.VoiceDurationCatalog
import kotlin.time.Duration

/**
 * One named step of a learning recipe.
 *
 * Steps are explicit and named rather than script expressions, so a recipe is data that validation
 * can check and a duration preview can measure, not code that has to be executed to be understood.
 */
sealed interface RecipeStep {
    /** How long this step occupies in the compiled timeline for [presentation]. */
    fun duration(
        catalog: VoiceDurationCatalog,
        timing: RecipeTiming,
        presentation: IntervalPresentation
    ): Duration

    /** The concrete notes this step sounds, if any. */
    fun notes(example: List<Int>, answer: List<Int>): List<Int>

    /** The semantic voice asset this step speaks, if any. */
    fun voiceId(answerId: VoiceAssetId): VoiceAssetId?

    /** True when this step's answer is revealed by speaking it. */
    val revealsAnswer: Boolean get() = false

    /** The example notes. */
    data object Example : RecipeStep {
        /**
         * A melodic example fills its own window: two notes and the gap between them. A harmonic
         * example sounds both notes at once, so it lasts one note. The presentation comes from the
         * concrete exercise, never from the recipe.
         */
        override fun duration(
            catalog: VoiceDurationCatalog,
            timing: RecipeTiming,
            presentation: IntervalPresentation
        ): Duration = when (presentation) {
            IntervalPresentation.ASCENDING, IntervalPresentation.DESCENDING ->
                timing.noteDuration * 2 + timing.melodicGap
            IntervalPresentation.HARMONIC -> timing.noteDuration
        }

        override fun notes(example: List<Int>, answer: List<Int>): List<Int> = example

        override fun voiceId(answerId: VoiceAssetId): VoiceAssetId? = null
    }

    /** Silence for thinking, before the answer is spoken. */
    data object Thinking : RecipeStep {
        override fun duration(
            catalog: VoiceDurationCatalog,
            timing: RecipeTiming,
            presentation: IntervalPresentation
        ): Duration = timing.thinkingSilence

        override fun notes(example: List<Int>, answer: List<Int>): List<Int> = emptyList()

        override fun voiceId(answerId: VoiceAssetId): VoiceAssetId? = null
    }

    /** The spoken answer. */
    data object Answer : RecipeStep {
        override val revealsAnswer: Boolean get() = true

        override fun duration(
            catalog: VoiceDurationCatalog,
            timing: RecipeTiming,
            presentation: IntervalPresentation
        ): Duration = catalog.durationOf(ANSWER_PLACEHOLDER)

        override fun notes(example: List<Int>, answer: List<Int>): List<Int> = emptyList()

        override fun voiceId(answerId: VoiceAssetId): VoiceAssetId = answerId
    }

    /** A gap whose length the step's own `gap` value supplies. */
    data class Gap(val gap: Duration) : RecipeStep {
        override fun duration(
            catalog: VoiceDurationCatalog,
            timing: RecipeTiming,
            presentation: IntervalPresentation
        ): Duration = gap

        override fun notes(example: List<Int>, answer: List<Int>): List<Int> = emptyList()

        override fun voiceId(answerId: VoiceAssetId): VoiceAssetId? = null
    }

    /** The single final silence, once per exercise. */
    data object FinalSilence : RecipeStep {
        override fun duration(
            catalog: VoiceDurationCatalog,
            timing: RecipeTiming,
            presentation: IntervalPresentation
        ): Duration = timing.postAnswerSilence

        override fun notes(example: List<Int>, answer: List<Int>): List<Int> = emptyList()

        override fun voiceId(answerId: VoiceAssetId): VoiceAssetId? = null
    }

    /**
     * The optional pair separator: a short two-note marker before a new exercise.
     *
     * The notes are fixed and high, so the marker is clearly not part of the example. It is the
     * owner-requested "ting" cue that announces a new pair.
     */
    data object Separator : RecipeStep {
        /** One marker note's length. */
        val NOTE_DURATION: Duration = Duration.parse("120ms")

        /** Gap between the two marker notes. */
        val NOTE_GAP: Duration = Duration.parse("80ms")

        /** The two fixed MIDI notes: C6 then G6. */
        val NOTE_VALUES: List<Int> = listOf(84, 91)

        override fun duration(
            catalog: VoiceDurationCatalog,
            timing: RecipeTiming,
            presentation: IntervalPresentation
        ): Duration = NOTE_DURATION * 2 + NOTE_GAP

        override fun notes(example: List<Int>, answer: List<Int>): List<Int> = NOTE_VALUES

        override fun voiceId(answerId: VoiceAssetId): VoiceAssetId? = null
    }

    companion object {
        /**
         * Placeholder used when a step's duration needs the catalog.
         *
         * `Answer` resolves the real answer id at compile time; this constant only exists so the
         * duration signature stays a pure function of the catalog.
         */
        val ANSWER_PLACEHOLDER: VoiceAssetId = VoiceAssetId("interval.unison")
    }
}

/** Timing values a recipe reads; the roadmap bounds are validated on construction. */
data class RecipeTiming(
    val noteDuration: Duration = Duration.parse("700ms"),
    /** The silence between the two notes of one melodic example. */
    val melodicGap: Duration = Duration.parse("100ms"),
    val thinkingSilence: Duration = Duration.parse("2s"),
    val postAnswerSilence: Duration = Duration.parse("2s"),
    val answerGap: Duration = Duration.parse("600ms"),
    val repeatGap: Duration = Duration.parse("400ms"),
    /** The silence between a context section and the target section of one example. */
    val contextGap: Duration = Duration.parse("500ms")
) {
    init {
        require(noteDuration > Duration.ZERO) { "A note duration must be positive" }
        require(melodicGap >= Duration.ZERO) { "A melodic gap cannot be negative" }
        require(thinkingSilence >= Duration.ZERO) { "A thinking silence cannot be negative" }
        require(postAnswerSilence >= Duration.ZERO) { "A final silence cannot be negative" }
        require(answerGap >= Duration.ZERO) { "An answer gap cannot be negative" }
        require(repeatGap >= Duration.ZERO) { "A repeat gap cannot be negative" }
        require(contextGap >= Duration.ZERO) { "A context gap cannot be negative" }
    }
}

/**
 * One of the product's recipe templates.
 *
 * Each template is built from the named steps in the roadmap table, so changing a template means
 * changing this list rather than editing a script.
 */
data class RecipeDefinition(
    val mode: RecipeMode,
    val timing: RecipeTiming = RecipeTiming(),
    /** Additional examples after the answer, 0..[MAX_REPLAY]. */
    val replayCount: Int = 0,
    /** Plays the short two-note pair separator before each exercise. */
    val separatorCue: Boolean = false
) {
    init {
        require(replayCount in 0..MAX_REPLAY) {
            "A replay count is 0..$MAX_REPLAY but was $replayCount"
        }
    }

    /**
     * The steps in order.
     *
     * The final silence appears once per exercise, at the end of every template.
     */
    val steps: List<RecipeStep>
        get() {
            val gap = timing.answerGap
            val repeatGap = timing.repeatGap
            val body = when (mode) {
                RecipeMode.RECOGNITION -> buildList {
                    add(RecipeStep.Example)
                    add(RecipeStep.Thinking)
                    add(RecipeStep.Answer)
                    repeat(replayCount) {
                        add(RecipeStep.Gap(repeatGap))
                        add(RecipeStep.Example)
                    }
                }
                RecipeMode.EXPOSURE -> buildList {
                    add(RecipeStep.Answer)
                    add(RecipeStep.Gap(gap))
                    add(RecipeStep.Example)
                    repeat(replayCount) {
                        add(RecipeStep.Gap(repeatGap))
                        add(RecipeStep.Example)
                    }
                }
                RecipeMode.ASSOCIATION -> buildList {
                    add(RecipeStep.Example)
                    // The first gap is the thinking pause, so the association mode honours the
                    // configured thinking time between the example and its answer.
                    add(RecipeStep.Thinking)
                    add(RecipeStep.Answer)
                    add(RecipeStep.Gap(gap))
                    add(RecipeStep.Example)
                    // Extra repeats follow the answer, exactly as the control promises.
                    repeat(replayCount) {
                        add(RecipeStep.Gap(repeatGap))
                        add(RecipeStep.Example)
                    }
                }
            }
            return if (separatorCue) {
                // The marker belongs to the transition: it sounds in the middle of the final
                // silence, so half the gap stays before it and half before the next pair's first
                // note. The very first pair has no predecessor, so it starts without a marker.
                buildList {
                    addAll(body)
                    val half = timing.postAnswerSilence / 2
                    add(RecipeStep.Gap(half))
                    add(RecipeStep.Separator)
                    add(RecipeStep.Gap(half))
                }
            } else {
                body + RecipeStep.FinalSilence
            }
        }

    /**
     * Total duration of the compiled exercise, including all speech and the final silence.
     *
     * The example window depends on the concrete exercise's presentation, so callers that know it
     * must pass it; the melodic assumption is the longer one and is the safe default for a preview
     * that does not.
     */
    fun totalDuration(
        catalog: VoiceDurationCatalog,
        answerId: VoiceAssetId,
        presentation: IntervalPresentation = IntervalPresentation.ASCENDING
    ): Duration {
        var total = Duration.ZERO
        steps.forEach { step ->
            val measured = if (step is RecipeStep.Answer) {
                // The answer's real clip length, never a placeholder.
                catalog.durationOf(answerId)
            } else {
                step.duration(catalog, timing, presentation)
            }
            total += measured
        }
        return total
    }

    /**
     * Total duration with explicit measured content windows.
     *
     * Chord exercises (M5.2) measure their own example and composed answer durations, which the
     * interval-shaped [totalDuration] cannot express. Only the content steps are replaced; every
     * silence, replay and the separator keep their recipe definition.
     */
    fun totalDurationOf(exampleDuration: Duration, answerDuration: Duration): Duration {
        var total = Duration.ZERO
        steps.forEach { step ->
            total += when (step) {
                RecipeStep.Example -> exampleDuration
                RecipeStep.Answer -> answerDuration
                RecipeStep.Thinking -> timing.thinkingSilence
                RecipeStep.FinalSilence -> timing.postAnswerSilence
                RecipeStep.Separator ->
                    RecipeStep.Separator.NOTE_DURATION * 2 +
                        RecipeStep.Separator.NOTE_GAP
                is RecipeStep.Gap -> step.gap
            }
        }
        return total
    }

    /**
     * Validates the recipe against the exercise cap and the roadmap timing bounds.
     *
     * @throws RecipeValidationException naming the measured duration and the bound it broke.
     */
    fun validate(
        catalog: VoiceDurationCatalog,
        answerId: VoiceAssetId,
        presentation: IntervalPresentation = IntervalPresentation.ASCENDING
    ) {
        val total = totalDuration(catalog, answerId, presentation)
        if (total > RecognitionRecipe.EXERCISE_CAP) {
            throw RecipeValidationException(
                "$mode lasts ${total.inWholeMilliseconds} ms, above the " +
                    "${RecognitionRecipe.EXERCISE_CAP.inWholeSeconds} s cap for one exercise"
            )
        }
    }

    companion object {
        /** Most additional examples a recipe may append. */
        const val MAX_REPLAY: Int = 8

        /** The default recipe per mode, with no optional repetition. */
        fun of(mode: RecipeMode): RecipeDefinition = RecipeDefinition(mode = mode)
    }
}

/** The product's learning modes. */
enum class RecipeMode {
    /** Example, think, answer, with optional replay. */
    RECOGNITION,

    /** Answer first, then the example. */
    EXPOSURE,

    /** Example, answer, example. */
    ASSOCIATION;

    /**
     * How many leading recipe steps form the mandatory learning unit.
     *
     * Recognition is Example-Thinking-Answer, Exposure Answer-Gap-Example and Association
     * Example-Thinking-Answer-Gap-Example (SC4). The compiler uses this to mark where a mode
     * switch may cut the timeline.
     */
    val mandatoryStepCount: Int
        get() = when (this) {
            RECOGNITION -> 3
            EXPOSURE -> 3
            ASSOCIATION -> 5
        }
}

/** Raised when a recipe cannot be compiled under the product's bounds. */
class RecipeValidationException(message: String) : IllegalArgumentException(message)

/**
 * How the generator chooses an interval and its presentation.
 *
 * Version 1 is preserved exactly: ascending pairs only, drawn as before. Version 2 adds mixed
 * presentation and an optional fixed absolute note.
 */
data class SequencePolicy(
    val definition: IntervalDefinition,
    /** Presentations that may be drawn, sorted and non-empty for a mixed policy. */
    val presentations: List<IntervalPresentation> = listOf(definition.presentation),
    /** A fixed absolute root, or null to draw roots from the selected pitch classes. */
    val fixedRoot: MidiNote? = null,
    /** False for the version 1 draw order, true for mixed presentation and fixed roots. */
    val mixedPresentation: Boolean = false
) {
    init {
        require(presentations.isNotEmpty()) { "At least one presentation must be enabled" }
        require(presentations.distinct().size == presentations.size) {
            "Presentations must not repeat: $presentations"
        }
        // Sorting is part of the draw contract: a mixed policy draws from a sorted enabled set, so
        // the same configuration always produces the same sequence.
        require(presentations == presentations.sortedBy { it.ordinal }) {
            "Presentations must be sorted by ordinal for reproducible draws: $presentations"
        }
        fixedRoot?.let { root ->
            require(root.value in definition.noteRange) {
                "A fixed root $root must be inside the configured range ${definition.noteRange}"
            }
            require(root.pitchClass.value in definition.selectedRoots.map { it.value }) {
                "A fixed root $root must match a selected root class"
            }
        }
    }

    /** Generator version recorded with the exercise: 2 for mixed presentations or a fixed root. */
    val generatorVersion: Int get() = if (mixedPresentation || fixedRoot != null) 2 else 1
}
