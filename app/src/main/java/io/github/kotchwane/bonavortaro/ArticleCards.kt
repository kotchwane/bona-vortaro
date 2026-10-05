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

import android.graphics.Canvas
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.runtime.remember
import androidx.compose.foundation.gestures.detectTapGestures
import android.view.MotionEvent
import android.graphics.Paint
import android.graphics.RectF
import android.text.method.LinkMovementMethod
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.text.style.ReplacementSpan
import android.util.TypedValue
import android.widget.TextView
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
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
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** How many languages of translations a card shows before "+ n lingvoj",
 * when it shows all of the languages. */
private const val SHOWN_TRANSLATIONS = 4

/** A single example up to this length (in characters) is put at the end
 * of the text before it, like in a printed dictionary. */
private const val INLINE_EXAMPLE_LENGTH = 70

/** Examples up to this total length are shown, longer ones are folded
 * behind "Ekzemploj (n) ▾". */
private const val SHOWN_EXAMPLES_LENGTH = 120

/** A derived word of the article: its header with its officiality, its
 * content (text and remarks) and its translations. */
@Composable
fun WordCard(
    entry: WordEntry,
    textSize: Int,
    unfoldExamples: Boolean,
    foldTranslations: Boolean,
    /** A long press on the headword, or where there is no word */
    onLongPress: () -> Unit,
    /** A long press on a word of the text, with its language */
    onLongPressWord: (word: String, language: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scale = BonaSettings.textScale(textSize)
    val onLongPressText: (String?) -> Unit = { word ->
        if (word != null) onLongPressWord(word, "eo") else onLongPress()
    }
    val primary = MaterialTheme.colorScheme.primary

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        /* A sheet of paper on the page, with a thin edge instead of a
         * shadow */
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
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
                    color = bonaDesign.headword,
                    onLongPress = { onLongPress() },
                    modifier = Modifier.align(Alignment.CenterVertically),
                )
                for (official in entry.official)
                    OfficialBadge(official, Modifier.align(Alignment.CenterVertically))
                for (label in entry.labels)
                    LabelChip(label, Modifier.align(Alignment.CenterVertically))
            }

            val exampleColor = MaterialTheme.colorScheme.onSurfaceVariant.toArgb()

            for ((index, group) in groupExamples(entry.blocks, exampleColor).withIndex()) {
                when (group) {
                    is BlockGroup.Single -> when (val block = group.block) {
                        is ContentBlock.Text -> SpannedText(
                            block.text,
                            style = MaterialTheme.typography.bodyLarge.scaled(scale),
                            color = MaterialTheme.colorScheme.onSurface,
                            onLongPress = onLongPressText,
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                        )
                        is ContentBlock.Example -> ExampleLine(block.text, scale, onLongPressText)
                        is ContentBlock.Note -> NoteBox(block.text, scale, onLongPressText)
                    }
                    is BlockGroup.Examples ->
                        FoldedExamples(group.examples, scale, onLongPressText, key = "${entry.section}-$index", unfolded = unfoldExamples)
                    is BlockGroup.ShortExamples ->
                        for (example in group.examples)
                            ExampleLine(example.text, scale, onLongPressText)
                }
            }

            if (entry.translations.isNotEmpty())
                TranslationsBox(entry.translations, scale, foldTranslations, onLongPressWord)
        }
    }
}

/** The blocks of a word, with the runs of several examples together. */
private sealed class BlockGroup {
    class Single(val block: ContentBlock) : BlockGroup()
    /** Folded */
    class Examples(val examples: List<ContentBlock.Example>) : BlockGroup()
    /** Shown, without a title */
    class ShortExamples(val examples: List<ContentBlock.Example>) : BlockGroup()
}

/* Whether the examples are folded depends on their length, not on
 * their number: a title and a tap cost more than a few short words. */
