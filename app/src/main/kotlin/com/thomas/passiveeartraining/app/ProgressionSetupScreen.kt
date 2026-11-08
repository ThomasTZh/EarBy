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
import com.thomas.passiveeartraining.music.DiatonicChord
import com.thomas.passiveeartraining.music.MidiNote
import com.thomas.passiveeartraining.music.PitchClass
import com.thomas.passiveeartraining.music.ProgressionVoicingMode
import com.thomas.passiveeartraining.training.ProgressionAnswerFormat
import com.thomas.passiveeartraining.training.recipe.RecipeMode
import com.thomas.passiveeartraining.training.recipe.SessionStopPolicy
import com.thomas.passiveeartraining.training.settings.ProgressionSetupDraft
import com.thomas.passiveeartraining.training.settings.ProgressionSetupValidator
import com.thomas.passiveeartraining.training.settings.SettingsRepository
import com.thomas.passiveeartraining.training.settings.SetupValidation

/**
 * The chord-progression setup form (M9B).
 *
 * The controls mirror the scale-degree screen: the everyday musical choices first, the shared
 * recipe controls next, and every value checked by the shared domain validator with all problems
 * listed at once. There is no key selection: every key is used unless the fixed-root switch pins
 * the tonic MIDI note. The chord grid is the fourteen-chord major-key pool (seven triads and their
 * seventh chords) for the three drawn slots, the opening I is fixed, and the voicing picker only
 * offers inversions when the nearest-inversion search uses them. The validator attaches an
 * impossible selection or range to the responsible field.
 */
