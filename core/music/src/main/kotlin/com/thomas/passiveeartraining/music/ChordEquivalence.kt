package com.thomas.passiveeartraining.music

/**
 * One allowed reading of a chord sound: a quality paired with the inversion it takes.
 *
 * The pair is the answer vocabulary unit (D08/M5.1); the root is deliberately absent because a
 * chord answer never names it.
 */
data class ChordInterpretation(val quality: ChordQuality, val inversion: Int) {
    init {
        require(inversion in 0..quality.maximumInversion) {
            "Inversion $inversion does not apply to ${quality.id}"
        }
    }
}

/**
 * D08 equivalence: which allowed readings describe a played chord voicing.
 *
 * Two readings describe the same sound exactly when their closed-position intervals above the
 * bass agree: a chord answer names the quality and the inversion, never the root, so the root is
 * only an implementation detail of the generated voicing. The operation is a pure musical
 * comparison and never depends on how the voicing was drawn.
 */
object ChordEquivalence {

    /** The ordered closed-position intervals above the bass for [quality] in [inversion]. */
    fun bassRelativeOffsets(quality: ChordQuality, inversion: Int): List<Int> {
        require(inversion in 0..quality.maximumInversion) {
            "Inversion $inversion does not apply to ${quality.id}"
        }
        val raised = quality.semitones.mapIndexed { index, semitone ->
            if (index < inversion) semitone + MidiNote.SEMITONES_PER_OCTAVE else semitone
        }.sorted()
        val bass = raised.first()
        return raised.map { it - bass }
    }

    /** The same view for a concrete voicing: its sounding notes relative to its bass. */
    fun bassRelativeOffsetsOf(voicing: ChordVoicing): List<Int> {
        val bass = voicing.bass.value
        return voicing.notes.map { it.value - bass }
    }

    /**
     * Every allowed reading whose closed voicing matches [voicing] exactly.
     *
     * The result is sorted by catalog order and then inversion; this order is the spoken and
     * displayed alternative order, so the two can never disagree (M5.1).
     */
    fun interpretationsOf(
        voicing: ChordVoicing,
        allowed: List<ChordInterpretation>
    ): List<ChordInterpretation> {
        val offsets = bassRelativeOffsetsOf(voicing)
        return allowed
            .filter { bassRelativeOffsets(it.quality, it.inversion) == offsets }
            .sortedWith(compareBy({ it.quality.ordinal }, { it.inversion }))
    }
}
