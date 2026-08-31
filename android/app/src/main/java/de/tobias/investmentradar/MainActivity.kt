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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.firebase.messaging.FirebaseMessaging
import java.util.Locale

private val RadarBg = Color(0xFF08111F)
private val RadarSurface = Color(0xFF101C2D)
private val RadarSurface2 = Color(0xFF16243A)
private val RadarGreen = Color(0xFF2EE59D)
private val RadarBlue = Color(0xFF4C8DFF)
private val RadarYellow = Color(0xFFFFC857)
private val RadarRed = Color(0xFFFF6577)
private val RadarText = Color(0xFFF2F6FC)
private val RadarMuted = Color(0xFF91A1B7)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (FirebaseBootstrap.isConfigured()) {
            FirebaseMessaging.getInstance().subscribeToTopic("investment-alerts")
            PortfolioStore.read(this).forEach { itemId ->
                FirebaseMessaging.getInstance().subscribeToTopic(MainViewModel.holdingTopic(itemId))
            }
        }
        setContent { InvestmentRadarUi() }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InvestmentRadarUi(vm: MainViewModel = viewModel()) {
    val state by vm.state.collectAsState()
    val holdingIds by vm.holdingIds.collectAsState()
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("investment_radar_settings", 0) }
    var budget by remember { mutableIntStateOf(prefs.getInt("monthly_budget", 100).coerceIn(10, 10000)) }
    var tab by remember { mutableIntStateOf(0) }
    var budgetDialog by remember { mutableStateOf(false) }
    var notificationPermissionAsked by remember { mutableStateOf(false) }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }

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
                        IconButton(onClick = { vm.refresh() }) { Icon(Icons.Default.Refresh, contentDescription = "Aktualisieren") }
                    }
                )
            },
            bottomBar = {
                NavigationBar(containerColor = RadarSurface) {
                    NavigationBarItem(selected = tab == 0, onClick = { tab = 0 }, icon = { Icon(Icons.Default.ShowChart, null) }, label = { Text("Live") })
                    NavigationBarItem(selected = tab == 1, onClick = { tab = 1 }, icon = { Icon(Icons.Default.Search, null) }, label = { Text("Radar") })
                    NavigationBarItem(selected = tab == 2, onClick = { tab = 2 }, icon = { Icon(Icons.Default.Favorite, null) }, label = { Text("Portfolio") })
                    NavigationBarItem(selected = tab == 3, onClick = { tab = 3 }, icon = { Icon(Icons.Default.Notifications, null) }, label = { Text("Alarme") })
                }
            }
        ) { padding ->
            Box(Modifier.padding(padding).fillMaxSize().background(RadarBg)) {
                when (val s = state) {
                    UiState.Loading -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                    is UiState.Error -> ErrorView(s.message) { vm.refresh() }
                    is UiState.Ready -> when (tab) {
                        0 -> DashboardScreen(s.data, budget, onEditBudget = { budgetDialog = true })
                        1 -> RadarScreen(s.data.items, holdingIds, vm::markBought)
                        2 -> PortfolioScreen(s.data.items.filter { it.id in holdingIds }, vm::removeHolding)
                        else -> AlertsScreen((vm.localAlerts() + s.data.alerts).distinctBy { it.id })
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
}

@Composable
private fun DashboardScreen(data: DashboardData, budget: Int, onEditBudget: () -> Unit) {
    val context = LocalContext.current
    val plannerItems = data.items.map { it.toPlannerItem() }
    val allocations = InvestmentPlanner.plan(plannerItems, budget).associate { it.id to it.amount }
    val top = data.items
        .filter { InvestmentPlanner.recommendation(it.toPlannerItem()).label == "KAUFEN" }
        .maxByOrNull { InvestmentPlanner.recommendation(it.toPlannerItem()).score }
        ?: data.items.firstOrNull { it.id == data.topPickId }
        ?: data.items.firstOrNull()

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 14.dp),
        contentPadding = PaddingValues(bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item { Spacer(Modifier.height(2.dp)) }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DarkMetricCard("MARKT", data.marketLight.uppercase(), marketAccent(data.marketLight), Modifier.weight(1f))
                DarkMetricCard("BUDGET", "$budget €", RadarBlue, Modifier.weight(1f), onClick = onEditBudget)
                DarkMetricCard("SIGNAL", if (top != null) "AKTIV" else "WARTEN", RadarGreen, Modifier.weight(1f))
            }
        }

        if (top != null) item {
            val reco = InvestmentPlanner.recommendation(top.toPlannerItem())
            val amount = allocations[top.id] ?: 0
            Text("HEUTIGE EMPFEHLUNG", style = MaterialTheme.typography.labelLarge, color = RadarMuted, fontWeight = FontWeight.Bold)
            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = Color.Transparent)
            ) {
                Column(
                    Modifier
                        .background(Brush.linearGradient(listOf(Color(0xFF133326), Color(0xFF112A3D))))
                        .padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(9.dp)
                ) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(reco.label, color = recommendationColor(reco.label), fontWeight = FontWeight.Black, style = MaterialTheme.typography.titleMedium)
                            Text(top.name, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black)
                            Text("${top.ticker} · ${top.type} · Risiko ${top.risk}/5", color = RadarMuted)
                        }
                        ScoreRing(reco.score)
                    }
                    Text(if (amount > 0) "$amount € einplanen" else "Heute kein Neukauf", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black, color = RadarGreen)
                    Text(reco.reason, color = RadarText)
                    Text(priceLine(top), color = RadarMuted)
                    Button(
                        onClick = { openInvestment(context, top) },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = RadarGreen, contentColor = Color(0xFF05150E))
                    ) {
                        Icon(Icons.Default.OpenInNew, null)
                        Spacer(Modifier.width(8.dp))
                        Text("Wertpapier öffnen", fontWeight = FontWeight.Bold)
                    }
                }
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
            RecommendationRow(item, allocations[item.id] ?: 0) { openInvestment(context, item) }
        }

        item {
            Text(
                "Der Kaufplan verwendet die Live-Signale der App. Werte ohne KAUFEN-Signal erhalten kein neues Budget. Keine automatische Order.",
                style = MaterialTheme.typography.bodySmall,
                color = RadarMuted,
                modifier = Modifier.padding(top = 6.dp)
            )
        }
    }
}

