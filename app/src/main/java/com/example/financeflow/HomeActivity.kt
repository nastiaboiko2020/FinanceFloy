package com.example.financeflow

import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.ViewModelProvider
import com.example.financeflow.ui.theme.FinanceFlowTheme
import com.example.financeflow.viewmodel.ExpenseViewModel
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.text.SimpleDateFormat
import java.util.*

class HomeActivity : ComponentActivity() {
    private lateinit var expenseViewModel: ExpenseViewModel
    private lateinit var sharedPreferences: SharedPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        expenseViewModel = ViewModelProvider(this).get(ExpenseViewModel::class.java)
        sharedPreferences = getSharedPreferences("finance_flow_prefs", MODE_PRIVATE)

        val initialBalance = sharedPreferences.getFloat("balance", 0f).toDouble()
        expenseViewModel.setBalance(initialBalance)
        val initialExpenses = loadExpenses(sharedPreferences)
        expenseViewModel.setExpenses(initialExpenses)

        setContent {
            FinanceFlowTheme {
                HomeScreen(expenseViewModel, sharedPreferences)
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
}

@Composable
fun HomeScreen(expenseViewModel: ExpenseViewModel, sharedPreferences: SharedPreferences) {
    val balance by expenseViewModel.balance
    val expenses by expenseViewModel.expenses

    fun saveExpenses(expenses: List<Expense>, prefs: SharedPreferences) {
        val gson = Gson()
        val json = gson.toJson(expenses)
        prefs.edit().putString("expenses_history", json).apply()
    }

    fun saveBalance(newBalance: Double) {
        sharedPreferences.edit().putFloat("balance", newBalance.toFloat()).apply()
    }

    var categoriesBalance = remember {
        mutableStateOf(
            mutableMapOf<String, Double>().apply {
                listOf("Харчування", "Житло", "Транспорт", "Медицина", "Розваги", "Подарунки", "Спорт", "Освіта", "Одяг", "Техніка")
                    .forEach { category ->
                        put(category, sharedPreferences.getFloat("expense_$category", 0f).toDouble())
                    }
            }
        )
    }
    var categories = remember { mutableStateOf(listOf("Харчування", "Житло", "Транспорт", "Медицина")) }
    var selectedCategories = remember { mutableStateOf(mutableSetOf<String>()) }
    var showDialog by remember { mutableStateOf(false) }
    var inputAmount by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf("") }
    var showAddCategoryDialog by remember { mutableStateOf(false) }
    var newCategoryName by remember { mutableStateOf("") }
    var showHistoryDialog by remember { mutableStateOf(false) }

    val scrollState = rememberScrollState()
    val context = LocalContext.current

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
                categoriesBalance.value.entries.forEach { (category: String, _: Double) ->
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
                .padding(top = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Шапка
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "FinanceFlow",
                    style = TextStyle(
                        color = Color.White,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold
                    )
                )
                Text(
                    text = "Історія",
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier
                        .clickable { showHistoryDialog = true }
                        .padding(8.dp)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Баланс
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .shadow(8.dp, RoundedCornerShape(16.dp)),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(
                    modifier = Modifier
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Мої кошти",
                        style = TextStyle(
                            color = Color(0xFF1A3D62),
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Medium
                        )
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "₴ $balance",
                        style = TextStyle(
                            color = Color(0xFF1A3D62),
                            fontSize = 36.sp,
                            fontWeight = FontWeight.Bold
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Кнопки дій
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                ActionButton(
                    text = "₴",
                    onClick = {
                        selectedCategory = ""
                        showDialog = true
                    },
                    icon = true
                )
                ActionButton(
                    text = "Аналіз",
                    onClick = {
                        val intent = Intent(context, StatisticsActivity::class.java)
                        context.startActivity(intent)
                    }
                )
                ActionButton(
                    text = "Чат",
                    onClick = {
                        val intent = Intent(context, AIChatActivity::class.java)
                        context.startActivity(intent)
                    }
                )
            }

            Spacer(modifier = Modifier.height(56.dp)) // Відступ перед "Мої витрати"

            // Заголовок "Мої витрати"
            Text(
                text = "Мої витрати",
                style = TextStyle(
                    color = Color(0xFF1A3D62),
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
            )

            Spacer(modifier = Modifier.height(16.dp)) // Додано відступ перед категоріями

            // Список категорій із прокруткою
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
                    categories.value.forEach { category ->
                        CategoryCard(
                            category = category,
                            amount = categoriesBalance.value[category] ?: 0.0,
                            onClick = {
                                selectedCategory = category
                                showDialog = true
                            }
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                }
            }

            // Кнопка "Додати категорію" внизу
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
                Text(
                    text = "Додати категорію",
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        // Діалоги
        if (showAddCategoryDialog) {
            ModernDialog(
                title = "Додати нову категорію витрат",
                onDismiss = { showAddCategoryDialog = false },
                content = {
                    Column {
                        val availableCategories = listOf("Розваги", "Подарунки", "Спорт", "Освіта", "Одяг", "Техніка")
                        availableCategories.forEach { category ->
                            val isSelected = selectedCategories.value.contains(category)
                            Button(
                                onClick = {
                                    selectedCategories.value = selectedCategories.value.toMutableSet().apply {
                                        if (isSelected) remove(category) else add(category)
                                    }
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
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
                        categories.value = categories.value + newCategoryName
                        expenseViewModel.addCategory(newCategoryName)
                        categoriesBalance.value[newCategoryName] = 0.0
                        newCategoryName = ""
                        showAddCategoryDialog = false
                    } else if (selectedCategories.value.isNotEmpty()) {
                        categories.value = categories.value + selectedCategories.value
                        selectedCategories.value.forEach { category ->
                            expenseViewModel.addCategory(category)
                            categoriesBalance.value[category] = 0.0
                        }
                        selectedCategories.value.clear()
                        showAddCategoryDialog = false
                    }
                }
            )
        }

        if (showDialog) {
            ModernDialog(
                title = if (selectedCategory.isEmpty()) "Додати кошти" else "Введіть суму витрат для $selectedCategory",
                onDismiss = { showDialog = false },
                content = {
                    OutlinedTextField(
                        value = inputAmount,
                        onValueChange = { inputAmount = it },
                        label = { Text("Сума") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp)
                    )
                },
                confirmAction = {
                    val amount = inputAmount.toDoubleOrNull() ?: 0.0
                    val currentDate = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault()).format(Date())
                    if (selectedCategory.isEmpty()) {
                        expenseViewModel.setBalance(amount)
                        saveBalance(balance + amount)
                    } else {
                        val currentAmount = categoriesBalance.value[selectedCategory] ?: 0.0
                        val newAmount = currentAmount + amount
                        categoriesBalance.value[selectedCategory] = newAmount
                        expenseViewModel.addExpense(selectedCategory, amount, currentDate)
                        saveBalance(balance - amount)
                        saveExpenses(expenseViewModel.expenses.value, sharedPreferences)
                        sharedPreferences.edit().putFloat("expense_$selectedCategory", newAmount.toFloat()).apply()
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
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 200.dp)
                                .verticalScroll(rememberScrollState())
                        ) {
                            expenses.reversed().forEachIndexed { index, expense ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 8.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Text(
                                            text = expense.category,
                                            style = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1A3D62))
                                        )
                                        Text(
                                            text = expense.date,
                                            style = TextStyle(fontSize = 14.sp, color = Color.Gray)
                                        )
                                    }
                                    Text(
                                        text = "₴${expense.amount}",
                                        style = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1A3D62))
                                    )
                                }
                                if (index < expenses.size - 1) {
                                    Divider(color = Color(0xFFE0E0E0), thickness = 1.dp)
                                }
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
                style = TextStyle(
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF1A3D62)
                )
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