/*
 * Bona vortaro - an Esperanto dictionary for Android, based on PReVo
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

import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.ui.text.withStyle
import java.io.IOException
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.AnnotatedString
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.foundation.layout.size
import androidx.compose.material3.IconButton
import androidx.compose.material3.Icon
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.Icons
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** A word found only in Vikivortaro, among the search results or in the
 * history, like Reta Vortaro's words but not bold: a word of another
 * language, with its Esperanto word and the start of its definition (Reta
 * Vortaro's, else Wiktionary's description); or an Esperanto word, with
 * the words of the language of its Wiktionary if the reader chose it. Like [ResultRow], with its label, long press
 * and button. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun VikiResultRow(
    result: VikiResult,
    onClick: () -> Unit,
    label: String? = null,
    onLongClick: (() -> Unit)? = null,
    action: (@Composable () -> Unit)? = null,
    /** Whether the words of the result's Wiktionary show: the translations
     * in its language do */
    inLanguage: Boolean = VikiLanguage.of(result.language)?.let { Vikivortaro.inArticles(LocalContext.current, it) } == true,
) {
    val context = LocalContext.current
    /* Only Esperanto under a word in the search: Reta Vortaro's
     * definition, else the Esperanto Wiktionary's; not the other language's
     * description nor the sense of its table, "furthermore → cetere", "In
     * addition; besides…" (user, 2026-11-10) */
    /* Read already, when the row comes back into view: as it was, at once */
    val known = remember(result) { result.revo?.let { SearchPreviews.peek(it) } }
    var severalSenses by remember(result) { mutableStateOf(known?.severalSenses == true) }
    /* A word Reta Vortaro has in an example only: which, as its own rows
     * say, not the definition of the word it is an example of ("Bahnhof →
     * fervoja stacio": ekzemplo ĉe fervoja, test round 10) */
    fun secondLine(preview: WordPreview) =
        if (result.revo?.isExample == true) context.getString(R.string.example_of, preview.title.split(',').first().trim())
        else preview.definition
    val definition by produceState(if (result.revo != null) known?.let(::secondLine) else result.definition, result) {
        /* The row may have shown another word just before: its own text
         * at once, whatever follows */
        value = if (result.revo != null) known?.let(::secondLine) else result.definition
        result.revo?.let { revo -> withContext(Dispatchers.IO) { SearchPreviews.get(context, revo) } }?.let {
            /* Not the first of several senses: Wiktionary doesn't say which
             * one its word is ("cafetière", not kafujo's tree) */
            severalSenses = it.severalSenses
            value = secondLine(it)
        }
    }
    val small = MaterialTheme.typography.bodyMedium

    Row(
        Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(start = 20.dp, end = if (action != null) 8.dp else 20.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            /* Advised against by "Vortoj de la bona lingvo", as Reta
             * Vortaro's words */
            val advice by produceState<BonaLingvoEntry?>(null, result.esperanto) {
                value = withContext(Dispatchers.IO) { BonaLingvo.discouraged(context, result) }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                /* The label after the word, on the next line when it doesn't
                 * fit: never the word broken to make room for it */
                FlowRow(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    itemVerticalAlignment = Alignment.CenterVertically,
                ) {
                    /* Not bold: one dictionary, where Reta Vortaro's words,
                     * nearly all of them, keep the weight; the end of the
                     * results says so */
                    if (result.inEsperanto)
                        Text(
                            result.word,
                            style = MaterialTheme.typography.titleMedium,
                            color = bonaDesign.headword,
                        )
                    /* A word of another language: "potiron → kukurbo", as Reta
                     * Vortaro's; the translation from Wiktionary, dotted under
                     * the word as in the articles; the Esperanto word bold when
                     * Reta Vortaro has it, "amidst → meze de", which isn't
                     * Wiktionary's word (user, 2026-11-10) */
                    else {
                        var layout by remember { mutableStateOf<TextLayoutResult?>(null) }
                        val style = MaterialTheme.typography.titleMedium
                        Text(
                            translationTitle(result.word, result.esperanto,
                                if (result.revo != null) style.copy(fontWeight = FontWeight.Bold) else style),
                            style = style,
                            onTextLayout = { layout = it },
                            modifier = Modifier.dottedUnder(listOf(result.word.indices), layout, MaterialTheme.colorScheme.onSurfaceVariant),
                        )
                    }
                    if (advice != null)
                        DiscouragedChip()
                }
                if (label != null)
                    Text(
                        label,
                        style = MaterialTheme.typography.labelMedium,
                        letterSpacing = 1.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 12.dp),
                    )
            }
            /* A word that Reta Vortaro doesn't have, of which its
             * Wiktionary says nothing ("maison de thé → teejo"): no
             * second line, rather than an empty one. One that Reta Vortaro
             * has keeps its line while its definition is read. */
            val nothingToSay = advice == null && !result.inEsperanto && (severalSenses ||
                result.revo == null && definition.isNullOrEmpty())
            /* Found in Esperanto: its Esperanto definition, for everyone;
             * else what it says in the other language, saying so */
            if (!nothingToSay) Text(
                advice?.let { preferredLine(it) } ?: AnnotatedString(if (result.inEsperanto)
                    if (result.definition != null)
                        result.definition
                    /* Only a translation table gives it: which, for every
                     * reader, as its page says, its words being all there
                     * is to it (user, 2026-11-11) */
                    else if (result.onlyTranslation)
                        stringResource(
                            if (result.foreignWords.size > 1) R.string.viki_only_translation_several else R.string.viki_only_translation,
                            LanguageList.getDefault(context).getLanguageName(result.language),
                            stringResource(R.string.words_and).let { and ->
                                joinedWords(result.foreignWords.map { "„$it“" }) { first, last -> and.format(first, last) }
                            },
                        )
                    else if (inLanguage)
                        stringResource(
                            R.string.viki_in_language,
                            LanguageList.getDefault(context).getLanguageName(result.language),
                            result.foreignWords.joinToString(", "),
                        )
                    /* Nothing in the reader's languages */
                    else stringResource(R.string.not_in_revo)
                else definition ?: ""),
                style = small.copy(fontStyle = FontStyle.Italic),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                /* All the words to say, on several lines if need be */
                maxLines = if (advice != null) Int.MAX_VALUE else 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        action?.invoke()
    }
}

/** An Esperanto word that Reta Vortaro doesn't have, with the words that
 * each Wiktionary translates by it, and its description. */
class VikivortaroActivity : BonaActivity() {

    companion object {
        private const val EXTRA_WORD = "io.github.kotchwane.bonavortaro.VikiPageWord"

        fun open(context: Context, word: String) {
            context.startActivity(Intent(context, VikivortaroActivity::class.java).putExtra(EXTRA_WORD, word))
        }
    }

    /* The settings, read again when coming back to the page: they may have
     * changed in between, eg. from its own settings button */
    private var vikiLanguages by mutableStateOf(emptyList<VikiLanguage>())
    private var readerOrder by mutableStateOf(emptyList<VikiLanguage>())
    private var textSize by mutableIntStateOf(BonaSettings.DEFAULT_TEXT_SIZE)
    /* A root opened from this page: a second tap opens nothing more until
     * the reader is back */
    private var opened = false

    override fun onResume() {
        super.onResume()
        opened = false
    }

    override fun onStart() {
        super.onStart()
        vikiLanguages = Vikivortaro.shownLanguages(this)
        readerOrder = Vikivortaro.readerOrder(this)
        textSize = BonaSettings.getTextSize(this)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val word = intent.getStringExtra(EXTRA_WORD).orEmpty()

        setContent {
            BonaTheme {
                VikiPage(
                    word,
                    vikiLanguages = vikiLanguages,
                    readerOrder = readerOrder,
                    textSize = textSize,
                    onOpenEntry = { entry ->
                        startActivity(
                            Intent(this, ArticleActivity::class.java)
                                .putExtra(ArticleActivity.EXTRA_ARTICLE_NUMBER, entry.article)
                                .putExtra(ArticleActivity.EXTRA_MARK_NUMBER, entry.mark)
                        )
                    },
                    onOpenRoot = { number ->
                        if (!opened) {
                            opened = true
                            startActivity(Intent(this, ArticleActivity::class.java).putExtra(ArticleActivity.EXTRA_ARTICLE_NUMBER, number))
                        }
                    },
                )
            }
        }
    }
}

@Composable
private fun VikiPage(
    word: String,
    vikiLanguages: List<VikiLanguage>,
    /** Every Wiktionary in the reader's order, those not shown too */
    readerOrder: List<VikiLanguage>,
    textSize: Int,
    onOpenEntry: (SearchResult) -> Unit,
    onOpenRoot: (Int) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    /* A word of the definition long pressed: its entries, as in Reta
     * Vortaro's articles (user, 2026-11-10) */
    var wordFor by remember { mutableStateOf<String?>(null) }
    wordFor?.let { pressed ->
        WordActions(
            word = pressed,
            language = "eo",
            onDismiss = { wordFor = null },
            onOpen = onOpenEntry,
            onCopy = { SpannedCopy.copyText(context, context.getText(R.string.word_label), pressed) },
        )
    }
    /* Reta Vortaro's roots around the place the word would have there, to
     * read on as from any article */
    val neighbours by produceState<List<Neighbour?>?>(null, word) {
        value = withContext(Dispatchers.IO) {
            val place = RevoFiles.placeOf(context, word)
            loadNeighbours({ ArticleLoader.load(context, it, onShowSection = {}, quoteColor = QUOTE_COLOR) }, listOf(place - 1, place))
        }
    }
    /* What each Wiktionary says of it, in its language: only for those
     * who chose to see the translations in that language, as in the
     * articles; for the others, the page has nothing in their languages.
     * But a word that only translation tables give, nothing describing
     * it: those words for every reader, with the sense of their tables,
     * all there is to it, the same for everyone (user, 2026-11-11: a Pole
     * finds "belto" as a German does from "Belt") */
    val foreign by produceState(emptyList<ForeignWords>(), word, vikiLanguages, readerOrder) {
        value = withContext(Dispatchers.IO) {
            fun wordsIn(languages: List<VikiLanguage>) =
                languages.map { ForeignWords(it.code, Vikivortaro.foreignOf(context, it, word), Vikivortaro.description(context, it, word),
                    hasPage = Vikivortaro.hasPage(context, it, word)) }
                    .filter { it.words.isNotEmpty() || it.description != null }
            val all = wordsIn(readerOrder)
            if (all.any { it.description != null } || Vikivortaro.definition(context, word) != null)
                wordsIn(vikiLanguages)
            else all.map { it.copy(senses = Vikivortaro.senses(context, VikiLanguage.of(it.language)!!, word)) }
        }
    }
    /* Its definition in the Esperanto Wiktionary, for every reader; what
     * "Vortoj de la bona lingvo" says of it; and whether they were read:
     * until then, the page doesn't know its sources */
    val read by produceState<PageSources?>(null, word) {
        value = withContext(Dispatchers.IO) { PageSources.read(context, word) }
    }
    val definitionRead = read != null
    val definition = read?.definition
    val advice = read?.advice
    /* How the word is built of Reta Vortaro's roots, as for a word nobody
     * has: "femurosto", femur + osto; also under its words, which let the
     * reader see a wrong guess ("potirono" isn't po + tir + ono):
     * "marvirino", mar + vir + in + o (user, 2026-11-10) */
    val otherCase by produceState<String?>(null, word) {
        value = withContext(Dispatchers.IO) { Vikivortaro.otherCase(context, word) }
    }
    val analysis by produceState<ShownAnalysis?>(null, word, read, foreign) {
        value = if (definitionRead)
            withContext(Dispatchers.IO) {
                if (foreign.isEmpty()) WordAnalyses.find(context, word, needsEnding = true)
                else WordAnalyses.forWiktionaryWord(context, word, foreign.flatMap { it.words }, advice?.discouraged == true)
            }
        else null
    }
    val edge = MaterialTheme.colorScheme.primary
    val muted = MaterialTheme.colorScheme.onSurfaceVariant

    BonaScaffold(
        /* As an article's: the search in place of a back arrow, which the
         * back of the phone does, and the settings */
        topBar = {
            BonaTopBar(
                title = { Text(word, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                onBack = null,
                navigation = {
                    IconButton(onClick = { Screens.goSearch(context) }, modifier = Modifier.size(56.dp)) {
                        Icon(Icons.Default.Search, stringResource(R.string.menu_search), Modifier.size(28.dp))
                    }
                },
                actions = {
                    IconButton(onClick = { Screens.goPreferences(context) }) {
                        Icon(Icons.Default.Settings, stringResource(R.string.preferences))
                    }
                },
            )
        },
    ) { padding ->
      /* What is read at the text size of the settings, as in an article;
       * the bar above stays as it is */
      ScaledText(textSize) {
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(12.dp)
                .navigationBarsPadding(),
        ) {
            /* A card like Reta Vortaro's, saying that the word isn't there,
             * with its words, then how it is built */
            /* A word only "Vortoj de la bona lingvo" has: not an entry, a
             * note that it isn't in the dictionary, and what to say instead
             * (user, 2026-10-27) */
            val onlyInList = read?.inViki == false && definition == null && foreign.isEmpty()
            if (onlyInList && advice != null)
                ListOnlyNote(word, advice, read!!, onOpenEntry)
            else
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            ) {
                Column(
                    Modifier
                        .drawBehind { drawRect(edge, size = Size(4.dp.toPx(), size.height)) }
                        .padding(start = 16.dp, end = 12.dp, top = 12.dp, bottom = 12.dp),
                ) {
                    /* The label on the next line rather than the word broken */
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        itemVerticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            word,
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                            color = bonaDesign.headword,
                        )
                        if (advice?.discouragedEverywhere == true)
                            DiscouragedChip()
                    }
                    /* Where it comes from, as the search's "laŭ Reta Vortaro" */
                    val origin = stringResource(
                        when {
                            read == null -> R.string.not_in_revo
                            !(read!!.inViki || foreign.isNotEmpty()) ->
                                if (advice != null) R.string.from_bl_not_in_revo else R.string.not_in_revo
                            advice != null -> R.string.from_viki_bl_not_in_revo
                            else -> R.string.from_viki_not_in_revo
                        }
                    )
                    Text(
                        origin,
                        style = MaterialTheme.typography.bodySmall.copy(fontStyle = FontStyle.Italic),
                        color = muted,
                    )
                    /* What to say instead, each word a link to its page */
                    val sources = read
                    if (advice != null && sources != null)
                        AdviceText(advice, sources.targets, sources.withPage, onOpenEntry, onOpenWord = { VikivortaroActivity.open(context, it) }, firstDefinition = sources.firstDefinition)
                    /* The definition in Esperanto, its senses numbered when
                     * there are several */
                    definition?.let { definition ->
                        Column(Modifier.padding(top = 10.dp, bottom = 4.dp)) {
                            definition.senses.forEachIndexed { index, sense ->
                                val top = Modifier.padding(top = if (index > 0) 6.dp else 0.dp)
                                if (definition.senses.size > 1)
                                    /* The number apart, the sense's lines under
                                     * its first word, not under the number */
                                    Row(top) {
                                        Text(
                                            "${index + 1}.",
                                            style = MaterialTheme.typography.bodyLarge,
                                            modifier = Modifier.widthIn(min = 24.dp).padding(end = 6.dp),
                                        )
                                        SenseLine(sense, onLongPress = { wordFor = it })
                                    }
                                else
                                    SenseLine(sense, top, onLongPress = { wordFor = it })
                            }
                        }
                    }
                    for ((code, words, description, senses) in foreign) {
                        /* Its words, then the description when it says
                         * more than them */
                        Row(Modifier.padding(top = 10.dp)) {
                            Text(
                                LanguageList.getDefault(context).getLanguageName(code),
                                style = MaterialTheme.typography.bodySmall.copy(fontStyle = FontStyle.Italic),
                                color = muted,
                                maxLines = 1,
                                softWrap = false,
                                /* As wide as it needs at any text size */
                                modifier = Modifier.widthIn(min = 52.dp).padding(top = 2.dp, end = 12.dp),
                            )
                            Column {
                                if (senses.isNotEmpty())
                                    /* Each word, with the sense its table
                                     * gives under it */
                                    for ((index, foreignWord) in words.withIndex()) {
                                        Text(
                                            foreignWord,
                                            style = MaterialTheme.typography.bodyLarge,
                                            modifier = Modifier.padding(top = if (index > 0) 6.dp else 0.dp),
                                        )
                                        senses[foreignWord]?.let {
                                            Text(
                                                it,
                                                style = MaterialTheme.typography.bodyMedium.copy(fontStyle = FontStyle.Italic),
                                                color = muted,
                                            )
                                        }
                                    }
                                else if (words.isNotEmpty())
                                    Text(words.joinToString(", "), style = MaterialTheme.typography.bodyLarge)
                                description?.let {
                                    Text(
                                        it,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = muted,
                                        modifier = Modifier.padding(top = 4.dp),
                                    )
                                }
                            }
                        }
                    }
                    /* The same word with the other case, which another
                     * Wiktionary gives: "smurfo" (English), "Smurfo" (French).
                     * Not merged, as some are other words: "Atena" the
                     * goddess, "atena" Athenian (user, 2026-11-10) */
                    otherCase?.let { other ->
                        Text(
                            stringResource(R.string.viki_other_case, other),
                            style = MaterialTheme.typography.bodyMedium.copy(fontStyle = FontStyle.Italic),
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .padding(top = 8.dp)
                                .heightIn(min = 48.dp)
                                .clickable(role = Role.Button) { VikivortaroActivity.open(context, other) }
                                .wrapContentHeight(Alignment.CenterVertically),
                        )
                    }
                    analysis?.let {
                        HorizontalDivider(Modifier.padding(top = 12.dp), color = muted.copy(alpha = 0.25f))
                        /* Discreet under the words; in full when it is all the page has */
                        if (foreign.isNotEmpty()) CompactAnalysis(it, onOpen = onOpenEntry)
                        else WordAnalysisView(it, onOpen = onOpenEntry)
                    }
                }
            }
            /* Under the card, as in an article */
            /* Not without a source to name: a page reached from a word to say
             * that only has its analysis */
            val hasSource = read?.let { foreign.isNotEmpty() || definition != null || it.inViki || advice != null } == true
            if (hasSource) SourcesLine(
                revo = false,
                /* A Wiktionary when its words are shown, or when the word
                 * comes from it only */
                vikiLanguages = foreign.map { it.language }.ifEmpty {
                    if (definition == null && read!!.inViki) listOfNotNull(read!!.vikiLanguage) else emptyList()
                },
                vikiWord = word,
                /* "and its description" only when one is shown: not
                 * for "anakondo", whose page has only words */
                vikiDescription = foreign.any { it.description != null },
                /* Where a Wiktionary only translates by it, its words' pages */
                vikiPages = foreign.filter { !it.hasPage && it.words.isNotEmpty() }.associate { it.language to it.words },
                vikiEsperanto = definition,
                bonaLingvo = advice != null,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 10.dp),
            )
            neighbours?.let {
                UnscaledText {
                    NeighbourRoots(
                        it,
                        onOpen = onOpenRoot,
                        onRandom = { scope.launch { onOpenRoot(withContext(Dispatchers.IO) { RandomRoot.pick(context) }) } },
                    )
                }
            }
        }
      }
    }
}

