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

/** A word found in an index, the article and section it leads to;
 * example: whether it is an example of the word of its section, or its
 * translation, "saluton!", "bonjour" */
class SearchResult @JvmOverloads constructor(
    @JvmField val word: String,
    @JvmField val article: Int,
    @JvmField val mark: Int,
    @JvmField val example: Boolean = false,
) {
    val isExample: Boolean
        get() = example

    override fun toString(): String = word

    companion object {
        /** A result as the index has it: an example's entry is marked by a
         * first character U+0001, which prevodb adds to its display name */
        @JvmStatic
        fun fromIndex(word: String, article: Int, mark: Int): SearchResult =
            if (word.isNotEmpty() && word[0] == '\u0001')
                SearchResult(word.substring(1), article, mark, true)
            else
                SearchResult(word, article, mark, false)
    }
}
