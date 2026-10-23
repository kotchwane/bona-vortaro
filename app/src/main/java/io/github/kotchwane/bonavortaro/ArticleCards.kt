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

import android.text.style.RelativeSizeSpan
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.res.painterResource
import android.graphics.drawable.Drawable
import androidx.compose.runtime.produceState
import android.content.Context
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.style.TextDecoration
import android.net.Uri
import androidx.compose.runtime.LaunchedEffect
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.withContext
import androidx.compose.ui.res.stringArrayResource
import android.graphics.Canvas
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.withStyle
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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Surface
import androidx.compose.ui.geometry.Offset
import androidx.compose.runtime.rememberUpdatedState
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.filterNotNull
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.delay
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.window.Popup
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.runtime.key
import androidx.compose.ui.window.PopupProperties
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
    unfoldExamples: Boolean,
    foldTranslations: Boolean,
    /** A long press on the headword, or where there is no word */
    onLongPress: () -> Unit,
    /** A long press on a word of the text, with its language */
    onLongPressWord: (word: String, language: String) -> Unit,
    modifier: Modifier = Modifier,
    vikiSearch: VikiSearch? = null,
    /** Whether Vikivortaro's French is shown: the French translations are */
    vikiShown: Boolean = false,
    /** Whether the Wiktionary adds words to this card */
    onViki: (Boolean) -> Unit = {},
    /** The example whose line of translations is shown: what was searched */
    example: SearchedExample? = null,
    /** Whether the list was scrolled to the card it was opened at: the
     * example's line is brought into view after */
    scrolled: Boolean = true,
) {
    /* What the French Wiktionary adds to the French translation of this
     * word, for each of its forms ("kukurbeto, itala kukurbo"); the French
     * word searched first, if the article was opened from it */
    val context = LocalContext.current
    val viki by produceState(emptyList<String>(), entry, vikiSearch, vikiShown) {
        /* Only for those who chose to see the French translations; again
         * when that changes in the settings */
        if (!vikiShown) {
            value = emptyList()
            return@produceState
        }
        value = withContext(Dispatchers.IO) { vikiWordsOf(context, entry, vikiSearch) }
    }
    /* And to its examples that Reta Vortaro translates, but not into
     * French: "bonvolu", "s'il vous plaît" */
    val vikiExamples by produceState(emptyList<VikiExample>(), entry, vikiShown, example) {
        if (!vikiShown) {
            value = emptyList()
            return@produceState
        }
        value = withContext(Dispatchers.IO) { vikiExamplesOf(context, entry, example) }
    }
    LaunchedEffect(viki, vikiExamples) { onViki(viki.isNotEmpty() || vikiExamples.isNotEmpty()) }
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
                    style = MaterialTheme.typography.titleLarge
                        .copy(fontWeight = FontWeight.Bold),
                    color = bonaDesign.headword,
                    onLongPress = { onLongPress() },
                    modifier = Modifier.align(Alignment.CenterVertically),
                )
                /* One badge, the oldest, with the number of the others; the
                 * panel tells which form has which */
                entry.official.firstOrNull()?.let {
                    OfficialBadge(
                        it,
                        Modifier.align(Alignment.CenterVertically),
                        others = entry.official.drop(1),
                        showOthers = true,
                        forms = entry.forms,
                    )
                }
                for (label in entry.labels)
                    LabelChip(label, Modifier.align(Alignment.CenterVertically))
            }

            val exampleColor = MaterialTheme.colorScheme.onSurfaceVariant.toArgb()

            for ((index, group) in groupExamples(entry.blocks, exampleColor).withIndex()) {
                when (group) {
                    is BlockGroup.Single -> when (val block = group.block) {
                        is ContentBlock.Text -> SpannedText(
                            compactParagraphs(block.text),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                            onLongPress = onLongPressText,
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                        )
                        is ContentBlock.Example -> ExampleLine(block.text, onLongPressText)
                        is ContentBlock.Note ->
                            NoteBox(block.text, key = "${entry.section}-note-$index", unfolded = unfoldExamples, onLongPressText)
                    }
                    is BlockGroup.Examples ->
                        FoldedExamples(group.examples, onLongPressText, key = "${entry.section}-$index", unfolded = unfoldExamples)
                    is BlockGroup.ShortExamples ->
                        for (example in group.examples)
                            ExampleLine(example.text, onLongPressText)
                }
            }

            if (entry.translations.isNotEmpty() || viki.isNotEmpty() || vikiExamples.isNotEmpty())
                TranslationsBox(entry.translations, foldTranslations, onLongPressWord, viki, example, vikiExamples, scrolled)
        }
    }
}

/** A text as shown: a label alone in its paragraph joins the next one,
 * "(malofte) Letero:" rather than on a line of its own, and between two
 * paragraphs the empty line is half as high, closer to the space between
 * two blocks (epistolo: "Letero:", "a) …", "b) …"). */
