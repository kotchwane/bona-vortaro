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

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

/* Teal, close to the colour of the links in the original theme. Used
 * when the phone doesn't provide its own colours (before Android 12). */
private val DarkColors = darkColorScheme(
    primary = Color(0xFF80CBC4),
    onPrimary = Color(0xFF003733),
    secondaryContainer = Color(0xFF1F4D49),
    onSecondaryContainer = Color(0xFFB2DFDB),
)

private val LightColors = lightColorScheme(
    primary = Color(0xFF00695C),
    onPrimary = Color.White,
    secondaryContainer = Color(0xFFB2DFDB),
    onSecondaryContainer = Color(0xFF00201D),
)

/** Material 3 theme that follows the phone's light/dark setting and,
 * from Android 12, its wallpaper colours (Material You). */
@Composable
fun PrevoTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val colors = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        dark -> DarkColors
        else -> LightColors
    }

    MaterialTheme(colorScheme = colors, content = content)
}
