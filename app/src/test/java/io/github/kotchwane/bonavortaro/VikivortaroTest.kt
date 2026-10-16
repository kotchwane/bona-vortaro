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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

class VikivortaroTest {
    @Test
    fun aLetterTypedWithAnAccentOnlyMatchesThatAccent() {
        /* Searched in every language from the Esperanto tab: "lŭ" isn't
         * "lubie" */
        assertFalse(Vikivortaro.matches("lŭ", "lubie"))
        assertFalse(Vikivortaro.matches("élève", "eleve"))
        assertTrue(Vikivortaro.matches("élève", "élève"))
    }

    @Test
    fun aLetterTypedWithoutAccentMatchesAnyAccent() {
        assertTrue(Vikivortaro.matches("eleve", "élève"))
        assertTrue(Vikivortaro.matches("potiron", "Potiron"))
        assertTrue(Vikivortaro.matches("aujourd'hui", "aujourd’hui"))
        assertFalse(Vikivortaro.matches("potirons", "potiron"))
    }

    private fun mix(revo: List<String>, viki: List<String>, query: String) =
        mixed(revo, viki.map { it.uppercase() }, query) { it }.joinToString(" ")

    @Test
    fun theWiktionaryWordsGoAmongReVosInAlphabeticalOrder() {
        /* Upper case: Wiktionary's */
        assertEquals("pot pot-au-feu POTIRON potron-minet",
            mix(listOf("pot", "pot-au-feu", "potron-minet"), listOf("potiron"), "pot"))
        /* After all of them */
        assertEquals("pot pote POTIRON", mix(listOf("pot", "pote"), listOf("potiron"), "pot"))
        /* An exact match after the exact matches, before the others */
        assertEquals("élever (une droite) élever ÉLEVER éleveur",
            mix(listOf("élever (une droite)", "élever", "éleveur"), listOf("élever"), "elever"))
        /* An expression under its word: "jouer la comédie" under "comédie" */
        assertEquals("jouer la comédie comédie COMESTIBLE comète",
            mix(listOf("jouer la comédie", "comédie", "comète"), listOf("comestible"), "come"))
        /* In Esperanto, ĉ after c: "acido" before "aĉa" */
        assertEquals("acero ACIDO aĉa ado",
            mixed(listOf("acero", "aĉa", "ado"), listOf("ACIDO"), "a", esperanto = true) { it }.joinToString(" "))
        /* The word before an expression filed under it */
        assertEquals("AUTO-STOP voyager en auto-stop",
            mix(listOf("voyager en auto-stop"), listOf("auto-stop"), "auto-stop"))
        /* Only Wiktionary's */
        assertEquals("POTIRON", mix(emptyList(), listOf("potiron"), "potiron"))
    }

    @Test
    fun aWordOfReVoBeyondTheResultsShownIsStillReVos() {
        val file = File("src/main/assets/indices/index-fr.bin")
        assumeTrue("The dictionary hasn't been built", file.exists())
        val french = file.inputStream().use { Trie(it) }
        /* Even when a search stops before it, at its limit */
        assertTrue(Vikivortaro.inRevo(french, "comédie musicale"))
        assertTrue(Vikivortaro.inRevo(french, "comédie"))
        /* Under a longer entry, "potager (jardin)" */
        assertTrue(Vikivortaro.inRevo(french, "potager"))
        assertFalse(Vikivortaro.inRevo(french, "comestible"))
        assertFalse(Vikivortaro.inRevo(french, "potiron"))
        /* The entry itself, for the history */
        assertTrue(Vikivortaro.revoEntry(french, "comédie musicale")!!.word.startsWith("comédie musicale"))
    }

    @Test
    fun theEsperantoOrderIsTheIndexOne() {
        assertEquals(listOf("acido", "aĉa", "ada"), listOf("aĉa", "ada", "acido").sortedBy { sortKey(it, esperanto = true) })
        /* Any letter with an accent after the one without, as Trie.orderOf */
        assertEquals(listOf("Zeno", "Zéno"), listOf("Zéno", "Zeno").sortedBy { sortKey(it, esperanto = true) })
    }

