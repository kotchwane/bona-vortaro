/*
 * Bona vortaro - an Esperanto dictionary for Android, based on PReVo
 * Copyright (C) 2012, 2016  Neil Roberts
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
import java.io.IOException
import java.util.Locale
import java.text.Normalizer

/** A word found by the search: in Reta Vortaro, or only in the French
 * Wiktionary. */
sealed class FoundWord {
    abstract val word: String

    class InRevo(val result: SearchResult) : FoundWord() {
        override val word: String get() = result.word
    }

    class InWiktionary(val result: VikiResult) : FoundWord() {
        override val word: String get() = result.word
    }
}

/** The result of a search: the matching words, the index of the language
 * (in the list that was searched) where they were found, and the French
 * words that only the French Wiktionary has; first, for an Esperanto word
 * typed with its endings, the entries of the word: "kuri" for "kuris". */
class SearchOutcome(
    val results: List<SearchResult>,
    val languageIndex: Int,
    val wiktionary: List<VikiResult> = emptyList(),
    query: String = "",
    esperanto: Boolean = false,
    /** The language where the words were found */
    val language: String = "eo",
    val lemmas: List<FoundWord> = emptyList(),
) {
    val isEmpty get() = results.isEmpty() && wiktionary.isEmpty() && lemmas.isEmpty()

    /** All the words, Wiktionary's among Reta Vortaro's in alphabetical
     * order, as in a printed dictionary */
    val words: List<FoundWord> = lemmas +
        mixed(results.map { FoundWord.InRevo(it) }, wiktionary.map { FoundWord.InWiktionary(it) }, query, esperanto) { it.word }
            .filter { found -> lemmas.none { it.word == found.word } }
}

/** Puts each word of [others] among [words] in alphabetical order, accents
 * and case aside, without changing the order of [words], which the search
 * already gives in that order; [others] too. */
internal fun <T> mixed(
    words: List<T>,
    others: List<T>,
    query: String,
    esperanto: Boolean = false,
    wordOf: (T) -> String,
): List<T> {
    val searched = sortKey(query, esperanto)
    val mixed = mutableListOf<T>()
    var next = 0
    for (other in others) {
        val key = orderKey(wordOf(other), searched, esperanto)
        while (next < words.size && orderKey(wordOf(words[next]), searched, esperanto) <= key)
            mixed.add(words[next++])
        mixed.add(other)
    }
    return mixed + words.drop(next)
}

/** Where a word found by the search is in the order: from its word that
 * starts with what was searched, under which the index has it, eg.
 * "comédie" in "jouer la comédie" for "come"; after that word itself, as
 * the search lists them: "voyager en auto-stop" after "auto-stop". */
internal fun orderKey(word: String, searched: String, esperanto: Boolean): String {
    val key = sortKey(word, esperanto)
    return key.indices.firstOrNull { i ->
        (i == 0 || key[i - 1] in " -'") && key.startsWith(searched, i)
    }?.let { if (it == 0) key else key.substring(it) + "\u0001" } ?: key
}

/** A word as the search orders it, as Trie.searchIgnoringAccents does:
 * accents and case aside; but in Esperanto, a letter with an accent right
 * after the one without, ĉ ĝ ĥ ĵ ŝ ŭ after c g h j s u */
internal fun sortKey(word: String, esperanto: Boolean): String {
    if (!esperanto)
        return Vikivortaro.key(word)
    return word.substringBefore(" (").lowercase(java.util.Locale.ROOT).replace('’', '\'')
        .replace('\u00a0', ' ').replace('\u202f', ' ').trim()
        .map { letter ->
            val plain = Vikivortaro.key(letter.toString())
            if (plain.isNotEmpty() && plain != letter.toString()) plain + "\uffff" else letter.toString()
        }
        .joinToString("")
}

/** Whether a word comes after the last of the results that a search kept,
 * as they stop at a limit: by the word an entry is filed under ("jouer la
 * comédie" under "comédie"), as they are ordered */
internal fun isBeyond(word: String, last: String, query: String, esperanto: Boolean): Boolean {
    val searched = sortKey(query, esperanto)
    return orderKey(word, searched, esperanto) > orderKey(last, searched, esperanto)
}

/** Whether a query is a pattern, with "*" or "?" in it */
fun isPattern(query: String): Boolean = '*' in query || '?' in query

