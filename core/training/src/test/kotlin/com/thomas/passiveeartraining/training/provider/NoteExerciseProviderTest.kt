package com.thomas.passiveeartraining.training.provider

import com.thomas.passiveeartraining.music.MidiNote
import com.thomas.passiveeartraining.music.PitchClass
import com.thomas.passiveeartraining.music.SpellingPreference
import com.thomas.passiveeartraining.training.BundledVoiceDurations
import com.thomas.passiveeartraining.training.MapVoiceDurationCatalog
import com.thomas.passiveeartraining.training.NoteDefinition
import com.thomas.passiveeartraining.training.VoiceAssetId
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * M7.1/M7-T01..T05: the note provider's finite draws, spelling projections and vocabulary coverage.
 *
 * The frozen vectors were recorded with a temporary printer against the default configuration and
 * seed 42 before this assertion was embedded.
 */
class NoteExerciseProviderTest {

    private val provider = NoteExerciseProvider()

    private fun definition(
        pitchClasses: List<Int> = (0..11).toList(),
        accidental: SpellingPreference = SpellingPreference.SHARP,
        speakOctave: Boolean = false,
        minimumNote: MidiNote = MidiNote.of(60),
        maximumNote: MidiNote = MidiNote.of(72)
    ): NoteDefinition = NoteDefinition.of(
        pitchClassValues = pitchClasses,
        accidental = accidental,
        speakOctave = speakOctave,
        minimumNote = minimumNote,
        maximumNote = maximumNote
    )

    private fun firstExercises(
        seed: Long,
        count: Int,
        config: ProviderConfig = provider.defaults()
    ) = provider.createGenerator(config, seed).let { generator ->
        (1..count).map { generator.next() }
    }

    /** Every vocabulary token at a fixed duration; overrides make the longest clip exact. */
    private fun tokenCatalog(
        overrides: Map<String, Duration> = emptyMap()
    ): MapVoiceDurationCatalog = MapVoiceDurationCatalog(
        buildMap {
            VoiceVocabulary.entries.forEach { put(it.id, 100.milliseconds) }
            overrides.forEach { (id, duration) -> put(VoiceAssetId(id), duration) }
        }
    )

    @Test
    fun theProviderIdentityIsTheNoteIdentificationKindAtVersionOne() {
        assertEquals(TrainingKind.NOTE_IDENTIFICATION, provider.kind)
        assertEquals(1, provider.providerVersion)
        assertEquals(ProviderConfig.Notes(NoteDefinition.default()), provider.defaults())
        val exercise = firstExercises(seed = 1L, count = 1).single()
        assertEquals(TrainingKind.NOTE_IDENTIFICATION, exercise.kind)
        val note = exercise.provenance.generatedNote!!
        assertEquals(definition().nameToken(note.pitchClass).value, exercise.categoryId)
        assertEquals(1, exercise.targetGroups.size)
        assertEquals(note.midiNote, exercise.targetGroups.single().notes.single())
        assertEquals(SpellingPreference.SHARP, note.accidental)
        assertEquals(false, note.octaveSpoken)
    }

    @Test
    fun theDefaultConfigurationValidatesWithTheBundledCatalog() {
        val result = provider.validate(provider.defaults(), BundledVoiceDurations)
        assertTrue("default note setup must validate: $result", result is ProviderValidation.Valid)
        val valid = result as ProviderValidation.Valid
        val expectedCategories = listOf(
            "note.c",
            "note.c_sharp",
            "note.d",
            "note.d_sharp",
            "note.e",
            "note.f",
            "note.f_sharp",
            "note.g",
            "note.g_sharp",
            "note.a",
            "note.a_sharp",
            "note.b"
        )
        assertEquals(expectedCategories, valid.categories.map { it.id })
        assertEquals(expectedCategories.toSet(), valid.requiredAssets.map { it.value }.toSet())
        assertEquals(
            "one 700 ms note is the whole example",
            700L,
            valid.maximumExampleDuration.inWholeMilliseconds
        )
        assertEquals(
            "with octave speech off the answer is exactly the longest name clip",
            valid.requiredAssets.maxOf { BundledVoiceDurations.durationOf(it) },
            valid.maximumAnswerDuration
        )
    }

    @Test
    fun aMissingClipIsReportedOnTheAnswersFieldBeforeTheFirstDraw() {
        val partial = MapVoiceDurationCatalog.of("note.c" to 500.milliseconds)
        val result = provider.validate(provider.defaults(), partial)
        assertTrue(result is ProviderValidation.Invalid)
        val issues = (result as ProviderValidation.Invalid).issues
        assertTrue(
            "every issue is an answer-clip issue: $issues",
            issues.all {
                it.field ==
                    "answers"
            }
        )
        assertTrue(
            "the missing sharp name must be named: $issues",
            issues.any {
                it.message.contains("note.c_sharp")
            }
        )
        assertTrue(issues.all { it.message.contains("is missing from the voice pack") })

        // Octave speech adds every octave clip to the requirement.
        val speaking = ProviderConfig.Notes(definition(speakOctave = true))
        val namesOnly = MapVoiceDurationCatalog.of(
            *definition().selectedPitchClasses
                .map { definition().nameToken(it).value to 500.milliseconds }
                .toTypedArray()
        )
        val speakingResult = provider.validate(speaking, namesOnly)
        assertTrue(speakingResult is ProviderValidation.Invalid)
        assertTrue(
            (speakingResult as ProviderValidation.Invalid).issues.any {
                it.field == "answers" && it.message.contains("note.octave.4")
            }
        )
    }

