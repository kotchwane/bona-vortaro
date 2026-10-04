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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

class WordLookupTest {
    private fun firstFound(word: String): String? {
        val file = File("src/main/assets/indices/index-eo.bin")
        assumeTrue("The dictionary hasn't been built", file.exists())
        val trie = file.inputStream().use { Trie(it) }
        val results = arrayOfNulls<SearchResult>(32)

        return WordLookup.candidates(word).firstNotNullOfOrNull { form ->
            val count = trie.search(form, results)
            results.take(count).firstOrNull { it!!.word.lowercase() == form }?.word
        }
    }

    @Test
    fun pluralAndAccusative() {
        assertEquals("arbo", firstFound("arbojn"))
        assertEquals("arbo", firstFound("arboj"))
        assertEquals("arbo", firstFound("arbon"))
        assertEquals("bela", firstFound("belajn"))
    }

    @Test
    fun verbs() {
        assertEquals("kuri", firstFound("kuris"))
        assertEquals("kuri", firstFound("kuru"))
        assertEquals("manĝi", firstFound("manĝos"))
    }

    @Test
    fun participlesAndOtherEndings() {
        /* "kuranta" has its own entry, before the verb */
        assertEquals("kuranta", firstFound("kurantaj"))
        assertEquals("kuri", WordLookup.candidates("kurantaj").last())
        assertTrue(firstFound("rapide") in listOf("rapide", "rapida"))
    }

    @Test
    fun theWordItselfFirst() {
        assertEquals("kaj", firstFound("kaj"))
        assertEquals("ankaŭ", firstFound("Ankaŭ"))
    }

    @Test
    fun wordAtAPlace() {
        val text = "Ligna planto, kun alta trunko."
        assertEquals("planto", WordLookup.wordAt(text, 8))
        assertEquals("planto", WordLookup.wordAt(text, 12))
        /* On the full stop just after it */
        assertEquals("trunko", WordLookup.wordAt(text, text.length - 1))
        assertNull(WordLookup.wordAt(text, 13))
        assertEquals("ĉevalo", WordLookup.wordAt("la ĉevalo", 3))
    }
}
