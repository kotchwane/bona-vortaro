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

package uk.co.busydoingnothing.prevo

import android.content.Context
import android.util.Log
import java.io.IOException

/** The result of a search: the matching words, and the index of the
 * language (in the list that was searched) where they were found. */
class SearchOutcome(val results: List<SearchResult>, val languageIndex: Int)

object DictionarySearch {
    private const val MAX_RESULTS = 128
    private const val TAG = "bonasearch"

    /** Searches the languages in order and returns the results of the
     * first one that has any. Typing the x-system is accepted (cx → ĉ), and
     * the accents can be left out ("eleve" finds "élève").
     * This does disk access, so it shouldn't run on the main thread. */
    fun search(context: Context, languages: List<String>, query: String): SearchOutcome {
        val filter = normaliseQuery(query)

        for ((index, language) in languages.withIndex()) {
            val trie = try {
                TrieCache.getTrie(context, language)
            } catch (e: IOException) {
                /* Skip the language instead of crashing, in case it has
                 * disappeared in an update of the dictionary data */
                Log.w(TAG, "Failed to load the index for $language")
                continue
            }

            val results = arrayOfNulls<SearchResult>(MAX_RESULTS)
            /* Ignoring the accents, the exact matches first */
            val count = trie.searchIgnoringAccents(filter, results)

            if (count > 0)
                return SearchOutcome(results.take(count).filterNotNull(), index)
        }

        return SearchOutcome(emptyList(), 0)
    }
}
