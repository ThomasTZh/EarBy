package com.thomas.passiveeartraining.training

import com.thomas.passiveeartraining.music.IntervalPresentation
import com.thomas.passiveeartraining.music.InvalidMidiNoteException
import com.thomas.passiveeartraining.music.Key
import com.thomas.passiveeartraining.music.Letter
import com.thomas.passiveeartraining.music.MidiNote
import com.thomas.passiveeartraining.music.PitchClass
import com.thomas.passiveeartraining.music.SimpleInterval
import com.thomas.passiveeartraining.music.SpelledPitch
import com.thomas.passiveeartraining.music.SpellingPreference
import com.thomas.passiveeartraining.music.Tonality
import com.thomas.passiveeartraining.training.context.ContextBuilder
import com.thomas.passiveeartraining.training.context.ContextKind
import com.thomas.passiveeartraining.training.context.TonalContext
import com.thomas.passiveeartraining.training.provider.VoiceVocabulary
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/** The answer the contextual-interval mode asks the listener to produce (M9B.1). */
enum class ContextualIntervalAnswerFormat {
    /** The interval phrase alone, for example "minor third". */
    INTERVAL,

    /** The two degree numbers in presented order, for example "one five". */
    DEGREE_PAIR,

    /** The interval phrase followed by both degree numbers, for example "minor third one five". */
    INTERVAL_AND_DEGREES
}

/**
 * One finite contextual-interval target pair (M9B.1).
 *
 * The pair is musical data drawn inside one selected key and tonality: [first] and [second] are the
 * two target MIDI notes in *presented* order. ASCENDING stores the lower note first, DESCENDING the
 * higher note first, and HARMONIC the low-to-high order. Unison is the one case where both entries
 * are the same sounding pitch. The degrees are theoretical scale degrees 1..7 relative to the key
 * tonic; they are derived from the notes' scale membership and never from the semitone distance.
 */
data class ContextualIntervalPair(
    /** The simple interval the pair realizes; its absolute semitone distance is the pair's. */
    val interval: SimpleInterval,
    /** The presentation the pair was enumerated for. */
    val presentation: IntervalPresentation,
    /** The first target note in presented order. */
    val first: MidiNote,
    /** The second target note in presented order. */
    val second: MidiNote,
    /** The theoretical degree 1..7 of [first] relative to the key tonic. */
    val firstDegree: Int,
    /** The theoretical degree 1..7 of [second] relative to the key tonic. */
    val secondDegree: Int
)

/**
 * Immutable, normalized contextual-interval configuration (M9B.1/M9B.2).
 *
 * The selection is a set of key pitch classes crossed with tonalities and simple interval ids. An
 * empty key selection means all twelve key tonics, exactly like the scale setup and the scale-degree
 * definition. [presentation] is a single choice, because one activation plays one layout: ascending,
 * descending or harmonic.
 *
 * Target candidates are the scale notes of the selected key/tonality inside [noteRange]; pairs are
 * the ordered target-note pairs whose absolute semitone distance equals the selected interval. The
 * key's theoretical scale always follows the selected tonality: the natural-minor target pool never
 * receives the raised leading tone of a minor dominant context, and a semitone distance never
 * implies a degree distance.
 *
 * [spelling] selects the sharp or flat reading of the key tonic and its diatonic letters, exactly as
 * in the scale-degree definition. It never changes the sounding classes, so it can never change
 * which pairs exist.
 *
 * Every feasibility question is answered against [noteRange]: a context construction is eligible
 * only when all its sounding notes fit the range, and a pair is eligible only when both target
 * notes fit the range and its semitone distance matches. A selected interval with no eligible
 * context or pair is reported by the provider against `range`, never silently removed here.
 *
 * @see ContextBuilder
 */
