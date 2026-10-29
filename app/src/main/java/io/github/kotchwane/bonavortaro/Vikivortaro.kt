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

/** The French word searched, which only the French Wiktionary translates
 * by [esperanto]: the article opened from it says so. */
class VikiSearch(val french: String, val esperanto: String)

/** A French word that the French Wiktionary translates into Esperanto,
 * with the article of Reta Vortaro for that Esperanto word, if it has one,
 * and Wiktionary's French description of it, if it says more than the
 * French word. Or, found [inEsperanto], an Esperanto word that only
 * Vikivortaro has, with the French words it gives for it. */
class VikiResult(
    val french: String,
    val esperanto: String,
    val revo: SearchResult?,
    val description: String?,
    val inEsperanto: Boolean = false,
    val frenchWords: List<String> = emptyList(),
    /** For an Esperanto word, the first sense of its definition in the
     * Esperanto Wiktionary, if it has one */
    val definition: String? = null,
) {
    /** The word as the search found it */
    val word: String get() = if (inEsperanto) esperanto else french
}

/** The definition of an Esperanto word in the Esperanto Wiktionary: its
 * senses, and whether it was corrected for Bona vortaro ("adaptita"),
 * else "" */
class EsperantoDefinition(val senses: List<String>, val origin: String) {
    val adapted get() = origin == "adaptita"
}

/**
 * What the French Wiktionary adds to Reta Vortaro: French words that Reta
 * Vortaro can't find, with their Esperanto translations, and French
 * descriptions of Esperanto words that it lacks; and what the Esperanto
 * Wiktionary adds: definitions of Esperanto words that it lacks, chosen
 * one by one (data/vikivortaro-eo-words.txt).
 *
 * Its data, by the contributors of the French Wiktionary under CC BY-SA 4.0,
 * is kept apart from Reta Vortaro's, in assets/vikivortaro/, written by
 * scripts/vikivortaro.py; it is always shown as coming from Wiktionary.
 */
object Vikivortaro {
    private const val TAG = "bonavikivortaro"
    private const val MAX_RESULTS = 64
    private const val MAX_LOOKED_AT = 400

    private class Data(
        /* Sorted by key: the French word without its case and accents */
        val keys: List<String>,
        val pairs: List<Pair<String, String>>,
        val frenchOf: Map<String, List<String>>,
        val descriptions: Map<String, String>,
        val definitions: Map<String, EsperantoDefinition>,
    ) {
        /* The Esperanto words that Vikivortaro describes or defines, sorted
         * by key */
        val esperantoWords: List<String> = (descriptions.keys + definitions.keys).distinct().sortedBy { key(it) }
        val esperantoKeys: List<String> = esperantoWords.map { key(it) }
    }

    @Volatile private var data: Data? = null

    /** Whether the French that Vikivortaro adds is shown: to those who chose
     * to see the French translations in the articles */
    fun inArticles(context: Context): Boolean = BonaSettings.translationFilter(context)("fr")


    /** Reads the data ahead of the first search, which would otherwise
     * wait for it. Disk access: not on the main thread. */
    fun preload(context: Context) {
        data(context)
    }

    @Synchronized
    private fun data(context: Context): Data = data ?: load(context).also { data = it }

    private fun lines(context: Context, file: String): List<Pair<String, String>> = try {
        context.assets.open("vikivortaro/$file").bufferedReader().useLines { lines ->
            lines.filter { !it.startsWith("#") }
                .mapNotNull { line -> line.split('\t').takeIf { it.size == 2 }?.let { it[0] to it[1] } }
                .toList()
        }
    } catch (e: IOException) {
        Log.w(TAG, "Failed to read $file")
        emptyList()
    }

    private fun load(context: Context): Data {
        val pairs = lines(context, "fr-eo.tsv").sortedBy { key(it.first) }
        val frenchOf = HashMap<String, MutableList<String>>()
        for ((french, esperanto) in pairs)
            /* By the word as written: "bruno" the colour isn't "Bruno" */
            frenchOf.getOrPut(esperanto) { mutableListOf() }.add(french)
        return Data(pairs.map { key(it.first) }, pairs, frenchOf, lines(context, "eo-fr.tsv").toMap(), definitions(context))
    }

