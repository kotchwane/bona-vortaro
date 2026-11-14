/*
 * Bona vortaro - an Esperanto dictionary for Android, based on PReVo
 * Copyright (C) 2012  Neil Roberts
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

import java.io.IOException
import java.io.InputStream
import java.text.Normalizer
import java.util.Locale
import java.util.TreeMap

/**
 * A search index, searched in the compact form the app ships
 * (prevodb/src/pdb-compact.h): the words of the language in order, each
 * written as the bytes it shares with the one before and the rest, with
 * its articles.
 *
 * The searches still think in the tree of letters prevodb builds, the
 * trie: a node is a start of words, "cha", and its children the letters
 * that follow it, "chab…" to "chau…". In the list, a node is the run of
 * words that begin with it, as the words are in the order of the tree:
 * the words of "cha" follow each other, those of "chat" among them.
 *
 * The words are in the order of prevodb's letters (pdb-strcmp.c): by
 * code point, each Esperanto letter with a hat right after the same
 * without, "ĥ" between "h" and "i"; the keys are all in lower case. So
 * the run of a start of words is found by a binary search. To search
 * without reading the words from the first one, the key of every
 * BLOCK-th word is kept whole when the index is loaded: a word is read
 * from the key before it.
 *
 * The loops on the bytes are written with plain arrays and while loops,
 * as they run on every keystroke.
 */
class Trie @Throws(IOException::class) constructor(dataStream: InputStream) {
    private val data: ByteArray = dataStream.readBytes()
    private val wordCount: Int

    /* Where each display name of the table is in data: its length, then
     * its UTF-8 */
    private val tableAt: IntArray

    /* Where the record of the first word of each block is in data, and its
     * whole key, in keys from keyAt[block] to keyAt[block + 1] */
    private val blockAt: IntArray
    private val keyAt: IntArray
    private val keys: ByteArray

    /* The letters that begin the words, and the first word of each: in
     * Chinese and Japanese, thousands, which every search would otherwise
     * look for again */
    private val firstLetterAt: IntArray
    private val firstLetters: IntArray

    /** A start of words: the words from lo to hi (excluded) begin with the
     * first depth bytes of the word lo; letter is its last letter */
    private class Node(val lo: Int, val hi: Int, val depth: Int, val letter: String) {
        /** Whether the words of 'other' begin with this node: in the tree,
         * whether it's this node or below it */
        fun contains(other: Node): Boolean =
            lo <= other.lo && other.hi <= hi && depth <= other.depth
    }

