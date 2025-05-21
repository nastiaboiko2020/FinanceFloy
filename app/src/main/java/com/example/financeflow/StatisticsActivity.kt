package com.example.financeflow

import android.app.Activity
import android.content.SharedPreferences
import android.graphics.Color
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.graphics.graphicsLayer
import com.example.financeflow.ui.theme.FinanceFlowTheme
import com.example.financeflow.viewmodel.Expense
import com.github.mikephil.charting.charts.BarChart
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.charts.PieChart
import com.github.mikephil.charting.data.*
import com.github.mikephil.charting.formatter.PercentFormatter
import com.google.accompanist.pager.ExperimentalPagerApi
import com.google.accompanist.pager.HorizontalPager
import com.google.accompanist.pager.rememberPagerState
import java.text.SimpleDateFormat
import java.util.*

class StatisticsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d("StatisticsActivity", "onCreate started")
        val sharedPreferences = getSharedPreferences("finance_flow_prefs", MODE_PRIVATE)
        setContent {
            FinanceFlowTheme(
                darkTheme = false, // Завжди світла тема
                useAIChatTheme = false,
                useFinanceFlowTheme = true
            ) {
                StatisticsScreen(sharedPreferences)
            }
        }
        Log.d("StatisticsActivity", "onCreate finished")
    }
}

