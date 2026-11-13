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

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import kotlin.random.Random

/** The compact indices the app ships give back the tries prevodb made,
 * which scripts/generate-dictionary.sh also writes as they are into
 * prevodb-build/raw-indices: byte for byte, and so the same results for
 * any search */
class CompactIndexTest {
    private val compact = File("src/main/assets/indices")
    private val raw = File("../prevodb-build/raw-indices")

    private fun pairs(): List<Pair<File, File>> {
        assumeTrue("The dictionary hasn't been built", raw.isDirectory && compact.isDirectory)
        val files = compact.listFiles { f -> f.name.startsWith("index-") }!!.sortedBy { it.name }
        assertTrue("no index", files.isNotEmpty())
        return files.map { it to File(raw, it.name) }
    }

    @Test
    fun everyCompactIndexGivesItsTrieBackByteForByte() {
        val pairs = pairs()
        for ((small, original) in pairs) {
            assertTrue("${small.name}: no trie to compare with", original.exists())
            assertArrayEquals(small.name, original.readBytes(), CompactIndex.decode(small.readBytes()))
        }
        /* As many as Reta Vortaro has languages */
        assertTrue(pairs.size > 100)
    }

    @Test
    fun theOldTrieAndTheCompactOneFindTheSame() {
        val random = Random(2026_11_13)
        for ((small, original) in pairs()) {
            val before = original.inputStream().use { Trie(it) }
            val after = small.inputStream().use { Trie(it) }
            /* The letters the words of this language begin with */
            val words = arrayOfNulls<SearchResult>(400)
            val letters = before.searchIgnoringAccents("", words, small.name == "index-eo.bin").let { n ->
                words.take(n).filterNotNull().map { it.word.lowercase() }
            }
            val queries = (1..3).flatMap { length ->
                List(20) { letters[random.nextInt(letters.size)].take(length) }
            } + listOf("a", "e", "*o", "?a*", "")
            for (query in queries.distinct()) {
                for (search in listOf<(Trie, Array<SearchResult?>) -> Int>(
                    { t, r -> t.search(query, r) },
                    { t, r -> t.searchIgnoringAccents(query, r, small.name == "index-eo.bin") },
                    { t, r -> if ('*' in query || '?' in query) t.searchPattern(query, r) else 0 },
                )) {
                    val r1 = arrayOfNulls<SearchResult>(64)
                    val r2 = arrayOfNulls<SearchResult>(64)
                    val n1 = search(before, r1)
                    val n2 = search(after, r2)
                    assertEquals("${small.name} \"$query\"", r1.take(n1).map { Triple(it!!.word, it.article, it.mark) },
                        r2.take(n2).map { Triple(it!!.word, it.article, it.mark) })
                }
            }
        }
    }
}
