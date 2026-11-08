package com.thomas.passiveeartraining.training.provider

import com.thomas.passiveeartraining.music.IntervalPresentation
import com.thomas.passiveeartraining.music.MidiNote
import com.thomas.passiveeartraining.music.SimpleInterval
import com.thomas.passiveeartraining.music.SpellingPreference
import com.thomas.passiveeartraining.music.Tonality
import com.thomas.passiveeartraining.training.BundledVoiceDurations
import com.thomas.passiveeartraining.training.ContextualIntervalAnswerFormat
import com.thomas.passiveeartraining.training.ContextualIntervalDefinition
import com.thomas.passiveeartraining.training.MapVoiceDurationCatalog
import com.thomas.passiveeartraining.training.NoteEvent
import com.thomas.passiveeartraining.training.SeededRandomSource
import com.thomas.passiveeartraining.training.VoiceAssetId
import com.thomas.passiveeartraining.training.context.ContextBuilder
import com.thomas.passiveeartraining.training.context.ContextKind
import com.thomas.passiveeartraining.training.recipe.RecipeDefinition
import com.thomas.passiveeartraining.training.recipe.RecipeMode
import com.thomas.passiveeartraining.training.recipe.RecipeTiming
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * M9B.1/M9B.2/M9B-T01..T03: the contextual-interval provider's finite draws, child streams, minor
 * context rule, unison/octave handling and validation.
 *
 * The frozen vectors were recorded with a temporary printer against the default configuration and
 * an A-minor descending cadence configuration before the assertions were embedded.
 */
class ContextualIntervalExerciseProviderTest {

    private val provider = ContextualIntervalExerciseProvider()

    private val minorThird = SimpleInterval.of(SimpleInterval.MINOR_THIRD)
    private val majorThird = SimpleInterval.of(SimpleInterval.MAJOR_THIRD)
    private val minorSixth = SimpleInterval.of(SimpleInterval.MINOR_SIXTH)

    private fun definition(
        keys: List<Int> = listOf(0),
        tonalities: List<Tonality> = listOf(Tonality.MAJOR),
        intervals: List<String> = listOf("minor_third", "major_third", "perfect_fifth"),
        presentation: IntervalPresentation = IntervalPresentation.ASCENDING,
        context: ContextKind = ContextKind.TONIC_TRIAD,
        gap: Duration = 500.milliseconds,
        answerFormat: ContextualIntervalAnswerFormat = ContextualIntervalAnswerFormat.INTERVAL,
        spelling: SpellingPreference = SpellingPreference.SHARP,
        minimum: Int = 60,
        maximum: Int = 72
    ): ContextualIntervalDefinition = ContextualIntervalDefinition.of(
        keyValues = keys,
        tonalities = tonalities,
        intervalIds = intervals,
        presentation = presentation,
        context = context,
        contextGap = gap,
        answerFormat = answerFormat,
        spelling = spelling,
        minimumNote = MidiNote.of(minimum),
        maximumNote = MidiNote.of(maximum)
    )

    private fun config(
        keys: List<Int> = listOf(0),
        tonalities: List<Tonality> = listOf(Tonality.MAJOR),
        intervals: List<String> = listOf("minor_third", "major_third", "perfect_fifth"),
        presentation: IntervalPresentation = IntervalPresentation.ASCENDING,
        context: ContextKind = ContextKind.TONIC_TRIAD,
        gap: Duration = 500.milliseconds,
        answerFormat: ContextualIntervalAnswerFormat = ContextualIntervalAnswerFormat.INTERVAL,
        spelling: SpellingPreference = SpellingPreference.SHARP,
        minimum: Int = 60,
        maximum: Int = 72
    ): ProviderConfig.ContextualIntervals = ProviderConfig.ContextualIntervals(
        definition(
            keys = keys,
            tonalities = tonalities,
            intervals = intervals,
            presentation = presentation,
            context = context,
            gap = gap,
            answerFormat = answerFormat,
            spelling = spelling,
            minimum = minimum,
            maximum = maximum
        )
    )

    private fun firstExercises(
        seed: Long,
        count: Int,
        config: ProviderConfig = provider.defaults()
    ): List<ConcreteExercise> = provider.createGenerator(config, seed).let { generator ->
        (1..count).map { generator.next() }
    }

