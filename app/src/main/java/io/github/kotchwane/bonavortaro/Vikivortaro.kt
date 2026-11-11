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

import android.content.Context
import android.util.Log
import java.io.IOException
import java.text.Normalizer
import java.util.Locale

/** The word of [language] searched, which only its Wiktionary translates
 * by [esperanto]: the article opened from it says so. */
class VikiSearch(val language: String, val foreign: String, val esperanto: String)

/** A word that the Wiktionary of [language] translates into Esperanto,
 * with the article of Reta Vortaro for that Esperanto word, if it has one,
 * and Wiktionary's description of it, if it says more than the word. Or,
 * found [inEsperanto], an Esperanto word that only Vikivortaro has, with
 * the words of [language] it gives for it. */
class VikiResult(
    val language: String,
    val foreign: String,
    val esperanto: String,
    val revo: SearchResult?,
    val description: String?,
    val inEsperanto: Boolean = false,
    val foreignWords: List<String> = emptyList(),
    /** For an Esperanto word, the first sense of its definition in the
     * Esperanto Wiktionary, if it has one */
    val definition: String? = null,
    /** Whether "Vortoj de la bona lingvo" advises against the Esperanto word
     * in the sense of this translation, when it does in some senses only:
     * "glitch → cimo" (pri komputiloj), not "bedbug → cimo" */
    val advised: Boolean = false,
) {
    /** The word as the search found it */
    val word: String get() = if (inEsperanto) esperanto else foreign
}

/** The definition of an Esperanto word in the Esperanto Wiktionary: its
 * senses, and whether it was corrected for Bona vortaro ("adaptita"),
 * else "" */
class EsperantoDefinition(val senses: List<String>, val origin: String) {
    val adapted get() = origin == "adaptita"
}

/**
 * What the Wiktionary of a language ([VikiLanguage], French so far) adds to
 * Reta Vortaro: words of that language that Reta Vortaro can't find, with
 * their Esperanto translations, and descriptions in that language of
 * Esperanto words that it lacks; and what the Esperanto Wiktionary adds:
 * definitions of Esperanto words that it lacks, chosen one by one
 * (data/vikivortaro-eo-words.txt).
 *
 * Its data, by the contributors of the Wiktionaries under CC BY-SA 4.0, is
 * kept apart from Reta Vortaro's, in assets/vikivortaro/, written by
 * scripts/vikivortaro.py; it is always shown as coming from Wiktionary.
 */
object Vikivortaro {
    private const val TAG = "bonavikivortaro"
    private const val MAX_RESULTS = 64
    private const val MAX_LOOKED_AT = 400

    private class Data(
        /* Sorted by key: the word without its case and accents */
        val keys: List<String>,
        val pairs: List<Pair<String, String>>,
        /* The words of the language for an Esperanto word */
        val foreignOf: Map<String, List<String>>,
        val descriptions: Map<String, String>,
        /* Shared by the languages: the Esperanto Wiktionary's */
        val definitions: Map<String, EsperantoDefinition>,
        /* The pairs whose Esperanto word "Vortoj de la bona lingvo" advises
         * against in their sense, reviewed one by one (data/vikivortaro-advised.tsv) */
        val advised: Set<Pair<String, String>> = emptySet(),
        /* The Esperanto words with nothing said of them that are shown
         * all the same ([VikiLanguage.showsUndescribed]) */
        val undescribed: Collection<String> = emptyList(),
        /* In German, the keys with umlauts and ß spelled out ("mueller",
         * "strasse"), sorted, each with the index of its pair */
        val spelledKeys: List<String> = emptyList(),
        val spelledIndices: IntArray = IntArray(0),
    ) {
        /* The Esperanto words that Vikivortaro describes or defines, sorted
         * by key; in English and German, all of their Esperanto words: the
         * same dictionary in every tab, "belto" found in Esperanto as "Belt"
         * finds it in German (user, 2026-11-11) */
        private val keyed = (descriptions.keys + definitions.keys + undescribed).distinct().map { key(it) to it }.sortedBy { it.first }
        val esperantoWords: List<String> = keyed.map { it.second }
        val esperantoKeys: List<String> = keyed.map { it.first }
    }

    private val data = java.util.concurrent.ConcurrentHashMap<String, Data>()
    /* A lock per language: a French search at start-up doesn't wait for
     * the English data, read meanwhile (test round 7) */
    private val locks = VikiLanguage.ALL.associate { it.code to Any() }
    @Volatile private var definitions: Map<String, EsperantoDefinition>? = null

    /** Whether the words of a language that Vikivortaro adds are shown: to
     * those who chose to see that language's translations in the articles */
    fun inArticles(context: Context, language: VikiLanguage): Boolean =
        BonaSettings.translationFilter(context)(language.code)

    /** The languages whose words Vikivortaro adds that are shown, see
     * [inArticles] */
    fun shownLanguages(context: Context): List<VikiLanguage> =
        BonaSettings.translationFilter(context).let { shown -> readerOrder(context).filter { shown(it.code) } }

    /** The languages of the Wiktionaries in the reader's order: those whose
     * translations they read, in the order of their search tabs, then of
     * the translations; then the others. An English reader gets
     * "anakondo"'s English words, not the French ones (test round 7); one
     * whose tabs say EO, EN, FR gets "marvirino"'s English (user,
     * 2026-11-10). */
    fun readerOrder(context: Context): List<VikiLanguage> {
        val tabs = BonaSettings.getSearchLanguages(context)
        val order = BonaSettings.getTranslationOrder(context)
        val shown = BonaSettings.translationFilter(context)
        fun place(list: List<String>, code: String) = list.indexOf(code).let { if (it < 0) Int.MAX_VALUE else it }
        return VikiLanguage.ALL.sortedWith(
            compareBy({ !shown(it.code) }, { place(tabs, it.code) }, { place(order, it.code) })
        )
    }

