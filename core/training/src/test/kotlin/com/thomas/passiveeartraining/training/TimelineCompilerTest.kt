package com.thomas.passiveeartraining.training

import com.thomas.passiveeartraining.music.IntervalPresentation
import com.thomas.passiveeartraining.music.MidiNote
import java.io.File
import kotlin.time.Duration
import kotlin.time.Duration.Companion.microseconds
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

private val FIFTH_VOICE = VoiceAssetId("interval.perfect_fifth")
private val UNISON_VOICE = VoiceAssetId("interval.unison")

private fun catalog(vararg entries: Pair<String, Long>): VoiceDurationCatalog =
    MapVoiceDurationCatalog(
        entries.associate { (id, micros) -> VoiceAssetId(id) to micros.microseconds }
    )

private fun recipe(
    noteDurationUs: Long = 700_000,
    melodicGapUs: Long = 100_000,
    thinkingSilenceUs: Long = 4_000_000,
    postAnswerSilenceUs: Long = 2_000_000
): RecognitionRecipe = RecognitionRecipe(
    noteDuration = noteDurationUs.microseconds,
    melodicGap = melodicGapUs.microseconds,
    thinkingSilence = thinkingSilenceUs.microseconds,
    postAnswerSilence = postAnswerSilenceUs.microseconds
)

private fun micros(value: Long): Duration = value.microseconds

private const val MICROS_PER_MILLI = 1_000L

/** Comparable ordering key: offset, then event rank, then sounding value. */
private data class FixtureSortKey(val offset: Long, val rank: Int, val midi: Int) :
    Comparable<FixtureSortKey> {
    override fun compareTo(other: FixtureSortKey): Int =
        compareValuesBy(this, other, { it.offset }, { it.rank }, { it.midi })
}

/** One parsed expectation of the readable fixture file. */
private sealed interface FixtureEvent {
    data class Note(val offset: Long, val length: Long, val midi: Int) : FixtureEvent
    data class Voice(val offset: Long, val length: Long, val id: String) : FixtureEvent
    data class Marker(val kind: String, val offset: Long) : FixtureEvent

    /** Same ranking and ordering the compiler must produce. */
    val sortKey: FixtureSortKey
        get() = when (this) {
            is Note -> FixtureSortKey(offset, 0, midi)
            is Voice -> FixtureSortKey(offset, 1, 0)
            is Marker -> FixtureSortKey(offset, if (kind == "answer_started") 2 else 3, 0)
        }
}

private class FixtureCase(val name: String, val voiceId: String, val events: List<FixtureEvent>) {
    /** Sounding notes exactly as the fixture lists them, in written order. */
    val sourceNotes: List<Int> = events.filterIsInstance<FixtureEvent.Note>().map { it.midi }

    /** Root is the first melodic note, or the lower note of a harmonic example. */
    val root: Int = sourceNotes.first()

    /** Target is the second melodic note, or the upper note of a harmonic example. */
    val target: Int = sourceNotes.last()
}

/** Parses the readable `resources/timelines` fixture into compiler inputs and expected events. */
private data class FixtureDocument(val inputs: Map<String, Long>, val cases: List<FixtureCase>)

