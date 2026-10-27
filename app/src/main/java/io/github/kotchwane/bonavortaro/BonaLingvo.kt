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
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.ClickableSpan
import android.text.style.StyleSpan
import android.util.Log
import android.view.View
import java.io.IOException
import java.util.Locale

/** A word to say instead of another: [word], in its [context] ("(en la
 * faka lingvo)"), "" if none; whether the other is advised against
 * ("malrekomendo", →) or only has this simpler alternative ("alternativo",
 * ~). Only those the working group has checked are in the app. */
class Simplification(
    val word: String,
    val context: String,
    val discouraged: Boolean,
    /** For a word that heads several articles of Reta Vortaro ("vato", the
     * wadding and the watt), the file of the one it is about, "-" for none;
     * null for any */
    val article: String? = null,
    /** The parts of the word as the group cut it, "urin·kanal·inflam·o";
     * null if it isn't one word, or they weren't given */
    val parts: String? = null,
)

/** A word of "Vortoj de la bona lingvo", in its [context] ("(en gramatiko)"),
 * "" if none, with the words to say instead, in the group's order. */
class BonaLingvoEntry(val word: String, val context: String, val simplifications: List<Simplification>) {
    /** Advised against, rather than given simpler alternatives */
    val discouraged: Boolean get() = simplifications.any { it.discouraged }

    /** The words to say instead: those of the advice against it, if any */
    val preferred: List<Simplification> get() = simplifications.filter { it.discouraged == discouraged }

    /** The advice is for some senses or uses only: the word has a context
     * ("afero (en la senco de konkreta aĵo)"), or every word to say has one
     * ("brava": "obeema (en la senco de obeema, trankvila)", "brile (al
     * artistoj)"), which doesn't say "laŭ la kunteksto". Such a word isn't
     * marked "malrekomendata" in the search: "courageux → brava" is right;
     * its article says in which senses. A word whose senses all have their
     * word ("agendo": "tagordo (se temas pri listo…)", "taglibro (…)") is so
     * too, its article saying what to say. */
    val limited: Boolean
        get() = (context.isNotEmpty() && context !in GENERAL) || preferred.all { it.context.isNotEmpty() && it.context !in GENERAL }

    /** Advised against in all its senses */
    val discouragedEverywhere: Boolean get() = discouraged && !limited

    /** The words to say, those with the same context together, in order:
     * "obeema, trankvila, bonkonduta" "(en la senco de obeema, trankvila)",
     * "brile, lerte" "(al artistoj)" */
    val groups: List<Pair<List<String>, String>>
        get() = preferred.fold(mutableListOf<Pair<MutableList<String>, String>>()) { groups, it ->
            groups.apply {
                if (isNotEmpty() && last().second == it.context) last().first.add(it.word)
                else add(mutableListOf(it.word) to it.context)
            }
        }

    private companion object {
        /* Contexts that don't limit the advice */
        val GENERAL = setOf("(laŭ la kunteksto)", "(laŭ la senco)", "(laŭ la signifo)", "(ĝenerale)", "(generale)")
    }
}

/**
 * "Vortoj de la bona lingvo" (labonalingvo.org), by Renato Corsetti, Noam
 * Mohr and the working group of La Bona Lingvo: neologisms, and the
 * Esperanto words to say instead, in Claude Piron's spirit; only what the
 * group has checked. Under CC BY 4.0. In assets/bonalingvo/bl.tsv, written by
 * scripts/bona-lingvo.py from data/bona-lingvo.csv.
 */
object BonaLingvo {
    private const val TAG = "bonalingvo"
    const val SITE = "https://labonalingvo.org/"

    private const val MAX_RESULTS = 64
    private const val MAX_LOOKED_AT = 400

    /** The entries by their word (and in small letters), and in the order
     * of their keys, for the search */
    private class Data(val byWord: Map<String, BonaLingvoEntry>, val keys: List<String>, val sorted: List<BonaLingvoEntry>) {
        /** The entries by the key of each of their words to say */
        val byPreferred: Map<String, List<BonaLingvoEntry>> by lazy {
            sorted.flatMap { entry -> entry.preferred.map { Vikivortaro.key(it.word) to entry } }
                .groupBy({ it.first }, { it.second }).mapValues { (_, entries) -> entries.distinct() }
        }
    }

