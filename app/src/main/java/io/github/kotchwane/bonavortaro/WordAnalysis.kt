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

import java.util.Locale

/** A piece of a word: as it is written in the word, and the entry of the
 * dictionary that explains it ("ul" → "ulo"), if any. */
class WordPiece(val text: String, val kind: Kind, val entry: String?) {
    enum class Kind { PREFIX, ROOT, SUFFIX, LINK, ENDING }
}

/** How a word missing from the dictionary may be built: its pieces, and
 * the end of it that couldn't be recognised ("" if none). */
class WordAnalysis(val pieces: List<WordPiece>, val unrecognised: String) {
    val complete get() = unrecognised.isEmpty()
}

/**
 * Cuts a word missing from the dictionary into the roots and affixes it
 * may be built from: "kafejestro" → kafej·estr·o, from "kafejo" and "estro".
 *
 * A root is a piece that the dictionary has as a word with an ending
 * ("hund" for "hundo"), so that each piece has an entry to open. The
 * likeliest cut has the fewest pieces: a derived word of the dictionary
 * counts as one ("sentem·ul·o", from "sentema", rather than
 * "sent·em·ul·o"). It is a guess, not a definition.
 */
object WordAnalyser {
    /** [entryOf] gives the entry of the dictionary written exactly so
     * ("hundo", "-ej", "mal-"), or null; it is asked each form once. */
    fun analyse(
        word: String,
        entryOf: (String) -> String?,
        best: Int = 2,
        /** A word taken from a text is complete: without an ending, or with
         * an end that can't be recognised, it isn't Esperanto ("budapest",
         * "effektenbrse"); a word being typed may lack them */
        needsEnding: Boolean = false,
    ): List<WordAnalysis> {
        val lower = word.lowercase(Locale.ROOT)
        /* Esperanto only: no q, w, x, y ("cx" is already "ĉ") */
        if (lower.length < 4 || lower.length > MAX_LENGTH || !lower.all { it in ALPHABET })
            return emptyList()

        /* Each form looked up once, even when it isn't in the dictionary,
         * which most aren't */
        val known = HashMap<String, String?>()
        val entry = { form: String ->
            if (form in known) known[form] else entryOf(form).also { known[form] = it }
        }

        /* The grammar at the end, as Esperanto allows it: -j after -o or -a,
         * -n after -o, -a, -e or -j; then the ending. Not "hundin" read as
         * hund·i·n, nor "kafej" as kaf·e·j */
        var stem = lower
        val tail = mutableListOf<String>()
        if (stem.endsWith("n") && stem.length > 3 && stem[stem.lastIndex - 1] in "oaej") {
            tail.add(0, "n")
            stem = stem.dropLast(1)
        }
        if (stem.endsWith("j") && stem.length > 3 && stem[stem.lastIndex - 1] in "oa") {
            tail.add(0, "j")
            stem = stem.dropLast(1)
        }
        val ending = ENDINGS.firstOrNull { stem.endsWith(it) && stem.length > it.length + 1 }
            /* a -j or -n needs the ending of a noun or an adjective */
            ?.takeIf { tail.isEmpty() || it in WORD_ENDINGS }

        val readings = mutableListOf<List<WordAnalysis>>()
        if (ending != null)
            readings.add(analyseStem(lower, stem.dropLast(ending.length), (listOf(ending) + tail).map {
                WordPiece(it, WordPiece.Kind.ENDING, null)
            }, entry, best, needsEnding))
        /* A word being typed may not have its ending yet: "hundin", "malsan"
         * read whole, if the reading with an ending says nothing complete */
        if (!needsEnding && readings.none { it.firstOrNull()?.complete == true })
            readings.add(analyseStem(lower, lower, emptyList(), entry, best, needsEnding))
        return readings.firstOrNull { it.firstOrNull()?.complete == true } ?: readings.firstOrNull { it.isNotEmpty() }.orEmpty()
    }

