/*
 * PReVo - A portable version of ReVo for Android
 * Copyright (C) 2012, 2013, 2016, 2018  Neil Roberts
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

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.text.method.LinkMovementMethod
import android.util.Log
import android.util.TypedValue
import android.view.KeyEvent
import android.widget.TextView
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import java.io.IOException
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch

class ArticleActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_ARTICLE_NUMBER = "uk.co.busydoingnothing.prevo.ArticleNumber"
        const val EXTRA_MARK_NUMBER = "uk.co.busydoingnothing.prevo.MarkNumber"

        /* http://www.openintents.org/en/node/720
         * This probably only works with AnkiDroid 2.0 */
        private const val ACTION_CREATE_FLASHCARD = "org.openintents.action.CREATE_FLASHCARD"
        const val SOURCE_TEXT = "SOURCE_TEXT"
        const val TARGET_TEXT = "TARGET_TEXT"

        private const val TAG = "prevoarticle"
    }

    private var articleNumber = -1
    private var article by mutableStateOf<Article?>(null)
    private var textSize by mutableIntStateOf(PrevoSettings.DEFAULT_TEXT_SIZE)
    private var showNoFlashcard by mutableStateOf(false)

    /* The translations that were shown when the article was loaded, to
     * reload it if they are changed in the settings */
    private var loadedTranslations: Set<String>? = null

    /* Sections to scroll to: the one of the intent, then the ones of the
     * links within the article */
    private val sectionRequests = MutableSharedFlow<Int>(extraBufferCapacity = 1)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        articleNumber = intent.getIntExtra(EXTRA_ARTICLE_NUMBER, -1)
        val mark = intent.getIntExtra(EXTRA_MARK_NUMBER, -1)

        setContent {
            PrevoTheme {
                var showAbout by remember { mutableStateOf(false) }

                ArticleScreen(
                    article = article,
                    textSize = textSize,
                    initialSection = if (savedInstanceState == null) mark else -1,
                    sectionRequests = sectionRequests,
                    onBack = ::finish,
                    onSearch = { MenuHelper.goSearch(this) },
                    onChooseLanguage = { MenuHelper.goChooseLanguage(this) },
                    onPreferences = { MenuHelper.goPreferences(this) },
                    onAbout = { showAbout = true },
                    onTextSizeChange = {
                        textSize = it
                        PrevoSettings.setTextSize(this, it)
                    },
                    onCopy = ::copyDefinition,
                    onFlashcard = ::createFlashcard,
                    onLookUpInPiv = ::lookUpInPiv,
                    onShare = ::share,
                )

                if (showAbout)
                    AboutDialog(onDismiss = { showAbout = false })

                if (showNoFlashcard) {
                    AlertDialog(
                        onDismissRequest = { showNoFlashcard = false },
                        text = { Text(stringResource(R.string.no_flashcard)) },
                        confirmButton = {
                            TextButton(onClick = { showNoFlashcard = false }) {
                                Text(stringResource(R.string.close))
                            }
                        },
                    )
                }
            }
        }
    }


    override fun onStart() {
        super.onStart()

        /* The settings may have changed while another screen was shown */
        textSize = PrevoSettings.getTextSize(this)

        val translations = currentTranslations()
        if (article == null || translations != loadedTranslations) {
            loadArticle()
            loadedTranslations = translations
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (keyCode == KeyEvent.KEYCODE_SEARCH) {
            MenuHelper.goSearch(this)
            return true
        }

        return super.onKeyDown(keyCode, event)
    }

    private fun currentTranslations(): Set<String> {
        val languages = LanguageList.getDefault(this).allLanguages.toList()
        return PrevoSettings.getTranslationLanguages(this, languages)
    }

    private fun loadArticle() {
        if (articleNumber < 0)
            return

        try {
            article = ArticleLoader.load(
                this,
                articleNumber,
                onShowSection = { sectionRequests.tryEmit(it) },
                quoteColor = QUOTE_COLOR,
            )
        } catch (e: IOException) {
            Log.wtf(TAG, "Error while loading an asset", e)
        }
    }

    private fun copyDefinition(section: ArticleSection) {
        SpannedCopy.copyText(this, getText(R.string.definition_label), section.content)
    }

    private fun createFlashcard(source: CharSequence, target: CharSequence) {
        val intent = Intent(ACTION_CREATE_FLASHCARD)
            .putExtra(SOURCE_TEXT, source.toString())
            .putExtra(TARGET_TEXT, target.toString())

        try {
            startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            Log.i(TAG, "Failed to start activity: ${e.message}")
            showNoFlashcard = true
        }
    }

    private fun lookUpInPiv(word: CharSequence) {
        /* Only the letters and a few punctuation marks, in lowercase.
         * Stop at the first comma, which usually separates several words
         * with the same definition: searching for all of them is useless. */
        val query = StringBuilder()
        for (ch in word) {
            if (ch == ',')
                break
            if (ch.isLetter() || ch in " .-'!’()")
                query.append(ch.lowercaseChar())
        }

        val uri = Uri.Builder()
            .scheme("https")
            .encodedPath("//vortaro.net/")
            .fragment(query.toString())
            .build()

        try {
            startActivity(Intent(Intent.ACTION_VIEW, uri))
        } catch (e: ActivityNotFoundException) {
            Log.w(TAG, "Failed to start activity: ${e.message}")
        }
    }

    private fun share(section: ArticleSection) {
        val intent = Intent(Intent.ACTION_SEND)
            .setType("text/plain")
            .putExtra(Intent.EXTRA_TEXT, "${section.header}\n\n${section.content}")

        startActivity(Intent.createChooser(intent, null))
    }
}

