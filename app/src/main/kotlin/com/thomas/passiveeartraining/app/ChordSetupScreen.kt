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
import com.thomas.passiveeartraining.music.ChordInversions
import com.thomas.passiveeartraining.music.ChordPresentation
import com.thomas.passiveeartraining.music.ChordQuality
import com.thomas.passiveeartraining.music.MidiNote
import com.thomas.passiveeartraining.music.PitchClass
import com.thomas.passiveeartraining.training.ChordAnswerMode
import com.thomas.passiveeartraining.training.recipe.RecipeMode
import com.thomas.passiveeartraining.training.recipe.SessionStopPolicy
import com.thomas.passiveeartraining.training.settings.ChordSetup
import com.thomas.passiveeartraining.training.settings.ChordSetupDraft
import com.thomas.passiveeartraining.training.settings.ChordSetupValidator
import com.thomas.passiveeartraining.training.settings.SettingsRepository
import com.thomas.passiveeartraining.training.settings.SetupField
import com.thomas.passiveeartraining.training.settings.SetupValidation

/**
 * The chord setup form (M5).
 *
 * The controls mirror the interval screen: the everyday choices first, the constrained musical
 * settings in the advanced panel, and every value checked by the shared domain validator with all
 * problems listed at once. An inapplicable inversion is disabled where the chip can express it and
 * explained in words where the draft already holds a stale selection.
 */
