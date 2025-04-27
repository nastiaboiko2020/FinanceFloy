package com.example.financeflow

import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
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
import com.example.financeflow.ui.theme.Theme
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

// Імпортуємо функції з Utils.kt
import com.example.financeflow.loadExpenses
import com.example.financeflow.loadIncomes
// Імпортуємо функції для повної історії з HomeActivity.kt
import com.example.financeflow.loadAllExpenses
import com.example.financeflow.loadAllIncomes

class AIChatActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            Theme(useAIChatTheme = true) {
                AIChatScreen(this) { finish() }
            }
        }
    }
}

@Composable
fun AIChatScreen(context: Context, onBackPressed: () -> Unit) {
    val sharedPreferences = context.getSharedPreferences("finance_flow_prefs", Context.MODE_PRIVATE)
    val editor = sharedPreferences.edit()

    // Завантажуємо історію чату з SharedPreferences
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

    // Скорочений список із 5 найцікавіших запитань
    val predefinedQuestions = listOf(
        "Які мої найбільші витрати?",
        "Чи можу я заощадити цього місяця?",
        "Скільки у мене зараз грошей?",
        "Які мої доходи цього місяця?",
        "На чому мені слід економити, щоб відкладати 3000 гривень?"
    )

    // Зберігаємо історію чату при кожній зміні
    LaunchedEffect(chatHistory) {
        val chatHistoryJson = Gson().toJson(chatHistory)
        editor.putString("chat_history", chatHistoryJson)
        editor.apply()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(150.dp)
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(MaterialTheme.colorScheme.primary, Color(0xFF2E5B8C))
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
                    color = MaterialTheme.colorScheme.onPrimary,
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
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
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
                                color = if (message.startsWith("AI:")) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
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

            // Оновлене оформлення підготовлених запитань
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 120.dp)
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                items(predefinedQuestions) { question ->
                    val interactionSource = remember { MutableInteractionSource() }
                    val isPressed by interactionSource.collectIsPressedAsState()
                    val backgroundColor = if (isPressed) {
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                    } else {
                        MaterialTheme.colorScheme.surface.copy(alpha = 0.5f)
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
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = question,
                            style = TextStyle(
                                color = MaterialTheme.colorScheme.primary,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium
                            )
                        )
                    }
                }
            }

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .shadow(4.dp, RoundedCornerShape(12.dp)),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
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
                            focusedIndicatorColor = MaterialTheme.colorScheme.secondary,
                            unfocusedIndicatorColor = Color.Gray,
                            cursorColor = MaterialTheme.colorScheme.primary
                        )
                    )
                    IconButton(
                        onClick = {
                            if (userInput.isNotBlank()) {
                                val inputToSend = userInput
                                Log.d("AIChatActivity", "Натиснуто кнопку, відправляємо: '$inputToSend'")
                                chatHistory.add("Ви: $inputToSend")
                                coroutineScope.launch {
                                    handleUserInput(inputToSend, chatHistory, isLoading, sharedPreferences, context)
                                }
                                userInput = ""
                            } else {
                                Log.d("AIChatActivity", "userInput порожній, нічого не відправлено")
                            }
                        },
                        modifier = Modifier
                            .size(48.dp)
                            .background(
                                color = MaterialTheme.colorScheme.secondary,
                                shape = RoundedCornerShape(50)
                            )
                    ) {
                        Icon(
                            imageVector = Icons.Default.Send,
                            contentDescription = "Відправити",
                            tint = MaterialTheme.colorScheme.onSecondary
                        )
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
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                shape = RoundedCornerShape(12.dp),
                elevation = ButtonDefaults.buttonElevation(defaultElevation = 4.dp)
            ) {
                Text(
                    text = "Назад",
                    color = MaterialTheme.colorScheme.onSecondary,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

// Функція для розпізнавання місяця і року з тексту запиту
fun parseMonthAndYearFromInput(input: String): Pair<Int, Int> {
    val inputLower = input.lowercase()
    val currentDate = Calendar.getInstance()
    val currentYear = currentDate.get(Calendar.YEAR)
    var targetMonth = -1
    var targetYear = currentYear // За замовчуванням — поточний рік

    // Мапа місяців (українською мовою)
    val monthMap = mapOf(
        "січень" to 1, "січня" to 1,
        "лютий" to 2, "лютого" to 2,
        "березень" to 3, "березня" to 3,
        "квітень" to 4, "квітня" to 4,
        "травень" to 5, "травня" to 5,
        "червень" to 6, "червня" to 6,
        "липень" to 7, "липня" to 7,
        "серпень" to 8, "серпня" to 8,
        "вересень" to 9, "вересня" to 9,
        "жовтень" to 10, "жовтня" to 10,
        "листопад" to 11, "листопада" to 11,
        "грудень" to 12, "грудня" to 12
    )

    // Шукаємо місяць у запиті
    for ((monthName, monthNumber) in monthMap) {
        if (inputLower.contains(monthName)) {
            targetMonth = monthNumber
            break
        }
    }

    // Шукаємо рік у запиті (наприклад, "2023", "2024")
    val yearMatch = Regex("\\b(20\\d{2})\\b").find(inputLower)
    if (yearMatch != null) {
        targetYear = yearMatch.value.toInt()
    }

    // Якщо місяць не знайдено, використовуємо поточний місяць
    if (targetMonth == -1) {
        targetMonth = currentDate.get(Calendar.MONTH) + 1
    }

    Log.d("AIChatActivity", "Розпізнано місяць: $targetMonth, рік: $targetYear")
    return Pair(targetMonth, targetYear)
}

// Функція для визначення, чи запит стосується конкретного місяця
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

// Функція для визначення, чи запит стосується порівняння місяців
fun isComparisonQuery(input: String): Boolean {
    val inputLower = input.lowercase()
    return inputLower.contains("найбільше") || inputLower.contains("найменше") || inputLower.contains("в якому місяці")
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
        Log.d("AIChatActivity", "Вхідний запит: '$input'")

        // Завантажуємо дані щоразу перед обробкою запиту
        val allExpenses = loadAllExpenses(sharedPreferences) // Повна історія витрат
        val allIncomes = loadAllIncomes(sharedPreferences) // Повна історія доходів
        val currentBalanceStr = sharedPreferences.getString("current_balance", "0.00") ?: "0.00"
        val currentBalance = BigDecimal(currentBalanceStr)

        Log.d("AIChatActivity", "Завантажено витрат (повна історія): ${allExpenses.size}, дані: ${allExpenses.joinToString()}")
        Log.d("AIChatActivity", "Завантажено доходів (повна історія): ${allIncomes.size}, дані: ${allIncomes.joinToString()}")
        Log.d("AIChatActivity", "Поточний баланс: ₴$currentBalance")

        // Визначаємо, чи запит стосується конкретного місяця
        val isMonthSpecific = isMonthSpecificQuery(input)
        val isComparison = isComparisonQuery(input)

        // Якщо запит стосується конкретного місяця, фільтруємо дані
        val expensesToSend: List<Expense>
        val incomesToSend: List<Income>
        val totalExpenses: BigDecimal
        val totalIncomes: BigDecimal

        if (isMonthSpecific && !isComparison) {
            // Розпізнаємо місяць і рік із запиту
            val (targetMonth, targetYear) = parseMonthAndYearFromInput(input)

            // Фільтруємо витрати за вказаним місяцем і роком
            val dateFormat = SimpleDateFormat("dd.MM.yyyy", Locale.getDefault())
            expensesToSend = allExpenses.filter { expense ->
                try {
                    val expenseDate = dateFormat.parse(expense.date)
                    val expenseCalendar = Calendar.getInstance().apply { time = expenseDate }
                    val expenseMonth = expenseCalendar.get(Calendar.MONTH) + 1
                    val expenseYear = expenseCalendar.get(Calendar.YEAR)
                    expenseMonth == targetMonth && expenseYear == targetYear
                } catch (e: Exception) {
                    Log.e("AIChatActivity", "Помилка парсингу дати витрати: ${expense.date}, помилка: ${e.message}")
                    false
                }
            }
            Log.d("AIChatActivity", "Витрати за місяць $targetMonth.$targetYear: ${expensesToSend.joinToString()}")

            // Фільтруємо доходи за вказаним місяцем і роком
            incomesToSend = allIncomes.filter { income ->
                try {
                    val incomeDate = dateFormat.parse(income.date)
                    val incomeCalendar = Calendar.getInstance().apply { time = incomeDate }
                    val incomeMonth = incomeCalendar.get(Calendar.MONTH) + 1
                    val incomeYear = incomeCalendar.get(Calendar.YEAR)
                    incomeMonth == targetMonth && incomeYear == targetYear
                } catch (e: Exception) {
                    Log.e("AIChatActivity", "Помилка парсингу дати доходу: ${income.date}, помилка: ${e.message}")
                    false
                }
            }
            Log.d("AIChatActivity", "Доходи за місяць $targetMonth.$targetYear: ${incomesToSend.joinToString()}")

            totalExpenses = expensesToSend.fold(BigDecimal.ZERO) { sum, expense -> sum + expense.amount }
            totalIncomes = incomesToSend.fold(BigDecimal.ZERO) { sum, income -> sum + income.amount }
        } else {
            // Для запитів, які не стосуються конкретного місяця (наприклад, порівняння), передаємо повну історію
            expensesToSend = allExpenses
            incomesToSend = allIncomes
            totalExpenses = allExpenses.fold(BigDecimal.ZERO) { sum, expense -> sum + expense.amount }
            totalIncomes = allIncomes.fold(BigDecimal.ZERO) { sum, income -> sum + income.amount }
        }

        val inputLower = input.trim().lowercase()

        // Локальна обробка базових привітань і прощань
        when {
            inputLower.contains("привіт") || inputLower.contains("добрий день") || inputLower.contains("здоров") -> {
                val response = "Привіт! Я твій фінансовий асистент. Як я можу допомогти?"
                withContext(Dispatchers.Main) {
                    chatHistory.add("AI: $response")
                }
                return
            }
            inputLower.contains("пока") || inputLower.contains("до побачення") || inputLower.contains("бувай") -> {
                val response = "До побачення! Якщо потрібна допомога з фінансами, повертайся!"
                withContext(Dispatchers.Main) {
                    chatHistory.add("AI: $response")
                }
                return
            }
            inputLower.contains("дякую") || inputLower.contains("спасибі") -> {
                val response = "Радий допомогти! Якщо є ще питання, пиши!"
                withContext(Dispatchers.Main) {
                    chatHistory.add("AI: $response")
                }
                return
            }
        }

        // Якщо немає даних, попереджаємо користувача
        if (allExpenses.isEmpty() && allIncomes.isEmpty() && currentBalance == BigDecimal.ZERO) {
            withContext(Dispatchers.Main) {
                chatHistory.add("AI: У мене немає даних про ваші витрати, доходи чи кошти. Але я можу відповісти на інші питання!")
            }
            return
        }

        // Формуємо агреговані дані для передачі в API
        val userData = mapOf(
            "current_balance" to currentBalance.toString(),
            "total_expenses" to totalExpenses.toString(),
            "total_incomes" to totalIncomes.toString(),
            "expenses" to expensesToSend,
            "incomes" to incomesToSend
        )
        val userDataJson = Gson().toJson(userData)
        Log.d("AIChatActivity", "Дані користувача для API: $userDataJson")

        // Оновлений системний промпт із інструкціями для порівняння
        val monthName = if (isMonthSpecific) {
            mapOf(
                1 to "січень", 2 to "лютий", 3 to "березень", 4 to "квітень",
                5 to "травень", 6 to "червень", 7 to "липень", 8 to "серпень",
                9 to "вересень", 10 to "жовтень", 11 to "листопад", 12 to "грудень"
            )[parseMonthAndYearFromInput(input).first] ?: "невідомий місяць"
        } else {
            "невідомий місяць"
        }

        val systemPrompt = """
            You are a financial assistant helping the user manage their finances. 
            You have access to the user's data: expenses, incomes, and current balance.
            User data: $userDataJson
            The "total_expenses" and "total_incomes" fields represent the pre-calculated sum of all expenses and incomes for the data provided.

            ### Instructions for handling queries:
            - When the user asks about expenses for a specific month (e.g., "які мої витрати за лютий?"):
              1. The data is already filtered for the specified month and year.
              2. **Always consider ALL records** in the "expenses" list.
              3. List each expense with its date, amount, and category in the format: " - <date>: ₴<amount> (<category>)".
              4. Calculate the total by summing the "amount" of all expenses in the "expenses" list.
              5. Alternatively, you can directly use the "total_expenses" field from the user data, which is the pre-calculated total.
              6. Respond with the detailed list of expenses followed by the total in the format: "Ваші витрати за <month> <year>:\n<list of expenses>\nЗагальна сума: ₴<total>."
              Example: If the user asks "які мої витрати за лютий?" and the data has two expenses, respond with:
              "Ваші витрати за лютий 2025:\n - 01.02.2025: ₴500 (Харчування)\n - 02.02.2025: ₴1000 (Одяг)\nЗагальна сума: ₴1500."

            - When the user asks about incomes for a specific month (e.g., "які доходи за лютий?"):
              1. The data is already filtered for the specified month and year.
              2. **Always consider ALL records** in the "incomes" list.
              3. List each income with its date, amount, and source in the format: " - <date>: ₴<amount> (<source>)".
              4. Calculate the total by summing the "amount" of all incomes in the "incomes" list.
              5. Alternatively, you can use the "total_incomes" field from the user data.
              6. Respond with the detailed list of incomes followed by the total in the format: "Ваші доходи за <month> <year>:\n<list of incomes>\nЗагальна сума: ₴<total>."

            - When the user asks about the month with the highest/lowest expenses (e.g., "в якому місяці я зробила найбільше витрат?"):
              1. The "expenses" list contains the full history of expenses (not filtered by month).
              2. Group the expenses by month and year (based on the "date" field in the format "dd.MM.yyyy").
              3. Calculate the total expenses for each month by summing the "amount" of all expenses in that month.
              4. Identify the month with the highest (or lowest, depending on the query) total expenses.
              5. Respond in the format: "Найбільше витрат було за <month> <year> - ₴<total>."
              Example: If the expenses are [{"amount": 500, "date": "01.01.2025"}, {"amount": 1000, "date": "01.02.2025"}, {"amount": 2000, "date": "02.02.2025"}], respond with:
              "Найбільше витрат було за лютий 2025 - ₴3000."

            - For other financial queries (e.g., "Can I afford a 5000 UAH purchase?"), use the provided data to respond accurately.
            - If the question is not related to finances, respond as a general AI, keeping the conversation engaging, but do not mention the user's financial data unless explicitly asked.

            ### General rules:
            - Respond **only** to the user's question. Do not provide additional information or answer other questions.
            - Keep your response concise, clear, and focused on the question asked.
            - Respond in the same language as the user's input (Ukrainian in this case).
            - Use the month name in Ukrainian (e.g., "лютий" for February) and the year from the user's query.
            - Current date: ${SimpleDateFormat("dd.MM.yyyy", Locale.getDefault()).format(Date())}
        """.trimIndent()
        Log.d("AIChatActivity", "Системний промпт: $systemPrompt")

        // Передаємо повну історію чату
        Log.d("AIChatActivity", "Повна історія чату: ${chatHistory.joinToString("\n")}")

        // Відправляємо запит до Groq API
        val apiKey = BuildConfig.GROQ_API_KEY
        Log.d("AIChatActivity", "API-ключ: $apiKey (довжина: ${apiKey.length} символів)")
        if (apiKey.isBlank()) {
            withContext(Dispatchers.Main) {
                chatHistory.add("AI: Помилка: API-ключ порожній. Перевірте налаштування.")
            }
            return
        }

        // Використовуємо Groq API
        val url = "https://api.groq.com/openai/v1/chat/completions"
        Log.d("AIChatActivity", "URL для запиту: $url")

        // Налаштування OkHttpClient з більшим тайм-аутом
        val client = OkHttpClient.Builder()
            .connectTimeout(60, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .build()

        // Формуємо запит у форматі Groq API (сумісний із OpenAI API)
        val requestBody = JSONObject().apply {
            put("model", "llama3-8b-8192") // Використовуємо модель Llama 3
            put("messages", JSONArray().apply {
                put(JSONObject().apply {
                    put("role", "system")
                    put("content", systemPrompt)
                })
                // Додаємо повну історію чату
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
                // Додаємо поточний запит користувача
                put(JSONObject().apply {
                    put("role", "user")
                    put("content", input)
                })
            })
            put("max_tokens", 300)
            put("temperature", 0.5) // Зменшуємо для більшої точності
        }.toString()
        Log.d("AIChatActivity", "Тіло запиту до API: $requestBody")

        val request = Request.Builder()
            .url(url)
            .header("Authorization", "Bearer $apiKey")
            .post(requestBody.toRequestBody("application/json".toMediaType()))
            .build()

        // Механізм повторних спроб
        var response: okhttp3.Response? = null
        val maxRetries = 5
        var attempt = 0
        val startTime = System.currentTimeMillis()
        while (attempt < maxRetries && response == null) {
            attempt++
            Log.d("AIChatActivity", "Спроба $attempt з $maxRetries")
            try {
                response = withContext(Dispatchers.IO) {
                    client.newCall(request).execute()
                }
                val elapsedTime = System.currentTimeMillis() - startTime
                Log.d("AIChatActivity", "Час виконання запиту: $elapsedTime мс")
            } catch (e: Exception) {
                Log.e("AIChatActivity", "Помилка на спробі $attempt: ${e.message}")
                if (attempt < maxRetries) {
                    delay(3000L) // Затримка 3 секунди перед наступною спробою
                } else {
                    throw e // Якщо всі спроби вичерпані, кидаємо помилку
                }
            }
        }

        if (response != null && response.isSuccessful) {
            val responseBody = response.body?.string()
            Log.d("AIChatActivity", "Відповідь від API: $responseBody")
            val json = JSONObject(responseBody)
            val aiResponse = json.getJSONArray("choices")
                .getJSONObject(0)
                .getJSONObject("message")
                .getString("content")

            // Перевіряємо, чи запит стосується фінансів
            val isFinanceQuery = isFinanceRelatedQuery(input)
            // Фільтруємо відповідь
            val filteredResponse = filterResponse(aiResponse, isFinanceQuery, input)

            withContext(Dispatchers.Main) {
                chatHistory.add("AI: $filteredResponse")
            }
        } else {
            val errorBody = response?.body?.string()
            Log.e("AIChatActivity", "Помилка API: Код: ${response?.code}, Повідомлення: ${response?.message}")
            Log.e("AIChatActivity", "Тіло помилки: $errorBody")
            withContext(Dispatchers.Main) {
                chatHistory.add("AI: Виникла помилка при зверненні до сервера: ${response?.code} - ${response?.message}")
                chatHistory.add("AI: Додаткова інформація: $errorBody")
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

// Функція для визначення, чи запит стосується фінансів
fun isFinanceRelatedQuery(input: String): Boolean {
    val financeKeywords = listOf(
        "витрати", "гроші", "баланс", "заощадити", "економити", "витратив", "витратила",
        "скільки", "покупка", "категорії", "днів", "середній", "найдорожча", "доходи", "заробила", "заробив"
    )
    val inputLower = input.lowercase()
    return financeKeywords.any { inputLower.contains(it) }
}

// Функція для фільтрації відповіді
fun filterResponse(response: String, isFinanceQuery: Boolean, input: String): String {
    // Для фінансових запитів повертаємо всю відповідь
    if (isFinanceQuery) {
        return response.trim()
    }

    // Для нефінансових запитів обрізаємо до першого абзацу
    val firstParagraph = response.split("\n").firstOrNull()?.trim() ?: response.trim()
    val lines = firstParagraph.split("\n")
    val filteredLines = lines.filterNot { line ->
        line.contains("гривень", ignoreCase = true) ||
                line.contains("витрати", ignoreCase = true) ||
                line.contains("доходи", ignoreCase = true) ||
                line.contains("баланс", ignoreCase = true) ||
                line.contains("обліковому записі", ignoreCase = true) ||
                line.contains("залишив", ignoreCase = true) ||
                line.matches(Regex(".*\\d+\\.\\d+.*"))
    }
    return filteredLines.joinToString("\n").trim()
}