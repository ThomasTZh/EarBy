package com.thomas.passiveeartraining.app

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.thomas.passiveeartraining.training.provider.TrainingKind

/**
 * The name the sidebar and the top bar show for a mode.
 *
 * Keyed by the stable kind id, so a persisted selection never depends on enum ordinals or display
 * labels (SC2/M2.4). The owner kept the label "Perfect pitch"; the stable kind id stays
 * `note_identification` and the legacy selection alias still decodes.
 */
internal val TrainingKind.label: String
    get() = when (this) {
        TrainingKind.INTERVALS -> "Interval recognition"
        TrainingKind.INTERVAL_WALK -> "Interval walk"
        TrainingKind.CHORDS -> "Chords"
        TrainingKind.SCALES -> "Scales"
        TrainingKind.CHORD_PROGRESSIONS -> "Chord progressions"
        TrainingKind.NOTE_IDENTIFICATION -> "Perfect pitch"
        TrainingKind.SCALE_DEGREES -> "Scale degrees"
        TrainingKind.INTERVALS_IN_CONTEXT -> "Intervals in context"
        TrainingKind.MELODIC_DICTATION -> "Melodic dictation"
        else -> id
    }

/** One sentence that explains what the mode trains. */
internal val TrainingKind.summary: String
    get() = when (this) {
        TrainingKind.INTERVALS -> "Identify the interval between two notes."
        TrainingKind.INTERVAL_WALK -> "Follow a chain of intervals and name each step."
        TrainingKind.CHORDS -> "Identify chord qualities from a played chord."
        TrainingKind.SCALES -> "Identify scales and modes by ear."
        TrainingKind.CHORD_PROGRESSIONS -> "Follow a chord progression and name its degrees."
        TrainingKind.NOTE_IDENTIFICATION -> "Name a note without a reference tone."
        TrainingKind.SCALE_DEGREES -> "Find a note's degree inside a key."
        TrainingKind.INTERVALS_IN_CONTEXT -> "Hear intervals inside a short musical phrase."
        TrainingKind.MELODIC_DICTATION -> "Transcribe a short melody you hear."
        else -> ""
    }

/** One sentence that explains what the mode sounds like and what the answer speaks. */
internal val TrainingKind.audioDescription: String
    get() = when (this) {
        TrainingKind.INTERVALS ->
            "Two notes play one after the other or together. " +
                "The spoken answer names the interval."
        TrainingKind.INTERVAL_WALK ->
            "A starting note plays, then a chain of intervals. The spoken answer names the " +
                "direction and interval of every step."
        TrainingKind.CHORDS ->
            "A chord plays as one sound or as an ascending arpeggio. " +
                "The spoken answer names its quality and, when the setup asks for it, the inversion."
        TrainingKind.SCALES ->
            "The scale plays up, down, or both ways. The spoken answer names the scale."
        TrainingKind.CHORD_PROGRESSIONS ->
            "Four chords play in order and the first one is the tonic. The spoken answer names " +
                "the degrees, the root motions, or the harmonic functions."
        TrainingKind.NOTE_IDENTIFICATION ->
            "One note plays without a reference tone. The spoken answer names the note and, " +
                "when the setup asks for it, the octave."
        TrainingKind.SCALE_DEGREES ->
            "A short context plays first, for example a triad or a cadence. One note follows, " +
                "and the spoken answer names its degree in the key."
        TrainingKind.INTERVALS_IN_CONTEXT ->
            "A short context plays first. Two notes follow, and the spoken answer names the " +
                "interval, the two degrees, or both."
        TrainingKind.MELODIC_DICTATION ->
            "A short context plays first. A melody of two to sixteen notes follows, and the " +
                "spoken answer names the note sequence."
        else -> ""
    }

/** True while the mode's audio, generation, storage and screens all exist. */
internal val TrainingKind.implemented: Boolean
    get() = this == TrainingKind.INTERVALS || this == TrainingKind.INTERVAL_WALK ||
        this == TrainingKind.CHORDS ||
        this == TrainingKind.SCALES || this == TrainingKind.NOTE_IDENTIFICATION ||
        this == TrainingKind.SCALE_DEGREES ||
        this == TrainingKind.CHORD_PROGRESSIONS ||
        this == TrainingKind.INTERVALS_IN_CONTEXT ||
        this == TrainingKind.MELODIC_DICTATION

/** The page shown for a mode whose backend has not arrived yet. */
@Composable
internal fun ModeUnavailableScreen(kind: TrainingKind, modifier: Modifier = Modifier) {
    Column(modifier = modifier.padding(24.dp)) {
        Text(kind.label, style = MaterialTheme.typography.headlineSmall)
        Text(
            kind.summary,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(top = 8.dp)
        )
        Text(
            "This mode arrives in a later update. Its slots are already reserved and stay empty " +
                "until then.",
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(top = 16.dp)
        )
    }
}
