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
    fun aFrenchWordWithUxIsFound() {
        val file = File("src/main/assets/indices/index-fr.bin")
        assumeTrue("The dictionary hasn't been built", file.exists())
        val trie = file.inputStream().use { Trie(it) }
        val results = arrayOfNulls<SearchResult>(32)
        for (word in listOf("heureux", "deux", "faux", "afflux"))
            assertTrue(word, trie.searchIgnoringAccents(normaliseQuery(word, xSystem = false), results) > 0)
    }
}
