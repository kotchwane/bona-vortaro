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
import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** What the Fundamento or an Oficiala Aldono contains: what it is, how
 * many roots and words, and their list, each opening its article. The
 * circles of the timeline go from one list to another. */
class AdditionActivity : BonaActivity() {

    companion object {
        private const val EXTRA_LEVEL = "uk.co.busydoingnothing.prevo.Level"

        fun open(context: Context, level: Int) {
            context.startActivity(Intent(context, AdditionActivity::class.java).putExtra(EXTRA_LEVEL, level))
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val level = intent.getIntExtra(EXTRA_LEVEL, 0)

        setContent {
            BonaTheme {
                AdditionPage(initialLevel = level, onOpen = ::openArticle, onBack = ::finish)
            }
        }
    }

    private fun openArticle(entry: OfficialEntry) {
        startActivity(
            Intent(this, ArticleActivity::class.java)
                .putExtra(ArticleActivity.EXTRA_ARTICLE_NUMBER, entry.article)
                .putExtra(ArticleActivity.EXTRA_MARK_NUMBER, entry.section)
        )
    }
}

@Composable
private fun AdditionPage(initialLevel: Int, onOpen: (OfficialEntry) -> Unit, onBack: () -> Unit) {
    val context = LocalContext.current
    var level by rememberSaveable { mutableIntStateOf(initialLevel) }
    var query by rememberSaveable(level) { mutableStateOf("") }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    val all by produceState<List<OfficialEntry>?>(null) {
        value = withContext(Dispatchers.IO) { OfficialLists.get(context) }
    }
    val groups = remember(all, level) { all?.let { OfficialLists.groups(it, level) } }
    val filter = normaliseQuery(query)
    val shown = remember(groups, filter) {
        groups?.filter { group ->
            filter.isEmpty() ||
                (listOfNotNull(group.root) + group.words).any { OfficialLists.sortKey(it.text).startsWith(OfficialLists.sortKey(filter)) }
        }
    }
    /* The first line of each letter, for the letters above the list */
    val letters = remember(shown) {
        shown?.mapIndexed { index, group -> OfficialLists.sortKey(group.title).take(1).let { it.ifEmpty { "#" } } to index }
            ?.distinctBy { it.first }.orEmpty()
    }

    /* After a jump to a letter, or a long scroll, back returns to the top
     * of the list first, and only then leaves. Jumps, not animations: an
     * animation builds every row on the way, a thousand in the Fundamento */
    val scrolled by remember { derivedStateOf { listState.firstVisibleItemIndex > 0 } }
    val toTop = { scope.launch { listState.scrollToItem(0) }; Unit }
    BackHandler(enabled = scrolled, onBack = toTop)

    BonaScaffold(
        topBar = { BonaTopBar(title = { Text(levelName(level)) }, onBack = if (scrolled) toTop else onBack) },
    ) { padding ->
        LazyColumn(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .consumeWindowInsets(padding)
                .imePadding(),
            state = listState,
            contentPadding = PaddingValues(bottom = 24.dp),
        ) {
            item {
                Column(Modifier.padding(horizontal = 20.dp)) {
                    OfficialTimeline(level, onSelect = { level = it; scope.launch { listState.scrollToItem(0) } })
                    Spacer(Modifier.height(16.dp))
                    AdditionAbout(level, examples = false)
                    Spacer(Modifier.height(8.dp))
                    RoundedSearchField(
                        query = query,
                        onQueryChange = { query = it },
                        placeholder = stringResource(R.string.addition_filter),
                        modifier = Modifier.padding(vertical = 8.dp),
                    )
                }
            }

            /* The letters, to jump in a long list */
            if (letters.size > 5)
                item {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(horizontal = 12.dp),
                    ) {
                        for ((letter, index) in letters)
                            Text(
                                letter.replace("~", "̂").uppercase(),
                                style = MaterialTheme.typography.titleSmall,
                                color = bonaDesign.headword,
                                modifier = Modifier
                                    .clickable { scope.launch { listState.scrollToItem(index + 2) } }
                                    .padding(horizontal = 9.dp, vertical = 8.dp),
                            )
                    }
                }

            items(shown.orEmpty(), key = { "${it.title}\t${it.root?.article ?: it.words.first().article}\t${it.words.firstOrNull()?.section}" }) { group ->
                OfficialGroupRow(group, onOpen)
                HorizontalDivider(
                    Modifier.padding(horizontal = 20.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.25f),
                )
            }
        }
    }
}

/** A root with the start of its definition, and under it the words its
 * entry lists; or a word alone, with the root of its article. */
@Composable
private fun OfficialGroupRow(group: OfficialGroup, onOpen: (OfficialEntry) -> Unit) {
    val first = group.root ?: group.words.first()
    val muted = MaterialTheme.colorScheme.onSurfaceVariant

    Column(Modifier.fillMaxWidth()) {
        Column(
            Modifier
                .fillMaxWidth()
                .clickable { onOpen(first) }
                .padding(horizontal = 20.dp, vertical = 10.dp),
        ) {
            Row {
                Text(
                    first.text,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = bonaDesign.headword,
                )
                group.articleRoot?.let {
                    Text(
                        "  " + stringResource(R.string.addition_from, it),
                        style = MaterialTheme.typography.bodyMedium.copy(fontStyle = FontStyle.Italic),
                        color = muted,
                    )
                }
            }
            Preview(first, muted)
        }
        if (group.root != null)
            for (word in group.words)
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clickable { onOpen(word) }
                        .padding(start = 40.dp, end = 20.dp, top = 2.dp, bottom = 8.dp),
                ) {
                    Text(word.text, style = MaterialTheme.typography.titleSmall, color = bonaDesign.headword)
                    Preview(word, muted)
                }
    }
}

/** The start of the definition, read from the article once shown */
@Composable
private fun Preview(entry: OfficialEntry, color: androidx.compose.ui.graphics.Color) {
    val context = LocalContext.current
    val preview by produceState<WordPreview?>(null, entry) {
        value = withContext(Dispatchers.IO) {
            SearchPreviews.get(context, SearchResult(entry.text, entry.article, entry.section))
        }
    }
    Text(
        preview?.definition ?: "",
        style = MaterialTheme.typography.bodySmall.copy(fontStyle = FontStyle.Italic),
        color = color,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}
