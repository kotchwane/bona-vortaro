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

import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

/** The rule of the words Wiktionary adds (user, 2026-11-11): a word on a
 * root Reta Vortaro lacks shows only with an Esperanto definition, which
 * every reader can read. A description in English or French doesn't do:
 * a reader without that language is left with a word they can't
 * understand, nor build from its parts. Such words go to "Vortoj de la
 * bona lingvo" instead, which may advise against them, with a better word.
 *
 * After a new extract, the words that break the rule are written, with
 * BREAKING=<file>, as rows of data/vikivortaro-<language>-rejected.tsv:
 * "*", the word, why. */
class VikivortaroRuleTest {
    private val assets = File("src/main/assets/vikivortaro")

    private fun words(file: File, column: Int) =
        if (!file.exists()) emptySet()
        else file.readLines().filter { !it.startsWith("#") }.mapNotNull { it.split('\t').getOrNull(column) }.toSet()

    @Test
    fun aWordOnANewRootHasAnEsperantoDefinition() {
        val index = File("src/main/assets/indices/index-eo.bin")
        assumeTrue("The dictionary hasn't been built", index.exists())
        val esperanto = index.inputStream().use { Trie(it) }
        val defined = words(File(assets, "eo-eo.tsv"), 0)
        val breaking = VikiLanguage.ALL.flatMap { language ->
            (words(File(assets, "${language.code}-eo.tsv"), 1) + words(File(assets, "eo-${language.code}.tsv"), 0))
                .filter { it !in defined && Vikivortaro.newRoot(esperanto, it) }
                .sorted().map { "${language.code}\t$it" }
        }
        System.getenv("BREAKING")?.let { File(it).writeText(breaking.joinToString("") { "$it\n" }) }
        assertTrue("Words on a new root without an Esperanto definition: ${breaking.take(20)}… (${breaking.size})",
            breaking.isEmpty())
    }
}
