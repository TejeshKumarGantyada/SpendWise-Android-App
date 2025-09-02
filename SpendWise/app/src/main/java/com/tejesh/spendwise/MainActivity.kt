package com.tejesh.spendwise

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.tejesh.spendwise.Screens.AboutScreen
import com.tejesh.spendwise.Screens.AccountsScreen
import com.tejesh.spendwise.Screens.AllTransactionsScreen
import com.tejesh.spendwise.Screens.BudgetsScreen
import com.tejesh.spendwise.Screens.ChartsScreen
import com.tejesh.spendwise.Screens.DashboardScreen
import com.tejesh.spendwise.Screens.HomeScreen
import com.tejesh.spendwise.Screens.MoreScreen
import com.tejesh.spendwise.Screens.ProfileScreen
import com.tejesh.spendwise.Screens.RecurringScreen
import com.tejesh.spendwise.Screens.Screen
import com.tejesh.spendwise.Screens.SettingsScreen
import com.tejesh.spendwise.Screens.TransactionScreen
import com.tejesh.spendwise.Screens.TransactionViewModel
import com.tejesh.spendwise.Screens.TransferScreen
import com.tejesh.spendwise.Screens.auth.AuthViewModel
import com.tejesh.spendwise.Screens.auth.GoogleAuthUiClient
import com.tejesh.spendwise.Screens.auth.SignInScreen
import com.tejesh.spendwise.Screens.auth.SignUpScreen
import com.tejesh.spendwise.Screens.settings.SettingsViewModel
import com.tejesh.spendwise.ui.theme.SpendWiseTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var googleAuthUiClient: GoogleAuthUiClient

    private val transactionViewModel: TransactionViewModel by viewModels()
    private val authViewModel: AuthViewModel by viewModels()
    private val settingsViewModel: SettingsViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()

        super.onCreate(savedInstanceState)
        setContent {
            SpendWiseTheme {
                val user by authViewModel.user.collectAsState()

                // This single LaunchedEffect is the source of truth for auth changes.
                // It ensures data is synced on login and cleared on logout.
                LaunchedEffect(user) {
                    if (user != null) {
                        transactionViewModel.initializeUserSession()
                    } else {
                        transactionViewModel.clearUserSession()
                    }
                }

                if (user == null) {
                    // --- Authentication Flow ---
                    val authNavController = rememberNavController()
                    NavHost(
                        navController = authNavController,
                        startDestination = "sign_in",
                        enterTransition = { slideInHorizontally(initialOffsetX = { 1000 }) },
                        exitTransition = { slideOutHorizontally(targetOffsetX = { -1000 }) },
                        popEnterTransition = { slideInHorizontally(initialOffsetX = { -1000 }) },
                        popExitTransition = { slideOutHorizontally(targetOffsetX = { 1000 }) }
                    ) {
                        composable("sign_in") {
                            SignInScreen(
                                navController = authNavController,
                                authViewModel = authViewModel,
                                googleAuthUiClient = googleAuthUiClient // Pass it to the screen
                            )
                        }
                        composable("sign_up") {
                            SignUpScreen(navController = authNavController, authViewModel = authViewModel)
                        }
                    }
                } else {
                    // --- Main App Flow ---
                    val navController = rememberNavController()
                    val bottomNavItems = listOf(Screen.Home, Screen.Charts, Screen.Budgets, Screen.More)

                    Scaffold(
                        bottomBar = {
                            NavigationBar {
                                val navBackStackEntry by navController.currentBackStackEntryAsState()
                                val currentDestination = navBackStackEntry?.destination

                                bottomNavItems.forEach { screen ->
                                    NavigationBarItem(
                                        selected = currentDestination?.hierarchy?.any { it.route == screen.route } == true,
                                        onClick = {
                                            navController.navigate(screen.route) {
                                                popUpTo(navController.graph.startDestinationId)
                                                launchSingleTop = true
                                            }
                                        },
                                        icon = { screen.icon?.let { Icon(it, contentDescription = screen.label) } },
                                        label = { Text(screen.label) }
                                    )
                                }
                            }
                        }
                    ) { innerPadding ->
                        NavHost(
                            navController = navController,
                            startDestination = Screen.Home.route,
                            modifier = Modifier.padding(innerPadding),
                            enterTransition = { slideInHorizontally(initialOffsetX = { 1000 }) },
                            exitTransition = { slideOutHorizontally(targetOffsetX = { -1000 }) },
                            popEnterTransition = { slideInHorizontally(initialOffsetX = { -1000 }) },
                            popExitTransition = { slideOutHorizontally(targetOffsetX = { 1000 }) }
                        ) {
                            composable(Screen.Home.route) {
                                HomeScreen(
                                    viewModel = transactionViewModel,
                                    authViewModel = authViewModel,
                                    onTransactionClick = { id -> navController.navigate("transaction/$id") },
                                    onAddTransaction = { type ->
                                        when (type) {
                                            "Transfer" -> navController.navigate(Screen.Transfer.route)
                                            else -> navController.navigate("transaction/new") // For Income/Expense
                                        }
                                    },
                                    onNavigateToBudgets = { navController.navigate(Screen.Budgets.route) },
                                    onNavigateToAllTransactions = { navController.navigate(Screen.AllTransactions.route) },
                                    onNavigateToProfile = { navController.navigate(Screen.Profile.route) },
                                    onNavigateToAccounts = { navController.navigate(Screen.Accounts.route) }
                                )
                            }
                            composable(Screen.Charts.route) { ChartsScreen(viewModel = transactionViewModel) }
                            composable(Screen.Budgets.route) { BudgetsScreen(viewModel = transactionViewModel) }
                            composable(Screen.More.route) { MoreScreen(navController = navController, viewModel = transactionViewModel) }
                            composable(Screen.Recurring.route) { RecurringScreen(viewModel = transactionViewModel) }
                            composable(Screen.Settings.route) { SettingsScreen(transactionViewModel = transactionViewModel, settingsViewModel = settingsViewModel) }
                            composable(Screen.Dashboard.route) { DashboardScreen() }
                            composable(Screen.About.route) {
                                AboutScreen(navController = navController)
                            }
                            composable(Screen.Profile.route) {
                                ProfileScreen(
                                    authViewModel = authViewModel,
                                    onNavigateUp = { navController.popBackStack() }
                                )
                            }
                            composable(Screen.AllTransactions.route) {
                                AllTransactionsScreen(
                                    viewModel = transactionViewModel,
                                    onTransactionClick = { id -> navController.navigate("transaction/$id") }
                                )
                            }
                            composable("transaction/{id}") { backStackEntry ->
                                val id = backStackEntry.arguments?.getString("id") ?: "new"
                                TransactionScreen(
                                    viewModel = transactionViewModel,
                                    transactionId = id,
                                    onNavigateUp = { navController.popBackStack() }
                                )
                            }
                            composable(Screen.Accounts.route) {
                                AccountsScreen(viewModel = transactionViewModel)
                            }
                            composable(Screen.Transfer.route) {
                                TransferScreen(
                                    navController = navController,
                                    viewModel = transactionViewModel
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
