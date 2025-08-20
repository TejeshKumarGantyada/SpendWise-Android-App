package com.tejesh.spendwise.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "categories")
data class Category(
    @PrimaryKey
    val id: String, // A unique ID, e.g., the category name in lowercase
    val name: String,
    val type: String // "Expense" or "Income"
) {
    // Required for Firestore deserialization
    constructor() : this("", "", "")
}