@Composable
private fun RadarScreen(items: List<InvestmentItem>, holdingIds: Set<String>, onBought: (String) -> Unit) {
    val context = LocalContext.current
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 14.dp),
        contentPadding = PaddingValues(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Text("RADAR", style = MaterialTheme.typography.labelLarge, color = RadarMuted, fontWeight = FontWeight.Bold)
            Text("Klare Signale für Aktien & ETFs", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
        }
        items(items.sortedByDescending { InvestmentPlanner.recommendation(it.toPlannerItem()).score }) { item ->
            val reco = InvestmentPlanner.recommendation(item.toPlannerItem())
            Card(colors = CardDefaults.cardColors(containerColor = RadarSurface), shape = RoundedCornerShape(20.dp)) {
                Column(Modifier.padding(15.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(item.name, fontWeight = FontWeight.Black, style = MaterialTheme.typography.titleMedium)
                            Text("${item.ticker} · ${item.isin}", color = RadarMuted, style = MaterialTheme.typography.bodySmall)
                        }
                        StatusPill(reco.label)
                    }
                    Text(priceLine(item), color = RadarMuted)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("Score ${reco.score}/100", fontWeight = FontWeight.Bold, color = recommendationColor(reco.label))
                        Text("Risiko ${item.risk}/5", color = RadarMuted)
                    }
                    Text(reco.reason, style = MaterialTheme.typography.bodySmall)
                    OutlinedButton(onClick = { openInvestment(context, item) }, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Default.OpenInNew, null)
                        Spacer(Modifier.width(7.dp))
                        Text("Wertpapier öffnen")
                    }
                    if (item.id in holdingIds) {
                        Text("✓ Im Portfolio · Verkaufsalarm aktiv", color = RadarGreen, fontWeight = FontWeight.Bold)
                    } else {
                        Button(onClick = { onBought(item.id) }, modifier = Modifier.fillMaxWidth()) { Text("Als gekauft markieren") }
                    }
                }
            }
        }
    }
}

@Composable
private fun PortfolioScreen(items: List<InvestmentItem>, onRemove: (String) -> Unit) {
    val context = LocalContext.current
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 14.dp),
        contentPadding = PaddingValues(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Text("PORTFOLIO", style = MaterialTheme.typography.labelLarge, color = RadarMuted, fontWeight = FontWeight.Bold)
            Text("Meine Positionen", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
            if (items.isEmpty()) Text("Noch keine Position markiert. Öffne den Radar und markiere gekaufte Werte.", color = RadarMuted)
        }
        items(items) { item ->
            val reco = InvestmentPlanner.recommendation(item.toPlannerItem())
            Card(colors = CardDefaults.cardColors(containerColor = RadarSurface), shape = RoundedCornerShape(20.dp)) {
                Column(Modifier.padding(15.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text(item.name, fontWeight = FontWeight.Black, modifier = Modifier.weight(1f))
                        StatusPill(reco.label)
                    }
                    Text(priceLine(item), color = RadarMuted)
                    Text("Score ${reco.score}/100 · Risiko ${item.risk}/5", color = recommendationColor(reco.label), fontWeight = FontWeight.Bold)
                    Text(reco.reason, style = MaterialTheme.typography.bodySmall)
                    OutlinedButton(onClick = { openInvestment(context, item) }, modifier = Modifier.fillMaxWidth()) { Text("Wertpapier öffnen") }
                    OutlinedButton(onClick = { onRemove(item.id) }, modifier = Modifier.fillMaxWidth()) { Text("Aus Portfolio entfernen") }
                }
            }
        }
    }
}

