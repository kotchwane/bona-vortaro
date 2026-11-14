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
 * The search as it was on the tries prevodb writes, before the app
 * searched the compact form itself (Trie): kept for the tests, which
 * compare the two on every index, query by query.
 *
 * A node: its size, 4 bytes in little endian with the high bit set if
 * words end on it, its letter in UTF-8, then those words, then its
 * children; each word: 2 bytes (0x8000 another word follows, 0x4000 a
 * display name follows, the article in the 14 low bits), the mark, and
 * the display name, its length then its UTF-8.
 */
class OldTrie @Throws(IOException::class) constructor(dataStream: InputStream) {
    private val data: ByteArray

    init {
        /* The 4 bytes of the length of the file first */
        val lengthBytes = ByteArray(4)
        readAll(dataStream, lengthBytes, 0, 4)
        val totalLength = extractInt(lengthBytes, 0) and 0x7fffffff
        data = ByteArray(totalLength)
        System.arraycopy(lengthBytes, 0, data, 0, 4)
        readAll(dataStream, data, 4, totalLength - 4)
    }

    /** The starts of the nodes still to visit, the end of their parent and
     * the length of the string before them, three ints each */
    private class Stack {
        private var data = IntArray(64)
        private var size = 0

        val topStart get() = data[size - 3]
        val topEnd get() = data[size - 2]
        val topStringLength get() = data[size - 1]

        fun pop() {
            size -= 3
        }

        fun isEmpty() = size <= 0

        fun push(start: Int, end: Int, stringLength: Int) {
            if (size + 3 >= data.size)
                data = data.copyOf(data.size * 2)
            data[size++] = start
            data[size++] = end
            data[size++] = stringLength
        }
    }

    private fun getCharacter(offset: Int) =
        String(data, offset, getUtf8Length(data[offset]), Charsets.UTF_8)

    /** The size of the node, without its flag */
    private fun sizeOf(node: Int) = extractInt(data, node) and 0x7fffffff

    /** The offset after the words of a node that starts at trieStart,
     * whose words start at 'at', if any end on it */
    private fun skipWords(trieStart: Int, at: Int): Int {
        var child = at
        if (extractInt(data, trieStart) < 0) {
            do {
                val hasNext = (data[child + 1].toInt() and 0x80) != 0
                val hasDisplayName = (data[child + 1].toInt() and 0x40) != 0
                child += 3
                if (hasDisplayName)
                    child += (data[child].toInt() and 0xff) + 1
            } while (hasNext)
        }
        return child
    }

    /** The offset of the first child of a node, after the words that end
     * on it, if any */
    private fun childrenOf(node: Int): Int =
        skipWords(node, node + 4 + getUtf8Length(data[node + 4]))

    /** Searches the trie for words that begin with 'prefix', as
     * Trie.search */
    fun search(prefix: String, results: Array<SearchResult?>): Int {
        val prefixBytes = prefix.toByteArray(Charsets.UTF_8)
        var trieStart = 0
        var prefixOffset = 0

        while (prefixOffset < prefixBytes.size) {
            val characterLen = getUtf8Length(prefixBytes[prefixOffset])
            val trieEnd = trieStart + sizeOf(trieStart)
            trieStart = childrenOf(trieStart)

            /* Scan the children for the next letter of the prefix */
            while (true) {
                if (trieStart >= trieEnd)
                    return 0
                if (compareArray(prefixBytes, prefixOffset, data, trieStart + 4, characterLen))
                    break
                trieStart += sizeOf(trieStart)
            }

            prefixOffset += characterLen
        }

        return collect(trieStart, StringBuilder(prefix), results, 0)
    }