    /* eo-eo.tsv: the word, its senses separated by " ¶ ", its origin */
    private fun definitions(context: Context): Map<String, EsperantoDefinition> = try {
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
    fun definition(context: Context, word: String): EsperantoDefinition? = data(context).definitions[word]

    /** A French word as the search compares it: without case, accents, and
     * what Reta Vortaro adds in brackets ("potager (jardin)"); "œ" as "oe",
     * as the search reads it */
    fun key(word: String): String =
        Normalizer.normalize(word.substringBefore(" (").lowercase(Locale.ROOT), Normalizer.Form.NFD)
            .filter { Character.getType(it) != Character.NON_SPACING_MARK.toInt() }
            .replace('’', '\'').replace('\u00a0', ' ').replace('\u202f', ' ')
            .replace("œ", "oe").replace("æ", "ae")
            .trim()

    /** The French words starting with the query that Reta Vortaro doesn't
     * have, or not for that Esperanto word, each with its Esperanto word.
     * Disk access: not on the main thread. */
    fun search(context: Context, query: String, stopped: () -> Boolean = { false }): List<VikiResult> {
        val data = data(context)
        val prefix = key(query)
        if (prefix.isEmpty())
            return emptyList()
        val typed = query.lowercase(Locale.ROOT).trim()
        var start = data.keys.binarySearch(prefix).let { if (it < 0) -it - 1 else it }
        while (start > 0 && data.keys[start - 1].startsWith(prefix)) start--

        val (esperanto, french) = try {
            TrieCache.getTrie(context, "eo") to TrieCache.getTrie(context, "fr")
        } catch (e: IOException) {
            return emptyList()
        }
        val results = mutableListOf<VikiResult>()
        var i = start
        /* Not too many lookups in Reta Vortaro, when it has most of them;
         * the words that don't match what was typed cost nothing: "â" finds
         * "âcreté" among all of "a…" */
        var looked = 0
        while (i < data.keys.size && data.keys[i].startsWith(prefix) && results.size < MAX_RESULTS && looked < MAX_LOOKED_AT &&
            (looked % 16 != 0 || !stopped())) {
            val (word, esperantoWord) = data.pairs[i++]
            if (!matches(typed, word))
                continue
            looked++
            /* Where Reta Vortaro has the French word, Wiktionary's word too,
             * after its own: "cafetière → kafujo" after "cafetière (verseuse)
             * → kafkruĉo". Not when Reta Vortaro gives it for that word
             * already, nor for a word only Wiktionary has, where most of
             * its doubtful words are ("fondre → fundi") */
            val entries = revoEntries(french, word)
            var revo: SearchResult? = null
            if (inRevo(french, word, esperanto, esperantoWord, typed, entries)) {
                revo = revoEsperanto(esperanto, esperantoWord, french, data.frenchOf[esperantoWord].orEmpty())
                /* By article: not either when Reta Vortaro gives it to
                 * another word of the same article */
                val articles = esperantoArticles(esperanto, esperantoWord)
                if (revo == null || entries.any { it.article in articles })
                    continue
            }
            result(data, esperanto, word, esperantoWord, french, revo)?.let { results.add(it) }
        }
        /* In the order of the keys, the exact matches first */
        return results
    }

    /** The Esperanto words starting with the query that only Vikivortaro
     * has, with a page of their own. Disk access: not on the main thread. */
    fun searchEsperanto(context: Context, query: String, stopped: () -> Boolean = { false }): List<VikiResult> {
        val data = data(context)
        val typed = query.lowercase(Locale.ROOT).trim()
        val prefix = key(typed)
        if (prefix.isEmpty())
            return emptyList()
        val (esperanto, french) = try {
            TrieCache.getTrie(context, "eo") to TrieCache.getTrie(context, "fr")
        } catch (e: IOException) {
            return emptyList()
        }
        var i = data.esperantoKeys.binarySearch(prefix).let { if (it < 0) -it - 1 else it }
        while (i > 0 && data.esperantoKeys[i - 1].startsWith(prefix)) i--
        val results = mutableListOf<VikiResult>()
        var looked = 0
        while (i < data.esperantoKeys.size && data.esperantoKeys[i].startsWith(prefix) && looked < MAX_LOOKED_AT &&
            (looked % 16 != 0 || !stopped())) {
            val word = data.esperantoWords[i++]
            if (!matches(typed, word))
                continue
            looked++
            if (revoEsperanto(esperanto, word, french, data.frenchOf[word].orEmpty()) == null
                && !isReformOfRevo(esperanto, word))
                results.add(esperantoResult(data, word))
        }
        /* In the order of the search in Esperanto, ĉ after c, to be put
         * among Reta Vortaro's words; the first ones in it */
        return results.sortedBy { sortKey(it.word, esperanto = true) }.take(MAX_RESULTS)
    }

    /** An Esperanto word that only Vikivortaro has, found again for the
     * history; null if an update of the data dropped it. */
    fun esperantoResult(context: Context, word: String): VikiResult? =
        data(context).let { if (word in it.descriptions || word in it.definitions) esperantoResult(it, word) else null }

    private fun esperantoResult(data: Data, word: String) = VikiResult(
        french = "",
        esperanto = word,
        revo = null,
        description = informative(data, word),
        inEsperanto = true,
        frenchWords = data.frenchOf[word].orEmpty().distinctBy { key(it) },
        definition = data.definitions[word]?.senses?.firstOrNull(),
    )

    /** Whether Reta Vortaro has a French word that Wiktionary translates by
     * [esperantoWord]: written the same ([strict]: "comédie (théâtre)" is
     * "comédie"), or written otherwise ([loose], or as a plural) for the
     * same Esperanto word, eg. "Terre" as "terre", for "Tero" and "tero".
     * Not another word: "pécheur" isn't "pêcheur", "recueillir" isn't "se
     * recueillir", nor "Réunion" "réunion". In the whole index: its search
     * for what was typed stops at a limit, and may not reach the word. */
    internal fun inRevo(
        french: Trie,
        word: String,
        esperanto: Trie? = null,
        esperantoWord: String? = null,
        typed: String? = null,
        /* Its entries, if already looked up */
        entries: List<SearchResult> = revoEntries(french, word),
    ): Boolean {
        val strict = strict(word)
        if (entries.any { strict(it.word) == strict })
            return true
        if (esperanto == null || esperantoWord == null || entries.isEmpty())
            return false
        /* Written otherwise, as long as what was typed finds it: "pécheur"
         * doesn't find Reta Vortaro's "pêcheur (rel.)" */
        val articles = esperantoArticles(esperanto, esperantoWord)
        return entries.any { it.article in articles && (typed == null || matches(typed, it.word)) }
    }

    /** Reta Vortaro's entry for a word that Vikivortaro gave, if it has
     * one since, eg. after an update of the dictionary: in French, written
     * the same ([strict]); in Esperanto, as written */
    internal fun revoEntry(index: Trie, word: String, esperanto: Boolean = false): SearchResult? {
        if (esperanto)
            return revoEsperanto(index, word)
        val strict = strict(word)
        return revoEntries(index, word).firstOrNull { strict(it.word) == strict }
    }

    /* Reta Vortaro's French entries for a word compared loosely, or as its
     * plural or singular: "alevin", "alevins" */
    private fun revoEntries(index: Trie, word: String): List<SearchResult> {
        val loose = loose(word)
        val found = mutableListOf<SearchResult>()
        val results = arrayOfNulls<SearchResult>(64)
        fun look(prefix: String) {
            val count = index.searchIgnoringAccents(prefix, results)
            found += results.take(count).filterNotNull().filter { loose(it.word) == loose || isForm(it.word, word) }
        }
        look(key(word))
        /* The singular, before the plural typed */
        if (FORM_ENDINGS.any { it.length <= 2 && !it.startsWith(" ") && key(word).endsWith(it) })
            look(key(word).dropLast(1))
        if (found.isNotEmpty())
            return found.distinct()
        /* An expression is filed under one of its words: "Amérique du Nord"
         * under "Amérique", "La Havane" under "Havane"; "Grande Bretagne"
         * is "Grande-Bretagne" */
        if (loose.none { it == ' ' || it == '\'' })
            return emptyList()
        val more = arrayOfNulls<SearchResult>(128)
        for (part in loose.split(' ', '\'').filter { it.length >= 3 }.distinct()) {
            val count = index.searchIgnoringAccents(part, more)
            more.take(count).firstOrNull { loose(it!!.word) == loose }?.let { return listOf(it) }
        }
        return emptyList()
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

    /** A French word as written, to tell it from another: as [loose], but
     * with its accents, the case of its first letter, and "se" ("pécheur",
     * "pêcheur"; "Réunion", "réunion"; "recueillir", "se recueillir"); "î"
     * and "û" as "i" and "u", which the spelling of 1990 drops */
    internal fun strict(word: String): String {
        val plain = Normalizer.normalize(word.trim().replace(QUALIFIER, "").substringBefore(" ("), Normalizer.Form.NFC)
            .replace('’', '\'').replace('\u00a0', ' ').replace('\u202f', ' ')
            .replace("œ", "oe").replace("Œ", "Oe").replace("æ", "ae").replace("Æ", "Ae")
            .replace('î', 'i').replace('û', 'u').replace('Î', 'I').replace('Û', 'U')
        val text = plain.lowercase(Locale.ROOT).replace(ARTICLE, "").replace('-', ' ').replace(NOT_A_LETTER, "")
            .split(' ').filter { it.isNotEmpty() }.joinToString(" ")
        return if (capital(plain)) "^$text" else text
    }

    /** A French word compared loosely, to tell whether Reta Vortaro already
     * has it: accents, case, "œ", an article or "se" before, a qualifier in
     * brackets before or after, punctuation and hyphens aside: "Les Alpes"
     * is "Alpes", "(viande de) bœuf" "boeuf", "petit-déjeuner (subst.)"
     * "petit déjeuner", "bravo !" "bravo" */
    internal fun loose(word: String): String =
        key(word.trim().replace(QUALIFIER, ""))
            .replace(LEADING, "")
            .replace('-', ' ')
            .replace(NOT_A_LETTER, "")
            .split(' ').filter { it.isNotEmpty() }.joinToString(" ")

    /** A French word of Wiktionary and its Esperanto word, found again for
     * the history; null if an update of the data dropped them. Disk access:
     * not on the main thread. */
    fun result(context: Context, french: String, word: String): VikiResult? {
        val data = data(context)
        val key = key(french)
        var i = data.keys.binarySearch(key).let { if (it < 0) return null else it }
        while (i > 0 && data.keys[i - 1] == key) i--
        while (i < data.keys.size && data.keys[i] == key && data.pairs[i] != (french to word)) i++
        if (i == data.keys.size || data.keys[i] != key)
            return null
        val (esperanto, frenchIndex) = try {
            TrieCache.getTrie(context, "eo") to TrieCache.getTrie(context, "fr")
        } catch (e: IOException) {
            return null
        }
        return result(data, esperanto, french, word, frenchIndex)
    }

    /* Nothing to show for a word that has neither an article in Reta
     * Vortaro nor a description in Wiktionary */
    private fun result(
        data: Data,
        esperanto: Trie,
        french: String,
        word: String,
        frenchIndex: Trie? = null,
        /* Its entry, if already looked up */
        known: SearchResult? = null,
    ): VikiResult? {
        val revo = known ?: revoEsperanto(esperanto, word, frenchIndex, data.frenchOf[word].orEmpty())
        if (revo == null && isReformOfRevo(esperanto, word))
            return null
        return if (revo != null || word in data.descriptions) VikiResult(french, word, revo, informative(data, word)) else null
    }

    /** Reta Vortaro's entry for an Esperanto word: as written, with its
     * hyphen ("Nov-Zelando"), else without, as the index has an affix
     * ("-ej" as "ej"). Not only without: "Nov-Zelando" isn't "novzelando",
     * and was taken for a word Reta Vortaro doesn't have. */
    internal fun revoEsperanto(
        index: Trie,
        word: String,
        french: Trie? = null,
        frenchWords: List<String> = emptyList(),
    ): SearchResult? {
        /* As written, with the case of its first letter: "Bruno" the name
         * isn't "bruno" the colour, "Butano" the country isn't "butano" */
        asWritten(index, word)?.let { return it }
        /* Written with the other case, the same word when Reta Vortaro
         * has one of its French words in that article: "Tero", "Terre", is
         * "tero"; but "Etno", "Etna", isn't "etno", nor "Graco", "Graz",
         * "graco" */
        if (french == null || word.isEmpty())
            return null
        val first = word.substring(0, 1)
        val other = (if (first == first.lowercase(Locale.ROOT)) first.uppercase(Locale.ROOT) else first.lowercase(Locale.ROOT)) +
            word.substring(1)
        val entry = asWritten(index, other) ?: return null
        /* Its French words written the same, case too: "Centre" the
         * region isn't "centre" */
        val found = frenchWords.flatMap { word -> revoEntries(french, word).filter { strict(it.word) == strict(word) } }
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
     * French words then go on none of them. Disk access. */
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

    /** A word with the suffix -iĉ- of the reform, "katiĉo", for a root Reta
     * Vortaro has, "kato": not a word of the dictionary */
    internal fun isReformOfRevo(index: Trie, word: String): Boolean {
        val stem = REFORM.find(word)?.let { word.removeRange(it.range.first, it.range.first + 2) } ?: return false
        return stem.length > 2 && revoEsperanto(index, stem) != null
    }

    /** Whether a French word starts with what was typed, as Reta Vortaro's
     * search does: a letter typed without accent matches any accent ("eleve"
     * finds "élève"), one typed with an accent only that one, so "lŭ" finds
     * nothing and not "lubie". */
    internal fun matches(typed: String, word: String): Boolean {
        val typed = typed.replace("œ", "oe").replace("æ", "ae")
        val french = Normalizer.normalize(word.lowercase(Locale.ROOT).replace('’', '\''), Normalizer.Form.NFC)
            .replace("œ", "oe").replace("æ", "ae")
        if (french.length < typed.length)
            return false
        return typed.indices.all { i -> typed[i] == french[i] || typed[i] == base(french[i]) }
    }

    private fun base(letter: Char): Char = Normalizer.normalize(letter.toString(), Normalizer.Form.NFD)[0]

    /** The French words that Wiktionary gives for an Esperanto word and
     * that [revo], its translation in Reta Vortaro, doesn't already say,
     * accents aside. Disk access: not on the main thread. */
    fun frenchFor(context: Context, word: String, revo: String?): List<String> {
        val data = data(context)
        return frenchFor(data.frenchOf[word].orEmpty() + otherCase(context, data, word), revo)
    }

    /* Wiktionary's French words for an Esperanto word of Reta Vortaro
     * that Wiktionary writes with the other case, the same word: "Latino"
     * for "latino" */
    private fun otherCase(context: Context, data: Data, word: String): List<String> {
        if (word.isEmpty())
            return emptyList()
        val first = word.substring(0, 1)
        val other = (if (first == first.lowercase(Locale.ROOT)) first.uppercase(Locale.ROOT) else first.lowercase(Locale.ROOT)) +
            word.substring(1)
        val french = data.frenchOf[other] ?: return emptyList()
        val (esperanto, frenchIndex) = try {
            TrieCache.getTrie(context, "eo") to TrieCache.getTrie(context, "fr")
        } catch (e: IOException) {
            return emptyList()
        }
        val entry = revoEsperanto(esperanto, other, frenchIndex, french) ?: return emptyList()
        return if (compact(entry.word) == compact(word)) french else emptyList()
    }

    /** The rest of [frenchFor], on Wiktionary's French words for the word */
    internal fun frenchFor(french: List<String>, revo: String?): List<String> {
        /* Without the numbers of the senses, which the translations have
         * when they differ by sense: "(1) Seeland, Sélande; (2) Zélande" */
        val said = revo?.replace(SENSES, "")?.split(',', ';')?.map { it.trim() }?.filter { key(it).isNotEmpty() }.orEmpty()
        return french
            .filter { word -> said.none { sameWord(it, word) } }
            .distinctBy { key(it) }
    }

    /** Whether two French words are the same, compared loosely ([loose]),
     * or one is the plural of the other, or the same with a preposition
     * after ("faire face", "faire face à"); but not a name and a word
     * ("clair", "Claire"), nor a word in another ("vers" in "envers"), nor
     * a feminine, which is often another word ("golf", "golfe") */
    internal fun sameWord(a: String, b: String): Boolean {
        val (ka, kb) = loose(a) to loose(b)
        if (ka == kb)
            return true
        if (capital(a) != capital(b))
            return false
        return isForm(a, b)
    }

    /* One the plural of the other, or with a preposition after, accents
     * counting: "congre" isn't "congrès" */
    private fun isForm(a: String, b: String): Boolean {
        val (ka, kb) = strict(a).removePrefix("^") to strict(b).removePrefix("^")
        val (short, long) = if (ka.length <= kb.length) ka to kb else kb to ka
        val ending = long.substring(short.length.coerceAtMost(long.length))
        /* "x" the plural of "-au", "-eu", "-ou" only: "amix" isn't "ami" */
        return short.isNotEmpty() && long.startsWith(short) && ending in FORM_ENDINGS &&
            (ending != "x" || short.endsWith("au") || short.endsWith("eu") || short.endsWith("ou"))
    }

    /* A form, or the same with its preposition: "faire face", "faire face à" */
    private val SENSES = Regex("\\(\\d+(, *\\d+)*\\)[ \u00A0]*")

    private val FORM_ENDINGS = setOf("s", "x", "es", " a", " à", " de", " d'")
    private val LEADING = Regex("^(le |la |les |l'|se |s')")
    private val ARTICLE = Regex("^(le |la |les |l')")
    private val QUALIFIER = Regex("^\\([^)]*\\) *")
    private val NOT_A_LETTER = Regex("[^\\p{L}\\p{N} ']")

    /* The suffix -iĉ- of the reform, before an ending */
    private val REFORM = Regex("iĉ(?=[oae]j?n?$)")

    /* Whether the word, after its article, is a name */
    private fun capital(word: String) =
        word.trim().replace(Regex("^(?i)(le |la |les |l'|l’)"), "").firstOrNull()?.isUpperCase() == true

    /** The French words that Wiktionary gives for an example of Reta
     * Vortaro that has no French there, "bonvolu" ("s'il vous plaît"),
     * and that [revo], the French of its word, doesn't already say. Disk
     * access: not on the main thread. */
    fun frenchForExample(context: Context, example: String, revo: String?): List<String> {
        val data = data(context)
        val plain = example.trim().trimEnd('!', '?', '.', ' ')
        return frenchFor(data.frenchOf[example].orEmpty() + data.frenchOf[plain].orEmpty(), revo)
    }

    /** The French words that Wiktionary translates by an Esperanto word */
    fun frenchOf(context: Context, word: String): List<String> =
        data(context).frenchOf[word].orEmpty().distinctBy { key(it) }

    /** Wiktionary's French description of an Esperanto word, if it says
     * more than the French words translated by it. Disk access: not on the
     * main thread. */
    fun description(context: Context, word: String): String? = informative(data(context), word)

    /* Two descriptions in three only repeat a French word: "spicisto",
     * "Épicier." */
    private fun informative(data: Data, word: String): String? {
        val description = data.descriptions[word] ?: return null
        val french = data.frenchOf[word].orEmpty().map { key(it) }
        /* Nor the French words again, in any order: "Acidité, aigreur." */
        return description.takeUnless { key(it.trimEnd('.')).let { plain -> plain in french || plain.split(", ").toSet() == french.toSet() } }
    }
}