    private fun fingerprint(exercise: ConcreteExercise): String {
        val interval = exercise.provenance.generatedContextualInterval!!
        return "${exercise.categoryId}|key=${interval.keyPitchClass}|${interval.tonality}|" +
            "context=${interval.contextKind}|anchor=${interval.anchorMidi}|" +
            "contextValues=${interval.contextValues.joinToString(",")}|" +
            "interval=${interval.intervalId}|${interval.presentation}|" +
            "targets=${interval.firstTargetMidi}->${interval.secondTargetMidi}|" +
            "degrees=${interval.firstDegree}/${interval.secondDegree}|" +
            "tokens=${exercise.answer.voiceIds.joinToString(",") { it.value }}|" +
            "label=${exercise.answer.display.label()}"
    }

    @Test
    fun theProviderIdentityIsIntervalsInContextAtVersionOne() {
        assertEquals(TrainingKind.INTERVALS_IN_CONTEXT, provider.kind)
        assertEquals(1, provider.providerVersion)
        assertEquals(
            ProviderConfig.ContextualIntervals(ContextualIntervalDefinition.default()),
            provider.defaults()
        )
        val exercise = firstExercises(seed = 1L, count = 1).single()
        assertEquals(TrainingKind.INTERVALS_IN_CONTEXT, exercise.kind)
        val interval = exercise.provenance.generatedContextualInterval!!
        assertEquals(interval.intervalId, exercise.categoryId)
        assertTrue(interval.intervalId in listOf("minor_third", "major_third", "perfect_fifth"))
    }

    @Test
    fun theDefaultConfigurationValidatesWithTheBundledCatalog() {
        val result = provider.validate(provider.defaults(), BundledVoiceDurations)
        assertTrue(
            "the default contextual-interval setup must validate: $result",
            result is ProviderValidation.Valid
        )
        val valid = result as ProviderValidation.Valid
        assertEquals(
            listOf("minor_third", "major_third", "perfect_fifth"),
            valid.categories.map { it.id }
        )
        assertEquals(
            "the three interval phrases the INTERVAL format actually speaks",
            setOf(
                "interval.minor_third",
                "interval.major_third",
                "interval.perfect_fifth"
            ),
            valid.requiredAssets.map { it.value }.toSet()
        )
        assertEquals(
            "one tonic triad 700 ms + 500 ms context gap + two target notes and the gap",
            2700L,
            valid.maximumExampleDuration.inWholeMilliseconds
        )
        assertEquals(
            "the longest reachable answer is the perfect-fifth clip",
            BundledVoiceDurations.durationOf(VoiceAssetId("interval.perfect_fifth")),
            valid.maximumAnswerDuration
        )
    }

    @Test
    fun aMissingAnswerClipIsReportedOnTheAnswersFieldBeforeTheFirstDraw() {
        val withoutFifth = MapVoiceDurationCatalog(
            VoiceVocabulary.entries
                .filterNot { it.id == VoiceAssetId("interval.perfect_fifth") }
                .associate { it.id to 500.milliseconds }
        )
        val result = provider.validate(provider.defaults(), withoutFifth)
        assertTrue(result is ProviderValidation.Invalid)
        val issues = (result as ProviderValidation.Invalid).issues
        assertEquals(1, issues.size)
        assertEquals("answers", issues.single().field)
        assertTrue(
            "the missing clip must be named: $issues",
            issues.single().message.contains("interval.perfect_fifth")
        )

        val withoutSeven = MapVoiceDurationCatalog(
            VoiceVocabulary.entries
                .filterNot { it.id == VoiceAssetId("degree.seven") }
                .associate { it.id to 500.milliseconds }
        )
        val degreeConfig = config(answerFormat = ContextualIntervalAnswerFormat.DEGREE_PAIR)
        val degreeResult = provider.validate(degreeConfig, withoutSeven)
        assertTrue(
            "the missing degree clip must be reported: $degreeResult",
            degreeResult is ProviderValidation.Invalid
        )
        assertTrue(
            "the missing degree clip must be named: $degreeResult",
            (degreeResult as ProviderValidation.Invalid).issues.single().message
                .contains("degree.seven")
        )
    }