    /** Reads the data ahead of the first search, which would otherwise
     * wait for it, the reader's languages first. Disk access: not on the
     * main thread. */
    fun preload(context: Context) {
        for (language in readerOrder(context))
            data(context, language)
    }

    private fun data(context: Context, language: VikiLanguage): Data =
        data[language.code] ?: synchronized(locks.getValue(language.code)) {
            data[language.code] ?: load(context, language).also { data[language.code] = it }
        }

    private fun lines(context: Context, file: String, optional: Boolean = false): List<Pair<String, String>> = try {
        context.assets.open("vikivortaro/$file").bufferedReader().useLines { lines ->
            lines.filter { !it.startsWith("#") }
                .mapNotNull { line -> line.split('\t').takeIf { it.size == 2 }?.let { it[0] to it[1] } }
                .toList()
        }
    } catch (e: IOException) {
        if (!optional)
            Log.w(TAG, "Failed to read $file")
        emptyList()
    }

    private fun load(context: Context, language: VikiLanguage): Data {
        val descriptions = lines(context, "eo-${language.code}.tsv").toMap()
        /* The pairs left out after a review, which the descriptions would
         * give again: "painfully", oj ("Ouch, painfully") */
        val rejected = lines(context, "${language.code}-eo-not.tsv", optional = true).toHashSet()
        val translations = lines(context, "${language.code}-eo.tsv").filter { it !in rejected }
        /* Case aside: the description "Irish wolfhound." of "irlanda
         * lupohundo" gives "irish wolfhound", the table's "Irish
         * wolfhound" (test round 8) */
        val known = translations.mapTo(HashSet()) { it.first.lowercase(Locale.ROOT) to it.second }
        /* Each key made once, not at each comparison of the sort */
        val keyed = (translations + glossPairs(descriptions, language).filter { (it.first.lowercase(Locale.ROOT) to it.second) !in known && it !in rejected })
            .map { key(it.first) to it }.sortedBy { it.first }
        val pairs = keyed.map { it.second }
        val foreignOf = HashMap<String, MutableList<String>>()
        for ((foreign, esperanto) in pairs)
            /* By the word as written: "bruno" the colour isn't "Bruno" */
            foreignOf.getOrPut(esperanto) { mutableListOf() }.add(foreign)
        val spelled = if (language == VikiLanguage.GERMAN)
            pairs.indices.map { spelledOut(pairs[it].first) to it }.sortedBy { it.first }
        else emptyList()
        return Data(
            keyed.map { it.first }, pairs, foreignOf, descriptions, definitions(context),
            advised = lines(context, "${language.code}-eo-advised.tsv", optional = true).toHashSet(),
            undescribed = if (language.showsUndescribed) foreignOf.keys else emptyList(),
            spelledKeys = spelled.map { it.first },
            spelledIndices = spelled.map { it.second }.toIntArray(),
        )
    }

    /** The words that a description of a Wiktionary gives as the meaning
     * of an Esperanto word, when it is only such words; in French:
     * "ĝirejo", "Virage."; "reakiri", "Recouvrer, regagner, récupérer,
     * reprendre." Its tables of translations lack them ("virage" has no
     * Esperanto), and Reta Vortaro too. Not a description of more words
     * ("Abuja, capitale du Nigeria"): at most two words each, without small
     * words ("de", "pour"…); for a name, only the names ("Cologne", not
     * "ville d’Allemagne"); nor "d’eau" for an adjective, akva, nor what
     * starts with "action", "fait"… ("fait d’acquérir"). */
    internal fun glossPairs(descriptions: Map<String, String>, language: VikiLanguage = VikiLanguage.FRENCH): List<Pair<String, String>> =
        descriptions.flatMap { (esperanto, description) ->
            val text = description.removeSuffix(".")
            if (text.isEmpty() || esperanto.isEmpty() || '.' in text || text.any { it in GLOSS_UNUSABLE || it.isDigit() })
                return@flatMap emptyList()
            val items = text.split(',', ';').map { it.trim() }
            if (items.any { item ->
                    val words = item.split(' ')
                    words.size !in 1..2 || words.any { it.isEmpty() || it.lowercase(Locale.ROOT) in language.glossSmallWords }
                })
                return@flatMap emptyList()
            val name = esperanto.first().isUpperCase()
            items.filter { !name || it.first().isUpperCase() }
                .filter { !language.glossNotFor(esperanto, it) }
                /* Not what describes the word, "action d’absorber"; but
                 * "action" alone, ago */
                .filter { ' ' !in it || it.substringBefore(' ').lowercase(Locale.ROOT) !in language.glossDescribing }
                .map { item ->
                    /* Its capital only as the first word of the description,
                     * unless a name ("États-Unien"), or a German noun */
                    val foreign = if (!name && !language.capitalNouns && item.first().isUpperCase() && item.drop(1).none { it.isUpperCase() })
                        item.replaceFirstChar { it.lowercase(Locale.ROOT) } else item
                    foreign to esperanto
                }
        }

    private const val GLOSS_UNUSABLE = "()[]:#«»\"/=+…!?"

    /* eo-eo.tsv: the word, its senses separated by " ¶ ", its origin. Read
     * once for all the languages */
    @Synchronized
    private fun definitions(context: Context): Map<String, EsperantoDefinition> =
        definitions ?: readDefinitions(context).also { definitions = it }

    private fun readDefinitions(context: Context): Map<String, EsperantoDefinition> = try {
        context.assets.open("vikivortaro/eo-eo.tsv").bufferedReader().useLines { lines ->
            lines.filter { !it.startsWith("#") }
                .mapNotNull { line ->
                    val fields = line.split('\t')
                    if (fields.size < 2 || fields[1].isBlank()) null
                    else fields[0] to EsperantoDefinition(fields[1].split(" ¶ "), fields.getOrNull(2).orEmpty())
                }
                .toMap()
        }
    } catch (e: IOException) {
        Log.w(TAG, "Failed to read eo-eo.tsv")
        emptyMap()
    }