@Composable
internal fun ProgressionSetupScreen(settingsRepository: SettingsRepository) {
    val viewModel: ProgressionSetupViewModel = viewModel(
        factory = ProgressionSetupViewModelFactory(
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
        Text("Chord progression setup", style = MaterialTheme.typography.headlineMedium)

        if (!state.loaded) {
            CircularProgressIndicator()
            return@Column
        }

        val draft = state.draft
        // The setup's own sharp/flat table spells the key tonics and every range readout;
        // progression answers never speak note names.
        val naming = draft.spelling

        Text("Chords", style = MaterialTheme.typography.titleMedium)
        Text(
            "Every exercise opens on the tonic I. The next three chords are drawn from these " +
                "in random order; a chord may repeat.",
            style = MaterialTheme.typography.bodySmall
        )
        Text("Triads", style = MaterialTheme.typography.labelLarge)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            DiatonicChord.TRIADS.forEach { chord ->
                FilterChip(
                    selected = chord.id in draft.chordIds,
                    onClick = { viewModel.editDraft { it.toggleChord(chord) } },
                    label = { Text(chord.romanLabel) }
                )
            }
        }
        Text("Seventh chords", style = MaterialTheme.typography.labelLarge)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            DiatonicChord.SEVENTHS.forEach { chord ->
                FilterChip(
                    selected = chord.id in draft.chordIds,
                    onClick = { viewModel.editDraft { it.toggleChord(chord) } },
                    label = { Text(chord.romanLabel) }
                )
            }
        }
        Text(
            "Select the chords for the three drawn slots; the opening I is always played. The " +
                "whole set must fit the range under the chosen voicing.",
            style = MaterialTheme.typography.bodySmall
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
                            fixedRoot = if (fixed) preferredProgressionFixedRoot(it) else null
                        )
                    }
                }
            )
        }
        if (draft.fixedRoot == null) {
            Text(
                "All twelve keys are used unless a fixed root pins one.",
                style = MaterialTheme.typography.bodySmall
            )
        }
        draft.fixedRoot?.let { root ->
            val options = remember(
                draft.chordIds,
                draft.voicing,
                draft.allowedInversions,
                draft.minimumNote,
                draft.maximumNote,
                draft.fixedRoot
            ) {
                // An empty feasibility list means the whole pool is unusable; the validator
                // explains it and the picker still offers the range so a draft stays editable.
                feasibleProgressionFixedRoots(draft)
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
                "Only roots that can play the whole selected pool in this range are offered. " +
                    "With no fixed root a key and anchor are drawn each time.",
                style = MaterialTheme.typography.bodySmall
            )
        }

        Text("Voicing", style = MaterialTheme.typography.titleMedium)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ProgressionVoicingMode.entries.forEach { mode ->
                FilterChip(
                    selected = draft.voicing == mode,
                    onClick = { viewModel.editDraft { it.copy(voicing = mode) } },
                    label = { Text(mode.chipLabel()) }
                )
            }
        }
        Text(draft.voicing.setupDescription(), style = MaterialTheme.typography.bodySmall)

        if (draft.voicing == ProgressionVoicingMode.NEAREST_INVERSION) {
            Text("Allowed inversions", style = MaterialTheme.typography.titleMedium)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                (0..3).forEach { inversion ->
                    FilterChip(
                        selected = inversion in draft.allowedInversions,
                        onClick = {
                            viewModel.editDraft { current ->
                                current.copy(
                                    allowedInversions =
                                    if (inversion in current.allowedInversions) {
                                        current.allowedInversions - inversion
                                    } else {
                                        (current.allowedInversions + inversion).sorted()
                                    }
                                )
                            }
                        },
                        label = { Text(inversion.toString()) }
                    )
                }
            }
            Text(
                "0 is root position, 1 the first inversion, 2 the second and 3 the third. " +
                    "Triads only use 0 to 2; seventh chords also use 3. The nearest search may " +
                    "use only these.",
                style = MaterialTheme.typography.bodySmall
            )
        }

        Text("Answer format", style = MaterialTheme.typography.titleMedium)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ProgressionAnswerFormat.entries.forEach { format ->
                FilterChip(
                    selected = draft.answerFormat == format,
                    onClick = { viewModel.editDraft { it.copy(answerFormat = format) } },
                    label = { Text(format.chipLabel()) }
                )
            }
        }
        Text(draft.answerFormat.setupDescription(), style = MaterialTheme.typography.bodySmall)

        Text("Range", style = MaterialTheme.typography.titleMedium)
        ChooserRow(
            label = "Lowest root note",
            options = NOTE_OPTIONS,
            selectedIndex = nearestIndex(NOTE_OPTIONS, draft.minimumNote),
            onSelected = { index ->
                val note = NOTE_OPTIONS[index]
                viewModel.editDraft {
                    it.copy(minimumNote = note, maximumNote = maxOf(note, it.maximumNote))
                        .withFeasibleProgressionFixedRoot()
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
                        .withFeasibleProgressionFixedRoot()
                }
            },
            format = { note -> MidiNote.of(note).displayName(spelling = naming) }
        )
        RootRangeReminder()
        Text(
            "Only the key tonic must be inside this range. The selected chords may extend " +
                "outside it. A combination with no playable realization is explained below.",
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
                "so it does not run into the first chord.",
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
                label = "Chord length",
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
                label = "Gap between chords",
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

/**
 * Toggles one chord in a draft, keeping catalog order.
 *
 * An id this build does not know is preserved rather than silently dropped, so a stored draft the
 * validator is explaining does not become a different draft when a chip is touched.
 */
private fun ProgressionSetupDraft.toggleChord(chord: DiatonicChord): ProgressionSetupDraft {
    val known = chordIds.filter { DiatonicChord.ofIdOrNull(it) != null }.toSet()
    val next = if (chord.id in known) known - chord.id else known + chord.id
    val ordered = DiatonicChord.entries.filter { it.id in next }.map { it.id }
    val unknown = chordIds.filter { DiatonicChord.ofIdOrNull(it) == null }
    return copy(chordIds = ordered + unknown)
}

/** Every fixed root the current pool can start on inside the range. */
private fun feasibleProgressionFixedRoots(draft: ProgressionSetupDraft): List<Int> =
    (draft.minimumNote..draft.maximumNote).filter { note ->
        ProgressionSetupValidator.validate(draft.copy(fixedRoot = note)) is SetupValidation.Valid
    }

/** Clears a fixed root that no longer fits the current range; the range reminder explains it. */
private fun ProgressionSetupDraft.withFeasibleProgressionFixedRoot(): ProgressionSetupDraft {
    val root = fixedRoot ?: return this
    return if (root in feasibleProgressionFixedRoots(this)) this else copy(fixedRoot = null)
}

/** The root nearest middle C that the current pool can start on. */
private fun preferredProgressionFixedRoot(draft: ProgressionSetupDraft): Int {
    val options = feasibleProgressionFixedRoots(draft)
        .ifEmpty { (draft.minimumNote..draft.maximumNote).toList() }
    val target = MidiNote.C4.coerceIn(draft.minimumNote, draft.maximumNote)
    return options.minByOrNull { kotlin.math.abs(it - target) } ?: target
}

/** The voicing chip title. */
internal fun ProgressionVoicingMode.chipLabel(): String = when (this) {
    ProgressionVoicingMode.ROOT_POSITION -> "Root position"
    ProgressionVoicingMode.NEAREST_INVERSION -> "Nearest inversion"
}

/** One line that explains what the selected voicing does. */
internal fun ProgressionVoicingMode.setupDescription(): String = when (this) {
    ProgressionVoicingMode.ROOT_POSITION ->
        "Every chord sounds as a closed root-position triad."
    ProgressionVoicingMode.NEAREST_INVERSION ->
        "Each chord takes the allowed inversion that moves the least from the chord before it."
}

/** The answer-format chip title. */
internal fun ProgressionAnswerFormat.chipLabel(): String = when (this) {
    ProgressionAnswerFormat.ROMAN -> "Roman sequence"
    ProgressionAnswerFormat.ROOT_MOTION -> "Root motion"
    ProgressionAnswerFormat.FUNCTIONS -> "Harmonic functions"
}

/** One line that explains what the selected answer speaks. */
internal fun ProgressionAnswerFormat.setupDescription(): String = when (this) {
    ProgressionAnswerFormat.ROMAN ->
        "Speaks each chord's degree number and quality in order, for example \u201Cone major, " +
            "six minor\u201D."
    ProgressionAnswerFormat.ROOT_MOTION ->
        "Speaks the directed interval between consecutive theoretical roots, for example " +
            "\u201Cdescending perfect fifth\u201D. It never describes inversion basses."
    ProgressionAnswerFormat.FUNCTIONS ->
        "Speaks each chord's harmonic function: tonic, predominant or dominant. It needs the " +
            "harmonic-function voice clips; if one is missing, its name is listed below."
}

private class ProgressionSetupViewModelFactory(
    private val settingsRepository: SettingsRepository,
    private val saveScope: kotlinx.coroutines.CoroutineScope?
) : androidx.lifecycle.ViewModelProvider.Factory {
    override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
        @Suppress("UNCHECKED_CAST")
        return ProgressionSetupViewModel(settingsRepository, saveScope = saveScope) as T
    }
}
