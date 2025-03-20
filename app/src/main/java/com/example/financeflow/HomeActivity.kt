package com.example.financeflow

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.rememberScrollState
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModelProvider
import com.example.financeflow.ui.theme.FinanceFlowTheme
import com.example.financeflow.viewmodel.Expense
import com.example.financeflow.viewmodel.ExpenseViewModel
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.text.SimpleDateFormat
import java.util.*

class HomeActivity : ComponentActivity() {
    private lateinit var expenseViewModel: ExpenseViewModel
    private lateinit var sharedPreferences: SharedPreferences

    internal val requestPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
        if (!isGranted) {
            android.widget.Toast.makeText(this, "Дозвіл на камеру потрібен для сканування чеку", android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    internal val cameraLauncher = registerForActivityResult(ActivityResultContracts.TakePicturePreview()) { bitmap ->
        bitmap?.let { processImage(it) }
    }

    internal val galleryLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let { processImageFromUri(it) }
    }

    internal var onImageProcessed: ((String) -> Unit)? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        expenseViewModel = ViewModelProvider(this).get(ExpenseViewModel::class.java)
        sharedPreferences = getSharedPreferences("finance_flow_prefs", MODE_PRIVATE)

        val initialBalance = sharedPreferences.getFloat("current_balance", 0f).toDouble()
        expenseViewModel.setBalance(initialBalance)
        val initialExpenses = loadExpenses(sharedPreferences)
        expenseViewModel.setExpenses(initialExpenses)

        setContent {
            FinanceFlowTheme {
                HomeScreen(expenseViewModel, sharedPreferences, this)
            }
        }
    }

    private fun loadExpenses(sharedPreferences: SharedPreferences): MutableList<Expense> {
        val gson = Gson()
        val json = sharedPreferences.getString("expenses_history", null)
        return if (json != null) {
            val type = object : TypeToken<MutableList<Expense>>() {}.type
            gson.fromJson(json, type) ?: mutableListOf()
        } else {
            mutableListOf()
        }
    }

    private fun processImage(bitmap: Bitmap) {
        val image = InputImage.fromBitmap(bitmap, 0)
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        recognizer.process(image)
            .addOnSuccessListener { visionText ->
                Log.d("HomeActivity", "Розпізнаний текст: ${visionText.text}")
                val amount = extractAmountFromText(visionText.text)
                onImageProcessed?.invoke(amount)
            }
            .addOnFailureListener { e ->
                onImageProcessed?.invoke("0.0")
                android.widget.Toast.makeText(this, "Не вдалося розпізнати суму: ${e.message}", android.widget.Toast.LENGTH_SHORT).show()
            }
    }

