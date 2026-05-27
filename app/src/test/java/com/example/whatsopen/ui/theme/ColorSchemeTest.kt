package com.example.whatsopen.ui.theme

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Test

class ColorSchemeTest {

    @Test
    fun lightPrimary() = assertEquals(Color(0xFF006B5E), LightColorScheme.primary)

    @Test
    fun lightOnPrimary() = assertEquals(Color(0xFFFFFFFF), LightColorScheme.onPrimary)

    @Test
    fun lightPrimaryContainer() = assertEquals(Color(0xFF74F8DC), LightColorScheme.primaryContainer)

    @Test
    fun lightOnPrimaryContainer() = assertEquals(Color(0xFF00201B), LightColorScheme.onPrimaryContainer)

    @Test
    fun lightSecondary() = assertEquals(Color(0xFF4B635C), LightColorScheme.secondary)

    @Test
    fun lightOnSecondary() = assertEquals(Color(0xFFFFFFFF), LightColorScheme.onSecondary)

    @Test
    fun lightSecondaryContainer() = assertEquals(Color(0xFFCDE8DF), LightColorScheme.secondaryContainer)

    @Test
    fun lightOnSecondaryContainer() = assertEquals(Color(0xFF07201A), LightColorScheme.onSecondaryContainer)

    @Test
    fun lightTertiary() = assertEquals(Color(0xFF416278), LightColorScheme.tertiary)

    @Test
    fun lightOnTertiary() = assertEquals(Color(0xFFFFFFFF), LightColorScheme.onTertiary)

    @Test
    fun lightTertiaryContainer() = assertEquals(Color(0xFFC5E7FF), LightColorScheme.tertiaryContainer)

    @Test
    fun lightOnTertiaryContainer() = assertEquals(Color(0xFF001E2F), LightColorScheme.onTertiaryContainer)

    @Test
    fun lightError() = assertEquals(Color(0xFFBA1A1A), LightColorScheme.error)

    @Test
    fun lightOnError() = assertEquals(Color(0xFFFFFFFF), LightColorScheme.onError)

    @Test
    fun lightErrorContainer() = assertEquals(Color(0xFFFFDAD6), LightColorScheme.errorContainer)

    @Test
    fun lightOnErrorContainer() = assertEquals(Color(0xFF410002), LightColorScheme.onErrorContainer)

    @Test
    fun lightBackground() = assertEquals(Color(0xFFFAFDFB), LightColorScheme.background)

    @Test
    fun lightOnBackground() = assertEquals(Color(0xFF191C1B), LightColorScheme.onBackground)

    @Test
    fun lightSurface() = assertEquals(Color(0xFFFAFDFB), LightColorScheme.surface)

    @Test
    fun lightOnSurface() = assertEquals(Color(0xFF191C1B), LightColorScheme.onSurface)

    @Test
    fun lightSurfaceVariant() = assertEquals(Color(0xFFDAE5E0), LightColorScheme.surfaceVariant)

    @Test
    fun lightOnSurfaceVariant() = assertEquals(Color(0xFF3F4945), LightColorScheme.onSurfaceVariant)

    @Test
    fun lightOutline() = assertEquals(Color(0xFF6F7975), LightColorScheme.outline)

    @Test
    fun lightOutlineVariant() = assertEquals(Color(0xFFBEC9C4), LightColorScheme.outlineVariant)

    @Test
    fun darkPrimary() = assertEquals(Color(0xFF54DBC0), DarkColorScheme.primary)

    @Test
    fun darkOnPrimary() = assertEquals(Color(0xFF003730), DarkColorScheme.onPrimary)

    @Test
    fun darkPrimaryContainer() = assertEquals(Color(0xFF005046), DarkColorScheme.primaryContainer)

    @Test
    fun darkOnPrimaryContainer() = assertEquals(Color(0xFF74F8DC), DarkColorScheme.onPrimaryContainer)

    @Test
    fun darkSecondary() = assertEquals(Color(0xFFB1CCC3), DarkColorScheme.secondary)

    @Test
    fun darkOnSecondary() = assertEquals(Color(0xFF1D352E), DarkColorScheme.onSecondary)

    @Test
    fun darkSecondaryContainer() = assertEquals(Color(0xFF334B44), DarkColorScheme.secondaryContainer)

    @Test
    fun darkOnSecondaryContainer() = assertEquals(Color(0xFFCDE8DF), DarkColorScheme.onSecondaryContainer)

    @Test
    fun darkTertiary() = assertEquals(Color(0xFFA9CBE3), DarkColorScheme.tertiary)

    @Test
    fun darkOnTertiary() = assertEquals(Color(0xFF0E3447), DarkColorScheme.onTertiary)

    @Test
    fun darkTertiaryContainer() = assertEquals(Color(0xFF294A5F), DarkColorScheme.tertiaryContainer)

    @Test
    fun darkOnTertiaryContainer() = assertEquals(Color(0xFFC5E7FF), DarkColorScheme.onTertiaryContainer)

    @Test
    fun darkError() = assertEquals(Color(0xFFFFB4AB), DarkColorScheme.error)

    @Test
    fun darkOnError() = assertEquals(Color(0xFF690005), DarkColorScheme.onError)

    @Test
    fun darkErrorContainer() = assertEquals(Color(0xFF93000A), DarkColorScheme.errorContainer)

    @Test
    fun darkOnErrorContainer() = assertEquals(Color(0xFFFFDAD6), DarkColorScheme.onErrorContainer)

    @Test
    fun darkBackground() = assertEquals(Color(0xFF191C1B), DarkColorScheme.background)

    @Test
    fun darkOnBackground() = assertEquals(Color(0xFFE1E3E0), DarkColorScheme.onBackground)

    @Test
    fun darkSurface() = assertEquals(Color(0xFF111413), DarkColorScheme.surface)

    @Test
    fun darkOnSurface() = assertEquals(Color(0xFFE1E3E0), DarkColorScheme.onSurface)

    @Test
    fun darkSurfaceVariant() = assertEquals(Color(0xFF3F4945), DarkColorScheme.surfaceVariant)

    @Test
    fun darkOnSurfaceVariant() = assertEquals(Color(0xFFBEC9C4), DarkColorScheme.onSurfaceVariant)

    @Test
    fun darkOutline() = assertEquals(Color(0xFF899390), DarkColorScheme.outline)

    @Test
    fun darkOutlineVariant() = assertEquals(Color(0xFF3F4945), DarkColorScheme.outlineVariant)
}
