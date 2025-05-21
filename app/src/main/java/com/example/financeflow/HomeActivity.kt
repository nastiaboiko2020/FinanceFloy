package com.example.financeflow

import android.Manifest
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
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.ArrowDropUp
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.rememberScrollState
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModelProvider
import com.example.financeflow.ui.theme.FinanceFlowTheme
import com.example.financeflow.viewmodel.Expense
import com.example.financeflow.viewmodel.ExpenseViewModel
import com.example.financeflow.viewmodel.Income
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.*
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import java.math.BigDecimal
import java.text.SimpleDateFormat
import java.util.*
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Chat

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

        val incomes = loadIncomes(sharedPreferences).toMutableList()
        val unwantedSources = listOf("Зарплата", "Фріланс", "Подарунок")
        val filteredIncomes = incomes.filter { income -> !unwantedSources.contains(income.source) }.toMutableList()
        saveIncomes(filteredIncomes, sharedPreferences)
        Log.d("HomeActivity", "Видалено тестові доходи: $unwantedSources. Залишилося доходів: $filteredIncomes")

        val expenses = loadExpenses(sharedPreferences)
        val savedBalanceStr = sharedPreferences.getString("current_balance", "0.00") ?: "0.00"
        val savedBalance = BigDecimal(savedBalanceStr)
        Log.d("HomeActivity", "Завантажено збережений баланс: $savedBalance")

        expenseViewModel.setBalance(savedBalance)
        expenseViewModel.setExpenses(expenses.toMutableList())

        setContent {
            FinanceFlowTheme {
                HomeScreen(expenseViewModel, sharedPreferences, this)
            }
        }
    }

    private fun processImage(bitmap: Bitmap) {
        val image = InputImage.fromBitmap(bitmap, 0)
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        recognizer.process(image)
            .addOnSuccessListener { visionText ->
                Log.d("HomeActivity", "Розпізнаний текст: ${visionText.text}")
                val amount = extractAmountFromText(visionText)
                onImageProcessed?.invoke(amount)
            }
            .addOnFailureListener { e ->
                onImageProcessed?.invoke("0.00") // Повертаємо "0.00" замість "0.0" для консистентності
                android.widget.Toast.makeText(this, "Не вдалося розпізнати суму: ${e.message}", android.widget.Toast.LENGTH_SHORT).show()
            }
    }
    private fun processImageFromUri(uri: Uri) {
        try {
            val bitmap = MediaStore.Images.Media.getBitmap(contentResolver, uri)
            processImage(bitmap)
        } catch (e: Exception) {
            onImageProcessed?.invoke("0.00") // Повертаємо "0.00" для консистентності
            android.widget.Toast.makeText(this, "Помилка завантаження зображення: ${e.message}", android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    private fun extractAmountFromText(visionText: Text): String {
        Log.d("HomeActivity", "Розпізнаний текст: ${visionText.text}")

        // Ключові слова для "До сплати" (перший пріоритет)
        val primaryKeywords = listOf("До сплати", "Ao cnnaTu", "To pay")

        // Ключові слова для "Сума" (другий пріоритет)
        val secondaryKeywords = listOf("Сума", "СУМА ГРН", "СУМА, ГРН", "CYMA", "Cyna", "cYMA")

        // Список слів для виключення (готівка, решта, знижки тощо)
        val excludeKeywords = listOf(
            "Готівка", "Готівкою", "Здача", "Решта", "Change", "Cash", "ЗНИЖКА", "Знижка", "Discount",
            "ЗАОЩАДЖЕННЯ", "Залишок", "Решта грн", "Решта, грн", "Знижка грн", "Знижка, грн", "rOTIBKA", "PEWTA", "BeenRA"
        )

        // Список для зберігання потенційних сум
        data class AmountCandidate(val amount: Double, val xPosition: Int, val yPosition: Int, val source: String, val hasCurrency: Boolean, val yDistance: Int)

        val candidates = mutableListOf<AmountCandidate>()

        // Регулярний вираз для виключення дат у форматі dd.mm.yyyy або dd-mm-yyyy
        val dateRegex = Regex("""\b\d{2}[.-]\d{2}[.-]\d{4}\b""")

        // Регулярний вираз для виключення відсотків (наприклад, ПДВ A 20.00%)
        val percentageRegex = Regex("""(?:ПДВ|ngB|nAB)\s*[А-ЯA-Z-]*\s*[-=]\s*\d+[,.]\d{1,2}%""", RegexOption.IGNORE_CASE)

        // Допоміжна функція для перевірки, чи число є частиною дати
        fun isPartOfDate(amountStr: String, text: String, position: Int): Boolean {
            val window = text.substring(maxOf(0, position - 10), minOf(text.length, position + 15))
            return dateRegex.containsMatchIn(window)
        }

        // Допоміжна функція для перевірки, чи число є частиною відсотка
        fun isPartOfPercentage(amountStr: String, text: String, position: Int): Boolean {
            val window = text.substring(maxOf(0, position - 20), minOf(text.length, position + 20))
            return percentageRegex.containsMatchIn(window) && window.contains(amountStr)
        }

        // Допоміжна функція для округлення до двох знаків після коми
        fun roundToTwoDecimals(value: Double): Double {
            return (value * 100).toLong().toDouble() / 100
        }

        // Допоміжна функція для форматування числа з двома знаками після коми
        fun formatToTwoDecimals(value: Double): String {
            return String.format("%.2f", value)
        }

        // Функція для пошуку суми в рядку або після ключового слова
        fun findAmount(keywords: List<String>, searchNextLines: Boolean = false): Boolean {
            val amountRegex = Regex("""(\d+[,.]\d{2})\s*(?:грн|UAH|rpH\.|TPH)?""", RegexOption.IGNORE_CASE)

            // Спочатку шукаємо суму в тому ж рядку
            for (block in visionText.textBlocks) {
                for (line in block.lines) {
                    val lineText = line.text
                    if (keywords.any { keyword -> lineText.contains(keyword, ignoreCase = true) }) {
                        Log.d("HomeActivity", "Знайдено ключове слово в рядку: $lineText")
                        val amountMatch = amountRegex.find(lineText)
                        if (amountMatch != null) {
                            val amountStr = amountMatch.groupValues[1].replace(",", ".")
                            val num = amountStr.toDoubleOrNull() ?: continue
                            val digitCount = amountStr.replace(".", "").length
                            val position = visionText.text.indexOf(amountMatch.value)
                            val isNotYear = num !in 1900.0..2099.0
                            val isNotMultiplier = !visionText.text.contains(Regex("""\b${amountMatch.groupValues[1]}\b\s*[xX]""")) &&
                                    !visionText.text.contains(Regex("""\d+\s*[xX]\s*${amountMatch.groupValues[1]}\b"""))
                            val isNotDate = !isPartOfDate(amountStr, visionText.text, position)
                            val isNotPercentage = !isPartOfPercentage(amountStr, visionText.text, position)

                            val isNotExcluded = excludeKeywords.none { keyword ->
                                val regex = Regex("""${keyword}\s*[:=]?\s*${amountMatch.groupValues[1]}""", RegexOption.IGNORE_CASE)
                                regex.containsMatchIn(visionText.text)
                            }

                            val hasCurrency = amountMatch.value.contains(Regex("""грн|UAH|rpH\.|TPH""", RegexOption.IGNORE_CASE))

                            if (digitCount <= 8 && num in 1.0..1_000_000.0 && isNotYear && isNotMultiplier && isNotDate && isNotPercentage && isNotExcluded) {
                                val roundedNum = roundToTwoDecimals(num)
                                candidates.add(AmountCandidate(roundedNum, position, 0, "In same line: ${amountMatch.value}", hasCurrency, 0))
                                Log.d("HomeActivity", "Знайдено суму в тому ж рядку: $roundedNum (позиція: $position, має валюту: $hasCurrency)")
                            }
                        }
                    }
                }
            }

            // Якщо не знайдено в тому ж рядку і дозволено шукати в наступних, шукаємо з координатами
            if (searchNextLines) {
                var boundingBoxAvailable = false
                for (block in visionText.textBlocks) {
                    for (line in block.lines) {
                        val lineText = line.text
                        if (keywords.any { keyword -> lineText.contains(keyword, ignoreCase = true) }) {
                            val keywordElement = line.elements.firstOrNull { element ->
                                keywords.any { keyword -> element.text.contains(keyword, ignoreCase = true) }
                            } ?: continue

                            val keywordBoundingBox = keywordElement.boundingBox
                            if (keywordBoundingBox == null) {
                                Log.d("HomeActivity", "boundingBox для ключового слова '${keywordElement.text}' недоступний (null)")
                                continue
                            }

                            boundingBoxAvailable = true
                            val keywordX = keywordBoundingBox.left
                            val keywordY = keywordBoundingBox.top
                            Log.d("HomeActivity", "Знайдено ключове слово '${keywordElement.text}' на позиції (x: $keywordX, y: $keywordY)")

                            for (otherBlock in visionText.textBlocks) {
                                for (otherLine in otherBlock.lines) {
                                    for (element in otherLine.elements) {
                                        val amountMatch = amountRegex.find(element.text) ?: continue
                                        val amountStr = amountMatch.groupValues[1].replace(",", ".")
                                        val num = amountStr.toDoubleOrNull() ?: continue
                                        val digitCount = amountStr.replace(".", "").length
                                        val elementBoundingBox = element.boundingBox
                                        if (elementBoundingBox == null) {
                                            Log.d("HomeActivity", "boundingBox для елемента '${element.text}' недоступний (null)")
                                            continue
                                        }

                                        val elementX = elementBoundingBox.left
                                        val elementY = elementBoundingBox.top
                                        Log.d("HomeActivity", "Знайдено елемент '${element.text}' на позиції (x: $elementX, y: $elementY)")

                                        if (elementX <= keywordX) continue
                                        val yDifference = Math.abs(elementY - keywordY)
                                        if (yDifference > 100) continue

                                        val isNotYear = num !in 1900.0..2099.0
                                        val isNotMultiplier = !visionText.text.contains(Regex("""\b${amountMatch.groupValues[1]}\b\s*[xX]""")) &&
                                                !visionText.text.contains(Regex("""\d+\s*[xX]\s*${amountMatch.groupValues[1]}\b"""))
                                        val isNotDate = !isPartOfDate(amountStr, visionText.text, visionText.text.indexOf(amountStr))
                                        val isNotPercentage = !isPartOfPercentage(amountStr, visionText.text, visionText.text.indexOf(amountStr))

                                        val isNotExcluded = excludeKeywords.none { keyword ->
                                            val regex = Regex("""${keyword}\s*[:=]?\s*${amountMatch.groupValues[1]}""", RegexOption.IGNORE_CASE)
                                            regex.containsMatchIn(visionText.text)
                                        }

                                        val hasCurrency = amountMatch.value.contains(Regex("""грн|UAH|rpH\.|TPH""", RegexOption.IGNORE_CASE))

                                        if (digitCount <= 8 && num in 1.0..1_000_000.0 && isNotYear && isNotMultiplier && isNotDate && isNotPercentage && isNotExcluded) {
                                            val roundedNum = roundToTwoDecimals(num)
                                            candidates.add(AmountCandidate(roundedNum, elementX, elementY, "After keyword: ${element.text}", hasCurrency, yDifference))
                                            Log.d("HomeActivity", "Знайдено суму праворуч від ключового слова: $roundedNum (x: $elementX, y: $elementY, має валюту: $hasCurrency, yDifference: $yDifference)")
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // Якщо координати недоступні, шукаємо в наступних рядках
                if (!boundingBoxAvailable) {
                    Log.d("HomeActivity", "Сума не знайдена з використанням координат, переходимо до пошуку в наступних рядках")
                    val lines = visionText.text.split("\n")
                    for (i in lines.indices) {
                        val line = lines[i]
                        if (keywords.any { keyword -> line.contains(keyword, ignoreCase = true) }) {
                            val nextLines = lines.subList(i, minOf(i + 6, lines.size)).joinToString("\n")
                            val amountMatches = amountRegex.findAll(nextLines)
                            amountMatches.forEach { match ->
                                val amountStr = match.groupValues[1].replace(",", ".")
                                val num = amountStr.toDoubleOrNull() ?: return@forEach
                                val digitCount = amountStr.replace(".", "").length
                                val position = visionText.text.indexOf(match.value)
                                val isNotYear = num !in 1900.0..2099.0
                                val isNotMultiplier = !visionText.text.contains(Regex("""\b${match.groupValues[1]}\b\s*[xX]""")) &&
                                        !visionText.text.contains(Regex("""\d+\s*[xX]\s*${match.groupValues[1]}\b"""))
                                val isNotDate = !isPartOfDate(amountStr, visionText.text, position)
                                val isNotPercentage = !isPartOfPercentage(amountStr, visionText.text, position)

                                val isNotExcluded = excludeKeywords.none { keyword ->
                                    val regex = Regex("""${keyword}\s*[:=]?\s*${match.groupValues[1]}""", RegexOption.IGNORE_CASE)
                                    regex.containsMatchIn(visionText.text)
                                }

                                val hasCurrency = match.value.contains(Regex("""грн|UAH|rpH\.|TPH""", RegexOption.IGNORE_CASE))

                                if (digitCount <= 8 && num in 1.0..1_000_000.0 && isNotYear && isNotMultiplier && isNotDate && isNotPercentage && isNotExcluded) {
                                    val roundedNum = roundToTwoDecimals(num)
                                    candidates.add(AmountCandidate(roundedNum, position, 0, "After keyword (next line): ${match.value}", hasCurrency, 0))
                                    Log.d("HomeActivity", "Знайдено суму в наступних рядках: $roundedNum (позиція: $position, має валюту: $hasCurrency)")
                                }
                            }
                        }
                    }
                }
            }
            return candidates.isNotEmpty()
        }

        // Спочатку шукаємо "До сплати"
        if (!findAmount(primaryKeywords, searchNextLines = true)) {
            // Якщо "До сплати" не знайдено, шукаємо "Сума"
            findAmount(secondaryKeywords, searchNextLines = true)
        }

        // Обираємо найкращого кандидата
        if (candidates.isNotEmpty()) {
            // Спочатку фільтруємо кандидатів, які не є відсотками
            val nonPercentageCandidates = candidates.filter { !isPartOfPercentage(it.amount.toString(), visionText.text, visionText.text.indexOf(it.amount.toString())) }
            val finalCandidates = if (nonPercentageCandidates.isNotEmpty()) nonPercentageCandidates else candidates

            // Вибираємо кандидата з валютою, якщо є
            val withCurrency = finalCandidates.filter { it.hasCurrency }
            val selectedCandidate = if (withCurrency.isNotEmpty()) {
                // Якщо є кандидати з валютою, вибираємо найбільшу суму
                withCurrency.maxByOrNull { it.amount }
            } else {
                // Якщо немає кандидатів з валютою, вибираємо найбільшу суму
                finalCandidates.maxByOrNull { it.amount }
            }

            val roundedAmount = roundToTwoDecimals(selectedCandidate?.amount ?: 0.0)
            Log.d("HomeActivity", "Обрано суму: $roundedAmount (джерело: ${selectedCandidate?.source}, yDistance: ${selectedCandidate?.yDistance})")
            return formatToTwoDecimals(roundedAmount)
        }

        Log.d("HomeActivity", "Не знайдено суми після ключових слів")
        return "0.00"
    }
    internal fun setupRetrofit(): MonobankApi {
        val retrofit = Retrofit.Builder()
            .baseUrl("https://api.monobank.ua/")
            .addConverterFactory(GsonConverterFactory.create())
            .build()
        return retrofit.create(MonobankApi::class.java)
    }
}

interface MonobankApi {
    @GET("bank/currency")
    suspend fun getExchangeRates(): List<MonobankResponse>
}

data class MonobankResponse(
    val currencyCodeA: Int,
    val currencyCodeB: Int,
    val date: Long,
    val rateBuy: Double?,
    val rateSell: Double?,
    val rateCross: Double?
)

fun loadAllExpenses(sharedPreferences: SharedPreferences): MutableList<Expense> {
    val gson = Gson()
    val json = sharedPreferences.getString("all_expenses", null)
    return if (json != null) {
        val type = object : TypeToken<MutableList<Expense>>() {}.type
        gson.fromJson(json, type) ?: mutableListOf()
    } else {
        mutableListOf()
    }
}

fun saveAllExpenses(expenses: List<Expense>, sharedPreferences: SharedPreferences) {
    val gson = Gson()
    val json = gson.toJson(expenses)
    sharedPreferences.edit().putString("all_expenses", json).apply()
}

fun loadAllIncomes(sharedPreferences: SharedPreferences): MutableList<Income> {
    val gson = Gson()
    val json = sharedPreferences.getString("all_incomes", null)
    return if (json != null) {
        val type = object : TypeToken<MutableList<Income>>() {}.type
        gson.fromJson(json, type) ?: mutableListOf()
    } else {
        mutableListOf()
    }
}

fun saveAllIncomes(incomes: List<Income>, sharedPreferences: SharedPreferences) {
    val gson = Gson()
    val json = gson.toJson(incomes)
    sharedPreferences.edit().putString("all_incomes", json).apply()
}
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(expenseViewModel: ExpenseViewModel, sharedPreferences: SharedPreferences, activity: HomeActivity) {
    val expenses by expenseViewModel.expenses
    var currentExpenses by remember { mutableStateOf(loadExpenses(sharedPreferences)) }
    var balance by remember { mutableStateOf(expenseViewModel.balance.value) }
    val context = LocalContext.current

    fun saveBalance(newBalance: BigDecimal) {
        Log.d("HomeActivity", "Зберігаємо баланс: $newBalance")
        sharedPreferences.edit().putString("current_balance", newBalance.toString()).commit()
        Log.d("HomeActivity", "Збережено баланс у SharedPreferences: $newBalance")
        balance = newBalance
        expenseViewModel.setBalance(newBalance)
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

    fun saveAvailableCategories(availableCategories: List<String>) {
        val gson = Gson()
        val json = gson.toJson(availableCategories)
        sharedPreferences.edit().putString("available_categories", json).apply()
    }

    fun loadAvailableCategories(): MutableList<String> {
        val gson = Gson()
        val json = sharedPreferences.getString("available_categories", null)
        return if (json != null) {
            val type = object : TypeToken<MutableList<String>>() {}.type
            gson.fromJson(json, type) ?: mutableListOf("Розваги", "Подарунки", "Спорт", "Освіта", "Одяг", "Техніка")
        } else {
            mutableListOf("Розваги", "Подарунки", "Спорт", "Освіта", "Одяг", "Техніка")
        }
    }

    var categoriesBalance = remember {
        mutableStateOf(
            mutableMapOf<String, BigDecimal>().apply {
                val allCategories = loadCategories()
                allCategories.forEach { category ->
                    val key = "expense_$category"
                    val expenseStr = try {
                        sharedPreferences.getString(key, "0.00") ?: "0.00"
                    } catch (e: ClassCastException) {
                        val floatValue = sharedPreferences.getFloat(key, 0f)
                        floatValue.toString()
                    }
                    put(category, BigDecimal(expenseStr))
                }
            }
        )
    }
    var categories by remember { mutableStateOf(loadCategories()) }
    var availableCategories by remember { mutableStateOf(loadAvailableCategories()) }
    var selectedCategories = remember { mutableStateOf(mutableSetOf<String>()) }
    var showDialog by remember { mutableStateOf(false) }
    var inputAmount by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf("") }
    var showAddCategoryDialog by remember { mutableStateOf(false) }
    var newCategoryName by remember { mutableStateOf("") }
    var showHistoryDialog by remember { mutableStateOf(false) }
    var showImageSourceDialog by remember { mutableStateOf(false) }

    var showExchangeRates by remember { mutableStateOf(false) }
    var exchangeRates by remember { mutableStateOf<List<ExchangeRate>>(emptyList()) }
    var lastUpdateTime by remember { mutableStateOf(0L) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    fun getCurrencyFlag(currency: String): String {
        return when (currency) {
            "USD" -> "🇺🇸"
            "EUR" -> "🇪🇺"
            "PLN" -> "🇵🇱"
            "GBP" -> "🇬🇧"
            "HUF" -> "🇭🇺"
            "TRY" -> "🇹🇷"
            "CZK" -> "🇨🇿"
            "DKK" -> "🇩🇰"
            "CAD" -> "🇨🇦"
            "CHF" -> "🇨🇭"
            "EUR/USD" -> "🇪🇺/🇺🇸"
            else -> ""
        }
    }

    suspend fun updateExchangeRates() {
        val currentTime = System.currentTimeMillis() / 1000
        if (currentTime - lastUpdateTime < 300) {
            Log.d("HomeActivity", "Курси валют ще не потрібно оновлювати. Час останнього оновлення: $lastUpdateTime")
            return
        }

        try {
            val monobankApi = activity.setupRetrofit()
            val response = monobankApi.getExchangeRates()
            val rates = response.mapNotNull { rate ->
                val currencyA = when (rate.currencyCodeA) {
                    840 -> "USD"
                    978 -> "EUR"
                    985 -> "PLN"
                    826 -> "GBP"
                    348 -> "HUF"
                    949 -> "TRY"
                    203 -> "CZK"
                    208 -> "DKK"
                    124 -> "CAD"
                    756 -> "CHF"
                    else -> null
                }
                val currencyB = when (rate.currencyCodeB) {
                    840 -> "USD"
                    980 -> "UAH"
                    else -> null
                }
                if (currencyA != null && currencyB != null) {
                    if (currencyA == "EUR" && currencyB == "USD") {
                        val buy = rate.rateBuy ?: rate.rateCross ?: 0.0
                        val sell = rate.rateSell ?: rate.rateCross ?: 0.0
                        ExchangeRate("EUR/USD", buy, sell)
                    } else if (currencyB == "UAH") {
                        val buy = rate.rateBuy ?: 0.0
                        val sell = rate.rateSell ?: rate.rateCross ?: 0.0
                        ExchangeRate(currencyA, buy, sell)
                    } else {
                        null
                    }
                } else {
                    null
                }
            }

            Log.d("HomeActivity", "Отримані курси валют з API: $rates")

            val filteredRates = rates.filter { rate ->
                if (rate.currency in listOf("USD", "EUR", "EUR/USD")) {
                    rate.buy != 0.0 || rate.sell != 0.0
                } else {
                    rate.sell != 0.0
                }
            }

            exchangeRates = filteredRates
            lastUpdateTime = currentTime
            errorMessage = null
            Log.d("HomeActivity", "Курси валют оновлено: $filteredRates")
        } catch (e: Exception) {
            Log.e("HomeActivity", "Помилка завантаження курсів валют: ${e.message}")
            errorMessage = "Не вдалося завантажити курси валют. Перевірте підключення до інтернету."
            exchangeRates = emptyList()
        }
    }

    LaunchedEffect(Unit) {
        updateExchangeRates()
        while (true) {
            delay(300_000L)
            updateExchangeRates()
        }
    }

    val incomeSources = listOf("Зарплата", "Фріланс", "Подарунок", "Інвестиції", "Інше")
    var selectedIncomeSource by remember { mutableStateOf(incomeSources[0]) }
    var customIncomeSource by remember { mutableStateOf("") }

    val scrollState = rememberScrollState()
    val exchangeRatesScrollState = rememberScrollState()

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
            val allExpenses = loadAllExpenses(sharedPreferences).toMutableList()
            val existingDates = allExpenses.map { it.date }.toSet()
            val newExpenses = currentExpenses.filter { it.date !in existingDates }
            allExpenses.addAll(newExpenses)
            saveAllExpenses(allExpenses, sharedPreferences)
            Log.d("HomeActivity", "Збережено витрати в повну історію (без дублювання): $allExpenses")

            categoriesBalance.value = categoriesBalance.value.mapValues { BigDecimal.ZERO }.toMutableMap()
            with(sharedPreferences.edit()) {
                categoriesBalance.value.keys.forEach { category ->
                    putString("expense_$category", "0.00")
                }
                putString("last_reset_date", SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(currentDate.time))
                apply()
            }
            Log.d("HomeActivity", "Витрати в категоріях обнулені: ${categoriesBalance.value}")

            currentExpenses = mutableListOf()
            saveExpenses(currentExpenses, sharedPreferences)
            Log.d("HomeActivity", "Список поточних витрат обнулено")
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "FinanceFlow",
                            style = TextStyle(color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                        )
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(onClick = { showHistoryDialog = true }) {
                                Icon(
                                    imageVector = Icons.Default.History,
                                    contentDescription = "Історія",
                                    tint = Color.White,
                                    modifier = Modifier.size(28.dp)
                                )
                            }
                            IconButton(onClick = {
                                context.startActivity(Intent(context, ProfileActivity::class.java))
                            }) {
                                Icon(
                                    imageVector = Icons.Default.AccountCircle,
                                    contentDescription = "Профіль",
                                    tint = Color.White,
                                    modifier = Modifier.size(28.dp)
                                )
                            }
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF1A3D62)
                )
            )
        },
        content = { padding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .background(Color(0xFFF5F7FA))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(125.dp)
                        .background(
                            brush = Brush.verticalGradient(
                                colors = listOf(Color(0xFF1A3D62), Color(0xFF2E5B8C))
                            )
                        )
                )

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(top = 4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {

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
                            horizontalAlignment = Alignment.Start
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
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                IconButton(
                                    onClick = { selectedCategory = ""; showDialog = true },
                                    modifier = Modifier
                                        .size(56.dp)
                                        .background(
                                            Color(0xFF4A7BA6).copy(alpha = 0.1f),
                                            CircleShape
                                        )
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Add,
                                        contentDescription = "Додати",
                                        tint = Color(0xFF1A3D62),
                                        modifier = Modifier.size(32.dp)
                                    )
                                }
                                Text(
                                    text = "Додати",
                                    style = TextStyle(
                                        color = Color(0xFF1A3D62),
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Medium
                                    ),
                                    modifier = Modifier.padding(top = 4.dp)
                                )
                            }

                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                IconButton(
                                    onClick = {
                                        context.startActivity(
                                            Intent(
                                                context,
                                                StatisticsActivity::class.java
                                            )
                                        )
                                    },
                                    modifier = Modifier
                                        .size(56.dp)
                                        .background(
                                            Color(0xFF4A7BA6).copy(alpha = 0.1f),
                                            CircleShape
                                        )
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.BarChart,
                                        contentDescription = "Аналіз",
                                        tint = Color(0xFF1A3D62),
                                        modifier = Modifier.size(32.dp)
                                    )
                                }
                                Text(
                                    text = "Аналіз",
                                    style = TextStyle(
                                        color = Color(0xFF1A3D62),
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Medium
                                    ),
                                    modifier = Modifier.padding(top = 4.dp)
                                )
                            }

                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                IconButton(
                                    onClick = {
                                        context.startActivity(
                                            Intent(
                                                context,
                                                AIChatActivity::class.java
                                            )
                                        )
                                    },
                                    modifier = Modifier
                                        .size(56.dp)
                                        .background(
                                            Color(0xFF4A7BA6).copy(alpha = 0.1f),
                                            CircleShape
                                        )
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Chat,
                                        contentDescription = "Чат",
                                        tint = Color(0xFF1A3D62),
                                        modifier = Modifier.size(32.dp)
                                    )
                                }
                                Text(
                                    text = "Чат",
                                    style = TextStyle(
                                        color = Color(0xFF1A3D62),
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Medium
                                    ),
                                    modifier = Modifier.padding(top = 4.dp)
                                )
                            }
                        }

                    Spacer(modifier = Modifier.height(24.dp))

                    Text(
                        text = "Мої витрати",
                        style = TextStyle(color = Color(0xFF1A3D62), fontSize = 18.sp, fontWeight = FontWeight.Bold),
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                        textAlign = TextAlign.Center )

                    Spacer(modifier = Modifier.height(16.dp))

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(0.5f)
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
                                    amount = categoriesBalance.value[category] ?: BigDecimal.ZERO,
                                    onClick = { selectedCategory = category; showDialog = true }
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                            }
                        }
                    }

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                            .clickable { showExchangeRates = !showExchangeRates }
                            .shadow(4.dp, RoundedCornerShape(12.dp)),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFE6E6E6)),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp)
                        ) {
                            Text(
                                text = "Курс валют",
                                style = TextStyle(
                                    color = Color.Black,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold
                                ),
                                modifier = Modifier.align(Alignment.Start)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            if (errorMessage != null || exchangeRates.isEmpty()) {
                                Text(
                                    text = errorMessage ?: "Завантаження курсів валют...",
                                    style = TextStyle(
                                        color = Color.Gray,
                                        fontSize = 14.sp
                                    ),
                                    modifier = Modifier.fillMaxWidth(),
                                    textAlign = TextAlign.Center
                                )
                            } else {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = "🇺🇸",
                                                fontSize = 20.sp,
                                                modifier = Modifier.padding(end = 4.dp)
                                            )
                                            Column {
                                                Text(
                                                    text = "Долар США",
                                                    style = TextStyle(
                                                        color = Color.Black,
                                                        fontSize = 12.sp
                                                    )
                                                )
                                                Text(
                                                    text = "${exchangeRates.firstOrNull { rate -> rate.currency == "USD" }?.buy ?: 0.0} / ${exchangeRates.firstOrNull { rate -> rate.currency == "USD" }?.sell ?: 0.0}",
                                                    style = TextStyle(
                                                        color = Color.Black,
                                                        fontSize = 14.sp,
                                                        fontWeight = FontWeight.Bold
                                                    )
                                                )
                                            }
                                        }
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = "🇪🇺",
                                                fontSize = 20.sp,
                                                modifier = Modifier.padding(end = 4.dp)
                                            )
                                            Column {
                                                Text(
                                                    text = "Євро",
                                                    style = TextStyle(
                                                        color = Color.Black,
                                                        fontSize = 12.sp
                                                    )
                                                )
                                                Text(
                                                    text = "${exchangeRates.firstOrNull { rate -> rate.currency == "EUR" }?.buy ?: 0.0} / ${exchangeRates.firstOrNull { rate -> rate.currency == "EUR" }?.sell ?: 0.0}",
                                                    style = TextStyle(
                                                        color = Color.Black,
                                                        fontSize = 14.sp,
                                                        fontWeight = FontWeight.Bold
                                                    )
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    if (showExchangeRates) {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp)
                                .shadow(4.dp, RoundedCornerShape(12.dp)),
                            colors = CardDefaults.cardColors(containerColor = Color.White),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(max = 200.dp)
                                    .verticalScroll(exchangeRatesScrollState)
                                    .padding(16.dp)
                            ) {
                                Text(
                                    text = "Курси валют",
                                    style = TextStyle(
                                        color = Color(0xFF1A3D62),
                                        fontSize = 18.sp,
                                        fontWeight = FontWeight.Bold
                                    ),
                                    modifier = Modifier.fillMaxWidth(),
                                    textAlign = TextAlign.Center
                                )
                                Spacer(modifier = Modifier.height(8.dp))

                                if (errorMessage != null || exchangeRates.isEmpty()) {
                                    Text(
                                        text = errorMessage ?: "Завантаження курсів валют...",
                                        style = TextStyle(
                                            color = Color.Gray,
                                            fontSize = 14.sp
                                        ),
                                        modifier = Modifier.fillMaxWidth(),
                                        textAlign = TextAlign.Center
                                    )
                                } else {
                                    Text(
                                        text = "Основні курси",
                                        style = TextStyle(
                                            color = Color(0xFF1A3D62),
                                            fontSize = 16.sp,
                                            fontWeight = FontWeight.Medium
                                        ),
                                        modifier = Modifier.padding(bottom = 8.dp)
                                    )
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            text = "Валюта",
                                            style = TextStyle(
                                                color = Color(0xFF1A3D62),
                                                fontSize = 14.sp,
                                                fontWeight = FontWeight.Medium
                                            )
                                        )
                                        Row {
                                            Text(
                                                text = "Купівля / Продаж",
                                                style = TextStyle(
                                                    color = Color(0xFF1A3D62),
                                                    fontSize = 14.sp,
                                                    fontWeight = FontWeight.Medium
                                                )
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(8.dp))
                                    val mainRates = exchangeRates.filter { rate -> rate.currency in listOf("USD", "EUR", "EUR/USD") }
                                    mainRates.forEach { rate ->
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(vertical = 4.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(
                                                    text = getCurrencyFlag(rate.currency),
                                                    style = TextStyle(fontSize = 16.sp),
                                                    modifier = Modifier.padding(end = 4.dp)
                                                )
                                                Text(
                                                    text = if (rate.currency == "EUR/USD") "EUR/USD" else rate.currency,
                                                    style = TextStyle(
                                                        color = Color(0xFF1A3D62),
                                                        fontSize = 14.sp
                                                    )
                                                )
                                            }
                                            Text(
                                                text = "${rate.buy} / ${rate.sell}",
                                                style = TextStyle(
                                                    color = Color(0xFF1A3D62),
                                                    fontSize = 14.sp,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            )
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(16.dp))

                                    Text(
                                        text = "Курси платіжних систем",
                                        style = TextStyle(
                                            color = Color(0xFF1A3D62),
                                            fontSize = 16.sp,
                                            fontWeight = FontWeight.Medium
                                        ),
                                        modifier = Modifier.padding(bottom = 8.dp)
                                    )
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            text = "Валюта",
                                            style = TextStyle(
                                                color = Color(0xFF1A3D62),
                                                fontSize = 14.sp,
                                                fontWeight = FontWeight.Medium
                                            )
                                        )
                                        Row {
                                            Text(
                                                text = "Продаж",
                                                style = TextStyle(
                                                    color = Color(0xFF1A3D62),
                                                    fontSize = 14.sp,
                                                    fontWeight = FontWeight.Medium
                                                )
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(8.dp))
                                    val paymentSystemRates = exchangeRates.filter { rate -> rate.currency !in listOf("USD", "EUR", "EUR/USD") }
                                    Log.d("HomeActivity", "Курси платіжних систем: $paymentSystemRates")
                                    if (paymentSystemRates.isEmpty()) {
                                        Text(
                                            text = "Немає даних про курси платіжних систем",
                                            style = TextStyle(
                                                color = Color.Gray,
                                                fontSize = 14.sp
                                            ),
                                            modifier = Modifier.fillMaxWidth(),
                                            textAlign = TextAlign.Center
                                        )
                                    } else {
                                        paymentSystemRates.forEach { rate ->
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(vertical = 4.dp),
                                                horizontalArrangement = Arrangement.SpaceBetween
                                            ) {
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Text(
                                                        text = getCurrencyFlag(rate.currency),
                                                        style = TextStyle(fontSize = 16.sp),
                                                        modifier = Modifier.padding(end = 4.dp)
                                                    )
                                                    Text(
                                                        text = rate.currency,
                                                        style = TextStyle(
                                                            color = Color(0xFF1A3D62),
                                                            fontSize = 14.sp
                                                        )
                                                    )
                                                }
                                                Text(
                                                    text = "${rate.sell}",
                                                    style = TextStyle(
                                                        color = Color(0xFF1A3D62),
                                                        fontSize = 14.sp,
                                                        fontWeight = FontWeight.Bold
                                                    )
                                                )
                                            }
                                        }
                                    }
                                }
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
            }
        }
    )

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
                    categoriesBalance.value[newCategoryName] = BigDecimal.ZERO
                    saveCategories(categories)
                    newCategoryName = ""
                    showAddCategoryDialog = false
                } else if (selectedCategories.value.isNotEmpty()) {
                    selectedCategories.value.forEach { category ->
                        categories.add(category)
                        expenseViewModel.addCategory(category)
                        categoriesBalance.value[category] = BigDecimal.ZERO
                        availableCategories.remove(category)
                    }
                    saveCategories(categories)
                    saveAvailableCategories(availableCategories)
                    selectedCategories.value.clear()
                    showAddCategoryDialog = false
                }
            }
        )
    }

    if (showDialog) {
        ModernDialog(
            title = if (selectedCategory.isEmpty()) "Додати дохід" else "Введіть суму витрат для $selectedCategory",
            onDismiss = { showDialog = false },
            content = {
                Column {
                    if (selectedCategory.isEmpty()) {
                        var expanded by remember { mutableStateOf(false) }
                        val alpha by animateFloatAsState(
                            targetValue = if (expanded) 1f else 0f,
                            animationSpec = tween(durationMillis = 300)
                        )

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier.fillMaxWidth(),
                                contentAlignment = Alignment.CenterStart
                            ) {
                                Button(
                                    onClick = {
                                        Log.d("DropdownMenu", "Button clicked, expanding dropdown")
                                        expanded = true
                                    },
                                    modifier = Modifier
                                        .width(200.dp)
                                        .height(48.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = Color.White,
                                        contentColor = Color.Black
                                    ),
                                    shape = RoundedCornerShape(12.dp),
                                    elevation = ButtonDefaults.buttonElevation(defaultElevation = 2.dp)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 12.dp)
                                    ) {
                                        Text(
                                            text = selectedIncomeSource,
                                            color = if (selectedIncomeSource == incomeSources[0]) Color.Gray else Color.Black,
                                            fontSize = 16.sp,
                                            modifier = Modifier.weight(1f)
                                        )
                                        Icon(
                                            imageVector = if (expanded) Icons.Default.ArrowDropUp else Icons.Default.ArrowDropDown,
                                            contentDescription = null,
                                            tint = Color.Gray
                                        )
                                    }
                                }

                                DropdownMenu(
                                    expanded = expanded,
                                    onDismissRequest = {
                                        Log.d("DropdownMenu", "Dropdown dismissed")
                                        expanded = false
                                    },
                                    modifier = Modifier
                                        .width(200.dp)
                                        .heightIn(max = 200.dp)
                                        .background(Color.White, RoundedCornerShape(12.dp))
                                        .alpha(alpha)
                                ) {
                                    incomeSources.forEach { source ->
                                        DropdownMenuItem(
                                            text = {
                                                Text(
                                                    source,
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .padding(12.dp),
                                                    fontSize = 16.sp,
                                                    color = Color(0xFF1A3D62),
                                                    textAlign = TextAlign.Start
                                                )
                                            },
                                            onClick = {
                                                Log.d("DropdownMenu", "Selected source: $source")
                                                selectedIncomeSource = source
                                                expanded = false
                                                if (source != "Інше") {
                                                    customIncomeSource = ""
                                                }
                                            }
                                        )
                                        Divider(color = Color(0xFFE0E0E0), thickness = 0.5.dp)
                                    }
                                }
                            }
                        }

                        if (selectedIncomeSource == "Інше") {
                            OutlinedTextField(
                                value = customIncomeSource,
                                onValueChange = { customIncomeSource = it },
                                label = { Text("Вкажіть джерело") },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 8.dp),
                                shape = RoundedCornerShape(8.dp)
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = inputAmount,
                            onValueChange = { inputAmount = it },
                            label = { Text("Сума") },
                            modifier = Modifier
                                .weight(1f)
                                .padding(end = 8.dp),
                            shape = RoundedCornerShape(8.dp)
                        )
                        if (selectedCategory.isNotEmpty()) {
                            IconButton(
                                onClick = { showImageSourceDialog = true },
                                modifier = Modifier
                                    .size(40.dp)
                                    .background(Color(0xFF4A7BA6), CircleShape)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CameraAlt,
                                    contentDescription = "Сканувати чек",
                                    tint = Color.White
                                )
                            }
                        }
                    }
                }
            },
            confirmAction = {
                Log.d("HomeActivity", "Обрана сума перед додаванням: $inputAmount")
                val cleanedAmount = inputAmount.replace(",", ".") // Замінюємо кому на крапку
                val amount = try {
                    BigDecimal(cleanedAmount)
                } catch (e: NumberFormatException) {
                    Log.e("HomeActivity", "Помилка парсингу суми: ${e.message}")
                    BigDecimal.ZERO
                }
                val currentDate = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault()).format(Date())
                if (selectedCategory.isEmpty()) {
                    val finalIncomeSource = if (selectedIncomeSource == "Інше" && customIncomeSource.isNotBlank()) {
                        customIncomeSource
                    } else {
                        selectedIncomeSource
                    }

                    val newBalance = balance + amount
                    saveBalance(newBalance)

                    val allIncomes = loadAllIncomes(sharedPreferences).toMutableList()
                    val newIncome = Income(finalIncomeSource, amount, currentDate)
                    allIncomes.add(newIncome)
                    saveAllIncomes(allIncomes, sharedPreferences)

                    val incomes = loadIncomes(sharedPreferences).toMutableList()
                    incomes.add(newIncome)
                    saveIncomes(incomes, sharedPreferences)
                } else {
                    val currentAmount = BigDecimal(categoriesBalance.value[selectedCategory]?.toString() ?: "0.0")
                    val newAmount = currentAmount.add(amount)
                    categoriesBalance.value[selectedCategory] = newAmount
                    sharedPreferences.edit().putFloat("expense_$selectedCategory", newAmount.toFloat()).apply()

                    val newBalance = balance - amount
                    Log.d("HomeActivity", "Поточний баланс: $balance, Витрата: $amount, Новий баланс: $newBalance")
                    if (newBalance >= BigDecimal.ZERO) {
                        saveBalance(newBalance)
                    } else {
                        saveBalance(newBalance)
                        android.widget.Toast.makeText(context, "Недостатньо коштів! Баланс: ₴$newBalance", android.widget.Toast.LENGTH_SHORT).show()
                    }

                    val allExpenses = loadAllExpenses(sharedPreferences).toMutableList()
                    val newExpense = Expense(selectedCategory, amount, currentDate)
                    allExpenses.add(newExpense)
                    saveAllExpenses(allExpenses, sharedPreferences)

                    currentExpenses = (currentExpenses + newExpense).toMutableList()
                    saveExpenses(currentExpenses, sharedPreferences)

                    expenseViewModel.addExpense(selectedCategory, amount, currentDate)
                }
                inputAmount = ""
                showDialog = false
            }
        )
    }

    if (showImageSourceDialog) {
        var isProcessing by remember { mutableStateOf(false) }
        LaunchedEffect(isProcessing) {
            if (isProcessing) {
                delay(2000) // Затримка для завершення розпізнавання (можна налаштувати)
                isProcessing = false
            }
        }

        AlertDialog(
            onDismissRequest = { showImageSourceDialog = false },
            title = {
                Text(
                    text = "Виберіть джерело",
                    style = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1A3D62))
                )
            },
            text = {
                Column {
                    TextButton(
                        onClick = {
                            if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                                isProcessing = true
                                activity.onImageProcessed = { extractedAmount ->
                                    inputAmount = extractedAmount // Оновлюємо inputAmount
                                    Log.d("HomeActivity", "Оновлено inputAmount після сканування: $inputAmount")
                                    showImageSourceDialog = false // Закриваємо після оновлення
                                }
                                activity.cameraLauncher.launch(null)
                            } else {
                                activity.requestPermissionLauncher.launch(Manifest.permission.CAMERA)
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Камера", color = Color(0xFF1A3D62), fontSize = 16.sp)
                    }
                    TextButton(
                        onClick = {
                            isProcessing = true
                            activity.onImageProcessed = { extractedAmount ->
                                inputAmount = extractedAmount // Оновлюємо inputAmount
                                Log.d("HomeActivity", "Оновлено inputAmount після галереї: $inputAmount")
                                showImageSourceDialog = false // Закриваємо після оновлення
                            }
                            activity.galleryLauncher.launch("image/*")
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Галерея", color = Color(0xFF1A3D62), fontSize = 16.sp)
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showImageSourceDialog = false }) {
                    Text("Скасувати", color = Color.Gray)
                }
            },
            shape = RoundedCornerShape(16.dp),
            containerColor = Color.White
        )
    }

    if (showHistoryDialog) {
        HistoryDialog(
            expenses = loadAllExpenses(sharedPreferences),
            incomes = loadAllIncomes(sharedPreferences),
            onDismiss = { showHistoryDialog = false }
        )
    }}

