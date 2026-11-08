package com.thomas.passiveeartraining.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Card
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.thomas.passiveeartraining.music.MidiNote
import com.thomas.passiveeartraining.music.SimpleInterval
import com.thomas.passiveeartraining.training.provider.TrainingKind
import com.thomas.passiveeartraining.training.settings.AnswerVisibility
import com.thomas.passiveeartraining.training.settings.ChordSetup
import com.thomas.passiveeartraining.training.settings.ContextualIntervalSetup
import com.thomas.passiveeartraining.training.settings.IntervalWalkSetup
import com.thomas.passiveeartraining.training.settings.LastSetup
import com.thomas.passiveeartraining.training.settings.MelodicDictationSetup
import com.thomas.passiveeartraining.training.settings.NoteSetup
import com.thomas.passiveeartraining.training.settings.ProgressionSetup
import com.thomas.passiveeartraining.training.settings.ScaleDegreeSetup
import com.thomas.passiveeartraining.training.settings.ScaleSetup
import com.thomas.passiveeartraining.training.settings.UserSettings

/** Sleep-timer choices offered on the Play tab; zero cancels. */
private val SLEEP_CHOICES = listOf(0, 15, 30, 45, 60, 90, 120, 180)

/**
 * The Play tab: the training home and the player in one surface.
 *
 * It shows the current setup, transport, the live session state and the sleep timer. The answer
 * text appears only after its frame was consumed; nothing here can reveal it earlier. A mode that
 * is not implemented yet shows its placeholder instead.
 */
@Composable
internal fun PlayScreen(playback: PlaybackViewModel, mode: TrainingKind) {
    if (!mode.implemented) {
        ModeUnavailableScreen(mode, modifier = Modifier.fillMaxSize())
        return
    }
    val application = LocalContext.current.applicationContext as PassiveEarTrainingApp
    val settings by application.settings.collectAsStateWithLifecycle()
    val state by playback.state.collectAsStateWithLifecycle()
    PlayContent(
        state = state,
        setup = settings.settings.lastSetup,
        chordSetup = settings.settings.chordSetup,
        scaleSetup = settings.settings.scalesSetup,
        noteSetup = settings.settings.noteSetup,
        degreeSetup = settings.settings.scaleDegreeSetup,
        progressionSetup = settings.settings.progressionSetup,
        contextualIntervalSetup = settings.settings.contextualIntervalSetup,
        melodicDictationSetup = settings.settings.melodicDictationSetup,
        intervalWalkSetup = settings.settings.intervalWalkSetup,
        mode = mode,
        musicGain = settings.settings.musicGain,
        voiceGain = settings.settings.voiceGain,
        answerVisibility = settings.settings.answerVisibility,
        onPlay = playback::start,
        onStop = playback::stop,
        onSleepChoice = playback::setSleepTimer
    )
}

/**
 * The stateless Play tab.
 *
 * Every value is passed in, so a host screenshot test can render a fixed state without a service,
 * a ViewModel or a device. The page itself never scrolls: the status sits at the top and the
 * controls and setup card collect at the bottom, with the empty middle reserved between them.
 * A long live line and an oversized setup summary scroll inside their own bounded regions, so
 * 150%/200% text can never push the transport off the screen.
 */
