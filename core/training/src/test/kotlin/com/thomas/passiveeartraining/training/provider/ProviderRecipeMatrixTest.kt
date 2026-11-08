package com.thomas.passiveeartraining.training.provider

import com.thomas.passiveeartraining.music.ChordPresentation
import com.thomas.passiveeartraining.music.ChordQuality
import com.thomas.passiveeartraining.music.DiatonicChord
import com.thomas.passiveeartraining.music.IntervalPresentation
import com.thomas.passiveeartraining.music.MidiNote
import com.thomas.passiveeartraining.music.PitchClass
import com.thomas.passiveeartraining.music.ProgressionVoicingMode
import com.thomas.passiveeartraining.music.ScaleDirection
import com.thomas.passiveeartraining.music.ScaleKind
import com.thomas.passiveeartraining.music.SimpleInterval
import com.thomas.passiveeartraining.music.SpellingPreference
import com.thomas.passiveeartraining.music.Tonality
import com.thomas.passiveeartraining.training.AnswerStartedEvent
import com.thomas.passiveeartraining.training.BundledVoiceDurations
import com.thomas.passiveeartraining.training.ChordAnswerMode
import com.thomas.passiveeartraining.training.ChordDefinition
import com.thomas.passiveeartraining.training.ContextualIntervalAnswerFormat
import com.thomas.passiveeartraining.training.ContextualIntervalDefinition
import com.thomas.passiveeartraining.training.DegreeAnswerFormat
import com.thomas.passiveeartraining.training.IntervalDefinition
import com.thomas.passiveeartraining.training.IntervalWalkDefinition
import com.thomas.passiveeartraining.training.MelodicAnswerFormat
import com.thomas.passiveeartraining.training.MelodicDictationDefinition
import com.thomas.passiveeartraining.training.MelodicDirectionBias
import com.thomas.passiveeartraining.training.NoteDefinition
import com.thomas.passiveeartraining.training.ProgressionAnswerFormat
import com.thomas.passiveeartraining.training.ProgressionDefinition
import com.thomas.passiveeartraining.training.RecognitionRecipe
import com.thomas.passiveeartraining.training.ScaleDefinition
import com.thomas.passiveeartraining.training.ScaleDegreeDefinition
import com.thomas.passiveeartraining.training.WalkDirection
import com.thomas.passiveeartraining.training.context.ContextKind
import com.thomas.passiveeartraining.training.recipe.RecipeDefinition
import com.thomas.passiveeartraining.training.recipe.RecipeMode
import com.thomas.passiveeartraining.training.recipe.RecipeTiming
import com.thomas.passiveeartraining.training.recipe.RecipeValidationException
import com.thomas.passiveeartraining.training.recipe.SequencePolicy
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * M11.2 provider matrix: all nine production providers compile every learning recipe from their
 * defaults and from a documented maximum-complexity configuration.
 *
 * Every generated exercise is compiled by the production [ExerciseCompiler], so this covers the
 * complete example/answer scheduling contract: exactly one answer marker, context groups before
 * target groups, Association's mandatory example heard twice around the answer, the 60 s cap and
 * an answer that starts before the exercise ends.
 *
 * The maximum configurations push each definition to the largest values its own validator accepts.
 * Where a single choice would make another impossible, the largest valid one is used instead and
 * the reason is documented at the configuration.
 */
class ProviderRecipeMatrixTest {

    private val catalog = BundledVoiceDurations
    private val compiler = ExerciseCompiler()
    private val timing = RecipeTiming()
    private val seed = 20261014L

    /** Every signed step the melodic control offers, for the maximum-complexity phrase. */
    private val everyAllowedStep: List<Int> = MelodicDictationDefinition.MAX_STEP.let { maximum ->
        (-maximum..maximum).toList()
    }

    @Test
    fun everyProductionDefaultValidatesAndCompilesUnderEveryRecipe() {
        val registry = ProviderRegistry.PRODUCTION
        assertEquals("nine production kinds", 9, registry.implementedKinds.size)
        for (kind in registry.implementedKinds) {
            val provider = requireNotNull(registry.providerFor(kind))
            val config = provider.defaults()
            val validation = provider.validate(config, catalog)
            assertTrue(
                "$kind defaults must validate but were $validation",
                validation is ProviderValidation.Valid
            )
            for (mode in RecipeMode.entries) {
                assertCompiles("$kind defaults", provider, config, mode)
            }
        }
    }

