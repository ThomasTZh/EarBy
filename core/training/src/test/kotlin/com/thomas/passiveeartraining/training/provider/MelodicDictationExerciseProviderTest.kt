package com.thomas.passiveeartraining.training.provider

import com.thomas.passiveeartraining.music.MidiNote
import com.thomas.passiveeartraining.music.SpellingPreference
import com.thomas.passiveeartraining.music.Tonality
import com.thomas.passiveeartraining.training.BundledVoiceDurations
import com.thomas.passiveeartraining.training.MapVoiceDurationCatalog
import com.thomas.passiveeartraining.training.MelodicAnswerFormat
import com.thomas.passiveeartraining.training.MelodicDictationDefinition
import com.thomas.passiveeartraining.training.MelodicDirectionBias
import com.thomas.passiveeartraining.training.MelodyNode
import com.thomas.passiveeartraining.training.NoteEvent
import com.thomas.passiveeartraining.training.RecognitionRecipe
import com.thomas.passiveeartraining.training.SeededRandomSource
import com.thomas.passiveeartraining.training.VoiceAssetId
import com.thomas.passiveeartraining.training.VoiceEvent
import com.thomas.passiveeartraining.training.context.ContextBuilder
import com.thomas.passiveeartraining.training.context.ContextKind
import com.thomas.passiveeartraining.training.context.TonalContext
import com.thomas.passiveeartraining.training.recipe.DurationFinish
import com.thomas.passiveeartraining.training.recipe.RecipeDefinition
import com.thomas.passiveeartraining.training.recipe.RecipeMode
import com.thomas.passiveeartraining.training.recipe.RecipeTiming
import com.thomas.passiveeartraining.training.recipe.SessionStopPolicy
import kotlin.math.abs
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * M10.1..M10.3/M10-T01..T07: the melodic-dictation provider's finite draws, frozen seed vectors,
 * answer speech, exact reachable duration bound and validation.
 *
 * The frozen vectors were recorded with a temporary printer against the default configuration and
 * a minor directed-interval and a flat descending configuration before the assertions were
 * embedded. The duration oracle enumerates every complete phrase of tiny graphs independently of
 * the graph DP; production-length phrases are never enumerated.
 */
class MelodicDictationExerciseProviderTest {

    private val provider = MelodicDictationExerciseProvider()

    private fun definition(
        keys: List<Int> = listOf(0),
        tonalities: List<Tonality> = listOf(Tonality.MAJOR),
        context: ContextKind = ContextKind.TONIC_TRIAD,
        gap: Duration = 500.milliseconds,
        minimum: Int = 60,
        maximum: Int = 72,
        length: Int = 4,
        start: Int? = 1,
        end: Int? = 1,
        steps: List<Int> = (-5..5).toList(),
        leap: Int = 5,
        bias: MelodicDirectionBias = MelodicDirectionBias.BALANCED,
        answerFormat: MelodicAnswerFormat = MelodicAnswerFormat.DEGREE_SEQUENCE,
        spelling: SpellingPreference = SpellingPreference.SHARP
    ): MelodicDictationDefinition = MelodicDictationDefinition.of(
        keyValues = keys,
        tonalities = tonalities,
        context = context,
        contextGap = gap,
        minimumNote = MidiNote.of(minimum),
        maximumNote = MidiNote.of(maximum),
        phraseLength = length,
        startDegree = start,
        endDegree = end,
        allowedSteps = steps,
        maximumLeap = leap,
        bias = bias,
        answerFormat = answerFormat,
        spelling = spelling
    )

    private fun config(
        keys: List<Int> = listOf(0),
        tonalities: List<Tonality> = listOf(Tonality.MAJOR),
        context: ContextKind = ContextKind.TONIC_TRIAD,
        gap: Duration = 500.milliseconds,
        minimum: Int = 60,
        maximum: Int = 72,
        length: Int = 4,
        start: Int? = 1,
        end: Int? = 1,
        steps: List<Int> = (-5..5).toList(),
        leap: Int = 5,
        bias: MelodicDirectionBias = MelodicDirectionBias.BALANCED,
        answerFormat: MelodicAnswerFormat = MelodicAnswerFormat.DEGREE_SEQUENCE,
        spelling: SpellingPreference = SpellingPreference.SHARP
    ): ProviderConfig.MelodicDictation = ProviderConfig.MelodicDictation(
        definition(
            keys = keys,
            tonalities = tonalities,
            context = context,
            gap = gap,
            minimum = minimum,
            maximum = maximum,
            length = length,
            start = start,
            end = end,
            steps = steps,
            leap = leap,
            bias = bias,
            answerFormat = answerFormat,
            spelling = spelling
        )
    )

    /** C4..C5, four notes, tonic to tonic, steps -5..5 including zero, degree answers. */
    private fun defaultConfig(): ProviderConfig.MelodicDictation =
        provider.defaults() as ProviderConfig.MelodicDictation