@Composable
fun HistoryDialog(
    expenses: List<Expense>,
    incomes: List<Income>,
    onDismiss: () -> Unit
) {
    var selectedTab by remember { mutableStateOf("Витрати") }
    val tabs = listOf("Витрати", "Доходи")
    val historyScrollState = rememberScrollState()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Історія",
                style = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1A3D62)),
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center
            )
        },
        text = {
            Column {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    tabs.forEach { tab ->
                        Button(
                            onClick = { selectedTab = tab },
                            modifier = Modifier
                                .weight(1f)
                                .padding(horizontal = 4.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (selectedTab == tab) Color(0xFF1A3D62) else Color(0xFFCCC8C8)
                            ),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(
                                text = tab,
                                color = if (selectedTab == tab) Color.White else Color.Black,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }

                if (selectedTab == "Витрати") {
                    if (expenses.isEmpty()) {
                        Text("Немає записів про витрати", color = Color.Gray, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
                    } else {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 300.dp)
                                .verticalScroll(historyScrollState)
                        ) {
                            val groupedExpenses = expenses.reversed().groupBy { expense -> expense.date.split(" ")[0] }
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
                                    textAlign = TextAlign.Center
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
                } else {
                    if (incomes.isEmpty()) {
                        Text("Немає записів про доходи", color = Color.Gray, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
                    } else {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 300.dp)
                                .verticalScroll(historyScrollState)
                        ) {
                            val groupedIncomes = incomes.reversed().groupBy { income -> income.date.split(" ")[0] }
                            groupedIncomes.forEach { (date, dateIncomes) ->
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
                                    textAlign = TextAlign.Center
                                )
                                dateIncomes.forEach { income ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 4.dp, horizontal = 8.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column {
                                            Text(
                                                text = income.source,
                                                style = TextStyle(fontSize = 14.sp, color = Color(0xFF1A3D62))
                                            )
                                            Text(
                                                text = income.date.split(" ")[1],
                                                style = TextStyle(fontSize = 12.sp, color = Color.Gray)
                                            )
                                        }
                                        Text(
                                            text = "₴${income.amount}",
                                            style = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1A3D62))
                                        )
                                    }
                                }
                                Divider(color = Color(0xFFE0E0E0), thickness = 1.dp)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Закрити", color = Color.Gray)
            }
        },
        shape = RoundedCornerShape(16.dp),
        containerColor = Color.White
    )
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
fun CategoryCard(category: String, amount: BigDecimal, onClick: () -> Unit) {
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

data class ExchangeRate(
    val currency: String,
    val buy: Double,
    val sell: Double
)