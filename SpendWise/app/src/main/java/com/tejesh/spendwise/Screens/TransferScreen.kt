package com.tejesh.spendwise.Screens

import android.app.DatePickerDialog
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.tejesh.spendwise.data.AccountBalance
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransferScreen(
    navController: NavController,
    viewModel: TransactionViewModel
) {
    // --- State Collection ---
    val accountsWithBalance by viewModel.accountBalances.collectAsState()
    val assetAccounts by viewModel.assetAccounts.collectAsState()
    val currencySymbol by viewModel.currencySymbol.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    // --- Form State ---
    var fromAccountId by remember { mutableStateOf("") }
    var toAccountId by remember { mutableStateOf("") }
    var amount by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    var selectedDate by remember { mutableStateOf(System.currentTimeMillis()) }

    // Effect to set default accounts when the list loads
    LaunchedEffect(accountsWithBalance) {
        if (accountsWithBalance.isNotEmpty()) {
            fromAccountId = accountsWithBalance.first().account.id
            toAccountId = accountsWithBalance.getOrNull(1)?.account?.id ?: ""
        }
    }

    // --- Validation ---
    val isFormValid = amount.toDoubleOrNull() ?: 0.0 > 0 &&
            fromAccountId.isNotBlank() &&
            toAccountId.isNotBlank() &&
            fromAccountId != toAccountId

    // Find the selected "To" account to power the smart UI
    val selectedToAccount = accountsWithBalance.find { it.account.id == toAccountId }

    // --- Date Picker Logic ---
    val context = LocalContext.current
    val calendar = Calendar.getInstance().apply { timeInMillis = selectedDate }
    val datePickerDialog = DatePickerDialog(
        context,
        { _, year, month, dayOfMonth ->
            calendar.set(year, month, dayOfMonth)
            selectedDate = calendar.timeInMillis
        },
        calendar.get(Calendar.YEAR),
        calendar.get(Calendar.MONTH),
        calendar.get(Calendar.DAY_OF_MONTH),
    )
    val formattedDate = remember(selectedDate) {
        SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(Date(selectedDate))
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("New Transfer") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // --- Smart Amount Field ---
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = amount,
                    onValueChange = { amount = it },
                    label = { Text("Amount") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f)
                )
                AnimatedVisibility(visible = selectedToAccount?.account?.type == "Credit Card" || selectedToAccount?.account?.type == "Loan Taken") {
                    TextButton(
                        onClick = {
                            selectedToAccount?.let {
                                amount = (-it.currentBalance).toString()
                            }
                        },
                        modifier = Modifier.padding(start = 8.dp)
                    ) {
                        Text("Pay in Full")
                    }
                }
            }

            if (selectedToAccount != null && (selectedToAccount.account.type == "Credit Card" || selectedToAccount.account.type == "Loan Taken")) {
                Text(
                    text = "Amount Due: $currencySymbol${"%.2f".format(-selectedToAccount.currentBalance)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            AccountSelector(
                label = "From Account",
                accountsWithBalance = assetAccounts,
                selectedAccountId = fromAccountId,
                onAccountSelected = { fromAccountId = it },
                currencySymbol = currencySymbol
            )

            AccountSelector(
                label = "To Account",
                accountsWithBalance = accountsWithBalance.filter { it.account.id != fromAccountId },
                selectedAccountId = toAccountId,
                onAccountSelected = { toAccountId = it },
                currencySymbol = currencySymbol
            )

            OutlinedTextField(
                value = formattedDate,
                onValueChange = {},
                readOnly = true,
                label = { Text("Date") },
                modifier = Modifier.fillMaxWidth(),
                trailingIcon = {
                    IconButton(onClick = { datePickerDialog.show() }) {
                        Icon(Icons.Default.DateRange, contentDescription = "Select Date")
                    }
                }
            )

            OutlinedTextField(
                value = note,
                onValueChange = { note = it },
                label = { Text("Note (Optional)") },
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.weight(1f))

            Button(
                onClick = {
                    viewModel.addTransfer(
                        fromAccountId = fromAccountId,
                        toAccountId = toAccountId,
                        amount = amount.toDoubleOrNull() ?: 0.0,
                        date = selectedDate,
                        note = note
                    ) { isSuccess, message ->
                        if (isSuccess) {
                            navController.popBackStack()
                        } else {
                            scope.launch {
                                snackbarHostState.showSnackbar(message)
                            }
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = isFormValid
            ) {
                Text("Save Transfer")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AccountSelector(
    label: String,
    accountsWithBalance: List<AccountBalance>,
    selectedAccountId: String,
    onAccountSelected: (String) -> Unit,
    currencySymbol: String
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedAccountName = accountsWithBalance.find { it.account.id == selectedAccountId }?.account?.name ?: ""

    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = !expanded }) {
        OutlinedTextField(
            value = selectedAccountName,
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .menuAnchor()
                .fillMaxWidth()
                .clickable { expanded = !expanded }
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            accountsWithBalance.forEach { accountBalance ->
                DropdownMenuItem(
                    text = {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(accountBalance.account.name)
                            Text("$currencySymbol${"%.2f".format(accountBalance.currentBalance)}")
                        }
                    },
                    onClick = {
                        onAccountSelected(accountBalance.account.id)
                        expanded = false
                    }
                )
            }
        }
    }
}
