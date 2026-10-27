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
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

/** The analysis on the real index of Esperanto, once the dictionary has
 * been built (it isn't in the repository). */
class WordAnalysisIndexTest {
    private val file = File("src/main/assets/indices/index-eo.bin")

    private fun analyser(): (String) -> List<WordAnalysis> {
        assumeTrue("the dictionary isn't built", file.exists())
        val trie = file.inputStream().use { Trie(it) }
        val entryOf = indexEntries(trie)
        return { WordAnalyser.analyse(it, entryOf) }
    }

    private fun shown(analysis: WordAnalysis) =
        analysis.pieces.joinToString("·") { it.text } + if (analysis.complete) "" else "·…" + analysis.unrecognised

    @Test
    fun theWordsOfTheReadersAreCut() {
        val analyse = analyser()
        val expected = mapOf(
            "babilemulo" to "babilem·ul·o",
            "nutriĝo" to "nutr·iĝ·o",
            "hundaĉo" to "hund·aĉ·o",
            "manĝemulo" to "manĝ·em·ul·o",
            "nelegebla" to "ne·leg·ebl·a",
            "forkurinto" to "forkur·int·o",
            "rekomencigi" to "rekomenc·ig·i",
            "francdevena" to "franc·deven·a",
            "sentemulo" to "sentem·ul·o",
            /* The dictionary has "negocebla"; the "i" of "négociable" is a
             * joining vowel, not "nego" + "cia" */
            "nenegociebla" to "ne·negoc·i·ebl·a",
        )
        val start = System.nanoTime()
        val got = expected.keys.associateWith { analyse(it).firstOrNull()?.let(::shown) }
        val ms = (System.nanoTime() - start) / 1_000_000
        println("${expected.size} words in $ms ms")
        for (word in listOf("trairebla", "kafejestro", "akvobotelo", "malriĉiĝo", "ĉevalidpatrinetporkaĉajemeco"))
            println("$word: " + analyse(word).joinToString("  |  ") { shown(it) })
        assertEquals(expected, got)
    }

    @Test
    fun aSecondReadingIsNotTheFirstCutMoreCoarsely() {
        /* "indeco" is an entry: malakcept·indec·o groups two pieces of the
         * first reading, it says nothing else */
        assertEquals(listOf("malakcept·ind·ec·o"), analyser()("malakceptindeco").map(::shown))
    }

    @Test
    fun aChainOfRootsBeyondWhatRealWordsHaveIsNotGuessed() {
        val analyse = analyser()
        assert(analyse("hundkatbirdfiŝarbdomlibro").isEmpty())
        assert(analyse("malkontraŭantaŭsupermalbonega").isEmpty())
        /* The numbers don't count: a real word */
        assertEquals("tri·dek·unu·jar·aĝ·ul·o", shown(analyse("tridekunujaraĝulo").first()))
        /* Not tri·de·kun·uj·ar·a: a number is not a compound of its numbers */
        assertEquals("tri·dek·unu·jar·a", shown(analyse("tridekunujara").first()))
    }

    @Test
    fun aRareSuffixDoesNotTakeThePlaceOfAPrefixAndARoot() {
        assumeTrue("the dictionary isn't built", file.exists())
        val index = IndexEntries(file.inputStream().use { Trie(it) })
        /* The words themselves hidden, as if missing from the dictionary */
        fun rebuilt(word: String): String {
            val stem = word.dropLast(1)
            val hidden = { form: String -> if (form.length == word.length && form.startsWith(stem)) null else index(form) }
            return shown(WordAnalyser.analyse(word, hidden).first())
        }
        /* re·vid·o, not rev·id·o ("revo" + "-id-") */
        assertEquals("re·vid·o", rebuilt("revido"))
        /* A suffix may be the first root: estr·ad·o, not est·rad·o */
        assertEquals("estr·ad·o", rebuilt("estrado"))
    }

    @Test
    fun ofTwoEntriesWrittenAlikeTheOfficialOneIsShown() {
        val officiality = File("src/main/assets/officiality.txt")
        assumeTrue("the dictionary isn't built", file.exists() && officiality.exists())
        val entries = OfficialLists.parse(officiality.readText())
        val levels = OfficialLists.articleLevels(entries)
        val index = IndexEntries(file.inputStream().use { Trie(it) }) { levels[it] }
        /* kor·lando: "koro" the heart (Fundamento), not the measure of
         * volume (issue #1) */
        val heart = entries.first { it.isRoot && it.text == "kor/o" }.article
        assertEquals("kor·land·o", shown(WordAnalyser.analyse("korlando", index).first()))
        assertEquals(heart, index.result("koro")?.article)
        /* Without officiality, the index's first: the measure */
        assert(IndexEntries(file.inputStream().use { Trie(it) }).result("koro")?.article != heart)
    }

    @Test
    fun theListsPartsAreKeptAndExplained() {
        val officiality = File("src/main/assets/officiality.txt")
        assumeTrue("the dictionary isn't built", file.exists() && officiality.exists())
        val levels = OfficialLists.articleLevels(OfficialLists.parse(officiality.readText()))
        val index = IndexEntries(file.inputStream().use { Trie(it) }) { levels[it] }
        fun read(parts: String) = WordAnalyser.fromParts(parts, index).pieces.map { "${it.text}:${it.kind}:${it.entry}" }
        assertEquals(listOf("urin:ROOT:urino", "kanal:ROOT:kanalo", "inflam:ROOT:inflamo", "o:ENDING:null"), read("urin·kanal·inflam·o"))
        /* The -o- of a compound, a suffix by its own word */
        val pieces = WordAnalyser.fromParts("alt·o·tim·eg·o", index).pieces
        assertEquals(listOf("ROOT", "LINK", "ROOT", "SUFFIX", "ENDING"), pieces.map { it.kind.name })
        assertEquals("ega", pieces[3].entry)
        /* The roots of the Fundamento, high and fear: not "alto" the voice */
        assertEquals(index.result("alta")?.article, index.result(pieces[0].entry!!)?.article)
        assertEquals(index.result("timi")?.article, index.result(pieces[2].entry!!)?.article)
        /* A prefix, even after a root */
        assertEquals("el:PREFIX:el", read("sperm·el·ĵet·i")[1])
        assertEquals("mal:PREFIX:mal-", read("alt·o·tim·mal·san·o")[3])
    }

    @Test
    fun aSuffixOpensItsOwnWordNotAnotherOne() {
        val analyse = analyser()
        /* "eta", small, not "eto", the Greek letter */
        assertEquals(listOf("babilema", "ulo", "eta", null), analyse("babilemuleto").first().pieces.map { it.entry })
    }
}
