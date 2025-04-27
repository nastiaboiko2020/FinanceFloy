package com.example.financeflow

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.graphicsLayer
import com.example.financeflow.ui.theme.FinanceFlowTheme

class SplashActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            FinanceFlowTheme {
                SplashScreen(onNavigateToRegister = { navigateToRegister() })
            }
        }
    }

    private fun navigateToRegister() {
        val intent = Intent(this, RegisterActivity::class.java)
        startActivity(intent)
        finish() // Закриваємо SplashActivity
    }
}

@Composable
fun SplashScreen(onNavigateToRegister: () -> Unit) {
    // Стан для анімації
    var startAnimation by remember { mutableStateOf(false) }

    // Запускаємо анімацію при завантаженні
    LaunchedEffect(Unit) {
        startAnimation = true
    }

    // Анімація для тексту "FinanceFlow"
    val titleFadeIn by animateFloatAsState(
        targetValue = if (startAnimation) 1f else 0f,
        animationSpec = tween(durationMillis = 1000)
    )
    val titleTranslateY by animateFloatAsState(
        targetValue = if (startAnimation) 0f else 100f,
        animationSpec = tween(durationMillis = 1000)
    )
    val titleRotation by animateFloatAsState(
        targetValue = if (startAnimation) 0f else -10f,
        animationSpec = tween(durationMillis = 1000)
    )

    // Анімація для підзаголовка
    val subtitleFadeIn by animateFloatAsState(
        targetValue = if (startAnimation) 1f else 0f,
        animationSpec = tween(durationMillis = 1000, delayMillis = 500) // Затримка 0.5 секунди
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF1A3D62), // Темно-синій
                        Color(0xFF2E5B8C)  // Світліший синій
                    )
                )
            )
            .clickable { onNavigateToRegister() } // Перехід при натисканні
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Основний текст "FinanceFlow"
            Text(
                text = "FinanceFlow",
                style = TextStyle(
                    color = Color.White,
                    fontSize = 60.sp,
                    fontWeight = FontWeight.Bold,
                    shadow = androidx.compose.ui.graphics.Shadow(
                        color = Color.Black.copy(alpha = 0.3f),
                        blurRadius = 8f
                    )
                ),
                modifier = Modifier
                    .graphicsLayer(
                        alpha = titleFadeIn,
                        translationY = titleTranslateY,
                        rotationZ = titleRotation
                    )
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Підзаголовок
            Text(
                text = "Ваш фінансовий помічник",
                style = TextStyle(
                    color = Color.White.copy(alpha = 0.8f),
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Medium
                ),
                modifier = Modifier
                    .graphicsLayer(alpha = subtitleFadeIn)
            )
        }
    }
}