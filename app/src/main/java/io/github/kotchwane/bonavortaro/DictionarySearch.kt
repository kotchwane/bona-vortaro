/*
 * Bona vortaro - an Esperanto dictionary for Android, based on PReVo
 * Copyright (C) 2012, 2016  Neil Roberts
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
import java.text.Normalizer

/** The result of a search: the matching words, and the index of the
 * language (in the list that was searched) where they were found. */
class SearchOutcome(val results: List<SearchResult>, val languageIndex: Int)

/** Whether a query is a pattern, with "*" or "?" in it */
fun isPattern(query: String): Boolean = '*' in query || '?' in query

/** What is searched in a language for a query: the x-system is read only
 * in Esperanto, where "ux" is a "ŭ"; in French, "î" and "û" are searched
 * as "i" and "u", which also find them, because Reta Vortaro has both
 * spellings, "maître" and "maitre", but some words only in the 1990 one:
 * "fraicheur", "ile". */
fun searchFilter(query: String, language: String): String {
    /* The apostrophe typed as the straight one, which also finds the
     * curly one */
    val filter = Normalizer.normalize(normaliseQuery(query, xSystem = language == "eo"), Normalizer.Form.NFC)
        .replace('’', '\'')
        /* Non-breaking spaces as spaces; "œ" as "oe", which finds both */
        .replace('\u00a0', ' ').replace('\u202f', ' ')
        .replace("œ", "oe").replace("æ", "ae")
    return if (language == "fr") filter.replace('î', 'i').replace('û', 'u') else filter
}

object DictionarySearch {
    private const val MAX_RESULTS = 128
    /* A pattern like "*o" can match most of the language */
    private const val MAX_PATTERN_RESULTS = 500
    private const val TAG = "bonasearch"

    /** Searches the languages in order and returns the results of the
     * first one that has any. Typing the x-system is accepted in Esperanto
     * (cx → ĉ), and the accents can be left out ("eleve" finds "élève").
     * With "*" (any letters) or "?" (one letter), the query is a pattern
     * for whole words.
     * This does disk access, so it shouldn't run on the main thread. */
    fun search(context: Context, languages: List<String>, query: String): SearchOutcome {
        for ((index, language) in languages.withIndex()) {
            val filter = searchFilter(query, language)
            val trie = try {
                TrieCache.getTrie(context, language)
            } catch (e: IOException) {
                /* Skip the language instead of crashing, in case it has
                 * disappeared in an update of the dictionary data */
                Log.w(TAG, "Failed to load the index for $language")
                continue
            }

            val count: Int
            val results: Array<SearchResult?>
            if (isPattern(filter)) {
                results = arrayOfNulls(MAX_PATTERN_RESULTS)
                count = trie.searchPattern(filter, results)
            } else {
                results = arrayOfNulls(MAX_RESULTS)
                /* Ignoring the accents, in alphabetical order */
                count = trie.searchIgnoringAccents(filter, results, language == "eo")
            }

            if (count > 0)
                /* The index has some entries twice, the same word for the
                 * same sense: once */
                return SearchOutcome(results.take(count).filterNotNull().distinctBy { Triple(it.word, it.article, it.mark) }, index)
            /* An Esperanto word with its endings, "hundoj", "kuris": its
             * entries, as a long press finds them */
            if (language == "eo" && !isPattern(filter)) {
                val entries = inflected(trie, filter)
                if (entries.isNotEmpty())
                    return SearchOutcome(entries, index)
            }
        }

        return SearchOutcome(emptyList(), 0)
    }

    /** The entries of the likeliest forms of an Esperanto word, other than
     * as typed: "hundoj" as "hundo" */
    private fun inflected(trie: Trie, word: String): List<SearchResult> {
        val results = arrayOfNulls<SearchResult>(16)
        for (form in WordLookup.candidates(word).drop(1)) {
            val count = trie.search(form, results)
            val found = results.take(count).filterNotNull().filter { it.word.lowercase(Locale.ROOT) == form }
            if (found.isNotEmpty())
                return found
        }
        return emptyList()
    }
}
