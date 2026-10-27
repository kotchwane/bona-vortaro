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

import android.content.ContextWrapper
import android.net.Uri
import android.text.style.ClickableSpan
import android.view.View
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.ui.platform.LocalContext
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

/** What "Vortoj de la bona lingvo" is, from the "ⓘ" after its name: its
 * principle, in its own words (labonalingvo.org), as it gives no reason
 * for each word, and who made it */
@Composable
fun BonaLingvoInfoDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.bl_name)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.bl_principle_intro))
                Text(stringResource(R.string.bl_principle), fontStyle = FontStyle.Italic)
                Text(stringResource(R.string.bl_authors), style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.close)) }
        },
        dismissButton = {
            TextButton(onClick = { openOutside(context, Uri.parse(BonaLingvo.SITE)) }) {
                Text("${stringResource(R.string.about_bl_link)} $OUTSIDE_SIGN")
            }
        },
    )
}

/** A screen that shows what "Vortoj de la bona lingvo" is, when its "ⓘ"
 * is tapped in a text */
interface BonaLingvoInfoHost {
    fun showBonaLingvoInfo()
}

/** The "ⓘ" after the name of the list in a remark of an article */
class BonaLingvoInfoSpan : ClickableSpan() {
    override fun onClick(widget: View) {
        var context = widget.context
        while (context !is BonaLingvoInfoHost && context is ContextWrapper)
            context = context.baseContext
        (context as? BonaLingvoInfoHost)?.showBonaLingvoInfo()
    }
}