    /** Adds to the results the words of the node at trieStart and of its
     * children, depth first so in sorted order. The node is the last one
     * of the string in stringBuf. Returns the new number of results. */
    private fun collect(trieStart: Int, stringBuf: StringBuilder, results: Array<SearchResult?>, found: Int): Int {
        var numResults = found
        val stack = Stack()
        /* As the original, which added before masking: the high bit of
         * trieStart + offset, cleared; the same as the end of the node */
        stack.push(trieStart, (trieStart + extractInt(data, trieStart)) and 0x7fffffff, stringBuf.length)

        var firstChar = true

        while (numResults < results.size && !stack.isEmpty()) {
            val searchStart = stack.topStart
            val searchEnd = stack.topEnd
            stringBuf.setLength(stack.topStringLength)
            stack.pop()

            var offset = extractInt(data, searchStart)
            val characterLen = getUtf8Length(data[searchStart + 4])
            var childrenStart = searchStart + 4 + characterLen
            val oldLength = stringBuf.length

            if (firstChar)
                firstChar = false
            else
                stringBuf.append(String(data, searchStart + 4, characterLen, Charsets.UTF_8))

            /* If this is a complete word then add it to the results */
            if (offset < 0) {
                var hasNext = true
                while (hasNext && numResults < results.size) {
                    var article = (data[childrenStart].toInt() and 0xff) or
                        ((data[childrenStart + 1].toInt() and 0xff) shl 8)
                    val mark = data[childrenStart + 2].toInt() and 0xff
                    hasNext = (article and 0x8000) != 0
                    val hasDisplayName = (article and 0x4000) != 0
                    childrenStart += 3
                    article = article and 0x3fff

                    val word = if (hasDisplayName) {
                        val len = data[childrenStart].toInt() and 0xff
                        val name = String(data, childrenStart + 1, len, Charsets.UTF_8)
                        childrenStart += len + 1
                        name
                    } else stringBuf.toString()

                    results[numResults++] = SearchResult.fromIndex(word, article, mark)
                }
                offset = offset and 0x7fffffff
            }

            /* The sibling after the children of this node */
            if (searchStart + offset < searchEnd)
                stack.push(searchStart + offset, searchEnd, oldLength)

            /* The children of this node */
            if (childrenStart < searchStart + offset)
                stack.push(childrenStart, searchStart + offset, stringBuf.length)
        }

        return numResults
    }

    /** A node reached twice, or inside another reached: its words once,
     * under the outer one */
    private fun keepOutermost(nodes: MutableList<Int>, paths: MutableList<String>) {
        for (i in nodes.indices.reversed()) {
            val node = nodes[i]
            for (j in nodes.indices) {
                val outer = nodes[j]
                val end = outer + sizeOf(outer)
                if (j != i && outer <= node && node < end && (outer != node || j < i)) {
                    nodes.removeAt(i)
                    paths.removeAt(i)
                    break
                }
            }
        }
    }

    /** The nodes whose path from the root matches the rest of the prefix,
     * ignoring the accents, as Trie */
    private fun findIgnoringAccents(
        trieStart: Int,
        prefix: String,
        prefixOffset: Int,
        path: StringBuilder,
        nodes: MutableList<Int>,
        paths: MutableList<String>,
    ) {
        if (prefixOffset >= prefix.length) {
            nodes.add(trieStart)
            paths.add(path.toString())
            return
        }

        val letterEnd = prefix.offsetByCodePoints(prefixOffset, 1)
        val typed = prefix.substring(prefixOffset, letterEnd).lowercase(Locale.ROOT)
        val letter = foldLetter(typed)
        val plain = letter == typed
        val trieEnd = trieStart + sizeOf(trieStart)

        var child = childrenOf(trieStart)
        while (child < trieEnd) {
            val childLetter = getCharacter(child + 4)

            if ((if (plain) foldLetter(childLetter) == letter
                 else childLetter.lowercase(Locale.ROOT) == typed) ||
                sameKana(childLetter, typed)) {
                val oldLength = path.length
                path.append(childLetter)
                findIgnoringAccents(child, prefix, letterEnd, path, nodes, paths)
                path.setLength(oldLength)
            }
            if (plain) {
                val ligature = childLetter.lowercase(Locale.ROOT)
                val pair = LETTER_PAIRS[ligature]
                if (pair != null && letter == pair.substring(0, 1)) {
                    val umlaut = UMLAUTS.indexOf(ligature) >= 0
                    var pairEnd = letterEnd
                    var skip = false
                    if (letterEnd < prefix.length &&
                        prefix.substring(letterEnd, prefix.offsetByCodePoints(letterEnd, 1))
                            .lowercase(Locale.ROOT) == pair.substring(1))
                        pairEnd = prefix.offsetByCodePoints(letterEnd, 1)
                    else if (letterEnd < prefix.length || umlaut)
                        skip = true

                    if (!skip) {
                        val oldLength = path.length
                        path.append(childLetter)
                        findIgnoringAccents(child, prefix, pairEnd, path, nodes, paths)
                        path.setLength(oldLength)
                    }
                }
            }
            child += sizeOf(child)
        }
    }

