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

class AttestationsTest {
    @Test
    fun theFormsKeepTheirOrderAndAFormIsNamedOnce() {
        val parsed = Attestations.parse(
            listOf(
                "24\taboli\tPlena Ilustrita Vortaro (1970)",
                "24\tabolicii\tPlena Vortaro Esperanto-Esperanta kaj Esperanto-Franca (1909)",
                /* The root's headword, then the word's own */
                "10777\tsceno\tWörterbuch Esperanto-Deutsch (1905)",
                "10777\tsceno\tFundamento de Esperanto (1905); Wörterbuch Esperanto-Deutsch (1905)",
                "not a line",
            )
        )

        assertEquals(
            listOf(
                "aboli" to "Plena Ilustrita Vortaro (1970)",
                "abolicii" to "Plena Vortaro Esperanto-Esperanta kaj Esperanto-Franca (1909)",
            ),
            parsed.getValue(24),
        )
        assertEquals(
            listOf("sceno" to "Wörterbuch Esperanto-Deutsch (1905); Fundamento de Esperanto (1905)"),
            parsed.getValue(10777),
        )
        assertEquals(setOf(24, 10777), parsed.keys)
    }
}
