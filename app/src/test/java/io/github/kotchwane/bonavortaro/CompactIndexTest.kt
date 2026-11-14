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

    /** The searches of the app, on the old trie and on the compact list */
    private fun searches(query: String, esperanto: Boolean): List<Pair<(OldTrie, Array<SearchResult?>) -> Int, (Trie, Array<SearchResult?>) -> Int>> =
        listOf(
            Pair({ t, r -> t.search(query, r) }, { t, r -> t.search(query, r) }),
            Pair({ t, r -> t.searchIgnoringAccents(query, r, esperanto) },
                { t, r -> t.searchIgnoringAccents(query, r, esperanto) }),
            Pair({ t, r -> if ('*' in query || '?' in query) t.searchPattern(query, r) else 0 },
                { t, r -> if ('*' in query || '?' in query) t.searchPattern(query, r) else 0 }),
        )

    private fun shown(results: Array<SearchResult?>, count: Int) =
        results.take(count).map { Triple(it!!.word, it.article, it.mark) }

    @Test
    fun theOldTrieAndTheCompactListFindTheSame() {
        val random = Random(2026_11_13)
        for ((small, original) in pairs()) {
            val esperanto = small.name == "index-eo.bin"
            val before = original.inputStream().use { OldTrie(it) }
            val after = small.inputStream().use { Trie(it) }
            /* Words from the whole index, not only its first ones: their
             * starts of 1 to 3 letters, a few whole, and patterns */
            val all = arrayOfNulls<SearchResult>(200_000)
            val words = before.search("", all).let { n -> all.take(n).filterNotNull().map { it.word.lowercase() } }
            val queries = (1..3).flatMap { length ->
                List(20) { words[random.nextInt(words.size)].take(length) }
            } + List(5) { words[random.nextInt(words.size)] } +
                List(3) { words[random.nextInt(words.size)].let { w -> "${w.take(1)}*${w.takeLast(1)}" } } +
                listOf("a", "e", "*o", "?a*", "", "zzzz", "A", "É")
            for (query in queries.distinct()) {
                for ((old, new) in searches(query, esperanto)) {
                    for (size in listOf(1, 64)) {
                        val r1 = arrayOfNulls<SearchResult>(size)
                        val r2 = arrayOfNulls<SearchResult>(size)
                        assertEquals("${small.name} \"$query\" ($size)", shown(r1, old(before, r1)), shown(r2, new(after, r2)))
                    }
                }
            }
        }
    }

    /** Every start of word in a few languages: the whole word and each of
     * its starts, as typed with and without its accents, and in capitals */
    @Test
    fun everyStartOfWordFindsTheSame() {
        for (language in listOf("eo", "fr", "de", "ja", "be")) {
            val esperanto = language == "eo"
            val before = File(raw, "index-$language.bin").takeIf { it.exists() }?.inputStream()?.use { OldTrie(it) }
            assumeTrue("The dictionary hasn't been built", before != null)
            val after = File(compact, "index-$language.bin").inputStream().use { Trie(it) }
            val all = arrayOfNulls<SearchResult>(200_000)
            val words = shown(all, before!!.search("", all)).map { it.first }
            val random = Random(language.hashCode())
            val queries = words.shuffled(random).take(300).flatMap { word ->
                val plain = java.text.Normalizer.normalize(word, java.text.Normalizer.Form.NFD).replace(Regex("\\p{Mn}"), "")
                (1..minOf(word.length, 6)).map { word.take(it) } + listOf(word, plain, word.uppercase(), "*${word.takeLast(3)}")
            }.distinct()
            for (query in queries)
                for ((old, new) in searches(query, esperanto)) {
                    val r1 = arrayOfNulls<SearchResult>(32)
                    val r2 = arrayOfNulls<SearchResult>(32)
                    assertEquals("$language \"$query\"", shown(r1, old(before, r1)), shown(r2, new(after, r2)))
                }
        }
    }

    /** How long the searches take, on the old trie and the compact list */
    @Test
    fun timeTheSearches() {
        for (language in listOf("eo", "fr", "ja", "zh")) {
            val file = File(raw, "index-$language.bin")
            assumeTrue(file.exists())
            val before = file.inputStream().use { OldTrie(it) }
            var after: Trie? = null
            val load = (1..5).minOf { kotlin.system.measureNanoTime { after = File(compact, "index-$language.bin").inputStream().use { Trie(it) } } }
            val esperanto = language == "eo"
            val queries = listOf("a", "ka", "kat", "e", "s", "*o", "*ologio", "?a*", "m", "た", "日")
            for ((name, index) in listOf(0 to "search", 1 to "accents", 2 to "pattern").map { it.second to it.first }) {
                fun time(run: () -> Unit) = (1..3).minOf { kotlin.system.measureNanoTime { repeat(20) { run() } } } / 20 / 1000
                val old = time { for (q in queries) searches(q, esperanto)[index].first(before, arrayOfNulls(64)) }
                val new = time { for (q in queries) searches(q, esperanto)[index].second(after!!, arrayOfNulls(64)) }
                println("$language $name: old ${old} µs, compact ${new} µs (11 queries)")
            }
            println("$language load ${load / 1000} µs")
        }
    }
}
