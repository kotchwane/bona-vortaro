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
import org.junit.Test

class VikipedioLinksTest {
    @Test
    fun theTitlesReadAsTheyAreWritten() {
        val titles = VikipedioLinks.parse(
            listOf(
                "4898\t0\tHundo",
                "4898\t16\tGranda_Hundo",
                /* Written for an address, or not */
                "6000\t1\tKo%C4%89ero_(konstelacio)",
                "6001\t0\tŜafo_(konstelacio)",
                "6002\t0\tC++",
                "7\t0\t",
                "broken",
            )
        )

        assertEquals("Hundo", titles[VikipedioLinks.key(4898, 0)])
        assertEquals("Granda_Hundo", titles[VikipedioLinks.key(4898, 16)])
        assertEquals("Koĉero_(konstelacio)", titles[VikipedioLinks.key(6000, 1)])
        assertEquals("Ŝafo_(konstelacio)", titles[VikipedioLinks.key(6001, 0)])
        assertEquals("C++", titles[VikipedioLinks.key(6002, 0)])
        assertEquals(5, titles.size)
    }
}