    init {
        if (data.size < 4 || (0 until 4).any { data[it] != MAGIC[it] })
            throw IOException("Not a compact index")

        /* Reads the numbers of the header and the words, checking them */
        var at = 4
        fun varint(): Long {
            var value = 0L
            var shift = 0
            while (shift < 64) {
                if (at >= data.size)
                    throw IOException("Truncated compact index")
                val b = data[at++].toInt() and 0xff
                value = value or ((b and 0x7f).toLong() shl shift)
                if (b and 0x80 == 0)
                    return value
                shift += 7
            }
            throw IOException("Invalid number in compact index")
        }
        fun count(): Int {
            val value = varint()
            if (value < 0 || value > data.size)
                throw IOException("Invalid length in compact index")
            return value.toInt()
        }

        /* The size of the trie the index was made from, and the root's
         * letter: not needed to search */
        varint()
        val rootLength = count()
        at += rootLength

        val tableSize = count()
        tableAt = IntArray(tableSize)
        for (i in 0 until tableSize) {
            tableAt[i] = at
            val length = count()
            at += length
            if (at > data.size)
                throw IOException("Truncated compact index")
        }

        wordCount = count()
        val blocks = (wordCount + BLOCK - 1) / BLOCK
        blockAt = IntArray(blocks)
        keyAt = IntArray(blocks + 1)
        val blockKeys = java.io.ByteArrayOutputStream()

        /* Read all the words once, to keep the key of each block, and to
         * check the index: the searches then trust it */
        var path = ByteArray(256)
        var pathLength = 0
        var letterAt = IntArray(64)
        var letters = IntArray(64)
        var letterCount = 0
        for (w in 0 until wordCount) {
            val record = at
            val shared = count()
            val rest = count()
            /* matchPattern keeps the length of a key in 16 bits */
            if (shared > pathLength || at + rest > data.size || shared + rest >= 0x10000)
                throw IOException("Invalid word in compact index")
            if (shared + rest > path.size)
                path = path.copyOf(maxOf(path.size * 2, shared + rest))

            /* The binary searches need the words in order: after the word
             * before, at the first letter they don't share */
            var letter = shared
            while (letter > 0 && letter < pathLength && (path[letter].toInt() and 0xc0) == 0x80)
                letter--
            val before = if (letter < pathLength) orderValue(codePoint(path, letter)) else -1

            System.arraycopy(data, at, path, shared, rest)
            at += rest
            pathLength = shared + rest

            if (w > 0 && (letter >= pathLength || before >= orderValue(codePoint(path, letter))))
                throw IOException("The words of the compact index aren't in order")

            if (pathLength > 0 && (letterCount == 0 || shared < utf8Length(path[0]))) {
                if (letterCount == letters.size) {
                    letterAt = letterAt.copyOf(letterCount * 2)
                    letters = letters.copyOf(letterCount * 2)
                }
                letterAt[letterCount] = w
                letters[letterCount++] = codePoint(path, 0)
            }

            if (w % BLOCK == 0) {
                blockAt[w / BLOCK] = record
                keyAt[w / BLOCK] = blockKeys.size()
                blockKeys.write(path, 0, pathLength)
            }

            val articles = count()
            if (articles == 0)
                throw IOException("A word without articles in compact index")
            for (j in 0 until articles) {
                varint()
                if (at >= data.size)
                    throw IOException("Truncated compact index")
                at++
                val code = varint()
                if (code == 1L)
                    varint()
                else if (code >= 2 && code - 2 >= tableSize)
                    throw IOException("Invalid display name in compact index")
            }
        }
        keyAt[blocks] = blockKeys.size()
        keys = blockKeys.toByteArray()
        firstLetterAt = letterAt.copyOf(letterCount)
        firstLetters = letters.copyOf(letterCount)

        if (at != data.size)
            throw IOException("Trailing data in compact index")
    }

    /** Reads the numbers of data from a place; the index was checked when
     * it was loaded */
    private inner class Reader {
        var at = 0

        fun count(): Int {
            var value = 0
            var shift = 0
            while (true) {
                val b = data[at++].toInt() and 0xff
                value = value or ((b and 0x7f) shl shift)
                if (b and 0x80 == 0)
                    return value
                shift += 7
            }
        }

        fun long(): Long {
            var value = 0L
            var shift = 0
            while (true) {
                val b = data[at++].toInt() and 0xff
                value = value or ((b and 0x7f).toLong() shl shift)
                if (b and 0x80 == 0)
                    return value
                shift += 7
            }
        }

        fun byte(): Int = data[at++].toInt() and 0xff

        /** Goes past the articles of a word */
        fun skipArticles(articles: Int) {
            for (j in 0 until articles) {
                count()
                at++
                if (count() == 1)
                    long()
            }
        }
    }

