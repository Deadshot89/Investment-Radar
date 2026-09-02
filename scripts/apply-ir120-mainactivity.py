from pathlib import Path
import re

path = Path("android/app/src/main/java/de/tobias/investmentradar/MainActivity.kt")
text = path.read_text(encoding="utf-8")


def sub_once(pattern: str, replacement: str, label: str) -> None:
    global text
    text, count = re.subn(pattern, replacement, text, count=1, flags=re.S)
    if count != 1:
        raise SystemExit(f"{label}: expected exactly one replacement, got {count}")


new_ui = r'''@OptIn(ExperimentalMaterial3Api::class)
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
    var radarFocusId by remember { mutableStateOf<String?>(null) }
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
                    NavigationBarItem(selected = tab == 0, onClick = { tab = 0 }, icon = { Icon(Icons.Default.ShowChart, null) }, label = { Text("Live") })
                    NavigationBarItem(selected = tab == 1, onClick = { radarFocusId = null; tab = 1 }, icon = { Icon(Icons.Default.Search, null) }, label = { Text("Radar") })
                    NavigationBarItem(selected = tab == 2, onClick = { tab = 2 }, icon = { Icon(Icons.Default.Favorite, null) }, label = { Text("Portfolio") })
                    NavigationBarItem(selected = tab == 3, onClick = { tab = 3 }, icon = { Icon(Icons.Default.Notifications, null) }, label = { Text("Alarme") })
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
                    is UiState.Ready -> when (tab) {
                        0 -> DashboardScreen(
                            data = s.data,
                            budget = budget,
                            holdingIds = holdingIds,
                            positions = positions,
                            watchlistIds = watchlistIds,
                            onEditBudget = { budgetDialog = true },
                            onOpenRadar = { radarFocusId = null; tab = 1 }
                        )
                        1 -> RadarScreen(
                            items = s.data.items,
                            holdingIds = holdingIds,
                            watchlistIds = watchlistIds,
                            focusItemId = radarFocusId,
                            onClearFocus = { radarFocusId = null },
                            onToggleWatchlist = vm::toggleWatchlist,
                            onBought = { investmentDialogItem = it },
                            onEditInvestment = { investmentDialogItem = it }
                        )
                        2 -> PortfolioScreen(
                            items = s.data.items.filter { it.id in holdingIds },
                            positions = positions,
                            customItems = customItems,
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
                                if (s.data.items.any { it.id == stored.alert.itemId }) {
                                    radarFocusId = stored.alert.itemId
                                    tab = 1
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
}'''

sub_once(
    r'@OptIn\(ExperimentalMaterial3Api::class\)\n@Composable\nfun InvestmentRadarUi\(.*?\n\}\n\n@Composable\nprivate fun DashboardScreen\(',
    new_ui + '\n\n@Composable\nprivate fun DashboardScreen(',
    'InvestmentRadarUi'
)

new_dashboard = r'''@Composable
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
}'''

sub_once(
    r'@Composable\nprivate fun DashboardScreen\(.*?\n\}\n\nprivate enum class RadarSortOption',
    new_dashboard + '\n\nprivate enum class RadarSortOption',
    'DashboardScreen'
)