    @Test
    fun theWordsBeyondTheLastResultAreByTheWordEntriesAreFiledUnder() {
        /* "jouer la comédie" is filed under "comédie": "comestible" is after it */
        assertTrue(isBeyond("comestible", "jouer la comédie", "come", esperanto = false))
        assertFalse(isBeyond("comédie musicale", "comète", "come", esperanto = false))
    }

    @Test
    fun aFormOfAWordOfReVoIsTheSameWordButNotAWordInsideIt() {
        assertTrue(Vikivortaro.sameWord("marque-page", "Marque-page"))
        /* A plural; not a feminine, often another word */
        assertTrue(Vikivortaro.sameWord("inuit", "inuits"))
        assertFalse(Vikivortaro.sameWord("golf", "golfe"))
        assertFalse(Vikivortaro.sameWord("parti", "partie"))
        /* œ, hyphen or space, a qualifier in brackets */
        assertTrue(Vikivortaro.sameWord("bœuf (espèce)", "boeuf"))
        assertTrue(Vikivortaro.sameWord("(viande de) bœuf", "bœuf"))
        assertTrue(Vikivortaro.sameWord("petit-déjeuner (subst.)", "petit déjeuner"))
        /* An article or "se" before */
        assertTrue(Vikivortaro.sameWord("les Alpes", "Alpes"))
        assertTrue(Vikivortaro.sameWord("s'abattre", "abattre"))
        assertTrue(Vikivortaro.sameWord("La Réunion", "Réunion"))
        assertTrue(Vikivortaro.sameWord("faire face à", "faire face"))
        assertTrue(Vikivortaro.sameWord("chut !", "chut"))
        /* A name and a word */
        assertFalse(Vikivortaro.sameWord("clair", "Claire"))
        /* A word inside another */
        assertFalse(Vikivortaro.sameWord("vers", "envers"))
        assertFalse(Vikivortaro.sameWord("Luc", "Lucas"))
        assertFalse(Vikivortaro.sameWord("dauphinelle", "dauphiné"))
    }

    @Test
    fun aWordOfReVoWithAHyphenIsReVos() {
        val file = File("src/main/assets/indices/index-eo.bin")
        assumeTrue("The dictionary hasn't been built", file.exists())
        val esperanto = file.inputStream().use { Trie(it) }
        for (word in listOf("Nov-Zelando", "Sankt-Peterburgo", "pli-malpli", "ĉi-tiea"))
            assertTrue(word, Vikivortaro.revoEsperanto(esperanto, word) != null)
        /* An affix, without its hyphen in the index */
        assertTrue(Vikivortaro.revoEsperanto(esperanto, "-ej") != null)
        assertFalse(Vikivortaro.revoEsperanto(esperanto, "potirono") != null)
    }

    @Test
    fun aTranslationOfOneSenseIsAlreadySaid() {
        /* Zelando: Seeland is Reta Vortaro's, for its first sense */
        assertEquals(emptyList<String>(), Vikivortaro.frenchFor(listOf("Seeland"), "(1) Seeland, Sélande; (2) Zélande"))
        assertEquals(listOf("Zeeland"), Vikivortaro.frenchFor(listOf("Seeland", "Zeeland"), "(1, 3) Seeland; (2) Zélande"))
    }

    private fun trie(language: String): Trie {
        val file = File("src/main/assets/indices/index-$language.bin")
        assumeTrue("The dictionary hasn't been built", file.exists())
        return file.inputStream().use { Trie(it) }
    }

    @Test
    fun anExpressionFiledUnderAnotherWordIsReVos() {
        val french = trie("fr")
        assertTrue(Vikivortaro.inRevo(french, "Amérique du Nord"))
        assertTrue(Vikivortaro.inRevo(french, "Grande Bretagne"))
        assertTrue(Vikivortaro.inRevo(french, "maladie cœliaque"))
        assertFalse(Vikivortaro.inRevo(french, "potiron"))
    }

    @Test
    fun theCaseOfAnEsperantoWordTellsTwoWords() {
        val esperanto = trie("eo")
        /* Reta Vortaro has Bruno the name, not bruno the colour; Butano the
         * country, not butano the gas */
        assertEquals("Bruno", Vikivortaro.revoEsperanto(esperanto, "Bruno")?.word)
        assertEquals(null, Vikivortaro.revoEsperanto(esperanto, "bruno"))
        assertEquals(null, Vikivortaro.revoEsperanto(esperanto, "butano"))
        assertEquals("dank' al", Vikivortaro.revoEsperanto(esperanto, "dank’ al")?.word)
    }

