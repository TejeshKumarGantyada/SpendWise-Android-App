package com.tejesh.spendwise.Screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.tejesh.spendwise.data.Account
import com.tejesh.spendwise.data.AccountBalance
import kotlinx.coroutines.launch
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountsScreen(viewModel: TransactionViewModel) {
    val accountsWithBalance by viewModel.accountBalances.collectAsState()
    val currencySymbol by viewModel.currencySymbol.collectAsState()
    val sheetState = rememberModalBottomSheetState()
    val scope = rememberCoroutineScope()
    var showBottomSheet by remember { mutableStateOf(false) }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Manage Accounts") }) },
        floatingActionButton = {
            FloatingActionButton(onClick = { showBottomSheet = true }) {
                Icon(Icons.Default.Add, contentDescription = "Add Account")
            }
        }
    ) { innerPadding ->
        if (accountsWithBalance.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize().padding(innerPadding), contentAlignment = Alignment.Center) {
                Text("No accounts set up. Add one to get started.")
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                items(accountsWithBalance) { accountBalance ->
                    // --- NEW: Conditionally show a different card for Credit Cards ---
                    when (accountBalance.account.type) {
                        "Credit Card" -> CreditCardItem(
                            accountBalance = accountBalance,
                            currencySymbol = currencySymbol,
                            onDelete = { viewModel.deleteAccount(accountBalance.account) }
                        )
                        else -> AccountItem(
                            accountBalance = accountBalance,
                            currencySymbol = currencySymbol,
                            onDelete = { viewModel.deleteAccount(accountBalance.account) }
                        )
                    }
                }
            }
        }

        if (showBottomSheet) {
            ModalBottomSheet(onDismissRequest = { showBottomSheet = false }, sheetState = sheetState) {
                AddAccountForm(
                    viewModel = viewModel, // Pass the ViewModel down
                    onAdd = { account, creditToAccountId ->
                        // The onAdd lambda now handles both simple accounts and smart loans
                        if (account.type == "Loan Taken" && creditToAccountId != null) {
                            viewModel.addLoan(account, creditToAccountId)
                        } else {
                            viewModel.addAccount(account)
                        }
                        scope.launch { sheetState.hide() }.invokeOnCompletion {
                            if (!sheetState.isVisible) showBottomSheet = false
                        }
                    }
                )
            }
        }
    }
}

@Composable
fun AccountItem(
    accountBalance: AccountBalance,
    currencySymbol: String,
    onDelete: () -> Unit
) {
    // --- THIS IS THE FINAL, CORRECTED LOGIC ---
    val balanceColor = when (accountBalance.account.type) {
        // A liability is red if you owe money (balance is negative)
        "Credit Card", "Loan Taken" -> if (accountBalance.currentBalance < 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
        // An asset is always green/primary
        else -> MaterialTheme.colorScheme.primary
    }

    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = accountBalance.account.name, style = MaterialTheme.typography.titleMedium)
                Text(text = accountBalance.account.type, style = MaterialTheme.typography.bodySmall)
            }
            Text(
                text = "$currencySymbol${"%.2f".format(accountBalance.currentBalance)}",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = balanceColor
            )
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, contentDescription = "Delete Account")
            }
        }
    }
}

