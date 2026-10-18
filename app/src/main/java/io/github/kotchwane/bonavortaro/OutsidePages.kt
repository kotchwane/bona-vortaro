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

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.text.Spanned
import android.text.SpannableStringBuilder
import android.util.Log

/* The pages outside the app (Vikipedio, Reta Vortaro, the works of the
 * examples), opened in the browser, and the sign "↗" that announces them */

/** The sign after what opens a page outside the app */
const val OUTSIDE_SIGN = "↗"

/** Opens a page in the browser, if there is one. */
fun openOutside(context: Context, page: Uri) {
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, page))
    } catch (e: ActivityNotFoundException) {
        Log.w("bonaoutside", "No browser for $page")
    }
}

/** For TalkBack, a text with "↗" read as words: "Legi en Vikipedio,
 * ekstera ligilo" rather than "north east arrow". */
fun spokenOutside(text: String, outsideLink: String): String =
    text.replace(" " + OUTSIDE_SIGN, ", $outsideLink").replace(OUTSIDE_SIGN, outsideLink)

/** Marks the sign "↗" written after a source, so that copies leave it out */
class OutsideSignSpan

/** The text of an article as copied or shared: without the signs "↗",
 * which only mean something on the screen. */
fun withoutOutsideSigns(text: CharSequence): CharSequence = removing(text, OutsideSignSpan::class.java, withSpaceBefore = false)

/** The text of an article without the sources of its examples, for a
 * flashcard, which is for learning the word. */
fun withoutSources(text: CharSequence): CharSequence =
    removing(withoutOutsideSigns(text), ExampleSource::class.java, withSpaceBefore = true)

private fun removing(text: CharSequence, kind: Class<*>, withSpaceBefore: Boolean): CharSequence {
    val spanned = text as? Spanned ?: return text
    val ranges = spanned.getSpans(0, spanned.length, kind)
        .map { spanned.getSpanStart(it) to spanned.getSpanEnd(it) }
        .distinct()
        .sortedByDescending { it.first }
    if (ranges.isEmpty())
        return text
    val result = SpannableStringBuilder(spanned)
    for ((start, end) in ranges) {
        /* The no-break space of the sign, or the space before a source */
        val from = if (start > 0 && (withSpaceBefore && result[start - 1] == ' ' || result[start - 1] == ' ')) start - 1 else start
        result.delete(from, end)
    }
    return result
}
