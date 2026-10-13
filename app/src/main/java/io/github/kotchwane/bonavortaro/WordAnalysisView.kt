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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import java.io.IOException

/** A piece of an analysis as it is shown: its entry, to open, and the
 * start of its definition; or a verb ending, explained by the app. */
class ShownPiece(val label: String, val result: SearchResult?, val gloss: String?, val ending: String? = null)

/** A reading of the word ready to be shown: the word cut, and its pieces */
class ShownReading(val analysis: WordAnalysis, val pieces: List<ShownPiece>)

/** The readings of a word, the likeliest first; a second one only if it
 * is nearly as likely. */
class ShownAnalysis(val readings: List<ShownReading>)

object WordAnalyses {
    private const val TAG = "bonaanalysis"
    private var entries: IndexEntries? = null

    /** How a word missing from the dictionary may be built, with what its
     * pieces mean; null if it can't be cut. Disk access: not on the main
     * thread. */
    @Synchronized
    fun find(context: Context, word: String, needsEnding: Boolean = false): ShownAnalysis? {
        val index = entries ?: try {
            IndexEntries(TrieCache.getTrie(context, "eo")).also { entries = it }
        } catch (e: IOException) {
            Log.w(TAG, "Failed to load the index of Esperanto")
            return null
        }
        /* Nothing worth showing for a beginning without its root */
        val readings = WordAnalyser.analyse(word, index, needsEnding = needsEnding)
            .filter { analysis -> analysis.pieces.any { it.kind == WordPiece.Kind.ROOT } }
            .map { ShownReading(it, shownPieces(context, index, it)) }
        return if (readings.isEmpty()) null else ShownAnalysis(readings)
    }

    private fun shownPieces(context: Context, index: IndexEntries, analysis: WordAnalysis) =
        analysis.pieces.mapNotNull { piece ->
            when {
                piece.entry != null -> index.result(piece.entry)?.let {
                    /* A suffix by its word, like "ulo", even when that word
                     * says nothing on its own (ejo) or only by the suffix
                     * (igi): the word as label, the suffix's definition */
                    val label = SUFFIX_WORDS[piece.text]?.takeIf { word -> index.result(word) != null }
                    ShownPiece(label ?: piece.entry, it, SearchPreviews.get(context, it)?.definition)
                }
                piece.kind == WordPiece.Kind.ENDING && piece.text in VERB_ENDINGS ->
                    ShownPiece("-${piece.text}", null, null, ending = piece.text)
                else -> null
            }
        }

}

private val VERB_ENDINGS = setOf("as", "is", "os", "us", "u")
private val SUFFIX_WORDS = mapOf("ej" to "ejo", "ig" to "igi", "iĝ" to "iĝi")

/* The rows shown at first; the others on demand */
private const val SHOWN_PIECES = 5

/** How a word missing from the dictionary may be built: a guess, in
 * grey, as a pencilled note, with only what can be opened marked. */
@Composable
fun WordAnalysisView(shown: ShownAnalysis, onOpen: (SearchResult) -> Unit, modifier: Modifier = Modifier) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val faint = muted.copy(alpha = 0.55f)
    val rule = muted.copy(alpha = 0.25f)
    /* The reading shown in full; a tap on "Aŭ:" swaps them */
    var selected by rememberSaveable(shown) { mutableIntStateOf(0) }
    val reading = shown.readings[selected.coerceIn(shown.readings.indices)]
    val other = shown.readings.getOrNull(1 - selected)
    var all by rememberSaveable(shown, selected) { mutableStateOf(false) }
    /* One row more is shown, rather than a button as tall to hide it */
    val folds = reading.pieces.size > SHOWN_PIECES + 1
    val rows = if (all || !folds) reading.pieces else reading.pieces.take(SHOWN_PIECES)

    Column(modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
        Text(
            stringResource(R.string.analysis_maybe),
            style = MaterialTheme.typography.bodyMedium.copy(fontStyle = FontStyle.Italic),
            color = muted,
            modifier = Modifier.padding(top = 8.dp),
        )
        Text(
            cutWord(reading.analysis, faint),
            style = MaterialTheme.typography.headlineSmall.copy(fontStyle = FontStyle.Italic),
            color = muted,
            modifier = Modifier.padding(top = 4.dp, bottom = 8.dp),
        )
        for (piece in rows) {
            HorizontalDivider(color = rule)
            Column(
                Modifier
                    .fillMaxWidth()
                    .then(if (piece.result != null) Modifier.clickable { onOpen(piece.result) } else Modifier)
                    .padding(vertical = 8.dp),
            ) {
                Text(
                    piece.label,
                    style = MaterialTheme.typography.titleMedium.copy(
                        textDecoration = if (piece.result != null) TextDecoration.Underline else null,
                    ),
                    color = MaterialTheme.colorScheme.onSurface,
                )
                val gloss = piece.gloss ?: piece.ending?.let { endingGloss(it) }
                if (!gloss.isNullOrEmpty())
                    Text(
                        gloss,
                        style = MaterialTheme.typography.bodyMedium.copy(fontStyle = FontStyle.Italic),
                        color = muted,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
            }
        }
        if (!all && folds)
            TextButton(onClick = { all = true }) {
                Text(stringResource(R.string.analysis_more, reading.pieces.size - SHOWN_PIECES))
            }
        HorizontalDivider(color = rule)
        other?.let {
            /* The other reading, discreet until it is asked for: a tap
             * shows it in full, and this one here */
            Text(
                buildAnnotatedString {
                    append(stringResource(R.string.analysis_or) + " ")
                    append(cutWord(it.analysis, faint))
                    withStyle(SpanStyle(color = faint)) { append("  ⇄") }
                },
                style = MaterialTheme.typography.bodyLarge.copy(fontStyle = FontStyle.Italic),
                color = muted,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { selected = 1 - selected }
                    .padding(vertical = 10.dp),
            )
        }
        Text(
            stringResource(R.string.analysis_note),
            style = MaterialTheme.typography.bodySmall.copy(fontStyle = FontStyle.Italic),
            color = faint,
            modifier = Modifier.padding(top = 12.dp, bottom = 16.dp),
        )
    }
}

/** "kafej + estro", "mal + antaŭvidebla": the ending with the last piece, as
 * the reader thinks of words; a verb ending apart, as it has its row
 * ("sentem + u"); the vowel that joins two pieces with the first, of
 * which it is most often the ending ("ago + povo", "dube + flava"); what
 * couldn't be recognised fainter */
private fun cutWord(analysis: WordAnalysis, faint: androidx.compose.ui.graphics.Color) = buildAnnotatedString {
    val shown = mutableListOf<String>()
    for (piece in analysis.pieces) {
        val glued = shown.isNotEmpty() &&
            (piece.kind == WordPiece.Kind.LINK || piece.kind == WordPiece.Kind.ENDING && piece.text !in VERB_ENDINGS)
        if (glued) shown[shown.lastIndex] += piece.text else shown.add(piece.text)
    }
    append(shown.joinToString(" + "))
    if (!analysis.complete)
        withStyle(SpanStyle(color = faint)) { append(" + …" + analysis.unrecognised) }
}

@Composable
private fun endingGloss(ending: String): String = stringResource(
    when (ending) {
        "as" -> R.string.ending_as
        "is" -> R.string.ending_is
        "os" -> R.string.ending_os
        "us" -> R.string.ending_us
        else -> R.string.ending_u
    }
)