@Composable
fun CreditCardItem(
    accountBalance: AccountBalance,
    currencySymbol: String,
    onDelete: () -> Unit
) {
    val usedAmount = -accountBalance.currentBalance
    val limit = accountBalance.account.creditLimit ?: 0.0
    val progress = if (limit > 0) (usedAmount / limit).toFloat() else 0f

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(text = accountBalance.account.name, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                IconButton(onClick = onDelete) {
                    Icon(Icons.Default.Delete, contentDescription = "Delete Account")
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "$currencySymbol${"%.2f".format(accountBalance.currentBalance)}",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.error // Red for debt
            )
            Spacer(modifier = Modifier.height(8.dp))
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier.fillMaxWidth().height(8.dp).clip(MaterialTheme.shapes.small)
            )
            Spacer(modifier = Modifier.height(4.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Used: $currencySymbol${"%.2f".format(usedAmount)}", style = MaterialTheme.typography.bodySmall)
                Text("Limit: $currencySymbol${"%.2f".format(limit)}", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddAccountForm(
    viewModel: TransactionViewModel,
    onAdd: (Account, String?) -> Unit // Lambda now provides an optional account ID
) {
    val allAccounts by viewModel.allAccounts.collectAsState()
    val assetAccounts = allAccounts.filter { it.type == "Bank" || it.type == "Cash" }

    var name by remember { mutableStateOf("") }
    var type by remember { mutableStateOf("Bank") }
    var initialBalance by remember { mutableStateOf("") }
    var creditLimit by remember { mutableStateOf("") }
    var creditToAccountId by remember { mutableStateOf(assetAccounts.firstOrNull()?.id ?: "") }
    val accountTypes = listOf("Bank", "Cash", "Credit Card", "Loan Taken")
    var expanded by remember { mutableStateOf(false) }

    val balanceLabel = when (type) {
        "Credit Card" -> "Current Due Amount (e.g., 5000)"
        "Loan Taken" -> "Loan Amount (e.g., 10000)"
        else -> "Initial Balance"
    }

    Column(
        modifier = Modifier.padding(16.dp).fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("Add New Account", style = MaterialTheme.typography.titleLarge)
        OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Account Name") }, singleLine = true)
        ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = !expanded }) {
            OutlinedTextField(
                value = type,
                onValueChange = {},
                readOnly = true,
                label = { Text("Account Type") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                modifier = Modifier.menuAnchor().fillMaxWidth()
            )
            ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                accountTypes.forEach { accountType ->
                    DropdownMenuItem(text = { Text(accountType) }, onClick = { type = accountType; expanded = false })
                }
            }
        }
        OutlinedTextField(
            value = initialBalance,
            onValueChange = { initialBalance = it },
            label = { Text(balanceLabel) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            singleLine = true
        )

        // --- NEW: Smart UI for Loan and Credit Card ---
        AnimatedVisibility(visible = type == "Credit Card" || type == "Loan Taken") {
            Column {
                if (type == "Credit Card") {
                    OutlinedTextField(
                        value = creditLimit,
                        onValueChange = { creditLimit = it },
                        label = { Text("Credit Limit") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                if (type == "Loan Taken") {
                    var creditToAccountExpanded by remember { mutableStateOf(false) }
                    val selectedAccountName = assetAccounts.find { it.id == creditToAccountId }?.name ?: "Select Account"

                    ExposedDropdownMenuBox(expanded = creditToAccountExpanded, onExpandedChange = { creditToAccountExpanded = !it }) {
                        OutlinedTextField(
                            value = selectedAccountName,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Credit Loan Amount To") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = creditToAccountExpanded) },
                            modifier = Modifier.menuAnchor().fillMaxWidth()
                        )
                        ExposedDropdownMenu(expanded = creditToAccountExpanded, onDismissRequest = { creditToAccountExpanded = false }) {
                            assetAccounts.forEach { account ->
                                DropdownMenuItem(
                                    text = { Text(account.name) },
                                    onClick = {
                                        creditToAccountId = account.id
                                        creditToAccountExpanded = false
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }

        Button(
            onClick = {
                var finalBalance = initialBalance.toDoubleOrNull() ?: 0.0
                // For liabilities, the initial balance should be negative to represent debt
                if ((type == "Credit Card" || type == "Loan Taken") && finalBalance > 0) {
                    finalBalance *= -1
                }
                onAdd(
                    Account(
                        id = UUID.randomUUID().toString(),
                        name = name,
                        type = type,
                        initialBalance = finalBalance,
                        creditLimit = if (type == "Credit Card") creditLimit.toDoubleOrNull() else null
                    ),
                    if (type == "Loan Taken") creditToAccountId else null
                )
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = name.isNotBlank()
        ) {
            Text("Save Account")
        }
    }
}