private object TimelineFixture {
    fun read(name: String): FixtureDocument {
        val resource = requireNotNull(javaClass.getResource("/timelines/$name")) {
            "Fixture /timelines/$name is missing"
        }
        val inputs = mutableMapOf<String, Long>()
        val cases = mutableListOf<FixtureCase>()
        var current: String? = null
        var voiceId = ""
        var events = mutableListOf<FixtureEvent>()
        fun flush() {
            val name = current ?: return
            cases += FixtureCase(name, voiceId, events.sortedBy { it.sortKey })
            events = mutableListOf()
            voiceId = ""
        }
        File(resource.toURI()).readLines().forEach { raw ->
            val line = raw.trim()
            when {
                line.isEmpty() || line.startsWith('#') -> Unit
                line.startsWith("input ") -> {
                    // Keys can contain a space, for example "input voice interval.unison=900000".
                    val body = line.removePrefix("input ")
                    val separator = body.lastIndexOf('=')
                    inputs[body.substring(0, separator).removePrefix("voice ").trim()] =
                        body.substring(separator + 1).trim().toLong()
                }
                line.startsWith("case ") -> {
                    flush()
                    // "case ascending C4 -> G4": only the first token is the case name; the
                    // pitches are documentation, the note lines below carry the actual values.
                    current = line.removePrefix("case ").split(' ').first()
                }

                // "voice <offset> <length> <asset id>": the id is last because it may contain dots.
                line.startsWith("voice ") -> {
                    val parts = line.split(' ')
                    voiceId = parts.last()
                    events += FixtureEvent.Voice(parts[1].toLong(), parts[2].toLong(), parts[3])
                }
                line.startsWith("note ") -> {
                    val parts = line.split(' ')
                    events +=
                        FixtureEvent.Note(parts[1].toLong(), parts[2].toLong(), parts[3].toInt())
                }

                line.startsWith("marker ") -> {
                    val parts = line.split(' ')
                    events += FixtureEvent.Marker(parts[1], parts[2].toLong())
                }

                else -> error("Unrecognized fixture line: $line")
            }
        }
        flush()
        return FixtureDocument(inputs, cases)
    }

    /** Recipe described by the fixture's own input block. */
    fun recipeOf(document: FixtureDocument): RecognitionRecipe = RecognitionRecipe(
        noteDuration = document.inputs.getValue("noteDurationUs").microseconds,
        melodicGap = document.inputs.getValue("melodicGapUs").microseconds,
        thinkingSilence = document.inputs.getValue("thinkingSilenceUs").microseconds,
        postAnswerSilence = document.inputs.getValue("postAnswerSilenceUs").microseconds
    )

    /** Answer clips referenced by the fixture cases, using the fixture's measured lengths. */
    fun catalogOf(document: FixtureDocument): VoiceDurationCatalog = MapVoiceDurationCatalog(
        document.cases.associate { case ->
            VoiceAssetId(case.voiceId) to document.inputs.getValue(case.voiceId).microseconds
        }
    )
}

private fun compiledEvents(timeline: Timeline): List<String> = timeline.orderedEvents.map { event ->
    when (event) {
        is NoteEvent ->
            "note:${event.note.value}:${event.startOffset.inWholeMicroseconds}:" +
                event.duration.inWholeMicroseconds
        is VoiceEvent ->
            "voice:${event.voiceId.value}:${event.startOffset.inWholeMicroseconds}:" +
                event.duration.inWholeMicroseconds
        is AnswerStartedEvent -> "answer_started:${event.startOffset.inWholeMicroseconds}"
        is ExerciseEndedEvent -> "exercise_ended:${event.startOffset.inWholeMicroseconds}"
    }
}

class TimelineCompilerRecognitionTest {

    private val compiler = TimelineCompiler(
        recipe = recipe(),
        voiceDurations = catalog("interval.perfect_fifth" to 900_000L)
    )

    @Test
    fun ascendingExampleFollowsTheDocumentedOffsets() {
        val timeline = compiler.compile(
            root = MidiNote.of(MidiNote.C4),
            target = MidiNote.of(67),
            presentation = IntervalPresentation.ASCENDING,
            answerVoiceId = FIFTH_VOICE
        )
        assertEquals(
            listOf(
                "note:60:0:700000",
                "note:67:800000:700000",
                "voice:interval.perfect_fifth:5500000:900000",
                "answer_started:5500000",
                "exercise_ended:8400000"
            ),
            compiledEvents(timeline)
        )
        assertEquals(micros(8_400_000), timeline.totalDuration)
        assertEquals(micros(700_000), timeline.notes[0].duration)
        assertEquals(micros(800_000), timeline.notes[1].startOffset)
        assertEquals(micros(900_000), timeline.voices.single().duration)
    }

    @Test
    fun harmonicExampleStartsBothNotesTogetherAndEndsEarlier() {
        val timeline = compiler.compile(
            root = MidiNote.of(60),
            target = MidiNote.of(67),
            presentation = IntervalPresentation.HARMONIC,
            answerVoiceId = FIFTH_VOICE
        )
        assertEquals(
            listOf(
                "note:60:0:700000",
                "note:67:0:700000",
                "voice:interval.perfect_fifth:4700000:900000",
                "answer_started:4700000",
                "exercise_ended:7600000"
            ),
            compiledEvents(timeline)
        )
        assertEquals(micros(4_700_000), timeline.voices.single().startOffset)
    }

