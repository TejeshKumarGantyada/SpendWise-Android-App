package com.tejesh.spendwise.Screens

import android.app.DatePickerDialog
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.google.firebase.firestore.ktx.BuildConfig
import com.tejesh.spendwise.data.RecurringTransaction
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecurringScreen(viewModel: TransactionViewModel) {
    val recurringTransactions by viewModel.allRecurringTransactions.collectAsState(initial = emptyList())
    val currencySymbol by viewModel.currencySymbol.collectAsState()
    val sheetState = rememberModalBottomSheetState()
    val scope = rememberCoroutineScope()
    var showBottomSheet by remember { mutableStateOf(false) }
    var showDebugInfo by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Recurring Transactions") },
                actions = {
                    if (BuildConfig.DEBUG) {
                        TextButton(onClick = { showDebugInfo = !showDebugInfo }) {
                            Text("Debug", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showBottomSheet = true }) {
                Icon(Icons.Default.Add, contentDescription = "Add Recurring Transaction")
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp)
        ) {
            if (showDebugInfo && BuildConfig.DEBUG) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Debug Info", style = MaterialTheme.typography.titleSmall)
                        Text("Total recurring transactions: ${recurringTransactions.size}")
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
            }

            if (recurringTransactions.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("No recurring transactions set up.")
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(recurringTransactions) { recurring ->
                        RecurringTransactionItem(
                            item = recurring,
                            currencySymbol = currencySymbol,
                            onDelete = { viewModel.deleteRecurringTransaction(recurring) }
                        )
                    }
                }
            }
        }

        if (showBottomSheet) {
            ModalBottomSheet(
                onDismissRequest = { showBottomSheet = false },
                sheetState = sheetState
            ) {
                AddRecurringTransactionForm(
                    viewModel = viewModel,
                    onAdd = { newRecurring ->
                        viewModel.addRecurringTransaction(newRecurring)
                        scope.launch { sheetState.hide() }.invokeOnCompletion {
                            if (!sheetState.isVisible) {
                                showBottomSheet = false
                            }
                        }
                    }
                )
            }
        }
    }
}