    /** As Trie.searchIgnoringAccents */
    @JvmOverloads
    fun searchIgnoringAccents(prefix: String, results: Array<SearchResult?>, esperanto: Boolean = false): Int {
        val nodes = ArrayList<Int>()
        val paths = ArrayList<String>()

        findIgnoringAccents(0, prefix, 0, StringBuilder(), nodes, paths)
        keepOutermost(nodes, paths)

        val start = TreeMap<String, Group>()
        val typed = foldLetter(prefix).length
        for (i in nodes.indices) {
            val path = paths[i]
            val key = StringBuilder()
            var at = 0
            while (at < path.length) {
                val next = path.offsetByCodePoints(at, 1)
                key.append(orderOf(path.substring(at, next), esperanto))
                at = next
            }
            if (!esperanto && key.length > typed)
                groupIn(start, key.substring(0, typed)).add(nodes[i], path, key.substring(typed))
            else
                groupIn(start, key.toString()).add(nodes[i], path, "")
        }

        val stack = java.util.ArrayDeque<Group>()
        pushInOrder(stack, start)

        var numResults = 0

        while (numResults < results.size && !stack.isEmpty()) {
            val group = stack.pop()
            group.sort()

            for (i in group.nodes.indices)
                if (group.pending[i].isEmpty())
                    numResults = addWordsExactFirst(group.nodes[i], group.paths[i], results, numResults)

            val children = TreeMap<String, Group>()
            for (i in group.nodes.indices) {
                val node = group.nodes[i]
                val pending = group.pending[i]

                if (pending.isNotEmpty()) {
                    groupIn(children, pending.substring(0, 1)).add(node, group.paths[i], pending.substring(1))
                    continue
                }

                val end = node + sizeOf(node)
                var child = childrenOf(node)
                while (child < end) {
                    val letter = getCharacter(child + 4)
                    var key = orderOf(letter, esperanto)
                    var rest = ""
                    if (!esperanto && key.length > 1) {
                        rest = key.substring(1)
                        key = key.substring(0, 1)
                    }
                    groupIn(children, key).add(child, group.paths[i] + letter, rest)
                    child += sizeOf(child)
                }
            }
            pushInOrder(stack, children)
        }

        return numResults
    }

    private class Group {
        val nodes = ArrayList<Int>()
        val paths = ArrayList<String>()
        val pending = ArrayList<String>()

        fun add(node: Int, path: String, rest: String) {
            nodes.add(node)
            paths.add(path)
            pending.add(rest)
        }

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

