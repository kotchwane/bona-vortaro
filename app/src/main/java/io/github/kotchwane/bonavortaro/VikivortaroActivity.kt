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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** A word found only in Vikivortaro, among the search results or in the
 * history, like Reta Vortaro's words but not bold: a French word, with its
 * Esperanto word and the start of its definition (Reta Vortaro's, else
 * Vikivortaro's description); or an Esperanto word, with its French words
 * if the reader chose French. Like [ResultRow], with its label, long press
 * and button. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun VikiResultRow(
    result: VikiResult,
    onClick: () -> Unit,
    label: String? = null,
    onLongClick: (() -> Unit)? = null,
    action: (@Composable () -> Unit)? = null,
    /** Whether Vikivortaro's French shows: the French translations do */
    inFrench: Boolean = Vikivortaro.inArticles(LocalContext.current),
) {
    val context = LocalContext.current
    /* Reta Vortaro's definition; else Vikivortaro's French description,
     * only for those who chose to see the French translations */
    val description = result.description.takeIf { inFrench }
    val definition by produceState(if (result.revo != null) null else description, result, inFrench) {
        /* The row may have shown another word just before: its own text
         * at once, whatever follows */
        value = if (result.revo != null) null else description
        result.revo?.let { revo -> withContext(Dispatchers.IO) { SearchPreviews.get(context, revo)?.definition }?.let { value = it } }
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
                value = withContext(Dispatchers.IO) { BonaLingvo.discouraged(context, result.esperanto) }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    /* Not bold: one dictionary, where Reta Vortaro's words,
                     * nearly all of them, keep the weight; the end of the
                     * results says so */
                    if (result.inEsperanto)
                        Text(
                            result.word,
                            style = MaterialTheme.typography.titleMedium,
                            color = bonaDesign.headword,
                            modifier = Modifier.weight(1f, fill = false),
                        )
                    /* A French word: "potiron → kukurbo", as Reta Vortaro's,
                     * not bold */
                    else
                        Text(
                            translationTitle(result.word, result.esperanto, MaterialTheme.typography.titleMedium),
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.weight(1f, fill = false),
                        )
                    if (advice != null)
                        DiscouragedChip(Modifier.padding(start = 8.dp))
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
            /* Found in Esperanto: its Esperanto definition, for everyone;
             * else what it says in French, saying so */
            Text(
                advice?.let { preferredLine(it) } ?: AnnotatedString(if (result.inEsperanto)
                    if (result.definition != null)
                        result.definition
                    else if (inFrench)
                        stringResource(R.string.viki_in_french, result.frenchWords.joinToString(", ")) +
                            (result.description?.let { " — $it" } ?: "")
                    /* Nothing in the reader's languages */
                    else stringResource(R.string.not_in_revo)
                else definition ?: result.description ?: ""),
                style = small.copy(fontStyle = FontStyle.Italic),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        action?.invoke()
    }
}

/** An Esperanto word that Reta Vortaro doesn't have, with the French words
 * that the French Wiktionary translates by it, and its description. */
class VikivortaroActivity : BonaActivity() {

    companion object {
        private const val EXTRA_WORD = "io.github.kotchwane.bonavortaro.VikiPageWord"

        fun open(context: Context, word: String) {
            context.startActivity(Intent(context, VikivortaroActivity::class.java).putExtra(EXTRA_WORD, word))
        }
    }

    /* The settings, read again when coming back to the page: they may have
     * changed in between, eg. from its own settings button */
    private var inFrench by mutableStateOf(false)
    private var guessWords by mutableStateOf(true)
    private var textSize by mutableIntStateOf(BonaSettings.DEFAULT_TEXT_SIZE)

    override fun onStart() {
        super.onStart()
        inFrench = Vikivortaro.inArticles(this)
        guessWords = BonaSettings.getGuessWords(this)
        textSize = BonaSettings.getTextSize(this)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val word = intent.getStringExtra(EXTRA_WORD).orEmpty()

        setContent {
            BonaTheme {
                VikiPage(
                    word,
                    inFrench = inFrench,
                    guessWords = guessWords,
                    textSize = textSize,
                    onOpenEntry = { entry ->
                        startActivity(
                            Intent(this, ArticleActivity::class.java)
                                .putExtra(ArticleActivity.EXTRA_ARTICLE_NUMBER, entry.article)
                                .putExtra(ArticleActivity.EXTRA_MARK_NUMBER, entry.mark)
                        )
                    },
                    onOpenRoot = { number ->
                        startActivity(Intent(this, ArticleActivity::class.java).putExtra(ArticleActivity.EXTRA_ARTICLE_NUMBER, number))
                    },
                )
            }
        }
    }
}

@Composable
private fun VikiPage(
    word: String,
    inFrench: Boolean,
    guessWords: Boolean,
    textSize: Int,
    onOpenEntry: (SearchResult) -> Unit,
    onOpenRoot: (Int) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    /* Reta Vortaro's roots around the place the word would have there, to
     * read on as from any article */
    val neighbours by produceState<List<Neighbour?>?>(null, word) {
        value = withContext(Dispatchers.IO) {
            val place = RevoFiles.placeOf(context, word)
            loadNeighbours({ ArticleLoader.load(context, it, onShowSection = {}, quoteColor = QUOTE_COLOR) }, listOf(place - 1, place))
        }
    }
    /* What Vikivortaro says of it is French: only for those who chose to
     * see the French translations, as in the articles; for the others,
     * the page has nothing in their languages, yet */
    val french by produceState(emptyList<String>(), word, inFrench) {
        value = if (inFrench) withContext(Dispatchers.IO) { Vikivortaro.frenchOf(context, word) } else emptyList()
    }
    val description by produceState<String?>(null, word, inFrench) {
        value = if (inFrench) withContext(Dispatchers.IO) { Vikivortaro.description(context, word) } else null
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
    /* Without them, how the word may be built, as for a word nobody has:
     * "femurosto", femur + osto */
    val analysis by produceState<ShownAnalysis?>(null, word, inFrench, guessWords, read) {
        value = if (definitionRead && definition == null && advice == null && !inFrench && guessWords)
            withContext(Dispatchers.IO) { WordAnalyses.find(context, word, needsEnding = true) }
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
            /* A card like Reta Vortaro's, saying that the word isn't there.
             * The word analysis only when there is no French to read: the
             * French says what the word means, a guess from its pieces can
             * be wrong ("potirono" isn't po + tir + ono). */
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
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            word,
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                            color = bonaDesign.headword,
                            modifier = Modifier.weight(1f, fill = false),
                        )
                        if (advice?.discouragedEverywhere == true)
                            DiscouragedChip(Modifier.padding(start = 10.dp))
                    }
                    /* Where it comes from, as the search's "laŭ Reta Vortaro" */
                    Text(
                        stringResource(
                            when {
                                read == null -> R.string.not_in_revo
                                !(read!!.inViki || french.isNotEmpty() || description != null) -> R.string.from_bl_not_in_revo
                                advice != null -> R.string.from_viki_bl_not_in_revo
                                else -> R.string.from_viki_not_in_revo
                            }
                        ),
                        style = MaterialTheme.typography.bodySmall.copy(fontStyle = FontStyle.Italic),
                        color = muted,
                    )
                    /* What to say instead, each word a link to its page */
                    val sources = read
                    if (advice != null && sources != null)
                        AdviceText(advice, sources.targets, onOpenEntry, onOpenWord = { VikivortaroActivity.open(context, it) }, firstDefinition = sources.firstDefinition)
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
                                        Text(sense, style = MaterialTheme.typography.bodyLarge)
                                    }
                                else
                                    Text(sense, style = MaterialTheme.typography.bodyLarge, modifier = top)
                            }
                        }
                    }
                    if (french.isNotEmpty() || description != null) {
                        /* The French words, then the description when it
                         * says more than them */
                        Row(Modifier.padding(top = 10.dp)) {
                            Text(
                                LanguageList.getDefault(context).getLanguageName("fr"),
                                style = MaterialTheme.typography.bodySmall.copy(fontStyle = FontStyle.Italic),
                                color = muted,
                                maxLines = 1,
                                softWrap = false,
                                /* As wide as it needs at any text size */
                                modifier = Modifier.widthIn(min = 52.dp).padding(top = 2.dp, end = 12.dp),
                            )
                            Column {
                                if (french.isNotEmpty())
                                    Text(french.joinToString(", "), style = MaterialTheme.typography.bodyLarge)
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
                    analysis?.let {
                        HorizontalDivider(Modifier.padding(top = 12.dp), color = muted.copy(alpha = 0.25f))
                        WordAnalysisView(it, onOpen = onOpenEntry)
                    }
                }
            }
            /* Under the card, as in an article */
            if (read != null) SourcesLine(
                revo = false,
                /* The French Wiktionary when its French is shown, or
                 * when the word comes from it only */
                viki = french.isNotEmpty() || description != null || (definition == null && read!!.inViki),
                vikiWord = word,
                vikiDescription = french.isNotEmpty() || description != null,
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
    val firstDefinition: String?,
    val inViki: Boolean,
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
                index?.let { Vikivortaro.revoEsperanto(it, target.word) }?.let { target.word to it }
            }.toMap()
            val first = advice?.preferred?.firstOrNull()?.let { targets[it.word] }
            return PageSources(
                definition = Vikivortaro.definition(context, word),
                advice = advice,
                targets = targets,
                firstDefinition = first?.let { SearchPreviews.get(context, it)?.definition },
                inViki = Vikivortaro.esperantoResult(context, word) != null || Vikivortaro.frenchOf(context, word).isNotEmpty(),
            )
        }
    }
}

/** "Prefere diru rulŝtuparo: Maŝina ŝtuparo, …", as the remark of an
 * article: each word to say a link to its article when Reta Vortaro has
 * it, else to its page if it has one; the first one's definition after
 * it; whether the group has yet to check it. */
@Composable
private fun AdviceText(
    advice: BonaLingvoEntry,
    targets: Map<String, SearchResult>,
    onOpenEntry: (SearchResult) -> Unit,
    onOpenWord: (String) -> Unit,
    firstDefinition: String?,
) {
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
                    withLink(LinkAnnotation.Clickable(word, link) { if (found != null) onOpenEntry(found) else onOpenWord(word) }) {
                        append(word)
                    }
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
    if (!advice.checked)
        Text(
            stringResource(R.string.bl_unchecked),
            style = MaterialTheme.typography.bodySmall.copy(fontStyle = FontStyle.Italic),
            color = muted,
            modifier = Modifier.padding(top = 6.dp),
        )
}
