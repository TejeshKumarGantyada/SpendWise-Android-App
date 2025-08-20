package com.tejesh.spendwise.Screens

import android.app.DatePickerDialog
import android.graphics.Typeface
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.patrykandpatrick.vico.compose.axis.horizontal.rememberBottomAxis
import com.patrykandpatrick.vico.compose.axis.vertical.rememberStartAxis
import com.patrykandpatrick.vico.compose.chart.Chart
import com.patrykandpatrick.vico.compose.chart.column.columnChart
import com.patrykandpatrick.vico.compose.chart.line.lineChart
import com.patrykandpatrick.vico.compose.legend.horizontalLegend
import com.patrykandpatrick.vico.compose.style.ProvideChartStyle
import com.patrykandpatrick.vico.core.axis.AxisPosition
import com.patrykandpatrick.vico.core.axis.formatter.AxisValueFormatter
import com.patrykandpatrick.vico.core.chart.line.LineChart.LineSpec
import com.patrykandpatrick.vico.core.component.shape.LineComponent
import com.patrykandpatrick.vico.core.component.shape.ShapeComponent
import com.patrykandpatrick.vico.core.component.shape.Shapes
import com.patrykandpatrick.vico.core.component.text.TextComponent
import com.patrykandpatrick.vico.core.entry.ChartEntryModelProducer
import com.patrykandpatrick.vico.core.entry.FloatEntry
import com.patrykandpatrick.vico.core.entry.entryOf
import com.tejesh.spendwise.data.DailyTrend
import com.tejesh.spendwise.data.MonthlySummary
import com.tejesh.spendwise.data.Transaction
import com.tejesh.spendwise.data.TransactionFilters
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChartsScreen(viewModel: TransactionViewModel) {
    // --- State Collection ---
    val transactions by viewModel.filteredTransactions.collectAsState()
    val filters by viewModel.filters.collectAsState()
    val allTransactionsForFilter by viewModel.allTransactions.collectAsState(initial = emptyList())
    val currencySymbol by viewModel.currencySymbol.collectAsState()

    // --- Chart Data Calculation (Reacts to filters) ---
    val expensePieData = transactions.filter { it.type == "Expense" }.groupBy { it.category }.mapValues { it.value.sumOf { tx -> tx.amount } }
    val incomePieData = transactions.filter { it.type == "Income" }.groupBy { it.category }.mapValues { it.value.sumOf { tx -> tx.amount } }
    val monthlySummaryData = transactions.groupBy { SimpleDateFormat("yyyy-MM", Locale.getDefault()).format(Date(it.date)) }.map { (month, txs) -> MonthlySummary(month, txs.filter { it.type == "Income" }.sumOf { it.amount }.toFloat(), txs.filter { it.type == "Expense" }.sumOf { it.amount }.toFloat()) }.sortedBy { it.yearMonth }
    val dailyTrendData = transactions.filter { it.date >= Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -30) }.timeInMillis }.groupBy { Calendar.getInstance().apply { timeInMillis = it.date; set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0) }.timeInMillis }.map { (ts, txs) -> DailyTrend(ts, txs.filter { it.type == "Income" }.sumOf { it.amount }.toFloat(), txs.filter { it.type == "Expense" }.sumOf { it.amount }.toFloat()) }.sortedBy { it.timestamp }

    var selectedTab by remember { mutableStateOf("Pie Chart") }
    val tabs = listOf("Pie Chart", "Bar Chart", "Line Chart")
    val scope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState()
    var showFilterSheet by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Analytics") },
                actions = {
                    IconButton(onClick = { showFilterSheet = true }) {
                        Icon(Icons.Default.FilterList, contentDescription = "Filter Charts")
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp)
        ) {
            TabRow(selectedTabIndex = tabs.indexOf(selectedTab)) {
                tabs.forEach { title ->
                    Tab(
                        selected = selectedTab == title,
                        onClick = { selectedTab = title },
                        text = { Text(title) }
                    )
                }
            }
            Spacer(modifier = Modifier.height(16.dp))

            Crossfade(targetState = selectedTab, label = "ChartCrossfade") { tab ->
                when (tab) {
                    "Pie Chart" -> PieChartTab(expenseData = expensePieData, incomeData = incomePieData, currencySymbol = currencySymbol)
                    "Bar Chart" -> BarChartTab(summaryData = monthlySummaryData, currencySymbol = currencySymbol)
                    "Line Chart" -> LineChartTab(trendData = dailyTrendData)
                }
            }
        }

        if (showFilterSheet) {
            ModalBottomSheet(onDismissRequest = { showFilterSheet = false }, sheetState = sheetState) {
                FilterSheetContent(
                    allTransactions = allTransactionsForFilter,
                    currentFilters = filters,
                    onApply = { newFilters ->
                        viewModel.applyFilters(newFilters)
                        scope.launch { sheetState.hide() }.invokeOnCompletion { showFilterSheet = false }
                    },
                    onClear = {
                        viewModel.clearFilters()
                        scope.launch { sheetState.hide() }.invokeOnCompletion { showFilterSheet = false }
                    }
                )
            }
        }
    }
}

