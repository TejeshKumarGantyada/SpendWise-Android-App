package com.tejesh.spendwise.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface BudgetDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(budget: Budget)

    @Delete
    suspend fun delete(budget: Budget)

    @Query("SELECT * FROM budgets WHERE yearMonth = :yearMonth")
    fun getBudgetsForMonth(yearMonth: String): Flow<List<Budget>>

    // THIS IS THE FIX: Add the missing functions for syncing
    @Query("DELETE FROM budgets")
    suspend fun clearAll()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(budgets: List<Budget>)
}