/* A neutral grey that is readable in both light and dark themes */
private const val QUOTE_COLOR = 0xFF9E9E9E.toInt()

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ArticleScreen(
    article: Article?,
    textSize: Int,
    initialSection: Int,
    sectionRequests: MutableSharedFlow<Int>,
    onBack: () -> Unit,
    onSearch: () -> Unit,
    onChooseLanguage: () -> Unit,
    onPreferences: () -> Unit,
    onAbout: () -> Unit,
    onTextSizeChange: (Int) -> Unit,
    onCopy: (ArticleSection) -> Unit,
    onFlashcard: (CharSequence, CharSequence) -> Unit,
    onLookUpInPiv: (CharSequence) -> Unit,
    onShare: (ArticleSection) -> Unit,
) {
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    var actionsFor by remember { mutableStateOf<ArticleSection?>(null) }
    var showTextSize by remember { mutableStateOf(false) }

    /* Show the section the article was opened at, once it is loaded */
    LaunchedEffect(article != null) {
        val sections = article?.sections ?: return@LaunchedEffect
        if (initialSection in sections.indices)
            listState.scrollToItem(initialSection)
    }

    /* Links to another section of the same article. This keeps running,
     * so it needs to see the current article, not the one it started
     * with. */
    val currentArticle by rememberUpdatedState(article)
    LaunchedEffect(Unit) {
        sectionRequests.collect { section ->
            if (section in (currentArticle?.sections?.indices ?: IntRange.EMPTY))
                scope.launch { listState.animateScrollToItem(section) }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        article?.title?.toString() ?: "",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back))
                    }
                },
                actions = {
                    IconButton(onClick = onSearch) {
                        Icon(Icons.Default.Search, stringResource(R.string.menu_search))
                    }
                    ArticleMenu(
                        onChooseLanguage = onChooseLanguage,
                        onTextSize = { showTextSize = true },
                        onPreferences = onPreferences,
                        onAbout = onAbout,
                    )
                },
            )
        },
    ) { padding ->
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = padding,
        ) {
            itemsIndexed(article?.sections ?: emptyList()) { _, section ->
                Section(section, textSize, onLongPress = { actionsFor = section })
            }
        }
    }

    actionsFor?.let { section ->
        DefinitionActions(
            section = section,
            onDismiss = { actionsFor = null },
            onCopy = { onCopy(section) },
            /* "definition → word": the definition is asked, the word is
             * the answer, and the other way round */
            onFlashcardWord = { onFlashcard(section.content, section.header) },
            onFlashcardDefinition = { onFlashcard(section.header, section.content) },
            onLookUpInPiv = { onLookUpInPiv(section.header) },
            onShare = { onShare(section) },
        )
    }

    if (showTextSize) {
        ModalBottomSheet(onDismissRequest = { showTextSize = false }) {
            Column(Modifier.padding(bottom = 32.dp)) {
                TextSizeSetting(textSize, onTextSizeChange)
            }
        }
    }
}