new_radar = r'''@Composable
private fun RadarScreen(
    items: List<InvestmentItem>,
    holdingIds: Set<String>,
    watchlistIds: Set<String>,
    focusItemId: String?,
    onClearFocus: () -> Unit,
    onToggleWatchlist: (String) -> Unit,
    onBought: (InvestmentItem) -> Unit,
    onEditInvestment: (InvestmentItem) -> Unit
) {
    val context = LocalContext.current
    var query by remember { mutableStateOf("") }
    var filter by remember { mutableStateOf("ALLE") }
    var sortOption by remember { mutableStateOf(RadarSortOption.SCORE) }

    val filteredItems = items
        .filter { item ->
            val recommendation = RecommendationPresentation.effectiveRecommendation(item)
            val matchesFocus = focusItemId == null || item.id == focusItemId
            val matchesQuery = query.isBlank() ||
                item.name.contains(query, ignoreCase = true) ||
                item.ticker.contains(query, ignoreCase = true) ||
                item.isin.contains(query, ignoreCase = true)
            val matchesFilter = when (filter) {
                "KAUFEN" -> recommendation == "BUY"
                "PORTFOLIO" -> item.id in holdingIds
                "ETF" -> item.type.equals("ETF", ignoreCase = true)
                "EIGENE" -> item.status.equals("EIGEN", ignoreCase = true)
                "WATCHLIST" -> item.id in watchlistIds
                "PRÜFEN" -> recommendation == "REVIEW"
                else -> true
            }
            matchesFocus && matchesQuery && matchesFilter
        }
        .let { filtered ->
            when (sortOption) {
                RadarSortOption.SCORE -> filtered.sortedByDescending { it.scoreTotal ?: Int.MIN_VALUE }
                RadarSortOption.DAY -> filtered.sortedByDescending { it.percentChange ?: Double.NEGATIVE_INFINITY }
                RadarSortOption.RISK -> filtered.sortedByDescending { it.risk }
                RadarSortOption.NAME -> filtered.sortedBy { it.name.lowercase(Locale.GERMANY) }
            }
        }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 14.dp),
        contentPadding = PaddingValues(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            NeonPanel(accent = RadarPurple) {
                Text("RADAR", style = MaterialTheme.typography.labelLarge, color = RadarPurple, fontWeight = FontWeight.Bold)
                Text("40 Werte · Analyse V2", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
                if (focusItemId != null) {
                    FilledTonalButton(onClick = onClearFocus, modifier = Modifier.fillMaxWidth()) { Text("Alarm-Fokus aufheben") }
                }
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    label = { Text("Suchen nach Name, Ticker oder ISIN") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(selected = filter == "ALLE", onClick = { filter = "ALLE" }, label = { Text("Alle") }, modifier = Modifier.weight(1f))
                        FilterChip(selected = filter == "KAUFEN", onClick = { filter = "KAUFEN" }, label = { Text("Kaufen") }, modifier = Modifier.weight(1f))
                        FilterChip(selected = filter == "PORTFOLIO", onClick = { filter = "PORTFOLIO" }, label = { Text("Portfolio") }, modifier = Modifier.weight(1f))
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(selected = filter == "ETF", onClick = { filter = "ETF" }, label = { Text("ETF") }, modifier = Modifier.weight(1f))
                        FilterChip(selected = filter == "EIGENE", onClick = { filter = "EIGENE" }, label = { Text("Eigene Werte") }, modifier = Modifier.weight(1f))
                        FilterChip(selected = filter == "PRÜFEN", onClick = { filter = "PRÜFEN" }, label = { Text("Prüfen") }, modifier = Modifier.weight(1f))
                    }
                    FilterChip(selected = filter == "WATCHLIST", onClick = { filter = "WATCHLIST" }, label = { Text("Watchlist (${watchlistIds.size})") }, modifier = Modifier.fillMaxWidth())
                }
                Text("SORTIERUNG", style = MaterialTheme.typography.labelSmall, color = RadarMuted, fontWeight = FontWeight.Bold)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    RadarSortOption.entries.forEach { option ->
                        FilterChip(selected = sortOption == option, onClick = { sortOption = option }, label = { Text(option.label) }, modifier = Modifier.weight(1f))
                    }
                }
                Text("${filteredItems.size} Treffer", color = RadarMuted, style = MaterialTheme.typography.bodySmall)
            }
        }
        if (filteredItems.isEmpty()) {
            item { NeonPanel(accent = RadarYellow) { Text("Keine Treffer", fontWeight = FontWeight.Black); Text("Passe die Suche oder den Filter an.", color = RadarMuted) } }
        }
        items(filteredItems) { item ->
            val label = RecommendationPresentation.label(item)
            NeonPanel(accent = recommendationColor(label)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(item.name, fontWeight = FontWeight.Black, style = MaterialTheme.typography.titleMedium)
                        Text("${item.ticker} · ${item.isin}", color = RadarMuted, style = MaterialTheme.typography.bodySmall)
                    }
                    StatusPill(label)
                }
                Text(priceLine(item), color = RadarMuted)
                PortfolioBadgeRow(
                    listOf(
                        "Score" to RecommendationPresentation.scoreText(item.scoreTotal),
                        "Daten" to item.coverage?.let { "$it %" }.orEmpty().ifBlank { "–" },
                        "Risiko" to "${item.risk}/5",
                        "Typ" to item.type
                    )
                )
                RecommendationPresentation.topReasons(item).forEach { reason -> Text("• $reason", style = MaterialTheme.typography.bodySmall) }
                ScoreBreakdownCard(item)
                if (item.price != null && item.dataSource.isNotBlank()) {
                    Text("Kursquelle ${item.dataSource}${if (item.dataDelayed) " · verzögert" else " · Live"}", color = RadarMuted, style = MaterialTheme.typography.bodySmall)
                }
                OutlinedButton(onClick = { TradeRepublicNavigator.open(context, item) }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.OpenInNew, null)
                    Spacer(Modifier.width(7.dp))
                    Text("Trade Republic öffnen")
                }
                FilledTonalButton(onClick = { onToggleWatchlist(item.id) }, modifier = Modifier.fillMaxWidth()) {
                    Text(if (item.id in watchlistIds) "Von Watchlist entfernen" else "Zur Watchlist")
                }
                if (item.id in holdingIds) {
                    Text("✓ Im Portfolio · Prüf-/Verkaufsalarme aktiv", color = RadarGreen, fontWeight = FontWeight.Bold)
                    FilledTonalButton(onClick = { onEditInvestment(item) }, modifier = Modifier.fillMaxWidth()) { Text("Transaktionen verwalten") }
                } else {
                    Button(onClick = { onBought(item) }, modifier = Modifier.fillMaxWidth()) { Text("Als gekauft markieren") }
                }
            }
        }
    }
}'''

