/*
 * Bona vortaro - an Esperanto dictionary for Android, based on PReVo
 * Copyright (C) 2016  Neil Roberts
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
import java.io.IOException

/** The indices loaded, the last five languages used */
object TrieCache {
    private const val CACHE_LENGTH = 5

    /* The most recently used first */
    private val entries = ArrayDeque<Pair<String, Trie>>()

    @JvmStatic
    @Throws(IOException::class)
    fun getTrie(context: Context, language: String): Trie {
        synchronized(entries) {
            val at = entries.indexOfFirst { it.first == language }
            val entry = if (at >= 0)
                entries.removeAt(at)
            else {
                val trie = context.assets.open("indices/index-$language.bin").use { Trie(it) }
                if (entries.size >= CACHE_LENGTH)
                    entries.removeLast()
                language to trie
            }
            entries.addFirst(entry)
            return entry.second
        }
    }
}
