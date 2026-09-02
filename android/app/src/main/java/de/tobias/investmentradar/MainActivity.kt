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
        val openItemId = intent.getStringExtra("openItemId")?.takeIf { it.isNotBlank() }
        val openAlerts = intent.getBooleanExtra("openAlerts", false)
        setContent {
            InvestmentRadarUi(
                initialTab = if (openAlerts || openItemId != null) 3 else 0,
                initialDetailId = openItemId
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InvestmentRadarUi(
    vm: MainViewModel = viewModel(),
    initialTab: Int = 0,
    initialDetailId: String? = null
) {
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
    var selectedDetailId by remember { mutableStateOf(initialDetailId?.takeIf { it.isNotBlank() }) }
    var detailReturnTab by remember { mutableIntStateOf(if (initialDetailId.isNullOrBlank()) initialTab.coerceIn(0, 3) else 3) }
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
                        val personalPlan = RecommendationEngine.plan(
                            s.data.items,
                            budget,
                            PortfolioAnalysis.values(s.data.items, positions, customItems)
                        )
                        val personalById = personalPlan.items.associateBy { it.itemId }
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
                                personalPlan = personalPlan,
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
            text = { Text("Version ${update.versionName} ist verfügbar.") },
            confirmButton = {
                Button(onClick = { AppUpdateManager.startUpdate(context, update) }) { Text("Installieren") }
            },
            dismissButton = { TextButton(onClick = { availableUpdate = null }) { Text("Später") } }
        )
    }

    updateStatusMessage?.let { message ->
        AlertDialog(
            onDismissRequest = { updateStatusMessage = null },
            title = { Text("Update-Prüfung") },
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
    personalPlan: RecommendationPlan,
    onEditBudget: () -> Unit,
    onOpenRadar: () -> Unit
) {
    val top = personalPlan.items.firstOrNull { it.signal == PersonalSignal.BUY }
    val marketOpen = data.items.count { it.marketOpen == true }
    val delayed = data.items.count { it.dataDelayed == true }
    val marketText = when {
        marketOpen > 0 -> "OFFEN"
        delayed > 0 -> "VERZÖGERT"
        else -> "GESCHLOSSEN"
    }
    val marketSubtitle = when {
        marketOpen > 0 -> "$marketOpen Werte mit offenem Markt"
        delayed > 0 -> "$delayed verzögerte Kursquellen"
        else -> "Aktuell keine offenen Märkte"
    }
    val coverageText = data.meta?.coveragePct?.let { "$it % Datenabdeckung" } ?: "Datenabdeckung wird geprüft"

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            DarkHeroCard(
                eyebrow = "LIVE DASHBOARD",
                title = "Analyse V2 für deinen nächsten Monatskauf",
                subtitle = coverageText
            )
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                DarkMetricCard("MARKT", marketText, RadarYellow, marketSubtitle, Modifier.weight(1f))
                DarkMetricCard("BUDGET", "$budget €", RadarBlue, "monatlich verfügbar", Modifier.weight(1f))
                DarkMetricCard(
                    "SIGNAL",
                    if (top != null) "AKTIV" else "WARTEN",
                    RadarGreen,
                    if (top != null) top.itemName else "Kein klarer Kauf aktuell",
                    Modifier.weight(1f),
                    valueStyle = if (top == null) MaterialTheme.typography.titleMedium else MaterialTheme.typography.titleLarge
                )
            }
        }
        item {
            DarkSectionTitle("Jetzt relevant", "Was der Radar aktuell priorisiert")
        }
        if (personalPlan.items.isEmpty()) {
            item {
                DarkInfoCard("Keine Empfehlung verfügbar", "Aktuell reichen die Daten für keine belastbare Monatsentscheidung.", RadarYellow)
            }
        } else {
            items(personalPlan.items.take(3), key = { it.itemId }) { item ->
                val accent = when (item.signal) {
                    PersonalSignal.BUY -> RadarGreen
                    PersonalSignal.WATCH -> RadarYellow
                    PersonalSignal.HOLD -> RadarBlue
                    PersonalSignal.REVIEW -> RadarPurple
                    PersonalSignal.SELL -> RadarRed
                }
                DarkInfoCard(item.itemName, item.explanation, accent)
            }
        }
        item {
            Button(onClick = onEditBudget, modifier = Modifier.fillMaxWidth()) { Text("Budget anpassen") }
            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = onOpenRadar, modifier = Modifier.fillMaxWidth()) { Text("Zum Radar") }
        }
    }
}

