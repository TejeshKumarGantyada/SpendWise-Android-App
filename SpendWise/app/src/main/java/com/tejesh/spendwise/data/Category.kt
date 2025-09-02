package com.tejesh.spendwise.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "categories")
data class Category(
    @PrimaryKey
    val id: String,
    val name: String,
    val type: String // "Expense" or "Income"
) {
    constructor() : this("", "", "")
}