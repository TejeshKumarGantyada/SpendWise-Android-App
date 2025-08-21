package com.tejesh.spendwise.Screens

import android.app.DatePickerDialog
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.tejesh.spendwise.data.Transaction
import com.tejesh.spendwise.data.TransactionFilters
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AllTransactionsScreen(
    viewModel: TransactionViewModel,
    onTransactionClick: (String) -> Unit
) {
    // --- State Collection ---
    val transactions by viewModel.filteredTransactions.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val filters by viewModel.filters.collectAsState()
    val allTransactionsForFilter by viewModel.allTransactions.collectAsState(initial = emptyList())
    val currencySymbol by viewModel.currencySymbol.collectAsState()

    // --- UI State ---
    val scope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState()
    var showFilterSheet by remember { mutableStateOf(false) }

    Scaffold(
        topBar = { TopAppBar(title = { Text("All Transactions") }) }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { viewModel.setSearchQuery(it) },
                    label = { Text("Search note or amount") },
                    modifier = Modifier.weight(1f),
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    singleLine = true
                )
                Spacer(modifier = Modifier.width(8.dp))
                IconButton(onClick = { showFilterSheet = true }) {
                    Icon(Icons.Default.FilterList, contentDescription = "Filter Transactions")
                }
            }
            Spacer(modifier = Modifier.height(16.dp))

            if (transactions.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("No transactions found.")
                }
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(transactions) { transaction ->
                        TransactionItem(
                            transaction = transaction,
                            currencySymbol = currencySymbol,
                            onClick = { onTransactionClick(transaction.id) }
                        )
                    }
                }
            }
        }

        if (showFilterSheet) {
            ModalBottomSheet(onDismissRequest = { showFilterSheet = false }, sheetState = sheetState) {
                FilterSheetContent(
                    allTransactions = allTransactionsForFilter,
                    currentFilters = filters,
                    onApply = { newFilters ->
                        viewModel.applyFilters(newFilters)
                        scope.launch { sheetState.hide() }.invokeOnCompletion { showFilterSheet = false }
                    },
                    onClear = {
                        viewModel.clearFilters()
                        scope.launch { sheetState.hide() }.invokeOnCompletion { showFilterSheet = false }
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FilterSheetContent(
    allTransactions: List<Transaction>,
    currentFilters: TransactionFilters,
    onApply: (TransactionFilters) -> Unit,
    onClear: () -> Unit
) {
    var selectedCategories by remember { mutableStateOf(currentFilters.categories) }
    var startDate by remember { mutableStateOf(currentFilters.startDate) }
    var endDate by remember { mutableStateOf(currentFilters.endDate) }
    val allCategories = allTransactions.map { it.category }.distinct()
    val context = LocalContext.current

    var selectedQuickFilter by remember { mutableStateOf<String?>("All Time") }
    val quickFilters = listOf("All Time", "Today", "Last Week", "Last Month", "Last Year")
    var quickFilterExpanded by remember { mutableStateOf(false) }

    val startCalendar = Calendar.getInstance().apply { timeInMillis = startDate ?: System.currentTimeMillis() }
    val endCalendar = Calendar.getInstance().apply { timeInMillis = endDate ?: System.currentTimeMillis() }

    val startDatePickerDialog = DatePickerDialog(context, { _, year, month, day ->
        startDate = Calendar.getInstance().apply { set(year, month, day, 0, 0) }.timeInMillis
        selectedQuickFilter = null
    }, startCalendar.get(Calendar.YEAR), startCalendar.get(Calendar.MONTH), startCalendar.get(Calendar.DAY_OF_MONTH))

    val endDatePickerDialog = DatePickerDialog(context, { _, year, month, day ->
        endDate = Calendar.getInstance().apply { set(year, month, day, 23, 59) }.timeInMillis
        selectedQuickFilter = null
    }, endCalendar.get(Calendar.YEAR), endCalendar.get(Calendar.MONTH), endCalendar.get(Calendar.DAY_OF_MONTH))

    Column(modifier = Modifier.padding(16.dp).fillMaxWidth()) {
        Text("Filter Transactions", style = MaterialTheme.typography.titleLarge)
        Spacer(modifier = Modifier.height(16.dp))

        ExposedDropdownMenuBox(
            expanded = quickFilterExpanded,
            onExpandedChange = { quickFilterExpanded = !quickFilterExpanded }
        ) {
            OutlinedTextField(
                value = selectedQuickFilter ?: "Custom Range",
                onValueChange = {},
                readOnly = true,
                label = { Text("Quick Filter") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = quickFilterExpanded) },
                modifier = Modifier.menuAnchor().fillMaxWidth()
            )
            ExposedDropdownMenu(
                expanded = quickFilterExpanded,
                onDismissRequest = { quickFilterExpanded = false }
            ) {
                quickFilters.forEach { filter ->
                    DropdownMenuItem(
                        text = { Text(filter) },
                        onClick = {
                            selectedQuickFilter = filter
                            quickFilterExpanded = false
                            val cal = Calendar.getInstance()
                            when (filter) {
                                "All Time" -> { startDate = null; endDate = null }
                                "Today" -> {
                                    startDate = cal.apply { set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0) }.timeInMillis
                                    endDate = cal.apply { set(Calendar.HOUR_OF_DAY, 23); set(Calendar.MINUTE, 59) }.timeInMillis
                                }
                                "Last Week" -> {
                                    cal.add(Calendar.WEEK_OF_YEAR, -1); cal.set(Calendar.DAY_OF_WEEK, cal.firstDayOfWeek); startDate = cal.apply { set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0) }.timeInMillis; cal.add(Calendar.DAY_OF_WEEK, 6); endDate = cal.apply { set(Calendar.HOUR_OF_DAY, 23); set(Calendar.MINUTE, 59) }.timeInMillis
                                }
                                "Last Month" -> {
                                    cal.add(Calendar.MONTH, -1); cal.set(Calendar.DAY_OF_MONTH, 1); startDate = cal.apply { set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0) }.timeInMillis; cal.set(Calendar.DAY_OF_MONTH, cal.getActualMaximum(Calendar.DAY_OF_MONTH)); endDate = cal.apply { set(Calendar.HOUR_OF_DAY, 23); set(Calendar.MINUTE, 59) }.timeInMillis
                                }
                                "Last Year" -> {
                                    cal.add(Calendar.YEAR, -1); cal.set(Calendar.DAY_OF_YEAR, 1); startDate = cal.apply { set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0) }.timeInMillis; cal.set(Calendar.DAY_OF_YEAR, cal.getActualMaximum(Calendar.DAY_OF_YEAR)); endDate = cal.apply { set(Calendar.HOUR_OF_DAY, 23); set(Calendar.MINUTE, 59) }.timeInMillis
                                }
                            }
                        }
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(16.dp))

        Text("Custom Range", style = MaterialTheme.typography.titleMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { startDatePickerDialog.show() }, modifier = Modifier.weight(1f)) {
                Text(startDate?.let { SimpleDateFormat("d MMM yyyy", Locale.getDefault()).format(Date(it)) } ?: "Start Date")
            }
            Button(onClick = { endDatePickerDialog.show() }, modifier = Modifier.weight(1f)) {
                Text(endDate?.let { SimpleDateFormat("d MMM yyyy", Locale.getDefault()).format(Date(it)) } ?: "End Date")
            }
        }
        Spacer(modifier = Modifier.height(16.dp))

        Text("Categories", style = MaterialTheme.typography.titleMedium)
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            allCategories.take(4).forEach { category ->
                FilterChip(
                    selected = category in selectedCategories,
                    onClick = {
                        selectedCategories = if (category in selectedCategories) selectedCategories - category else selectedCategories + category
                    },
                    label = { Text(category) }
                )
            }
        }
        Spacer(modifier = Modifier.height(24.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { selectedQuickFilter = "All Time"; onClear() }, modifier = Modifier.weight(1f)) { Text("Clear") }
            Button(onClick = { onApply(TransactionFilters(selectedCategories, startDate, endDate)) }, modifier = Modifier.weight(1f)) { Text("Apply") }
        }
    }
}