    /** An Esperanto word's definition in the Esperanto Wiktionary, if it is
     * one of those chosen. Disk access, the first time. */
    fun definition(context: Context, word: String): EsperantoDefinition? = definitions(context)[word]

    /* eo-parts.tsv: how the English Wiktionary says a word is made, the
     * stems of its parts ("mar·virin") or [BORROWED]. Read once */
    @Volatile
    private var parts: Map<String, String>? = null

    const val BORROWED = "<"

    /** How Wiktionary says an Esperanto word is made: the stems of its
     * parts, "mar·virin" for marvirino; [BORROWED] for a word taken whole
     * from another language, "oceloto"; null if it doesn't say. Disk
     * access, the first time. */
    fun madeOf(context: Context, word: String): String? =
        (parts ?: lines(context, "eo-parts.tsv", optional = true).toMap().also { parts = it })[word]

    /** A word as the search compares it: without case, accents, and
     * what Reta Vortaro adds in brackets ("potager (jardin)"); "œ" as "oe",
     * as the search reads it */
    fun key(word: String): String =
        /* Most words, without accents or other signs: the same, quicker,
         * for the data read before the first search */
        if (word.all { it < '\u0080' }) word.substringBefore(" (").lowercase(Locale.ROOT).trim()
        else Normalizer.normalize(word.substringBefore(" (").lowercase(Locale.ROOT), Normalizer.Form.NFD)
            .filter { Character.getType(it) != Character.NON_SPACING_MARK.toInt() }
            .replace('’', '\'').replace('\u00a0', ' ').replace('\u202f', ' ')
            .replace("œ", "oe").replace("æ", "ae")
            .trim()

    /** The words of a language starting with the query that Reta Vortaro
     * doesn't have, or not for that Esperanto word, each with its Esperanto
     * word. Disk access: not on the main thread. */
    fun search(context: Context, language: VikiLanguage, query: String, stopped: () -> Boolean = { false }): List<VikiResult> {
        val data = data(context, language)
        val prefix = key(query)
        if (prefix.isEmpty())
            return emptyList()
        val typed = query.lowercase(Locale.ROOT).trim()
        var start = data.keys.binarySearch(prefix).let { if (it < 0) -it - 1 else it }
        while (start > 0 && data.keys[start - 1].startsWith(prefix)) start--
        var end = start
        while (end < data.keys.size && data.keys[end].startsWith(prefix)) end++
        /* In German, typed without umlauts nor ß: "Strasse" Straße,
         * "Weissrussland" Weißrussland, also with a real "ss" beside */
        val spelled = germanSpelled(data, query, start until end)

        val (esperanto, foreign) = try {
            TrieCache.getTrie(context, "eo") to TrieCache.getTrie(context, language.code)
        } catch (e: IOException) {
            return emptyList()
        }
        val results = mutableListOf<VikiResult>()
        /* In the order of the keys, the words spelled out among the others */
        val indices = if (spelled.isEmpty()) (start until end).asSequence()
            else ((start until end) + spelled).sorted().asSequence()
        val iterator = indices.iterator()
        /* Not too many lookups in Reta Vortaro, when it has most of them;
         * the words that don't match what was typed cost nothing: "â" finds
         * "âcreté" among all of "a…" */
        var looked = 0
        while (iterator.hasNext() && results.size < MAX_RESULTS && looked < MAX_LOOKED_AT &&
            (looked % 16 != 0 || !stopped())) {
            val index = iterator.next()
            val (word, esperantoWord) = data.pairs[index]
            if (index in start until end && !matches(typed, word))
                continue
            looked++
            /* Where Reta Vortaro has the word, Wiktionary's Esperanto too,
             * after its own: "cafetière → kafujo" after "cafetière (verseuse)
             * → kafkruĉo". Not when Reta Vortaro gives it for that word
             * already, nor for a word only Wiktionary has, where most of
             * its doubtful words are ("fondre → fundi") */
            val entries = revoEntries(foreign, word, language)
            var revo: SearchResult? = null
            if (inRevo(foreign, word, esperanto, esperantoWord, typed, entries, language)) {
                revo = revoEsperanto(esperanto, esperantoWord, foreign, data.foreignOf[esperantoWord].orEmpty(), language)
                /* By article: not either when Reta Vortaro gives it to
                 * another word of the same article */
                val articles = esperantoArticles(esperanto, esperantoWord)
                if (revo == null || entries.any { it.article in articles })
                    continue
            }
            result(context, language, data, esperanto, word, esperantoWord, foreign, revo)?.let { results.add(it) }
        }
        /* In the order of the keys, the exact matches first; for each word,
         * the Esperanto words built from Reta Vortaro's roots before those
         * made on a root it lacks, unlabelled: "frogmouth" ranbuŝulo
         * before podargo (user, 2026-11-10) */
        return newRootsLast(esperanto, results, stopped) { BonaLingvo.discouraged(context, it) != null }
    }

    /* Whether each Esperanto word is on a new root, kept for the next
     * keystrokes: "s", "sp", "spr" analyse the same words (round 9); a
     * few thousand at most, emptied past that */
    private val onNewRoot = java.util.concurrent.ConcurrentHashMap<String, Boolean>()
    private const val ON_NEW_ROOT_KEPT = 5000

    /* And a word "Vortoj de la bona lingvo" advises against in that
     * sense, [discouraged], after them all: "glitch" problemo before cimo */
    internal fun newRootsLast(
        esperanto: Trie,
        results: List<VikiResult>,
        stopped: () -> Boolean = { false },
        discouraged: (VikiResult) -> Boolean = { false },
    ): List<VikiResult> {
        if (onNewRoot.size > ON_NEW_ROOT_KEPT)
            onNewRoot.clear()
        fun isLate(result: VikiResult) = result.revo == null && onNewRoot.getOrPut(result.esperanto) { newRoot(esperanto, result.esperanto) }
        val ordered = ArrayList<VikiResult>(results.size)
        var start = 0
        while (start < results.size) {
            /* A newer search: as found, the rest of it */
            if (stopped())
                return ordered + results.subList(start, results.size)
            val word = key(results[start].foreign)
            var end = start
            while (end < results.size && key(results[end].foreign) == word) end++
            /* A stable sort: Wiktionary's order otherwise */
            ordered += results.subList(start, end).sortedBy { if (discouraged(it)) 2 else if (isLate(it)) 1 else 0 }
            start = end
        }
        return ordered
    }

