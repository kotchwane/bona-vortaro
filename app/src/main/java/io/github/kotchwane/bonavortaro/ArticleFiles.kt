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
import java.io.IOException
import java.io.InputStream
import org.tukaani.xz.XZInputStream

/** A span of a string as the articles have it: its type (ArticleLoader's
 * SPAN_*), where it starts and its length, in UTF-16 units, and its two
 * numbers, 0 if it has none */
data class SpanData(val type: Int, val start: Int, val length: Int, val data1: Int, val data2: Int)

/** A string of an article and its spans, in their order */
data class TextData(val text: String, val spans: List<SpanData>)

/** A section of an article: Esperanto ("eo") for the words, else the
 * translations of a language */
data class SectionData(val language: String, val header: TextData, val content: TextData)

/** An article as prevodb writes it: its title and its sections */
data class ArticleData(val title: TextData, val sections: List<SectionData>)

/**
 * Reads the articles prevodb writes (prevodb/src/pdb-articles.h): 64 to a
 * file, articles/articles-XXX.xz, compressed with xz. The file of the
 * last article read stays in memory, decompressed: the articles of a
 * search, often neighbours, are read from it without decompressing it
 * again.
 */
object ArticleFiles {
    const val PER_FILE = 64
    private val MAGIC = byteArrayOf('B'.code.toByte(), 'V'.code.toByte(), 'A'.code.toByte(), '1'.code.toByte())

    /** A file decompressed: where each of its articles starts, and its
     * length */
    class Block(val data: ByteArray, val starts: IntArray, val lengths: IntArray) {
        val count get() = starts.size
    }

    private class Cached(val number: Int, val block: Block)

    @Volatile private var cached: Cached? = null

    private fun fileName(file: Int) = String.format(java.util.Locale.US, "articles/articles-%03x.xz", file)

    /** The file, decompressed and split into its articles */
    @Throws(IOException::class)
    fun block(input: InputStream): Block = parseBlock(XZInputStream(input.buffered()).use { it.readBytes() })

    @Throws(IOException::class)
    private fun block(context: Context, file: Int): Block {
        cached?.let { if (it.number == file) return it.block }
        val block = context.assets.open(fileName(file)).use { block(it) }
        cached = Cached(file, block)
        return block
    }

    /** The article n. Disk access, but not for an article of the file of
     * the article read before. */
    @Throws(IOException::class)
    fun article(context: Context, number: Int): ArticleData {
        val block = block(context, number / PER_FILE)
        val at = number % PER_FILE
        if (at >= block.count)
            throw IOException("No article $number")
        return parseArticle(block.data, block.starts[at], block.lengths[at])
    }

    /** The number of articles: the files before the last one are full.
     * Disk access. */
    @Throws(IOException::class)
    fun count(context: Context): Int {
        val files = context.assets.list("articles")?.count { it.startsWith("articles-") && it.endsWith(".xz") } ?: 0
        if (files == 0)
            return 0
        return (files - 1) * PER_FILE + block(context, files - 1).count
    }

    /** Reads the numbers of the data */
    private class Reader(val data: ByteArray, var at: Int, val end: Int) {
        fun count(): Int {
            var value = 0
            var shift = 0
            while (true) {
                if (at >= end || shift > 28)
                    throw IOException("Invalid number in an article")
                val b = data[at++].toInt() and 0xff
                value = value or ((b and 0x7f) shl shift)
                if (b and 0x80 == 0)
                    return value
                shift += 7
            }
        }

        fun byte(): Int {
            if (at >= end)
                throw IOException("Truncated article")
            return data[at++].toInt() and 0xff
        }

        fun text(): TextData {
            val length = count()
            if (length < 0 || length > end - at)
                throw IOException("Truncated article")
            val text = String(data, at, length, Charsets.UTF_8)
            at += length
            val spanCount = count()
            if (spanCount < 0 || spanCount > end - at)
                throw IOException("Invalid number of spans")
            val spans = ArrayList<SpanData>(spanCount)
            var start = 0
            for (i in 0 until spanCount) {
                val flags = byte()
                /* Zigzag: 2n for n, 2n - 1 for -n */
                val delta = count()
                start += if (delta and 1 != 0) -((delta + 1) / 2) else delta / 2
                val spanLength = count()
                val data1 = if (flags and 0x40 != 0) count() else 0
                val data2 = if (flags and 0x80 != 0) count() else 0
                spans.add(SpanData(flags and 0x3f, start, spanLength, data1, data2))
            }
            return TextData(text, spans)
        }
    }

    /** A file decompressed: "BVA1", the number of articles, the length of
     * each, then the articles */
    @Throws(IOException::class)
    fun parseBlock(data: ByteArray): Block {
        if (data.size < 4 || (0 until 4).any { data[it] != MAGIC[it] })
            throw IOException("Not a file of articles")
        val reader = Reader(data, 4, data.size)
        val count = reader.count()
        /* Each article takes at least a byte: a damaged count can't ask
         * for more memory than the file has */
        if (count < 0 || count > data.size)
            throw IOException("Invalid number of articles")
        val lengths = IntArray(count) { reader.count() }
        val starts = IntArray(count)
        var at = reader.at
        for (i in 0 until count) {
            if (lengths[i] < 0 || lengths[i] > data.size - at)
                throw IOException("The articles don't fill their file")
            starts[i] = at
            at += lengths[i]
        }
        if (at != data.size)
            throw IOException("The articles don't fill their file")
        return Block(data, starts, lengths)
    }

    /** An article: its title, its number of sections, then each one: its
     * language code (3 bytes, the end zeros left out), its header and its
     * content */
    @Throws(IOException::class)
    fun parseArticle(data: ByteArray, start: Int, length: Int): ArticleData {
        val reader = Reader(data, start, start + length)
        val title = reader.text()
        val sectionCount = reader.count()
        if (sectionCount < 0 || sectionCount > length)
            throw IOException("Invalid number of sections")
        val sections = ArrayList<SectionData>(sectionCount)
        for (i in 0 until sectionCount) {
            if (reader.at + 3 > reader.end)
                throw IOException("Truncated article")
            var codeLength = 3
            while (codeLength > 0 && data[reader.at + codeLength - 1] == 0.toByte())
                codeLength--
            val language = String(data, reader.at, codeLength, Charsets.UTF_8)
            reader.at += 3
            sections.add(SectionData(language, reader.text(), reader.text()))
        }
        if (reader.at != reader.end)
            throw IOException("Trailing data in an article")
        return ArticleData(title, sections)
    }
}
