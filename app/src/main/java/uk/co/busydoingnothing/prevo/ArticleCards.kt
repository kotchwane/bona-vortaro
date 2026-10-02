/*
 * PReVo - A portable version of ReVo for Android
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

package uk.co.busydoingnothing.prevo

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.text.method.LinkMovementMethod
import android.text.style.ReplacementSpan
import android.util.TypedValue
import android.widget.TextView
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import kotlin.math.roundToInt

/** How many languages of translations a card shows before "+ n lingvoj". */
private const val SHOWN_TRANSLATIONS = 4

/** A derived word of the article: its header with its officiality, its
 * content (text and remarks) and its translations. */
@Composable
fun WordCard(entry: WordEntry, textSize: Int, onLongPress: () -> Unit, modifier: Modifier = Modifier) {
    val scale = PrevoSettings.textScale(textSize)
    val primary = MaterialTheme.colorScheme.primary

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(
            Modifier
                /* The coloured edge on the left of the card */
                .drawBehind { drawRect(primary, size = Size(4.dp.toPx(), size.height)) }
                .padding(start = 16.dp, end = 12.dp, top = 12.dp, bottom = 12.dp),
        ) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.Center,
            ) {
                SpannedText(
                    entry.title,
                    style = MaterialTheme.typography.titleLarge.scaled(scale)
                        .copy(fontWeight = FontWeight.Bold),
                    color = primary,
                    modifier = Modifier.align(Alignment.CenterVertically),
                )
                for (official in entry.official)
                    OfficialBadge(official, Modifier.align(Alignment.CenterVertically))
                for (label in entry.labels)
                    LabelChip(label, Modifier.align(Alignment.CenterVertically))
            }

            for ((index, group) in groupExamples(entry.blocks).withIndex()) {
                when (group) {
                    is BlockGroup.Single -> when (val block = group.block) {
                        is ContentBlock.Text -> SpannedText(
                            block.text,
                            style = MaterialTheme.typography.bodyLarge.scaled(scale),
                            color = MaterialTheme.colorScheme.onSurface,
                            onLongPress = onLongPress,
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                        )
                        is ContentBlock.Example -> ExampleLine(block.text, scale, onLongPress)
                        is ContentBlock.Note -> NoteBox(block.text, scale, onLongPress)
                    }
                    is BlockGroup.Examples ->
                        FoldedExamples(group.examples, scale, onLongPress, key = "${entry.section}-$index")
                    is BlockGroup.OneExample -> {
                        ExamplesTitle(stringResource(R.string.example))
                        ExampleLine(group.example.text, scale, onLongPress)
                    }
                }
            }

            if (entry.translations.isNotEmpty())
                TranslationsBox(entry.translations, scale)
        }
    }
}

/** The blocks of a word, with the runs of several examples together. */
private sealed class BlockGroup {
    class Single(val block: ContentBlock) : BlockGroup()
    class Examples(val examples: List<ContentBlock.Example>) : BlockGroup()
    class OneExample(val example: ContentBlock.Example) : BlockGroup()
}

private fun groupExamples(blocks: List<ContentBlock>): List<BlockGroup> {
    val groups = mutableListOf<BlockGroup>()
    var run = mutableListOf<ContentBlock.Example>()

    fun endRun() {
        when (run.size) {
            0 -> {}
            /* A single example is short enough to be shown as it is, with
             * a title like the folded ones */
            1 -> groups.add(BlockGroup.OneExample(run[0]))
            else -> groups.add(BlockGroup.Examples(run))
        }
        run = mutableListOf()
    }

    for (block in blocks) {
        if (block is ContentBlock.Example) {
            run.add(block)
        } else {
            endRun()
            groups.add(BlockGroup.Single(block))
        }
    }
    endRun()

    return groups
}

/** An example on its own line, with a bar on its left. */
@Composable
private fun ExampleLine(text: CharSequence, scale: Float, onLongPress: () -> Unit) {
    val bar = MaterialTheme.colorScheme.outlineVariant

    SpannedText(
        text,
        style = MaterialTheme.typography.bodyMedium.scaled(scale),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        onLongPress = onLongPress,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 6.dp)
            .drawBehind { drawRect(bar, size = Size(2.dp.toPx(), size.height)) }
            .padding(start = 10.dp),
    )
}

/** The title of a single example, in the style of the folded ones. */
@Composable
private fun ExamplesTitle(title: String) {
    Text(
        title,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 10.dp),
    )
}

/** Several examples, folded behind "Ekzemploj (n) ▾". */
@Composable
private fun FoldedExamples(
    examples: List<ContentBlock.Example>,
    scale: Float,
    onLongPress: () -> Unit,
    key: String,
) {
    var expanded by rememberSaveable(key) { mutableStateOf(false) }

    TextButton(
        onClick = { expanded = !expanded },
        contentPadding = PaddingValues(horizontal = 0.dp, vertical = 0.dp),
    ) {
        Text(
            stringResource(
                if (expanded) R.string.examples_shown else R.string.examples_folded,
                examples.size,
            ),
            style = MaterialTheme.typography.labelLarge,
        )
    }

    if (expanded) {
        for (example in examples)
            ExampleLine(example.text, scale, onLongPress)
    }
}

