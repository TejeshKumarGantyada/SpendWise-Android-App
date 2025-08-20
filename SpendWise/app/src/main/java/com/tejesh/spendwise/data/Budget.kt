package com.tejesh.spendwise.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "budgets")
data class Budget(
    @PrimaryKey
    val id: String, // A unique ID, e.g., "Food_2025-08"
    val category: String,
    val amount: Double,
    val yearMonth: String // Format: "YYYY-MM", e.g., "2025-08"
) {
    // Required for Firestore deserialization
    constructor() : this("", "", 0.0, "")
}