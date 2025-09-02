package com.tejesh.spendwise.Screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.functions.ktx.functions
import com.google.firebase.ktx.Firebase
import com.tejesh.spendwise.data.*
import com.tejesh.spendwise.data.repository.SettingsRepository
import com.tejesh.spendwise.data.repository.TransactionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject

data class AiInsightState(
    val isLoading: Boolean = false,
    val insight: String? = null,
    val error: String? = null,
)

@HiltViewModel
class TransactionViewModel @Inject constructor(
    private val transactionRepository: TransactionRepository,
    private val settingsRepository: SettingsRepository,
) : ViewModel() {

    // --- STATE MANAGEMENT ---
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery

    private val _filters = MutableStateFlow(TransactionFilters())
    val filters: StateFlow<TransactionFilters> = _filters

    private val _currentYearMonth = MutableStateFlow(
        SimpleDateFormat("yyyy-MM", Locale.getDefault()).format(Date())
    )
    val currentYearMonth: StateFlow<String> = _currentYearMonth

    private val _aiInsightState = MutableStateFlow(AiInsightState())
    val aiInsightState: StateFlow<AiInsightState> = _aiInsightState.asStateFlow()

    // --- LIVE DATA FLOWS (Source of Truth from Repository) ---
    val allTransactions: Flow<List<Transaction>> = transactionRepository.allTransactions
    val allRecurringTransactions: Flow<List<RecurringTransaction>> = transactionRepository.allRecurringTransactions
    val allCategories: StateFlow<List<Category>> = transactionRepository.allCategories
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val allAccounts: StateFlow<List<Account>> = transactionRepository.allAccounts
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val currencySymbol: StateFlow<String> = settingsRepository.currencySymbolFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "₹")

        @OptIn(ExperimentalCoroutinesApi::class)
        val budgetsForCurrentMonth: Flow<List<Budget>> = _currentYearMonth.flatMapLatest { yearMonth ->  // user for month change in budgets screen
            transactionRepository.getBudgetsForMonth(yearMonth)
        }

    // --- DERIVED STATE (Calculated flows for the UI) ---

    private val transactionsForAnalytics: Flow<List<Transaction>> = allTransactions
        .map { list -> list.filter { it.category != "Transfer" && it.category != "Loan Credit" } }

    val accountsForForms: StateFlow<List<Account>> = allAccounts.map { accounts ->
        accounts.filter { it.type != "Loan Taken" }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    // viewmodelscope -> state will be live till viewmodel exists
    // 5000 -> upstream will stop after 5 sec once the ui stops observing



    val filteredTransactions: StateFlow<List<Transaction>> =
        // combine -> combine multiple flows into one
        combine(allTransactions, _searchQuery, _filters) { transactions, query, filters ->
            val searchedList = if (query.isBlank()) {
                transactions
            } else {
                transactions.filter {
                    it.note.contains(query, ignoreCase = true) || it.amount.toString().contains(query)
                }
            }
            searchedList.filter { transaction ->
                val afterStartDate = filters.startDate?.let { transaction.date >= it } ?: true
                val beforeEndDate = filters.endDate?.let { transaction.date <= it } ?: true
                val inCategory = if (filters.categories.isEmpty()) true else transaction.category in filters.categories
                afterStartDate && beforeEndDate && inCategory
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val accountBalances: StateFlow<List<AccountBalance>> =
        combine(allAccounts, allTransactions) { accounts, transactions ->
            accounts.map { account ->
                val income = transactions
                    .filter { it.accountId == account.id && it.type == "Income" }
                    .sumOf { it.amount }
                val expense = transactions
                    .filter { it.accountId == account.id && it.type == "Expense" }
                    .sumOf { it.amount }

                val currentBalance = account.initialBalance + income - expense

                var availableCredit: Double? = null
                if (account.type == "Credit Card" && account.creditLimit != null) {
                    availableCredit = account.creditLimit + currentBalance
                }

                AccountBalance(account, currentBalance, availableCredit)
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val netWorth: StateFlow<Double> = accountBalances.map { list ->
        list.sumOf { it.currentBalance }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.0)

    val assetAccounts: StateFlow<List<AccountBalance>> = accountBalances.map { accounts ->
        accounts.filter { it.account.type != "Loan Taken" }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val budgetProgress: StateFlow<List<BudgetProgress>> =
        combine(budgetsForCurrentMonth, allTransactions) { budgets, transactions ->
            val transactionsForMonth = transactions.filter {
                SimpleDateFormat("yyyy-MM", Locale.getDefault()).format(Date(it.date)) == _currentYearMonth.value
            }
            budgets.map { budget ->
                val spent = transactionsForMonth
                    .filter { it.type == "Expense" && it.category == budget.category }
                    .sumOf { it.amount }
                val progress = if (budget.amount > 0) (spent / budget.amount).toFloat() else 0f
                BudgetProgress(budget, spent, progress)
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val topBudgets: StateFlow<List<BudgetProgress>> = budgetProgress
        .map { list -> list.sortedByDescending { it.spentAmount }.take(3) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val expenseCategories: StateFlow<List<Category>> = allCategories.map { list ->
        list.filter { it.type == "Expense" }.sortedBy { it.name }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val incomeCategories: StateFlow<List<Category>> = allCategories.map { list ->
        list.filter { it.type == "Income" }.sortedBy { it.name }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val currentMonthDisplay: StateFlow<String> = _currentYearMonth.map {
        val date = SimpleDateFormat("yyyy-MM", Locale.getDefault()).parse(it) ?: Date()
        SimpleDateFormat("MMMM yyyy", Locale.getDefault()).format(date)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "")

    private fun getTotals(transactions: List<Transaction>, startDate: Long, endDate: Long, type: String): Double =
        transactions.filter { it.type == type && it.date >= startDate && it.date <= endDate }.sumOf { it.amount }

    val todaysIncome: StateFlow<Double> = transactionsForAnalytics.map { list ->
        val cal = Calendar.getInstance(); val start = cal.apply { set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0) }.timeInMillis; val end = cal.apply { set(Calendar.HOUR_OF_DAY, 23); set(Calendar.MINUTE, 59) }.timeInMillis; getTotals(list, start, end, "Income")
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.0)

    val todaysExpense: StateFlow<Double> = transactionsForAnalytics.map { list ->
        val cal = Calendar.getInstance(); val start = cal.apply { set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0) }.timeInMillis; val end = cal.apply { set(Calendar.HOUR_OF_DAY, 23); set(Calendar.MINUTE, 59) }.timeInMillis; getTotals(list, start, end, "Expense")
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.0)

    val weeklyIncome: StateFlow<Double> = transactionsForAnalytics.map { list ->
        val cal = Calendar.getInstance(); cal.set(Calendar.DAY_OF_WEEK, cal.firstDayOfWeek); val start = cal.apply { set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0) }.timeInMillis; cal.add(Calendar.DAY_OF_WEEK, 6); val end = cal.apply { set(Calendar.HOUR_OF_DAY, 23); set(Calendar.MINUTE, 59) }.timeInMillis; getTotals(list, start, end, "Income")
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.0)

    val weeklyExpense: StateFlow<Double> = transactionsForAnalytics.map { list ->
        val cal = Calendar.getInstance(); cal.set(Calendar.DAY_OF_WEEK, cal.firstDayOfWeek); val start = cal.apply { set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0) }.timeInMillis; cal.add(Calendar.DAY_OF_WEEK, 6); val end = cal.apply { set(Calendar.HOUR_OF_DAY, 23); set(Calendar.MINUTE, 59) }.timeInMillis; getTotals(list, start, end, "Expense")
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.0)

    val monthlyIncome: StateFlow<Double> = transactionsForAnalytics.map { list ->
        val cal = Calendar.getInstance(); cal.set(Calendar.DAY_OF_MONTH, 1); val start = cal.apply { set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0) }.timeInMillis; cal.set(Calendar.DAY_OF_MONTH, cal.getActualMaximum(Calendar.DAY_OF_MONTH)); val end = cal.apply { set(Calendar.HOUR_OF_DAY, 23); set(Calendar.MINUTE, 59) }.timeInMillis; getTotals(list, start, end, "Income")
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.0)

    val monthlyExpense: StateFlow<Double> = transactionsForAnalytics.map { list ->
        val cal = Calendar.getInstance(); cal.set(Calendar.DAY_OF_MONTH, 1); val start = cal.apply { set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0) }.timeInMillis; cal.set(Calendar.DAY_OF_MONTH, cal.getActualMaximum(Calendar.DAY_OF_MONTH)); val end = cal.apply { set(Calendar.HOUR_OF_DAY, 23); set(Calendar.MINUTE, 59) }.timeInMillis; getTotals(list, start, end, "Expense")
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.0)

    val monthlySummary: StateFlow<List<MonthlySummary>> = transactionsForAnalytics.map { list ->
        list.groupBy { SimpleDateFormat("yyyy-MM", Locale.getDefault()).format(Date(it.date)) }.map { (month, txs) -> MonthlySummary(month, txs.filter { it.type == "Income" }.sumOf { it.amount }.toFloat(), txs.filter { it.type == "Expense" }.sumOf { it.amount }.toFloat()) }.sortedBy { it.yearMonth }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val dailyTrend: StateFlow<List<DailyTrend>> = transactionsForAnalytics.map { list ->
        val thirtyDaysAgo = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -30) }.timeInMillis; list.filter { it.date >= thirtyDaysAgo }.groupBy { Calendar.getInstance().apply { timeInMillis = it.date; set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0) }.timeInMillis }.map { (ts, txs) -> DailyTrend(ts, txs.filter { it.type == "Income" }.sumOf { it.amount }.toFloat(), txs.filter { it.type == "Expense" }.sumOf { it.amount }.toFloat()) }.sortedBy { it.timestamp }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val expenseByCategory: StateFlow<Map<String, Double>> = transactionsForAnalytics.map { list ->
        list.filter { it.type == "Expense" }.groupBy { it.category }.mapValues { it.value.sumOf { tx -> tx.amount } }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    val incomeBySource: StateFlow<Map<String, Double>> = transactionsForAnalytics.map { list ->
        list.filter { it.type == "Income" }.groupBy { it.category }.mapValues { it.value.sumOf { tx -> tx.amount } }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    val spendingAlert: StateFlow<String?> = transactionsForAnalytics.map { transactions ->
        val currentMonthCalendar = Calendar.getInstance()
        val previousMonthCalendar = Calendar.getInstance().apply { add(Calendar.MONTH, -1) }
        val currentMonthExpense = getTotalsForMonth(transactions, currentMonthCalendar, "Expense")
        val previousMonthExpense = getTotalsForMonth(transactions, previousMonthCalendar, "Expense")
        if (previousMonthExpense > 0 && currentMonthExpense > (previousMonthExpense * 1.2)) {
            val increasePercent = ((currentMonthExpense / previousMonthExpense - 1) * 100).toInt()
            "Spending Alert: You've spent $increasePercent% more than last month!"
        } else {
            null
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    private fun getTotalsForMonth(transactions: List<Transaction>, calendar: Calendar, type: String): Double {
        val monthStartCal = calendar.clone() as Calendar; monthStartCal.set(Calendar.DAY_OF_MONTH, 1); val monthEndCal = calendar.clone() as Calendar; monthEndCal.set(Calendar.DAY_OF_MONTH, calendar.getActualMaximum(Calendar.DAY_OF_MONTH)); return getTotals(transactions, monthStartCal.timeInMillis, monthEndCal.timeInMillis, type)
    }

    // --- PUBLIC FUNCTIONS (for UI to call) ---

    fun initializeUserSession() {
        viewModelScope.launch {
            transactionRepository.syncAllData(); transactionRepository.startListeners()
        }
    }

    fun clearUserSession() {
        viewModelScope.launch {
            transactionRepository.clearLocalData()
        }
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun applyFilters(newFilters: TransactionFilters) {
        _filters.value = newFilters
    }

    fun clearFilters() {
        _filters.value = TransactionFilters()
    }

    fun changeMonth(amount: Int) {
        val cal = Calendar.getInstance();
        val sdf = SimpleDateFormat("yyyy-MM", Locale.getDefault());
        val date = sdf.parse(_currentYearMonth.value) ?: Date(); cal.time = date;
        cal.add(Calendar.MONTH, amount);
        _currentYearMonth.value = sdf.format(cal.time)
    }

    fun addBudget(budget: Budget) = viewModelScope.launch {
        transactionRepository.addBudget(budget)
    }

    fun deleteBudget(budget: Budget) = viewModelScope.launch {
        transactionRepository.deleteBudget(budget)
    }

    fun addTransaction(transaction: Transaction) = viewModelScope.launch {
        transactionRepository.addTransaction(transaction)
    }

    fun updateTransaction(transaction: Transaction) = viewModelScope.launch {
        transactionRepository.updateTransaction(transaction)
    }

    fun deleteTransaction(transaction: Transaction) = viewModelScope.launch {
        transactionRepository.deleteTransaction(transaction)
    }

    fun getTransactionById(id: String): Flow<Transaction?> = transactionRepository.getTransactionById(id)

    fun addRecurringTransaction(recurring: RecurringTransaction) = viewModelScope.launch {
        transactionRepository.addRecurringTransaction(recurring)
    }

    fun deleteRecurringTransaction(recurring: RecurringTransaction) = viewModelScope.launch {
        transactionRepository.deleteRecurringTransaction(recurring)
    }

    fun addCategory(category: Category) = viewModelScope.launch {
        transactionRepository.addCategory(category)
    }

    fun deleteCategory(category: Category) = viewModelScope.launch {
        transactionRepository.deleteCategory(category)
    }

    fun addAccount(account: Account) = viewModelScope.launch {
        transactionRepository.addAccount(account)
    }

    fun deleteAccount(account: Account) = viewModelScope.launch {
        transactionRepository.deleteAccount(account)
    }

    fun addTransfer(
        fromAccountId: String,
        toAccountId: String,
        amount: Double,
        date: Long,
        note: String,
        onResult: (Boolean, String) -> Unit
    ) {
        viewModelScope.launch {
            val fromAccountBalance = accountBalances.value.find { it.account.id == fromAccountId }
            val toAccountBalance = accountBalances.value.find { it.account.id == toAccountId }
            if (fromAccountBalance == null || toAccountBalance == null) {
                onResult(false, "Selected account not found."); return@launch
            }

            // Standard overdraft validation
            if (fromAccountBalance.account.type != "Credit Card" && fromAccountBalance.currentBalance < amount) {
                onResult(false, "Insufficient funds in ${fromAccountBalance.account.name}."); return@launch
            }

            val toAccount = toAccountBalance.account

            // --- NEW: Stricter validation ONLY for "Loan Taken" ---
            if (toAccount.type == "Loan Taken") {
                val amountDue = -toAccountBalance.currentBalance
                if (amount > amountDue) {
                    onResult(false, "Payment amount cannot be greater than the loan amount due."); return@launch
                }

                transactionRepository.addTransfer(fromAccountBalance.account, toAccount, amount, date, note)

                // Auto-delete ONLY if the loan is fully paid off
                if ((toAccountBalance.currentBalance + amount) == 0.0) {
                    transactionRepository.deleteAccount(toAccount)
                }
            } else {
                // For all other account types (including Credit Card), just perform the transfer
                transactionRepository.addTransfer(fromAccountBalance.account, toAccount, amount, date, note)
            }

            onResult(true, "Transfer successful!")
        }
    }

    fun addLoan(
        loanAccount: Account,
        creditToAccountId: String
    ) {
        viewModelScope.launch {
            val finalCreditToAccountId = if (creditToAccountId.isNotBlank()) {
                creditToAccountId
            } else {
                val cashAccountId = UUID.randomUUID().toString()
                val cashAccount = Account(
                    id = cashAccountId,
                    name = "Cash",
                    type = "Cash"
                )
                transactionRepository.addAccount(cashAccount)
                cashAccountId
            }
            transactionRepository.createLoanAndCreditTransaction(loanAccount, finalCreditToAccountId)
        }
    }

    suspend fun exportTransactionsToCsv(): String {
        val transactions = transactionRepository.getAllTransactionsSnapshot();
        val header = "ID,Date,Type,Category,Amount,Note\n";
        val csvData = StringBuilder().append(header);
        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());
        transactions.forEach {
            val dateStr = dateFormat.format(Date(it.date));
            csvData.append("${it.id},$dateStr,${it.type},${it.category},${it.amount},\"${it.note}\"\n")
        };
        return csvData.toString()
    }

    fun getFinancialInsight() {
        viewModelScope.launch {
            _aiInsightState.value = AiInsightState(isLoading = true)
            try {
                // 1. Get the current state of the account balances.
                val currentAccountBalances = accountBalances.value
                if (currentAccountBalances.isEmpty()) {
                    _aiInsightState.value = AiInsightState(insight = "Add an account to get your first Smart Insight!")
                    return@launch
                }
                // Create a simplified list of account data to send to the AI.
                val accountsData = currentAccountBalances.map {
                    mapOf(
                        "name" to it.account.name,
                        "type" to it.account.type,
                        "balance" to it.currentBalance,
                        "limit" to it.account.creditLimit
                    )
                }

                // 2. Get the last 30 days of transactions, EXCLUDING transfers.
                val recentTransactions = transactionRepository.getAllTransactionsSnapshot()
                    .filter {
                        it.category != "Transfer" && it.category != "Loan Credit" &&
                                it.date >= Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -30) }.timeInMillis
                    }
                    .map { mapOf("amount" to it.amount, "type" to it.type, "category" to it.category) }

                if (recentTransactions.size == 0) {
                    _aiInsightState.value = AiInsightState(insight = "Keep adding transactions to unlock your first Smart Insight!")
                    return@launch
                }

                // 3. Combine both into a single payload.
                val data = hashMapOf(
                    "accounts" to accountsData,
                    "transactions" to recentTransactions
                )

                // 4. Call the backend function with the new, richer data.
                val result = Firebase.functions.getHttpsCallable("getFinancialInsight").call(data).await()
                val insightText = (result.data as? Map<*, *>)?.get("insight") as? String

                _aiInsightState.value = AiInsightState(insight = insightText)

            } catch (e: Exception) {
                e.printStackTrace()
                _aiInsightState.value = AiInsightState(error = "Could not generate insight.")
            }
        }
    }
}
