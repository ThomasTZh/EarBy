package com.thomas.passiveeartraining.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.thomas.passiveeartraining.BuildConfig
import com.thomas.passiveeartraining.training.provider.TrainingKind

/**
 * The Help page: what the app is for, how to start, what every mode trains, the range rules, the
 * credits, and the packed build version.
 *
 * The mode list is generated from [TrainingKind.ALL] and the same summaries the drawer uses, so a
 * mode description cannot drift between the two surfaces. Sections and cards keep the page
 * scannable instead of one continuous paragraph.
 */
@Composable
internal fun HelpScreen(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("About EarBy", style = MaterialTheme.typography.headlineSmall)
            Text(
                "EarBy is passive ear training. It plays short examples and then speaks the " +
                    "answer, so you learn to recognize intervals, chords, scales, notes, " +
                    "degrees, progressions and melodies by ear. There is nothing to tap and no " +
                    "scoring. Listen, remember and compare.",
                style = MaterialTheme.typography.bodyLarge
            )
            Text(
                "Everything is generated on the phone from bundled sounds and recorded speech. " +
                    "There is no account, no network connection, no telemetry and no advertising.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        HelpSection("How to use it") {
            HelpPoint(
                "Choose a mode",
                "Open the menu at the top left. Each of the nine modes keeps its own setup and " +
                    "five preset slots."
            )
            HelpPoint(
                "Set it up",
                "Choose notes, intervals, chords, keys, timing and answers in Setup. The " +
                    "Advanced controls are always visible, so nothing is hidden behind a switch."
            )
            HelpPoint(
                "Press Start",
                "The example plays first, then the answer is spoken. Stop ends the session. " +
                    "Changing mode waits for the current example to finish."
            )
            HelpPoint(
                "Let it run",
                "Use the sleep timer for hands-off practice. Progress is not recorded; the " +
                    "point is repeated listening."
            )
        }

        HelpSection("Three ways to listen") {
            HelpPoint(
                "Recognition",
                "Recognition plays the example, then the spoken answer after the thinking time."
            )
            HelpPoint(
                "Exposure",
                "Exposure speaks the answer first and plays the example after it."
            )
            HelpPoint(
                "Association",
                "Association plays the example again after the answer."
            )
        }

        HelpSection("The nine modes") {
            TrainingKind.ALL.forEach { kind ->
                ModeCard(kind)
            }
        }

        HelpSection("Ranges and answers") {
            HelpBody(
                "In interval, chord, scale, progression, degree, context and melody modes, the " +
                    "low and high range restrict roots or key tonics only. Other notes may sound " +
                    "outside that range, but never outside the instrument's A0-C8 samples."
            )
            HelpBody(
                "Perfect pitch is the exception: its range limits every note, because every note " +
                    "is the answer. Selected note classes with no playable note are marked in " +
                    "Setup and skipped during a session."
            )
            HelpBody(
                "The spoken answer is the only feedback. Recognition answers stay hidden on " +
                    "screen, in media metadata and from accessibility services until they are " +
                    "spoken."
            )
        }

        HelpSection("Presets and sound") {
            HelpBody(
                "Each mode keeps its last setup and five preset slots. Starter presets are " +
                    "included; applying a preset starts a fresh random sequence rather than " +
                    "replaying an old one."
            )
            HelpBody(
                "Music and voice volumes are in Settings, from 0 to 100 percent, with 50 percent " +
                    "as the reviewed normal. The sliders follow your finger and save the final " +
                    "level when you let go."
            )
        }

        HelpSection("Privacy") {
            HelpBody(
                "EarBy collects no personal information. Settings and presets stay in " +
                    "app-private storage, Android automatic backup is disabled, and uninstalling " +
                    "the app removes that data."
            )
            HelpBody(
                "There is no account, analytics, advertising, telemetry, cloud sync, runtime " +
                    "text-to-speech or INTERNET permission. The spoken answers are bundled " +
                    "recordings."
            )
        }

        HelpSection("Credits") {
            HelpBody(
                "The EarBy ear-and-note icon is hand-drawn by Dr Ham Hamster and used with her " +
                    "permission."
            )
            HelpBody(
                "Music comes from the project's own sampled instrument, rendered by its " +
                    "in-project sample player and miniaudio. Spoken answers are bundled " +
                    "recordings made with the Piper voices Alba, Amy, Cori and Jenny."
            )
            HelpBody(
                "EarBy uses AndroidX, Kotlin, kotlinx.serialization, Media3 and Jackson under " +
                    "the Apache License 2.0. Full notices and licence texts ship with the source."
            )
        }

        HorizontalDivider()
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Build", style = MaterialTheme.typography.titleMedium)
            Text(
                "EarBy ${BuildConfig.VERSION_NAME} (build ${BuildConfig.VERSION_CODE})",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun HelpSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        content()
    }
}

@Composable
private fun HelpBody(text: String) {
    Text(text, style = MaterialTheme.typography.bodyMedium)
}

@Composable
private fun HelpPoint(title: String, text: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            "\u2022",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.primary
        )
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(text, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun ModeCard(kind: TrainingKind) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(kind.label, style = MaterialTheme.typography.titleSmall)
            Text(kind.summary, style = MaterialTheme.typography.bodyMedium)
            Text(
                kind.audioDescription,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
