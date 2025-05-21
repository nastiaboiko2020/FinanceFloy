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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.graphicsLayer
import com.example.financeflow.ui.theme.FinanceFlowTheme
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.ktx.auth
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.launch
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff

class RegisterActivity : ComponentActivity() {
    private lateinit var auth: FirebaseAuth

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        auth = Firebase.auth
        if (auth.currentUser != null && auth.currentUser?.isEmailVerified == true) {
            navigateToHome()
            return
        }

        val initialError = intent.getStringExtra("error")
        val initialSuccess = intent.getStringExtra("success")

        setContent {
            FinanceFlowTheme(
                darkTheme = false, // Завжди світла тема
                useAIChatTheme = false,
                useFinanceFlowTheme = true
            ) {
                RegisterScreen(
                    initialError = initialError,
                    initialSuccess = initialSuccess,
                    onAuthClicked = { email, password, isLoginMode ->
                        if (isLoginMode) {
                            signInUser(email, password)
                        } else {
                            registerUser(email, password)
                        }
                    },
                    onForgotPasswordClicked = { email ->
                        resetPassword(email)
                    }
                )
            }
        }
    }

    private fun registerUser(email: String, password: String) {
        auth.createUserWithEmailAndPassword(email, password)
            .addOnCompleteListener(this) { task ->
                if (task.isSuccessful) {
                    auth.currentUser?.sendEmailVerification()
                        ?.addOnCompleteListener { verificationTask ->
                            val intent = Intent(this, EmailVerificationActivity::class.java)
                            intent.putExtra("email", email)
                            if (verificationTask.isSuccessful) {
                                intent.putExtra("success", "Лист для підтвердження надіслано на $email.")
                            } else {
                                intent.putExtra("error", "Помилка надсилання email: ${verificationTask.exception?.message}")
                            }
                            startActivity(intent)
                        }
                } else {
                    val intent = Intent(this, RegisterActivity::class.java)
                    intent.putExtra("error", when (task.exception?.message) {
                        "CONFIGURATION_NOT_FOUND" -> "Помилка конфігурації Firebase. Перевірте налаштування reCAPTCHA."
                        else -> "Помилка реєстрації: ${task.exception?.message}"
                    })
                    startActivity(intent)
                    finish()
                }
            }
    }

    private fun signInUser(email: String, password: String) {
        auth.signInWithEmailAndPassword(email, password)
            .addOnCompleteListener(this) { task ->
                if (task.isSuccessful) {
                    if (auth.currentUser?.isEmailVerified == true) {
                        navigateToHome()
                    } else {
                        val intent = Intent(this, EmailVerificationActivity::class.java)
                        intent.putExtra("email", email)
                        intent.putExtra("error", "Будь ласка, підтвердіть ваш email перед входом.")
                        startActivity(intent)
                    }
                } else {
                    val intent = Intent(this, RegisterActivity::class.java)
                    intent.putExtra("error", when (task.exception?.message) {
                        "CONFIGURATION_NOT_FOUND" -> "Помилка конфігурації Firebase. Перевірте налаштування reCAPTCHA."
                        else -> "Помилка входу: ${task.exception?.message}"
                    })
                    startActivity(intent)
                    finish()
                }
            }
    }

    private fun resetPassword(email: String) {
        if (email.isBlank()) {
            val intent = Intent(this, RegisterActivity::class.java)
            intent.putExtra("error", "Введіть email для відновлення пароля.")
            startActivity(intent)
            finish()
            return
        }
        auth.sendPasswordResetEmail(email)
            .addOnCompleteListener { task ->
                val intent = Intent(this, RegisterActivity::class.java)
                if (task.isSuccessful) {
                    intent.putExtra("success", "Лист для відновлення пароля надіслано на $email.")
                } else {
                    intent.putExtra("error", "Помилка: ${task.exception?.message}")
                }
                startActivity(intent)
                finish()
            }
    }

    private fun navigateToHome() {
        val intent = Intent(this, HomeActivity::class.java)
        startActivity(intent)
        finish()
    }
}

