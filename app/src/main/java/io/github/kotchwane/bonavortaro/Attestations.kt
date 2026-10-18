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
import android.util.Log
import java.io.IOException

/**
 * The dictionaries that Reta Vortaro names for a headword, as shown in
 * "Fontoj": "aboli — Plena Ilustrita Vortaro (1970); abolicii — Plena
 * Vortaro Esperanto-Esperanta kaj Esperanto-Franca (1909)". Written by
 * prevodb in assets/attestations.txt, a line "article\tform\tworks" each.
 */
object Attestations {
    private const val TAG = "bonaattestations"

    @Volatile private var byArticle: Map<Int, List<Pair<String, String>>>? = null

    /** The forms of an article and their dictionaries, in their order,
     * a form once. Disk access, the first time: not on the main thread. */
    @Synchronized
    fun of(context: Context, article: Int): List<Pair<String, String>> =
        (byArticle ?: try {
            parse(context.assets.open("attestations.txt").bufferedReader().readLines())
        } catch (e: IOException) {
            Log.w(TAG, "Failed to read the attestations")
            emptyMap()
        }.also { byArticle = it })[article] ?: emptyList()

    /** The same form can be named in two places of its article (its root's
     * headword and its own), with two dictionaries: they are put together */
    internal fun parse(lines: List<String>): Map<Int, List<Pair<String, String>>> =
        lines.mapNotNull { line ->
            val fields = line.split('\t')
            fields.getOrNull(0)?.toIntOrNull()?.takeIf { fields.size == 3 }?.let { Triple(it, fields[1], fields[2]) }
        }.groupBy({ it.first }) { it.second to it.third }
            .mapValues { (_, forms) ->
                forms.groupBy({ it.first }) { it.second }
                    .map { (form, works) -> form to works.flatMap { it.split("; ") }.distinct().joinToString("; ") }
            }

    /** "aboli — Plena Ilustrita Vortaro (1970); abolicii — …" */
    fun describe(forms: List<Pair<String, String>>): String =
        forms.joinToString("; ") { (form, works) -> "$form — $works" }
}
