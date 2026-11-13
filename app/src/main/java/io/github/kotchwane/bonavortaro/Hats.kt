/*
 * Bona vortaro - an Esperanto dictionary for Android, based on PReVo
 * Copyright (C) 2012  Neil Roberts
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

object Hats {
    private val HATTED = mapOf(
        'c' to 'ĉ', 'C' to 'Ĉ', 'g' to 'ĝ', 'G' to 'Ĝ', 'h' to 'ĥ', 'H' to 'Ĥ',
        'j' to 'ĵ', 'J' to 'Ĵ', 's' to 'ŝ', 'S' to 'Ŝ', 'u' to 'ŭ', 'U' to 'Ŭ',
    )

    /** The letters written in the x-system as the letters with hats:
     * "cxevalo" is "ĉevalo" */
    @JvmStatic
    fun removeHats(string: CharSequence): String {
        val result = StringBuilder()
        var i = 0

        while (i < string.length) {
            if (i + 1 < string.length && (string[i + 1] == 'x' || string[i + 1] == 'X')) {
                val hatted = HATTED[string[i]]
                if (hatted != null)
                    result.append(hatted)
                else
                    result.append(string, i, i + 2)
                i += 2
            } else {
                result.append(string[i])
                i++
            }
        }

        return result.toString()
    }
}
