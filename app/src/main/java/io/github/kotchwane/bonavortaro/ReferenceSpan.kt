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

import android.content.Intent
import android.text.style.ClickableSpan
import android.util.Log
import android.view.View

/** A link to a section of an article; senseNumber is the sense it points
 * to, from 1, or -1 for the word: "De⁴" */
class ReferenceSpan @JvmOverloads constructor(
    val articleNumber: Int,
    val sectionNumber: Int,
    private val senseNumber: Int = -1,
) : ClickableSpan() {
    override fun onClick(view: View) {
        Log.i(TAG, "Jumping to article ($articleNumber,$sectionNumber)")

        val intent = Intent(view.context, ArticleActivity::class.java)
        intent.putExtra(ArticleActivity.EXTRA_ARTICLE_NUMBER, articleNumber)
        intent.putExtra(ArticleActivity.EXTRA_MARK_NUMBER, sectionNumber)
        if (senseNumber > 0)
            intent.putExtra(ArticleActivity.EXTRA_SENSE_NUMBER, senseNumber)
        view.context.startActivity(intent)
    }

    private companion object {
        const val TAG = "BonaReference"
    }
}