    /** The readings of a stem, with the ending pieces after it */
    private fun analyseStem(
        lower: String,
        stem: String,
        endingPieces: List<WordPiece>,
        entry: (String) -> String?,
        best: Int,
        needsEnding: Boolean,
    ): List<WordAnalysis> {
        /* No real word has more: in the examples of the dictionary, at most
         * 2 prefixes, 3 roots (not counting the numbers: du·dek·kvin·jara)
         * and 5 pieces. Beyond, a game or jargon cut into pieces
         * (hund·kat·bird·fiŝ·arb·dom·libro, klor·op·er·ok·sob·en·zo·ato) */
        val cuts = Segmenter(stem, entry).cuts()
        /* The likeliest cut within; one far from the likeliest of all would
         * be stranger than saying nothing (hund·kat·bird·fiŝ·arb·dom·libro
         * has none near), but a near one is often the right one: tri·dek·
         * unu·jar·aĝ·ulo, after tri·de·kun·uj·ar·aĝ·ulo */
        val likeliest = cuts.firstOrNull() ?: return emptyList()
        val first = cuts.firstOrNull { it.plausible() }?.takeIf { it.cost <= likeliest.cost + PLAUSIBLE_NEAR }
            ?: return emptyList()
        /* Too dear for its length, made of letter names and joining vowels
         * ("ap·ze·o·ze·i"): better say nothing. A good cut costs about 3 a
         * letter (hund·aĉ·o: 18 for its 6) */
        if (first.cost > MAX_COST_PER_LETTER * first.end.coerceAtLeast(1))
            return emptyList()
        /* A beginning only if it is most of the word: not "mal" for a word
         * of twenty random letters */
        if (first.end < stem.length && (needsEnding || first.end < 2 * (lower.length - first.end)))
            return emptyList()
        /* A second cut only if it is nearly as likely, and really another
         * one: not the first cut again, nor the same cut more finely
         * (kafej·estr·o, kaf·ej·estr·o) or more coarsely
         * (malakcept·ind·ec·o, malakcept·indec·o) */
        val likely = listOf(first) + cuts.filter { it !== first }.filter {
            it.cost <= first.cost + CLOSE && it.plausible() && !it.refines(first) && !first.refines(it)
        }.distinctBy { cut -> cut.pieces.map { it.text } }.take(1)
        return likely
            .map {
                if (it.end == stem.length) WordAnalysis(it.pieces + endingPieces, "")
                /* What couldn't be recognised, as typed: its ending too */
                else WordAnalysis(it.pieces, lower.substring(it.end))
            }
            .take(best)
    }

    /** The cuts of a stem, the likeliest first: complete ones if any,
     * else the longest beginning that can be cut. */
    private class Segmenter(val stem: String, val entry: (String) -> String?) {
        /* The best cuts of stem[i..], by what came before: whether a root
         * was met, and whether the last piece was a prefix */
        private val memo = HashMap<Triple<Int, Boolean, Boolean>, List<Cut>>()

        class Cut(val cost: Int, val pieces: List<WordPiece>)

        class Found(val pieces: List<WordPiece>, val end: Int, val cost: Int) {
            /** Where the pieces end in the stem */
            private val bounds get() = pieces.runningFold(0) { at, piece -> at + piece.text.length }.toSet()

            /** Whether it cuts the stem at least where [other] does, the
             * same cut or a finer one */
            fun refines(other: Found) = bounds.containsAll(other.bounds)

            /** Within what real words have */
            fun plausible(): Boolean {
                /* The numbers, however many, are one number: tri·dek·unu */
                val shown = pieces.filter { it.kind != WordPiece.Kind.LINK && it.text !in NUMBERS }
                return shown.count { it.kind == WordPiece.Kind.PREFIX } <= MAX_PREFIXES &&
                    shown.count { it.kind == WordPiece.Kind.ROOT } <= MAX_ROOTS &&
                    shown.size <= MAX_PIECES
            }
        }

        fun cuts(): List<Found> {
            val complete = from(0, rootSeen = false, afterPrefix = false)
            if (complete.isNotEmpty())
                return complete.map { Found(it.pieces, stem.length, it.cost) }
            /* Nothing complete: the longest beginning that can be cut */
            for (end in stem.length - 1 downTo 2) {
                val part = Segmenter(stem.substring(0, end), entry).from(0, rootSeen = false, afterPrefix = false)
                if (part.isNotEmpty())
                    return listOf(Found(part.first().pieces, end, part.first().cost))
            }
            return emptyList()
        }