    /** The Esperanto words starting with the query that only Vikivortaro
     * has, with a page of their own: with the words of the first language
     * that has them. Disk access: not on the main thread. */
    fun searchEsperanto(context: Context, query: String, stopped: () -> Boolean = { false }): List<VikiResult> {
        val typed = query.lowercase(Locale.ROOT).trim()
        val prefix = key(typed)
        if (prefix.isEmpty())
            return emptyList()
        val esperanto = try {
            TrieCache.getTrie(context, "eo")
        } catch (e: IOException) {
            return emptyList()
        }
        val languages = readerOrder(context).map { it to data(context, it) }
        /* The words of every language once, in the order of their keys:
         * each looked up in Reta Vortaro once, not once per language (the
         * definitions of the Esperanto Wiktionary are in each) */
        val candidates = sortedSetOf<Pair<String, String>>(compareBy({ it.first }, { it.second }))
        for ((_, data) in languages) {
            var i = data.esperantoKeys.binarySearch(prefix).let { if (it < 0) -it - 1 else it }
            while (i > 0 && data.esperantoKeys[i - 1].startsWith(prefix)) i--
            var taken = 0
            while (i < data.esperantoKeys.size && data.esperantoKeys[i].startsWith(prefix) && taken < MAX_LOOKED_AT) {
                val word = data.esperantoWords[i]
                if (matches(typed, word) && candidates.add(data.esperantoKeys[i] to word))
                    taken++
                i++
            }
        }
        val results = mutableListOf<VikiResult>()
        val foreignIndex = HashMap<String, Trie?>()
        for ((looked, candidate) in candidates.withIndex()) {
            if (looked >= MAX_LOOKED_AT || (looked % 16 == 0 && stopped()))
                break
            val word = candidate.second
            val (language, data) = languageOf(languages, word)
            val foreign = foreignIndex.getOrPut(language.code) {
                try {
                    TrieCache.getTrie(context, language.code)
                } catch (e: IOException) {
                    null
                }
            }
            if (revoEsperanto(esperanto, word, foreign, data.foreignOf[word].orEmpty(), language) == null
                && !isReformOfRevo(esperanto, word))
                results.add(esperantoResult(language, data, word))
        }
        /* In the order of the search in Esperanto, ĉ after c, to be put
         * among Reta Vortaro's words; the first ones in it */
        return results.sortedBy { sortKey(it.word, esperanto = true) }.take(MAX_RESULTS)
    }

    /** An Esperanto word written so that only Vikivortaro has, as the
     * search finds it; null if Reta Vortaro has it, or it isn't there */
    fun onlyInWiktionary(context: Context, word: String): VikiResult? {
        val result = esperantoResult(context, word) ?: return null
        val esperanto = try {
            TrieCache.getTrie(context, "eo")
        } catch (e: IOException) {
            return null
        }
        return result.takeIf { revoEsperanto(esperanto, word) == null && !isReformOfRevo(esperanto, word) }
    }

    /* The language whose Wiktionary says most of an Esperanto word for the
     * reader: the first of theirs that describes it, else that gives its
     * words, else the first of theirs (its Esperanto definition only). Not
     * "nur traduko de la germana" where French describes it, as its page
     * does (review, 2026-11-11) */
    private fun languageOf(languages: List<Pair<VikiLanguage, Data>>, word: String): Pair<VikiLanguage, Data> =
        languages.firstOrNull { (_, data) -> informative(data, word) != null }
            ?: languages.firstOrNull { (_, data) -> word in data.foreignOf || word in data.descriptions } ?: languages.first()

    /** The same word with its first letter in the other case, when only
     * Wiktionary has both: "Smurfo" for "smurfo"; null otherwise. Disk
     * access: not on the main thread. */
    fun otherCase(context: Context, word: String): String? {
        val first = word.firstOrNull() ?: return null
        val other = (if (first.isUpperCase()) first.lowercaseChar() else first.uppercaseChar()) + word.drop(1)
        return other.takeIf { it != word && onlyInWiktionary(context, it) != null && onlyInWiktionary(context, word) != null }
    }

    /** An Esperanto word that only Vikivortaro has, found again for the
     * history, with the reader's language that has it; null if an update
     * of the data dropped it. */
    fun esperantoResult(context: Context, word: String): VikiResult? {
        val languages = readerOrder(context).map { it to data(context, it) }
        if (languages.none { (language, data) -> word in data.descriptions || word in data.definitions ||
                (language.showsUndescribed && word in data.foreignOf) })
            return null
        val (language, data) = languageOf(languages, word)
        return esperantoResult(language, data, word)
    }

    private fun esperantoResult(language: VikiLanguage, data: Data, word: String) = VikiResult(
        language = language.code,
        foreign = "",
        esperanto = word,
        revo = null,
        description = informative(data, word),
        inEsperanto = true,
        foreignWords = data.foreignOf[word].orEmpty().distinctBy { key(it) },
        definition = data.definitions[word]?.senses?.firstOrNull(),
    )

