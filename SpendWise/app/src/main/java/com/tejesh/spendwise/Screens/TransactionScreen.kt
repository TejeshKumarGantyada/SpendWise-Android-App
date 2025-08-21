package com.tejesh.spendwise.Screens

import android.Manifest
import android.app.DatePickerDialog
import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.tejesh.spendwise.data.Transaction
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import java.util.regex.Pattern

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionScreen(
    viewModel: TransactionViewModel,
    transactionId: String,
    onNavigateUp: () -> Unit,
) {
    val isEditing = transactionId != "new"
    val context = LocalContext.current

    // --- State for the form ---
    val expenseCategories by viewModel.expenseCategories.collectAsState()
    val incomeCategories by viewModel.incomeCategories.collectAsState()
    val accounts by viewModel.accountsForForms.collectAsState()
    var transactionType by remember { mutableStateOf("Expense") }
    var amount by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("") }
    var selectedAccountId by remember { mutableStateOf("") }
    var selectedDate by remember { mutableStateOf(System.currentTimeMillis()) }

    // --- OCR and Camera Logic ---
    var imageUri by remember { mutableStateOf<Uri?>(null) }
    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture(),
        onResult = { success ->
            if (success) {
                imageUri?.let { uri ->
                    processImageForText(context, uri) { extractedAmount, extractedDate ->
                        if (extractedAmount != null) {
                            amount = extractedAmount
                        }
                        if (extractedDate != null) {
                            selectedDate = extractedDate
                        }
                    }
                }
            }
        }
    )
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { isGranted ->
            if (isGranted) {
                val newImageUri = createImageUri(context)
                imageUri = newImageUri
                cameraLauncher.launch(newImageUri)
            }
        }
    )

    // Effect to pre-fill the form when editing
    LaunchedEffect(key1 = Unit) {
        if (isEditing) {
            viewModel.getTransactionById(transactionId).collect { transaction ->
                if (transaction != null) {
                    transactionType = transaction.type
                    amount = transaction.amount.toString()
                    category = transaction.category
                    note = transaction.note
                    selectedDate = transaction.date
                    selectedAccountId = transaction.accountId
                }
            }
        }
    }

    LaunchedEffect(transactionType, expenseCategories, incomeCategories, accounts) {
        if (!isEditing) {
            val categories = if (transactionType == "Expense") expenseCategories else incomeCategories
            category = categories.firstOrNull()?.name ?: ""
            selectedAccountId = accounts.firstOrNull()?.id ?: ""
        }
    }

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
                title = { Text(if (isEditing) "Edit Transaction" else "Add Transaction") },
                actions = {
                    if (isEditing) {
                        val coroutineScope = rememberCoroutineScope()
                        IconButton(onClick = {
                            coroutineScope.launch {
                                val transaction = viewModel.getTransactionById(transactionId).firstOrNull()
                                if (transaction != null) {
                                    viewModel.deleteTransaction(transaction)
                                }
                                onNavigateUp()
                            }
                        }) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete Transaction")
                        }
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .padding(16.dp)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(
                    onClick = { transactionType = "Expense" },
                    modifier = Modifier.weight(1f),
                    shape = MaterialTheme.shapes.medium,
                    colors = if (transactionType == "Expense") ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary) else ButtonDefaults.outlinedButtonColors()
                ) { Text("Expense") }
                Spacer(modifier = Modifier.width(8.dp))
                OutlinedButton(
                    onClick = { transactionType = "Income" },
                    modifier = Modifier.weight(1f),
                    shape = MaterialTheme.shapes.medium,
                    colors = if (transactionType == "Income") ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary) else ButtonDefaults.outlinedButtonColors()
                ) { Text("Income") }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = amount,
                    onValueChange = { amount = it },
                    label = { Text("Amount") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.width(8.dp))
                IconButton(onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) }) {
                    Icon(Icons.Default.CameraAlt, contentDescription = "Scan Receipt")
                }
            }

            OutlinedTextField(
                value = formattedDate,
                onValueChange = { },
                readOnly = true,
                label = { Text("Date") },
                modifier = Modifier.fillMaxWidth(),
                trailingIcon = {
                    IconButton(onClick = { datePickerDialog.show() }) {
                        Icon(
                            imageVector = Icons.Default.DateRange,
                            contentDescription = "Select Date"
                        )
                    }
                }
            )

            var accountExpanded by remember { mutableStateOf(false) }
            val selectedAccountName = accounts.find { it.id == selectedAccountId }?.name ?: "Select Account"

            ExposedDropdownMenuBox(
                expanded = accountExpanded,
                onExpandedChange = { accountExpanded = it } // let the box handle expansion
            ) {
                OutlinedTextField(
                    value = selectedAccountName,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Account") },
                    trailingIcon = {
                        ExposedDropdownMenuDefaults.TrailingIcon(expanded = accountExpanded)
                    },
                    modifier = Modifier
                        .menuAnchor() // required for positioning
                        .fillMaxWidth()
                )

                ExposedDropdownMenu(
                    expanded = accountExpanded,
                    onDismissRequest = { accountExpanded = false }
                ) {
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



            // --- FIXED DROPDOWN ---
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

            OutlinedTextField(
                value = note,
                onValueChange = { note = it },
                label = { Text("Note (Optional)") },
                modifier = Modifier.fillMaxWidth()
            )

            Button(
                onClick = {
                    val transactionAmount = amount.toDoubleOrNull() ?: 0.0
                    val transactionToSave = Transaction(
                        id = if (isEditing) transactionId else UUID.randomUUID().toString(),
                        accountId = selectedAccountId,
                        amount = transactionAmount,
                        category = category,
                        note = note,
                        date = selectedDate,
                        type = transactionType
                    )

                    if (isEditing) {
                        viewModel.updateTransaction(transactionToSave)
                    } else {
                        viewModel.addTransaction(transactionToSave)
                    }
                    onNavigateUp()
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = amount.isNotBlank() && category.isNotBlank()
            ) {
                Text("Save Transaction")
            }
        }
    }
}

