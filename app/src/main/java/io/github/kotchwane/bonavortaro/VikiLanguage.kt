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

/**
 * A language whose Wiktionary adds to Reta Vortaro: its words with their
 * Esperanto translations, in assets/vikivortaro/[code]-eo.tsv, and its
 * descriptions of Esperanto words, in eo-[code].tsv, written by
 * scripts/vikivortaro.py; with what Vikivortaro must know of its grammar
 * to compare its words with Reta Vortaro's.
 */
class VikiLanguage(
    /** Its code, as Reta Vortaro's index and the settings have it: "fr" */
    val code: String,
    /** What may come before a word and isn't part of it: an article, "se"
     * for a verb ("Les Alpes" is "Alpes", "se recueillir" "recueillir") */
    val leading: Regex,
    /** The articles alone, which a word as written ([Vikivortaro.strict])
     * drops, keeping "se": "recueillir" isn't "se recueillir" */
    val articles: Regex,
    /** The articles before a word, in any case, to tell a name: "La Havane"
     * is one, "Le monde" isn't */
    val nameArticles: Regex,
    /** The endings of a form of a word, or of the word with the preposition
     * it takes: a plural "s", "faire face à" */
    val formEndings: Set<String>,
    /** Whether an ending of [formEndings] makes a form of this word:
     * French "x" only after "-au", "-eu", "-ou" ("amix" isn't "ami") */
    val isFormEnding: (word: String, ending: String) -> Boolean = { _, _ -> true },
    /** A word written as Reta Vortaro's French may write it: without the
     * "î" and "û" that the spelling of 1990 drops */
    val respelled: (String) -> String = { it },
    /** The small words that make a description more than the meaning of an
     * Esperanto word ("ville de…"), see [Vikivortaro.glossPairs] */
    val glossSmallWords: Set<String>,
    /** The words that start a description rather than a meaning: "action
     * d’absorber" */
    val glossDescribing: Set<String>,
    /** An item that doesn't translate an Esperanto word, which a
     * description gives for it: "d’eau", "of water", for the adjective akva */
    val glossNotFor: (esperanto: String, item: String) -> Boolean = { _, _ -> false },
    /** Whether its pairs show when nothing is said of their Esperanto word
     * (no article in Reta Vortaro, no description, no definition): when
     * they were all reviewed. In French, such words were too often doubtful
     * ("fondre → fundi"); in English, after a review of every pair, 6,400
     * good ones were hidden, "dreamcatcher → sonĝkaptilo" (2026-11-10) */
    val showsUndescribed: Boolean = false,
    /** Whether its nouns keep their capital in a description's words: in
     * German, "Hund" isn't "hund" */
    val capitalNouns: Boolean = false,
) {

    companion object {
        val FRENCH = VikiLanguage(
            code = "fr",
            leading = Regex("^(le |la |les |l'|se |s')"),
            articles = Regex("^(le |la |les |l')"),
            nameArticles = Regex("^(?i)(le |la |les |l'|l’)"),
            formEndings = setOf("s", "x", "es", " a", " à", " de", " d'"),
            isFormEnding = { word, ending -> ending != "x" || word.endsWith("au") || word.endsWith("eu") || word.endsWith("ou") },
            respelled = { it.replace('î', 'i').replace('û', 'u').replace('Î', 'I').replace('Û', 'U') },
            glossSmallWords = setOf("de", "du", "des", "à", "au", "aux", "en", "pour", "par", "qui", "que", "un", "une", "le", "la", "les", "ou", "et"),
            glossDescribing = setOf("abréviation", "action", "fait", "processus"),
            /* Not "d’eau" for akva, which says "of water": an adverb is
             * fine, "d’abord" komence */
            glossNotFor = { esperanto, item ->
                esperanto.endsWith('a') && item.lowercase(java.util.Locale.ROOT).let { it.startsWith("d’") || it.startsWith("d'") }
            },
        )

        val ENGLISH = VikiLanguage(
            code = "en",
            leading = Regex("^(the |a |an |to )"),
            articles = Regex("^(the |a |an )"),
            nameArticles = Regex("^(?i)(the )"),
            formEndings = setOf("s", "es", " of", " to", " for", " with"),
            glossSmallWords = setOf("of", "the", "a", "an", "to", "for", "by", "in", "on", "at", "with", "from",
                "who", "which", "that", "or", "and"),
            glossDescribing = setOf("abbreviation", "act", "action", "process", "state", "quality"),
            /* Not "of water" for akva */
            glossNotFor = { esperanto, item -> esperanto.endsWith('a') && item.lowercase(java.util.Locale.ROOT).startsWith("of ") },
            showsUndescribed = true,
        )

        val GERMAN = VikiLanguage(
            code = "de",
            leading = Regex("^(der |die |das |sich )"),
            articles = Regex("^(der |die |das )"),
            nameArticles = Regex("^(?i)(der |die |das )"),
            /* The plural "s" only: "Rat" isn't "Rate", nor "Wage" "Wagen" */
            formEndings = setOf("s", " an", " auf", " für", " mit", " von", " zu"),
            glossSmallWords = setOf("der", "die", "das", "des", "dem", "den", "ein", "eine", "einer", "eines", "einem",
                "einen", "von", "vom", "zu", "zum", "zur", "für", "mit", "in", "im", "an", "am", "auf", "aus", "bei",
                "durch", "oder", "und", "als", "wie", "sich", "nicht"),
            glossDescribing = setOf("abkürzung", "handlung", "vorgang", "zustand", "eigenschaft", "tätigkeit"),
            capitalNouns = true,
            /* Every pair reviewed (2026-11-10), 477 left out after test round 10 */
            showsUndescribed = true,
        )

        /** The languages whose Wiktionary Bona vortaro has, in the order
         * of their lines */
        val ALL = listOf(FRENCH, ENGLISH, GERMAN)

        fun of(code: String): VikiLanguage? = ALL.firstOrNull { it.code == code }
    }
}
