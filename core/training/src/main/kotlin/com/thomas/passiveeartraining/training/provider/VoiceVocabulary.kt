package com.thomas.passiveeartraining.training.provider

import com.thomas.passiveeartraining.music.ChordInversions
import com.thomas.passiveeartraining.music.ChordQuality
import com.thomas.passiveeartraining.music.HarmonicFunction
import com.thomas.passiveeartraining.music.Letter
import com.thomas.passiveeartraining.music.ProgressionStep
import com.thomas.passiveeartraining.music.RootMotionDirection
import com.thomas.passiveeartraining.music.ScaleKind
import com.thomas.passiveeartraining.music.SimpleInterval
import com.thomas.passiveeartraining.training.VoiceAssetId

/**
 * The stable namespaces of the semantic voice vocabulary (M4.3).
 *
 * Every namespace is independent: a clip may share a pronunciation with another namespace only
 * through an explicit registry entry, never by reusing an id from a different meaning.
 */
enum class VoiceNamespace(val prefix: String) {
    /** The thirteen frozen interval phrases; ids and text are unchanged. */
    INTERVAL("interval"),

    /** Chord qualities, inversion labels and the alternative conjunction. */
    CHORD("chord"),

    /** The thirteen canonical scale names; aliases are display metadata only. */
    SCALE("scale"),

    /** Absolute note names: letter + accidental and separate octave tokens. */
    NOTE("note"),

    /** Degree numbers 1 through 7. */
    DEGREE("degree"),

    /** Phrase connectors such as ascending, descending, unison and "or". */
    CONNECTOR("connector"),

    /** The three harmonic function words (M9); Roman answers compose degree and chord tokens. */
    HARMONY("harmony")
}

/** One semantic voice token: a stable id, its namespace and the exact spoken text. */
data class VoiceEntry(val id: VoiceAssetId, val namespace: VoiceNamespace, val spokenText: String) {
    init {
        require(id.value.startsWith("${namespace.prefix}.")) {
            "Voice id ${id.value} is not in the ${namespace.prefix} namespace"
        }
        require(spokenText.isNotBlank()) { "A voice entry must have spoken text" }
    }
}

/**
 * The declarative vocabulary every mode consumes.
 *
 * The interval, chord and scale entries are derived from the frozen music catalogs, so a catalog
 * change cannot leave the vocabulary behind. Note, octave and degree entries are generated from
 * the same theoretical tables the answers use. Token *order* per answer template belongs to the
 * owning mode milestone; this registry only fixes ids, text and namespaces.
 */
object VoiceVocabulary {

    /** The alternative conjunction used by composed answers such as equivalent chords. */
    val OR: VoiceEntry = entry(VoiceNamespace.CONNECTOR, "or", "or")

    /** The direction and phrasing connectors. */
    val ASCENDING: VoiceEntry = entry(VoiceNamespace.CONNECTOR, "ascending", "ascending")
    val DESCENDING: VoiceEntry = entry(VoiceNamespace.CONNECTOR, "descending", "descending")
    val UNISON: VoiceEntry = entry(VoiceNamespace.CONNECTOR, "unison", "unison")

    /** Every interval phrase, derived from the frozen interval catalog. */
    fun intervalEntry(interval: SimpleInterval): VoiceEntry = entry(
        VoiceNamespace.INTERVAL,
        interval.id,
        interval.englishAnswer
    )

    /** Every chord quality name. */
    fun chordQualityEntry(quality: ChordQuality): VoiceEntry = entry(
        VoiceNamespace.CHORD,
        quality.id,
        quality.englishAnswer
    )

    /** The inversion labels, 0 = root position through 3 = third inversion. */
    fun chordInversionEntry(inversion: Int): VoiceEntry = entry(
        VoiceNamespace.CHORD,
        "inversion.${ChordInversions.idSuffix(inversion)}",
        ChordInversions.answer(inversion)
    )

    /** The three harmonic function words of the M9 progression catalog. */
    fun harmonyFunctionEntry(function: HarmonicFunction): VoiceEntry = entry(
        VoiceNamespace.HARMONY,
        function.id,
        function.englishAnswer
    )

