package com.thomas.passiveeartraining.app

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.thomas.passiveeartraining.R
import com.thomas.passiveeartraining.training.provider.TrainingKind

/**
 * The mode sidebar.
 *
 * Every mode is listed so the shell is ready for the later ones; a mode that is not implemented yet
 * still opens and shows its placeholder, marked "Soon". Settings is pinned at the bottom.
 */
@Composable
internal fun ModeDrawerContent(
    selected: TrainingKind,
    onSelectMode: (TrainingKind) -> Unit,
    onOpenSettings: () -> Unit,
    settingsSelected: Boolean,
    onOpenHelp: () -> Unit,
    helpSelected: Boolean
) {
    ModalDrawerSheet {
        Text(
            "EarBy",
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.padding(start = 28.dp, top = 28.dp, end = 28.dp, bottom = 4.dp)
        )
        Text(
            "Passive ear training, always by your side.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 28.dp, end = 28.dp, bottom = 12.dp)
        )
        TrainingKind.ALL.forEach { mode ->
            NavigationDrawerItem(
                label = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(mode.label)
                        if (!mode.implemented) {
                            Text(
                                "Soon",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(start = 8.dp)
                            )
                        }
                    }
                },
                selected = !settingsSelected && mode == selected,
                onClick = { onSelectMode(mode) },
                modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
            )
        }
        Spacer(modifier = Modifier.weight(1f))
        HorizontalDivider(modifier = Modifier.padding(horizontal = 28.dp))
        Spacer(modifier = Modifier.height(8.dp))
        NavigationDrawerItem(
            label = { Text("Settings") },
            selected = settingsSelected,
            onClick = onOpenSettings,
            icon = { Icon(Icons.Filled.Settings, contentDescription = null) },
            modifier = Modifier
                .fillMaxWidth()
                .padding(NavigationDrawerItemDefaults.ItemPadding)
        )
        NavigationDrawerItem(
            label = { Text("Help") },
            selected = helpSelected,
            onClick = onOpenHelp,
            icon = { Icon(painterResource(R.drawable.ic_help), contentDescription = null) },
            modifier = Modifier
                .fillMaxWidth()
                .padding(NavigationDrawerItemDefaults.ItemPadding)
        )
        Spacer(modifier = Modifier.height(12.dp))
    }
}