private fun compactParagraphs(text: CharSequence): CharSequence {
    if ("\n\n" !in text)
        return text
    val result = SpannableStringBuilder(text)
    Regex("""^\([^()\n]*\) *\n\n""").find(result)?.let { label ->
        result.replace(label.value.indexOf(')') + 1, label.range.last + 1, " ")
    }
    var at = result.indexOf("\n\n")
    while (at >= 0) {
        /* The second line break is the empty line */
        result.setSpan(RelativeSizeSpan(0.5f), at + 1, at + 2, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        at = result.indexOf("\n\n", at + 2)
    }
    return result
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
            /* With its source, an example has its own line, like the
             * others, rather than making the definition longer */
            run.size == 1 && run[0].text.length <= INLINE_EXAMPLE_LENGTH && !run[0].text.hasSource() && before != null ->
                groups[groups.lastIndex] =
                    BlockGroup.Single(ContentBlock.Text(withExample(before.text, run[0].text, exampleColor)))
            run.sumOf { it.text.withoutSourceLength() } <= SHOWN_EXAMPLES_LENGTH ->
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

private fun CharSequence.hasSource(): Boolean =
    (this as? Spanned)?.getSpans(0, length, ExampleSource::class.java)?.isNotEmpty() == true

/** The length of an example without its source: shown or folded as it
 * was before the sources were */
private fun CharSequence.withoutSourceLength(): Int =
    length - ((this as? Spanned)?.getSpans(0, length, ExampleSource::class.java)
        ?.sumOf { getSpanEnd(it) - getSpanStart(it) + 1 } ?: 0)

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
private fun ExampleLine(
    text: CharSequence,
    onLongPress: (String?) -> Unit,
    onClick: (() -> Unit)? = null,
    onLayout: ((TextView) -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val bar = MaterialTheme.colorScheme.outlineVariant

    SpannedText(
        text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        onLongPress = onLongPress,
        onClick = onClick,
        onLayout = onLayout,
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 6.dp)
            .drawBehind { drawRect(bar, size = Size(2.dp.toPx(), size.height)) }
            .padding(start = 10.dp),
    )
}

/** The examples and remarks whose folding the reader changed, by their
 * key, for the whole article. The article screen keeps them rather than
 * their card: the state a card saves is lost when the screen is left
 * while the card is off screen. */
val LocalToggledParts = staticCompositionLocalOf<SnapshotStateList<String>?> { null }

/** Whether a part is unfolded, and how to change it: [unfolded] at first,
 * as the settings say, or as the reader made it. */
@Composable
private fun unfoldedState(key: String, unfolded: Boolean): Pair<Boolean, (Boolean) -> Unit> {
    val toggled = LocalToggledParts.current
    if (toggled != null) {
        val shown = unfolded != (key in toggled)
        return shown to { value ->
            if (value != shown) {
                if (!toggled.remove(key))
                    toggled.add(key)
            }
        }
    }
    var expanded by rememberSaveable(key, unfolded) { mutableStateOf(unfolded) }
    return expanded to { expanded = it }
}

/** Several examples, or one long one, folded: the first shown, cut to
 * its first lines if it is long, then "5 pliaj ekzemploj ▾", which says
 * plainly that there are more; a single one ends with "… legi plu ▾", as
 * a long remark. Unfolded, all of them, then "Faldi la ekzemplojn ▴". */
@Composable
private fun FoldedExamples(
    examples: List<ContentBlock.Example>,
    onLongPress: (String?) -> Unit,
    key: String,
    unfolded: Boolean,
) {
    /* Unfolded at first if the user chose it, and can still be folded */
    val (expanded, setExpanded) = unfoldedState(key, unfolded)
    val link = MaterialTheme.colorScheme.primary
    val first = examples.first().text
    val more = if (examples.size == 1) stringResource(R.string.note_more) else ""
    /* The first example cut at the end of its last shown line, for the
     * width and the size it was measured with; null while it fits. Kept
     * while unfolded, so that folding again doesn't show it whole first */
    var folded by remember(first) { mutableStateOf<FoldedNote?>(null) }
    var measured by remember(first) { mutableStateOf(false) }
    val measure: (TextView) -> Unit = { view ->
        val width = view.layout.width
        val size = view.paint.textSize
        if (!measured || folded?.let { it.width != width || it.size != size } == true) {
            folded = foldNote(first, view.paint, width, more, link.toArgb(), FOLD_EXAMPLE_ABOVE, FOLDED_EXAMPLE_LINES) {
                setExpanded(true)
            }
            measured = true
        }
    }
    /* Something to unfold: other examples, or the end of the first one */
    val hidden = examples.size > 1 || folded != null

    val start = remember { BringIntoViewRequester() }
    val top = topWithRoom()
    val scope = rememberCoroutineScope()
    if (expanded) {
        ExampleLine(first, onLongPress, onLayout = measure, modifier = Modifier.bringIntoViewRequester(start))
        for (example in examples.drop(1))
            ExampleLine(example.text, onLongPress)
        if (hidden)
            FoldLink(stringResource(R.string.examples_less), link) {
                /* Back to the start of the group first: folding removes
                 * the text above the link, and the list, kept at its
                 * offset, would land far past the card */
                scope.launch {
                    start.bringIntoView(top)
                    setExpanded(false)
                }
            }
        return
    }

    ExampleLine(
        folded?.text ?: first,
        onLongPress,
        onClick = if (hidden) ({ setExpanded(true) }) else null,
        /* Measured again when the width or the text size change */
        onLayout = measure,
    )
    if (examples.size > 1)
        FoldLink(
            if (examples.size == 2) stringResource(R.string.examples_one_more)
            else stringResource(R.string.examples_more, examples.size - 1),
            link,
        ) { setExpanded(true) }
}

/** "5 pliaj ekzemploj ▾", "Faldi la ekzemplojn ▴": as wide as the card,
 * which makes it easy to tap, without a large empty space around it */
@Composable
private fun FoldLink(text: String, color: Color, onClick: () -> Unit) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = color,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClick = onClick)
            .padding(top = 6.dp, bottom = 4.dp),
    )
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
        val style = MaterialTheme.typography.labelMedium
        val icon = label.field?.let { FIELD_ICONS[it] }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
        ) {
            /* As high as the letters: the chip keeps its height */
            if (icon != null) {
                val painter = painterResource(icon)
                /* As high as the letters, so that its details can be made out */
                val height = with(LocalDensity.current) { (style.fontSize * 1.2f).toDp() }
                Icon(
                    painter,
                    contentDescription = null,
                    modifier = Modifier
                        .padding(end = 4.dp)
                        .size(height * (painter.intrinsicSize.width / painter.intrinsicSize.height), height),
                )
            }
            Text(
                /* "landokodo: DE": an abbreviation says what it is */
                if (label.abbreviation < 0) label.text
                else stringArrayResource(R.array.abbreviation_kinds).getOrElse(label.abbreviation) { "" }
                    .let { kind -> if (kind.isEmpty()) label.text else "$kind: ${label.text}" },
                style = style,
            )
        }
    }
}

