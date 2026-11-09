package com.cindrix.pocket

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

// Same palette as Cindrix AirLink: charcoal surfaces, bone text, blue accent.
val Charcoal = Color(0xFF1F2226)
val Panel = Color(0xFF2A2E33)
val PanelHigh = Color(0xFF363B41)
val Bone = Color(0xFFECE6D8)
val Muted = Color(0xFF9AA3AB)
val Blue = Color(0xFF67B4FF)
val BlueDeep = Color(0xFF3B9BFF)
val Danger = Color(0xFFE5534B)

// Same condensed, heavy heading font as AirLink (TeX Gyre Heros Condensed Bold).
val HeadingFont = FontFamily(Font(R.font.texgyreheroscn_bold, FontWeight.Bold))

// Buttons stay rounded.
val Round = RoundedCornerShape(14.dp)

@Composable
fun CindrixTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Blue,
            onPrimary = Color(0xFF07101F),
            secondary = BlueDeep,
            background = Charcoal,
            onBackground = Bone,
            surface = Panel,
            onSurface = Bone,
            surfaceVariant = PanelHigh,
            onSurfaceVariant = Muted,
            outline = Color(0xFF4A5159),
            error = Danger,
        ),
        content = {
            // Without this, text outside a card falls back to black on the dark background.
            Surface(color = Charcoal, contentColor = Bone) { content() }
        },
    )
}
