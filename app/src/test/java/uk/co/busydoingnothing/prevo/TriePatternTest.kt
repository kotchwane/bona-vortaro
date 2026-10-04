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


package uk.co.busydoingnothing.prevo

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test

/** The patterns of the search, on the real Esperanto index, which the
 * build of the dictionary makes. */
class TriePatternTest {
    private lateinit var trie: Trie

    @Before
    fun load() {
        val file = File("src/main/assets/indices/index-eo.bin")
        assumeTrue("The dictionary hasn't been built", file.exists())
        trie = file.inputStream().use { Trie(it) }
    }

    private fun pattern(query: String): List<String> {
        val results = arrayOfNulls<SearchResult>(5000)
        val count = trie.searchPattern(query, results)
        return results.take(count).map { it!!.word }
    }

    private fun prefix(query: String): List<String> {
        val results = arrayOfNulls<SearchResult>(5000)
        val count = trie.searchIgnoringAccents(query, results)
        return results.take(count).map { it!!.word }
    }

    @Test
    fun starAtTheStartFindsTheEnds() {
        val words = pattern("*ologio")
        assertTrue("biologio" in words)
        assertTrue("geologio" in words)
        assertTrue(words.all { it.lowercase().endsWith("ologio") })
    }

    @Test
    fun questionMarkIsOneLetter() {
        val words = pattern("?rbo")
        assertTrue("arbo" in words)
        assertTrue(words.all { it.length == 4 && it.endsWith("rbo") })
    }

    @Test
    fun starAtTheEndIsThePrefixSearch() {
        assertEquals(prefix("arb").toSet(), pattern("arb*").toSet())
    }

    @Test
    fun severalStarsFindEachWordOnce() {
        val results = arrayOfNulls<SearchResult>(5000)
        val count = trie.searchPattern("*arb**o*", results)
        /* A word can be in two articles (homonyms), but each entry only once */
        val entries = results.take(count).map { Triple(it!!.word, it.article, it.mark) }
        assertEquals(entries.size, entries.toSet().size)
        assertEquals(pattern("*arb*o*").toSet(), entries.map { it.first }.toSet())
    }

    @Test
    fun lettersWithoutAccentsMatchAccentedOnes() {
        assertTrue("ĉevalo" in pattern("cevalo*"))
        assertTrue("ĉevalo" !in pattern("cxevalo".replace("cx", "c") + "x"))
        assertTrue(pattern("ĉ?valo").all { it.startsWith("ĉ") })
    }

    @Test
    fun aWholeWordWithoutWildcardIsExact() {
        assertEquals(listOf("arbo"), pattern("arbo").filter { it == "arbo" })
        assertTrue(pattern("arbo").all { it.lowercase() == "arbo" })
    }
}
