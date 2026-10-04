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

import android.content.Context
import android.util.Log
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager
import androidx.activity.compose.BackHandler
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import java.io.IOException

/** A word of the history, found again in the index of its language. */
class RecentSearch(val entry: HistoryEntry, val result: SearchResult)

private const val TAG = "bonahistory"

/** Finds the words of the history in the index of their language, to
 * open their article. A word that an update of the dictionary removed is
 * left out. Disk access: not on the main thread. */
fun findRecentSearches(context: Context, entries: List<HistoryEntry>): List<RecentSearch> {
    val results = arrayOfNulls<SearchResult>(32)

    return entries.mapNotNull { entry ->
        val trie = try {
            TrieCache.getTrie(context, entry.language)
        } catch (e: IOException) {
            Log.w(TAG, "Failed to load the index for ${entry.language}")
            return@mapNotNull null
        }
        val count = trie.search(entry.word, results)
        results.take(count).filterNotNull()
            .firstOrNull { it.word == entry.word }
            ?.let { RecentSearch(entry, it) }
    }
}

/** What the search shows while nothing is typed: the words looked up
 * last, all of the languages together, the most recent first; or, while
 * there are none, how the search works. */
@Composable
fun RecentSearches(
    recent: List<RecentSearch>?,
    keepHistory: Boolean,
    /** Whether a bin shows next to each word, after a long press */
    editing: Boolean,
    onEditingChange: (Boolean) -> Unit,
    textSize: Int,
    onOpen: (RecentSearch) -> Unit,
    onRemove: (RecentSearch) -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scale = BonaSettings.textScale(textSize)
    val title = MaterialTheme.typography.titleMedium
    val wordStyle = title.copy(
        fontSize = title.fontSize * scale,
        lineHeight = title.lineHeight * scale,
        fontWeight = FontWeight.Bold,
    )
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val rule = muted.copy(alpha = 0.25f)

    val showBins = editing && keepHistory && !recent.isNullOrEmpty()
    val focusManager = LocalFocusManager.current
    BackHandler(enabled = showBins) { onEditingChange(false) }

    LazyColumn(
        modifier
            .fillMaxSize()
            /* A tap where there is nothing to tap ends the editing; the
             * words, the bins and the scrolling are left alone */
            .pointerInput(showBins) {
                if (showBins)
                    detectTapGestures { onEditingChange(false) }
            },
        contentPadding = PaddingValues(bottom = 12.dp),
    ) {
        item {
            RunningHeadText(stringResource(R.string.recent_searches)) {
                if (showBins)
                    TextButton(onClick = { onEditingChange(false) }) { Text(stringResource(R.string.done)) }
            }
        }

        when {
            /* Still being read */
            recent == null -> {}
            recent.isEmpty() || !keepHistory -> item { SearchTips(keepHistory) }
            else -> {
                itemsIndexed(recent, key = { _, it -> "${it.entry.language}\t${it.entry.word}" }) { index, search ->
                    ResultRow(
                        search.result,
                        wordStyle,
                        scale,
                        inEsperanto = search.entry.language == "eo",
                        onClick = { onOpen(search) },
                        label = search.entry.language.uppercase(),
                        onLongClick = {
                            /* The keyboard closes, so that the back
                             * button ends the editing at once */
                            focusManager.clearFocus()
                            onEditingChange(true)
                        },
                        action = if (!showBins) null else {
                            {
                                IconButton(onClick = { onRemove(search) }) {
                                    Icon(
                                        Icons.Default.Delete,
                                        contentDescription = stringResource(R.string.remove_from_history),
                                        tint = muted,
                                    )
                                }
                            }
                        },
                    )
                    if (index != recent.lastIndex)
                        HorizontalDivider(Modifier.padding(horizontal = 20.dp), color = rule)
                }
                item {
                    Box(Modifier.fillMaxWidth().padding(top = 8.dp), contentAlignment = Alignment.Center) {
                        TextButton(onClick = onClear) {
                            Text(
                                stringResource(R.string.clear_history),
                                style = MaterialTheme.typography.bodyMedium.copy(fontStyle = FontStyle.Italic),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}

/** A title between two rules, like the running head of the results,
 * and optionally a button after it. */
@Composable
private fun RunningHeadText(text: String, trailing: @Composable () -> Unit = {}) {
    val rule = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)

    Row(
        Modifier.padding(horizontal = 20.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        HorizontalDivider(Modifier.weight(1f), color = rule)
        Text(
            text,
            style = MaterialTheme.typography.bodySmall.copy(fontStyle = FontStyle.Italic),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 10.dp),
        )
        HorizontalDivider(Modifier.weight(1f), color = rule)
        trailing()
    }
}

/** While there is no history: where it will appear, and the three things
 * about the search that nobody guesses. They go away with the first
 * word looked up. */
@Composable
private fun SearchTips(keepHistory: Boolean) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val body = MaterialTheme.typography.bodyMedium

    Column(
        Modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text(
            stringResource(if (keepHistory) R.string.history_empty else R.string.history_off),
            style = body.copy(fontStyle = FontStyle.Italic),
            color = muted,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        Text(stringResource(R.string.tip_languages), style = body, color = muted)
        Text(stringResource(R.string.tip_typing), style = body, color = muted)
    }
}