    @Volatile private var data: Data? = null

    /** Reads the list ahead of the first search. Disk access: not on the
     * main thread. */
    fun preload(context: Context) {
        data(context)
    }

    @Synchronized
    private fun data(context: Context): Data = data ?: load(context).let { byWord ->
        val sorted = byWord.values.distinct().sortedBy { Vikivortaro.key(it.word) }
        Data(byWord, sorted.map { Vikivortaro.key(it.word) }, sorted)
    }.also { data = it }

    private fun entries(context: Context): Map<String, BonaLingvoEntry> = data(context).byWord

    private fun load(context: Context): Map<String, BonaLingvoEntry> {
        val lines = try {
            context.assets.open("bonalingvo/bl.tsv").bufferedReader().useLines { lines ->
                lines.filter { !it.startsWith("#") && it.isNotBlank() }.map { it.split('\t') }.filter { it.size >= 5 }.toList()
            }
        } catch (e: IOException) {
            Log.w(TAG, "Failed to read the list")
            return emptyMap()
        }
        /* The lines of a word follow each other, in the group's order */
        val found = LinkedHashMap<String, BonaLingvoEntry>()
        for ((word, lines) in lines.groupBy { it[0] }) {
            val entry = BonaLingvoEntry(
                word,
                lines[0][1],
                lines.map {
                    Simplification(
                        it[3], it[4], it[2] == "→",
                        article = it.getOrNull(5)?.takeIf { article -> article.isNotEmpty() },
                        parts = it.getOrNull(6)?.takeIf { parts -> parts.isNotEmpty() },
                    )
                },
            )
            found[word] = entry
            /* Also without the case of its first letter, as typed */
            /* Not putIfAbsent: Android 6 lacks it */
            val small = word.lowercase(Locale.ROOT)
            if (small !in found)
                found[small] = entry
        }
        return found
    }

    /** The entry of a word: as written, else in small letters ("Alta
     * Adiĝo"). Disk access: not on the main thread. */
    fun entry(context: Context, word: String): BonaLingvoEntry? {
        val all = entries(context)
        return all[word] ?: all[word.lowercase(Locale.ROOT)]
    }

    /** For the words of an article that Reta Vortaro's remarks don't say
     * anything of from the list, by their section: what the list says,
     * "Laŭ Vortoj de la bona lingvo: prefere diru akvokondukilo.", the
     * words to say links: to their article when Reta Vortaro has them, else
     * to their search.
     * About 1,750 of its words have such a remark already ("Laŭ BL…"). Disk
     * access: not on the main thread. */
    fun remarks(context: Context, layout: ArticleLayout, file: String?): Map<Int, CharSequence> {
        val index = try {
            TrieCache.getTrie(context, "eo")
        } catch (e: IOException) {
            null
        }
        return layout.words.mapNotNull { word ->
            if (word.blocks.any { it is ContentBlock.Note && (it.text.contains("bona lingvo", ignoreCase = true) || it.text.contains("BL:")) })
                return@mapNotNull null
            /* Each of its headwords: "faraĉi, fuŝfari" */
            val headwords = word.title.toString().split(",").map { it.trim() }.filter { it.isNotEmpty() }
            /* As written: "Xyz" the name isn't the list's "xyz" */
            val found = headwords.mapNotNull { headword ->
                entries(context)[headword]?.takeIf { it.word == headword }?.let { entry ->
                    /* Of a word that heads several articles, what is about this one */
                    val here = entry.simplifications.filter { it.article == null || it.article == file }
                    if (here.isEmpty()) null else headword to BonaLingvoEntry(entry.word, entry.context, here)
                }
            }
            if (found.isEmpty())
                null
            else
                word.section to SpannableStringBuilder().apply {
                    append(credit(context))
                    /* The list named once; when the word has several
                     * headwords, what it says of each on its own line */
                    if (headwords.size > 1) {
                        append(":")
                        for ((headword, entry) in found) {
                            append("\n„$headword“")
                            append(remark(context, entry, index))
                        }
                    } else
                        append(remark(context, found[0].second, index))
                }
        }.toMap()
    }

