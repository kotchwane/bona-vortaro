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

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.io.IOException

/** The articles the app ships, compact and compressed, read as prevodb
 * wrote them before: scripts/generate-dictionary.sh also writes them in
 * the old format into prevodb-build/raw-articles */
class ArticleFilesTest {
    private val compact = File("src/main/assets/articles")
    private val raw = File("../prevodb-build/raw-articles")

    /** The articles of the old format, 16 to a file: each its length (4
     * bytes), its title, then its sections; a string, its length (2
     * bytes), its UTF-8, its spans (length, start, data1, data2: 2 bytes
     * each; type: 1 byte) up to a length of 0 */
    private fun oldArticles(): List<ArticleData> {
        val files = raw.listFiles { f -> f.name.matches(Regex("article-[0-9a-f]+x\\.bin")) }!!.sortedBy { it.name }
        val articles = ArrayList<ArticleData>()
        for (file in files) {
            val d = file.readBytes()
            var p = 0
            fun u16(): Int = ((d[p].toInt() and 0xff) or ((d[p + 1].toInt() and 0xff) shl 8)).also { p += 2 }
            fun text(): TextData {
                val length = u16()
                val text = String(d, p, length, Charsets.UTF_8)
                p += length
                val spans = ArrayList<SpanData>()
                while (true) {
                    val spanLength = u16()
                    if (spanLength == 0)
                        break
                    val start = u16()
                    val data1 = u16()
                    val data2 = u16()
                    val type = d[p++].toInt() and 0xff
                    spans.add(SpanData(type, start, spanLength, data1, data2))
                }
                return TextData(text, spans)
            }
            while (p < d.size) {
                val length = (d[p].toInt() and 0xff) or ((d[p + 1].toInt() and 0xff) shl 8) or
                    ((d[p + 2].toInt() and 0xff) shl 16) or ((d[p + 3].toInt() and 0xff) shl 24)
                p += 4
                val end = p + length
                val title = text()
                val sections = ArrayList<SectionData>()
                while (p < end) {
                    var codeLength = 3
                    while (codeLength > 0 && d[p + codeLength - 1] == 0.toByte()) codeLength--
                    val language = String(d, p, codeLength, Charsets.UTF_8)
                    p += 3
                    sections.add(SectionData(language, text(), text()))
                }
                assertEquals(file.name, end, p)
                articles.add(ArticleData(title, sections))
            }
        }
        return articles
    }

    private fun compactFiles(): List<File> {
        assumeTrue("The dictionary hasn't been built", raw.isDirectory && compact.isDirectory)
        return compact.listFiles { f -> f.name.matches(Regex("articles-[0-9a-f]+\\.xz")) }!!.sortedBy { it.name }
    }

    @Test
    fun everyArticleReadsAsPrevodbWroteIt() {
        val files = compactFiles()
        val old = oldArticles()
        var number = 0
        for ((index, file) in files.withIndex()) {
            assertEquals(String.format("articles-%03x.xz", index), file.name)
            val block = file.inputStream().use { ArticleFiles.block(it) }
            /* Full files, the last one partly */
            if (index < files.size - 1)
                assertEquals(file.name, ArticleFiles.PER_FILE, block.count)
            for (i in 0 until block.count) {
                assertEquals("article $number", old[number], ArticleFiles.parseArticle(block.data, block.starts[i], block.lengths[i]))
                number++
            }
        }
        assertEquals(old.size, number)
        /* As many as Reta Vortaro has */
        assertTrue(number > 13000)
    }

    @Test
    fun theArticlesHaveTheirWordsAndSpans() {
        val files = compactFiles()
        /* kato: 5920, the 32nd of file 92 */
        val block = files[5920 / ArticleFiles.PER_FILE].inputStream().use { ArticleFiles.block(it) }
        val kato = ArticleFiles.parseArticle(block.data, block.starts[5920 % 64], block.lengths[5920 % 64])
        /* "*kat/o": the star of the Fundamento, a superscript span */
        assertTrue(kato.title.text, "kat/o" in kato.title.text)
        assertTrue(kato.sections.any { it.language == "fr" && "chat" in it.content.text })
        assertTrue(kato.sections.first().content.spans.isNotEmpty())
    }

    @Test
    fun aDamagedFileIsRefused() {
        val file = compactFiles().first()
        val bytes = file.readBytes()
        /* A byte changed in the middle: xz's check finds it */
        bytes[bytes.size / 2] = (bytes[bytes.size / 2].toInt() xor 0x10).toByte()
        assertThrows(IOException::class.java) { bytes.inputStream().use { ArticleFiles.block(it) } }
        /* Not a file of articles */
        assertThrows(IOException::class.java) { ArticleFiles.parseBlock("BVX1....".toByteArray()) }
    }
}
