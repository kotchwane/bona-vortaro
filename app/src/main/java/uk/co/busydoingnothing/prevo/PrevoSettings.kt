/*
 * PReVo - A portable version of ReVo for Android
 * Copyright (C) 2013, 2016  Neil Roberts
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

import android.app.Application
import android.content.Context
import androidx.appcompat.app.AppCompatDelegate
import kotlin.math.pow

enum class Theme { SYSTEM, LIGHT, DARK }

/** The settings of the app, stored in the same preferences as in the
 * original app so that existing settings are kept. */
object PrevoSettings {
    private const val PREF_THEME = "theme"

    /* There are 10 text sizes, from 0 to 9, on a logarithmic scale.
     * Same values as the original zoom of the articles. */
    const val TEXT_SIZE_COUNT = 10
    const val DEFAULT_TEXT_SIZE = TEXT_SIZE_COUNT / 2
    private const val TEXT_SIZE_ROOT = 1.2f

    fun getTheme(context: Context): Theme =
        when (prefs(context).getString(PREF_THEME, null)) {
            "light" -> Theme.LIGHT
            "dark" -> Theme.DARK
            else -> Theme.SYSTEM
        }

    fun setTheme(context: Context, theme: Theme) {
        prefs(context).edit().putString(PREF_THEME, theme.name.lowercase()).apply()
        applyTheme(theme)
    }

    /** Applies the theme to all the screens, including open ones. */
    fun applyTheme(theme: Theme) {
        AppCompatDelegate.setDefaultNightMode(
            when (theme) {
                Theme.SYSTEM -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
                Theme.LIGHT -> AppCompatDelegate.MODE_NIGHT_NO
                Theme.DARK -> AppCompatDelegate.MODE_NIGHT_YES
            }
        )
    }

    fun getTextSize(context: Context): Int =
        prefs(context).getInt(MenuHelper.PREF_FONT_SIZE, DEFAULT_TEXT_SIZE)
            .coerceIn(0, TEXT_SIZE_COUNT - 1)

    fun setTextSize(context: Context, size: Int) {
        prefs(context).edit()
            .putInt(MenuHelper.PREF_FONT_SIZE, size.coerceIn(0, TEXT_SIZE_COUNT - 1))
            .apply()
    }

    /** How much to scale the text of the articles for the given size. */
    fun textScale(size: Int): Float = TEXT_SIZE_ROOT.pow(size - DEFAULT_TEXT_SIZE)

    /** The languages whose translations are shown in the articles. */
    fun getTranslationLanguages(context: Context, all: List<Language>): Set<String> {
        val selected = SelectedLanguages(context)
        return all.map { it.code }.filter { selected.contains(it) }.toSet()
    }

    fun setTranslationLanguages(context: Context, all: List<Language>, codes: Set<String>) {
        val editor = prefs(context).edit()

        /* Same encoding as the original app: no value means all of the
         * languages, an empty value means none of them */
        when {
            all.all { it.code in codes } -> editor.remove(SelectedLanguages.PREF)
            else -> editor.putString(
                SelectedLanguages.PREF,
                all.map { it.code }.filter { it in codes }.joinToString(","),
            )
        }

        editor.apply()
    }

    private fun prefs(context: Context) =
        context.getSharedPreferences(MenuHelper.PREVO_PREFERENCES, Context.MODE_PRIVATE)
}

class PrevoApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        PrevoSettings.applyTheme(PrevoSettings.getTheme(this))
    }
}