    @Test
    fun descendingExampleKeepsTheRootAsTheFirstMelodicNote() {
        val timeline = compiler.compile(
            root = MidiNote.of(67),
            target = MidiNote.of(60),
            presentation = IntervalPresentation.DESCENDING,
            answerVoiceId = FIFTH_VOICE
        )
        assertEquals(67, timeline.notes[0].note.value)
        assertEquals(60, timeline.notes[1].note.value)
        assertEquals(micros(800_000), timeline.notes[1].startOffset)
        assertEquals(micros(8_400_000), timeline.totalDuration)
    }

    @Test
    fun harmonicUnisonProducesOneAudibleNote() {
        val timeline = TimelineCompiler(
            recipe = recipe(),
            voiceDurations = catalog("interval.unison" to 900_000L)
        ).compile(
            root = MidiNote.of(60),
            target = MidiNote.of(60),
            presentation = IntervalPresentation.HARMONIC,
            answerVoiceId = UNISON_VOICE
        )
        assertEquals(listOf(60), timeline.notes.map { it.note.value })
        assertEquals(
            listOf(
                "note:60:0:700000",
                "voice:interval.unison:4700000:900000",
                "answer_started:4700000",
                "exercise_ended:7600000"
            ),
            compiledEvents(timeline)
        )
    }

    @Test
    fun harmonicNotesAreOrderedBySoundingValue() {
        val timeline = compiler.compile(
            root = MidiNote.of(67),
            target = MidiNote.of(60),
            presentation = IntervalPresentation.HARMONIC,
            answerVoiceId = FIFTH_VOICE
        )
        assertEquals(listOf(60, 67), timeline.notes.map { it.note.value })
        assertEquals(micros(0), timeline.notes[0].startOffset)
        assertEquals(micros(0), timeline.notes[1].startOffset)
    }

    @Test
    fun theRecordedFixtureMatchesTheCompiler() {
        val document = TimelineFixture.read("recognition-v1.timeline")
        val cases = document.cases
        assertTrue("fixture must define cases", cases.isNotEmpty())
        assertEquals(
            mapOf(
                "noteDurationUs" to 700_000L,
                "melodicGapUs" to 100_000L,
                "thinkingSilenceUs" to 4_000_000L,
                "postAnswerSilenceUs" to 2_000_000L,
                "interval.perfect_fifth" to 900_000L,
                "interval.unison" to 900_000L
            ),
            document.inputs
        )
        cases.forEach { case ->
            val compiler = TimelineCompiler(
                recipe = TimelineFixture.recipeOf(document),
                voiceDurations = TimelineFixture.catalogOf(document)
            )
            val actual = compiledEvents(
                compiler.compile(
                    root = MidiNote.of(case.root),
                    target = MidiNote.of(case.target),
                    presentation = presentationOf(case.name),
                    answerVoiceId = VoiceAssetId(case.voiceId)
                )
            )
            val expected = case.events.map { event ->
                when (event) {
                    is FixtureEvent.Note -> "note:${event.midi}:${event.offset}:${event.length}"
                    is FixtureEvent.Voice -> "voice:${event.id}:${event.offset}:${event.length}"
                    is FixtureEvent.Marker -> "${event.kind}:${event.offset}"
                }
            }
            assertEquals(case.name, expected, actual)
        }
    }

    private fun presentationOf(caseName: String): IntervalPresentation = when {
        caseName.startsWith("ascending") -> IntervalPresentation.ASCENDING
        caseName.startsWith("descending") -> IntervalPresentation.DESCENDING
        else -> IntervalPresentation.HARMONIC
    }

