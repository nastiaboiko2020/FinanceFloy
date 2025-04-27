package com.example.financeflow.viewmodel

import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import java.math.BigDecimal

// Імпортуємо Expense, якщо він у тому ж пакеті
// Якщо Expense у іншому пакеті, наприклад com.example.financeflow.model, змініть імпорт:
// import com.example.financeflow.model.Expense

class ExpenseViewModel : ViewModel() {
    private val _balance = mutableStateOf(BigDecimal.ZERO)
    val balance: State<BigDecimal> get() = _balance

    private val _expenses = mutableStateOf<MutableList<Expense>>(mutableListOf())
    val expenses: State<List<Expense>> get() = _expenses

    // Додаємо змінну для балансів по категоріях
    private val _categoriesBalance = mutableStateOf<MutableMap<String, BigDecimal>>(mutableMapOf())
    val categoriesBalance: State<Map<String, BigDecimal>> get() = _categoriesBalance

    fun setBalance(newBalance: BigDecimal) {
        _balance.value = newBalance
    }

    fun setExpenses(newExpenses: MutableList<Expense>) {
        _expenses.value = newExpenses
    }

    fun addExpense(category: String, amount: BigDecimal, date: String) {
        val currentExpenses = _expenses.value.toMutableList()
        currentExpenses.add(Expense(category, amount, date))
        _expenses.value = currentExpenses
    }

    fun addCategory(category: String) {
        _categoriesBalance.value = _categoriesBalance.value.toMutableMap().apply {
            put(category, BigDecimal.ZERO) // Ініціалізуємо нову категорію з балансом 0
        }
    }
}