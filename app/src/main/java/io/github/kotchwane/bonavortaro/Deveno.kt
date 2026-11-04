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
import java.text.Normalizer
import java.util.Locale

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

    /** The origins of a word: Rajki's lines (two for "mino", the mine
     * dug and the mine laid), and Vikivortaro's text, if any, from the page
     * of [vikiWord] */
    class Origins(val rajki: List<String>, val vikivortaro: String?, val vikiWord: String?)

    /** What a card shows of a word's origins, and from which sources */
    class Note(val text: CharSequence, val rajki: Boolean, val vikivortaro: Boolean)

    /** A line of either source; for a word that heads several articles of
     * Reta Vortaro, the file of the one it is about ("-" for none), else
     * null */
    private class Line(val text: String, val file: String?)

    private class Data(val rajki: Map<String, List<Line>>, val vikivortaro: Map<String, List<Line>>)

    @Volatile private var data: Data? = null

    @Synchronized
    private fun data(context: Context): Data = data ?: Data(
        lines(read(context, "deveno/rajki.tsv")),
        lines(read(context, "deveno/vikivortaro.tsv")),
    ).also { data = it }

    private fun lines(rows: List<List<String>>): Map<String, List<Line>> =
        rows.groupBy({ it[0] }, { Line(it[1], it.getOrNull(2)?.takeIf { file -> file.isNotEmpty() }) })

    private fun read(context: Context, file: String): List<List<String>> =
        try {
            context.assets.open(file).bufferedReader().useLines { lines ->
                lines.filter { !it.startsWith("#") && it.isNotBlank() }
                    .map { it.split('\t') }.filter { it.size >= 2 }
                    .toList()
            }
        } catch (e: IOException) {
            Log.w(TAG, "Failed to read $file")
            emptyList()
        }

    /** The origins of a word as written, in the article of Reta Vortaro
     * [file], if either source has them: of a word that heads several
     * articles ("radio", "talio"), only what is about that article. Disk access: not on the
     * main thread. */
    fun of(context: Context, word: String, file: String?): Origins? {
        val data = data(context)
        val rajki = data.rajki[word].orEmpty().filter { it.file == null || it.file == file }.map { it.text }
        val viki = data.vikivortaro[word].orEmpty().firstOrNull { it.file == null || it.file == file }?.text
        return if (rajki.isEmpty() && viki == null) null else Origins(rajki, viki, word)
    }

    /** For the words of the article [file] that have origins, by their
     * section: a line for each source, "franca ⟨dent⟩, itala ⟨dente⟩ —
     * Rajki", the name of Vikivortaro a link to the word's page there, to
     * correct or complete it. The root's card has those of the root's other
     * forms too ("laboro" beside "labori"), which have none of their own; the
     * other words keep theirs (calques: "senpeka", from Russian). Disk
     * access: not on the main thread. */
    fun notes(context: Context, layout: ArticleLayout, file: String?): Map<Int, Note> {
        val root = layout.words.firstOrNull() ?: return emptyMap()
        val found = layout.words.map { word ->
            word to headwords(word).firstNotNullOfOrNull { headword -> of(context, headword, file) }
        }
        /* The other forms of the root: one headword, the root with another ending */
        val rootWord = headwords(root).firstOrNull().orEmpty()
        val stem = rootWord.takeIf { it.length > 1 && it.last() in ENDINGS }?.dropLast(1)
        fun isForm(word: WordEntry) = word !== root && stem != null && headwords(word).let {
            it.size == 1 && it[0].length == rootWord.length && it[0].startsWith(stem) && it[0].last() in ENDINGS
        }
        val notes = mutableMapOf<Int, Note>()
        /* For each source, the root's own, else the first of its other forms
         * that has one: Rajki's "kosti" and "kosto" say nearly the same */
        val group = found.filter { (word, _) -> word === root || isForm(word) }.mapNotNull { it.second }
        if (group.isNotEmpty()) {
            val rajki = group.firstOrNull { it.rajki.isNotEmpty() }
            val viki = group.firstOrNull { it.vikivortaro != null }
            shown(Origins(rajki?.rajki.orEmpty(), viki?.vikivortaro, viki?.vikiWord))
                ?.let { notes[root.section] = note(context, it) }
        }
        for ((word, origins) in found)
            if (word !== root && !isForm(word) && origins != null)
                shown(origins)?.let { notes[word.section] = note(context, it) }
        return notes
    }

    private fun headwords(word: WordEntry) =
        word.title.toString().split(",").map { it.trim() }.filter { it.isNotEmpty() }

    private const val ENDINGS = "oaie"

    /* Vikivortaro's text only if it gives a word of another language that
     * Rajki doesn't: else it says again what his line says ("el la angla
     * have", "angla have") */
    private fun shown(origins: Origins): Origins? {
        val rajki = origins.rajki.flatMap { foreignWords(it) }.toSet()
        val viki = origins.vikivortaro?.let { foreignWords(it) }.orEmpty()
        val keepViki = origins.vikivortaro != null && (origins.rajki.isEmpty() || viki.isEmpty() || !rajki.containsAll(viki))
        return Origins(origins.rajki, origins.vikivortaro.takeIf { keepViki }, origins.vikiWord)
            .takeIf { it.rajki.isNotEmpty() || it.vikivortaro != null }
    }

    /* The words between ⟨⟩, without their accents and case: "dēns" as "dens" */
    private fun foreignWords(text: String): List<String> =
        Regex("⟨([^⟩]*)⟩").findAll(text).map { m ->
            Normalizer.normalize(m.groupValues[1], Normalizer.Form.NFD).replace(MARKS, "").lowercase(Locale.ROOT)
        }.toList()

    private val MARKS = Regex("\\p{Mn}+")

    private fun note(context: Context, origins: Origins): Note =
        Note(note(context, origins.vikiWord.orEmpty(), origins), origins.rajki.isNotEmpty(), origins.vikivortaro != null)

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
        append("—\u00A0").append(name)
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
