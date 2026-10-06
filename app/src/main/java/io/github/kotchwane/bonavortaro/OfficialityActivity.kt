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
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.foundation.clickable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.material3.FilledTonalButton
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private const val FUNDAMENTO_URL = "https://eo.wikipedia.org/wiki/Fundamento_de_Esperanto"
private const val ADDITIONS_URL = "https://eo.wikipedia.org/wiki/Oficialaj_aldonoj"

/** The years of the Fundamento (index 0) and of the Oficialaj Aldonoj
 * (index n for the n-th), from the Akademio de Esperanto. */
val OFFICIAL_YEARS = listOf(
    "1905", "1909", "1919", "1921", "1929", "1934", "1935", "1958", "1974", "2007", "2023",
)

/** The level of an officiality mark of the dictionary: 0 for the
 * Fundamento ("*"), n for the n-th Oficiala Aldono, or null if the mark
 * is something else. */
fun officialLevel(official: String): Int? = when {
    official == "*" -> 0
    else -> official.toIntOrNull()?.takeIf { it in 1 until OFFICIAL_YEARS.size }
}

/** A page that explains the Fundamento and the Oficialaj Aldonoj. */
class OfficialityActivity : BonaActivity() {

    companion object {
        fun open(context: Context) {
            context.startActivity(Intent(context, OfficialityActivity::class.java))
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            BonaTheme { OfficialityPage(onBack = ::finish) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun OfficialityPage(onBack: () -> Unit) {
    BonaScaffold(
        topBar = {
            BonaTopBar(title = { Text(stringResource(R.string.officiality_title)) }, onBack = onBack)
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .navigationBarsPadding(),
        ) {
            Text(stringResource(R.string.officiality_intro), style = MaterialTheme.typography.bodyLarge)
            Spacer(Modifier.height(20.dp))
            val context = LocalContext.current
            OfficialTimeline(level = null, onSelect = { AdditionActivity.open(context, it) })

            Paragraph(R.string.officiality_fundamento_title, R.string.officiality_fundamento_text, FUNDAMENTO_URL)
            Paragraph(R.string.officiality_additions_title, R.string.officiality_additions_text, ADDITIONS_URL)
            OfficialListsSection()

            Spacer(Modifier.height(24.dp))
            Text(
                stringResource(R.string.officiality_source),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(24.dp))
        }
    }
}

/** One line per list, with its counts, opening it */
@Composable
private fun OfficialListsSection() {
    val context = LocalContext.current
    val all by produceState<List<OfficialEntry>?>(null) {
        value = withContext(Dispatchers.IO) { OfficialLists.get(context) }
    }

    Spacer(Modifier.height(24.dp))
    Text(
        stringResource(R.string.official_lists_title),
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.Bold,
    )
    for (level in OFFICIAL_YEARS.indices) {
        val counts = all?.let { OfficialLists.counts(OfficialLists.groups(it, level)) }
        Row(
            Modifier
                .fillMaxWidth()
                .clickable { AdditionActivity.open(context, level) }
                .padding(vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text("${levelName(level)} (${OFFICIAL_YEARS[level]})", style = MaterialTheme.typography.bodyLarge)
                counts?.let { (roots, words) ->
                    Text(
                        countsText(roots, words),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Text("›", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** A paragraph, and a link to more about it in Vikipedio */
@Composable
private fun Paragraph(title: Int, text: Int, link: String) {
    val context = LocalContext.current

    Spacer(Modifier.height(24.dp))
    Text(
        stringResource(title),
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.Bold,
    )
    Spacer(Modifier.height(6.dp))
    Text(stringResource(text), style = MaterialTheme.typography.bodyLarge)
    Text(
        stringResource(R.string.officiality_wikipedia),
        style = MaterialTheme.typography.bodyLarge.copy(textDecoration = TextDecoration.Underline),
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier
            .clickable { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(link))) }
            .padding(vertical = 8.dp),
    )
}

/** What the Fundamento or an Oficiala Aldono is, opened by tapping the
 * badge of a word: what is worth knowing about it, some of its words,
 * and where it is in the timeline. A circle of the timeline shows
 * another one in the same panel; the buttons open its list, and the
 * page with more. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OfficialSheet(
    level: Int,
    onDismiss: () -> Unit,
    /** The other levels of the same word, highlighted in the timeline */
    also: Set<Int> = emptySet(),
    /** The forms of the title, when they don't all have the same levels */
    forms: List<OfficialForm> = emptyList(),
) {
    val context = LocalContext.current
    var shown by rememberSaveable { mutableIntStateOf(level) }

    /* Opened at once to the height of its content, never half-way */
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(Modifier.padding(start = 24.dp, end = 24.dp, bottom = 32.dp)) {
            Text(
                "${levelName(shown)} (${OFFICIAL_YEARS[shown]})",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold,
            )
            /* Which form has which level: "monarĥo — Fundamento (1905)",
             * "monarko — 8a Oficiala Aldono (1974)" */
            if (forms.isNotEmpty()) {
                Spacer(Modifier.height(10.dp))
                for (form in forms) {
                    val levels = form.marks.mapNotNull { officialLevel(it) }.distinct().sorted()
                    Row(Modifier.padding(vertical = 2.dp)) {
                        Text(form.text, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold, color = bonaDesign.headword)
                        Text(
                            " — " + if (levels.isEmpty()) stringResource(R.string.official_form_unofficial)
                            else levels.map { "${levelName(it)} (${OFFICIAL_YEARS[it]})" }.joinToString(", "),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            AdditionAbout(shown, examples = true)
            Spacer(Modifier.height(20.dp))
            Text(
                stringResource(R.string.official_timeline),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            /* All the levels of the word, of all its forms */
            val marked = also + level + forms.flatMap { form -> form.marks.mapNotNull { officialLevel(it) } }
            OfficialTimeline(shown, onSelect = { shown = it }, marked = marked)
            Spacer(Modifier.height(20.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                FilledTonalButton(
                    onClick = {
                        onDismiss()
                        AdditionActivity.open(context, shown)
                    },
                    modifier = Modifier.weight(1f),
                ) {
                    Text(stringResource(R.string.addition_list), maxLines = 1)
                }
                OutlinedButton(
                    onClick = {
                        onDismiss()
                        OfficialityActivity.open(context)
                    },
                    modifier = Modifier.weight(1f),
                ) {
                    Text(stringResource(R.string.official_more), maxLines = 1)
                }
            }
        }
    }
}

/** What is worth knowing about a level, optionally some of its words,
 * and how many roots and words of this dictionary it made official. */
@Composable
fun AdditionAbout(level: Int, examples: Boolean) {
    val context = LocalContext.current
    val all by produceState<List<OfficialEntry>?>(null) {
        value = withContext(Dispatchers.IO) { OfficialLists.get(context) }
    }
    val muted = MaterialTheme.colorScheme.onSurfaceVariant

    Text(stringArrayResource(R.array.addition_about)[level], style = MaterialTheme.typography.bodyLarge)
    if (examples) {
        Spacer(Modifier.height(8.dp))
        Text(
            stringResource(R.string.addition_examples, stringArrayResource(R.array.addition_example_words)[level]),
            style = MaterialTheme.typography.bodyLarge,
        )
    }
    all?.let { entries ->
        val (roots, words) = remember(entries, level) { OfficialLists.counts(OfficialLists.groups(entries, level)) }
        Spacer(Modifier.height(8.dp))
        Text(
            stringResource(R.string.addition_counts, countsText(roots, words)),
            style = MaterialTheme.typography.bodyMedium.copy(fontStyle = FontStyle.Italic),
            color = muted,
        )
    }
}

/** "Fundamento" or "9a Oficiala Aldono" */
@Composable
fun levelName(level: Int): String =
    if (level == 0) stringResource(R.string.official_fundamento)
    else stringResource(R.string.official_addition, level)

/** "209 radikoj, 72 vortoj" */
@Composable
fun countsText(roots: Int, words: Int): String {
    val r = if (roots == 1) stringResource(R.string.one_root) else stringResource(R.string.roots, roots)
    val w = if (words == 1) stringResource(R.string.one_word) else stringResource(R.string.words, words)
    return when {
        words == 0 -> r
        roots == 0 -> w
        else -> "$r, $w"
    }
}

/** The Fundamento and the Oficialaj Aldonoj with their years: "F", 1,
 * 2… The given level is highlighted, and the earlier ones tinted. A tap
 * on a circle selects its level. */
@Composable
fun OfficialTimeline(
    level: Int?,
    onSelect: ((Int) -> Unit)? = null,
    /** Levels shown as the word's own, besides the current one */
    marked: Set<Int> = emptySet(),
) {
    val colors = MaterialTheme.colorScheme

    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        for ((index, year) in OFFICIAL_YEARS.withIndex()) {
            val current = index == level || index in marked
            val earlier = level != null && index < level

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .weight(1f)
                    .then(if (onSelect != null) Modifier.clickable { onSelect(index) } else Modifier)
                    .padding(vertical = 4.dp),
            ) {
                Surface(
                    shape = CircleShape,
                    color = when {
                        current || level == null -> colors.primary
                        earlier -> colors.primaryContainer
                        else -> colors.surfaceContainerHigh
                    },
                    contentColor = if (current || level == null) colors.onPrimary else colors.onSurfaceVariant,
                    modifier = Modifier.size(24.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            if (index == 0) "F" else "$index",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
                Text(
                    year,
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                    color = if (current) colors.primary else colors.onSurfaceVariant,
                    fontWeight = if (current) FontWeight.Bold else null,
                    maxLines = 1,
                )
            }
        }
    }
}