    @Test
    fun anExampleOfReVoIsReVos() {
        val (french, esperanto) = trie("fr") to trie("eo")
        /* The translations of its examples, and their phrases, are in the
         * indices: "bonjour!" for "saluton!" */
        assertTrue(Vikivortaro.inRevo(french, "bonjour", esperanto, "saluton"))
        assertEquals("saluton!", Vikivortaro.revoEsperanto(esperanto, "saluton")?.word)
        assertTrue(Vikivortaro.revoEsperanto(esperanto, "femurosto") != null)
        /* The word itself before its example in another article, and not
         * a homonym: "animo" (religio) */
        assertFalse(Vikivortaro.revoEsperanto(esperanto, "animo")!!.isExample)
        assertFalse(Vikivortaro.ambiguous(esperanto, "fingringo"))
    }

    @Test
    fun aWordOfSeveralEntriesIsAmbiguous() {
        val esperanto = trie("eo")
        assertTrue(Vikivortaro.ambiguous(esperanto, "korveto"))
        assertFalse(Vikivortaro.ambiguous(esperanto, "legosigno"))
        /* Two entries of one article: the whisk and the mixing desk */
        assertTrue(Vikivortaro.ambiguous(esperanto, "miksilo"))
    }

    @Test
    fun anotherFrenchWordOfReVoDoesntHideOneOfWiktionary() {
        val (french, esperanto) = trie("fr") to trie("eo")
        /* Accents, "se", case: other words */
        assertFalse(Vikivortaro.inRevo(french, "huppé", esperanto, "altranga"))
        /* Reta Vortaro's "pêcheur (rel.)", "pekulo", for "pécheur" written
         * otherwise: found by "pecheur", not by "pécheur" */
        assertTrue(Vikivortaro.inRevo(french, "pécheur", esperanto, "pekulo", "pecheur"))
        assertFalse(Vikivortaro.inRevo(french, "pécheur", esperanto, "pekulo", "pécheur"))
        assertFalse(Vikivortaro.inRevo(french, "recueillir", esperanto, "kolekti"))
        assertFalse(Vikivortaro.inRevo(french, "Barbarie", esperanto, "Berberio"))
        /* The same, written otherwise, for the same Esperanto word: the
         * case, the plural */
        assertTrue(Vikivortaro.inRevo(french, "alevin", esperanto, "alvuso"))
        /* Not a neologism: "amix" isn't the plural of "ami" */
        assertFalse(Vikivortaro.inRevo(french, "amix", esperanto, "amiko"))
        assertTrue(Vikivortaro.sameWord("chou", "choux"))
        assertFalse(Vikivortaro.sameWord("congre", "congrès"))
    }

    @Test
    fun anEsperantoWordWrittenWithTheOtherCaseIsTheSameWhenItsFrenchIs() {
        val (french, esperanto) = trie("fr") to trie("eo")
        /* "Latino", "latin": Reta Vortaro's "Latino" */
        assertEquals("Latino", Vikivortaro.revoEsperanto(esperanto, "latino", french, listOf("latin"))?.word)
        /* Not "bruno" the colour, whose French is elsewhere */
        assertEquals(null, Vikivortaro.revoEsperanto(esperanto, "bruno", french, listOf("brun", "marron")))
        /* Nor "Etno", "Etna", whose French Reta Vortaro doesn't have */
        assertEquals(null, Vikivortaro.revoEsperanto(esperanto, "Etno", french, listOf("Etna")))
        assertEquals(null, Vikivortaro.revoEsperanto(esperanto, "butano", french, listOf("butane")))
    }

    @Test
    fun aWordOfTheReformIsNotAWord() {
        val esperanto = trie("eo")
        assertTrue(Vikivortaro.isReformOfRevo(esperanto, "katiĉo"))
        assertFalse(Vikivortaro.isReformOfRevo(esperanto, "riĉa"))
    }

    @Test
    fun oeTypedMatchesTheLigature() {
        assertTrue(Vikivortaro.matches("coeur", "cœur"))
        assertTrue(Vikivortaro.matches("cœ", "coeur"))
    }
}
