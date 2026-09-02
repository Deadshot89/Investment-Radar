package de.tobias.investmentradar

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.ShowChart
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.firebase.messaging.FirebaseMessaging
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

private val RadarBg = Color(0xFF08111F)
private val RadarSurface = Color(0xFF101C2D)
private val RadarSurface2 = Color(0xFF16243A)
private val RadarGreen = Color(0xFF2EE59D)
private val RadarBlue = Color(0xFF4C8DFF)
private val RadarYellow = Color(0xFFFFC857)
private val RadarRed = Color(0xFFFF6577)
private val RadarText = Color(0xFFF2F6FC)
private val RadarMuted = Color(0xFF91A1B7)
private val RadarPurple = Color(0xFF9F7BFF)
private val RadarCyan = Color(0xFF4DE6FF)
private val RadarPink = Color(0xFFFF5EDB)
private val RadarGlow = Color(0x332EE59D)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (FirebaseBootstrap.isConfigured()) {
            FirebaseMessaging.getInstance().subscribeToTopic("investment-alerts")
            PortfolioStore.read(this).forEach { itemId ->
                FirebaseMessaging.getInstance().subscribeToTopic(MainViewModel.holdingTopic(itemId))
            }
        }
        setContent { InvestmentRadarUi(initialTab = if (intent.getBooleanExtra("openAlerts", false)) 3 else 0) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InvestmentRadarUi(vm: MainViewModel = viewModel(), initialTab: Int = 0) {
    val state by vm.state.collectAsState()
    val holdingIds by vm.holdingIds.collectAsState()
    val positions by vm.positions.collectAsState()
    val customItems by vm.customItems.collectAsState()
    val watchlistIds by vm.watchlistIds.collectAsState()
    val alerts by vm.alerts.collectAsState()
    val alertPreferences by vm.alertPreferences.collectAsState()
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("investment_radar_settings", 0) }
    var budget by remember { mutableIntStateOf(prefs.getInt("monthly_budget", 100).coerceIn(10, 10000)) }
    var tab by remember { mutableIntStateOf(initialTab.coerceIn(0, 3)) }
    var selectedDetailId by remember { mutableStateOf<String?>(null) }
    var detailReturnTab by remember { mutableIntStateOf(initialTab.coerceIn(0, 3)) }
    var missingAlertItemMessage by rememberSaveable { mutableStateOf<String?>(null) }
    var budgetDialog by remember { mutableStateOf(false) }
    var investmentDialogItem by remember { mutableStateOf<InvestmentItem?>(null) }
    var customAssetDialog by remember { mutableStateOf(false) }
    var editingCustomAsset by remember { mutableStateOf<CustomInvestment?>(null) }
    var notificationPermissionAsked by remember { mutableStateOf(false) }
    var availableUpdate by remember { mutableStateOf<AppUpdateInfo?>(null) }
    var updateStatusMessage by remember { mutableStateOf<String?>(null) }
    var updateCheckRequested by remember { mutableIntStateOf(0) }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }

    LaunchedEffect(updateCheckRequested) {
        if (updateCheckRequested == 0) {
            availableUpdate = AppUpdateManager.check(context)
        } else {
            when (val result = AppUpdateManager.checkResult(context)) {
                is UpdateCheckResult.Available -> availableUpdate = result.update
                is UpdateCheckResult.Current -> updateStatusMessage = "Du nutzt bereits die aktuelle Version ${result.versionName}."
                is UpdateCheckResult.Error -> updateStatusMessage = "Update konnte nicht geprüft werden: ${result.message}"
            }
        }
    }

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= 33 &&
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED &&
            !notificationPermissionAsked
        ) {
            notificationPermissionAsked = true
            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = RadarGreen,
            secondary = RadarBlue,
            background = RadarBg,
            surface = RadarSurface,
            surfaceVariant = RadarSurface2,
            onBackground = RadarText,
            onSurface = RadarText
        )
    ) {
        Scaffold(
            containerColor = RadarBg,
            topBar = {
                TopAppBar(
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = RadarBg),
                    title = {
                        Column {
                            Text("Investment Radar", fontWeight = FontWeight.Black)
                            Text("Klare Entscheidungen statt Datenflut", style = MaterialTheme.typography.labelMedium, color = RadarMuted)
                        }
                    },
                    actions = {
                        IconButton(onClick = { budgetDialog = true }) { Icon(Icons.Default.Edit, contentDescription = "Budget ändern") }
                        TextButton(onClick = { updateCheckRequested++ }) { Text("Update") }
                        IconButton(onClick = { vm.refresh() }) { Icon(Icons.Default.Refresh, contentDescription = "Aktualisieren") }
                    }
                )
            },
            bottomBar = {
                NavigationBar(containerColor = RadarSurface) {
                    NavigationBarItem(selected = tab == 0, onClick = { selectedDetailId = null; tab = 0 }, icon = { Icon(Icons.Default.ShowChart, null) }, label = { Text("Live") })
                    NavigationBarItem(selected = tab == 1, onClick = { selectedDetailId = null; tab = 1 }, icon = { Icon(Icons.Default.Search, null) }, label = { Text("Radar") })
                    NavigationBarItem(selected = tab == 2, onClick = { selectedDetailId = null; tab = 2 }, icon = { Icon(Icons.Default.Favorite, null) }, label = { Text("Portfolio") })
                    NavigationBarItem(selected = tab == 3, onClick = { selectedDetailId = null; tab = 3 }, icon = { Icon(Icons.Default.Notifications, null) }, label = { Text("Alarme") })
                }
            }
        ) { padding ->
            Box(
                Modifier
                    .padding(padding)
                    .fillMaxSize()
                    .background(Brush.verticalGradient(listOf(Color(0xFF050B14), Color(0xFF0B1628), RadarBg)))
            ) {
                when (val s = state) {
                    UiState.Loading -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                    is UiState.Error -> ErrorView(s.message) { vm.refresh() }
                    is UiState.Ready -> {
                        val personalById = RecommendationEngine.plan(
                            s.data.items,
                            budget,
                            PortfolioAnalysis.values(s.data.items, positions, customItems)
                        ).items.associateBy { it.itemId }
                        val detailId = selectedDetailId
                        if (detailId != null) {
                            val detailItem = s.data.items.firstOrNull { it.id == detailId }
                            val detailCustom = customItems.firstOrNull { it.id == detailId }
                            InvestmentDetailScreen(
                                item = detailItem,
                                customItem = detailCustom,
                                position = positions[detailId],
                                personalRecommendation = personalById[detailId],
                                isWatchlisted = detailId in watchlistIds,
                                onBack = { selectedDetailId = null; tab = detailReturnTab },
                                onToggleWatchlist = vm::toggleWatchlist,
                                onEditPosition = { investmentDialogItem = it },
                                onOpenPortfolio = { selectedDetailId = null; tab = 2 }
                            )
                        } else when (tab) {
                            0 -> DashboardScreen(
                                data = s.data,
                                budget = budget,
                                holdingIds = holdingIds,
                                positions = positions,
                                watchlistIds = watchlistIds,
                                onEditBudget = { budgetDialog = true },
                                onOpenRadar = { selectedDetailId = null; tab = 1 }
                            )
                            1 -> RadarScreenV2(
                                items = s.data.items,
                                holdingIds = holdingIds,
                                watchlistIds = watchlistIds,
                                personalById = personalById,
                                onToggleWatchlist = vm::toggleWatchlist,
                                onBought = { investmentDialogItem = it },
                                onEditInvestment = { investmentDialogItem = it },
                                onOpenDetail = { id ->
                                    detailReturnTab = 1
                                    selectedDetailId = id
                                }
                            )
                            2 -> PortfolioDashboard(
                                items = s.data.items,
                                positions = positions,
                                customItems = customItems,
                                personalById = personalById,
                                onOpenDetail = { id ->
                                    detailReturnTab = 2
                                    selectedDetailId = id
                                },
                                onEdit = { investmentDialogItem = it },
                                onRemove = vm::removeHolding,
                                onAddCustom = { customAssetDialog = true },
                                onEditCustom = { editingCustomAsset = it },
                                onRemoveCustom = vm::removeCustomInvestment
                            )
                            else -> AlertsScreen(
                                alerts = alerts,
                                preferences = alertPreferences,
                                onOpen = { stored ->
                                    vm.markAlertRead(stored.alert.id)
                                    val id = stored.alert.itemId
                                    when {
                                        s.data.items.any { it.id == id } -> {
                                            detailReturnTab = 3
                                            selectedDetailId = id
                                        }
                                        customItems.any { it.id == id } -> {
                                            detailReturnTab = 3
                                            selectedDetailId = id
                                        }
                                        else -> missingAlertItemMessage = "Das Wertpapier ist im aktuellen Radar nicht mehr verfügbar."
                                    }
                                },
                                onMarkAllRead = vm::markAllAlertsRead,
                                onDelete = vm::deleteAlert,
                                onClear = vm::clearAlerts,
                                onPreferencesChange = vm::updateAlertPreferences
                            )
                        }
                    }
                }
            }
        }
    }

    if (budgetDialog) {
        BudgetDialog(
            current = budget,
            onDismiss = { budgetDialog = false },
            onSave = { newBudget ->
                budget = newBudget.coerceIn(10, 10000)
                prefs.edit().putInt("monthly_budget", budget).apply()
                budgetDialog = false
            }
        )
    }

    investmentDialogItem?.let { item ->
        PurchaseHistoryDialog(
            item = item,
            current = positions[item.id] ?: PortfolioPosition(item.id),
            onDismiss = { investmentDialogItem = null },
            onUpsertPurchase = { purchase -> vm.upsertPurchase(item.id, purchase) },
            onDeletePurchase = { purchaseId -> vm.removePurchase(item.id, purchaseId) },
            onUpsertSale = { sale -> vm.upsertSale(item.id, sale) },
            onDeleteSale = { saleId -> vm.removeSale(item.id, saleId) }
        )
    }

    if (customAssetDialog) {
        CustomInvestmentDialog(
            existing = null,
            onDismiss = { customAssetDialog = false },
            onSave = { item, purchase ->
                vm.addCustomInvestment(item, purchase)
                customAssetDialog = false
            }
        )
    }

    editingCustomAsset?.let { existing ->
        CustomInvestmentDialog(
            existing = existing,
            onDismiss = { editingCustomAsset = null },
            onSave = { item, _ ->
                vm.updateCustomInvestment(item)
                editingCustomAsset = null
            }
        )
    }

    missingAlertItemMessage?.let { message ->
        AlertDialog(
            onDismissRequest = { missingAlertItemMessage = null },
            title = { Text("Wertpapier nicht verfügbar") },
            text = { Text(message) },
            confirmButton = {
                TextButton(onClick = { missingAlertItemMessage = null }) { Text("OK") }
            }
        )
    }

    availableUpdate?.let { update ->
        AlertDialog(
            onDismissRequest = { availableUpdate = null },
            title = { Text("Update verfügbar") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Neue Version ${update.versionName} ist verfügbar.", fontWeight = FontWeight.Bold)
                    if (update.notes.isNotBlank()) Text(update.notes, color = RadarMuted)
                    Text("Die App lädt die neue APK selbst herunter. Android fragt dich danach nur noch, ob Investment Radar aktualisiert werden soll.", color = RadarMuted)
                }
            },
            confirmButton = {
                Button(onClick = {
                    AppUpdateManager.openUpdate(context, update)
                    availableUpdate = null
                }) { Text("Jetzt aktualisieren") }
            },
            dismissButton = { TextButton(onClick = { availableUpdate = null }) { Text("Später") } }
        )
    }

    updateStatusMessage?.let { message ->
        AlertDialog(
            onDismissRequest = { updateStatusMessage = null },
            title = { Text("Update") },
            text = { Text(message) },
            confirmButton = { TextButton(onClick = { updateStatusMessage = null }) { Text("OK") } }
        )
    }
}

