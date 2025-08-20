package com.tejesh.spendwise.Screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.Firebase
import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.functions.functions
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
    val error: String? = null
)

@HiltViewModel
class TransactionViewModel @Inject constructor(
    private val transactionRepository: TransactionRepository,
    private val settingsRepository: SettingsRepository,
) : ViewModel() {

    private val functions: FirebaseFunctions = Firebase.functions

    // --- NEW: StateFlow for the AI Insight ---
    private val _aiInsightState = MutableStateFlow(AiInsightState())
    val aiInsightState: StateFlow<AiInsightState> = _aiInsightState.asStateFlow()

    init {
        viewModelScope.launch {
            transactionRepository.allTransactions
                .debounce(1000) // Wait 1 second for changes to settle
                .distinctUntilChanged() // Only proceed if the list is actually different
                .collectLatest { transactions -> // Use collectLatest to avoid old requests
                    // Only get an insight if the list is not empty
                    if (transactions.isNotEmpty()) {
                        getFinancialInsight()
                    }
                }
        }
    }

    fun initializeUserSession() {
        viewModelScope.launch {
            transactionRepository.syncAllData()
            transactionRepository.startListeners()
        }
    }



    // --- STATE MANAGEMENT ---
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery

    private val _filters = MutableStateFlow(TransactionFilters())
    val filters: StateFlow<TransactionFilters> = _filters

    private val _currentYearMonth = MutableStateFlow(
        SimpleDateFormat("yyyy-MM", Locale.getDefault()).format(Date())
    )
    val currentYearMonth: StateFlow<String> = _currentYearMonth

    // --- LIVE DATA FLOWS (Source of Truth) ---
    val allTransactions: Flow<List<Transaction>> = transactionRepository.allTransactions
    val allRecurringTransactions: Flow<List<RecurringTransaction>> = transactionRepository.allRecurringTransactions
    val allCategories: StateFlow<List<Category>> = transactionRepository.allCategories
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    @OptIn(ExperimentalCoroutinesApi::class)
    val budgetsForCurrentMonth: Flow<List<Budget>> = _currentYearMonth.flatMapLatest { yearMonth ->
        transactionRepository.getBudgetsForMonth(yearMonth)
    }

    val currencySymbol: StateFlow<String> = settingsRepository.currencySymbolFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "₹")

    // --- DERIVED STATE (for UI) ---
    val filteredTransactions: StateFlow<List<Transaction>> =
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


    // THIS IS THE FIX: topBudgets is now defined after budgetProgress
    val topBudgets: StateFlow<List<BudgetProgress>> = budgetProgress
        .map { list ->
            list.sortedByDescending { it.spentAmount }.take(3)
        }
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

    private fun getTotals(transactions: List<Transaction>, startDate: Long, endDate: Long, type: String): Double {
        return transactions
            .filter { it.type == type && it.date >= startDate && it.date <= endDate }
            .sumOf { it.amount }
    }

    val todaysIncome: StateFlow<Double> = allTransactions.map { list ->
        val cal = Calendar.getInstance()
        val start = cal.apply { set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0) }.timeInMillis
        val end = cal.apply { set(Calendar.HOUR_OF_DAY, 23); set(Calendar.MINUTE, 59) }.timeInMillis
        getTotals(list, start, end, "Income")
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.0)

    val todaysExpense: StateFlow<Double> = allTransactions.map { list ->
        val cal = Calendar.getInstance()
        val start = cal.apply { set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0) }.timeInMillis
        val end = cal.apply { set(Calendar.HOUR_OF_DAY, 23); set(Calendar.MINUTE, 59) }.timeInMillis
        getTotals(list, start, end, "Expense")
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.0)

    val weeklyIncome: StateFlow<Double> = allTransactions.map { list ->
        val cal = Calendar.getInstance()
        cal.set(Calendar.DAY_OF_WEEK, cal.firstDayOfWeek)
        val start = cal.apply { set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0) }.timeInMillis
        cal.add(Calendar.DAY_OF_WEEK, 6)
        val end = cal.apply { set(Calendar.HOUR_OF_DAY, 23); set(Calendar.MINUTE, 59) }.timeInMillis
        getTotals(list, start, end, "Income")
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.0)

    val weeklyExpense: StateFlow<Double> = allTransactions.map { list ->
        val cal = Calendar.getInstance()
        cal.set(Calendar.DAY_OF_WEEK, cal.firstDayOfWeek)
        val start = cal.apply { set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0) }.timeInMillis
        cal.add(Calendar.DAY_OF_WEEK, 6)
        val end = cal.apply { set(Calendar.HOUR_OF_DAY, 23); set(Calendar.MINUTE, 59) }.timeInMillis
        getTotals(list, start, end, "Expense")
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.0)

    val monthlyIncome: StateFlow<Double> = allTransactions.map { list ->
        val cal = Calendar.getInstance()
        cal.set(Calendar.DAY_OF_MONTH, 1)
        val start = cal.apply { set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0) }.timeInMillis
        cal.set(Calendar.DAY_OF_MONTH, cal.getActualMaximum(Calendar.DAY_OF_MONTH))
        val end = cal.apply { set(Calendar.HOUR_OF_DAY, 23); set(Calendar.MINUTE, 59) }.timeInMillis
        getTotals(list, start, end, "Income")
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.0)

    val monthlyExpense: StateFlow<Double> = allTransactions.map { list ->
        val cal = Calendar.getInstance()
        cal.set(Calendar.DAY_OF_MONTH, 1)
        val start = cal.apply { set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0) }.timeInMillis
        cal.set(Calendar.DAY_OF_MONTH, cal.getActualMaximum(Calendar.DAY_OF_MONTH))
        val end = cal.apply { set(Calendar.HOUR_OF_DAY, 23); set(Calendar.MINUTE, 59) }.timeInMillis
        getTotals(list, start, end, "Expense")
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.0)

    val monthlySummary: StateFlow<List<MonthlySummary>> = allTransactions.map { list ->
        list.groupBy { SimpleDateFormat("yyyy-MM", Locale.getDefault()).format(Date(it.date)) }
            .map { (month, txs) -> MonthlySummary(month, txs.filter { it.type == "Income" }.sumOf { it.amount }.toFloat(), txs.filter { it.type == "Expense" }.sumOf { it.amount }.toFloat()) }
            .sortedBy { it.yearMonth }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val dailyTrend: StateFlow<List<DailyTrend>> = allTransactions.map { list ->
        val thirtyDaysAgo = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -30) }.timeInMillis
        list.filter { it.date >= thirtyDaysAgo }
            .groupBy { Calendar.getInstance().apply { timeInMillis = it.date; set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0) }.timeInMillis }
            .map { (ts, txs) -> DailyTrend(ts, txs.filter { it.type == "Income" }.sumOf { it.amount }.toFloat(), txs.filter { it.type == "Expense" }.sumOf { it.amount }.toFloat()) }
            .sortedBy { it.timestamp }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val expenseByCategory: StateFlow<Map<String, Double>> = allTransactions.map { list ->
        list.filter { it.type == "Expense" }.groupBy { it.category }.mapValues { it.value.sumOf { tx -> tx.amount } }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    val incomeBySource: StateFlow<Map<String, Double>> = allTransactions.map { list ->
        list.filter { it.type == "Income" }.groupBy { it.category }.mapValues { it.value.sumOf { tx -> tx.amount } }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    val spendingAlert: StateFlow<String?> = allTransactions.map { transactions ->
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
        val monthStartCal = calendar.clone() as Calendar
        monthStartCal.set(Calendar.DAY_OF_MONTH, 1)
        val monthEndCal = calendar.clone() as Calendar
        monthEndCal.set(Calendar.DAY_OF_MONTH, calendar.getActualMaximum(Calendar.DAY_OF_MONTH))
        return getTotals(transactions, monthStartCal.timeInMillis, monthEndCal.timeInMillis, type)
    }

    // --- PUBLIC FUNCTIONS ---
    fun clearUserSession() { viewModelScope.launch { transactionRepository.clearLocalData() } }
    fun setSearchQuery(query: String) { _searchQuery.value = query }
    fun applyFilters(newFilters: TransactionFilters) { _filters.value = newFilters }
    fun clearFilters() { _filters.value = TransactionFilters() }
    fun changeMonth(amount: Int) {
        val cal = Calendar.getInstance(); val sdf = SimpleDateFormat("yyyy-MM", Locale.getDefault()); val date = sdf.parse(_currentYearMonth.value) ?: Date(); cal.time = date; cal.add(Calendar.MONTH, amount); _currentYearMonth.value = sdf.format(cal.time)
    }
    fun addBudget(budget: Budget) = viewModelScope.launch { transactionRepository.addBudget(budget) }
    fun deleteBudget(budget: Budget) = viewModelScope.launch { transactionRepository.deleteBudget(budget) }
    fun addTransaction(transaction: Transaction) = viewModelScope.launch { transactionRepository.addTransaction(transaction) }
    fun updateTransaction(transaction: Transaction) = viewModelScope.launch { transactionRepository.updateTransaction(transaction) }
    fun deleteTransaction(transaction: Transaction) = viewModelScope.launch { transactionRepository.deleteTransaction(transaction) }
    fun getTransactionById(id: String): Flow<Transaction?> = transactionRepository.getTransactionById(id)
    fun addRecurringTransaction(recurring: RecurringTransaction) = viewModelScope.launch { transactionRepository.addRecurringTransaction(recurring) }
    fun deleteRecurringTransaction(recurring: RecurringTransaction) = viewModelScope.launch { transactionRepository.deleteRecurringTransaction(recurring) }
    fun addCategory(category: Category) = viewModelScope.launch { transactionRepository.addCategory(category) }
    fun deleteCategory(category: Category) = viewModelScope.launch { transactionRepository.deleteCategory(category) }
    suspend fun exportTransactionsToCsv(): String {
        val transactions = transactionRepository.getAllTransactionsSnapshot(); val header = "ID,Date,Type,Category,Amount,Note\n"; val csvData = StringBuilder().append(header); val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()); transactions.forEach { val dateStr = dateFormat.format(Date(it.date)); csvData.append("${it.id},$dateStr,${it.type},${it.category},${it.amount},\"${it.note}\"\n") }; return csvData.toString()
    }
    fun getFinancialInsight() {
        viewModelScope.launch {
            _aiInsightState.value = AiInsightState(isLoading = true)
            try {
                // 1. Get the last 30 days of transactions to send for analysis
                val recentTransactions = transactionRepository.getAllTransactionsSnapshot()
                    .filter {
                        it.date >= Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -30) }.timeInMillis
                    }
                    // We only need a few fields for the analysis
                    .map { mapOf("amount" to it.amount, "type" to it.type, "category" to it.category) }

                if (recentTransactions.size == 0) { // Require a minimum number of transactions
                    _aiInsightState.value = AiInsightState(insight = "Keep adding transactions to unlock your first Smart Insight!")
                    return@launch
                }

                val data = hashMapOf("transactions" to recentTransactions)

                // 2. Call the backend function
                val result = Firebase.functions.getHttpsCallable("getFinancialInsight").call(data).await()
                val insightText = (result.data as? Map<*, *>)?.get("insight") as? String

                // 3. Update the state with the result
                _aiInsightState.value = AiInsightState(insight = insightText)

            } catch (e: Exception) {
                e.printStackTrace()
                _aiInsightState.value = AiInsightState(error = "Could not generate insight.")
            }
        }
    }

}