    /** Reads the words one after the other, from any of them */
    private inner class Cursor {
        /** The word read, -1 before the first */
        var index = -1

        /** Its key, and the number of bytes it shares with the word before */
        var path = ByteArray(256)
        var length = 0
        var shared = 0

        /** Its number of articles, where they start, and where the next word
         * starts */
        var articles = 0
        var articlesAt = 0
        var nextAt = 0

        private val reader = Reader()
        private val nameReader = Reader()

        /** Reads the word at 'record', the key before it in path */
        fun read(record: Int) {
            val r = reader
            r.at = record
            shared = r.count()
            val rest = r.count()
            if (shared + rest > path.size)
                path = path.copyOf(maxOf(path.size * 2, shared + rest))
            System.arraycopy(data, r.at, path, shared, rest)
            r.at += rest
            length = shared + rest
            articles = r.count()
            articlesAt = r.at
            r.skipArticles(articles)
            nextAt = r.at
            index++
        }

        fun next() = read(nextAt)

        /** Goes to the word i */
        fun at(i: Int) {
            if (index < 0 || i < index || i - index > BLOCK) {
                val block = i / BLOCK
                val keyLength = keyAt[block + 1] - keyAt[block]
                if (keyLength > path.size)
                    path = ByteArray(maxOf(path.size * 2, keyLength))
                /* The bytes the first word shares with the one before are
                 * already the start of its key */
                System.arraycopy(keys, keyAt[block], path, 0, keyLength)
                index = block * BLOCK - 1
                read(blockAt[block])
            }
            while (index < i)
                next()
        }

        /** Whether the word is past the words that begin with 'start'
         * (exclusive), or at or past their first one */
        private fun past(
            key: ByteArray, offset: Int, keyLength: Int,
            start: ByteArray, startLength: Int, exclusive: Boolean,
        ): Boolean {
            val order = compareStart(key, offset, keyLength, start, startLength)
            return if (exclusive) order > 0 else order >= 0
        }

        /** The first word from lo to hi (or hi) that is past the words that
         * begin with 'start', or at or past their first one */
        fun first(lo: Int, hi: Int, start: ByteArray, startLength: Int, exclusive: Boolean): Int {
            if (lo >= hi)
                return hi

            /* The first block, among those that start after lo and before
             * hi, whose first word is past */
            val firstBlock = lo / BLOCK + 1
            val lastBlock = (hi - 1) / BLOCK
            var a = firstBlock
            var z = lastBlock + 1
            while (a < z) {
                val m = (a + z) ushr 1
                if (past(keys, keyAt[m], keyAt[m + 1] - keyAt[m], start, startLength, exclusive))
                    z = m
                else
                    a = m + 1
            }

            /* The word is after the start of the block before, and at most
             * the first of that block */
            val from = if (a == firstBlock) lo else (a - 1) * BLOCK
            val to = if (a <= lastBlock) a * BLOCK else hi
            var i = from
            while (i < to) {
                at(i)
                if (past(path, 0, length, start, startLength, exclusive))
                    return i
                i++
            }
            return to
        }

        /** The word after the run of those that begin as the word 'i' does in
         * its first 'depth' bytes, before hi */
        fun endOfRun(i: Int, hi: Int, depth: Int): Int {
            at(i)
            /* Most runs are short: read on while the words share the start,
             * only the bytes they share and the length of the rest. The key
             * of i stays in path: the first word past the run shares less
             * than depth bytes with the word before, all of them in path */
            val r = reader
            r.at = nextAt
            var j = i + 1
            while (j < hi && j <= i + SCAN) {
                val record = r.at
                if (r.count() < depth) {
                    index = j - 1
                    read(record)
                    return j
                }
                val rest = r.count()
                r.at += rest
                r.skipArticles(r.count())
                j++
            }
            if (i + SCAN >= hi - 1)
                return hi
            return first(i, hi, path.copyOf(depth), depth, true)
        }

        /** The letters that follow a node, each the node of the words that
         * begin with it, in the order of the index */
        fun children(node: Node): List<Node> {
            val children = ArrayList<Node>()
            /* The root's, found when loading */
            if (node.depth == 0) {
                for (i in firstLetterAt.indices) {
                    val letter = String(Character.toChars(firstLetters[i]))
                    val end = if (i + 1 < firstLetterAt.size) firstLetterAt[i + 1] else wordCount
                    children.add(Node(firstLetterAt[i], end, utf8LengthOf(firstLetters[i]), letter))
                }
                return children
            }
            var i = node.lo
            at(i)
            if (length == node.depth)
                i++
            while (i < node.hi) {
                at(i)
                val letterLength = utf8Length(path[node.depth])
                val depth = node.depth + letterLength
                val letter = String(path, node.depth, letterLength, Charsets.UTF_8)
                val end = endOfRun(i, node.hi, depth)
                children.add(Node(i, end, depth, letter))
                i = end
            }
            return children
        }

        /** The key of the word read, with the letters of the mask as
         * capitals */
        private fun withCapitals(mask: Long): String {
            val key = key()
            val name = StringBuilder(key.length)
            var letter = 0
            var at = 0
            while (at < key.length) {
                var c = key.codePointAt(at)
                at += Character.charCount(c)
                if (letter < 64 && ((mask ushr letter) and 1L) != 0L)
                    c = Character.toUpperCase(c)
                name.appendCodePoint(c)
                letter++
            }
            return name.toString()
        }

        /** Adds the articles of the word read, shown as 'word' when they
         * have no display name of their own */
        fun addArticles(word: String, results: Array<SearchResult?>, numResults: Int): Int {
            var n = numResults
            val r = reader
            r.at = articlesAt
            var j = 0
            while (j < articles && n < results.size) {
                val article = r.count()
                val mark = r.byte()
                val code = r.count()

                val shown = when {
                    code == 1 -> withCapitals(r.long())
                    code >= 2 -> {
                        nameReader.at = tableAt[code - 2]
                        val length = nameReader.count()
                        String(data, nameReader.at, length, Charsets.UTF_8)
                    }
                    else -> word
                }

                results[n++] = SearchResult.fromIndex(shown, article, mark)
                j++
            }
            return n
        }

        fun key(): String = String(path, 0, length, Charsets.UTF_8)
    }