sub_once(
    r'@Composable\nprivate fun RadarScreen\(.*?\n\}\n\n@Composable\nprivate fun PortfolioScreen\(',
    new_radar + '\n\n@Composable\nprivate fun PortfolioScreen(',
    'RadarScreen'
)

new_recommendation_row = r'''@Composable
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
}'''

sub_once(
    r'@Composable\nprivate fun AlertsScreen\(alerts: List<SignalAlert>\) \{.*?\n\}\n\n@Composable\nprivate fun RecommendationRow\(.*?\n\}\n\n@Composable\nprivate fun DarkMetricCard',
    new_recommendation_row + '\n\n@Composable\nprivate fun DarkMetricCard',
    'legacy AlertsScreen + RecommendationRow'
)

sub_once(
    r'private fun InvestmentItem\.toPlannerItem\(\) = PlannerItem\(.*?\nprivate fun openSavedTradeRepublicUrl',
    'private fun openSavedTradeRepublicUrl',
    'legacy planner and Trade Republic helpers'
)

text = text.replace('setContent { InvestmentRadarUi() }', 'setContent { InvestmentRadarUi(initialTab = if (intent.getBooleanExtra("openAlerts", false)) 3 else 0) }')
text = text.replace('openInvestment(context, item)', 'TradeRepublicNavigator.open(context, item)')
text = text.replace('openInvestment(context, top)', 'TradeRepublicNavigator.open(context, top)')

for forbidden in ('InvestmentPlanner.', 'private fun AlertsScreen(', 'private fun openInvestment(', 'TRADE_REPUBLIC_STOCK_BASE_URL'):
    if forbidden in text:
        raise SystemExit(f"forbidden legacy code remains: {forbidden}")

required = (
    'RecommendationEngine.plan',
    'val cashAmount = personalPlan.cashAmount',
    'DIESEN MONAT WARTEN',
    'vm.alerts.collectAsState',
    'vm.alertPreferences.collectAsState',
    'onMarkAllRead = vm::markAllAlertsRead',
    'AppUpdateManager.checkResult',
    'Du nutzt bereits die aktuelle Version',
    'Update konnte nicht geprüft werden',
    'TradeRepublicNavigator.open(context, item)',
    'ScoreBreakdownCard'
)
for needle in required:
    if needle not in text:
        raise SystemExit(f"required wiring missing: {needle}")

path.write_text(text, encoding="utf-8")
