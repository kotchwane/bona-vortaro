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
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import java.io.IOException

/** A root of the dictionary drawn at random, to discover words. */
object RandomRoot {
    /* The articles are in files of 16, the last one partly filled */
    private const val ARTICLES_PER_FILE = 16

    @Volatile
    private var count: Int? = null

    /** The number of articles, counted once. Disk access. */
    private fun count(context: Context): Int =
        count ?: synchronized(this) {
            count ?: run {
                val files = context.assets.list("articles")?.size ?: 0
                if (files == 0) return@run 0
                val last = String.format(java.util.Locale.US, "articles/article-%03xx.bin", files - 1)
                var inLast = 0
                context.assets.open(last).use { stream ->
                    val input = BinaryReader(stream)
                    try {
                        while (inLast < ARTICLES_PER_FILE) {
                            input.skip(input.readInt().toLong())
                            inLast++
                        }
                    } catch (e: IOException) {
                        /* The end of the last file */
                    }
                }
                (files - 1) * ARTICLES_PER_FILE + inLast
            }.also { count = it }
        }

    /** The number of an article drawn at random. Disk access. */
    fun pick(context: Context): Int = (0 until count(context)).random()
}

/** A die showing five, drawn in a colour: there is none among the icons. */
@Composable
fun Dice(color: Color, modifier: Modifier = Modifier, size: Dp = 24.dp) {
    Canvas(modifier.size(size)) {
        val side = this.size.minDimension
        val stroke = side * 0.09f
        drawRoundRect(
            color,
            topLeft = Offset(stroke / 2, stroke / 2),
            size = Size(side - stroke, side - stroke),
            cornerRadius = CornerRadius(side * 0.22f),
            style = Stroke(stroke),
        )
        for ((x, y) in listOf(0.3f to 0.3f, 0.7f to 0.3f, 0.5f to 0.5f, 0.3f to 0.7f, 0.7f to 0.7f))
            drawCircle(color, side * 0.085f, Offset(side * x, side * y))
    }
}

/** On the search, before anything is typed: a root to discover. */
@Composable
fun RandomRootButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant

    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, muted.copy(alpha = 0.3f)),
        modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Dice(bonaDesign.headword, size = 30.dp)
            Column(Modifier.weight(1f).padding(start = 16.dp)) {
                Text(
                    stringResource(R.string.random_root),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = bonaDesign.headword,
                )
                Text(
                    stringResource(R.string.random_root_summary),
                    style = MaterialTheme.typography.bodyMedium,
                    color = muted,
                )
            }
            Text("›", style = MaterialTheme.typography.titleLarge, color = muted)
        }
    }
}