    /** Whether Reta Vortaro has a word that Wiktionary translates by
     * [esperantoWord]: written the same ([strict]: "comédie (théâtre)" is
     * "comédie"), or written otherwise ([loose], or as a plural) for the
     * same Esperanto word, eg. "Terre" as "terre", for "Tero" and "tero".
     * Not another word: "pécheur" isn't "pêcheur", "recueillir" isn't "se
     * recueillir", nor "Réunion" "réunion". In the whole index: its search
     * for what was typed stops at a limit, and may not reach the word. */
    internal fun inRevo(
        foreign: Trie,
        word: String,
        esperanto: Trie? = null,
        esperantoWord: String? = null,
        typed: String? = null,
        /* Its entries, if already looked up */
        entries: List<SearchResult>? = null,
        language: VikiLanguage = VikiLanguage.FRENCH,
    ): Boolean {
        val entries = entries ?: revoEntries(foreign, word, language)
        val strict = strict(word, language)
        if (entries.any { strict(it.word, language) == strict })
            return true
        if (esperanto == null || esperantoWord == null || entries.isEmpty())
            return false
        /* Written otherwise, as long as what was typed finds it: "pécheur"
         * doesn't find Reta Vortaro's "pêcheur (rel.)" */
        val articles = esperantoArticles(esperanto, esperantoWord)
        return entries.any { it.article in articles && (typed == null || matches(typed, it.word)) }
    }

    /** Reta Vortaro's entry for a word that Vikivortaro gave, if it has
     * one since, eg. after an update of the dictionary: in its language,
     * written the same ([strict]); in Esperanto, as written */
    internal fun revoEntry(
        index: Trie,
        word: String,
        esperanto: Boolean = false,
        language: VikiLanguage = VikiLanguage.FRENCH,
        /** In another language, the Esperanto word Wiktionary gave and
         * Reta Vortaro's index of Esperanto: its entry only in that word's
         * article. "cafetière → kafujo" isn't "cafetière (verseuse) →
         * kafkruĉo", which the history opened instead (test round 7) */
        esperantoIndex: Trie? = null,
        esperantoWord: String? = null,
    ): SearchResult? {
        if (esperanto)
            return revoEsperanto(index, word)
        val strict = strict(word, language)
        val articles = if (esperantoIndex != null && esperantoWord != null) esperantoArticles(esperantoIndex, esperantoWord) else null
        return revoEntries(index, word, language).firstOrNull { strict(it.word, language) == strict && (articles == null || it.article in articles) }
    }

    /* Reta Vortaro's entries in a language for a word compared loosely, or
     * as its plural or singular: "alevin", "alevins" */
    private fun revoEntries(index: Trie, word: String, language: VikiLanguage): List<SearchResult> {
        val loose = loose(word, language)
        val found = mutableListOf<SearchResult>()
        val results = arrayOfNulls<SearchResult>(64)
        fun look(prefix: String) {
            val count = index.searchIgnoringAccents(prefix, results)
            found += results.take(count).filterNotNull().filter { loose(it.word, language) == loose || isForm(it.word, word, language) }
        }
        look(key(word))
        /* The singular, before the plural typed */
        if (language.formEndings.any { it.length <= 2 && !it.startsWith(" ") && key(word).endsWith(it) })
            look(key(word).dropLast(1))
        /* An expression filed under its first word too: "right away → tuj"
         * under "right", whereas "right away → senprokraste" is found as
         * written, and Wiktionary's "right away → tuj" was shown again
         * (test round 7) */
        val first = loose.substringBefore(' ')
        if (' ' in loose && first.length >= 2) {
            val more = arrayOfNulls<SearchResult>(128)
            val count = index.searchIgnoringAccents(first, more)
            found += more.take(count).filterNotNull().filter { loose(it.word, language) == loose }
        }
        if (found.isNotEmpty())
            return found.distinct()
        /* An expression is filed under one of its words: "Amérique du Nord"
         * under "Amérique", "La Havane" under "Havane"; "Grande Bretagne"
         * is "Grande-Bretagne" */
        if (loose.any { it == ' ' || it == '\'' }) {
            val more = arrayOfNulls<SearchResult>(128)
            for (part in loose.split(' ', '\'').filter { it.length >= 3 }.distinct()) {
                val count = index.searchIgnoringAccents(part, more)
                more.take(count).firstOrNull { loose(it!!.word, language) == loose }?.let { return listOf(it) }
            }
        }
        /* Written in one word or in two: Wiktionary's "hummingbird" is Reta
         * Vortaro's "humming bird", which was shown twice (user,
         * 2026-11-09). Each place of the space tried, last: "fortune
         * teller" is found as written, not as "fortuneteller", another
         * article */
        val joined = loose.replace(" ", "")
        if (joined.length < 5)
            return emptyList()
        val forms = if (' ' in loose) listOf(joined)
            /* Each part of two letters at least: "log in", "screw up" */
            else (2..joined.length - 2).map { joined.substring(0, it) + " " + joined.substring(it) }
        for (form in forms) {
            val count = index.searchIgnoringAccents(form, results)
            found += results.take(count).filterNotNull().filter { loose(it.word, language).replace(" ", "") == joined }
        }
        return found.distinct()
    }

    /* The articles of Reta Vortaro's entries for an Esperanto word, its
     * case aside */
    private fun esperantoArticles(index: Trie, word: String): Set<Int> {
        val written = compact(word).lowercase(Locale.ROOT)
        val results = arrayOfNulls<SearchResult>(16)
        val articles = mutableSetOf<Int>()
        for (form in listOf(word, word.replace("-", "").replace(" ", "")).distinct()) {
            val count = index.search(form.lowercase(Locale.ROOT).replace('’', '\''), results)
            results.take(count).filterNotNull().filter { compact(it.word).lowercase(Locale.ROOT) == written }
                .mapTo(articles) { it.article }
        }
        return articles
    }

    /** A word as written, to tell it from another: as [loose], but with
     * its accents, the case of its first letter, and "se" ("pécheur",
     * "pêcheur"; "Réunion", "réunion"; "recueillir", "se recueillir"); in
     * French "î" and "û" as "i" and "u", which the spelling of 1990 drops */
    internal fun strict(word: String, language: VikiLanguage = VikiLanguage.FRENCH): String {
        val plain = language.respelled(Normalizer.normalize(word.trim().replace(QUALIFIER, "").substringBefore(" ("), Normalizer.Form.NFC)
            .replace('’', '\'').replace('\u00a0', ' ').replace('\u202f', ' ')
            .replace("œ", "oe").replace("Œ", "Oe").replace("æ", "ae").replace("Æ", "Ae"))
        val text = plain.lowercase(Locale.ROOT).replace(language.articles, "").replace('-', ' ').replace(NOT_A_LETTER, "")
            .split(' ').filter { it.isNotEmpty() }.joinToString(" ")
        return if (capital(plain, language)) "^$text" else text
    }