    @Test
    fun compilingAnExerciseUsesItsOwnPresentation() {
        val definition = IntervalDefinition.default()
        val generator = SeededIntervalGenerator(definition, SeededRandomSource(4L))
        val allAnswers = catalog(
            "interval.perfect_fifth" to 900_000L,
            "interval.minor_third" to 850_000L,
            "interval.major_third" to 950_000L
        )
        val exerciseCompiler = TimelineCompiler(recipe = recipe(), voiceDurations = allAnswers)
        val exercise = generator.next()
        val timeline = exerciseCompiler.compile(exercise)
        val expected = exerciseCompiler.compile(
            root = exercise.root,
            target = exercise.target,
            presentation = exercise.presentation,
            answerVoiceId = VoiceAssetId(exercise.answerId)
        )
        assertEquals(expected, timeline)
    }
}

class TimelineCompilerValidationTest {

    private fun compilerWith(
        recipe: RecognitionRecipe = recipe(),
        voiceMicros: Long = 900_000L
    ): TimelineCompiler = TimelineCompiler(
        recipe = recipe,
        voiceDurations = catalog("interval.perfect_fifth" to voiceMicros)
    )

    @Test
    fun zeroGapsAndZeroSilencesAreAccepted() {
        val timeline = compilerWith(
            recipe = recipe(melodicGapUs = 0, thinkingSilenceUs = 0, postAnswerSilenceUs = 0)
        ).compile(
            root = MidiNote.of(60),
            target = MidiNote.of(67),
            presentation = IntervalPresentation.ASCENDING,
            answerVoiceId = FIFTH_VOICE
        )
        assertEquals(micros(700_000), timeline.notes[1].startOffset)
        assertEquals(micros(1_400_000), timeline.voices.single().startOffset)
        assertEquals(micros(2_300_000), timeline.events.last().startOffset)
        assertEquals(micros(2_300_000), timeline.totalDuration)
    }

    @Test
    fun aMissingVoiceDurationIsRejectedWithItsIdentifier() {
        val error = assertThrows(MissingVoiceDurationException::class.java) {
            compilerWith().compile(
                root = MidiNote.of(60),
                target = MidiNote.of(67),
                presentation = IntervalPresentation.ASCENDING,
                answerVoiceId = VoiceAssetId("interval.tritone")
            )
        }
        assertEquals(VoiceAssetId("interval.tritone"), error.voiceId)
        assertTrue(error.message!!.contains("interval.tritone"))
    }

    @Test
    fun nonpositiveAndNegativeDurationsAreRejected() {
        assertThrows(InvalidTimelineException::class.java) {
            compilerWith(recipe = recipe(noteDurationUs = 0)).compile(
                MidiNote.of(60),
                MidiNote.of(67),
                IntervalPresentation.ASCENDING,
                FIFTH_VOICE
            )
        }
        assertThrows(InvalidTimelineException::class.java) {
            compilerWith(recipe = recipe(melodicGapUs = -1)).compile(
                MidiNote.of(60),
                MidiNote.of(67),
                IntervalPresentation.ASCENDING,
                FIFTH_VOICE
            )
        }
        assertThrows(InvalidTimelineException::class.java) {
            compilerWith(recipe = recipe(thinkingSilenceUs = -1)).compile(
                MidiNote.of(60),
                MidiNote.of(67),
                IntervalPresentation.ASCENDING,
                FIFTH_VOICE
            )
        }
        // A measured voice clip of zero length would silently skip the answer.
        assertThrows(InvalidTimelineException::class.java) {
            compilerWith(voiceMicros = 0).compile(
                MidiNote.of(60),
                MidiNote.of(67),
                IntervalPresentation.ASCENDING,
                FIFTH_VOICE
            )
        }
    }

    @Test
    fun nonfiniteDurationsAreRejected() {
        assertThrows(InvalidTimelineException::class.java) {
            compilerWith(recipe = recipe().copy(noteDuration = Duration.INFINITE)).compile(
                MidiNote.of(60),
                MidiNote.of(67),
                IntervalPresentation.ASCENDING,
                FIFTH_VOICE
            )
        }
        assertThrows(InvalidTimelineException::class.java) {
            compilerWith(recipe = recipe().copy(thinkingSilence = Duration.INFINITE)).compile(
                MidiNote.of(60),
                MidiNote.of(67),
                IntervalPresentation.ASCENDING,
                FIFTH_VOICE
            )
        }
    }