@Composable
private fun DashboardScreen(
    data: DashboardData,
    budget: Int,
    holdingIds: Set<String>,
    positions: Map<String, PortfolioPosition>,
    watchlistIds: Set<String>,
    onEditBudget: () -> Unit,
    onOpenRadar: () -> Unit
) {
    val context = LocalContext.current
    val currentValues = data.items.associate { item ->
        val position = positions[item.id]
        val value = position?.currentValue(euroComparablePrice(item)) ?: position?.investedAmount ?: 0.0
        item.id to value.coerceAtLeast(0.0)
    }
    val personalPlan = RecommendationEngine.plan(data.items, budget, currentValues)
    val cashAmount = personalPlan.cashAmount
    val personalById = personalPlan.items.associateBy { it.itemId }
    val allocations = personalPlan.items.associate { it.itemId to it.allocationEur }
    val top = data.items
        .filter { RecommendationPresentation.effectiveRecommendation(it) == "BUY" }
        .maxByOrNull { it.scoreTotal ?: Int.MIN_VALUE }
    val buyCandidates = data.items
        .filter { RecommendationPresentation.effectiveRecommendation(it) == "BUY" }
        .sortedByDescending { it.scoreTotal ?: Int.MIN_VALUE }
    val reviewItems = data.items.filter { item ->
        item.id in holdingIds && RecommendationPresentation.effectiveRecommendation(item) == "REVIEW"
    }
    val missingQuoteItems = data.items.filter { it.status.equals("EIGEN", true) && it.price == null }
    val concentrationWarning = personalPlan.items.maxByOrNull { it.currentWeightPct }
        ?.takeIf { it.currentWeightPct >= 40.0 }
        ?.let { row -> data.items.firstOrNull { it.id == row.itemId }?.let { it to row.currentWeightPct } }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 14.dp),
        contentPadding = PaddingValues(bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item { Spacer(Modifier.height(2.dp)) }
        item {
            NeonPanel(accent = RadarCyan) {
                Text("LIVE DASHBOARD", style = MaterialTheme.typography.labelLarge, color = RadarCyan, fontWeight = FontWeight.Black)
                Text("Analyse V2 für deinen nächsten Monatskauf", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black)
                Text("Qualität, Bewertung, Wachstum, Momentum, Risiko und deine aktuelle Depotgewichtung fließen zusammen.", color = RadarMuted)
            }
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DarkMetricCard("MARKT", data.marketLight.uppercase(), marketAccent(data.marketLight), Modifier.weight(1f))
                DarkMetricCard("BUDGET", "$budget €", RadarBlue, Modifier.weight(1f), onClick = onEditBudget)
                DarkMetricCard("SIGNAL", if (top != null) "AKTIV" else "WARTEN", if (top != null) RadarGreen else RadarYellow, Modifier.weight(1f))
            }
        }

        item {
            NeonPanel(accent = if (reviewItems.isNotEmpty() || concentrationWarning != null) RadarYellow else RadarCyan) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Jetzt relevant", color = RadarCyan, fontWeight = FontWeight.Black, style = MaterialTheme.typography.labelLarge)
                        Text("Deine wichtigsten Punkte auf einen Blick", fontWeight = FontWeight.Black, style = MaterialTheme.typography.titleMedium)
                    }
                    StatusPill(if (reviewItems.isNotEmpty()) "PRÜFEN" else "AKTUELL")
                }
                RelevantRow("Kaufkandidaten", buyCandidates.take(3).joinToString { it.ticker }.ifBlank { "Keine" }, RadarGreen)
                RelevantRow("Prüfsignale", reviewItems.joinToString { it.ticker }.ifBlank { "Keine" }, if (reviewItems.isEmpty()) RadarMuted else RadarYellow)
                RelevantRow("Watchlist", "${watchlistIds.size} Werte", RadarPurple)
                if (missingQuoteItems.isNotEmpty()) RelevantRow("Kursdaten fehlen", missingQuoteItems.joinToString { it.ticker }, RadarYellow)
                concentrationWarning?.let { (item, share) ->
                    RelevantRow("Konzentration", "${item.ticker} ${String.format(Locale.GERMANY, "%.1f", share)} %", RadarRed)
                }
                TextButton(onClick = onOpenRadar, modifier = Modifier.align(Alignment.End)) { Text("Radar öffnen") }
            }
        }

        if (top != null) item {
            val label = RecommendationPresentation.label(top)
            val personal = personalById[top.id]
            val amount = personal?.allocationEur ?: 0
            Text("HEUTIGE EMPFEHLUNG", style = MaterialTheme.typography.labelLarge, color = RadarMuted, fontWeight = FontWeight.Bold)
            NeonPanel(accent = recommendationColor(label)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text(label, color = recommendationColor(label), fontWeight = FontWeight.Black, style = MaterialTheme.typography.titleMedium)
                        Text(top.name, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black)
                        Text("${top.ticker} · ${top.type} · Risiko ${top.risk}/5", color = RadarMuted)
                    }
                    ScoreRing(top.scoreTotal ?: 0)
                }
                PortfolioBadgeRow(
                    listOf(
                        "Monatskauf" to if (amount > 0) "$amount €" else "0 €",
                        "Score" to RecommendationPresentation.scoreText(top.scoreTotal),
                        "Signal" to RecommendationPresentation.confidence(top),
                        "Depotanteil" to personal?.currentWeightPct?.let { String.format(Locale.GERMANY, "%.1f %%", it) }.orEmpty().ifBlank { "–" }
                    )
                )
                Text(
                    if (amount > 0) "Diesen Monat $amount € investieren" else personal?.explanation ?: "DIESEN MONAT WARTEN",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Black,
                    color = recommendationColor(label)
                )
                Text(personal?.explanation ?: RecommendationPresentation.topReasons(top).joinToString(" · ").ifBlank { "Analyse liegt vor." }, color = RadarText)
                Text(priceLine(top), color = RadarMuted)
                ScoreBreakdownCard(top)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = { TradeRepublicNavigator.open(context, top) },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = RadarGreen, contentColor = Color(0xFF05150E))
                    ) {
                        Icon(Icons.Default.OpenInNew, null)
                        Spacer(Modifier.width(6.dp))
                        Text("Trade Republic öffnen", fontWeight = FontWeight.Bold)
                    }
                    OutlinedButton(onClick = { openMarketQuote(context, top) }, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Default.ShowChart, null)
                        Spacer(Modifier.width(6.dp))
                        Text("Kurs")
                    }
                }
            }
        }

        if (cashAmount > 0) item {
            NeonPanel(accent = RadarYellow) {
                Text("DIESEN MONAT WARTEN", color = RadarYellow, fontWeight = FontWeight.Black, style = MaterialTheme.typography.titleMedium)
                Text("$cashAmount € bleiben als Cash, weil aktuell kein geeigneter persönlicher Neukauf übrig bleibt.", color = RadarMuted)
            }
        }

        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column {
                    Text("DEIN KAUFPLAN", style = MaterialTheme.typography.labelLarge, color = RadarMuted, fontWeight = FontWeight.Bold)
                    Text("$budget € Monatsbudget", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
                }
                FilledTonalButton(onClick = onEditBudget) {
                    Icon(Icons.Default.AccountBalanceWallet, null)
                    Spacer(Modifier.width(6.dp))
                    Text("Ändern")
                }
            }
        }

        items(data.items.sortedByDescending { allocations[it.id] ?: 0 }) { item ->
            RecommendationRow(item, personalById[item.id]) { TradeRepublicNavigator.open(context, item) }
        }

        item {
            Text(
                "Nur objektive BUY-Signale erhalten neues Budget. Depotkonzentration und Risiko können die persönliche Zuteilung reduzieren oder blockieren. Keine automatische Order.",
                style = MaterialTheme.typography.bodySmall,
                color = RadarMuted,
                modifier = Modifier.padding(top = 6.dp)
            )
        }
    }
}

