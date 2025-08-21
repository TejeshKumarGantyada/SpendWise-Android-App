package com.tejesh.spendwise.data.repository

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.tejesh.spendwise.data.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.util.*
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TransactionRepository @Inject constructor(
    private val transactionDao: TransactionDao,
    private val recurringTransactionDao: RecurringTransactionDao,
    private val budgetDao: BudgetDao,
    private val categoryDao: CategoryDao,
    private val accountDao: AccountDao,
    private val auth: FirebaseAuth,
    private val firestore: FirebaseFirestore,
) {
    private val repoScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val TAG = "TransactionRepository"

    // --- Collection References ---
    private fun getTransactionsCollection() = auth.currentUser?.uid?.let { firestore.collection("users").document(it).collection("transactions") }
    private fun getRecurringTransactionsCollection() = auth.currentUser?.uid?.let { firestore.collection("users").document(it).collection("recurring_transactions") }
    private fun getBudgetsCollection() = auth.currentUser?.uid?.let { firestore.collection("users").document(it).collection("budgets") }
    private fun getCategoriesCollection() = auth.currentUser?.uid?.let { firestore.collection("users").document(it).collection("categories") }
    private fun getAccountsCollection() = auth.currentUser?.uid?.let { firestore.collection("users").document(it).collection("accounts") }

    // --- Live Data Flows (Read from Local DB) ---
    val allTransactions = transactionDao.getAllTransactions()
    val allRecurringTransactions = recurringTransactionDao.getAll()
    val allCategories = categoryDao.getAll()
    val allAccounts = accountDao.getAll()
    fun getBudgetsForMonth(yearMonth: String) = budgetDao.getBudgetsForMonth(yearMonth)
    fun getTransactionById(id: String) = transactionDao.getTransactionById(id)
    suspend fun getAllTransactionsSnapshot(): List<Transaction> = transactionDao.getAllTransactionsList()
    suspend fun getDueRecurringTransactions(today: Long): List<RecurringTransaction> = recurringTransactionDao.getDueTransactions(today)

    suspend fun createDefaultCategories() {
        val defaultExpense = listOf("Food", "Travel", "Shopping", "Entertainment", "Rent", "Bills", "Health")
        val defaultIncome = listOf("Salary", "Freelance", "Gift", "Bonus")

        defaultExpense.forEach { name ->
            val category = Category(
                id = name.lowercase().replace(" ", "_"),
                name = name,
                type = "Expense"
            )
            addCategory(category)
        }

        defaultIncome.forEach { name ->
            val category = Category(
                id = name.lowercase().replace(" ", "_"),
                name = name,
                type = "Income"
            )
            addCategory(category)
        }
        Log.d(TAG, "Created default categories for new user.")
    }

    // --- Write Operations (Write ONLY to Firestore) ---

    suspend fun addTransaction(transaction: Transaction) {
        try {
            getTransactionsCollection()?.add(transaction)?.await()
        } catch (e: Exception) {
            Log.e(TAG, "Error adding transaction", e)
        }
    }

    suspend fun updateTransaction(transaction: Transaction) {
        try {
            getTransactionsCollection()?.document(transaction.id)?.set(transaction)?.await()
        } catch (e: Exception) {
            Log.e(TAG, "Error updating transaction", e)
        }
    }

    suspend fun deleteTransaction(transaction: Transaction) {
        try {
            getTransactionsCollection()?.document(transaction.id)?.delete()?.await()
        } catch (e: Exception) {
            Log.e(TAG, "Error deleting transaction", e)
        }
    }

    suspend fun addRecurringTransaction(recurring: RecurringTransaction) {
        try {
            getRecurringTransactionsCollection()?.document(recurring.id)?.set(recurring)?.await()
        } catch (e: Exception) {
            Log.e(TAG, "Error adding/updating recurring transaction", e)
        }
    }

    suspend fun deleteRecurringTransaction(recurring: RecurringTransaction) {
        try {
            getRecurringTransactionsCollection()?.document(recurring.id)?.delete()?.await()
        } catch (e: Exception) {
            Log.e(TAG, "Error deleting recurring transaction", e)
        }
    }

    suspend fun addBudget(budget: Budget) {
        try {
            getBudgetsCollection()?.document(budget.id)?.set(budget)?.await()
        } catch (e: Exception) {
            Log.e(TAG, "Error adding budget", e)
        }
    }

    suspend fun deleteBudget(budget: Budget) {
        try {
            getBudgetsCollection()?.document(budget.id)?.delete()?.await()
        } catch (e: Exception) {
            Log.e(TAG, "Error deleting budget", e)
        }
    }

    suspend fun addCategory(category: Category) {
        try {
            getCategoriesCollection()?.document(category.id)?.set(category)?.await()
        } catch (e: Exception) {
            Log.e(TAG, "Error adding category", e)
        }
    }

    suspend fun deleteCategory(category: Category) {
        try {
            getCategoriesCollection()?.document(category.id)?.delete()?.await()
        } catch (e: Exception) {
            Log.e(TAG, "Error deleting category", e)
        }
    }

    suspend fun addAccount(account: Account) {
        try {
            getAccountsCollection()?.document(account.id)?.set(account)?.await()
        } catch (e: Exception) {
            Log.e(TAG, "Error adding account", e)
        }
    }

    suspend fun deleteAccount(account: Account) {
        try {
            getAccountsCollection()?.document(account.id)?.delete()?.await()
        } catch (e: Exception) {
            Log.e(TAG, "Error deleting account", e)
        }
    }

    // --- Sync & Listeners ---

    suspend fun syncAllData() {
        if (auth.currentUser == null) return
        Log.d(TAG, "Starting full data sync...")
        syncTransactions()
        syncRecurringTransactions()
        syncBudgets()
        syncCategories() // Sync categories
        syncAccounts()
    }

    fun startListeners() {
        if (auth.currentUser == null) return
        Log.d(TAG, "Starting all Firestore listeners...")
        startListeningForChanges()
        startListeningForRecurringChanges()
        startListeningForBudgetChanges()
        startListeningForCategoryChanges() // Start category listener
        startListeningForAccountChanges()
    }

    suspend fun clearLocalData() {
        transactionDao.clearAll()
        recurringTransactionDao.clearAll()
        budgetDao.clearAll()
        categoryDao.clearAll() // Clear categories
        accountDao.clearAll()
        Log.d(TAG, "Cleared all local data.")
    }

    private suspend fun syncAccounts() {
        try {
            val snapshot = getAccountsCollection()?.get()?.await()
            val accounts = snapshot?.documents?.mapNotNull { doc ->
                doc.toObject(Account::class.java)?.copy(id = doc.id)
            }
            if (accounts != null) {
                accountDao.clearAll()
                accountDao.insertAll(accounts)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error syncing accounts", e)
        }
    }

    private fun startListeningForAccountChanges() {
        getAccountsCollection()?.addSnapshotListener { snapshot, error ->
            if (error != null) {
                Log.e(TAG, "Account listener error", error)
                return@addSnapshotListener
            }
            snapshot?.documentChanges?.forEach { docChange ->
                val account = docChange.document.toObject(Account::class.java).copy(id = docChange.document.id)
                repoScope.launch {
                    when (docChange.type) {
                        com.google.firebase.firestore.DocumentChange.Type.ADDED,
                        com.google.firebase.firestore.DocumentChange.Type.MODIFIED -> accountDao.insert(account)
                        com.google.firebase.firestore.DocumentChange.Type.REMOVED -> accountDao.delete(account)
                    }
                }
            }
        }
    }

    private suspend fun syncTransactions() {
        try {
            val snapshot = getTransactionsCollection()?.get()?.await()
            val items = snapshot?.documents?.mapNotNull { doc -> doc.toObject(Transaction::class.java)?.copy(id = doc.id) }
            if (items != null) {
                transactionDao.clearAll()
                transactionDao.insertAll(items)
                Log.d(TAG, "Synced ${items.size} transactions.")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error syncing transactions", e)
        }
    }

    private suspend fun syncRecurringTransactions() {
        try {
            val snapshot = getRecurringTransactionsCollection()?.get()?.await()
            val items = snapshot?.documents?.mapNotNull { doc -> doc.toObject(RecurringTransaction::class.java)?.copy(id = doc.id) }
            if (items != null) {
                recurringTransactionDao.clearAll()
                recurringTransactionDao.insertAll(items)
                Log.d(TAG, "Synced ${items.size} recurring transactions.")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error syncing recurring transactions", e)
        }
    }

    private suspend fun syncBudgets() {
        try {
            val snapshot = getBudgetsCollection()?.get()?.await()
            val items = snapshot?.documents?.mapNotNull { doc -> doc.toObject(Budget::class.java)?.copy(id = doc.id) }
            if (items != null) {
                budgetDao.clearAll()
                budgetDao.insertAll(items)
                Log.d(TAG, "Synced ${items.size} budgets.")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error syncing budgets", e)
        }
    }

    private suspend fun syncCategories() {
        try {
            val snapshot = getCategoriesCollection()?.get()?.await()
            val items = snapshot?.documents?.mapNotNull { doc -> doc.toObject(Category::class.java)?.copy(id = doc.id) }
            if (items != null) {
                categoryDao.clearAll()
                categoryDao.insertAll(items)
                Log.d(TAG, "Synced ${items.size} categories.")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error syncing categories", e)
        }
    }

    private fun startListeningForChanges() {
        getTransactionsCollection()?.addSnapshotListener { snapshot, error ->
            if (error != null) return@addSnapshotListener
            snapshot?.documentChanges?.forEach { docChange ->
                val item = docChange.document.toObject(Transaction::class.java).copy(id = docChange.document.id)
                repoScope.launch {
                    when (docChange.type) {
                        com.google.firebase.firestore.DocumentChange.Type.ADDED,
                        com.google.firebase.firestore.DocumentChange.Type.MODIFIED -> transactionDao.insertTransaction(item)
                        com.google.firebase.firestore.DocumentChange.Type.REMOVED -> transactionDao.deleteTransaction(item)
                    }
                }
            }
        }
    }

    private fun startListeningForRecurringChanges() {
        getRecurringTransactionsCollection()?.addSnapshotListener { snapshot, error ->
            if (error != null) return@addSnapshotListener
            snapshot?.documentChanges?.forEach { docChange ->
                val item = docChange.document.toObject(RecurringTransaction::class.java).copy(id = docChange.document.id)
                repoScope.launch {
                    when (docChange.type) {
                        com.google.firebase.firestore.DocumentChange.Type.ADDED,
                        com.google.firebase.firestore.DocumentChange.Type.MODIFIED -> recurringTransactionDao.insert(item)
                        com.google.firebase.firestore.DocumentChange.Type.REMOVED -> recurringTransactionDao.delete(item)
                    }
                }
            }
        }
    }

    private fun startListeningForBudgetChanges() {
        getBudgetsCollection()?.addSnapshotListener { snapshot, error ->
            if (error != null) return@addSnapshotListener
            snapshot?.documentChanges?.forEach { docChange ->
                val item = docChange.document.toObject(Budget::class.java).copy(id = docChange.document.id)
                repoScope.launch {
                    when (docChange.type) {
                        com.google.firebase.firestore.DocumentChange.Type.ADDED,
                        com.google.firebase.firestore.DocumentChange.Type.MODIFIED -> budgetDao.insert(item)
                        com.google.firebase.firestore.DocumentChange.Type.REMOVED -> budgetDao.delete(item)
                    }
                }
            }
        }
    }

    private fun startListeningForCategoryChanges() {
        getCategoriesCollection()?.addSnapshotListener { snapshot, error ->
            if (error != null) return@addSnapshotListener
            snapshot?.documentChanges?.forEach { docChange ->
                val item = docChange.document.toObject(Category::class.java).copy(id = docChange.document.id)
                repoScope.launch {
                    when (docChange.type) {
                        com.google.firebase.firestore.DocumentChange.Type.ADDED,
                        com.google.firebase.firestore.DocumentChange.Type.MODIFIED -> categoryDao.insert(item)
                        com.google.firebase.firestore.DocumentChange.Type.REMOVED -> categoryDao.delete(item)
                    }
                }
            }
        }
    }

    // --- Debug & Test Methods ---
    suspend fun manuallyTriggerRecurringTransactions() {
        try {
            Log.d(TAG, "Manually triggering recurring transaction check...")
            val today = Calendar.getInstance().apply { set(Calendar.HOUR_OF_DAY, 23); set(Calendar.MINUTE, 59) }.timeInMillis
            val dueTransactions = getDueRecurringTransactions(today)
            Log.d(TAG, "Found ${dueTransactions.size} due transactions.")
            for (recurring in dueTransactions) {
                val newTransaction = Transaction(
                    id = "",
                    accountId = recurring.accountId,
                    amount = recurring.amount,
                    type = recurring.type,
                    category = recurring.category,
                    date = recurring.nextDueDate,
                    note = recurring.note.ifEmpty { "Recurring: ${recurring.category}" }
                )
                addTransaction(newTransaction)
                val newDueDate = getNextDueDate(recurring.nextDueDate, recurring.frequency)
                val updatedRecurring = recurring.copy(nextDueDate = newDueDate)
                addRecurringTransaction(updatedRecurring)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in manual trigger", e)
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
            else -> calendar.add(Calendar.MONTH, 1)
        }
        calendar.set(Calendar.HOUR_OF_DAY, 0); calendar.set(Calendar.MINUTE, 0); calendar.set(Calendar.SECOND, 0); calendar.set(Calendar.MILLISECOND, 0)
        return calendar.timeInMillis
    }

    // --- NEW: Function to handle transfers ---
    suspend fun addTransfer(
        fromAccount: Account,
        toAccount: Account,
        amount: Double,
        date: Long,
        note: String
    ) {
        val transferId = UUID.randomUUID().toString()
        val transferNote = "Transfer from ${fromAccount.name} to ${toAccount.name}" + if (note.isNotBlank()) " - $note" else ""

        // 1. Create the expense transaction
        val expenseTransaction = Transaction(
            id = "", // Firestore will generate this
            accountId = fromAccount.id,
            amount = amount,
            type = "Expense",
            category = "Transfer", // Special category
            date = date,
            note = transferNote
        )

        // 2. Create the income transaction
        val incomeTransaction = Transaction(
            id = "", // Firestore will generate this
            accountId = toAccount.id,
            amount = amount,
            type = "Income",
            category = "Transfer", // Special category
            date = date,
            note = transferNote
        )

        // 3. Add both to Firestore
        addTransaction(expenseTransaction)
        addTransaction(incomeTransaction)
    }

    // --- NEW: A powerful function to create a loan and its corresponding credit ---
    suspend fun createLoanAndCreditTransaction(
        loanAccount: Account,
        creditToAccountId: String
    ) {
        // 1. Create the new "Loan Taken" account (initialBalance is negative)
        addAccount(loanAccount)

        // 2. Create the corresponding income transaction to credit the other account
        val creditTransaction = Transaction(
            id = "", // Firestore will generate this
            accountId = creditToAccountId,
            // --- THIS IS THE FIX: Use the absolute (positive) value of the loan ---
            amount = kotlin.math.abs(loanAccount.initialBalance),
            type = "Income",
            category = "Loan Credit", // Use a more specific category
            date = System.currentTimeMillis(),
            note = "Loan received from ${loanAccount.name}"
        )
        addTransaction(creditTransaction)
    }
}
