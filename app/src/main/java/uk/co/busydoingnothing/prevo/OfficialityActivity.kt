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

import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

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
class OfficialityActivity : AppCompatActivity() {

    companion object {
        fun open(context: Context) {
            context.startActivity(Intent(context, OfficialityActivity::class.java))
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        /* The colours of the icons of the system bars follow the theme only
         * when they are set */
        enableEdgeToEdge()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        setContent {
            PrevoTheme { OfficialityPage(onBack = ::finish) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun OfficialityPage(onBack: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.officiality_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back))
                    }
                },
            )
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
            OfficialTimeline(level = null)

            Paragraph(R.string.officiality_fundamento_title, R.string.officiality_fundamento_text)
            Paragraph(R.string.officiality_additions_title, R.string.officiality_additions_text)
            Paragraph(R.string.officiality_meaning_title, R.string.officiality_meaning_text)

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

@Composable
private fun Paragraph(title: Int, text: Int) {
    Spacer(Modifier.height(24.dp))
    Text(
        stringResource(title),
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.Bold,
    )
    Spacer(Modifier.height(6.dp))
    Text(stringResource(text), style = MaterialTheme.typography.bodyLarge)
}

/** What the badge of a word means, opened by tapping it: a short
 * explanation, where the word is in the timeline, and a link to the
 * page with more. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OfficialSheet(level: Int, onDismiss: () -> Unit) {
    val context = LocalContext.current

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(start = 24.dp, end = 24.dp, bottom = 32.dp)) {
            Text(
                if (level == 0) stringResource(R.string.official_fundamento_title)
                else stringResource(R.string.official_addition_title, level),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(12.dp))
            Text(
                if (level == 0) stringResource(R.string.official_fundamento_text)
                else stringResource(R.string.official_addition_text, level, OFFICIAL_YEARS[level]),
                style = MaterialTheme.typography.bodyLarge,
            )
            Spacer(Modifier.height(20.dp))
            Text(
                stringResource(R.string.official_timeline),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            OfficialTimeline(level)
            Spacer(Modifier.height(20.dp))
            OutlinedButton(onClick = {
                onDismiss()
                OfficialityActivity.open(context)
            }) {
                Text(stringResource(R.string.official_more))
            }
        }
    }
}

/** The Fundamento and the Oficialaj Aldonoj with their years: "F", 1,
 * 2… The given level is highlighted, and the earlier ones tinted. */
@Composable
private fun OfficialTimeline(level: Int?) {
    val colors = MaterialTheme.colorScheme

    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        for ((index, year) in OFFICIAL_YEARS.withIndex()) {
            val current = index == level
            val earlier = level != null && index < level

            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
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
