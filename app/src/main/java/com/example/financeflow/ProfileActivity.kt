package com.example.financeflow

import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Bundle
import android.util.Base64
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.ktx.auth
import com.google.firebase.ktx.Firebase
import java.io.ByteArrayOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import android.Manifest
import android.net.Uri
import android.provider.MediaStore
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.foundation.Image
import com.example.financeflow.ui.theme.FinanceFlowTheme

class ProfileActivity : ComponentActivity() {
    private lateinit var sharedPreferences: SharedPreferences
    private val auth: FirebaseAuth by lazy { FirebaseAuth.getInstance() }

    private val cameraLauncher = registerForActivityResult(ActivityResultContracts.TakePicturePreview()) { bitmap ->
        bitmap?.let { saveUserPhoto(it) }
    }

    private val galleryLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let { saveUserPhotoFromUri(it) }
    }

    private val requestPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
        if (isGranted) {
            cameraLauncher.launch(null)
        } else {
            Toast.makeText(this, "Дозвіл на використання камери відхилено", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        sharedPreferences = getSharedPreferences("FinanceFlowPrefs", MODE_PRIVATE)

        setContent {
            FinanceFlowTheme(
                darkTheme = false, // Завжди світла тема
                useAIChatTheme = false,
                useFinanceFlowTheme = true
            ) {
                ProfileScreen(
                    sharedPreferences = sharedPreferences,
                    onBackClicked = { finish() },
                    onSettingsClicked = {
                        startActivity(Intent(this, SettingsActivity::class.java))
                    },
                    onCameraClicked = {
                        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                            cameraLauncher.launch(null)
                        } else {
                            requestPermissionLauncher.launch(Manifest.permission.CAMERA)
                        }
                    },
                    onGalleryClicked = {
                        galleryLauncher.launch("image/*")
                    }
                )
            }
        }
    }

    private fun saveUserPhoto(bitmap: Bitmap) {
        val byteArrayOutputStream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, byteArrayOutputStream)
        val byteArray = byteArrayOutputStream.toByteArray()
        val encoded = Base64.encodeToString(byteArray, Base64.DEFAULT)
        sharedPreferences.edit().putString("user_photo", encoded).apply()
    }

    private fun saveUserPhotoFromUri(uri: Uri) {
        try {
            val bitmap = MediaStore.Images.Media.getBitmap(contentResolver, uri)
            saveUserPhoto(bitmap)
        } catch (e: Exception) {
            Toast.makeText(this, "Помилка завантаження фото: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    sharedPreferences: SharedPreferences,
    onBackClicked: () -> Unit,
    onSettingsClicked: () -> Unit,
    onCameraClicked: () -> Unit,
    onGalleryClicked: () -> Unit
) {
    val context = LocalContext.current
    val userPhotoBase64 by remember { mutableStateOf(sharedPreferences.getString("user_photo", null)) }
    var userPhotoBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var showImageSourceDialog by remember { mutableStateOf(false) }
    var financialGoal by remember { mutableStateOf(sharedPreferences.getString("financial_goal", "Накопичити 10 000 грн") ?: "Накопичити 10 000 грн") }
    var showGoalDialog by remember { mutableStateOf(false) }
    var newGoal by remember { mutableStateOf(financialGoal) }

    // Список фінансових цитат
    val financialQuotes = listOf(
        "Гроші – це лише інструмент. Вони приведуть вас туди, куди ви забажаєте, але не замінять вас як водія. – Айн Ренд",
        "Багатство – це здатність повною мірою відчувати життя. – Генрі Девід Торо",
        "Не заощаджуйте те, що залишилося після витрат, а витрачайте те, що залишилося після заощаджень. – Воррен Баффет",
        "Інвестуйте в себе. Ваші знання – це найбільший актив. – Бенджамін Франклін",
        "Гроші не куплять щастя, але вони можуть зробити бідність комфортнішою. – Марк Твен"
    )

    // Випадкова цитата
    val randomQuote by remember { mutableStateOf(financialQuotes.random()) }

    // Завантаження фото з SharedPreferences
    LaunchedEffect(userPhotoBase64) {
        userPhotoBitmap = userPhotoBase64?.let {
            try {
                val decodedBytes = Base64.decode(it, Base64.DEFAULT)
                BitmapFactory.decodeByteArray(decodedBytes, 0, decodedBytes.size)
            } catch (e: Exception) {
                null
            }
        }
    }

    // Отримання даних користувача з Firebase
    val auth = FirebaseAuth.getInstance()
    val user = auth.currentUser
    val email = user?.email ?: "Невідомий користувач"
    val registrationDate = user?.metadata?.creationTimestamp?.let {
        SimpleDateFormat("dd.MM.yyyy", Locale.getDefault()).format(Date(it))
    } ?: "Невідомо"

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Профіль",
                        style = TextStyle(
                            color = ComposeColor.White,
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
                            tint = ComposeColor.White
                        )
                    }
                },
                actions = {
                    IconButton(onClick = onSettingsClicked) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Налаштування",
                            tint = ComposeColor.White
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = ComposeColor(0xFF1A3D62)
                )
            )
        },
        content = { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .background(MaterialTheme.colorScheme.background)
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(modifier = Modifier.height(24.dp))

                // Фото користувача або заглушка
                Box(
                    modifier = Modifier
                        .size(120.dp)
                        .clip(CircleShape)
                        .background(ComposeColor(0xFF1A3D62))
                        .clickable { showImageSourceDialog = true },
                    contentAlignment = Alignment.Center
                ) {
                    if (userPhotoBitmap != null) {
                        Image(
                            bitmap = userPhotoBitmap!!.asImageBitmap(),
                            contentDescription = "Фото користувача",
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(CircleShape)
                        )
                    } else {
                        Text(
                            text = email.firstOrNull()?.uppercase() ?: "U",
                            style = TextStyle(
                                color = ComposeColor.White,
                                fontSize = 48.sp,
                                fontWeight = FontWeight.Bold
                            )
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Email користувача
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = ComposeColor.White // Завжди світла тема
                    ),
                    shape = RoundedCornerShape(12.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                ) {
                    Text(
                        text = "Email: $email",
                        style = TextStyle(
                            color = ComposeColor(0xFF1A3D62), // Тільки світла тема
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Medium
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        textAlign = TextAlign.Center
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Дата реєстрації
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = ComposeColor.White // Завжди світла тема
                    ),
                    shape = RoundedCornerShape(12.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                ) {
                    Text(
                        text = "Дата реєстрації: $registrationDate",
                        style = TextStyle(
                            color = ComposeColor(0xFF1A3D62), // Тільки світла тема
                            fontSize = 16.sp
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        textAlign = TextAlign.Center
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Фінансова мета
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp)
                        .clickable { showGoalDialog = true },
                    colors = CardDefaults.cardColors(
                        containerColor = ComposeColor.White // Завжди світла тема
                    ),
                    shape = RoundedCornerShape(12.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = "Фінансова мета: $financialGoal",
                            style = TextStyle(
                                color = ComposeColor(0xFF1A3D62), // Тільки світла тема
                                fontSize = 16.sp
                            )
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = "Редагувати мету",
                            tint = ComposeColor(0xFF1A3D62), // Тільки світла тема
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Випадкова фінансова цитата
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = ComposeColor.White // Завжди світла тема
                    ),
                    shape = RoundedCornerShape(12.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                ) {
                    Text(
                        text = randomQuote,
                        style = TextStyle(
                            color = ComposeColor(0xFF1A3D62), // Тільки світла тема
                            fontSize = 14.sp,
                            fontStyle = FontStyle.Italic
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        textAlign = TextAlign.Center
                    )
                }

                Spacer(modifier = Modifier.weight(1f))
            }
        }
    )

    // Діалог для вибору джерела фото
    if (showImageSourceDialog) {
        AlertDialog(
            onDismissRequest = { showImageSourceDialog = false },
            title = {
                Text(
                    text = "Виберіть джерело",
                    style = TextStyle(
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = ComposeColor(0xFF1A3D62) // Тільки світла тема
                    )
                )
            },
            text = {
                Column {
                    TextButton(
                        onClick = {
                            onCameraClicked()
                            showImageSourceDialog = false
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            "Камера",
                            color = ComposeColor(0xFF1A3D62), // Тільки світла тема
                            fontSize = 16.sp
                        )
                    }
                    TextButton(
                        onClick = {
                            onGalleryClicked()
                            showImageSourceDialog = false
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            "Галерея",
                            color = ComposeColor(0xFF1A3D62), // Тільки світла тема
                            fontSize = 16.sp
                        )
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showImageSourceDialog = false }) {
                    Text(
                        "Скасувати",
                        color = ComposeColor.Gray // Тільки світла тема
                    )
                }
            },
            shape = RoundedCornerShape(16.dp),
            containerColor = ComposeColor.White // Тільки світла тема
        )
    }

    // Діалог для редагування фінансової мети
    if (showGoalDialog) {
        AlertDialog(
            onDismissRequest = { showGoalDialog = false },
            title = {
                Text(
                    text = "Редагувати фінансову мету",
                    style = TextStyle(
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = ComposeColor(0xFF1A3D62) // Тільки світла тема
                    )
                )
            },
            text = {
                OutlinedTextField(
                    value = newGoal,
                    onValueChange = { newGoal = it },
                    label = {
                        Text(
                            "Фінансова мета",
                            color = ComposeColor.Black // Тільки світла тема
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = ComposeColor.Black, // Тільки світла тема
                        unfocusedTextColor = ComposeColor.Black, // Тільки світла тема
                        focusedBorderColor = ComposeColor(0xFF1A3D62), // Тільки світла тема
                        unfocusedBorderColor = ComposeColor.Gray // Тільки світла тема
                    )
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    financialGoal = newGoal
                    sharedPreferences.edit().putString("financial_goal", financialGoal).apply()
                    showGoalDialog = false
                }) {
                    Text(
                        "Зберегти",
                        color = ComposeColor(0xFF1A3D62) // Тільки світла тема
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showGoalDialog = false }) {
                    Text(
                        "Скасувати",
                        color = ComposeColor.Gray // Тільки світла тема
                    )
                }
            },
            shape = RoundedCornerShape(16.dp),
            containerColor = ComposeColor.White // Тільки світла тема
        )
    }
}