@Composable
internal fun PlayContent(
    state: PlaybackUiState,
    setup: LastSetup,
    musicGain: Float,
    voiceGain: Float,
    answerVisibility: AnswerVisibility = AnswerVisibility.SHOWN_WHEN_CONSUMED,
    mode: TrainingKind = TrainingKind.INTERVALS,
    chordSetup: ChordSetup = ChordSetup.default(),
    scaleSetup: ScaleSetup = ScaleSetup.default(),
    noteSetup: NoteSetup = NoteSetup.default(),
    degreeSetup: ScaleDegreeSetup = ScaleDegreeSetup.default(),
    progressionSetup: ProgressionSetup = ProgressionSetup.default(),
    contextualIntervalSetup: ContextualIntervalSetup = ContextualIntervalSetup.default(),
    melodicDictationSetup: MelodicDictationSetup = MelodicDictationSetup.default(),
    intervalWalkSetup: IntervalWalkSetup = IntervalWalkSetup.default(),
    onPlay: () -> Unit,
    onStop: () -> Unit,
    onSleepChoice: (Int) -> Unit
) {
    // The available height bounds the setup summary: at 150%/200% text the summary scrolls
    // inside its own card instead of pushing the transport and the sleep timer off the screen.
    // At normal sizes every card is smaller than the bound, so the page reads exactly as before.
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val summaryMaxHeight = maxHeight * 0.42f
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // The live line gets every vertical dp the controls leave. A short status stays at
            // the top and the space below it stays empty, exactly as before; a long melodic
            // answer (up to 16 notes or 15 interval phrases) wraps and scrolls inside this
            // region instead of pushing the transport off the screen at large text.
            val answerScroll = rememberScrollState()
            // A new answer starts at its first token rather than wherever the previous one
            // ended.
            LaunchedEffect(state.answerText) { answerScroll.scrollTo(0) }
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .verticalScroll(answerScroll)
            ) {
                // One text box for the live line: "Playing" while the notes sound, and the answer
                // itself once the spoken answer starts. It is never shown before its frame is
                // consumed, and the "hide on screen" preference suppresses it entirely.
                val answerShown =
                    state.answerText != null &&
                        answerVisibility == AnswerVisibility.SHOWN_WHEN_CONSUMED
                val status = when {
                    state.unavailable -> "Playback is not available in this build."
                    state.error != null -> "Playback error: ${state.error}"
                    answerShown -> "Answer: ${state.answerText}"
                    state.playing -> "Playing"
                    state.paused -> "Stopped"
                    state.active -> "Preparing"
                    state.connected -> "Ready"
                    else -> "Connecting"
                }
                Text(
                    // The live status uses the page-title size; the current mode is named in the
                    // setup card below.
                    status,
                    style = MaterialTheme.typography.titleLarge
                )
                state.pendingKind?.let { pending ->
                    Text(
                        "Switching to ${pending.label} after this item.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }

            Card {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = summaryMaxHeight)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        "Current setup",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold
                    )
                    Text("Mode: ${mode.label}", style = MaterialTheme.typography.bodySmall)
                    if (state.currentNotes.isNotEmpty()) {
                        Text(
                            "Notes: " + state.currentNotes.joinToString(", ") {
                                MidiNote.of(it).displayName()
                            },
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    when (mode) {
                        TrainingKind.INTERVAL_WALK -> {
                            Text(
                                "Intervals: " + intervalWalkSetup.intervalIds.joinToString(", ") {
                                    com.thomas.passiveeartraining.music.SimpleInterval
                                        .ofIdOrNull(it)?.englishAnswer ?: it
                                },
                                style = MaterialTheme.typography.bodySmall
                            )
                            Text(
                                "Direction: ${intervalWalkSetup.direction.name.lowercase()} · " +
                                    "${intervalWalkSetup.length} steps",
                                style = MaterialTheme.typography.bodySmall
                            )
                            Text(
                                "Starting roots: " +
                                    MidiNote.of(intervalWalkSetup.minimumNote.value).displayName() +
                                    " to " +
                                    MidiNote.of(intervalWalkSetup.maximumNote.value).displayName() +
                                    (
                                        intervalWalkSetup.fixedStart?.let {
                                            " · fixed ${it.displayName()}"
                                        } ?: " · drawn"
                                        ),
                                style = MaterialTheme.typography.bodySmall
                            )
                            Text(
                                "Learning mode: ${modeLabel(intervalWalkSetup.mode)} · thinking " +
                                    "${intervalWalkSetup.thinkingSilence.inWholeSeconds} s",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }

                        TrainingKind.CHORDS -> {
                            Text(
                                "Qualities: ${chordSetup.qualitiesLabel()}",
                                style = MaterialTheme.typography.bodySmall
                            )
                            Text(
                                "Inversions: ${chordSetup.inversionsLabel()}",
                                style = MaterialTheme.typography.bodySmall
                            )
                            Text(
                                "Answer format: ${chordSetup.answerMode.englishLabel()}",
                                style = MaterialTheme.typography.bodySmall
                            )
                            Text(
                                "Presentation: ${chordSetup.presentation.englishLabel()} · " +
                                    "mode: ${chordSetup.mode.name.lowercase().replace('_', ' ')}",
                                style = MaterialTheme.typography.bodySmall
                            )
                            Text(
                                "Roots: ${chordSetup.rootsLabel()}",
                                style = MaterialTheme.typography.bodySmall
                            )
                            Text(
                                "Range: ${
                                    MidiNote.of(
                                        chordSetup.minimumNote
                                    ).displayName()
                                } to " +
                                    MidiNote.of(
                                        chordSetup.maximumNote
                                    ).displayName() +
                                    " · thinking ${chordSetup.thinkingSilence.inWholeSeconds} s" +
                                    " · repeats ${chordSetup.replayCount}",
                                style = MaterialTheme.typography.bodySmall
                            )
                            chordSetup.fixedRoot?.let { root ->
                                Text(
                                    "Fixed root: ${
                                        MidiNote.of(root).displayName()
                                    }",
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }

                        TrainingKind.SCALES -> {
                            // The stored setup is named here; the scale the session generated is
                            // never shown until its answer frame is consumed.
                            Text(
                                "Scales: ${scaleSetup.scalesLabel()}",
                                style = MaterialTheme.typography.bodySmall
                            )
                            Text(
                                "Direction: ${scaleSetup.direction.englishLabel()}",
                                style = MaterialTheme.typography.bodySmall
                            )
                            Text(
                                "Roots: ${scaleSetup.rootsLabel()}",
                                style = MaterialTheme.typography.bodySmall
                            )
                            Text(
                                "Range: ${
                                    MidiNote.of(
                                        scaleSetup.minimumNote
                                    ).displayName()
                                } to " +
                                    MidiNote.of(
                                        scaleSetup.maximumNote
                                    ).displayName() +
                                    " · thinking ${scaleSetup.thinkingSilence.inWholeSeconds} s" +
                                    " · repeats ${scaleSetup.replayCount}",
                                style = MaterialTheme.typography.bodySmall
                            )
                            scaleSetup.fixedRoot?.let { root ->
                                Text(
                                    "Fixed root: ${
                                        MidiNote.of(root).displayName()
                                    }",
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }

                        TrainingKind.NOTE_IDENTIFICATION -> {
                            // The stored setup is named here; the note the session generated is
                            // never shown, and no "current MIDI note" diagnostic exists on any
                            // surface.
                            Text(
                                "Notes: ${noteSetup.notesLabel()}",
                                style = MaterialTheme.typography.bodySmall
                            )
                            Text(
                                "Range: ${
                                    MidiNote.of(
                                        noteSetup.minimumNote
                                    ).displayName()
                                } to " +
                                    MidiNote.of(
                                        noteSetup.maximumNote
                                    ).displayName() +
                                    " · thinking ${noteSetup.thinkingSilence.inWholeSeconds} s" +
                                    " · repeats ${noteSetup.replayCount}",
                                style = MaterialTheme.typography.bodySmall
                            )
                            Text(
                                "Accidentals: ${noteSetup.accidental.englishLabel()} · " +
                                    "octave speech: ${if (noteSetup.speakOctave) "on" else "off"}",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }

                        TrainingKind.SCALE_DEGREES -> {
                            // The stored setup is named here; the key, context and target the
                            // session generated are never shown before their answer frame is
                            // consumed.
                            Text(
                                "Keys: ${degreeSetup.keysLabel()}",
                                style = MaterialTheme.typography.bodySmall
                            )
                            Text(
                                "Tonalities: ${degreeSetup.tonalitiesLabel()}",
                                style = MaterialTheme.typography.bodySmall
                            )
                            Text(
                                "Degrees: ${degreeSetup.degreesLabel()}",
                                style = MaterialTheme.typography.bodySmall
                            )
                            Text(
                                "Context: ${degreeSetup.context.label} · gap " +
                                    "${formatSeconds(degreeSetup.contextGap)} s",
                                style = MaterialTheme.typography.bodySmall
                            )
                            Text(
                                "Answer format: ${degreeSetup.answerFormat.englishLabel()}",
                                style = MaterialTheme.typography.bodySmall
                            )
                            Text(
                                "Range: ${
                                    MidiNote.of(
                                        degreeSetup.minimumNote
                                    ).displayName()
                                } to " +
                                    MidiNote.of(
                                        degreeSetup.maximumNote
                                    ).displayName() +
                                    " · thinking ${degreeSetup.thinkingSilence.inWholeSeconds} s" +
                                    " · repeats ${degreeSetup.replayCount}",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }

                        TrainingKind.CHORD_PROGRESSIONS -> {
                            // The stored setup is named here; the progression the session generated
                            // is never shown before its answer frame is consumed.
                            Text(
                                if (progressionSetup.fixedRoot != null) {
                                    "Fixed root: ${progressionSetup.fixedRootLabel()}"
                                } else {
                                    "Keys: all twelve"
                                },
                                style = MaterialTheme.typography.bodySmall
                            )
                            Text(
                                "Chords: ${progressionSetup.chordsLabel()}",
                                style = MaterialTheme.typography.bodySmall
                            )
                            Text(
                                "Voicing: ${progressionSetup.voicingLabel()}",
                                style = MaterialTheme.typography.bodySmall
                            )
                            Text(
                                "Answer format: ${progressionSetup.answerFormat.englishLabel()}",
                                style = MaterialTheme.typography.bodySmall
                            )
                            Text(
                                "Range: ${
                                    MidiNote.of(
                                        progressionSetup.minimumNote
                                    ).displayName(spelling = progressionSetup.spelling)
                                } to " +
                                    MidiNote.of(
                                        progressionSetup.maximumNote
                                    ).displayName(spelling = progressionSetup.spelling) +
                                    " · thinking " +
                                    "${progressionSetup.thinkingSilence.inWholeSeconds} s" +
                                    " · repeats ${progressionSetup.replayCount}",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }

                        TrainingKind.INTERVALS_IN_CONTEXT -> {
                            // The stored setup is named here; the drawn interval and its two target
                            // notes are never shown before their answer frame is consumed. The
                            // selected allowed intervals are safe configuration.
                            Text(
                                "Keys: ${contextualIntervalSetup.keysLabel()}",
                                style = MaterialTheme.typography.bodySmall
                            )
                            Text(
                                "Tonalities: ${contextualIntervalSetup.tonalitiesLabel()}",
                                style = MaterialTheme.typography.bodySmall
                            )
                            Text(
                                "Intervals: ${contextualIntervalSetup.intervalsLabel()}",
                                style = MaterialTheme.typography.bodySmall
                            )
                            Text(
                                "Context: ${contextualIntervalSetup.context.label} · gap " +
                                    "${formatSeconds(contextualIntervalSetup.contextGap)} s",
                                style = MaterialTheme.typography.bodySmall
                            )
                            Text(
                                "Presentation: " +
                                    "${contextualIntervalSetup.presentation.englishLabel()} · " +
                                    "answer format: " +
                                    contextualIntervalSetup.answerFormat.englishLabel(),
                                style = MaterialTheme.typography.bodySmall
                            )
                            Text(
                                "Range: ${
                                    MidiNote.of(
                                        contextualIntervalSetup.minimumNote
                                    ).displayName(spelling = contextualIntervalSetup.spelling)
                                } to " +
                                    MidiNote.of(
                                        contextualIntervalSetup.maximumNote
                                    ).displayName(spelling = contextualIntervalSetup.spelling) +
                                    " · thinking " +
                                    "${contextualIntervalSetup.thinkingSilence.inWholeSeconds} s" +
                                    " · repeats ${contextualIntervalSetup.replayCount}",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }

                        TrainingKind.MELODIC_DICTATION -> {
                            // The stored setup is named here; the drawn melody, its key and its
                            // answer are never shown before the answer frame is consumed. The
                            // phrase length, endpoints and steps are safe configuration.
                            Text(
                                "Keys: ${melodicDictationSetup.keysLabel()}",
                                style = MaterialTheme.typography.bodySmall
                            )
                            Text(
                                "Tonalities: ${melodicDictationSetup.tonalitiesLabel()}",
                                style = MaterialTheme.typography.bodySmall
                            )
                            Text(
                                "Context: ${melodicDictationSetup.context.label} · gap " +
                                    "${formatSeconds(melodicDictationSetup.contextGap)} s",
                                style = MaterialTheme.typography.bodySmall
                            )
                            Text(
                                "Phrase: ${melodicDictationSetup.phraseLength} notes · " +
                                    melodicDictationSetup.endpointsLabel(),
                                style = MaterialTheme.typography.bodySmall
                            )
                            Text(
                                "Steps: ${melodicDictationSetup.stepsLabel()} · leap " +
                                    "${melodicDictationSetup.maximumLeap} · bias " +
                                    melodicDictationSetup.bias.englishLabel(),
                                style = MaterialTheme.typography.bodySmall
                            )
                            Text(
                                "Answer format: " +
                                    melodicDictationSetup.answerFormat.englishLabel(),
                                style = MaterialTheme.typography.bodySmall
                            )
                            Text(
                                "Range: ${
                                    melodicDictationSetup.minimumNote.displayName(
                                        spelling = melodicDictationSetup.spelling
                                    )
                                } to " +
                                    melodicDictationSetup.maximumNote.displayName(
                                        spelling = melodicDictationSetup.spelling
                                    ) +
                                    " · thinking " +
                                    "${melodicDictationSetup.thinkingSilence.inWholeSeconds} s" +
                                    " · repeats ${melodicDictationSetup.replayCount}",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }

                        else -> {
                            Text(
                                if (setup.intervalIds.isEmpty()) {
                                    "Intervals: all (none selected)"
                                } else {
                                    "Intervals: " +
                                        setup.intervalIds.joinToString { id ->
                                            SimpleInterval.ofId(id).englishAnswer
                                        }
                                },
                                style = MaterialTheme.typography.bodySmall
                            )
                            Text(
                                "Presentation: ${setup.presentationLabel()} · " +
                                    "mode: ${setup.mode.name.lowercase().replace('_', ' ')}",
                                style = MaterialTheme.typography.bodySmall
                            )
                            Text(
                                "Range: ${
                                    MidiNote.of(
                                        setup.minimumNote
                                    ).displayName()
                                } to " +
                                    MidiNote.of(setup.maximumNote)
                                        .displayName() +
                                    " · thinking ${setup.thinkingSilence.inWholeSeconds} s" +
                                    " · repeats ${setup.replayCount}",
                                style = MaterialTheme.typography.bodySmall
                            )
                            setup.fixedRoot?.let { root ->
                                Text(
                                    "Fixed note: ${
                                        MidiNote.of(root).displayName()
                                    }",
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                    }
                    // The next Start uses the setup's own seed; with none stored it draws a
                    // fresh one, and the last session's seed is shown until then.
                    val configuredSeed = when (mode) {
                        TrainingKind.CHORDS -> chordSetup.seed
                        TrainingKind.SCALES -> scaleSetup.seed
                        TrainingKind.NOTE_IDENTIFICATION -> noteSetup.seed
                        TrainingKind.SCALE_DEGREES -> degreeSetup.seed
                        TrainingKind.CHORD_PROGRESSIONS -> progressionSetup.seed
                        TrainingKind.INTERVALS_IN_CONTEXT -> contextualIntervalSetup.seed
                        TrainingKind.MELODIC_DICTATION -> melodicDictationSetup.seed
                        TrainingKind.INTERVAL_WALK -> intervalWalkSetup.seed
                        else -> setup.seed
                    }
                    if (configuredSeed != null) {
                        Text(
                            "Seed: $configuredSeed (fixed by the setup)",
                            style = MaterialTheme.typography.bodySmall
                        )
                    } else {
                        state.seed?.let { seed ->
                            Text(
                                "Seed: $seed (a new one is drawn each start)",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                    Text(
                        "Music ${
                            setupGain(musicGain, UserSettings.DEFAULT_MUSIC_GAIN)
                        }, voice ${
                            setupGain(voiceGain, UserSettings.DEFAULT_VOICE_GAIN)
                        }",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }

            // One round play button starts or stops the session; Setup lives in the bottom tabs.
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center
            ) {
                val running = state.playing || state.active
                FilledIconButton(
                    onClick = { if (running) onStop() else onPlay() },
                    enabled = state.connected,
                    modifier = Modifier
                        .size(72.dp)
                        .testTag("play_stop")
                ) {
                    Icon(
                        imageVector = if (running) StopSquare else Icons.Filled.PlayArrow,
                        contentDescription = if (running) "Stop" else "Start"
                    )
                }
            }

            // The sleep timer is a value button; tapping it opens the scroll wheel in a dialog.
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text("Sleep timer", style = MaterialTheme.typography.titleMedium)
                    val remaining = state.sleepTimerRemainingMs
                    Text(
                        when {
                            state.sleepTimerMinutes <= 0 -> "Off"
                            remaining != null && state.sleepTimerDraining ->
                                "Stopping in ${formatCountdown(remaining)} (finishing the item)"

                            remaining != null -> "Stopping in ${formatCountdown(remaining)}"
                            else -> "Set"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                ChooserButton(
                    options = SLEEP_CHOICES,
                    selectedIndex = SLEEP_CHOICES.indexOf(state.sleepTimerMinutes).coerceAtLeast(0),
                    onSelected = { index -> onSleepChoice(SLEEP_CHOICES[index]) },
                    description = "Sleep timer",
                    modifier = Modifier.testTag("sleep_picker"),
                    format = { minutes -> if (minutes == 0) "Off" else "$minutes min" }
                )
            }
        }
    }
}

/** A gain shown as its slider percentage, with the reviewed normal at 50%. */
internal fun setupGain(value: Float, default: Float): String =
    "${(gainPosition(value, default) * 100).toInt()}%"

/** The stop square drawn locally: the core icon set has no stop glyph. */
private val StopSquare: ImageVector = ImageVector.Builder(
    name = "StopSquare",
    defaultWidth = 24.dp,
    defaultHeight = 24.dp,
    viewportWidth = 24f,
    viewportHeight = 24f
).apply {
    path(fill = SolidColor(Color.Black)) {
        moveTo(7f, 7f)
        horizontalLineTo(17f)
        verticalLineTo(17f)
        horizontalLineTo(7f)
        close()
    }
}.build()

/** Formats a remaining duration as m:ss for the sleep-timer countdown. */
internal fun formatCountdown(milliseconds: Long): String {
    val safe = milliseconds.coerceAtLeast(0L)
    val minutes = safe / 60_000L
    val seconds = (safe % 60_000L) / 1_000L
    return "$minutes:${seconds.toString().padStart(2, '0')}"
}