    /** The direction connector a root-motion answer speaks for [direction]. */
    fun rootMotionDirectionEntry(direction: RootMotionDirection): VoiceEntry = when (direction) {
        RootMotionDirection.ASCENDING -> ASCENDING
        RootMotionDirection.DESCENDING -> DESCENDING
        RootMotionDirection.UNISON -> UNISON
    }

    /**
     * The degree-number then chord-quality entries a Roman answer composes for [step].
     *
     * The composition rule lives here once, so speech and display can never list a different pair
     * order than the definition's token list.
     */
    fun romanEntries(step: ProgressionStep): List<VoiceEntry> =
        listOf(degreeNumberEntry(step.degree), chordQualityEntry(step.quality))

    /** Every canonical scale name; aliases are not separate entries. */
    fun scaleEntry(kind: ScaleKind): VoiceEntry = entry(
        VoiceNamespace.SCALE,
        kind.id,
        kind.id.replace('_', ' ')
    )

    /** One absolute note name without the octave, for example `note.c_sharp` = "C sharp". */
    fun noteEntry(letter: Letter, accidental: Int): VoiceEntry {
        require(accidental in -2..2) { "An accidental is -2..2 but was $accidental" }
        val letterId = letter.name.lowercase()
        val accidentalId = when (accidental) {
            0 -> ""
            1 -> "_sharp"
            2 -> "_double_sharp"
            -1 -> "_flat"
            else -> "_double_flat"
        }
        val accidentalText = when (accidental) {
            0 -> ""
            1 -> " sharp"
            2 -> " double sharp"
            -1 -> " flat"
            else -> " double flat"
        }
        return entry(
            VoiceNamespace.NOTE,
            "$letterId$accidentalId",
            "${letter.name}$accidentalText"
        )
    }

    /** One spoken octave token, -1 through 9. */
    fun octaveEntry(octave: Int): VoiceEntry {
        require(octave in -1..9) { "A spoken octave is -1..9 but was $octave" }
        val text = when (octave) {
            -1 -> "minus one"
            0 -> "zero"
            1 -> "one"
            2 -> "two"
            3 -> "three"
            4 -> "four"
            5 -> "five"
            6 -> "six"
            7 -> "seven"
            8 -> "eight"
            else -> "nine"
        }
        val suffix = if (octave < 0) "minus_1" else "$octave"
        return entry(VoiceNamespace.NOTE, "octave.$suffix", text)
    }

    /** The degree number token, 1 through 7. */
    fun degreeNumberEntry(degree: Int): VoiceEntry {
        require(degree in 1..7) { "A degree is 1..7 but was $degree" }
        val words = listOf("one", "two", "three", "four", "five", "six", "seven")
        return entry(VoiceNamespace.DEGREE, words[degree - 1], words[degree - 1])
    }

    private fun entry(namespace: VoiceNamespace, suffix: String, text: String): VoiceEntry =
        VoiceEntry(VoiceAssetId("${namespace.prefix}.$suffix"), namespace, text)

    /** Every entry the vocabulary currently declares, in stable namespace order. */
    val entries: List<VoiceEntry> = buildList {
        SimpleInterval.ALL.forEach { add(intervalEntry(it)) }
        ChordQuality.entries.forEach { add(chordQualityEntry(it)) }
        (0..3).forEach { add(chordInversionEntry(it)) }
        ScaleKind.entries.forEach { add(scaleEntry(it)) }
        Letter.entries.forEach { letter ->
            for (accidental in -2..2) add(noteEntry(letter, accidental))
        }
        (-1..9).forEach { add(octaveEntry(it)) }
        (1..7).forEach { add(degreeNumberEntry(it)) }
        HarmonicFunction.entries.forEach { add(harmonyFunctionEntry(it)) }
        add(OR)
        add(ASCENDING)
        add(DESCENDING)
        add(UNISON)
    }

    private val byId: Map<VoiceAssetId, VoiceEntry> = entries.associateBy { it.id }

    /** The entry for [id], or null when the vocabulary does not declare it. */
    fun ofIdOrNull(id: VoiceAssetId): VoiceEntry? = byId[id]

    /** The entry for [id]; a missing id is a programmer error, not a silent fallback. */
    fun requireEntry(id: VoiceAssetId): VoiceEntry = byId[id]
        ?: throw IllegalArgumentException("No voice entry for '${id.value}'")
}
