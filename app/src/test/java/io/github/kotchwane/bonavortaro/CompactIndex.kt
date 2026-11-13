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

import java.io.IOException

/**
 * The compact form of a search index, as the app ships it, made into the
 * trie prevodb wrote before compacting it, byte for byte
 * (prevodb/src/pdb-compact.h describes both): the words in the trie's
 * order, each written as the bytes it shares with the one before and the
 * rest, its display name once if several words have it, or as the
 * capitals of its letters ("Hund" for "hund"). For the tests, which
 * compare it with prevodb's tries and search it with OldTrie.
 */
object CompactIndex {
    private val MAGIC = byteArrayOf('B'.code.toByte(), 'V'.code.toByte(), 'X'.code.toByte(), '1'.code.toByte())

    private const val HAS_WORDS = 0x80000000.toInt()
    private const val HAS_NEXT = 0x8000
    private const val HAS_DISPLAY = 0x4000
    private const val ARTICLE = 0x3fff
    private const val MASK_LETTERS = 64

    fun isCompact(start: ByteArray): Boolean =
        start.size >= 4 && (0 until 4).all { start[it] == MAGIC[it] }

    /** Where the reading is in the compact data */
    private class Reader(val data: ByteArray, var pos: Int) {
        fun varint(): Long {
            var value = 0L
            var shift = 0
            while (shift < 64) {
                if (pos >= data.size)
                    throw IOException("Truncated compact index")
                val b = data[pos++].toInt() and 0xff
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

        fun bytes(length: Int): ByteArray {
            if (pos + length > data.size)
                throw IOException("Truncated compact index")
            val out = data.copyOfRange(pos, pos + length)
            pos += length
            return out
        }
    }

    /** The trie being written */
    private class Out(capacity: Int) {
        var buf = ByteArray(capacity)
        var len = 0

        fun ensure(more: Int) {
            if (len + more > buf.size)
                buf = buf.copyOf(maxOf(buf.size * 2, len + more))
        }

        fun add(b: Byte) {
            ensure(1)
            buf[len++] = b
        }

        fun add(bytes: ByteArray, offset: Int, length: Int) {
            ensure(length)
            System.arraycopy(bytes, offset, buf, len, length)
            len += length
        }

        fun putInt(at: Int, value: Int) {
            buf[at] = value.toByte()
            buf[at + 1] = (value shr 8).toByte()
            buf[at + 2] = (value shr 16).toByte()
            buf[at + 3] = (value shr 24).toByte()
        }
    }

    private fun utf8Length(first: Byte): Int {
        val b = first.toInt() and 0xff
        return if (b < 0x80) 1 else if (b < 0xe0) 2 else if (b < 0xf0) 3 else 4
    }

    /** The key with the letters of the mask as capitals */
    private fun withCapitals(path: ByteArray, pathLen: Int, mask: Long): ByteArray {
        val key = String(path, 0, pathLen, Charsets.UTF_8)
        val name = StringBuilder(key.length)
        var letter = 0
        var at = 0
        while (at < key.length) {
            var c = key.codePointAt(at)
            at += Character.charCount(c)
            if (letter < MASK_LETTERS && ((mask ushr letter) and 1L) != 0L)
                c = Character.toUpperCase(c)
            name.appendCodePoint(c)
            letter++
        }
        return name.toString().toByteArray(Charsets.UTF_8)
    }

    @Throws(IOException::class)
    fun decode(data: ByteArray): ByteArray {
        if (!isCompact(data))
            throw IOException("Not a compact index")

        val input = Reader(data, 4)
        /* The size of the trie, written first: made at once, not grown */
        val size = input.varint()
        if (size < 0 || size > Int.MAX_VALUE - 8)
            throw IOException("Invalid size in compact index")
        val out = Out(size.toInt())

        /* The open nodes: where each starts, the length of its path,
         * whether words end on it */
        var starts = IntArray(64)
        var pathLens = IntArray(64)
        var withWords = BooleanArray(64)
        var open = 0

        fun close() {
            open--
            out.putInt(starts[open], (out.len - starts[open]) or (if (withWords[open]) HAS_WORDS else 0))
        }

        /* The root */
        val rootLen = input.count()
        starts[open] = out.len
        pathLens[open] = 0
        withWords[open++] = false
        out.ensure(4)
        out.len += 4
        out.add(input.bytes(rootLen), 0, rootLen)

        val table = Array(input.count()) { input.bytes(input.count()) }

        val words = input.count()
        var path = ByteArray(256)
        var pathLen = 0

        for (w in 0 until words) {
            val shared = input.count()
            val rest = input.count()
            if (shared > pathLen || input.pos + rest > data.size)
                throw IOException("Invalid word in compact index")
            if (shared + rest > path.size)
                path = path.copyOf(maxOf(path.size * 2, shared + rest))
            System.arraycopy(data, input.pos, path, shared, rest)
            input.pos += rest
            pathLen = shared + rest

            /* Close the nodes beyond the bytes shared, open those of the
             * rest */
            while (open > 1 && pathLens[open - 1] > shared)
                close()
            var at = shared
            while (at < pathLen) {
                val len = utf8Length(path[at])
                if (open == starts.size) {
                    starts = starts.copyOf(open * 2)
                    pathLens = pathLens.copyOf(open * 2)
                    withWords = withWords.copyOf(open * 2)
                }
                starts[open] = out.len
                pathLens[open] = at + len
                withWords[open++] = false
                out.ensure(4)
                out.len += 4
                out.add(path, at, len)
                at += len
            }
            withWords[open - 1] = true

            val count = input.count()
            if (count == 0)
                throw IOException("A word without articles in compact index")

            for (j in 0 until count) {
                val article = input.varint()
                if (input.pos >= data.size)
                    throw IOException("Truncated compact index")
                val mark = data[input.pos++]
                val code = input.varint()

                val display = when {
                    code == 1L -> withCapitals(path, pathLen, input.varint())
                    code >= 2 -> {
                        if (code - 2 >= table.size)
                            throw IOException("Invalid display name in compact index")
                        table[(code - 2).toInt()]
                    }
                    else -> null
                }

                val bits = (article.toInt() and ARTICLE) or
                    (if (j + 1 < count) HAS_NEXT else 0) or (if (display != null) HAS_DISPLAY else 0)
                out.add(bits.toByte())
                out.add((bits shr 8).toByte())
                out.add(mark)
                if (display != null) {
                    out.add(display.size.toByte())
                    out.add(display, 0, display.size)
                }
            }
        }

        while (open > 0)
            close()

        if (input.pos != data.size)
            throw IOException("Trailing data in compact index")

        if (out.len.toLong() != size)
            throw IOException("The compact index doesn't make a trie of its size")
        return out.buf
    }
}
