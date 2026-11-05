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

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FrequencyTest {
    @Test
    fun aWordLooksLikeTheWordItTranslates() {
        assertTrue(Frequency.looksLike("agréable", "agrabla"))
        assertTrue(Frequency.looksLike("Moldavie", "Moldavio"))
        assertTrue(Frequency.looksLike("cathédrale", "katedralo"))
        assertTrue(Frequency.looksLike("conduire (phys.)", "kondukti"))
        assertFalse(Frequency.looksLike("agréable", "dolĉa"))
        assertFalse(Frequency.looksLike("deviner", "duonkompreni"))
        assertFalse(Frequency.looksLike("sommet", "supro"))
    }
}