    @Test
    fun aClassWithNoFeasibleNoteIsExcludedFromPlayableCategories() {
        val config = ProviderConfig.Notes(
            definition(
                pitchClasses = listOf(0, 1),
                minimumNote = MidiNote.of(61),
                maximumNote = MidiNote.of(62)
            )
        )
        val result = provider.validate(config, BundledVoiceDurations)
        assertTrue(result is ProviderValidation.Valid)
        val valid = result as ProviderValidation.Valid
        assertEquals(listOf("note.c_sharp"), valid.categories.map { it.id })
        assertTrue(config.definition.notesFor(PitchClass.of(0)).isEmpty())
        assertTrue(config.definition.notesFor(PitchClass.of(1)).isNotEmpty())
    }

    @Test
    fun aSelectionWithNoPlayableClassIsRejectedOnTheRangeField() {
        val config = ProviderConfig.Notes(
            definition(
                pitchClasses = listOf(0),
                minimumNote = MidiNote.of(61),
                maximumNote = MidiNote.of(62)
            )
        )
        val result = provider.validate(config, BundledVoiceDurations)
        assertTrue(result is ProviderValidation.Invalid)
        assertTrue(
            (result as ProviderValidation.Invalid).issues.any {
                it.field == "range" && it.message.contains("No selected note class")
            }
        )
    }

    @Test
    fun requiredVocabularyCoversEveryClassAndEveryOctaveToken() {
        val defaultTokens = provider.requiredVocabulary(provider.defaults()).map { it.value }
        assertEquals(12, defaultTokens.size)
        assertEquals("note.c_sharp", defaultTokens[1])
        assertTrue(defaultTokens.none { it.startsWith("note.octave.") })

        val speaking = provider.requiredVocabulary(
            ProviderConfig.Notes(definition(speakOctave = true))
        ).map { it.value }
        assertEquals(23, speaking.size)
        assertEquals(
            (-1..9).map { if (it < 0) "note.octave.minus_1" else "note.octave.$it" },
            speaking.filter { it.startsWith("note.octave.") }
        )

        val flat = provider.requiredVocabulary(
            ProviderConfig.Notes(definition(accidental = SpellingPreference.FLAT))
        ).map { it.value }
        assertTrue(flat.contains("note.d_flat"))
        assertTrue(flat.none { it == "note.c_sharp" })
    }

    @Test
    fun bagCoverageIsIndependentOfTheNumberOfFeasibleOctaves() {
        val narrowDefinition = definition(
            pitchClasses = listOf(0, 1),
            minimumNote = MidiNote.of(60),
            maximumNote = MidiNote.of(72)
        )
        val wideDefinition = definition(
            pitchClasses = listOf(0, 1),
            minimumNote = MidiNote.of(48),
            maximumNote = MidiNote.of(84)
        )
        assertEquals(
            "C sharp has exactly one note in C4..C5",
            listOf(61),
            narrowDefinition.notesFor(PitchClass.of(1)).map { it.value }
        )
        assertEquals(
            "C has two octaves in C4..C5",
            listOf(60, 72),
            narrowDefinition.notesFor(PitchClass.of(0)).map { it.value }
        )
        assertEquals(4, wideDefinition.notesFor(PitchClass.of(0)).size)
        assertEquals(3, wideDefinition.notesFor(PitchClass.of(1)).size)

        val narrow = firstExercises(7L, 6, ProviderConfig.Notes(narrowDefinition))
        val wide = firstExercises(7L, 6, ProviderConfig.Notes(wideDefinition))
        listOf(narrow, wide).forEach { exercises ->
            assertEquals(
                "every class fills exactly one bag slot per cycle",
                listOf(0, 1, 0, 1, 0, 1),
                exercises.map { it.cycle!!.positionInCycle }
            )
            assertEquals(
                "three cycles weigh both classes equally",
                mapOf("note.c" to 3, "note.c_sharp" to 3),
                exercises.groupingBy { it.categoryId }.eachCount()
            )
        }
        assertTrue(
            "the narrow range never widens",
            narrow.all { it.provenance.generatedNote!!.midiNote.value in 60..72 }
        )
    }

