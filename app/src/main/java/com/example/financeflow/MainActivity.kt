package com.example.financeflow

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.sp
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.graphics.graphicsLayer

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = MaterialTheme.colorScheme.background
            ) {
                AppTitle()
            }
        }
    }
}

@Composable
fun AppTitle() {
    // Анімація для появи назви
    val fadeIn = animateFloatAsState(
        targetValue = 1f,
        animationSpec = tween(durationMillis = 2000) // Анімація тривалістю 2 секунди
    )

    Text(
        text = "FinanceFlow",
        style = TextStyle(
            color = Color(0xFFF4F4F4),
            fontSize = 60.sp // Стандартний шрифт
        ),
        modifier = Modifier.graphicsLayer(alpha = fadeIn.value) // Застосовуємо анімацію до альфа-каналу
    )
}

@Preview(showBackground = true)
@Composable
fun GreetingPreview() {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        AppTitle()
    }
}
