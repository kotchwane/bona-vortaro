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

import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.ClickableSpan
import android.text.style.QuoteSpan
import android.text.style.SuperscriptSpan

/*
 * Turns an article as stored by prevodb (a list of sections: first the
 * derived words, then one section of translations per language) into one
 * entry per word, with its own translations.
 *
 * In a translation section, every translation starts with a link to the
 * section of the word it translates, written like "~o" or "~a 1" (for
 * the first sense), e.g. "~o: topologie; ~a 1: topologique". These links
 * are used to give each word its translations. Whatever can't be
 * attributed to a word is kept as a separate translation section, as
 * before, so that nothing is lost.
 *
 * With the data of the fork of prevodb, the kind of word and its field
 * are marked (GrammarSpan, FieldSpan): when they are given for the whole
 * word, at the start of its content, they become labels next to its
 * title. The examples are marked too (ExampleSpan), and become blocks of
 * their own. With older data, these marks are missing and the content
 * is shown as it is.
 */

/** A part of the content of a word: normal text, an example, or a
 * remark ("Rim."). */
sealed class ContentBlock(val text: CharSequence) {
    class Text(text: CharSequence) : ContentBlock(text)
    class Example(text: CharSequence) : ContentBlock(text)
    class Note(text: CharSequence) : ContentBlock(text)
}

/** A label of the whole word: its field ("matematiko") or its kind
 * ("transitiva"). */
class Label(
    val isField: Boolean,
    val text: String,
    /** For an abbreviation, its kind (R.array.abbreviation_kinds), else -1 */
    val abbreviation: Int = -1,
)

class Translation(val language: String, val text: String)

class WordEntry(
    /** Index of the section in the article, as used by the links */
    val section: Int,
    val source: ArticleSection,
    /** The officiality marks of the word ("*", "1"…), the oldest first */
    val official: List<String>,
    /** Its forms, and which marks each has */
    val forms: OfficialTitle,
    /** The header without its officiality mark */
    val title: CharSequence,
    val labels: List<Label>,
    val blocks: List<ContentBlock>,
    val translations: List<Translation>,
)

class ArticleLayout(val words: List<WordEntry>, val otherTranslations: List<ArticleSection>)

/** Translation sections are those of a language other than Esperanto. */
private fun ArticleSection.isWord() = language == "eo"

/** The types of links, in the order of prevodb, with the words of ReVo
 * for them (from its website, xsl/inc/revo_ref.xsl of voko-grundo), for one
 * link and for several. */
enum class LinkType(val one: String, val several: String, val family: LinkFamily) {
    SEE("vidu", "vidu", LinkFamily.OTHER),
    HOMONYM("homonimo", "homonimoj", LinkFamily.OTHER),
    DEFINITION("difino ĉe", "difino ĉe", LinkFamily.OTHER),
    SYNONYM("sinonimo", "sinonimoj", LinkFamily.EQUIVALENCE),
    ANTONYM("antonimo", "antonimoj", LinkFamily.EQUIVALENCE),
    BROADER("supernocio", "supernocioj", LinkFamily.HIERARCHY),
    NARROWER("subnocio", "subnocioj", LinkFamily.HIERARCHY),
    PARTS("parto", "partoj", LinkFamily.PARTS),
    PART_OF("parto de", "parto de", LinkFamily.PARTS),
    EXAMPLE("ekzemplo", "ekzemploj", LinkFamily.OTHER),
}

/** Families of types of links, shown with different colours. */
enum class LinkFamily { HIERARCHY, PARTS, EQUIVALENCE, OTHER }

/**
 * @param markLabel called for each field or kind of word left within the
 *   text (eg. at the start of a sense, "a) muziko …"), to return the span
 *   that shows it as a label
 * @param markLinkType called for each symbol of the type of a link, with
 *   the word to show instead, to return the span that shows it
 */