    private fun addWordsExactFirst(node: Int, path: String, results: Array<SearchResult?>, numResults: Int): Int {
        val words = arrayOfNulls<SearchResult>(results.size - numResults)
        val count = addWordsOf(node, path, words, 0)
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

    /** Adds the words that end on a node, whose path is 'path' */
    private fun addWordsOf(node: Int, path: String, results: Array<SearchResult?>, numResults: Int): Int {
        if (extractInt(data, node) >= 0)
            return numResults

        var n = numResults
        var child = node + 4 + getUtf8Length(data[node + 4])
        var hasNext = true

        while (hasNext && n < results.size) {
            var article = (data[child].toInt() and 0xff) or ((data[child + 1].toInt() and 0xff) shl 8)
            val mark = data[child + 2].toInt() and 0xff
            hasNext = (article and 0x8000) != 0
            val hasDisplayName = (article and 0x4000) != 0
            child += 3
            article = article and 0x3fff

            var word = path
            if (hasDisplayName) {
                val len = data[child].toInt() and 0xff
                word = String(data, child + 1, len, Charsets.UTF_8)
                child += len + 1
            }

            results[n++] = SearchResult.fromIndex(word, article, mark)
        }

        return n
    }

    private fun matchPattern(
        node: Int,
        pattern: String,
        patternOffset: Int,
        path: StringBuilder,
        visited: MutableSet<Long>,
        results: Array<SearchResult?>,
        numResults: Int,
    ): Int {
        var n = numResults
        if (n >= results.size || !visited.add((node.toLong() shl 32) or patternOffset.toLong()))
            return n

        if (patternOffset >= pattern.length)
            return addWordsOf(node, path.toString(), results, n)

        val nextOffset = pattern.offsetByCodePoints(patternOffset, 1)
        val typed = pattern.substring(patternOffset, nextOffset).lowercase(Locale.ROOT)
        val star = typed == "*"

        if (star)
            n = matchPattern(node, pattern, nextOffset, path, visited, results, n)

        val letter = foldLetter(typed)
        val plain = letter == typed
        val nodeEnd = node + sizeOf(node)

        var child = childrenOf(node)
        while (child < nodeEnd && n < results.size) {
            val childLetter = getCharacter(child + 4)
            val matches = when {
                star || typed == "?" -> true
                plain -> foldLetter(childLetter) == letter
                else -> childLetter.lowercase(Locale.ROOT) == typed
            }

            if (matches) {
                val oldLength = path.length
                path.append(childLetter)
                n = matchPattern(child, pattern, if (star) patternOffset else nextOffset, path, visited, results, n)
                path.setLength(oldLength)
            }
            child += sizeOf(child)
        }

        return n
    }

    fun searchPattern(pattern: String, results: Array<SearchResult?>): Int =
        matchPattern(0, pattern.replace(Regex("\\*+"), "*"), 0, StringBuilder(), HashSet(), results, 0)

    private companion object {
        val LETTER_PAIRS = mapOf(
            "œ" to "oe", "æ" to "ae", "ü" to "ue",
            "ä" to "ae", "ö" to "oe", "ß" to "ss",
        )
        const val UMLAUTS = "üäö"

        fun readAll(stream: InputStream, data: ByteArray, offset: Int, length: Int) {
            var at = offset
            var left = length
            while (left > 0) {
                val got = stream.read(data, at, left)
                if (got == -1)
                    throw IOException("Unexpected end of file")
                at += got
                left -= got
            }
        }

        fun extractInt(data: ByteArray, offset: Int): Int =
            (data[offset].toInt() and 0xff) or
                ((data[offset + 1].toInt() and 0xff) shl 8) or
                ((data[offset + 2].toInt() and 0xff) shl 16) or
                ((data[offset + 3].toInt() and 0xff) shl 24)

        fun getUtf8Length(firstByte: Byte): Int {
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

        fun compareArray(a: ByteArray, aOffset: Int, b: ByteArray, bOffset: Int, length: Int): Boolean {
            for (i in 0 until length)
                if (a[aOffset + i] != b[bOffset + i])
                    return false
            return true
        }

        fun foldLetter(letter: String): String {
            val decomposed = Normalizer.normalize(letter, Normalizer.Form.NFD)
            val buf = StringBuilder()
            /* The voicing marks of the kana aren't accents (0.9) */
            for (ch in decomposed)
                if (Character.getType(ch) != Character.NON_SPACING_MARK.toInt() || ch.code == 0x3099 || ch.code == 0x309a)
                    buf.append(ch)
            return buf.toString().lowercase(Locale.ROOT).replace('’', '\'')
                .replace('\u00a0', ' ').replace('\u202f', ' ')
                .replace("œ", "oe").replace("æ", "ae")
        }

        /* Its own copies, not Trie's, which it checks */
        fun hiragana(letter: String): String {
            if (letter.length != 1)
                return letter
            val ch = letter[0]
            return if (ch in '\u30a1'..'\u30f6') (ch - 0x60).toString() else letter
        }

        fun sameKana(a: String, b: String): Boolean {
            val plainA = hiragana(a)
            val plainB = hiragana(b)
            return ((plainA != a) != (plainB != b)) && plainA == plainB
        }

        fun groupIn(groups: TreeMap<String, Group>, key: String): Group = groups.getOrPut(key) { Group() }

        fun pushInOrder(stack: java.util.ArrayDeque<Group>, groups: TreeMap<String, Group>) {
            for (group in groups.descendingMap().values)
                stack.push(group)
        }

        /* A katakana as its hiragana, a kana without its voicing mark, in
         * the order (0.9) */
        fun orderOf(letter: String, esperanto: Boolean): String {
            val plain = hiragana(letter)
            val folded = foldLetter(plain).filter { it.code != 0x3099 && it.code != 0x309a }
            if (esperanto && folded != plain.lowercase(Locale.ROOT))
                return folded + '\uffff'
            return folded
        }
    }
}
