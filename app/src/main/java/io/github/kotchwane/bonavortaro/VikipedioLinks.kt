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

/**
 * The article of Vikipedio about a word, where Reta Vortaro links to one:
 * "Hundo" for hundo, "Granda_Hundo" for the constellation. Written by
 * prevodb in assets/vikipedio.txt, a line "article\tsection\ttitle" each,
 * from Reta Vortaro's list (data/vikiref.json).
 */
object VikipedioLinks {
    private const val TAG = "bonavikipedio"
    private const val SITE = "https://eo.wikipedia.org/wiki/"

    @Volatile private var titles: Map<Long, String>? = null

    /** The page of Vikipedio about the word of a section, if Reta Vortaro
     * has one. Disk access, the first time: not on the main thread. */
    @Synchronized
    fun page(context: Context, article: Int, section: Int): Uri? =
        (titles ?: try {
            parse(context.assets.open("vikipedio.txt").bufferedReader().readLines())
        } catch (e: IOException) {
            Log.w(TAG, "Failed to read the Vikipedio links")
            emptyMap()
        }.also { titles = it })[key(article, section)]
            ?.let { Uri.parse(SITE + Uri.encode(it, "_()',")) }

    internal fun key(article: Int, section: Int) = (article.toLong() shl 32) or section.toLong()

    /** Some titles come written for an address ("Ko%C4%89ero"), others as
     * they read ("Ŝafo"): all as they read */
    internal fun parse(lines: List<String>): Map<Long, String> =
        lines.mapNotNull { line ->
            val fields = line.split('\t')
            val article = fields.getOrNull(0)?.toIntOrNull()
            val section = fields.getOrNull(1)?.toIntOrNull()
            if (article == null || section == null || fields.size != 3 || fields[2].isEmpty()) null
            else key(article, section) to percentDecoded(fields[2])
        }.toMap()

    /* "%C4%89" as "ĉ"; a "+" stays a "+" ("C++") */
    private fun percentDecoded(text: String): String {
        if ('%' !in text)
            return text
        val bytes = java.io.ByteArrayOutputStream()
        var i = 0
        while (i < text.length) {
            val hex = if (text[i] == '%' && i + 3 <= text.length) text.substring(i + 1, i + 3).toIntOrNull(16) else null
            if (hex != null) {
                bytes.write(hex)
                i += 3
            } else {
                val char = text.codePointAt(i)
                bytes.write(String(Character.toChars(char)).toByteArray(Charsets.UTF_8))
                i += Character.charCount(char)
            }
        }
        return bytes.toString(Charsets.UTF_8.name())
    }
}
