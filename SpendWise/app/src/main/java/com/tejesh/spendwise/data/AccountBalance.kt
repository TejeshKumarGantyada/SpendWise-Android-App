package com.tejesh.spendwise.data

data class AccountBalance(
    val account: Account,
    val currentBalance: Double,
    val availableCredit: Double? = null // NEW: Optional field for UI
)