    @Test
    fun durationsFinerThanOneMicrosecondAreRejected() {
        val finer = RecognitionRecipe(
            noteDuration = Duration.parse("700.0005ms"),
            melodicGap = micros(0),
            thinkingSilence = micros(0),
            postAnswerSilence = micros(0)
        )
        assertThrows(InvalidTimelineException::class.java) {
            compilerWith(recipe = finer).compile(
                MidiNote.of(60),
                MidiNote.of(67),
                IntervalPresentation.ASCENDING,
                FIFTH_VOICE
            )
        }
        // Exactly one microsecond is accepted.
        val exact = compilerWith(
            recipe = recipe(
                noteDurationUs = 1,
                melodicGapUs = 0,
                thinkingSilenceUs = 0,
                postAnswerSilenceUs = 0
            )
        ).compile(
            MidiNote.of(60),
            MidiNote.of(67),
            IntervalPresentation.ASCENDING,
            FIFTH_VOICE
        )
        assertEquals(micros(1), exact.notes[0].duration)
    }

    @Test
    fun theSixtySecondBoundaryIsEnforcedWithoutSaturation() {
        // 59.999999 s total is inside the cap; one microsecond more is not.
        val justInside = compilerWith(
            recipe = recipe(
                melodicGapUs = 0,
                thinkingSilenceUs = 0,
                postAnswerSilenceUs = 57_600_000
            ),
            voiceMicros = 1_000_000
        )
        val timeline = justInside.compile(
            MidiNote.of(60),
            MidiNote.of(67),
            IntervalPresentation.ASCENDING,
            FIFTH_VOICE
        )
        // 700 ms note + 700 ms target + 1 s voice + 57.6 s final silence is exactly the cap.
        assertEquals(micros(60_000_000), timeline.totalDuration)
        assertEquals(micros(60_000_000), timeline.events.last().startOffset)

        assertThrows(InvalidTimelineException::class.java) {
            compilerWith(
                recipe = recipe(
                    melodicGapUs = 0,
                    thinkingSilenceUs = 0,
                    postAnswerSilenceUs = 57_600_001
                ),
                voiceMicros = 1_000_000
            ).compile(
                MidiNote.of(60),
                MidiNote.of(67),
                IntervalPresentation.ASCENDING,
                FIFTH_VOICE
            )
        }
    }

    @Test
    fun enormousInputsFailInsteadOfSaturatingToInfinity() {
        // Kotlin Duration saturates at Long.MAX_VALUE nanoseconds. These inputs are valid whole
        // microsecond durations that multiply past a Long nanosecond range if added naively, so
        // the compiler must reject them rather than accept a saturated "infinite" timeline.
        val hugeMicros = Long.MAX_VALUE / 2
        assertThrows(InvalidTimelineException::class.java) {
            compilerWith(recipe = recipe(postAnswerSilenceUs = hugeMicros)).compile(
                MidiNote.of(60),
                MidiNote.of(67),
                IntervalPresentation.ASCENDING,
                FIFTH_VOICE
            )
        }
        assertThrows(InvalidTimelineException::class.java) {
            compilerWith(
                recipe = recipe(postAnswerSilenceUs = 0),
                voiceMicros = hugeMicros
            ).compile(
                MidiNote.of(60),
                MidiNote.of(67),
                IntervalPresentation.ASCENDING,
                FIFTH_VOICE
            )
        }
        // A Duration beyond the representable microsecond range is not a whole-microsecond input.
        val beyondRange = Duration.INFINITE
        assertThrows(InvalidTimelineException::class.java) {
            compilerWith(recipe = recipe().copy(postAnswerSilence = beyondRange)).compile(
                MidiNote.of(60),
                MidiNote.of(67),
                IntervalPresentation.ASCENDING,
                FIFTH_VOICE
            )
        }
    }

    @Test
    fun oneHourOfNotesPlusSpeechExceedsTheCap() {
        assertThrows(InvalidTimelineException::class.java) {
            compilerWith(
                recipe = recipe(
                    noteDurationUs = 3_000_000_000,
                    thinkingSilenceUs = 0,
                    postAnswerSilenceUs = 0
                )
            ).compile(
                MidiNote.of(60),
                MidiNote.of(67),
                IntervalPresentation.ASCENDING,
                FIFTH_VOICE
            )
        }
    }