    private fun root() = Node(0, wordCount, 0, "")

    /**
     * Searches the trie for words that begin with 'prefix'. The results
     * array is filled with the results. If more results are available
     * than the length of the results array then they are ignored. If
     * less are available then the remainder of the array is untouched.
     * Returns the number of results found.
     */
    fun search(prefix: String, results: Array<SearchResult?>): Int {
        val start = prefix.toByteArray(Charsets.UTF_8)
        val cursor = Cursor()

        /* The words that begin with it follow each other, in the order of
         * the tree */
        val lo = cursor.first(0, wordCount, start, start.size, false)
        val hi = cursor.first(lo, wordCount, start, start.size, true)

        var numResults = 0
        var i = lo
        while (i < hi && numResults < results.size) {
            cursor.at(i)
            numResults = cursor.addArticles(cursor.key(), results, numResults)
            i++
        }
        return numResults
    }

    /**
     * Finds the nodes whose path from the root matches the rest of the
     * prefix, from prefixOffset, ignoring the accents. Only the letters
     * typed without an accent match the accented ones: "e" matches "é",
     * but "é" only matches "é", and "ĉ" (or "cx") doesn't match "c". Each
     * node is added to the nodes with its path, the string with the
     * accents of the index.
     */
    private fun findIgnoringAccents(
        cursor: Cursor,
        node: Node,
        prefix: String,
        prefixOffset: Int,
        path: StringBuilder,
        nodes: MutableList<Node>,
        paths: MutableList<String>,
    ) {
        if (prefixOffset >= prefix.length) {
            nodes.add(node)
            paths.add(path.toString())
            return
        }

        val letterEnd = prefix.offsetByCodePoints(prefixOffset, 1)
        val typed = prefix.substring(prefixOffset, letterEnd).lowercase(Locale.ROOT)
        val letter = foldLetter(typed)
        val plain = letter == typed

        for (child in cursor.children(node)) {
            val childLetter = child.letter

            if ((if (plain) foldLetter(childLetter) == letter
                 else childLetter.lowercase(Locale.ROOT) == typed) ||
                sameKana(childLetter, typed)) {
                val oldLength = path.length
                path.append(childLetter)
                findIgnoringAccents(cursor, child, prefix, letterEnd, path, nodes, paths)
                path.setLength(oldLength)
            }
            /* "oe" typed also finds "œ", and "ae" "æ": "coeur" finds "cœur";
             * an "o" at the end of what is typed, already "œ". The German
             * letters too: "ue" finds "ü" ("Mueller" Müller), "ae" "ä", "oe"
             * "ö", and "ss" "ß" ("Strasse" Straße), with an "s" at the end
             * already "ß"; an "u" alone finds "ü" above */
            if (plain) {
                val ligature = childLetter.lowercase(Locale.ROOT)
                val pair = LETTER_PAIRS[ligature]
                if (pair != null && letter == pair.substring(0, 1)) {
                    val umlaut = UMLAUTS.indexOf(ligature) >= 0
                    var pairEnd = letterEnd
                    if (letterEnd < prefix.length &&
                        prefix.substring(letterEnd, prefix.offsetByCodePoints(letterEnd, 1))
                            .lowercase(Locale.ROOT) == pair.substring(1))
                        pairEnd = prefix.offsetByCodePoints(letterEnd, 1)
                    else if (letterEnd < prefix.length || umlaut)
                        continue

                    val oldLength = path.length
                    path.append(childLetter)
                    findIgnoringAccents(cursor, child, prefix, pairEnd, path, nodes, paths)
                    path.setLength(oldLength)
                }
            }
        }
    }