    /* "Laŭ Vortoj de la bona lingvo" */
    private fun credit(context: Context): CharSequence =
        SpannableStringBuilder().apply {
            val according = context.getString(R.string.bl_according, "\u0000")
            append(according.substringBefore('\u0000'))
            val name = context.getString(R.string.bl_name)
            append(name)
            setSpan(StyleSpan(Typeface.ITALIC), length - name.length, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            append(according.substringAfter('\u0000'))
        }

    /** Opens the search on a word that Reta Vortaro doesn't have */
    private class SearchSpan(private val word: String) : ClickableSpan() {
        override fun onClick(view: View) = Screens.searchFor(view.context, word)
    }

    /* " (en la senco de …): prefere diru akvokondukilo." */
    private fun remark(context: Context, entry: BonaLingvoEntry, index: Trie?): CharSequence =
        SpannableStringBuilder().apply {
            if (entry.context.isNotEmpty())
                append(" ${entry.context}")
            append(": ")
            append(context.getString(if (entry.discouraged) R.string.bl_say_instead else R.string.bl_also_say))
            append(" ")
            entry.groups.forEachIndexed { group, (words, wordsContext) ->
                if (group > 0)
                    append("; ")
                words.forEachIndexed { number, word ->
                    if (number > 0)
                        append(", ")
                    val start = length
                    append(word)
                    /* A link, to the word itself when Reta Vortaro has it,
                     * else to its search: what the list says of it, and how
                     * it is built */
                    val found = index?.let { index -> Vikivortaro.revoEsperanto(index, word) }
                    val link = if (found != null) ReferenceSpan(found.article, found.mark) else SearchSpan(word)
                    setSpan(link, start, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                }
                if (wordsContext.isNotEmpty())
                    append(" $wordsContext")
            }
            append(".")
        }

    /** The words of the list starting with the query that neither Reta
     * Vortaro nor Vikivortaro has (their rows say what the list says of
     * theirs), in the order of the search in Esperanto. Disk access: not on
     * the main thread. */
    fun search(context: Context, query: String, index: Trie, stopped: () -> Boolean = { false }): List<BonaLingvoEntry> {
        val data = data(context)
        val typed = query.lowercase(Locale.ROOT).trim()
        val prefix = Vikivortaro.key(typed)
        if (prefix.isEmpty())
            return emptyList()
        var i = data.keys.binarySearch(prefix).let { if (it < 0) -it - 1 else it }
        while (i > 0 && data.keys[i - 1].startsWith(prefix)) i--
        val results = mutableListOf<BonaLingvoEntry>()
        var looked = 0
        while (i < data.keys.size && data.keys[i].startsWith(prefix) && looked < MAX_LOOKED_AT &&
            (looked % 16 != 0 || !stopped())) {
            val entry = data.sorted[i++]
            if (!Vikivortaro.matches(typed, entry.word))
                continue
            looked++
            if (Vikivortaro.revoEsperanto(index, entry.word) == null)
                results.add(entry)
        }
        return results.sortedBy { sortKey(it.word, esperanto = true) }.take(MAX_RESULTS)
    }

    /** A word the list proposes, "uato", that no dictionary has nor the
     * list as its own word: the words it is proposed instead of, each with
     * its entry in Reta Vortaro if it has one; empty for any other word.
     * Disk access: not on the main thread. */
    fun proposing(context: Context, word: String, index: Trie): List<Pair<BonaLingvoEntry, SearchResult?>> {
        val by = data(context).byPreferred[Vikivortaro.key(word)] ?: return emptyList()
        if (entry(context, word) != null || Vikivortaro.revoEsperanto(index, word) != null ||
            Vikivortaro.esperantoResult(context, word) != null || Vikivortaro.frenchOf(context, word).isNotEmpty())
            return emptyList()
        return by.map { it to Vikivortaro.revoEsperanto(index, it.word) }
    }

    /** The entry of a word, if the group advises against it. Disk access:
     * not on the main thread. */
    fun discouraged(context: Context, word: String): BonaLingvoEntry? = entry(context, word)?.takeIf { it.discouragedEverywhere }
}
