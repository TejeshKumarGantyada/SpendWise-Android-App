package com.tejesh.spendwise.workers

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.tejesh.spendwise.data.RecurringTransaction
import com.tejesh.spendwise.data.Transaction
import com.tejesh.spendwise.data.repository.TransactionRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.util.*

@HiltWorker
class RecurringTransactionWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted workerParams: WorkerParameters,
    private val transactionRepository: TransactionRepository,
) : CoroutineWorker(context, workerParams) {

    companion object {
        private const val TAG = "RecurringWorker"
    }

    override suspend fun doWork(): Result {
        try {
            Log.d(TAG, "Starting recurring transaction check")

            val today = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, 23)
                set(Calendar.MINUTE, 59)
            }.timeInMillis

            val dueTransactions = transactionRepository.getDueRecurringTransactions(today)
            Log.d(TAG, "Found ${dueTransactions.size} due recurring transactions")

            for (recurring in dueTransactions) {
                try {
                    // 1. Create a new transaction from the rule
                    val newTransaction = Transaction(
                        id = "", // Firestore will generate this
                        amount = recurring.amount,
                        type = recurring.type,
                        category = recurring.category,
                        date = recurring.nextDueDate,
                        note = recurring.note.ifEmpty { "Recurring: ${recurring.category}" },
                    )
                    transactionRepository.addTransaction(newTransaction)
                    Log.d(TAG, "Created transaction for: ${recurring.category}")

                    // 2. Calculate the next due date and update the rule
                    val newDueDate = getNextDueDate(recurring.nextDueDate, recurring.frequency)
                    val updatedRecurring = recurring.copy(nextDueDate = newDueDate)

                    // THIS IS THE FIX: Use addRecurringTransaction to update the rule,
                    // as it handles both creating and updating.
                    transactionRepository.addRecurringTransaction(updatedRecurring)

                    Log.d(TAG, "Updated next due date for ${recurring.category} to ${Date(newDueDate)}")
                } catch (e: Exception) {
                    Log.e(TAG, "Error processing item ${recurring.id}", e)
                }
            }

            Log.d(TAG, "Successfully processed ${dueTransactions.size} recurring transactions")
            return Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Worker failed", e)
            return Result.retry()
        }
    }

    private fun getNextDueDate(currentDueDate: Long, frequency: String): Long {
        val calendar = Calendar.getInstance()
        calendar.timeInMillis = currentDueDate

        when (frequency.lowercase()) {
            "daily" -> calendar.add(Calendar.DAY_OF_YEAR, 1)
            "weekly" -> calendar.add(Calendar.WEEK_OF_YEAR, 1)
            "monthly" -> calendar.add(Calendar.MONTH, 1)
            "yearly" -> calendar.add(Calendar.YEAR, 1)
            else -> {
                Log.w(TAG, "Unknown frequency: '$frequency', defaulting to monthly")
                calendar.add(Calendar.MONTH, 1)
            }
        }

        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)

        return calendar.timeInMillis
    }
}
