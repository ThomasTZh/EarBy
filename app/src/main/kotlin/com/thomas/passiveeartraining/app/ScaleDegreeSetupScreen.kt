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
import com.thomas.passiveeartraining.music.SpellingPreference as MusicSpellingPreference
import com.thomas.passiveeartraining.music.Tonality
import com.thomas.passiveeartraining.training.DegreeAnswerFormat
import com.thomas.passiveeartraining.training.context.ContextKind
import com.thomas.passiveeartraining.training.recipe.RecipeMode
import com.thomas.passiveeartraining.training.recipe.SessionStopPolicy
import com.thomas.passiveeartraining.training.settings.SettingsRepository
import com.thomas.passiveeartraining.training.settings.SetupValidation

/**
 * The scale-degree setup form (M8.3).
 *
 * The controls mirror the note screen: the everyday musical choices first, the shared recipe
 * controls next, and every value checked by the shared domain validator with all problems listed
 * at once. Keys read "no selection = all twelve keys" exactly like the scale screen's root
 * classes, the checklist is the degrees 1..7, and the context picker explains the selected
 * prelude; the provider attaches an impossible context/range combination to the range field with
 * the exact key, context and range that failed (M8.3).
 */
@Composable
internal fun ScaleDegreeSetupScreen(settingsRepository: SettingsRepository) {
    val viewModel: ScaleDegreeSetupViewModel = viewModel(
        factory = ScaleDegreeSetupViewModelFactory(
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
        Text("Scale degree setup", style = MaterialTheme.typography.headlineMedium)

        if (!state.loaded) {
            CircularProgressIndicator()
            return@Column
        }

        val draft = state.draft

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
                "Each key tonic is trained in every selected tonality."
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

        Text("Target degrees", style = MaterialTheme.typography.titleMedium)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            (1..7).forEach { degree ->
                FilterChip(
                    selected = degree in draft.degrees,
                    onClick = {
                        viewModel.editDraft { current ->
                            current.copy(
                                degrees = if (degree in current.degrees) {
                                    current.degrees - degree
                                } else {
                                    (current.degrees + degree).sorted()
                                }
                            )
                        }
                    },
                    label = { Text(degree.toString()) }
                )
            }
        }
        if (draft.degrees.isEmpty()) {
            Text(
                "Select at least one target degree; an empty selection cannot be played.",
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
            "The silence between the complete context and the target note. It is included in " +
                "every example and in the exercise duration.",
            style = MaterialTheme.typography.bodySmall
        )

        Text("Answer format", style = MaterialTheme.typography.titleMedium)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            DegreeAnswerFormat.entries.forEach { format ->
                FilterChip(
                    selected = draft.answerFormat == format,
                    onClick = { viewModel.editDraft { it.copy(answerFormat = format) } },
                    label = { Text(format.chipLabel()) }
                )
            }
        }
        Text(draft.answerFormat.setupDescription(), style = MaterialTheme.typography.bodySmall)

        Text("Accidental style", style = MaterialTheme.typography.titleMedium)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MusicSpellingPreference.entries.forEach { accidental ->
                FilterChip(
                    selected = draft.spelling == accidental,
                    onClick = { viewModel.editDraft { it.copy(spelling = accidental) } },
                    label = {
                        Text(
                            when (accidental) {
                                MusicSpellingPreference.SHARP -> "Sharp"
                                MusicSpellingPreference.FLAT -> "Flat"
                            }
                        )
                    }
                )
            }
        }
        Text(
            "Sharp (C\u266F, F\u266F) or flat (D\u266D, G\u266D) spelling for key tonics and " +
                "note-name answers.",
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
                }
            },
            format = { note -> MidiNote.of(note).displayName() }
        )
        RootRangeReminder()
        Text(
            "Only the key tonic must be inside this range. Context and target notes may extend " +
                "outside it. A key, tonality and degree combination with no playable target is " +
                "explained below.",
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
                label = "Gap between context parts",
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
 * The key-tonality a tonic chip trains, in the wording the requirement table uses.
 *
 * Major and natural minor are the only tonalities in this release, so the chips stay literal.
 */
internal fun Tonality.chipLabel(): String = when (this) {
    Tonality.MAJOR -> "Major"
    Tonality.NATURAL_MINOR -> "Natural minor"
}

/** The context chip title, matching the M8 settings table wording. */
internal fun ContextKind.chipLabel(): String = when (this) {
    ContextKind.TONIC_NOTE -> "Tonic note"
    ContextKind.TONIC_TRIAD -> "Tonic triad"
    ContextKind.ASCENDING_SCALE -> "Ascending octave scale"
    ContextKind.TONIC_DOMINANT_TONIC -> "Tonic\u2013dominant\u2013tonic"
    ContextKind.SUBDOMINANT_DOMINANT_TONIC -> "Subdominant\u2013dominant\u2013tonic"
}

/**
 * One line that explains exactly what the selected context plays.
 *
 * The minor cadences use a major V with the raised leading tone, and the line says so, because a
 * listener must not infer the harmonic function from the natural-minor target scale (M8.1).
 */
internal fun ContextKind.setupDescription(): String = when (this) {
    ContextKind.TONIC_NOTE ->
        "One tonic note at the anchor, then the target."
    ContextKind.TONIC_TRIAD ->
        "The root-position tonic triad (major or natural minor), then the target."
    ContextKind.ASCENDING_SCALE ->
        "The complete ascending octave, including the upper tonic, then the target."
    ContextKind.TONIC_DOMINANT_TONIC ->
        "I\u2013V\u2013I, then the target. In natural minor it is i\u2013V\u2013i with a major V."
    ContextKind.SUBDOMINANT_DOMINANT_TONIC ->
        "IV\u2013V\u2013I, then the target. In natural minor it is iv\u2013V\u2013i with a " +
            "major V."
}

/** The answer-format chip title. */
internal fun DegreeAnswerFormat.chipLabel(): String = when (this) {
    DegreeAnswerFormat.DEGREE -> "Degree"
    DegreeAnswerFormat.NOTE_NAME -> "Note name"
}

/** One line that explains what the selected answer speaks. */
internal fun DegreeAnswerFormat.setupDescription(): String = when (this) {
    DegreeAnswerFormat.DEGREE ->
        "Speaks the degree number alone, for example \u201Cfive\u201D."
    DegreeAnswerFormat.NOTE_NAME ->
        "Speaks the absolute note name of the degree, spelled from the key."
}

private class ScaleDegreeSetupViewModelFactory(
    private val settingsRepository: SettingsRepository,
    private val saveScope: kotlinx.coroutines.CoroutineScope?
) : androidx.lifecycle.ViewModelProvider.Factory {
    override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
        @Suppress("UNCHECKED_CAST")
        return ScaleDegreeSetupViewModel(settingsRepository, saveScope = saveScope) as T
    }
}
