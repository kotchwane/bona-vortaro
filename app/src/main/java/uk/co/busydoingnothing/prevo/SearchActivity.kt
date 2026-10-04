/*
 * Bona vortaro - an Esperanto dictionary for Android, based on PReVo
 * Copyright (C) 2012, 2013, 2016  Neil Roberts
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

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.HorizontalDivider
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarHost
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.Lifecycle
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.withContext

class SearchActivity : BonaActivity() {

    companion object {
        const val EXTRA_LANGUAGE = "uk.co.busydoingnothing.prevo.Language"
        const val EXTRA_SEARCH_TERM = "uk.co.busydoingnothing.prevo.SearchTerm"

        private const val STATE_SELECTED = "selected"
    }

    /* The language whose tab is selected, and the user's search
     * languages, re-read when coming back to the screen in case they were
     * changed meanwhile */
    private var selected by mutableStateOf("eo")
    private var searchLanguages by mutableStateOf(listOf("eo"))

    /* Re-read when coming back, in case it was changed in the settings */
    private var textSize by mutableIntStateOf(BonaSettings.DEFAULT_TEXT_SIZE)

    /* The words looked up last, shown while nothing is typed; null while
     * they are read. Re-read when coming back from an article. */
    private var recent by mutableStateOf<List<RecentSearch>?>(null)
    private var keepHistory by mutableStateOf(true)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        searchLanguages = BonaSettings.getSearchLanguages(this)
        selected = (savedInstanceState?.getString(STATE_SELECTED)
            ?: intent.getStringExtra(EXTRA_LANGUAGE))
            ?.takeIf { it in searchLanguages }
            ?: searchLanguages.first()

        val initialQuery = intent.getStringExtra(EXTRA_SEARCH_TERM) ?: ""

        setContent {
            BonaTheme {
                SearchScreen(
                    languages = searchLanguages,
                    selected = selected,
                    textSize = textSize,
                    initialQuery = initialQuery,
                    onLanguageChosen = ::chooseLanguage,
                    onResultClick = ::openArticle,
                    onChooseLanguage = { SelectLanguageActivity.open(this) },
                    onPreferences = { Screens.goPreferences(this) },
                    recent = recent,
                    keepHistory = keepHistory,
                    onRemoveRecent = ::removeFromHistory,
                    onRestoreHistory = ::restoreHistory,
                )
            }
        }
    }

    override fun onStart() {
        super.onStart()
        textSize = BonaSettings.getTextSize(this)
        keepHistory = BonaSettings.getKeepHistory(this)
        loadHistory()
    }

    private fun loadHistory() {
        lifecycleScope.launch {
            recent = withContext(Dispatchers.IO) {
                findRecentSearches(applicationContext, History.get(applicationContext))
            }
        }
    }

    /** Removes a word from the history, and returns the history as it was
     * before, to undo it. */
    private fun removeFromHistory(search: RecentSearch): List<HistoryEntry> {
        val before = History.get(this)
        History.remove(this, search.entry)
        recent = recent?.filter { it !== search }
        return before
    }

    private fun restoreHistory(entries: List<HistoryEntry>) {
        History.set(this, entries)
        loadHistory()
    }

    override fun onRestart() {
        super.onRestart()
        /* The languages may have been changed meanwhile */
        searchLanguages = BonaSettings.getSearchLanguages(this)
        if (selected !in searchLanguages)
            selected = searchLanguages.first()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString(STATE_SELECTED, selected)
    }


    private fun chooseLanguage(language: String) {
        if (language != selected) {
            selected = language

            /* Reopened at the next start */
            BonaSettings.setLastLanguage(this, language)
        }
    }

    /** Opens the article of a result, found in the given language, and
     * keeps the word in the history. */
    private fun openArticle(result: SearchResult, language: String) {
        if (keepHistory) {
            val entry = HistoryEntry(language, result.word)
            lifecycleScope.launch(Dispatchers.IO) { History.add(applicationContext, entry) }
        }

        val intent = Intent(this, ArticleActivity::class.java)
        intent.putExtra(ArticleActivity.EXTRA_ARTICLE_NUMBER, result.article)
        intent.putExtra(ArticleActivity.EXTRA_MARK_NUMBER, result.mark)
        startActivity(intent)
    }
}

