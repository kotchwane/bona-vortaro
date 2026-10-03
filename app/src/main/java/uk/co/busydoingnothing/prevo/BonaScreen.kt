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

package uk.co.busydoingnothing.prevo

import android.content.res.Configuration
import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource

/** What every screen of the app does: drawing under the system bars,
 * and following a change of theme without being recreated (the activities
 * declare configChanges="uiMode" in the manifest). */
abstract class BonaActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        /* The colours of the icons of the system bars follow the theme only
         * when they are set again */
        enableEdgeToEdge()
    }
}

/** A screen on the background of the design: paper by day, the night
 * sky by night. */
@Composable
fun BonaScaffold(
    modifier: Modifier = Modifier,
    topBar: @Composable () -> Unit = {},
    bottomBar: @Composable () -> Unit = {},
    snackbarHost: @Composable () -> Unit = {},
    content: @Composable (PaddingValues) -> Unit,
) {
    Scaffold(
        modifier = modifier.bonaBackground(),
        topBar = topBar,
        bottomBar = bottomBar,
        snackbarHost = snackbarHost,
        containerColor = Color.Transparent,
        /* The colour of the text, which a transparent container doesn't give */
        contentColor = MaterialTheme.colorScheme.onBackground,
        content = content,
    )
}

/** The top bar of a screen, with a back arrow unless [onBack] is null. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BonaTopBar(
    title: @Composable () -> Unit,
    onBack: (() -> Unit)?,
    actions: @Composable RowScope.() -> Unit = {},
) {
    TopAppBar(
        colors = bonaTopBarColors(),
        title = title,
        navigationIcon = {
            if (onBack != null)
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back))
                }
        },
        actions = actions,
    )
}