    @Test
    fun aTopEdgeIntervalWithoutATargetPairIsReportedOnTheRangeField() {
        val tiny = config(minimum = 105, maximum = 108)
        val result = provider.validate(tiny, BundledVoiceDurations)
        assertTrue(result is ProviderValidation.Invalid)
        val issues = (result as ProviderValidation.Invalid).issues
        assertTrue("a top-edge range is a range error: $issues", issues.all { it.field == "range" })
        assertTrue(
            "the infeasible construction is named with its key and range: $issues",
            issues.all {
                it.message.contains("C major") &&
                    it.message.contains("105..108") &&
                    it.message.contains("ascending")
            }
        )
    }

    @Test
    fun aForeignConfigurationIsRejectedOnTheKindField() {
        val result = provider.validate(
            ProviderConfig.ScaleDegrees(
                com.thomas.passiveeartraining.training.ScaleDegreeDefinition.default()
            ),
            BundledVoiceDurations
        )
        assertTrue(result is ProviderValidation.Invalid)
        assertEquals("kind", (result as ProviderValidation.Invalid).issues.single().field)
    }

    @Test
    fun everyIntervalInEveryKeyAndTonalityHasACompletionInAWideRange() {
        val wideRange = 48..84
        for (presentation in IntervalPresentation.entries) {
            val wide = config(
                keys = emptyList(),
                tonalities = Tonality.entries,
                intervals = SimpleInterval.ALL.map { it.id },
                presentation = presentation,
                minimum = wideRange.first,
                maximum = wideRange.last
            )
            val result = provider.validate(wide, BundledVoiceDurations)
            assertTrue(
                "all intervals must validate for $presentation: $result",
                result is ProviderValidation.Valid
            )
            for (keyValue in 0..11) {
                for (tonality in Tonality.entries) {
                    for (interval in SimpleInterval.ALL) {
                        assertTrue(
                            "$keyValue/$tonality/$interval/$presentation must have a completion",
                            wide.definition.supports(keyValue, tonality, interval)
                        )
                    }
                }
            }
        }
    }

    @Test
    fun seed42ContextualDrawsStayStableAndPlayable() {
        val first = firstExercises(seed = 42L, count = 8)
        val second = firstExercises(seed = 42L, count = 8)
        assertEquals(first.map { fingerprint(it) }, second.map { fingerprint(it) })
        assertTrue(
            "every generated target is playable",
            first.all { exercise ->
                val interval = exercise.provenance.generatedContextualInterval!!
                interval.firstTargetMidi in 21..108 && interval.secondTargetMidi in 21..108
            }
        )
    }

    @Test
    fun theSameSeedRepeatsAndDifferentSeedsDiverge() {
        val first = firstExercises(seed = 21L, count = 6).map { fingerprint(it) }
        val second = firstExercises(seed = 21L, count = 6).map { fingerprint(it) }
        assertEquals(first, second)
        assertNotEquals(first, firstExercises(seed = 22L, count = 6).map { fingerprint(it) })
    }

    @Test
    fun theBagCoversEverySelectedIntervalBeforeRepeating() {
        val exercises = firstExercises(seed = 42L, count = 7)
        assertEquals(
            "the first bag contains every interval exactly once",
            setOf("minor_third", "major_third", "perfect_fifth"),
            exercises.take(3).map { it.categoryId }.toSet()
        )
        assertEquals(
            listOf(0, 1, 2, 0, 1, 2, 0),
            exercises.map { it.cycle!!.positionInCycle }
        )
        assertEquals(
            listOf(0, 0, 0, 1, 1, 1, 2),
            exercises.map { it.cycle!!.cycleIndex }
        )
        assertEquals((0..6).toList(), exercises.map { it.sequenceIndex })
    }

    @Test
    fun oneContextAndOneTargetDrawHappenPerExerciseEvenForSingletonLabels() {
        val single = config(
            intervals = listOf("minor_third"),
            maximum = 79
        )
        val constructions = single.definition.eligibleConstructions(0, Tonality.MAJOR)
        assertEquals("C4 and C5 triads are the two eligible constructions", 2, constructions.size)
        val pairs = single.definition.eligiblePairs(0, Tonality.MAJOR, minorThird)
        assertEquals(29, pairs.size)

        val contextStream = SeededRandomSource(
            ModeSeedDerivation.derive(
                11L,
                TrainingKind.INTERVALS_IN_CONTEXT,
                0L,
                ModeSeedDerivation.CONTEXT
            )
        )
        val targetStream = SeededRandomSource(
            ModeSeedDerivation.derive(
                11L,
                TrainingKind.INTERVALS_IN_CONTEXT,
                0L,
                ModeSeedDerivation.TARGET
            )
        )
        firstExercises(seed = 11L, count = 5, config = single).forEach { exercise ->
            val interval = exercise.provenance.generatedContextualInterval!!
            assertEquals(
                "one context draw per exercise",
                contextStream.nextInt(constructions.size),
                constructions.indexOfFirst { it.anchor.value == interval.anchorMidi }
            )
            assertEquals(
                "one pair draw per exercise",
                targetStream.nextInt(pairs.size),
                pairs.indexOfFirst { it.first.value == interval.firstTargetMidi }
            )
        }
    }