fun layoutArticle(
    article: Article,
    preferredLanguages: List<String>,
    markLabel: () -> Any,
    markLinkType: (LinkType, String) -> Any,
    /** The words for the kinds of abbreviations (R.array.abbreviation_kinds) */
    abbreviationKinds: List<String> = emptyList(),
): ArticleLayout {
    /* For each word and language, the translations with their sense */
    val translations = mutableMapOf<Int, MutableMap<String, MutableList<Pair<String, String>>>>()
    val otherTranslations = mutableListOf<ArticleSection>()
    val wordSections = article.sections.indices.filter { article.sections[it].isWord() }.toSet()

    for (section in article.sections) {
        if (section.isWord())
            continue

        val attributed = splitTranslations(section.content)
        if (attributed == null || attributed.any { it.first !in wordSections }) {
            otherTranslations.add(section)
            continue
        }

        for ((target, sense, text) in attributed) {
            translations.getOrPut(target) { mutableMapOf() }
                .getOrPut(section.language) { mutableListOf() }
                .add(sense to text)
        }
    }

    val words = wordSections.sorted().map { index ->
        val section = article.sections[index]
        val official = parseOfficial(section.header)
        val (labels, content) = splitLabels(section.content)
        val byLanguage = translations[index] ?: emptyMap<String, List<Pair<String, String>>>()

        WordEntry(
            section = index,
            source = section,
            official = official.marks,
            forms = official,
            title = official.text,
            labels = labels,
            blocks = splitBlocks(markLinkTypes(markInlineLabels(content, markLabel, abbreviationKinds), markLinkType)),
            translations = byLanguage.entries
                /* The user's languages first, in their order, then the
                 * others in the order of the article */
                .sortedBy { (language, _) ->
                    preferredLanguages.indexOf(language).let { if (it < 0) Int.MAX_VALUE else it }
                }
                .map { (language, texts) -> Translation(language, joinSenses(texts)) },
        )
    }

    return ArticleLayout(words, otherTranslations)
}

/** Joins the translations of the senses of a word, merging the senses that
 * have the same one: [(1, a), (3, a), (4, b)] → "(1, 3) a; (4) b". */
private fun joinSenses(texts: List<Pair<String, String>>): String =
    texts.groupBy({ it.second }, { it.first }).entries.joinToString("; ") { (text, senses) ->
        val numbers = senses.filter { it.isNotEmpty() }
        if (numbers.isEmpty()) text else "(${numbers.joinToString(", ")}) $text"
    }

/**
 * Splits "~o: topologie; ~a 1: topologique" into the section each part
 * points to, its sense ("" for the whole word) and its text:
 * [(0, "", "topologie"), (1, "1", "topologique")].
 * Returns null if the text doesn't follow this structure.
 */
private fun splitTranslations(content: Spanned): List<Triple<Int, String, String>>? {
    val links = content.getSpans(0, content.length, SectionLinkSpan::class.java)
        .sortedBy { content.getSpanStart(it) }

    if (links.isEmpty())
        return null

    /* Nothing should come before the first link */
    if (content.subSequence(0, content.getSpanStart(links[0])).isNotBlank())
        return null

    return links.mapIndexed { i, link ->
        val start = content.getSpanStart(link)
        val end = if (i + 1 < links.size) content.getSpanStart(links[i + 1]) else content.length
        val part = content.subSequence(start, end).toString().trim().removeSuffix(";").trim()

        /* "~a 1: topologique" → the label "~a 1" and the text */
        val colon = part.indexOf(':')
        if (colon < 0)
            return null

        /* The label is the word, abbreviated ("~o", "malforta ~o", "~o de
         * simpla konverĝo"), followed by the number of the sense when the
         * translation is only for one of them ("~a 1") */
        val label = part.substring(0, colon).trim()
        val text = part.substring(colon + 1).trim()
        val sense = label.substringAfterLast(' ', "").takeIf { it.isNotEmpty() && it.all(Char::isDigit) } ?: ""

        Triple(link.section, sense, text)
    }
}

/** A form of a title ("monark/o" in "monarĥ/o, monark/o"), and the
 * officiality marks put before it ("*", "8"…). */
class OfficialForm(val text: String, val marks: List<String>)

/** A title without its officiality marks, and its forms with theirs. */
class OfficialTitle(val text: CharSequence, val forms: List<OfficialForm>) {
    /** The marks of all the forms, once each, the oldest first */
    val marks: List<String>
        get() = forms.flatMap { it.marks }.distinct().sortedBy { officialLevel(it) ?: Int.MAX_VALUE }

