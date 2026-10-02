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

import android.text.SpannableStringBuilder
import android.text.Spanned
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
class Label(val isField: Boolean, val text: String)

class Translation(val language: String, val text: String)

class WordEntry(
    /** Index of the section in the article, as used by the links */
    val section: Int,
    val source: ArticleSection,
    /** The officiality marks of the word ("*", "1"…) */
    val official: List<String>,
    /** The header without its officiality mark */
    val title: CharSequence,
    val labels: List<Label>,
    val blocks: List<ContentBlock>,
    val translations: List<Translation>,
)

class ArticleLayout(val words: List<WordEntry>, val otherTranslations: List<ArticleSection>)

/** Translation sections are those of a language other than Esperanto. */
private fun ArticleSection.isWord() = language == "eo"

/**
 * @param markLabel called for each field or kind of word left within the
 *   text (eg. at the start of a sense, "a) muziko …"), to return the span
 *   that shows it as a label
 */
fun layoutArticle(
    article: Article,
    preferredLanguages: List<String>,
    markLabel: () -> Any,
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
        val (official, title) = splitOfficial(section.header)
        val (labels, content) = splitLabels(section.content)
        val byLanguage = translations[index] ?: emptyMap<String, List<Pair<String, String>>>()

        WordEntry(
            section = index,
            source = section,
            official = official,
            title = title,
            labels = labels,
            blocks = splitBlocks(markInlineLabels(content, markLabel)),
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

/** Separates the officiality marks ("*" for the Fundamento, a number for
 * an Official Addition), written as superscripts at the start of the
 * header, from the rest of the header. A word can have several, eg.
 * "*" and "7" when one of its senses came with the 7th addition. */
fun splitOfficial(header: Spanned): Pair<List<String>, CharSequence> {
    val superscripts = header.getSpans(0, header.length, SuperscriptSpan::class.java)
        .map { header.getSpanStart(it) to header.getSpanEnd(it) }
    val marks = mutableListOf<String>()
    var position = 0

    while (true) {
        val end = superscripts.filter { it.first == position }.maxOfOrNull { it.second } ?: break
        val mark = header.subSequence(position, end).toString().trim()
        if (mark.isEmpty() || !(mark == "*" || mark.all { it.isDigit() }))
            break
        marks.add(mark)
        position = end
    }

    return marks to header.subSequence(position, header.length).trimStart()
}

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
    }
    addText(content.length)

    return blocks
}

/** Removes the "Rim." that the box replaces and, as prevodb drops the
 * sources, a "Laŭ :" left without its source. */
private fun cleanNote(note: CharSequence): CharSequence {
    var text = note
    text = text.removePrefixIgnoringSpace("Rim.")
    text = text.removePrefixIgnoringSpace("Laŭ :")
    return text
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
        val text = content.subSequence(position, to).trimBlankLines()
        if (text.isNotBlank())
            blocks.add(ContentBlock.Text(text))
    }

    for ((s, e) in examples) {
        if (s < position)
            continue
        addText(s)
        val example = content.subSequence(s, e).trimBlankLines().trimEndPunctuation()
        if (example.isNotBlank())
            blocks.add(ContentBlock.Example(example))
        position = e
    }
    addText(end)

    return blocks
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

        for ((isField, s, e) in spans)
            labels.add(Label(isField, text.substring(s, e).trim()))
        start = end
    }

    return labels to content.subSequence(start, content.length) as Spanned
}

/** The fields and kinds of word in a part of the content: whether it is a
 * field, its start and its end. */
private fun labelSpans(content: Spanned, start: Int, end: Int): List<Triple<Boolean, Int, Int>> {
    val fields = content.getSpans(start, end, FieldSpan::class.java)
        .map { Triple(true, content.getSpanStart(it), content.getSpanEnd(it)) }
    val grammar = content.getSpans(start, end, GrammarSpan::class.java)
        .map { Triple(false, content.getSpanStart(it), content.getSpanEnd(it)) }

    return (fields + grammar)
        .filter { (_, s, e) -> s >= start && e <= end }
        .sortedBy { it.second }
}

/** Shows the fields and kinds of word left within the text as labels. */
private fun markInlineLabels(content: Spanned, markLabel: () -> Any): Spanned {
    val spans = labelSpans(content, 0, content.length)
    if (spans.isEmpty())
        return content

    val marked = SpannableStringBuilder(content)
    for ((_, s, e) in spans)
        marked.setSpan(markLabel(), s, e, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
    return marked
}

private fun CharSequence.trimEndPunctuation(): CharSequence {
    var end = length
    while (end > 0 && (this[end - 1].isWhitespace() || this[end - 1] == ';'))
        end--
    return subSequence(0, end)
}