    @Test
    fun aNoOpReconfigureContinuesAllThreeStreams() {
        val uninterrupted = firstExercises(seed = 21L, count = 4)
        val generator = provider.createGenerator(provider.defaults(), 21L)
        generator.next()
        generator.next()
        generator.reconfigure(provider.defaults())
        val continued = listOf(generator.next(), generator.next())
        assertEquals(
            uninterrupted.drop(2).map { fingerprint(it) },
            continued.map { fingerprint(it) }
        )
    }

    @Test
    fun reconfigureContinuesTheSequenceAndDropsTheOldBag() {
        val generator = provider.createGenerator(provider.defaults(), 21L)
        generator.next()
        generator.next()
        generator.reconfigure(config(intervals = listOf("minor_third")))
        val after = generator.next()
        assertEquals("minor_third", after.categoryId)
        assertEquals("the sequence index keeps moving", 2, after.sequenceIndex)
        assertEquals("the edited bag restarts at its first slot", 0, after.cycle!!.positionInCycle)
    }

    @Test
    fun contextGroupsPrecedeTheTwoSequentialTargetGroups() {
        val exercise = firstExercises(seed = 42L, count = 1).single()
        val interval = exercise.provenance.generatedContextualInterval!!
        assertEquals(GroupRole.CONTEXT, exercise.groups.first().role)
        assertEquals(
            "the tonic triad is one context group",
            1,
            exercise.contextGroups.size
        )
        assertEquals(
            interval.contextValues,
            exercise.contextGroups.flatMap { group -> group.notes.map { it.value } }
        )
        assertEquals(2, exercise.targetGroups.size)
        assertEquals(
            listOf(interval.firstTargetMidi, interval.secondTargetMidi),
            exercise.targetGroups.map { group -> group.notes.single().value }
        )
        assertTrue(
            "every group before the targets is context",
            exercise.groups.take(exercise.groups.size - 2).all { it.role == GroupRole.CONTEXT }
        )
    }

    @Test
    fun harmonicTargetsAreOneSimultaneousGroupInLowToHighOrder() {
        val exercise = firstExercises(
            seed = 42L,
            count = 1,
            config = config(presentation = IntervalPresentation.HARMONIC)
        ).single()
        val interval = exercise.provenance.generatedContextualInterval!!
        assertEquals(1, exercise.targetGroups.size)
        assertEquals(
            listOf(interval.firstTargetMidi, interval.secondTargetMidi).sorted(),
            exercise.targetGroups.single().notes.map { it.value }
        )
        assertTrue(interval.firstTargetMidi < interval.secondTargetMidi)
    }

    @Test
    fun harmonicUnisonDeduplicatesTheSoundingPitchButKeepsBothDegrees() {
        val harmonic = firstExercises(
            seed = 5L,
            count = 1,
            config = config(
                intervals = listOf("unison"),
                presentation = IntervalPresentation.HARMONIC,
                answerFormat = ContextualIntervalAnswerFormat.DEGREE_PAIR
            )
        ).single()
        val harmonicInterval = harmonic.provenance.generatedContextualInterval!!
        assertEquals(1, harmonic.targetGroups.size)
        assertEquals(
            "one sounding voice for the same pitch",
            1,
            harmonic.targetGroups.single().notes.size
        )
        assertEquals(harmonicInterval.firstTargetMidi, harmonicInterval.secondTargetMidi)
        assertEquals(harmonicInterval.firstDegree, harmonicInterval.secondDegree)
        val degreeToken = "degree." + listOf(
            "one",
            "two",
            "three",
            "four",
            "five",
            "six",
            "seven"
        )[harmonicInterval.firstDegree - 1]
        assertEquals(
            "the answer still states the two-target relationship",
            listOf(degreeToken, degreeToken),
            harmonic.answer.voiceIds.map { it.value }
        )
    }

