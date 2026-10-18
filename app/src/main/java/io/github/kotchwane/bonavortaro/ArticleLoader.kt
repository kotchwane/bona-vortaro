/*
 * Bona vortaro - an Esperanto dictionary for Android, based on PReVo
 * Copyright (C) 2012, 2013, 2016  Neil Roberts
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

import android.widget.TextView
import android.text.style.TtsSpan
import android.text.style.CharacterStyle
import android.text.Spannable
import android.text.Selection
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.net.Uri
import android.text.SpannableString
import android.text.SpannableStringBuilder
import android.text.TextPaint
import android.text.style.ClickableSpan
import android.text.style.MetricAffectingSpan
import android.text.style.QuoteSpan
import android.text.style.RelativeSizeSpan
import android.text.style.StyleSpan
import android.text.style.StrikethroughSpan
import android.text.style.SubscriptSpan
import android.text.style.SuperscriptSpan
import android.util.Log
import android.view.View
import java.io.IOException
import java.util.Locale
import kotlin.concurrent.thread

/** A section of an article: a derived word or a group of translations. */
class ArticleSection(
    val language: String,
    val header: SpannableString,
    val content: SpannableString,
)

class Article(val title: SpannableString, val sections: List<ArticleSection>)

/* Marks for the parts of the text that the article screen shows in its
 * own way: the kind of word ("transitiva"), its field ("matematiko") and
 * the examples. They don't change how the text is drawn by themselves. */
class GrammarSpan
/** With the code of the field, "ZOO", for its icon, if the data has it */
class FieldSpan(val code: String? = null)
class ExampleSpan

/** The symbol of the type of a link, eg. "↘", with its type as numbered by
 * prevodb (see LinkType). */
class LinkTypeSpan(val type: Int)

/** An abbreviation or a code given for the word ("DE" for Germanujo),
 * with its kind: the index in R.array.abbreviation_kinds */
class AbbreviationSpan(val kind: Int)

/** The source of an example, "— Zamenhof, Fundamento de Esperanto",
 * paler than the example, with or without a link (see [SourceSpan] and
 * [SourceLinkSpan]). */
interface ExampleSource

/* A little paler than the example, still readable: 4.8:1 on the card */
private fun paler(paint: TextPaint) {
    paint.color = Color.argb(Color.alpha(paint.color) * 9 / 10, Color.red(paint.color), Color.green(paint.color), Color.blue(paint.color))
}

/** A source without an address: only paler. */
class SourceSpan : CharacterStyle(), ExampleSource {
    override fun updateDrawState(paint: TextPaint) = paler(paint)
}

/** A source whose work a tap opens, its [number] in assets/sources.txt.
 * It ends with "↗", the sign of a page outside the app, rather than taking
 * the colour of the links, which stay in it. */
class SourceLinkSpan(val number: Int) : ClickableSpan(), ExampleSource {
    override fun updateDrawState(paint: TextPaint) = paler(paint)

    override fun onClick(view: View) {
        /* No highlight left on it when coming back */
        ((view as? TextView)?.text as? Spannable)?.let { Selection.removeSelection(it) }
        val context = view.context
        /* The addresses are read once, away from the main thread */
        thread {
            val url = SourceLinks.url(context, number) ?: return@thread
            view.post { openOutside(context, Uri.parse(url)) }
        }
    }
}

/** Upright, in a text in italics: the source after an example. Set after
 * the other spans, to be applied last. */
class UprightSpan : MetricAffectingSpan() {
    override fun updateDrawState(paint: TextPaint) = upright(paint)

    override fun updateMeasureState(paint: TextPaint) = upright(paint)

    private fun upright(paint: TextPaint) {
        val typeface = paint.typeface ?: Typeface.DEFAULT
        paint.typeface = Typeface.create(typeface, typeface.style and Typeface.ITALIC.inv())
        paint.textSkewX = 0f
    }
}

/** The addresses of the sources of the examples, written by prevodb in
 * assets/sources.txt, one a line. */
object SourceLinks {
    @Volatile private var urls: List<String>? = null

    /** The address of a source, numbered from 1. Disk access, the first
     * time: not on the main thread. */
    @Synchronized
    fun url(context: Context, number: Int): String? = (urls ?: try {
        context.assets.open("sources.txt").bufferedReader().readLines()
    } catch (e: IOException) {
        Log.w("bonasources", "Failed to read the addresses of the sources")
        emptyList()
    }.also { urls = it }).getOrNull(number - 1)
}

/** A usage ReVo marks as wrong: in italics, or upright in a text already
 * in italics, such as an example. It must be set after the other spans,
 * so that it is applied last. */
class MisuseSpan : MetricAffectingSpan() {
    override fun updateDrawState(paint: TextPaint) = toggleItalic(paint)

    override fun updateMeasureState(paint: TextPaint) = toggleItalic(paint)

