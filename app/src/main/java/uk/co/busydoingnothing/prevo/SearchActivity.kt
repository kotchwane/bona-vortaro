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
import android.content.res.Configuration
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.HorizontalDivider
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Settings
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

        private const val STATE_SELECTED = "selected"
    }

    /* The language whose tab is selected, and the user's search
     * languages, re-read when coming back to the screen in case they were
     * changed meanwhile */
    private var selected by mutableStateOf("eo")
    private var searchLanguages by mutableStateOf(listOf("eo"))

    /* Re-read when coming back, in case it was changed in the settings */
    private var textSize by mutableIntStateOf(PrevoSettings.DEFAULT_TEXT_SIZE)

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        /* The colours of the icons of the system bars follow the theme only
         * when they are set, and the screen isn't recreated any more when
         * the theme changes */
        enableEdgeToEdge()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        searchLanguages = MyLanguages.searchLanguages(this)
        selected = (savedInstanceState?.getString(STATE_SELECTED)
            ?: intent.getStringExtra(EXTRA_LANGUAGE))
            ?.takeIf { it in searchLanguages }
            ?: searchLanguages.first()

        val initialQuery = intent.getStringExtra(EXTRA_SEARCH_TERM) ?: ""

        setContent {
            PrevoTheme {
                SearchScreen(
                    languages = searchLanguages,
                    selected = selected,
                    textSize = textSize,
                    initialQuery = initialQuery,
                    onLanguageChosen = ::chooseLanguage,
                    onResultClick = ::openArticle,
                    onChooseLanguage = { SelectLanguageActivity.open(this) },
                    onPreferences = { MenuHelper.goPreferences(this) },
                )
            }
        }
    }

    override fun onStart() {
        super.onStart()
        textSize = PrevoSettings.getTextSize(this)
    }

    override fun onRestart() {
        super.onRestart()
        /* The languages may have been changed meanwhile */
        searchLanguages = MyLanguages.searchLanguages(this)
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
            getSharedPreferences(MenuHelper.PREVO_PREFERENCES, MODE_PRIVATE)
                .edit()
                .putString(MenuHelper.PREF_LAST_LANGUAGE, language)
                .apply()
        }
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
) {
    val context = LocalContext.current
    var query by rememberSaveable { mutableStateOf(initialQuery) }

    /* The selected language is searched first, then the others in the
     * order of the tabs */
    val searchOrder = listOf(selected) + languages.filter { it != selected }

    /* null until the first search is done, so that "no results" isn't
     * shown while the index is still loading */
    val outcome by produceState<SearchOutcome?>(null, query, selected, languages) {
        value = withContext(Dispatchers.Default) {
            DictionarySearch.search(context, searchOrder, query)
        }
    }

    Scaffold(
        modifier = Modifier.prevoBackground(),
        containerColor = Color.Transparent,
        /* The colour of the text, which a transparent container doesn't give */
        contentColor = MaterialTheme.colorScheme.onBackground,
        topBar = {
            Column(Modifier.windowInsetsPadding(WindowInsets.statusBars)) {
                Wordmark()
                SearchField(
                    query = query,
                    onQueryChange = { query = it },
                    onPreferences = onPreferences,
                )
                LanguageTabs(
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
                modifier = Modifier.padding(padding).imePadding(),
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
            color = prevoDesign.headword,
        )
        Text(" ★", fontSize = 20.sp, color = prevoDesign.star)
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
            .focusRequester(focusRequester),
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
    val ink = prevoDesign.headword
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
    onResultClick: (SearchResult) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scale = PrevoSettings.textScale(textSize)
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
        val inEsperanto = searchOrder.getOrNull(outcome.languageIndex) == "eo"
        val last = outcome.results.lastIndex

        itemsIndexed(outcome.results) { index, result ->
            ResultRow(result, wordStyle, scale, inEsperanto, onClick = { onResultClick(result) })
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
            .padding(horizontal = 20.dp, vertical = 10.dp),
    ) {
        Text(result.word, style = wordStyle, color = prevoDesign.headword)

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
}
