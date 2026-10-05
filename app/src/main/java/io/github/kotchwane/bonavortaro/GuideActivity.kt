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

/** How to use the app: the things about the search and the articles
 * that nobody guesses, gathered in one page of the settings. */
class GuideActivity : BonaActivity() {

    companion object {
        fun open(context: Context) {
            context.startActivity(Intent(context, GuideActivity::class.java))
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            BonaTheme { GuidePage(onBack = ::finish) }
        }
    }
}

/* The tips, by part: a title, then its tips, each a title and a text */
private val GUIDE = listOf(
    R.string.guide_search to listOf(
        R.string.guide_languages_title to R.string.guide_languages,
        R.string.guide_typing_title to R.string.guide_typing,
        R.string.guide_wildcards_title to R.string.guide_wildcards,
        R.string.guide_history_title to R.string.guide_history,
    ),
    R.string.guide_read to listOf(
        R.string.guide_links_title to R.string.guide_links,
        R.string.guide_translations_title to R.string.guide_translations,
        R.string.guide_official_title to R.string.guide_official,
        R.string.guide_neighbours_title to R.string.guide_neighbours,
        R.string.guide_long_press_title to R.string.guide_long_press,
    ),
)

@Composable
private fun GuidePage(onBack: () -> Unit) {
    BonaScaffold(
        topBar = { BonaTopBar(title = { Text(stringResource(R.string.guide_title)) }, onBack = onBack) },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .navigationBarsPadding(),
        ) {
            for ((part, tips) in GUIDE) {
                Spacer(Modifier.height(16.dp))
                Text(
                    stringResource(part),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                )
                for ((title, text) in tips) {
                    Spacer(Modifier.height(14.dp))
                    Text(
                        stringResource(title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(withItalics(stringResource(text), marker = '_'), style = MaterialTheme.typography.bodyLarge)
                }
            }
            Spacer(Modifier.height(32.dp))
        }
    }
}