    private fun toggleItalic(paint: TextPaint) {
        val typeface = paint.typeface ?: Typeface.DEFAULT
        if (typeface.isItalic || paint.textSkewX != 0f) {
            paint.typeface = Typeface.create(typeface, typeface.style and Typeface.ITALIC.inv())
            paint.textSkewX = 0f
        } else {
            paint.typeface = Typeface.create(typeface, typeface.style or Typeface.ITALIC)
            /* As StyleSpan does when the font has no italics */
            if (!paint.typeface.isItalic)
                paint.textSkewX = -0.25f
        }
    }
}

/** A link to another section of the same article. The translations use
 * them to point back to the word they translate. */
class SectionLinkSpan(val section: Int, private val onShowSection: (Int) -> Unit) : ClickableSpan() {
    override fun onClick(widget: View) = onShowSection(section)
}

/** Reads the articles generated by prevodb. Each file contains 16
 * articles: article n is the (n & 0xf)th of articles/article-(n >> 4).bin.
 * Each article is a title followed by sections, each one being a language
 * code, a header and a content. Every string is UTF-8 text followed by a
 * list of spans. */
object ArticleLoader {
    private const val TAG = "bonaarticle"

    /* Types of the spans in the files */
    private const val SPAN_REFERENCE = 0
    private const val SPAN_SUPERSCRIPT = 1
    private const val SPAN_ITALIC = 2
    private const val SPAN_QUOTE = 3
    private const val SPAN_BOLD = 4
    /* Added to prevodb in the fork; prevodb without them doesn't write
     * them, and older readers ignore them */
    private const val SPAN_GRAMMAR = 5
    private const val SPAN_FIELD = 6
    private const val SPAN_EXAMPLE = 7
    private const val SPAN_LINK_TYPE = 8
    private const val SPAN_ABBREVIATION = 9
    /* ReVo's <sub>, "CH₃"; its <sup> is a superscript, "10¹²" */
    private const val SPAN_SUBSCRIPT = 10
    /* ReVo's <ts>, a word struck through: "edziĝi kun al" */
    private const val SPAN_STRIKETHROUGH = 11
    /* ReVo's <mis>, a usage marked wrong: "ne ĉesu la laboron" */
    private const val SPAN_MISUSE = 12
    /* The source of an example, " — Zamenhof, Fundamento de Esperanto",
     * with the number of its address in sources.txt */
    private const val SPAN_SOURCE = 13
    /* After a source that opens a page outside the app */
    private const val OUTSIDE = "\u00a0" + OUTSIDE_SIGN

    /* The codes of the fields, for the number in their spans, written
     * by prevodb in fields.txt */
    @Volatile private var fieldCodes: List<String>? = null

    @Synchronized
    private fun fieldCodes(context: Context): List<String> = fieldCodes ?: try {
        context.assets.open("fields.txt").bufferedReader().readLines()
    } catch (e: IOException) {
        Log.w(TAG, "Failed to read the codes of the fields")
        emptyList()
    }.also { fieldCodes = it }

    /**
     * Loads an article, keeping only the sections of the languages shown
     * in the settings (Esperanto is always shown).
     *
     * @param onShowSection called when a link to another section of the
     *   same article is clicked, with the number of the section
     * @param quoteColor colour of the bar drawn next to quotations
     */
    fun load(
        context: Context,
        articleNumber: Int,
        onShowSection: (Int) -> Unit,
        quoteColor: Int,
        /** The languages kept: by default, those of the settings */
        languages: ((String) -> Boolean)? = null,
        /** Whether the sources of the examples are kept: by default, as
         * the settings say */
        sources: Boolean? = null,
    ): Article {
        val fileName = String.format(Locale.US, "articles/article-%03xx.bin", articleNumber shr 4)
        val stream = context.assets.open(fileName)
        val input = BinaryReader(stream)

        try {
            skipArticles(input, articleNumber and 0xf)

            val articleLength = input.readInt()
            val articleStart = input.position
            val reader = SpanReader(
                articleNumber, onShowSection, quoteColor,
                fieldCodes(context), sources ?: BonaSettings.getShowSources(context),
                context.getString(R.string.outside_link),
            )
            val title = reader.read(input)
            val showTranslations = languages ?: BonaSettings.translationFilter(context)
            val sections = mutableListOf<ArticleSection>()

            while (input.position - articleStart < articleLength) {
                val language = readLanguageCode(input)

                if (showTranslations(language)) {
                    val header = reader.read(input)
                    val content = reader.read(input)
                    sections.add(ArticleSection(language, header, content))
                } else {
                    skipSpannableString(input)
                    skipSpannableString(input)
                }
            }

            return Article(title, sections)
        } finally {
            stream.close()
        }
    }

    private fun skipArticles(input: BinaryReader, count: Int) {
        repeat(count) {
            input.skip(input.readInt().toLong())
        }
    }

    private fun readLanguageCode(input: BinaryReader): String {
        val buf = ByteArray(3)
        input.readAll(buf)

        var length = buf.size
        while (length > 0 && buf[length - 1] == 0.toByte())
            length--

        return String(buf, 0, length, Charsets.UTF_8)
    }

