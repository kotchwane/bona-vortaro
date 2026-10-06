/*
 * Bona vortaro - an Esperanto dictionary for Android, based on PReVo
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

package io.github.kotchwane.bonavortaro

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.mutableStateListOf
import androidx.activity.compose.BackHandler
import android.util.LruCache
import androidx.compose.foundation.layout.size
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.height
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.foundation.layout.Spacer
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.produceState
import androidx.compose.material3.Surface
import androidx.compose.material3.HorizontalDivider
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.BorderStroke
import android.net.Uri
import android.os.Bundle
import android.text.method.LinkMovementMethod
import android.util.Log
import android.util.TypedValue
import android.view.KeyEvent
import android.widget.TextView
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Alignment
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.material.icons.filled.Settings
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import java.io.IOException
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch

class ArticleActivity : BonaActivity() {

    companion object {
        const val EXTRA_ARTICLE_NUMBER = "io.github.kotchwane.bonavortaro.ArticleNumber"
        const val EXTRA_MARK_NUMBER = "io.github.kotchwane.bonavortaro.MarkNumber"

        /* http://www.openintents.org/en/node/720
         * This probably only works with AnkiDroid 2.0 */
        private const val ACTION_CREATE_FLASHCARD = "org.openintents.action.CREATE_FLASHCARD"
        const val SOURCE_TEXT = "SOURCE_TEXT"
        const val TARGET_TEXT = "TARGET_TEXT"

        private const val TAG = "bonaarticle"
        private const val STATE_ROOTS = "roots"
    }

    /* The roots read on this screen, the one shown last: the previous,
     * next and random roots are added to it, in place, and back takes
     * them off again, root by root */
    private val roots = mutableStateListOf<Int>()
    private val articleNumber get() = roots.lastOrNull() ?: -1

    /* The articles already read: those of the cards of the previous and
     * next roots, and those of the roots before, for back */
    private val articleCache = LruCache<Int, Article>(16)
    private var article by mutableStateOf<Article?>(null)
    private var textSize by mutableIntStateOf(BonaSettings.DEFAULT_TEXT_SIZE)
    private var unfoldExamples by mutableStateOf(false)
    private var foldTranslations by mutableStateOf(true)

    /* The user's languages, whose translations are shown first */
    private var preferredLanguages by mutableStateOf(emptyList<String>())
    private var showNoFlashcard by mutableStateOf(false)

    /* The translations that were shown when the article was loaded, to
     * reload it if they are changed in the settings */
    private var loadedTranslations: Set<String>? = null

    /* Sections to scroll to: the one of the intent, then the ones of the
     * links within the article */
    private val sectionRequests = MutableSharedFlow<Int>(extraBufferCapacity = 1)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        roots.addAll(
            savedInstanceState?.getIntArray(STATE_ROOTS)?.toList()
                ?: listOf(intent.getIntExtra(EXTRA_ARTICLE_NUMBER, -1))
        )
        val mark = intent.getIntExtra(EXTRA_MARK_NUMBER, -1)

        setContent {
            /* Each root read keeps its own state, its scroll above all,
             * to find it as it was left on the way back */
            val rootStates = rememberSaveableStateHolder()
            val depth = roots.size

            BackHandler(enabled = depth > 1) {
                rootStates.removeState(rootKey(depth))
                roots.removeAt(roots.lastIndex)
                showCurrentRoot()
            }

            BonaTheme {
              rootStates.SaveableStateProvider(rootKey(depth)) {
                ArticleScreen(
                    articleNumber = articleNumber,
                    article = article,
                    textSize = textSize,
                    unfoldExamples = unfoldExamples,
                    foldTranslations = foldTranslations,
                    preferredLanguages = preferredLanguages,
                    /* The section of the intent, for the root it opened */
                    initialSection = if (savedInstanceState == null && depth == 1) mark else -1,
                    sectionRequests = sectionRequests,
                    onSearch = { Screens.goSearch(this) },
                    onPreferences = { Screens.goPreferences(this) },
                    onCopy = ::copyDefinition,
                    onFlashcard = ::createFlashcard,
                    onLookUpInPiv = ::lookUpInPiv,
                    onShare = ::share,
                    onOpenNeighbour = ::openRoot,
                    onOpenRandom = ::openRoot,
                    onOpenEntry = ::openEntry,
                    onCopyWord = ::copyWord,
                    loadArticle = ::loadCachedArticle,
                )
              }

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
        textSize = BonaSettings.getTextSize(this)
        unfoldExamples = BonaSettings.getUnfoldExamples(this)
        preferredLanguages = BonaSettings.getTranslationOrder(this)

        val translations = currentTranslations()
        /* The translations are folded only when all of the languages
         * are shown: the ones the user chose are all shown */
        foldTranslations = translations.size == LanguageList.getDefault(this).allLanguages.size
        if (article == null || translations != loadedTranslations) {
            /* The articles read before show other translations */
            if (translations != loadedTranslations)
                articleCache.evictAll()
            showCurrentRoot()
            loadedTranslations = translations
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (keyCode == KeyEvent.KEYCODE_SEARCH) {
            Screens.goSearch(this)
            return true
        }

        return super.onKeyDown(keyCode, event)
    }

    /* Only which languages, not their order: the order doesn't need
     * the article to be loaded again */
    private fun currentTranslations(): Set<String> {
        val languages = LanguageList.getDefault(this).allLanguages.toList()
        return BonaSettings.getTranslationLanguages(this, languages).toSet()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putIntArray(STATE_ROOTS, roots.toIntArray())
    }

    /* The key of the state of the root at a depth: the same root read
     * twice in a row has two states */
    private fun rootKey(depth: Int) = "$depth:${roots.getOrNull(depth - 1)}"

    /** An article, from the cache if it was read already. Disk access
     * otherwise. */
    @Throws(IOException::class)
    private fun loadCachedArticle(number: Int): Article =
        articleCache.get(number)
            ?: ArticleLoader.load(
                this,
                number,
                onShowSection = { sectionRequests.tryEmit(it) },
                quoteColor = QUOTE_COLOR,
            ).also { articleCache.put(number, it) }

    private fun showCurrentRoot() {
        if (articleNumber < 0)
            return

        article = try {
            loadCachedArticle(articleNumber)
        } catch (e: IOException) {
            Log.wtf(TAG, "Error while loading an asset", e)
            null
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

    /* The previous, next or random root replaces this one on the same
     * screen, at once, without the animation of a new screen. Back
     * returns to this one, where it was left; the search, at the top
     * left, to the search at once */
    private fun openRoot(number: Int) {
        roots.add(number)
        showCurrentRoot()
    }

    /* An entry found from a word of the text, on top of this article,
     * as a link would */
    private fun openEntry(entry: SearchResult) {
        startActivity(
            Intent(this, ArticleActivity::class.java)
                .putExtra(EXTRA_ARTICLE_NUMBER, entry.article)
                .putExtra(EXTRA_MARK_NUMBER, entry.mark)
        )
    }

    private fun copyWord(word: String) {
        SpannedCopy.copyText(this, getText(R.string.word_label), word)
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
    articleNumber: Int,
    article: Article?,
    textSize: Int,
    unfoldExamples: Boolean,
    foldTranslations: Boolean,
    preferredLanguages: List<String>,
    initialSection: Int,
    sectionRequests: MutableSharedFlow<Int>,
    onSearch: () -> Unit,
    onPreferences: () -> Unit,
    onCopy: (ArticleSection) -> Unit,
    onFlashcard: (CharSequence, CharSequence) -> Unit,
    onLookUpInPiv: (CharSequence) -> Unit,
    onShare: (ArticleSection) -> Unit,
    onOpenNeighbour: (Int) -> Unit,
    onOpenRandom: (Int) -> Unit,
    onOpenEntry: (SearchResult) -> Unit,
    onCopyWord: (String) -> Unit,
    loadArticle: (Int) -> Article,
) {
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    var actionsFor by remember { mutableStateOf<ArticleSection?>(null) }
    /* Read with the article, so that they are there before the end of it
     * can be reached */
    val context = LocalContext.current
    val neighbours by produceState<List<Neighbour?>?>(null, articleNumber) {
        value = withContext(Dispatchers.IO) { loadNeighbours(loadArticle, articleNumber) }
    }
    /* The word pressed in the text, and its language */
    var wordFor by remember { mutableStateOf<Pair<String, String>?>(null) }

    /* One card per word, with its translations */
    val colors = MaterialTheme.colorScheme
    val density = LocalDensity.current.density
    val layout = remember(article, preferredLanguages, colors) {
        article?.let {
            layoutArticle(
                it,
                preferredLanguages,
                markLabel = {
                    InlineLabelSpan(
                        background = colors.secondaryContainer.toArgb(),
                        foreground = colors.onSecondaryContainer.toArgb(),
                        radius = 6 * density,
                        padding = 4 * density,
                    )
                },
                markLinkType = { type, label ->
                    LinkTypeLabelSpan(
                        label = label,
                        /* Only primary and tertiary are strong colours
                         * with the wallpaper's colours, secondary is
                         * almost grey like outline. The words tell the
                         * hierarchy and the parts apart. */
                        color = when (type.family) {
                            LinkFamily.HIERARCHY, LinkFamily.PARTS -> colors.primary
                            LinkFamily.EQUIVALENCE -> colors.tertiary
                            LinkFamily.OTHER -> colors.outline
                        }.toArgb(),
                        radius = 50 * density,
                        padding = 5 * density,
                        stroke = 1 * density,
                    )
                },
            )
        }
    }

    /* Where the card of each section is in the list */
    val positions = remember(layout) {
        layout?.words?.mapIndexed { position, word -> word.section to position }?.toMap() ?: emptyMap()
    }

    /* Show the section the article was opened at, once it is loaded, and
     * only once: coming back to this root, its own scroll is restored */
    var showedSection by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(layout != null) {
        if (layout != null && !showedSection) {
            positions[initialSection]?.let { listState.scrollToItem(it) }
            showedSection = true
        }
    }

    /* Links to another section of the same article. This keeps running,
     * so it needs to see the current article, not the one it started
     * with. */
    val currentPositions by rememberUpdatedState(positions)
    LaunchedEffect(Unit) {
        sectionRequests.collect { section ->
            currentPositions[section]?.let { scope.launch { listState.animateScrollToItem(it) } }
        }
    }

    BonaScaffold(
        topBar = {
            BonaTopBar(
                title = {
                    val (marks, title) = article?.title?.let { splitOfficial(it) } ?: (emptyList<String>() to "")

                    /* The officiality of the root of the article, which
                     * the words of the article share */
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            title.toString(),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false),
                        )
                        for (mark in marks)
                            OfficialBadge(mark, Modifier.padding(start = 8.dp))
                    }
                },
                /* No back arrow, which the back of the phone does: in its
                 * place, the search, where the way back usually is and away
                 * from the settings; larger, as it is the main way back */
                onBack = null,
                navigation = {
                    IconButton(onClick = onSearch, modifier = Modifier.size(56.dp)) {
                        Icon(Icons.Default.Search, stringResource(R.string.menu_search), Modifier.size(28.dp))
                    }
                },
                actions = {
                    IconButton(onClick = onPreferences) {
                        Icon(Icons.Default.Settings, stringResource(R.string.preferences))
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 12.dp,
                end = 12.dp,
                top = padding.calculateTopPadding() + 4.dp,
                bottom = padding.calculateBottomPadding() + 16.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(layout?.words ?: emptyList(), key = { it.section }) { word ->
                WordCard(
                    word, textSize, unfoldExamples, foldTranslations,
                    onLongPress = { actionsFor = word.source },
                    onLongPressWord = { pressed, language -> wordFor = pressed to language },
                )
            }
            /* Translations that couldn't be given to a word, if any */
            items(layout?.otherTranslations ?: emptyList()) { section ->
                Section(section, textSize, onLongPress = { actionsFor = section })
            }
            /* The roots before and after, once the article is there, and
             * whole: if it grew after a scroll reached the end, the cards
             * would be left below the screen */
            if (layout != null && neighbours != null)
                item(key = "neighbours") {
                    NeighbourRoots(
                        neighbours!!,
                        onOpen = onOpenNeighbour,
                        onRandom = { scope.launch { onOpenRandom(withContext(Dispatchers.IO) { RandomRoot.pick(context) }) } },
                    )
                }
        }
    }

    wordFor?.let { (word, language) ->
        WordActions(
            word = word,
            language = language,
            onDismiss = { wordFor = null },
            onOpen = onOpenEntry,
            onCopy = { onCopyWord(word) },
        )
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
}


/** A section: its header, then its content. The texts are drawn by
 * TextViews, which already handle the spans of the articles (links,
 * superscripts, quotations…). */
@Composable
private fun Section(section: ArticleSection, textSize: Int, onLongPress: () -> Unit) {
    val scale = BonaSettings.textScale(textSize)
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

/** What can be done with a word of the text, shown on a long press: its
 * entries in the dictionary, found from the form it has in the text
 * ("arbojn" → arbo), each with the start of its definition, then copy
 * it. Vortaro.net stays in the menu of the headword: this one keeps to
 * the dictionary. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WordActions(
    word: String,
    language: String,
    onDismiss: () -> Unit,
    onOpen: (SearchResult) -> Unit,
    onCopy: () -> Unit,
) {
    val context = LocalContext.current
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    /* null while it is looked up */
    val entries by produceState<List<Pair<SearchResult, WordPreview?>>?>(null, word, language) {
        value = withContext(Dispatchers.IO) {
            WordLookup.find(context, word, language).map { it to SearchPreviews.get(context, it) }
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Text(
            if (language == "eo") word
            else "$word (${LanguageList.getDefault(context).getLanguageName(language)})",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )
        when {
            entries == null -> {}
            entries!!.isEmpty() -> Text(
                stringResource(R.string.word_not_found, word),
                style = MaterialTheme.typography.bodyMedium.copy(fontStyle = FontStyle.Italic),
                color = muted,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
            else -> for ((entry, preview) in entries!!)
                ListItem(
                    headlineContent = {
                        Text(
                            preview?.title ?: entry.word,
                            fontWeight = FontWeight.Bold,
                            color = bonaDesign.headword,
                        )
                    },
                    supportingContent = preview?.let {
                        {
                            Text(
                                it.definition,
                                style = MaterialTheme.typography.bodyMedium.copy(fontStyle = FontStyle.Italic),
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    },
                    modifier = Modifier.clickable { onDismiss(); onOpen(entry) },
                )
        }
        HorizontalDivider(Modifier.padding(horizontal = 16.dp, vertical = 4.dp), color = muted.copy(alpha = 0.25f))
        ListItem(
            headlineContent = { Text(stringResource(R.string.copy_word)) },
            modifier = Modifier.clickable { onDismiss(); onCopy() },
        )
        Spacer(Modifier.height(16.dp))
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


/** An article next to this one, in the order of the dictionary */
private class Neighbour(val number: Int, val root: String, val definition: String)

/** The articles before and after one, null where there is none: before
 * the first article, and after the last. They are read as the article
 * itself, into its cache, so that opening them reads nothing more. Disk
 * access. */
private fun loadNeighbours(loadArticle: (Int) -> Article, articleNumber: Int): List<Neighbour?> =
    listOf(articleNumber - 1, articleNumber + 1).map { number ->
        if (number < 0) return@map null
        try {
            val article = loadArticle(number)
            Neighbour(
                number,
                splitOfficial(article.title).second.toString(),
                previewOf(article, 0)?.definition.orEmpty(),
            )
        } catch (e: IOException) {
            null
        }
    }

/** The roots just before and after this one, as on the page of a printed
 * dictionary, to read on from one to the next, and between them a root
 * drawn at random. A tap opens it. */
@Composable
private fun NeighbourRoots(neighbours: List<Neighbour?>, onOpen: (Int) -> Unit, onRandom: () -> Unit) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant

    Column(Modifier.fillMaxWidth().padding(top = 14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)) {
            HorizontalDivider(Modifier.weight(1f), color = muted.copy(alpha = 0.4f))
            Text(
                stringResource(R.string.neighbours_title),
                style = MaterialTheme.typography.bodySmall.copy(fontStyle = FontStyle.Italic),
                color = muted,
                modifier = Modifier.padding(horizontal = 10.dp),
            )
            HorizontalDivider(Modifier.weight(1f), color = muted.copy(alpha = 0.4f))
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            /* The three cards as high as the highest */
            modifier = Modifier.padding(top = 6.dp).height(IntrinsicSize.Min),
        ) {
            for ((index, neighbour) in neighbours.withIndex()) {
                val before = index == 0
                if (index == 1)
                    RandomRootCard(onRandom, Modifier.width(84.dp).fillMaxHeight())
                if (neighbour == null) {
                    Spacer(Modifier.weight(1f))
                    continue
                }
                Surface(
                    onClick = { onOpen(neighbour.number) },
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    border = BorderStroke(1.dp, muted.copy(alpha = 0.3f)),
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                ) {
                    Column(
                        Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
                        horizontalAlignment = if (before) Alignment.Start else Alignment.End,
                    ) {
                        Text(
                            stringResource(if (before) R.string.neighbour_previous else R.string.neighbour_next),
                            style = MaterialTheme.typography.labelMedium,
                            color = muted,
                        )
                        Text(
                            neighbour.root,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = bonaDesign.headword,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            neighbour.definition,
                            style = MaterialTheme.typography.bodySmall.copy(fontStyle = FontStyle.Italic),
                            color = muted,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            textAlign = if (before) TextAlign.Start else TextAlign.End,
                        )
                    }
                }
            }
        }
    }
}

/** The card between the previous and the next roots: a root at random */
@Composable
private fun RandomRootCard(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val label = stringResource(R.string.random_root)

    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, muted.copy(alpha = 0.3f)),
        modifier = modifier.semantics { contentDescription = label },
    ) {
        Column(
            Modifier.padding(horizontal = 6.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Dice(bonaDesign.headword, size = 30.dp)
            Text(
                stringResource(R.string.random_root_short),
                style = MaterialTheme.typography.labelMedium,
                color = muted,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}
