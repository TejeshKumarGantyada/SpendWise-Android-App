package com.tejesh.spendwise.data

// This data class holds the combined budget and spending info for the UI
data class BudgetProgress(
    val budget: Budget,
    val spentAmount: Double,
    val progress: Float,
)