    /** Whether the forms don't all have the same marks: then only the
     * panel can tell which has which */
    val formsDiffer: Boolean
        get() = forms.size > 1 && forms.map { it.marks.toSet() }.distinct().size > 1
}

/** Reads the officiality marks of a title: superscripts made of "*" or of
 * a number, before a word (after its start, a space or another mark, and
 * before a letter, maybe after spaces or other marks). A superscript
 * right after a word is a number of homonym, and stays. ReVo puts them
 * at the start ("*7Mari/o", "* 7germano") and before variants
 * ("monarĥ/o, 8monark/o"). */
fun parseOfficial(header: Spanned): OfficialTitle {
    val superscripts = header.getSpans(0, header.length, SuperscriptSpan::class.java)
        .map { header.getSpanStart(it) to header.getSpanEnd(it) }
        .distinct()
        .filter { (start, end) ->
            val text = header.subSequence(start, end).toString().trim()
            text == "*" || (text.isNotEmpty() && text.all { it.isDigit() })
        }
        .sortedBy { it.first }

    val marks = superscripts.filter { (start, end) ->
        /* Not right after a letter */
        if (start > 0 && header[start - 1].isLetter())
            return@filter false
        /* Before a letter, through spaces and other marks */
        var next = end
        while (next < header.length) {
            if (header[next].isWhitespace())
                next++
            else
                next = superscripts.firstOrNull { it.first == next }?.second ?: break
        }
        next < header.length && header[next].isLetter()
    }

    /* The forms, separated by commas, with the marks inside each */
    val forms = mutableListOf<OfficialForm>()
    var formStart = 0
    for (formEnd in header.indices.filter { header[it] == ',' } + header.length) {
        val inForm = marks.filter { it.first in formStart until formEnd }
        val text = StringBuilder()
        var position = formStart
        for ((start, end) in inForm) {
            text.append(header, position, start)
            position = end
        }
        text.append(header, position, formEnd)
        val formText = text.toString().replace(Regex("\\s+"), " ").trim()
        if (formText.isNotEmpty())
            forms.add(OfficialForm(formText, inForm.map { header.subSequence(it.first, it.second).toString().trim() }))
        formStart = formEnd + 1
    }

    /* The title without the marks, nor the spaces after them */
    val text = SpannableStringBuilder(header)
    for ((start, end) in marks.sortedByDescending { it.first }) {
        var after = end
        while (after < text.length && text[after] == ' ')
            after++
        text.delete(start, after)
    }

    return OfficialTitle(text.trim() as CharSequence, forms)
}

/** The marks of a title, the oldest first, and the title without them. */
fun splitOfficial(header: Spanned): Pair<List<String>, CharSequence> =
    parseOfficial(header).let { it.marks to it.text }

/** Splits the content into text and remarks. prevodb writes a remark as a
 * "quotation" span starting with "Rim.". */
private fun splitBlocks(content: Spanned): List<ContentBlock> {
    val notes = content.getSpans(0, content.length, QuoteSpan::class.java)
        .map { content.getSpanStart(it) to content.getSpanEnd(it) }
        .sortedBy { it.first }

    val blocks = mutableListOf<ContentBlock>()
    var position = 0

    fun addText(end: Int) {
        blocks.addAll(splitExamples(content, position, end))
    }

    for ((start, end) in notes) {
        if (start < position)
            continue
        addText(start)
        val note = cleanNote(content.subSequence(start, end).trimBlankLines())
        if (note.isNotBlank())
            blocks.add(ContentBlock.Note(note))
        position = end
        /* The punctuation after a remark is only its end, eg. in
         * "komputilo", where it was left alone on a line */
        while (position < content.length && (content[position] in ";,." || content[position].isWhitespace()))
            position++
    }
    addText(content.length)

    return blocks
}

/** Removes the "Rim." and the quotation bar that the box replaces and, as
 * the original prevodb drops the sources, a "Laŭ :" left without its
 * source. */