/** What is searched in a language for a query: the x-system is read only
 * in Esperanto, where "ux" is a "ŭ"; in French, "î" and "û" are searched
 * as "i" and "u", which also find them, because Reta Vortaro has both
 * spellings, "maître" and "maitre", but some words only in the 1990 one:
 * "fraicheur", "ile". */
fun searchFilter(query: String, language: String): String {
    /* The apostrophe typed as the straight one, which also finds the
     * curly one */
    val filter = Normalizer.normalize(normaliseQuery(query, xSystem = language == "eo"), Normalizer.Form.NFC)
        .replace('’', '\'')
        /* Non-breaking spaces as spaces; "œ" as "oe", which finds both */
        .replace('\u00a0', ' ').replace('\u202f', ' ')
        .replace("œ", "oe").replace("æ", "ae")
    return if (language == "fr") filter.replace('î', 'i').replace('û', 'u') else filter
}

object DictionarySearch {
    private const val MAX_RESULTS = 128
    /* A pattern like "*o" can match most of the language */
    private const val MAX_PATTERN_RESULTS = 500
    private const val TAG = "bonasearch"

    /** Searches the languages in order and returns the results of the
     * first one that has any. Typing the x-system is accepted in Esperanto
     * (cx → ĉ), and the accents can be left out ("eleve" finds "élève").
     * With "*" (any letters) or "?" (one letter), the query is a pattern
     * for whole words.
     * This does disk access, so it shouldn't run on the main thread. */
    fun search(
        context: Context,
        languages: List<String>,
        query: String,
        /** Whether the search isn't wanted any more, another key typed:
         * it stops, its outcome unused */
        stopped: () -> Boolean = { false },
    ): SearchOutcome {
        for ((index, language) in languages.withIndex()) {
            val filter = searchFilter(query, language)
            val trie = try {
                TrieCache.getTrie(context, language)
            } catch (e: IOException) {
                /* Skip the language instead of crashing, in case it has
                 * disappeared in an update of the dictionary data */
                Log.w(TAG, "Failed to load the index for $language")
                continue
            }
            val esperanto = language == "eo"

            /* As typed; else with a hyphen for a space, or the other way
             * round, or without either: "petit dejeuner" is
             * "petit-déjeuner", "Porto-Riko" "Portoriko" */
            for (typed in spellings(filter)) {
                if (stopped())
                    return SearchOutcome(emptyList(), 0)
                val outcome = searchAs(context, trie, typed, language, esperanto, stopped)
                if (!outcome.isEmpty)
                    return SearchOutcome(outcome.results, index, outcome.wiktionary, typed, esperanto, language, outcome.lemmas)
            }
        }

        return SearchOutcome(emptyList(), 0)
    }

    /* What a search finds as typed in one language */
    private class Found(
        val results: List<SearchResult>,
        val wiktionary: List<VikiResult>,
        val lemmas: List<FoundWord> = emptyList(),
    ) {
        val isEmpty get() = results.isEmpty() && wiktionary.isEmpty() && lemmas.isEmpty()
    }

    private fun spellings(filter: String): List<String> =
        if (isPattern(filter) || (' ' !in filter && '-' !in filter)) listOf(filter)
        else listOf(filter, filter.replace(' ', '-'), filter.replace('-', ' '), filter.replace(" ", "").replace("-", "")).distinct()