@Composable
fun StatisticsScreen(sharedPreferences: SharedPreferences) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()

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

    val expenses = loadAllExpenses(sharedPreferences)
    val dateFormatMonth = SimpleDateFormat("yyyy-MM", Locale.getDefault())
    val dateFormatFull = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault())
    val uniqueMonths = expenses.map { expense ->
        try {
            dateFormatMonth.format(dateFormatFull.parse(expense.date) ?: Date())
        } catch (e: Exception) {
            Log.e("StatisticsScreen", "Error parsing date: ${expense.date}", e)
            "Invalid Date"
        }
    }.distinct().sorted()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(ComposeColor(0xFFF5F7FA)) // Фон для світлої теми
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(150.dp)
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            ComposeColor(0xFF1A3D62), // Темно-синій
                            ComposeColor(0xFF2E5B8C)  // Світліший синій
                        )
                    )
                )
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = 16.dp)
        ) {
            Text(
                text = "Статистика витрат",
                style = TextStyle(
                    color = ComposeColor.White,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold
                ),
                modifier = Modifier
                    .padding(top = 16.dp, bottom = 24.dp)
                    .align(Alignment.CenterHorizontally)
                    .graphicsLayer(alpha = fadeIn, translationY = translateY)
            )

            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(scrollState)
                    .padding(horizontal = 16.dp)
            ) {
                ChartWithPager(
                    title = "Витрати за місяць",
                    uniqueMonths = uniqueMonths,
                    chartContent = { month ->
                        val filteredExpenses = expenses.filter { expense ->
                            try {
                                dateFormatMonth.format(dateFormatFull.parse(expense.date) ?: Date()) == month
                            } catch (e: Exception) {
                                false
                            }
                        }
                        if (filteredExpenses.isEmpty()) {
                            Text(
                                text = "Немає даних за $month",
                                color = ComposeColor.Black,
                                fontSize = 14.sp,
                                modifier = Modifier.align(Alignment.CenterHorizontally)
                            )
                        } else {
                            Row(
                                modifier = Modifier.fillMaxSize(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Box(modifier = Modifier.weight(1f)) {
                                    AndroidView(
                                        factory = { ctx ->
                                            PieChart(ctx).apply {
                                                configurePieChart(this, filteredExpenses)
                                            }
                                        },
                                        update = { chart ->
                                            configurePieChart(chart, filteredExpenses)
                                        },
                                        modifier = Modifier.fillMaxSize()
                                    )
                                }
                                CustomLegend(expenses = filteredExpenses)
                            }
                        }
                    }
                )

                Spacer(modifier = Modifier.height(16.dp))

                ChartWithPager(
                    title = "Середня витрата за категоріями",
                    uniqueMonths = uniqueMonths,
                    chartContent = { month ->
                        val filteredExpenses = expenses.filter { expense ->
                            try {
                                dateFormatMonth.format(dateFormatFull.parse(expense.date) ?: Date()) == month
                            } catch (e: Exception) {
                                false
                            }
                        }
                        Row(
                            modifier = Modifier.fillMaxSize(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Box(modifier = Modifier.weight(1f)) {
                                AndroidView(
                                    factory = { ctx ->
                                        BarChart(ctx).apply {
                                            configureBarChart(this, filteredExpenses)
                                        }
                                    },
                                    update = { chart ->
                                        configureBarChart(chart, filteredExpenses)
                                    },
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                            CustomLegend(expenses = filteredExpenses)
                        }
                    }
                )

                Spacer(modifier = Modifier.height(16.dp))

                ChartWithPager(
                    title = "Витрати по днях",
                    uniqueMonths = uniqueMonths,
                    chartContent = { month ->
                        val filteredExpenses = expenses.filter { expense ->
                            try {
                                dateFormatMonth.format(dateFormatFull.parse(expense.date) ?: Date()) == month
                            } catch (e: Exception) {
                                false
                            }
                        }
                        AndroidView(
                            factory = { ctx ->
                                LineChart(ctx).apply {
                                    configureLineChart(this, filteredExpenses)
                                }
                            },
                            update = { chart ->
                                configureLineChart(chart, filteredExpenses)
                            },
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                )
            }

            Button(
                onClick = { (context as? Activity)?.finish() },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                    .height(56.dp)
                    .shadow(4.dp, RoundedCornerShape(12.dp))
                    .graphicsLayer(alpha = fadeIn, translationY = translateY),
                colors = ButtonDefaults.buttonColors(containerColor = ComposeColor(0xFF4A7BA6)),
                shape = RoundedCornerShape(12.dp),
                elevation = ButtonDefaults.buttonElevation(defaultElevation = 4.dp)
            ) {
                Text(
                    text = "Назад",
                    color = ComposeColor.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

fun configurePieChart(chart: PieChart, expenses: List<Expense>) {
    chart.legend.isEnabled = false
    val entries = expenses.groupBy { it.category }
        .map { (category, list) ->
            PieEntry(list.sumOf { it.amount.toDouble() }.toFloat(), "")
        }
    val dataSet = PieDataSet(entries, "Категорії")
    dataSet.colors = getColorList()
    dataSet.valueTextColor = Color.BLACK
    dataSet.valueTextSize = 12f
    val pieData = PieData(dataSet)
    pieData.setValueFormatter(PercentFormatter(chart))
    chart.data = pieData
    chart.description.isEnabled = false
    chart.setBackgroundColor(Color.TRANSPARENT)
    chart.animateY(1000)
    chart.setHoleColor(Color.WHITE)
    chart.setTransparentCircleColor(Color.WHITE)
    chart.invalidate()
}

fun configureBarChart(chart: BarChart, expenses: List<Expense>) {
    val entries = expenses.groupBy { it.category }
        .entries.mapIndexed { index, entry ->
            val (category, list) = entry
            val average = if (list.isNotEmpty()) list.sumOf { it.amount.toDouble() }.toFloat() / list.size else 0f
            BarEntry(index.toFloat(), average)
        }
    val dataSet = BarDataSet(entries, "Категорії")
    dataSet.colors = getColorList()
    chart.data = BarData(dataSet)
    chart.description.isEnabled = false
    chart.animateY(1000)
    chart.axisLeft.textColor = Color.BLACK
    chart.axisRight.textColor = Color.BLACK
    chart.xAxis.isEnabled = false
    chart.setBackgroundColor(Color.TRANSPARENT)
    chart.invalidate()
}

fun configureLineChart(chart: LineChart, expenses: List<Expense>) {
    val dateFormatDay = SimpleDateFormat("dd", Locale.getDefault())
    val dateFormatFull = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault())
    val entries = expenses.groupBy { expense ->
        dateFormatDay.format(dateFormatFull.parse(expense.date) ?: Date()).toInt()
    }.map { (day, list) ->
        Entry(day.toFloat(), list.sumOf { it.amount.toDouble() }.toFloat())
    }.sortedBy { it.x }
    val dataSet = LineDataSet(entries, "Дні")
    dataSet.color = Color.parseColor("#1E88E5")
    dataSet.valueTextColor = Color.BLACK
    chart.data = LineData(dataSet)
    chart.description.isEnabled = false
    chart.animateXY(1000, 1000)
    chart.axisLeft.textColor = Color.BLACK
    chart.axisRight.textColor = Color.BLACK
    chart.xAxis.textColor = Color.BLACK
    chart.setBackgroundColor(Color.TRANSPARENT)
    chart.invalidate()
}

@OptIn(ExperimentalPagerApi::class)
@Composable
fun ChartWithPager(
    title: String,
    uniqueMonths: List<String>,
    chartContent: @Composable (String) -> Unit
) {
    val pagerState = rememberPagerState(initialPage = if (uniqueMonths.isNotEmpty()) uniqueMonths.size - 1 else 0)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
            .shadow(4.dp, RoundedCornerShape(12.dp)),
        colors = CardDefaults.cardColors(containerColor = ComposeColor.White),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    color = ComposeColor.Black,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = uniqueMonths.getOrElse(pagerState.currentPage) { "Немає даних" },
                    color = ComposeColor.Black,
                    fontSize = 14.sp
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            HorizontalPager(
                count = uniqueMonths.size,
                state = pagerState,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp)
            ) { page ->
                chartContent(uniqueMonths[page])
            }
        }
    }
}

@Composable
fun CustomLegend(expenses: List<Expense>) {
    val categories = expenses.groupBy { it.category }
        .map { (category, list) -> category to list.sumOf { it.amount.toDouble() }.toFloat() }
    val colors = getColorList().map { ComposeColor(it) }
    val legendScrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .heightIn(max = 180.dp)
            .verticalScroll(legendScrollState)
            .padding(start = 8.dp)
    ) {
        categories.forEachIndexed { index, (category, _) ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(12.dp)
                        .background(colors[index % colors.size], RoundedCornerShape(4.dp))
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = category,
                    color = ComposeColor.Black,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
        }
    }
}

fun getColorList(): List<Int> {
    return listOf(
        Color.parseColor("#1E88E5"),
        Color.parseColor("#388E3C"),
        Color.parseColor("#D32F2F"),
        Color.parseColor("#FBC02D"),
        Color.parseColor("#8E24AA"),
        Color.parseColor("#5E35B1"),
        Color.parseColor("#00ACC1"),
        Color.parseColor("#00897B"),
        Color.parseColor("#F4511E"),
        Color.parseColor("#6D4C41"),
        Color.parseColor("#757575"),
        Color.parseColor("#43A047"),
        Color.parseColor("#FFA726")
    )
}