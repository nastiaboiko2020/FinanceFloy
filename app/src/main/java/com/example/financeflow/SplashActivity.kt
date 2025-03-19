package com.example.financeflow

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontWeight

// Переконайтеся, що цей імпорт правильний
import com.example.financeflow.RegisterActivity

class SplashActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            SplashScreen {
                // Переходимо до RegisterActivity при натисканні
                val intent = Intent(this, RegisterActivity::class.java)
                startActivity(intent)
                finish()  // Закриваємо SplashActivity, щоб не залишити її в історії
            }
        }
    }
}

@Composable
fun SplashScreen(onClick: () -> Unit) {
    var visible by remember { mutableStateOf(false) }

    // Зміна видимості для анімації
    LaunchedEffect(Unit) {
        visible = true
    }

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(animationSpec = androidx.compose.animation.core.tween(durationMillis = 1000))
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF1A3D62))  // Фоновий колір
                .clickable { onClick() },  // Обробка натискання
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "FinanceFlow",  // Текст на екрані
                style = TextStyle(
                    color = Color(0xFFF4F4F4),
                    fontSize = 48.sp,
                    fontWeight = FontWeight.SemiBold
                )
            )
        }
    }
}
