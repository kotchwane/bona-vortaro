/*
 * Bona vortaro - an Esperanto dictionary for Android, based on PReVo
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.Alignment
import androidx.compose.material3.Checkbox
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.lazy.LazyListScope
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.graphics.Color
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
    val fieldColor = bonaDesign.field

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
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
            focusedContainerColor = fieldColor,
            unfocusedContainerColor = fieldColor,
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
        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
        color = MaterialTheme.colorScheme.primary,
        modifier = modifier.padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 4.dp),
    )
}

/** The text size of the articles, with a slider and a preview. */
@Composable
fun TextSizeSetting(textSize: Int, onTextSizeChange: (Int) -> Unit) {
    val scale = BonaSettings.textScale(textSize)
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
            valueRange = BonaSettings.MIN_TEXT_SIZE.toFloat()..BonaSettings.MAX_TEXT_SIZE.toFloat(),
            /* One step per size, the ends excluded */
            steps = BonaSettings.MAX_TEXT_SIZE - BonaSettings.MIN_TEXT_SIZE - 1,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
        /* A preview at the size the articles will use */
        Text(
            stringResource(R.string.text_size_sample),
            style = body.copy(fontSize = body.fontSize * scale, lineHeight = body.lineHeight * scale),
            modifier = Modifier.padding(horizontal = 16.dp),
        )
        TextButton(
            onClick = { onTextSizeChange(BonaSettings.DEFAULT_TEXT_SIZE) },
            enabled = textSize != BonaSettings.DEFAULT_TEXT_SIZE,
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
        /* Transparent on the background of the screen, except while
         * it is dragged above the others */
        color = if (isDragged) MaterialTheme.colorScheme.surfaceContainerHigh else Color.Transparent,
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

/** A list of languages to choose from, for the search and the
 * translations: the chosen ones first, in their order, which can be
 * changed by dragging them after a long press, then the others. A tap
 * anywhere on a row ticks or unticks its language. With a filter, every
 * matching language is listed, ticked or not, in one list.
 * [canUntick] can refuse to untick a language, eg. the last one.
 * [onSearchDone] is called when a language is ticked or unticked from a
 * search, to end it and show the whole list again. */
fun LazyListScope.languageChoice(
    languages: List<Language>,
    chosen: List<String>,
    onChange: (List<String>) -> Unit,
    filter: String,
    reorder: ListReorder,
    orderHelp: Int,
    canUntick: (List<String>) -> Boolean = { true },
    onSearchDone: () -> Unit = {},
) {
    val byCode = languages.associateBy { it.code }
    val selected = chosen.mapNotNull { byCode[it] }
    val others = languages.filter { it.code !in chosen }
    val toggle = { language: Language ->
        if (language.code in chosen) {
            val remaining = chosen - language.code
            if (canUntick(remaining))
                onChange(remaining)
        } else {
            onChange(chosen + language.code)
        }
    }

    if (filter.isNotEmpty()) {
        val matching = languages.filter { it.matches(filter) }
        val toggleAndShowAll = { language: Language ->
            toggle(language)
            onSearchDone()
        }

        if (matching.isEmpty())
            item(key = "none") {
                Text(
                    stringResource(R.string.no_language_found),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth().padding(32.dp),
                )
            }
        for (language in matching)
            item(key = language.code) { LanguageCheckRow(language, language.code in chosen, toggleAndShowAll) }
        return
    }

    if (selected.isNotEmpty()) {
        item(key = "selected") {
            SectionTitle(stringResource(R.string.selected_languages, selected.size))
        }
        item(key = "order-help") {
            Text(
                stringResource(orderHelp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 4.dp),
            )
        }
    }

    for (language in selected)
        item(key = language.code) {
            ReorderableRow(reorder, language.code, chosen, onChange) {
                LanguageCheckRow(language, true, toggle) { DragHandle() }
            }
        }

    if (others.isNotEmpty())
        item(key = "others") { SectionTitle(stringResource(R.string.other_languages)) }

    for (language in others)
        item(key = language.code) {
            LanguageCheckRow(language, false, toggle, Modifier.animateItem())
        }
}

/** A language with a check box, ticked or unticked by a tap anywhere on
 * the row. */
@Composable
private fun LanguageCheckRow(
    language: Language,
    checked: Boolean,
    onToggle: (Language) -> Unit,
    modifier: Modifier = Modifier,
    trailing: @Composable () -> Unit = {},
) {
    Row(
        modifier
            .fillMaxWidth()
            .toggleable(value = checked, role = Role.Checkbox) { onToggle(language) }
            .padding(start = 16.dp, end = 8.dp, top = 2.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(language.name, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        trailing()
        Checkbox(checked = checked, onCheckedChange = null, modifier = Modifier.padding(12.dp))
    }
}
