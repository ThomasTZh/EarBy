package com.thomas.passiveeartraining.training.provider

import com.thomas.passiveeartraining.music.MidiNote
import com.thomas.passiveeartraining.music.SpellingPreference
import com.thomas.passiveeartraining.music.Tonality
import com.thomas.passiveeartraining.training.BundledVoiceDurations
import com.thomas.passiveeartraining.training.DegreeAnswerFormat
import com.thomas.passiveeartraining.training.MapVoiceDurationCatalog
import com.thomas.passiveeartraining.training.NoteDefinition
import com.thomas.passiveeartraining.training.NoteEvent
import com.thomas.passiveeartraining.training.ScaleDegreeDefinition
import com.thomas.passiveeartraining.training.VoiceAssetId
import com.thomas.passiveeartraining.training.context.ContextBuilder
import com.thomas.passiveeartraining.training.context.ContextKind
import com.thomas.passiveeartraining.training.recipe.RecipeDefinition
import com.thomas.passiveeartraining.training.recipe.RecipeMode
import com.thomas.passiveeartraining.training.recipe.RecipeTiming
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * M8.1/M8.2/M8-T01..T05: the scale-degree provider's finite draws, child streams and vocabulary.
 *
 * The frozen vectors were recorded with a temporary printer against the default configuration and
 * the A-minor cadence configuration before the assertions were embedded.
 */
class ScaleDegreeExerciseProviderTest {

    private val provider = ScaleDegreeExerciseProvider()

    private fun definition(
        keys: List<Int> = listOf(0),
        tonalities: List<Tonality> = listOf(Tonality.MAJOR),
        degrees: List<Int> = (1..7).toList(),
        context: ContextKind = ContextKind.TONIC_TRIAD,
        gap: Duration = 500.milliseconds,
        answerFormat: DegreeAnswerFormat = DegreeAnswerFormat.DEGREE,
        spelling: SpellingPreference = SpellingPreference.SHARP,
        minimum: Int = 60,
        maximum: Int = 72
    ): ScaleDegreeDefinition = ScaleDegreeDefinition.of(
        keyValues = keys,
        tonalities = tonalities,
        degrees = degrees,
        context = context,
        contextGap = gap,
        answerFormat = answerFormat,
        spelling = spelling,
        minimumNote = MidiNote.of(minimum),
        maximumNote = MidiNote.of(maximum)
    )