    @Test
    fun sequentialUnisonKeepsTwoTargetGroupsAndTwoDegrees() {
        val sequential = firstExercises(
            seed = 5L,
            count = 1,
            config = config(
                intervals = listOf("unison"),
                answerFormat = ContextualIntervalAnswerFormat.DEGREE_PAIR
            )
        ).single()
        val interval = sequential.provenance.generatedContextualInterval!!
        assertEquals(2, sequential.targetGroups.size)
        assertEquals(
            "the same pitch sounds in both slots",
            listOf(interval.firstTargetMidi, interval.secondTargetMidi),
            sequential.targetGroups.map { group -> group.notes.single().value }
        )
        assertEquals(2, sequential.answer.voiceIds.size)
        assertEquals(interval.firstDegree, interval.secondDegree)
    }

    @Test
    fun octaveTargetsShareTheDegreeWithDifferentMidiValues() {
        val exercise = firstExercises(
            seed = 5L,
            count = 1,
            config = config(
                intervals = listOf("octave"),
                answerFormat = ContextualIntervalAnswerFormat.DEGREE_PAIR
            )
        ).single()
        val interval = exercise.provenance.generatedContextualInterval!!
        assertEquals(12, interval.secondTargetMidi - interval.firstTargetMidi)
        assertEquals(interval.firstDegree, interval.secondDegree)
        val tokens = exercise.answer.voiceIds.map { it.value }
        assertEquals(2, tokens.size)
        assertEquals(
            "both answer tokens speak the same degree",
            tokens[0],
            tokens[1]
        )
    }

    @Test
    fun theMinorContextLeadingToneNeverEntersTheNaturalMinorTargetPool() {
        val definition = config(
            keys = listOf(9),
            tonalities = listOf(Tonality.NATURAL_MINOR),
            intervals = listOf("minor_sixth", "major_third"),
            presentation = IntervalPresentation.DESCENDING,
            context = ContextKind.TONIC_TRIAD,
            minimum = 79,
            maximum = 88
        ).definition

        val targetValues = definition.targetNotesFor(9, Tonality.NATURAL_MINOR)
        assertTrue(targetValues.isNotEmpty())
        assertTrue(
            "G sharp is never a natural-minor target note",
            targetValues.none { it.value.mod(12) == 8 }
        )
        assertTrue(definition.eligibleConstructions(9, Tonality.NATURAL_MINOR).isNotEmpty())
        for (interval in SimpleInterval.ALL) {
            for (presentation in IntervalPresentation.entries) {
                definition.eligiblePairs(9, Tonality.NATURAL_MINOR, interval, presentation)
                    .forEach { pair ->
                        assertNotEquals(8, pair.first.value.mod(12))
                        assertNotEquals(8, pair.second.value.mod(12))
                    }
            }
        }

        val result = provider.validate(
            ProviderConfig.ContextualIntervals(definition),
            BundledVoiceDurations
        )
        assertTrue(
            "the natural-minor selection remains playable: $result",
            result is ProviderValidation.Valid
        )
        val gbOnly = provider.validate(
            config(
                keys = listOf(9),
                tonalities = listOf(Tonality.NATURAL_MINOR),
                intervals = listOf("major_third"),
                presentation = IntervalPresentation.DESCENDING,
                context = ContextKind.TONIC_TRIAD,
                minimum = 79,
                maximum = 88
            ),
            BundledVoiceDurations
        )
        assertTrue("the G/B pair must validate: $gbOnly", gbOnly is ProviderValidation.Valid)
    }

