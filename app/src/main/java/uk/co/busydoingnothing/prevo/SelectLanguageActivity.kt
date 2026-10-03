/*
 * PReVo - A portable version of ReVo for Android
 * Copyright (C) 2012, 2016  Neil Roberts
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
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp

class SelectLanguageActivity : AppCompatActivity() {

    companion object {
        /** Opened from the settings to choose the user's search languages:
         * a tap on a language adds or removes it instead of searching in
         * it. */
        const val EXTRA_MANAGE = "uk.co.busydoingnothing.prevo.Manage"
    }

    /* Reloaded every time the screen is shown, because choosing a
     * language changes them */
    private var mainLanguages by mutableStateOf(emptyList<Language>())
    private var myLanguages by mutableStateOf(emptyList<Language>())

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

        val allLanguages = getAllLanguages()
        val manage = intent.getBooleanExtra(EXTRA_MANAGE, false)

        setContent {
            PrevoTheme {
                LanguageChooser(
                    manage = manage,
                    mainLanguages = mainLanguages,
                    myLanguages = myLanguages,
                    allLanguages = allLanguages,
                    onLanguageClick = if (manage) ::toggleMine else ::chooseLanguage,
                    onToggleMine = ::toggleMine,
                    onReorderMine = ::reorderMine,
                    /* Nothing to go back to on the very first start,
                     * when this is the first screen of the app */
                    onBack = if (isTaskRoot) null else ::finish,
                    onPreferences = if (manage) null else ({ MenuHelper.goPreferences(this) }),
                )
            }
        }
    }

    override fun onStart() {
        super.onStart()

        /* Esperanto is always the first main language */
        mainLanguages = toLanguages(listOf("eo") + LanguageDatabaseHelper(this).languages)
        myLanguages = toLanguages(MyLanguages.get(this))
    }

    private fun toLanguages(codes: List<String>): List<Language> {
        val languageList = LanguageList.getDefault(this)
        return codes.map { Language(languageList.getLanguageName(it), it) }
    }

    private fun toggleMine(language: Language) {
        myLanguages = toLanguages(MyLanguages.toggle(this, language.code))
    }

    private fun reorderMine(codes: List<String>) {
        MyLanguages.set(this, codes)
        myLanguages = toLanguages(codes)
    }


    /** All of the languages except Esperanto, which is always the first
     * main language. */
    private fun getAllLanguages(): List<Language> =
        LanguageList.getDefault(this).allLanguages.filter { it.code != "eo" }

    private fun chooseLanguage(language: Language) {
        val intent = MenuHelper.createSearchIntent(this, language.code)

        /* Replace the search this screen was opened from, if any,
         * instead of stacking a new one on top of it */
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
        startActivity(intent)
        finish()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LanguageChooser(
    manage: Boolean,
    mainLanguages: List<Language>,
    myLanguages: List<Language>,
    allLanguages: List<Language>,
    onLanguageClick: (Language) -> Unit,
    onToggleMine: (Language) -> Unit,
    onReorderMine: (List<String>) -> Unit,
    onBack: (() -> Unit)?,
    onPreferences: (() -> Unit)?,
) {
    var query by rememberSaveable { mutableStateOf("") }
    val filter = normaliseQuery(query)
    val myCodes = myLanguages.map { it.code }.toSet()
    val listState = rememberLazyListState()
    val reorder = rememberListReorder(listState)
    val row = LanguageRow(
        myCodes,
        onLanguageClick,
        onToggleMine,
        myLanguages.map { it.code },
        onReorderMine,
        reorder,
    )

    Scaffold(
        modifier = Modifier.prevoBackground(),
        containerColor = Color.Transparent,
        /* The colour of the text, which a transparent container doesn't give */
        contentColor = MaterialTheme.colorScheme.onBackground,
        topBar = {
            TopAppBar(
                colors = prevoTopBarColors(),
                title = {
                    Text(stringResource(if (manage) R.string.my_search_languages else R.string.select_language))
                },
                navigationIcon = {
                    if (onBack != null) {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back))
                        }
                    }
                },
                actions = {
                    if (onPreferences != null) {
                        IconButton(onClick = onPreferences) {
                            Icon(Icons.Default.Settings, stringResource(R.string.preferences))
                        }
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding(),
            state = listState,
        ) {
            if (manage) {
                item {
                    Text(
                        stringResource(R.string.my_languages_help),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                    )
                }
            }
            item { LanguageSearchField(query, onQueryChange = { query = it }) }

            if (filter.isEmpty()) {
                /* The user's own languages replace the automatic ones */
                val top = myLanguages.ifEmpty { mainLanguages }

                if (myLanguages.isNotEmpty())
                    languageSection(R.string.my_languages, myLanguages, row, reorderable = true)
                else
                    languageSection(R.string.main_languages, mainLanguages, row)

                /* Each language is listed once: the ones at the top
                 * aren't repeated below */
                val topCodes = top.map { it.code }.toSet()
                languageSection(
                    R.string.other_languages,
                    allLanguages.filter { it.code !in topCodes },
                    row,
                )
            } else {
                val matching = allLanguages.filter { it.matches(filter) }

                if (matching.isEmpty()) {
                    item {
                        Box(
                            Modifier.fillMaxWidth().padding(32.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                stringResource(R.string.no_language_found),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                } else {
                    languageSection(R.string.all_languages, matching, row)
                }
            }
        }
    }
}

/** What a row of the list needs to know and do. */
private class LanguageRow(
    val myCodes: Set<String>,
    val onClick: (Language) -> Unit,
    val onToggleMine: (Language) -> Unit,
    /** The order of the user's languages, which can be changed by
     * dragging them */
    val order: List<String>,
    val onReorder: (List<String>) -> Unit,
    val reorder: ListReorder,
)

private fun LazyListScope.languageSection(
    title: Int,
    languages: List<Language>,
    row: LanguageRow,
    reorderable: Boolean = false,
) {
    item { SectionTitle(stringResource(title)) }

    /* The keys keep the rows in place when a language moves between
     * the sections */
    items(languages, key = { it.code }) { language ->
        /* The order of the user's languages is the order of the chips
         * and of the search */
        if (reorderable)
            ReorderableRow(row.reorder, language.code, row.order, row.onReorder) {
                LanguageItem(language, row) { DragHandle() }
            }
        else
            LanguageItem(language, row)
    }
}

@Composable
private fun LanguageItem(language: Language, row: LanguageRow, handle: @Composable () -> Unit = {}) {
    val mine = language.code in row.myCodes

    Row(
        Modifier
            .fillMaxWidth()
            .clickable { row.onClick(language) }
            .padding(start = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            language.name,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f).padding(vertical = 14.dp),
        )
        handle()
        IconButton(onClick = { row.onToggleMine(language) }) {
            Icon(
                Icons.Default.Star,
                contentDescription = stringResource(
                    if (mine) R.string.remove_from_my_languages
                    else R.string.add_to_my_languages
                ),
                tint = if (mine) MaterialTheme.colorScheme.primary
                       else MaterialTheme.colorScheme.outlineVariant,
            )
        }
    }
}

@Composable
private fun LanguageSearchField(query: String, onQueryChange: (String) -> Unit) {
    RoundedSearchField(
        query = query,
        onQueryChange = onQueryChange,
        placeholder = stringResource(R.string.search_language),
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
    )
}