    /**
     * Like search, but ignoring the accents left out: "eleve" also finds
     * "élève", and "cevalo" "ĉevalo", but "ĉevalo" doesn't find "cevalo".
     * The words are in alphabetical order, accents and case aside, as in a
     * printed dictionary: "com" finds "comédie" between "combustible" and
     * "comique", where the order of the index put it after "comme", beyond
     * the results kept. A word without accent comes before the same with
     * ("cote", "coté", "côte", "côté"), so the words that match the prefix
     * exactly come first. In Esperanto, ĉ ĝ ĥ ĵ ŝ ŭ are letters of their
     * own, after c g h j s u.
     */
    @JvmOverloads
    fun searchIgnoringAccents(
        prefix: String,
        results: Array<SearchResult?>,
        esperanto: Boolean = false,
    ): Int {
        val cursor = Cursor()
        val nodes = ArrayList<Node>()
        val paths = ArrayList<String>()

        findIgnoringAccents(cursor, root(), prefix, 0, StringBuilder(), nodes, paths)
        keepOutermost(nodes, paths)

        /* The nodes reached, in groups that are the same in the order, eg.
         * "élève" and "eleve" */
        val start = TreeMap<String, Group>()
        val typed = foldLetter(prefix).length
        for (i in nodes.indices) {
            val path = paths[i]
            val key = StringBuilder()
            var at = 0
            while (at < path.length) {
                val next = path.offsetByCodePoints(at, 1)
                key.append(orderOf(path.substring(at, next), esperanto, path.substring(0, at)))
                at = next
            }
            /* "co" reaches "cœ", whose "e" is still to come in the order */
            if (!esperanto && key.length > typed)
                groupIn(start, key.substring(0, typed)).add(nodes[i], path, key.substring(typed))
            else
                groupIn(start, key.toString()).add(nodes[i], path, "")
        }

        val stack = java.util.ArrayDeque<Group>()
        pushInOrder(stack, start)

        var numResults = 0

        /* Depth first, each group of letters in order */
        while (numResults < results.size && !stack.isEmpty()) {
            val group = stack.pop()
            group.sort()

            for (i in group.nodes.indices)
                if (group.pending[i].isEmpty())
                    numResults = addWordsExactFirst(cursor, group.nodes[i], group.paths[i], results, numResults)

            val children = TreeMap<String, Group>()
            for (i in group.nodes.indices) {
                val node = group.nodes[i]
                val pending = group.pending[i]

                /* A ligature's second letter, in the order: "œ" after "o"
                 * among the "e", "chœur" between "choéphore" and "chômer" */
                if (pending.isNotEmpty()) {
                    groupIn(children, pending.substring(0, 1)).add(node, group.paths[i], pending.substring(1))
                    continue
                }

                for (child in cursor.children(node)) {
                    val letter = child.letter
                    var key = orderOf(letter, esperanto, group.paths[i])
                    var rest = ""
                    if (!esperanto && key.length > 1) {
                        rest = key.substring(1)
                        key = key.substring(0, 1)
                    }
                    groupIn(children, key).add(child, group.paths[i] + letter, rest)
                }
            }
            pushInOrder(stack, children)
        }

        return numResults
    }

    /** Nodes reached by paths that are the same in the order of the
     * results, accents aside */
    private class Group {
        val nodes = ArrayList<Node>()
        val paths = ArrayList<String>()

        /** The letters of the order a node is still ahead of: the "e" of
         * "œ" */
        val pending = ArrayList<String>()

        fun add(node: Node, path: String, rest: String) {
            nodes.add(node)
            paths.add(path)
            pending.add(rest)
        }

        /** Without accent first: "cote", "coté", "côte", "côté" */
        fun sort() {
            for (i in 1 until paths.size) {
                var j = i
                while (j > 0 && paths[j - 1] > paths[j]) {
                    paths.add(j - 1, paths.removeAt(j))
                    nodes.add(j - 1, nodes.removeAt(j))
                    pending.add(j - 1, pending.removeAt(j))
                    j--
                }
            }
        }
    }

