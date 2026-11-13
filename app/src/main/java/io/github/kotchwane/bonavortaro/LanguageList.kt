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

package io.github.kotchwane.bonavortaro

import android.content.Context
import org.xmlpull.v1.XmlPullParser

/** The languages of res/xml/languages.xml, the translations of Reta
 * Vortaro: by name, as the file lists them, and found by their code */
class LanguageList(context: Context) {
    val allLanguages: Array<Language>
    private val languagesByCode: List<Language>

    init {
        val languages = ArrayList<Language>()
        val parser = context.resources.getXml(R.xml.languages)
        val name = StringBuilder()

        while (true) {
            var eventType = parser.eventType
            if (eventType == XmlPullParser.START_TAG) {
                if (parser.name == "lang") {
                    val code = parser.getAttributeValue(null, "code")
                    name.setLength(0)
                    while (true) {
                        parser.next()
                        eventType = parser.eventType
                        if (eventType == XmlPullParser.END_TAG)
                            break
                        else if (eventType == XmlPullParser.TEXT)
                            name.append(parser.text)
                    }
                    languages.add(Language(name.toString(), code))
                }
            } else if (eventType == XmlPullParser.END_DOCUMENT)
                break
            parser.next()
        }

        allLanguages = languages.toTypedArray()
        languagesByCode = allLanguages.sortedBy { it.code }
    }

    private fun find(languageCode: String): Language? {
        val at = languagesByCode.binarySearchBy(languageCode) { it.code }
        return if (at >= 0) languagesByCode[at] else null
    }

    fun hasLanguage(languageCode: String): Boolean =
        languageCode == "eo" || find(languageCode) != null

    /** The name of the language, an adjective as the others: "esperanta,
     * franca", "la esperanta" */
    @JvmOverloads
    fun getLanguageName(languageCode: String, withArticle: Boolean = false): String {
        val name = if (languageCode == "eo") "esperanta" else find(languageCode)?.name ?: return languageCode
        return if (withArticle) "la $name" else name
    }

    companion object {
        private var defaultLanguageList: LanguageList? = null

        @JvmStatic
        fun getDefault(context: Context): LanguageList =
            defaultLanguageList ?: LanguageList(context).also { defaultLanguageList = it }
    }
}
