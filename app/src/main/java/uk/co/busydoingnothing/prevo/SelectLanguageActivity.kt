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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
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
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import java.util.Locale

class SelectLanguageActivity : AppCompatActivity() {

    /* Reloaded every time the screen is shown, because choosing a
     * language changes them */
    private var mainLanguages by mutableStateOf(emptyList<Language>())

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        val allLanguages = getAllLanguages()

        setContent {
            PrevoTheme {
                LanguageChooser(
                    mainLanguages = mainLanguages,
                    allLanguages = allLanguages,
                    onLanguageClick = ::chooseLanguage,
                    onPreferences = { MenuHelper.goPreferences(this) },
                    onAbout = { MenuHelper.showAbout(this) },
                )
            }
        }
    }

    override fun onStart() {
        super.onStart()

        val languageList = LanguageList.getDefault(this)

        /* Esperanto is always the first main language */
        mainLanguages = listOf(Language(languageList.getLanguageName("eo"), "eo")) +
            LanguageDatabaseHelper(this).languages.map {
                Language(languageList.getLanguageName(it), it)
            }
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

/** Whether the language matches what was typed: the start of any word
 * of its name, or its exact code. Case-insensitive, x-system accepted. */
private fun Language.matches(filter: String): Boolean {
    if (code == filter)
        return true

    return name.split(' ', '(', ')', '-').any { it.startsWith(filter) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LanguageChooser(
    mainLanguages: List<Language>,
    allLanguages: List<Language>,
    onLanguageClick: (Language) -> Unit,
    onPreferences: () -> Unit,
    onAbout: () -> Unit,
) {
    var query by rememberSaveable { mutableStateOf("") }
    val filter = Hats.removeHats(query.trim()).lowercase(Locale.ROOT)

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
                languageSection(R.string.main_languages, mainLanguages, onLanguageClick)
                languageSection(R.string.all_languages, allLanguages, onLanguageClick)
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
                    languageSection(R.string.all_languages, matching, onLanguageClick)
                }
            }
        }
    }
}

private fun LazyListScope.languageSection(
    title: Int,
    languages: List<Language>,
    onLanguageClick: (Language) -> Unit,
) {
    item {
        Text(
            stringResource(title),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp),
        )
    }

    items(languages) { language ->
        Text(
            language.name,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onLanguageClick(language) }
                .padding(horizontal = 16.dp, vertical = 14.dp),
        )
    }
}

@Composable
private fun LanguageSearchField(query: String, onQueryChange: (String) -> Unit) {
    TextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        placeholder = { Text(stringResource(R.string.search_language)) },
        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(onClick = { onQueryChange("") }) {
                    Icon(Icons.Default.Clear, contentDescription = null)
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
        ),
    )
}

@Composable
private fun OverflowMenu(onPreferences: () -> Unit, onAbout: () -> Unit) {
    var open by remember { mutableStateOf(false) }

    IconButton(onClick = { open = true }) {
        Icon(Icons.Default.MoreVert, contentDescription = null)
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
