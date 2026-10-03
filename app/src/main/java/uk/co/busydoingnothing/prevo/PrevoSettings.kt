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
import androidx.core.os.ConfigurationCompat
import androidx.appcompat.app.AppCompatDelegate
import kotlin.math.pow

enum class Theme { SYSTEM, LIGHT, DARK }

/** The settings of the app, stored in the same preferences as in the
 * original app so that existing settings are kept. */
object PrevoSettings {
    private const val PREF_THEME = "theme"
    private const val PREF_UNFOLD_EXAMPLES = "unfoldExamples"

    /* The text sizes are numbered as in the original zoom of the articles,
     * from 0 to 9, each one 1.2 times bigger than the previous one, the
     * default being 5. The two smallest ones (40 % and 48 % of the normal
     * size) were too small to be useful, so the smallest is now 2 (58 %).
     * A stored 0 or 1 is read as 2. */
    const val MIN_TEXT_SIZE = 2
    const val MAX_TEXT_SIZE = 9
    const val DEFAULT_TEXT_SIZE = 5
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
            .coerceIn(MIN_TEXT_SIZE, MAX_TEXT_SIZE)

    fun setTextSize(context: Context, size: Int) {
        prefs(context).edit()
            .putInt(MenuHelper.PREF_FONT_SIZE, size.coerceIn(MIN_TEXT_SIZE, MAX_TEXT_SIZE))
            .apply()
    }

    /** Whether the examples of the articles are shown unfolded. */
    fun getUnfoldExamples(context: Context): Boolean =
        prefs(context).getBoolean(PREF_UNFOLD_EXAMPLES, false)

    fun setUnfoldExamples(context: Context, unfold: Boolean) {
        prefs(context).edit().putBoolean(PREF_UNFOLD_EXAMPLES, unfold).apply()
    }

    /** How much to scale the text of the articles for the given size. */
    fun textScale(size: Int): Float = TEXT_SIZE_ROOT.pow(size - DEFAULT_TEXT_SIZE)

    /** The languages whose translations are shown in the articles, in
     * the order of the articles. */
    fun getTranslationLanguages(context: Context, all: List<Language>): List<String> {
        val stored = prefs(context).getString(SelectedLanguages.PREF, null)
            ?: return defaultTranslationOrder(context, all)
        val known = all.map { it.code }.toSet()

        return stored.split(',').filter { it in known }.distinct()
    }

    fun setTranslationLanguages(context: Context, all: List<Language>, codes: List<String>) {
        val editor = prefs(context).edit()

        /* Same encoding as the original app: no value means all of the
         * languages, an empty value means none of them. The value keeps
         * the order chosen by the user, which the original app ignores. */
        if (codes == defaultTranslationOrder(context, all))
            editor.remove(SelectedLanguages.PREF)
        else
            editor.putString(SelectedLanguages.PREF, codes.joinToString(","))

        editor.apply()
    }

    /** The order of the translations in the articles: the one chosen by
     * the user, or else the languages they likely read first. */
    fun getTranslationOrder(context: Context): List<String> =
        prefs(context).getString(SelectedLanguages.PREF, null)?.split(',')
            ?: likelyLanguages(context)

    /* The languages the user likely reads: their search languages, even
     * the automatic ones, so that the language chosen on the first screen
     * comes first, then the languages of the phone */
    private fun likelyLanguages(context: Context): List<String> {
        val locales = ConfigurationCompat.getLocales(context.resources.configuration)
        val phone = (0 until locales.size()).mapNotNull { locales[it]?.language }

        return (MyLanguages.searchLanguages(context) + phone).distinct()
    }

    /* All of the languages, the ones the user likely reads first */
    private fun defaultTranslationOrder(context: Context, all: List<Language>): List<String> {
        val codes = all.map { it.code }
        val mine = likelyLanguages(context).filter { it in codes }

        return mine + (codes - mine.toSet())
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
