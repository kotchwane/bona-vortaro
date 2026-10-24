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

import androidx.compose.foundation.BorderStroke
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

/** "prefere rulŝtuparo, …", under a word advised against in the search:
 * the words to say, in the colour of the headwords */
@Composable
fun preferredLine(entry: BonaLingvoEntry): AnnotatedString {
    val headword = bonaDesign.headword
    val preferred = entry.preferred
    return buildAnnotatedString {
        append(stringResource(R.string.bl_preferably) + " ")
        preferred.forEachIndexed { index, it ->
            if (index > 0)
                append(", ")
            withStyle(SpanStyle(fontStyle = FontStyle.Normal, fontWeight = FontWeight.Bold, color = headword)) { append(it.word) }
        }
    }
}
