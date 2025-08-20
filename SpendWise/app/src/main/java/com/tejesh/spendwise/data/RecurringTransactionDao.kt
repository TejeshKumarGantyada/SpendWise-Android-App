package com.tejesh.spendwise.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface RecurringTransactionDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(recurringTransaction: RecurringTransaction)

    // Add this for efficient bulk inserts during sync
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(recurringTransactions: List<RecurringTransaction>)

    @Delete
    suspend fun delete(recurringTransaction: RecurringTransaction)

    @Query("SELECT * FROM recurring_transactions ORDER BY nextDueDate ASC")
    fun getAll(): Flow<List<RecurringTransaction>>

    @Query("SELECT * FROM recurring_transactions ORDER BY nextDueDate ASC")
    suspend fun getAllList(): List<RecurringTransaction>

    @Query("SELECT * FROM recurring_transactions WHERE nextDueDate <= :today ORDER BY nextDueDate ASC")
    suspend fun getDueTransactions(today: Long): List<RecurringTransaction>

    // Add this for efficient clearing during sync
    @Query("DELETE FROM recurring_transactions")
    suspend fun clearAll()
}