@Composable
fun RegisterScreen(
    initialError: String?,
    initialSuccess: String?,
    onAuthClicked: (String, String, Boolean) -> Unit,
    onForgotPasswordClicked: (String) -> Unit
) {
    var startAnimation by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        startAnimation = true
    }
    val fadeIn by animateFloatAsState(
        targetValue = if (startAnimation) 1f else 0f,
        animationSpec = tween(durationMillis = 1000)
    )
    val translateY by animateFloatAsState(
        targetValue = if (startAnimation) 0f else 50f,
        animationSpec = tween(durationMillis = 1000)
    )

    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf(initialError) }
    var successMessage by remember { mutableStateOf(initialSuccess) }
    var isLoginMode by remember { mutableStateOf(false) }
    var passwordVisible by remember { mutableStateOf(false) }

    val coroutineScope = rememberCoroutineScope()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF5F7FA)) // Світла тема
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
                ),
                modifier = Modifier
                    .graphicsLayer(alpha = fadeIn, translationY = translateY)
            )

            Spacer(modifier = Modifier.height(32.dp))

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .shadow(8.dp, RoundedCornerShape(16.dp)),
                colors = CardDefaults.cardColors(containerColor = Color.White), // Світла тема
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(
                    modifier = Modifier
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = if (isLoginMode) "Увійдіть у свій акаунт" else "Створіть свій акаунт",
                        style = TextStyle(
                            color = Color(0xFF1A3D62), // Світла тема
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold
                        ),
                        modifier = Modifier
                            .padding(bottom = 16.dp)
                            .graphicsLayer(alpha = fadeIn)
                    )

                    errorMessage?.let { errorText ->
                        Text(
                            text = errorText,
                            color = Color.Red,
                            fontSize = 14.sp,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                    }

                    successMessage?.let { successText ->
                        Text(
                            text = successText,
                            color = Color.Green,
                            fontSize = 14.sp,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                    }

                    OutlinedTextField(
                        value = email,
                        onValueChange = { email = it },
                        label = { Text("Email", color = Color(0xFF1A3D62)) }, // Світла тема
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color.White, RoundedCornerShape(12.dp))
                            .shadow(4.dp, RoundedCornerShape(12.dp)),
                        colors = TextFieldDefaults.colors(
                            focusedTextColor = Color.Black, // Світла тема
                            unfocusedTextColor = Color.Black, // Світла тема
                            focusedContainerColor = Color.White, // Світла тема
                            unfocusedContainerColor = Color.White, // Світла тема
                            focusedIndicatorColor = Color(0xFF4A7BA6), // Світла тема
                            unfocusedIndicatorColor = Color.Gray, // Світла тема
                            cursorColor = Color(0xFF1A3D62) // Світла тема
                        ),
                        shape = RoundedCornerShape(12.dp),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                        isError = errorMessage != null
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        label = { Text("Пароль", color = Color(0xFF1A3D62)) }, // Світла тема
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color.White, RoundedCornerShape(12.dp))
                            .shadow(4.dp, RoundedCornerShape(12.dp)),
                        colors = TextFieldDefaults.colors(
                            focusedTextColor = Color.Black, // Світла тема
                            unfocusedTextColor = Color.Black, // Світла тема
                            focusedContainerColor = Color.White, // Світла тема
                            unfocusedContainerColor = Color.White, // Світла тема
                            focusedIndicatorColor = Color(0xFF4A7BA6), // Світла тема
                            unfocusedIndicatorColor = Color.Gray, // Світла тема
                            cursorColor = Color(0xFF1A3D62) // Світла тема
                        ),
                        shape = RoundedCornerShape(12.dp),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            val icon = if (passwordVisible) Icons.Filled.Visibility else Icons.Filled.VisibilityOff
                            IconButton(onClick = { passwordVisible = !passwordVisible }) {
                                Icon(icon, contentDescription = "Toggle password visibility")
                            }
                        },
                        isError = errorMessage != null
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    if (isLoginMode) {
                        Text(
                            text = "Забули пароль?",
                            color = Color(0xFF4A7BA6), // Світла тема
                            fontSize = 14.sp,
                            modifier = Modifier
                                .align(Alignment.End)
                                .clickable { onForgotPasswordClicked(email) }
                                .padding(bottom = 16.dp)
                        )
                    }

                    Text(
                        text = if (isLoginMode) "Немає акаунту? Зареєструйтесь" else "Вже маєте акаунт? Увійдіть",
                        color = Color(0xFF4A7BA6), // Світла тема
                        fontSize = 14.sp,
                        modifier = Modifier
                            .clickable { isLoginMode = !isLoginMode }
                            .padding(bottom = 16.dp)
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Button(
                        onClick = {
                            when {
                                email.isBlank() -> errorMessage = "Введіть email"
                                password.isBlank() -> errorMessage = "Введіть пароль"
                                password.length < 6 -> errorMessage = "Пароль має бути щонайменше 6 символів"
                                else -> {
                                    errorMessage = null
                                    successMessage = null
                                    coroutineScope.launch {
                                        onAuthClicked(email, password, isLoginMode)
                                    }
                                }
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp)
                            .shadow(4.dp, RoundedCornerShape(12.dp))
                            .graphicsLayer(alpha = fadeIn, translationY = translateY),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4A7BA6)), // Світла тема
                        shape = RoundedCornerShape(12.dp),
                        elevation = ButtonDefaults.buttonElevation(defaultElevation = 4.dp)
                    ) {
                        Text(
                            text = if (isLoginMode) "Увійти" else "Зареєструватися",
                            color = Color.White, // Світла тема
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}