    private fun processImageFromUri(uri: Uri) {
        try {
            val bitmap = MediaStore.Images.Media.getBitmap(contentResolver, uri)
            processImage(bitmap)
        } catch (e: Exception) {
            onImageProcessed?.invoke("0.0")
            android.widget.Toast.makeText(this, "Помилка завантаження зображення: ${e.message}", android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    private fun extractAmountFromText(text: String): String {
        // Шукаємо всі числа в тексті
        val regex = Regex("""(\d+[,.]\d{1,2}|\d+)\s*(грн|UAH)?""")
        val matches = regex.findAll(text)

        // Перетворюємо числа в список, замінюємо кому на крапку
        val amounts = matches.map { it.groupValues[1].replace(",", ".") }
            .filter { amount ->
                val num = amount.toDoubleOrNull() ?: 0.0
                // Фільтруємо числа:
                // - Виключаємо номери чеків (більше 6 цифр)
                // - Виключаємо занадто малі числа (менше 1 грн)
                // - Приймаємо числа до 1_000_000 грн
                val digitCount = amount.replace(".", "").length
                digitCount <= 6 && num in 1.0..1_000_000.0
            }
            .map { it.toDoubleOrNull() ?: 0.0 }
            .toList()

        // Якщо є числа, повертаємо найбільше
        return if (amounts.isNotEmpty()) {
            val maxAmount = amounts.maxOrNull() ?: 0.0
            Log.d("HomeActivity", "Найбільша сума: $maxAmount")
            maxAmount.toString()
        } else {
            Log.d("HomeActivity", "Не знайдено жодного числа")
            "0.0"
        }
    }
}

@Composable
fun HomeScreen(expenseViewModel: ExpenseViewModel, sharedPreferences: SharedPreferences, activity: HomeActivity) {
    val expenses by expenseViewModel.expenses
    var balance by remember { mutableStateOf(expenseViewModel.balance.value) } // Локальний стан для балансу
    val context = LocalContext.current

    fun saveExpenses(expenses: List<Expense>, prefs: SharedPreferences) {
        val gson = Gson()
        val json = gson.toJson(expenses)
        prefs.edit().putString("expenses_history", json).apply()
    }

    fun saveBalance(newBalance: Double) {
        sharedPreferences.edit().putFloat("current_balance", newBalance.toFloat()).apply()
        Log.d("HomeActivity", "Збережено баланс: ₴$newBalance")
        balance = newBalance // Оновлюємо локальний стан
        expenseViewModel.setBalance(newBalance) // Синхронізуємо з ViewModel
    }

    fun saveCategories(categories: List<String>) {
        val gson = Gson()
        val json = gson.toJson(categories)
        sharedPreferences.edit().putString("user_categories", json).apply()
    }

    fun loadCategories(): MutableList<String> {
        val gson = Gson()
        val json = sharedPreferences.getString("user_categories", null)
        return if (json != null) {
            val type = object : TypeToken<MutableList<String>>() {}.type
            gson.fromJson(json, type) ?: mutableListOf("Харчування", "Житло", "Транспорт", "Медицина")
        } else {
            mutableListOf("Харчування", "Житло", "Транспорт", "Медицина")
        }
    }

    var categoriesBalance = remember {
        mutableStateOf(
            mutableMapOf<String, Double>().apply {
                val allCategories = loadCategories()
                allCategories.forEach { category ->
                    put(category, sharedPreferences.getFloat("expense_$category", 0f).toDouble())
                }
            }
        )
    }
    var categories by remember { mutableStateOf(loadCategories()) }
    val predefinedCategories = listOf("Розваги", "Подарунки", "Спорт", "Освіта", "Одяг", "Техніка")
    var availableCategories by remember { mutableStateOf(predefinedCategories.toMutableList()) }
    var selectedCategories = remember { mutableStateOf(mutableSetOf<String>()) }
    var showDialog by remember { mutableStateOf(false) }
    var inputAmount by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf("") }
    var showAddCategoryDialog by remember { mutableStateOf(false) }
    var newCategoryName by remember { mutableStateOf("") }
    var showHistoryDialog by remember { mutableStateOf(false) }

    val scrollState = rememberScrollState()

    // Обнулення витрат по категоріях першого числа місяця
    LaunchedEffect(Unit) {
        val lastResetDateStr = sharedPreferences.getString("last_reset_date", null)
        val currentDate = Calendar.getInstance()
        val currentDay = currentDate.get(Calendar.DAY_OF_MONTH)
        val currentMonth = currentDate.get(Calendar.MONTH)
        val currentYear = currentDate.get(Calendar.YEAR)

        val lastResetCalendar = Calendar.getInstance()
        if (lastResetDateStr != null) {
            lastResetCalendar.time = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).parse(lastResetDateStr) ?: Date()
        } else {
            lastResetCalendar.set(currentYear, currentMonth - 1, 1)
        }

        val lastResetMonth = lastResetCalendar.get(Calendar.MONTH)
        val lastResetYear = lastResetCalendar.get(Calendar.YEAR)

        val shouldReset = currentDay == 1 && (currentMonth != lastResetMonth || currentYear != lastResetYear)

        if (shouldReset) {
            categoriesBalance.value = categoriesBalance.value.mapValues { 0.0 }.toMutableMap()
            with(sharedPreferences.edit()) {
                categoriesBalance.value.keys.forEach { category ->
                    putFloat("expense_$category", 0f)
                }
                putString("last_reset_date", SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(currentDate.time))
                apply()
            }
            Log.d("HomeActivity", "Витрати в категоріях обнулені: ${categoriesBalance.value}")
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF5F7FA))
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(280.dp)
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(Color(0xFF1A3D62), Color(0xFF2E5B8C))
                    )
                )
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "FinanceFlow",
                    style = TextStyle(color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                )
                Text(
                    text = "Історія",
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.clickable { showHistoryDialog = true }.padding(8.dp)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .shadow(8.dp, RoundedCornerShape(16.dp)),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Мої кошти",
                        style = TextStyle(color = Color(0xFF1A3D62), fontSize = 18.sp, fontWeight = FontWeight.Medium)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "₴ $balance",
                        style = TextStyle(color = Color(0xFF1A3D62), fontSize = 36.sp, fontWeight = FontWeight.Bold)
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                ActionButton(text = "₴", onClick = { selectedCategory = ""; showDialog = true }, icon = true)
                ActionButton(
                    text = "Аналіз",
                    onClick = { context.startActivity(Intent(context, StatisticsActivity::class.java)) }
                )
                ActionButton(
                    text = "Чат",
                    onClick = { context.startActivity(Intent(context, AIChatActivity::class.java)) }
                )
            }

            Spacer(modifier = Modifier.height(56.dp))

            Text(
                text = "Мої витрати",
                style = TextStyle(color = Color(0xFF1A3D62), fontSize = 20.sp, fontWeight = FontWeight.Bold),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
            )

            Spacer(modifier = Modifier.height(16.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(horizontal = 16.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(scrollState)
                ) {
                    categories.forEach { category ->
                        CategoryCard(
                            category = category,
                            amount = categoriesBalance.value[category] ?: 0.0,
                            onClick = { selectedCategory = category; showDialog = true }
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                }
            }

            Button(
                onClick = { showAddCategoryDialog = true },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                    .height(56.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1A3D62)),
                shape = RoundedCornerShape(12.dp),
                elevation = ButtonDefaults.buttonElevation(defaultElevation = 4.dp)
            ) {
                Text(text = "Додати категорію", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
        }

        if (showAddCategoryDialog) {
            ModernDialog(
                title = "Додати нову категорію витрат",
                onDismiss = { showAddCategoryDialog = false },
                content = {
                    Column {
                        availableCategories.forEach { category ->
                            val isSelected = selectedCategories.value.contains(category)
                            Button(
                                onClick = {
                                    selectedCategories.value = selectedCategories.value.toMutableSet().apply {
                                        if (isSelected) remove(category) else add(category)
                                    }
                                },
                                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (isSelected) Color(0xFF1A3D62) else Color(0xFFCCC8C8)
                                ),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text(
                                    text = category,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = if (isSelected) Color.White else Color.Black
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                        OutlinedTextField(
                            value = newCategoryName,
                            onValueChange = { newCategoryName = it },
                            label = { Text("Інше") },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(8.dp)
                        )
                    }
                },
                confirmAction = {
                    if (newCategoryName.isNotBlank()) {
                        categories.add(newCategoryName)
                        expenseViewModel.addCategory(newCategoryName)
                        categoriesBalance.value[newCategoryName] = 0.0
                        saveCategories(categories)
                        newCategoryName = ""
                        showAddCategoryDialog = false
                    } else if (selectedCategories.value.isNotEmpty()) {
                        selectedCategories.value.forEach { category ->
                            categories.add(category)
                            expenseViewModel.addCategory(category)
                            categoriesBalance.value[category] = 0.0
                            availableCategories.remove(category)
                        }
                        saveCategories(categories)
                        selectedCategories.value.clear()
                        showAddCategoryDialog = false
                    }
                }
            )
        }

        if (showDialog) {
            ModernDialog(
                title = if (selectedCategory.isEmpty()) "Встановити мої кошти" else "Введіть суму витрат для $selectedCategory",
                onDismiss = { showDialog = false },
                content = {
                    Column {
                        OutlinedTextField(
                            value = inputAmount,
                            onValueChange = { inputAmount = it },
                            label = { Text("Сума") },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(8.dp)
                        )
                        if (selectedCategory.isNotEmpty()) { // Додаємо кнопки лише для витрат
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 8.dp),
                                horizontalArrangement = Arrangement.SpaceEvenly
                            ) {
                                Button(
                                    onClick = {
                                        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                                            activity.onImageProcessed = { extractedAmount ->
                                                inputAmount = extractedAmount
                                            }
                                            activity.cameraLauncher.launch(null)
                                        } else {
                                            activity.requestPermissionLauncher.launch(Manifest.permission.CAMERA)
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4A7BA6))
                                ) {
                                    Text("Камера", color = Color.White)
                                }
                                Button(
                                    onClick = {
                                        activity.onImageProcessed = { extractedAmount ->
                                            inputAmount = extractedAmount
                                        }
                                        activity.galleryLauncher.launch("image/*")
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4A7BA6))
                                ) {
                                    Text("Галерея", color = Color.White)
                                }
                            }
                        }
                    }
                },
                confirmAction = {
                    val amount = inputAmount.toDoubleOrNull() ?: 0.0
                    val currentDate = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault()).format(Date())
                    if (selectedCategory.isEmpty()) {
                        saveBalance(amount) // Встановлюємо новий баланс
                    } else {
                        // Додаємо витрату до категорії
                        val currentAmount = categoriesBalance.value[selectedCategory] ?: 0.0
                        val newAmount = currentAmount + amount
                        categoriesBalance.value[selectedCategory] = newAmount
                        expenseViewModel.addExpense(selectedCategory, amount, currentDate)
                        saveExpenses(expenseViewModel.expenses.value, sharedPreferences)
                        sharedPreferences.edit().putFloat("expense_$selectedCategory", newAmount.toFloat()).apply()

                        // Віднімаємо суму з балансу
                        val newBalance = balance - amount
                        Log.d("HomeActivity", "Поточний баланс: $balance, Витрата: $amount, Новий баланс: $newBalance")
                        if (newBalance >= 0) {
                            saveBalance(newBalance)
                        } else {
                            // Дозволяємо додавання витрати, але показуємо попередження
                            saveBalance(newBalance) // Дозволяємо баланс стати від'ємним
                            android.widget.Toast.makeText(context, "Недостатньо коштів! Баланс: ₴$newBalance", android.widget.Toast.LENGTH_SHORT).show()
                        }
                    }
                    inputAmount = ""
                    showDialog = false
                }
            )
        }

        if (showHistoryDialog) {
            ModernDialog(
                title = "Історія витрат",
                onDismiss = { showHistoryDialog = false },
                content = {
                    if (expenses.isEmpty()) {
                        Text("Немає записів про витрати", color = Color.Gray)
                    } else {
                        val historyScrollState = rememberScrollState()
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 300.dp)
                                .verticalScroll(historyScrollState)
                        ) {
                            val groupedExpenses = expenses.reversed().groupBy { it.date.split(" ")[0] }
                            groupedExpenses.forEach { (date, dateExpenses) ->
                                Text(
                                    text = date,
                                    style = TextStyle(
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF1A3D62)
                                    ),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 8.dp),
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                )
                                dateExpenses.forEach { expense ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 4.dp, horizontal = 8.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column {
                                            Text(
                                                text = expense.category,
                                                style = TextStyle(fontSize = 14.sp, color = Color(0xFF1A3D62))
                                            )
                                            Text(
                                                text = expense.date.split(" ")[1],
                                                style = TextStyle(fontSize = 12.sp, color = Color.Gray)
                                            )
                                        }
                                        Text(
                                            text = "₴${expense.amount}",
                                            style = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1A3D62))
                                        )
                                    }
                                }
                                Divider(color = Color(0xFFE0E0E0), thickness = 1.dp)
                            }
                        }
                    }
                }
            )
        }
    }
}

