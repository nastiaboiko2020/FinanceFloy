package com.example.financeflow

import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import java.text.SimpleDateFormat
import java.util.*

@Parcelize
data class Expense(
    val category: String,
    val amount: Double,
    val date: String = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault()).format(Date())
) : Parcelable
