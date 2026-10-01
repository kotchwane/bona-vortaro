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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

class PreferenceActivity : AppCompatActivity() {

    private var theme by mutableStateOf(Theme.SYSTEM)
    private var textSize by mutableStateOf(PrevoSettings.DEFAULT_TEXT_SIZE)
    private var myLanguages by mutableStateOf(emptyList<Language>())
    private var translations by mutableStateOf(emptySet<String>())

    /** Every language that can be a translation, i.e. all but Esperanto */
    private lateinit var translationLanguages: List<Language>

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
                    myLanguages = myLanguages,
                    onMoveLanguage = { language, delta ->
                        myLanguages = toLanguages(MyLanguages.move(this, language.code, delta))
                    },
                    onRemoveLanguage = {
                        myLanguages = toLanguages(MyLanguages.toggle(this, it.code))
                    },
                    translationLanguages = translationLanguages,
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
        myLanguages = toLanguages(MyLanguages.get(this))
        translations = PrevoSettings.getTranslationLanguages(this, translationLanguages)
    }

    private fun toLanguages(codes: List<String>): List<Language> {
        val languageList = LanguageList.getDefault(this)
        return codes.map { Language(languageList.getLanguageName(it), it) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsScreen(
    theme: Theme,
    onThemeChange: (Theme) -> Unit,
    textSize: Int,
    onTextSizeChange: (Int) -> Unit,
    myLanguages: List<Language>,
    onMoveLanguage: (Language, Int) -> Unit,
    onRemoveLanguage: (Language) -> Unit,
    translationLanguages: List<Language>,
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

            sectionTitle(R.string.my_languages)
            myLanguagesSetting(myLanguages, onMoveLanguage, onRemoveLanguage)

            sectionTitle(R.string.show_translations)
            item {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    TextButton(onClick = {
                        onTranslationsChange(translationLanguages.map { it.code }.toSet())
                    }) {
                        Text(stringResource(R.string.show_all_translations))
                    }
                    TextButton(onClick = { onTranslationsChange(emptySet()) }) {
                        Text(stringResource(R.string.show_no_translations))
                    }
                }
            }
            item { FilterField(query, onQueryChange = { query = it }) }

            val shown = if (filter.isEmpty()) translationLanguages
                        else translationLanguages.filter { it.matches(filter) }

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

@Composable
private fun TextSizeSetting(textSize: Int, onTextSizeChange: (Int) -> Unit) {
    val scale = PrevoSettings.textScale(textSize)
    val body = MaterialTheme.typography.bodyLarge

    Column {
        Text(
            stringResource(R.string.text_size),
            style = body,
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp),
        )
        Slider(
            value = textSize.toFloat(),
            onValueChange = { onTextSizeChange(it.roundToInt()) },
            valueRange = 0f..(PrevoSettings.TEXT_SIZE_COUNT - 1).toFloat(),
            steps = PrevoSettings.TEXT_SIZE_COUNT - 2,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
        /* A preview at the size the articles will use */
        Text(
            stringResource(R.string.text_size_sample),
            style = body.copy(fontSize = body.fontSize * scale, lineHeight = body.lineHeight * scale),
            modifier = Modifier.padding(horizontal = 16.dp),
        )
    }
}

private fun LazyListScope.myLanguagesSetting(
    languages: List<Language>,
    onMove: (Language, Int) -> Unit,
    onRemove: (Language) -> Unit,
) {
    if (languages.isEmpty()) {
        item {
            Text(
                stringResource(R.string.my_languages_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
        }
        return
    }

    itemsIndexed(languages, key = { _, language -> "mine-" + language.code }) { index, language ->
        Row(
            Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                language.name,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = { onMove(language, -1) }, enabled = index > 0) {
                Icon(Icons.Default.KeyboardArrowUp, stringResource(R.string.move_up))
            }
            IconButton(onClick = { onMove(language, 1) }, enabled = index < languages.size - 1) {
                Icon(Icons.Default.KeyboardArrowDown, stringResource(R.string.move_down))
            }
            IconButton(onClick = { onRemove(language) }) {
                Icon(Icons.Default.Close, stringResource(R.string.remove_from_my_languages))
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