    @Test
    fun theSameSeedRepeatsTheSequenceAndDifferentSeedsDiverge() {
        fun fingerprint(seed: Long) = firstExercises(seed, 8).map { exercise ->
            val note = exercise.provenance.generatedNote!!
            "${exercise.categoryId}|${note.midiNote.value}|" +
                exercise.answer.voiceIds.joinToString(",") { it.value }
        }
        assertEquals(fingerprint(11L), fingerprint(11L))
        assertNotEquals(fingerprint(11L), fingerprint(12L))
    }

    @Test
    fun frozenDrawVectorsForSeed42StayStable() {
        val expected = listOf(
            "note.c|60|note.c",
            "note.c_sharp|61|note.c_sharp",
            "note.f_sharp|66|note.f_sharp",
            "note.g|67|note.g",
            "note.d_sharp|63|note.d_sharp",
            "note.f|65|note.f",
            "note.a_sharp|70|note.a_sharp",
            "note.b|71|note.b"
        )
        val actual = firstExercises(seed = 42L, count = 8).map { exercise ->
            val note = exercise.provenance.generatedNote!!
            "${exercise.categoryId}|${note.midiNote.value}|" +
                exercise.answer.voiceIds.joinToString(",") { it.value }
        }
        assertEquals(expected, actual)
    }

    @Test
    fun octaveSpeechControlsTheTokensAndTheDisplayTogether() {
        val silent = firstExercises(
            seed = 1L,
            count = 1,
            config = ProviderConfig.Notes(definition(pitchClasses = listOf(0)))
        ).single()
        assertEquals(listOf("note.c"), silent.answer.voiceIds.map { it.value })
        assertEquals("C", silent.answer.display.label())
        assertTrue(silent.answer.display is AnswerDisplay.Note)
        assertEquals(false, (silent.answer.display as AnswerDisplay.Note).octaveSpoken)

        val speaking = firstExercises(
            seed = 1L,
            count = 1,
            config = ProviderConfig.Notes(
                definition(pitchClasses = listOf(0), speakOctave = true)
            )
        ).single()
        val drawn = speaking.provenance.generatedNote!!.midiNote
        assertEquals(
            listOf("note.c", "note.octave.${drawn.octave}"),
            speaking.answer.voiceIds.map { it.value }
        )
        assertEquals(
            "the display words equal the spoken token words",
            speaking.answer.voiceIds.joinToString(" ") {
                VoiceVocabulary.requireEntry(it).spokenText
            },
            speaking.answer.display.label()
        )
    }

    @Test
    fun reconfigureContinuesTheSameStreamWithoutReplayingTheOldBag() {
        val generator = provider.createGenerator(provider.defaults(), 21L)
        generator.next()
        generator.next()
        generator.reconfigure(
            ProviderConfig.Notes(definition(pitchClasses = listOf(0, 4, 7)))
        )
        val after = generator.next()
        assertTrue(
            "the edited category is reachable",
            after.categoryId in setOf("note.c", "note.e", "note.g")
        )
        assertEquals("the sequence index keeps moving", 2, after.sequenceIndex)
    }

    @Test
    fun theContentBoundsCountOneNoteAndTheLongestReachableAnswer() {
        val catalog = tokenCatalog(
            overrides = mapOf(
                "note.c_sharp" to 400.milliseconds,
                "note.octave.9" to 250.milliseconds
            )
        )
        val silent = provider.validate(provider.defaults(), catalog) as ProviderValidation.Valid
        assertEquals(700L, silent.maximumExampleDuration.inWholeMilliseconds)
        assertEquals(
            "the longest single name clip bounds a silent answer",
            400L,
            silent.maximumAnswerDuration.inWholeMilliseconds
        )

        val speaking = provider.validate(
            ProviderConfig.Notes(
                definition(
                    speakOctave = true,
                    minimumNote = MidiNote.of(0),
                    maximumNote = MidiNote.of(127)
                )
            ),
            catalog
        ) as ProviderValidation.Valid
        assertEquals(
            "name 400 ms + 80 ms gap + octave 250 ms",
            730L,
            speaking.maximumAnswerDuration.inWholeMilliseconds
        )
    }

    @Test
    fun everyMidiNoteResolvesToBundledNameAndOctaveTokens() {
        for (value in 0..127) {
            val note = MidiNote.of(value)
            for (accidental in SpellingPreference.entries) {
                for (speakOctave in listOf(false, true)) {
                    val definition = NoteDefinition.of(
                        pitchClassValues = listOf(note.pitchClass.value),
                        accidental = accidental,
                        speakOctave = speakOctave,
                        minimumNote = note,
                        maximumNote = note
                    )
                    val tokens = definition.answerTokensFor(note)
                    assertEquals(
                        "$note $accidental octave=$speakOctave token count",
                        if (speakOctave) 2 else 1,
                        tokens.size
                    )
                    tokens.forEach { token ->
                        assertTrue(
                            "missing vocabulary entry for $token",
                            VoiceVocabulary.ofIdOrNull(token) != null
                        )
                        // A missing bundled clip would throw MissingVoiceDurationException.
                        BundledVoiceDurations.durationOf(token)
                    }
                }
            }
        }
    }
}
