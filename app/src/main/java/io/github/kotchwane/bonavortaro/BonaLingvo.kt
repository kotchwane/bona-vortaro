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
import android.text.style.StyleSpan
import android.util.Log
import java.io.IOException
import java.util.Locale

/** A word to say instead of another: [word], in its [context] ("(en la
 * faka lingvo)"), "" if none; whether the other is advised against
 * ("malrekomendo", →) or only has this simpler alternative ("alternativo",
 * ~); whether the group has checked it. */
class Simplification(val word: String, val context: String, val discouraged: Boolean, val checked: Boolean)

/** A word of "Vortoj de la bona lingvo", in its [context] ("(en gramatiko)"),
 * "" if none, with the words to say instead, in the group's order. */
class BonaLingvoEntry(val word: String, val context: String, val simplifications: List<Simplification>) {
    /** Advised against, rather than given simpler alternatives */
    val discouraged: Boolean get() = simplifications.any { it.discouraged }

    /** The words to say instead: those of the advice against it, if any */
    val preferred: List<Simplification> get() = simplifications.filter { it.discouraged == discouraged }

    /** Checked by the group: the words from PIV or Reta Vortaro aren't yet */
    val checked: Boolean get() = preferred.all { it.checked }
}

/**
 * "Vortoj de la bona lingvo" (labonalingvo.org), by Renato Corsetti and the
 * working group of La Bona Lingvo: neologisms, and the Esperanto words to
 * say instead, in Claude Piron's spirit. Used with their permission; not
 * under the GPL. In assets/bonalingvo/bl.tsv, written by
 * scripts/bona-lingvo.py from data/bona-lingvo.csv.
 */
object BonaLingvo {
    private const val TAG = "bonalingvo"
    const val SITE = "https://labonalingvo.org/"

    @Volatile private var entries: Map<String, BonaLingvoEntry>? = null

    /** Reads the list ahead of the first search. Disk access: not on the
     * main thread. */
    fun preload(context: Context) {
        entries(context)
    }

    @Synchronized
    private fun entries(context: Context): Map<String, BonaLingvoEntry> = entries ?: load(context).also { entries = it }

    private fun load(context: Context): Map<String, BonaLingvoEntry> {
        val lines = try {
            context.assets.open("bonalingvo/bl.tsv").bufferedReader().useLines { lines ->
                lines.filter { !it.startsWith("#") && it.isNotBlank() }.map { it.split('\t') }.filter { it.size == 6 }.toList()
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
                lines.map { Simplification(it[3], it[4], it[2] == "→", it[5] == "1") },
            )
            found[word] = entry
            /* Also without the case of its first letter, as typed */
            found.putIfAbsent(word.lowercase(Locale.ROOT), entry)
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
     * words to say a link to their article when Reta Vortaro has them.
     * About 1,750 of its words have such a remark already ("Laŭ BL…"). Disk
     * access: not on the main thread. */
    fun remarks(context: Context, layout: ArticleLayout): Map<Int, CharSequence> {
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
            val found = headwords.mapNotNull { headword -> entry(context, headword)?.let { headword to it } }
            if (found.isEmpty())
                null
            else
                word.section to SpannableStringBuilder().apply {
                    found.forEachIndexed { number, (headword, entry) ->
                        if (number > 0)
                            append("\n")
                        /* Whose it is, when the word has several */
                        if (headwords.size > 1)
                            append("„$headword“: ")
                        append(remark(context, entry, index))
                    }
                }
        }.toMap()
    }

    private fun remark(context: Context, entry: BonaLingvoEntry, index: Trie?): CharSequence =
        SpannableStringBuilder().apply {
            val according = context.getString(R.string.bl_according, "\u0000")
            append(according.substringBefore('\u0000'))
            val name = context.getString(R.string.bl_name)
            append(name)
            setSpan(StyleSpan(Typeface.ITALIC), length - name.length, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            append(according.substringAfter('\u0000'))
            if (entry.context.isNotEmpty())
                append(" ${entry.context}")
            append(": ")
            append(context.getString(if (entry.discouraged) R.string.bl_say_instead else R.string.bl_also_say))
            append(" ")
            entry.preferred.forEachIndexed { number, it ->
                if (number > 0)
                    append(", ")
                val start = length
                append(it.word)
                /* A link, to the word itself, when Reta Vortaro has it */
                index?.let { index -> Vikivortaro.revoEsperanto(index, it.word) }?.let { found ->
                    setSpan(ReferenceSpan(found.article, found.mark), start, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                }
                if (it.context.isNotEmpty())
                    append(" ${it.context}")
            }
            append(".")
            if (!entry.checked)
                append(" " + context.getString(R.string.bl_unchecked))
        }

    /** The entry of a word, if the group advises against it. Disk access:
     * not on the main thread. */
    fun discouraged(context: Context, word: String): BonaLingvoEntry? = entry(context, word)?.takeIf { it.discouraged }
}
