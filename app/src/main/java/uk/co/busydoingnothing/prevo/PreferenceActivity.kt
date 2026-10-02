/*
 * PReVo - A portable version of ReVo for Android
 * Copyright (C) 2013, 2016  Neil Roberts
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

import android.content.res.Configuration
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp

class PreferenceActivity : AppCompatActivity() {

    private var theme by mutableStateOf(Theme.SYSTEM)
    private var textSize by mutableIntStateOf(PrevoSettings.DEFAULT_TEXT_SIZE)
    private var translations by mutableStateOf(emptySet<String>())

    /* The selected languages first, then the others. Computed when the
     * screen is shown, so that a language doesn't move while it is
     * being ticked or unticked */
    private var orderedLanguages by mutableStateOf(emptyList<Language>())

    /** Every language that can be a translation, i.e. all but Esperanto */
    private lateinit var translationLanguages: List<Language>

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

        translationLanguages =
            LanguageList.getDefault(this).allLanguages.filter { it.code != "eo" }

        setContent {
            PrevoTheme {
                SettingsScreen(
                    theme = theme,
                    onThemeChange = {
                        theme = it
                        PrevoSettings.setTheme(this, it)
                    },
                    textSize = textSize,
                    onTextSizeChange = {
                        textSize = it
                        PrevoSettings.setTextSize(this, it)
                    },
                    translationLanguages = translationLanguages,
                    orderedLanguages = orderedLanguages,
                    translations = translations,
                    onTranslationsChange = {
                        translations = it
                        PrevoSettings.setTranslationLanguages(this, translationLanguages, it)
                    },
                    onBack = ::finish,
                )
            }
        }
    }

    override fun onStart() {
        super.onStart()

        theme = PrevoSettings.getTheme(this)
        textSize = PrevoSettings.getTextSize(this)
        translations = PrevoSettings.getTranslationLanguages(this, translationLanguages)
        orderedLanguages = translationLanguages.sortedBy { if (it.code in translations) 0 else 1 }
    }

}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsScreen(
    theme: Theme,
    onThemeChange: (Theme) -> Unit,
    textSize: Int,
    onTextSizeChange: (Int) -> Unit,
    translationLanguages: List<Language>,
    orderedLanguages: List<Language>,
    translations: Set<String>,
    onTranslationsChange: (Set<String>) -> Unit,
    onBack: () -> Unit,
) {
    var query by rememberSaveable { mutableStateOf("") }
    val filter = normaliseQuery(query)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.preferences)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.back),
                        )
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
        ) {
            sectionTitle(R.string.appearance)
            item { ThemeSetting(theme, onThemeChange) }
            item { TextSizeSetting(textSize, onTextSizeChange) }

            sectionTitle(R.string.translations_title)
            item {
                TranslationsIntro(
                    selected = translations.size,
                    total = translationLanguages.size,
                    onAll = { onTranslationsChange(translationLanguages.map { it.code }.toSet()) },
                    onNone = { onTranslationsChange(emptySet()) },
                )
            }
            item { FilterField(query, onQueryChange = { query = it }) }

            val shown = if (filter.isEmpty()) orderedLanguages
                        else orderedLanguages.filter { it.matches(filter) }

            items(shown, key = { it.code }) { language ->
                val checked = language.code in translations
                val toggle = {
                    onTranslationsChange(
                        if (checked) translations - language.code
                        else translations + language.code
                    )
                }

                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable(onClick = toggle)
                        .padding(start = 16.dp, end = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        language.name,
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.weight(1f),
                    )
                    Checkbox(checked = checked, onCheckedChange = { toggle() })
                }
            }
        }
    }
}

private fun LazyListScope.sectionTitle(title: Int) {
    item { SectionTitle(stringResource(title), Modifier.padding(top = 8.dp)) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ThemeSetting(theme: Theme, onThemeChange: (Theme) -> Unit) {
    val options = listOf(
        Theme.SYSTEM to R.string.theme_system,
        Theme.LIGHT to R.string.theme_light,
        Theme.DARK to R.string.theme_dark,
    )

    Column {
        Text(
            stringResource(R.string.theme),
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
        SingleChoiceSegmentedButtonRow(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            options.forEachIndexed { index, (value, label) ->
                SegmentedButton(
                    selected = theme == value,
                    onClick = { onThemeChange(value) },
                    shape = SegmentedButtonDefaults.itemShape(index, options.size),
                ) {
                    Text(stringResource(label))
                }
            }
        }
    }
}

/** What the translations setting is for, how many languages are
 * selected, and buttons to select all or none of them. */
@Composable
private fun TranslationsIntro(selected: Int, total: Int, onAll: () -> Unit, onNone: () -> Unit) {
    Column(Modifier.padding(horizontal = 16.dp)) {
        Text(
            stringResource(R.string.translations_help),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            stringResource(R.string.translations_count, selected, total),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(top = 8.dp),
        )
        Row(
            Modifier.padding(top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedButton(onClick = onAll, enabled = selected < total) {
                Text(stringResource(R.string.select_all))
            }
            OutlinedButton(onClick = onNone, enabled = selected > 0) {
                Text(stringResource(R.string.select_none))
            }
        }
    }
}

@Composable
private fun FilterField(query: String, onQueryChange: (String) -> Unit) {
    RoundedSearchField(
        query = query,
        onQueryChange = onQueryChange,
        placeholder = stringResource(R.string.search_language),
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
    )
}
