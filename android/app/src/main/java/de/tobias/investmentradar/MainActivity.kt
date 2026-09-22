package de.tobias.investmentradar

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
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
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.automirrored.filled.ShowChart
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
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
private const val NeonPanelGlowAlpha = 0.04f

class MainActivity : ComponentActivity() {
    private var pendingOpenItemId by mutableStateOf<String?>(null)
    private var pendingOpenAlertId by mutableStateOf<String?>(null)
    private var pendingOpenAlerts by mutableStateOf(false)
    private var pendingOpenSavingsPlans by mutableStateOf(false)
    private var pushNavigationRequest by mutableLongStateOf(0L)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        DailyAnalysisScheduler.schedule(this)
        ExitStrategyScheduler.schedule(this)
        if (FirebaseBootstrap.isConfigured()) {
            FirebaseMessaging.getInstance().subscribeToTopic("investment-alerts")
            PortfolioStore.read(this).forEach { itemId ->
                FirebaseMessaging.getInstance().subscribeToTopic(MainViewModel.holdingTopic(itemId))
            }
        }
        applyPushIntent(intent)
        setContent {
            val target = PushNavigationTarget.resolve(
                openSavingsPlans = pendingOpenSavingsPlans,
                openAlerts = pendingOpenAlerts,
                itemId = pendingOpenItemId
            )
            InvestmentRadarUi(
                initialTab = when (target) {
                    PushNavigationTarget.SAVINGS_PLANS -> 2
                    PushNavigationTarget.ALERTS, PushNavigationTarget.ALERT_DETAIL -> 3
                    PushNavigationTarget.HOME -> 0
                },
                initialDetailId = pendingOpenItemId?.takeIf { target == PushNavigationTarget.ALERT_DETAIL },
                initialAlertId = pendingOpenAlertId,
                initialOpenSavingsPlans = target == PushNavigationTarget.SAVINGS_PLANS,
                pushNavigationRequest = pushNavigationRequest
            )
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        applyPushIntent(intent)
    }

