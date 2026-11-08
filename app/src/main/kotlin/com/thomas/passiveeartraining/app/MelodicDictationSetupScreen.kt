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
import com.thomas.passiveeartraining.music.MidiNote
import com.thomas.passiveeartraining.music.PitchClass
import com.thomas.passiveeartraining.music.Tonality
import com.thomas.passiveeartraining.training.MelodicAnswerFormat
import com.thomas.passiveeartraining.training.MelodicDictationDefinition
import com.thomas.passiveeartraining.training.MelodicDirectionBias
import com.thomas.passiveeartraining.training.context.ContextKind
import com.thomas.passiveeartraining.training.recipe.RecipeMode
import com.thomas.passiveeartraining.training.recipe.SessionStopPolicy
import com.thomas.passiveeartraining.training.settings.MelodicDictationSetupDraft
import com.thomas.passiveeartraining.training.settings.SettingsRepository
import com.thomas.passiveeartraining.training.settings.SetupValidation

/**
 * The melodic-dictation setup form (M10.4).
 *
 * The controls mirror the scale-degree, progression and contextual-interval screens: the everyday
 * musical choices first, the shared recipe controls next, and every value checked by the shared
 * domain validator with all problems listed at once. Keys read "no selection = all twelve keys"
 * exactly like the other editors, the five M8 contexts keep their wording, and the phrase is
 * described by its exact note count, its optional start and end degrees and the signed steps the
 * sampler may use. The provider attaches an impossible phrase length, endpoint, step, leap or
 * range combination to the responsible field with the exact key and constraint that failed.
 *
 * This mode is passive: there is no answer entry, no marks and no correct/incorrect state - only
 * listening, remembering and comparing with the spoken answer. The mode owns no label cycle, so
 * the cycle stop choices are hidden with an explanation; a stored or imported cycle policy stays
 * in the draft and is reported by the validator until it is changed.
 */
