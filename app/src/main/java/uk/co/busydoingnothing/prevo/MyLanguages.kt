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

    /** Adds the language at the end of the list, or removes it. The first
     * time a language is added, Esperanto is added before it, so that it
     * doesn't silently disappear from the search languages. */
    fun toggle(context: Context, language: String): List<String> {
        val current = get(context)
        val updated = when {
            language in current -> current - language
            current.isEmpty() && language != "eo" -> listOf("eo", language)
            else -> current + language
        }

        set(context, updated)
        return updated
    }

    private fun prefs(context: Context) =
        context.getSharedPreferences(MenuHelper.PREVO_PREFERENCES, Context.MODE_PRIVATE)
}
