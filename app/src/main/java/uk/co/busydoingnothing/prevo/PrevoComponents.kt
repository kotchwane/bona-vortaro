/*
 * PReVo - A portable version of ReVo for Android
 * Copyright (C) 2026  kotchwane
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; version 2 of the License.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package uk.co.busydoingnothing.prevo

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.zIndex
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import java.util.Locale
import kotlin.math.roundToInt

/** Normalises what was typed in a search field: trimmed, lowercase, and
 * x-system converted (cx → ĉ). */
fun normaliseQuery(query: String): String =
    Hats.removeHats(query.trim()).lowercase(Locale.ROOT)

/** Whether the language matches a normalised filter: the start of any
 * word of its name, or its exact code. */
fun Language.matches(filter: String): Boolean {
    if (code == filter)
        return true

    return name.split(' ', '(', ')', '-').any { it.startsWith(filter) }
}

/** The rounded search field used on every screen, with a clear button and
 * optionally more buttons after it. */
@Composable
fun RoundedSearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    imeAction: ImeAction = ImeAction.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    extraButtons: @Composable () -> Unit = {},
) {
    TextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = modifier.fillMaxWidth(),
        placeholder = { Text(placeholder) },
        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
        trailingIcon = {
            Row {
                if (query.isNotEmpty()) {
                    IconButton(onClick = { onQueryChange("") }) {
                        Icon(Icons.Default.Clear, contentDescription = stringResource(R.string.clear))
                    }
                }
                extraButtons()
            }
        },
        singleLine = true,
        shape = RoundedCornerShape(28.dp),
        colors = TextFieldDefaults.colors(
            focusedIndicatorColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            unfocusedIndicatorColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        ),
        /* No automatic capital or correction: these are dictionary
         * searches, not sentences */
        keyboardOptions = KeyboardOptions(
            capitalization = KeyboardCapitalization.None,
            autoCorrectEnabled = false,
            imeAction = imeAction,
        ),
        keyboardActions = keyboardActions,
    )
}

/** The title of a section of a list. */
@Composable
fun SectionTitle(title: String, modifier: Modifier = Modifier) {
    Text(
        title,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp),
    )
}

/** The text size of the articles, with a slider and a preview. */
@Composable
fun TextSizeSetting(textSize: Int, onTextSizeChange: (Int) -> Unit) {
    val scale = PrevoSettings.textScale(textSize)
    val body = MaterialTheme.typography.bodyLarge

    Column {
        Text(
            stringResource(R.string.text_size),
            style = body,
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp),
        )
        Text(
            stringResource(R.string.text_size_help),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
        Slider(
            value = textSize.toFloat(),
            onValueChange = { onTextSizeChange(it.roundToInt()) },
            valueRange = PrevoSettings.MIN_TEXT_SIZE.toFloat()..PrevoSettings.MAX_TEXT_SIZE.toFloat(),
            /* One step per size, the ends excluded */
            steps = PrevoSettings.MAX_TEXT_SIZE - PrevoSettings.MIN_TEXT_SIZE - 1,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
        /* A preview at the size the articles will use */
        Text(
            stringResource(R.string.text_size_sample),
            style = body.copy(fontSize = body.fontSize * scale, lineHeight = body.lineHeight * scale),
            modifier = Modifier.padding(horizontal = 16.dp),
        )
        TextButton(
            onClick = { onTextSizeChange(PrevoSettings.DEFAULT_TEXT_SIZE) },
            enabled = textSize != PrevoSettings.DEFAULT_TEXT_SIZE,
            modifier = Modifier.padding(horizontal = 8.dp),
        ) {
            Text(stringResource(R.string.default_text_size))
        }
    }
}

/** The row being dragged in a list whose rows can be reordered by
 * dragging them after a long press, and how far it is from its place. */
class ListReorder(private val listState: LazyListState) {
    var dragged by mutableStateOf<String?>(null)
        private set
    var offset by mutableFloatStateOf(0f)
        private set

    fun start(key: String) {
        dragged = key
        offset = 0f
    }

    fun stop() {
        dragged = null
        offset = 0f
    }

    /** Moves the dragged row by delta. Returns the new order if it went
     * over another row of the order, under its middle, or else null. */
    fun dragBy(delta: Float, order: List<String>): List<String>? {
        val key = dragged ?: return null
        offset += delta

        val items = listState.layoutInfo.visibleItemsInfo
        val current = items.firstOrNull { it.key == key } ?: return null
        val middle = current.offset + offset + current.size / 2
        val target = items.firstOrNull {
            it.key != key && it.key in order && middle >= it.offset && middle < it.offset + it.size
        } ?: return null

        val newOrder = order.toMutableList()
        newOrder.remove(key)
        newOrder.add(order.indexOf(target.key as String), key)
        /* The row moves to its new place, so the offset is now from there */
        offset -= target.offset - current.offset

        return newOrder
    }
}

@Composable
fun rememberListReorder(listState: LazyListState) = remember(listState) { ListReorder(listState) }

/** A row of a list that can be reordered: a long press lets it be
 * dragged, raised above the others, among the rows of the order. Its
 * key in the list must be the same as in the order. */
@Composable
fun LazyItemScope.ReorderableRow(
    reorder: ListReorder,
    key: String,
    order: List<String>,
    onReorder: (List<String>) -> Unit,
    content: @Composable () -> Unit,
) {
    val currentOrder by rememberUpdatedState(order)
    val currentOnReorder by rememberUpdatedState(onReorder)
    val isDragged = reorder.dragged == key
    val elevation by animateDpAsState(if (isDragged) 6.dp else 0.dp, label = "drag")

    Surface(
        shadowElevation = elevation,
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier
            .zIndex(if (isDragged) 1f else 0f)
            .graphicsLayer { translationY = if (isDragged) reorder.offset else 0f }
            .then(if (isDragged) Modifier else Modifier.animateItem())
            .pointerInput(key) {
                detectDragGesturesAfterLongPress(
                    onDragStart = { reorder.start(key) },
                    onDrag = { change, amount ->
                        change.consume()
                        reorder.dragBy(amount.y, currentOrder)?.let(currentOnReorder)
                    },
                    onDragEnd = { reorder.stop() },
                    onDragCancel = { reorder.stop() },
                )
            },
        content = content,
    )
}

/** The handle that shows that a row can be moved. */
@Composable
fun DragHandle() {
    Icon(
        Icons.Default.Menu,
        contentDescription = stringResource(R.string.drag_to_reorder),
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 8.dp),
    )
}