@Composable
private fun DarkHeroCard(eyebrow: String, title: String, subtitle: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = RadarSurface)
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Text(eyebrow, color = RadarGreen, fontWeight = FontWeight.Black, style = MaterialTheme.typography.labelLarge)
            Text(title, fontWeight = FontWeight.Black, style = MaterialTheme.typography.headlineSmall)
            Text(subtitle, color = RadarMuted)
        }
    }
}

@Composable
private fun DarkMetricCard(
    label: String,
    value: String,
    accent: Color,
    subtitle: String,
    modifier: Modifier = Modifier,
    valueStyle: androidx.compose.ui.text.TextStyle = MaterialTheme.typography.titleLarge
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = RadarSurface2)
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(label, color = RadarMuted, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
            Text(value, color = accent, fontWeight = FontWeight.Black, style = valueStyle)
            Text(subtitle, color = RadarMuted, style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
private fun DarkSectionTitle(title: String, subtitle: String) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(title, fontWeight = FontWeight.Black, style = MaterialTheme.typography.titleLarge)
        Text(subtitle, color = RadarMuted, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun DarkInfoCard(title: String, text: String, accent: Color) {
    Card(
        modifier = Modifier.fillMaxWidth().border(1.dp, accent.copy(alpha = 0.35f), RoundedCornerShape(18.dp)),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = RadarSurface)
    ) {
        Column(Modifier.padding(15.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text(title, fontWeight = FontWeight.Black)
            Text(text, color = RadarMuted)
        }
    }
}

@Composable
private fun ErrorView(message: String, onRetry: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Daten konnten nicht geladen werden", fontWeight = FontWeight.Black)
        Spacer(Modifier.height(8.dp))
        Text(message, color = RadarMuted)
        Spacer(Modifier.height(16.dp))
        Button(onClick = onRetry) { Text("Erneut versuchen") }
    }
}

@Composable
private fun BudgetDialog(current: Int, onDismiss: () -> Unit, onSave: (Int) -> Unit) {
    var value by remember { mutableStateOf(current.toString()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Monatsbudget") },
        text = {
            OutlinedTextField(
                value = value,
                onValueChange = { value = it.filter(Char::isDigit).take(5) },
                label = { Text("Budget in €") },
                singleLine = true
            )
        },
        confirmButton = {
            Button(onClick = { value.toIntOrNull()?.let(onSave) }) { Text("Speichern") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Abbrechen") } }
    )
}

@Composable
private fun PurchaseHistoryDialog(
    item: InvestmentItem,
    current: PortfolioPosition,
    onDismiss: () -> Unit,
    onUpsertPurchase: (PortfolioPurchase) -> Unit,
    onDeletePurchase: (String) -> Unit,
    onUpsertSale: (PortfolioSale) -> Unit,
    onDeleteSale: (String) -> Unit
) {
    var showPurchaseForm by remember { mutableStateOf(false) }
    var showSaleForm by remember { mutableStateOf(false) }
    var editingPurchase by remember { mutableStateOf<PortfolioPurchase?>(null) }
    var editingSale by remember { mutableStateOf<PortfolioSale?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("${item.name} · Portfolio") },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth().heightIn(max = 560.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text("Käufe", fontWeight = FontWeight.Black)
                current.purchases.forEach { purchase ->
                    Card(colors = CardDefaults.cardColors(containerColor = RadarSurface2)) {
                        Column(Modifier.padding(10.dp)) {
                            Text("${purchase.date} · ${formatMoney(purchase.amountEur)} €")
                            Text("${formatNumber(purchase.quantity)} Stück · ${formatMoney(purchase.priceEur)} €", color = RadarMuted)
                            Row {
                                TextButton(onClick = { editingPurchase = purchase; showPurchaseForm = true }) { Text("Bearbeiten") }
                                TextButton(onClick = { onDeletePurchase(purchase.id) }) { Text("Löschen") }
                            }
                        }
                    }
                }
                Button(onClick = { editingPurchase = null; showPurchaseForm = true }, modifier = Modifier.fillMaxWidth()) { Text("Kauf hinzufügen") }
                HorizontalDivider()
                Text("Verkäufe", fontWeight = FontWeight.Black)
                current.sales.forEach { sale ->
                    Card(colors = CardDefaults.cardColors(containerColor = RadarSurface2)) {
                        Column(Modifier.padding(10.dp)) {
                            Text("${sale.date} · ${formatMoney(sale.amountEur)} €")
                            Text("${formatNumber(sale.quantity)} Stück · ${formatMoney(sale.priceEur)} €", color = RadarMuted)
                            Row {
                                TextButton(onClick = { editingSale = sale; showSaleForm = true }) { Text("Bearbeiten") }
                                TextButton(onClick = { onDeleteSale(sale.id) }) { Text("Löschen") }
                            }
                        }
                    }
                }
                Button(onClick = { editingSale = null; showSaleForm = true }, modifier = Modifier.fillMaxWidth()) { Text("Verkauf hinzufügen") }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Fertig") } }
    )

    if (showPurchaseForm) {
        val existing = editingPurchase
        TransactionDialog(
            title = if (existing == null) "Kauf hinzufügen" else "Kauf bearbeiten",
            initialDate = existing?.date.orEmpty(),
            initialAmount = existing?.amountEur,
            initialQuantity = existing?.quantity,
            initialPrice = existing?.priceEur,
            onDismiss = { showPurchaseForm = false },
            onSave = { date, amount, quantity, price ->
                onUpsertPurchase(
                    PortfolioPurchase(
                        id = existing?.id ?: UUID.randomUUID().toString(),
                        date = date,
                        amountEur = amount,
                        quantity = quantity,
                        priceEur = price
                    )
                )
                showPurchaseForm = false
            }
        )
    }

    if (showSaleForm) {
        val existing = editingSale
        TransactionDialog(
            title = if (existing == null) "Verkauf hinzufügen" else "Verkauf bearbeiten",
            initialDate = existing?.date.orEmpty(),
            initialAmount = existing?.amountEur,
            initialQuantity = existing?.quantity,
            initialPrice = existing?.priceEur,
            onDismiss = { showSaleForm = false },
            onSave = { date, amount, quantity, price ->
                onUpsertSale(
                    PortfolioSale(
                        id = existing?.id ?: UUID.randomUUID().toString(),
                        date = date,
                        amountEur = amount,
                        quantity = quantity,
                        priceEur = price
                    )
                )
                showSaleForm = false
            }
        )
    }
}

@Composable
private fun TransactionDialog(
    title: String,
    initialDate: String,
    initialAmount: Double?,
    initialQuantity: Double?,
    initialPrice: Double?,
    onDismiss: () -> Unit,
    onSave: (String, Double, Double, Double) -> Unit
) {
    var date by remember { mutableStateOf(initialDate.ifBlank { SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date()) }) }
    var amount by remember { mutableStateOf(initialAmount?.toString().orEmpty()) }
    var quantity by remember { mutableStateOf(initialQuantity?.toString().orEmpty()) }
    var price by remember { mutableStateOf(initialPrice?.toString().orEmpty()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = date, onValueChange = { date = it }, label = { Text("Datum (JJJJ-MM-TT)") }, singleLine = true)
                OutlinedTextField(value = amount, onValueChange = { amount = normalizeDecimalInput(it) }, label = { Text("Betrag €") }, singleLine = true)
                OutlinedTextField(value = quantity, onValueChange = { quantity = normalizeDecimalInput(it) }, label = { Text("Stück") }, singleLine = true)
                OutlinedTextField(value = price, onValueChange = { price = normalizeDecimalInput(it) }, label = { Text("Preis je Stück €") }, singleLine = true)
            }
        },
        confirmButton = {
            Button(onClick = {
                val amountValue = amount.toDoubleOrNull()
                val quantityValue = quantity.toDoubleOrNull()
                val priceValue = price.toDoubleOrNull()
                if (date.isNotBlank() && amountValue != null && quantityValue != null && priceValue != null && amountValue > 0 && quantityValue > 0 && priceValue > 0) {
                    onSave(date, amountValue, quantityValue, priceValue)
                }
            }) { Text("Speichern") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Abbrechen") } }
    )
}

@Composable
private fun CustomInvestmentDialog(
    existing: CustomInvestment?,
    onDismiss: () -> Unit,
    onSave: (CustomInvestment, PortfolioPurchase?) -> Unit
) {
    var name by remember { mutableStateOf(existing?.name.orEmpty()) }
    var isin by remember { mutableStateOf(existing?.isin.orEmpty()) }
    var ticker by remember { mutableStateOf(existing?.ticker.orEmpty()) }
    var quantity by remember { mutableStateOf("") }
    var price by remember { mutableStateOf(existing?.manualPriceEur?.toString().orEmpty()) }
    var date by remember { mutableStateOf(SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (existing == null) "Eigenen Wert hinzufügen" else "Eigenen Wert bearbeiten") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Name") }, singleLine = true)
                OutlinedTextField(value = isin, onValueChange = { isin = it.uppercase().take(12) }, label = { Text("ISIN") }, singleLine = true)
                OutlinedTextField(value = ticker, onValueChange = { ticker = it.uppercase() }, label = { Text("Ticker") }, singleLine = true)
                if (existing == null) OutlinedTextField(value = quantity, onValueChange = { quantity = normalizeDecimalInput(it) }, label = { Text("Stück") }, singleLine = true)
                OutlinedTextField(value = price, onValueChange = { price = normalizeDecimalInput(it) }, label = { Text("Preis €") }, singleLine = true)
                if (existing == null) OutlinedTextField(value = date, onValueChange = { date = it }, label = { Text("Kaufdatum") }, singleLine = true)
            }
        },
        confirmButton = {
            Button(onClick = {
                if (name.isBlank()) return@Button
                val id = existing?.id ?: "custom-${UUID.randomUUID()}"
                val priceValue = price.toDoubleOrNull()
                val item = CustomInvestment(id = id, name = name.trim(), isin = isin.trim(), ticker = ticker.trim(), manualPriceEur = priceValue)
                val purchase = if (existing == null) {
                    val qty = quantity.toDoubleOrNull()
                    if (qty != null && qty > 0 && priceValue != null && priceValue > 0) {
                        PortfolioPurchase(
                            id = UUID.randomUUID().toString(),
                            date = date,
                            amountEur = qty * priceValue,
                            quantity = qty,
                            priceEur = priceValue
                        )
                    } else null
                } else null
                onSave(item, purchase)
            }) { Text("Speichern") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Abbrechen") } }
    )
}

private fun normalizeDecimalInput(value: String): String = value.replace(',', '.').filter { it.isDigit() || it == '.' }.let { normalized ->
    val firstDot = normalized.indexOf('.')
    if (firstDot < 0) normalized else normalized.substring(0, firstDot + 1) + normalized.substring(firstDot + 1).replace(".", "")
}

private fun formatMoney(value: Double): String = String.format(Locale.GERMANY, "%.2f", value)
private fun formatNumber(value: Double): String = String.format(Locale.GERMANY, "%.4f", value).trimEnd('0').trimEnd(',')
