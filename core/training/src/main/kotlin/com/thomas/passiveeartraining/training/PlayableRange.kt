package com.thomas.passiveeartraining.training

/**
 * The MIDI notes covered by the packaged instrument samples.
 *
 * Root and tonic settings may choose any note in their configured range, but every note that can
 * actually sound, including non-root chord, interval, scale and melody tones, must stay inside this
 * range. The note-identification mode uses its configured range directly.
 */
object PlayableRange {
    const val LOWEST: Int = 21
    const val HIGHEST: Int = 108
    val VALUES: IntRange = LOWEST..HIGHEST
}