    private fun searchAs(
        context: Context,
        trie: Trie,
        filter: String,
        language: String,
        esperanto: Boolean,
        stopped: () -> Boolean,
    ): Found {
        val count: Int
        val results: Array<SearchResult?>
        val limit: Int
        if (isPattern(filter)) {
            results = arrayOfNulls(MAX_PATTERN_RESULTS)
            count = trie.searchPattern(filter, results)
            limit = MAX_PATTERN_RESULTS
        } else {
            results = arrayOfNulls(MAX_RESULTS)
            /* Ignoring the accents, in alphabetical order */
            count = trie.searchIgnoringAccents(filter, results, esperanto)
            limit = MAX_RESULTS
        }

        /* The index has some entries twice, the same word for the same
         * sense; or with its other case, "Januaro, januaro": once, as
         * typed if it is so */
        val found = inTypedCase(once(results.take(count).filterNotNull(), filter), filter)
        /* What Reta Vortaro lacks, from Vikivortaro: in French; in
         * Esperanto, its words, for everyone: the same dictionary, what
         * it says of them only in the languages each reader chose */
        val wiktionary = when {
            isPattern(filter) -> emptyList()
            language == "fr" -> Vikivortaro.search(context, filter, stopped)
            esperanto -> Vikivortaro.searchEsperanto(context, filter, stopped)
            else -> emptyList()
        }
            /* When Reta Vortaro's words stop at the limit, those after the
             * last one aren't shown either: by what the index gave, before
             * its double entries were left out */
            .filter { count < limit || found.isEmpty() || !isBeyond(it.word, found.last().word, filter, esperanto) }

        /* An Esperanto word with its endings, "hundoj", "kuris": its
         * entries first, as a long press finds them, Reta Vortaro's or
         * Vikivortaro's; also when what is typed starts other words,
         * "kuristo" */
        val typed = filter.lowercase(Locale.ROOT)
        val exact = found.any { it.word.lowercase(Locale.ROOT) == typed } ||
            wiktionary.any { it.word.lowercase(Locale.ROOT) == typed }
        if (esperanto && !isPattern(filter) && !exact &&
            ((found.isEmpty() && wiktionary.isEmpty()) || INFLECTED.matches(typed) || typed in PRONOUNS)) {
            val lemmas = inflected(context, trie, filter)
            if (lemmas.isNotEmpty())
                return Found(found, wiktionary.filter { viki -> lemmas.none { it.word == viki.word } }, lemmas)
        }
        return Found(found, wiktionary)
    }

    /* An Esperanto word with an ending, which, typed, finds its entries
     * before the words it starts: "kuris", "grandan", "bonaj", not "bon"
     * ("bo" with an accusative) */
    private val INFLECTED = Regex("^\\p{L}{2,}([oae]j?n|[oa]j|(a|i|o|u)s|u)$")
    private val PRONOUNS = setOf("min", "vin", "lin", "ŝin", "ĝin", "sin", "nin", "ilin", "onin")

    /* Each entry once: by its word, case aside, its article and its mark */
    private fun once(results: List<SearchResult>, typed: String): List<SearchResult> {
        val groups = results.groupBy { Triple(it.word.lowercase(Locale.ROOT), it.article, it.mark) }
        return results.distinctBy { Triple(it.word.lowercase(Locale.ROOT), it.article, it.mark) }
            .map { first ->
                groups.getValue(Triple(first.word.lowercase(Locale.ROOT), first.article, first.mark))
                    .firstOrNull { it.word.startsWith(typed) } ?: first
            }
    }

    /* Of the words that differ only by their case, those written as typed
     * first: "vorto" before "Vorto", "Vorto" before "vorto" */
    private fun inTypedCase(results: List<SearchResult>, typed: String): List<SearchResult> {
        val sorted = mutableListOf<SearchResult>()
        var i = 0
        while (i < results.size) {
            val same = results[i].word.lowercase(Locale.ROOT)
            var end = i
            while (end < results.size && results[end].word.lowercase(Locale.ROOT) == same) end++
            sorted += results.subList(i, end).sortedBy { if (it.word.startsWith(typed)) 0 else 1 }
            i = end
        }
        return sorted
    }

    /** The entries of the likeliest form of an Esperanto word, other than
     * as typed, in Reta Vortaro or Vikivortaro: "hundoj" as "hundo";
     * "abortigoj" as Vikivortaro's "abortigo" before Reta Vortaro's
     * "abortigi" */
    private fun inflected(context: Context, trie: Trie, word: String): List<FoundWord> {
        val results = arrayOfNulls<SearchResult>(16)
        for (form in WordLookup.candidates(word).drop(1)) {
            val count = trie.search(form, results)
            /* The word itself before its examples in other articles */
            val revo = results.take(count).filterNotNull().filter { it.word.lowercase(Locale.ROOT) == form }
                .distinctBy { Triple(it.word, it.article, it.mark) }.sortedBy { it.isExample }
            val viki = Vikivortaro.searchEsperanto(context, form).filter { it.esperanto.lowercase(Locale.ROOT) == form }
            if (revo.isNotEmpty() || viki.isNotEmpty())
                return revo.map { FoundWord.InRevo(it) } + viki.map { FoundWord.InWiktionary(it) }
        }
        return emptyList()
    }
}
