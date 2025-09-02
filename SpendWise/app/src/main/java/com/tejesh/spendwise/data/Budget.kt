package com.tejesh.spendwise.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "budgets")
data class Budget(
    @PrimaryKey
    val id: String,
    val category: String,
    val amount: Double,
    val yearMonth: String
) {
    constructor() : this("", "", 0.0, "")
}