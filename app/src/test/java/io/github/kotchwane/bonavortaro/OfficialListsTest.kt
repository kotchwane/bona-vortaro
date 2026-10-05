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

class OfficialListsTest {
    private val data = """
        *	R	767	0	arb/o
        *	W	767	1	arbo
        10	W	767	10	Nigra Arbaro
        9	R	6379	0	komput/i
        9	W	6379	0	komputi
        9	W	6379	3	komputoscienco
        9	W	6379	4	komputilo
        *	R	801	0	arĥaism/o, arkaism/o
        7	R	801	0	arĥaism/o, arkaism/o
        1923	R	5	0	a year in the data
        broken line
    """.trimIndent()

    private val entries = OfficialLists.parse(data)

    @Test
    fun theLinesAreRead() {
        assertEquals(9, entries.size)
        assertEquals(OfficialEntry(9, false, 6379, 4, "komputilo"), entries[6])
    }

    @Test
    fun aRootListsTheWordsOfItsEntryButNotTheOneThatRepeatsIt() {
        val groups = OfficialLists.groups(entries, 9)
        assertEquals(1, groups.size)
        assertEquals("komput/i", groups[0].root?.text)
        assertEquals(listOf("komputoscienco", "komputilo"), groups[0].words.map { it.text })
    }

    @Test
    fun aWordWhoseRootIsInAnotherListComesAloneWithItsRoot() {
        val groups = OfficialLists.groups(entries, 10)
        assertNull(groups[0].root)
        assertEquals("Nigra Arbaro", groups[0].title)
        assertEquals("arb/o", groups[0].articleRoot)
    }

    @Test
    fun theVariantsOfARootAreAllMainForms() {
        assertEquals(setOf("arĥaismo", "arkaismo"), OfficialLists.mainForms("arĥaism/o, arkaism/o"))
        assertEquals(listOf<String>(), OfficialLists.groups(entries, 0).first { it.root?.article == 767 }.words.map { it.text })
    }

    @Test
    fun theCountsAreThoseShown() {
        assertEquals(1 to 2, OfficialLists.counts(OfficialLists.groups(entries, 9)))
        assertEquals(2 to 0, OfficialLists.counts(OfficialLists.groups(entries, 0)))
    }

    @Test
    fun theOrderIsTheOneOfTheEsperantoAlphabet() {
        val words = listOf("ĉevalo", "dento", "cindro", "Celo", "elefanto", "ĝardeno", "gaso", "zebro", "ŭato", "uzi")
        assertEquals(
            listOf("Celo", "cindro", "ĉevalo", "dento", "elefanto", "gaso", "ĝardeno", "uzi", "ŭato", "zebro"),
            words.sortedBy { OfficialLists.sortKey(it) },
        )
    }
}