@Composable
private fun SearchScreen(
    languages: List<String>,
    selected: String,
    textSize: Int,
    initialQuery: String,
    onLanguageChosen: (String) -> Unit,
    onResultClick: (SearchResult, String) -> Unit,
    onChooseLanguage: () -> Unit,
    onPreferences: () -> Unit,
    recent: List<RecentSearch>?,
    keepHistory: Boolean,
    onRemoveRecent: (RecentSearch) -> List<HistoryEntry>,
    onRestoreHistory: (List<HistoryEntry>) -> Unit,
) {
    val context = LocalContext.current
    var query by rememberSaveable { mutableStateOf(initialQuery) }
    /* The bins of the history: any action but removing or scrolling
     * ends them */
    var editing by remember { mutableStateOf(false) }
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { editing = false }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val removed = stringResource(R.string.history_removed)
    val undo = stringResource(R.string.undo)

    /* A removal from the history can be undone for a moment */
    fun offerUndo(message: String, before: List<HistoryEntry>) {
        scope.launch {
            snackbar.currentSnackbarData?.dismiss()
            if (snackbar.showSnackbar(message, actionLabel = undo, withDismissAction = true) ==
                SnackbarResult.ActionPerformed)
                onRestoreHistory(before)
        }
    }

    /* The selected language is searched first, then the others in the
     * order of the tabs */
    val searchOrder = listOf(selected) + languages.filter { it != selected }

    /* null until the first search is done, so that "no results" isn't
     * shown while the index is still loading */
    val outcome by produceState<SearchOutcome?>(null, query, selected, languages) {
        /* Nothing is searched while nothing is typed: the history shows */
        value = if (query.isBlank()) null else withContext(Dispatchers.Default) {
            DictionarySearch.search(context, searchOrder, query)
        }
    }

    BonaScaffold(
        /* Above the keyboard, which the search opens with */
        snackbarHost = { SnackbarHost(snackbar, Modifier.imePadding()) },
        topBar = {
            Column(Modifier.windowInsetsPadding(WindowInsets.statusBars)) {
                Wordmark()
                SearchField(
                    query = query,
                    onQueryChange = { editing = false; query = it },
                    onPreferences = { editing = false; onPreferences() },
                    onFocused = { editing = false },
                )
                LanguageTabs(
                    languages = languages,
                    selected = selected,
                    onSelect = { editing = false; onLanguageChosen(it) },
                    onMore = { editing = false; onChooseLanguage() },
                )
            }
        },
    ) { padding ->
        val current = outcome

        if (query.isBlank()) {
            RecentSearches(
                recent = recent,
                keepHistory = keepHistory,
                editing = editing,
                onEditingChange = { editing = it },
                textSize = textSize,
                onOpen = { editing = false; onResultClick(it.result, it.entry.language) },
                onRemove = { offerUndo(removed, onRemoveRecent(it)) },
                modifier = Modifier.padding(padding).consumeWindowInsets(padding).imePadding(),
            )
        } else if (current != null && current.results.isEmpty()) {
            Box(
                Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding).imePadding(),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    stringResource(R.string.no_results),
                    style = MaterialTheme.typography.bodyLarge.copy(fontStyle = FontStyle.Italic),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else if (current != null) {
            ResultList(
                outcome = current,
                searchOrder = searchOrder,
                textSize = textSize,
                onResultClick = onResultClick,
                modifier = Modifier.padding(padding).consumeWindowInsets(padding).imePadding(),
            )
        }
    }
}

/** The name of the app with the star of Esperanto, like the title of a
 * dictionary, and the name of ReVo. */
@Composable
private fun Wordmark() {
    Row(
        Modifier.padding(start = 20.dp, end = 20.dp, top = 6.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            stringResource(R.string.brand_name),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = bonaDesign.headword,
        )
        Text(" ★", fontSize = 20.sp, color = bonaDesign.star)
        Spacer(Modifier.weight(1f))
        Text(
            stringResource(R.string.brand_subtitle),
            style = MaterialTheme.typography.bodySmall.copy(fontStyle = FontStyle.Italic),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun SearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    onPreferences: () -> Unit,
    /** The field is tapped, or gets the focus otherwise */
    onFocused: () -> Unit,
) {
    val focusRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current

    /* Like the original search screen, start with the keyboard open */
    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
        keyboard?.show()
    }

    RoundedSearchField(
        query = query,
        onQueryChange = onQueryChange,
        placeholder = stringResource(R.string.type_to_filter),
        modifier = Modifier
            .padding(start = 16.dp, end = 16.dp, top = 8.dp)
            .focusRequester(focusRequester)
            .onFocusChanged { if (it.isFocused) onFocused() },
        imeAction = ImeAction.Search,
        keyboardActions = KeyboardActions(onSearch = { keyboard?.hide() }),
        extraButtons = {
            IconButton(onClick = onPreferences) {
                Icon(Icons.Default.Settings, contentDescription = stringResource(R.string.preferences))
            }
        },
    )
}

/** The search languages, like the thumb index of a printed dictionary:
 * their codes in small capitals, the selected one underlined. */
@Composable
private fun LanguageTabs(
    languages: List<String>,
    selected: String,
    onSelect: (String) -> Unit,
    onMore: () -> Unit,
) {
    val context = LocalContext.current
    val languageList = remember { LanguageList.getDefault(context) }
    val chooseLanguage = stringResource(R.string.menu_choose_language)
    val ink = bonaDesign.headword
    val muted = MaterialTheme.colorScheme.onSurfaceVariant

    Row(
        Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        for (language in languages) {
            val isSelected = language == selected
            val name = languageList.getLanguageName(language)

            Text(
                language.uppercase(),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                letterSpacing = 2.sp,
                color = if (isSelected) ink else muted,
                modifier = Modifier
                    .selectable(selected = isSelected, role = Role.Tab) { onSelect(language) }
                    .semantics { contentDescription = name }
                    .padding(horizontal = 10.dp, vertical = 12.dp)
                    .drawBehind {
                        if (isSelected) {
                            val thickness = 2.dp.toPx()
                            drawRect(
                                ink,
                                topLeft = Offset(0f, size.height + 3.dp.toPx()),
                                size = Size(size.width - 2.sp.toPx(), thickness),
                            )
                        }
                    },
            )
        }
        IconButton(onClick = onMore) {
            Icon(Icons.Default.Add, contentDescription = chooseLanguage, tint = muted)
        }
    }
}

@Composable
private fun ResultList(
    outcome: SearchOutcome,
    searchOrder: List<String>,
    textSize: Int,
    onResultClick: (SearchResult, String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scale = BonaSettings.textScale(textSize)
    val title = MaterialTheme.typography.titleMedium
    val wordStyle = title.copy(
        fontSize = title.fontSize * scale,
        lineHeight = title.lineHeight * scale,
        fontWeight = FontWeight.Bold,
    )
    val rule = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.25f)

    LazyColumn(modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 12.dp)) {
        /* Explain when the words come from another language than the
         * selected one */
        if (outcome.languageIndex > 0) {
            item {
                val languageList = LanguageList.getDefault(context)
                Text(
                    stringResource(
                        R.string.other_language_note,
                        languageList.getLanguageName(searchOrder[0], true),
                        languageList.getLanguageName(searchOrder[outcome.languageIndex], true),
                    ),
                    style = MaterialTheme.typography.bodyMedium.copy(fontStyle = FontStyle.Italic),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                )
            }
        }

        item { RunningHead(outcome.results.first().word, outcome.results.last().word) }

        /* For a search in another language, the preview starts with the
         * Esperanto word that the result translates */
        /* The language where the words were found, kept with them in the
         * history */
        val language = searchOrder[outcome.languageIndex]
        val inEsperanto = language == "eo"
        val last = outcome.results.lastIndex

        itemsIndexed(outcome.results) { index, result ->
            ResultRow(result, wordStyle, scale, inEsperanto, onClick = { onResultClick(result, language) })
            if (index != last)
                HorizontalDivider(Modifier.padding(horizontal = 20.dp), color = rule)
        }
    }
}

/** The first and the last word of the results, like at the top of a page
 * of a printed dictionary. */
@Composable
private fun RunningHead(first: String, last: String) {
    val rule = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)

    Row(
        Modifier.padding(horizontal = 20.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        HorizontalDivider(Modifier.weight(1f), color = rule)
        Text(
            if (first == last) first else "$first — $last",
            style = MaterialTheme.typography.bodySmall.copy(fontStyle = FontStyle.Italic),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 10.dp),
        )
        HorizontalDivider(Modifier.weight(1f), color = rule)
    }
}

