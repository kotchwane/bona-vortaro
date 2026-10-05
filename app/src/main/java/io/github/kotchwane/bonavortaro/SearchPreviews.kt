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
import android.util.LruCache
import java.io.IOException

/** The previews of the definitions in the list of search results. They
 * are read from the articles when a result is shown, so this does disk
 * access and shouldn't run on the main thread. */
object SearchPreviews {
    private const val TAG = "bonapreview"

    /* Neighbouring results often come from the same article, and the
     * same results come back while typing */
    private val articles = LruCache<Int, Article>(64)
    private val previews = LruCache<Long, WordPreview>(512)

    fun get(context: Context, result: SearchResult): WordPreview? {
        val key = (result.article.toLong() shl 32) or result.mark.toLong()
        previews.get(key)?.let { return it }

        val article = articles.get(result.article) ?: try {
            ArticleLoader.load(context, result.article, onShowSection = {}, quoteColor = 0)
                .also { articles.put(result.article, it) }
        } catch (e: IOException) {
            Log.w(TAG, "Failed to load article ${result.article}")
            return null
        }

        return previewOf(article, result.mark)?.also { previews.put(key, it) }
    }
}