    /** M10 frozen vector B: A natural minor, directed intervals, ascending bias, zero allowed. */
    private fun minorDirectedConfig(): ProviderConfig.MelodicDictation = config(
        keys = listOf(9),
        tonalities = listOf(Tonality.NATURAL_MINOR),
        minimum = 57,
        maximum = 69,
        length = 3,
        steps = listOf(-3, -2, 0, 2, 3),
        leap = 3,
        bias = MelodicDirectionBias.ASCENDING,
        answerFormat = MelodicAnswerFormat.DIRECTED_INTERVAL_SEQUENCE,
        spelling = SpellingPreference.SHARP
    )

    /** M10 frozen vector C: B flat major, degree answers, descending bias, flat spelling. */
    private fun flatDescendingConfig(): ProviderConfig.MelodicDictation = config(
        keys = listOf(10),
        tonalities = listOf(Tonality.MAJOR),
        minimum = 58,
        maximum = 74,
        length = 3,
        steps = (-4..4).toList(),
        leap = 4,
        bias = MelodicDirectionBias.DESCENDING,
        answerFormat = MelodicAnswerFormat.DEGREE_SEQUENCE,
        spelling = SpellingPreference.FLAT
    )

    private fun firstExercises(
        seed: Long,
        count: Int,
        config: ProviderConfig = provider.defaults()
    ): List<ConcreteExercise> = provider.createGenerator(config, seed).let { generator ->
        (1..count).map { generator.next() }
    }

    private fun fingerprint(exercise: ConcreteExercise): String {
        val melody = exercise.provenance.generatedMelody!!
        return "${exercise.categoryId}|key=${melody.keyPitchClass}|${melody.tonality}|" +
            "context=${melody.context}|anchor=${melody.anchorMidi}|" +
            "contextValues=${melody.contextNotes.joinToString(",")}|" +
            "notes=${melody.notes.joinToString(",") { "${it.midi}:${it.degree}" }}|" +
            "steps=${melody.steps.joinToString(",")}|bias=${melody.bias}|" +
            "format=${melody.answerFormat}|" +
            "tokens=${exercise.answer.voiceIds.joinToString(",") { it.value }}|" +
            "label=${exercise.answer.display.label()}"
    }

    /** The documented feasible context order: key ascending, tonality ordinal, anchor ascending. */
    private fun feasibleContexts(definition: MelodicDictationDefinition): List<TonalContext> =
        buildList {
            for (keyValue in definition.effectiveKeyValues) {
                for (tonality in definition.tonalities) {
                    if (!definition.graphFor(keyValue, tonality).isFeasible) continue
                    addAll(definition.eligibleConstructions(keyValue, tonality))
                }
            }
        }

    /**
     * Replays the documented draw contract on [targetStream] for one phrase.
     *
     * One start draw over the eligible starts, then one weighted draw over that position's
     * candidates; even a singleton list consumes its `nextInt`.
     */
    private fun expectedPhrase(
        definition: MelodicDictationDefinition,
        construction: TonalContext,
        targetStream: SeededRandomSource
    ): List<Int> {
        val graph = definition.graphFor(construction.keyPitchClass, construction.tonality)
        val starts = graph.eligibleStarts
        var current = starts[targetStream.nextInt(starts.size)]
        val values = mutableListOf(current.value)
        for (position in 1 until definition.phraseLength) {
            val candidates = graph.candidatesForNext(position, current)
            val total = candidates.sumOf { candidate ->
                definition.bias.weightOf(candidate.value - current.value)
            }
            val draw = targetStream.nextInt(total)
            var cumulative = 0
            var chosen: MelodyNode? = null
            for (candidate in candidates) {
                cumulative += definition.bias.weightOf(candidate.value - current.value)
                if (draw < cumulative) {
                    chosen = candidate
                    break
                }
            }
            val next = requireNotNull(chosen) { "The candidate buckets must cover draw $draw" }
            values += next.value
            current = next
        }
        return values
    }

    /** Asserts that every produced phrase follows the two child streams draw for draw. */
    private fun assertDrawContract(
        seed: Long,
        config: ProviderConfig.MelodicDictation,
        count: Int
    ) {
        val definition = config.definition
        val contexts = feasibleContexts(definition)
        val contextStream = SeededRandomSource(
            ModeSeedDerivation.derive(
                seed,
                TrainingKind.MELODIC_DICTATION,
                0L,
                ModeSeedDerivation.CONTEXT
            )
        )
        val targetStream = SeededRandomSource(
            ModeSeedDerivation.derive(
                seed,
                TrainingKind.MELODIC_DICTATION,
                0L,
                ModeSeedDerivation.TARGET
            )
        )
        firstExercises(seed, count, config).forEach { exercise ->
            val melody = exercise.provenance.generatedMelody!!
            val construction = contexts[contextStream.nextInt(contexts.size)]
            assertEquals(construction.keyPitchClass, melody.keyPitchClass)
            assertEquals(construction.tonality, melody.tonality)
            assertEquals(construction.anchor.value, melody.anchorMidi)
            assertEquals(
                "one exercise consumes one context draw and one draw per phrase note",
                expectedPhrase(definition, construction, targetStream),
                melody.notes.map { it.midi }
            )
        }
    }

