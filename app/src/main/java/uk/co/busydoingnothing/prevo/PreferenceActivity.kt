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

import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Bundle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.filled.Menu
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.zIndex
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.selection.selectable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

/*
 * The settings, on two levels: a main page with one line per setting and
 * its current value, and a page of their own for the translations, which
 * is a long list. Everything that used to be in the menus of the other
 * screens (the text size, the about dialog…) is here.
 */
class PreferenceActivity : AppCompatActivity() {

    private var theme by mutableStateOf(Theme.SYSTEM)
    private var textSize by mutableIntStateOf(PrevoSettings.DEFAULT_TEXT_SIZE)
    private var myLanguages by mutableStateOf(emptyList<String>())
    private var translations by mutableStateOf(emptyList<String>())

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

        val version = try {
            packageManager.getPackageInfo(packageName, 0).versionName
        } catch (e: PackageManager.NameNotFoundException) {
            null
        } ?: "?"

        setContent {
            PrevoTheme {
                var page by rememberSaveable { mutableStateOf(Page.MAIN) }

                when (page) {
                    Page.MAIN -> MainSettings(
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
                        translationsSummary = translationsSummary(),
                        onTranslations = { page = Page.TRANSLATIONS },
                        myLanguagesSummary = languageNames(myLanguages),
                        onMyLanguages = {
                            startActivity(
                                Intent(this, SelectLanguageActivity::class.java)
                                    .putExtra(SelectLanguageActivity.EXTRA_MANAGE, true)
                            )
                        },
                        version = version,
                        onBack = ::finish,
                    )
                    Page.TRANSLATIONS -> {
                        BackHandler { page = Page.MAIN }
                        TranslationSettings(
                            languages = translationLanguages,
                            translations = translations,
                            onTranslationsChange = {
                                translations = it
                                PrevoSettings.setTranslationLanguages(this, translationLanguages, it)
                            },
                            onBack = { page = Page.MAIN },
                        )
                    }
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()

        /* The user's languages may have been changed in the chooser */
        theme = PrevoSettings.getTheme(this)
        textSize = PrevoSettings.getTextSize(this)
        myLanguages = MyLanguages.get(this)
        translations = PrevoSettings.getTranslationLanguages(this, translationLanguages)
    }

    private fun languageNames(codes: List<String>): String? {
        val languageList = LanguageList.getDefault(this)
        return codes.takeIf { it.isNotEmpty() }?.joinToString(", ") { languageList.getLanguageName(it) }
    }

    @Composable
    private fun translationsSummary(): String = when (translations.size) {
        translationLanguages.size -> stringResource(R.string.translations_all, translations.size)
        0 -> stringResource(R.string.translations_none)
        else -> {
            val languageList = LanguageList.getDefault(this)
            translations.joinToString(", ") { languageList.getLanguageName(it) }
        }
    }
}

private enum class Page { MAIN, TRANSLATIONS }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsScaffold(
    title: String,
    onBack: () -> Unit,
    snackbarHost: SnackbarHostState? = null,
    bottomBar: @Composable () -> Unit = {},
    listState: LazyListState = rememberLazyListState(),
    content: LazyListScope.() -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back))
                    }
                },
            )
        },
        bottomBar = bottomBar,
        snackbarHost = { snackbarHost?.let { SnackbarHost(it) } },
    ) { padding ->
        LazyColumn(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding(),
            state = listState,
            content = content,
        )
    }
}

/** A big section title, with a line above it except for the first one. */
private fun LazyListScope.bigTitle(title: Int, first: Boolean = false) {
    item {
        Column {
            if (!first)
                HorizontalDivider(Modifier.padding(top = 12.dp))
            Text(
                stringResource(title),
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 4.dp),
            )
        }
    }
}