    /** A word compared loosely, to tell whether Reta Vortaro already has
     * it: accents, case, "œ", an article or "se" before, a qualifier in
     * brackets before or after, punctuation and hyphens aside: "Les Alpes"
     * is "Alpes", "(viande de) bœuf" "boeuf", "petit-déjeuner (subst.)"
     * "petit déjeuner", "bravo !" "bravo" */
    internal fun loose(word: String, language: VikiLanguage = VikiLanguage.FRENCH): String =
        key(word.trim().replace(QUALIFIER, ""))
            .replace(language.leading, "")
            .replace('-', ' ')
            .replace(NOT_A_LETTER, "")
            .split(' ').filter { it.isNotEmpty() }.joinToString(" ")

    /** A word of a language's Wiktionary and its Esperanto word, found
     * again for the history; null if an update of the data dropped them.
     * Disk access: not on the main thread. */
    fun result(context: Context, language: VikiLanguage, foreign: String, word: String): VikiResult? {
        val data = data(context, language)
        val key = key(foreign)
        var i = data.keys.binarySearch(key).let { if (it < 0) return null else it }
        while (i > 0 && data.keys[i - 1] == key) i--
        while (i < data.keys.size && data.keys[i] == key && data.pairs[i] != (foreign to word)) i++
        if (i == data.keys.size || data.keys[i] != key)
            return null
        val (esperanto, foreignIndex) = try {
            TrieCache.getTrie(context, "eo") to TrieCache.getTrie(context, language.code)
        } catch (e: IOException) {
            return null
        }
        return result(context, language, data, esperanto, foreign, word, foreignIndex)
    }

    /* Nothing to show for a word that has neither an article in Reta
     * Vortaro nor a description in Wiktionary, unless "Vortoj de la bona
     * lingvo" says what to say instead: "cassoulet → kasuleto", prefere
     * "franca fazeolaĵo" */
    private fun result(
        context: Context,
        language: VikiLanguage,
        data: Data,
        esperanto: Trie,
        foreign: String,
        word: String,
        foreignIndex: Trie? = null,
        /* Its entry, if already looked up */
        known: SearchResult? = null,
    ): VikiResult? {
        val revo = known ?: revoEsperanto(esperanto, word, foreignIndex, data.foreignOf[word].orEmpty(), language)
        if (revo == null && isReformOfRevo(esperanto, word))
            return null
        /* Its definition in the Esperanto Wiktionary says something too:
         * "seabed → marfundo"; in English, every pair, all reviewed:
         * "dreamcatcher → sonĝkaptilo" (user, 2026-11-10) */
        return if (revo != null || word in data.descriptions || word in data.definitions || language.showsUndescribed ||
            BonaLingvo.entry(context, word) != null)
            VikiResult(language.code, foreign, word, revo, informative(data, word), advised = (foreign to word) in data.advised,
                definition = data.definitions[word]?.senses?.firstOrNull())
        else null
    }

    /** Reta Vortaro's entry for an Esperanto word: as written, with its
     * hyphen ("Nov-Zelando"), else without, as the index has an affix
     * ("-ej" as "ej"). Not only without: "Nov-Zelando" isn't "novzelando",
     * and was taken for a word Reta Vortaro doesn't have. */
    internal fun revoEsperanto(
        index: Trie,
        word: String,
        foreign: Trie? = null,
        foreignWords: List<String> = emptyList(),
        language: VikiLanguage = VikiLanguage.FRENCH,
    ): SearchResult? {
        /* As written, with the case of its first letter: "Bruno" the name
         * isn't "bruno" the colour, "Butano" the country isn't "butano" */
        asWritten(index, word)?.let { return it }
        /* Written with the other case, the same word when Reta Vortaro
         * has one of its foreign words in that article: "Tero", "Terre", is
         * "tero"; but "Etno", "Etna", isn't "etno", nor "Graco", "Graz",
         * "graco" */
        if (foreign == null || word.isEmpty())
            return null
        val first = word.substring(0, 1)
        val other = (if (first == first.lowercase(Locale.ROOT)) first.uppercase(Locale.ROOT) else first.lowercase(Locale.ROOT)) +
            word.substring(1)
        val entry = asWritten(index, other) ?: return null
        /* Its foreign words written the same, case too: "Centre" the
         * region isn't "centre" */
        val found = foreignWords.flatMap { word ->
            revoEntries(foreign, word, language).filter { strict(it.word, language) == strict(word, language) }
        }
        return entry.takeIf { found.any { it.article == entry.article } }
    }

    private fun asWritten(index: Trie, word: String): SearchResult? {
        val written = compact(word)
        val results = arrayOfNulls<SearchResult>(16)
        for (form in listOf(word, word.replace("-", "").replace(" ", "")).distinct()) {
            val count = index.search(form.lowercase(Locale.ROOT).replace('’', '\''), results)
            /* The word itself rather than an example of it in another
             * article: "animo", not the example of "religio" */
            val found = results.take(count).filterNotNull().filter { compact(it.word) == written }
            (found.firstOrNull { !it.isExample } ?: found.firstOrNull())?.let { return it }
        }
        return null
    }

    /* A word as Reta Vortaro's may write it: the apostrophe straight, no
     * hyphen nor space ("Porto-Riko", "Portoriko"; "-ej", "ej"), nor the
     * mark of an exclamation ("saluton!", an example), its first letter as
     * it is, the others small */
    private fun compact(word: String): String {
        val plain = word.replace('’', '\'').replace("-", "").replace(" ", "").trimEnd('!', '?', '.')
        return plain.take(1) + plain.drop(1).lowercase(Locale.ROOT)
    }

