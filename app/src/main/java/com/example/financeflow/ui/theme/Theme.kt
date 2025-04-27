package com.example.financeflow.ui.theme

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

// Кольори для інших сторінок додатка (залишаємо як є)
private val DarkColorScheme = darkColorScheme(
    primary = Purple80,
    secondary = PurpleGrey80,
    tertiary = Pink80,
    background = Color(0xFF121212), // Темний фон для темної теми
    surface = Color(0xFF1E1E1E), // Темна поверхня
    onPrimary = Color.White,
    onSecondary = Color.White,
    onBackground = Color.White,
    onSurface = Color.White
)

private val LightColorScheme = lightColorScheme(
    primary = Purple40,
    secondary = PurpleGrey40,
    tertiary = Pink40,
    background = Color.White, // Білий фон для світлої теми
    surface = Color.White,
    onPrimary = Color.White,
    onSecondary = Color.White,
    onBackground = Color.Black,
    onSurface = Color.Black
)

// Кольори для AIChatActivity
val AIChatPrimary = Color(0xFF1A3D62) // Темно-синій для основних елементів
val AIChatSecondary = Color(0xFF4A7BA6) // Світліший синій для другорядних елементів
val AIChatBackground = Color(0xFFF5F7FA) // Світло-сірий фон
val AIChatSurface = Color.White // Білий для поверхонь (наприклад, карток)
val AIChatOnPrimary = Color.White // Білий текст на основному кольорі
val AIChatOnSecondary = Color.White // Білий текст на другорядному кольорі
val AIChatOnBackground = Color.Black // Чорний текст на фоні
val AIChatOnSurface = Color.Black // Чорний текст на поверхнях

// Темна схема для AIChatActivity
private val AIChatDarkColorScheme = darkColorScheme(
    primary = AIChatPrimary,
    secondary = AIChatSecondary,
    background = Color(0xFF121212), // Темний фон для темної теми
    surface = Color(0xFF1E1E1E), // Темна поверхня для карток у темній темі
    onPrimary = AIChatOnPrimary,
    onSecondary = AIChatOnSecondary,
    onBackground = Color.White, // Білий текст на темному фоні
    onSurface = Color.White // Білий текст на темних поверхнях
)

// Світла схема для AIChatActivity
private val AIChatLightColorScheme = lightColorScheme(
    primary = AIChatPrimary,
    secondary = AIChatSecondary,
    background = AIChatBackground,
    surface = AIChatSurface,
    onPrimary = AIChatOnPrimary,
    onSecondary = AIChatOnSecondary,
    onBackground = AIChatOnBackground,
    onSurface = AIChatOnSurface
)

// Загальна тема для додатку
@Composable
fun Theme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    useAIChatTheme: Boolean = false, // Додаємо параметр для вибору теми AIChat
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        useAIChatTheme -> if (darkTheme) AIChatDarkColorScheme else AIChatLightColorScheme
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}

// Тема для фінансового додатку (залишаємо для сумісності)
@Composable
fun FinanceFlowTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    useAIChatTheme: Boolean = false, // Додаємо параметр для вибору теми AIChat
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        useAIChatTheme -> if (darkTheme) AIChatDarkColorScheme else AIChatLightColorScheme
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}