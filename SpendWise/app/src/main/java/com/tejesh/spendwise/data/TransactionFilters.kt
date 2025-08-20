package com.tejesh.spendwise.data

// This data class holds the state for our filter sheet
data class TransactionFilters(
    val categories: Set<String> = emptySet(),
    val startDate: Long? = null,
    val endDate: Long? = null,
)