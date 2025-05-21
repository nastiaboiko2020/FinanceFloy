package com.example.financeflow

import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ExitToApp
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.financeflow.ui.theme.FinanceFlowTheme
import com.google.firebase.auth.FirebaseAuth

class SettingsActivity : ComponentActivity() {
    private lateinit var sharedPreferences: SharedPreferences
    private lateinit var auth: FirebaseAuth

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        sharedPreferences = getSharedPreferences("FinanceFlowPrefs", MODE_PRIVATE)
        auth = FirebaseAuth.getInstance()

        setContent {
            FinanceFlowTheme(
                darkTheme = false, // Завжди світла тема
                useFinanceFlowTheme = true
            ) {
                SettingsScreen(
                    sharedPreferences = sharedPreferences,
                    onBackClicked = { finish() },
                    onLogoutClicked = {
                        auth.signOut()
                        val intent = Intent(this, RegisterActivity::class.java)
                        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                        startActivity(intent)
                        finish()
                    },
                    onDeleteAccountClicked = {
                        // Видаляємо всі дані користувача з SharedPreferences
                        sharedPreferences.edit().clear().apply()
                        // Видаляємо акаунт з Firebase
                        auth.currentUser?.delete()?.addOnCompleteListener { task ->
                            if (task.isSuccessful) {
                                val intent = Intent(this, RegisterActivity::class.java)
                                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                                startActivity(intent)
                                finish()
                            } else {
                                android.widget.Toast.makeText(
                                    this,
                                    "Помилка видалення акаунту: ${task.exception?.message}",
                                    android.widget.Toast.LENGTH_SHORT
                                ).show()
                            }
                        }
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    sharedPreferences: SharedPreferences,
    onBackClicked: () -> Unit,
    onLogoutClicked: () -> Unit,
    onDeleteAccountClicked: () -> Unit
) {
    val context = LocalContext.current
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showLogoutDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Налаштування",
                        style = TextStyle(
                            color = ComposeColor.White, // Білий текст у TopAppBar
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold
                        )
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBackClicked) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "Назад",
                            tint = ComposeColor.White // Біла іконка
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = ComposeColor(0xFF1A3D62) // Синій фон TopAppBar
                )
            )
        },
        content = { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .background(ComposeColor(0xFFF5F7FA)) // Фон для світлої теми
                    .padding(16.dp)
            ) {
                // Політика конфіденційності
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                        .clickable { /* Відкрити сторінку політики конфіденційності */ },
                    colors = CardDefaults.cardColors(
                        containerColor = ComposeColor.White // Світла тема
                    ),
                    shape = RoundedCornerShape(12.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = "Політика конфіденційності",
                            tint = ComposeColor.Black, // Світла тема
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                        Text(
                            text = "Політика конфіденційності",
                            style = TextStyle(
                                color = ComposeColor.Black, // Світла тема
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Medium
                            )
                        )
                    }
                }

                // Про застосунок
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                        .clickable { /* Відкрити сторінку про застосунок */ },
                    colors = CardDefaults.cardColors(
                        containerColor = ComposeColor.White // Світла тема
                    ),
                    shape = RoundedCornerShape(12.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = "Про застосунок",
                            tint = ComposeColor.Black, // Світла тема
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                        Text(
                            text = "Про застосунок",
                            style = TextStyle(
                                color = ComposeColor.Black, // Світла тема
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Medium
                            )
                        )
                    }
                }

                Spacer(modifier = Modifier.weight(1f))

                // Видалити акаунт
                Button(
                    onClick = { showDeleteDialog = true },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .padding(vertical = 8.dp)
                        .border(
                            width = 2.dp,
                            color = ComposeColor(0xFFB00020), // Червоний обідок
                            shape = RoundedCornerShape(12.dp)
                        ),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = ComposeColor.Transparent,
                        contentColor = ComposeColor.Black // Світла тема
                    ),
                    shape = RoundedCornerShape(12.dp),
                    elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp) // Без тіні
                ) {
                    Text(
                        text = "Видалити акаунт",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                // Вийти з профілю
                Button(
                    onClick = { showLogoutDialog = true },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .padding(vertical = 8.dp)
                        .border(
                            width = 2.dp,
                            color = ComposeColor(0xFF1A3D62), // Синій обідок
                            shape = RoundedCornerShape(12.dp)
                        ),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = ComposeColor.Transparent,
                        contentColor = ComposeColor.Black // Світла тема
                    ),
                    shape = RoundedCornerShape(12.dp),
                    elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp) // Без тіні
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.ExitToApp,
                            contentDescription = "Вийти",
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Вийти з профілю",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    )

    // Діалог підтвердження видалення акаунту
    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = {
                Text(
                    text = "Видалити акаунт?",
                    style = TextStyle(
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = ComposeColor.Black // Світла тема
                    )
                )
            },
            text = {
                Text(
                    text = "Усі ваші дані буде видалено без можливості відновлення. Ви впевнені?",
                    style = TextStyle(
                        color = ComposeColor.Black, // Світла тема
                        fontSize = 16.sp
                    )
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    onDeleteAccountClicked()
                    showDeleteDialog = false
                }) {
                    Text(
                        "Видалити",
                        color = ComposeColor(0xFFB00020) // Червоний для кнопки
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text(
                        "Скасувати",
                        color = ComposeColor.Black.copy(alpha = 0.6f) // Світла тема
                    )
                }
            },
            shape = RoundedCornerShape(16.dp),
            containerColor = ComposeColor.White // Світла тема
        )
    }

    // Діалог підтвердження виходу з профілю
    if (showLogoutDialog) {
        AlertDialog(
            onDismissRequest = { showLogoutDialog = false },
            title = {
                Text(
                    text = "Вийти з профілю?",
                    style = TextStyle(
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = ComposeColor.Black // Світла тема
                    )
                )
            },
            text = {
                Text(
                    text = "Ви впевнені, що хочете вийти з профілю?",
                    style = TextStyle(
                        color = ComposeColor.Black, // Світла тема
                        fontSize = 16.sp
                    )
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    onLogoutClicked()
                    showLogoutDialog = false
                }) {
                    Text(
                        "Вийти",
                        color = ComposeColor(0xFF1A3D62) // Синій для кнопки
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showLogoutDialog = false }) {
                    Text(
                        "Скасувати",
                        color = ComposeColor.Black.copy(alpha = 0.6f) // Світла тема
                    )
                }
            },
            shape = RoundedCornerShape(16.dp),
            containerColor = ComposeColor.White // Світла тема
        )
    }
}