    @Test
    fun everyDocumentedMaximumConfigurationValidatesAndCompilesUnderEveryRecipe() {
        val registry = ProviderRegistry.PRODUCTION
        val configurations = maximumConfigurations()
        assertEquals(
            "every production kind has a documented maximum configuration",
            registry.implementedKinds.toSet(),
            configurations.keys
        )
        for ((kind, candidates) in configurations) {
            val provider = requireNotNull(registry.providerFor(kind))
            for ((label, config) in candidates) {
                val validation = provider.validate(config, catalog)
                assertTrue(
                    "$kind $label must validate but was $validation",
                    validation is ProviderValidation.Valid
                )
                for (mode in RecipeMode.entries) {
                    assertCompiles("$kind $label", provider, config, mode)
                }
            }
        }
    }

    /** Generates [EXERCISES_PER_CONFIGURATION] exercises and checks every compiled unit. */
    private fun assertCompiles(
        label: String,
        provider: ExerciseProvider,
        config: ProviderConfig,
        mode: RecipeMode
    ) {
        val definition = RecipeDefinition(mode = mode, timing = timing)
        val generator = provider.createGenerator(config, seed)
        repeat(EXERCISES_PER_CONFIGURATION) { index ->
            val exercise = generator.next()
            val eventLabel = "$label ${provider.kind} $mode exercise $index"
            val compiled = try {
                compiler.compile(exercise, definition, catalog)
            } catch (failure: RecipeValidationException) {
                throw AssertionError("$eventLabel failed to compile: ${failure.message}", failure)
            }

            assertTrue(
                "$eventLabel timeline must not be empty",
                compiled.timeline.events.isNotEmpty()
            )
            assertTrue("$eventLabel total must be positive", compiled.total > Duration.ZERO)
            assertEquals(
                "$eventLabel exactly one answer start",
                1,
                compiled.timeline.events.count { it is AnswerStartedEvent }
            )
            assertTrue(
                "$eventLabel total ${compiled.total} stays inside the " +
                    "${RecognitionRecipe.EXERCISE_CAP} cap",
                compiled.total <= RecognitionRecipe.EXERCISE_CAP
            )
            assertTrue(
                "$eventLabel answer must start before the exercise ends",
                compiled.answerStart < compiled.total
            )

            // The compiler schedules the concrete groups in generation order: context groups then
            // target groups. Recognition and Exposure hear one example, Association two.
            val expected = exercise.groups.flatMap { group -> group.notes }.map { it.value }
            val sounded = compiled.timeline.notes.map { it.note.value }
            val occurrences = if (mode == RecipeMode.ASSOCIATION) 2 else 1
            assertEquals(
                "$eventLabel example occurrences",
                List(occurrences) { expected }.flatten(),
                sounded
            )
            if (exercise.contextGroups.isNotEmpty()) {
                val contextCount = exercise.contextGroups.sumOf { it.notes.size }
                assertEquals(
                    "$eventLabel context groups come first",
                    exercise.contextGroups.flatMap { it.notes }.map { it.value },
                    sounded.take(contextCount)
                )
                assertEquals(
                    "$eventLabel target groups follow the context",
                    exercise.targetGroups.flatMap { it.notes }.map { it.value },
                    sounded.drop(contextCount).take(exercise.targetGroups.sumOf { it.notes.size })
                )
            }

            val notes = compiled.timeline.notes
            when (mode) {
                RecipeMode.RECOGNITION -> assertTrue(
                    "$eventLabel the example precedes the answer",
                    notes.all { it.startOffset < compiled.answerStart }
                )

                RecipeMode.EXPOSURE -> assertTrue(
                    "$eventLabel the answer precedes the example",
                    notes.all { it.startOffset >= compiled.answerStart }
                )

                RecipeMode.ASSOCIATION -> {
                    val unit = expected.size
                    assertTrue(
                        "$eventLabel the first mandatory example precedes the answer",
                        notes.take(unit).all { it.startOffset < compiled.answerStart }
                    )
                    assertTrue(
                        "$eventLabel the second mandatory example follows the answer",
                        notes.drop(unit).all { it.startOffset > compiled.answerStart }
                    )
                }
            }
        }
    }

