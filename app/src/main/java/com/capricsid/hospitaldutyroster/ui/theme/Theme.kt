package com.capricsid.hospitaldutyroster.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = Color(0xFF0F5F50),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFC8F2E5),
    secondary = Color(0xFF885B1A),
    secondaryContainer = Color(0xFFFFE4BF),
    tertiary = Color(0xFF5E4A90),
    background = Color(0xFFF7F4ED),
    surface = Color(0xFFFFFBF5),
    surfaceVariant = Color(0xFFE6E1D7),
    outlineVariant = Color(0xFFD0C9BC)
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF73D6C1),
    onPrimary = Color(0xFF00382F),
    primaryContainer = Color(0xFF005143),
    secondary = Color(0xFFF0C078),
    secondaryContainer = Color(0xFF664314),
    tertiary = Color(0xFFD0BCFF),
    background = Color(0xFF15130F),
    surface = Color(0xFF1D1B17),
    surfaceVariant = Color(0xFF444038),
    outlineVariant = Color(0xFF5E594E)
)

@Composable
fun HospitalDutyRosterTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = LightColors,
        content = content
    )
}