@Composable
private fun CustomInvestmentDialog(
    existing: CustomInvestment?,
    onDismiss: () -> Unit,
    onSave: (CustomInvestment, PortfolioPurchase?) -> Unit
) {
    var name by remember(existing?.id) { mutableStateOf(existing?.name.orEmpty()) }
    var ticker by remember(existing?.id) { mutableStateOf(existing?.ticker.orEmpty()) }
    var isin by remember(existing?.id) { mutableStateOf(existing?.isin.orEmpty()) }
    var type by remember(existing?.id) { mutableStateOf(existing?.type ?: "Aktie") }
    var tradeRepublicUrl by remember(existing?.id) { mutableStateOf(existing?.tradeRepublicUrl.orEmpty()) }
    var risk by remember(existing?.id) { mutableIntStateOf(existing?.risk ?: 3) }
    var manualPriceText by remember(existing?.id) { mutableStateOf(existing?.manualPriceEur?.let(::formatEditableNumber).orEmpty()) }
    var dateText by remember(existing?.id) { mutableStateOf(todayPurchaseDate()) }
    var amountText by remember(existing?.id) { mutableStateOf("") }
    var sharesText by remember(existing?.id) { mutableStateOf("") }

    val amount = parseDecimal(amountText)
    val shares = parseDecimal(sharesText)
    val manualPrice = parseDecimal(manualPriceText)?.takeIf { it > 0.0 }
    val newValid = existing != null || (isValidPurchaseDate(dateText) && amount != null && amount > 0 && shares != null && shares > 0)
    val metaValid = name.isNotBlank() && ticker.isNotBlank()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (existing == null) "Aktie/ETF hinzufügen" else "Eigenen Wert bearbeiten") },
        text = {
            Column(Modifier.heightIn(max = 600.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Eigene Werte", color = RadarPurple, fontWeight = FontWeight.Black)
                OutlinedTextField(name, { name = it.take(80) }, label = { Text("Name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(ticker, { ticker = it.uppercase().filter { ch -> ch.isLetterOrDigit() || ch in ".-" }.take(20) }, label = { Text("Ticker") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(isin, { isin = it.uppercase().filter(Char::isLetterOrDigit).take(20) }, label = { Text("ISIN") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = type == "Aktie", onClick = { type = "Aktie" }, label = { Text("Aktie") }, modifier = Modifier.weight(1f))
                    FilterChip(selected = type == "ETF", onClick = { type = "ETF" }, label = { Text("ETF") }, modifier = Modifier.weight(1f))
                }
                Text("Risiko", color = RadarMuted, style = MaterialTheme.typography.labelSmall)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    (1..5).forEach { level ->
                        FilterChip(selected = risk == level, onClick = { risk = level }, label = { Text(level.toString()) }, modifier = Modifier.weight(1f))
                    }
                }
                OutlinedTextField(tradeRepublicUrl, { tradeRepublicUrl = it.take(250) }, label = { Text("Trade-Republic-Link (optional)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(manualPriceText, { manualPriceText = sanitizeDecimalInput(it) }, label = { Text("MANUELLER EUR-KURS (optional)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Text("Nur als Fallback, wenn keine Kursquelle den Wert findet.", color = RadarMuted, style = MaterialTheme.typography.bodySmall)
                if (existing == null) {
                    HorizontalDivider(color = RadarSurface2)
                    Text("Ersten Kauf erfassen", fontWeight = FontWeight.Black)
                    OutlinedTextField(dateText, { dateText = it.filter { ch -> ch.isDigit() || ch == '.' }.take(10) }, label = { Text("Kaufdatum (TT.MM.JJJJ)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(amountText, { amountText = sanitizeDecimalInput(it) }, label = { Text("Investierter Betrag in €") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(sharesText, { sharesText = sanitizeDecimalInput(it) }, label = { Text("Stückzahl / Anteile") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    val unitPrice = if (amount != null && shares != null && shares > 0) amount / shares else null
                    Text("Kaufkurs: ${unitPrice?.let(::formatMoney) ?: "–"}", color = RadarGreen, fontWeight = FontWeight.Bold)
                }
                Text("Der aktuelle Kurs wird anschließend automatisch über das Investment-Radar-Backend geladen. Falls der Ticker nicht gefunden wird, bleibt die Position trotzdem gespeichert.", color = RadarMuted, style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = {
            Button(enabled = metaValid && newValid, onClick = {
                val id = existing?.id ?: CustomInvestmentStore.createId(ticker, isin)
                val item = CustomInvestment(id, name.trim(), ticker.trim().uppercase(), isin.trim().uppercase(), type, tradeRepublicUrl.trim(), risk, manualPrice)
                val purchase = if (existing == null) PortfolioPurchase(UUID.randomUUID().toString(), dateText, amount!!, shares!!) else null
                onSave(item, purchase)
            }) { Text(if (existing == null) "Hinzufügen" else "Speichern") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Abbrechen") } }
    )
}

@Composable
private fun RelevantRow(label: String, value: String, accent: Color) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(Color(0x0DFFFFFF), RoundedCornerShape(14.dp))
            .padding(horizontal = 12.dp, vertical = 9.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, color = RadarMuted, style = MaterialTheme.typography.bodySmall)
        Text(value, color = accent, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun PortfolioValueRow(label: String, value: String, valueColor: Color) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = RadarMuted)
        Text(value, fontWeight = FontWeight.Bold, color = valueColor)
    }
}

@Composable
private fun PurchaseHistoryDialog(
    item: InvestmentItem,
    current: PortfolioPosition,
    onDismiss: () -> Unit,
    onUpsertPurchase: (PortfolioPurchase) -> Boolean,
    onDeletePurchase: (String) -> Boolean,
    onUpsertSale: (PortfolioSale) -> Boolean,
    onDeleteSale: (String) -> Boolean
) {
    var entryType by remember(item.id) { mutableStateOf("BUY") }
    var editingId by remember(item.id) { mutableStateOf<String?>(null) }
    var dateText by remember(item.id) { mutableStateOf(todayPurchaseDate()) }
    var amountText by remember(item.id) { mutableStateOf("") }
    var sharesText by remember(item.id) { mutableStateOf("") }
    var errorText by remember(item.id) { mutableStateOf<String?>(null) }

    fun resetEditor(nextType: String = entryType) {
        entryType = nextType
        editingId = null
        dateText = todayPurchaseDate()
        amountText = ""
        sharesText = ""
        errorText = null
    }

    fun editPurchase(purchase: PortfolioPurchase) {
        entryType = "BUY"
        editingId = purchase.id
        dateText = purchase.date.ifBlank { todayPurchaseDate() }
        amountText = formatEditableNumber(purchase.investedAmount)
        sharesText = formatEditableNumber(purchase.shares)
        errorText = null
    }

    fun editSale(sale: PortfolioSale) {
        entryType = "SELL"
        editingId = sale.id
        dateText = sale.date.ifBlank { todayPurchaseDate() }
        amountText = formatEditableNumber(sale.proceeds)
        sharesText = formatEditableNumber(sale.shares)
        errorText = null
    }

    val amount = parseDecimal(amountText)
    val shares = parseDecimal(sharesText)
    val unitPrice = if (amount != null && shares != null && shares > 0.0) amount / shares else null
    val valid = isValidPurchaseDate(dateText) && amount != null && amount > 0.0 && shares != null && shares > 0.0
    val editingPurchase = entryType == "BUY" && editingId != null
    val editingSale = entryType == "SELL" && editingId != null

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Kaufhistorie & Verkäufe") },
        text = {
            Column(
                modifier = Modifier.heightIn(max = 590.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(item.name, fontWeight = FontWeight.Black)
                Text("${item.ticker} · ${item.isin}", color = RadarMuted, style = MaterialTheme.typography.bodySmall)

                NeonPanel(accent = RadarPurple) {
                    PortfolioBadgeRow(
                        listOf(
                            "Bestand" to formatShares(current.shares),
                            "Käufe" to current.purchases.size.toString(),
                            "Verkäufe" to current.sales.size.toString()
                        )
                    )
                    PortfolioValueRow("Verbleibend investiert", formatMoney(current.investedAmount), RadarText)
                    PortfolioValueRow("Verbleibende Stückzahl", formatShares(current.shares), RadarText)
                    PortfolioValueRow("Ø Einstand", current.averageBuyPrice()?.let(::formatMoney) ?: "–", RadarGreen)
                    PortfolioValueRow("Realisierter G/V", formatSignedMoney(current.realizedProfitLoss()), profitColor(current.realizedProfitLoss()))
                }

                Text("Transaktion hinzufügen", fontWeight = FontWeight.Black, style = MaterialTheme.typography.titleMedium)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = entryType == "BUY",
                        onClick = { resetEditor("BUY") },
                        label = { Text("Kauf") },
                        modifier = Modifier.weight(1f)
                    )
                    FilterChip(
                        selected = entryType == "SELL",
                        onClick = { resetEditor("SELL") },
                        label = { Text("Verkauf") },
                        modifier = Modifier.weight(1f)
                    )
                }

                Text(
                    when {
                        entryType == "BUY" && editingPurchase -> "Kauf bearbeiten"
                        entryType == "BUY" -> "Nachkauf hinzufügen"
                        entryType == "SELL" && editingSale -> "Verkauf bearbeiten"
                        else -> "Verkauf hinzufügen"
                    },
                    fontWeight = FontWeight.Black,
                    style = MaterialTheme.typography.titleMedium
                )
                OutlinedTextField(
                    value = dateText,
                    onValueChange = { dateText = it.filter { ch -> ch.isDigit() || ch == '.' }.take(10); errorText = null },
                    label = { Text(if (entryType == "BUY") "Kaufdatum (TT.MM.JJJJ)" else "Verkaufsdatum (TT.MM.JJJJ)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = amountText,
                    onValueChange = { amountText = sanitizeDecimalInput(it); errorText = null },
                    label = { Text(if (entryType == "BUY") "Investierter Betrag in €" else "Verkaufserlös in €") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = sharesText,
                    onValueChange = { sharesText = sanitizeDecimalInput(it); errorText = null },
                    label = { Text(if (entryType == "BUY") "Stückzahl / Anteile" else "Verkaufte Stückzahl / Anteile") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    if (entryType == "BUY") {
                        "Kaufkurs: ${unitPrice?.let(::formatMoney) ?: "–"}"
                    } else {
                        "Verkaufspreis: ${unitPrice?.let(::formatMoney) ?: "–"}"
                    },
                    color = if (unitPrice != null) if (entryType == "BUY") RadarGreen else RadarYellow else RadarMuted,
                    fontWeight = FontWeight.Bold
                )

                errorText?.let { Text(it, color = RadarRed, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold) }

                Button(
                    onClick = {
                        if (entryType == "BUY") {
                            val purchase = PortfolioPurchase(
                                id = editingId ?: UUID.randomUUID().toString(),
                                date = dateText,
                                investedAmount = amount!!,
                                shares = shares!!
                            )
                            if (onUpsertPurchase(purchase)) {
                                resetEditor("BUY")
                            } else {
                                errorText = "Änderung nicht möglich – dadurch wäre ein bereits erfasster Verkauf nicht mehr durch den Bestand gedeckt."
                            }
                        } else {
                            val sale = PortfolioSale(
                                id = editingId ?: UUID.randomUUID().toString(),
                                date = dateText,
                                proceeds = amount!!,
                                shares = shares!!
                            )
                            if (onUpsertSale(sale)) {
                                resetEditor("SELL")
                            } else {
                                errorText = "Verkauf nicht möglich – die Stückzahl zum gewählten Datum überschreitet deinen vorhandenen Bestand."
                            }
                        }
                    },
                    enabled = valid,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        when {
                            entryType == "BUY" && editingPurchase -> "Kauf aktualisieren"
                            entryType == "BUY" -> "Nachkauf speichern"
                            entryType == "SELL" && editingSale -> "Verkauf aktualisieren"
                            else -> "Verkauf speichern"
                        }
                    )
                }
                if (editingId != null) {
                    TextButton(onClick = { resetEditor(entryType) }, modifier = Modifier.fillMaxWidth()) { Text("Bearbeitung abbrechen") }
                }

                HorizontalDivider(color = RadarSurface2)
                Text("Bisherige Käufe", fontWeight = FontWeight.Black, style = MaterialTheme.typography.titleMedium)
                if (current.purchases.isEmpty()) {
                    Text("Noch kein Kauf erfasst.", color = RadarMuted)
                } else {
                    current.purchases.asReversed().forEach { purchase ->
                        NeonPanel(accent = RadarGreen) {
                                Text(purchase.date.ifBlank { "Bestand übernommen" }, fontWeight = FontWeight.Bold)
                                Text(
                                    "${formatMoney(purchase.investedAmount)} · ${formatShares(purchase.shares)} Anteile · Kaufkurs ${purchase.buyPrice()?.let(::formatMoney) ?: "–"}",
                                    color = RadarMuted,
                                    style = MaterialTheme.typography.bodySmall
                                )
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    TextButton(onClick = { editPurchase(purchase) }, modifier = Modifier.weight(1f)) { Text("Ändern") }
                                    TextButton(
                                        onClick = {
                                            if (onDeletePurchase(purchase.id)) {
                                                if (editingId == purchase.id && entryType == "BUY") resetEditor("BUY")
                                            } else {
                                                errorText = "Änderung nicht möglich – dieser Kauf wird für einen späteren Verkauf benötigt."
                                            }
                                        },
                                        modifier = Modifier.weight(1f)
                                    ) { Text("Löschen", color = RadarRed) }
                                }
                            }
                        }
                    }

                HorizontalDivider(color = RadarSurface2)
                Text("Bisherige Verkäufe", fontWeight = FontWeight.Black, style = MaterialTheme.typography.titleMedium)
                if (current.sales.isEmpty()) {
                    Text("Noch kein Verkauf erfasst.", color = RadarMuted)
                } else {
                    current.sales.asReversed().forEach { sale ->
                        NeonPanel(accent = RadarYellow) {
                                Text(sale.date.ifBlank { "Verkauf" }, fontWeight = FontWeight.Bold, color = RadarYellow)
                                Text(
                                    "${formatMoney(sale.proceeds)} Erlös · ${formatShares(sale.shares)} Anteile · Verkaufspreis ${sale.salePrice()?.let(::formatMoney) ?: "–"}",
                                    color = RadarMuted,
                                    style = MaterialTheme.typography.bodySmall
                                )
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    TextButton(onClick = { editSale(sale) }, modifier = Modifier.weight(1f)) { Text("Ändern") }
                                    TextButton(
                                        onClick = {
                                            onDeleteSale(sale.id)
                                            if (editingId == sale.id && entryType == "SELL") resetEditor("SELL")
                                        },
                                        modifier = Modifier.weight(1f)
                                    ) { Text("Löschen", color = RadarRed) }
                                }
                            }
                        }
                    }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Fertig") } },
        dismissButton = {}
    )
}

@Composable
private fun RecommendationRow(item: InvestmentItem, personal: PersonalRecommendation?, onOpen: () -> Unit) {
    val label = RecommendationPresentation.label(item)
    val amount = personal?.allocationEur ?: 0
    NeonPanel(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onOpen),
        accent = recommendationColor(label)
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(item.name, fontWeight = FontWeight.Black)
                Text("${item.ticker} · Score ${RecommendationPresentation.scoreText(item.scoreTotal)} · Risiko ${item.risk}/5", color = RadarMuted, style = MaterialTheme.typography.bodySmall)
                Text(
                    if (amount > 0) "Diesen Monat $amount €" else personal?.explanation ?: RecommendationPresentation.label(item),
                    color = recommendationColor(label),
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.labelLarge
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(if (amount > 0) "$amount €" else "0 €", fontWeight = FontWeight.Black, style = MaterialTheme.typography.titleMedium, color = if (amount > 0) RadarGreen else RadarMuted)
                Icon(Icons.Default.OpenInNew, null, tint = RadarCyan, modifier = Modifier.size(18.dp))
            }
        }
    }
}

@Composable
private fun DarkMetricCard(label: String, value: String, accent: Color, modifier: Modifier = Modifier, onClick: (() -> Unit)? = null) {
    val clickModifier = if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier
    NeonPanel(modifier.then(clickModifier), accent) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = RadarMuted)
        Text(value, fontWeight = FontWeight.Black, color = accent, style = MaterialTheme.typography.titleLarge)
        Text("Live Übersicht", style = MaterialTheme.typography.labelSmall, color = accent.copy(alpha = 0.78f))
    }
}

@Composable
private fun ScoreRing(score: Int) {
    Surface(
        color = Color(0x141FFFFFF),
        shape = RoundedCornerShape(18.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, RadarGreen.copy(alpha = 0.45f))
    ) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(score.toString(), fontWeight = FontWeight.Black, style = MaterialTheme.typography.titleLarge, color = RadarGreen)
            Text("/100", color = RadarMuted, style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
private fun StatusPill(status: String) {
    Surface(
        color = recommendationColor(status).copy(alpha = 0.14f),
        shape = RoundedCornerShape(50),
        border = androidx.compose.foundation.BorderStroke(1.dp, recommendationColor(status).copy(alpha = 0.40f))
    ) {
        Text(status, Modifier.padding(horizontal = 10.dp, vertical = 5.dp), color = recommendationColor(status), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Black)
    }
}

@Composable
private fun NeonPanel(
    modifier: Modifier = Modifier,
    accent: Color = RadarBlue,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        shape = RoundedCornerShape(22.dp)
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .background(Brush.linearGradient(listOf(accent.copy(alpha = 0.10f), RadarSurface.copy(alpha = 0.99f), RadarSurface2.copy(alpha = 0.96f))))
                .border(1.dp, accent.copy(alpha = 0.34f), RoundedCornerShape(22.dp))
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            content = {
                Box(
                    Modifier
                        .fillMaxWidth(0.32f)
                        .height(3.dp)
                        .background(brush = Brush.horizontalGradient(listOf(accent.copy(alpha = 0.18f), accent, RadarPurple)), shape = RoundedCornerShape(50))
                )
                content()
            }
        )
    }
}

@Composable
private fun PortfolioBadgeRow(entries: List<Pair<String, String>>) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            entries.take(2).forEach { (label, value) ->
                MetricBadge(label, value, Modifier.weight(1f))
            }
        }
        if (entries.size > 2) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                entries.drop(2).take(2).forEach { (label, value) ->
                    MetricBadge(label, value, Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun MetricBadge(label: String, value: String, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        color = Color(0x12FFFFFF),
        shape = RoundedCornerShape(16.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, RadarGlow)
    ) {
        Column(Modifier.padding(horizontal = 10.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(label.uppercase(), style = MaterialTheme.typography.labelSmall, color = RadarMuted)
            Text(value, fontWeight = FontWeight.Bold, color = RadarText)
        }
    }
}

@Composable
private fun NeonStatStrip(entries: List<Pair<String, String>>, accent: Color) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(accent.copy(alpha = 0.06f), RoundedCornerShape(18.dp))
            .border(1.dp, accent.copy(alpha = 0.24f), RoundedCornerShape(18.dp))
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        entries.chunked(2).forEach { rowEntries ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                rowEntries.forEach { (label, value) ->
                    MetricBadge(label, value, Modifier.weight(1f))
                }
                if (rowEntries.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun BudgetDialog(current: Int, onDismiss: () -> Unit, onSave: (Int) -> Unit) {
    var value by remember(current) { mutableStateOf(current.toString()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Monatsbudget ändern") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Der Kaufplan wird sofort auf dein neues Budget neu verteilt.", color = RadarMuted)
                OutlinedTextField(
                    value = value,
                    onValueChange = { value = it.filter(Char::isDigit).take(5) },
                    label = { Text("Budget in €") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(50, 100, 200, 500).forEach { preset ->
                        AssistChip(onClick = { value = preset.toString() }, label = { Text("$preset €") })
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = { value.toIntOrNull()?.let { onSave(it) } }, enabled = (value.toIntOrNull() ?: 0) >= 10) { Text("Übernehmen") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Abbrechen") } }
    )
}

@Composable
private fun ErrorView(message: String, retry: () -> Unit) {
    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        NeonPanel(accent = RadarRed, modifier = Modifier.fillMaxWidth()) {
            Text("Keine Live-Verbindung", fontWeight = FontWeight.Black, style = MaterialTheme.typography.titleLarge)
            Text(message, color = RadarMuted)
            Button(onClick = retry, modifier = Modifier.fillMaxWidth()) { Text("Erneut versuchen") }
        }
    }
}

private fun openSavedTradeRepublicUrl(context: android.content.Context, url: String) {
    val normalized = url.trim()
    if (!normalized.startsWith("https://") && !normalized.startsWith("http://")) return
    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(normalized))) }
}

private fun openMarketQuote(context: android.content.Context, item: InvestmentItem) {
    val ticker = Uri.encode(item.ticker.trim())
    val url = "https://finance.yahoo.com/quote/$ticker"
    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
}

private fun priceLine(item: InvestmentItem): String {
    val price = item.price?.let { String.format(Locale.GERMANY, "%.2f", it) } ?: "–"
    val change = item.percentChange?.let { String.format(Locale.GERMANY, "%+.2f%%", it) } ?: "–"
    val eur = item.priceEur
        ?.takeIf { item.price != null && !item.currency.equals("EUR", ignoreCase = true) }
        ?.let { " · ≈ ${formatMoney(it)}" }
        .orEmpty()
    val quality = if (item.price == null) "" else if (item.dataDelayed) " · verzögert" else " · Live"
    return "Kurs $price ${item.currency}$eur · Heute $change$quality"
}

private fun euroComparablePrice(item: InvestmentItem): Double? =
    item.priceEur ?: item.price?.takeIf { item.currency.isBlank() || item.currency.equals("EUR", ignoreCase = true) }

private fun todayPurchaseDate(): String =
    SimpleDateFormat("dd.MM.yyyy", Locale.GERMANY).format(Date())

private fun isValidPurchaseDate(value: String): Boolean {
    if (!value.matches(Regex("\\d{2}\\.\\d{2}\\.\\d{4}"))) return false
    val format = SimpleDateFormat("dd.MM.yyyy", Locale.GERMANY).apply { isLenient = false }
    val parsed = runCatching { format.parse(value) }.getOrNull() ?: return false
    return format.format(parsed) == value
}

private fun parseDecimal(value: String): Double? = value.trim().replace(',', '.').toDoubleOrNull()

private fun sanitizeDecimalInput(value: String): String {
    val normalized = value.filter { it.isDigit() || it == ',' || it == '.' }.replace('.', ',')
    val firstComma = normalized.indexOf(',')
    return if (firstComma < 0) normalized.take(12) else {
        normalized.substring(0, firstComma + 1) + normalized.substring(firstComma + 1).replace(",", "").take(6)
    }.take(16)
}

private fun formatEditableNumber(value: Double): String =
    if (value % 1.0 == 0.0) value.toLong().toString() else String.format(Locale.GERMANY, "%.6f", value).trimEnd('0').trimEnd(',')

private fun formatMoney(value: Double): String = String.format(Locale.GERMANY, "%.2f €", value)

private fun formatSignedMoney(value: Double): String = String.format(Locale.GERMANY, "%+.2f €", value)

private fun formatShares(value: Double): String = String.format(Locale.GERMANY, "%.6f", value).trimEnd('0').trimEnd(',')

private fun formatSignedPercent(value: Double): String = String.format(Locale.GERMANY, "%+.2f%%", value)

private fun profitColor(value: Double?): Color = when {
    value == null -> RadarMuted
    value > 0.0 -> RadarGreen
    value < 0.0 -> RadarRed
    else -> RadarMuted
}

private fun recommendationColor(label: String) = when (label.uppercase()) {
    "KAUFEN" -> RadarGreen
    "BEOBACHTEN" -> RadarYellow
    "VERKAUF PRÜFEN", "VERKAUFEN" -> RadarRed
    "NICHT KAUFEN" -> Color(0xFFFF8B6A)
    else -> RadarBlue
}

private fun marketAccent(light: String) = when (light.uppercase()) {
    "GRÜN", "GRUEN", "GREEN" -> RadarGreen
    "ROT", "RED" -> RadarRed
    else -> RadarYellow
}

private fun alertDarkColor(level: String) = when (level.uppercase()) {
    "SELL" -> Color(0xFF351824)
    "REVIEW" -> Color(0xFF392B18)
    "BUY" -> Color(0xFF123126)
    else -> RadarSurface2
}