    private fun skipSpannableString(input: BinaryReader) {
        input.skip(input.readShort().toLong())

        /* Each span is its length, start, two data values and its type */
        while (input.readShort() != 0)
            input.skip(2 + 2 + 2 + 1)
    }

    private class SpanReader(
        val articleNumber: Int,
        val onShowSection: (Int) -> Unit,
        val quoteColor: Int,
        val fieldCodes: List<String>,
        val showSources: Boolean,
        /** "ekstera ligilo": how TalkBack reads "↗" */
        val outsideLink: String,
    ) {
        fun read(input: BinaryReader): SpannableString {
            val bytes = ByteArray(input.readShort())
            input.readAll(bytes)

            val string = SpannableString(String(bytes, Charsets.UTF_8))
            /* Set at the end: they undo the italics of the spans around */
            val misuses = mutableListOf<Pair<Int, Int>>()
            /* The sources of the examples: start, end and address */
            val sources = mutableListOf<Triple<Int, Int, Int>>()

            while (true) {
                val spanLength = input.readShort()
                if (spanLength == 0)
                    break

                val spanStart = input.readShort()
                val data1 = input.readShort()
                val data2 = input.readShort()
                val spanType = input.readByte()
                val spanEnd = spanStart + spanLength

                if (spanStart < 0 || spanLength < 0 || spanEnd > string.length) {
                    /* Skip it instead of crashing in setSpan */
                    Log.wtf(TAG, "Invalid span $spanStart→$spanEnd for string of length ${string.length}")
                    continue
                }

                if (spanType == SPAN_MISUSE)
                    misuses.add(spanStart to spanEnd)
                else if (spanType == SPAN_SOURCE)
                    sources.add(Triple(spanStart, spanEnd, data1))
                else
                    for (span in createSpans(spanType, data1, data2))
                        string.setSpan(span, spanStart, spanEnd, 0)
            }

            for ((start, end) in misuses)
                string.setSpan(MisuseSpan(), start, end, 0)

            if (sources.isEmpty())
                return string
            if (showSources) {
                val marked = SpannableStringBuilder(string)
                for ((start, end, number) in sources.sortedByDescending { it.first }) {
                    var to = end
                    if (number > 0) {
                        /* The spans that end with the source, such as its
                         * example, end after the sign; those that start
                         * after it, such as a second source, start after
                         * it too */
                        val around = marked.getSpans(end, end, Any::class.java)
                            .map { Triple(it, marked.getSpanStart(it), marked.getSpanEnd(it)) to marked.getSpanFlags(it) }
                        marked.insert(end, OUTSIDE)
                        to = end + OUTSIDE.length
                        for ((span, flags) in around) {
                            val (it, from, until) = span
                            when {
                                until == end && from < end -> marked.setSpan(it, from, to, flags)
                                from == end && until > end -> marked.setSpan(it, to, until + OUTSIDE.length, flags)
                            }
                        }
                        marked.setSpan(OutsideSignSpan(), end, to, 0)
                        marked.setSpan(TtsSpan.TextBuilder(outsideLink).build(), end, to, 0)
                    }
                    marked.setSpan(RelativeSizeSpan(0.85f), start, to, 0)
                    marked.setSpan(UprightSpan(), start, to, 0)
                    marked.setSpan(if (number > 0) SourceLinkSpan(number) else SourceSpan(), start, to, 0)
                }
                return SpannableString(marked)
            }
            /* Left out, with the space before them, from the end so that
             * the others stay where they are */
            val without = SpannableStringBuilder(string)
            for ((start, end, _) in sources.sortedByDescending { it.first })
                without.delete(if (start > 0 && without[start - 1] == ' ') start - 1 else start, end)
            return SpannableString(without)
        }

        private fun createSpans(type: Int, data1: Int, data2: Int): List<Any> =
            when (type) {
                SPAN_REFERENCE ->
                    /* data1 is the article and data2 the section */
                    if (data1 == articleNumber)
                        listOf(SectionLinkSpan(data2, onShowSection))
                    else
                        listOf(ReferenceSpan(data1, data2))
                SPAN_SUPERSCRIPT -> listOf(SuperscriptSpan(), RelativeSizeSpan(0.5f))
                SPAN_ITALIC -> listOf(StyleSpan(Typeface.ITALIC))
                SPAN_QUOTE -> listOf(QuoteSpan(quoteColor))
                SPAN_BOLD -> listOf(StyleSpan(Typeface.BOLD))
                SPAN_GRAMMAR -> listOf(GrammarSpan())
                SPAN_FIELD -> listOf(FieldSpan(fieldCodes.getOrNull(data1 - 1)))
                SPAN_EXAMPLE -> listOf(ExampleSpan())
                SPAN_LINK_TYPE -> listOf(LinkTypeSpan(data1))
                SPAN_ABBREVIATION -> listOf(AbbreviationSpan(data1))
                SPAN_SUBSCRIPT -> listOf(SubscriptSpan(), RelativeSizeSpan(0.7f))
                SPAN_STRIKETHROUGH -> listOf(StrikethroughSpan())
                else -> emptyList()
            }
    }
}
