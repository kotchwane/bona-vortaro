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
import android.net.Uri
import android.util.Log
import java.io.IOException
import java.util.Locale

/**
 * The files of Reta Vortaro that the articles come from, one per article,
 * in their order: "korvet" for "korvet/o". Its site names its pages after
 * them, and the articles are in their alphabetical order, the x-system's
 * ("cxeval" for "ĉeval/o"): a word that Reta Vortaro doesn't have has a
 * place among them. Written by prevodb in assets/articles/files.txt.
 */
object RevoFiles {
    private const val TAG = "bonarevofiles"
    private const val SITE = "https://reta-vortaro.de/revo/art/"

    @Volatile private var names: List<String>? = null

    /** Disk access, the first time: not on the main thread. */
    @Synchronized
    private fun names(context: Context): List<String> = names ?: try {
        context.assets.open("articles/files.txt").bufferedReader().readLines()
    } catch (e: IOException) {
        Log.w(TAG, "Failed to read the files of the articles")
        emptyList()
    }.also { names = it }

    /** The file of Reta Vortaro an article comes from, "salut", if known:
     * the same after an update, unlike its number. Disk access, the first
     * time. */
    fun fileOf(context: Context, article: Int): String? = names(context).getOrNull(article)

    /** The page of an article on Reta Vortaro's site, if known. Disk
     * access, the first time. */
    fun page(context: Context, article: Int): Uri? =
        names(context).getOrNull(article)?.let { Uri.parse("$SITE$it.html") }

    /** The number of the first article after the place of a word, eg.
     * "potirono" between "potir…" and what follows: the articles before and
     * after it are its neighbours. Disk access, the first time. */
    fun placeOf(context: Context, word: String): Int = placeAmong(names(context), word)

    /* The rest doesn't need a phone, and is tested on its own */

    /** Where a word goes among the names of the files, by its root: in the
     * x-system, without its ending ("potirono" as "potiron", "kukurbojn"
     * as "kukurb"), but two letters at least. */
    internal fun placeAmong(names: List<String>, word: String): Int {
        val root = xSystem(word.lowercase(Locale.ROOT)).let { written ->
            ENDING.find(written)?.takeIf { it.range.first >= 2 }?.let { written.substring(0, it.range.first) } ?: written
        }
        val found = names.binarySearch(root)
        return if (found >= 0) found + 1 else -found - 1
    }

    /* The endings of Esperanto: of words, plural and accusative; of verbs */
    private val ENDING = Regex("(?:[oa]j?n?|en?|i|as|is|os|us|u)$")

    private fun xSystem(word: String) = buildString {
        for (letter in word)
            append(
                when (letter) {
                    'ĉ' -> "cx"; 'ĝ' -> "gx"; 'ĥ' -> "hx"; 'ĵ' -> "jx"; 'ŝ' -> "sx"; 'ŭ' -> "ux"
                    else -> letter.toString()
                }
            )
    }
}
