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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.thomas.passiveeartraining.music.MidiNote
import com.thomas.passiveeartraining.music.PitchClass
import com.thomas.passiveeartraining.music.SimpleInterval
import com.thomas.passiveeartraining.training.WalkDirection
import com.thomas.passiveeartraining.training.recipe.RecipeMode
import com.thomas.passiveeartraining.training.recipe.SessionStopPolicy
import com.thomas.passiveeartraining.training.settings.IntervalWalkSetup
import com.thomas.passiveeartraining.training.settings.IntervalWalkSetupDraft
import com.thomas.passiveeartraining.training.settings.SettingsRepository
import com.thomas.passiveeartraining.training.settings.SetupValidation

/** The interval-walk setup form. */
@Composable
internal fun IntervalWalkSetupScreen(settingsRepository: SettingsRepository) {
    val viewModel: IntervalWalkSetupViewModel = viewModel(
        factory = IntervalWalkSetupViewModelFactory(
            settingsRepository,
            (LocalContext.current.applicationContext as? PassiveEarTrainingApp)?.settingsWriteScope
        )
    )
    val state by viewModel.state.collectAsStateWithLifecycle()

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
        Text("Interval walk setup", style = MaterialTheme.typography.headlineMedium)
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
                            val next = if (interval.id in current.intervalIds) {
                                current.intervalIds - interval.id
                            } else {
                                current.intervalIds + interval.id
                            }
                            current.copy(
                                intervalIds = SimpleInterval.ALL.map { it.id }
                                    .filter { it in next }
                            )
                        }
                    },
                    label = { Text(interval.englishAnswer) }
                )
            }
        }
        if (draft.intervalIds.isEmpty()) {
            Text(
                "Select at least one interval; the walk needs a step.",
                style = MaterialTheme.typography.bodySmall
            )
        }

        Text("Direction", style = MaterialTheme.typography.titleMedium)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            WalkDirection.entries.forEach { direction ->
                FilterChip(
                    selected = draft.direction == direction,
                    onClick = { viewModel.editDraft { it.copy(direction = direction) } },
                    label = {
                        Text(direction.name.lowercase().replaceFirstChar(Char::uppercaseChar))
                    }
                )
            }
        }

        ChooserRow(
            label = "Steps in the walk",
            options = (2..8).toList(),
            selectedIndex = (draft.length - 2).coerceIn(0, 6),
            onSelected = { index -> viewModel.editDraft { it.copy(length = 2 + index) } },
            format = { count -> "$count steps" }
        )

        Text("Starting root", style = MaterialTheme.typography.titleMedium)
        RootRangeReminder()
        ChooserRow(
            label = "Lowest starting root",
            options = NOTE_OPTIONS,
            selectedIndex = nearestIndex(NOTE_OPTIONS, draft.minimumNote.value),
            onSelected = { index ->
                val note = NOTE_OPTIONS[index]
                viewModel.editDraft {
                    it.copy(
                        minimumNote = MidiNote.of(note),
                        maximumNote = MidiNote.of(
                            maxOf(note, it.maximumNote.value)
                        )
                    ).clearFixedStartOutsideRange()
                }
            },
            format = { note -> MidiNote.of(note).displayName() }
        )
        ChooserRow(
            label = "Highest starting root",
            options = NOTE_OPTIONS,
            selectedIndex = nearestIndex(NOTE_OPTIONS, draft.maximumNote.value),
            onSelected = { index ->
                val note = NOTE_OPTIONS[index]
                viewModel.editDraft {
                    it.copy(
                        maximumNote = MidiNote.of(note),
                        minimumNote = MidiNote.of(
                            minOf(note, it.minimumNote.value)
                        )
                    ).clearFixedStartOutsideRange()
                }
            },
            format = { note -> MidiNote.of(note).displayName() }
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("Fix the starting root", modifier = Modifier.weight(1f))
            Switch(
                checked = draft.fixedStart != null,
                onCheckedChange = { fixed ->
                    viewModel.editDraft {
                        if (!fixed) {
                            it.copy(fixedStart = null)
                        } else {
                            it.copy(
                                fixedStart = MidiNote.of(
                                    it.minimumNote.value.coerceAtLeast(21)
                                )
                            )
                        }
                    }
                }
            )
        }
        draft.fixedStart?.let { fixed ->
            val options = remember(draft.minimumNote, draft.maximumNote, draft.rootValues) {
                (draft.minimumNote.value..draft.maximumNote.value)
                    .filter { value ->
                        draft.rootValues.isEmpty() ||
                            value.mod(PitchClass.COUNT) in draft.rootValues
                    }
                    .ifEmpty { (draft.minimumNote.value..draft.maximumNote.value).toList() }
            }
            ChooserRow(
                label = "Fixed starting root",
                options = options,
                selectedIndex = nearestIndex(
                    options,
                    fixed.value.coerceIn(draft.minimumNote.value, draft.maximumNote.value)
                ),
                onSelected = { index ->
                    viewModel.editDraft { it.copy(fixedStart = MidiNote.of(options[index])) }
                },
                format = { note -> MidiNote.of(note).displayName() }
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

        Text("Advanced", style = MaterialTheme.typography.titleMedium)
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
            label = "Gap between walk notes",
            options = GAP_OPTIONS,
            selectedIndex = nearestIndex(
                GAP_OPTIONS,
                draft.melodicGap.inWholeMilliseconds.toInt()
            ),
            onSelected = { index ->
                viewModel.editDraft {
                    it.copy(melodicGap = kotlin.time.Duration.parse("${GAP_OPTIONS[index]}ms"))
                }
            },
            format = { millis -> "$millis ms" }
        )
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
            format = { millis -> "$millis ms" }
        )
        ChooserRow(
            label = "Gap after the answer",
            options = GAP_OPTIONS,
            selectedIndex = nearestIndex(GAP_OPTIONS, draft.answerGap.inWholeMilliseconds.toInt()),
            onSelected = { index ->
                viewModel.editDraft {
                    it.copy(answerGap = kotlin.time.Duration.parse("${GAP_OPTIONS[index]}ms"))
                }
            },
            format = { millis -> "$millis ms" }
        )
        ChooserRow(
            label = "Gap between repeats",
            options = GAP_OPTIONS,
            selectedIndex = nearestIndex(GAP_OPTIONS, draft.repeatGap.inWholeMilliseconds.toInt()),
            onSelected = { index ->
                viewModel.editDraft {
                    it.copy(repeatGap = kotlin.time.Duration.parse("${GAP_OPTIONS[index]}ms"))
                }
            },
            format = { millis -> "$millis ms" }
        )
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
                            stopPolicy = SessionStopPolicy.CompletedItems(
                                COMPLETED_OPTIONS[index]
                            )
                        )
                    }
                },
                format = { count -> count.toString() }
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("Separator cue before each walk", modifier = Modifier.weight(1f))
            Switch(
                checked = draft.separatorCue,
                onCheckedChange = { enabled ->
                    viewModel.editDraft { it.copy(separatorCue = enabled) }
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
        Text(
            "Only the starting root is restricted by this range. The walk may continue outside " +
                "it, but every sounding note stays inside A0-C8. A stored setup is saved " +
                "automatically when it is valid.",
            style = MaterialTheme.typography.bodySmall
        )
    }
}

private fun IntervalWalkSetupDraft.clearFixedStartOutsideRange(): IntervalWalkSetupDraft {
    val start = fixedStart ?: return this
    val inRange = start.value in minimumNote.value..maximumNote.value
    val rootAllowed = rootValues.isEmpty() || start.pitchClass.value in rootValues
    return if (inRange && rootAllowed) this else copy(fixedStart = null)
}

private class IntervalWalkSetupViewModelFactory(
    private val settingsRepository: SettingsRepository,
    private val saveScope: kotlinx.coroutines.CoroutineScope?
) : androidx.lifecycle.ViewModelProvider.Factory {
    override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
        @Suppress("UNCHECKED_CAST")
        return IntervalWalkSetupViewModel(settingsRepository, saveScope = saveScope) as T
    }
}
