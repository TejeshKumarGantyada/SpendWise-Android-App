package com.tejesh.spendwise.Screens

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.ui.graphics.vector.ImageVector

sealed class Screen( // sealed class --> all direct subclasses of Screen must be defined in this same file
    val route: String,
    val label: String,
    val icon: ImageVector? = null
) {
    // --- Bottom Bar Screens ---
    object Home : Screen("home", "Home", Icons.Default.Home)
    object Charts : Screen("charts", "Charts", Icons.Outlined.PieChart)
    object Budgets : Screen("budgets", "Budgets", Icons.Outlined.AccountBalanceWallet)
    object More : Screen("more", "More", Icons.Outlined.MoreHoriz)

    // --- Screens within the "More" tab ---
    object Profile : Screen("profile", "Profile", Icons.Outlined.Person)
    object Recurring : Screen("recurring", "Recurring", Icons.Outlined.Autorenew)
    object Settings : Screen("settings", "Settings", Icons.Outlined.Settings)
    object Accounts : Screen("accounts", "Accounts", Icons.Outlined.AccountBalance)
    object About : Screen("about", "About", Icons.Outlined.Info)
    object Dashboard : Screen("dashboard", "Dashboard", Icons.Outlined.Dashboard)

    object AllTransactions : Screen("all_transactions", "All Transactions")
    object Transfer : Screen("transfer", "Transfer", Icons.Default.SwapHoriz)
    object TransactionDetail : Screen("transaction/{id}", "Transaction")
}