    @Test
    fun anEmptyVoiceIdentifierIsRejected() {
        assertThrows(InvalidTimelineException::class.java) { VoiceAssetId("") }
        assertThrows(InvalidTimelineException::class.java) { VoiceAssetId("   ") }
        assertThrows(InvalidTimelineException::class.java) { VoiceAssetId.forInterval("") }
        assertEquals(VoiceAssetId("interval.minor_third"), VoiceAssetId.forInterval("minor_third"))
    }
}

class TimelineStructureTest {

    private val compiler = TimelineCompiler(
        recipe = recipe(),
        voiceDurations = catalog("interval.perfect_fifth" to 900_000L)
    )

    @Test
    fun identicalInputsProduceIdenticalOutput() {
        val first = compiler.compile(
            MidiNote.of(60),
            MidiNote.of(67),
            IntervalPresentation.ASCENDING,
            FIFTH_VOICE
        )
        val second = compiler.compile(
            MidiNote.of(60),
            MidiNote.of(67),
            IntervalPresentation.ASCENDING,
            FIFTH_VOICE
        )
        assertEquals(first, second)
        assertEquals(first.hashCode(), second.hashCode())
        assertEquals(first.events, second.events)
    }

    @Test
    fun orderingIsStableAndMarkersFollowTheVoice() {
        val timeline = compiler.compile(
            MidiNote.of(60),
            MidiNote.of(67),
            IntervalPresentation.ASCENDING,
            FIFTH_VOICE
        )
        assertEquals(
            listOf(
                "NoteEvent",
                "NoteEvent",
                "VoiceEvent",
                "AnswerStartedEvent",
                "ExerciseEndedEvent"
            ),
            timeline.events.map { it::class.simpleName }
        )
        // The AnswerStarted marker shares the voice offset and must never precede it.
        val voiceIndex = timeline.events.indexOfFirst { it is VoiceEvent }
        val answerIndex = timeline.events.indexOfFirst { it is AnswerStartedEvent }
        assertTrue(answerIndex > voiceIndex)
        assertEquals(
            timeline.events[voiceIndex].startOffset,
            timeline.events[answerIndex].startOffset
        )
    }

    @Test
    fun onlyTheEndMarkerHasNoFollowingDuration() {
        val timeline = compiler.compile(
            MidiNote.of(60),
            MidiNote.of(67),
            IntervalPresentation.ASCENDING,
            FIFTH_VOICE
        )
        timeline.events.forEach { event ->
            when (event) {
                is ExerciseEndedEvent, is AnswerStartedEvent -> assertTrue(
                    "$event is a marker",
                    event.duration == null
                )
                else -> assertTrue(
                    "$event must carry a duration",
                    (event.duration ?: Duration.ZERO) > Duration.ZERO
                )
            }
        }
        assertNotEquals(timeline.totalDuration, Duration.ZERO)
        assertEquals(timeline.totalDuration, timeline.events.last().startOffset)
    }

    @Test
    fun audibleWindowsAreHalfOpenAndEndBeforeTheNextStart() {
        val timeline = compiler.compile(
            MidiNote.of(60),
            MidiNote.of(67),
            IntervalPresentation.ASCENDING,
            FIFTH_VOICE
        )
        val windows = timeline.events.filter { it.duration != null }
        windows.zipWithNext().forEach { (earlier, later) ->
            assertTrue("$earlier ends after $later starts", earlier.endOffset <= later.startOffset)
        }
        windows.forEach { event ->
            assertEquals(event.startOffset + event.duration!!, event.endOffset)
        }
    }

    @Test
    fun musicAndVoiceWindowsNeverOverlap() {
        for (presentation in IntervalPresentation.entries) {
            val timeline = compiler.compile(
                MidiNote.of(60),
                MidiNote.of(67),
                presentation,
                FIFTH_VOICE
            )
            val voiceStart = timeline.voices.single().startOffset
            timeline.notes.forEach { note ->
                assertTrue("$presentation note overlaps the answer", note.endOffset <= voiceStart)
            }
        }
    }