private fun groupExamples(blocks: List<ContentBlock>, exampleColor: Int): List<BlockGroup> {
    val groups = mutableListOf<BlockGroup>()
    var run = mutableListOf<ContentBlock.Example>()

    fun endRun() {
        val before = (groups.lastOrNull() as? BlockGroup.Single)?.block as? ContentBlock.Text

        when {
            run.isEmpty() -> {}
            run.size == 1 && run[0].text.length <= INLINE_EXAMPLE_LENGTH && before != null ->
                groups[groups.lastIndex] =
                    BlockGroup.Single(ContentBlock.Text(withExample(before.text, run[0].text, exampleColor)))
            run.sumOf { it.text.length } <= SHOWN_EXAMPLES_LENGTH ->
                groups.add(BlockGroup.ShortExamples(run))
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

/** The text followed by a short example, in the colour of the
 * examples. The example keeps its italics and its links. */
private fun withExample(text: CharSequence, example: CharSequence, color: Int): CharSequence {
    val result = SpannableStringBuilder(text).append(' ')
    val start = result.length

    result.append(example)
    result.setSpan(ForegroundColorSpan(color), start, result.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)

    return result
}

/** An example on its own line, with a bar on its left. */
@Composable
private fun ExampleLine(text: CharSequence, scale: Float, onLongPress: (String?) -> Unit) {
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

/** Several examples, folded behind "Ekzemploj (n) ▾". */
@Composable
private fun FoldedExamples(
    examples: List<ContentBlock.Example>,
    scale: Float,
    onLongPress: (String?) -> Unit,
    key: String,
    unfolded: Boolean,
) {
    /* Unfolded at first if the user chose it, and can still be folded */
    var expanded by rememberSaveable(key, unfolded) { mutableStateOf(unfolded) }

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
        fm?.let { fitPill(it, paint, labelPaint(paint), padding, 0f) }
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

/** The height of the line of the surrounding text, made taller if
 * needed so that a pill drawn around the smaller label text isn't cut
 * at the top or the bottom of the line. */
private fun fitPill(fm: Paint.FontMetricsInt, paint: Paint, label: Paint, padding: Float, stroke: Float) {
    paint.getFontMetricsInt(fm)

    val pillTop = floor(label.ascent() - padding / 2 - stroke).toInt()
    val pillBottom = ceil(label.descent() + padding / 2 + stroke).toInt()

    fm.ascent = min(fm.ascent, pillTop)
    fm.top = min(fm.top, pillTop)
    fm.descent = max(fm.descent, pillBottom)
    fm.bottom = max(fm.bottom, pillBottom)
}

/** Draws the symbol of the type of a link as a word in a rounded pill,
 * eg. "subnocioj" instead of "↘", in the colour of its family. */
class LinkTypeLabelSpan(
    private val label: String,
    private val color: Int,
    private val radius: Float,
    private val padding: Float,
    private val stroke: Float,
) : ReplacementSpan() {

    private fun labelPaint(paint: Paint) = Paint(paint).apply {
        textSize = paint.textSize * 0.78f
        isFakeBoldText = true
        color = this@LinkTypeLabelSpan.color
    }

    override fun getSize(paint: Paint, text: CharSequence, start: Int, end: Int, fm: Paint.FontMetricsInt?): Int {
        fm?.let { fitPill(it, paint, labelPaint(paint), padding, stroke) }
        /* The width of the pill and a space after it */
        return (labelPaint(paint).measureText(label) + 2 * padding + padding).roundToInt()
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
        val width = label.measureText(this.label) + 2 * padding
        /* The outline is drawn inside the pill, not across its edges */
        val inset = stroke / 2
        val rect = RectF(
            x + inset,
            y + label.ascent() - padding / 2 + inset,
            x + width - inset,
            y + label.descent() + padding / 2 - inset,
        )

        /* A light tint of the colour, and an outline */
        val fill = Paint(paint).apply {
            color = this@LinkTypeLabelSpan.color
            alpha = 0x1f
            style = Paint.Style.FILL
        }
        val outline = Paint(paint).apply {
            color = this@LinkTypeLabelSpan.color
            style = Paint.Style.STROKE
            strokeWidth = stroke
        }
        canvas.drawRoundRect(rect, radius, radius, fill)
        canvas.drawRoundRect(rect, radius, radius, outline)
        canvas.drawText(this.label, x + padding, y.toFloat(), label)
    }
}

/** "Fundamento" for "*", "n-a Oficiala Aldono" for a number. A tap
 * explains what it means, with a small ⓘ to show that it can be tapped. */
@Composable
fun OfficialBadge(official: String, modifier: Modifier = Modifier) {
    val level = officialLevel(official)
    val label = when (level) {
        null -> official
        0 -> stringResource(R.string.official_fundamento)
        else -> stringResource(R.string.official_addition, level)
    }
    var explain by rememberSaveable { mutableStateOf(false) }

    Surface(
        onClick = { explain = true },
        enabled = level != null,
        modifier = modifier,
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.primary,
        contentColor = MaterialTheme.colorScheme.onPrimary,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(start = 8.dp, end = if (level != null) 5.dp else 8.dp, top = 2.dp, bottom = 2.dp),
        ) {
            Text(label, style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold))
            if (level != null)
                Icon(
                    Icons.Outlined.Info,
                    contentDescription = stringResource(R.string.official_explain),
                    modifier = Modifier.padding(start = 3.dp).size(13.dp),
                )
        }
    }

    if (explain && level != null)
        OfficialSheet(level, onDismiss = { explain = false })
}

/** A remark, in a tinted box with its title. */
@Composable
private fun NoteBox(text: CharSequence, scale: Float, onLongPress: (String?) -> Unit) {
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
private fun TranslationsBox(
    translations: List<Translation>,
    scale: Float,
    foldable: Boolean,
    onLongPressWord: (word: String, language: String) -> Unit,
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    val folded = foldable && !expanded
    val shown = if (folded) translations.take(SHOWN_TRANSLATIONS) else translations
    val body = MaterialTheme.typography.bodyMedium.scaled(scale)

    /* Outlined, to be told apart from the filled box of the remarks */
    Surface(
        modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
        shape = RoundedCornerShape(10.dp),
        color = Color.Transparent,
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
                    /* A long press looks up the word under the finger in
                     * the language of the translation */
                    var layout by remember { mutableStateOf<TextLayoutResult?>(null) }
                    Text(
                        translation.text,
                        style = body,
                        onTextLayout = { layout = it },
                        modifier = Modifier
                            .weight(1f)
                            .pointerInput(translation) {
                                detectTapGestures(onLongPress = { position ->
                                    val offset = layout?.getOffsetForPosition(position) ?: return@detectTapGestures
                                    WordLookup.wordAt(translation.text, offset)
                                        ?.let { onLongPressWord(it, translation.language) }
                                })
                            },
                    )
                }
            }
            if (foldable && translations.size > SHOWN_TRANSLATIONS) {
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
    /** With the word pressed, or null if the press wasn't on a word */
    onLongPress: ((String?) -> Unit)? = null,
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
            /* Serif, like the rest of the design */
            view.typeface = android.graphics.Typeface.create(
                android.graphics.Typeface.SERIF,
                if ((style.fontWeight?.weight ?: 400) >= 600) android.graphics.Typeface.BOLD
                else android.graphics.Typeface.NORMAL,
            )
            if (onLongPress != null) {
                /* Where the finger went down, to find the word under it;
                 * the touch goes on to the links */
                val down = FloatArray(2)
                view.setOnTouchListener { _, event ->
                    if (event.actionMasked == MotionEvent.ACTION_DOWN) {
                        down[0] = event.x
                        down[1] = event.y
                    }
                    false
                }
                view.setOnLongClickListener {
                    val offset = view.getOffsetForPosition(down[0], down[1])
                    onLongPress(WordLookup.wordAt(view.text, offset))
                    true
                }
            }
        },
        modifier = modifier,
    )
}

private fun TextStyle.scaled(scale: Float) =
    copy(fontSize = fontSize * scale, lineHeight = lineHeight * scale)
