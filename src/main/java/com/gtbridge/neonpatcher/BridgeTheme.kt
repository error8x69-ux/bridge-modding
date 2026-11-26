package com.gtbridge.neonpatcher

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val palette = darkColorScheme(
    primary = Color(0xFF6AF6ED),
    onPrimary = Color(0xFF002020),
    primaryContainer = Color(0xFF153537),
    onPrimaryContainer = Color(0xFFB0FFF5),
    secondary = Color(0xFFEF92AD),
    onSecondary = Color(0xFF381222),
    secondaryContainer = Color(0xFF153537),
    onSecondaryContainer = Color(0xFFB0FFF5),
    tertiary = Color(0xFFEF92AD),
    tertiaryContainer = Color(0xFF3F2230),
    onTertiaryContainer = Color(0xFFFFD8E3),
    background = Color(0xFF090B0E),
    onBackground = Color(0xFFEFF2F5),
    surface = Color(0xFF101419),
    onSurface = Color(0xFFEFF2F5),
    surfaceContainer = Color(0xFF161B21),
    surfaceContainerHigh = Color(0xFF20272E),
    surfaceVariant = Color(0xFF1C232A),
    onSurfaceVariant = Color(0xFFA5B1BB),
    outline = Color(0xFF687780),
    outlineVariant = Color(0xFF303B43),
    error = Color(0xFFFFB3BA),
    errorContainer = Color(0xFF431B24),
    onErrorContainer = Color(0xFFFFDBDC)
)

private val type = Typography(
    headlineLarge = TextStyle(fontSize = 36.sp, lineHeight = 42.sp,
        fontWeight = FontWeight.Bold, letterSpacing = (-1.25).sp),
    titleLarge = TextStyle(fontSize = 22.sp, lineHeight = 28.sp,
        fontWeight = FontWeight.SemiBold, letterSpacing = (-0.35).sp),
    titleMedium = TextStyle(fontSize = 16.sp, lineHeight = 22.sp, fontWeight = FontWeight.SemiBold),
    bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 23.sp),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 20.sp),
    bodySmall = TextStyle(fontSize = 12.sp, lineHeight = 17.sp),
    labelMedium = TextStyle(fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.Medium)
)

private val shapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
internal fun BridgeTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = palette, typography = type, shapes = shapes, content = content)
}

@Composable
internal fun Panel(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp), content = content)
    }
}

@Composable
internal fun ScreenHeading(title: String, details: String) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(title, style = MaterialTheme.typography.headlineLarge)
        Text(details, style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
