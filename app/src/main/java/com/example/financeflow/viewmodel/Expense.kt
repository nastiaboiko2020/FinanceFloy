package com.example.financeflow.viewmodel

import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import java.text.SimpleDateFormat
import java.util.*
import java.math.BigDecimal

@Parcelize
data class Expense(
    val category: String,
    val amount: BigDecimal,
    val date: String = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault()).format(Date())
) : Parcelable
