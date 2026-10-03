/*
 * Bona vortaro - an Esperanto dictionary for Android, based on PReVo
 * Copyright (C) 2012, 2013, 2016  Neil Roberts
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

package uk.co.busydoingnothing.prevo

import android.content.Context
import android.content.Intent

/** Opening the main screens from anywhere. */
object Screens {

    /** The search, in the given language. */
    fun searchIntent(context: Context, language: String): Intent =
        Intent(context, SearchActivity::class.java)
            .putExtra(SearchActivity.EXTRA_LANGUAGE, language)

    /** The search, in the language used last. */
    fun goSearch(context: Context) {
        val language = PrevoSettings.getLastLanguage(context)
            /* The language may have disappeared in an update of the
             * dictionary data */
            ?.takeIf { LanguageList.getDefault(context).hasLanguage(it) }
            ?: "eo"

        context.startActivity(searchIntent(context, language))
    }

    fun goPreferences(context: Context) {
        context.startActivity(Intent(context, PreferenceActivity::class.java))
    }
}