@Composable
fun RecurringTransactionItem(item: RecurringTransaction, currencySymbol: String, onDelete: () -> Unit) {
    val formattedDate = remember(item.nextDueDate) {
        SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()).format(Date(item.nextDueDate))
    }

    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = getIconForCategory(item.category),
                contentDescription = item.category,
                modifier = Modifier
                    .size(40.dp)
                    .padding(8.dp)
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(text = item.category, style = MaterialTheme.typography.titleMedium)
                Text(
                    text = "Next on: $formattedDate (${item.frequency})",
                    style = MaterialTheme.typography.bodySmall
                )
            }
            val amountColor = if (item.type == "Expense") Color(0xFFE53935) else Color(0xFF00897B)
            Text(
                text = "$currencySymbol${"%.2f".format(item.amount)}",
                color = amountColor,
                style = MaterialTheme.typography.bodyLarge
            )
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, contentDescription = "Delete Recurring Transaction")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddRecurringTransactionForm(
    viewModel: TransactionViewModel,
    onAdd: (RecurringTransaction) -> Unit
) {
    val expenseCategories by viewModel.expenseCategories.collectAsState()
    val incomeCategories by viewModel.incomeCategories.collectAsState()
    val accounts by viewModel.allAccounts.collectAsState()

    var amount by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    var transactionType by remember { mutableStateOf("Expense") }
    var frequency by remember { mutableStateOf("Monthly") }
    val frequencies = listOf("Daily", "Weekly", "Monthly", "Yearly")
    var category by remember { mutableStateOf("") }
    var selectedAccountId by remember { mutableStateOf("") }
    var selectedDate by remember { mutableStateOf(System.currentTimeMillis()) }

    // Effect to set default category and account
    LaunchedEffect(transactionType, expenseCategories, incomeCategories, accounts) {
        val categories = if (transactionType == "Expense") expenseCategories else incomeCategories
        category = categories.firstOrNull()?.name ?: ""
        selectedAccountId = accounts.firstOrNull()?.id ?: ""
    }

    val context = LocalContext.current
    val calendar = Calendar.getInstance()
    calendar.timeInMillis = selectedDate
    val datePickerDialog = DatePickerDialog(
        context,
        { _, year, month, dayOfMonth ->
            calendar.set(year, month, dayOfMonth)
            selectedDate = calendar.timeInMillis
        },
        calendar.get(Calendar.YEAR),
        calendar.get(Calendar.MONTH),
        calendar.get(Calendar.DAY_OF_MONTH)
    )
    val formattedDate = remember(selectedDate) {
        SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(Date(selectedDate))
    }

    Column(
        modifier = Modifier
            .padding(16.dp)
            .fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("Add New Recurring Rule", style = MaterialTheme.typography.titleLarge)

        Row(modifier = Modifier.fillMaxWidth()) {
            OutlinedButton(
                onClick = { transactionType = "Expense" },
                modifier = Modifier.weight(1f),
                colors = if (transactionType == "Expense")
                    ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    )
                else ButtonDefaults.outlinedButtonColors()
            ) { Text("Expense") }
            Spacer(modifier = Modifier.width(8.dp))
            OutlinedButton(
                onClick = { transactionType = "Income" },
                modifier = Modifier.weight(1f),
                colors = if (transactionType == "Income")
                    ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    )
                else ButtonDefaults.outlinedButtonColors()
            ) { Text("Income") }
        }

        OutlinedTextField(
            value = amount,
            onValueChange = { amount = it },
            label = { Text("Amount") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            singleLine = true
        )

        OutlinedTextField(
            value = note,
            onValueChange = { note = it },
            label = { Text("Note (Optional)") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        OutlinedTextField(
            value = formattedDate,
            onValueChange = {},
            readOnly = true,
            label = { Text("First Due Date") },
            modifier = Modifier.fillMaxWidth(),
            trailingIcon = {
                IconButton(onClick = { datePickerDialog.show() }) {
                    Icon(Icons.Default.DateRange, contentDescription = "Select Date")
                }
            }
        )

        // --- NEW: Account Dropdown ---
        var accountExpanded by remember { mutableStateOf(false) }
        val selectedAccountName = accounts.find { it.id == selectedAccountId }?.name ?: "Select Account"

        ExposedDropdownMenuBox(expanded = accountExpanded, onExpandedChange = { accountExpanded = !it }) {
            OutlinedTextField(
                value = selectedAccountName,
                onValueChange = {},
                readOnly = true,
                label = { Text("Account") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = accountExpanded) },
                modifier = Modifier
                    .menuAnchor()
                    .fillMaxWidth()
                    .clickable { accountExpanded = !accountExpanded }
            )
            ExposedDropdownMenu(expanded = accountExpanded, onDismissRequest = { accountExpanded = false }) {
                accounts.forEach { account ->
                    DropdownMenuItem(
                        text = { Text(account.name) },
                        onClick = {
                            selectedAccountId = account.id
                            accountExpanded = false
                        }
                    )
                }
            }
        }

        // Category Dropdown
        var categoryExpanded by remember { mutableStateOf(false) }
        val categoriesToShow = if (transactionType == "Expense") expenseCategories else incomeCategories
        ExposedDropdownMenuBox(
            expanded = categoryExpanded,
            onExpandedChange = { categoryExpanded = it }
        ) {
            OutlinedTextField(
                value = category,
                onValueChange = {},
                readOnly = true,
                label = { Text(if (transactionType == "Expense") "Category" else "Source") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = categoryExpanded) },
                modifier = Modifier
                    .menuAnchor()
                    .fillMaxWidth()
            )
            ExposedDropdownMenu(
                expanded = categoryExpanded,
                onDismissRequest = { categoryExpanded = false }
            ) {
                categoriesToShow.forEach { selectionOption ->
                    DropdownMenuItem(
                        text = { Text(selectionOption.name) },
                        onClick = {
                            category = selectionOption.name
                            categoryExpanded = false
                        }
                    )
                }
            }
        }

        // Frequency Dropdown
        var freqExpanded by remember { mutableStateOf(false) }
        ExposedDropdownMenuBox(
            expanded = freqExpanded,
            onExpandedChange = { freqExpanded = it }
        ) {
            OutlinedTextField(
                value = frequency,
                onValueChange = {},
                readOnly = true,
                label = { Text("Frequency") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = freqExpanded) },
                modifier = Modifier
                    .menuAnchor()
                    .fillMaxWidth()
            )
            ExposedDropdownMenu(
                expanded = freqExpanded,
                onDismissRequest = { freqExpanded = false }
            ) {
                frequencies.forEach { f ->
                    DropdownMenuItem(
                        text = { Text(f) },
                        onClick = {
                            frequency = f
                            freqExpanded = false
                        }
                    )
                }
            }
        }

        val isValid = amount.isNotBlank() &&
                amount.toDoubleOrNull() != null &&
                amount.toDoubleOrNull()!! > 0 &&
                category.isNotBlank()

        Button(
            onClick = {
                onAdd(
                    RecurringTransaction(
                        id = UUID.randomUUID().toString(),
                        accountId = selectedAccountId,
                        amount = amount.toDoubleOrNull() ?: 0.0,
                        type = transactionType,
                        category = category,
                        note = note,
                        frequency = frequency,
                        nextDueDate = selectedDate
                    )
                )
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = isValid
        ) {
            Text("Save Rule")
        }
    }
}
