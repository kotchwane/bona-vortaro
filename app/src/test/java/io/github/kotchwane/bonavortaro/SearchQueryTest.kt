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

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

class SearchQueryTest {
    @Test
    fun theXSystemIsOnlyEsperanto() {
        assertEquals("ĉevalo", normaliseQuery(" Cxevalo "))
        assertEquals("heureux", normaliseQuery("Heureux", xSystem = false))
    }

    @Test
    fun theCircumflexIsBothSpellingsInFrench() {
        assertEquals("fraicheur", searchFilter("Fraîcheur", "fr"))
        assertEquals("ŝtono", searchFilter("sxtono", "eo"))
    }

    private fun french(): (String) -> List<String> {
        val file = File("src/main/assets/indices/index-fr.bin")
        assumeTrue("The dictionary hasn't been built", file.exists())
        val trie = file.inputStream().use { Trie(it) }
        return { query ->
            val results = arrayOfNulls<SearchResult>(128)
            val count = trie.searchIgnoringAccents(searchFilter(query, "fr"), results)
            results.take(count).map { it!!.word }
        }
    }

    @Test
    fun aFrenchWordWithUxIsFound() {
        val search = french()
        for (word in listOf("heureux", "deux", "faux", "afflux"))
            assertTrue(word, search(word).isNotEmpty())
    }

    @Test
    fun aFrenchWordWithACircumflexIsFoundInBothSpellings() {
        val search = french()
        for (word in listOf("fraîcheur", "rafraîchir", "traîneau", "goûter"))
            assertTrue(word, search(word).isNotEmpty())
        assertTrue(search("maître").containsAll(listOf("maître", "maitre (titre univ.)")))
        assertTrue(search("île").containsAll(listOf("ile", "Île de Man")))
    }

    private fun index(language: String): Trie {
        val file = File("src/main/assets/indices/index-$language.bin")
        assumeTrue("The dictionary hasn't been built", file.exists())
        return file.inputStream().use { Trie(it) }
    }

    @Test
    fun theResultsAreInAlphabeticalOrderAccentsAside() {
        val trie = index("fr")
        /* The 128 that the search keeps */
        val results = arrayOfNulls<SearchResult>(128)
        val words = results.take(trie.searchIgnoringAccents("com", results)).map { it!!.word }
        val comedie = words.indexOfFirst { it.startsWith("comédie") }
        assertTrue(words.toString(), comedie > words.indexOfLast { it.startsWith("combustible") })
        assertTrue(words.toString(), comedie < words.indexOf("comique"))
    }

    @Test
    fun inEsperantoTheHattedLettersComeAfterTheirOwn() {
        val trie = index("eo")
        val results = arrayOfNulls<SearchResult>(5000)
        val words = results.take(trie.searchIgnoringAccents("a", results, true)).map { it!!.word.lowercase() }
        /* aĉ… after ac…, before ad… */
        val hatted = words.indexOfFirst { it.startsWith("aĉ") }
        assertTrue(hatted > words.indexOfLast { it.startsWith("ac") })
        assertTrue(hatted < words.indexOfFirst { it.startsWith("ad") })
        /* c… then ĉ…, as before */
        val c = results.take(trie.searchIgnoringAccents("c", results, true)).map { it!!.word.lowercase() }
        assertTrue(c.indexOfLast { it.startsWith("c") } < c.indexOfFirst { it.startsWith("ĉ") })
    }
}