@Composable
private fun ArticleMenu(
    onChooseLanguage: () -> Unit,
    onTextSize: () -> Unit,
    onPreferences: () -> Unit,
    onAbout: () -> Unit,
) {
    var open by remember { mutableStateOf(false) }

    IconButton(onClick = { open = true }) {
        Icon(Icons.Default.MoreVert, stringResource(R.string.more_options))
    }
    DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
        DropdownMenuItem(
            text = { Text(stringResource(R.string.menu_choose_language)) },
            onClick = { open = false; onChooseLanguage() },
        )
        DropdownMenuItem(
            text = { Text(stringResource(R.string.text_size)) },
            onClick = { open = false; onTextSize() },
        )
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

/** A section: its header, then its content. The texts are drawn by
 * TextViews, which already handle the spans of the articles (links,
 * superscripts, quotations…). */
@Composable
private fun Section(section: ArticleSection, textSize: Int, onLongPress: () -> Unit) {
    val scale = PrevoSettings.textScale(textSize)
    val colors = MaterialTheme.colorScheme
    val headerSize = MaterialTheme.typography.titleSmall.fontSize.value * scale
    val bodySize = MaterialTheme.typography.bodyLarge.fontSize.value * scale

    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        AndroidView(
            factory = { context ->
                TextView(context).apply {
                    isAllCaps = true
                    typeface = android.graphics.Typeface.DEFAULT_BOLD
                }
            },
            update = { view ->
                view.text = section.header
                view.setTextColor(colors.primary.toArgb())
                view.setTextSize(TypedValue.COMPLEX_UNIT_SP, headerSize)
            },
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 4.dp),
        )
        AndroidView(
            factory = { context ->
                TextView(context).apply {
                    movementMethod = LinkMovementMethod.getInstance()
                    setLineSpacing(0f, 1.2f)
                }
            },
            update = { view ->
                view.setText(section.content, TextView.BufferType.SPANNABLE)
                view.setTextColor(colors.onSurface.toArgb())
                view.setLinkTextColor(colors.primary.toArgb())
                view.setTextSize(TypedValue.COMPLEX_UNIT_SP, bodySize)
                view.setOnLongClickListener {
                    onLongPress()
                    true
                }
            },
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/** What can be done with a definition, shown on a long press. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DefinitionActions(
    section: ArticleSection,
    onDismiss: () -> Unit,
    onCopy: () -> Unit,
    onFlashcardWord: () -> Unit,
    onFlashcardDefinition: () -> Unit,
    onLookUpInPiv: () -> Unit,
    onShare: () -> Unit,
) {
    val actions = listOf(
        R.string.menu_copy_definition to onCopy,
        R.string.share to onShare,
        R.string.menu_create_flashcard_word to onFlashcardWord,
        R.string.menu_create_flashcard_definition to onFlashcardDefinition,
        R.string.menu_look_up_in_piv to onLookUpInPiv,
    )

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Text(
            section.header.toString(),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )
        for ((label, action) in actions) {
            ListItem(
                headlineContent = { Text(stringResource(label)) },
                modifier = Modifier.clickable { onDismiss(); action() },
            )
        }
    }
}
