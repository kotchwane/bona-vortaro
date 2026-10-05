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
import java.util.Locale

/** Finding the entries of a word read in an article, as it is written
 * there: "arbojn" is found as "arbo", "kuris" as "kuri". */
object WordLookup {
    private const val TAG = "bonaword"
    private const val MAX_ENTRIES = 4

    /* The endings of the verbs, and of the participles before their own
     * ending (-anta, -inte…) */
    private val VERB_ENDINGS = listOf("as", "is", "os", "us", "u")
    private val PARTICIPLES = listOf("ant", "int", "ont", "at", "it", "ot")
    private val WORD_ENDINGS = listOf("o", "a", "e", "i")

    /** The forms under which an Esperanto word may be in the dictionary,
     * the likeliest first: the word itself, then without the plural and
     * the accusative, then with the other endings of its root. */
    internal fun candidates(word: String): List<String> {
        val forms = mutableListOf<String>()
        val lower = word.lowercase(Locale.ROOT)
        forms.add(lower)

        /* The plural and the accusative */
        val bare = when {
            lower.endsWith("jn") && lower.length > 3 -> lower.dropLast(2)
            (lower.endsWith("j") || lower.endsWith("n")) && lower.length > 2 -> lower.dropLast(1)
            else -> lower
        }
        forms.add(bare)

        /* A verb: its infinitive */
        VERB_ENDINGS.firstOrNull { bare.endsWith(it) && bare.length > it.length + 1 }?.let {
            forms.add(bare.dropLast(it.length) + "i")
        }

        /* A noun, an adjective or an adverb: the root with each ending,
         * and a participle: the infinitive of its verb */
        WORD_ENDINGS.firstOrNull { bare.endsWith(it) && bare.length > 2 }?.let { ending ->
            val root = bare.dropLast(ending.length)
            for (other in WORD_ENDINGS)
                forms.add(root + other)
            PARTICIPLES.firstOrNull { root.endsWith(it) && root.length > it.length + 1 }?.let {
                forms.add(root.dropLast(it.length) + "i")
            }
        }

        return forms.distinct()
    }

    /** The entries of a word in the index of a language: in Esperanto,
     * those of its likeliest forms; in another language, the word as it
     * is. Disk access: not on the main thread. */
    fun find(context: Context, word: String, language: String): List<SearchResult> {
        val trie = try {
            TrieCache.getTrie(context, language)
        } catch (e: IOException) {
            Log.w(TAG, "Failed to load the index for $language")
            return emptyList()
        }
        val forms = if (language == "eo") candidates(word) else listOf(word.lowercase(Locale.ROOT))
        val results = arrayOfNulls<SearchResult>(32)

        return forms.flatMap { form ->
            /* The words of the node come first, then the longer ones */
            val count = trie.search(form, results)
            results.take(count).filterNotNull().filter { it.word.lowercase(Locale.ROOT) == form }
        }.distinctBy { it.article to it.mark }.take(MAX_ENTRIES)
    }

    /** The word around a place in a text, or null if the place isn't
     * in a word: letters only, so "arbojn," gives "arbojn". */
    fun wordAt(text: CharSequence, offset: Int): String? {
        var start = offset.coerceIn(0, text.length)
        /* Past the end of a line, the last letter of it */
        if ((start >= text.length || !text[start].isLetter()) && start > 0 && text[start - 1].isLetter())
            start--
        if (start >= text.length || !text[start].isLetter())
            return null

        var end = start
        while (start > 0 && text[start - 1].isLetter())
            start--
        while (end < text.length && text[end].isLetter())
            end++

        return text.substring(start, end)
    }
}
