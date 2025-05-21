package com.example.financeflow

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer // Додано імпорт для graphicsLayer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.financeflow.ui.theme.FinanceFlowTheme
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.ktx.auth
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class EmailVerificationActivity : ComponentActivity() {
    private lateinit var auth: FirebaseAuth

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        auth = Firebase.auth
        val email = intent.getStringExtra("email") ?: ""
        val error = intent.getStringExtra("error")
        val success = intent.getStringExtra("success")

        setContent {
            FinanceFlowTheme {
                EmailVerificationScreen(
                    email = email,
                    initialError = error,
                    initialSuccess = success,
                    onBackToLoginClicked = {
                        auth.signOut()
                        val intent = Intent(this, RegisterActivity::class.java)
                        startActivity(intent)
                        finish()
                    }
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Перевіряємо статус підтвердження при поверненні на екран
        auth.currentUser?.reload()
    }
}

@Composable
fun EmailVerificationScreen(
    email: String,
    initialError: String?,
    initialSuccess: String?,
    onBackToLoginClicked: () -> Unit
) {
    var errorMessage by remember { mutableStateOf(initialError) }
    var successMessage by remember { mutableStateOf(initialSuccess) }
    var isVerified by remember { mutableStateOf(false) }
    var showCheckmark by remember { mutableStateOf(false) }
    var canResend by remember { mutableStateOf(false) }
    var countdown by remember { mutableStateOf(60) }

    val coroutineScope = rememberCoroutineScope()
    val auth = Firebase.auth

    // Анімація для галочки
    val checkmarkAlpha by animateFloatAsState(
        targetValue = if (showCheckmark) 1f else 0f,
        animationSpec = tween(durationMillis = 500)
    )
    val checkmarkScale by animateFloatAsState(
        targetValue = if (showCheckmark) 1f else 0f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy)
    )

    // Перевірка статусу підтвердження
    LaunchedEffect(Unit) {
        while (!isVerified) {
            auth.currentUser?.reload()
            if (auth.currentUser?.isEmailVerified == true) {
                isVerified = true
                showCheckmark = true
                successMessage = "Email підтверджено!"
                delay(1500L) // Затримка перед переходом
                val intent = Intent(auth.app.applicationContext, HomeActivity::class.java)
                auth.app.applicationContext.startActivity(intent)
                // Оскільки ми всередині Composable, нам потрібно знайти спосіб завершити активність
                // Використаємо onBackToLoginClicked як приклад
                onBackToLoginClicked()
            }
            delay(3000L) // Перевіряємо кожні 3 секунди
        }
    }

    // Таймер для повторного надсилання
    LaunchedEffect(Unit) {
        while (countdown > 0) {
            delay(1000L)
            countdown--
        }
        canResend = true
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF5F7FA))
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(200.dp)
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            Color(0xFF1A3D62),
                            Color(0xFF2E5B8C)
                        )
                    )
                )
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = 48.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "FinanceFlow",
                style = TextStyle(
                    color = Color.White,
                    fontSize = 36.sp,
                    fontWeight = FontWeight.Bold,
                    shadow = androidx.compose.ui.graphics.Shadow(
                        color = Color.Black.copy(alpha = 0.3f),
                        blurRadius = 8f
                    )
                )
            )

            Spacer(modifier = Modifier.height(32.dp))

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .shadow(8.dp, RoundedCornerShape(16.dp)),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(
                    modifier = Modifier
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Підтвердіть ваш email",
                        style = TextStyle(
                            color = Color(0xFF1A3D62),
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold
                        ),
                        modifier = Modifier.padding(bottom = 16.dp)
                    )

                    Text(
                        text = "Перейдіть за посиланням, надісланим на $email",
                        color = Color.Gray,
                        fontSize = 14.sp,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )

                    errorMessage?.let {
                        Text(
                            text = it,
                            color = Color.Red,
                            fontSize = 14.sp,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                    }

                    successMessage?.let {
                        Text(
                            text = it,
                            color = Color.Green,
                            fontSize = 14.sp,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                    }

                    // Анімація завантаження або галочка
                    Box(
                        modifier = Modifier
                            .size(60.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF4A7BA6)),
                        contentAlignment = Alignment.Center
                    ) {
                        if (!showCheckmark) {
                            CircularProgressIndicator(
                                color = Color.White,
                                modifier = Modifier.size(40.dp)
                            )
                        } else {
                            Text(
                                text = "✔",
                                color = Color.White,
                                fontSize = 30.sp,
                                modifier = Modifier
                                    .alpha(checkmarkAlpha)
                                    .graphicsLayer(scaleX = checkmarkScale, scaleY = checkmarkScale)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = if (canResend) "Надіслати лист ще раз" else "Надіслати ще раз через $countdown сек",
                            color = if (canResend) Color(0xFF4A7BA6) else Color.Gray,
                            fontSize = 14.sp,
                            modifier = Modifier
                                .clickable(enabled = canResend) {
                                    coroutineScope.launch {
                                        auth.currentUser?.sendEmailVerification()
                                            ?.addOnCompleteListener { task ->
                                                if (task.isSuccessful) {
                                                    successMessage = "Лист для підтвердження надіслано на $email."
                                                    errorMessage = null
                                                    countdown = 60
                                                    canResend = false
                                                } else {
                                                    errorMessage = "Помилка: ${task.exception?.message}"
                                                    successMessage = null
                                                }
                                            }
                                    }
                                }
                        )

                        Text(
                            text = "Повернутися до входу",
                            color = Color(0xFF4A7BA6),
                            fontSize = 14.sp,
                            modifier = Modifier.clickable { onBackToLoginClicked() }
                        )
                    }
                }
            }
        }
    }
}