    @Test
    fun theRaisedLeadingToneOfTheMinorCadenceStaysInTheContext() {
        val cadence = config(
            keys = listOf(9),
            tonalities = listOf(Tonality.NATURAL_MINOR),
            intervals = listOf("minor_third", "perfect_fifth"),
            presentation = IntervalPresentation.ASCENDING,
            context = ContextKind.TONIC_DOMINANT_TONIC,
            answerFormat = ContextualIntervalAnswerFormat.DEGREE_PAIR,
            minimum = 69,
            maximum = 84
        )
        val valid = provider.validate(cadence, BundledVoiceDurations)
        assertTrue("the minor cadence validates: $valid", valid is ProviderValidation.Valid)

        val exercises = firstExercises(seed = 3L, count = 6, config = cadence)
        exercises.forEach { exercise ->
            val interval = exercise.provenance.generatedContextualInterval!!
            assertTrue(
                "the minor V context always carries the raised leading tone: $interval",
                interval.contextValues.any { it.mod(12) == 8 }
            )
            for (target in listOf(interval.firstTargetMidi, interval.secondTargetMidi)) {
                assertNotEquals("a target is never G sharp", 8, target.mod(12))
                assertNotNull(
                    "every target stays a natural-minor degree",
                    cadence.definition.degreeOfNote(9, Tonality.NATURAL_MINOR, MidiNote.of(target))
                )
            }
            assertTrue(
                "the answer speaks degrees, not context notes",
                exercise.answer.voiceIds.all { it.value.startsWith("degree.") }
            )
        }
    }

    @Test
    fun theCompilerPlacesTheContextGapBetweenTheContextAndBothTargets() {
        val exercise = firstExercises(seed = 42L, count = 1).single()
        val definition = (provider.defaults() as ProviderConfig.ContextualIntervals).definition
        assertEquals(500L, definition.contextGap.inWholeMilliseconds)
        val recipe = RecipeDefinition(
            mode = RecipeMode.RECOGNITION,
            timing = RecipeTiming(contextGap = definition.contextGap)
        )
        val compiled = ExerciseCompiler().compile(exercise, recipe, BundledVoiceDurations)
        val notes = compiled.timeline.events.filterIsInstance<NoteEvent>()
        val interval = exercise.provenance.generatedContextualInterval!!
        val contextEnd = ContextBuilder.duration(recipe.timing, definition.context)
        assertEquals(
            "one 700 ms context + one 500 ms gap",
            1200L,
            (contextEnd + definition.contextGap).inWholeMilliseconds
        )
        val targetEvents = notes.filter { it.startOffset > Duration.ZERO }
        val first = targetEvents.filter { it.note.value == interval.firstTargetMidi }
        val second = targetEvents.filter { it.note.value == interval.secondTargetMidi }
        assertTrue(first.isNotEmpty() && second.isNotEmpty())
        assertEquals(
            contextEnd + definition.contextGap,
            first.single().startOffset
        )
        assertEquals(
            contextEnd + definition.contextGap + recipe.timing.noteDuration +
                recipe.timing.melodicGap,
            second.single().startOffset
        )
        assertEquals(
            "every context note sounds at offset zero",
            interval.contextValues.toSet(),
            notes.filter { it.startOffset == Duration.ZERO }.map { it.note.value }.toSet()
        )

        val harmonic = firstExercises(
            seed = 42L,
            count = 1,
            config = config(presentation = IntervalPresentation.HARMONIC)
        ).single()
        val harmonicCompiled = ExerciseCompiler().compile(harmonic, recipe, BundledVoiceDurations)
        val harmonicInterval = harmonic.provenance.generatedContextualInterval!!
        val harmonicTargetNotes = harmonicCompiled.timeline.events.filterIsInstance<NoteEvent>()
            .filter { it.startOffset > Duration.ZERO }
        assertEquals(
            "the harmonic example sounds exactly its two target notes after the gap",
            setOf(harmonicInterval.firstTargetMidi, harmonicInterval.secondTargetMidi),
            harmonicTargetNotes.map { it.note.value }.toSet()
        )
        harmonicTargetNotes.forEach {
            assertEquals(
                "a harmonic pair shares one start after the gap",
                contextEnd + definition.contextGap,
                it.startOffset
            )
        }
    }