/** Draws a field or a kind of word left within the text, eg. at the
 * start of a sense, as a small label with a tinted background, after the
 * icon of the field if it has one, as high as the letters. */
class InlineLabelSpan(
    private val background: Int,
    private val foreground: Int,
    private val radius: Float,
    private val padding: Float,
    private val icon: Drawable? = null,
) : ReplacementSpan() {

    private fun labelPaint(paint: Paint) = Paint(paint).apply { textSize = paint.textSize * 0.85f }

    /* The icon's height: the whole height of the letters, so that its
     * details can be made out; its width as the drawing's */
    private fun iconSize(label: Paint) = if (icon == null) 0f else label.textSize

    private fun iconDrawnWidth(label: Paint) =
        icon?.let { iconSize(label) * it.intrinsicWidth / it.intrinsicHeight.coerceAtLeast(1) } ?: 0f

    private fun iconWidth(label: Paint) = if (icon == null) 0f else iconDrawnWidth(label) + padding * 0.75f

    override fun getSize(
        paint: Paint,
        text: CharSequence,
        start: Int,
        end: Int,
        fm: Paint.FontMetricsInt?,
    ): Int {
        val label = labelPaint(paint)
        fm?.let { fitPill(it, paint, label, padding, 0f) }
        return (label.measureText(text, start, end) + iconWidth(label) + 2 * padding).roundToInt()
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
        val width = label.measureText(text, start, end) + iconWidth(label) + 2 * padding
        val rect = RectF(x, y + label.ascent() - padding / 2, x + width, y + label.descent() + padding / 2)

        canvas.drawRoundRect(rect, radius, radius, Paint(paint).apply { color = background })
        icon?.let {
            /* Centred on the letters without descender */
            val size = iconSize(label)
            val middle = y - label.textSize * 0.36f
            it.setBounds(
                (x + padding).roundToInt(),
                (middle - size / 2).roundToInt(),
                (x + padding + iconDrawnWidth(label)).roundToInt(),
                (middle + size / 2).roundToInt(),
            )
            it.setTint(foreground)
            it.draw(canvas)
        }
        label.color = foreground
        canvas.drawText(text, start, end, x + padding + iconWidth(label), y.toFloat(), label)
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
fun OfficialBadge(
    official: String,
    modifier: Modifier = Modifier,
    /** The other officialities of the same word, shown in its panel */
    others: List<String> = emptyList(),
    /** Whether the label counts them: "Fundamento +1" */
    showOthers: Boolean = false,
    /** The forms of the title, for the panel to tell which has which */
    forms: OfficialTitle? = null,
    /** Only its sign in a circle, "F" or "8", as in the timeline: where the
     * root would be cut by the whole badge */
    compact: Boolean = false,
) {
    val level = officialLevel(official)
    val label = officialLabel(official, others, showOthers)
    var explain by rememberSaveable { mutableStateOf(false) }

    if (compact && level != null)
        Surface(
            onClick = { explain = true },
            modifier = modifier.semantics { contentDescription = label },
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
        ) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.defaultMinSize(28.dp, 28.dp)) {
                Text(
                    if (level == 0) "F" else "$level",
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                    modifier = Modifier.padding(horizontal = 6.dp),
                )
            }
        }
    else Surface(
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
                    modifier = Modifier.padding(start = 3.dp).size(with(LocalDensity.current) { 13.sp.toDp() }),
                )
        }
    }

    /* The panel at the size of the other panels, not of the text */
    if (explain && level != null) UnscaledText {
        OfficialSheet(
            level,
            also = others.mapNotNull { officialLevel(it) }.toSet(),
            forms = forms?.takeIf { it.formsDiffer }?.forms.orEmpty(),
            onDismiss = { explain = false },
        )
    }
}

/** The words of an officiality badge: "Fundamento", "8a Oficiala
 * Aldono", with the number of the others if asked: "Fundamento +1" */
@Composable
fun officialLabel(official: String, others: List<String> = emptyList(), showOthers: Boolean = false): String {
    val name = when (val level = officialLevel(official)) {
        null -> official
        0 -> stringResource(R.string.official_fundamento)
        else -> stringResource(R.string.official_addition, level)
    }
    return if (showOthers && others.isNotEmpty()) "$name +${others.size}" else name
}

/** A remark, in a tinted box with its title. A long one is folded to
 * its first lines, which often say the main point, ending with
 * "… legi plu ▾" where the reading stops; a short one, folded, would only
 * cost a tap. */