    /** Whether Reta Vortaro has a word under several entries, which
     * Wiktionary doesn't tell apart: "korveto" the ship and the bird. Its
     * Wiktionary words then go on none of them. Disk access. */
    fun ambiguous(context: Context, word: String): Boolean {
        val index = try {
            TrieCache.getTrie(context, "eo")
        } catch (e: IOException) {
            return true
        }
        return ambiguous(index, word)
    }

    internal fun ambiguous(index: Trie, word: String): Boolean {
        val results = arrayOfNulls<SearchResult>(32)
        val count = index.search(word.lowercase(Locale.ROOT), results)
        /* Two entries of one article too: "miksilo" the whisk and the
         * mixing desk */
        /* Not its examples in other articles: "animo" in "religio" */
        return results.take(count).filter { it!!.word == word && !it.isExample }.map { it!!.article to it.mark }.distinct().size > 1
    }

    /** Whether an Esperanto word that Reta Vortaro doesn't have is made on
     * a root it lacks too: "anakondo", "aŭdicio"; not a word built from
     * its roots and affixes ("kaŝpasaĝero"), a name, nor an abbreviation.
     * Such words come after the others ([newRootsLast]), unlabelled. */
    internal fun newRoot(index: Trie, word: String): Boolean {
        if (word.isEmpty() || word[0].isUpperCase())
            return false
        val entryOf = IndexEntries(index)
        fun known(part: String) =
            WordLookup.candidates(part).any { entryOf(it) != null } || WordAnalyser.ofAffixes(part) ||
                WordAnalyser.analyse(part, entryOf, needsEnding = true).any { it.complete } ||
                WordAnalyser.analyse(part, entryOf).any { it.complete }
        /* Each word of "konsilia ĉambro", each part of "kvindek-naŭa"; a
         * word with its vowel elided, "dank' al", "grand'osta", as itself
         * with -o or -a (test round 7) */
        val words = word.split('-', ' ').filter { it.isNotEmpty() }
        return words.any { written ->
            val parts = written.split('\'', '’')
            parts.withIndex().any { (i, part) ->
                /* Followed by the apostrophe: its vowel elided */
                val elided = i < parts.lastIndex
                part.length > 3 && '.' !in part && part[0].isLowerCase() &&
                    !known(part) && !(elided && (known(part + "o") || known(part + "a")))
            }
        }
    }

    /** A word with the suffix -iĉ- of the reform, "katiĉo", for a root Reta
     * Vortaro has, "kato": not a word of the dictionary */
    internal fun isReformOfRevo(index: Trie, word: String): Boolean {
        val stem = REFORM.find(word)?.let { word.removeRange(it.range.first, it.range.first + 2) } ?: return false
        return stem.length > 2 && revoEsperanto(index, stem) != null
    }

    /** Whether a word starts with what was typed, as Reta Vortaro's
     * search does: a letter typed without accent matches any accent ("eleve"
     * finds "élève"), one typed with an accent only that one, so "lŭ" finds
     * nothing and not "lubie". */
    /** A German word as typed without umlauts nor ß: "mueller" for
     * Müller, "strasse" for Straße; the case and other accents aside */
    internal fun spelledOut(word: String): String =
        key(word.lowercase(Locale.ROOT).replace("ü", "ue").replace("ä", "ae").replace("ö", "oe").replace("ß", "ss"))

    /* The indices of the German pairs that the query finds spelled out and
     * not as it is: only when it has such a pair of letters, "ue", "ss" */
    private fun germanSpelled(data: Data, query: String, found: IntRange): List<Int> {
        if (data.spelledKeys.isEmpty() || !SPELLED_PAIRS.containsMatchIn(query.lowercase(Locale.ROOT)))
            return emptyList()
        val prefix = spelledOut(query)
        var i = data.spelledKeys.binarySearch(prefix).let { if (it < 0) -it - 1 else it }
        while (i > 0 && data.spelledKeys[i - 1].startsWith(prefix)) i--
        val extra = mutableListOf<Int>()
        while (i < data.spelledKeys.size && data.spelledKeys[i].startsWith(prefix) && extra.size < MAX_LOOKED_AT) {
            val index = data.spelledIndices[i++]
            if (index !in found)
                extra.add(index)
        }
        return extra
    }

    private val SPELLED_PAIRS = Regex("ue|ae|oe|ss")

    internal fun matches(typed: String, word: String): Boolean {
        val typed = typed.replace("œ", "oe").replace("æ", "ae")
        val written = Normalizer.normalize(word.lowercase(Locale.ROOT).replace('’', '\''), Normalizer.Form.NFC)
            .replace("œ", "oe").replace("æ", "ae")
        if (written.length < typed.length)
            return false
        return typed.indices.all { i -> typed[i] == written[i] || typed[i] == base(written[i]) }
    }

    private fun base(letter: Char): Char = Normalizer.normalize(letter.toString(), Normalizer.Form.NFD)[0]

    /** The words of a language that its Wiktionary gives for an Esperanto
     * word and that [revo], its translation in Reta Vortaro, doesn't
     * already say, accents aside. Disk access: not on the main thread. */
    fun foreignFor(context: Context, language: VikiLanguage, word: String, revo: String?): List<String> {
        val data = data(context, language)
        return foreignFor(data.foreignOf[word].orEmpty() + otherCase(context, language, data, word), revo, language)
    }