private fun cleanNote(note: CharSequence): CharSequence {
    var text = note
    text = text.removePrefixIgnoringSpace("Rim.")
    text = text.removePrefixIgnoringSpace("Laŭ :")

    /* The box replaces the bar of the quotation style */
    val withoutBar = SpannableStringBuilder(text)
    for (span in withoutBar.getSpans(0, withoutBar.length, QuoteSpan::class.java))
        withoutBar.removeSpan(span)
    return withoutBar
}

private fun CharSequence.removePrefixIgnoringSpace(prefix: String): CharSequence {
    val trimmed = trimStart()
    return if (trimmed.startsWith(prefix)) trimmed.subSequence(prefix.length, trimmed.length).trimStart()
           else trimmed
}

/** Trims the spaces and line breaks around a part of the text, keeping its
 * spans. */
private fun CharSequence.trimBlankLines(): CharSequence {
    var start = 0
    var end = length
    while (start < end && this[start].isWhitespace()) start++
    while (end > start && this[end - 1].isWhitespace()) end--
    return subSequence(start, end)
}

/** Splits a part of the content into text and examples. Consecutive
 * examples are only separated by spaces, as their punctuation (";") is in
 * the example itself, and is removed here. */
private fun splitExamples(content: Spanned, start: Int, end: Int): List<ContentBlock> {
    val examples = content.getSpans(start, end, ExampleSpan::class.java)
        .map { content.getSpanStart(it) to content.getSpanEnd(it) }
        .filter { (s, e) -> s >= start && e <= end }
        .sortedBy { it.first }
    val blocks = mutableListOf<ContentBlock>()
    var position = start

    fun addText(to: Int) {
        var from = position
        /* After an example, the ";", "," or "." that follow it are only
         * its punctuation, between two examples or after the last one:
         * dropping them keeps the examples together, and avoids a line
         * with only a "." before the next sense */
        if (blocks.lastOrNull() is ContentBlock.Example) {
            while (from < to && (content[from] in ";,." || content[from].isWhitespace()))
                from++
        }
        val text = content.subSequence(from, to).trimBlankLines()
        if (text.isNotBlank())
            blocks.add(ContentBlock.Text(text))
    }

    for ((s, e) in examples) {
        if (s < position)
            continue
        addText(s)
        /* "disbranĉiĝanta:" announced the examples on the same line, as
         * in a printed dictionary; here they have their own lines, or are
         * folded, and the definition ends with a period instead */
        (blocks.lastOrNull() as? ContentBlock.Text)?.let { text ->
            withFinalPeriod(text.text)?.let { blocks[blocks.lastIndex] = ContentBlock.Text(it) }
        }
        val example = content.subSequence(s, e).trimBlankLines().trimEndPunctuation()
        if (example.isNotBlank())
            blocks.add(ContentBlock.Example(example))
        position = e
    }
    addText(end)

    return blocks
}

/** The text with a period in place of the colon at its end, or null if it
 * doesn't end with one. */
private fun withFinalPeriod(text: CharSequence): CharSequence? {
    var end = text.length
    while (end > 0 && text[end - 1].isWhitespace()) end--
    if (end == 0 || text[end - 1] != ':')
        return null
    end--
    while (end > 0 && text[end - 1].isWhitespace()) end--
    val sentence = SpannableStringBuilder(text.subSequence(0, end))
    /* "k.t.p.:" has its period already */
    if (end > 0 && text[end - 1] !in ".!?…")
        sentence.append('.')
    return sentence
}

/** Takes the labels given for the whole word out of the content: the
 * paragraphs at its start that only contain fields and kinds of word. */
private fun splitLabels(content: Spanned): Pair<List<Label>, Spanned> {
    val text = content.toString()
    val labels = mutableListOf<Label>()
    var start = 0

    while (true) {
        while (start < text.length && text[start].isWhitespace())
            start++
        if (start >= text.length)
            break

        val end = text.indexOf("\n\n", start).let { if (it < 0) text.length else it }
        val spans = labelSpans(content, start, end)
        if (spans.isEmpty())
            break

        /* Only take the paragraph if there is nothing else in it */
        var rest = text.substring(start, end)
        for ((_, s, e) in spans)
            rest = rest.replace(text.substring(s, e), "")
        if (rest.trim(' ', ',', ';', '(', ')').isNotEmpty())
            break

        for (span in spans)
            labels.add(Label(span.isField, text.substring(span.start, span.end).trim(), span.abbreviation))
        start = end
    }

    return labels to content.subSequence(start, content.length) as Spanned
}

