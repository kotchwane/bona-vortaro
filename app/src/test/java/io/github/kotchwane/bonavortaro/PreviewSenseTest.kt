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
import org.junit.Assert.assertNull
import org.junit.Test

class PreviewSenseTest {
    /* domo's French: "domo 1: maison (construction), bâtiment; domo 2:
     * maison (lignée), lignée; domo 3: …"; domaĉo, section 4, for the
     * whole word */
    private val domo = listOf(
        Triple(0, "1", "maison (construction), bâtiment"),
        Triple(0, "2", "maison (lignée), lignée"),
        Triple(0, "3", "foyer, chez-soi"),
        Triple(4, "", "masure"),
    )

    @Test
    fun aTranslationOfOneSenseGivesIt() {
        assertEquals(0 to "2", uniqueSense(domo, "maison (lignée)"))
        assertEquals(0 to "2", uniqueSense(domo, "Lignée"))
        assertEquals(0 to "1", uniqueSense(domo, "bâtiment"))
    }

    @Test
    fun anotherWordOfTheArticleWithTheSameTranslationDoesntHideTheSense() {
        /* kaf/o: kafujo 3 and kafskatolo are both "boîte à café" */
        val kafo = listOf(Triple(3, "1", "caféier"), Triple(3, "3", "boîte à café"), Triple(5, "", "boîte à café"))
        assertEquals(3 to "3", uniqueSense(kafo, "boîte à café", mark = 3))
        assertNull(uniqueSense(kafo, "boîte à café", mark = 5))
        /* Not the sense of another word, for a word that doesn't have it */
        assertNull(uniqueSense(kafo, "boîte à café", mark = 7))
        assertNull(uniqueSense(kafo, "boîte à café"))
    }

    @Test
    fun aTranslationOfTheWholeWordOrOfSeveralSensesGivesNone() {
        assertNull(uniqueSense(domo, "masure"))
        assertNull(uniqueSense(domo + Triple(0, "3", "maison (construction)"), "maison (construction)"))
        /* Only the whole words: not "maison" in "maison (lignée)" */
        assertNull(uniqueSense(domo, "maison"))
    }

    @Test
    fun thePreviewStartsAtTheSense() {
        val paragraphs = listOf("1. Tuta konstruaĵo", "2. Nobela familianaro", "3. Hejmo")
        assertEquals(listOf("2. Nobela familianaro", "3. Hejmo"), fromSense(paragraphs, "2"))
        assertEquals(paragraphs, fromSense(paragraphs, "7"))
        assertEquals(paragraphs, fromSense(paragraphs, null))
    }

    @Test
    fun aLinkFindsItsSenseInTheText() {
        val text = "1. Ĉe la loko.\n\n2. Disde.\n\n3. Kaŭzon.\n\n4. Aganton."
        assertEquals(text.indexOf("4."), senseOffset(text, 4))
        assertEquals(0, senseOffset(text, 1))
        /* Not "14." nor a number within a line */
        assertNull(senseOffset("13. a\n\n14. b, en 4. jarcento", 4))
        assertNull(senseOffset(text, 7))
    }
}
