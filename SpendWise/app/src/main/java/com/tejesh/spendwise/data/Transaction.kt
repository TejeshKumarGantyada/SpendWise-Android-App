package com.tejesh.spendwise.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "transactions")
data class Transaction(
    @PrimaryKey
    val id: String,
    val accountId: String,
    val amount: Double,
    val type: String, // expense or income
    val category: String,
    val date: Long,
    val note: String
){
    constructor() : this("", "", 0.0, "", "", 0L, "")
}