/** What the page of a word Reta Vortaro doesn't have shows, read off the
 * main thread: its definition from the Esperanto Wiktionary, what "Vortoj
 * de la bona lingvo" says of it with Reta Vortaro's entries of the words to
 * say, and the definition of the first one; whether Vikivortaro has the
 * word at all. */
private class PageSources(
    val definition: EsperantoDefinition?,
    val advice: BonaLingvoEntry?,
    val targets: Map<String, SearchResult>,
    /** The words to say that Reta Vortaro lacks but have a page: Vikivortaro's or the list's */
    val withPage: Set<String>,
    val firstDefinition: String?,
    val inViki: Boolean,
    /** The first language whose Wiktionary has the word */
    val vikiLanguage: String?,
) {
    companion object {
        fun read(context: Context, word: String): PageSources {
            val advice = BonaLingvo.entry(context, word)
            val index = try {
                TrieCache.getTrie(context, "eo")
            } catch (e: IOException) {
                null
            }
            val targets = advice?.preferred.orEmpty().mapNotNull { target ->
                index?.let { Vikivortaro.revoEsperanto(it, BonaLingvo.withoutBrackets(target.word)) }?.let { target.word to it }
            }.toMap()
            val first = advice?.preferred?.firstOrNull()?.let { targets[it.word] }
            val withPage = advice?.preferred.orEmpty().map { it.word }.filter { it !in targets }.filter {
                val target = BonaLingvo.withoutBrackets(it)
                Vikivortaro.esperantoResult(context, target) != null || Vikivortaro.anyForeignOf(context, target) ||
                    BonaLingvo.entry(context, target) != null
            }.toSet()
            return PageSources(
                definition = Vikivortaro.definition(context, word),
                advice = advice,
                targets = targets,
                withPage = withPage,
                firstDefinition = first?.let { SearchPreviews.get(context, it)?.definition },
                inViki = Vikivortaro.esperantoResult(context, word) != null || Vikivortaro.anyForeignOf(context, word),
                vikiLanguage = Vikivortaro.esperantoResult(context, word)?.language
                    ?: Vikivortaro.readerOrder(context).firstOrNull { Vikivortaro.foreignOf(context, it, word).isNotEmpty() }?.code,
            )
        }
    }
}

