package com.example.financeflow

import android.content.SharedPreferences
import android.util.Log
import com.example.financeflow.viewmodel.Expense
import com.example.financeflow.viewmodel.Income
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

fun loadExpenses(sharedPreferences: SharedPreferences): List<Expense> {
    val gson = Gson()
    val json = sharedPreferences.getString("expenses_history", null) // Змінюємо "expenses" на "expenses_history", щоб відповідати StatisticsActivity
    Log.d("Utils", "Завантажуємо витрати з SharedPreferences: $json")
    return if (json != null) {
        val type = object : TypeToken<List<Expense>>() {}.type
        try {
            val expenses = gson.fromJson<List<Expense>>(json, type) ?: emptyList()
            Log.d("Utils", "Десеріалізовані витрати: $expenses")
            expenses
        } catch (e: Exception) {
            Log.e("Utils", "Помилка при завантаженні витрат: ${e.message}")
            emptyList()
        }
    } else {
        emptyList()
    }
}

fun loadIncomes(sharedPreferences: SharedPreferences): List<Income> {
    val gson = Gson()
    val json = sharedPreferences.getString("incomes", null)
    Log.d("Utils", "Завантажуємо доходи з SharedPreferences: $json")
    return if (json != null) {
        val type = object : TypeToken<List<Income>>() {}.type
        try {
            val incomes = gson.fromJson<List<Income>>(json, type) ?: emptyList()
            Log.d("Utils", "Десеріалізовані доходи: $incomes")
            incomes
        } catch (e: Exception) {
            Log.e("Utils", "Помилка при завантаженні доходів: ${e.message}")
            emptyList()
        }
    } else {
        emptyList()
    }
}

fun saveExpenses(expenses: List<Expense>, sharedPreferences: SharedPreferences) {
    val gson = Gson()
    val json = gson.toJson(expenses)
    Log.d("Utils", "Зберігаємо витрати в SharedPreferences: $json")
    sharedPreferences.edit().putString("expenses_history", json).apply()
}

fun saveIncomes(incomes: List<Income>, sharedPreferences: SharedPreferences) {
    val gson = Gson()
    val json = gson.toJson(incomes)
    Log.d("Utils", "Зберігаємо доходи в SharedPreferences: $json")
    sharedPreferences.edit().putString("incomes", json).apply()
}