    private fun applyPushIntent(intent: Intent) {
        pendingOpenItemId = intent.getStringExtra("openItemId")?.takeIf { it.isNotBlank() }
        pendingOpenAlertId = intent.getStringExtra("openAlertId")?.takeIf { it.isNotBlank() }
        pendingOpenAlerts = intent.getBooleanExtra("openAlerts", false)
        pendingOpenSavingsPlans = intent.getBooleanExtra("openSavingsPlans", false)
        pushNavigationRequest++
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InvestmentRadarUi(
    vm: MainViewModel = viewModel(),
    initialTab: Int = 0,
    initialDetailId: String? = null,
    initialAlertId: String? = null,
    initialOpenSavingsPlans: Boolean = false,
    pushNavigationRequest: Long = 0L
) {
    val state by vm.state.collectAsState()
    val refreshNotice by vm.refreshNotice.collectAsState()
    val budgetState by vm.budgetState.collectAsState()
    val holdingIds by vm.holdingIds.collectAsState()
    val positions by vm.positions.collectAsState()
    val customItems by vm.customItems.collectAsState()
    val exitStrategies by vm.exitStrategies.collectAsState()
    val watchlistIds by vm.watchlistIds.collectAsState()
    val alerts by vm.alerts.collectAsState()
    val alertPreferences by vm.alertPreferences.collectAsState()
    val context = LocalContext.current
    var tab by remember { mutableIntStateOf(initialTab.coerceIn(0, 4)) }
    var selectedDetailId by remember { mutableStateOf(initialDetailId?.takeIf { it.isNotBlank() }) }
    var detailReturnTab by remember { mutableIntStateOf(if (initialDetailId.isNullOrBlank()) initialTab.coerceIn(0, 4) else 3) }
    var showSavingsPlans by remember { mutableStateOf(initialOpenSavingsPlans) }
    var missingAlertItemMessage by rememberSaveable { mutableStateOf<String?>(null) }
    var budgetDialog by remember { mutableStateOf(false) }
    var moneyActionCenter by remember {
        mutableStateOf(
            DepotActionCenterState(
                summary = DepotActionCenterSummary(urgentActions = 0, plannedBuyEur = 0.0, cashEur = 0.0),
                items = emptyList()
            )
        )
    }
    var moneyActionItemsById by remember { mutableStateOf<Map<String, InvestmentItem>>(emptyMap()) }
    var pendingActionAmountEur by remember { mutableStateOf<Double?>(null) }
    var pendingActionShares by remember { mutableStateOf<Double?>(null) }
    var pendingActionPrefillMessage by remember { mutableStateOf<String?>(null) }
    var pendingActionSaleNote by remember { mutableStateOf<String?>(null) }
    var investmentDialogItem by remember { mutableStateOf<InvestmentItem?>(null) }
    var investmentDialogEntryType by remember { mutableStateOf("BUY") }
    var customAssetDialog by remember { mutableStateOf(false) }
    var editingCustomAsset by remember { mutableStateOf<CustomInvestment?>(null) }
    var notificationPermissionAsked by remember { mutableStateOf(false) }
    var availableUpdate by remember { mutableStateOf<AppUpdateInfo?>(null) }
    var updateDialogVisible by remember { mutableStateOf(false) }
    var updateStatusMessage by remember { mutableStateOf<String?>(null) }
    var updateCheckRequested by remember { mutableIntStateOf(0) }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    val exportBackupLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) {
            runCatching {
                val payload = UserDataBackupManager.exportJson(context, BuildConfig.VERSION_NAME)
                val output = context.contentResolver.openOutputStream(uri, "wt")
                    ?: error("Backup-Datei konnte nicht geöffnet werden.")
                output.bufferedWriter(Charsets.UTF_8).use { writer -> writer.write(payload) }
            }.onSuccess {
                Toast.makeText(context, "Backup erfolgreich gespeichert.", Toast.LENGTH_LONG).show()
            }.onFailure { error ->
                Toast.makeText(context, "Backup fehlgeschlagen: ${error.message}", Toast.LENGTH_LONG).show()
            }
        }
    }
    val restoreBackupLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            val previousHoldingIds = holdingIds.toSet()
            runCatching {
                val input = context.contentResolver.openInputStream(uri)
                    ?: error("Backup-Datei konnte nicht geöffnet werden.")
                val raw = input.use { stream -> UserDataBackupManager.readJson(stream) }
                val restoredValueCount = UserDataBackupManager.restoreJson(context, raw)
                vm.reloadLocalUserDataAfterRestore(previousHoldingIds)
                restoredValueCount
            }.onSuccess { restoredValueCount ->
                Toast.makeText(
                    context,
                    "Backup wiederhergestellt · $restoredValueCount Werte.",
                    Toast.LENGTH_LONG
                ).show()
            }.onFailure { error ->
                Toast.makeText(
                    context,
                    "Wiederherstellung abgebrochen: ${error.message}",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    val activeOverlay = when {
        budgetDialog -> AppOverlay.BUDGET
        investmentDialogItem != null -> AppOverlay.PURCHASE_HISTORY
        customAssetDialog -> AppOverlay.CUSTOM_ASSET
        editingCustomAsset != null -> AppOverlay.EDIT_CUSTOM_ASSET
        missingAlertItemMessage != null -> AppOverlay.MISSING_ALERT_ITEM
        updateDialogVisible -> AppOverlay.UPDATE
        updateStatusMessage != null -> AppOverlay.UPDATE_STATUS
        else -> AppOverlay.NONE
    }
    val navigationState = AppNavigationState(
        rootTab = tab,
        detailId = selectedDetailId,
        detailReturnTab = detailReturnTab,
        child = if (showSavingsPlans) AppChildScreen.SAVINGS_PLANS else AppChildScreen.NONE,
        overlay = activeOverlay
    )

    BackHandler(enabled = navigationState.onBack() !is BackResult.ExitActivity) {
        when (val backResult = navigationState.onBack()) {
            is BackResult.Consume -> {
                val next = backResult.next
                if (navigationState.overlay != AppOverlay.NONE && next.overlay == AppOverlay.NONE) {
                    when (navigationState.overlay) {
                        AppOverlay.BUDGET -> budgetDialog = false
                        AppOverlay.PURCHASE_HISTORY -> {
                            investmentDialogItem = null
                            investmentDialogEntryType = "BUY"
                            pendingActionAmountEur = null
                            pendingActionShares = null
                            pendingActionPrefillMessage = null
                            pendingActionSaleNote = null
                        }
                        AppOverlay.CUSTOM_ASSET -> customAssetDialog = false
                        AppOverlay.EDIT_CUSTOM_ASSET -> editingCustomAsset = null
                        AppOverlay.MISSING_ALERT_ITEM -> missingAlertItemMessage = null
                        AppOverlay.UPDATE -> updateDialogVisible = false
                        AppOverlay.UPDATE_STATUS -> updateStatusMessage = null
                        AppOverlay.NONE -> Unit
                    }
                }
                tab = next.rootTab
                selectedDetailId = next.detailId
                detailReturnTab = next.detailReturnTab
                showSavingsPlans = next.child == AppChildScreen.SAVINGS_PLANS
            }
            BackResult.ExitActivity -> Unit
        }
    }

    LaunchedEffect(pushNavigationRequest) {
        if (pushNavigationRequest > 0L) {
            tab = if (!initialDetailId.isNullOrBlank()) 3 else initialTab.coerceIn(0, 4)
            detailReturnTab = if (initialDetailId.isNullOrBlank()) initialTab.coerceIn(0, 4) else 3
            selectedDetailId = initialDetailId?.takeIf { it.isNotBlank() }
            showSavingsPlans = initialOpenSavingsPlans
            initialAlertId?.takeIf { it.isNotBlank() }?.let { vm.markAlertRead(it) }
        }
    }

    LaunchedEffect(updateCheckRequested) {
        if (updateCheckRequested == 0) {
            val update = AppUpdateManager.check(context)
            availableUpdate = update
            updateDialogVisible = update != null
        } else {
            when (val result = AppUpdateManager.checkResult(context)) {
                is UpdateCheckResult.Available -> {
                    availableUpdate = result.update
                    updateDialogVisible = true
                }
                is UpdateCheckResult.Current -> {
                    availableUpdate = null
                    updateStatusMessage = "Du nutzt bereits die aktuelle Version ${result.versionName}."
                }
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
                        IconButton(onClick = { selectedDetailId = null; showSavingsPlans = false; tab = 4 }) { Icon(Icons.Default.AccountBalanceWallet, contentDescription = "Geldverwaltung öffnen") }
                        TextButton(onClick = {
                            if (availableUpdate != null) updateDialogVisible = true else updateCheckRequested++
                        }) {
                            Text(
                                if (availableUpdate != null) "Update verfügbar · v${availableUpdate!!.versionName}" else "v${BuildConfig.VERSION_NAME}",
                                color = if (availableUpdate != null) RadarGreen else RadarMuted,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = if (availableUpdate != null) FontWeight.Bold else FontWeight.Medium
                            )
                        }
                        IconButton(onClick = { vm.refresh() }) { Icon(Icons.Default.Refresh, contentDescription = "Aktualisieren") }
                    }
                )
            },
            bottomBar = {
                NavigationBar(containerColor = RadarSurface) {
                    NavigationBarItem(selected = tab == 0, onClick = { selectedDetailId = null; showSavingsPlans = false; tab = 0 }, icon = { Icon(Icons.AutoMirrored.Filled.ShowChart, null) }, label = { Text("Live") })
                    NavigationBarItem(selected = tab == 1, onClick = { selectedDetailId = null; showSavingsPlans = false; tab = 1 }, icon = { Icon(Icons.Default.Search, null) }, label = { Text("Radar") })
                    NavigationBarItem(selected = tab == 2, onClick = { selectedDetailId = null; showSavingsPlans = false; tab = 2 }, icon = { Icon(Icons.Default.Favorite, null) }, label = { Text("Portfolio") })
                    NavigationBarItem(selected = tab == 3, onClick = { selectedDetailId = null; showSavingsPlans = false; tab = 3 }, icon = { Icon(Icons.Default.Notifications, null) }, label = { Text("Alarme") })
                    NavigationBarItem(selected = tab == 4, onClick = { selectedDetailId = null; showSavingsPlans = false; tab = 4 }, icon = { Icon(Icons.Default.AccountBalanceWallet, null) }, label = { Text("Geld") })
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
                        val portfolioValues = PortfolioAnalysis.values(s.data.items, positions, customItems)
                        val monthlySavings = SavingsPlanBudget.monthlyAmounts(SavingsPlanStore.readPlans(context))
                        val advisorCandidates = s.data.items.map { item ->
                            PortfolioAdvisorCandidateFactory.create(
                                item = item,
                                isHolding = item.id in holdingIds,
                                currentValueEur = portfolioValues[item.id],
                                monthlySavingsEur = monthlySavings[item.id] ?: 0,
                                freshness = DataFreshness.summarize(item)
                            )
                        }
                        val advisorPlan = PortfolioAdvisorEngine.allocate(advisorCandidates, budgetState.advisorBudgetEur)
                        val advisorById = advisorPlan.candidates.associateBy { it.itemId }
                        val itemsById = s.data.items.associateBy { it.id }
                        val liquidityItemsById = buildMap {
                            putAll(itemsById)
                            customItems.forEach { custom ->
                                if (custom.id !in this) put(custom.id, custom.fallbackItem())
                            }
                        }
                        val currentPricesEur = itemsById.mapValues { (_, item) -> euroComparablePrice(item) }
                        val liquidityPricesEur = liquidityItemsById.mapValues { (_, item) -> euroComparablePrice(item) }
                        val liquidityHoldings = positions.values.mapNotNull { position ->
                            if (!position.isActiveHolding()) return@mapNotNull null
                            val itemId = position.itemId
                            val item = liquidityItemsById[itemId]
                            val candidate = advisorById[itemId]
                            val price = liquidityPricesEur[itemId]
                            val value = (portfolioValues[itemId] ?: position.currentValue(price))
                                ?.takeIf { it.isFinite() && it > 0.01 }
                                ?: return@mapNotNull null
                            val shares = position.shares.takeIf { it.isFinite() && it > 0.0 }
                                ?: position.trackedShares?.takeIf { it.isFinite() && it > 0.0 }
                            LiquidityHolding(
                                itemId = itemId,
                                instrumentName = item?.name ?: itemId,
                                currentValueEur = value,
                                shares = shares,
                                advisorAction = candidate?.action,
                                advisorScore = candidate?.advisor?.score,
                                dataReliable = candidate?.advisor?.reliable
                                    ?: ((item?.dataQuality?.overallCoverage ?: item?.coverage ?: 0) >= 60),
                                forecastDirection = candidate?.forecastDirection ?: item?.forecast?.direction,
                                profitLossPct = position.unrealizedProfitLossPercent(price)
                            )
                        }
                        val baseActionPlan = ActionPlanEngine.build(
                            analysisDay = s.data.generatedAt.take(10).ifBlank { "current" },
                            advisorPlan = advisorPlan,
                            currentPricesEur = currentPricesEur
                        ).copy(availableBudgetEur = budgetState.monthlyAvailableEur)
                        val liveActionPlan = ExitStrategyActionOverlay.apply(
                            plan = baseActionPlan,
                            positions = positions,
                            currentPricesEur = currentPricesEur,
                            strategies = exitStrategies
                        )
                        val currentMoneyActionCenter = DepotActionCenterMapper.build(
                            actionPlan = liveActionPlan,
                            advisorById = advisorById,
                            itemsById = itemsById,
                            positions = positions
                        )
                        SideEffect {
                            if (moneyActionCenter != currentMoneyActionCenter) moneyActionCenter = currentMoneyActionCenter
                            if (moneyActionItemsById != itemsById) moneyActionItemsById = itemsById
                        }
                        val detailId = selectedDetailId
                        if (detailId != null) {
                            val detailItem = s.data.items.firstOrNull { it.id == detailId }
                            val detailCustom = customItems.firstOrNull { it.id == detailId }
                            InvestmentDetailScreen(
                                item = detailItem,
                                customItem = detailCustom,
                                position = positions[detailId],
                                advisorCandidate = advisorById[detailId],
                                advisorHistory = AdvisorStore.history(context, detailId),
                                isWatchlisted = detailId in watchlistIds,
                                onBack = { selectedDetailId = null; tab = detailReturnTab },
                                onToggleWatchlist = vm::toggleWatchlist,
                                onEditPosition = { investmentDialogItem = it },
                                onOpenPortfolio = { selectedDetailId = null; tab = 2 },
                                exitStrategy = exitStrategies[detailId] ?: ExitStrategy(detailId),
                                onSaveExitStrategy = vm::saveExitStrategy
                            )
                        } else when (tab) {
                            0 -> DashboardScreen(
                                data = s.data,
                                budgetState = budgetState,
                                holdingIds = holdingIds,
                                positions = positions,
                                customItems = customItems,
                                watchlistIds = watchlistIds,
                                advisorPlan = advisorPlan,
                                onEditBudget = { budgetDialog = true },
                                onOpenRadar = { selectedDetailId = null; tab = 1 },
                                onOpenPortfolio = { selectedDetailId = null; tab = 2 },
                                onOpenInstrument = { id -> detailReturnTab = 0; selectedDetailId = id },
                                onEditRecommendation = { item, recommendedAmountEur, sellMode ->
                                    val actionType = if (sellMode) {
                                        when (advisorById[item.id]?.action) {
                                            PortfolioAdvisorAction.REDUZIEREN -> ActionType.REDUCE
                                            else -> ActionType.SELL
                                        }
                                    } else {
                                        if (positions[item.id]?.isActiveHolding() == true) ActionType.BUY_MORE else ActionType.OPEN_POSITION
                                    }
                                    val prefill = RecommendationTradePrefill.calculate(
                                        type = actionType,
                                        amountEur = recommendedAmountEur.toDouble().takeIf { it > 0.0 },
                                        priceEur = euroComparablePrice(item),
                                        heldShares = positions[item.id]?.shares
                                    )
                                    pendingActionAmountEur = prefill.amountEur.takeIf { it > 0.0 }
                                    pendingActionShares = prefill.shares
                                    pendingActionPrefillMessage = "Empfehlung direkt bearbeiten · ${prefill.message}"
                                    pendingActionSaleNote = null
                                    investmentDialogEntryType = if (sellMode) "SELL" else "BUY"
                                    investmentDialogItem = item
                                },
                                onAddToPortfolio = { investmentDialogItem = it }
                            )
                            1 -> RadarScreenV2(
                                items = s.data.items,
                                holdingIds = holdingIds,
                                watchlistIds = watchlistIds,
                                advisorById = advisorById,
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
                                advisorPlan = advisorPlan,
                                showSavingsPlans = showSavingsPlans,
                                onShowSavingsPlansChange = { showSavingsPlans = it },
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
                            3 -> AlertsScreen(
                                alerts = alerts,
                                preferences = alertPreferences,
                                advisorById = advisorById,
                                itemsById = itemsById,
                                positions = positions,
                                onExecuteAction = { action ->
                                    when (action.type) {
                                        ActionType.BUY_MORE, ActionType.OPEN_POSITION -> {
                                            val item = itemsById[action.instrumentId]
                                            if (item != null) {
                                                val prefill = RecommendationTradePrefill.calculate(
                                                    type = action.type,
                                                    amountEur = action.displayAmountEur,
                                                    priceEur = euroComparablePrice(item),
                                                    heldShares = positions[item.id]?.shares
                                                )
                                                pendingActionAmountEur = prefill.amountEur.takeIf { it > 0.0 }
                                                pendingActionShares = prefill.shares
                                                pendingActionPrefillMessage = prefill.message
                                                pendingActionSaleNote = null
                                                investmentDialogEntryType = "BUY"
                                                investmentDialogItem = item
                                            } else {
                                                missingAlertItemMessage = "Das Wertpapier ist nicht im aktuellen Radar enthalten."
                                            }
                                        }
                                        ActionType.SELL, ActionType.REDUCE -> {
                                            val item = itemsById[action.instrumentId]
                                            if (item != null) {
                                                val prefill = RecommendationTradePrefill.calculate(
                                                    type = action.type,
                                                    amountEur = action.displayAmountEur,
                                                    priceEur = euroComparablePrice(item),
                                                    heldShares = positions[item.id]?.shares
                                                )
                                                pendingActionAmountEur = prefill.amountEur.takeIf { it > 0.0 }
                                                pendingActionShares = prefill.shares
                                                pendingActionPrefillMessage = prefill.message
                                                pendingActionSaleNote = null
                                                investmentDialogEntryType = "SELL"
                                                investmentDialogItem = item
                                            } else {
                                                missingAlertItemMessage = "Das Wertpapier ist nicht im aktuellen Radar enthalten."
                                            }
                                        }
                                        ActionType.REVIEW_SAVINGS_PLAN, ActionType.KEEP_SAVINGS_PLAN -> {
                                            selectedDetailId = null
                                            showSavingsPlans = true
                                            tab = 2
                                        }
                                        ActionType.HOLD_CASH -> Unit
                                    }
                                },
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
                                        else -> missingAlertItemMessage = "Das Wertpapier ist nicht im aktuellen Radar enthalten."
                                    }
                                },
                                onMarkAllRead = vm::markAllAlertsRead,
                                onDelete = vm::deleteAlert,
                                onClear = vm::clearAlerts,
                                onPreferencesChange = vm::updateAlertPreferences
                            )
                            else -> MoneyManagementScreen(
                                current = budgetState,
                                actionCenter = moneyActionCenter,
                                liquidityHoldings = liquidityHoldings,
                                onOpenBudgetEditor = { budgetDialog = true },
                                onExportBackup = {
                                    val stamp = SimpleDateFormat("yyyy-MM-dd-HHmm", Locale.US).format(Date())
                                    exportBackupLauncher.launch("investment-radar-backup-$stamp.json")
                                },
                                onRestoreBackup = {
                                    restoreBackupLauncher.launch(arrayOf("application/json", "text/plain"))
                                },
                                onExecuteLiquiditySale = { suggestion, flowTag ->
                                    val item = liquidityItemsById[suggestion.itemId]
                                    if (item != null) {
                                        pendingActionAmountEur = suggestion.amountEur
                                        pendingActionShares = suggestion.shares
                                        pendingActionPrefillMessage = buildString {
                                            append("Geldbedarf: ")
                                            append(if (suggestion.fullExit) "Vollverkauf" else "Teilverkauf")
                                            if (suggestion.reason.isNotBlank()) append(" · ").append(suggestion.reason)
                                        }
                                        pendingActionSaleNote = "$flowTag · privater Verkaufserlös"
                                        investmentDialogEntryType = "SELL"
                                        investmentDialogItem = item
                                    } else {
                                        missingAlertItemMessage = "Das Wertpapier ist nicht im aktuellen Radar enthalten."
                                    }
                                },
                                onRecordWithdrawal = { amountEur, flowTag ->
                                    vm.addBudgetAdjustment(amountEur, false, "Auszahlung / Geldbedarf · $flowTag")
                                },
                                onExecuteAction = { action ->
                                    when (action.type) {
                                        ActionType.BUY_MORE, ActionType.OPEN_POSITION -> {
                                            val item = itemsById[action.instrumentId]
                                            if (item != null) {
                                                val prefill = RecommendationTradePrefill.calculate(
                                                    type = action.type,
                                                    amountEur = action.displayAmountEur,
                                                    priceEur = euroComparablePrice(item),
                                                    heldShares = positions[item.id]?.shares
                                                )
                                                pendingActionAmountEur = prefill.amountEur.takeIf { it > 0.0 }
                                                pendingActionShares = prefill.shares
                                                pendingActionPrefillMessage = prefill.message
                                                pendingActionSaleNote = null
                                                investmentDialogEntryType = "BUY"
                                                investmentDialogItem = item
                                            } else {
                                                missingAlertItemMessage = "Das Wertpapier ist nicht im aktuellen Radar enthalten."
                                            }
                                        }
                                        ActionType.SELL, ActionType.REDUCE -> {
                                            val item = itemsById[action.instrumentId]
                                            if (item != null) {
                                                val prefill = RecommendationTradePrefill.calculate(
                                                    type = action.type,
                                                    amountEur = action.displayAmountEur,
                                                    priceEur = euroComparablePrice(item),
                                                    heldShares = positions[item.id]?.shares
                                                )
                                                pendingActionAmountEur = prefill.amountEur.takeIf { it > 0.0 }
                                                pendingActionShares = prefill.shares
                                                pendingActionPrefillMessage = prefill.message
                                                pendingActionSaleNote = null
                                                investmentDialogEntryType = "SELL"
                                                investmentDialogItem = item
                                            } else {
                                                missingAlertItemMessage = "Das Wertpapier ist nicht im aktuellen Radar enthalten."
                                            }
                                        }
                                        ActionType.REVIEW_SAVINGS_PLAN, ActionType.KEEP_SAVINGS_PLAN -> {
                                            selectedDetailId = null
                                            showSavingsPlans = true
                                            tab = 2
                                        }
                                        ActionType.HOLD_CASH -> Unit
                                    }
                                }
                            )
                        }
                    }
                }

                refreshNotice?.let { notice ->
                    Surface(
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        shape = RoundedCornerShape(14.dp),
                        color = RadarYellow.copy(alpha = 0.96f),
                        tonalElevation = 6.dp
                    ) {
                        Text(
                            text = notice,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                            color = Color(0xFF201703),
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }

    if (budgetDialog) {
        BudgetDialog(
            current = budgetState,
            actionCenter = moneyActionCenter,
            onDismiss = { budgetDialog = false },
            onSaveMonthly = vm::setMonthlyBudget,
            onAddExtra = { amount -> vm.addExtraFunding(amount) },
            onAdjustment = { amount, credit, note -> vm.addBudgetAdjustment(amount, credit, note) },
            onExecuteAction = { action ->
                budgetDialog = false
                when (action.type) {
                    ActionType.BUY_MORE, ActionType.OPEN_POSITION -> {
                        val item = moneyActionItemsById[action.instrumentId]
                        if (item != null) {
                            val prefill = RecommendationTradePrefill.calculate(
                                type = action.type,
                                amountEur = action.displayAmountEur,
                                priceEur = euroComparablePrice(item),
                                heldShares = positions[item.id]?.shares
                            )
                            pendingActionAmountEur = prefill.amountEur.takeIf { it > 0.0 }
                            pendingActionShares = prefill.shares
                            pendingActionPrefillMessage = prefill.message
                            pendingActionSaleNote = null
                            investmentDialogEntryType = "BUY"
                            investmentDialogItem = item
                        } else {
                            missingAlertItemMessage = "Das Wertpapier ist nicht im aktuellen Radar enthalten."
                        }
                    }
                    ActionType.SELL, ActionType.REDUCE -> {
                        val item = moneyActionItemsById[action.instrumentId]
                        if (item != null) {
                            val prefill = RecommendationTradePrefill.calculate(
                                type = action.type,
                                amountEur = action.displayAmountEur,
                                priceEur = euroComparablePrice(item),
                                heldShares = positions[item.id]?.shares
                            )
                            pendingActionAmountEur = prefill.amountEur.takeIf { it > 0.0 }
                            pendingActionShares = prefill.shares
                            pendingActionPrefillMessage = prefill.message
                            pendingActionSaleNote = null
                            investmentDialogEntryType = "SELL"
                            investmentDialogItem = item
                        } else {
                            missingAlertItemMessage = "Das Wertpapier ist nicht im aktuellen Radar enthalten."
                        }
                    }
                    ActionType.REVIEW_SAVINGS_PLAN, ActionType.KEEP_SAVINGS_PLAN -> {
                        selectedDetailId = null
                        showSavingsPlans = true
                        tab = 2
                    }
                    ActionType.HOLD_CASH -> Unit
                }
            }
        )
    }

    investmentDialogItem?.let { item ->
        PurchaseHistoryDialog(
            item = item,
            current = positions[item.id] ?: PortfolioPosition(item.id),
            budgetHistory = budgetState.history,
            initialEntryType = investmentDialogEntryType,
            initialAmountEur = pendingActionAmountEur,
            initialShares = pendingActionShares,
            initialPrefillMessage = pendingActionPrefillMessage,
            onDismiss = {
                investmentDialogItem = null
                investmentDialogEntryType = "BUY"
                pendingActionAmountEur = null
                pendingActionShares = null
                pendingActionPrefillMessage = null
                pendingActionSaleNote = null
            },
            onUpsertPurchase = { purchase, fee -> vm.upsertPurchase(item.id, purchase, fee) },
            onDeletePurchase = { purchaseId -> vm.removePurchase(item.id, purchaseId) },
            onUpsertSale = { sale, fee -> vm.upsertSale(item.id, sale, fee) },
            onDeleteSale = { saleId -> vm.removeSale(item.id, saleId) },
            onExecutePurchase = { purchase, fee -> vm.executeBuy(item.id, purchase, feeEur = fee) },
            onExecuteSale = { sale, fee ->
                val saved = vm.executeSale(
                    itemId = item.id,
                    sale = sale,
                    feeEur = fee,
                    note = pendingActionSaleNote ?: "Verkauf ausgeführt"
                )
                if (saved) pendingActionSaleNote = null
                saved
            }
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
            title = { Text("Wertpapier nicht im Radar") },
            text = { Text(message) },
            confirmButton = {
                TextButton(onClick = { missingAlertItemMessage = null }) { Text("OK") }
            }
        )
    }

    if (updateDialogVisible) availableUpdate?.let { update ->
        AlertDialog(
            onDismissRequest = { updateDialogVisible = false },
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
                    updateDialogVisible = false
                }) { Text("Jetzt aktualisieren") }
            },
            dismissButton = { TextButton(onClick = { updateDialogVisible = false }) { Text("Später") } }
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
    budgetState: InvestmentBudgetViewState,
    holdingIds: Set<String>,
    positions: Map<String, PortfolioPosition>,
    customItems: List<CustomInvestment>,
    watchlistIds: Set<String>,
    advisorPlan: PortfolioAdvisorPlan,
    onEditBudget: () -> Unit,
    onOpenRadar: () -> Unit,
    onOpenPortfolio: () -> Unit,
    onOpenInstrument: (String) -> Unit,
    onEditRecommendation: (InvestmentItem, Int, Boolean) -> Unit,
    onAddToPortfolio: (InvestmentItem) -> Unit
) {
    val context = LocalContext.current
    val cashAmount = advisorPlan.cashEur
    val advisorById = advisorPlan.candidates.associateBy { it.itemId }
    val allocations = advisorPlan.allocations.associate { it.itemId to it.amountEur }
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
    val concentrationWarning: Pair<InvestmentItem, Double>? = null

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
                Text("Qualität, Bewertung, Wachstum, Momentum, Risiko und Datenqualität bestimmen das Signal. Depotgewicht bleibt davon getrennt.", color = RadarMuted)
            }
        }
        item {
            val signalAccent = if (top != null) RadarGreen else RadarPurple
            val signalStyle = if (top == null) MaterialTheme.typography.labelLarge else MaterialTheme.typography.titleLarge
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DarkMetricCard("MARKT", data.marketLight.uppercase(), RadarYellow, Modifier.weight(1f))
                DarkMetricCard("FÜR KÄUFE", formatMoney(budgetState.monthlyAvailableEur), RadarBlue, Modifier.weight(1f), onClick = onEditBudget)
                DarkMetricCard("SIGNAL", if (top != null) "AKTIV" else "WARTEN", signalAccent, Modifier.weight(1f), valueStyle = signalStyle)
            }
        }

        item {
            NeonPanel(accent = RadarBlue) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("BUDGET-COCKPIT", color = RadarBlue, fontWeight = FontWeight.Black, style = MaterialTheme.typography.labelLarge)
                        Text("Monatsbudget für neue Käufe", fontWeight = FontWeight.Black, style = MaterialTheme.typography.titleMedium)
                    }
                    IconButton(onClick = onEditBudget) { Icon(Icons.Default.Edit, contentDescription = "Budget bearbeiten") }
                }
                NeonStatStrip(
                    entries = listOf(
                        "Monatsbudget ${budgetState.monthLabel}" to formatMoney(budgetState.monthlyBudgetEur),
                        "Diesen Monat investiert" to formatMoney(budgetState.spentThisMonthEur),
                        "Für Käufe frei" to formatMoney(budgetState.monthlyAvailableEur),
                        "Übertrag (kein Kaufbudget)" to formatMoney(budgetState.carryoverEur),
                        "Zusatz-Cash (kein Kaufbudget)" to formatMoney(budgetState.extraFundingEur),
                        "Depot-Einstand" to formatMoney(budgetState.investedEur),
                        "Kontostand gesamt" to formatMoney(budgetState.cashBalanceEur),
                        "Reserviert" to formatMoney(budgetState.reservedEur),
                        "Cash gesamt (kein Kaufbudget)" to formatMoney(budgetState.availableEur)
                    ),
                    accent = RadarBlue
                )
                Text("Kaufempfehlungen dürfen nur dein Monatsbudget nutzen. Übertrag, Zusatz-Cash, Verkaufserlöse und Korrekturen bleiben getrennt und erhöhen den Kaufrahmen nicht.", color = RadarMuted, style = MaterialTheme.typography.bodySmall)
            }
        }

        item {
            LivePortfolioCard(
                items = data.items,
                positions = positions,
                customItems = customItems,
                onOpenPortfolio = onOpenPortfolio
            )
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
                buyCandidates.take(3).forEach { candidate ->
                    RelevantInstrumentRow(
                        label = "Kaufkandidat",
                        item = candidate,
                        accent = RadarGreen,
                        amountEur = allocations[candidate.id] ?: 0,
                        onOpen = { onOpenInstrument(candidate.id) },
                        onEdit = { onEditRecommendation(candidate, allocations[candidate.id] ?: 0, false) }
                    )
                }
                if (buyCandidates.isEmpty()) RelevantRow("Kaufkandidaten", "Keine", RadarMuted)
                reviewItems.take(3).forEach { candidate ->
                    RelevantInstrumentRow(
                        label = "Prüfsignal",
                        item = candidate,
                        accent = RadarYellow,
                        amountEur = 0,
                        onOpen = { onOpenInstrument(candidate.id) },
                        onEdit = { onEditRecommendation(candidate, 0, true) }
                    )
                }
                if (reviewItems.isEmpty()) RelevantRow("Prüfsignale", "Keine", RadarMuted)
                RelevantRow("Watchlist", "${watchlistIds.size} Werte", RadarPurple)
                if (missingQuoteItems.isNotEmpty()) RelevantRow("Kursdaten fehlen", missingQuoteItems.joinToString { dashboardInstrumentLabel(it) }, RadarYellow)
                concentrationWarning?.let { (item, share) ->
                    RelevantRow("Konzentration", "${dashboardInstrumentLabel(item)} ${String.format(Locale.GERMANY, "%.1f", share)} %", RadarRed)
                }
                TextButton(onClick = onOpenRadar, modifier = Modifier.align(Alignment.End)) { Text("Radar öffnen") }
            }
        }

        if (top != null) item {
            val label = RecommendationPresentation.label(top)
            val advisor = advisorById[top.id]
            val amount = allocations[top.id] ?: 0
            val topPosition = positions[top.id]
            val topInDepot = topPosition?.isActiveHolding() == true
            val topDepotValue = topPosition?.takeIf { it.isActiveHolding() }?.currentValue(top.price)
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
                        "Depotwert" to topDepotValue?.let(::formatMoney).orEmpty().ifBlank { "–" },
                        "Score" to RecommendationPresentation.scoreText(top.scoreTotal),
                        "Signal" to RecommendationPresentation.confidence(top),
                        "Konfidenz" to advisor?.advisor?.confidencePct?.let { "$it %" }.orEmpty().ifBlank { "–" }
                    )
                )
                Text(
                    if (amount > 0) "Diesen Monat $amount € investieren" else advisor?.advisor?.reasons?.firstOrNull() ?: "DIESEN MONAT WARTEN",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Black,
                    color = recommendationColor(label)
                )
                Text(advisor?.advisor?.reasons?.joinToString(" · ") ?: RecommendationPresentation.topReasons(top).joinToString(" · ").ifBlank { "Analyse liegt vor." }, color = RadarText)
                Text(priceLine(top), color = RadarMuted)
                Text(
                    "Datenstand Analyse: ${data.generatedAt.ifBlank { "nicht gemeldet" }}",
                    color = RadarMuted,
                    style = MaterialTheme.typography.bodySmall
                )
                LiveForecastSummary(top)
                ScoreBreakdownCard(top)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = { onEditRecommendation(top, amount, false) },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Edit, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text("Empfehlung bearbeiten", fontWeight = FontWeight.Black)
                    }
                    OutlinedButton(
                        onClick = { onAddToPortfolio(top) },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(if (topInDepot) "Position erhöhen" else "Zum Depot", fontWeight = FontWeight.Bold)
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = { TradeRepublicNavigator.open(context, top) },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = RadarGreen, contentColor = Color(0xFF05150E))
                    ) {
                        Icon(Icons.AutoMirrored.Filled.OpenInNew, null)
                        Spacer(Modifier.width(6.dp))
                        Text("Trade Republic öffnen", fontWeight = FontWeight.Bold)
                    }
                    OutlinedButton(onClick = { openMarketQuote(context, top) }, modifier = Modifier.weight(1f)) {
                        Icon(Icons.AutoMirrored.Filled.ShowChart, null)
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
                    Text("${formatMoney(budgetState.monthlyAvailableEur)} für neue Käufe frei", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
                }
                FilledTonalButton(onClick = onEditBudget) {
                    Icon(Icons.Default.AccountBalanceWallet, null)
                    Spacer(Modifier.width(6.dp))
                    Text("Budget")
                }
            }
        }

        items(data.items.sortedByDescending { allocations[it.id] ?: 0 }) { item ->
            RecommendationRow(
                item = item,
                personal = null,
                position = positions[item.id],
                onOpen = { TradeRepublicNavigator.open(context, item) },
                onAddToPortfolio = { onAddToPortfolio(item) }
            )
        }

        item {
            Text(
                "Nur belastbare Beratersignale erhalten neues Budget. Depotgewicht bleibt Information und bestimmt nicht die Handlung. Keine automatische Order.",
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
private fun RelevantInstrumentRow(
    label: String,
    item: InvestmentItem,
    accent: Color,
    amountEur: Int,
    onOpen: () -> Unit,
    onEdit: () -> Unit
) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(Color(0x0DFFFFFF), RoundedCornerShape(14.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(label, color = RadarMuted, style = MaterialTheme.typography.labelSmall)
        Text(dashboardInstrumentLabel(item), color = accent, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Surface(
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(12.dp),
                color = accent.copy(alpha = 0.08f),
                border = androidx.compose.foundation.BorderStroke(1.dp, accent.copy(alpha = 0.24f))
            ) {
                Column(Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
                    Text("Signal", color = RadarMuted, style = MaterialTheme.typography.labelSmall)
                    Text(RecommendationPresentation.label(item), color = accent, fontWeight = FontWeight.Black)
                }
            }
            Surface(
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(12.dp),
                color = Color(0x0AFFFFFF),
                border = androidx.compose.foundation.BorderStroke(1.dp, RadarGlow.copy(alpha = 0.24f))
            ) {
                Column(Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
                    Text("Betrag", color = RadarMuted, style = MaterialTheme.typography.labelSmall)
                    Text(if (amountEur > 0) "$amountEur €" else "manuell", color = RadarText, fontWeight = FontWeight.Black)
                }
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onOpen, modifier = Modifier.weight(1f)) {
                Text("Öffnen")
            }
            Button(onClick = onEdit, modifier = Modifier.weight(1f)) {
                Icon(Icons.Default.Edit, contentDescription = null)
                Spacer(Modifier.width(6.dp))
                Text("Bearbeiten", fontWeight = FontWeight.Black)
            }
        }
    }
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
    budgetHistory: List<BudgetHistoryItem>,
    initialEntryType: String = "BUY",
    initialAmountEur: Double? = null,
    initialShares: Double? = null,
    initialPrefillMessage: String? = null,
    onDismiss: () -> Unit,
    onUpsertPurchase: (PortfolioPurchase, Double?) -> Boolean,
    onDeletePurchase: (String) -> Boolean,
    onUpsertSale: (PortfolioSale, Double?) -> Boolean,
    onDeleteSale: (String) -> Boolean,
    onExecutePurchase: (PortfolioPurchase, Double) -> Boolean,
    onExecuteSale: (PortfolioSale, Double) -> Boolean
) {
    var entryType by remember(item.id, initialEntryType) { mutableStateOf(initialEntryType.takeIf { it == "SELL" } ?: "BUY") }
    var editingId by remember(item.id) { mutableStateOf<String?>(null) }
    var dateText by remember(item.id) { mutableStateOf(todayPurchaseDate()) }
    var amountText by remember(item.id, initialAmountEur) {
        mutableStateOf(initialAmountEur?.takeIf { it > 0.0 }?.let(::formatEditableNumber).orEmpty())
    }
    var feeText by remember(item.id) { mutableStateOf("") }
    var sharesText by remember(item.id, initialShares) {
        mutableStateOf(initialShares?.takeIf { it > 0.0 }?.let(::formatEditableNumber).orEmpty())
    }
    var budgetRelevant by remember(item.id) { mutableStateOf(true) }
    var errorText by remember(item.id) { mutableStateOf<String?>(null) }

    fun resetEditor(nextType: String = entryType) {
        entryType = nextType
        editingId = null
        dateText = todayPurchaseDate()
        amountText = ""
        feeText = ""
        sharesText = ""
        budgetRelevant = true
        errorText = null
    }

    fun editPurchase(purchase: PortfolioPurchase) {
        entryType = "BUY"
        editingId = purchase.id
        dateText = purchase.date.ifBlank { todayPurchaseDate() }
        amountText = formatEditableNumber(purchase.investedAmount)
        feeText = budgetHistory.firstOrNull { it.id == purchase.id }?.feeEur?.takeIf { it > 0.0 }?.let(::formatEditableNumber).orEmpty()
        sharesText = formatEditableNumber(purchase.shares)
        errorText = null
    }

    fun editSale(sale: PortfolioSale) {
        entryType = "SELL"
        editingId = sale.id
        dateText = sale.date.ifBlank { todayPurchaseDate() }
        amountText = formatEditableNumber(sale.proceeds)
        feeText = budgetHistory.firstOrNull { it.id == sale.id }?.feeEur?.takeIf { it > 0.0 }?.let(::formatEditableNumber).orEmpty()
        sharesText = formatEditableNumber(sale.shares)
        errorText = null
    }

    val amount = parseDecimal(amountText)
    val fee = parseDecimal(feeText) ?: 0.0
    val shares = parseDecimal(sharesText)
    val unitPrice = if (amount != null && shares != null && shares > 0.0) amount / shares else null
    val valid = isValidPurchaseDate(dateText) && amount != null && amount > 0.0 && fee >= 0.0 &&
        (entryType != "BUY" || fee <= amount) && shares != null && shares > 0.0
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
                Text(
                    "Aktueller Live-Kurs: ${euroComparablePrice(item)?.let(::formatMoney) ?: "–"} · nur Orientierung, kein automatischer Einstand",
                    color = RadarCyan,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Bold
                )
                initialPrefillMessage?.takeIf { it.isNotBlank() }?.let { message ->
                    Text(
                        "$message Bitte Betrag, Kurs und Anteile vor der Bestätigung prüfen.",
                        color = RadarYellow,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold
                    )
                }

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
                    label = { Text(if (entryType == "BUY") "Tatsächliche Belastung inkl. Gebühren" else "Netto-Verkaufserlös nach Gebühren") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = feeText,
                    onValueChange = { feeText = sanitizeDecimalInput(it); errorText = null },
                    label = { Text("Davon Gebühren in € (optional)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                if (editingId == null) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                        Column(Modifier.weight(1f)) {
                            Text(if (entryType == "BUY") "Budgetwirksam" else "Im Geldverlauf erfassen", fontWeight = FontWeight.Bold)
                            Text(
                                when {
                                    entryType == "BUY" && budgetRelevant -> "Verändert den Investment-Kontostand."
                                    entryType == "SELL" && budgetRelevant -> "Dokumentiert den Verkaufserlös. Er erhöht App-Cash und Kaufbudget nicht."
                                    else -> "Nur historische Depotbuchung – verändert das Budget nicht."
                                },
                                color = RadarMuted,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                        Switch(checked = budgetRelevant, onCheckedChange = { budgetRelevant = it })
                    }
                }
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

                if (editingId == null) {
                    Text(
                        if (budgetRelevant) {
                            if (entryType == "BUY") "Erst diese Bestätigung bucht die tatsächliche Belastung vom Budget ab." else "Erst diese Bestätigung erfasst den Verkaufserlös im Geldverlauf. Er bleibt privat und erhöht App-Cash und Kaufbudget nicht."
                        } else {
                            "Historische Depotbuchung: Stückzahl und Einstand werden ergänzt, der Investment-Kontostand bleibt unverändert."
                        },
                        color = RadarMuted,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
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
                            val saved = if (editingPurchase) {
                                onUpsertPurchase(purchase, budgetHistory.firstOrNull { it.id == purchase.id }?.let { fee })
                            } else if (budgetRelevant) {
                                onExecutePurchase(purchase, fee)
                            } else {
                                onUpsertPurchase(purchase, null)
                            }
                            if (saved) {
                                resetEditor("BUY")
                            } else {
                                errorText = if (editingPurchase) {
                                    "Änderung nicht möglich – dadurch wäre ein bereits erfasster Verkauf nicht mehr durch den Bestand gedeckt."
                                } else {
                                    "Kauf nicht möglich – prüfe verfügbares Budget, Betrag und Stückzahl."
                                }
                            }
                        } else {
                            val sale = PortfolioSale(
                                id = editingId ?: UUID.randomUUID().toString(),
                                date = dateText,
                                proceeds = amount!!,
                                shares = shares!!
                            )
                            val saved = if (editingSale) {
                                onUpsertSale(sale, budgetHistory.firstOrNull { it.id == sale.id }?.let { fee })
                            } else if (budgetRelevant) {
                                onExecuteSale(sale, fee)
                            } else {
                                onUpsertSale(sale, null)
                            }
                            if (saved) {
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
                            entryType == "BUY" -> "Kauf ausgeführt"
                            entryType == "SELL" && editingSale -> "Verkauf aktualisieren"
                            else -> "Verkauf ausgeführt"
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
private fun RecommendationRow(
    item: InvestmentItem,
    personal: PersonalRecommendation?,
    position: PortfolioPosition?,
    onOpen: () -> Unit,
    onAddToPortfolio: () -> Unit
) {
    val label = RecommendationPresentation.label(item)
    val amount = personal?.allocationEur ?: 0
    val isHolding = position?.isActiveHolding() == true
    val depotValue = position?.takeIf { it.isActiveHolding() }?.currentValue(item.price)
    NeonPanel(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onOpen),
        accent = recommendationColor(label)
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(item.name, fontWeight = FontWeight.Black)
                Text("${item.ticker} · Score ${RecommendationPresentation.scoreText(item.scoreTotal)} · Risiko ${item.risk}/5", color = RadarMuted, style = MaterialTheme.typography.bodySmall)
                if (isHolding) {
                    Text(
                        if (depotValue != null) "IM DEPOT · ${formatMoney(depotValue)}" else "IM DEPOT · Kurs fehlt",
                        color = RadarPurple,
                        fontWeight = FontWeight.Black,
                        style = MaterialTheme.typography.labelLarge
                    )
                }
                Text(
                    if (amount > 0) "Diesen Monat $amount €" else personal?.explanation ?: RecommendationPresentation.label(item),
                    color = recommendationColor(label),
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.labelLarge
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(if (amount > 0) "$amount €" else "0 €", fontWeight = FontWeight.Black, style = MaterialTheme.typography.titleMedium, color = if (amount > 0) RadarGreen else RadarMuted)
                Icon(Icons.AutoMirrored.Filled.OpenInNew, null, tint = RadarCyan, modifier = Modifier.size(18.dp))
            }
        }
        LiveForecastSummary(item, compact = true)
        FilledTonalButton(onClick = onAddToPortfolio, modifier = Modifier.fillMaxWidth()) {
            Text(if (isHolding) "Position erhöhen" else "Zum Depot hinzufügen", fontWeight = FontWeight.Black)
        }
    }
}

@Composable
private fun DarkMetricCard(
    label: String,
    value: String,
    accent: Color,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    valueStyle: androidx.compose.ui.text.TextStyle? = null
) {
    val clickModifier = if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier
    NeonPanel(modifier.then(clickModifier), accent) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = RadarMuted)
        Text(value, fontWeight = FontWeight.Black, color = accent, style = valueStyle ?: MaterialTheme.typography.titleLarge)
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
                .background(Brush.linearGradient(listOf(accent.copy(alpha = NeonPanelGlowAlpha), RadarSurface.copy(alpha = 0.99f), RadarSurface2.copy(alpha = 0.96f))))
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

private fun visibleBudgetHistoryNote(note: String): String = when {
    note.startsWith("Geldbedarf:") -> note.substringAfter(" · ", "Privater Verkaufserlös")
    " · Geldbedarf:" in note -> note.substringBefore(" · Geldbedarf:")
    else -> note
}

@Composable
private fun MoneyManagementScreen(
    current: InvestmentBudgetViewState,
    actionCenter: DepotActionCenterState,
    liquidityHoldings: List<LiquidityHolding>,
    onOpenBudgetEditor: () -> Unit,
    onExportBackup: () -> Unit,
    onRestoreBackup: () -> Unit,
    onExecuteLiquiditySale: (LiquiditySaleSuggestion, String) -> Unit,
    onRecordWithdrawal: (Double, String) -> Boolean,
    onExecuteAction: (DepotActionCenterItem) -> Unit
) {
    var liquidityNeedText by rememberSaveable { mutableStateOf("") }
    var confirmBackupRestore by rememberSaveable { mutableStateOf(false) }
    var useCashFirst by rememberSaveable { mutableStateOf(true) }
    var liquidityFlowId by rememberSaveable { mutableStateOf(UUID.randomUUID().toString()) }
    val liquidityNeed = parseDecimal(liquidityNeedText)?.takeIf { it > 0.0 }
    val liquidityFlowTag = "Geldbedarf:$liquidityFlowId"
    val liquidityProgress = liquidityNeed?.let {
        LiquidityNeedEngine.progress(
            requestedEur = it,
            history = current.history,
            flowTag = liquidityFlowTag
        )
    }
    val remainingLiquidityNeed = liquidityProgress?.remainingEur
    val liquidityPlan = remember(remainingLiquidityNeed, useCashFirst, current.availableEur, liquidityHoldings) {
        remainingLiquidityNeed?.takeIf { it > 0.01 }?.let {
            LiquidityNeedEngine.plan(
                requestedEur = it,
                availableCashEur = if (useCashFirst) current.availableEur else 0.0,
                holdings = liquidityHoldings
            )
        }
    }
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag("moneyManagementList"),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            NeonPanel(accent = RadarGreen) {
                Text("GELDVERWALTUNG · ${current.monthLabel}", color = RadarGreen, fontWeight = FontWeight.Black)
                Text("Gesamtes Cash inkl. Übertrag · kein Kaufbudget", color = RadarMuted, style = MaterialTheme.typography.bodySmall)
                Text(formatMoney(current.availableEur), style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Black, color = RadarGreen)
                Text(
                    "Käufe werden erst nach deiner Bestätigung abgezogen. Verkäufe werden erfasst; ihre Erlöse werden privat verwendet und erhöhen dein App-Cash nicht.",
                    color = RadarMuted,
                    style = MaterialTheme.typography.bodySmall
                )
                Button(
                    onClick = onOpenBudgetEditor,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = RadarGreen, contentColor = Color(0xFF05150E))
                ) {
                    Icon(Icons.Default.Edit, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Budget & Geld bearbeiten", fontWeight = FontWeight.Black)
                }
            }
        }

        item {
            NeonPanel(accent = RadarBlue) {
                Text("DATENSICHERUNG", color = RadarBlue, fontWeight = FontWeight.Black)
                Text(
                    "Sichert Depot, Käufe/Verkäufe, Budget, Watchlist, Sparpläne, eigene Werte, Exit-Strategien und Alarm-Einstellungen.",
                    color = RadarMuted,
                    style = MaterialTheme.typography.bodySmall
                )
                Text(
                    "Die Backup-Datei enthält persönliche Finanzdaten im Klartext. Speichere sie nur an einem geschützten Ort. Wiederherstellen ersetzt die lokalen App-Daten durch den Stand aus der gewählten Datei.",
                    color = RadarYellow,
                    style = MaterialTheme.typography.bodySmall
                )
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = onExportBackup,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = RadarBlue)
                    ) {
                        Text("Backup exportieren", fontWeight = FontWeight.Bold)
                    }
                    OutlinedButton(
                        onClick = { confirmBackupRestore = true },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Backup wiederherstellen", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        item {
            NeonStatStrip(
                entries = listOf(
                    "Für Käufe frei" to formatMoney(current.monthlyAvailableEur),
                    "Cash gesamt (kein Kaufbudget)" to formatMoney(current.availableEur),
                    "Monatsbudget" to formatMoney(current.monthlyBudgetEur),
                    "Übertrag (kein Kaufbudget)" to formatMoney(current.carryoverEur),
                    "Zusatz-Cash (kein Kaufbudget)" to formatMoney(current.extraFundingEur),
                    "Depot-Einstand" to formatMoney(current.investedEur),
                    "Kontostand gesamt" to formatMoney(current.cashBalanceEur),
                    "Reserviert" to formatMoney(current.reservedEur)
                ),
                accent = RadarBlue
            )
        }

        item {
            NeonPanel(accent = RadarYellow) {
                Text("ICH BRAUCHE GELD", color = RadarYellow, fontWeight = FontWeight.Black)
                Text(
                    "Sag der App, wie viel Geld du brauchst. Sie nutzt auf Wunsch zuerst freies Cash und schlägt danach die sinnvollsten Verkäufe vor. Keine Order wird automatisch ausgeführt.",
                    color = RadarMuted,
                    style = MaterialTheme.typography.bodySmall
                )
                OutlinedTextField(
                    value = liquidityNeedText,
                    onValueChange = {
                        val next = sanitizeDecimalInput(it)
                        if (next != liquidityNeedText) liquidityFlowId = UUID.randomUUID().toString()
                        liquidityNeedText = next
                    },
                    label = { Text("Benötigter Betrag in €") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(50, 100, 250, 500).forEach { preset ->
                        AssistChip(
                            onClick = {
                                liquidityFlowId = UUID.randomUUID().toString()
                                liquidityNeedText = preset.toString()
                            },
                            label = { Text("$preset €") }
                        )
                    }
                }
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("Freies Cash zuerst verwenden", fontWeight = FontWeight.Bold)
                        Text(
                            if (useCashFirst) "Nur der fehlende Rest wird verkauft." else "Der komplette Betrag wird über Verkäufe geplant.",
                            color = RadarMuted,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    Switch(
                        checked = useCashFirst,
                        onCheckedChange = { useCashFirst = it }
                    )
                }

                if (liquidityNeed != null && liquidityProgress?.complete == true) {
                    Text(
                        "Geldbedarf vollständig gedeckt.",
                        color = RadarGreen,
                        fontWeight = FontWeight.Black
                    )
                    Text(
                        "${formatMoney(liquidityProgress.withdrawnCashEur)} aus App-Cash entnommen · ${formatMoney(liquidityProgress.privateSaleCoveredEur)} aus privaten Verkaufserlösen.",
                        color = RadarMuted,
                        style = MaterialTheme.typography.bodySmall
                    )
                    OutlinedButton(
                        onClick = {
                            liquidityNeedText = ""
                            liquidityFlowId = UUID.randomUUID().toString()
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Neuen Geldbedarf planen") }
                } else if (liquidityPlan != null) {
                    val plan = liquidityPlan
                    HorizontalDivider(color = RadarSurface2)
                    Text("Benötigt gesamt: ${formatMoney(liquidityNeed ?: plan.requestedEur)}", fontWeight = FontWeight.Black)
                    liquidityProgress?.takeIf { it.coveredEur > 0.01 }?.let { progress ->
                        Text(
                            "Bereits gedeckt: ${formatMoney(progress.coveredEur)} · noch offen: ${formatMoney(progress.remainingEur)}",
                            color = RadarGreen,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    if (useCashFirst && plan.cashUsedEur > 0.0) {
                        Text("Davon aus freiem Cash: ${formatMoney(plan.cashUsedEur)}", color = RadarGreen)
                    }
                    if (plan.saleNeededEur <= 0.01) {
                        Text(
                            "Kein Verkauf nötig. Dein freies Cash reicht für diesen Geldbedarf.",
                            color = RadarGreen,
                            fontWeight = FontWeight.Black
                        )
                    } else {
                        Text(
                            "Noch durch Verkäufe freizumachen: ${formatMoney(plan.saleNeededEur)}",
                            color = RadarRed,
                            fontWeight = FontWeight.Black
                        )
                        plan.suggestions.forEachIndexed { index, suggestion ->
                            NeonPanel(accent = if (index == 0) RadarRed else RadarYellow) {
                                Row(
                                    Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(Modifier.weight(1f)) {
                                        Text("${index + 1}. ${suggestion.instrumentName}", fontWeight = FontWeight.Black)
                                        Text(
                                            if (suggestion.fullExit) "Vollständig verkaufen" else "Teilverkauf",
                                            color = if (suggestion.fullExit) RadarRed else RadarYellow,
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                    Text(formatMoney(suggestion.amountEur), color = RadarRed, fontWeight = FontWeight.Black)
                                }
                                suggestion.shares?.let {
                                    Text("Ca. ${formatShares(it)} Anteile", color = RadarText, style = MaterialTheme.typography.bodySmall)
                                }
                                if (!suggestion.fullExit) {
                                    Text(
                                        "Danach verbleiben ca. ${formatMoney(suggestion.remainingValueEur)} in der Position.",
                                        color = RadarMuted,
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                                Text(suggestion.reason, color = RadarMuted, style = MaterialTheme.typography.bodySmall)
                                Button(
                                    onClick = { onExecuteLiquiditySale(suggestion, liquidityFlowTag) },
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = ButtonDefaults.buttonColors(containerColor = RadarRed, contentColor = Color.White)
                                ) {
                                    Text("Verkauf erfassen", fontWeight = FontWeight.Black)
                                }
                            }
                        }
                        if (plan.uncoveredEur > 0.01) {
                            Text(
                                "Noch nicht gedeckt: ${formatMoney(plan.uncoveredEur)}. Der aktuell bewertbare Depotwert reicht für den gewünschten Betrag nicht aus.",
                                color = RadarRed,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    if (plan.cashUsedEur > 0.01) {
                        HorizontalDivider(color = RadarSurface2)
                        Text(
                            "Diesen Anteil kannst du aus vorhandenem App-Cash privat entnehmen. Verkaufserlöse werden separat direkt als privat gedeckt gezählt.",
                            color = RadarMuted,
                            style = MaterialTheme.typography.bodySmall
                        )
                        Button(
                            onClick = {
                                onRecordWithdrawal(plan.cashUsedEur, liquidityFlowTag)
                            },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = RadarYellow, contentColor = Color(0xFF201703))
                        ) {
                            Text("${formatMoney(plan.cashUsedEur)} App-Cash entnehmen", fontWeight = FontWeight.Black)
                        }
                    }
                }
            }
        }

        item {
            NeonPanel(accent = if (actionCenter.isEmpty) RadarMuted else RadarCyan) {
                Text("WAS SOLL ICH JETZT TUN?", color = RadarCyan, fontWeight = FontWeight.Black)
                Text(
                    if (actionCenter.isEmpty)
                        "Aktuell gibt es keine belastbare Kauf- oder Verkaufsaktion. Dein Geld bleibt als Cash verfügbar."
                    else
                        "Hier stehen nur konkrete Aktionen aus dem aktuellen Investment-Plan. Keine Order wird automatisch ausgeführt.",
                    color = RadarMuted,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }

        if (!actionCenter.isEmpty) {
            items(actionCenter.items.take(6)) { action ->
                val accent = when (action.type) {
                    ActionType.SELL -> RadarRed
                    ActionType.REDUCE, ActionType.REVIEW_SAVINGS_PLAN -> RadarYellow
                    ActionType.BUY_MORE, ActionType.OPEN_POSITION -> RadarGreen
                    ActionType.KEEP_SAVINGS_PLAN -> RadarBlue
                    ActionType.HOLD_CASH -> RadarMuted
                }
                NeonPanel(accent = accent) {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(action.instrumentName, fontWeight = FontWeight.Black)
                            if (action.reason.isNotBlank()) {
                                Text(action.reason, color = RadarMuted, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                        Text(action.cashImpactText, color = accent, fontWeight = FontWeight.Black)
                    }
                    if (action.actionText.isNotBlank() && action.actionText != action.cashImpactText) {
                        Text(action.actionText, color = RadarText, style = MaterialTheme.typography.bodySmall)
                    }
                    if (action.executable) {
                        FilledTonalButton(
                            onClick = { onExecuteAction(action) },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                when (action.type) {
                                    ActionType.BUY_MORE, ActionType.OPEN_POSITION -> "Kauf bestätigen"
                                    ActionType.SELL, ActionType.REDUCE -> "Verkauf bestätigen"
                                    ActionType.REVIEW_SAVINGS_PLAN, ActionType.KEEP_SAVINGS_PLAN -> "Sparplan prüfen"
                                    ActionType.HOLD_CASH -> "Cash halten"
                                },
                                fontWeight = FontWeight.Black
                            )
                        }
                    }
                }
            }
        }

        item {
            Text("Geldverlauf", color = RadarCyan, fontWeight = FontWeight.Black, style = MaterialTheme.typography.titleMedium)
            Text(
                "So kannst du jederzeit nachvollziehen, warum sich dein verfügbarer Betrag geändert hat.",
                color = RadarMuted,
                style = MaterialTheme.typography.bodySmall
            )
        }

        if (current.history.isEmpty()) {
            item { Text("Noch keine Geldbewegungen vorhanden.", color = RadarMuted) }
        } else {
            items(current.history.take(30)) { entry ->
                val isSale = entry.title == "Verkauf"
                val accent = when {
                    isSale -> RadarYellow
                    entry.isCredit -> RadarGreen
                    else -> RadarBlue
                }
                NeonPanel(accent = accent) {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(entry.title, fontWeight = FontWeight.Black)
                            Text(entry.date, color = RadarMuted, style = MaterialTheme.typography.bodySmall)
                        }
                        Text(if (isSale) formatMoney(kotlin.math.abs(entry.amountEur)) else formatSignedMoney(entry.amountEur), color = accent, fontWeight = FontWeight.Black)
                    }
                    val details = listOf(entry.itemId, visibleBudgetHistoryNote(entry.note)).filter { it.isNotBlank() }.joinToString(" · ")
                    if (details.isNotBlank()) Text(details, color = RadarMuted, style = MaterialTheme.typography.bodySmall)
                }
            }
        }

        item { Spacer(Modifier.height(84.dp)) }
    }

    if (confirmBackupRestore) {
        AlertDialog(
            onDismissRequest = { confirmBackupRestore = false },
            title = { Text("Backup wiederherstellen?") },
            text = {
                Text(
                    "Die lokalen Depot-, Budget-, Watchlist- und Sparplandaten werden durch den Stand aus der ausgewählten Backup-Datei ersetzt. Bei einer ungültigen oder unvollständigen Datei wird nichts geändert."
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        confirmBackupRestore = false
                        onRestoreBackup()
                    }
                ) { Text("Datei auswählen") }
            },
            dismissButton = {
                TextButton(onClick = { confirmBackupRestore = false }) { Text("Abbrechen") }
            }
        )
    }
}

@Composable
private fun BudgetDialog(
    current: InvestmentBudgetViewState,
    actionCenter: DepotActionCenterState,
    onDismiss: () -> Unit,
    onSaveMonthly: (Double) -> Boolean,
    onAddExtra: (Double) -> Boolean,
    onAdjustment: (Double, Boolean, String) -> Boolean,
    onExecuteAction: (DepotActionCenterItem) -> Unit
) {
    var monthlyValue by remember(current.monthlyBudgetEur) { mutableStateOf(formatEditableNumber(current.monthlyBudgetEur)) }
    var extraValue by remember { mutableStateOf("") }
    var adjustmentValue by remember { mutableStateOf("") }
    var adjustmentNote by remember { mutableStateOf("") }
    var message by remember { mutableStateOf<String?>(null) }
    val monthly = parseDecimal(monthlyValue)
    val extra = parseDecimal(extraValue)
    val adjustment = parseDecimal(adjustmentValue)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Geldverwaltung") },
        text = {
            Column(Modifier.heightIn(max = 590.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("GELDVERWALTUNG · ${current.monthLabel}", color = RadarBlue, fontWeight = FontWeight.Black)
                Text("Für neue Käufe frei: ${formatMoney(current.monthlyAvailableEur)}", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black, color = RadarGreen)
                NeonStatStrip(
                    entries = listOf(
                        "Für Käufe frei" to formatMoney(current.monthlyAvailableEur),
                        "Cash gesamt (kein Kaufbudget)" to formatMoney(current.availableEur),
                        "Monatsbudget" to formatMoney(current.monthlyBudgetEur),
                        "Depot-Einstand" to formatMoney(current.investedEur),
                        "Zusatz-Cash (kein Kaufbudget)" to formatMoney(current.extraFundingEur),
                        "Übertrag (kein Kaufbudget)" to formatMoney(current.carryoverEur),
                        "Kontostand gesamt" to formatMoney(current.cashBalanceEur),
                        "Reserviert" to formatMoney(current.reservedEur)
                    ),
                    accent = RadarBlue
                )
                Text(
                    "Für neue Kaufempfehlungen zählt ausschließlich dein Monatsbudget abzüglich bereits bestätigter Käufe und Reservierungen. Überträge, Zusatz-Cash, Verkaufserlöse und Korrekturgutschriften erhöhen dieses Kaufbudget nicht.",
                    color = RadarMuted,
                    style = MaterialTheme.typography.bodySmall
                )
                if (current.feesEur > 0.0) {
                    Text("Diesen Monat in Transaktionen ausgewiesene Gebühren: ${formatMoney(current.feesEur)}", color = RadarYellow, style = MaterialTheme.typography.bodySmall)
                }

                HorizontalDivider(color = RadarSurface2)
                Text("Was soll ich mit meinem Geld tun?", fontWeight = FontWeight.Black, style = MaterialTheme.typography.titleMedium)
                Text("Konkrete Kauf-, Verkauf- und Reduzierungsaktionen aus deinem aktuellen Aktionsplan. Es wird keine Order automatisch ausgeführt.", color = RadarMuted, style = MaterialTheme.typography.bodySmall)
                if (actionCenter.isEmpty) {
                    Text("Aktuell gibt es keine belastbare Aktion. Das verfügbare Geld bleibt als Cash erhalten.", color = RadarMuted)
                } else {
                    actionCenter.items.take(6).forEach { action ->
                        val accent = when (action.type) {
                            ActionType.SELL -> RadarRed
                            ActionType.REDUCE, ActionType.REVIEW_SAVINGS_PLAN -> RadarYellow
                            ActionType.BUY_MORE, ActionType.OPEN_POSITION -> RadarGreen
                            ActionType.KEEP_SAVINGS_PLAN -> RadarBlue
                            ActionType.HOLD_CASH -> RadarMuted
                        }
                        NeonPanel(accent = accent) {
                            Text(action.instrumentName, fontWeight = FontWeight.Black)
                            Text(action.cashImpactText, fontWeight = FontWeight.Black, color = accent)
                            if (action.actionText.isNotBlank() && action.actionText != action.cashImpactText) {
                                Text(action.actionText, color = RadarText, style = MaterialTheme.typography.bodySmall)
                            }
                            if (action.reason.isNotBlank()) Text(action.reason, color = RadarMuted, style = MaterialTheme.typography.bodySmall)
                            if (action.executable) {
                                FilledTonalButton(onClick = { onExecuteAction(action) }, modifier = Modifier.fillMaxWidth()) {
                                    Text(
                                        when (action.type) {
                                            ActionType.BUY_MORE, ActionType.OPEN_POSITION -> "Kauf erfassen"
                                            ActionType.SELL, ActionType.REDUCE -> "Verkauf erfassen"
                                            ActionType.REVIEW_SAVINGS_PLAN, ActionType.KEEP_SAVINGS_PLAN -> "Sparplan prüfen"
                                            ActionType.HOLD_CASH -> "Cash halten"
                                        }
                                    )
                                }
                            }
                        }
                    }
                }

                HorizontalDivider(color = RadarSurface2)
                Text("Monatsbudget ändern", fontWeight = FontWeight.Black)
                Text("Die Änderung ersetzt nur das Budget des aktuellen Monats und wird als Standard für kommende Monate gespeichert. Frühere Monate bleiben in der Historie erhalten.", color = RadarMuted, style = MaterialTheme.typography.bodySmall)
                OutlinedTextField(
                    value = monthlyValue,
                    onValueChange = { monthlyValue = sanitizeDecimalInput(it); message = null },
                    label = { Text("Monatsbudget in €") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(50, 100, 150, 200).forEach { preset ->
                        AssistChip(onClick = { monthlyValue = preset.toString() }, label = { Text("$preset €") })
                    }
                }
                Button(
                    onClick = {
                        val value = monthly ?: return@Button
                        message = if (onSaveMonthly(value)) "Monatsbudget aktualisiert." else "Monatsbudget konnte nicht gespeichert werden."
                    },
                    enabled = monthly != null && monthly >= 0.0,
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Monatsbudget speichern") }

                HorizontalDivider(color = RadarSurface2)
                Text("Wechselgeld / zusätzliches Geld", fontWeight = FontWeight.Black)
                Text("Zusatzgeld erhöht den Kontostand zusätzlich zum Monatsbudget. Es wird nur einmal gebucht.", color = RadarMuted, style = MaterialTheme.typography.bodySmall)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    AssistChip(onClick = { message = if (onAddExtra(5.0)) "5 € Wechselgeld hinzugefügt." else "Zusatzgeld konnte nicht gespeichert werden." }, label = { Text("+ 5 €") })
                    AssistChip(onClick = { message = if (onAddExtra(10.0)) "10 € Wechselgeld hinzugefügt." else "Zusatzgeld konnte nicht gespeichert werden." }, label = { Text("+ 10 €") })
                    AssistChip(onClick = { message = if (onAddExtra(20.0)) "20 € zusätzliches Geld hinzugefügt." else "Zusatzgeld konnte nicht gespeichert werden." }, label = { Text("+ 20 €") })
                }
                OutlinedTextField(
                    value = extraValue,
                    onValueChange = { extraValue = sanitizeDecimalInput(it); message = null },
                    label = { Text("Zusätzlicher Betrag in €") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Button(
                    onClick = {
                        val value = extra ?: return@Button
                        if (onAddExtra(value)) {
                            extraValue = ""
                            message = "Wechselgeld hinzugefügt."
                        } else {
                            message = "Zusatzgeld konnte nicht gespeichert werden."
                        }
                    },
                    enabled = extra != null && extra > 0.0,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = RadarGreen, contentColor = Color(0xFF05150E))
                ) { Text("Wechselgeld hinzufügen", fontWeight = FontWeight.Black) }

                HorizontalDivider(color = RadarSurface2)
                Text("Korrektur", fontWeight = FontWeight.Black)
                Text("Nur verwenden, wenn der angezeigte Kontostand nach einer realen Buchung korrigiert werden muss. Die Begründung bleibt in der Historie sichtbar.", color = RadarMuted, style = MaterialTheme.typography.bodySmall)
                OutlinedTextField(
                    value = adjustmentValue,
                    onValueChange = { adjustmentValue = sanitizeDecimalInput(it); message = null },
                    label = { Text("Korrekturbetrag in €") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = adjustmentNote,
                    onValueChange = { adjustmentNote = it.take(120); message = null },
                    label = { Text("Grund der Korrektur") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = {
                            val value = adjustment ?: return@Button
                            if (onAdjustment(value, true, adjustmentNote)) {
                                adjustmentValue = ""; adjustmentNote = ""; message = "Gutschrift korrigiert."
                            } else message = "Korrektur konnte nicht gespeichert werden."
                        },
                        enabled = adjustment != null && adjustment > 0.0 && adjustmentNote.isNotBlank(),
                        modifier = Modifier.weight(1f)
                    ) { Text("+ Gutschrift") }
                    OutlinedButton(
                        onClick = {
                            val value = adjustment ?: return@OutlinedButton
                            if (onAdjustment(value, false, adjustmentNote)) {
                                adjustmentValue = ""; adjustmentNote = ""; message = "Abbuchung korrigiert."
                            } else message = "Korrektur konnte nicht gespeichert werden."
                        },
                        enabled = adjustment != null && adjustment > 0.0 && adjustmentNote.isNotBlank(),
                        modifier = Modifier.weight(1f)
                    ) { Text("− Abbuchung") }
                }

                HorizontalDivider(color = RadarSurface2)
                Text("Geldverlauf", color = RadarCyan, fontWeight = FontWeight.Black, style = MaterialTheme.typography.titleMedium)
                Text("Jede Einzahlung, jeder Kauf, Verkauf und jede Korrektur erklärt nachvollziehbar, warum sich dein verfügbares Geld verändert hat.", color = RadarMuted, style = MaterialTheme.typography.bodySmall)
                if (current.history.isEmpty()) {
                    Text("Noch keine Budgetbuchungen vorhanden.", color = RadarMuted)
                } else {
                    current.history.take(50).forEach { entry ->
                        val accent = if (entry.isCredit) RadarGreen else RadarBlue
                        NeonPanel(accent = accent) {
                            Row(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(entry.title, fontWeight = FontWeight.Black)
                                    Text(entry.date, color = RadarMuted, style = MaterialTheme.typography.bodySmall)
                                }
                                Text(formatSignedMoney(entry.amountEur), color = accent, fontWeight = FontWeight.Black)
                            }
                            val details = listOf(entry.itemId, entry.note).filter { it.isNotBlank() }.joinToString(" · ")
                            if (details.isNotBlank()) Text(details, color = RadarMuted, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }

                message?.let { Text(it, color = if (it.contains("nicht")) RadarRed else RadarGreen, fontWeight = FontWeight.Bold) }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Fertig") } },
        dismissButton = {}
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

private fun dashboardInstrumentLabel(item: InvestmentItem): String = item.name.trim().takeIf { it.isNotBlank() } ?: item.ticker.trim().takeIf { it.isNotBlank() } ?: item.id