/** The fields and kinds of word in a part of the content: whether it is a
 * field, its start and its end. */
/** A label found in the text: a field, a kind of word or an
 * abbreviation, and where it is */
private class LabelSpan(val isField: Boolean, val start: Int, val end: Int, val abbreviation: Int = -1) {
    operator fun component1() = isField
    operator fun component2() = start
    operator fun component3() = end
}

private fun labelSpans(content: Spanned, start: Int, end: Int): List<LabelSpan> {
    val fields = content.getSpans(start, end, FieldSpan::class.java)
        .map { LabelSpan(true, content.getSpanStart(it), content.getSpanEnd(it)) }
    val grammar = content.getSpans(start, end, GrammarSpan::class.java)
        .map { LabelSpan(false, content.getSpanStart(it), content.getSpanEnd(it)) }
    /* "DE" for Germanujo: shown as "landokodo: DE" */
    val abbreviations = content.getSpans(start, end, AbbreviationSpan::class.java)
        .map { LabelSpan(false, content.getSpanStart(it), content.getSpanEnd(it), it.kind) }

    return (fields + grammar + abbreviations)
        .filter { it.start >= start && it.end <= end }
        .sortedBy { it.start }
}

/** The abbreviations at the start of a paragraph, after its labels
 * only: "PLN" in "ekonomiko PLN Monunuo de Pollando" */
private fun leadingAbbreviations(content: Spanned, spans: List<LabelSpan>): List<LabelSpan> {
    val text = content.toString()

    return spans.filter { abbreviation ->
        if (abbreviation.abbreviation < 0)
            return@filter false
        val paragraph = text.lastIndexOf("\n\n", abbreviation.start).let { if (it < 0) 0 else it + 2 }
        var before = text.substring(paragraph, abbreviation.start)
        for (label in spans)
            if (label.abbreviation < 0 && label.start >= paragraph && label.end <= abbreviation.start)
                before = before.replace(text.substring(label.start, label.end), "")
        before.trim(' ', ',', ';', '(', ')', '\n').isEmpty()
    }
}

/** Shows the fields and kinds of word left within the text as labels,
 * and an abbreviation right after them as one too, with its kind
 * ("valutokodo: PLN"). An abbreviation within a sentence ("(atm)")
 * stays text. */
private val LABEL_SEPARATOR = Regex(""",\s*""")