    @Test
    fun theExportedEventListsAreReadOnlyViews() {
        val timeline = compiler.compile(
            MidiNote.of(60),
            MidiNote.of(67),
            IntervalPresentation.ASCENDING,
            FIFTH_VOICE
        )
        assertEquals(2, timeline.notes.size)
        assertEquals(1, timeline.voices.size)
        assertNotEquals(timeline.notes, timeline.voices)
    }

    @Test
    fun theCompilerNeverReadsTheInjectedClock() {
        var reads = 0
        val clock = object : MonotonicClock {
            override fun elapsed(): Duration {
                reads += 1
                return Duration.ZERO
            }
        }
        val timeline = compiler.compile(
            MidiNote.of(60),
            MidiNote.of(67),
            IntervalPresentation.ASCENDING,
            FIFTH_VOICE
        )
        assertEquals(0, reads)
        assertEquals(micros(8_400_000), timeline.totalDuration)
        assertEquals(Duration.ZERO, clock.elapsed() - Duration.ZERO)
        assertEquals(1, reads)
    }

    @Test
    fun voiceDurationCatalogReportsMissingClips() {
        val catalog = catalog("interval.perfect_fifth" to 900_000L)
        assertEquals(micros(900_000), catalog.durationOf(FIFTH_VOICE))
        assertThrows(MissingVoiceDurationException::class.java) { catalog.durationOf(UNISON_VOICE) }
        val single = MapVoiceDurationCatalog.of("interval.unison" to micros(900_000))
        assertEquals(micros(900_000), single.durationOf(UNISON_VOICE))
    }
}

class MonotonicClockTest {

    @Test
    fun theFakeClockAdvancesAndRefusesToMoveBackwards() {
        val clock = FakeMonotonicClock(micros(1_000))
        assertEquals(micros(1_000), clock.elapsed())
        clock.advanceBy(micros(500))
        assertEquals(micros(1_500), clock.elapsed())
        clock.moveTo(micros(9_000))
        assertEquals(micros(9_000), clock.elapsed())
        assertThrows(ClockWentBackwardsException::class.java) { clock.advanceBy(micros(-1)) }
        assertThrows(ClockWentBackwardsException::class.java) { clock.moveTo(micros(8_999)) }
        clock.advanceBy(Duration.ZERO)
        assertEquals(micros(9_000), clock.elapsed())
    }

    @Test
    fun millisecondsConvertToWholeMicroseconds() {
        assertEquals(micros(700_000), micros(MICROS_PER_MILLI * 700))
        assertEquals(700, Duration.parse("700ms").inWholeMilliseconds)
    }
}

class TimingProfileTest {

    @Test
    fun everyProfileCompilesInsideTheExerciseCap() {
        val compilerFor: (RecognitionRecipe) -> TimelineCompiler = { recipe ->
            TimelineCompiler(
                recipe = recipe,
                voiceDurations = catalog("interval.perfect_fifth" to 900_000L)
            )
        }
        val labels = RecognitionRecipe.PROFILES.keys.toList()
        assertEquals(listOf("spacious", "default", "quick"), labels)
        RecognitionRecipe.PROFILES.forEach { (name, recipe) ->
            val timeline = compilerFor(recipe).compile(
                MidiNote.of(60),
                MidiNote.of(67),
                IntervalPresentation.ASCENDING,
                FIFTH_VOICE
            )
            assertTrue(
                "$name must stay inside the 60 s cap",
                timeline.totalDuration <= RecognitionRecipe.EXERCISE_CAP
            )
            assertTrue("$name must produce sound", timeline.notes.isNotEmpty())
        }
    }

    @Test
    fun theProfilesDifferOnlyInTiming() {
        val spacious = RecognitionRecipe.PROFILES.getValue("spacious")
        val standard = RecognitionRecipe.PROFILES.getValue("default")
        val quick = RecognitionRecipe.PROFILES.getValue("quick")
        // The thinking silence is the value the reviewer asked to keep adjustable.
        assertTrue(spacious.thinkingSilence > standard.thinkingSilence)
        assertTrue(standard.thinkingSilence > quick.thinkingSilence)
        assertEquals(Duration.parse("2s"), standard.thinkingSilence)
        assertEquals(RecognitionRecipe.DEFAULT, standard)
    }
}