@Composable
internal fun MelodicDictationSetupScreen(settingsRepository: SettingsRepository) {
    val viewModel: MelodicDictationSetupViewModel = viewModel(
        factory = MelodicDictationSetupViewModelFactory(
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
        Text("Melodic dictation setup", style = MaterialTheme.typography.headlineMedium)
        Text(
            "Listen to the context and the phrase, remember them and compare with the spoken " +
                "answer; there is no entry or scoring.",
            style = MaterialTheme.typography.bodyMedium
        )

        if (!state.loaded) {
            CircularProgressIndicator()
            return@Column
        }

        val draft = state.draft
        // The setup's own sharp/flat table spells the key tonics and every range readout;
        // melodic-dictation answers never speak note names.
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
                "Every selected phrase is trained in every selected key and tonality that can " +
                    "realize it."
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

        Text("Phrase", style = MaterialTheme.typography.titleMedium)
        ChooserRow(
            label = "Phrase length",
            options = PHRASE_LENGTH_OPTIONS,
            selectedIndex = nearestIndex(PHRASE_LENGTH_OPTIONS, draft.phraseLength),
            onSelected = { index ->
                viewModel.editDraft { it.copy(phraseLength = PHRASE_LENGTH_OPTIONS[index]) }
            },
            format = { count -> "$count notes" }
        )
        Text(
            "The exact note count, including the first and the last note. The graph stays " +
                "bounded: at most 16 notes per phrase.",
            style = MaterialTheme.typography.bodySmall
        )

        EndpointChooser(
            title = "Start degree",
            selected = draft.startDegree,
            onSelected = { degree -> viewModel.editDraft { it.copy(startDegree = degree) } }
        )
        EndpointChooser(
            title = "End degree",
            selected = draft.endDegree,
            onSelected = { degree -> viewModel.editDraft { it.copy(endDegree = degree) } }
        )
        Text(
            "Any allows every feasible degree; degree 1 is the tonic. The endpoint rule never " +
                "requires an octave, so any feasible octave of the degree is accepted.",
            style = MaterialTheme.typography.bodySmall
        )

        Text("Allowed steps", style = MaterialTheme.typography.titleMedium)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            (-MelodicDictationDefinition.MAX_STEP..MelodicDictationDefinition.MAX_STEP)
                .forEach { step ->
                    FilterChip(
                        selected = step in draft.allowedSteps,
                        onClick = { viewModel.editDraft { it.toggleStep(step) } },
                        label = { Text(stepChipLabel(step)) }
                    )
                }
        }
        Text(
            if (draft.allowedSteps.isEmpty()) {
                "Select at least one signed step; an empty selection cannot be played."
            } else {
                "A step is a signed semitone move: positive ascends, negative descends and " +
                    "0 (repeat) keeps the same note. A transition is used only when it is " +
                    "selected and its size is at most the maximum leap."
            },
            style = MaterialTheme.typography.bodySmall
        )

        ChooserRow(
            label = "Maximum leap",
            options = LEAP_OPTIONS,
            selectedIndex = nearestIndex(LEAP_OPTIONS, draft.maximumLeap),
            onSelected = { index ->
                viewModel.editDraft { it.copy(maximumLeap = LEAP_OPTIONS[index]) }
            },
            format = { semitones -> "$semitones semitones" }
        )
        Text(
            "A step of exactly the maximum leap is allowed; a larger one is not. The allowed " +
                "steps cross this limit, so some selected steps may not sound.",
            style = MaterialTheme.typography.bodySmall
        )

        Text("Direction bias", style = MaterialTheme.typography.titleMedium)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MelodicDirectionBias.entries.forEach { bias ->
                FilterChip(
                    selected = draft.bias == bias,
                    onClick = { viewModel.editDraft { it.copy(bias = bias) } },
                    label = { Text(bias.chipLabel()) }
                )
            }
        }
        Text(draft.bias.setupDescription(), style = MaterialTheme.typography.bodySmall)
        Text(
            "The weights change only the likelihood among feasible notes; they never relax a " +
                "constraint and promise no overall direction.",
            style = MaterialTheme.typography.bodySmall
        )

        Text("Answer format", style = MaterialTheme.typography.titleMedium)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MelodicAnswerFormat.entries.forEach { format ->
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
            "The silence between the complete context and the first phrase note. It is included " +
                "in every example and in the exercise duration.",
            style = MaterialTheme.typography.bodySmall
        )

        Text("Range", style = MaterialTheme.typography.titleMedium)
        ChooserRow(
            label = "Lowest root note",
            options = NOTE_OPTIONS,
            selectedIndex = nearestIndex(NOTE_OPTIONS, draft.minimumNote.value),
            onSelected = { index ->
                val note = NOTE_OPTIONS[index]
                viewModel.editDraft {
                    it.copy(
                        minimumNote = MidiNote.of(note),
                        maximumNote = MidiNote.of(maxOf(note, it.maximumNote.value))
                    )
                }
            },
            format = { note -> MidiNote.of(note).displayName(spelling = naming) }
        )
        ChooserRow(
            label = "Highest root note",
            options = NOTE_OPTIONS,
            selectedIndex = nearestIndex(NOTE_OPTIONS, draft.maximumNote.value),
            onSelected = { index ->
                val note = NOTE_OPTIONS[index]
                viewModel.editDraft {
                    it.copy(
                        maximumNote = MidiNote.of(note),
                        minimumNote = MidiNote.of(minOf(note, it.minimumNote.value))
                    )
                }
            },
            format = { note -> MidiNote.of(note).displayName(spelling = naming) }
        )
        RootRangeReminder()
        Text(
            "Only the key tonic must be inside this range. Context and melody notes may extend " +
                "outside it. A selection with no playable phrase is explained below.",
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
        Text(
            "Melodic dictation has no label cycle, so \u201CAfter one cycle\u201D and " +
                "finish-cycle duration limits are not offered here. The Play tab's sleep timer " +
                "stops by elapsed time, exactly as in the other modes. A stored cycle policy is " +
                "reported below until it is changed.",
            style = MaterialTheme.typography.bodySmall
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
                label = "Gap between phrase notes",
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

/** Every selectable phrase length in ascending order; the graph bound is the domain's. */
private val PHRASE_LENGTH_OPTIONS: List<Int> =
    MelodicDictationDefinition.PHRASE_LENGTH_RANGE.toList()

/** Every selectable maximum leap; the domain bound is the maximum step size. */
private val LEAP_OPTIONS: List<Int> = (0..MelodicDictationDefinition.MAX_LEAP).toList()

/** Final-silence choices, 0 to 4 seconds in quarter seconds. */
private val FINAL_SILENCE_OPTIONS: List<Int> = (0..4000 step 250).toList()

/** Context-gap choices: 0 to the 30 s maximum in quarter seconds, including the 500 ms default. */
private val CONTEXT_GAP_OPTIONS: List<Int> = (0..30_000 step 250).toList()

/**
 * The "Any" plus 1..7 degree chips of one phrase endpoint.
 *
 * The draft intentionally keeps a nullable degree distinguishable from tonic: a saved degree 1
 * selects the tonic chip, while "Any" is the absence of a requirement.
 */
@Composable
private fun EndpointChooser(title: String, selected: Int?, onSelected: (Int?) -> Unit) {
    Text(title, style = MaterialTheme.typography.titleMedium)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterChip(
            selected = selected == null,
            onClick = { onSelected(null) },
            label = { Text("Any") }
        )
        (1..7).forEach { degree ->
            FilterChip(
                selected = selected == degree,
                onClick = { onSelected(degree) },
                label = { Text(degree.toString()) }
            )
        }
    }
}

/** The signed-step chip title: the zero chip says what it means on its face. */
internal fun stepChipLabel(step: Int): String = when {
    step > 0 -> "+$step"
    step == 0 -> "0 (repeat)"
    else -> step.toString()
}

/**
 * Toggles one signed step in a draft, keeping the numeric order.
 *
 * A value outside the domain range is preserved rather than silently dropped, so a stored draft
 * the validator is explaining does not become a different draft when a chip is touched.
 */
private fun MelodicDictationSetupDraft.toggleStep(step: Int): MelodicDictationSetupDraft {
    val known = allowedSteps
        .filter { it in -MelodicDictationDefinition.MAX_STEP..MelodicDictationDefinition.MAX_STEP }
        .toSet()
    val next = if (step in known) known - step else known + step
    val ordered = (-MelodicDictationDefinition.MAX_STEP..MelodicDictationDefinition.MAX_STEP)
        .filter { it in next }
    val unknown = allowedSteps.filter {
        it !in -MelodicDictationDefinition.MAX_STEP..MelodicDictationDefinition.MAX_STEP
    }
    return copy(allowedSteps = ordered + unknown)
}

/** The direction-bias chip title. */
internal fun MelodicDirectionBias.chipLabel(): String = when (this) {
    MelodicDirectionBias.BALANCED -> "Balanced"
    MelodicDirectionBias.ASCENDING -> "Ascending"
    MelodicDirectionBias.DESCENDING -> "Descending"
}

/**
 * One line that explains the selected transition weights (M10.2).
 *
 * The weights change only the likelihood among feasible notes: they never add an edge, relax an
 * endpoint or force the phrase to move in the biased direction.
 */
internal fun MelodicDirectionBias.setupDescription(): String = when (this) {
    MelodicDirectionBias.BALANCED ->
        "Up, down and repeated steps are equally likely (weights 1/1/1)."
    MelodicDirectionBias.ASCENDING ->
        "Up steps are three times as likely as down or repeated steps (weights 3/1/1)."
    MelodicDirectionBias.DESCENDING ->
        "Down steps are three times as likely as up or repeated steps (weights 1/3/1)."
}

/** The answer-format chip title. */
internal fun MelodicAnswerFormat.chipLabel(): String = when (this) {
    MelodicAnswerFormat.DEGREE_SEQUENCE -> "Degree sequence"
    MelodicAnswerFormat.DIRECTED_INTERVAL_SEQUENCE -> "Directed interval sequence"
}

/** One line that explains what the selected answer speaks (M10.3). */
internal fun MelodicAnswerFormat.setupDescription(): String = when (this) {
    MelodicAnswerFormat.DEGREE_SEQUENCE ->
        "Speaks one degree number per note, for example \u201Cone five three\u201D."
    MelodicAnswerFormat.DIRECTED_INTERVAL_SEQUENCE ->
        "Speaks one direction and interval phrase per step, for example \u201Cascending minor " +
            "third\u201D; a repeated note speaks the unison alone."
}

private class MelodicDictationSetupViewModelFactory(
    private val settingsRepository: SettingsRepository,
    private val saveScope: kotlinx.coroutines.CoroutineScope?
) : androidx.lifecycle.ViewModelProvider.Factory {
    override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
        @Suppress("UNCHECKED_CAST")
        return MelodicDictationSetupViewModel(
            settingsRepository,
            saveScope = saveScope
        ) as T
    }
}
