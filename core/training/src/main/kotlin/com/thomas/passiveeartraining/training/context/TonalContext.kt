package com.thomas.passiveeartraining.training.context

import com.thomas.passiveeartraining.music.MidiNote
import com.thomas.passiveeartraining.music.SpelledPitch
import com.thomas.passiveeartraining.music.Tonality
import com.thomas.passiveeartraining.training.provider.GroupRole
import com.thomas.passiveeartraining.training.provider.NoteGroup
import com.thomas.passiveeartraining.training.recipe.RecipeTiming
import kotlin.time.Duration

/**
 * The five reusable tonal contexts one scale-degree example can establish (M8.1).
 *
 * [label] is diagnostic text for range errors and summaries; it is never a persisted value and
 * never an answer.
 */
enum class ContextKind(val label: String) {
    /** One tonic note at the selected anchor. */
    TONIC_NOTE("tonic note"),

    /** The root-position tonic triad: major [0,4,7], natural minor [0,3,7]. */
    TONIC_TRIAD("tonic triad"),

    /** The complete ascending major or natural-minor octave, including the upper tonic. */
    ASCENDING_SCALE("ascending scale"),

    /** Tonic-dominant-tonic; the minor form uses a major V with the raised leading tone. */
    TONIC_DOMINANT_TONIC("tonic-dominant-tonic"),

    /** Subdominant-dominant-tonic; the minor form uses a minor iv and a major V. */
    SUBDOMINANT_DOMINANT_TONIC("subdominant-dominant-tonic")
}

/**
 * Immutable tonal context: the spelled key, the tonic MIDI anchor, the tonality, the context kind
 * and the sounding groups marked [GroupRole.CONTEXT].
 *
 * A context is generated musical data, not live playback state: it is built once by
 * [ContextBuilder] and reused by later providers instead of a second audio pipeline. Every group
 * is a context group by construction, so a target can never contaminate the prelude that exposure
 * accounting, answer calculation and target-interval calculation must exclude.
 *
 * @throws IllegalArgumentException when no group is present or a group is not a context group.
 */
data class TonalContext(
    /** The spelled key tonic the context was built for. */
    val key: SpelledPitch,
    /** The tonic MIDI anchor the context sounds at. */
    val anchor: MidiNote,
    val tonality: Tonality,
    val kind: ContextKind,
    private val groupList: List<NoteGroup>
) {
    /** The scheduling groups in play order; a defensive immutable copy of the built groups. */
    val groups: List<NoteGroup> = java.util.Collections.unmodifiableList(groupList.toList())

    init {
        require(this.groups.isNotEmpty()) { "A tonal context must contain at least one group" }
        require(this.groups.all { it.role == GroupRole.CONTEXT }) {
            "Every tonal context group must have the CONTEXT role: $this"
        }
    }

    /** The sounding pitch class of the spelled key tonic, C = 0 through B = 11. */
    val keyPitchClass: Int get() = key.midiValue.mod(MidiNote.SEMITONES_PER_OCTAVE)

    /** Every sounding MIDI value of this context, in group order. */
    val soundingValues: List<Int>
        get() = groups.flatMap { group -> group.notes.map { it.value } }
}

/**
 * Enumerates the exact musical content of the five contexts (M8.1).
 *
 * All notes are diatonic to the key except the natural minor's dominant V, which raises the
 * leading tone only inside that chord. Cadence chords are root-position closed triads at the
 * tonic anchor's octave; an inversion is never silently chosen to fit a narrow range, so range
 * feasibility belongs to the caller (M8.2's finite candidate enumeration).
 *
 * @throws com.thomas.passiveeartraining.music.InvalidMidiNoteException when a required note leaves
 *   the MIDI range 0..127.
 */
object ContextBuilder {

    /** Builds the groups of [kind] for the spelled [key] at [anchor] in [tonality]. */
    fun build(
        key: SpelledPitch,
        anchor: MidiNote,
        tonality: Tonality,
        kind: ContextKind
    ): TonalContext {
        val tonicIsMajor = tonality == Tonality.MAJOR
        val subdominantRoot = tonality.degreeOffsets[3]
        val dominantRoot = tonality.degreeOffsets[4]
        val groups = when (kind) {
            ContextKind.TONIC_NOTE -> listOf(group(listOf(anchor)))

            ContextKind.TONIC_TRIAD ->
                listOf(triad(anchor, rootOffset = 0, major = tonicIsMajor))

            ContextKind.ASCENDING_SCALE -> ascendingScale(anchor, tonality)

            ContextKind.TONIC_DOMINANT_TONIC -> listOf(
                triad(anchor, rootOffset = 0, major = tonicIsMajor),
                triad(anchor, rootOffset = dominantRoot, major = true),
                triad(anchor, rootOffset = 0, major = tonicIsMajor)
            )

            ContextKind.SUBDOMINANT_DOMINANT_TONIC -> listOf(
                triad(anchor, rootOffset = subdominantRoot, major = tonicIsMajor),
                triad(anchor, rootOffset = dominantRoot, major = true),
                triad(anchor, rootOffset = 0, major = tonicIsMajor)
            )
        }
        return TonalContext(key, anchor, tonality, kind, groups)
    }

    /**
     * The context's duration for [timing]: one note for the tonic note and tonic triad,
     * `8*d + 7*g` for the eight ascending scale notes, and `3*d + 2*g` for a three-chord cadence.
     */
    fun duration(timing: RecipeTiming, kind: ContextKind): Duration = when (kind) {
        ContextKind.TONIC_NOTE,
        ContextKind.TONIC_TRIAD -> timing.noteDuration

        ContextKind.ASCENDING_SCALE -> timing.noteDuration * 8 + timing.melodicGap * 7

        ContextKind.TONIC_DOMINANT_TONIC,
        ContextKind.SUBDOMINANT_DOMINANT_TONIC ->
            timing.noteDuration * 3 + timing.melodicGap * 2
    }

    private fun group(notes: List<MidiNote>): NoteGroup = NoteGroup(notes, GroupRole.CONTEXT)

    /** A root-position closed triad at [anchor] + [rootOffset]. */
    private fun triad(anchor: MidiNote, rootOffset: Int, major: Boolean): NoteGroup {
        val root = anchor.requireTransposedBy(rootOffset)
        val third = if (major) 4 else 3
        return group(
            listOf(root, root.requireTransposedBy(third), root.requireTransposedBy(7))
        )
    }

    /** Seven diatonic degrees plus the repeated upper tonic, one note per group. */
    private fun ascendingScale(anchor: MidiNote, tonality: Tonality): List<NoteGroup> =
        (tonality.degreeOffsets + MidiNote.SEMITONES_PER_OCTAVE).map { offset ->
            group(listOf(anchor.requireTransposedBy(offset)))
        }
}
