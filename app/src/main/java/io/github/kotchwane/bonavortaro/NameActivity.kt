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
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp

/** The book of Claude Piron, on his website (which is only served over
 * HTTP; the browser opens it). */
private const val BOOK_URL = "http://claudepiron.free.fr/livres/bonalingvo.htm"

/** Why the app is called "Bona vortaro": a tribute to *La bona lingvo*,
 * the book of Claude Piron. */
class NameActivity : BonaActivity() {

    companion object {
        fun open(context: Context) {
            context.startActivity(Intent(context, NameActivity::class.java))
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            BonaTheme { NamePage(onBack = ::finish) }
        }
    }
}

@Composable
private fun NamePage(onBack: () -> Unit) {
    val context = LocalContext.current
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val body = MaterialTheme.typography.bodyLarge

    BonaScaffold(
        topBar = { BonaTopBar(title = { Text(stringResource(R.string.name_title)) }, onBack = onBack) },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .navigationBarsPadding(),
        ) {
            Spacer(Modifier.height(8.dp))

            /* The first sentence of the book, with a bar like a quotation */
            val bar = MaterialTheme.colorScheme.primary
            Column(
                Modifier
                    .drawBehind { drawRect(bar, size = Size(3.dp.toPx(), size.height)) }
                    .padding(start = 16.dp, top = 4.dp, bottom = 4.dp),
            ) {
                Text(
                    stringResource(R.string.name_quote),
                    style = MaterialTheme.typography.titleLarge.copy(fontStyle = FontStyle.Italic),
                    color = bonaDesign.headword,
                )
                Spacer(Modifier.height(6.dp))
                Text(withItalics(stringResource(R.string.name_quote_source)), style = MaterialTheme.typography.bodyMedium, color = muted)
            }

            for (paragraph in listOf(R.string.name_tribute, R.string.name_aim, R.string.name_examples)) {
                Spacer(Modifier.height(16.dp))
                Text(withItalics(stringResource(paragraph)), style = body)
            }

            Spacer(Modifier.height(24.dp))
            OutlinedButton(onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(BOOK_URL))) }) {
                Text(withItalics(stringResource(R.string.name_read_book)))
            }
            Spacer(Modifier.height(6.dp))
            Text(stringResource(R.string.name_read_book_where), style = MaterialTheme.typography.bodySmall, color = muted)
            Spacer(Modifier.height(32.dp))
        }
    }
}

/** The text with the parts between asterisks in italics: "*La bona
 * lingvo*" for a title, as the texts of strings.xml write them. Texts
 * that need a real asterisk mark their italics with another character. */
fun withItalics(text: String, marker: Char = '*'): AnnotatedString = buildAnnotatedString {
    text.split(marker).forEachIndexed { index, part ->
        if (index % 2 == 1) withStyle(SpanStyle(fontStyle = FontStyle.Italic)) { append(part) }
        else append(part)
    }
}