class ContextualIntervalDefinition private constructor(
    selectedKeyValues: List<Int>,
    selectedTonalities: List<Tonality>,
    selectedIntervalIds: List<String>,
    val presentation: IntervalPresentation,
    val context: ContextKind,
    val contextGap: Duration,
    val answerFormat: ContextualIntervalAnswerFormat,
    val spelling: SpellingPreference,
    val minimumNote: MidiNote,
    val maximumNote: MidiNote
) {
    /**
     * Selected key tonic pitch classes in numeric order, possibly empty.
     *
     * An empty list means all twelve key tonics; [effectiveKeyValues] resolves that reading.
     */
    val keyValues: List<Int> = java.util.Collections.unmodifiableList(selectedKeyValues.toList())

    /** Selected tonalities in ordinal order; never empty. */
    val tonalities: List<Tonality> =
        java.util.Collections.unmodifiableList(selectedTonalities.toList())

    /** Selected interval ids in canonical semitone order; never empty. */
    val intervalIds: List<String> =
        java.util.Collections.unmodifiableList(selectedIntervalIds.toList())

    /** The key tonics this definition actually enumerates: [keyValues], or all twelve when empty. */
    val effectiveKeyValues: List<Int>
        get() = if (keyValues.isEmpty()) ALL_KEY_VALUES else keyValues

    /** The selected intervals as parsed catalog values, in canonical semitone order. */
    val intervals: List<SimpleInterval> get() = intervalIds.map { SimpleInterval.ofId(it) }

    val minimumNoteValue: Int = minimumNote.value
    val maximumNoteValue: Int = maximumNote.value

    /** Inclusive sounding range every context note and both target notes must fit. */
    val noteRange: IntRange = minimumNoteValue..maximumNoteValue

    /**
     * The spelled tonic of key pitch class [keyValue] under this definition's [spelling].
     *
     * The sharp table spells 1/3/6/8/10 as C#/D#/F#/G#/A# and the flat table as Db/Eb/Gb/Ab/Bb;
     * every other class is a natural. The spelling is always within one accidental.
     */
    fun spelledKeyTonic(keyValue: Int): SpelledPitch {
        require(keyValue in 0 until PitchClass.COUNT) {
            "A key pitch class must be 0..${PitchClass.COUNT - 1} but was $keyValue"
        }
        val letter = when (spelling) {
            SpellingPreference.SHARP -> SHARP_KEY_LETTERS[keyValue]
            SpellingPreference.FLAT -> FLAT_KEY_LETTERS[keyValue]
        }
        return SpelledPitch.spellingFor(MidiNote.C4 + keyValue, letter)
    }

    /** The theoretical [Key] of key pitch class [keyValue] in [tonality]. */
    fun keyFor(keyValue: Int, tonality: Tonality): Key = Key(spelledKeyTonic(keyValue), tonality)

    /**
     * The seven scale pitch classes of [keyValue] in [tonality], in degree order 1..7.
     *
     * Major uses offsets 0,2,4,5,7,9,11; natural minor uses 0,2,3,5,7,8,10. The natural-minor
     * leading tone is 10, so a raised leading tone of a minor dominant context is never a member
     * of this list.
     */
    fun scalePitchClasses(keyValue: Int, tonality: Tonality): List<Int> {
        require(keyValue in 0 until PitchClass.COUNT) {
            "A key pitch class must be 0..${PitchClass.COUNT - 1} but was $keyValue"
        }
        return tonality.degreeOffsets.map { offset -> (keyValue + offset).mod(PitchClass.COUNT) }
    }

    /**
     * The theoretical degree 1..7 of [pitchClass] in [keyValue]/[tonality], or null when the class
     * is not a scale member.
     */
    fun degreeOf(keyValue: Int, tonality: Tonality, pitchClass: Int): Int? {
        require(pitchClass in 0 until PitchClass.COUNT) {
            "A pitch class must be 0..${PitchClass.COUNT - 1} but was $pitchClass"
        }
        val index = scalePitchClasses(keyValue, tonality).indexOf(pitchClass)
        return if (index >= 0) index + 1 else null
    }

    /** The theoretical degree 1..7 of [note] in [keyValue]/[tonality], or null when it is chromatic. */
    fun degreeOfNote(keyValue: Int, tonality: Tonality, note: MidiNote): Int? =
        degreeOf(keyValue, tonality, note.pitchClass.value)

    /**
     * Every target note of [keyValue]/[tonality] inside the packaged instrument range, ascending.
     *
     * The target pool is the key's diatonic scale only; a pitch class outside the scale can never
     * be a target, so a context's chromatic tone is never mistaken for a target note. The
     * configured range constrains key-tonic anchors, not target octaves.
     */
    fun targetNotesFor(keyValue: Int, tonality: Tonality): List<MidiNote> {
        val classes = scalePitchClasses(keyValue, tonality)
        return PlayableRange.VALUES
            .filter { it.mod(PitchClass.COUNT) in classes }
            .map { MidiNote.of(it) }
    }

    /**
     * Every eligible context construction of ([keyValue], [tonality]), anchor ascending.
     *
     * A construction is eligible when every sounding note of the complete context fits the
     * packaged instrument range and the key-tonic anchor fits the configured root range. The
     * context is built exactly as playback will build it, so an anchor whose triad, scale or
     * cadence would leave the range is excluded here, never truncated.
     */
    fun eligibleConstructions(keyValue: Int, tonality: Tonality): List<TonalContext> {
        val key = spelledKeyTonic(keyValue)
        return (minimumNoteValue..maximumNoteValue)
            .map { MidiNote.of(it) }
            .filter { it.pitchClass.value == keyValue }
            .mapNotNull { anchor ->
                val context = try {
                    ContextBuilder.build(key, anchor, tonality, this.context)
                } catch (_: InvalidMidiNoteException) {
                    // The construction leaves the MIDI range; the range rule excludes it too.
                    return@mapNotNull null
                }
                if (context.groups.all { group ->
                        group.notes.all { it.value in PlayableRange.VALUES }
                    }
                ) {
                    context
                } else {
                    null
                }
            }
    }

    /**
     * Every target pair of [keyValue]/[tonality] realizing [interval] under this definition's
     * presentation, in the stable order `(firstMidi, secondMidi, degree1, degree2)`.
     *
     * Both notes come from [targetNotesFor], so both are diatonic. The absolute semitone distance
     * must equal the interval; ASCENDING adds the distance, DESCENDING subtracts it and HARMONIC
     * stores low to high. Equal notes are permitted only for unison, where the one pair per target
     * note keeps the two-target relationship.
     */
    fun eligiblePairs(
        keyValue: Int,
        tonality: Tonality,
        interval: SimpleInterval
    ): List<ContextualIntervalPair> = eligiblePairs(keyValue, tonality, interval, presentation)

    /** [eligiblePairs] with an explicit presentation, for tests and diagnostics. */
    fun eligiblePairs(
        keyValue: Int,
        tonality: Tonality,
        interval: SimpleInterval,
        presentation: IntervalPresentation
    ): List<ContextualIntervalPair> {
        val targets = targetNotesFor(keyValue, tonality)
        val targetValues = targets.map { it.value }.toSet()
        val degreeByPitchClass = scalePitchClasses(keyValue, tonality)
            .withIndex()
            .associate { (index, pitchClass) -> pitchClass to index + 1 }
        val pairs = mutableListOf<ContextualIntervalPair>()
        for (first in targets) {
            val secondValue = when (presentation) {
                IntervalPresentation.ASCENDING,
                IntervalPresentation.HARMONIC -> first.value + interval.semitones

                IntervalPresentation.DESCENDING -> first.value - interval.semitones
            }
            // Adding for ascending/harmonic and subtracting for descending already enforces the
            // direction; a unison pair is the one case where both notes are the same pitch.
            if (secondValue !in targetValues) continue
            val second = MidiNote.of(secondValue)
            pairs += ContextualIntervalPair(
                interval = interval,
                presentation = presentation,
                first = first,
                second = second,
                firstDegree = degreeByPitchClass.getValue(first.pitchClass.value),
                secondDegree = degreeByPitchClass.getValue(second.pitchClass.value)
            )
        }
        return pairs.sortedWith(PAIR_ORDER)
    }

    /**
     * True when ([keyValue], [tonality], [interval]) has at least one eligible context and pair.
     *
     * A pair without a complete context is not a completion: the example must sound both.
     */
    fun supports(keyValue: Int, tonality: Tonality, interval: SimpleInterval): Boolean =
        eligiblePairs(keyValue, tonality, interval).isNotEmpty() &&
            eligibleConstructions(keyValue, tonality).isNotEmpty()

    /** The ordered answer tokens of one pair under this definition's answer format. */
    fun answerTokensFor(
        interval: SimpleInterval,
        firstDegree: Int,
        secondDegree: Int
    ): List<VoiceAssetId> = answerTokens(answerFormat, interval, firstDegree, secondDegree)

    /** The words [answerTokensFor] speaks and the contextual-interval display shows. */
    fun answerTextFor(interval: SimpleInterval, firstDegree: Int, secondDegree: Int): String =
        answerTokensFor(interval, firstDegree, secondDegree).joinToString(" ") {
            VoiceVocabulary.requireEntry(it).spokenText
        }

    /** Diagnostic key name such as `C major` or `A natural minor`, for range errors. */
    fun keyDisplayName(keyValue: Int, tonality: Tonality): String {
        val spelled = spelledKeyTonic(keyValue)
        val name = VoiceVocabulary.noteEntry(spelled.letter, spelled.accidental).spokenText
        val tonalityText = when (tonality) {
            Tonality.MAJOR -> "major"
            Tonality.NATURAL_MINOR -> "natural minor"
        }
        return "$name $tonalityText"
    }

    override fun equals(other: Any?): Boolean = other is ContextualIntervalDefinition &&
        keyValues == other.keyValues &&
        tonalities == other.tonalities &&
        intervalIds == other.intervalIds &&
        presentation == other.presentation &&
        context == other.context &&
        contextGap == other.contextGap &&
        answerFormat == other.answerFormat &&
        spelling == other.spelling &&
        minimumNote == other.minimumNote &&
        maximumNote == other.maximumNote

    override fun hashCode(): Int {
        var result = keyValues.hashCode()
        result = 31 * result + tonalities.hashCode()
        result = 31 * result + intervalIds.hashCode()
        result = 31 * result + presentation.hashCode()
        result = 31 * result + context.hashCode()
        result = 31 * result + contextGap.hashCode()
        result = 31 * result + answerFormat.hashCode()
        result = 31 * result + spelling.hashCode()
        result = 31 * result + minimumNote.hashCode()
        result = 31 * result + maximumNote.hashCode()
        return result
    }

    override fun toString(): String =
        "ContextualIntervalDefinition(keys=$keyValues, tonalities=$tonalities, " +
            "intervals=$intervalIds, presentation=$presentation, context=$context, " +
            "contextGap=$contextGap, answer=$answerFormat, spelling=$spelling, " +
            "range=$minimumNoteValue..$maximumNoteValue)"

    companion object {
        /** The longest allowed context-to-target silence. */
        val MAX_CONTEXT_GAP: Duration = 30.seconds

        /** All twelve key pitch classes, used when the key selection is empty. */
        private val ALL_KEY_VALUES: List<Int> = java.util.Collections.unmodifiableList(
            (0 until PitchClass.COUNT).toList()
        )

        /** Sharp-table key letters per pitch class, C = 0. */
        private val SHARP_KEY_LETTERS = listOf(
            Letter.C, Letter.C, Letter.D, Letter.D, Letter.E, Letter.F,
            Letter.F, Letter.G, Letter.G, Letter.A, Letter.A, Letter.B
        )

        /** Flat-table key letters per pitch class, C = 0. */
        private val FLAT_KEY_LETTERS = listOf(
            Letter.C, Letter.D, Letter.D, Letter.E, Letter.E, Letter.F,
            Letter.G, Letter.G, Letter.A, Letter.A, Letter.B, Letter.B
        )

        /** Stable order of enumerated pairs: `(firstMidi, secondMidi, degree1, degree2)`. */
        private val PAIR_ORDER: Comparator<ContextualIntervalPair> = compareBy(
            { it.first.value },
            { it.second.value },
            { it.firstDegree },
            { it.secondDegree }
        )

        /**
         * Default fixture: C major, tonic triad, minor/major third and perfect fifth, ascending,
         * interval answers, C4..C5.
         */
        fun default(): ContextualIntervalDefinition = of(
            keyValues = listOf(0),
            tonalities = listOf(Tonality.MAJOR),
            intervalIds = listOf(
                SimpleInterval.of(SimpleInterval.MINOR_THIRD).id,
                SimpleInterval.of(SimpleInterval.MAJOR_THIRD).id,
                SimpleInterval.of(SimpleInterval.PERFECT_FIFTH).id
            ),
            presentation = IntervalPresentation.ASCENDING,
            context = ContextKind.TONIC_TRIAD,
            contextGap = 500.milliseconds,
            answerFormat = ContextualIntervalAnswerFormat.INTERVAL,
            spelling = SpellingPreference.SHARP,
            minimumNote = MidiNote.of(60),
            maximumNote = MidiNote.of(72)
        )

        /**
         * Normalizes and validates a definition.
         *
         * An empty [keyValues] collection is valid and means all twelve key tonics. An empty
         * tonality or interval collection is not: a mode with nothing to draw cannot start.
         * Interval ids are deduplicated and ordered by semitone so the label bag always draws from
         * the same canonical list. A selected interval whose context or pair cannot fit is
         * deliberately not rejected here; the provider reports it against `range`, so an invalid
         * draft stays constructible for form feedback.
         *
         * @throws InvalidContextualIntervalDefinitionException when a key value is not a pitch
         *   class, a tonality or interval selection is empty, an interval id is not a simple
         *   interval, the context gap is outside 0..30 s, or the range is inverted. The exception
         *   names the setup field (`keys`, `tonalities`, `intervals`, `contextGap` or `range`) the
         *   failure belongs to.
         */
        fun of(
            keyValues: Collection<Int>,
            tonalities: Collection<Tonality>,
            intervalIds: Collection<String>,
            presentation: IntervalPresentation,
            context: ContextKind,
            contextGap: Duration,
            answerFormat: ContextualIntervalAnswerFormat,
            spelling: SpellingPreference = SpellingPreference.SHARP,
            minimumNote: MidiNote,
            maximumNote: MidiNote
        ): ContextualIntervalDefinition {
            val keys = keyValues.toSet().sorted()
            keys.forEach { value ->
                if (value !in 0 until PitchClass.COUNT) {
                    throw InvalidContextualIntervalDefinitionException(
                        "A key pitch class must be 0..${PitchClass.COUNT - 1} but was $value",
                        field = "keys"
                    )
                }
            }
            val tonalityList = tonalities.toSet().sortedBy { it.ordinal }
            if (tonalityList.isEmpty()) {
                throw InvalidContextualIntervalDefinitionException(
                    "At least one tonality must be selected",
                    field = "tonalities"
                )
            }
            val selected = intervalIds.toSet()
            selected.forEach { id ->
                if (SimpleInterval.ofIdOrNull(id) == null) {
                    throw InvalidContextualIntervalDefinitionException(
                        "Unknown simple interval id '$id'",
                        field = "intervals"
                    )
                }
            }
            val intervalList = SimpleInterval.ALL
                .filter { it.id in selected }
                .map { it.id }
            if (intervalList.isEmpty()) {
                throw InvalidContextualIntervalDefinitionException(
                    "At least one interval must be selected",
                    field = "intervals"
                )
            }
            if (!contextGap.isFinite() || contextGap < Duration.ZERO ||
                contextGap > MAX_CONTEXT_GAP
            ) {
                throw InvalidContextualIntervalDefinitionException(
                    "A context gap must be 0..${MAX_CONTEXT_GAP.inWholeSeconds} s but was $contextGap",
                    field = "contextGap"
                )
            }
            if (minimumNote.value > maximumNote.value) {
                throw InvalidContextualIntervalDefinitionException(
                    "Minimum note ${minimumNote.value} must not exceed maximum note " +
                        "${maximumNote.value}",
                    field = "range"
                )
            }
            return ContextualIntervalDefinition(
                selectedKeyValues = keys,
                selectedTonalities = tonalityList,
                selectedIntervalIds = intervalList,
                presentation = presentation,
                context = context,
                contextGap = contextGap,
                answerFormat = answerFormat,
                spelling = spelling,
                minimumNote = minimumNote,
                maximumNote = maximumNote
            )
        }

        /**
         * The ordered answer tokens of one pair under an explicit format.
         *
         * INTERVAL is the interval phrase alone; DEGREE_PAIR is the two degree-number tokens in
         * presented order; INTERVAL_AND_DEGREES is the interval phrase followed by both degree
         * numbers. This function is the single source of truth for both speech and display, so the
         * compiler inserts its exactly-80 ms gaps between adjacent tokens and nothing else.
         */
        fun answerTokens(
            format: ContextualIntervalAnswerFormat,
            interval: SimpleInterval,
            firstDegree: Int,
            secondDegree: Int
        ): List<VoiceAssetId> = when (format) {
            ContextualIntervalAnswerFormat.INTERVAL ->
                listOf(VoiceVocabulary.intervalEntry(interval).id)

            ContextualIntervalAnswerFormat.DEGREE_PAIR -> listOf(
                VoiceVocabulary.degreeNumberEntry(firstDegree).id,
                VoiceVocabulary.degreeNumberEntry(secondDegree).id
            )

            ContextualIntervalAnswerFormat.INTERVAL_AND_DEGREES -> listOf(
                VoiceVocabulary.intervalEntry(interval).id,
                VoiceVocabulary.degreeNumberEntry(firstDegree).id,
                VoiceVocabulary.degreeNumberEntry(secondDegree).id
            )
        }

        /** The word a range message uses for [presentation]. */
        fun presentationLabel(presentation: IntervalPresentation): String = when (presentation) {
            IntervalPresentation.ASCENDING -> "ascending"
            IntervalPresentation.DESCENDING -> "descending"
            IntervalPresentation.HARMONIC -> "harmonic"
        }
    }
}

/** Raised when a contextual-interval configuration is unknown, empty or internally contradictory. */
class InvalidContextualIntervalDefinitionException(
    message: String,
    cause: Throwable? = null,
    /** The setup field the failure belongs to, for form feedback. */
    val field: String? = null
) : IllegalArgumentException(message, cause)
