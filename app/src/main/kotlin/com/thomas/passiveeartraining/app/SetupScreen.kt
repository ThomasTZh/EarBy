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
import androidx.compose.runtime.remember
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
import com.thomas.passiveeartraining.training.recipe.RecipeMode
import com.thomas.passiveeartraining.training.recipe.SessionStopPolicy
import com.thomas.passiveeartraining.training.settings.SettingsRepository
import com.thomas.passiveeartraining.training.settings.SetupDraft
import com.thomas.passiveeartraining.training.settings.SetupField
import com.thomas.passiveeartraining.training.settings.SetupValidation
import com.thomas.passiveeartraining.training.settings.SetupValidator

/**
 * The interval setup form.
 *
 * The basic controls are the product's everyday choices; the advanced panel holds the constrained
 * musical settings. Every value is checked by the shared domain validator, and all problems are
 * listed at once with the field they belong to.
 */
@Composable
internal fun SetupScreen(settingsRepository: SettingsRepository) {
    val viewModel: SetupViewModel = viewModel(
        factory = SetupViewModelFactory(
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
        Text("Interval setup", style = MaterialTheme.typography.headlineMedium)

        if (!state.loaded) {
            CircularProgressIndicator()
            return@Column
        }

        val draft = state.draft

        Text("Intervals", style = MaterialTheme.typography.titleMedium)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SimpleInterval.ALL.forEach { interval ->
                FilterChip(
                    selected = interval.id in draft.intervalIds,
                    onClick = {
                        viewModel.editDraft { current ->
                            current.copy(
                                intervalIds = if (interval.id in current.intervalIds) {
                                    current.intervalIds - interval.id
                                } else {
                                    current.intervalIds + interval.id
                                }
                            ).withFeasibleFixedRoot()
                        }
                    },
                    label = { Text(interval.englishAnswer) }
                )
            }
        }
        if (draft.intervalIds.isEmpty()) {
            Text(
                "No interval selected means every interval is included.",
                style = MaterialTheme.typography.bodySmall
            )
        }

        Text("Presentation", style = MaterialTheme.typography.titleMedium)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            val randomUpDown = draft.enabledPresentations.size > 1
            IntervalPresentation.entries.forEach { presentation ->
                FilterChip(
                    selected = !randomUpDown && draft.presentation == presentation,
                    onClick = {
                        // A single choice replaces any mixed set, keeping the version 1 draw order.
                        viewModel.editDraft {
                            it.copy(presentation = presentation, presentations = null)
                                .withFeasibleFixedRoot()
                        }
                    },
                    label = { Text(presentation.name.lowercase()) }
                )
            }
            FilterChip(
                selected = randomUpDown,
                onClick = {
                    // Mixed presentation draws one direction per item; the policy becomes version 2.
                    viewModel.editDraft {
                        it.copy(
                            presentation = IntervalPresentation.ASCENDING,
                            presentations = listOf(
                                IntervalPresentation.ASCENDING,
                                IntervalPresentation.DESCENDING
                            )
                        ).withFeasibleFixedRoot()
                    }
                },
                label = { Text("random up/down") }
            )
        }

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

        Text("Root note", style = MaterialTheme.typography.titleMedium)
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("Fix the root note", modifier = Modifier.weight(1f))
            Switch(
                checked = draft.fixedRoot != null,
                onCheckedChange = { fixed ->
                    viewModel.editDraft {
                        it.copy(
                            fixedRoot = if (fixed) preferredFixedRoot(it) else null
                        )
                    }
                }
            )
        }
        draft.fixedRoot?.let { root ->
            val options = remember(
                draft.intervalIds,
                draft.rootValues,
                draft.minimumNote,
                draft.maximumNote,
                draft.presentation,
                draft.presentations,
                draft.fixedRoot
            ) {
                SetupValidator.feasibleFixedRoots(draft)
                    .ifEmpty { (draft.minimumNote..draft.maximumNote).toList() }
            }
            ChooserRow(
                label = "Fixed note",
                options = options,
                selectedIndex = nearestIndex(
                    options,
                    root.coerceIn(draft.minimumNote, draft.maximumNote)
                ),
                onSelected = { index ->
                    viewModel.editDraft { it.copy(fixedRoot = options[index]) }
                },
                format = { note -> MidiNote.of(note).displayName() }
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // The label wraps before the switch, so a large font scale cannot push it off screen.
            Text("Separator cue before each pair", modifier = Modifier.weight(1f))
            Switch(
                checked = draft.separatorCue,
                onCheckedChange = { enabled ->
                    viewModel.editDraft { it.copy(separatorCue = enabled) }
                }
            )
        }
        Text(
            "Plays a short two-note marker in the middle of the silence before the next pair, so " +
                "it does not run into the first note.",
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
            Text("Root note classes", style = MaterialTheme.typography.titleMedium)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                (0 until PitchClass.COUNT).forEach { value ->
                    FilterChip(
                        selected = value in draft.rootValues,
                        onClick = {
                            viewModel.editDraft { current ->
                                current.copy(
                                    rootValues = if (value in current.rootValues) {
                                        current.rootValues - value
                                    } else {
                                        (current.rootValues + value).sorted()
                                    }
                                ).withFeasibleFixedRoot()
                            }
                        },
                        label = { Text(PitchClass.of(value).displayName()) }
                    )
                }
            }
            if (draft.rootValues.isEmpty()) {
                Text(
                    "No root class selected means every root class is included.",
                    style = MaterialTheme.typography.bodySmall
                )
            }

            ChooserRow(
                label = "Lowest root note",
                options = NOTE_OPTIONS,
                selectedIndex = nearestIndex(NOTE_OPTIONS, draft.minimumNote),
                onSelected = { index ->
                    val note = NOTE_OPTIONS[index]
                    viewModel.editDraft {
                        it.copy(minimumNote = note, maximumNote = maxOf(note, it.maximumNote))
                            .withFeasibleFixedRoot()
                    }
                },
                format = { note -> MidiNote.of(note).displayName() }
            )
            ChooserRow(
                label = "Highest root note",
                options = NOTE_OPTIONS,
                selectedIndex = nearestIndex(NOTE_OPTIONS, draft.maximumNote),
                onSelected = { index ->
                    val note = NOTE_OPTIONS[index]
                    viewModel.editDraft {
                        it.copy(maximumNote = note, minimumNote = minOf(note, it.minimumNote))
                            .withFeasibleFixedRoot()
                    }
                },
                format = { note -> MidiNote.of(note).displayName() }
            )
            RootRangeReminder()

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
                "Saved automatically. A running session applies the changes from the next pair; " +
                    "start a new one from the Play tab.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private const val ADVANCED_LOW = 21
private const val ADVANCED_HIGH = 108

internal fun modeLabel(mode: RecipeMode): String = mode.name
    .lowercase()
    .replace('_', ' ')
    .replaceFirstChar { it.uppercaseChar() }

/** One plain sentence that explains what the selected learning mode does. */
internal fun modeDescription(mode: RecipeMode): String = when (mode) {
    RecipeMode.RECOGNITION ->
        "You hear the example, then a thinking pause, then the spoken answer. Repeats add more " +
            "examples after the answer."
    RecipeMode.EXPOSURE ->
        "The answer comes first, then a gap, then the example. Repeats add more examples."
    RecipeMode.ASSOCIATION ->
        "The example, a thinking pause, the answer, then the example again, so you link the " +
            "sound and its name. Repeats add more examples after the answer."
}

/** Formats a duration with two decimals, for thinking-pause values like 2.25 s. */
internal fun formatSeconds(duration: kotlin.time.Duration): String {
    val hundredths = duration.inWholeMilliseconds / 10L
    val whole = hundredths / 100L
    val fraction = (hundredths % 100L).toString().padStart(2, '0')
    return "$whole.$fraction"
}

internal fun fieldLabel(field: SetupField): String = field.name
    .lowercase()
    .replace('_', ' ')
    .replaceFirstChar { it.uppercaseChar() }

/** Thinking-pause choices, 0 to 4 seconds in quarter seconds. */
internal val THINKING_OPTIONS: List<Int> = (0..4000 step 250).toList()

/** Additional repeats after the answer, 0 to 8. */
internal val REPEAT_OPTIONS: List<Int> = (0..8).toList()

/** Every playable semitone in the advanced range. */
internal val NOTE_OPTIONS: List<Int> = (ADVANCED_LOW..ADVANCED_HIGH).toList()

/** Shared explanation of the root/tonic range rule for multi-note modes. */
@Composable
internal fun RootRangeReminder() {
    Text(
        "This range restricts root notes only. Other notes may sound outside it, but always " +
            "within the instrument's A0-C8 samples. Roots that cannot fit are skipped; a fixed " +
            "root that no longer fits is cleared.",
        style = MaterialTheme.typography.bodySmall
    )
}

/** One note's sounding length, 300 ms to 2 s. */
internal val NOTE_LENGTH_OPTIONS: List<Int> = (300..2000 step 100).toList()

/** A gap inside an exercise, 0 to 1.5 s. */
internal val GAP_OPTIONS: List<Int> = (0..1500 step 100).toList()

/** Completed items for an "after N items" stop policy. */
internal val COMPLETED_OPTIONS: List<Int> = (1..50).toList()

/** The option closest to a stored value, so an old value still selects a visible choice. */
internal fun nearestIndex(options: List<Int>, value: Int): Int =
    options.indices.minByOrNull { kotlin.math.abs(options[it] - value) } ?: 0

/**
 * The fixed root the switch selects: the feasible note nearest the middle of the range.
 *
 * Choosing the lowest note, as the first switch-on used to, often cannot play a descending example
 * at all; a middle note can, in every presentation.
 */
private fun preferredFixedRoot(setup: SetupDraft): Int {
    val middle = (setup.minimumNote + setup.maximumNote) / 2
    val options = SetupValidator.feasibleFixedRoots(setup.copy(fixedRoot = middle))
    return options.minByOrNull { kotlin.math.abs(it - middle) } ?: middle
}

/** Clears a fixed root that no longer fits; the range warning explains it. */
private fun SetupDraft.withFeasibleFixedRoot(): SetupDraft {
    val root = fixedRoot ?: return this
    val options = SetupValidator.feasibleFixedRoots(this)
    return if (root in options) this else copy(fixedRoot = null)
}

private class SetupViewModelFactory(
    private val settingsRepository: SettingsRepository,
    private val saveScope: kotlinx.coroutines.CoroutineScope?
) : androidx.lifecycle.ViewModelProvider.Factory {
    override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
        @Suppress("UNCHECKED_CAST")
        return SetupViewModel(settingsRepository, saveScope = saveScope) as T
    }
}
