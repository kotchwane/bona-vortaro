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
import java.io.IOException
import java.text.Normalizer
import java.util.Locale

/**
 * How often the words of Reta Vortaro are used, per million words, in the
 * Esperanto corpora of the Leipzig Corpora Collection (CC BY 4.0), written by
 * scripts/frequency.py in assets/frequency/. For the order of the search
 * results: of the Esperanto words a word of another language is translated
 * by, the much more frequent one first.
 */
object Frequency {
    private const val TAG = "bonafrequency"

    @Volatile private var data: Map<String, Float>? = null

    @Synchronized
    private fun data(context: Context): Map<String, Float> = data ?: try {
        context.assets.open("frequency/leipzig.tsv").bufferedReader().useLines { lines ->
            lines.filter { !it.startsWith("#") }
                .mapNotNull { line -> line.split('\t').takeIf { it.size == 2 }?.let { it[0] to (it[1].toFloatOrNull() ?: 0f) } }
                .toMap()
        }
    } catch (e: IOException) {
        Log.w(TAG, "Failed to read the frequencies")
        emptyMap()
    }.also { data = it }

    /** How often a word is used, per million words; 0 for a word of several
     * words, or one the corpora don't have. Disk access, the first time. */
    fun of(context: Context, word: String): Float =
        if (' ' in word.trim()) 0f else data(context)[word.trim().lowercase(Locale.ROOT)] ?: 0f

    /** Whether an Esperanto word looks like the word it translates, as
     * "agrabla" like "agréable", "Moldavio" like "Moldavie": the likely
     * translation, which a more frequent word doesn't replace ("dolĉa") */
    fun looksLike(foreign: String, esperanto: String): Boolean {
        val a = stem(foreign).replace("qu", "k").replace("c", "k").replace("ph", "f").replace("th", "t").replace('y', 'i')
        val b = stem(esperanto).replace('c', 'k')
        val longest = maxOf(a.length, b.length)
        return longest > 0 && 1 - distance(a, b).toFloat() / longest >= 0.65f
    }

    /* A word without its accents, case, brackets and Esperanto ending */
    private fun stem(word: String): String {
        val plain = Normalizer.normalize(word.replace(BRACKETS, "").trim().lowercase(Locale.ROOT), Normalizer.Form.NFD)
            .filter { Character.getType(it) != Character.NON_SPACING_MARK.toInt() }
        return if (' ' in plain) plain else plain.replace(ENDING, "")
    }

    private val BRACKETS = Regex("""\(.*?\)""")
    private val ENDING = Regex("""(oj|aj|o|a|i|e|j|n)$""")

    /* The Levenshtein distance */
    private fun distance(a: String, b: String): Int {
        var previous = IntArray(b.length + 1) { it }
        for (i in a.indices) {
            val current = IntArray(b.length + 1)
            current[0] = i + 1
            for (j in b.indices)
                current[j + 1] = minOf(previous[j + 1] + 1, current[j] + 1, previous[j] + if (a[i] == b[j]) 0 else 1)
            previous = current
        }
        return previous[b.length]
    }
}