    @Test
    fun theContentBoundsMeasureTheContextGapAndThePresentation() {
        val default = (provider.defaults() as ProviderConfig.ContextualIntervals).definition
        assertEquals(
            "700 + 500 + 2*700 + 100",
            2700L,
            ContextualIntervalExerciseProvider.maximumExampleDuration(default)
                .inWholeMilliseconds
        )

        val cadence = definition(
            keys = listOf(9),
            tonalities = listOf(Tonality.NATURAL_MINOR),
            intervals = listOf("minor_third"),
            context = ContextKind.TONIC_DOMINANT_TONIC,
            minimum = 57,
            maximum = 84
        )
        assertEquals(
            "3*700 + 2*100 + 500 + two target notes and the gap",
            4300L,
            ContextualIntervalExerciseProvider.maximumExampleDuration(cadence)
                .inWholeMilliseconds
        )
        assertEquals(
            "the harmonic shape replaces the two-note target with one note",
            3500L,
            ContextualIntervalExerciseProvider
                .maximumExampleDuration(
                    definition(
                        keys = listOf(9),
                        tonalities = listOf(Tonality.NATURAL_MINOR),
                        intervals = listOf("minor_third"),
                        context = ContextKind.TONIC_DOMINANT_TONIC,
                        presentation = IntervalPresentation.HARMONIC,
                        minimum = 57,
                        maximum = 84
                    )
                )
                .inWholeMilliseconds
        )

        val catalog = MapVoiceDurationCatalog(
            buildMap {
                VoiceVocabulary.entries.forEach { put(it.id, 100.milliseconds) }
                put(VoiceAssetId("interval.minor_third"), 300.milliseconds)
                put(VoiceAssetId("degree.seven"), 250.milliseconds)
            }
        )
        val both = config(answerFormat = ContextualIntervalAnswerFormat.INTERVAL_AND_DEGREES)
        val valid = provider.validate(both, catalog) as ProviderValidation.Valid
        assertEquals(
            "minor third 300 ms + 80 + degree 100 + 80 + degree 250",
            810L,
            valid.maximumAnswerDuration.inWholeMilliseconds
        )
    }

    @Test
    fun requiredVocabularyFollowsTheAnswerFormat() {
        assertEquals(
            "INTERVAL needs only the selected interval phrases",
            setOf("interval.minor_third", "interval.major_third", "interval.perfect_fifth"),
            provider.requiredVocabulary(provider.defaults()).map { it.value }.toSet()
        )
        assertEquals(
            "DEGREE_PAIR needs only the reachable degree numbers",
            (1..7).map { VoiceVocabulary.degreeNumberEntry(it).id.value }.toSet(),
            provider.requiredVocabulary(
                config(answerFormat = ContextualIntervalAnswerFormat.DEGREE_PAIR)
            ).map { it.value }.toSet()
        )
        assertEquals(
            "INTERVAL_AND_DEGREES reaches both namespaces",
            setOf(
                "interval.minor_third",
                "interval.major_third",
                "interval.perfect_fifth",
                "degree.one",
                "degree.two",
                "degree.three",
                "degree.four",
                "degree.five",
                "degree.six",
                "degree.seven"
            ),
            provider.requiredVocabulary(
                config(answerFormat = ContextualIntervalAnswerFormat.INTERVAL_AND_DEGREES)
            ).map { it.value }.toSet()
        )
        listOf(
            ContextualIntervalAnswerFormat.INTERVAL,
            ContextualIntervalAnswerFormat.DEGREE_PAIR,
            ContextualIntervalAnswerFormat.INTERVAL_AND_DEGREES
        ).forEach { format ->
            provider.requiredVocabulary(config(answerFormat = format)).forEach { token ->
                assertNotNull(
                    "the vocabulary must declare $token",
                    VoiceVocabulary.ofIdOrNull(token)
                )
            }
        }
    }

    @Test
    fun everyReachableAnswerTokenMatchesTheSpokenDisplay() {
        listOf(
            ContextualIntervalAnswerFormat.INTERVAL,
            ContextualIntervalAnswerFormat.DEGREE_PAIR,
            ContextualIntervalAnswerFormat.INTERVAL_AND_DEGREES
        ).forEach { format ->
            val exercise = firstExercises(
                seed = 1L,
                count = 1,
                config = config(answerFormat = format)
            ).single()
            assertEquals(
                "display and speech are one projection for $format",
                exercise.answer.voiceIds.joinToString(" ") {
                    VoiceVocabulary.requireEntry(it).spokenText
                },
                exercise.answer.display.label()
            )
        }
    }

    @Test
    fun theProvenanceSeedIsTheActivationSeed() {
        val exercise = firstExercises(seed = 99L, count = 1).single()
        assertEquals(99L, exercise.provenance.seed)
        assertEquals(TrainingKind.INTERVALS_IN_CONTEXT.id, exercise.provenance.providerId)
        assertEquals(1, exercise.provenance.providerVersion)
        assertEquals(SeededRandomSource.RNG_ID, exercise.provenance.rngId)
    }
}