    /** Enumerates every complete phrase of a tiny graph, independently of the graph DP. */
    private fun completePhrases(
        definition: MelodicDictationDefinition,
        keyValue: Int,
        tonality: Tonality
    ): List<List<MelodyNode>> {
        val nodes = definition.scaleNodes(keyValue, tonality)
        val transitions = definition.effectiveSteps.toSet()
        val phrases = mutableListOf<List<MelodyNode>>()
        fun extend(prefix: List<MelodyNode>) {
            if (prefix.size == definition.phraseLength) {
                if (definition.endDegree == null ||
                    prefix.last().degree == definition.endDegree
                ) {
                    phrases += prefix
                }
                return
            }
            for (next in nodes) {
                if (next.value - prefix.last().value in transitions) {
                    extend(prefix + next)
                }
            }
        }
        for (startNode in nodes) {
            if (definition.startDegree == null || startNode.degree == definition.startDegree) {
                extend(listOf(startNode))
            }
        }
        return phrases
    }

    /** The exhaustive answer duration of a tiny definition, or null when no phrase exists. */
    private fun exhaustiveAnswerDuration(
        definition: MelodicDictationDefinition,
        keyValue: Int,
        tonality: Tonality,
        catalog: MapVoiceDurationCatalog
    ): Duration? {
        val phrases = completePhrases(definition, keyValue, tonality)
        return phrases.maxOfOrNull { phrase ->
            val notes = phrase.map { MelodyNote(midi = it.value, degree = it.degree) }
            val steps = phrase.zipWithNext { from, to -> to.value - from.value }
            val tokens = definition.answerTokensFor(notes, steps)
            tokens.fold(Duration.ZERO) { total, token -> total + catalog.durationOf(token) } +
                ExerciseCompiler.TOKEN_GAP * (tokens.size - 1)
        }
    }

    /** Every generated phrase stays inside its graph, range and endpoints. */
    private fun assertPhraseInvariants(
        config: ProviderConfig.MelodicDictation,
        exercise: ConcreteExercise
    ) {
        val definition = config.definition
        val melody = exercise.provenance.generatedMelody!!
        val graph = definition.graphFor(melody.keyPitchClass, melody.tonality)
        assertEquals(TrainingKind.MELODIC_DICTATION, exercise.kind)
        assertEquals(definition.phraseLength, melody.notes.size)
        assertEquals(definition.phraseLength - 1, melody.steps.size)
        melody.notes.forEachIndexed { index, note ->
            assertTrue("playable range: ${note.midi}", note.midi in 21..108)
            val node = graph.nodes.singleOrNull { it.value == note.midi }
            assertNotNull("every phrase note is a scale node: $note", node)
            assertEquals(node!!.degree, note.degree)
            if (index > 0) {
                assertEquals(
                    "the stored step is the signed difference",
                    melody.steps[index - 1],
                    note.midi - melody.notes[index - 1].midi
                )
            }
        }
        melody.steps.forEach { step ->
            assertTrue("effective step: $step", step in graph.transitions)
            assertTrue("maximum leap: $step", abs(step) <= definition.maximumLeap)
        }
        definition.startDegree?.let { assertEquals(it, melody.notes.first().degree) }
        definition.endDegree?.let { assertEquals(it, melody.notes.last().degree) }
        assertTrue(melody.steps.all { it != 0 || 0 in graph.transitions })
        assertTrue(
            "the start can still finish",
            graph.canFinish(
                definition.phraseLength - 1,
                graph.nodes.single { it.value == melody.notes.first().midi }
            )
        )
        assertEquals(definition.phraseLength, exercise.targetGroups.size)
        exercise.targetGroups.forEachIndexed { index, group ->
            assertEquals(listOf(melody.notes[index].midi), group.notes.map { it.value })
        }
        assertTrue(exercise.contextGroups.isNotEmpty())
        assertEquals(
            melody.contextNotes,
            exercise.contextGroups.flatMap { group ->
                group.notes.map { it.value }
            }
        )
        assertEquals(
            "speech and display are one projection",
            exercise.answer.voiceIds.joinToString(" ") {
                VoiceVocabulary.requireEntry(it).spokenText
            },
            exercise.answer.display.label()
        )
        assertNull("the mode owns no label cycle", exercise.cycle)
    }

    @Test
    fun theProviderIdentityIsMelodicDictationAtVersionOne() {
        assertEquals(TrainingKind.MELODIC_DICTATION, provider.kind)
        assertEquals(1, provider.providerVersion)
        assertEquals(
            ProviderConfig.MelodicDictation(MelodicDictationDefinition.default()),
            provider.defaults()
        )
        val exercise = firstExercises(seed = 1L, count = 1).single()
        assertEquals(TrainingKind.MELODIC_DICTATION, exercise.kind)
        assertEquals("melodic.degree_sequence", exercise.categoryId)
        assertEquals(
            TrainingKind.MELODIC_DICTATION.id,
            exercise.provenance.providerId
        )
        assertEquals(1, exercise.provenance.providerVersion)
        assertEquals(SeededRandomSource.RNG_ID, exercise.provenance.rngId)
        assertEquals(1L, exercise.provenance.seed)
        assertEquals(0, exercise.sequenceIndex)
    }

