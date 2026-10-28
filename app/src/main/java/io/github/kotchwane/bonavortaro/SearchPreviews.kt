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

import android.content.Context
import android.util.Log
import android.util.LruCache
import java.io.IOException

/** The previews of the definitions in the list of search results. They
 * are read from the articles when a result is shown, so this does disk
 * access and shouldn't run on the main thread. */
object SearchPreviews {
    private const val TAG = "bonapreview"

    /* Neighbouring results often come from the same article, and the
     * same results come back while typing */
    private val articles = LruCache<Int, Article>(64)
    private val previews = LruCache<Long, WordPreview>(512)
    /* Of the translations found for one sense, by language, article and text */
    private val senses = LruCache<String, WordPreview>(256)

    private val examples = LruCache<String, Found>(512)
    /* The articles with all their languages, laid out, for the examples */
    private val layouts = LruCache<Int, Pair<ArticleLayout, String?>>(16)

    /* What an example lookup found: an example, or nothing */
    private class Found(val example: ExamplePreview?)

    /** The example of a word that a search result is the translation of,
     * or, in Esperanto, the example it is, with its translation in the
     * first of [readerLanguages] that has one: "amplement" is "abunde",
     * an example of "abunda". Null for a result that isn't an example's.
     * Disk access: not on the main thread. */
    fun example(context: Context, result: SearchResult, language: String, readerLanguages: List<String>): ExamplePreview? {
        if (!result.isExample)
            return null
        /* Vikivortaro's French for an example Reta Vortaro doesn't
         * translate into French, for those who read it */
        val inFrench = Vikivortaro.inArticles(context)
        val key = "${result.article}/${result.mark}/$language/${result.word}/$readerLanguages/$inFrench"
        examples.get(key)?.let { return it.example }
        val (layout, title) = layouts.get(result.article) ?: try {
            val article = ArticleLoader.load(context, result.article, onShowSection = {}, quoteColor = 0, languages = { true }, sources = false)
            /* The title of the article, "al/", for a part without a word */
            val title = parseOfficial(article.title).forms.firstOrNull()?.text?.replace("/", "")?.trim()
            (layoutArticle(article, emptyList(), markLabel = { _, _, _ -> Any() }, markLinkType = { _, _ -> Any() }) to title)
                .also { layouts.put(result.article, it) }
        } catch (e: IOException) {
            Log.w(TAG, "Failed to load article ${result.article}")
            return null
        }
        val entry = layout.words.firstOrNull { it.section == result.mark }
        val example = entry?.let {
            exampleOf(it, result.word, language, readerLanguages, title) { example ->
                if (inFrench) Vikivortaro.frenchForExample(context, example, null) else emptyList()
            }
        }
        examples.put(key, Found(example))
        return example
    }

    /** [language]: the language of the search, for a translation, whose
     * sense is shown when the article gives it ("maison (lignée)": domo
     * 2, the family, not 1, the building) */
    fun get(context: Context, result: SearchResult, language: String? = null): WordPreview? {
        val translated = language != null && language != "eo" && !result.isExample
        val key = (result.article.toLong() shl 32) or result.mark.toLong()
        if (!translated)
            previews.get(key)?.let { return it }

        val article = articles.get(result.article) ?: try {
            ArticleLoader.load(context, result.article, onShowSection = {}, quoteColor = 0, sources = false)
                .also { articles.put(result.article, it) }
        } catch (e: IOException) {
            Log.w(TAG, "Failed to load article ${result.article}")
            return null
        }

        if (translated) {
            val text = "$language/${result.article}/${result.word}"
            senses.get(text)?.let { return it }
            translationSense(article, language!!, result.word)?.let { (section, sense) ->
                return previewOf(article, section, sense)?.also { senses.put(text, it) }
            }
        }
        return previewOf(article, result.mark)?.also { previews.put(key, it) }
    }
}

/** An example a search result leads to: the example, the word it is an
 * example of, and in Esperanto, its translation in the reader's language */
class ExamplePreview(val example: String, val word: String, val language: String? = null, val translation: String? = null)

/* A text compared: its case, its marks around and its spaces aside, but
 * with its accents ("concerné" isn't "concerne") and all of it, brackets
 * too ("accès (lieu)" isn't "accès (passage)") */
private fun plainText(text: String) =
    java.text.Normalizer.normalize(text.lowercase(java.util.Locale.ROOT), java.text.Normalizer.Form.NFC)
        .replace('’', '\'').replace('\u00a0', ' ').replace('\u202f', ' ')
        .replace(Regex("\\s+"), " ").trim().trimStart('¡', '¿').trimEnd('!', '?', '.', ' ')

private fun phraseKey(text: String) = plainText(text)

internal fun exampleOf(
    entry: WordEntry,
    word: String,
    language: String,
    readerLanguages: List<String>,
    /** The title of the article, for a part without a word: "II." */
    articleTitle: String? = null,
    vikiFrench: (String) -> List<String> = { emptyList() },
): ExamplePreview? {
    val own = entry.title.toString().split(',').first().trim()
    val title = if (Regex("^[IVXL]+\\.?$").matches(own) && articleTitle != null) articleTitle else own
    val searched = phraseKey(word)
    if (language == "eo") {
        /* The word itself, not its example of the same word: "piedo" */
        if (searched in entry.title.toString().split(',').map { phraseKey(it) })
            return null
        val examples = entry.translations.flatMap { t -> t.examples.map { t.language to it } }
        val found = examples.firstOrNull { phraseKey(it.second.example) == searched }
            /* An example Reta Vortaro translates into no language, whose
             * French only Vikivortaro has: "drinki kiel funelo" */
            ?: run {
                val french = vikiFrench(word).takeIf { it.isNotEmpty() } ?: return null
                return ExamplePreview(word, title, "fr", french.joinToString(", "))
            }
        val translated = readerLanguages.firstNotNullOfOrNull { reader ->
            examples.firstOrNull { it.first == reader && phraseKey(it.second.example) == searched }
                ?.let { reader to it.second.text }
                ?: if (reader == "fr") vikiFrench(found.second.example).takeIf { it.isNotEmpty() }?.let { reader to it.joinToString(", ") }
                else null
        }
        return ExamplePreview(found.second.example, title, translated?.first, translated?.second)
    }
    val translation = entry.translations.firstOrNull { it.language == language } ?: return null
    /* One of its translations, or within one, words whole: a comma may be
     * missing between two ("annexe (ajout) appendice (ajout)"), or be in it
     * ("man soll das Fell des Bären nicht verteilen, bevor…") */
    val words = Regex("(^|\\W)" + Regex.escape(plainText(word)) + "($|\\W)")
    fun has(text: String) = text.split(',', ';').any { phraseKey(it.replace(Regex("^\\(\\d+(, *\\d+)*\\) *"), "")) == searched } ||
        words.containsMatchIn(plainText(text))
    if (has(translation.text))
        return null
    return translation.examples.firstOrNull { has(it.text) }?.let { ExamplePreview(it.example, title) }
}
