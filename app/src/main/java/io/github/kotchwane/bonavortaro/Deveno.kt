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
import android.graphics.Typeface
import android.net.Uri
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.TextPaint
import android.text.style.ClickableSpan
import android.text.style.RelativeSizeSpan
import android.text.style.StyleSpan
import android.util.Log
import android.view.View
import java.io.IOException

/**
 * Where the words come from, which the articles show when the user turns
 * it on: from András Rajki's "Etimologia Vortaro de Esperanto" (2006, CC0),
 * the words a root corresponds to in the languages it comes from ("itala
 * acqua, latina aqua"), and from the Esperanto Wiktionary (CC BY-SA), its
 * section "Deveno". Written by scripts/deveno.py in assets/deveno/, the
 * words of other languages between ⟨ and ⟩.
 */
object Deveno {
    private const val TAG = "bonadeveno"
    const val RAJKI_SITE = "https://archive.org/details/EsperantoEtymologicalDictionary"
    private const val VIKI_PAGE = "https://eo.wiktionary.org/wiki/"

    /** The origins of a word: Rajki's lines (two for "radio", the radio
     * and the radius), and Vikivortaro's text, if any */
    class Origins(val rajki: List<String>, val vikivortaro: String?)

    private class Data(val rajki: Map<String, List<String>>, val vikivortaro: Map<String, String>)

    @Volatile private var data: Data? = null

    @Synchronized
    private fun data(context: Context): Data = data ?: Data(
        read(context, "deveno/rajki.tsv").groupBy({ it.first }, { it.second }),
        read(context, "deveno/vikivortaro.tsv").toMap(),
    ).also { data = it }

    private fun read(context: Context, file: String): List<Pair<String, String>> =
        try {
            context.assets.open(file).bufferedReader().useLines { lines ->
                lines.filter { !it.startsWith("#") && it.isNotBlank() }
                    .mapNotNull { line -> line.split('\t').takeIf { it.size == 2 }?.let { it[0] to it[1] } }
                    .toList()
            }
        } catch (e: IOException) {
            Log.w(TAG, "Failed to read $file")
            emptyList()
        }

    /** The origins of a word as written, if either source has them. Disk
     * access: not on the main thread. */
    fun of(context: Context, word: String): Origins? {
        val data = data(context)
        val rajki = data.rajki[word].orEmpty()
        val viki = data.vikivortaro[word]
        return if (rajki.isEmpty() && viki == null) null else Origins(rajki, viki)
    }

    /** For the words of an article that have origins, by their section:
     * a line for each source, "franca ⟨dent⟩, itala ⟨dente⟩ — Rajki", the
     * name of Vikivortaro a link to the word's page there, to correct or
     * complete it. The first of its headwords that has some. Disk access:
     * not on the main thread. */
    fun notes(context: Context, layout: ArticleLayout): Map<Int, CharSequence> =
        layout.words.mapNotNull { word ->
            val headwords = word.title.toString().split(",").map { it.trim() }.filter { it.isNotEmpty() }
            headwords.firstNotNullOfOrNull { headword -> of(context, headword)?.let { headword to it } }
                ?.let { (headword, origins) -> word.section to note(context, headword, origins) }
        }.toMap()

    private fun note(context: Context, word: String, origins: Origins): CharSequence =
        SpannableStringBuilder().apply {
            for (line in origins.rajki) {
                if (isNotEmpty())
                    append("\n")
                appendForeign(line)
                appendSource(context.getString(R.string.rajki_name), null)
            }
            origins.vikivortaro?.let {
                if (isNotEmpty())
                    append("\n")
                appendForeign(it)
                appendSource(context.getString(R.string.viki_name), VIKI_PAGE + Uri.encode(word.replace(' ', '_')))
            }
        }

    /* The text, its words of other languages in italics, without their ⟨⟩ */
    private fun SpannableStringBuilder.appendForeign(text: String) {
        var at = 0
        while (at < text.length) {
            val open = text.indexOf('⟨', at)
            val close = if (open >= 0) text.indexOf('⟩', open) else -1
            if (open < 0 || close < 0) {
                append(text, at, text.length)
                break
            }
            append(text, at, open)
            val start = length
            append(text, open + 1, close)
            setSpan(StyleSpan(Typeface.ITALIC), start, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            at = close + 1
        }
    }

    /* " — Rajki", paler, like the source of an example; with an address,
     * a link to it, followed by "↗" */
    private fun SpannableStringBuilder.appendSource(name: String, url: String?) {
        append(" ")
        val start = length
        append("— ").append(name)
        if (url != null)
            append(" $OUTSIDE_SIGN")
        setSpan(if (url != null) OriginLinkSpan(url) else SourceSpan(), start, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        /* Smaller, as the sources of the examples are */
        setSpan(RelativeSizeSpan(0.85f), start, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
    }

    /** The source of an origin whose page a tap opens */
    private class OriginLinkSpan(private val url: String) : ClickableSpan(), ExampleSource {
        override fun updateDrawState(paint: TextPaint) = paler(paint)

        override fun onClick(view: View) = openOutside(view.context, Uri.parse(url))
    }
}
