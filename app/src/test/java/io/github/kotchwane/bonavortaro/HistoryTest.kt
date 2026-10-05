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

class HistoryTest {
    private val chercher = HistoryEntry("fr", "chercher")
    private val arbaro = HistoryEntry("eo", "arbaro")
    private val konfuzo = HistoryEntry("eo", "konfuzo")

    @Test
    fun aNewWordComesFirst() {
        assertEquals(listOf(konfuzo, chercher, arbaro), History.added(listOf(chercher, arbaro), konfuzo))
    }

    @Test
    fun aWordOpenedAgainMovesToTheTopInsteadOfAppearingTwice() {
        assertEquals(listOf(arbaro, konfuzo, chercher), History.added(listOf(konfuzo, chercher, arbaro), arbaro))
    }

    @Test
    fun theSameWordInAnotherLanguageIsAnotherEntry() {
        val bonaEo = HistoryEntry("eo", "bona")
        val bonaPt = HistoryEntry("pt", "bona")
        assertEquals(listOf(bonaPt, bonaEo), History.added(listOf(bonaEo), bonaPt))
    }

    @Test
    fun theOldestWordsAreLeftOutBeyondTheMaximum() {
        assertEquals(listOf(konfuzo, chercher), History.added(listOf(chercher, arbaro), konfuzo, max = 2))
    }

    @Test
    fun theEntriesAreReadBackAsTheyWereWritten() {
        val entries = listOf(chercher, HistoryEntry("eo", "*-malforta topologio"), HistoryEntry("fr", "régional de l'étape"))
        assertEquals(entries, History.decode(History.encode(entries)))
    }

    @Test
    fun theLinesThatCantBeReadAreSkipped() {
        assertEquals(listOf(arbaro), History.decode("garbage\n\tno language\nfr\t\neo\tarbaro\n"))
    }

    @Test
    fun anEmptyFileIsAnEmptyHistory() {
        assertEquals(emptyList<HistoryEntry>(), History.decode(""))
    }
}
