package com.example.financeflow.viewmodel

import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import com.example.financeflow.Expense

class ExpenseViewModel : ViewModel() {
    private val _balance = mutableStateOf(0.0)
    val balance: State<Double> get() = _balance

    private val _categoriesBalance = mutableStateOf(
        mapOf<String, Double>(
            "Розваги" to 0.0,
            "Подарунки" to 0.0,
            "Спорт" to 0.0,
            "Освіта" to 0.0,
            "Одяг" to 0.0,
            "Техніка" to 0.0
        )
    )
    val categoriesBalance: State<Map<String, Double>> get() = _categoriesBalance

    // Додаємо список витрат
    private val _expenses = mutableStateOf<List<Expense>>(emptyList())
    val expenses: State<List<Expense>> get() = _expenses

    fun addExpense(category: String, amount: Double, date: String = "") {
        // Оновлюємо баланс категорії
        _categoriesBalance.value = _categoriesBalance.value.toMutableMap().apply {
            put(category, (this[category] ?: 0.0) + amount)
        }
        // Зменшуємо загальний баланс (оскільки це витрата)
        _balance.value -= amount
        // Додаємо витрату до історії
        _expenses.value = _expenses.value + Expense(category, amount, date)
    }

    fun addCategory(category: String) {
        _categoriesBalance.value = _categoriesBalance.value.toMutableMap().apply {
            put(category, 0.0)
        }
    }

    // Метод для ініціалізації списку витрат із SharedPreferences
    fun setExpenses(expenses: List<Expense>) {
        _expenses.value = expenses
    }

    // Метод для встановлення балансу (наприклад, при додаванні коштів)
    fun setBalance(amount: Double) {
        _balance.value += amount
    }
}