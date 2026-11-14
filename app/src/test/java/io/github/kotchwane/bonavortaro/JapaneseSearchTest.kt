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

    private fun found(query: String, limit: Int = 32): List<String> {
        val file = File("src/main/assets/indices/index-ja.bin")
        assumeTrue("The dictionary hasn't been built", file.exists())
        val trie = file.inputStream().use { Trie(it) }
        val results = arrayOfNulls<SearchResult>(limit)
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

    /** The reading of a word as shown, "餓鬼（がき）" → "がき" */
    private fun reading(word: String) = word.substringAfter('（', word).substringBefore('）')

    @Test
    fun theVoicingMarksAreNotAccents() {
        /* "かき" ka-ki: 火器, 下記…, not 餓鬼 ga-ki nor 鍵 ka-gi */
        val kaki = found("かき", 128)
        assertTrue(kaki.toString(), "火器（かき）" in kaki)
        assertTrue(kaki.none { reading(it).startsWith("が") || reading(it).startsWith("かぎ") })
        assertTrue(kaki.isNotEmpty())
        /* The same in katakana */
        assertEquals(kaki, found("カキ", 128))
        /* Typed with its mark, the voiced word, in either script */
        assertTrue("がき" in found("がき"))
        assertTrue("がき" in found("ガキ"))
        /* "は" ha: no "ば" ba nor "ぱ" pa */
        assertTrue(found("は", 128).none { reading(it).startsWith("ば") || reading(it).startsWith("ぱ") })
    }

    @Test
    fun oneKanaFindsTheKatakanaWordsInTheirPlace() {
        /* "い": more words than are kept; the katakana words among them in
         * the order of their reading, not after all the hiragana ones */
        val i = found("い", 128)
        assertEquals(128, i.size)
        assertTrue(i.toString(), i.any { w -> w.all { it in '\u30a0'..'\u30ff' } })
        assertEquals(i, found("イ", 128))
    }

    @Test
    fun theFinalLongVowelIsTriedAsTypedThenWithout() {
        assertEquals(listOf("コンピューター", "コンピュータ"), DictionarySearch.spellings("コンピューター"))
        /* Too short to be a word with its long vowel */
        assertEquals(listOf("ター"), DictionarySearch.spellings("ター"))
        /* A pattern is whole words, as typed */
        assertEquals(listOf("*ター"), DictionarySearch.spellings("*ター"))
        assertEquals(listOf("コ?ー"), DictionarySearch.spellings("コ?ー"))
    }
}