        fun from(i: Int, rootSeen: Boolean, afterPrefix: Boolean): List<Cut> = memo.getOrPut(Triple(i, rootSeen, afterPrefix)) {
            if (i == stem.length)
                return@getOrPut if (rootSeen && !afterPrefix) listOf(Cut(0, emptyList())) else emptyList()
            val found = mutableListOf<Cut>()
            for (j in i + 1..stem.length) {
                val text = stem.substring(i, j)
                for ((piece, cost) in readings(text, rootSeen, afterPrefix)) {
                    val isRoot = piece.kind == WordPiece.Kind.ROOT
                    for (rest in from(j, rootSeen || isRoot, piece.kind == WordPiece.Kind.PREFIX))
                        found.add(Cut(cost + rest.cost, listOf(piece) + rest.pieces))
                    /* The -o- between the roots of a compound: akv·o·botelo;
                     * and a vowel that some put between a root and a suffix,
                     * after their own language: negoc·i·ebla (négociable),
                     * where the dictionary has "negocebla" */
                    if (isRoot && j < stem.length && stem[j] in LINKS)
                        for (rest in from(j + 1, true, false)) {
                            val next = rest.pieces.firstOrNull()?.kind
                            /* Between two roots, the -o-; another vowel only
                             * between real roots ("dub·e·flava", "propr·a·sine"),
                             * not between letter names ("ti·a·aĵo", "ap·ze·o·ze·i") */
                            val nextText = rest.pieces.firstOrNull()?.text.orEmpty()
                            val linkCost = when {
                                next == WordPiece.Kind.ROOT && stem[j] == 'o' -> LINK_COST
                                next == WordPiece.Kind.SUFFIX -> LOOSE_LINK_COST
                                next == WordPiece.Kind.ROOT && text.length >= 3 && nextText.length >= 3 -> ROOT_LINK_COST
                                else -> continue
                            }
                            found.add(Cut(cost + linkCost + rest.cost, listOf(piece, WordPiece(stem[j].toString(), WordPiece.Kind.LINK, null)) + rest.pieces))
                        }
                }
            }
            found.sortedBy { it.cost }.take(KEPT)
        }

        /** What a piece can be here, with its cost */
        private fun readings(text: String, rootSeen: Boolean, afterPrefix: Boolean): List<Pair<WordPiece, Int>> {
            val readings = mutableListOf<Pair<WordPiece, Int>>()
            /* A prefix only before what it modifies: those the dictionary
             * writes so ("tele-", "mikro-"), and the prepositions. Of two
             * letters, only Esperanto's own: not "di-" of chemistry, which
             * made "ne·di·finit·eco" of "nedifiniteco" */
            if (text.length >= 3 || text in PREFIXES || text in PREPOSITIONS)
                (entry("$text-") ?: if (text in PREFIXES || text in PREPOSITIONS) entry(text) else null)
                    ?.let { readings.add(WordPiece(text, WordPiece.Kind.PREFIX, it) to PIECE_COST) }
            /* A suffix only after a root */
            if (text in SUFFIXES && rootSeen && !afterPrefix)
                suffixEntry(text)?.let {
                    /* The rare ones dearer: re·vid·o, not rev·id·o */
                    val cost = if (text in RARE_SUFFIXES) PIECE_COST + RARE_SUFFIX_COST else SUFFIX_COST
                    readings.add(WordPiece(text, WordPiece.Kind.SUFFIX, it) to cost)
                }
            /* "ĉiu·jara", "sia·parte", "du·loka": words that are their own root */
            val asItIs = if (text in WHOLE_WORDS) entry(text) else null
            /* A suffix is a root too where none came before: estr·ad·o */
            if (text.length >= 2 && (text !in SUFFIXES || !rootSeen))
                (asItIs ?: WORD_ENDINGS.firstNotNullOfOrNull { entry(text + it) })?.let {
                    var cost = PIECE_COST
                    if (text.length <= 2 && text !in SHORT_ROOTS && asItIs == null) cost += SHORT_ROOT_COST
                    /* A second root, even after a prefix: a compound; but
                     * not a number, after a root that is one or not:
                     * tri·dek·unu is one number, tri·dek·unu·jara one
                     * compound */
                    if (rootSeen && text !in NUMBERS) cost += COMPOUND_COST
                    readings.add(WordPiece(text, WordPiece.Kind.ROOT, it) to cost)
                }
            return readings
        }

        /** The suffix's own word when it says the same thing ("ulo":
         * "Estaĵo, individuo"), else the suffix itself ("-ej") */
        private fun suffixEntry(text: String): String? =
            OWN_WORDS[text]?.let { entry(it) } ?: entry("-$text")
    }

    private const val MAX_LENGTH = 40
    private const val KEPT = 4
    private const val PIECE_COST = 10
    /* An affix rather than a root, when both fit: pork·aĉ, not por·kaĉ */
    private const val SUFFIX_COST = 8
    private const val RARE_SUFFIX_COST = 4
    private val RARE_SUFFIXES = setOf("id", "on", "op", "um", "ing", "obl", "end")
    /* "el" in "akvobotelo" would be the letter L, "elo" */
    private const val SHORT_ROOT_COST = 20
    private const val CLOSE = 4
    private const val MAX_COST_PER_LETTER = 6
    private const val MAX_PREFIXES = 2
    private const val MAX_ROOTS = 3
    private const val MAX_PIECES = 5
    private const val PLAUSIBLE_NEAR = 10
    private val NUMBERS = setOf("unu", "du", "tri", "kvar", "kvin", "ses", "sep", "ok", "naŭ", "dek", "cent", "mil")
    private const val COMPOUND_COST = 6
    private const val LINK_COST = 2
    /* Another vowel, or one before a suffix: possible, not usual */
    private const val LOOSE_LINK_COST = 6
    private const val ROOT_LINK_COST = 8
    private val LINKS = setOf('o', 'a', 'e', 'i')

