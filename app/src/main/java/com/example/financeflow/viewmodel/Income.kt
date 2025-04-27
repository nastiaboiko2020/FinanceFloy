package com.example.financeflow.viewmodel

import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import java.math.BigDecimal

@Parcelize
data class Income(
    val source: String,
    val amount: BigDecimal,
    val date: String
) : Parcelable