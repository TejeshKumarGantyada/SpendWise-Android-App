package com.tejesh.spendwise.data

data class DailyTrend(
    val timestamp: Long,
    val totalIncome: Float,
    val totalExpense: Float
)