// --- Helper Functions for OCR ---

private fun createImageUri(context: Context): Uri {
    val imageFile = File(context.cacheDir, "receipt_${System.currentTimeMillis()}.jpg")
    return androidx.core.content.FileProvider.getUriForFile(
        context,
        "${context.packageName}.provider",
        imageFile
    )
}

private fun processImageForText(
    context: Context,
    imageUri: Uri,
    onResult: (String?, Long?) -> Unit
) {
    val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    try {
        val image = InputImage.fromFilePath(context, imageUri)
        recognizer.process(image)
            .addOnSuccessListener { visionText ->
                val text = visionText.text
                val extractedAmount = OcrParser.parseTotalAmount(text)
                val extractedDate = OcrParser.parseDate(text)
                onResult(extractedAmount, extractedDate)
            }
            .addOnFailureListener { e ->
                Log.e("OCR", "Text recognition failed", e)
                onResult(null, null)
            }
    } catch (e: Exception) {
        Log.e("OCR", "Error creating InputImage", e)
        onResult(null, null)
    }
}

object OcrParser {
    private val amountPattern = Pattern.compile("[0-9,]+\\.\\d{2}")
    private val datePatterns = listOf(
        Pattern.compile("(\\d{1,2})/(\\d{1,2})/(\\d{2,4})") to "MM/dd/yyyy",
        Pattern.compile("(\\d{1,2})-(\\d{1,2})-(\\d{2,4})") to "MM-dd-yyyy",
        Pattern.compile("(\\d{4})-(\\d{1,2})-(\\d{1,2})") to "yyyy-MM-dd"
    )

    fun parseTotalAmount(text: String): String? {
        var highestNumber = -1.0
        val lines = text.split("\n")

        for (line in lines.reversed()) {
            val lowerCaseLine = line.lowercase()
            if (lowerCaseLine.contains("total") || lowerCaseLine.contains("amount")) {
                val matcher = amountPattern.matcher(line)
                if (matcher.find()) {
                    try {
                        val numberStr = matcher.group(0).replace(",", "")
                        val number = numberStr.toDouble()
                        if (number > highestNumber) {
                            highestNumber = number
                        }
                    } catch (_: NumberFormatException) { }
                }
            }
        }

        if (highestNumber == -1.0) {
            for (line in lines) {
                val matcher = amountPattern.matcher(line)
                while (matcher.find()) {
                    try {
                        val numberStr = matcher.group(0).replace(",", "")
                        val number = numberStr.toDouble()
                        if (number > highestNumber) {
                            highestNumber = number
                        }
                    } catch (_: NumberFormatException) { }
                }
            }
        }
        return if (highestNumber > 0) String.format(Locale.US, "%.2f", highestNumber) else null
    }

    fun parseDate(text: String): Long? {
        for ((pattern, format) in datePatterns) {
            val matcher = pattern.matcher(text)
            if (matcher.find()) {
                try {
                    val dateStr = matcher.group(0)
                    val sdf = SimpleDateFormat(format, Locale.getDefault())
                    if (dateStr.length <= 8) {
                        val cal = Calendar.getInstance()
                        cal.time = sdf.parse(dateStr) ?: Date()
                        if (cal.get(Calendar.YEAR) > Calendar.getInstance().get(Calendar.YEAR)) {
                            cal.add(Calendar.YEAR, -100)
                        }
                        return cal.timeInMillis
                    }
                    return sdf.parse(dateStr)?.time
                } catch (_: Exception) { }
            }
        }
        return null
    }
}