    @Test
    fun theDefaultConfigurationValidatesWithTheBundledCatalog() {
        val result = provider.validate(provider.defaults(), BundledVoiceDurations)
        assertTrue("the default setup must validate: $result", result is ProviderValidation.Valid)
        val valid = result as ProviderValidation.Valid
        assertEquals(listOf("melodic.degree_sequence"), valid.categories.map { it.id })
        assertEquals(
            (1..7).map { VoiceVocabulary.degreeNumberEntry(it).id.value }.toSet(),
            valid.requiredAssets.map { it.value }.toSet()
        )
        assertEquals(
            "one tonic triad 700 ms + 500 ms context gap + 4 notes and 3 gaps",
            4300L,
            valid.maximumExampleDuration.inWholeMilliseconds
        )
        assertEquals(
            "the exact longest reachable four-degree answer with three 80 ms gaps",
            4366L,
            valid.maximumAnswerDuration.inWholeMilliseconds
        )
    }

    @Test
    fun aMissingAnswerClipIsReportedOnTheAnswersFieldBeforeTheFirstDraw() {
        val withoutFive = MapVoiceDurationCatalog(
            VoiceVocabulary.entries
                .filterNot { it.id == VoiceAssetId("degree.five") }
                .associate { it.id to 500.milliseconds }
        )
        val degreeResult = provider.validate(provider.defaults(), withoutFive)
        assertTrue(degreeResult is ProviderValidation.Invalid)
        val issues = (degreeResult as ProviderValidation.Invalid).issues
        assertEquals(1, issues.size)
        assertEquals("answers", issues.single().field)
        assertTrue(issues.single().message.contains("degree.five"))

        val withoutDescending = MapVoiceDurationCatalog(
            VoiceVocabulary.entries
                .filterNot { it.id == VoiceAssetId("connector.descending") }
                .associate { it.id to 500.milliseconds }
        )
        val intervalResult = provider.validate(minorDirectedConfig(), withoutDescending)
        assertTrue(intervalResult is ProviderValidation.Invalid)
        assertTrue(
            (intervalResult as ProviderValidation.Invalid).issues.single().message
                .contains("connector.descending")
        )
    }

