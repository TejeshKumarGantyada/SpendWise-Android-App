package com.tejesh.spendwise.Screens

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.tejesh.spendwise.R
import com.tejesh.spendwise.data.AccountBalance
import com.tejesh.spendwise.data.BudgetProgress
import com.tejesh.spendwise.data.Transaction
import com.tejesh.spendwise.Screens.auth.AuthViewModel
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: TransactionViewModel,
    authViewModel: AuthViewModel,
    onTransactionClick: (String) -> Unit,
    onAddTransaction: (String) -> Unit,
    onNavigateToBudgets: () -> Unit,
    onNavigateToAllTransactions: () -> Unit,
    onNavigateToProfile: () -> Unit,
    onNavigateToAccounts: () -> Unit
) {
    // --- State Collection ---
    val currencySymbol by viewModel.currencySymbol.collectAsState()
    val transactions by viewModel.filteredTransactions.collectAsState()
    val monthlyIncome by viewModel.monthlyIncome.collectAsState()
    val monthlyExpense by viewModel.monthlyExpense.collectAsState()
    val topBudgets by viewModel.topBudgets.collectAsState()
    val user by authViewModel.user.collectAsState()
    val aiInsightState by viewModel.aiInsightState.collectAsState()
    val accountBalances by viewModel.accountBalances.collectAsState()

    // --- Effects ---
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { /* ... */ },
    )
    LaunchedEffect(key1 = Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    Scaffold(
        topBar = {
            HomeTopAppBar(
                user = user,
                onProfileClick = onNavigateToProfile
            )
        },
        floatingActionButton = {
            SpeedDialFab(onFabClick = onAddTransaction)
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp)
        ) {
            item {
                Spacer(modifier = Modifier.height(16.dp))
                Greeting(name = user?.displayName?.split(" ")?.get(0) ?: "User")
                Spacer(modifier = Modifier.height(24.dp))
            }

            item {
                NetWorthCard(netWorth = viewModel.netWorth.collectAsState().value, currencySymbol = currencySymbol)
                Spacer(modifier = Modifier.height(16.dp))
                AccountsCarousel(accounts = accountBalances, currencySymbol = currencySymbol, onNavigateToAccounts = onNavigateToAccounts)
                Spacer(modifier = Modifier.height(24.dp))
            }

            item {
                DynamicHeaderCard(
                    insightState = aiInsightState,
                    monthlyIncome = monthlyIncome,
                    monthlyExpense = monthlyExpense,
                    currencySymbol = currencySymbol,
                    onGetInsight = { viewModel.getFinancialInsight() }
                )
                Spacer(modifier = Modifier.height(24.dp))
            }

            item {
                BudgetsOverview(
                    topBudgets = topBudgets,
                    currencySymbol = currencySymbol,
                    onNavigateToBudgets = onNavigateToBudgets
                )
                Spacer(modifier = Modifier.height(24.dp))
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Recent Transactions", style = MaterialTheme.typography.titleLarge)
                    TextButton(onClick = onNavigateToAllTransactions) {
                        Text("View All")
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
            }

            if (transactions.isEmpty()) {
                item {
                    Box(modifier = Modifier.fillParentMaxHeight(0.5f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Text("No transactions yet. Add one to get started!")
                    }
                }
            } else {
                items(transactions.take(5)) { transaction ->
                    TransactionItem(
                        transaction = transaction,
                        currencySymbol = currencySymbol,
                        onClick = { onTransactionClick(transaction.id) }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeTopAppBar(
    user: com.google.firebase.auth.FirebaseUser?,
    onProfileClick: () -> Unit
) {
    TopAppBar(
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Image(
                    painter = painterResource(id = R.mipmap.ic_launcher_foreground),
                    contentDescription = "App Logo",
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                )
                Text("SpendWise")
            }
        },
        actions = {
            IconButton(onClick = { /* TODO: Handle notifications */ }) {
                Icon(Icons.Outlined.Notifications, contentDescription = "Notifications")
            }
            IconButton(onClick = onProfileClick) {
                if (user?.photoUrl != null) {
                    AsyncImage(
                        model = user?.photoUrl,
                        contentDescription = "Profile Picture",
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Icon(
                        Icons.Default.AccountCircle,
                        contentDescription = "Profile",
                        modifier = Modifier.size(32.dp)
                    )
                }
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.surface,
        )
    )
}

@Composable
fun Greeting(name: String) {
    val calendar = Calendar.getInstance()
    val greetingText = when (calendar.get(Calendar.HOUR_OF_DAY)) {
        in 0..11 -> "Good morning, "
        in 12..16 -> "Good afternoon, "
        else -> "Good evening, "
    }
    Text(
        buildAnnotatedString {
            withStyle(style = SpanStyle(color = MaterialTheme.colorScheme.onSurfaceVariant)) {
                append(greetingText)
            }
            withStyle(style = SpanStyle(fontWeight = FontWeight.Bold)) {
                append(name)
            }
        },
        style = MaterialTheme.typography.headlineMedium,
    )
}

@Composable
fun NetWorthCard(netWorth: Double, currencySymbol: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Net Worth", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onPrimaryContainer)
            Text(
                text = "$currencySymbol${"%.2f".format(netWorth)}",
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
    }
}

@Composable
fun AccountsCarousel(
    accounts: List<AccountBalance>,
    currencySymbol: String,
    onNavigateToAccounts: () -> Unit
) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("Your Accounts", style = MaterialTheme.typography.titleLarge)
            TextButton(onClick = onNavigateToAccounts) {
                Text("View All")
            }
        }
        LazyRow(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            items(accounts) { accountBalance ->
                AccountBalanceCard(accountBalance = accountBalance, currencySymbol = currencySymbol)
            }
        }
    }
}

@Composable
fun AccountBalanceCard(accountBalance: AccountBalance, currencySymbol: String) {
    val balanceColor = when (accountBalance.account.type) {
        "Credit Card", "Loan Taken" -> if (accountBalance.currentBalance < 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.primary
    }
    Card(
        modifier = Modifier.width(150.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(accountBalance.account.name, style = MaterialTheme.typography.labelMedium)
            Text(
                text = "$currencySymbol${"%.2f".format(accountBalance.currentBalance)}",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                color = balanceColor
            )
        }
    }
}

@Composable
fun DynamicHeaderCard(
    insightState: AiInsightState,
    monthlyIncome: Double,
    monthlyExpense: Double,
    currencySymbol: String,
    onGetInsight: () -> Unit
) {
    var showInsight by remember { mutableStateOf(false) }

    Card(modifier = Modifier.fillMaxWidth()) {
        AnimatedContent(
            targetState = showInsight,
            transitionSpec = {
                fadeIn(animationSpec = tween(300)) togetherWith fadeOut(animationSpec = tween(300))
            }, label = "HeaderCardAnimation"
        ) { isShowingInsight ->
            if (isShowingInsight) {
                AiInsightCard(state = insightState, onShowSummary = { showInsight = false })
            } else {
                MonthlySummaryContent(
                    income = monthlyIncome,
                    expense = monthlyExpense,
                    currencySymbol = currencySymbol,
                    onShowInsight = {
                        onGetInsight()
                        showInsight = true
                    }
                )
            }
        }
    }
}

@Composable
fun AiInsightCard(
    state: AiInsightState,
    onShowSummary: () -> Unit
) {
    Column(modifier = Modifier.padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.AutoAwesome, "Smart Insight", tint = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Smart Insight", style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.weight(1f))
            IconButton(onClick = onShowSummary, modifier = Modifier.size(24.dp)) {
                Icon(Icons.Default.TrendingUp, "Show Summary")
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Box(modifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = 48.dp), contentAlignment = Alignment.CenterStart) {
            when {
                state.isLoading -> CircularProgressIndicator(modifier = Modifier.size(24.dp))
                state.error != null -> Text(state.error, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
                state.insight != null -> Text(state.insight, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
fun MonthlySummaryContent(
    income: Double,
    expense: Double,
    currencySymbol: String,
    onShowInsight: () -> Unit
) {
    Column {
        Row(
            modifier = Modifier.padding(start = 16.dp, end = 4.dp, top = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Monthly Summary", style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.weight(1f))
            IconButton(onClick = onShowInsight, modifier = Modifier.size(24.dp)) {
                Icon(Icons.Default.AutoAwesome, "Show Insight")
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp, start = 16.dp, end = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            KPICardContent(label = "Income", amount = income, currencySymbol = currencySymbol, modifier = Modifier.weight(1f))
            KPICardContent(label = "Expense", amount = expense, currencySymbol = currencySymbol, modifier = Modifier.weight(1f))
        }
    }
}

@Composable
fun KPICardContent(label: String, amount: Double, currencySymbol: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Text(
            text = "$currencySymbol${"%.2f".format(amount)}",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
fun BudgetsOverview(
    topBudgets: List<BudgetProgress>,
    currencySymbol: String,
    onNavigateToBudgets: () -> Unit
) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("Top Budgets", style = MaterialTheme.typography.titleLarge)
            TextButton(onClick = onNavigateToBudgets) {
                Text("View All")
                Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null)
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        if (topBudgets.isEmpty()) {
            Text("No budgets set for this month.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                topBudgets.forEach { budgetProgress ->
                    BudgetOverviewItem(
                        progress = budgetProgress,
                        currencySymbol = currencySymbol,
                        onClick = onNavigateToBudgets
                    )
                }
            }
        }
    }
}

@Composable
fun BudgetOverviewItem(
    progress: BudgetProgress,
    currencySymbol: String,
    onClick: () -> Unit,
) {
    val progressAnimation by animateFloatAsState(
        targetValue = progress.progress,
        animationSpec = ProgressIndicatorDefaults.ProgressAnimationSpec, label = ""
    )
    val progressColor = when {
        progress.progress > 1f -> MaterialTheme.colorScheme.error
        progress.progress > 0.8f -> Color(0xFFFF9800) // Orange
        else -> MaterialTheme.colorScheme.primary
    }

    Card(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(text = progress.budget.category, style = MaterialTheme.typography.titleMedium)
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
                modifier = Modifier.fillMaxWidth().height(8.dp).clip(MaterialTheme.shapes.small),
                color = progressColor
            )
        }
    }
}

@Composable
fun SpeedDialFab(onFabClick: (String) -> Unit) {
    var isExpanded by remember { mutableStateOf(false) }

    Box(contentAlignment = Alignment.BottomEnd) {
        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(16.dp)) {
            AnimatedVisibility(visible = isExpanded, enter = fadeIn() + slideInVertically { it }, exit = fadeOut() + slideOutVertically { it }) {
                SmallFloatingActionButton(
                    onClick = {
                        onFabClick("Transfer")
                        isExpanded = false
                    },
                    containerColor = MaterialTheme.colorScheme.secondaryContainer
                ) {
                    Icon(Icons.Default.SwapHoriz, "New Transfer")
                }
            }
            AnimatedVisibility(visible = isExpanded, enter = fadeIn() + slideInVertically { it }, exit = fadeOut() + slideOutVertically { it }) {
                SmallFloatingActionButton(
                    onClick = {
                        onFabClick("Income")
                        isExpanded = false
                    },
                    containerColor = MaterialTheme.colorScheme.secondaryContainer
                ) {
                    Icon(Icons.Default.ArrowUpward, "Add Income")
                }
            }
            AnimatedVisibility(visible = isExpanded, enter = fadeIn() + slideInVertically { it }, exit = fadeOut() + slideOutVertically { it }) {
                SmallFloatingActionButton(
                    onClick = {
                        onFabClick("Expense")
                        isExpanded = false
                    },
                    containerColor = MaterialTheme.colorScheme.secondaryContainer
                ) {
                    Icon(Icons.Default.ArrowDownward, "Add Expense")
                }
            }
        }

        FloatingActionButton(onClick = { isExpanded = !isExpanded }) {
            Icon(
                if (isExpanded) Icons.Default.Close else Icons.Default.Add,
                contentDescription = "Add Transaction"
            )
        }
    }
}

@Composable
fun TransactionItem(transaction: Transaction, currencySymbol: String, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = getIconForCategory(transaction.category),
                contentDescription = transaction.category,
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .padding(8.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(text = transaction.category, style = MaterialTheme.typography.bodyLarge)
                Text(text = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()).format(Date(transaction.date)), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            val amountColor = if (transaction.type == "Expense") MaterialTheme.colorScheme.error else Color(0xFF00897B)
            val amountText = if (transaction.type == "Expense") {
                "-$currencySymbol${"%.2f".format(transaction.amount)}"
            } else {
                "+$currencySymbol${"%.2f".format(transaction.amount)}"
            }
            Text(
                text = amountText,
                color = amountColor,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}
