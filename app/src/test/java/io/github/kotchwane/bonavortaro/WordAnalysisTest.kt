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
import org.junit.Test

class WordAnalysisTest {
    /* A few entries of the dictionary, written as in its index */
    private val dictionary = setOf(
        "ulo", "-ul", "senti", "sentema", "temo", "ema", "-em", "nutri", "-iĝ", "tra-", "tra", "iri",
        "ebla", "-ebl", "mal-", "riĉa", "al", "veni", "-ig", "igi", "akvo", "botelo", "kafo", "-ej", "estro",
        "-estr", "hundo", "aĉa", "-aĉ", "aj", "kuri", "for", "-int", "eta", "ino", "-in", "kafo", "-ej",
    )
    private fun analyse(word: String) = WordAnalyser.analyse(word, { if (it in dictionary) it else null })
    private fun shown(analysis: WordAnalysis) =
        analysis.pieces.joinToString("·") { it.text } + if (analysis.complete) "" else "·…" + analysis.unrecognised
    private fun best(word: String) = shown(analyse(word).first())

    @Test
    fun aWordIsCutIntoItsRootAndAffixes() {
        assertEquals("hund·ul·o", best("hundulo"))
        assertEquals("nutr·iĝ·o", best("nutriĝo"))
        assertEquals("hund·aĉ·o", best("hundaĉo"))
        assertEquals("kaf·ej·estr·o", best("kafejestro"))
    }

    @Test
    fun eachPieceOpensItsEntryTheSuffixByItsOwnWordWhenItExplainsIt() {
        val pieces = analyse("hundulo").first().pieces
        assertEquals(listOf("hundo", "ulo", null), pieces.map { it.entry })
        /* "-ej" has no word of its own */
        assertEquals("-ej", analyse("kafejestro").first().pieces[1].entry)
    }

    @Test
    fun prefixesAndPrepositionsComeBeforeTheRoot() {
        assertEquals("tra·ir·ebl·a", best("trairebla"))
        assertEquals("mal·riĉ·iĝ·o", best("malriĉiĝo"))
        assertEquals("al·ven·ig·i", best("alvenigi"))
        assertEquals("for·kur·int·o", best("forkurinto"))
    }

    @Test
    fun aDerivedWordOfTheDictionaryCountsAsOnePiece() {
        assertEquals("sentem·ul·o", best("sentemulo"))
    }

    @Test
    fun theRootsOfACompoundAreJoinedByTheirO() {
        assertEquals("akv·o·botel·o", best("akvobotelo"))
    }

    @Test
    fun aVerbEndingAndTheGrammarAreKept() {
        assertEquals("sentem·u", best("sentemu"))
        assertEquals("hund·aĉ·o·j·n", best("hundaĉojn"))
    }

    @Test
    fun aWordBeingTypedIsCutAsFarAsItGoes() {
        assertEquals("hund·ul", best("hundul"))
    }

    @Test
    fun theGrammarAtTheEndIsEsperantos() {
        /* A suffix, not an ending followed by -n or -j */
        assertEquals("hund·in", best("hundin"))
        assertEquals("kaf·ej", best("kafej"))
        /* No -n after a verb ending, no -j after -e */
        assertTrue(WordAnalyser.analyse("hundasn", { if (it in dictionary) it else null }, needsEnding = true).isEmpty())
        assertTrue(WordAnalyser.analyse("kafejej", { if (it in dictionary) it else null }, needsEnding = true).isEmpty())
    }

    @Test
    fun aFormNotInTheDictionaryIsLookedUpOnce() {
        val asked = mutableListOf<String>()
        WordAnalyser.analyse("hundbrzktnmlpo", { asked.add(it); if (it in dictionary) it else null })
        assertEquals(asked.size, asked.toSet().size)
    }

    @Test
    fun whatCannotBeCutIsLeftAtTheEnd() {
        /* "aj" is only an interjection: not a root */
        assertEquals("hund·ul·et·…ajo", best("hunduletajo"))
    }

    @Test
    fun aWordWithLettersOutsideEsperantoOrMostlyUnknownIsNotGuessed() {
        assertTrue(analyse("hundylo").isEmpty())
        /* "hund" recognised, but not most of the word */
        assertTrue(analyse("hundbrzktnmlpo").isEmpty())
    }

    @Test
    fun aSecondCutIsAnotherOneNotTheSameMoreFinely() {
        /* kaf·ej·estr·o and kafej·estr·o are one reading */
        val withKafejo = WordAnalyser.analyse("kafejestro", { if (it in dictionary + "kafejo") it else null })
        assertEquals(listOf("kafej·estr·o"), withKafejo.map { shown(it) })
    }

    @Test
    fun nothingIsGuessedForShortOrStrangeWords() {
        assertTrue(analyse("hun").isEmpty())
        assertTrue(analyse("hund-ulo").isEmpty())
        assertTrue(analyse("xyzwq").isEmpty())
    }
}
