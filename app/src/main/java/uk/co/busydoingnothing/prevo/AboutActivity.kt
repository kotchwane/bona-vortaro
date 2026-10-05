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
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp

private const val PREVO_URL = "https://github.com/bpeel/prevo"
private const val SOURCE_URL = "https://github.com/kotchwane/bona-vortaro"
private const val RETA_VORTARO_URL = "https://reta-vortaro.de/"
private const val LICENSE_URL = "https://www.gnu.org/licenses/old-licenses/gpl-2.0-standalone.html"

/** About the app: what it is, that it is free software, works without
 * internet and asks for no permission, where its data comes from, its
 * copyright, and that it comes with no warranty. */
class AboutActivity : BonaActivity() {

    companion object {
        fun open(context: Context) {
            context.startActivity(Intent(context, AboutActivity::class.java))
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            BonaTheme { AboutPage(onBack = ::finish) }
        }
    }
}

/* The parts of the page: a title, a text, and maybe a link */
private class AboutPart(val title: Int, val text: Int, val link: Pair<Int, String>? = null)

private val PARTS = listOf(
    AboutPart(R.string.about_free_title, R.string.about_free, R.string.about_source_link to SOURCE_URL),
    AboutPart(R.string.about_offline_title, R.string.about_offline),
    AboutPart(R.string.about_permissions_title, R.string.about_permissions),
    AboutPart(R.string.about_data_title, R.string.about_data, R.string.about_data_link to RETA_VORTARO_URL),
    AboutPart(R.string.about_copyright_title, R.string.about_copyright),
    AboutPart(R.string.about_warranty_title, R.string.about_warranty, R.string.about_license_link to LICENSE_URL),
)

/** The version of the app, as the build names it */
fun appVersion(context: Context): String = try {
    context.packageManager.getPackageInfo(context.packageName, 0).versionName
} catch (e: PackageManager.NameNotFoundException) {
    null
} ?: "?"

@Composable
private fun AboutPage(onBack: () -> Unit) {
    val context = LocalContext.current
    val body = MaterialTheme.typography.bodyLarge

    BonaScaffold(
        topBar = { BonaTopBar(title = { Text(stringResource(R.string.about_app)) }, onBack = onBack) },
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
            Text(
                stringResource(R.string.about_version, appVersion(context)),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            Text(withLinks(stringResource(R.string.about_intro), "Retan Vortaron" to RETA_VORTARO_URL), style = body)
            /* Where it comes from, at once: PReVo is its starting point */
            Spacer(Modifier.height(8.dp))
            Text(withLinks(stringResource(R.string.about_origin), "PReVo" to PREVO_URL), style = body)

            for (part in PARTS) {
                Spacer(Modifier.height(20.dp))
                Text(
                    stringResource(part.title),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.height(4.dp))
                Text(withItalics(stringResource(part.text)), style = body)
                part.link?.let { (label, url) -> Link(label, url) }
            }
            Spacer(Modifier.height(32.dp))
        }
    }
}

/** The text with its italics, and a word of it as a link to a website */
@Composable
private fun withLinks(text: String, link: Pair<String, String>): AnnotatedString {
    val style = TextLinkStyles(
        SpanStyle(color = MaterialTheme.colorScheme.primary, textDecoration = TextDecoration.Underline)
    )
    val italics = withItalics(text)

    return buildAnnotatedString {
        append(italics)
        val (label, url) = link
        val start = italics.text.indexOf(label)
        if (start >= 0)
            addLink(LinkAnnotation.Url(url, style), start, start + label.length)
    }
}

/** A link to a website, opened in the browser */
@Composable
private fun Link(label: Int, url: String) {
    val context = LocalContext.current

    Text(
        stringResource(label),
        style = MaterialTheme.typography.bodyLarge.copy(textDecoration = TextDecoration.Underline),
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier
            .clickable { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
            .padding(vertical = 8.dp),
    )
}
