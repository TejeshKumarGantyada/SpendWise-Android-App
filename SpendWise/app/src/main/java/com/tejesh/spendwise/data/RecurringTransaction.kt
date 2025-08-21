package com.tejesh.spendwise.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "recurring_transactions")
data class RecurringTransaction(
    @PrimaryKey
    val id: String,
    val accountId: String,
    val amount: Double,
    val type: String,
    val category: String,
    val note: String,
    val frequency: String, // "Daily", "Weekly", "Monthly"
    val nextDueDate: Long // Timestamp of the next occurrence
) {
    constructor() : this("", "", 0.0, "", "", "", "Monthly", 0L)
}