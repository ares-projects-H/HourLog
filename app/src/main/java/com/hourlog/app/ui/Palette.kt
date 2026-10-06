package com.hourlog.app.ui

import androidx.compose.material3.*
import androidx.compose.ui.graphics.Color
import androidx.core.graphics.ColorUtils

fun hourLogColors(seedHex: String, dark: Boolean): ColorScheme {
    val seed = android.graphics.Color.parseColor("#$seedHex")
    fun blend(base: Int, proportion: Float) = Color(ColorUtils.blendARGB(seed, base, proportion))
    var primary = seed
    if (!dark) while (ColorUtils.calculateContrast(android.graphics.Color.WHITE, primary) < 4.5)
        primary = ColorUtils.blendARGB(primary, android.graphics.Color.BLACK, 0.1f)
    if (dark) return darkColorScheme(
        primary = blend(-1, .65f), onPrimary = Color(0xff10231b), primaryContainer = blend(0xff10201a.toInt(), .70f),
        onPrimaryContainer = Color(0xffe5f5ed), secondary = blend(-1, .60f), onSecondary = Color(0xff10231b),
        secondaryContainer = blend(0xff28362f.toInt(), .85f), onSecondaryContainer = Color(0xffe5f5ed),
        surface = blend(0xff101713.toInt(), .96f), background = blend(0xff101713.toInt(), .96f),
        surfaceContainer = blend(0xff19231c.toInt(), .94f), surfaceContainerHighest = blend(0xff28332c.toInt(), .90f),
        onSurface = Color(0xffedf2ef), onSurfaceVariant = Color(0xffcbd8cf))
    return lightColorScheme(primary = Color(primary), onPrimary = Color.White, primaryContainer = blend(-1,.84f),
        onPrimaryContainer = Color(0xff13261b), secondary = Color(primary), onSecondary = Color.White,
        secondaryContainer = blend(-1,.90f), onSecondaryContainer = Color(0xff13261b),
        surface = blend(-1,.985f), background = blend(-1,.985f),
        surfaceContainer = blend(-1,.95f), surfaceContainerHighest = blend(-1,.90f),
        onSurface = Color(0xff19241e), onSurfaceVariant = Color(0xff3d4c43))
}
