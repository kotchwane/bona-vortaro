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
import org.junit.Test

class BonaLingvoTest {
    private fun advice(word: String, context: String = "", vararg targets: Pair<String, String>) =
        BonaLingvoEntry(word, context, targets.map { Simplification(it.first, it.second, discouraged = true, checked = true) })

    @Test
    fun aWordAdvisedAgainstWithoutContextIsSoEverywhere() {
        assertTrue(advice("akvedukto", "", "akvokondukilo" to "").discouragedEverywhere)
        /* A context that only says how to choose */
        assertTrue(advice("absciso", "(laŭ la kunteksto)", "ikso-koordinato" to "").discouragedEverywhere)
    }

    @Test
    fun anAdviceForSomeSensesOnlyIsNotMarkedEverywhere() {
        /* The context of the word: "afero" is right as "aŭ ĉio, kio okazas" */
        assertFalse(advice("afero", "(en la senco de konkreta aĵo)", "aĵo" to "").discouragedEverywhere)
        /* Every word to say has its own: "brava" is right as "kuraĝa" */
        val brava = advice(
            "brava", "",
            "obeema" to "(en la senco de obeema, trankvila)",
            "trankvila" to "(en la senco de obeema, trankvila)",
            "brile" to "(al artistoj)",
        )
        assertTrue(brava.limited)
        assertFalse(brava.discouragedEverywhere)
        /* One without a context is said everywhere */
        assertTrue(advice("x", "", "a" to "", "b" to "(en muziko)").discouragedEverywhere)
    }

    @Test
    fun theWordsToSayAreGroupedByTheirContext() {
        val brava = advice("brava", "", "obeema" to "(a)", "trankvila" to "(a)", "brile" to "(b)", "lerte" to "(b)")
        assertEquals(listOf(listOf("obeema", "trankvila") to "(a)", listOf("brile", "lerte") to "(b)"), brava.groups)
    }
}