@Composable
private fun NoteBox(text: CharSequence, key: String, unfolded: Boolean, onLongPress: (String?) -> Unit) {
    val link = MaterialTheme.colorScheme.primary
    val (expanded, setExpanded) = unfoldedState(key, unfolded)
    val more = stringResource(R.string.note_more)
    /* The text cut at the end of its last shown line, for the width and
     * the size it was measured with; null while it fits */
    var folded by remember(text) { mutableStateOf<FoldedNote?>(null) }

    val isFolded = folded != null && !expanded
    val start = remember { BringIntoViewRequester() }
    val top = topWithRoom()
    val scope = rememberCoroutineScope()

    Surface(
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp).bringIntoViewRequester(start),
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        /* Folded, the whole box unfolds it, its margins too: "legi plu" is
         * on its last line, a small target for a finger */
        Column(
            Modifier
                .clickable(enabled = isFolded) { setExpanded(true) }
                .padding(horizontal = 10.dp, vertical = 8.dp),
        ) {
            Text(
                stringResource(R.string.remark),
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.primary,
            )
            SpannedText(
                folded?.takeIf { !expanded }?.text ?: text,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                onLongPress = onLongPress,
                /* Measured again when the width or the text size change,
                 * eg. when the phone is turned */
                onClick = if (isFolded) ({ setExpanded(true) }) else null,
                onLayout = { view ->
                    val width = view.layout.width
                    val size = view.paint.textSize
                    if (folded?.width != width || folded?.size != size)
                        folded = foldNote(text, view.paint, width, more, link.toArgb()) { setExpanded(true) }
                },
                modifier = Modifier.fillMaxWidth(),
            )
            if (expanded && folded != null)
                Text(
                    stringResource(R.string.note_less),
                    style = MaterialTheme.typography.labelLarge,
                    color = link,
                    /* A finger-sized target, without more room around it */
                    modifier = Modifier
                        .padding(top = 2.dp)
                        .heightIn(min = 32.dp)
                        /* Back to its start first, as for the examples */
                        .clickable {
                            scope.launch {
                                start.bringIntoView(top)
                                setExpanded(false)
                            }
                        }
                        .wrapContentHeight(),
                )
        }
    }
}

/** A remark cut to its first lines, and what it was measured for. */
private class FoldedNote(val text: CharSequence, val width: Int, val size: Float)

/** The remark cut at the end of its [FOLDED_NOTE_LINES]th line, between two
 * words, with "… legi plu ▾" where it fits; null if it is not longer than
 * [FOLD_NOTES_ABOVE] lines. Android draws no "…" itself on a text with
 * links, so the text is cut here. */