    /* Real roots of two letters, unlike the names of the letters ("elo",
     * "zeo") and the particles that the other short stems come from; not
     * "as", "av", "te", "or" either (aso, avo, teo, oro): rare in compounds,
     * frequent in foreign words and names ("avoir", "terima", "Orlando") */
    private val SHORT_ROOTS = setOf(
        "aĝ", "am", "ir", "ov", "uz", "ag", "ŝu", "ul", "du", "ok", "lu", "ĝu", "di", "eĥ",
    )
    /* Words with no ending of their own, used as the first part of others */
    private val WHOLE_WORDS = setOf(
        "ĉiu", "ĉia", "ĉie", "ĉiam", "ĉies", "tiu", "tia", "tie", "tiam", "ties", "iu", "ia", "ie", "iam", "ies",
        "kiu", "kia", "kie", "neniu", "nenia", "mia", "via", "sia", "lia", "ŝia", "ĝia", "nia", "ilia",
        "unu", "du", "tri", "kvar", "kvin", "ses", "sep", "ok", "naŭ", "dek", "cent", "mil", "pli", "plej", "tro",
    )
    private val ALPHABET = "abcĉdefgĝhĥijĵklmnoprsŝtuŭvz".toSet()
    private val ENDINGS = listOf("as", "is", "os", "us", "u", "o", "a", "e", "i")
    private val WORD_ENDINGS = listOf("o", "a", "i", "e")
    private val PREFIXES = setOf("mal", "re", "ek", "ge", "dis", "mis", "eks", "for", "pra", "bo", "fi", "ne", "vic", "et", "po")
    private val PREPOSITIONS = setOf(
        "al", "ĉe", "de", "el", "en", "kun", "por", "pri", "sen", "sub", "sur", "super", "tra", "trans", "inter",
        "kontraŭ", "antaŭ", "post", "preter", "ĉirkaŭ", "apud", "dum", "laŭ", "pli",
    )
    private val SUFFIXES = setOf(
        "ul", "ig", "iĝ", "ebl", "ej", "in", "ist", "et", "eg", "ar", "ec", "ad", "aĵ", "an", "em", "end", "ind",
        "il", "estr", "aĉ", "id", "ing", "ism", "obl", "on", "op", "uj", "um", "er", "ĉj", "nj",
        "ant", "int", "ont", "at", "it", "ot",
    )
    /* Suffixes whose own word explains them alone, written out: "eta", not
     * "eto", a Greek letter; not "igi", which is defined by the suffix */
    private val OWN_WORDS = mapOf(
        "ul" to "ulo", "in" to "ino", "ebl" to "ebla", "em" to "ema", "estr" to "estro", "et" to "eta",
        "eg" to "ega", "ar" to "aro", "ec" to "eco", "aĵ" to "aĵo", "an" to "ano", "ist" to "isto", "il" to "ilo",
        "ind" to "inda", "end" to "enda", "aĉ" to "aĉa", "id" to "ido", "uj" to "ujo", "ism" to "ismo",
        "er" to "ero", "obl" to "obla", "on" to "ono",
    )
}

/** The entries of the index of Esperanto, looked up by how they are
 * written ("hundo", "-ej", "mal-"). Nothing is kept from one word to the
 * next: the analysis keeps what it looked up for its word, and a cache
 * here would grow all the time the app is open. Not thread-safe. */
class IndexEntries(
    private val trie: Trie,
    /** The officiality of the root of an article (0 for the Fundamento),
     * null if it has none */
    private val articleLevel: (Int) -> Int? = { null },
) : (String) -> String? {
    private val results = arrayOfNulls<SearchResult>(16)

    /** The entry written exactly so, if the index has it; of two written
     * alike, the official one, the oldest: "koro" the heart (Fundamento),
     * not the measure of volume */
    fun result(form: String): SearchResult? {
        /* The index leaves out the hyphens of "-ej" and "mal-", and gives
         * the words of the node first */
        val count = trie.search(form.replace("-", ""), results)
        return results.take(count).filterNotNull().filter { it.word.lowercase(Locale.ROOT) == form }
            .minByOrNull { articleLevel(it.article) ?: Int.MAX_VALUE }
    }

    override fun invoke(form: String): String? = result(form)?.let { form }
}

fun indexEntries(trie: Trie) = IndexEntries(trie)
