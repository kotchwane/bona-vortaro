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

    /** A word of "Vortoj de la bona lingvo" that neither has */
    class InBonaLingvo(val entry: BonaLingvoEntry) : FoundWord() {
        override val word: String get() = entry.word
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
    /** What was searched, as typed */
    val query: String = "",
    esperanto: Boolean = false,
    /** The language where the words were found */
    val language: String = "eo",
    val lemmas: List<FoundWord> = emptyList(),
    /** The words of "Vortoj de la bona lingvo" that neither has */
    val bonaLingvo: List<BonaLingvoEntry> = emptyList(),
    /** Typed in full, a word that the list proposes and nobody has, "uato":
     * the words it is proposed instead of, with their entries */
    val proposedInstead: List<Pair<BonaLingvoEntry, SearchResult?>> = emptyList(),
) {
    val isEmpty get() = results.isEmpty() && wiktionary.isEmpty() && lemmas.isEmpty() && bonaLingvo.isEmpty() && proposedInstead.isEmpty()

    /** All the words, Wiktionary's and the list's among Reta Vortaro's in
     * alphabetical order, as in a printed dictionary */
    val words: List<FoundWord> = lemmas +
        mixed(
            mixed(results.map { FoundWord.InRevo(it) }, wiktionary.map { FoundWord.InWiktionary(it) }, query, esperanto) { it.word },
            bonaLingvo.map { FoundWord.InBonaLingvo(it) },
            query,
            esperanto,
        ) { it.word }
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

/** Whether an entry of [form], another form of the word [typed], is its
 * word: always with only a plural or an accusative added ("min" mi,
 * "tiujn" tiu); with another ending, if the form is long or its entry of
 * a root ("ama" ami, but not "pre" pro or "pros" pri, words of their own,
 * with no root and ending) */
internal fun isFormOf(typed: String, form: String, hasRoot: () -> Boolean): Boolean =
    typed.startsWith(form) && typed.substring(form.length) in PLURAL_AND_ACCUSATIVE ||
        form.length > 3 || hasRoot()

private val PLURAL_AND_ACCUSATIVE = setOf("n", "j", "jn")

/** A word copied from a text with the punctuation after it, "hundo." or
 * "hundo,": the word as a whole, as with a space after it. Not "?", a
 * pattern; "ekz." still finds "ekz.", which starts with "ekz" */
fun withoutEndPunctuation(query: String): String {
    val word = query.trimEnd().trimEnd('.', ',', ';', ':', '!', '…')
    return if (word.length < query.trimEnd().length && word.lastOrNull()?.isLetter() == true) "$word " else query
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
     * for whole words. A space after the word asks for that word as a
     * whole, not the longer ones it starts: "malva " finds "malvo", not
     * "malvacoj"; "valet " finds "valet d'écurie", not "valetaille";
     * so does a full stop or a comma after it, "hundo.". This does disk
     * access, so it shouldn't run on the main thread. */
    fun search(
        context: Context,
        languages: List<String>,
        query: String,
        /** Whether the search isn't wanted any more, another key typed:
         * it stops, its outcome unused */
        stopped: () -> Boolean = { false },
    ): SearchOutcome {
        val query = withoutEndPunctuation(query)
        val whole = query.isNotBlank() && query.last().isWhitespace()
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
                val outcome = searchAs(context, trie, typed, language, esperanto, whole, stopped)
                if (!outcome.isEmpty)
                    return SearchOutcome(outcome.results, index, outcome.wiktionary, typed, esperanto, language, outcome.lemmas, outcome.bonaLingvo, outcome.proposedInstead)
            }
        }

        return SearchOutcome(emptyList(), 0)
    }

    /* What a search finds as typed in one language */
    private class Found(
        val results: List<SearchResult>,
        val wiktionary: List<VikiResult>,
        val lemmas: List<FoundWord> = emptyList(),
        val bonaLingvo: List<BonaLingvoEntry> = emptyList(),
        val proposedInstead: List<Pair<BonaLingvoEntry, SearchResult?>> = emptyList(),
    ) {
        val isEmpty get() = results.isEmpty() && wiktionary.isEmpty() && lemmas.isEmpty() && bonaLingvo.isEmpty() && proposedInstead.isEmpty()
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
        whole: Boolean,
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
        val all = inTypedCase(once(results.take(count).filterNotNull(), filter), filter)
        /* The word as a whole, accents and case aside, alone or followed
         * by other words: "malva " leaves out "malvacoj"; "eleve " finds
         * "élève"; "valet " keeps "valet d'écurie" */
        val key = Vikivortaro.key(filter)
        val wanted = { word: String ->
            !whole || isPattern(filter) || Vikivortaro.key(word).let {
                it.startsWith(key) && (it.length == key.length || !it[key.length].isLetterOrDigit())
            }
        }
        val found = all.filter { wanted(it.word) }.let {
            if (esperanto || isPattern(filter)) it else lesserLast(context, it, language, stopped)
        }
        /* What Reta Vortaro lacks, from Vikivortaro: in French; in
         * Esperanto, its words, for everyone: the same dictionary, what
         * it says of them only in the languages each reader chose */
        val wiktionary = when {
            isPattern(filter) -> emptyList()
            language == "fr" -> Vikivortaro.search(context, filter, stopped)
            esperanto -> Vikivortaro.searchEsperanto(context, filter, stopped)
            else -> emptyList()
        }
            .filter { wanted(it.word) }
            /* When Reta Vortaro's words stop at the limit, those after the
             * last one aren't shown either: by what the index gave, before
             * its double entries were left out */
            .filter { count < limit || all.isEmpty() || !isBeyond(it.word, all.last().word, filter, esperanto) }

        /* In Esperanto, the words of "Vortoj de la bona lingvo" that
         * neither has, "eskalatoro": advised against, what to say instead */
        val bonaLingvo = if (esperanto && !isPattern(filter))
            BonaLingvo.search(context, filter, trie, stopped)
                .filter { entry -> wiktionary.none { it.word == entry.word } }
                .filter { wanted(it.word) }
                .filter { count < limit || all.isEmpty() || !isBeyond(it.word, all.last().word, filter, esperanto) }
        else emptyList()

        /* An Esperanto word with its endings, "hundoj", "kuris": its
         * entries first, as a long press finds them, Reta Vortaro's or
         * Vikivortaro's; also when what is typed starts other words,
         * "kuristo" */
        val typed = filter.lowercase(Locale.ROOT)
        val exact = found.any { it.word.lowercase(Locale.ROOT) == typed } ||
            wiktionary.any { it.word.lowercase(Locale.ROOT) == typed } ||
            bonaLingvo.any { it.word.lowercase(Locale.ROOT) == typed }
        if (esperanto && !isPattern(filter) && !exact &&
            ((found.isEmpty() && wiktionary.isEmpty()) || INFLECTED.matches(typed) || typed in PRONOUNS)) {
            val lemmas = inflected(context, trie, filter)
            if (lemmas.isNotEmpty())
                return Found(found, wiktionary.filter { viki -> lemmas.none { it.word == viki.word } }, lemmas, bonaLingvo)
        }
        /* A word typed in full that only the list proposes, "uato": what
         * it is proposed instead of, rather than nothing */
        val proposedInstead = if (esperanto && !isPattern(filter) && !exact)
            BonaLingvo.proposing(context, filter.trim(), trie)
        else emptyList()
        return Found(found, wiktionary, bonaLingvo = bonaLingvo, proposedInstead = proposedInstead)
    }

    /* An Esperanto word with an ending, which, typed, finds its entries
     * before the words it starts: "kuris", "grandan", "bonaj", also with
     * another ending than its entry's, "malva" (malvo) before "malvarma";
     * not "bon" ("bo" with an accusative) */
    private val INFLECTED = Regex("^\\p{L}{2,}([oae]j?n|[oa]j|(a|i|o|u)s|u|[oae])$")
    private val PRONOUNS = setOf("min", "vin", "lin", "ŝin", "ĝin", "sin", "nin", "ilin", "onin")

    /* The order of the Esperanto words a word of another language is
     * translated by, which the index gives in the order of their files,
     * saying nothing of them. Only for the first results, which are seen,
     * as it reads their articles. See [ordered] */
    private fun lesserLast(
        context: Context,
        results: List<SearchResult>,
        language: String,
        stopped: () -> Boolean,
    ): List<SearchResult> {
        val sorted = results.toMutableList()
        var i = 0
        while (i < minOf(sorted.size, LESSER_CHECKED)) {
            val same = sorted[i].word.lowercase(Locale.ROOT)
            var end = i
            while (end < sorted.size && sorted[end].word.lowercase(Locale.ROOT) == same) end++
            /* Only its first words, which are seen: the others, rarely
             * read, keep their order, and a long group is quick */
            end = minOf(end, i + LESSER_CHECKED)
            if (end - i > 1) {
                if (stopped())
                    return results
                val group = sorted.subList(i, end)
                val reordered = ordered(context, group.toList(), language)
                for (k in reordered.indices) group[k] = reordered[k]
            }
            i = end
        }
        return sorted
    }

    private const val LESSER_CHECKED = 24

    /* A word's Esperanto translations, [group]:
     * 1. those that are rare, archaic or to avoid, "(malofte) uvo", or
     *    that "Vortoj de la bona lingvo" advises against, after the others:
     *    "raisin" gives vinbero, then uvo;
     * 2. in French, those that the French Wiktionary gives too first:
     *    "bouder" paŭti;
     * 3. then a much more frequent one first, by [Frequency]: "deviner"
     *    diveni rather than duonkompreni. Only for a word alone, without
     *    brackets: "conduire (phys.)" is kondukti, not the frequent
     *    konduki; and not instead of a word that looks like it, unless the
     *    frequent one does too: "agréable" stays agrabla, not dolĉa;
     * 4. a word that "Vortoj de la bona lingvo" advises against in some
     *    sense only, after the word it proposes for that sense, when that
     *    one translates the word too, which says that it is that sense:
     *    "pollution" poluado before polucio, "game" ludo before partio;
     * 5. the simpler word that the list proposes instead of the first
     *    ("~"), when it translates the word too: first if it is used at
     *    least a twentieth as much, "truck" ŝarĝaŭto before kamiono,
     *    "author" verkinto before aŭtoro; else second, "alphabet"
     *    alfabeto then aboco;
     * 6. but a word of the hand-made list first, where the frequency is
     *    wrong, mostly for very common grammatical words: "aus" el, not de */
    private fun ordered(context: Context, group: List<SearchResult>, language: String): List<SearchResult> {
        val (lesser, others) = group.partition { !it.isExample && isLesser(context, it, language) }
        val foreign = group.first().word
        val esperanto = others.associateWith { result ->
            if (result.isExample) null
            else SearchPreviews.get(context, result, language)?.title?.split(',')?.firstOrNull()
                ?.filter { it.isLetter() || it == ' ' || it == '-' }?.trim()?.takeIf { it.isNotEmpty() }
        }
        val inWiktionary = { result: SearchResult ->
            language == "fr" && esperanto[result]?.let { word ->
                Vikivortaro.frenchOf(context, word).any { Vikivortaro.key(it) == Vikivortaro.key(foreign) }
            } == true
        }
        val ordered = others.sortedBy { if (inWiktionary(it)) 0 else 1 }.toMutableList()
        /* The first word, past an example that may come before it */
        val top = ordered.firstOrNull { esperanto[it] != null }
        val topWord = top?.let { esperanto[it] }
        if (topWord != null && ' ' !in foreign.trim() && '(' !in foreign && '\'' !in foreign) {
            val frequency = { result: SearchResult -> esperanto[result]?.let { Frequency.of(context, it) } ?: 0f }
            val best = ordered.filter { esperanto[it] != null }.maxByOrNull(frequency)
            val bestWord = best?.let { esperanto[it] }
            if (best != null && best !== top && bestWord != null &&
                frequency(best) >= FREQUENT && frequency(best) >= MORE_FREQUENT * maxOf(frequency(top), 0.001f) &&
                !(inWiktionary(top) && !inWiktionary(best)) &&
                (!Frequency.looksLike(foreign, topWord) || Frequency.looksLike(foreign, bestWord))) {
                ordered.remove(best)
                ordered.add(ordered.indexOf(top), best)
            }
        }
        /* The word of the group that Bona lingvo's entry proposes */
        val proposedIn = { entry: BonaLingvoEntry, result: SearchResult ->
            val proposed = entry.preferred
                .flatMap { listOf(BonaLingvo.withoutBrackets(it.word), it.word.replace("(", "").replace(")", "")) }
                .map { it.lowercase(Locale.ROOT) }.toSet()
            ordered.firstOrNull { it !== result && esperanto[it]?.lowercase(Locale.ROOT) in proposed }
        }
        for (result in ordered.toList()) {
            val entry = esperanto[result]?.let { BonaLingvo.entry(context, it) }?.takeIf { it.discouraged } ?: continue
            val better = proposedIn(entry, result) ?: continue
            if (ordered.indexOf(better) > ordered.indexOf(result)) {
                ordered.remove(better)
                ordered.add(ordered.indexOf(result), better)
            }
        }
        ordered.firstOrNull { esperanto[it] != null }?.let { first ->
            val entry = esperanto[first]?.let { BonaLingvo.entry(context, it) }?.takeIf { !it.discouraged } ?: return@let
            val simpler = proposedIn(entry, first) ?: return@let
            val frequency = { result: SearchResult -> esperanto[result]?.let { Frequency.of(context, it) } ?: 0f }
            ordered.remove(simpler)
            val at = ordered.indexOf(first)
            ordered.add(if (frequency(simpler) >= SIMPLER_USED * frequency(first)) at else at + 1, simpler)
        }
        Frequency.preferred(context, language, foreign)?.let { word ->
            ordered.firstOrNull { esperanto[it] == word }?.let { ordered.remove(it); ordered.add(0, it) }
        }
        return ordered + lesser
    }

    /* How much more frequent a word must be to come first, and how frequent
     * at least, per million words */
    private const val MORE_FREQUENT = 2f
    private const val FREQUENT = 0.05f

    /* How much a simpler word of "Vortoj de la bona lingvo" must be used,
     * compared with the first word, to come before it */
    private const val SIMPLER_USED = 0.05f

    private fun isLesser(context: Context, result: SearchResult, language: String): Boolean {
        val preview = SearchPreviews.get(context, result, language) ?: return false
        return preview.isLesserStyle ||
            preview.title.split(',').map { it.trim() }.any { it.isNotEmpty() && BonaLingvo.discouraged(context, it) != null }
    }

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

    /* Whether an article is of a root and its endings, "am/i", rather
     * than of a word of its own, "pro" */
    private fun hasRoot(context: Context, article: Int): Boolean =
        SearchPreviews.title(context, article)?.contains('/') == true

    /** The entries of the likeliest form of an Esperanto word, other than
     * as typed, in Reta Vortaro or Vikivortaro: "hundoj" as "hundo";
     * "abortigoj" as Vikivortaro's "abortigo" before Reta Vortaro's
     * "abortigi" */
    private fun inflected(context: Context, trie: Trie, word: String): List<FoundWord> {
        val results = arrayOfNulls<SearchResult>(16)
        val typed = word.lowercase(Locale.ROOT)
        for (form in WordLookup.candidates(word).drop(1)) {
            val count = trie.search(form, results)
            /* The word itself before its examples in other articles */
            val revo = results.take(count).filterNotNull().filter { it.word.lowercase(Locale.ROOT) == form }
                .distinctBy { Triple(it.word, it.article, it.mark) }.sortedBy { it.isExample }
                .filter { isFormOf(typed, form) { hasRoot(context, it.article) } }
            val viki = Vikivortaro.searchEsperanto(context, form).filter { it.esperanto.lowercase(Locale.ROOT) == form }
            if (revo.isNotEmpty() || viki.isNotEmpty())
                return revo.map { FoundWord.InRevo(it) } + viki.map { FoundWord.InWiktionary(it) }
        }
        return emptyList()
    }
}