/** A setting: its name and current value, opening something on a tap. */
@Composable
private fun SettingRow(title: String, value: String?, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            value?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                )
            }
        }
        Icon(
            Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MainSettings(
    theme: Theme,
    onThemeChange: (Theme) -> Unit,
    textSize: Int,
    onTextSizeChange: (Int) -> Unit,
    translationsSummary: String,
    onTranslations: () -> Unit,
    myLanguagesSummary: String?,
    onMyLanguages: () -> Unit,
    version: String,
    onBack: () -> Unit,
) {
    var showTheme by remember { mutableStateOf(false) }
    var showTextSize by remember { mutableStateOf(false) }
    var showAbout by remember { mutableStateOf(false) }
    val themeNames = mapOf(
        Theme.SYSTEM to stringResource(R.string.theme_system),
        Theme.LIGHT to stringResource(R.string.theme_light),
        Theme.DARK to stringResource(R.string.theme_dark),
    )
    val percent = (PrevoSettings.textScale(textSize) * 100).roundToInt()
    val textSizeValue =
        if (textSize == PrevoSettings.DEFAULT_TEXT_SIZE) stringResource(R.string.text_size_default_value, percent)
        else stringResource(R.string.text_size_value, percent)

    SettingsScaffold(stringResource(R.string.preferences), onBack) {
        bigTitle(R.string.appearance, first = true)
        item { SettingRow(stringResource(R.string.theme), themeNames[theme]) { showTheme = true } }
        item { SettingRow(stringResource(R.string.text_size), textSizeValue) { showTextSize = true } }

        bigTitle(R.string.languages)
        item {
            SettingRow(stringResource(R.string.translations_title), translationsSummary, onTranslations)
        }
        item {
            SettingRow(
                stringResource(R.string.my_search_languages),
                myLanguagesSummary ?: stringResource(R.string.my_languages_automatic),
                onMyLanguages,
            )
        }

        bigTitle(R.string.about_section)
        item {
            SettingRow(
                stringResource(R.string.about_prevo),
                stringResource(R.string.version, version),
            ) { showAbout = true }
        }
    }

    if (showTheme) {
        AlertDialog(
            onDismissRequest = { showTheme = false },
            title = { Text(stringResource(R.string.theme)) },
            text = {
                Column {
                    for ((value, name) in themeNames) {
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .selectable(selected = theme == value) {
                                    onThemeChange(value)
                                    showTheme = false
                                }
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(selected = theme == value, onClick = null)
                            Text(name, Modifier.padding(start = 12.dp))
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showTheme = false }) { Text(stringResource(R.string.close)) }
            },
        )
    }

    if (showTextSize) {
        ModalBottomSheet(onDismissRequest = { showTextSize = false }) {
            Column(Modifier.padding(bottom = 32.dp)) {
                TextSizeSetting(textSize, onTextSizeChange)
            }
        }
    }

    if (showAbout)
        AboutDialog(onDismiss = { showAbout = false })
}

/** Which translations the articles show, and in which order. The
 * selected languages come first, in their order, which can be changed
 * by dragging them after a long press. The buttons that change all of
 * them at once are at the bottom, and can be undone. */
@Composable
private fun TranslationSettings(
    languages: List<Language>,
    translations: List<String>,
    onTranslationsChange: (List<String>) -> Unit,
    onBack: () -> Unit,
) {
    var query by rememberSaveable { mutableStateOf("") }
    val filter = normaliseQuery(query)
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    val allSelected = stringResource(R.string.all_selected)
    val noneSelected = stringResource(R.string.none_selected)
    val undo = stringResource(R.string.undo)

    val byCode = languages.associateBy { it.code }
    val selected = translations.mapNotNull { byCode[it] }
    val others = languages.filter { it.code !in translations }

    /* The language being dragged, and how far from its place */
    var dragged by remember { mutableStateOf<String?>(null) }
    var dragOffset by remember { mutableFloatStateOf(0f) }
    val currentTranslations by rememberUpdatedState(translations)
    val currentOnChange by rememberUpdatedState(onTranslationsChange)

    fun changeAll(selection: List<String>, message: String) {
        val previous = translations
        onTranslationsChange(selection)
        scope.launch {
            snackbar.currentSnackbarData?.dismiss()
            if (snackbar.showSnackbar(message, actionLabel = undo, withDismissAction = true) ==
                SnackbarResult.ActionPerformed)
                onTranslationsChange(previous)
        }
    }

    /* Moves the dragged language over the selected one under its middle */
    fun dragBy(delta: Float) {
        val code = dragged ?: return
        dragOffset += delta

        val items = listState.layoutInfo.visibleItemsInfo
        val current = items.firstOrNull { it.key == code } ?: return
        val middle = current.offset + dragOffset + current.size / 2
        val target = items.firstOrNull {
            it.key != code && it.key in currentTranslations &&
                middle >= it.offset && middle < it.offset + it.size
        } ?: return

        val order = currentTranslations.toMutableList()
        order.remove(code)
        order.add(currentTranslations.indexOf(target.key as String), code)
        /* The row moves to its new place, so the offset is now from there */
        dragOffset -= target.offset - current.offset
        currentOnChange(order)
    }

    SettingsScaffold(
        stringResource(R.string.translations_title),
        onBack,
        snackbarHost = snackbar,
        listState = listState,
        bottomBar = {
            Surface(color = MaterialTheme.colorScheme.surfaceContainer) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    OutlinedButton(
                        /* The ones already selected keep their order */
                        onClick = { changeAll(translations + others.map { it.code }, allSelected) },
                        enabled = others.isNotEmpty(),
                    ) { Text(stringResource(R.string.select_all)) }
                    OutlinedButton(
                        onClick = { changeAll(emptyList(), noneSelected) },
                        enabled = translations.isNotEmpty(),
                    ) { Text(stringResource(R.string.select_none)) }
                }
            }
        },
    ) {
        item {
            Text(
                stringResource(R.string.translations_help),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )
        }
        item {
            RoundedSearchField(
                query = query,
                onQueryChange = { query = it },
                placeholder = stringResource(R.string.search_language),
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }

        @Composable
        fun LanguageCheckRow(language: Language, modifier: Modifier = Modifier, trailing: @Composable () -> Unit = {}) {
            val checked = language.code in translations
            val toggle = {
                onTranslationsChange(if (checked) translations - language.code else translations + language.code)
            }
            Row(
                modifier
                    .fillMaxWidth()
                    .clickable(onClick = toggle)
                    .padding(start = 16.dp, end = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(language.name, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                trailing()
                Checkbox(checked = checked, onCheckedChange = { toggle() })
            }
        }

        if (filter.isNotEmpty()) {
            /* A search lists every match, ticked or not, in one list */
            for (language in languages.filter { it.matches(filter) })
                item(key = language.code) { LanguageCheckRow(language) }
            return@SettingsScaffold
        }

        if (selected.isNotEmpty()) {
            item(key = "selected") {
                SectionTitle(stringResource(R.string.selected_languages, selected.size))
            }
            item(key = "order-help") {
                Text(
                    stringResource(R.string.translations_order_help),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 4.dp),
                )
            }
        }

        for (language in selected) {
            item(key = language.code) {
                val isDragged = dragged == language.code
                val elevation by animateDpAsState(if (isDragged) 6.dp else 0.dp, label = "drag")

                Surface(
                    shadowElevation = elevation,
                    color = MaterialTheme.colorScheme.surface,
                    modifier = Modifier
                        .zIndex(if (isDragged) 1f else 0f)
                        .graphicsLayer { translationY = if (isDragged) dragOffset else 0f }
                        .then(if (isDragged) Modifier else Modifier.animateItem())
                        .pointerInput(language.code) {
                            detectDragGesturesAfterLongPress(
                                onDragStart = { dragged = language.code; dragOffset = 0f },
                                onDrag = { change, amount -> change.consume(); dragBy(amount.y) },
                                onDragEnd = { dragged = null; dragOffset = 0f },
                                onDragCancel = { dragged = null; dragOffset = 0f },
                            )
                        },
                ) {
                    LanguageCheckRow(language) {
                        Icon(
                            Icons.Default.Menu,
                            contentDescription = stringResource(R.string.drag_to_reorder),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 8.dp),
                        )
                    }
                }
            }
        }

        if (others.isNotEmpty())
            item(key = "others") { SectionTitle(stringResource(R.string.other_languages)) }

        for (language in others)
            item(key = language.code) { LanguageCheckRow(language, Modifier.animateItem()) }
    }
}