@Composable
internal fun ChordSetupScreen(settingsRepository: SettingsRepository) {
    val viewModel: ChordSetupViewModel = viewModel(
        factory = ChordSetupViewModelFactory(
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
        Text("Chord setup", style = MaterialTheme.typography.headlineMedium)

        if (!state.loaded) {
            CircularProgressIndicator()
            return@Column
        }

        val draft = state.draft

        Text("Chord qualities", style = MaterialTheme.typography.titleMedium)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ChordQuality.entries.forEach { quality ->
                FilterChip(
                    selected = quality.id in draft.qualityIds,
                    onClick = {
                        viewModel.editDraft { current ->
                            val qualities = if (quality.id in current.qualityIds) {
                                current.qualityIds - quality.id
                            } else {
                                current.qualityIds + quality.id
                            }
                            // Normalize to catalog order so a saved round-trip stays identical.
                            current.copy(
                                qualityIds = ChordQuality.entries
                                    .filter { it.id in qualities }
                                    .map { it.id }
                            ).withFeasibleChordFixedRoot()
                        }
                    },
                    label = { Text(quality.englishAnswer) }
                )
            }
        }
        if (draft.qualityIds.isEmpty()) {
            Text(
                "Select at least one chord quality; an empty selection cannot be played.",
                style = MaterialTheme.typography.bodySmall
            )
        }

        Text("Inversions", style = MaterialTheme.typography.titleMedium)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            (0..3).forEach { inversion ->
                val selected = inversion in draft.inversions
                val applicable = chordInversionAppliesToSelection(draft, inversion)
                FilterChip(
                    selected = selected,
                    // An inapplicable choice cannot be picked; a stale selection stays clickable
                    // so the draft can be corrected after its quality was removed.
                    enabled = applicable || selected,
                    onClick = {
                        viewModel.editDraft { current ->
                            current.copy(
                                inversions = if (inversion in current.inversions) {
                                    current.inversions - inversion
                                } else {
                                    (current.inversions + inversion).sorted()
                                }
                            ).withFeasibleChordFixedRoot()
                        }
                    },
                    label = { Text(ChordInversions.answer(inversion)) }
                )
            }
        }
        Text(
            "Root position, first and second inversion apply to every quality. Third inversion " +
                "applies only to seventh chords.",
            style = MaterialTheme.typography.bodySmall
        )
        inversionExplanations(draft).forEach {
            Text(it, style = MaterialTheme.typography.bodySmall)
        }

        Text("Presentation", style = MaterialTheme.typography.titleMedium)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ChordPresentation.entries.forEach { presentation ->
                FilterChip(
                    selected = draft.presentation == presentation,
                    onClick = {
                        viewModel.editDraft {
                            it.copy(presentation = presentation).withFeasibleChordFixedRoot()
                        }
                    },
                    label = { Text(presentation.englishLabel()) }
                )
            }
        }

        Text("Answer format", style = MaterialTheme.typography.titleMedium)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ChordAnswerMode.entries.forEach { answerMode ->
                FilterChip(
                    selected = draft.answerMode == answerMode,
                    onClick = { viewModel.editDraft { it.copy(answerMode = answerMode) } },
                    label = { Text(answerMode.englishLabel()) }
                )
            }
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
                            fixedRoot = if (fixed) preferredChordFixedRoot(it) else null
                        )
                    }
                }
            )
        }
        draft.fixedRoot?.let { root ->
            val options = remember(
                draft.qualityIds,
                draft.inversions,
                draft.rootValues,
                draft.minimumNote,
                draft.maximumNote,
                draft.presentation,
                draft.fixedRoot
            ) {
                // An empty feasibility list means the whole selection is unusable; the validator
                // explains it and the picker still offers the range so a draft stays editable.
                ChordSetupValidator.feasibleFixedRoots(draft)
                    .ifEmpty { (draft.minimumNote..draft.maximumNote).toList() }
            }
            ChooserRow(
                label = "Fixed root",
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
            Text(
                "Only roots that can play every selected chord in this range are offered. " +
                    "With no fixed root a root class is drawn each time.",
                style = MaterialTheme.typography.bodySmall
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // The label wraps before the switch, so a large font scale cannot push it off screen.
            Text("Separator cue before each chord", modifier = Modifier.weight(1f))
            Switch(
                checked = draft.separatorCue,
                onCheckedChange = { enabled ->
                    viewModel.editDraft { it.copy(separatorCue = enabled) }
                }
            )
        }
        Text(
            "Plays a short two-note marker in the middle of the silence before the next chord, " +
                "so it does not run into the first note.",
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
                                ).withFeasibleChordFixedRoot()
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
                            .withFeasibleChordFixedRoot()
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
                            .withFeasibleChordFixedRoot()
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
            if (draft.presentation == ChordPresentation.ARPEGGIO) {
                ChooserRow(
                    label = "Gap between arpeggio notes",
                    options = GAP_OPTIONS,
                    selectedIndex = nearestIndex(
                        GAP_OPTIONS,
                        draft.melodicGap.inWholeMilliseconds.toInt()
                    ),
                    onSelected = { index ->
                        viewModel.editDraft {
                            it.copy(
                                melodicGap = kotlin.time.Duration.parse(
                                    "${GAP_OPTIONS[index]}ms"
                                )
                            )
                        }
                    },
                    format = { millis -> "$millis ms" }
                )
            }
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

/** The selected qualities in catalog order, skipping ids a draft could no longer resolve. */
private fun selectedChordQualities(draft: ChordSetupDraft): List<ChordQuality> = draft.qualityIds
    .mapNotNull { ChordQuality.ofIdOrNull(it) }
    .distinct()
    .sortedBy { it.ordinal }

/** True when at least one selected quality can produce [inversion]. */
internal fun chordInversionAppliesToSelection(draft: ChordSetupDraft, inversion: Int): Boolean =
    selectedChordQualities(draft).any { inversion <= it.maximumInversion }

/**
 * Explains inversion states the chips cannot show by themselves (M5.3).
 *
 * A disabled chip says an inversion applies to no selected quality; a stale selection (its
 * quality was deselected afterwards) and a selected quality with no applicable selected inversion
 * are real but invalid draft states, explained rather than silently repaired.
 */
internal fun inversionExplanations(draft: ChordSetupDraft): List<String> {
    val qualities = selectedChordQualities(draft)
    val notes = mutableListOf<String>()
    draft.inversions
        .filter { inversion -> qualities.none { inversion <= it.maximumInversion } }
        .forEach { inversion ->
            notes += "${ChordInversions.answer(inversion).capitalizeFirst()} does not apply to " +
                "any selected quality; remove it or add a quality it fits."
        }
    qualities
        .filter { quality -> draft.inversions.none { it <= quality.maximumInversion } }
        .forEach { quality ->
            val applicable = (0..quality.maximumInversion)
                .joinToString { ChordInversions.answer(it) }
            notes += "${quality.englishAnswer.capitalizeFirst()} has no applicable inversion " +
                "selected; add one of $applicable."
        }
    if (notes.isEmpty()) {
        when {
            qualities.isEmpty() ->
                notes += "Select a quality to enable its inversions."
            !chordInversionAppliesToSelection(draft, 3) ->
                notes += "Third inversion is unavailable: no selected quality is a seventh chord."
        }
    }
    return notes
}

private fun String.capitalizeFirst(): String = replaceFirstChar { it.uppercaseChar() }

/**
 * The fixed root the switch selects: the feasible note nearest the middle of the range.
 *
 * A middle note is playable for every presentation, where an extreme one often is not.
 */
private fun preferredChordFixedRoot(draft: ChordSetupDraft): Int {
    val middle = (draft.minimumNote + draft.maximumNote) / 2
    val options = ChordSetupValidator.feasibleFixedRoots(draft.copy(fixedRoot = middle))
    return options.minByOrNull { kotlin.math.abs(it - middle) } ?: middle
}

/** Clears a fixed root that no longer fits; the range warning explains it. */
private fun ChordSetupDraft.withFeasibleChordFixedRoot(): ChordSetupDraft {
    val root = fixedRoot ?: return this
    val options = ChordSetupValidator.feasibleFixedRoots(this)
    return if (root in options) this else copy(fixedRoot = null)
}

private class ChordSetupViewModelFactory(
    private val settingsRepository: SettingsRepository,
    private val saveScope: kotlinx.coroutines.CoroutineScope?
) : androidx.lifecycle.ViewModelProvider.Factory {
    override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
        @Suppress("UNCHECKED_CAST")
        return ChordSetupViewModel(settingsRepository, saveScope = saveScope) as T
    }
}