/** A label of the whole word: its field, or its kind. */
@Composable
private fun LabelChip(label: Label, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(8.dp),
        color = if (label.isField) colors.secondaryContainer else colors.surface,
        contentColor = if (label.isField) colors.onSecondaryContainer else colors.onSurfaceVariant,
        border = if (label.isField) null else BorderStroke(1.dp, colors.outlineVariant),
    ) {
        Text(
            label.text,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
        )
    }
}

/** Draws a field or a kind of word left within the text, eg. at the
 * start of a sense, as a small label with a tinted background. */
class InlineLabelSpan(
    private val background: Int,
    private val foreground: Int,
    private val radius: Float,
    private val padding: Float,
) : ReplacementSpan() {

    private fun labelPaint(paint: Paint) = Paint(paint).apply { textSize = paint.textSize * 0.85f }

    override fun getSize(
        paint: Paint,
        text: CharSequence,
        start: Int,
        end: Int,
        fm: Paint.FontMetricsInt?,
    ): Int {
        /* Keep the height of the line of the surrounding text */
        fm?.let { paint.getFontMetricsInt(it) }
        return (labelPaint(paint).measureText(text, start, end) + 2 * padding).roundToInt()
    }

    override fun draw(
        canvas: Canvas,
        text: CharSequence,
        start: Int,
        end: Int,
        x: Float,
        top: Int,
        y: Int,
        bottom: Int,
        paint: Paint,
    ) {
        val label = labelPaint(paint)
        val width = label.measureText(text, start, end) + 2 * padding
        val rect = RectF(x, y + label.ascent() - padding / 2, x + width, y + label.descent() + padding / 2)

        canvas.drawRoundRect(rect, radius, radius, Paint(paint).apply { color = background })
        label.color = foreground
        canvas.drawText(text, start, end, x + padding, y.toFloat(), label)
    }
}

/** "Fundamento" for "*", "n-a Oficiala Aldono" for a number. */
@Composable
private fun OfficialBadge(official: String, modifier: Modifier = Modifier) {
    val label = when {
        official == "*" -> stringResource(R.string.official_fundamento)
        official.toIntOrNull() != null -> stringResource(R.string.official_addition, official.toInt())
        else -> official
    }

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.primary,
        contentColor = MaterialTheme.colorScheme.onPrimary,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
        )
    }
}

/** A remark, in a tinted box with its title. */
@Composable
private fun NoteBox(text: CharSequence, scale: Float, onLongPress: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Column(Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
            Text(
                stringResource(R.string.remark),
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.primary,
            )
            SpannedText(
                text,
                style = MaterialTheme.typography.bodyMedium.scaled(scale),
                color = MaterialTheme.colorScheme.onSurface,
                onLongPress = onLongPress,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/** The translations of the word, the user's languages first. After a few
 * languages, the others are shown on demand. */
@Composable
private fun TranslationsBox(translations: List<Translation>, scale: Float) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    val shown = if (expanded) translations else translations.take(SHOWN_TRANSLATIONS)
    val body = MaterialTheme.typography.bodyMedium.scaled(scale)

    /* Outlined, to be told apart from the filled box of the remarks */
    Surface(
        modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(Modifier.padding(start = 10.dp, end = 10.dp, top = 8.dp)) {
            Text(
                stringResource(R.string.translations),
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.primary,
            )
            for (translation in shown) {
                Row(Modifier.padding(vertical = 3.dp)) {
                    /* The code of the language in a small pill */
                    Box(Modifier.width(38.dp).padding(top = 2.dp)) {
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = MaterialTheme.colorScheme.secondaryContainer,
                            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                        ) {
                            Text(
                                translation.language.uppercase(),
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp),
                            )
                        }
                    }
                    Text(
                        translation.text,
                        style = body,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            if (translations.size > SHOWN_TRANSLATIONS) {
                TextButton(onClick = { expanded = !expanded }) {
                    Text(
                        if (expanded) stringResource(R.string.fewer_languages)
                        else stringResource(R.string.more_languages, translations.size - SHOWN_TRANSLATIONS)
                    )
                }
            } else {
                Spacer(Modifier.height(8.dp))
            }
        }
    }
}

/** Text with the spans of the articles (links, superscripts, italics…),
 * drawn by a TextView, which handles them. */
@Composable
fun SpannedText(
    text: CharSequence,
    style: TextStyle,
    color: Color,
    modifier: Modifier = Modifier,
    onLongPress: (() -> Unit)? = null,
) {
    val linkColor = MaterialTheme.colorScheme.primary

    AndroidView(
        factory = { context ->
            TextView(context).apply {
                movementMethod = LinkMovementMethod.getInstance()
                setLineSpacing(0f, 1.2f)
            }
        },
        update = { view ->
            view.setText(text, TextView.BufferType.SPANNABLE)
            view.setTextColor(color.toArgb())
            view.setLinkTextColor(linkColor.toArgb())
            view.setTextSize(TypedValue.COMPLEX_UNIT_SP, style.fontSize.value)
            view.typeface = if ((style.fontWeight?.weight ?: 400) >= 600)
                android.graphics.Typeface.DEFAULT_BOLD else android.graphics.Typeface.DEFAULT
            if (onLongPress != null)
                view.setOnLongClickListener { onLongPress(); true }
        },
        modifier = modifier,
    )
}

private fun TextStyle.scaled(scale: Float) =
    copy(fontSize = fontSize * scale, lineHeight = lineHeight * scale)