private fun markInlineLabels(content: Spanned, markLabel: () -> Any, abbreviationKinds: List<String>): Spanned {
    val all = labelSpans(content, 0, content.length)
    val spans = all.filter { it.abbreviation < 0 } + leadingAbbreviations(content, all)
    if (spans.isEmpty())
        return content

    /* From the end, so that the words added for the abbreviations don't
     * move the spans still to mark */
    val marked = SpannableStringBuilder(content)
    for (span in spans.sortedByDescending { it.start }) {
        var end = span.end
        val kind = abbreviationKinds.getOrNull(span.abbreviation)
        if (kind != null) {
            val label = "$kind: ${content.subSequence(span.start, span.end)}"
            marked.replace(span.start, span.end, label)
            end = span.start + label.length
            marked.setSpan(markLabel(), span.start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            continue
        }
        /* "hortikulturo, arbokulturo, silvikulturo": one label each,
         * apart, so that they go to the next line instead of past the
         * edge, which a label, drawn in one piece, would */
        for (piece in LABEL_SEPARATOR.findAll(content.subSequence(span.start, span.end)).toList().reversed()) {
            val at = span.start + piece.range.first
            if (at + piece.value.length < end)
                marked.setSpan(markLabel(), at + piece.value.length, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            marked.replace(at, at + piece.value.length, " ")
            end = at
        }
        marked.setSpan(markLabel(), span.start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
    }
    return marked
}

private fun CharSequence.trimEndPunctuation(): CharSequence {
    var end = length
    while (end > 0 && (this[end - 1].isWhitespace() || this[end - 1] == ';'))
        end--
    return subSequence(0, end)
}

/** A short preview of a word for the list of search results: its title
 * and the start of its definition. */
class WordPreview(val title: String, val definition: String)

private val SENSE_NUMBER = Regex("""^(\d+\.|[a-z]\))\s*""")

/**
 * The preview of the section a search result points to. A result in
 * another language than Esperanto points to the word it translates, or
 * to a translation section; in that case the first word of the article is
 * used. Returns null if the article has no word.
 */
fun previewOf(article: Article, mark: Int): WordPreview? {
    val section = article.sections.getOrNull(mark)?.takeIf { it.isWord() }
        ?: article.sections.firstOrNull { it.isWord() }
        ?: return null

    val (_, title) = splitOfficial(section.header)
    val (_, content) = splitLabels(section.content)

    /* Left out of the preview: the labels within the text, eg. "muziko"
     * at the start of a sense, so that it starts with the definition
     * itself, and the superscripts, eg. the sense number of "trovi¹",
     * which would read as "trovi1" in plain text */
    val superscripts = content.getSpans(0, content.length, SuperscriptSpan::class.java)
        .map { content.getSpanStart(it) to content.getSpanEnd(it) }
    val labels = labelSpans(content, 0, content.length)
    val removed = ((labels.filter { it.abbreviation < 0 } + leadingAbbreviations(content, labels))
        .map { (_, start, end) -> start to end } + superscripts)
        .sortedBy { it.first }
        /* Merged where they overlap, so that nothing is deleted twice */
        .fold(mutableListOf<Pair<Int, Int>>()) { ranges, range ->
            val last = ranges.lastOrNull()
            if (last != null && range.first <= last.second)
                ranges[ranges.lastIndex] = last.first to maxOf(last.second, range.second)
            else
                ranges.add(range)
            ranges
        }
    val withoutLabels = SpannableStringBuilder(content)
    for ((start, end) in removed.asReversed())
        withoutLabels.delete(start, end)

    /* The first paragraph that says something: not a lone sense number,
     * nor a label in brackets such as "(malofte)" */
    val definition = splitBlocks(withoutLabels)
        .filterIsInstance<ContentBlock.Text>()
        .flatMap { it.text.toString().split("\n\n") }
        .map { it.replace(SENSE_NUMBER, "").replace(Regex("""\s+"""), " ").trim() }
        .firstOrNull { it.length > 3 && !(it.startsWith("(") && it.endsWith(")")) }
        ?.removeSuffix(":")
        ?.trim()
        ?.withoutFinalPeriod()
        ?: ""

    return WordPreview(title.toString(), definition)
}

/* A preview ends without a period, as most definitions do; the period of
 * an abbreviation ("k.t.p.") stays */
private fun String.withoutFinalPeriod(): String =
    if (endsWith(".") && '.' !in substringAfterLast(' ').dropLast(1)) dropLast(1).trimEnd() else this

/** Shows the symbols of the types of links as words: the plural when it
 * is followed by several links, up to the end of the group (";", ".") or
 * of the line. */
private fun markLinkTypes(content: Spanned, markLinkType: (LinkType, String) -> Any): Spanned {
    val spans = content.getSpans(0, content.length, LinkTypeSpan::class.java)
    if (spans.isEmpty())
        return content

    val marked = SpannableStringBuilder(content)
    val text = content.toString()

    for (span in spans) {
        val type = LinkType.entries.getOrNull(span.type) ?: continue
        val start = content.getSpanStart(span)
        val end = content.getSpanEnd(span)

        var groupEnd = end
        while (groupEnd < text.length && text[groupEnd] !in ";.\n")
            groupEnd++
        val links = content.getSpans(end, groupEnd, ClickableSpan::class.java).size

        marked.setSpan(
            markLinkType(type, if (links > 1) type.several else type.one),
            start,
            end,
            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
        )
    }

    return marked
}