    /**
     * Like addWordsOf, but the word itself before the expressions filed
     * under it: "Amérique" before "Amérique du Nord", "cadre" before "dans
     * le cadre de"; then the word with a clarification, "livre" before
     * "livre (monnaie)", before the expressions.
     */
    private fun addWordsExactFirst(
        cursor: Cursor,
        node: Node,
        path: String,
        results: Array<SearchResult?>,
        numResults: Int,
    ): Int {
        val words = arrayOfNulls<SearchResult>(results.size - numResults)
        val count = addWordsOf(cursor, node, path, words, 0)
        val exact = path.lowercase(Locale.ROOT)
        var n = numResults

        for (pass in 0 until 3)
            for (i in 0 until count) {
                val word = words[i]!!.word.lowercase(Locale.ROOT)
                val kind = if (word == exact) 0 else if (word.startsWith("$exact (")) 1 else 2
                if (kind == pass)
                    results[n++] = words[i]
            }

        return n
    }

    /** Adds the words that end on a node, whose path is 'path': the first
     * word of its run, if it is the node itself */
    private fun addWordsOf(
        cursor: Cursor,
        node: Node,
        path: String,
        results: Array<SearchResult?>,
        numResults: Int,
    ): Int {
        cursor.at(node.lo)
        if (cursor.length != node.depth)
            return numResults
        return cursor.addArticles(path, results, numResults)
    }

    /** Follows the pattern from patternOffset, the node being reached by
     * 'path'. A node is visited only once for each place in the pattern,
     * so that two stars don't find a word twice. */
    private fun matchPattern(
        cursor: Cursor,
        node: Node,
        pattern: String,
        patternOffset: Int,
        path: StringBuilder,
        visited: MutableSet<Long>,
        knownChildren: List<Node>?,
        results: Array<SearchResult?>,
        numResults: Int,
    ): Int {
        var n = numResults

        /* A node is its first word and its length; the length of a key and
         * the place in the pattern are below 2^16. The first word in the
         * high bits, for a hash that mixes it with the rest */
        val place = (node.lo.toLong() shl 32) or (node.depth.toLong() shl 16) or patternOffset.toLong()
        if (n >= results.size || !visited.add(place))
            return n

        if (patternOffset >= pattern.length)
            return addWordsOf(cursor, node, path.toString(), results, n)

        val nextOffset = pattern.offsetByCodePoints(patternOffset, 1)
        val typed = pattern.substring(patternOffset, nextOffset).lowercase(Locale.ROOT)
        val star = typed == "*"

        /* The letters that follow, once for both ways of a star */
        val children = knownChildren ?: cursor.children(node)

        /* A star can also stand for nothing */
        if (star)
            n = matchPattern(cursor, node, pattern, nextOffset, path, visited, children, results, n)

        val letter = foldLetter(typed)
        val plain = letter == typed

        for (child in children) {
            if (n >= results.size)
                break

            val childLetter = child.letter
            val matches = when {
                star || typed == "?" -> true
                plain -> foldLetter(childLetter) == letter
                else -> childLetter.lowercase(Locale.ROOT) == typed
            }

            if (matches) {
                val oldLength = path.length
                path.append(childLetter)
                /* A star stays, to take more letters */
                n = matchPattern(
                    cursor, child, pattern, if (star) patternOffset else nextOffset,
                    path, visited, null, results, n,
                )
                path.setLength(oldLength)
            }
        }

        return n
    }

    /**
     * Searches the whole words that match a pattern, where "*" stands for
     * any letters, even none, and "?" for exactly one letter: "*ologio"
     * finds "biologio" and "geologio". The letters follow the same rules
     * as searchIgnoringAccents. The results are in the order of the index.
     */
    fun searchPattern(pattern: String, results: Array<SearchResult?>): Int {
        /* Several stars in a row are the same as one */
        val simple = pattern.replace(Regex("\\*+"), "*")

        /* The place in the pattern is kept in 16 bits */
        if (simple.length >= 0x10000)
            return 0

        return matchPattern(Cursor(), root(), simple, 0, StringBuilder(), HashSet(), null, results, 0)
    }