    /* Wiktionary's words for an Esperanto word of Reta Vortaro that
     * Wiktionary writes with the other case, the same word: "Latino" for
     * "latino" */
    private fun otherCase(context: Context, language: VikiLanguage, data: Data, word: String): List<String> {
        if (word.isEmpty())
            return emptyList()
        val first = word.substring(0, 1)
        val other = (if (first == first.lowercase(Locale.ROOT)) first.uppercase(Locale.ROOT) else first.lowercase(Locale.ROOT)) +
            word.substring(1)
        val foreign = data.foreignOf[other] ?: return emptyList()
        val (esperanto, foreignIndex) = try {
            TrieCache.getTrie(context, "eo") to TrieCache.getTrie(context, language.code)
        } catch (e: IOException) {
            return emptyList()
        }
        val entry = revoEsperanto(esperanto, other, foreignIndex, foreign, language) ?: return emptyList()
        return if (compact(entry.word) == compact(word)) foreign else emptyList()
    }

    /** The rest of [foreignFor], on Wiktionary's words for the word */
    internal fun foreignFor(foreign: List<String>, revo: String?, language: VikiLanguage = VikiLanguage.FRENCH): List<String> {
        /* Without the numbers of the senses, which the translations have
         * when they differ by sense: "(1) Seeland, Sélande; (2) Zélande" */
        val said = revo?.replace(SENSES, "")?.split(',', ';')?.map { it.trim() }?.filter { key(it).isNotEmpty() }.orEmpty()
        return foreign
            .filter { word -> said.none { sameWord(it, word, language) } }
            .distinctBy { key(it) }
    }

    /** Whether two words are the same, compared loosely ([loose]), or one
     * is the plural of the other, or the same with a preposition after
     * ("faire face", "faire face à"); but not a name and a word ("clair",
     * "Claire"), nor a word in another ("vers" in "envers"), nor a feminine,
     * which is often another word ("golf", "golfe") */
    internal fun sameWord(a: String, b: String, language: VikiLanguage = VikiLanguage.FRENCH): Boolean {
        val (ka, kb) = loose(a, language) to loose(b, language)
        if (ka == kb)
            return true
        if (capital(a, language) != capital(b, language))
            return false
        return isForm(a, b, language)
    }

    /* One the plural of the other, or with a preposition after, accents
     * counting: "congre" isn't "congrès" */
    private fun isForm(a: String, b: String, language: VikiLanguage): Boolean {
        val (ka, kb) = strict(a, language).removePrefix("^") to strict(b, language).removePrefix("^")
        val (short, long) = if (ka.length <= kb.length) ka to kb else kb to ka
        val ending = long.substring(short.length.coerceAtMost(long.length))
        return short.isNotEmpty() && long.startsWith(short) && ending in language.formEndings && language.isFormEnding(short, ending)
    }

    /* The numbers of the senses before a translation: "(1) Seeland" */
    private val SENSES = Regex("\\(\\d+(, *\\d+)*\\)[ \u00A0]*")

    private val QUALIFIER = Regex("^\\([^)]*\\) *")
    private val NOT_A_LETTER = Regex("[^\\p{L}\\p{N} ']")

    /* The suffix -iĉ- of the reform, before an ending */
    private val REFORM = Regex("iĉ(?=[oae]j?n?$)")

    /* Whether the word, after its article, is a name */
    private fun capital(word: String, language: VikiLanguage) =
        word.trim().replace(language.nameArticles, "").firstOrNull()?.isUpperCase() == true

    /** The words of a language that its Wiktionary gives for an example of
     * Reta Vortaro that has no translation in it there, "bonvolu" ("s'il
     * vous plaît"), and that [revo], the translation of its word, doesn't
     * already say. Disk access: not on the main thread. */
    fun foreignForExample(context: Context, language: VikiLanguage, example: String, revo: String?): List<String> {
        val data = data(context, language)
        val plain = example.trim().trimEnd('!', '?', '.', ' ')
        return foreignFor(data.foreignOf[example].orEmpty() + data.foreignOf[plain].orEmpty(), revo, language)
    }

    /** The words of a language that its Wiktionary translates by an
     * Esperanto word */
    fun foreignOf(context: Context, language: VikiLanguage, word: String): List<String> =
        data(context, language).foreignOf[word].orEmpty().distinctBy { key(it) }

    /** Whether a Wiktionary translates a word of its language by an
     * Esperanto word */
    fun anyForeignOf(context: Context, word: String): Boolean =
        VikiLanguage.ALL.any { foreignOf(context, it, word).isNotEmpty() }

    /** For an Esperanto word that nothing describes, the sense of the
     * table each word of a language is translated in, in that language:
     * "Belt", "Ostseeraum: Meerenge zwischen einer Insel und dem
     * gegenüberliegenden Festland" for belto (user, 2026-11-11). Read from
     * the file each time, for one page: not kept. Disk access: not on the
     * main thread. */
    fun senses(context: Context, language: VikiLanguage, word: String): Map<String, String> = try {
        val start = word + "\t"
        context.assets.open("vikivortaro/${language.code}-eo-senses.tsv").bufferedReader().useLines { lines ->
            lines.filter { it.startsWith(start) }
                .mapNotNull { line -> line.split('\t').takeIf { it.size == 3 }?.let { it[1] to it[2] } }
                .toMap()
        }
    } catch (e: IOException) {
        emptyMap()
    }

    /** A Wiktionary's description of an Esperanto word, in its language, if
     * it says more than the words translated by it. Disk access: not on the
     * main thread. */
    fun description(context: Context, language: VikiLanguage, word: String): String? = informative(data(context, language), word)

    /** Whether a Wiktionary has a page of its own for an Esperanto word: a
     * description, even one only repeating its words ("spicisto",
     * "Épicier."). Disk access: not on the main thread. */
    fun hasPage(context: Context, language: VikiLanguage, word: String): Boolean = word in data(context, language).descriptions

    /* Two descriptions in three only repeat a word: "spicisto", "Épicier." */
    private fun informative(data: Data, word: String): String? {
        val description = data.descriptions[word] ?: return null
        val foreign = data.foreignOf[word].orEmpty().map { key(it) }
        /* Nor the words again, in any order: "Acidité, aigreur." */
        return description.takeUnless { key(it.trimEnd('.')).let { plain -> plain in foreign || plain.split(", ").toSet() == foreign.toSet() } }
    }
}
