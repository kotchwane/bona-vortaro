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

import android.app.Dialog
import android.content.Intent
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
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp

class SelectLanguageActivity : AppCompatActivity() {

    /* Reloaded every time the screen is shown, because choosing a
     * language changes them */
    private var mainLanguages by mutableStateOf(emptyList<Language>())
    private var myLanguages by mutableStateOf(emptyList<Language>())

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        val allLanguages = getAllLanguages()

        setContent {
            PrevoTheme {
                LanguageChooser(
                    mainLanguages = mainLanguages,
                    myLanguages = myLanguages,
                    allLanguages = allLanguages,
                    onLanguageClick = ::chooseLanguage,
                    onToggleMine = ::toggleMine,
                    onPreferences = { MenuHelper.goPreferences(this) },
                    onAbout = { MenuHelper.showAbout(this) },
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

    @Deprecated("The about dialog still uses the old dialog API")
    override fun onCreateDialog(id: Int): Dialog? =
        MenuHelper.onCreateDialog(this, id)

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
    mainLanguages: List<Language>,
    myLanguages: List<Language>,
    allLanguages: List<Language>,
    onLanguageClick: (Language) -> Unit,
    onToggleMine: (Language) -> Unit,
    onPreferences: () -> Unit,
    onAbout: () -> Unit,
) {
    var query by rememberSaveable { mutableStateOf("") }
    val filter = normaliseQuery(query)
    val myCodes = myLanguages.map { it.code }.toSet()
    val row = LanguageRow(myCodes, onLanguageClick, onToggleMine)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.select_language)) },
                actions = { OverflowMenu(onPreferences, onAbout) },
            )
        },
    ) { padding ->
        LazyColumn(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding(),
        ) {
            item { LanguageSearchField(query, onQueryChange = { query = it }) }

            if (filter.isEmpty()) {
                /* The user's own languages replace the automatic ones */
                if (myLanguages.isNotEmpty())
                    languageSection(R.string.my_languages, myLanguages, row)
                else
                    languageSection(R.string.main_languages, mainLanguages, row)

                languageSection(R.string.all_languages, allLanguages, row)
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
)

private fun LazyListScope.languageSection(
    title: Int,
    languages: List<Language>,
    row: LanguageRow,
) {
    item { SectionTitle(stringResource(title)) }

    items(languages) { language ->
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

@Composable
private fun OverflowMenu(onPreferences: () -> Unit, onAbout: () -> Unit) {
    var open by remember { mutableStateOf(false) }

    IconButton(onClick = { open = true }) {
        Icon(Icons.Default.MoreVert, contentDescription = stringResource(R.string.more_options))
    }
    DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
        DropdownMenuItem(
            text = { Text(stringResource(R.string.menu_preferences)) },
            onClick = { open = false; onPreferences() },
        )
        DropdownMenuItem(
            text = { Text(stringResource(R.string.menu_about)) },
            onClick = { open = false; onAbout() },
        )
    }
}
