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
import java.text.Normalizer

/** A root or a word made official by the Fundamento (level 0) or an
 * Oficiala Aldono (level n), with where its article is. */
data class OfficialEntry(
    val level: Int,
    val isRoot: Boolean,
    val article: Int,
    val section: Int,
    /** eg. "komput/i" for a root, "komputilo" for a word */
    val text: String,
)

/** A line of the list of an addition: a root with the words that its
 * entry lists, or a word alone, whose root came from another list. */
class OfficialGroup(
    val root: OfficialEntry?,
    val words: List<OfficialEntry>,
    /** For a word alone: the root of its article, eg. "arb/o" for
     * "Nigra Arbaro" */
    val articleRoot: String?,
) {
    val title: String get() = root?.text ?: words.first().text
}

/** What each list contains, read from assets/officiality.txt, which
 * prevodb writes. */
object OfficialLists {
    private const val FILE = "officiality.txt"

    @Volatile
    private var entries: List<OfficialEntry>? = null

    /** All of the entries. Reads the file the first time: not on the
     * main thread. */
    fun get(context: Context): List<OfficialEntry> =
        entries ?: synchronized(this) {
            entries ?: context.assets.open(FILE).bufferedReader().use { parse(it.readText()) }
                .also { entries = it }
        }

    /* The rest doesn't need a phone, and is tested on its own */

    /** The lines are "level, R or W, article, section, text", separated
     * by tabs. The levels are "*" (the Fundamento) and the numbers of the
     * additions; the others (a few years in the data) are left out. */
    internal fun parse(text: String): List<OfficialEntry> =
        text.lineSequence().mapNotNull { line ->
            val fields = line.split('\t')
            if (fields.size != 5) return@mapNotNull null
            val level = if (fields[0] == "*") 0 else fields[0].toIntOrNull() ?: return@mapNotNull null
            if (level !in OFFICIAL_YEARS.indices) return@mapNotNull null
            OfficialEntry(
                level = level,
                isRoot = fields[1] == "R",
                article = fields[2].toIntOrNull() ?: return@mapNotNull null,
                section = fields[3].toIntOrNull() ?: return@mapNotNull null,
                text = fields[4],
            )
        }.toList()

    /** The list of a level: each root with the words its entry lists,
     * and the words whose root is in another list, in alphabetical
     * order. The word that only repeats a root ("arbo" for "arb/o") is
     * left out. */
    internal fun groups(entries: List<OfficialEntry>, level: Int): List<OfficialGroup> {
        val atLevel = entries.filter { it.level == level }
        val roots = atLevel.filter { it.isRoot }
        val rootOf = entries.filter { it.isRoot }.associate { it.article to it.text }
        val words = atLevel.filter { !it.isRoot }.groupBy { it.article }

        val groups = roots.map { root ->
            val forms = mainForms(root.text)
            OfficialGroup(root, words[root.article].orEmpty().filter { it.text !in forms }, null)
        } + words.filterKeys { article -> roots.none { it.article == article } }
            .flatMap { (article, list) -> list.map { OfficialGroup(null, listOf(it), rootOf[article]) } }

        return groups.sortedBy { sortKey(it.title) }
    }

    /** How many roots and words a level has, as shown in its list. */
    internal fun counts(groups: List<OfficialGroup>): Pair<Int, Int> =
        groups.count { it.root != null } to groups.sumOf { it.words.size }

    /** The words that a root writes, without its slash: "komputi" for
     * "komput/i", and each variant of "arĥaism/o, arkaism/o". */
    internal fun mainForms(root: String): Set<String> =
        root.split(",").map { it.trim().replace("/", "") }.toSet()

    /** A key to sort in the order of the Esperanto alphabet: c, ĉ, d…,
     * the letters with a circumflex or a breve after their plain letter,
     * capitals and other accents (é) ignored. */
    internal fun sortKey(text: String): String {
        val key = StringBuilder()
        for (c in text.lowercase()) {
            when (c) {
                'ĉ' -> key.append("c~")
                'ĝ' -> key.append("g~")
                'ĥ' -> key.append("h~")
                'ĵ' -> key.append("j~")
                'ŝ' -> key.append("s~")
                'ŭ' -> key.append("u~")
                '/', '-', ' ', '\'' -> {}
                else -> key.append(
                    Normalizer.normalize(c.toString(), Normalizer.Form.NFD).filter { it.isLetterOrDigit() }
                )
            }
        }
        return key.toString()
    }
}