    private fun config(
        context: ContextKind = ContextKind.TONIC_TRIAD,
        keys: List<Int> = listOf(0),
        tonalities: List<Tonality> = listOf(Tonality.MAJOR),
        degrees: List<Int> = (1..7).toList(),
        gap: Duration = 500.milliseconds,
        answerFormat: DegreeAnswerFormat = DegreeAnswerFormat.DEGREE,
        spelling: SpellingPreference = SpellingPreference.SHARP,
        minimum: Int = 60,
        maximum: Int = 72
    ): ProviderConfig.ScaleDegrees = ProviderConfig.ScaleDegrees(
        definition(
            keys = keys,
            tonalities = tonalities,
            degrees = degrees,
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
    ) = provider.createGenerator(config, seed).let { generator ->
        (1..count).map { generator.next() }
    }

    private fun fingerprint(exercise: ConcreteExercise): String {
        val degree = exercise.provenance.generatedScaleDegree!!
        return "${exercise.categoryId}|key=${degree.keyPitchClass}|${degree.tonality}|" +
            "${degree.contextKind}|anchor=${degree.anchorMidi}|" +
            "context=${degree.contextValues.joinToString(",")}|degree=${degree.degree}|" +
            "target=${degree.targetMidi}|" +
            "tokens=${exercise.answer.voiceIds.joinToString(",") { it.value }}|" +
            "label=${exercise.answer.display.label()}"
    }

    @Test
    fun theProviderIdentityIsTheScaleDegreesKindAtVersionOne() {
        assertEquals(TrainingKind.SCALE_DEGREES, provider.kind)
        assertEquals(1, provider.providerVersion)
        assertEquals(
            ProviderConfig.ScaleDegrees(ScaleDegreeDefinition.default()),
            provider.defaults()
        )
        val exercise = firstExercises(seed = 1L, count = 1).single()
        assertEquals(TrainingKind.SCALE_DEGREES, exercise.kind)
        val degree = exercise.provenance.generatedScaleDegree!!
        assertEquals("degree.${degree.degree}", exercise.categoryId)
    }

    @Test
    fun theDefaultConfigurationValidatesWithTheBundledCatalog() {
        val result = provider.validate(provider.defaults(), BundledVoiceDurations)
        assertTrue(
            "default scale-degree setup must validate: $result",
            result is ProviderValidation.Valid
        )
        val valid = result as ProviderValidation.Valid
        assertEquals((1..7).map { "degree.$it" }, valid.categories.map { it.id })
        assertEquals(
            (1..7).map { VoiceVocabulary.degreeNumberEntry(it).id.value }.toSet(),
            valid.requiredAssets.map { it.value }.toSet()
        )
        assertEquals(
            "one tonic triad 700 ms + 500 ms context gap + one target note 700 ms",
            1900L,
            valid.maximumExampleDuration.inWholeMilliseconds
        )
        assertEquals(
            "the longest reachable answer is the longest degree number clip",
            BundledVoiceDurations.durationOf(VoiceAssetId("degree.six")),
            valid.maximumAnswerDuration
        )
    }

    @Test
    fun aMissingAnswerClipIsReportedOnTheAnswersFieldBeforeTheFirstDraw() {
        val withoutFive = MapVoiceDurationCatalog(
            (1..7).filter { it != 5 }
                .associate { VoiceVocabulary.degreeNumberEntry(it).id to 500.milliseconds }
        )
        val result = provider.validate(provider.defaults(), withoutFive)
        assertTrue(result is ProviderValidation.Invalid)
        val issues = (result as ProviderValidation.Invalid).issues
        assertTrue(
            "every issue is an answer-clip issue: $issues",
            issues.all { it.field == "answers" }
        )
        assertTrue(
            "the missing clip must be named: $issues",
            issues.any { it.message.contains("degree.five") }
        )
        assertTrue(issues.all { it.message.contains("is missing from the voice pack") })
    }

    @Test
    fun aRootRangeWithNoPlayableContextIsReportedOnTheRangeField() {
        val config = config(minimum = 105, maximum = 108)
        val result = provider.validate(config, BundledVoiceDurations)
        assertTrue(result is ProviderValidation.Invalid)
        val issues = (result as ProviderValidation.Invalid).issues
        assertTrue("a top-edge range is a range error: $issues", issues.all { it.field == "range" })
        assertTrue(
            "every issue names the selected key and range: $issues",
            issues.all { it.message.contains("C major") && it.message.contains("105..108") }
        )

        // Targets are bounded by the playable samples, not by the root range.
        val targetful = config(
            context = ContextKind.TONIC_NOTE,
            degrees = listOf(1, 7),
            minimum = 60,
            maximum = 70
        )
        assertTrue(provider.validate(targetful, BundledVoiceDurations) is ProviderValidation.Valid)
        assertTrue(
            "degree 7 still has target notes above the root range",
            targetful.definition.targetNotesFor(0, Tonality.MAJOR, 7).isNotEmpty()
        )
    }

    @Test
    fun everyKeyTonalityAndDegreeHasACompletionWhenTheRangeAllowsIt() {
        val wideRange = 48..84
        for (kind in ContextKind.entries) {
            val wide = config(
                context = kind,
                keys = emptyList(),
                tonalities = Tonality.entries,
                minimum = wideRange.first,
                maximum = wideRange.last
            )
            val result = provider.validate(wide, BundledVoiceDurations)
            assertTrue(
                "all keys must validate for $kind: $result",
                result is ProviderValidation.Valid
            )
            for (keyValue in 0..11) {
                for (tonality in Tonality.entries) {
                    for (degree in 1..7) {
                        assertTrue(
                            "$keyValue/$tonality/$degree must have a completion for $kind",
                            wide.definition.supports(keyValue, tonality, degree)
                        )
                    }
                }
            }
        }
    }

    @Test
    fun everyAnswerFormatReachesAllKeysWithTheBundledCatalog() {
        data class Candidate(val format: DegreeAnswerFormat, val spelling: SpellingPreference)
        val candidates = listOf(
            Candidate(DegreeAnswerFormat.DEGREE, SpellingPreference.SHARP),
            Candidate(DegreeAnswerFormat.NOTE_NAME, SpellingPreference.SHARP),
            Candidate(DegreeAnswerFormat.NOTE_NAME, SpellingPreference.FLAT)
        )
        candidates.forEach { candidate ->
            val wide = config(
                keys = emptyList(),
                tonalities = Tonality.entries,
                answerFormat = candidate.format,
                spelling = candidate.spelling,
                minimum = 48,
                maximum = 84
            )
            val result = provider.validate(wide, BundledVoiceDurations)
            assertTrue(
                "every reachable combination must validate for ${candidate.format}: $result",
                result is ProviderValidation.Valid
            )
            for (keyValue in 0..11) {
                for (tonality in Tonality.entries) {
                    for (degree in 1..7) {
                        assertTrue(
                            "every answer token must exist for " +
                                "${candidate.format}/$keyValue/$tonality/$degree",
                            wide.definition.answerTokensFor(keyValue, tonality, degree)
                                .all { VoiceVocabulary.ofIdOrNull(it) != null }
                        )
                    }
                }
            }
        }

        // The note-name union over all twelve keys is exactly the twelve note clips: no context
        // speech, no octave speech and no double accidentals.
        val allNoteNames = provider.requiredVocabulary(
            config(
                keys = emptyList(),
                tonalities = Tonality.entries,
                answerFormat = DegreeAnswerFormat.NOTE_NAME,
                minimum = 48,
                maximum = 84
            )
        ).map { it.value }.toSet()
        assertEquals(
            setOf(
                "note.c", "note.c_sharp", "note.d", "note.d_sharp", "note.e", "note.f",
                "note.f_sharp", "note.g", "note.g_sharp", "note.a", "note.a_sharp", "note.b"
            ),
            allNoteNames
        )
    }

    @Test
    fun everyKeyCanUseAContextOutsideTheRootRange() {
        val defaultRange = config(
            keys = emptyList(),
            minimum = 60,
            maximum = 72
        )
        for (keyValue in 0..11) {
            assertTrue(
                "key $keyValue can build its context around or outside the root range",
                defaultRange.definition.supports(keyValue, Tonality.MAJOR, 1)
            )
        }
    }

    @Test
    fun seed42DrawsAreStableAndStayInsideThePlayableRange() {
        val first = firstExercises(seed = 42L, count = 8)
        val second = firstExercises(seed = 42L, count = 8)
        assertEquals(first.map { fingerprint(it) }, second.map { fingerprint(it) })
        assertTrue(
            "the recorded seed is the activation seed every child stream derives from",
            first.all { it.provenance.seed == 42L }
        )
        assertTrue(
            "every target note is playable",
            first.all { exercise ->
                val degree = exercise.provenance.generatedScaleDegree!!
                degree.targetMidi in 21..108
            }
        )
    }

    @Test
    fun theMinorCadenceKeepsTheRaisedLeadingToneAndTheNaturalTarget() {
        val minorCadence = config(
            keys = listOf(9),
            tonalities = listOf(Tonality.NATURAL_MINOR),
            context = ContextKind.TONIC_DOMINANT_TONIC,
            minimum = 57,
            maximum = 84
        )
        val exercises = firstExercises(seed = 7L, count = 6, config = minorCadence)
        assertEquals(6, exercises.size)

        val degreeSeven = exercises.first { it.categoryId == "degree.7" }
            .provenance.generatedScaleDegree!!
        assertTrue(
            "the V context carries the raised leading tone",
            degreeSeven.contextValues.any { it.mod(12) == 8 }
        )
        assertEquals(7, degreeSeven.targetMidi.mod(12))
        assertEquals(
            listOf("degree.seven"),
            exercises.first { it.categoryId == "degree.7" }
                .answer.voiceIds.map { it.value }
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
    fun repeatedDrawsUseTheSameContextStreams() {
        val wide = config(
            keys = emptyList(),
            minimum = 48,
            maximum = 84
        )
        val first = firstExercises(seed = 11L, count = 24, config = wide)
            .map { it.provenance.generatedScaleDegree!!.contextValues }
        val second = firstExercises(seed = 11L, count = 24, config = wide)
            .map { it.provenance.generatedScaleDegree!!.contextValues }
        assertEquals(first, second)
        assertTrue("a wide range must offer several constructions", first.distinct().size > 1)
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
        generator.reconfigure(config(degrees = listOf(1, 4)))
        val after = generator.next()
        assertTrue(
            "the edited categories are reachable",
            after.categoryId in setOf("degree.1", "degree.4")
        )
        assertEquals("the sequence index keeps moving", 2, after.sequenceIndex)
        assertEquals("the edited bag restarts at its first slot", 0, after.cycle!!.positionInCycle)
    }

    @Test
    fun contextGroupsPrecedeExactlyOneTargetGroup() {
        val exercise = firstExercises(seed = 42L, count = 3).last()
        val degree = exercise.provenance.generatedScaleDegree!!
        assertEquals(GroupRole.TARGET, exercise.groups.last().role)
        assertTrue(
            "every group before the target is context",
            exercise.groups.dropLast(1).all { it.role == GroupRole.CONTEXT }
        )
        assertEquals(
            degree.contextValues,
            exercise.contextGroups.flatMap { group -> group.notes.map { it.value } }
        )
        assertEquals(
            listOf(degree.targetMidi),
            exercise.targetGroups.single().notes.map { it.value }
        )
    }

    @Test
    fun aTargetOutsideTheRootRangeKeepsTheTheoreticalDegree() {
        // The third default-seed exercise targets C7 over the C4 anchor without changing degree 1.
        val exercise = firstExercises(seed = 42L, count = 3).last()
        val degree = exercise.provenance.generatedScaleDegree!!
        assertEquals(60, degree.anchorMidi)
        assertEquals(96, degree.targetMidi)
        assertEquals(1, degree.degree)
        assertEquals("degree.1", exercise.categoryId)
        assertEquals(
            "the definition enumerates every playable octave of the tonic",
            listOf(24, 36, 48, 60, 72, 84, 96, 108),
            provider.defaults().let { (it as ProviderConfig.ScaleDegrees).definition }
                .targetNotesFor(0, Tonality.MAJOR, 1)
                .map { it.value }
        )
    }

    @Test
    fun everyAnswerFormatSpeaksItsExactTokenSequence() {
        val degree = 5
        val number = config(degrees = listOf(degree))
        val degreeExercise = firstExercises(seed = 1L, count = 1, config = number).single()
        assertEquals(listOf("degree.five"), degreeExercise.answer.voiceIds.map { it.value })
        assertEquals("five", degreeExercise.answer.display.label())

        // F major degree 4 is A#/B flat: the spelling preference picks the chromatic entry.
        val sharpName = config(
            keys = listOf(5),
            degrees = listOf(4),
            answerFormat = DegreeAnswerFormat.NOTE_NAME,
            spelling = SpellingPreference.SHARP
        )
        val sharpExercise = firstExercises(seed = 1L, count = 1, config = sharpName).single()
        assertEquals(listOf("note.a_sharp"), sharpExercise.answer.voiceIds.map { it.value })
        assertEquals("A sharp", sharpExercise.answer.display.label())

        val flatName = config(
            keys = listOf(5),
            degrees = listOf(4),
            answerFormat = DegreeAnswerFormat.NOTE_NAME,
            spelling = SpellingPreference.FLAT
        )
        val flatExercise = firstExercises(seed = 1L, count = 1, config = flatName).single()
        assertEquals(listOf("note.b_flat"), flatExercise.answer.voiceIds.map { it.value })
        assertEquals("B flat", flatExercise.answer.display.label())

        // Display and speech are one projection for every format.
        listOf(
            degreeExercise,
            sharpExercise,
            flatExercise
        ).forEach { exercise ->
            assertEquals(
                exercise.answer.voiceIds.joinToString(" ") {
                    VoiceVocabulary.requireEntry(it).spokenText
                },
                exercise.answer.display.label()
            )
        }
    }

    @Test
    fun theCompilerPlacesTheDefinitionGapBetweenContextAndTarget() {
        val exercise = firstExercises(seed = 42L, count = 1).single()
        val definition =
            (provider.defaults() as ProviderConfig.ScaleDegrees).definition
        assertEquals(500L, definition.contextGap.inWholeMilliseconds)
        val recipe = RecipeDefinition(
            mode = RecipeMode.RECOGNITION,
            timing = RecipeTiming(contextGap = definition.contextGap)
        )
        val compiled = ExerciseCompiler().compile(exercise, recipe, BundledVoiceDurations)
        val notes = compiled.timeline.events.filterIsInstance<NoteEvent>()
        val target = notes.filter {
            it.note.value ==
                exercise.targetGroups.single().notes.single().value
        }
        assertTrue("the target note must be scheduled", target.isNotEmpty())
        assertEquals(
            "one 700 ms context note + one 500 ms gap",
            1200L,
            target.single().startOffset.inWholeMilliseconds
        )
        assertTrue(
            "every context note sounds before the gap",
            notes.filter { it.note.value in listOf(60, 64, 67) }
                .all { it.startOffset.inWholeMilliseconds == 0L }
        )
    }

    @Test
    fun theContentBoundsMeasureTheDefinitionGapAndTheLongestAnswerClip() {
        val cadence = config(
            context = ContextKind.TONIC_DOMINANT_TONIC,
            gap = 750.milliseconds,
            keys = listOf(9),
            tonalities = listOf(Tonality.NATURAL_MINOR),
            minimum = 57,
            maximum = 84
        )
        val catalog = MapVoiceDurationCatalog(
            buildMap {
                VoiceVocabulary.entries.forEach { put(it.id, 100.milliseconds) }
                put(VoiceVocabulary.degreeNumberEntry(4).id, 300.milliseconds)
            }
        )
        val valid = provider.validate(cadence, catalog) as ProviderValidation.Valid
        assertEquals(
            "three cadence chords 700 ms + two 100 ms gaps + 750 ms gap + one target note",
            3750L,
            valid.maximumExampleDuration.inWholeMilliseconds
        )
        assertEquals(
            "the longest reachable answer is the 300 ms degree number",
            300L,
            valid.maximumAnswerDuration.inWholeMilliseconds
        )
    }

    @Test
    fun aForeignConfigurationIsRejectedOnTheKindField() {
        val result = provider.validate(
            ProviderConfig.Notes(NoteDefinition.default()),
            BundledVoiceDurations
        )
        assertTrue(result is ProviderValidation.Invalid)
        assertEquals("kind", (result as ProviderValidation.Invalid).issues.single().field)
    }

    @Test
    fun maximumExampleDurationUsesTheContextDurationPerKind() {
        val default = (provider.defaults() as ProviderConfig.ScaleDegrees).definition
        assertEquals(
            700L,
            ContextBuilder.duration(RecipeTiming(), ContextKind.TONIC_TRIAD).inWholeMilliseconds
        )
        assertEquals(
            "700 + 500 + 700",
            1900L,
            ScaleDegreeExerciseProvider.maximumExampleDuration(default).inWholeMilliseconds
        )
        val cadence = definition(
            keys = listOf(9),
            tonalities = listOf(Tonality.NATURAL_MINOR),
            context = ContextKind.TONIC_DOMINANT_TONIC,
            minimum = 57,
            maximum = 84
        )
        assertEquals(
            "3*700 + 2*100 + 500 + 700",
            3500L,
            ScaleDegreeExerciseProvider.maximumExampleDuration(cadence).inWholeMilliseconds
        )
        val scale = definition(
            context = ContextKind.ASCENDING_SCALE,
            minimum = 48,
            maximum = 84
        )
        assertEquals(
            "8*700 + 7*100 + 500 + 700",
            7500L,
            ScaleDegreeExerciseProvider.maximumExampleDuration(scale).inWholeMilliseconds
        )
    }
}
