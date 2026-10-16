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
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

class RevoFilesTest {
    private val names = listOf("cxeval", "kukurb", "potas", "potenc", "potr", "spic", "spion")

    @Test
    fun aWordGoesAmongTheRootsByItsOwn() {
        /* "potiron", between "potenc" and "potr" */
        assertEquals(4, RevoFiles.placeAmong(names, "potirono"))
        /* In the x-system, without its ending */
        assertEquals(1, RevoFiles.placeAmong(names, "ĉevalido"))
        assertEquals(6, RevoFiles.placeAmong(names, "spicisto"))
        /* Any ending, but two letters of root at least */
        assertEquals(2, RevoFiles.placeAmong(names, "kukurbojn"))
        assertEquals(4, RevoFiles.placeAmong(names, "potiras"))
        assertEquals(0, RevoFiles.placeAmong(names, "aj"))
        /* A root it has: right after it */
        assertEquals(2, RevoFiles.placeAmong(names, "kukurbo"))
    }

    @Test
    fun theFilesAreTheArticlesInOrder() {
        val files = File("src/main/assets/articles/files.txt")
        val index = File("src/main/assets/indices/index-eo.bin")
        assumeTrue("The dictionary hasn't been built", files.exists() && index.exists())
        val names = files.readLines()
        val trie = index.inputStream().use { Trie(it) }
        for ((word, file) in listOf("korveto" to "korvet", "ĉevalo" to "cxeval", "zorgi" to "zorg")) {
            val results = arrayOfNulls<SearchResult>(8)
            val count = trie.search(word, results)
            val article = results.take(count).first { it!!.word == word }!!.article
            assertEquals(file, names[article])
        }
        assertEquals(names.sorted(), names)
    }
}
