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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

/** Searching in Japanese as it is typed: in hiragana before choosing the
 * characters, in katakana as the names of animals are written (a
 * Japanese reader, 2026-11-13) */
class JapaneseSearchTest {
    @Test
    fun aKatakanaLetterIsItsHiragana() {
        assertEquals("い", Trie.hiragana("イ"))
        assertEquals("ぬ", Trie.hiragana("ぬ"))
        assertEquals("ー", Trie.hiragana("ー"))
        assertTrue(Trie.sameKana("イ", "い"))
        assertTrue(Trie.sameKana("い", "イ"))
        /* Not the same letter, nor a letter compared with itself here */
        assertFalse(Trie.sameKana("イ", "ぬ"))
        assertFalse(Trie.sameKana("い", "い"))
        assertFalse(Trie.sameKana("a", "a"))
    }

    private fun found(query: String): List<String> {
        val file = File("src/main/assets/indices/index-ja.bin")
        assumeTrue("The dictionary hasn't been built", file.exists())
        val trie = file.inputStream().use { Trie(it) }
        val results = arrayOfNulls<SearchResult>(32)
        val count = trie.searchIgnoringAccents(query, results)
        return results.take(count).filterNotNull().map { it.word }
    }

    @Test
    fun aWordIsFoundByItsCharactersItsReadingAndItsKatakana() {
        assertTrue("犬（いぬ）" in found("犬"))
        assertTrue("犬（いぬ）" in found("いぬ"))
        assertTrue("犬（いぬ）" in found("イヌ"))
        assertTrue("食べる（たべる）" in found("たべ"))
        /* A katakana word by its hiragana reading too */
        assertTrue("コンピュータ（こんぴゅーた）" in found("こんぴゅーた"))
    }

    @Test
    fun aWordFoundByItsReadingComesBeforeLongerWords() {
        val searched = sortKey("いぬ", esperanto = false)
        val words = listOf("イヌイット", "イヌワシ", "犬の（いぬの）", "犬（いぬ）")
        /* Then by reading, as a Japanese dictionary orders them: いぬいっと
         * before いぬの */
        assertEquals(listOf("犬（いぬ）", "イヌイット", "犬の（いぬの）", "イヌワシ"),
            words.sortedBy { orderKey(it, searched, esperanto = false) })
    }
}
