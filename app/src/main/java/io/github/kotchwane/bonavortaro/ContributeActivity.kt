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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

private const val REVO_EDITORS_URL = "https://revuloj.github.io/temoj/redinfo"
private const val VIKI_EO_HELP_URL = "https://eo.wiktionary.org/wiki/Helpo:Kiel_redakti_pa%C4%9Don"
private const val VIKI_FR_HELP_URL = "https://fr.wiktionary.org/wiki/Aide:Accueil"
private const val ISSUES_URL = "https://github.com/kotchwane/bona-vortaro/issues"

/** How to improve the dictionary: at its sources, each with its own
 * editor and review (Reta Vortaro, Vikivortaro, Vortoj de la bona lingvo),
 * or the app itself on GitHub. The app doesn't edit anything: it works
 * without internet. */
class ContributeActivity : BonaActivity() {

    companion object {
        fun open(context: Context) {
            context.startActivity(Intent(context, ContributeActivity::class.java))
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            BonaTheme { ContributePage(onBack = ::finish) }
        }
    }
}

/** A part of the page: what to correct, where and how, and the links */
private class ContributePart(val title: Int, val text: Int, val links: List<Pair<Int, String>>)

private val CONTRIBUTE_PARTS = listOf(
    ContributePart(R.string.contribute_revo_title, R.string.contribute_revo, listOf(R.string.contribute_revo_link to REVO_EDITORS_URL)),
    ContributePart(
        R.string.contribute_viki_title, R.string.contribute_viki,
        listOf(R.string.contribute_viki_link to VIKI_EO_HELP_URL, R.string.contribute_viki_fr_link to VIKI_FR_HELP_URL),
    ),
    ContributePart(R.string.contribute_bl_title, R.string.contribute_bl, listOf(R.string.contribute_bl_link to BonaLingvo.SITE)),
    ContributePart(R.string.contribute_app_title, R.string.contribute_app, listOf(R.string.contribute_app_link to ISSUES_URL)),
)

@Composable
private fun ContributePage(onBack: () -> Unit) {
    val body = MaterialTheme.typography.bodyLarge

    BonaScaffold(
        topBar = { BonaTopBar(title = { Text(stringResource(R.string.contribute_title)) }, onBack = onBack) },
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
            Text(withItalics(stringResource(R.string.contribute_intro)), style = body)
            Spacer(Modifier.height(8.dp))
            Text(
                stringResource(R.string.contribute_why),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            for (part in CONTRIBUTE_PARTS) {
                Spacer(Modifier.height(20.dp))
                Text(
                    stringResource(part.title),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.height(4.dp))
                Text(withItalics(stringResource(part.text)), style = body)
                for ((label, url) in part.links) Link(label, url)
            }
            Spacer(Modifier.height(32.dp))
        }
    }
}
