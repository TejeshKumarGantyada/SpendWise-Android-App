package com.tejesh.spendwise.Screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.tejesh.spendwise.data.Category
import com.tejesh.spendwise.Screens.settings.SettingsViewModel
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    transactionViewModel: TransactionViewModel,
    settingsViewModel: SettingsViewModel
) {
    val expenseCategories by transactionViewModel.expenseCategories.collectAsState()
    val incomeCategories by transactionViewModel.incomeCategories.collectAsState()
    val currentCurrency by settingsViewModel.currencySymbol.collectAsState()

    var showAddCategoryDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Settings") }) },
        floatingActionButton = {
            FloatingActionButton(onClick = { showAddCategoryDialog = true }) {
                Icon(Icons.Default.Add, contentDescription = "Add Category")
            }
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Text("App Settings", style = MaterialTheme.typography.titleMedium)
                CurrencySelector(
                    currentSymbol = currentCurrency,
                    onSymbolSelected = { settingsViewModel.setCurrencySymbol(it) }
                )
            }

            item {
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                Text("Manage Categories", style = MaterialTheme.typography.titleMedium)
            }
            items(expenseCategories) { category ->
                CategoryItem(
                    category = category,
                    onDelete = { transactionViewModel.deleteCategory(category) }
                )
            }

            item {
                Spacer(modifier = Modifier.height(16.dp))
                Text("Income Sources", style = MaterialTheme.typography.titleMedium)
            }
            items(incomeCategories) { category ->
                CategoryItem(
                    category = category,
                    onDelete = { transactionViewModel.deleteCategory(category) }
                )
            }
        }

        if (showAddCategoryDialog) {
            AddCategoryDialog(
                onDismiss = { showAddCategoryDialog = false },
                onAdd = { name, type ->
                    val newCategory = Category(
                        id = name.lowercase().replace(" ", "_"),
                        name = name,
                        type = type
                    )
                    transactionViewModel.addCategory(newCategory)
                    showAddCategoryDialog = false
                }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CurrencySelector(
    currentSymbol: String,
    onSymbolSelected: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    // --- NEW: A list of pairs to hold both name and symbol ---
    val currencies = listOf(
        "Indian Rupee" to "₹",
        "US Dollar" to "$",
        "Euro" to "€",
        "British Pound" to "£",
        "Japanese Yen" to "¥"
    )

    // Find the full display name for the current symbol
    val currentDisplayName = currencies.find { it.second == currentSymbol }?.let { "${it.first} (${it.second})" } ?: currentSymbol

    Card(modifier = Modifier.fillMaxWidth()) {
        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = { expanded = !expanded },
            modifier = Modifier.padding(8.dp)
        ) {
            OutlinedTextField(
                value = currentDisplayName,
                onValueChange = {},
                readOnly = true,
                label = { Text("Currency") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                modifier = Modifier.menuAnchor().fillMaxWidth()
            )
            ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                currencies.forEach { (name, symbol) ->
                    DropdownMenuItem(
                        text = { Text("$name ($symbol)") },
                        onClick = {
                            onSymbolSelected(symbol)
                            expanded = false
                        }
                    )
                }
            }
        }
    }
}


@Composable
fun CategoryItem(category: Category, onDelete: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = category.name, modifier = Modifier.weight(1f))
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, contentDescription = "Delete Category")
            }
        }
    }
}

@Composable
fun AddCategoryDialog(
    onDismiss: () -> Unit,
    onAdd: (String, String) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var type by remember { mutableStateOf("Expense") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add New Category") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Category Name") }
                )
                Row {
                    RadioButton(
                        selected = type == "Expense",
                        onClick = { type = "Expense" }
                    )
                    Text("Expense", modifier = Modifier.align(Alignment.CenterVertically))
                    Spacer(modifier = Modifier.width(16.dp))
                    RadioButton(
                        selected = type == "Income",
                        onClick = { type = "Income" }
                    )
                    Text("Income", modifier = Modifier.align(Alignment.CenterVertically))
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onAdd(name, type) },
                enabled = name.isNotBlank()
            ) {
                Text("Add")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
