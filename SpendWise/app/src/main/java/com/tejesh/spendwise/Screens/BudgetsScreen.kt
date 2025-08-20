package com.tejesh.spendwise.Screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.tejesh.spendwise.data.Budget
import com.tejesh.spendwise.data.BudgetProgress
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BudgetsScreen(viewModel: TransactionViewModel) {
    val budgetProgressList by viewModel.budgetProgress.collectAsState()
    val currentMonthDisplay by viewModel.currentMonthDisplay.collectAsState()
    val currentYearMonth by viewModel.currentYearMonth.collectAsState()
    val currencySymbol by viewModel.currencySymbol.collectAsState()

    val sheetState = rememberModalBottomSheetState()
    val scope = rememberCoroutineScope()
    var showBottomSheet by remember { mutableStateOf(false) }
    var budgetToEdit by remember { mutableStateOf<Budget?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = currentMonthDisplay,
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Center
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { viewModel.changeMonth(-1) }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Previous Month")
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.changeMonth(1) }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "Next Month")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = {
                budgetToEdit = null
                showBottomSheet = true
            }) {
                Icon(Icons.Default.Add, contentDescription = "Add Budget")
            }
        }
    ) { innerPadding ->
        if (budgetProgressList.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize().padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                Text("No budgets set for $currentMonthDisplay.")
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                items(budgetProgressList) { progress ->
                    BudgetProgressItem(
                        progress = progress,
                        currencySymbol = currencySymbol,
                        onDelete = { viewModel.deleteBudget(progress.budget) },
                        onClick = {
                            budgetToEdit = progress.budget
                            showBottomSheet = true
                        }
                    )
                }
            }
        }

        if (showBottomSheet) {
            ModalBottomSheet(
                onDismissRequest = { showBottomSheet = false },
                sheetState = sheetState
            ) {
                AddBudgetForm(
                    viewModel = viewModel, // Pass the ViewModel down
                    budgetToEdit = budgetToEdit,
                    currentYearMonth = currentYearMonth,
                    onSave = { budget ->
                        viewModel.addBudget(budget)
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
fun BudgetProgressItem(
    progress: BudgetProgress,
    currencySymbol: String,
    onDelete: () -> Unit,
    onClick: () -> Unit,
) {
    val progressAnimation by animateFloatAsState(
        targetValue = progress.progress,
        animationSpec = ProgressIndicatorDefaults.ProgressAnimationSpec, label = "BudgetProgress"
    )
    val progressColor = when {
        progress.progress > 1f -> MaterialTheme.colorScheme.error
        progress.progress > 0.8f -> Color(0xFFFF9800) // Orange
        else -> MaterialTheme.colorScheme.primary
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = progress.budget.category,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = onDelete, modifier = Modifier.size(24.dp)) {
                    Icon(Icons.Default.Delete, contentDescription = "Delete Budget")
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(text = "Spent: $currencySymbol${"%.2f".format(progress.spentAmount)}")
                Text(text = "Budget: $currencySymbol${"%.2f".format(progress.budget.amount)}")
            }
            Spacer(modifier = Modifier.height(8.dp))
            LinearProgressIndicator(
                progress = { progressAnimation },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(MaterialTheme.shapes.small),
                color = progressColor
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddBudgetForm(
    viewModel: TransactionViewModel,
    budgetToEdit: Budget?,
    currentYearMonth: String,
    onSave: (Budget) -> Unit,
) {
    val isEditing = budgetToEdit != null
    // --- THIS IS THE FIX: Collect the dynamic category list ---
    val expenseCategories by viewModel.expenseCategories.collectAsState()

    var amount by remember { mutableStateOf(budgetToEdit?.amount?.toString() ?: "") }
    var selectedCategory by remember { mutableStateOf("") }
    var expanded by remember { mutableStateOf(false) }

    // Effect to set a default category
    LaunchedEffect(expenseCategories) {
        if (!isEditing) {
            selectedCategory = expenseCategories.firstOrNull()?.name ?: ""
        } else {
            selectedCategory = budgetToEdit?.category ?: ""
        }
    }

    Column(
        modifier = Modifier
            .padding(16.dp)
            .fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = if (isEditing) "Edit Budget" else "Set New Budget",
            style = MaterialTheme.typography.titleLarge
        )

        ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = !expanded }) {
            OutlinedTextField(
                value = selectedCategory,
                onValueChange = {},
                readOnly = true,
                label = { Text("Category") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                modifier = Modifier
                    .menuAnchor()
                    .fillMaxWidth()
                    .clickable { expanded = !expanded },
                enabled = !isEditing
            )
            ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                // --- THIS IS THE FIX: Use the dynamic list ---
                expenseCategories.forEach { category ->
                    DropdownMenuItem(
                        text = { Text(category.name) },
                        onClick = {
                            selectedCategory = category.name
                            expanded = false
                        }
                    )
                }
            }
        }

        OutlinedTextField(
            value = amount,
            onValueChange = { amount = it },
            label = { Text("Budget Amount") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth()
        )

        Button(
            onClick = {
                val budgetAmount = amount.toDoubleOrNull() ?: 0.0
                if (budgetAmount > 0) {
                    onSave(
                        Budget(
                            id = budgetToEdit?.id ?: "${currentYearMonth}_${selectedCategory}",
                            category = selectedCategory,
                            amount = budgetAmount,
                            yearMonth = currentYearMonth
                        )
                    )
                }
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = amount.isNotBlank() && (amount.toDoubleOrNull() ?: 0.0) > 0 && selectedCategory.isNotBlank()
        ) {
            Text("Save Budget")
        }
    }
}