    /**
     * The documented maximum-complexity configuration of every provider.
     *
     * Each entry is a candidate a user could actually save: the provider's own validator must
     * accept it, and every recipe must compile under it.
     */
    private fun maximumConfigurations(): Map<TrainingKind, List<Pair<String, ProviderConfig>>> =
        linkedMapOf(
            TrainingKind.INTERVALS to listOf(
                "all intervals, all roots, full range, mixed presentations" to
                    ProviderConfig.Intervals(
                        SequencePolicy(
                            definition = IntervalDefinition.of(
                                intervalIds = SimpleInterval.ALL.map { it.id },
                                rootValues = PitchClass.ALL.map { it.value },
                                minimumNote = MidiNote.of(MidiNote.MIN_VALUE),
                                maximumNote = MidiNote.of(MidiNote.MAX_VALUE),
                                presentation = IntervalPresentation.ASCENDING
                            ),
                            presentations = IntervalPresentation.entries.sortedBy { it.ordinal },
                            mixedPresentation = true
                        )
                    )
            ),
            TrainingKind.INTERVAL_WALK to listOf(
                "all simple intervals, mixed direction, four steps, full start range" to
                    ProviderConfig.IntervalWalks(
                        IntervalWalkDefinition.of(
                            intervalIds = SimpleInterval.ALL.map { it.id },
                            rootValues = PitchClass.ALL.map { it.value },
                            minimumNote = MidiNote.of(21),
                            maximumNote = MidiNote.of(108),
                            direction = WalkDirection.MIXED,
                            length = 4
                        )
                    )
            ),
            TrainingKind.CHORDS to listOf(
                "all qualities with every applicable inversion, arpeggio, inversion answers" to
                    ProviderConfig.Chords(
                        ChordDefinition.of(
                            qualityIds = ChordQuality.entries.map { it.id },
                            // Inversions 0..3 are all valid: the seventh qualities apply 0..3 and
                            // the definition itself filters inapplicable pairs per quality.
                            inversions = (0..3).toList(),
                            rootValues = PitchClass.ALL.map { it.value },
                            fixedRoot = null,
                            minimumNote = MidiNote.of(MidiNote.MIN_VALUE),
                            maximumNote = MidiNote.of(MidiNote.MAX_VALUE),
                            presentation = ChordPresentation.ARPEGGIO,
                            answerMode = ChordAnswerMode.QUALITY_AND_INVERSION
                        )
                    )
            ),
            TrainingKind.SCALES to listOf(
                "all scales in both traversals, all roots, full range" to
                    ProviderConfig.Scales(
                        ScaleDefinition.of(
                            scaleIds = ScaleKind.entries.map { it.id },
                            direction = ScaleDirection.BOTH,
                            rootValues = PitchClass.ALL.map { it.value },
                            fixedRoot = null,
                            minimumNote = MidiNote.of(MidiNote.MIN_VALUE),
                            maximumNote = MidiNote.of(MidiNote.MAX_VALUE)
                        )
                    )
            ),
            TrainingKind.NOTE_IDENTIFICATION to listOf(
                "all pitch classes, full range, flat names with octave speech" to
                    ProviderConfig.Notes(
                        NoteDefinition.of(
                            pitchClassValues = PitchClass.ALL.map { it.value },
                            // The flat table exercises the other chromatic spelling.
                            accidental = SpellingPreference.FLAT,
                            speakOctave = true,
                            minimumNote = MidiNote.of(MidiNote.MIN_VALUE),
                            maximumNote = MidiNote.of(MidiNote.MAX_VALUE)
                        )
                    )
            ),
            TrainingKind.SCALE_DEGREES to buildList {
                // The answer format is a single-valued control, so every format is a candidate of
                // its own.
                for (format in DegreeAnswerFormat.entries) {
                    add(
                        "all keys, both tonalities, all degrees, " +
                            "${format.name.lowercase()} answers" to
                            ProviderConfig.ScaleDegrees(
                                ScaleDegreeDefinition.of(
                                    keyValues = PitchClass.ALL.map { it.value },
                                    tonalities = Tonality.entries,
                                    degrees = (1..7).toList(),
                                    // The longest context: three complete cadence triads.
                                    context = ContextKind.SUBDOMINANT_DOMINANT_TONIC,
                                    contextGap = 500.milliseconds,
                                    answerFormat = format,
                                    spelling = SpellingPreference.SHARP,
                                    minimumNote = MidiNote.of(MidiNote.MIN_VALUE),
                                    maximumNote = MidiNote.of(MidiNote.MAX_VALUE)
                                )
                            )
                    )
                }
            },
            TrainingKind.CHORD_PROGRESSIONS to listOf(
                "all chords and keys, nearest-inversion voicing, Roman answers" to
                    ProviderConfig.Progressions(
                        ProgressionDefinition.of(
                            chords = DiatonicChord.entries,
                            fixedRoot = null,
                            voicing = ProgressionVoicingMode.NEAREST_INVERSION,
                            allowedInversions = listOf(0, 1, 2, 3),
                            answerFormat = ProgressionAnswerFormat.ROMAN,
                            spelling = SpellingPreference.SHARP,
                            minimumNote = MidiNote.of(MidiNote.MIN_VALUE),
                            maximumNote = MidiNote.of(MidiNote.MAX_VALUE)
                        )
                    )
            ),
            TrainingKind.INTERVALS_IN_CONTEXT to buildList {
                // The presentation and the answer format are single-valued controls, so the two
                // directional presentations are crossed with every answer format.
                for (presentation in listOf(
                    IntervalPresentation.DESCENDING,
                    IntervalPresentation.HARMONIC
                )) {
                    for (format in ContextualIntervalAnswerFormat.entries) {
                        add(
                            "all keys, both tonalities, all intervals, " +
                                "${presentation.name.lowercase()} presentation, " +
                                "${format.name.lowercase()} answers" to
                                ProviderConfig.ContextualIntervals(
                                    ContextualIntervalDefinition.of(
                                        keyValues = PitchClass.ALL.map { it.value },
                                        tonalities = Tonality.entries,
                                        intervalIds = SimpleInterval.ALL.map { it.id },
                                        presentation = presentation,
                                        context = ContextKind.SUBDOMINANT_DOMINANT_TONIC,
                                        contextGap = 500.milliseconds,
                                        answerFormat = format,
                                        spelling = SpellingPreference.SHARP,
                                        minimumNote = MidiNote.of(MidiNote.MIN_VALUE),
                                        maximumNote = MidiNote.of(MidiNote.MAX_VALUE)
                                    )
                                )
                        )
                    }
                }
            },
            TrainingKind.MELODIC_DICTATION to listOf(
                "sixteen-note phrases, every step and leap, all keys, full range" to
                    ProviderConfig.MelodicDictation(
                        MelodicDictationDefinition.of(
                            keyValues = PitchClass.ALL.map { it.value },
                            tonalities = Tonality.entries,
                            context = ContextKind.SUBDOMINANT_DOMINANT_TONIC,
                            contextGap = 500.milliseconds,
                            minimumNote = MidiNote.of(MidiNote.MIN_VALUE),
                            maximumNote = MidiNote.of(MidiNote.MAX_VALUE),
                            phraseLength = MelodicDictationDefinition.MAX_PHRASE_LENGTH,
                            startDegree = null,
                            endDegree = null,
                            allowedSteps = everyAllowedStep,
                            maximumLeap = MelodicDictationDefinition.MAX_LEAP,
                            bias = MelodicDirectionBias.BALANCED,
                            // The default degree sequence. The directed-interval format with a
                            // full 16-note phrase and the longest clips can exceed the 60 s cap
                            // (about 65 s for recognition and 81 s for association), so the
                            // largest valid maximum keeps the degree sequence; the directed format
                            // is exercised at its own bounds by the provider tests.
                            answerFormat = MelodicAnswerFormat.DEGREE_SEQUENCE,
                            spelling = SpellingPreference.SHARP
                        )
                    )
            )
        )

    private companion object {
        /**
         * Three exercises per configuration catches bag and cycle boundaries, not only the first
         * draw.
         */
        const val EXERCISES_PER_CONFIGURATION = 3
    }
}
