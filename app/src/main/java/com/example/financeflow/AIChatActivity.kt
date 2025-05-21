package com.example.financeflow

import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.outlined.Info
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
import com.example.financeflow.viewmodel.Income
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import android.util.Log
import java.math.BigDecimal
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject

// Імпортуємо функції з Utils.kt і HomeActivity.kt
import com.example.financeflow.loadAllExpenses
import com.example.financeflow.loadAllIncomes

class AIChatActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            FinanceFlowTheme(darkTheme = false, useAIChatTheme = true) {
                AIChatScreen(
                    context = this@AIChatActivity,
                    onBackPressed = { finish() }
                )
            }
        }
    }
}

@Composable
fun AIChatScreen(context: Context, onBackPressed: () -> Unit) {
    val sharedPreferences = context.getSharedPreferences("finance_flow_prefs", Context.MODE_PRIVATE)
    val editor = sharedPreferences.edit()

    // Завантажуємо історію чату
    val savedChatHistoryJson = sharedPreferences.getString("chat_history", null)
    val initialChatHistory = if (savedChatHistoryJson != null) {
        val type = object : TypeToken<MutableList<String>>() {}.type
        Gson().fromJson(savedChatHistoryJson, type) ?: mutableListOf("AI: Привіт! Я твій фінансовий асистент. Як я можу допомогти?")
    } else {
        mutableListOf("AI: Привіт! Я твій фінансовий асистент. Як я можу допомогти?")
    }

    val chatHistory = remember { mutableStateListOf<String>().apply { addAll(initialChatHistory) } }
    var userInput by remember { mutableStateOf("") }
    val isLoading = remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()
    val scrollState = rememberScrollState()

    // Оновлені заготовлені запитання
    val predefinedQuestions = listOf(
        "Яка у мене середня витрата за день цього місяця?",
        "Чи вистачить мені грошей на покупку за 5000 гривень?",
        "Яка категорія витрат у мене найбільша?",
        "Скільки я можу відкласти цього місяця?",
        "Як змінились мої витрати порівняно з минулим місяцем?",
        "Який у мене найбільший дохід за весь час?",
        "Чи є у мене зайві витрати, які можна скоротити?",
        "Який у мене фінансовий тренд за останні 3 місяці?"
    )

    // Зберігаємо історію чату
    LaunchedEffect(chatHistory) {
        val chatHistoryJson = Gson().toJson(chatHistory)
        editor.putString("chat_history", chatHistoryJson)
        editor.apply()
    }

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
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp, bottom = 24.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "AI Асистент",
                    style = TextStyle(
                        color = Color.White,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold
                    )
                )
            }

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

            Spacer(modifier = Modifier.height(8.dp))

            // Список заготовлених питань
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 120.dp)
                ) {
                    items(predefinedQuestions) { question ->
                        val interactionSource = remember { MutableInteractionSource() }
                        val isPressed by interactionSource.collectIsPressedAsState()
                        val backgroundColor = if (isPressed) {
                            Color(0xFF1A3D62).copy(alpha = 0.1f)
                        } else {
                            Color(0xFFF5F7FA).copy(alpha = 0.5f)
                        }

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                                .background(
                                    color = backgroundColor,
                                    shape = RoundedCornerShape(8.dp)
                                )
                                .clickable(
                                    interactionSource = interactionSource,
                                    indication = null
                                ) {
                                    chatHistory.add("Ви: $question")
                                    coroutineScope.launch {
                                        handleUserInput(question, chatHistory, isLoading, sharedPreferences, context)
                                    }
                                }
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Info,
                                contentDescription = null,
                                tint = Color(0xFF1A3D62),
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = question,
                                style = TextStyle(
                                    color = Color(0xFF1A3D62),
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

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
                        onValueChange = { userInput = it },
                        label = { Text("Введіть запит", color = Color(0xFF1A3D62)) },
                        modifier = Modifier
                            .weight(1f)
                            .padding(end = 8.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = TextFieldDefaults.colors(
                            focusedIndicatorColor = Color(0xFF4A7BA6),
                            unfocusedIndicatorColor = Color.Gray.copy(alpha = 0.5f),
                            cursorColor = Color(0xFF1A3D62)
                        )
                    )

                    Crossfade(targetState = userInput.isNotEmpty(), label = "ButtonTransition") { showSendButton ->
                        if (showSendButton) {
                            IconButton(
                                onClick = {
                                    if (userInput.isNotBlank()) {
                                        val inputToSend = userInput
                                        chatHistory.add("Ви: $inputToSend")
                                        coroutineScope.launch {
                                            handleUserInput(inputToSend, chatHistory, isLoading, sharedPreferences, context)
                                        }
                                        userInput = ""
                                    }
                                },
                                modifier = Modifier
                                    .size(48.dp)
                                    .background(
                                        color = Color(0xFF4A7BA6),
                                        shape = RoundedCornerShape(50)
                                    )
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Send,
                                    contentDescription = "Відправити",
                                    tint = Color.White
                                )
                            }
                        } else {
                            IconButton(
                                onClick = {
                                    coroutineScope.launch {
                                        handleFinancialAdvice(chatHistory, isLoading, sharedPreferences, context)
                                    }
                                },
                                modifier = Modifier
                                    .size(48.dp)
                                    .background(
                                        color = Color(0xFF1A3D62),
                                        shape = CircleShape
                                    )
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Lightbulb,
                                    contentDescription = "Отримати фінансову пораду",
                                    tint = Color.White,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }
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

suspend fun handleFinancialAdvice(
    chatHistory: MutableList<String>,
    isLoading: MutableState<Boolean>,
    sharedPreferences: SharedPreferences,
    context: Context
) {
    isLoading.value = true
    try {
        val allExpenses = loadAllExpenses(sharedPreferences)
        val allIncomes = loadAllIncomes(sharedPreferences)
        val currentBalanceStr = sharedPreferences.getString("current_balance", "0.00") ?: "0.00"
        val currentBalance = BigDecimal(currentBalanceStr)

        if (allExpenses.isEmpty() && allIncomes.isEmpty() && currentBalance == BigDecimal.ZERO) {
            withContext(Dispatchers.Main) {
                chatHistory.add("AI: У вас немає фінансових даних. Додайте записи про витрати чи доходи!")
            }
            return
        }

        // Групуємо витрати за категоріями
        val expensesByCategory = allExpenses.groupBy { it.category }
        val categoryTotals = expensesByCategory.mapValues { entry ->
            entry.value.fold(BigDecimal.ZERO) { sum, expense -> sum + expense.amount }
        }
        val topCategory = categoryTotals.maxByOrNull { it.value }

        // Аналіз трендів за останні 3 місяці
        val dateFormat = SimpleDateFormat("dd.MM.yyyy", Locale.getDefault())
        val currentDate = Calendar.getInstance()
        val threeMonthsAgo = Calendar.getInstance().apply { add(Calendar.MONTH, -3) }
        val recentExpenses = allExpenses.filter { expense ->
            val expenseDate = dateFormat.parse(expense.date) ?: return@filter false
            val expenseCal = Calendar.getInstance().apply { time = expenseDate }
            expenseCal.after(threeMonthsAgo) && expenseCal.before(currentDate)
        }
        val expensesByMonth = recentExpenses.groupBy { expense ->
            val expenseDate = dateFormat.parse(expense.date) ?: return@groupBy "Unknown"
            val cal = Calendar.getInstance().apply { time = expenseDate }
            "${cal.get(Calendar.MONTH) + 1}.${cal.get(Calendar.YEAR)}"
        }
        val monthlyTotals = expensesByMonth.mapValues { entry ->
            entry.value.fold(BigDecimal.ZERO) { sum, expense -> sum + expense.amount }
        }

        val userData = mapOf(
            "current_balance" to currentBalance.toString(),
            "total_expenses" to allExpenses.sumOf { it.amount }.toString(),
            "total_incomes" to allIncomes.sumOf { it.amount }.toString(),
            "expenses" to allExpenses,
            "incomes" to allIncomes,
            "top_category" to (topCategory?.key ?: "немає даних"),
            "top_category_amount" to (topCategory?.value?.toString() ?: "0.00"),
            "monthly_expense_trends" to monthlyTotals
        )
        val userDataJson = Gson().toJson(userData)

        val systemPrompt = """
            You are a financial assistant providing actionable advice based on the user's financial data.
            User data: $userDataJson

            ### Instructions:
            - Analyze the user's financial data: current balance, total expenses, total incomes, top spending category, and monthly expense trends.
            - Provide concise financial advice in Ukrainian (1-2 sentences).
            - Focus on one actionable step to improve the user's financial situation:
              - If the top category is significant, suggest reducing spending there.
              - If the balance is low compared to expenses, suggest saving strategies.
              - If incomes exceed expenses, encourage saving or investing.
              - If monthly trends show increasing expenses, suggest reviewing spending habits.
            - Respond in the format: "Фінансова порада: <your advice>."
            - Current date: ${SimpleDateFormat("dd.MM.yyyy", Locale.getDefault()).format(Date())}
        """.trimIndent()

        val apiKey = BuildConfig.GROQ_API_KEY
        if (apiKey.isBlank()) {
            withContext(Dispatchers.Main) {
                chatHistory.add("AI: Помилка: API-ключ відсутній.")
            }
            return
        }

        val client = OkHttpClient.Builder()
            .connectTimeout(60, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .build()

        val requestBody = JSONObject().apply {
            put("model", "llama3-8b-8192")
            put("messages", JSONArray().apply {
                put(JSONObject().apply {
                    put("role", "system")
                    put("content", systemPrompt)
                })
                put(JSONObject().apply {
                    put("role", "user")
                    put("content", "Дай фінансову пораду")
                })
            })
            put("max_tokens", 100)
            put("temperature", 0.5)
        }.toString()

        val request = Request.Builder()
            .url("https://api.groq.com/openai/v1/chat/completions")
            .header("Authorization", "Bearer $apiKey")
            .post(requestBody.toRequestBody("application/json".toMediaType()))
            .build()

        var response: okhttp3.Response? = null
        val maxRetries = 5
        var attempt = 0
        while (attempt < maxRetries && response == null) {
            attempt++
            try {
                response = withContext(Dispatchers.IO) {
                    client.newCall(request).execute()
                }
            } catch (e: Exception) {
                if (attempt == maxRetries) throw e
                delay(3000L)
            }
        }

        if (response != null && response.isSuccessful) {
            val responseBody = response.body?.string()
            val json = JSONObject(responseBody)
            val advice = json.getJSONArray("choices")
                .getJSONObject(0)
                .getJSONObject("message")
                .getString("content")
            withContext(Dispatchers.Main) {
                chatHistory.add("AI: $advice")
            }
        } else {
            withContext(Dispatchers.Main) {
                chatHistory.add("AI: Не вдалося отримати пораду. Спробуйте ще раз пізніше.")
            }
        }
    } catch (e: Exception) {
        withContext(Dispatchers.Main) {
            chatHistory.add("AI: Виникла помилка. Спробуйте ще раз.")
        }
    } finally {
        isLoading.value = false
    }
}

fun parseMonthAndYearFromInput(input: String): Pair<Int, Int> {
    val inputLower = input.lowercase()
    val currentDate = Calendar.getInstance()
    val currentYear = currentDate.get(Calendar.YEAR)
    var targetMonth = currentDate.get(Calendar.MONTH) + 1
    var targetYear = currentYear

    val monthMap = mapOf(
        "січень" to 1, "січня" to 1, "лютий" to 2, "лютого" to 2,
        "березень" to 3, "березня" to 3, "квітень" to 4, "квітня" to 4,
        "травень" to 5, "травня" to 5, "червень" to 6, "червня" to 6,
        "липень" to 7, "липня" to 7, "серпень" to 8, "серпня" to 8,
        "вересень" to 9, "вересня" to 9, "жовтень" to 10, "жовтня" to 10,
        "листопад" to 11, "листопада" to 11, "грудень" to 12, "грудня" to 12
    )

    for ((monthName, monthNumber) in monthMap) {
        if (inputLower.contains(monthName)) {
            targetMonth = monthNumber
            break
        }
    }

    val yearMatch = Regex("\\b(20\\d{2})\\b").find(inputLower)
    if (yearMatch != null) {
        targetYear = yearMatch.value.toInt()
    }

    return Pair(targetMonth, targetYear)
}

fun isMonthSpecificQuery(input: String): Boolean {
    val inputLower = input.lowercase()
    val monthKeywords = listOf(
        "січень", "січня", "лютий", "лютого", "березень", "березня",
        "квітень", "квітня", "травень", "травня", "червень", "червня",
        "липень", "липня", "серпень", "серпня", "вересень", "вересня",
        "жовтень", "жовтня", "листопад", "листопада", "грудень", "грудня"
    )
    return monthKeywords.any { inputLower.contains(it) }
}

fun isComparisonQuery(input: String): Boolean {
    val inputLower = input.lowercase()
    return inputLower.contains("найбільше") || inputLower.contains("найменше") || inputLower.contains("в якому місяці") || inputLower.contains("порівняно")
}

suspend fun handleUserInput(
    input: String,
    chatHistory: MutableList<String>,
    isLoading: MutableState<Boolean>,
    sharedPreferences: SharedPreferences,
    context: Context
) {
    isLoading.value = true
    try {
        val allExpenses = loadAllExpenses(sharedPreferences)
        val allIncomes = loadAllIncomes(sharedPreferences)
        val currentBalanceStr = sharedPreferences.getString("current_balance", "0.00") ?: "0.00"
        val currentBalance = BigDecimal(currentBalanceStr)

        if (allExpenses.isEmpty() && allIncomes.isEmpty() && currentBalance == BigDecimal.ZERO) {
            withContext(Dispatchers.Main) {
                chatHistory.add("AI: У вас немає фінансових даних. Додайте записи про витрати чи доходи!")
            }
            return
        }

        val isMonthSpecific = isMonthSpecificQuery(input)
        val isComparison = isComparisonQuery(input)

        val expensesToSend: List<Expense>
        val incomesToSend: List<Income>
        val totalExpenses: BigDecimal
        val totalIncomes: BigDecimal

        if (isMonthSpecific && !isComparison) {
            val (targetMonth, targetYear) = parseMonthAndYearFromInput(input)
            val dateFormat = SimpleDateFormat("dd.MM.yyyy", Locale.getDefault())
            expensesToSend = allExpenses.filter { expense ->
                try {
                    val expenseDate = dateFormat.parse(expense.date)
                    val expenseCalendar = Calendar.getInstance().apply { time = expenseDate }
                    val expenseMonth = expenseCalendar.get(Calendar.MONTH) + 1
                    val expenseYear = expenseCalendar.get(Calendar.YEAR)
                    expenseMonth == targetMonth && expenseYear == targetYear
                } catch (e: Exception) {
                    false
                }
            }
            incomesToSend = allIncomes.filter { income ->
                try {
                    val incomeDate = dateFormat.parse(income.date)
                    val incomeCalendar = Calendar.getInstance().apply { time = incomeDate }
                    val incomeMonth = incomeCalendar.get(Calendar.MONTH) + 1
                    val incomeYear = incomeCalendar.get(Calendar.YEAR)
                    incomeMonth == targetMonth && incomeYear == targetYear
                } catch (e: Exception) {
                    false
                }
            }
            totalExpenses = expensesToSend.sumOf { it.amount }
            totalIncomes = incomesToSend.sumOf { it.amount }
        } else {
            expensesToSend = allExpenses
            incomesToSend = allIncomes
            totalExpenses = allExpenses.sumOf { it.amount }
            totalIncomes = allIncomes.sumOf { it.amount }
        }

        // Аналіз трендів за останні 3 місяці
        val dateFormat = SimpleDateFormat("dd.MM.yyyy", Locale.getDefault())
        val currentDate = Calendar.getInstance()
        val threeMonthsAgo = Calendar.getInstance().apply { add(Calendar.MONTH, -3) }
        val recentExpenses = allExpenses.filter { expense ->
            val expenseDate = dateFormat.parse(expense.date) ?: return@filter false
            val expenseCal = Calendar.getInstance().apply { time = expenseDate }
            expenseCal.after(threeMonthsAgo) && expenseCal.before(currentDate)
        }
        val expensesByMonth = recentExpenses.groupBy { expense ->
            val expenseDate = dateFormat.parse(expense.date) ?: return@groupBy "Unknown"
            val cal = Calendar.getInstance().apply { time = expenseDate }
            "${cal.get(Calendar.MONTH) + 1}.${cal.get(Calendar.YEAR)}"
        }
        val monthlyTotals = expensesByMonth.mapValues { entry ->
            entry.value.fold(BigDecimal.ZERO) { sum, expense -> sum + expense.amount }
        }

        val inputLower = input.trim().lowercase()
        when {
            inputLower.contains("привіт") || inputLower.contains("добрий день") -> {
                withContext(Dispatchers.Main) {
                    chatHistory.add("AI: Привіт! Як я можу допомогти з твоїми фінансами?")
                }
                return
            }
            inputLower.contains("пока") || inputLower.contains("до побачення") -> {
                withContext(Dispatchers.Main) {
                    chatHistory.add("AI: До побачення! Звертайся, якщо потрібна допомога!")
                }
                return
            }
            inputLower.contains("дякую") || inputLower.contains("спасибі") -> {
                withContext(Dispatchers.Main) {
                    chatHistory.add("AI: Завжди радий допомогти! Пиши, якщо будуть ще питання.")
                }
                return
            }
        }

        val userData = mapOf(
            "current_balance" to currentBalance.toString(),
            "total_expenses" to totalExpenses.toString(),
            "total_incomes" to totalIncomes.toString(),
            "expenses" to expensesToSend,
            "incomes" to incomesToSend,
            "monthly_expense_trends" to monthlyTotals
        )
        val userDataJson = Gson().toJson(userData)

        val systemPrompt = """
            You are a financial assistant helping the user manage their finances.
            User data: $userDataJson
            The "total_expenses" and "total_incomes" fields are pre-calculated sums for the provided data.
            The "monthly_expense_trends" field shows expenses grouped by month (e.g., "4.2025": 5000).

            ### Instructions for handling queries:
            - When the user asks about expenses for a specific month (e.g., "які мої витрати за лютий?"):
              1. Use the "expenses" list (already filtered for the specified month and year).
              2. List each expense with its date, amount, and category in the format: " - <date>: ₴<amount> (<category>)".
              3. Use the "total_expenses" field for the total.
              4. Respond in the format: "Ваші витрати за <month> <year>:\n<list of expenses>\nЗагальна сума: ₴<total>."

            - When the user asks about incomes for a specific month (e.g., "які доходи за лютий?"):
              1. Use the "incomes" list (already filtered for the specified month and year).
              2. List each income with its date, amount, and source in the format: " - <date>: ₴<amount> (<source>)".
              3. Use the "total_incomes" field for the total.
              4. Respond in the format: "Ваші доходи за <month> <year>:\n<list of incomes>\nЗагальна сума: ₴<total>."

            - When the user asks about the month with the highest/lowest expenses (e.g., "в якому місяці я витратив найбільше?"):
              1. Use the full "expenses" list.
              2. Group expenses by month and year (based on the "date" field, format "dd.MM.yyyy").
              3. Calculate the total for each month.
              4. Identify the month with the highest (or lowest) total expenses.
              5. Respond in the format: "Найбільше витрат було за <month> <year> - ₴<total>."

            - When the user asks about the largest expense category (e.g., "Яка категорія витрат у мене найбільша?"):
              1. Use the full "expenses" list.
              2. Group expenses by category.
              3. Calculate the total for each category.
              4. Identify the category with the highest total expenses.
              5. Respond in the format: "Найбільша категорія витрат у вас - це \"<category>\", де витрати склали ₴<total>."

            - When the user asks about average daily expenses for the current month (e.g., "Яка у мене середня витрата за день цього місяця?"):
              1. Filter the expenses for the current month and year (current date: ${SimpleDateFormat("dd.MM.yyyy", Locale.getDefault()).format(Date())}).
              2. Calculate the total expenses for the current month.
              3. Determine the number of days in the current month.
              4. Calculate the average daily expense.
              5. Respond in the format: "Ваша середня витрата за день цього місяця (<month> <year>) становить ₴<average>."

            - When the user asks if they can afford a purchase (e.g., "Чи вистачить мені грошей на покупку за 5000 гривень?"):
              1. Extract the amount from the query (e.g., 5000).
              2. Compare the amount with the "current_balance".
              3. Respond in the format: "Так, ви можете дозволити покупку за ₴<amount>. Ваш поточний баланс: ₴<current_balance>." or "Ні, вам не вистачить грошей на покупку за ₴<amount>. Ваш поточний баланс: ₴<current_balance>."

            - When the user asks how much they can save this month (e.g., "Скільки я можу відкласти цього місяця?"):
              1. Filter the incomes and expenses for the current month.
              2. Calculate the difference: total incomes - total expenses.
              3. If the difference is positive, suggest saving that amount.
              4. Respond in the format: "Ви можете відкласти ₴<amount> цього місяця." or "Цього місяця у вас немає можливості відкласти кошти, оскільки витрати перевищують доходи."

            - When the user asks about trends (e.g., "Який у мене фінансовий тренд за останні 3 місяці?"):
              1. Use the "monthly_expense_trends" field.
              2. Analyze the trend (e.g., increasing, decreasing, or stable).
              3. Respond in the format: "Ваші витрати за останні 3 місяці <trend>.\n<month>: ₴<amount>\n<month>: ₴<amount>\n<month>: ₴<amount>."

            - When the user asks about unnecessary expenses (e.g., "Чи є у мене зайві витрати?"):
              1. Identify categories with unusually high spending (e.g., top 2 categories).
              2. Suggest reducing spending in those categories.
              3. Respond in the format: "Ви можете скоротити витрати в категорії \"<category>\", де витрати склали ₴<amount>."

            - For other financial queries, use the provided data to respond accurately.
            - If the question is not related to finances, respond as a general AI without mentioning financial data unless explicitly asked.

            ### General rules:
            - Respond only to the user's question.
            - Keep responses concise and focused.
            - Respond in Ukrainian.
            - Use Ukrainian month names (e.g., "лютий" for February).
            - Current date: ${SimpleDateFormat("dd.MM.yyyy", Locale.getDefault()).format(Date())}
        """.trimIndent()

        val apiKey = BuildConfig.GROQ_API_KEY
        if (apiKey.isBlank()) {
            withContext(Dispatchers.Main) {
                chatHistory.add("AI: Помилка: API-ключ відсутній.")
            }
            return
        }

        val client = OkHttpClient.Builder()
            .connectTimeout(60, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .build()

        val requestBody = JSONObject().apply {
            put("model", "llama3-8b-8192")
            put("messages", JSONArray().apply {
                put(JSONObject().apply {
                    put("role", "system")
                    put("content", systemPrompt)
                })
                chatHistory.forEach { message ->
                    if (message.startsWith("Ви: ")) {
                        put(JSONObject().apply {
                            put("role", "user")
                            put("content", message.removePrefix("Ви: "))
                        })
                    } else if (message.startsWith("AI: ")) {
                        put(JSONObject().apply {
                            put("role", "assistant")
                            put("content", message.removePrefix("AI: "))
                        })
                    }
                }
                put(JSONObject().apply {
                    put("role", "user")
                    put("content", input)
                })
            })
            put("max_tokens", 300)
            put("temperature", 0.5)
        }.toString()

        val request = Request.Builder()
            .url("https://api.groq.com/openai/v1/chat/completions")
            .header("Authorization", "Bearer $apiKey")
            .post(requestBody.toRequestBody("application/json".toMediaType()))
            .build()

        var response: okhttp3.Response? = null
        val maxRetries = 5
        var attempt = 0
        while (attempt < maxRetries && response == null) {
            attempt++
            try {
                response = withContext(Dispatchers.IO) {
                    client.newCall(request).execute()
                }
            } catch (e: Exception) {
                if (attempt == maxRetries) throw e
                delay(3000L)
            }
        }

        if (response != null && response.isSuccessful) {
            val responseBody = response.body?.string()
            val json = JSONObject(responseBody)
            val aiResponse = json.getJSONArray("choices")
                .getJSONObject(0)
                .getJSONObject("message")
                .getString("content")
            withContext(Dispatchers.Main) {
                chatHistory.add("AI: $aiResponse")
            }
        } else {
            withContext(Dispatchers.Main) {
                chatHistory.add("AI: Не вдалося обробити запит. Спробуйте ще раз.")
            }
        }
    } catch (e: Exception) {
        withContext(Dispatchers.Main) {
            chatHistory.add("AI: Виникла помилка. Спробуйте ще раз.")
        }
    } finally {
        isLoading.value = false
    }
}