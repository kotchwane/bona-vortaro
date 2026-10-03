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

package uk.co.busydoingnothing.prevo

import android.content.Context
import java.io.File

/** A word opened from the search: the language searched in, and the word
 * as the index of that language has it, eg. "chercher" in French. Words,
 * not article numbers, which change with every update of the dictionary. */
data class HistoryEntry(val language: String, val word: String)

/** The history of the searches: the words whose article was opened from
 * the search, the most recent first, each one once. It is kept in a
 * private file of the app, which the backups to the cloud leave out: it
 * doesn't leave the phone. */
object History {
    const val MAX_ENTRIES = 300

    /* Also named in res/xml/backup_rules.xml and data_extraction_rules.xml */
    private const val FILE = "history.txt"

    @Synchronized
    fun get(context: Context): List<HistoryEntry> {
        val file = file(context)
        return if (file.exists()) decode(file.readText()) else emptyList()
    }

    @Synchronized
    fun add(context: Context, entry: HistoryEntry) {
        file(context).writeText(encode(added(get(context), entry)))
    }

    @Synchronized
    fun remove(context: Context, entry: HistoryEntry) {
        file(context).writeText(encode(get(context) - entry))
    }

    @Synchronized
    fun clear(context: Context) {
        file(context).delete()
    }

    private fun file(context: Context) = File(context.filesDir, FILE)

    /* The rest doesn't need a phone, and is tested on its own */

    /** The entries with a new one first, without its earlier occurrence,
     * and the oldest ones left out beyond [max]. */
    internal fun added(entries: List<HistoryEntry>, entry: HistoryEntry, max: Int = MAX_ENTRIES) =
        (listOf(entry) + entries.filter { it != entry }).take(max)

    /** One entry per line: the language, a tab, the word. */
    internal fun encode(entries: List<HistoryEntry>): String =
        entries.joinToString("\n") { "${it.language}\t${it.word.replace(Regex("[\t\n]"), " ")}" }

    /** Reads what [encode] writes, skipping the lines it can't read. */
    internal fun decode(text: String): List<HistoryEntry> =
        text.lineSequence().mapNotNull { line ->
            val tab = line.indexOf('\t')
            if (tab <= 0 || tab == line.lastIndex) null
            else HistoryEntry(line.substring(0, tab), line.substring(tab + 1))
        }.toList()
}