private fun foldNote(
    text: CharSequence,
    paint: android.text.TextPaint,
    width: Int,
    more: String,
    linkColor: Int,
    above: Int = FOLD_NOTES_ABOVE,
    lines: Int = FOLDED_NOTE_LINES,
    onMore: () -> Unit,
): FoldedNote? {
    val layout = android.text.StaticLayout.Builder.obtain(text, 0, text.length, paint, width)
        .setLineSpacing(0f, 1.2f)
        .build()
    if (layout.lineCount <= above)
        return null

    val label = "… $more"
    val last = lines - 1
    var cut = layout.getOffsetForHorizontal(last, width - paint.measureText(label))
        .coerceAtMost(layout.getLineEnd(last))
    /* Between two words, not in one */
    val space = text.subSequence(0, cut).indexOfLast { it.isWhitespace() }
    if (space > layout.getLineStart(last))
        cut = space

    /* "deduktas…", not "deduktas,…" */
    val cutText = android.text.SpannableStringBuilder(text.subSequence(0, cut).trimEnd { it.isWhitespace() || it in ",;:" })
    /* "…" against the last word, "legi plu ▾" the link, if there is one */
    if (more.isEmpty())
        return FoldedNote(cutText.append("…"), width, paint.textSize)
    cutText.append("… ")
    val start = cutText.length
    cutText.append(more)
    cutText.setSpan(object : android.text.style.ClickableSpan() {
        override fun onClick(widget: android.view.View) = onMore()
        override fun updateDrawState(ds: android.text.TextPaint) {
            ds.color = linkColor
            ds.isUnderlineText = false
        }
    }, start, cutText.length, android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
    return FoldedNote(cutText, width, paint.textSize)
}

/* A remark is folded beyond so many lines, to the first ones; the first
 * of folded examples too, shorter: the others are a tap away */
private const val FOLD_NOTES_ABOVE = 8
private const val FOLDED_NOTE_LINES = 5
/** The top of a part, to bring into view with room above it, for the
 * lines before it (the sense of the examples): all of it may not fit */
@Composable
private fun topWithRoom(): Rect {
    val room = with(LocalDensity.current) { 120.dp.toPx() }
    return Rect(0f, -room, 1f, 1f)
}
private const val FOLD_EXAMPLE_ABOVE = 3
private const val FOLDED_EXAMPLE_LINES = 2

/** What the French Wiktionary adds to the French translation of a word,
 * for each of its forms ("kukurbeto, itala kukurbo"); the French word
 * searched first, if the article was opened from it. Disk access: not on
 * the main thread. */
fun vikiWordsOf(context: Context, entry: WordEntry, vikiSearch: VikiSearch?): List<String> {
    /* Not a form Reta Vortaro has under several entries, whose sense
     * Wiktionary doesn't tell: "korveto", the ship and the bird */
    val forms = entry.title.toString().split(',').map { it.trim() }.filter { it.isNotEmpty() }
        .filterNot { Vikivortaro.ambiguous(context, it) }
    /* Its examples' too: "bonjour!" for "saluton!" */
    val revoFrench = entry.translations.filter { it.language == "fr" }
        .joinToString(", ") { (listOf(it.text) + it.examples.map { e -> e.text }).joinToString(", ") }
    val words = forms.flatMap { Vikivortaro.frenchFor(context, it, revoFrench) }
    val searched = vikiSearch?.takeIf { s -> forms.any { it == s.esperanto } }
    return (listOfNotNull(searched?.french) + words).distinctBy { Vikivortaro.key(it) }
}

/** And to its examples that Reta Vortaro translates, but not into French:
 * "bonvolu", "s'il vous plaît"; with the example searched, which Reta
 * Vortaro may translate into no language: "drinki kiel funelo". Disk
 * access: not on the main thread. */
fun vikiExamplesOf(context: Context, entry: WordEntry, example: SearchedExample?): List<VikiExample> {
    val french = entry.translations.firstOrNull { it.language == "fr" }
    val said = french?.examples.orEmpty().map { it.example }.toSet()
    val revoFrench = french?.let { (listOf(it.text) + it.examples.map { e -> e.text }).joinToString(", ") }
    return (entry.translations.flatMap { it.examples }.map { it.example } +
        listOfNotNull(example?.takeIf { it.language == "eo" }?.example)).distinct()
        .filter { it !in said }
        .mapNotNull { phrase ->
            Vikivortaro.frenchForExample(context, phrase, revoFrench).takeIf { it.isNotEmpty() }
                ?.let { VikiExample(phrase, it) }
        }
}

/** The French that Vikivortaro gives for an example of the word */
class VikiExample(val example: String, val words: List<String>)

/** An example whose translation was searched, in [language] */
class SearchedExample(val example: String, val language: String)

/** The translations of the word, the user's languages first. After a few
 * languages, the others are shown on demand. */
@Composable
private fun TranslationsBox(
    translations: List<Translation>,
    foldable: Boolean,
    onLongPressWord: (word: String, language: String) -> Unit,
    viki: List<String> = emptyList(),
    /** The example whose translations were searched: brought into view and
     * marked for a moment */
    example: SearchedExample? = null,
    /** Vikivortaro's French for examples Reta Vortaro doesn't translate into it */
    vikiExamples: List<VikiExample> = emptyList(),
    scrolled: Boolean = true,
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    /* The line searched stays marked while the article is open from the
     * search, until a tap on it; brought into view once */
    var markShown by rememberSaveable(example?.example, example?.language) { mutableStateOf(example != null) }
    var broughtIntoView by rememberSaveable(example?.example, example?.language) { mutableStateOf(false) }
    val markColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.7f)
    /* The French Wiktionary's words go on the line of Reta Vortaro's French,
     * which comes first if Reta Vortaro has none */
    val translations = remember(translations, viki, vikiExamples) {
        if ((viki.isEmpty() && vikiExamples.isEmpty()) || translations.any { it.language == "fr" }) translations
        else listOf(Translation("fr", "")) + translations
    }
    val folded = foldable && !expanded
    val shown = if (folded) translations.take(SHOWN_TRANSLATIONS) else translations
    val body = MaterialTheme.typography.bodyMedium
    val nameStyle = MaterialTheme.typography.bodySmall.copy(fontStyle = FontStyle.Italic)
    val dots = MaterialTheme.colorScheme.onSurfaceVariant

    /* The names of the languages, and a column as wide as the longest, up
     * to a share of the box that grows with the text; a longer name, at a
     * large text size or "internaci-signolingva", goes above its
     * translation rather than being cut */
    val context = LocalContext.current
    val names = remember(translations) {
        val list = LanguageList.getDefault(context)
        translations.associate { it.language to list.getLanguageName(it.language) }
    }
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val widths = remember(names, nameStyle, density) {
        names.mapValues { (_, name) -> with(density) { measurer.measure(name, nameStyle).size.width.toDp() + 10.dp } }
    }

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
            /* A third of the box at most for the names */
            BoxWithConstraints {
            val limit = maxWidth / 3
            val nameWidth = widths.values.filter { it <= limit }.maxOrNull() ?: 0.dp
            Column {
            /* Each language its own state, as the French line may come
             * first once Vikivortaro's words are read */
            for (translation in shown) key(translation.language) {
                val apart = widths.getValue(translation.language) > limit
                if (apart)
                    Text(
                        names.getValue(translation.language),
                        style = nameStyle,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 3.dp),
                    )
                Row(Modifier.padding(vertical = 3.dp)) {
                    /* The name of the language, in full like the rest of the
                     * app, quiet beside the translation it introduces */
                    Box(Modifier.width(nameWidth).padding(top = 2.dp)) {
                        if (!apart)
                            Text(
                                names.getValue(translation.language),
                                style = nameStyle,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                            )
                    }
                    /* A long press looks up the word under the finger in
                     * the language of the translation */
                    var layout by remember { mutableStateOf<TextLayoutResult?>(null) }
                    /* Vikivortaro's words follow Reta Vortaro's, where they
                     * are in the text */
                    val vikiRanges = mutableListOf<IntRange>()
                    val french = translation.language == "fr"
                    val text = if (viki.isNotEmpty() && french) {
                        /* Some of Reta Vortaro's end with a comma: "…, net," */
                        val revo = frenchSpacing(translation.text.trimEnd(' ', ',', ';'))
                        buildString {
                            /* After senses with their numbers, on a line of
                             * their own: they would read as the last sense,
                             * "(3) perçant, …, saillant" */
                            if (revo.isNotEmpty())
                                append(if (Regex("\\(\\d").containsMatchIn(revo)) "$revo\n" else "$revo, ")
                            for ((index, word) in viki.withIndex()) {
                                if (index > 0)
                                    append(", ")
                                vikiRanges.add(length until length + word.length)
                                append(word)
                            }
                        }
                    } else if (french) frenchSpacing(translation.text) else translation.text
                    /* Then the translations of its examples, each on its
                     * line after its example, quiet like the examples of
                     * the article: "saluton! bonjour !, salut !"; the one
                     * searched marked */
                    var marked: IntRange? = null
                    val shownText = buildAnnotatedString {
                        append(text)
                        for (line in translation.examples) {
                            if (length > 0)
                                append("\n")
                            val start = length
                            withStyle(SpanStyle(fontStyle = FontStyle.Italic, color = dots)) { append(line.example) }
                            append(" ")
                            append(if (french) frenchSpacing(line.text) else line.text)
                            if (line.example == example?.example &&
                                (example.language == "eo" || example.language == translation.language)) {
                                marked = start until length
                                if (markShown)
                                    addStyle(SpanStyle(background = markColor), start, length)
                            }
                        }
                        /* Vikivortaro's French for the examples Reta Vortaro
                         * doesn't translate into French, dotted */
                        if (french)
                            for (line in vikiExamples) {
                                if (length > 0)
                                    append("\n")
                                val start = length
                                withStyle(SpanStyle(fontStyle = FontStyle.Italic, color = dots)) { append(line.example) }
                                append(" ")
                                for ((index, word) in line.words.withIndex()) {
                                    if (index > 0)
                                        append(", ")
                                    vikiRanges.add(length until length + word.length)
                                    append(word)
                                }
                                if (line.example == example?.example) {
                                    marked = start until length
                                    if (markShown)
                                        addStyle(SpanStyle(background = markColor), start, length)
                                }
                            }
                    }
                    val bringIntoView = remember { BringIntoViewRequester() }
                    val margin = with(LocalDensity.current) { 48.dp.toPx() }
                    /* Once the list is at the card; done only once it is in
                     * view: the text laid out again meanwhile (Vikivortaro's
                     * words coming) cancels it, and it starts again */
                    val markedNow by rememberUpdatedState(marked)
                    LaunchedEffect(marked != null, scrolled, markShown) {
                        if (markedNow == null || !markShown || broughtIntoView || !scrolled)
                            return@LaunchedEffect
                        delay(300)
                        val laid = snapshotFlow { layout }.filterNotNull().first()
                        val range = markedNow ?: return@LaunchedEffect
                        /* With room around it, above the bar of the
                         * system's buttons, not only just visible */
                        bringIntoView.bringIntoView(laid.getBoundingBox(range.first).let { box ->
                            Rect(
                                0f,
                                box.top - margin,
                                laid.size.width.toFloat(),
                                laid.getLineBottom(laid.getLineForOffset(range.last)) + 3 * margin,
                            )
                        })
                        broughtIntoView = true
                    }
                    /* A tap on one of them tells where it comes from; a screen
                     * reader says it after the line */
                    /* Closed when the screen turns: its place was the old one */
                    var bubble by remember(LocalConfiguration.current.orientation) { mutableStateOf<Pair<Offset, String>?>(null) }
                    val spoken = if (vikiRanges.isEmpty()) null
                        else shownText.text + ". " + stringResource(R.string.viki_word_source, vikiRanges.joinToString("“, „") { shownText.text.substring(it) })
                    Box(Modifier.weight(1f)) {
                        Text(
                            shownText,
                            style = body,
                            onTextLayout = { layout = it },
                            modifier = Modifier
                                .bringIntoViewRequester(bringIntoView)
                                .dottedUnder(vikiRanges, layout, dots)
                                .semantics { spoken?.let { contentDescription = it } }
                                .pointerInput(shownText) {
                                    detectTapGestures(
                                        onTap = { position ->
                                            val offset = layout?.getOffsetForPosition(position) ?: return@detectTapGestures
                                            val word = vikiRanges.firstOrNull { offset in it || offset == it.last + 1 }
                                            if (word != null)
                                                bubble = position to shownText.text.substring(word)
                                            /* A tap on the line searched unmarks it */
                                            else if (marked?.let { offset in it } == true)
                                                markShown = false
                                        },
                                        onLongPress = { position ->
                                            val offset = layout?.getOffsetForPosition(position) ?: return@detectTapGestures
                                            WordLookup.wordAt(shownText.text, offset)
                                                ?.let { onLongPressWord(it, translation.language) }
                                        },
                                    )
                                },
                        )
                        bubble?.let { (position, word) ->
                            SourceBubble(stringResource(R.string.viki_word_source, word), position, onDismiss = { bubble = null })
                        }
                    }
                }
            }
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

/** Dots under the given ranges of a text: Vikivortaro's words among Reta
 * Vortaro's, quietly, like a note there is to read */
private fun Modifier.dottedUnder(ranges: List<IntRange>, layout: TextLayoutResult?, color: Color) =
    if (ranges.isEmpty()) this else drawBehind {
        val laid = layout ?: return@drawBehind
        val step = 3.dp.toPx()
        val radius = 0.75.dp.toPx()
        for (range in ranges)
            for (i in range) {
                if (i >= laid.layoutInput.text.length) break
                val box = laid.getBoundingBox(i)
                val y = laid.getLineBottom(laid.getLineForOffset(i)) - 2.dp.toPx()
                /* At the same steps on every letter, so that they join */
                var x = (box.left / step).toInt() * step + step / 2
                while (x < box.right) {
                    drawCircle(color, radius, Offset(x, y))
                    x += step
                }
            }
    }

/** A small bubble over a tapped place, closed by a tap anywhere: above it,
 * centred on it, kept in the window; below it, where there is no room
 * above */
@Composable
private fun SourceBubble(text: String, position: Offset, onDismiss: () -> Unit) {
    val gap = with(LocalDensity.current) { 8.dp.roundToPx() }
    val placement = remember(position, gap) {
        object : PopupPositionProvider {
            override fun calculatePosition(
                anchorBounds: IntRect,
                windowSize: IntSize,
                layoutDirection: LayoutDirection,
                popupContentSize: IntSize,
            ): IntOffset {
                val x = (anchorBounds.left + position.x.toInt() - popupContentSize.width / 2)
                    .coerceIn(0, maxOf(0, windowSize.width - popupContentSize.width))
                val above = anchorBounds.top + position.y.toInt() - gap - popupContentSize.height
                val y = if (above >= 0) above else anchorBounds.top + position.y.toInt() + gap
                return IntOffset(x, y)
            }
        }
    }
    Popup(
        popupPositionProvider = placement,
        onDismissRequest = onDismiss,
        properties = PopupProperties(focusable = true),
    ) {
        Surface(
            shape = RoundedCornerShape(8.dp),
            color = MaterialTheme.colorScheme.inverseSurface,
            shadowElevation = 2.dp,
        ) {
            Text(
                text,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.inverseOnSurface,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            )
        }
    }
}

/** At the end of a page, where its text comes from, in a line: "Fontoj:
 * Reta Vortaro · Vikivortaro ›". A tap tells what comes from each, Reta
 * Vortaro first, with a link to read it there (their authors and licences
 * are in "Pri"): the article [revoArticle] on Reta
 * Vortaro's site, or on the page of a word only Vikivortaro has,
 * [vikiWord]'s page. */
@Composable
fun SourcesLine(
    revo: Boolean,
    viki: Boolean,
    modifier: Modifier = Modifier,
    revoArticle: Int? = null,
    vikiWord: String? = null,
    /** Whether the page shows Vikivortaro's description of [vikiWord] */
    vikiDescription: Boolean = true,
    /** The definition of [vikiWord] from the Esperanto Wiktionary that the
     * page shows, if any */
    vikiEsperanto: EsperantoDefinition? = null,
) {
    var open by rememberSaveable { mutableStateOf(false) }
    val context = LocalContext.current
    val revoPage by produceState<String?>(null, revoArticle) {
        value = revoArticle?.let { withContext(Dispatchers.IO) { RevoFiles.page(context, it)?.toString() } }
    }
    /* The dictionaries its headwords are in */
    val dictionaries by produceState(emptyList<Pair<String, String>>(), revoArticle) {
        value = revoArticle?.let { article -> withContext(Dispatchers.IO) { Attestations.of(context, article) } }
            ?: emptyList()
    }
    val revoName = stringResource(R.string.revo_name)
    val vikiName = stringResource(R.string.viki_name)
    val muted = MaterialTheme.colorScheme.onSurfaceVariant

    /* A button, as high as one should be to be tapped */
    Box(
        modifier
            .heightIn(min = 48.dp)
            .clickable(role = Role.Button) { open = true },
        contentAlignment = Alignment.CenterStart,
    ) {
        Text(
            stringResource(R.string.sources_line, listOfNotNull(revoName.takeIf { revo }, vikiName.takeIf { viki || vikiEsperanto != null }).joinToString(" · ")),
            style = MaterialTheme.typography.bodySmall.copy(fontStyle = FontStyle.Italic),
            color = muted,
        )
    }
    if (open)
        AlertDialog(
            onDismissRequest = { open = false },
            confirmButton = { TextButton(onClick = { open = false }) { Text(stringResource(R.string.close)) } },
            title = { Text(stringResource(R.string.sources)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (revo)
                        Source(revoName, stringResource(R.string.sources_revo), revoPage ?: REVO_SITE)
                    /* The article on Reta Vortaro, where its "Redakti" button
                     * lets one correct it, and who changed what in it */
                    if (revo) revoPage?.let { page ->
                        val link = TextLinkStyles(SpanStyle(color = MaterialTheme.colorScheme.primary, textDecoration = TextDecoration.Underline))
                        Text(
                            buildAnnotatedString {
                                append(stringResource(R.string.sources_revo_article) + " ")
                                withLink(LinkAnnotation.Url(page, link)) { append(stringResource(R.string.sources_revo_read)) }
                                append(" · ")
                                withLink(LinkAnnotation.Url(page.replace("/art/", "/hst/"), link)) {
                                    append(stringResource(R.string.sources_revo_history))
                                }
                            },
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                    if (revo && dictionaries.isNotEmpty()) {
                        /* "En la vortaroj: aboli — …", the words in italics */
                        val line = stringResource(R.string.sources_dictionaries, "\u0000")
                        Text(
                            buildAnnotatedString {
                                append(line.substringBefore('\u0000'))
                                dictionaries.forEachIndexed { index, (form, works) ->
                                    if (index > 0) append("; ")
                                    withStyle(SpanStyle(fontStyle = FontStyle.Italic)) { append(form) }
                                    append(" — $works")
                                }
                                append(line.substringAfter('\u0000'))
                            },
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                    /* Vikivortaro once: with the French too, "Vikivortaro
                     * (eo · fr)", each edition a link to the word's page */
                    if (vikiEsperanto != null && vikiWord != null) {
                        val page = "wiki/" + Uri.encode(vikiWord.replace(' ', '_'))
                        if (viki)
                            Source(
                                stringResource(R.string.sources_viki_editions, vikiName, "eo", "fr"),
                                /* "adaptitaj" for both if the definition was
                                 * adapted too, else for the French only */
                                if (vikiEsperanto.adapted)
                                    stringResource(
                                        R.string.sources_viki_eo_fr,
                                        stringResource(if (vikiDescription) R.string.sources_viki_fr_description else R.string.sources_viki_fr_translations),
                                    )
                                else
                                    stringResource(
                                        R.string.sources_viki_eo_fr_as_is,
                                        stringResource(if (vikiDescription) R.string.sources_viki_fr_description_adapted else R.string.sources_viki_fr_translations_adapted),
                                    ),
                                links = listOf("eo" to VIKI_EO_SITE + page, "fr" to VIKI_SITE + page),
                            )
                        else
                            Source(
                                vikiName,
                                stringResource(if (vikiEsperanto.adapted) R.string.sources_viki_eo_adapted else R.string.sources_viki_eo, vikiWord),
                                VIKI_EO_SITE + page,
                            )
                    } else if (viki)
                        Source(
                            vikiName,
                            if (vikiWord != null)
                                stringResource(if (vikiDescription) R.string.sources_viki_word else R.string.sources_viki_word_only, vikiWord)
                            else stringResource(R.string.sources_viki),
                            if (vikiWord != null) VIKI_SITE + "wiki/" + Uri.encode(vikiWord.replace(' ', '_')) else VIKI_SITE,
                        )
                }
            },
        )
}

private const val REVO_SITE = "https://reta-vortaro.de/"
private const val VIKI_SITE = "https://fr.wiktionary.org/"
private const val VIKI_EO_SITE = "https://eo.wiktionary.org/"

/** A source, its name a link to read it there ([url]); or, with several
 * places to read it, its name plain and each text of [links] in the name
 * ("eo" in "Vikivortaro (eo · fr)") a link to its address */
@Composable
private fun Source(name: String, what: String, url: String? = null, links: List<Pair<String, String>> = emptyList()) {
    val link = TextLinkStyles(
        SpanStyle(
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Bold,
            textDecoration = TextDecoration.Underline,
        )
    )
    Text(
        buildAnnotatedString {
            if (url != null)
                withLink(LinkAnnotation.Url(url, link)) { append(name) }
            else
                withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(name) }
            for ((text, address) in links) {
                val at = name.lastIndexOf(text)
                if (at >= 0)
                    addLink(LinkAnnotation.Url(address, link), at, at + text.length)
            }
            append(" — ")
            append(what)
        },
        style = MaterialTheme.typography.bodyMedium,
    )
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
    /** Once laid out: the view, with its width and paint */
    onLayout: ((TextView) -> Unit)? = null,
    /** A tap on the text, links aside */
    onClick: (() -> Unit)? = null,
) {
    val linkColor = MaterialTheme.colorScheme.primary
    /* In pixels: the TextView would only apply the font size of Android,
     * not the text size of the settings that the density carries */
    val size = with(LocalDensity.current) { style.fontSize.toPx() }

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
            view.setTextSize(TypedValue.COMPLEX_UNIT_PX, size)
            if (onLayout != null)
                view.post { if (view.layout != null) onLayout(view) }
            /* Only away from the links, which open their article */
            if (onClick != null)
                view.setOnClickListener {
                    val tapped = view.text as? android.text.Spanned
                    val onLink = tapped != null && view.selectionStart >= 0 &&
                        tapped.getSpans(view.selectionStart, view.selectionEnd, android.text.style.ClickableSpan::class.java).isNotEmpty()
                    if (!onLink) onClick()
                }
            else
                view.setOnClickListener(null)
            /* Serif, like the rest of the design */
            view.typeface = android.graphics.Typeface.create(
                android.graphics.Typeface.SERIF,
                if ((style.fontWeight?.weight ?: 400) >= 600) android.graphics.Typeface.BOLD
                else android.graphics.Typeface.NORMAL,
            )
            if (onLongPress != null) {
                /* Where the finger went down, to find the word under it;
                 * the touch goes on to the links, but not the lifting of
                 * the finger after a long press, which would follow the
                 * link pressed as well */
                val down = FloatArray(2)
                var longPressed = false
                view.setOnTouchListener { _, event ->
                    when (event.actionMasked) {
                        MotionEvent.ACTION_DOWN -> {
                            down[0] = event.x
                            down[1] = event.y
                            longPressed = false
                        }
                        MotionEvent.ACTION_UP -> if (longPressed) event.action = MotionEvent.ACTION_CANCEL
                    }
                    false
                }
                view.setOnLongClickListener {
                    longPressed = true
                    val offset = view.getOffsetForPosition(down[0], down[1])
                    /* Not the author or the work of an example */
                    val inSource = (view.text as? Spanned)
                        ?.getSpans(offset, offset, ExampleSource::class.java)?.isNotEmpty() == true
                    onLongPress(if (inSource) null else WordLookup.wordAt(view.text, offset))
                    true
                }
            }
        },
        modifier = modifier,
    )
}