@Composable
private fun AlertsScreen(alerts: List<SignalAlert>) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 14.dp),
        contentPadding = PaddingValues(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Text("ALARME", style = MaterialTheme.typography.labelLarge, color = RadarMuted, fontWeight = FontWeight.Bold)
            Text("Handlungsbedarf", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
            if (alerts.isEmpty()) Text("Aktuell kein Verkaufs- oder Prüfsignal.", color = RadarMuted)
        }
        items(alerts) { alert ->
            Card(colors = CardDefaults.cardColors(containerColor = alertDarkColor(alert.level)), shape = RoundedCornerShape(18.dp)) {
                Column(Modifier.padding(15.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(alert.title, fontWeight = FontWeight.Black)
                    Text(alert.message)
                    Text(alert.createdAt, style = MaterialTheme.typography.bodySmall, color = RadarMuted)
                }
            }
        }
    }
}

@Composable
private fun RecommendationRow(item: InvestmentItem, amount: Int, onOpen: () -> Unit) {
    val reco = InvestmentPlanner.recommendation(item.toPlannerItem())
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onOpen),
        colors = CardDefaults.cardColors(containerColor = RadarSurface),
        shape = RoundedCornerShape(18.dp)
    ) {
        Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(item.name, fontWeight = FontWeight.Black)
                Text("${item.ticker} · Score ${reco.score}/100 · Risiko ${item.risk}/5", color = RadarMuted, style = MaterialTheme.typography.bodySmall)
                Text(reco.label, color = recommendationColor(reco.label), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelLarge)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(if (amount > 0) "$amount €" else "0 €", fontWeight = FontWeight.Black, style = MaterialTheme.typography.titleMedium, color = if (amount > 0) RadarGreen else RadarMuted)
                Icon(Icons.Default.OpenInNew, null, tint = RadarMuted, modifier = Modifier.size(18.dp))
            }
        }
    }
}

@Composable
private fun DarkMetricCard(label: String, value: String, accent: Color, modifier: Modifier = Modifier, onClick: (() -> Unit)? = null) {
    val clickModifier = if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier
    Card(modifier.then(clickModifier), colors = CardDefaults.cardColors(containerColor = RadarSurface), shape = RoundedCornerShape(16.dp)) {
        Column(Modifier.fillMaxWidth().padding(vertical = 13.dp, horizontal = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = RadarMuted)
            Text(value, fontWeight = FontWeight.Black, color = accent)
        }
    }
}

@Composable
private fun ScoreRing(score: Int) {
    Surface(color = Color(0x221FFFFFF), shape = RoundedCornerShape(50)) {
        Column(Modifier.padding(horizontal = 13.dp, vertical = 9.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(score.toString(), fontWeight = FontWeight.Black, style = MaterialTheme.typography.titleLarge, color = RadarGreen)
            Text("/100", color = RadarMuted, style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
private fun StatusPill(status: String) {
    Surface(color = recommendationColor(status).copy(alpha = 0.16f), shape = RoundedCornerShape(50)) {
        Text(status, Modifier.padding(horizontal = 10.dp, vertical = 5.dp), color = recommendationColor(status), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Black)
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
    Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        Text("Keine Live-Verbindung", fontWeight = FontWeight.Black, style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(8.dp))
        Text(message, color = RadarMuted)
        Spacer(Modifier.height(16.dp))
        Button(onClick = retry) { Text("Erneut versuchen") }
    }
}

private fun InvestmentItem.toPlannerItem() = PlannerItem(
    id = id,
    type = type,
    status = status,
    baseAllocation = allocation,
    risk = risk,
    percentChange = percentChange
)

private fun openInvestment(context: android.content.Context, item: InvestmentItem) {
    val query = Uri.encode("Trade Republic ${item.isin} ${item.name}")
    val url = "https://www.google.com/search?q=$query"
    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
}

private fun priceLine(item: InvestmentItem): String {
    val price = item.price?.let { String.format(Locale.GERMANY, "%.2f", it) } ?: "–"
    val change = item.percentChange?.let { String.format(Locale.GERMANY, "%+.2f%%", it) } ?: "–"
    return "Kurs $price ${item.currency} · Heute $change"
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
