package com.example.financeflow

import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
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
import com.example.financeflow.ui.theme.FinanceFlowTheme
import com.example.financeflow.viewmodel.Expense
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import android.util.Log
import java.text.SimpleDateFormat
import java.util.*

class AIChatActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            FinanceFlowTheme {
                AIChatScreen(this) { finish() }
            }
        }
    }
}

@Composable
fun AIChatScreen(context: Context, onBackPressed: () -> Unit) {
    val sharedPreferences = context.getSharedPreferences("finance_flow_prefs", Context.MODE_PRIVATE)
    var userInput by remember { mutableStateOf("") }
    val chatHistory = remember { mutableStateListOf("AI: Привіт! Як я можу допомогти з твоїми фінансами?") }
    val isLoading = remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()
    val scrollState = rememberScrollState()

    val expenses = loadExpenses(sharedPreferences)
    val currentBalance = sharedPreferences.getFloat("current_balance", 0f).toDouble()
    Log.d("AIChatActivity", "Завантажено витрат: ${expenses.size}, дані: ${expenses.joinToString()}")
    Log.d("AIChatActivity", "Поточний баланс: ₴$currentBalance")

    val predefinedQuestions = listOf(
        "Які мої найбільші витрати?",
        "Чи можу я заощадити цього місяця?",
        "Скільки я витратив на їжу?",
        "Який мій середній щоденний витрата?",
        "На чому мені слід економити, щоб відкладати 3000 гривень?",
        "Які витрати в мене були цього місяця?",
        "Чи перевищують мої витрати мої кошти?",
        "Скільки у мене зараз грошей?",
        "Скільки я вчора витратила?",
        "Скільки я сьогодні витратила?"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF5F7FA))
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(150.dp)
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(Color(0xFF1A3D62), Color(0xFF2E5B8C))
                    )
                )
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = 16.dp)
        ) {
            Text(
                text = "AI Асистент",
                style = TextStyle(
                    color = Color.White,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold
                ),
                modifier = Modifier
                    .padding(top = 16.dp, bottom = 24.dp)
                    .align(Alignment.CenterHorizontally)
            )

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(horizontal = 16.dp)
                    .shadow(4.dp, RoundedCornerShape(12.dp)),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp)
                        .verticalScroll(scrollState)
                ) {
                    chatHistory.forEach { message ->
                        Text(
                            text = message,
                            style = TextStyle(
                                color = if (message.startsWith("AI:")) Color(0xFF1A3D62) else Color.Black,
                                fontSize = 16.sp
                            ),
                            modifier = Modifier.padding(vertical = 4.dp)
                        )
                    }
                    if (isLoading.value) {
                        CircularProgressIndicator(
                            modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 8.dp)
                        )
                    }
                }
            }

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 120.dp)
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                items(predefinedQuestions) { question ->
                    Text(
                        text = question,
                        style = TextStyle(
                            color = Color(0xFF1A3D62),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .background(Color.White, RoundedCornerShape(8.dp))
                            .shadow(2.dp, RoundedCornerShape(8.dp))
                            .padding(8.dp)
                            .clickable {
                                chatHistory.add("Ви: $question")
                                coroutineScope.launch {
                                    handleUserInput(question, chatHistory, isLoading, expenses, currentBalance, context)
                                }
                            }
                    )
                }
            }

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .shadow(4.dp, RoundedCornerShape(12.dp)),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = userInput,
                        onValueChange = {
                            userInput = it
                            Log.d("AIChatActivity", "Оновлено userInput: '$userInput'")
                        },
                        label = { Text("Введіть запит") },
                        modifier = Modifier
                            .weight(1f)
                            .padding(end = 8.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = TextFieldDefaults.colors(
                            focusedIndicatorColor = Color(0xFF4A7BA6),
                            unfocusedIndicatorColor = Color.Gray,
                            cursorColor = Color(0xFF1A3D62)
                        )
                    )
                    Button(
                        onClick = {
                            if (userInput.isNotBlank()) {
                                val inputToSend = userInput
                                Log.d("AIChatActivity", "Натиснуто кнопку, відправляємо: '$inputToSend'")
                                chatHistory.add("Ви: $inputToSend")
                                coroutineScope.launch {
                                    handleUserInput(inputToSend, chatHistory, isLoading, expenses, currentBalance, context)
                                }
                                userInput = ""
                            } else {
                                Log.d("AIChatActivity", "userInput порожній, нічого не відправлено")
                            }
                        },
                        modifier = Modifier.size(48.dp),
                        shape = RoundedCornerShape(50),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4A7BA6))
                    ) {
                        Text("🗨️", fontSize = 20.sp, color = Color.White)
                    }
                }
            }

            Button(
                onClick = onBackPressed,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                    .height(56.dp)
                    .shadow(4.dp, RoundedCornerShape(12.dp)),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4A7BA6)),
                shape = RoundedCornerShape(12.dp),
                elevation = ButtonDefaults.buttonElevation(defaultElevation = 4.dp)
            ) {
                Text(
                    text = "Назад",
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

suspend fun handleUserInput(
    input: String,
    chatHistory: MutableList<String>,
    isLoading: MutableState<Boolean>,
    expenses: List<Expense>,
    currentBalance: Double,
    context: Context
) {
    isLoading.value = true

    try {
        Log.d("AIChatActivity", "Вхідний запит: '$input'")
        Log.d("AIChatActivity", "Список витрат: ${expenses.joinToString()}")
        Log.d("AIChatActivity", "Поточний баланс: ₴$currentBalance")

        if (expenses.isEmpty() && currentBalance == 0.0) {
            withContext(Dispatchers.Main) {
                chatHistory.add("AI: У мене немає даних про ваші витрати чи кошти.")
            }
            return
        }

        val inputLower = input.trim().lowercase()
        Log.d("AIChatActivity", "Запит у нижньому регістрі: '$inputLower'")

        val totalSpent = expenses.sumOf { it.amount }
        val dateFormat = SimpleDateFormat("dd.MM.yyyy", Locale.getDefault())
        val currentDate = Calendar.getInstance().apply { time = Date() } // Поточна дата: 20.03.2025
        val currentMonth = currentDate.get(Calendar.MONTH) + 1

        when {
            inputLower == "які мої найбільші витрати?" -> {
                val maxExpense = expenses.groupBy { it.category }
                    .mapValues { it.value.sumOf { expense -> expense.amount } }
                    .maxByOrNull { it.value }
                val response = "Ваші найбільші витрати — це ${maxExpense?.key} (₴${maxExpense?.value})."
                withContext(Dispatchers.Main) {
                    chatHistory.add("AI: $response")
                }
            }
            inputLower == "чи можу я заощадити цього місяця?" -> {
                val thisMonthExpenses = expenses.filter {
                    val expenseDate = dateFormat.parse(it.date.split(" ")[0])
                    expenseDate?.let { date -> Calendar.getInstance().apply { time = date }.get(Calendar.MONTH) + 1 == currentMonth } ?: false
                }.sumOf { it.amount }
                val savingsPotential = currentBalance - thisMonthExpenses
                val response = if (savingsPotential > 0) {
                    "Так, з вашим поточним балансом (₴$currentBalance) ви можете заощадити ₴${String.format("%.2f", savingsPotential)} цього місяця."
                } else {
                    "Ні, ваші витрати цього місяця (₴$thisMonthExpenses) перевищують поточний баланс (₴$currentBalance) на ₴${String.format("%.2f", -savingsPotential)}."
                }
                withContext(Dispatchers.Main) {
                    chatHistory.add("AI: $response")
                }
            }
            inputLower == "скільки я витратив на їжу?" -> {
                val foodExpenses = expenses.filter { it.category == "Харчування" }.sumOf { it.amount }
                val response = "Ви витратили на їжу ₴$foodExpenses."
                withContext(Dispatchers.Main) {
                    chatHistory.add("AI: $response")
                }
            }
            inputLower == "який мій середній щоденний витрата?" -> {
                val uniqueDays = expenses.map { it.date.split(" ")[0] }.distinct().size
                val averageDaily = totalSpent / uniqueDays
                val response = "Ваш середній щоденний витрата — ₴${String.format("%.2f", averageDaily)}."
                withContext(Dispatchers.Main) {
                    chatHistory.add("AI: $response")
                }
            }
            inputLower.contains("скільки я вчора витратила?") || inputLower.contains("скільки я вчора витратив?") ||
                    inputLower.contains("скільки я вчора потратила?") || inputLower.contains("скільки я вчора потратив?") -> {
                val yesterday = Calendar.getInstance().apply {
                    time = Date()
                    add(Calendar.DAY_OF_YEAR, -1) // Вчорашня дата
                }
                val yesterdayStr = dateFormat.format(yesterday.time)
                val yesterdayExpenses = expenses.filter {
                    it.date.split(" ")[0] == yesterdayStr
                }.sumOf { it.amount }
                val response = "Вчора (${yesterdayStr}) ви витратили ₴${String.format("%.2f", yesterdayExpenses)}."
                withContext(Dispatchers.Main) {
                    chatHistory.add("AI: $response")
                }
            }
            inputLower.contains("скільки я сьогодні витратила?") || inputLower.contains("скільки я сьогодні витратив?") ||
                    inputLower.contains("сколько я сьогодні потратила?") || inputLower.contains("сколько я сьогодні потратив?") -> {
                val todayStr = dateFormat.format(currentDate.time)
                val todayExpenses = expenses.filter {
                    it.date.split(" ")[0] == todayStr
                }.sumOf { it.amount }
                val response = "Сьогодні (${todayStr}) ви витратили ₴${String.format("%.2f", todayExpenses)}."
                withContext(Dispatchers.Main) {
                    chatHistory.add("AI: $response")
                }
            }
            inputLower.contains("скільки я витратив") || inputLower.contains("скільки я витратила") -> {
                val categoryMatch = Regex("на ([а-яїє]+)").find(inputLower)?.groupValues?.get(1)
                val dateRangeMatch = Regex("з (\\d+\\.\\d+) по (\\d+\\.\\d+)").find(inputLower)
                val singleDateMatch = if (dateRangeMatch == null) Regex("(\\d+\\.\\d+)").find(inputLower) else null

                val category = when (categoryMatch?.lowercase()) {
                    "їжу", "харчування" -> "Харчування"
                    "житло" -> "Житло"
                    "транспорт" -> "Транспорт"
                    "медицину" -> "Медицина"
                    "одяг" -> "Одяг"
                    "спорт" -> "Спорт"
                    "техніку" -> "Техніка"
                    else -> null
                }

                if (category != null) {
                    val filteredExpenses = if (dateRangeMatch != null) {
                        val startDateStr = dateRangeMatch.groupValues[1]
                        val endDateStr = dateRangeMatch.groupValues[2]
                        filterExpensesByDateRange(expenses, startDateStr, endDateStr)
                    } else if (singleDateMatch != null) {
                        val dateStr = singleDateMatch.groupValues[1]
                        filterExpensesBySingleDate(expenses, dateStr)
                    } else {
                        expenses
                    }

                    val categoryExpenses = filteredExpenses.filter { it.category == category }.sumOf { it.amount }
                    val response = when {
                        dateRangeMatch != null -> "Ви витратили на $category ₴$categoryExpenses з ${dateRangeMatch.groupValues[1]} по ${dateRangeMatch.groupValues[2]}."
                        singleDateMatch != null -> "Ви витратили на $category ₴$categoryExpenses ${singleDateMatch.groupValues[1]}."
                        else -> "Ви витратили на $category ₴$categoryExpenses загалом."
                    }
                    withContext(Dispatchers.Main) {
                        chatHistory.add("AI: $response")
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        chatHistory.add("AI: Я не розпізнав категорію витрат. Спробуйте вказати, наприклад, 'їжу', 'житло' тощо.")
                    }
                }
            }
            inputLower.contains("на чому мені слід економити") || inputLower.contains("як мені заощадити") -> {
                val savingsGoalMatch = Regex("(\\d+)\\s*гривень").find(inputLower)
                val savingsGoal = savingsGoalMatch?.groupValues?.get(1)?.toDoubleOrNull() ?: 0.0

                if (currentBalance >= savingsGoal) {
                    val response = "З вашим поточним балансом (₴$currentBalance) ви вже можете відкласти ₴$savingsGoal."
                    withContext(Dispatchers.Main) {
                        chatHistory.add("AI: $response")
                    }
                } else {
                    val deficit = savingsGoal - currentBalance
                    val categoryTotals = expenses.groupBy { it.category }
                        .mapValues { it.value.sumOf { expense -> expense.amount } }
                        .entries.sortedByDescending { it.value }

                    val suggestions = mutableListOf<String>()
                    var remainingDeficit = deficit
                    for ((category, amount) in categoryTotals) {
                        if (remainingDeficit > 0 && amount > 0) {
                            val reduction = minOf(amount, remainingDeficit)
                            suggestions.add("Скоротіть витрати на $category на ₴${String.format("%.2f", reduction)}")
                            remainingDeficit -= reduction
                        }
                    }

                    val response = if (suggestions.isNotEmpty()) {
                        "Щоб відкладати ₴$savingsGoal з вашим балансом (₴$currentBalance), вам потрібно заощадити ₴${String.format("%.2f", deficit)}. " +
                                "Ось що можна зробити:\n" + suggestions.joinToString("\n")
                    } else {
                        "Ваш баланс (₴$currentBalance) замалий, щоб відкласти ₴$savingsGoal, навіть якщо скоротити всі витрати."
                    }
                    withContext(Dispatchers.Main) {
                        chatHistory.add("AI: $response")
                    }
                }
            }
            inputLower.contains("які витрати в мене були цього місяця?") -> {
                val thisMonthExpenses = expenses.filter {
                    val expenseDate = dateFormat.parse(it.date.split(" ")[0])
                    expenseDate?.let { date -> Calendar.getInstance().apply { time = date }.get(Calendar.MONTH) + 1 == currentMonth } ?: false
                }
                val summary = thisMonthExpenses.groupBy { it.category }
                    .map { "${it.key}: ₴${it.value.sumOf { expense -> expense.amount }}" }
                    .joinToString("\n")
                val totalThisMonth = thisMonthExpenses.sumOf { it.amount }
                val response = "Ваші витрати цього місяця:\n$summary\nЗагалом: ₴$totalThisMonth"
                withContext(Dispatchers.Main) {
                    chatHistory.add("AI: $response")
                }
            }
            inputLower.contains("чи перевищують мої витрати мої кошти?") -> {
                val response = if (totalSpent > currentBalance) {
                    "Так, ваші витрати (₴$totalSpent) перевищують ваші кошти (₴$currentBalance) на ₴${String.format("%.2f", totalSpent - currentBalance)}."
                } else {
                    "Ні, ваші витрати (₴$totalSpent) менші за ваші кошти (₴$currentBalance), у вас залишається ₴${String.format("%.2f", currentBalance - totalSpent)}."
                }
                withContext(Dispatchers.Main) {
                    chatHistory.add("AI: $response")
                }
            }
            inputLower.contains("яка моя найдорожча покупка?") -> {
                val mostExpensive = expenses.maxByOrNull { it.amount }
                val response = if (mostExpensive != null) {
                    "Ваша найдорожча покупка — це ${mostExpensive.category} на суму ₴${mostExpensive.amount} від ${mostExpensive.date}."
                } else {
                    "У вас поки немає записів про покупки."
                }
                withContext(Dispatchers.Main) {
                    chatHistory.add("AI: $response")
                }
            }
            inputLower.contains("скільки я можу відкласти?") -> {
                val savingsPotential = currentBalance - totalSpent
                val response = if (savingsPotential > 0) {
                    "З вашим балансом (₴$currentBalance) і витратами (₴$totalSpent) ви можете відкласти ₴${String.format("%.2f", savingsPotential)}."
                } else {
                    "Ви не можете відкласти нічого, бо ваші витрати (₴$totalSpent) перевищують баланс (₴$currentBalance) на ₴${String.format("%.2f", -savingsPotential)}."
                }
                withContext(Dispatchers.Main) {
                    chatHistory.add("AI: $response")
                }
            }
            inputLower.contains("які категорії витрат у мене є?") -> {
                val categories = expenses.map { it.category }.distinct().joinToString(", ")
                val response = "Ваші категорії витрат: $categories."
                withContext(Dispatchers.Main) {
                    chatHistory.add("AI: $response")
                }
            }
            inputLower.contains("скільки днів я витрачав гроші?") -> {
                val uniqueDays = expenses.map { it.date.split(" ")[0] }.distinct().size
                val response = "Ви витрачали гроші протягом $uniqueDays днів."
                withContext(Dispatchers.Main) {
                    chatHistory.add("AI: $response")
                }
            }
            inputLower.contains("скільки у мене зараз грошей?") -> {
                val response = "Наразі у вас є ₴${String.format("%.2f", currentBalance)}."
                withContext(Dispatchers.Main) {
                    chatHistory.add("AI: $response")
                }
            }
            else -> {
                Log.d("AIChatActivity", "Жодна умова не спрацювала, аналізуємо ключові слова")
                val response = when {
                    inputLower.contains("витрати") -> "Я можу розповісти про ваші витрати. Спробуйте 'Які мої найбільші витрати?' або 'Скільки я витратив на їжу?'"
                    inputLower.contains("гроші") || inputLower.contains("кошти") -> "Хочете знати про ваші кошти? Спробуйте 'Скільки у мене зараз грошей?' або 'Чи перевищують мої витрати мої кошти?'"
                    inputLower.contains("заощадити") || inputLower.contains("економити") -> "Хочете заощадити? Спробуйте 'На чому мені слід економити, щоб відкладати 3000 гривень?'"
                    inputLower.contains("покупка") -> "Цікавлять покупки? Спробуйте 'Яка моя найдорожча покупка?'"
                    inputLower.contains("скільки") -> "Хочете знати суму? Уточніть, наприклад, 'Скільки я можу відкласти?' або 'Скільки я витратив на їжу?'"
                    else -> "Я не зрозумів вашого питання. Спробуйте запитати щось про витрати, заощадження чи ваші кошти."
                }
                withContext(Dispatchers.Main) {
                    chatHistory.add("AI: $response")
                }
            }
        }
    } catch (e: Exception) {
        withContext(Dispatchers.Main) {
            chatHistory.add("AI: Виникла помилка: ${e.message}")
            Log.e("AIChatActivity", "Помилка обробки: ", e)
        }
    } finally {
        isLoading.value = false
    }
}

fun filterExpensesByDateRange(expenses: List<Expense>, startDateStr: String, endDateStr: String): List<Expense> {
    val dateFormat = SimpleDateFormat("dd.MM.yyyy", Locale.getDefault())
    val currentYear = Calendar.getInstance().get(Calendar.YEAR)

    val startDateFull = if (startDateStr.split(".").size == 2) "$startDateStr.$currentYear" else startDateStr
    val endDateFull = if (endDateStr.split(".").size == 2) "$endDateStr.$currentYear" else endDateStr

    val startDate = dateFormat.parse(startDateFull) ?: run {
        Log.e("AIChatActivity", "Не вдалося розпарсити початкову дату: $startDateFull")
        return emptyList()
    }
    val endDate = dateFormat.parse(endDateFull) ?: run {
        Log.e("AIChatActivity", "Не вдалося розпарсити кінцеву дату: $endDateFull")
        return emptyList()
    }

    return expenses.filter { expense ->
        val expenseDateStr = expense.date.split(" ")[0]
        val expenseDate = dateFormat.parse(expenseDateStr) ?: return@filter false
        expenseDate in startDate..endDate
    }
}

fun filterExpensesBySingleDate(expenses: List<Expense>, dateStr: String): List<Expense> {
    val dateFormat = SimpleDateFormat("dd.MM.yyyy", Locale.getDefault())
    val currentYear = Calendar.getInstance().get(Calendar.YEAR)

    val dateFull = if (dateStr.split(".").size == 2) "$dateStr.$currentYear" else dateStr
    val targetDate = dateFormat.parse(dateFull) ?: run {
        Log.e("AIChatActivity", "Не вдалося розпарсити дату: $dateFull")
        return emptyList()
    }

    return expenses.filter { expense ->
        val expenseDateStr = expense.date.split(" ")[0]
        val expenseDate = dateFormat.parse(expenseDateStr) ?: return@filter false
        expenseDate == targetDate
    }
}

fun analyzeExpenses(expenses: List<Expense>): String {
    if (expenses.isEmpty()) return "Дані про витрати відсутні."

    val totalSpent = expenses.sumOf { it.amount }
    val categorySummary = expenses.groupBy { it.category }
        .map { "$it.key: ₴${it.value.sumOf { expense -> expense.amount }}" }
        .joinToString(", ")
    val averageDaily = totalSpent / (expenses.map { it.date.split(" ")[0] }.distinct().size)

    return """
        Загальні витрати: ₴$totalSpent
        Витрати за категоріями: $categorySummary
        Середні витрати на день: ₴${String.format("%.2f", averageDaily)}
    """.trimIndent()
}