/** The page of a word only "Vortoj de la bona lingvo" has, which the
 * dictionary doesn't describe: "konsenso ne estas en la vortaro. Laŭ Vortoj
 * de la bona lingvo, ĝi estas malrekomendata. Prefere diru …" */
@Composable
private fun ListOnlyNote(word: String, advice: BonaLingvoEntry, sources: PageSources, onOpenEntry: (SearchResult) -> Unit) {
    val context = LocalContext.current
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    Column(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 12.dp)) {
        val notHere = stringResource(R.string.bl_only_not_here, "\u0000")
        Text(
            buildAnnotatedString {
                append(notHere.substringBefore('\u0000'))
                withStyle(SpanStyle(fontStyle = FontStyle.Italic)) { append(word) }
                append(notHere.substringAfter('\u0000'))
            },
            style = MaterialTheme.typography.titleMedium,
            color = muted,
        )
        val says = stringResource(if (advice.discouraged) R.string.bl_only_discouraged else R.string.bl_only_simpler, "\u0000")
        val according = stringResource(R.string.bl_according, "\u0000")
        Text(
            buildAnnotatedString {
                append(says.substringBefore('\u0000'))
                append(according.substringBefore('\u0000'))
                withStyle(SpanStyle(fontStyle = FontStyle.Italic)) { append(stringResource(R.string.bl_name)) }
                append(according.substringAfter('\u0000'))
                append(says.substringAfter('\u0000'))
            },
            style = MaterialTheme.typography.bodyMedium,
            color = muted,
            modifier = Modifier.padding(top = 4.dp, bottom = 12.dp),
        )
        AdviceText(advice, sources.targets, sources.withPage, onOpenEntry, onOpenWord = { VikivortaroActivity.open(context, it) }, firstDefinition = sources.firstDefinition)
    }
}