@Composable
fun ActionButton(text: String, onClick: () -> Unit, icon: Boolean = false) {
    Button(
        onClick = onClick,
        modifier = Modifier
            .width(100.dp)
            .height(50.dp)
            .shadow(4.dp, RoundedCornerShape(8.dp)),
        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4A7BA6)),
        shape = RoundedCornerShape(8.dp),
        elevation = ButtonDefaults.buttonElevation(defaultElevation = 2.dp)
    ) {
        if (icon) {
            Text(text = text, color = Color.White, fontSize = 24.sp)
        } else {
            Text(text = text, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Medium)
        }
    }
}

@Composable
fun CategoryCard(category: String, amount: Double, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(60.dp)
            .clickable { onClick() }
            .shadow(4.dp, RoundedCornerShape(12.dp)),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = category,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                color = Color(0xFF1A3D62)
            )
            Text(
                text = "₴ $amount",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF1A3D62)
            )
        }
    }
}

@Composable
fun ModernDialog(
    title: String,
    onDismiss: () -> Unit,
    content: @Composable () -> Unit,
    confirmAction: (() -> Unit)? = null
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = title,
                style = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1A3D62))
            )
        },
        text = { content() },
        confirmButton = {
            if (confirmAction != null) {
                TextButton(onClick = confirmAction) {
                    Text("Додати", color = Color(0xFF1A3D62), fontWeight = FontWeight.Bold)
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Скасувати", color = Color.Gray)
            }
        },
        shape = RoundedCornerShape(16.dp),
        containerColor = Color.White
    )
}
