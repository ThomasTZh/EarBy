package com.thomas.passiveeartraining.app

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import kotlin.math.abs

/**
 * A vertical wheel picker: the user scrolls a short list and the row under the centre marker is
 * the selection. It is shown inside [PickerDialog], never inline, so a long list of settings stays
 * compact until one of them is opened.
 *
 * The wheel reports a selection only after a scroll settles, so a fling cannot fire a stream of
 * intermediate values. Changing [selectedIndex] from outside scrolls the wheel to it.
 */
@Composable
internal fun <T> VerticalPicker(
    options: List<T>,
    selectedIndex: Int,
    onSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
    visibleRows: Int = 3,
    rowHeight: Dp = 40.dp,
    width: Dp = 150.dp,
    description: String = "Scroll to choose",
    label: (T) -> String = { it.toString() }
) {
    require(options.isNotEmpty()) { "A picker needs at least one option" }
    require(visibleRows % 2 == 1) { "A picker needs an odd number of visible rows" }
    require(selectedIndex in options.indices) {
        "Selected index $selectedIndex is outside 0..${options.lastIndex}"
    }
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = selectedIndex)
    val fling = rememberSnapFlingBehavior(lazyListState = listState)
    val halfRows = visibleRows / 2

    // An outside change (a restored draft, a preset) moves the wheel without echoing back.
    LaunchedEffect(selectedIndex) {
        if (listState.firstVisibleItemIndex != selectedIndex ||
            listState.firstVisibleItemScrollOffset != 0
        ) {
            listState.scrollToItem(selectedIndex)
        }
    }

    val centeredIndex by remember(listState) {
        derivedStateOf {
            val info = listState.layoutInfo
            val center = (info.viewportStartOffset + info.viewportEndOffset) / 2
            info.visibleItemsInfo
                .minByOrNull { item -> abs(item.offset + item.size / 2 - center) }
                ?.index
        }
    }
    LaunchedEffect(listState, centeredIndex) {
        snapshotFlow { listState.isScrollInProgress }.collect { scrolling ->
            val index = centeredIndex ?: return@collect
            if (!scrolling && index != selectedIndex) {
                onSelected(index)
            }
        }
    }

    Box(
        modifier = modifier
            .height(rowHeight * visibleRows)
            .width(width)
            .clip(RoundedCornerShape(12.dp))
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center
    ) {
        // The centre marker sits behind the wheel, so the selected row reads as highlighted.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(rowHeight)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.secondaryContainer)
        )
        LazyColumn(
            state = listState,
            flingBehavior = fling,
            contentPadding = PaddingValues(vertical = rowHeight * halfRows),
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxSize()
        ) {
            items(options.size) { index ->
                val centered = index == (centeredIndex ?: selectedIndex)
                Text(
                    label(options[index]),
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = if (centered) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (centered) {
                        MaterialTheme.colorScheme.onSecondaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(rowHeight)
                        .wrapContentHeight(Alignment.CenterVertically)
                )
            }
        }
    }
}

/**
 * The modal version: a title, the wheel and OK/Cancel.
 *
 * The caller's value only changes on OK, so a cancelled dialog leaves the setting untouched. The
 * surface wraps its content instead of filling the screen, because the wheel is narrow.
 */
@Composable
internal fun <T> PickerDialog(
    title: String,
    options: List<T>,
    selectedIndex: Int,
    onConfirm: (Int) -> Unit,
    onDismiss: () -> Unit,
    format: (T) -> String = { it.toString() }
) {
    var pending by remember(selectedIndex) { mutableIntStateOf(selectedIndex) }
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(28.dp),
            tonalElevation = 6.dp,
            color = MaterialTheme.colorScheme.surfaceContainerHigh
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleSmall,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.width(120.dp)
                )
                Spacer(modifier = Modifier.height(8.dp))
                VerticalPicker(
                    options = options,
                    selectedIndex = pending,
                    onSelected = { pending = it },
                    visibleRows = 5,
                    rowHeight = 44.dp,
                    width = 112.dp,
                    description = title,
                    label = format
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = onDismiss, colors = darkTextButtonColors()) {
                        Text("Cancel")
                    }
                    TextButton(
                        onClick = { onConfirm(pending) },
                        colors = darkTextButtonColors()
                    ) { Text("OK") }
                }
            }
        }
    }
}

/**
 * A value button that opens [PickerDialog] when tapped.
 *
 * This is the compact control used wherever a fixed set of choices exists: the button shows the
 * current value and the list only appears when the user asks for it.
 */
@Composable
internal fun <T> ChooserButton(
    options: List<T>,
    selectedIndex: Int,
    onSelected: (Int) -> Unit,
    description: String,
    modifier: Modifier = Modifier,
    format: (T) -> String = { it.toString() }
) {
    var open by remember { mutableStateOf(false) }
    OutlinedButton(
        onClick = { open = true },
        modifier = modifier.semantics { contentDescription = description },
        colors = darkOutlinedButtonColors()
    ) {
        Text(format(options[selectedIndex]))
    }
    if (open) {
        PickerDialog(
            title = description,
            options = options,
            selectedIndex = selectedIndex,
            onConfirm = { index ->
                onSelected(index)
                open = false
            },
            onDismiss = { open = false },
            format = format
        )
    }
}

/** A labelled row with a [ChooserButton] value; the setup form's standard control. */
@Composable
internal fun <T> ChooserRow(
    label: String,
    options: List<T>,
    selectedIndex: Int,
    onSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
    format: (T) -> String = { it.toString() }
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyMedium)
        }
        ChooserButton(
            options = options,
            selectedIndex = selectedIndex,
            onSelected = onSelected,
            description = label,
            format = format
        )
    }
}