/** "Prefere diru rulŝtuparo: Maŝina ŝtuparo, …", as the remark of an
 * article: each word to say a link to its article when Reta Vortaro has
 * it, else to its page if it has one, else to its search; the first one's
 * definition after it. */
@Composable
private fun AdviceText(
    advice: BonaLingvoEntry,
    targets: Map<String, SearchResult>,
    withPage: Set<String>,
    onOpenEntry: (SearchResult) -> Unit,
    onOpenWord: (String) -> Unit,
    firstDefinition: String?,
) {
    val context = LocalContext.current
    val link = TextLinkStyles(SpanStyle(color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold, textDecoration = TextDecoration.Underline))
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val preferred = advice.preferred
    Text(
        buildAnnotatedString {
            if (advice.context.isNotEmpty())
                append("${advice.context} ")
            append(stringResource(if (advice.discouraged) R.string.bl_say_instead else R.string.bl_also_say).replaceFirstChar { it.uppercase() })
            append(" ")
            advice.groups.forEachIndexed { group, (words, wordsContext) ->
                if (group > 0)
                    append("; ")
                words.forEachIndexed { number, word ->
                    if (number > 0)
                        append(", ")
                    val found = targets[word]
                    /* What is in brackets is said of the word, not part
                     * of the link: "(kristana) monaĥestro" */
                    val bare = BonaLingvo.withoutBrackets(word)
                    val at = word.indexOf(bare).takeIf { bare.isNotEmpty() && it >= 0 }
                    val linked = if (at != null) bare else word
                    if (at != null)
                        append(word.take(at))
                    when {
                        found != null -> withLink(LinkAnnotation.Clickable(word, link) { onOpenEntry(found) }) { append(linked) }
                        word in withPage -> withLink(LinkAnnotation.Clickable(word, link) { onOpenWord(linked) }) { append(linked) }
                        /* Nowhere to read it: its search, which says what the
                         * list proposes it for and how it is built */
                        else -> withLink(LinkAnnotation.Clickable(word, link) { Screens.searchFor(context, linked) }) { append(linked) }
                    }
                    if (at != null)
                        append(word.drop(at + bare.length))
                }
                if (wordsContext.isNotEmpty())
                    append(" $wordsContext")
            }
            append(if (firstDefinition != null && preferred.size == 1) ":" else ".")
        },
        style = MaterialTheme.typography.bodyLarge,
        modifier = Modifier.padding(top = 10.dp),
    )
    if (firstDefinition != null && preferred.size == 1)
        Text(
            firstDefinition,
            style = MaterialTheme.typography.bodyMedium.copy(fontStyle = FontStyle.Italic),
            color = muted,
        )
}

/** What the Wiktionary of [language] says of a word: its words for it, and
 * its description when it says more */
private data class ForeignWords(
    val language: String,
    val words: List<String>,
    val description: String?,
    /** Where nothing describes the word, the sense of each word's table */
    val senses: Map<String, String> = emptyMap(),
    /** Whether that Wiktionary has a page for the Esperanto word */
    val hasPage: Boolean = description != null,
)

/** A sense of a definition, each word long pressed looked up, as in Reta
 * Vortaro's articles */
@Composable
private fun SenseLine(sense: String, modifier: Modifier = Modifier, onLongPress: (String) -> Unit) {
    SpannedText(
        sense,
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurface,
        onLongPress = { it?.let(onLongPress) },
        modifier = modifier,
    )
}

/** Words one after the other, the last two joined by [and]: "„X“, „Y“
 * kaj „Z“" */
internal fun joinedWords(words: List<String>, and: (String, String) -> String): String =
    if (words.size > 1) and(words.dropLast(1).joinToString(", "), words.last()) else words.joinToString()
