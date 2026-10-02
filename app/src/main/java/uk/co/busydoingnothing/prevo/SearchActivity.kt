/*
 * PReVo - A portable version of ReVo for Android
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
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AssistChip
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
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
import kotlinx.coroutines.withContext

class SearchActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_LANGUAGE = "uk.co.busydoingnothing.prevo.Language"
        const val EXTRA_SEARCH_TERM = "uk.co.busydoingnothing.prevo.SearchTerm"
        const val EXTRA_USE_LANGUAGE = "uk.co.busydoingnothing.prevo.UseLanguage"

        /** Maximum number of languages offered as chips when they are
         * chosen automatically: the opened one, Esperanto and the main
         * languages. The user's own languages have no limit. */
        private const val MAX_SEARCH_LANGUAGES = 3

        private const val STATE_SELECTED = "selected"
    }

    private lateinit var dbHelper: LanguageDatabaseHelper

    /* The language whose chip is selected, and the languages of all the
     * chips. The chips keep their order while the screen is shown, even
     * though choosing one counts as a use, and are recomputed when coming
     * back to it, in case the user's languages were changed meanwhile. */
    private var selected by mutableStateOf("eo")
    private var searchLanguages by mutableStateOf(listOf("eo"))

    /* Re-read when coming back, in case it was changed in the settings */
    private var textSize by mutableIntStateOf(PrevoSettings.DEFAULT_TEXT_SIZE)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        dbHelper = LanguageDatabaseHelper(this)

        selected = savedInstanceState?.getString(STATE_SELECTED)
            ?: intent.getStringExtra(EXTRA_LANGUAGE) ?: "eo"
        searchLanguages = getSearchLanguages(selected)

        /* Only count the language once, not every time the activity is
         * recreated, for example when the screen is rotated */
        if (savedInstanceState == null &&
            intent.getBooleanExtra(EXTRA_USE_LANGUAGE, false))
            useLanguage(selected)

        val initialQuery = intent.getStringExtra(EXTRA_SEARCH_TERM) ?: ""

        setContent {
            PrevoTheme {
                var showAbout by remember { mutableStateOf(false) }

                SearchScreen(
                    languages = searchLanguages,
                    selected = selected,
                    textSize = textSize,
                    initialQuery = initialQuery,
                    onLanguageChosen = ::chooseLanguage,
                    onResultClick = ::openArticle,
                    onChooseLanguage = { MenuHelper.goChooseLanguage(this) },
                    onPreferences = { MenuHelper.goPreferences(this) },
                    onAbout = { showAbout = true },
                )

                if (showAbout)
                    AboutDialog(onDismiss = { showAbout = false })
            }
        }
    }

    override fun onStart() {
        super.onStart()
        textSize = PrevoSettings.getTextSize(this)
    }

    override fun onRestart() {
        super.onRestart()
        searchLanguages = getSearchLanguages(selected)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString(STATE_SELECTED, selected)
    }


    private fun getSearchLanguages(mainLanguage: String): List<String> {
        val myLanguages = MyLanguages.get(this)

        /* The user's own languages, in their order and as many as they
         * want. The language that was opened is added if it isn't one of
         * them, so that it can still be selected. */
        if (myLanguages.isNotEmpty())
            return if (mainLanguage in myLanguages) myLanguages
                   else listOf(mainLanguage) + myLanguages

        /* Otherwise, as in the original app: the chosen language,
         * Esperanto and the main languages from the usage counts */
        val languages = mutableListOf<String>()

        languages.add(mainLanguage)

        if (mainLanguage != "eo")
            languages.add("eo")

        for (language in dbHelper.languages) {
            if (languages.size >= MAX_SEARCH_LANGUAGES)
                break
            if (language !in languages)
                languages.add(language)
        }

        return languages
    }

    private fun chooseLanguage(language: String) {
        if (language != selected) {
            selected = language
            useLanguage(language)
        }
    }

    /** Remembers the language as explicitly chosen: it counts towards the
     * main languages and is reopened at the next start. */
    private fun useLanguage(language: String) {
        dbHelper.useLanguage(language)

        getSharedPreferences(MenuHelper.PREVO_PREFERENCES, MODE_PRIVATE)
            .edit()
            .putString(MenuHelper.PREF_LAST_LANGUAGE, language)
            .apply()
    }

    private fun openArticle(result: SearchResult) {
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
    onResultClick: (SearchResult) -> Unit,
    onChooseLanguage: () -> Unit,
    onPreferences: () -> Unit,
    onAbout: () -> Unit,
) {
    val context = LocalContext.current
    var query by rememberSaveable { mutableStateOf(initialQuery) }

    /* The selected language is searched first, then the others in the
     * order of the chips */
    val searchOrder = listOf(selected) + languages.filter { it != selected }

    /* null until the first search is done, so that "no results" isn't
     * shown while the index is still loading */
    val outcome by produceState<SearchOutcome?>(null, query, selected, languages) {
        value = withContext(Dispatchers.Default) {
            DictionarySearch.search(context, searchOrder, query)
        }
    }

    Scaffold(
        topBar = {
            Column(Modifier.windowInsetsPadding(WindowInsets.statusBars)) {
                SearchField(
                    query = query,
                    onQueryChange = { query = it },
                    onChooseLanguage = onChooseLanguage,
                    onPreferences = onPreferences,
                    onAbout = onAbout,
                )
                LanguageChips(
                    languages = languages,
                    selected = selected,
                    onSelect = onLanguageChosen,
                    onMore = onChooseLanguage,
                )
            }
        },
    ) { padding ->
        val current = outcome

        if (current != null && current.results.isEmpty()) {
            Box(
                Modifier.fillMaxSize().padding(padding).imePadding(),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    stringResource(R.string.no_results),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else if (current != null) {
            ResultList(
                outcome = current,
                searchOrder = searchOrder,
                textSize = textSize,
                onResultClick = onResultClick,
                modifier = Modifier.padding(padding).imePadding(),
            )
        }
    }
}

@Composable
private fun SearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    onChooseLanguage: () -> Unit,
    onPreferences: () -> Unit,
    onAbout: () -> Unit,
) {
    val focusRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    var menuOpen by remember { mutableStateOf(false) }

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
            .focusRequester(focusRequester),
        imeAction = ImeAction.Search,
        keyboardActions = KeyboardActions(onSearch = { keyboard?.hide() }),
        extraButtons = {
            IconButton(onClick = { menuOpen = true }) {
                Icon(Icons.Default.MoreVert, contentDescription = stringResource(R.string.more_options))
            }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.menu_choose_language)) },
                    onClick = { menuOpen = false; onChooseLanguage() },
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.menu_preferences)) },
                    onClick = { menuOpen = false; onPreferences() },
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.menu_about)) },
                    onClick = { menuOpen = false; onAbout() },
                )
            }
        },
    )
}