/** A search result: the word, and under it the start of its definition,
 * read from its article once the row is shown. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun ResultRow(
    result: SearchResult,
    wordStyle: TextStyle,
    scale: Float,
    inEsperanto: Boolean,
    onClick: () -> Unit,
    /** Shown small at the right of the word, eg. the language "FR" */
    label: String? = null,
    onLongClick: (() -> Unit)? = null,
    /** A button at the right of the row, eg. to remove it */
    action: (@Composable () -> Unit)? = null,
) {
    val context = LocalContext.current
    val preview by produceState<WordPreview?>(null, result) {
        value = withContext(Dispatchers.IO) { SearchPreviews.get(context, result) }
    }
    val small = MaterialTheme.typography.bodyMedium

    Row(
        Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(start = 20.dp, end = if (action != null) 8.dp else 20.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(result.word, style = wordStyle, color = bonaDesign.headword, modifier = Modifier.weight(1f))
                if (label != null)
                    Text(
                        label,
                        style = MaterialTheme.typography.labelMedium,
                        letterSpacing = 1.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 12.dp),
                    )
            }

            val text = preview?.let {
                when {
                    inEsperanto -> it.definition
                    it.definition.isEmpty() -> it.title
                    else -> "${it.title} — ${it.definition}"
                }
            }
            /* Keep the height of the row while the preview is loading */
            Text(
                text ?: "",
                style = small.copy(
                    fontSize = small.fontSize * scale,
                    lineHeight = small.lineHeight * scale,
                    fontStyle = FontStyle.Italic,
                ),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        action?.invoke()
    }
}