@Composable
fun PieChartTab(expenseData: Map<String, Double>, incomeData: Map<String, Double>, currencySymbol: String) {
    var selectedPie by remember { mutableStateOf("Expense") }
    val pieTypes = listOf("Expense", "Income")

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        SegmentedButton(segments = pieTypes, selectedSegment = selectedPie, onSegmentSelected = { selectedPie = it })
        Spacer(modifier = Modifier.height(16.dp))

        Card(modifier = Modifier.fillMaxWidth()) {
            when (selectedPie) {
                "Expense" -> {
                    if (expenseData.isEmpty()) NoDataPlaceholder("No expense data for this period.")
                    else PieChart(data = expenseData, title = "Expenses by Category", currencySymbol = currencySymbol)
                }
                "Income" -> {
                    if (incomeData.isEmpty()) NoDataPlaceholder("No income data for this period.")
                    else PieChart(data = incomeData, title = "Income by Source", currencySymbol = currencySymbol)
                }
            }
        }
    }
}

@Composable
fun BarChartTab(summaryData: List<MonthlySummary>, currencySymbol: String) {
    Card(modifier = Modifier.fillMaxWidth()) {
        if (summaryData.isEmpty()) NoDataPlaceholder("No data for this period.")
        else IncomeExpenseBarChart(monthlySummaries = summaryData, currencySymbol = currencySymbol)
    }
}

