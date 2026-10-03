/*
 * PReVo - A portable version of ReVo for Android
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

/** The languages the user chose to search in, in their order. When the
 * list is empty, the search languages are chosen automatically from the
 * usage counts, as in the original app. */
object MyLanguages {
    private const val PREF = "myLanguages"

    fun get(context: Context): List<String> {
        val value = prefs(context).getString(PREF, null) ?: return emptyList()
        val languageList = LanguageList.getDefault(context)

        /* Skip languages that have disappeared in an update of the
         * dictionary data */
        return value.split(',').filter { it.isNotEmpty() && languageList.hasLanguage(it) }
    }

    fun set(context: Context, languages: List<String>) {
        prefs(context).edit().putString(PREF, languages.joinToString(",")).apply()
    }

    /** The search languages: the user's own, and only those. Esperanto
     * if there are none yet, which only happens before the first screen
     * has been gone through. */
    fun searchLanguages(context: Context): List<String> = get(context).ifEmpty { listOf("eo") }

    private fun prefs(context: Context) =
        context.getSharedPreferences(MenuHelper.PREVO_PREFERENCES, Context.MODE_PRIVATE)
}
