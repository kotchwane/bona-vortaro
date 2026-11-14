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

    /** The reading of a word as shown, in hiragana: "餓鬼（がき）" and
     * "ガキ" are "がき" */
    private fun reading(word: String) =
        word.substringAfter('（', word).substringBefore('）').map { Trie.hiragana(it.toString()) }.joinToString("")

    /** The reading as the order has it: without the voicing marks */
    private fun unvoiced(word: String) =
        java.text.Normalizer.normalize(reading(word), java.text.Normalizer.Form.NFD).filter { it.code != 0x3099 && it.code != 0x309a }

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
        /* "は" ha: no "ば" ba nor "ぱ" pa; "ハ" neither, in either script */
        for (ha in listOf("は", "ハ")) {
            val words = found(ha, 128)
            assertTrue(words.isNotEmpty())
            assertTrue(words.toString(), words.none { reading(it).startsWith("ば") || reading(it).startsWith("ぱ") })
        }
        /* "カ" doesn't find a katakana "ガ" word either */
        val ka = found("カ", 128)
        assertTrue(ka.isNotEmpty())
        assertTrue(ka.none { reading(it).startsWith("が") })
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
    fun theResultsComeInTheOrderOfAJapaneseDictionary() {
        /* Katakana among the hiragana, not before nor after them all */
        val i = found("い", 2000).map { w -> w.all { it in '\u30a0'..'\u30ff' } }
        assertFalse("the first word is a katakana one", i.first())
        val firstKatakana = i.indexOf(true)
        assertTrue(firstKatakana > 0 && i.drop(firstKatakana).contains(false))
        /* A voiced kana right after the same without its mark, at the same
         * place: "かぎ" before the longer "かき…", "かきあつめる" */
        val ka = found("か", 2000).map(::reading)
        val kagi = ka.indexOf("かぎ")
        assertTrue(ka.toString(), kagi > 0)
        assertTrue(ka.drop(kagi).any { it.startsWith("かき") && it.length > 2 })
        /* The long vowel ー as the vowel it lengthens: "コーヒー" as
         * "こおひい", before the "こん…" */
        val ko = found("コ", 2000)
        val coffee = ko.indexOfFirst { it.startsWith("コーヒー") }
        val kon = ko.indexOfFirst { reading(it).startsWith("こん") }
        assertTrue(ko.toString(), coffee in 0 until kon)
        assertEquals("こおひい", sortKey("コーヒー", esperanto = false))
        assertEquals('あ', Trie.prolonged('カ'))
        assertEquals(null, Trie.prolonged('ん'))
    }

    @Test
    fun aWordTypedWithItsLongVowelThatTheIndexHasWithoutIt() {
        /* "コンピューター": the index has "コンピュータ", and longer words
         * with the ー: these aren't the word typed, so without the ー */
        assertTrue(DictionarySearch.lacksTypedWord("コンピューター", listOf("コンピューターサイエンス")))
        assertFalse(DictionarySearch.lacksTypedWord("ショー", listOf("ショー", "ショーウインドー")))
        /* With its reading, in either script */
        assertFalse(DictionarySearch.lacksTypedWord("こーひー", listOf("コーヒー")))
    }

    @Test
    fun halfWidthKatakanaIsTheKatakanaOfTheIndex() {
        assertEquals("ガキ", searchFilter("ｶﾞｷ", "ja").uppercase())
        assertEquals("コーヒー", searchFilter("ｺｰﾋｰ", "ja").uppercase())
        /* A half-width mark after a full-width kana, pasted */
        assertEquals("ガキ", searchFilter("カﾞキ", "ja"))
        /* Only in Japanese */
        assertEquals("ｶ", searchFilter("ｶ", "eo"))
        /* Only the half-width kana: the index writes some words with
         * full-width letters and digits, found as typed */
        for (word in listOf("３０分", "ｘ線", "第２")) {
            assertEquals(word, searchFilter(word, "ja"))
            assertTrue(word, found(word).isNotEmpty())
        }
    }

    @Test
    fun aWholeWordIsTheWordAloneOrFollowedByOthers() {
        /* By its reading, in either script */
        assertTrue(isWholeWord("犬（いぬ）", "いぬ"))
        assertTrue(isWholeWord("イヌ", "いぬ"))
        assertTrue(isWholeWord("犬（いぬ）", "イヌ"))
        assertFalse(isWholeWord("犬の（いぬの）", "いぬ"))
        assertFalse(isWholeWord("イヌイット", "いぬ"))
        /* Other languages as before */
        assertTrue(isWholeWord("valet d'écurie", "valet"))
        assertFalse(isWholeWord("valetaille", "valet"))
        assertTrue(isWholeWord("élève", "eleve"))
        assertTrue(isWholeWord("malva", "malva"))
        assertFalse(isWholeWord("malvacoj", "malva"))
        /* A word without a reading isn't kept through an empty one */
        assertFalse(isWholeWord("hundo", "xyz"))
    }

    @Test
    fun theIndexHasTheShorterSpellingOfTheLongVowel() {
        /* What the search loop relies on, on the index itself */
        assertTrue(DictionarySearch.lacksTypedWord("コンピューター", found("コンピューター", 128)))
        assertTrue(found("コンピュータ").any { it.startsWith("コンピュータ（") })
        assertFalse(DictionarySearch.lacksTypedWord("コーヒー", found("コーヒー", 128)))
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
