package com.tejesh.spendwise.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "accounts")
data class Account(
    @PrimaryKey
    val id: String,
    val name: String,
    val type: String, // "Bank", "Cash", "Credit Card", "Loan"
    val initialBalance: Double = 0.0,
    val creditLimit: Double? = null // NEW: Optional field for credit cards
) {
    constructor() : this("", "", "Bank", 0.0, null)
}
