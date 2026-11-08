@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.thomas.passiveeartraining.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.thomas.passiveeartraining.music.IntervalPresentation
import com.thomas.passiveeartraining.music.MidiNote
import com.thomas.passiveeartraining.music.PitchClass
import com.thomas.passiveeartraining.music.SimpleInterval
import com.thomas.passiveeartraining.music.Tonality
import com.thomas.passiveeartraining.training.ContextualIntervalAnswerFormat
import com.thomas.passiveeartraining.training.context.ContextKind
import com.thomas.passiveeartraining.training.recipe.RecipeMode
import com.thomas.passiveeartraining.training.recipe.SessionStopPolicy
import com.thomas.passiveeartraining.training.settings.ContextualIntervalSetupDraft
import com.thomas.passiveeartraining.training.settings.SettingsRepository
import com.thomas.passiveeartraining.training.settings.SetupValidation

/**
 * The intervals-in-context setup form (M9B.2).
 *
 * The controls mirror the scale-degree and progression screens: the everyday musical choices first,
 * the shared recipe controls next, and every value checked by the shared domain validator with all
 * problems listed at once. Keys read "no selection = all twelve keys" exactly like the degree
 * screen, the checklist is the thirteen simple intervals with minor/major third and perfect fifth
 * selected initially, and the provider attaches an impossible interval/presentation/key or range
 * combination to the responsible field with the exact key, context, interval and range that failed.
 *
 * The selected allowed intervals are safe configuration: showing them never reveals which interval
 * or degree pair the current exercise is. The context picker reuses the M8 labels and wording
 * because the five contexts are the same musical objects.
 */
