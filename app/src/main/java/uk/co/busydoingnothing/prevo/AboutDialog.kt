/*
 * Bona vortaro - an Esperanto dictionary for Android, based on PReVo
 * Copyright (C) 2012, 2016  Neil Roberts
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

import android.content.Context
import android.content.pm.PackageManager
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration

private const val LICENSE_URL = "https://www.gnu.org/licenses/old-licenses/gpl-2.0-standalone.html"
private const val RETA_VORTARO_URL = "https://reta-vortaro.de/revo/"
private const val FORK_URL = "https://github.com/kotchwane/prevo"

/** The "Pri" dialog: version, copyright, data source and licence. */
@Composable
fun AboutDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val linkColor = MaterialTheme.colorScheme.primary
    val message = remember(linkColor) { aboutMessage(context, linkColor) }

    AlertDialog(
        onDismissRequest = onDismiss,
        text = { Text(message, style = MaterialTheme.typography.bodyMedium) },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.close)) }
        },
    )
}

/** The text of R.string.about_message with the version filled in, the
 * first line in bold, and the same links as in the original app. */
private fun aboutMessage(context: Context, linkColor: Color): AnnotatedString {
    val version = try {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName
    } catch (e: PackageManager.NameNotFoundException) {
        null
    } ?: "?"

    val text = context.getString(R.string.about_message)
        .trim()
        .replace("@VERSION@", version)
    val links = TextLinkStyles(
        SpanStyle(color = linkColor, textDecoration = TextDecoration.Underline)
    )

    return buildAnnotatedString {
        append(text)

        val firstLineEnd = text.indexOf('\n').takeIf { it >= 0 } ?: text.length
        addStyle(SpanStyle(fontWeight = FontWeight.Bold), 0, firstLineEnd)

        fun link(start: Int, end: Int, url: String) {
            if (start >= 0)
                addLink(LinkAnnotation.Url(url, links), start, end)
        }

        /* "Click here for details": only the word "here" is the link */
        val clickHere = text.indexOf("Click here for")
        if (clickHere >= 0)
            link(clickHere + "Click ".length, clickHere + "Click here".length, LICENSE_URL)

        for ((label, url) in listOf(
            "Reta Vortaro" to RETA_VORTARO_URL,
            "github.com/kotchwane/prevo" to FORK_URL,
        )) {
            val start = text.indexOf(label)
            link(start, start + label.length, url)
        }
    }
}