    companion object {
        private val MAGIC = byteArrayOf('B'.code.toByte(), 'V'.code.toByte(), 'X'.code.toByte(), '1'.code.toByte())

        /* A whole key kept every BLOCK words */
        private const val BLOCK = 16

        /* Beyond that many words, the end of a run is found by a binary
         * search rather than read word by word */
        private const val SCAN = 24

        /** The number of bytes of a UTF-8 sequence which begins with the
         * given byte */
        private fun utf8Length(firstByte: Byte): Int {
            val b = firstByte.toInt()
            return when {
                b >= 0 -> 1
                b and 0xe0 == 0xc0 -> 2
                b and 0xf0 == 0xe0 -> 3
                b and 0xf8 == 0xf0 -> 4
                b and 0xfc == 0xf8 -> 5
                else -> 6
            }
        }

        /** The number of bytes of a letter in UTF-8 */
        private fun utf8LengthOf(letter: Int): Int = when {
            letter < 0x80 -> 1
            letter < 0x800 -> 2
            letter < 0x10000 -> 3
            else -> 4
        }

        /** The letter starting at 'at' */
        private fun codePoint(bytes: ByteArray, at: Int): Int {
            val b = bytes[at].toInt() and 0xff
            if (b < 0x80)
                return b
            val length = utf8Length(bytes[at])
            var value = b and (0x7f shr length)
            for (i in 1 until length)
                value = (value shl 6) or (bytes[at + i].toInt() and 0x3f)
            return value
        }

        /** The place of a letter in the order of the index, as
         * pdb_strcmp_ch: ĉ ĝ ĥ ĵ ŝ ŭ right after c g h j s u. The keys are in
         * lower case, so prevodb's lower case changes nothing */
        private fun orderValue(letter: Int): Int = when (letter) {
            0x109 -> 'c'.code * 2 + 1
            0x11d -> 'g'.code * 2 + 1
            0x125 -> 'h'.code * 2 + 1
            0x135 -> 'j'.code * 2 + 1
            0x15d -> 's'.code * 2 + 1
            0x16d -> 'u'.code * 2 + 1
            else -> letter * 2
        }

        /** Compares the key with the start of words: 0 if the key begins
         * with it, else the order of the two */
        private fun compareStart(
            key: ByteArray, keyOffset: Int, keyLength: Int,
            start: ByteArray, startLength: Int,
        ): Int {
            var letter = 0
            var i = 0
            while (i < startLength) {
                if (i >= keyLength)
                    return -1
                val k = key[keyOffset + i]
                val s = start[i]
                if ((s.toInt() and 0xc0) != 0x80)
                    letter = i
                if (k != s) {
                    val kv = orderValue(codePoint(key, keyOffset + letter))
                    val sv = orderValue(codePoint(start, letter))
                    return if (kv < sv) -1 else 1
                }
                i++
            }
            return 0
        }

        /* The letters typed as two: the ligatures, the German umlauts and ß */
        private val LETTER_PAIRS = mapOf(
            "œ" to "oe", "æ" to "ae", "ü" to "ue",
            "ä" to "ae", "ö" to "oe", "ß" to "ss",
        )
        private const val UMLAUTS = "üäö"

        /** A node reached twice, or inside another reached ("mue" reaches
         * "mü" as "ue", and "müe" as "u" then "e"): its words once, under
         * the outer one */
        private fun keepOutermost(nodes: MutableList<Node>, paths: MutableList<String>) {
            for (i in nodes.indices.reversed()) {
                val node = nodes[i]
                for (j in nodes.indices) {
                    val outer = nodes[j]
                    val same = outer.lo == node.lo && outer.depth == node.depth
                    if (j != i && outer.contains(node) && (!same || j < i)) {
                        nodes.removeAt(i)
                        paths.removeAt(i)
                        break
                    }
                }
            }
        }

        /** The letter without its accents and in lower case, eg. "e" for
         * "É" and "c" for "ĉ", to compare letters ignoring their accents */
        private fun foldLetter(letter: String): String {
            val decomposed = Normalizer.normalize(letter, Normalizer.Form.NFD)
            val buf = StringBuilder()

            /* The voicing marks of the kana aren't accents: they change the
             * sound, "か" ka doesn't find "が" ga, nor "は" ha "ぱ" pa */
            for (ch in decomposed)
                if (Character.getType(ch) != Character.NON_SPACING_MARK.toInt() || isVoicing(ch))
                    buf.append(ch)

            /* The curly apostrophe of some keyboards, and of a few entries,
             * is the straight one: "aujourd’hui" is "aujourd'hui". The
             * non-breaking spaces of some entries, "brr !", are spaces; the
             * ligatures, in the order, their two letters */
            return buf.toString().lowercase(Locale.ROOT).replace('’', '\'')
                .replace('\u00a0', ' ').replace('\u202f', ' ')
                .replace("œ", "oe").replace("æ", "ae")
        }

        /** A katakana letter as its hiragana, "イ" as "い"; any other letter
         * as it is */
        @JvmStatic
        fun hiragana(letter: String): String {
            if (letter.length != 1)
                return letter
            val ch = letter[0]
            return if (ch in 'ァ'..'ヶ') (ch - 0x60).toString() else letter
        }

        /** Whether two kana are the same in hiragana and katakana: "イヌ"
         * typed finds "いぬ", the reading of 犬, and "いぬ" finds "イヌ", as
         * the names of animals and plants are written */
        @JvmStatic
        fun sameKana(a: String, b: String): Boolean {
            val plainA = hiragana(a)
            val plainB = hiragana(b)
            /* One katakana and one hiragana, the same once both hiragana */
            return ((plainA != a) != (plainB != b)) && plainA == plainB
        }

        private fun groupIn(groups: TreeMap<String, Group>, key: String): Group =
            groups.getOrPut(key) { Group() }

        /** The first group on top */
        private fun pushInOrder(stack: java.util.ArrayDeque<Group>, groups: TreeMap<String, Group>) {
            for (group in groups.descendingMap().values)
                stack.push(group)
        }

        /* The kana by their vowel, for the long vowel ー */
        private const val VOWEL_A = "ぁあかがさざただなはばぱまゃやらゎわゕ"
        private const val VOWEL_I = "ぃいきぎしじちぢにひびぴみりゐ"
        private const val VOWEL_U = "ぅうくぐすずっつづぬふぶぷむゅゆるゔ"
        private const val VOWEL_E = "ぇえけげせぜてでねへべぺめれゑゖ"
        private const val VOWEL_O = "ぉおこごそぞとどのほぼぽもょよろを"

        /** The vowel a long vowel ー after this kana lengthens, as a
         * Japanese dictionary orders it: "あ" after "カ", "コーヒー" read
         * "こおひい"; null after another letter, "ん" or "ー" */
        @JvmStatic
        fun prolonged(before: Char): Char? {
            val kana = hiragana(before.toString())[0]
            return when (kana) {
                in VOWEL_A -> 'あ'
                in VOWEL_I -> 'い'
                in VOWEL_U -> 'う'
                in VOWEL_E -> 'え'
                in VOWEL_O -> 'お'
                else -> null
            }
        }

        /** The voicing mark of a kana once decomposed: "が" is "か" and
         * U+3099, "ぱ" "は" and U+309A */
        private fun isVoicing(ch: Char) = ch.code == 0x3099 || ch.code == 0x309a

        /** The place of a letter in the order of the results: the letter
         * without its accents and case; in Esperanto, ĉ ĝ ĥ ĵ ŝ ŭ right
         * after c g h j s u. A katakana as its hiragana, and a kana without
         * its voicing mark, as a Japanese dictionary orders them: "イヌ"
         * among the "い", "がき" right after "かき" */
        private fun orderOf(letter: String, esperanto: Boolean, before: String = ""): String {
            /* The long vowel as the vowel it lengthens: "コー" as "こお",
             * "コーヒー" among the "こお", not after the "こん" */
            val vowel = if (letter == "ー" && before.isNotEmpty()) prolonged(before.last()) else null
            val plain = vowel?.toString() ?: hiragana(letter)
            val folded = foldLetter(plain).filterNot(::isVoicing)
            if (esperanto && folded != plain.lowercase(Locale.ROOT))
                return folded + '\uffff'
            return folded
        }
    }
}
