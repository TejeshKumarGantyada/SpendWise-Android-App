package com.tejesh.spendwise.data

data class MonthlySummary(
    val yearMonth: String, // e.g., "2025-07"
    val totalIncome: Float,
    val totalExpense: Float
)