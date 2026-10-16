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

import android.content.Context
import java.io.File

/** A word opened from the search: the language searched in, and the word
 * as the index of that language has it, eg. "chercher" in French. Words,
 * not article numbers, which change with every update of the dictionary.
 * For a French word that only the French Wiktionary has, the Esperanto
 * word opened: "potiron" leads to "kukurbo" and to "potirono". */
data class HistoryEntry(val language: String, val word: String, val wiktionary: String? = null)

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

    /** Puts back a history as it was, eg. to undo a removal. */
    @Synchronized
    fun set(context: Context, entries: List<HistoryEntry>) {
        file(context).writeText(encode(entries.take(MAX_ENTRIES)))
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

    /** One entry per line: the language, a tab, the word, and for a word
     * of Wiktionary, another tab and its Esperanto word. */
    internal fun encode(entries: List<HistoryEntry>): String =
        entries.joinToString("\n") { entry ->
            listOfNotNull(entry.language, entry.word, entry.wiktionary)
                .joinToString("\t") { it.replace(Regex("[\t\n\r]"), " ") }
        }

    /** Reads what [encode] writes, skipping the lines it can't read. */
    internal fun decode(text: String): List<HistoryEntry> =
        text.lineSequence().mapNotNull { line ->
            val tab = line.indexOf('\t')
            if (tab <= 0 || tab == line.lastIndex) null
            else {
                val rest = line.substring(tab + 1).split('\t')
                if (rest[0].isEmpty()) null
                else HistoryEntry(line.substring(0, tab), rest[0], rest.getOrNull(1)?.takeIf { it.isNotEmpty() })
            }
        }.toList()
}
