package com.tejesh.spendwise.Screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavController
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MoreScreen(
    navController: NavController,
    viewModel: TransactionViewModel
) {
    val menuItems = listOf(
        Screen.Profile,
        Screen.Accounts,
        Screen.Recurring,
        Screen.Settings,
        Screen.About,
        Screen.Dashboard
    )

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val fileSaverLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/csv"),
        onResult = { uri ->
            uri?.let {
                scope.launch {
                    val csvContent = viewModel.exportTransactionsToCsv()
                    context.contentResolver.openOutputStream(it)?.use { os -> os.write(csvContent.toByteArray()) }
                }
            }
        }
    )

    Scaffold(
        topBar = { TopAppBar(title = { Text("More Options") }) }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Navigation items from the list
            items(menuItems) { screen ->
                ListItem(
                    headlineContent = { Text(screen.label) },
                    leadingContent = { screen.icon?.let { Icon(it, contentDescription = screen.label) } },
                    modifier = Modifier.clickable { navController.navigate(screen.route) }
                )
            }

            // THIS IS THE FIX: The single "Export" item is now
            // correctly wrapped in its own item { } block.
            item {
                ListItem(
                    headlineContent = { Text("Export to CSV") },
                    leadingContent = { Icon(Icons.Default.Share, contentDescription = "Export") },
                    modifier = Modifier.clickable { fileSaverLauncher.launch("spendwise_export.csv") }
                )
            }
        }
    }
}