@Composable
internal fun ContextualIntervalSetupScreen(settingsRepository: SettingsRepository) {
    val viewModel: ContextualIntervalSetupViewModel = viewModel(
        factory = ContextualIntervalSetupViewModelFactory(
            settingsRepository,
            (LocalContext.current.applicationContext as? PassiveEarTrainingApp)?.settingsWriteScope
        )
    )
    val state by viewModel.state.collectAsStateWithLifecycle()

    // Every edit autosaves; leaving the page flushes a pending debounce so nothing is lost.
    DisposableEffect(Unit) {
        onDispose { viewModel.save() }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("Intervals in context setup", style = MaterialTheme.typography.headlineMedium)

        if (!state.loaded) {
            CircularProgressIndicator()
            return@Column
        }

        val draft = state.draft
        // The setup's own sharp/flat table spells the key tonics and every range readout;
        // contextual-interval answers never speak note names.
        val naming = draft.spelling

        Text("Key tonics", style = MaterialTheme.typography.titleMedium)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            (0 until PitchClass.COUNT).forEach { value ->
                FilterChip(
                    selected = value in draft.keyValues,
                    onClick = {
                        viewModel.editDraft { current ->
                            current.copy(
                                keyValues = if (value in current.keyValues) {
                                    current.keyValues - value
                                } else {
                                    (current.keyValues + value).sorted()
                                }
                            )
                        }
                    },
                    label = { Text(spelledPitchClassLabel(value, draft.spelling)) }
                )
            }
        }
        Text(
            if (draft.keyValues.isEmpty()) {
                "No key selected means all twelve keys are included."
            } else {
                "Each key tonic is trained in every selected tonality that can realize it."
            },
            style = MaterialTheme.typography.bodySmall
        )

        Text("Tonalities", style = MaterialTheme.typography.titleMedium)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Tonality.entries.forEach { tonality ->
                FilterChip(
                    selected = tonality in draft.tonalities,
                    onClick = {
                        viewModel.editDraft { current ->
                            current.copy(
                                tonalities = if (tonality in current.tonalities) {
                                    current.tonalities - tonality
                                } else {
                                    (current.tonalities + tonality).sortedBy { it.ordinal }
                                }
                            )
                        }
                    },
                    label = { Text(tonality.chipLabel()) }
                )
            }
        }
        if (draft.tonalities.isEmpty()) {
            Text(
                "Select at least one tonality; an empty selection cannot be played.",
                style = MaterialTheme.typography.bodySmall
            )
        }

        Text("Context", style = MaterialTheme.typography.titleMedium)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ContextKind.entries.forEach { kind ->
                FilterChip(
                    selected = draft.context == kind,
                    onClick = { viewModel.editDraft { it.copy(context = kind) } },
                    label = { Text(kind.chipLabel()) }
                )
            }
        }
        Text(draft.context.setupDescription(), style = MaterialTheme.typography.bodySmall)

        Text("Intervals", style = MaterialTheme.typography.titleMedium)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SimpleInterval.ALL.forEach { interval ->
                FilterChip(
                    selected = interval.id in draft.intervalIds,
                    onClick = {
                        viewModel.editDraft { it.toggleInterval(interval) }
                    },
                    label = { Text(interval.englishAnswer) }
                )
            }
        }
        Text(
            if (draft.intervalIds.isEmpty()) {
                "Select at least one interval; an empty selection cannot be played."
            } else {
                "Every selected interval must have a playable context and target pair in every " +
                    "selected key. Impossible combinations are explained below."
            },
            style = MaterialTheme.typography.bodySmall
        )

        Text("Presentation", style = MaterialTheme.typography.titleMedium)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            IntervalPresentation.entries.forEach { presentation ->
                FilterChip(
                    selected = draft.presentation == presentation,
                    onClick = { viewModel.editDraft { it.copy(presentation = presentation) } },
                    label = { Text(presentation.setupLabel()) }
                )
            }
        }
        Text(draft.presentation.setupDescription(), style = MaterialTheme.typography.bodySmall)

        Text("Answer format", style = MaterialTheme.typography.titleMedium)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ContextualIntervalAnswerFormat.entries.forEach { format ->
                FilterChip(
                    selected = draft.answerFormat == format,
                    onClick = { viewModel.editDraft { it.copy(answerFormat = format) } },
                    label = { Text(format.chipLabel()) }
                )
            }
        }
        Text(draft.answerFormat.setupDescription(), style = MaterialTheme.typography.bodySmall)

        ChooserRow(
            label = "Context gap",
            options = CONTEXT_GAP_OPTIONS,
            selectedIndex = nearestIndex(
                CONTEXT_GAP_OPTIONS,
                draft.contextGap.inWholeMilliseconds.toInt()
            ),
            onSelected = { index ->
                viewModel.editDraft {
                    it.copy(
                        contextGap = kotlin.time.Duration.parse(
                            "${CONTEXT_GAP_OPTIONS[index]}ms"
                        )
                    )
                }
            },
            format = { millis ->
                "${formatSeconds(kotlin.time.Duration.parse("${millis}ms"))} s"
            }
        )
        Text(
            "The silence between the complete context and the first target note. It is included " +
                "in every example and in the exercise duration.",
            style = MaterialTheme.typography.bodySmall
        )

        Text("Range", style = MaterialTheme.typography.titleMedium)
        ChooserRow(
            label = "Lowest root note",
            options = NOTE_OPTIONS,
            selectedIndex = nearestIndex(NOTE_OPTIONS, draft.minimumNote),
            onSelected = { index ->
                val note = NOTE_OPTIONS[index]
                viewModel.editDraft {
                    it.copy(minimumNote = note, maximumNote = maxOf(note, it.maximumNote))
                }
            },
            format = { note -> MidiNote.of(note).displayName(spelling = naming) }
        )
        ChooserRow(
            label = "Highest root note",
            options = NOTE_OPTIONS,
            selectedIndex = nearestIndex(NOTE_OPTIONS, draft.maximumNote),
            onSelected = { index ->
                val note = NOTE_OPTIONS[index]
                viewModel.editDraft {
                    it.copy(maximumNote = note, minimumNote = minOf(note, it.minimumNote))
                }
            },
            format = { note -> MidiNote.of(note).displayName(spelling = naming) }
        )
        RootRangeReminder()
        Text(
            "Only the key tonic must be inside this range. Context and target notes may extend " +
                "outside it. An impossible key, context or interval combination is explained " +
                "below.",
            style = MaterialTheme.typography.bodySmall
        )

        Text("Learning mode", style = MaterialTheme.typography.titleMedium)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            RecipeMode.entries.forEach { mode ->
                FilterChip(
                    selected = draft.mode == mode,
                    onClick = { viewModel.editDraft { it.copy(mode = mode) } },
                    label = { Text(modeLabel(mode)) }
                )
            }
        }
        Text(modeDescription(draft.mode), style = MaterialTheme.typography.bodySmall)

        Text("Stopping", style = MaterialTheme.typography.titleMedium)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = draft.stopPolicy == SessionStopPolicy.UntilStopped,
                onClick = {
                    viewModel.editDraft { it.copy(stopPolicy = SessionStopPolicy.UntilStopped) }
                },
                label = { Text("Until stopped") }
            )
            FilterChip(
                selected = draft.stopPolicy == SessionStopPolicy.AfterCycle,
                onClick = {
                    viewModel.editDraft { it.copy(stopPolicy = SessionStopPolicy.AfterCycle) }
                },
                label = { Text("After one cycle") }
            )
            FilterChip(
                selected = draft.stopPolicy is SessionStopPolicy.CompletedItems,
                onClick = {
                    viewModel.editDraft {
                        it.copy(stopPolicy = SessionStopPolicy.CompletedItems(10))
                    }
                },
                label = { Text("After N items") }
            )
        }
        (draft.stopPolicy as? SessionStopPolicy.CompletedItems)?.let { stop ->
            ChooserRow(
                label = "Completed items",
                options = COMPLETED_OPTIONS,
                selectedIndex = nearestIndex(COMPLETED_OPTIONS, stop.count),
                onSelected = { index ->
                    viewModel.editDraft {
                        it.copy(
                            stopPolicy = SessionStopPolicy.CompletedItems(COMPLETED_OPTIONS[index])
                        )
                    }
                },
                format = { count -> count.toString() }
            )
        }

        ChooserRow(
            label = "Thinking time",
            options = THINKING_OPTIONS,
            selectedIndex = nearestIndex(
                THINKING_OPTIONS,
                draft.thinkingSilence.inWholeMilliseconds.toInt()
            ),
            onSelected = { index ->
                viewModel.editDraft {
                    it.copy(
                        thinkingSilence = kotlin.time.Duration.parse(
                            "${THINKING_OPTIONS[index]}ms"
                        )
                    )
                }
            },
            format = { millis ->
                "${formatSeconds(kotlin.time.Duration.parse("${millis}ms"))} s"
            }
        )
        ChooserRow(
            label = "Additional repeats after the answer",
            options = REPEAT_OPTIONS,
            selectedIndex = nearestIndex(REPEAT_OPTIONS, draft.replayCount),
            onSelected = { index ->
                viewModel.editDraft { it.copy(replayCount = REPEAT_OPTIONS[index]) }
            },
            format = { count -> count.toString() }
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("Separator cue before each example", modifier = Modifier.weight(1f))
            Switch(
                checked = draft.separatorCue,
                onCheckedChange = { enabled ->
                    viewModel.editDraft { it.copy(separatorCue = enabled) }
                }
            )
        }
        Text(
            "Plays a short two-note marker in the middle of the silence before the next example, " +
                "so it does not run into the context.",
            style = MaterialTheme.typography.bodySmall
        )

        HorizontalDivider()
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "Advanced",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f)
            )
        }

        run {
            ChooserRow(
                label = "Note length",
                options = NOTE_LENGTH_OPTIONS,
                selectedIndex = nearestIndex(
                    NOTE_LENGTH_OPTIONS,
                    draft.noteDuration.inWholeMilliseconds.toInt()
                ),
                onSelected = { index ->
                    viewModel.editDraft {
                        it.copy(
                            noteDuration = kotlin.time.Duration.parse(
                                "${NOTE_LENGTH_OPTIONS[index]}ms"
                            )
                        )
                    }
                },
                format = { millis -> "$millis ms" }
            )
            ChooserRow(
                label = "Gap between target notes",
                options = GAP_OPTIONS,
                selectedIndex = nearestIndex(
                    GAP_OPTIONS,
                    draft.melodicGap.inWholeMilliseconds.toInt()
                ),
                onSelected = { index ->
                    viewModel.editDraft {
                        it.copy(
                            melodicGap = kotlin.time.Duration.parse("${GAP_OPTIONS[index]}ms")
                        )
                    }
                },
                format = { millis -> "$millis ms" }
            )
            ChooserRow(
                label = "Gap after the answer",
                options = GAP_OPTIONS,
                selectedIndex = nearestIndex(
                    GAP_OPTIONS,
                    draft.answerGap.inWholeMilliseconds.toInt()
                ),
                onSelected = { index ->
                    viewModel.editDraft {
                        it.copy(
                            answerGap = kotlin.time.Duration.parse("${GAP_OPTIONS[index]}ms")
                        )
                    }
                },
                format = { millis -> "$millis ms" }
            )
            ChooserRow(
                label = "Gap between repeats",
                options = GAP_OPTIONS,
                selectedIndex = nearestIndex(
                    GAP_OPTIONS,
                    draft.repeatGap.inWholeMilliseconds.toInt()
                ),
                onSelected = { index ->
                    viewModel.editDraft {
                        it.copy(
                            repeatGap = kotlin.time.Duration.parse("${GAP_OPTIONS[index]}ms")
                        )
                    }
                },
                format = { millis -> "$millis ms" }
            )
            ChooserRow(
                label = "Final silence",
                options = FINAL_SILENCE_OPTIONS,
                selectedIndex = nearestIndex(
                    FINAL_SILENCE_OPTIONS,
                    draft.finalSilence.inWholeMilliseconds.toInt()
                ),
                onSelected = { index ->
                    viewModel.editDraft {
                        it.copy(
                            finalSilence = kotlin.time.Duration.parse(
                                "${FINAL_SILENCE_OPTIONS[index]}ms"
                            )
                        )
                    }
                },
                format = { millis ->
                    "${formatSeconds(kotlin.time.Duration.parse("${millis}ms"))} s"
                }
            )
        }

        val invalid = state.validation as? SetupValidation.Invalid
        if (invalid != null) {
            Card {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        "Fix these to apply the changes",
                        style = MaterialTheme.typography.titleMedium
                    )
                    invalid.byField.forEach { (field, explanations) ->
                        Text("${fieldLabel(field)}: ${explanations.joinToString(" ")}")
                    }
                }
            }
        }

        state.saveError?.let {
            Text("Could not save: $it", color = MaterialTheme.colorScheme.error)
        }

        if (state.saved) {
            Text(
                "Saved automatically. A running session applies the changes from the next " +
                    "exercise; start a new one from the Play tab.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/** Final-silence choices, 0 to 4 seconds in quarter seconds. */
private val FINAL_SILENCE_OPTIONS: List<Int> = (0..4000 step 250).toList()

/** Context-gap choices: 0 to the 30 s maximum in quarter seconds, including the 500 ms default. */
private val CONTEXT_GAP_OPTIONS: List<Int> = (0..30_000 step 250).toList()

/**
 * Toggles one simple interval in a draft, keeping catalog (semitone) order.
 *
 * An id this build does not know is preserved rather than silently dropped, so a stored draft the
 * validator is explaining does not become a different draft when a chip is touched.
 */
private fun ContextualIntervalSetupDraft.toggleInterval(
    interval: SimpleInterval
): ContextualIntervalSetupDraft {
    val known = intervalIds.filter { SimpleInterval.ofIdOrNull(it) != null }.toSet()
    val next = if (interval.id in known) known - interval.id else known + interval.id
    val ordered = SimpleInterval.ALL.filter { it.id in next }.map { it.id }
    val unknown = intervalIds.filter { SimpleInterval.ofIdOrNull(it) == null }
    return copy(intervalIds = ordered + unknown)
}

/** The presentation chip title. */
internal fun IntervalPresentation.setupLabel(): String = when (this) {
    IntervalPresentation.ASCENDING -> "Ascending"
    IntervalPresentation.DESCENDING -> "Descending"
    IntervalPresentation.HARMONIC -> "Harmonic"
}

/**
 * One line that explains what the selected presentation plays (M9B.1).
 *
 * Ascending and descending enforce their direction; a unison is the one case where both target
 * notes are the same pitch. The harmonic layout is ordered low to high and a unison sounds once.
 */
internal fun IntervalPresentation.setupDescription(): String = when (this) {
    IntervalPresentation.ASCENDING ->
        "The two target notes sound in order, lower first, then higher; a unison is the same " +
            "note twice."
    IntervalPresentation.DESCENDING ->
        "The two target notes sound in order, higher first, then lower; a unison is the same " +
            "note twice."
    IntervalPresentation.HARMONIC ->
        "Both target notes sound together, ordered low to high; a unison sounds once."
}

/** The answer-format chip title. */
internal fun ContextualIntervalAnswerFormat.chipLabel(): String = when (this) {
    ContextualIntervalAnswerFormat.INTERVAL -> "Interval"
    ContextualIntervalAnswerFormat.DEGREE_PAIR -> "Degree pair"
    ContextualIntervalAnswerFormat.INTERVAL_AND_DEGREES -> "Interval and degrees"
}

/** One line that explains what the selected answer speaks (M9B.2). */
internal fun ContextualIntervalAnswerFormat.setupDescription(): String = when (this) {
    ContextualIntervalAnswerFormat.INTERVAL ->
        "Speaks the interval phrase alone, for example \u201Cminor third\u201D."
    ContextualIntervalAnswerFormat.DEGREE_PAIR ->
        "Speaks the two degree numbers in presented order, for example \u201Cone five\u201D."
    ContextualIntervalAnswerFormat.INTERVAL_AND_DEGREES ->
        "Speaks the interval phrase followed by both degree numbers, for example \u201Cminor " +
            "third one five\u201D."
}

private class ContextualIntervalSetupViewModelFactory(
    private val settingsRepository: SettingsRepository,
    private val saveScope: kotlinx.coroutines.CoroutineScope?
) : androidx.lifecycle.ViewModelProvider.Factory {
    override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
        @Suppress("UNCHECKED_CAST")
        return ContextualIntervalSetupViewModel(settingsRepository, saveScope = saveScope) as T
    }
}