    @Test
    fun impossibleSelectionsReportConcreteFields() {
        val topRange = config(minimum = 105, maximum = 108)
        val topResult = provider.validate(topRange, BundledVoiceDurations)
        assertTrue(topResult is ProviderValidation.Invalid)
        assertTrue(
            (topResult as ProviderValidation.Invalid).issues.all { it.field == "range" }
        )

        val withoutZero = config(length = 2, steps = (-5..5).filter { it != 0 })
        val zeroResult = provider.validate(withoutZero, BundledVoiceDurations)
        assertTrue(zeroResult is ProviderValidation.Invalid)
        assertTrue(
            (zeroResult as ProviderValidation.Invalid).issues.any { it.field == "steps" }
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
    fun seed42MelodicDrawsStayStableAndPlayable() {
        val first = firstExercises(seed = 42L, count = 6).map { fingerprint(it) }
        val second = firstExercises(seed = 42L, count = 6).map { fingerprint(it) }
        assertEquals(first, second)
        assertEquals(6, first.size)
    }

    @Test
    fun theSameSeedRepeatsAndDifferentSeedsDiverge() {
        val first = firstExercises(seed = 21L, count = 5).map { fingerprint(it) }
        val second = firstExercises(seed = 21L, count = 5).map { fingerprint(it) }
        assertEquals(first, second)
        assertNotEquals(first, firstExercises(seed = 22L, count = 5).map { fingerprint(it) })
    }

    @Test
    fun everyDrawFollowsTheContextThenTargetContract() {
        assertDrawContract(seed = 42L, config = defaultConfig(), count = 5)
        assertDrawContract(seed = 7L, config = minorDirectedConfig(), count = 5)
        assertDrawContract(seed = 5L, config = flatDescendingConfig(), count = 5)
    }

    @Test
    fun aSingletonCandidateListStillConsumesItsDocumentedDraw() {
        val singleton = config(
            minimum = 60,
            maximum = 67,
            length = 3,
            start = 1,
            end = null,
            steps = listOf(2),
            leap = 2
        )
        val graph = singleton.definition.graphFor(0, Tonality.MAJOR)
        assertTrue("C4 is an eligible start", graph.eligibleStarts.any { it.value == 60 })
        assertEquals(
            listOf(62),
            graph.candidatesForNext(1, graph.eligibleStarts.first { it.value == 60 })
                .map { it.value }
        )
        assertEquals(
            listOf(64),
            graph.candidatesForNext(2, graph.nodes.single { it.value == 62 })
                .map { it.value }
        )
        assertDrawContract(seed = 11L, config = singleton, count = 4)
        val targetStream = SeededRandomSource(
            ModeSeedDerivation.derive(
                11L,
                TrainingKind.MELODIC_DICTATION,
                0L,
                ModeSeedDerivation.TARGET
            )
        )
        repeat(4) {
            assertEquals("the start draw", 0, targetStream.nextInt(1))
            assertEquals("the first singleton draw", 0, targetStream.nextInt(1))
            assertEquals("the second singleton draw", 0, targetStream.nextInt(1))
        }
        val phrases = firstExercises(11L, 4, singleton).map { exercise ->
            exercise.provenance.generatedMelody!!
        }
        assertTrue(
            "every phrase follows the +2 +2 shape from a tonic node",
            phrases.all { melody ->
                melody.notes.size == 3 &&
                    melody.notes.first().degree == 1 &&
                    melody.steps == listOf(2, 2) &&
                    melody.notes.all { it.midi in 21..108 }
            }
        )
    }

    @Test
    fun answersAndDisplaySpeakBothFormats() {
        listOf(
            MelodicAnswerFormat.DEGREE_SEQUENCE,
            MelodicAnswerFormat.DIRECTED_INTERVAL_SEQUENCE
        ).forEach { format ->
            val exercise = firstExercises(
                seed = 42L,
                count = 1,
                config = config(answerFormat = format)
            ).single()
            val melody = exercise.provenance.generatedMelody!!
            assertEquals(
                "display and speech are one projection for $format",
                exercise.answer.voiceIds.joinToString(" ") {
                    VoiceVocabulary.requireEntry(it).spokenText
                },
                exercise.answer.display.label()
            )
            when (format) {
                MelodicAnswerFormat.DEGREE_SEQUENCE -> {
                    assertEquals(melody.notes.size, exercise.answer.voiceIds.size)
                    assertTrue(
                        "a degree answer adds no octave number",
                        exercise.answer.voiceIds.none { it.value.contains("octave") }
                    )
                }

                MelodicAnswerFormat.DIRECTED_INTERVAL_SEQUENCE -> {
                    val expectedClips = melody.steps.sumOf { step -> if (step == 0) 1 else 2 }
                    assertEquals(expectedClips, exercise.answer.voiceIds.size)
                }
            }
        }
    }

    @Test
    fun aSixteenNoteDegreePhraseSpeaksOneClipPerNote() {
        val zeroOnly = config(
            length = 16,
            start = 1,
            end = 1,
            steps = listOf(0),
            leap = 0
        )
        val valid = provider.validate(zeroOnly, BundledVoiceDurations)
        assertTrue(valid is ProviderValidation.Valid)
        val exercise = firstExercises(seed = 3L, count = 1, config = zeroOnly).single()
        val melody = exercise.provenance.generatedMelody!!
        assertEquals(16, melody.notes.size)
        assertEquals(15, melody.steps.size)
        assertTrue(melody.steps.all { it == 0 })
        assertEquals(List(16) { "degree.one" }, exercise.answer.voiceIds.map { it.value })
        assertEquals(
            BundledVoiceDurations.durationOf(VoiceAssetId("degree.one")) * 16 +
                ExerciseCompiler.TOKEN_GAP * 15,
            (valid as ProviderValidation.Valid).maximumAnswerDuration
        )
        assertEquals(14590L, valid.maximumAnswerDuration.inWholeMilliseconds)
        assertEquals(13900L, valid.maximumExampleDuration.inWholeMilliseconds)
        assertEquals(
            "one degree number per note, sixteen notes long",
            16,
            exercise.answer.display.label().split(" ").size
        )
    }

    @Test
    fun aSixteenNoteIntervalPhraseSpeaksFifteenUnisonClips() {
        val zeroOnly = config(
            length = 16,
            start = 1,
            end = 1,
            steps = listOf(0),
            leap = 0,
            answerFormat = MelodicAnswerFormat.DIRECTED_INTERVAL_SEQUENCE
        )
        val valid = provider.validate(zeroOnly, BundledVoiceDurations)
        assertTrue(valid is ProviderValidation.Valid)
        val exercise = firstExercises(seed = 3L, count = 1, config = zeroOnly).single()
        assertEquals(
            "fifteen transitions, each the unison phrase alone",
            List(15) { "interval.unison" },
            exercise.answer.voiceIds.map { it.value }
        )
        assertTrue(exercise.answer.voiceIds.none { it.value.startsWith("connector.") })
        assertEquals(
            BundledVoiceDurations.durationOf(VoiceAssetId("interval.unison")) * 15 +
                ExerciseCompiler.TOKEN_GAP * 14,
            (valid as ProviderValidation.Valid).maximumAnswerDuration
        )
        assertEquals(23745L, valid.maximumAnswerDuration.inWholeMilliseconds)
        assertEquals(
            "fifteen spoken unison words",
            List(15) { "Unison" }.joinToString(" "),
            exercise.answer.display.label()
        )
    }

    @Test
    fun aSixteenNotePhraseCanUseTheLongestReachableClipEveryTime() {
        val longest = config(
            length = 16,
            start = 6,
            end = 6,
            steps = listOf(0),
            leap = 0
        )
        val valid = provider.validate(longest, BundledVoiceDurations) as ProviderValidation.Valid
        assertEquals(
            setOf("degree.six"),
            valid.requiredAssets.map { it.value }.toSet()
        )
        val expected = BundledVoiceDurations.durationOf(VoiceAssetId("degree.six")) * 16 +
            ExerciseCompiler.TOKEN_GAP * 15
        assertEquals(expected, valid.maximumAnswerDuration)
        assertEquals(20820L, valid.maximumAnswerDuration.inWholeMilliseconds)
    }

    @Test
    fun theExactAnswerBoundMatchesExhaustiveEnumerationOnTinyGraphs() {
        val catalog = MapVoiceDurationCatalog(
            VoiceVocabulary.entries.associate { entry ->
                entry.id to (100 + (entry.id.value.length % 5) * 40).milliseconds
            }
        )
        val tinyDefinitions = MelodicAnswerFormat.entries.flatMap { format ->
            listOf(
                definition(
                    minimum = 60,
                    maximum = 64,
                    length = 2,
                    start = 1,
                    end = 1,
                    steps = listOf(0, 2, -2),
                    leap = 2,
                    answerFormat = format
                ),
                definition(
                    minimum = 60,
                    maximum = 64,
                    length = 3,
                    start = 1,
                    end = null,
                    steps = (-2..2).toList(),
                    leap = 2,
                    answerFormat = format
                ),
                definition(
                    minimum = 60,
                    maximum = 64,
                    length = 4,
                    start = null,
                    end = 1,
                    steps = listOf(-2, -1, 0, 1, 2),
                    leap = 2,
                    answerFormat = format
                ),
                definition(
                    keys = listOf(9),
                    tonalities = listOf(Tonality.NATURAL_MINOR),
                    minimum = 57,
                    maximum = 64,
                    length = 3,
                    start = 1,
                    end = 1,
                    steps = listOf(-2, 0, 2),
                    leap = 2,
                    answerFormat = format
                )
            )
        }
        tinyDefinitions.forEach { definition ->
            val expected = definition.effectiveKeyValues.flatMap { keyValue ->
                definition.tonalities.mapNotNull { tonality ->
                    exhaustiveAnswerDuration(definition, keyValue, tonality, catalog)
                }
            }.maxOrNull()
            assertEquals(
                "the DP must equal the exhaustive maximum for $definition",
                expected ?: Duration.ZERO,
                MelodicDictationExerciseProvider.maximumAnswerDuration(definition, catalog)
            )
        }
    }

    @Test
    fun theSixtySecondCapIsJudgedAtBelowAtAndAbove() {
        val long = definition(
            minimum = 48,
            maximum = 84,
            length = 16,
            start = 1,
            end = 1,
            steps = (-7..7).toList(),
            leap = 7,
            context = ContextKind.ASCENDING_SCALE
        )
        val withoutThinking = MelodicDictationExerciseProvider.maximumExerciseDuration(
            long,
            RecipeDefinition(
                mode = RecipeMode.RECOGNITION,
                timing = RecipeTiming(thinkingSilence = Duration.ZERO)
            ),
            BundledVoiceDurations
        )
        assertTrue(
            "the longest setup stays below the cap before thinking time",
            withoutThinking < RecognitionRecipe.EXERCISE_CAP
        )
        val gapToCap = RecognitionRecipe.EXERCISE_CAP - withoutThinking
        val atCap = RecipeDefinition(
            mode = RecipeMode.RECOGNITION,
            timing = RecipeTiming(thinkingSilence = gapToCap)
        )
        assertEquals(
            "exactly sixty seconds is still valid",
            RecognitionRecipe.EXERCISE_CAP,
            MelodicDictationExerciseProvider.maximumExerciseDuration(
                long,
                atCap,
                BundledVoiceDurations
            )
        )
        val aboveCap = atCap.copy(
            timing = atCap.timing.copy(
                thinkingSilence =
                gapToCap + 1.milliseconds
            )
        )
        assertTrue(
            "one millisecond above the cap is invalid",
            MelodicDictationExerciseProvider.maximumExerciseDuration(
                long,
                aboveCap,
                BundledVoiceDurations
            ) > RecognitionRecipe.EXERCISE_CAP
        )
        val defaultRecipe = RecipeDefinition(mode = RecipeMode.RECOGNITION)
        assertTrue(
            "the default four-note setup is well below the cap",
            MelodicDictationExerciseProvider.maximumExerciseDuration(
                defaultConfig().definition,
                defaultRecipe,
                BundledVoiceDurations
            ) < RecognitionRecipe.EXERCISE_CAP
        )
        val withReplays = defaultRecipe.copy(replayCount = RecipeDefinition.MAX_REPLAY)
        assertTrue(
            "the maximum replay count pushes the longest setup above the cap",
            MelodicDictationExerciseProvider.maximumExerciseDuration(
                long,
                withReplays,
                BundledVoiceDurations
            ) > RecognitionRecipe.EXERCISE_CAP
        )
    }

    @Test
    fun everyRecipeModeMeasuresTheBoundThroughTheSharedCalculation() {
        val definition = defaultConfig().definition
        val timing = RecipeTiming()
        val example = MelodicDictationExerciseProvider.maximumExampleDuration(definition, timing)
        val answer = MelodicDictationExerciseProvider.maximumAnswerDuration(
            definition,
            BundledVoiceDurations
        )
        val totals = RecipeMode.entries.associateWith { mode ->
            val recipe = RecipeDefinition(mode = mode, timing = timing)
            MelodicDictationExerciseProvider.maximumExerciseDuration(
                definition,
                recipe,
                BundledVoiceDurations
            )
        }
        assertEquals(
            totals.getValue(RecipeMode.RECOGNITION),
            example + timing.thinkingSilence + answer + timing.postAnswerSilence
        )
        assertEquals(
            totals.getValue(RecipeMode.EXPOSURE),
            answer + timing.answerGap + example + timing.postAnswerSilence
        )
        assertEquals(
            "Association keeps its second full example",
            totals.getValue(RecipeMode.RECOGNITION) + example + timing.answerGap,
            totals.getValue(RecipeMode.ASSOCIATION)
        )
    }

    @Test
    fun cycleStopPoliciesAreRejectedAndOtherPoliciesStayAvailable() {
        val afterCycle = MelodicDictationExerciseProvider.stopPolicyIssue(
            SessionStopPolicy.AfterCycle
        )
        assertNotNull(afterCycle)
        assertEquals("stopPolicy", afterCycle!!.field)
        assertTrue(afterCycle.message, afterCycle.message.contains("no label cycle"))

        val finishCycle = MelodicDictationExerciseProvider.stopPolicyIssue(
            SessionStopPolicy.Duration(10.seconds, DurationFinish.FINISH_CYCLE)
        )
        assertNotNull(finishCycle)
        assertEquals("stopPolicy", finishCycle!!.field)
        assertTrue(finishCycle.message, finishCycle.message.contains("cycle"))

        assertNull(
            MelodicDictationExerciseProvider.stopPolicyIssue(SessionStopPolicy.UntilStopped)
        )
        assertNull(
            MelodicDictationExerciseProvider.stopPolicyIssue(
                SessionStopPolicy.CompletedItems(5)
            )
        )
        assertNull(
            MelodicDictationExerciseProvider.stopPolicyIssue(
                SessionStopPolicy.Duration(10.seconds, DurationFinish.IMMEDIATE)
            )
        )
        assertNull(
            MelodicDictationExerciseProvider.stopPolicyIssue(
                SessionStopPolicy.Duration(10.seconds, DurationFinish.FINISH_CURRENT_ITEM)
            )
        )
    }

    @Test
    fun requiredVocabularyFollowsTheAnswerFormat() {
        assertEquals(
            "the default degree answer reaches every degree 1..7",
            (1..7).map { VoiceVocabulary.degreeNumberEntry(it).id.value }.toSet(),
            provider.requiredVocabulary(provider.defaults()).map { it.value }.toSet()
        )
        assertEquals(
            setOf(
                "connector.ascending",
                "connector.descending",
                "interval.major_second",
                "interval.minor_third",
                "interval.unison"
            ),
            provider.requiredVocabulary(minorDirectedConfig()).map { it.value }.toSet()
        )
        MelodicAnswerFormat.entries.forEach { format ->
            provider.requiredVocabulary(config(answerFormat = format)).forEach { token ->
                assertNotNull(
                    "the vocabulary must declare $token",
                    VoiceVocabulary.ofIdOrNull(token)
                )
            }
        }
    }

    @Test
    fun theContextRepeatsInEveryExampleAndTargetsAreOneNoteGroups() {
        val exercise = firstExercises(seed = 42L, count = 1).single()
        val definition = defaultConfig().definition
        val melody = exercise.provenance.generatedMelody!!
        val recipe = RecipeDefinition(mode = RecipeMode.RECOGNITION)
        val compiled = ExerciseCompiler().compile(exercise, recipe, BundledVoiceDurations)
        val contextEnd = ContextBuilder.duration(recipe.timing, definition.context)
        val targetEvents = compiled.timeline.events
            .filterIsInstance<NoteEvent>()
            .filter { it.startOffset > Duration.ZERO }
        assertEquals(melody.notes.size, targetEvents.size)
        targetEvents.sortedBy { it.startOffset }
            .forEachIndexed { index, event ->
                assertEquals(
                    "the phrase stride is note duration plus melodic gap",
                    contextEnd + definition.contextGap +
                        (recipe.timing.noteDuration + recipe.timing.melodicGap) * index,
                    event.startOffset
                )
                assertEquals(melody.notes[index].midi, event.note.value)
            }
        assertEquals(
            "the default C4 tonic triad is one context group",
            1,
            exercise.contextGroups.size
        )
        val contextValues = exercise.contextGroups.flatMap { group -> group.notes.map { it.value } }
        assertTrue(contextValues.isNotEmpty())
        assertTrue(contextValues.all { it in 21..108 })
        val voiceEvents = compiled.timeline.events
            .filterIsInstance<VoiceEvent>()
            .sortedBy { it.startOffset }
        assertEquals(
            "the answer clips sound in token order",
            exercise.answer.voiceIds,
            voiceEvents.map { it.voiceId }
        )
        assertEquals(compiled.answerStart, voiceEvents.first().startOffset)
        voiceEvents.forEachIndexed { index, event ->
            if (index > 0) {
                val previous = voiceEvents[index - 1]
                assertEquals(
                    "exactly one 80 ms gap between adjacent clips",
                    previous.startOffset + previous.duration + ExerciseCompiler.TOKEN_GAP,
                    event.startOffset
                )
            }
        }
    }

    @Test
    fun reconfigureContinuesBothStreamsAndKeepsTheSequenceIndexMonotonic() {
        val seed = 21L
        val generator = provider.createGenerator(defaultConfig(), seed)
        generator.next()
        generator.next()
        val contextStream = SeededRandomSource(
            ModeSeedDerivation.derive(
                seed,
                TrainingKind.MELODIC_DICTATION,
                0L,
                ModeSeedDerivation.CONTEXT
            )
        )
        val targetStream = SeededRandomSource(
            ModeSeedDerivation.derive(
                seed,
                TrainingKind.MELODIC_DICTATION,
                0L,
                ModeSeedDerivation.TARGET
            )
        )
        val firstConfig = defaultConfig()
        val firstContexts = feasibleContexts(firstConfig.definition)
        repeat(2) {
            val construction = firstContexts[contextStream.nextInt(firstContexts.size)]
            expectedPhrase(firstConfig.definition, construction, targetStream)
        }
        val secondConfig = minorDirectedConfig()
        val secondContexts = feasibleContexts(secondConfig.definition)
        generator.reconfigure(secondConfig)
        val afterEdit = generator.next()
        val afterEditMelody = requireNotNull(afterEdit.provenance.generatedMelody)
        val expectedConstruction = secondContexts[contextStream.nextInt(secondContexts.size)]
        assertEquals(expectedConstruction.anchor.value, afterEditMelody.anchorMidi)
        assertEquals(
            "the edited combination continues the same target stream",
            expectedPhrase(secondConfig.definition, expectedConstruction, targetStream),
            afterEditMelody.notes.map { it.midi }
        )
        assertEquals("the sequence index keeps moving", 2, afterEdit.sequenceIndex)
        assertNull(afterEdit.cycle)

        val uninterrupted = firstExercises(seed = 7L, count = 4, config = secondConfig)
        val noOp = provider.createGenerator(secondConfig, 7L)
        noOp.next()
        noOp.next()
        noOp.reconfigure(secondConfig)
        assertEquals(
            uninterrupted.drop(2).map { fingerprint(it) },
            listOf(noOp.next(), noOp.next()).map { fingerprint(it) }
        )
    }

    @Test
    fun tenThousandPhrasesStayInsideTheirGraphs() {
        val corpus = listOf(
            defaultConfig(),
            minorDirectedConfig(),
            flatDescendingConfig(),
            config(
                keys = emptyList(),
                tonalities = listOf(Tonality.MAJOR, Tonality.NATURAL_MINOR),
                context = ContextKind.TONIC_NOTE,
                minimum = 48,
                maximum = 84,
                length = 4,
                start = null,
                end = null,
                steps = (-12..12).toList(),
                leap = 12
            ),
            config(
                minimum = 60,
                maximum = 67,
                length = 2,
                start = 1,
                end = 1,
                steps = listOf(0, 2, -2),
                leap = 2,
                answerFormat = MelodicAnswerFormat.DIRECTED_INTERVAL_SEQUENCE
            )
        )
        corpus.forEach { config ->
            val validation = provider.validate(config, BundledVoiceDurations)
            assertTrue(
                "corpus config must validate: $validation",
                validation is ProviderValidation.Valid
            )
        }
        val startedAt = System.nanoTime()
        var phrases = 0
        corpus.forEachIndexed { index, config ->
            val generator = provider.createGenerator(config, 1_000L + index)
            repeat(2_000) {
                assertPhraseInvariants(config, generator.next())
                phrases += 1
            }
        }
        assertEquals(10_000, phrases)
        val elapsedMs = (System.nanoTime() - startedAt) / 1_000_000L
        assertTrue(
            "10,000 phrases must generate in bounded time but took $elapsedMs ms",
            elapsedMs < 60_000L
        )
    }

    @Test
    fun zeroLeapPhrasesRepeatOnePitchAndSignedStepsFilterTheGraph() {
        val repeated = config(
            length = 2,
            start = 1,
            end = 1,
            steps = listOf(0),
            leap = 0
        )
        assertTrue(provider.validate(repeated, BundledVoiceDurations) is ProviderValidation.Valid)
        val exercise = firstExercises(seed = 4L, count = 1, config = repeated).single()
        val melody = exercise.provenance.generatedMelody!!
        assertEquals(melody.notes.first().midi, melody.notes.last().midi)
        assertEquals(listOf(0), melody.steps)
        assertTrue(
            "a zero-leap phrase is played as two equal notes",
            exercise.targetGroups.all {
                it.notes.size == 1 &&
                    it.notes.single().value == melody.notes.first().midi
            }
        )

        val wrongEnd = config(length = 2, start = 1, end = 2, steps = listOf(0), leap = 0)
        assertTrue(
            "zero alone cannot change the endpoint degree",
            provider.validate(wrongEnd, BundledVoiceDurations) is ProviderValidation.Invalid
        )
    }
}
