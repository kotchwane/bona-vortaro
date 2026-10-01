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

import android.app.Dialog
import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AssistChip
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class SearchActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_LANGUAGE = "uk.co.busydoingnothing.prevo.Language"
        const val EXTRA_SEARCH_TERM = "uk.co.busydoingnothing.prevo.SearchTerm"
        const val EXTRA_USE_LANGUAGE = "uk.co.busydoingnothing.prevo.UseLanguage"

        /** Maximum number of languages offered as chips: the chosen one,
         * Esperanto and the main languages. */
        private const val MAX_SEARCH_LANGUAGES = 3
    }

    private lateinit var dbHelper: LanguageDatabaseHelper

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        dbHelper = LanguageDatabaseHelper(this)

        /* Computed once, so that the chips keep their order while the
         * screen is open, even though choosing one counts as a use */
        val searchLanguages = getSearchLanguages()

        /* Only count the language once, not every time the activity is
         * recreated, for example when the screen is rotated */
        if (savedInstanceState == null &&
            intent.getBooleanExtra(EXTRA_USE_LANGUAGE, false))
            useLanguage(searchLanguages[0])

        val initialQuery = intent.getStringExtra(EXTRA_SEARCH_TERM) ?: ""

        setContent {
            PrevoTheme {
                SearchScreen(
                    languages = searchLanguages,
                    initialQuery = initialQuery,
                    onLanguageChosen = ::useLanguage,
                    onResultClick = ::openArticle,
                    onChooseLanguage = { MenuHelper.goChooseLanguage(this) },
                    onPreferences = { MenuHelper.goPreferences(this) },
                    onAbout = { MenuHelper.showAbout(this) },
                )
            }
        }
    }

    @Deprecated("The about dialog still uses the old dialog API")
    override fun onCreateDialog(id: Int): Dialog? =
        MenuHelper.onCreateDialog(this, id)

    private fun getSearchLanguages(): List<String> {
        val languages = mutableListOf<String>()
        val mainLanguage = intent.getStringExtra(EXTRA_LANGUAGE) ?: "eo"

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
    initialQuery: String,
    onLanguageChosen: (String) -> Unit,
    onResultClick: (SearchResult) -> Unit,
    onChooseLanguage: () -> Unit,
    onPreferences: () -> Unit,
    onAbout: () -> Unit,
) {
    val context = LocalContext.current
    var query by rememberSaveable { mutableStateOf(initialQuery) }
    var selected by rememberSaveable { mutableStateOf(languages[0]) }

    /* The selected language is searched first, then the others in the
     * order of the chips */
    val searchOrder = listOf(selected) + languages.filter { it != selected }

    /* null until the first search is done, so that "no results" isn't
     * shown while the index is still loading */
    val outcome by produceState<SearchOutcome?>(null, query, selected) {
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
                    onSelect = {
                        if (it != selected) {
                            selected = it
                            onLanguageChosen(it)
                        }
                    },
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

    TextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = 8.dp)
            .focusRequester(focusRequester),
        placeholder = { Text(stringResource(R.string.type_to_filter)) },
        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
        trailingIcon = {
            Row {
                if (query.isNotEmpty()) {
                    IconButton(onClick = { onQueryChange("") }) {
                        Icon(Icons.Default.Clear, contentDescription = null)
                    }
                }
                IconButton(onClick = { menuOpen = true }) {
                    Icon(Icons.Default.MoreVert, contentDescription = null)
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
        keyboardOptions = KeyboardOptions(
            capitalization = KeyboardCapitalization.None,
            autoCorrectEnabled = false,
            imeAction = ImeAction.Search,
        ),
        keyboardActions = KeyboardActions(onSearch = { keyboard?.hide() }),
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
    onResultClick: (SearchResult) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current

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

        items(outcome.results) { result ->
            Text(
                result.word,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onResultClick(result) }
                    .padding(horizontal = 16.dp, vertical = 14.dp),
            )
        }
    }
}
