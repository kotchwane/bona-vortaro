/*
 * Bona vortaro - an Esperanto dictionary for Android, based on PReVo
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

/** All of the settings of the app, in one place. The keys are those of
 * the original app (PReVo). */
object BonaSettings {
    private const val FILE = "BonaVortaro"
    private const val PREF_THEME = "theme"
    private const val PREF_TEXT_SIZE = "fontSize"
    private const val PREF_UNFOLD_EXAMPLES = "unfoldExamples"
    /* No value means all of the languages, an empty value none of them */
    private const val PREF_TRANSLATION_LANGUAGES = "selectedLanguages"
    private const val PREF_SEARCH_LANGUAGES = "myLanguages"
    private const val PREF_LAST_LANGUAGE = "lastLanguage"

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
        prefs(context).getInt(PREF_TEXT_SIZE, DEFAULT_TEXT_SIZE)
            .coerceIn(MIN_TEXT_SIZE, MAX_TEXT_SIZE)

    fun setTextSize(context: Context, size: Int) {
        prefs(context).edit()
            .putInt(PREF_TEXT_SIZE, size.coerceIn(MIN_TEXT_SIZE, MAX_TEXT_SIZE))
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
        val stored = prefs(context).getString(PREF_TRANSLATION_LANGUAGES, null)
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
            editor.remove(PREF_TRANSLATION_LANGUAGES)
        else
            editor.putString(PREF_TRANSLATION_LANGUAGES, codes.joinToString(","))

        editor.apply()
    }

    /** The order of the translations in the articles: the one chosen by
     * the user, or else the languages they likely read first. */
    fun getTranslationOrder(context: Context): List<String> =
        prefs(context).getString(PREF_TRANSLATION_LANGUAGES, null)?.split(',')
            ?: likelyLanguages(context)

    /** Whether the articles show the translations in a language, read
     * quickly while an article is loaded. Esperanto is always shown. */
    fun translationFilter(context: Context): (String) -> Boolean {
        val stored = prefs(context).getString(PREF_TRANSLATION_LANGUAGES, null)
            ?: return { true }
        val codes = stored.split(',').filter { it.isNotEmpty() }.toSet()

        return { it == "eo" || it in codes }
    }

    /** The languages the user chose to search in, in their order, or an
     * empty list before they have gone through the first screen. */
    fun getChosenSearchLanguages(context: Context): List<String> {
        val value = prefs(context).getString(PREF_SEARCH_LANGUAGES, null) ?: return emptyList()
        val languageList = LanguageList.getDefault(context)

        /* Skip languages that have disappeared in an update of the
         * dictionary data */
        return value.split(',').filter { it.isNotEmpty() && languageList.hasLanguage(it) }
    }

    /** The search languages: the user's, and only those; Esperanto before
     * the first screen has been gone through. */
    fun getSearchLanguages(context: Context): List<String> =
        getChosenSearchLanguages(context).ifEmpty { listOf("eo") }

    fun setSearchLanguages(context: Context, languages: List<String>) {
        prefs(context).edit().putString(PREF_SEARCH_LANGUAGES, languages.joinToString(",")).apply()
    }

    /** The search language used last, reopened at the next start. */
    fun getLastLanguage(context: Context): String? =
        prefs(context).getString(PREF_LAST_LANGUAGE, null)

    fun setLastLanguage(context: Context, language: String) {
        prefs(context).edit().putString(PREF_LAST_LANGUAGE, language).apply()
    }

    /* The languages the user likely reads: their search languages, even
     * the automatic ones, so that the language chosen on the first screen
     * comes first, then the languages of the phone */
    private fun likelyLanguages(context: Context): List<String> {
        val locales = ConfigurationCompat.getLocales(context.resources.configuration)
        val phone = (0 until locales.size()).mapNotNull { locales[it]?.language }

        return (getSearchLanguages(context) + phone).distinct()
    }

    /* All of the languages, the ones the user likely reads first */
    private fun defaultTranslationOrder(context: Context, all: List<Language>): List<String> {
        val codes = all.map { it.code }
        val mine = likelyLanguages(context).filter { it in codes }

        return mine + (codes - mine.toSet())
    }

    private fun prefs(context: Context) =
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
}

class BonaApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        BonaSettings.applyTheme(BonaSettings.getTheme(this))
        /* The usage counts of the languages, which the original app kept
         * to guess the main languages, aren't used any more */
        deleteDatabase("language")
    }
}
