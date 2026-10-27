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

import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp

/** "malrekomendata", after a word that "Vortoj de la bona lingvo" advises
 * against: a label like the kinds of words ("transitiva") */
@Composable
fun DiscouragedChip(modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(8.dp),
        color = colors.surface,
        contentColor = colors.onSurfaceVariant,
        border = BorderStroke(1.dp, colors.outlineVariant),
    ) {
        Text(
            stringResource(R.string.bl_discouraged),
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 1.dp),
        )
    }
}

/** "prefere rulŝtuparo, …", under a word advised against in the search,
 * "ankaŭ eblas diri …" under one with simpler alternatives: the words to
 * say, in the colour of the headwords */
@Composable
fun preferredLine(entry: BonaLingvoEntry): AnnotatedString {
    val headword = bonaDesign.headword
    return buildAnnotatedString {
        /* With the contexts when the advice is for some senses only */
        if (entry.limited && entry.context.isNotEmpty())
            append("${entry.context} ")
        append(stringResource(if (entry.discouraged) R.string.bl_preferably else R.string.bl_also_say) + " ")
        entry.groups.forEachIndexed { group, (words, context) ->
            if (group > 0)
                append("; ")
            words.forEachIndexed { index, word ->
                if (index > 0)
                    append(", ")
                withStyle(SpanStyle(fontStyle = FontStyle.Normal, fontWeight = FontWeight.Bold, color = headword)) { append(word) }
            }
            if (context.isNotEmpty())
                append(" $context")
        }
    }
}

/** A word only "Vortoj de la bona lingvo" has, in the search or the
 * history: not bold, as the words that aren't Reta Vortaro's; marked if
 * advised against; what to say instead under it. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun BonaLingvoRow(
    entry: BonaLingvoEntry,
    onClick: () -> Unit,
    /** Shown small at the right of the word, eg. the language "EO" */
    label: String? = null,
    onLongClick: (() -> Unit)? = null,
    action: (@Composable () -> Unit)? = null,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(start = 20.dp, end = if (action != null) 8.dp else 20.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                /* The label after the word, on the next line when it doesn't
                 * fit: never the word broken to make room for it */
                FlowRow(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    itemVerticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(entry.word, style = MaterialTheme.typography.titleMedium, color = bonaDesign.headword)
                    if (entry.discouragedEverywhere)
                        DiscouragedChip()
                }
                if (label != null)
                    Text(
                        label,
                        style = MaterialTheme.typography.labelMedium,
                        letterSpacing = 1.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 12.dp),
                    )
            }
            /* All the words to say, on several lines if need be */
            Text(
                preferredLine(entry),
                style = MaterialTheme.typography.bodyMedium.copy(fontStyle = FontStyle.Italic),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        action?.invoke()
    }
}

/** "Vortoj de la bona lingvo proponas „uato“ anstataŭ ŭato.", for a word
 * the list proposes that no dictionary has, typed in full: each word it is
 * proposed instead of a link, to its article or its page */
@Composable
fun ProposedInstead(
    word: String,
    instead: List<Pair<BonaLingvoEntry, SearchResult?>>,
    onOpenEntry: (SearchResult) -> Unit,
    onOpenListWord: (BonaLingvoEntry) -> Unit,
    modifier: Modifier = Modifier,
) {
    val link = TextLinkStyles(SpanStyle(color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold, textDecoration = TextDecoration.Underline))
    val text = stringResource(R.string.bl_proposes, "\u0001", word, "\u0002")
    val name = stringResource(R.string.bl_name)
    Text(
        buildAnnotatedString {
            append(text.substringBefore('\u0001'))
            withStyle(SpanStyle(fontStyle = FontStyle.Italic)) { append(name) }
            append(text.substringAfter('\u0001').substringBefore('\u0002'))
            instead.forEachIndexed { index, (entry, found) ->
                if (index > 0)
                    append(", ")
                withLink(LinkAnnotation.Clickable(entry.word, link) { if (found != null) onOpenEntry(found) else onOpenListWord(entry) }) {
                    append(entry.word)
                }
            }
            append(text.substringAfter('\u0002'))
        },
        style = MaterialTheme.typography.bodyLarge,
        modifier = modifier.padding(horizontal = 20.dp, vertical = 16.dp),
    )
}