@Composable
fun LineChartTab(trendData: List<DailyTrend>) {
    Card(modifier = Modifier.fillMaxWidth()) {
        if (trendData.isEmpty()) NoDataPlaceholder("No trend data for this period.")
        else TrendLineChart(dailyTrend = trendData)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SegmentedButton(segments: List<String>, selectedSegment: String, onSegmentSelected: (String) -> Unit) {
    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
        segments.forEachIndexed { index, label ->
            SegmentedButton(
                selected = label == selectedSegment,
                onClick = { onSegmentSelected(label) },
                shape = SegmentedButtonDefaults.itemShape(index = index, count = segments.size),
                label = { Text(label) }
            )
        }
    }
}

@Composable
fun TrendLineChart(dailyTrend: List<DailyTrend>) {
    val chartModelProducer = ChartEntryModelProducer()
    val dateFormat = SimpleDateFormat("d MMM", Locale.getDefault())
    LaunchedEffect(dailyTrend) {
        chartModelProducer.setEntries(
            dailyTrend.mapIndexed { index, trend -> FloatEntry(index.toFloat(), trend.totalIncome) },
            dailyTrend.mapIndexed { index, trend -> FloatEntry(index.toFloat(), trend.totalExpense) }
        )
    }
    val bottomAxisFormatter = AxisValueFormatter<AxisPosition.Horizontal.Bottom> { value, _ ->
        val timestamp = dailyTrend.getOrNull(value.toInt())?.timestamp
        if (timestamp != null) dateFormat.format(Date(timestamp)) else ""
    }
    val labelColor = MaterialTheme.colorScheme.onSurface
    val textComponent = remember(labelColor) { TextComponent.Builder().apply { color = labelColor.toArgb() }.build() }
    val legend = horizontalLegend(
        items = listOf(
            com.patrykandpatrick.vico.core.legend.LegendItem(icon = ShapeComponent(Shapes.pillShape, Color(0xFF00897B).toArgb()), label = textComponent, labelText = "Income"),
            com.patrykandpatrick.vico.core.legend.LegendItem(icon = ShapeComponent(Shapes.pillShape, Color(0xFFE53935).toArgb()), label = textComponent, labelText = "Expense")
        ),
        iconSize = 8.dp, iconPadding = 8.dp,
    )
    ProvideChartStyle {
        Chart(
            chart = lineChart(
                lines = listOf(
                    LineSpec(lineColor = Color(0xFF00897B).toArgb()),
                    LineSpec(lineColor = Color(0xFFE53935).toArgb())
                )
            ),
            chartModelProducer = chartModelProducer,
            startAxis = rememberStartAxis(),
            bottomAxis = rememberBottomAxis(valueFormatter = bottomAxisFormatter, guideline = null, labelRotationDegrees = 45f),
            legend = legend
        )
    }
}

@Composable
fun IncomeExpenseBarChart(monthlySummaries: List<MonthlySummary>, currencySymbol: String) {
    val chartModelProducer = ChartEntryModelProducer()
    LaunchedEffect(monthlySummaries) {
        chartModelProducer.setEntries(
            monthlySummaries.mapIndexed { index, summary -> entryOf(index, summary.totalIncome) },
            monthlySummaries.mapIndexed { index, summary -> entryOf(index, summary.totalExpense) }
        )
    }
    val bottomAxisValueFormatter = AxisValueFormatter<AxisPosition.Horizontal.Bottom> { value, _ ->
        (monthlySummaries.getOrNull(value.toInt())?.yearMonth ?: "").substring(5)
    }
    val labelColor = MaterialTheme.colorScheme.onSurface
    val textComponent = remember(labelColor) { TextComponent.Builder().apply { color = labelColor.toArgb() }.build() }
    val dataLabel = remember(labelColor) {
        TextComponent.Builder().apply {
            color = labelColor.toArgb()
            textSizeSp = 10f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD) // Corrected line
        }.build()
    }
    ProvideChartStyle {
        val legend = horizontalLegend(
            items = listOf(
                com.patrykandpatrick.vico.core.legend.LegendItem(icon = ShapeComponent(Shapes.pillShape, Color(0xFF00897B).toArgb()), label = textComponent, labelText = "Income"),
                com.patrykandpatrick.vico.core.legend.LegendItem(icon = ShapeComponent(Shapes.pillShape, Color(0xFFE53935).toArgb()), label = textComponent, labelText = "Expense")
            ),
            iconSize = 8.dp, iconPadding = 8.dp,
        )
        Chart(
            chart = columnChart(
                columns = listOf(
                    LineComponent(MaterialTheme.colorScheme.primary.toArgb()).apply { thicknessDp = 8f },
                    LineComponent(MaterialTheme.colorScheme.error.toArgb()).apply { thicknessDp = 8f }
                ),
                dataLabel = dataLabel
            ),
            chartModelProducer = chartModelProducer,
            startAxis = rememberStartAxis(),
            bottomAxis = rememberBottomAxis(valueFormatter = bottomAxisValueFormatter),
            legend = legend
        )
    }
}

@Composable
fun PieChart(data: Map<String, Double>, title: String, currencySymbol: String) {
    val predefinedColors = listOf(
        Color(0xFFF44336), Color(0xFF2196F3), Color(0xFF4CAF50), Color(0xFFFFEB3B),
        Color(0xFFFF9800), Color(0xFF9C27B0), Color.Cyan, Color.Magenta
    )
    val total = data.values.sum()
    val angles = data.values.map { 360 * (it / total).toFloat() }
    val colors = remember(data.keys) {
        data.keys.mapIndexed { index, _ -> predefinedColors[index % predefinedColors.size] }
    }
    Column(modifier = Modifier.fillMaxWidth().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(title, style = MaterialTheme.typography.titleLarge)
        Box(modifier = Modifier.size(200.dp).padding(16.dp), contentAlignment = Alignment.Center) {
            var startAngle = 0f
            Canvas(modifier = Modifier.fillMaxSize()) {
                angles.forEachIndexed { index, angle ->
                    drawArc(color = colors[index], startAngle = startAngle, sweepAngle = angle, useCenter = true)
                    startAngle += angle
                }
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
        Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.Start) {
            data.keys.forEachIndexed { index, categoryName ->
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 4.dp)) {
                    Box(modifier = Modifier.size(16.dp).clip(CircleShape).background(colors[index]))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = "$categoryName: $currencySymbol${"%.2f".format(data[categoryName])}", fontSize = 16.sp)
                }
            }
        }
    }
}

@Composable
fun NoDataPlaceholder(message: String) {
    Box(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = Icons.Outlined.BarChart,
                contentDescription = "No Data",
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(message, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