@Composable
private fun LanguageChips(
    languages: List<String>,
    selected: String,
    onSelect: (String) -> Unit,
    onMore: () -> Unit,
) {
    val context = LocalContext.current
    val languageList = remember { LanguageList.getDefault(context) }
    val chooseLanguage = stringResource(R.string.menu_choose_language)

    Row(
        Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        for (language in languages) {
            val name = languageList.getLanguageName(language)
            FilterChip(
                selected = language == selected,
                onClick = { onSelect(language) },
                label = { Text(language.uppercase()) },
                modifier = Modifier.semantics { contentDescription = name },
            )
        }
        AssistChip(
            onClick = onMore,
            label = { Icon(Icons.Default.Add, contentDescription = chooseLanguage) },
        )
    }
}

@Composable
private fun ResultList(
    outcome: SearchOutcome,
    searchOrder: List<String>,
    textSize: Int,
    onResultClick: (SearchResult) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scale = PrevoSettings.textScale(textSize)
    val body = MaterialTheme.typography.bodyLarge
    val wordStyle = body.copy(fontSize = body.fontSize * scale, lineHeight = body.lineHeight * scale)

    LazyColumn(modifier.fillMaxSize()) {
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
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                )
            }
        }

        /* For a search in another language, the preview starts with the
         * Esperanto word that the result translates */
        val inEsperanto = searchOrder.getOrNull(outcome.languageIndex) == "eo"

        items(outcome.results) { result ->
            ResultRow(result, wordStyle, scale, inEsperanto, onClick = { onResultClick(result) })
        }
    }
}

/** A search result: the word, and under it the start of its definition,
 * read from its article once the row is shown. */
@Composable
private fun ResultRow(
    result: SearchResult,
    wordStyle: TextStyle,
    scale: Float,
    inEsperanto: Boolean,
    onClick: () -> Unit,
) {
    val context = LocalContext.current
    val preview by produceState<WordPreview?>(null, result) {
        value = withContext(Dispatchers.IO) { SearchPreviews.get(context, result) }
    }
    val small = MaterialTheme.typography.bodyMedium

    Column(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
    ) {
        Text(result.word, style = wordStyle)

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
            style = small.copy(fontSize = small.fontSize * scale, lineHeight = small.lineHeight * scale),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
