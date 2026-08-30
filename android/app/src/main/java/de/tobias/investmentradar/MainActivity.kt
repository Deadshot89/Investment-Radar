package de.tobias.investmentradar

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.ShowChart
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.firebase.messaging.FirebaseMessaging
import java.util.Locale

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (FirebaseBootstrap.isConfigured()) {
            FirebaseMessaging.getInstance().subscribeToTopic("investment-alerts")
        }
        setContent { InvestmentRadarUi() }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InvestmentRadarUi(vm: MainViewModel = viewModel()) {
    val state by vm.state.collectAsState()
    val context = LocalContext.current
    var tab by remember { mutableIntStateOf(0) }
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
        colorScheme = lightColorScheme(
            primary = Color(0xFF173F72),
            secondary = Color(0xFF2D7D4A),
            background = Color(0xFFF6F7FB),
            surface = Color.White
        )
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Investment Radar", fontWeight = FontWeight.Bold) },
                    actions = {
                        IconButton(onClick = { vm.refresh() }) {
                            Icon(Icons.Default.Refresh, contentDescription = "Aktualisieren")
                        }
                    }
                )
            },
            bottomBar = {
                NavigationBar {
                    NavigationBarItem(selected = tab == 0, onClick = { tab = 0 }, icon = { Icon(Icons.Default.ShowChart, null) }, label = { Text("Live") })
                    NavigationBarItem(selected = tab == 1, onClick = { tab = 1 }, icon = { Icon(Icons.Default.Search, null) }, label = { Text("Radar") })
                    NavigationBarItem(selected = tab == 2, onClick = { tab = 2 }, icon = { Icon(Icons.Default.Notifications, null) }, label = { Text("Alarme") })
                }
            }
        ) { padding ->
            Box(Modifier.padding(padding).fillMaxSize()) {
                when (val s = state) {
                    UiState.Loading -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                    is UiState.Error -> ErrorView(s.message) { vm.refresh() }
                    is UiState.Ready -> when (tab) {
                        0 -> DashboardScreen(s.data)
                        1 -> RadarScreen(s.data.items)
                        else -> AlertsScreen((vm.localAlerts() + s.data.alerts).distinctBy { it.id })
                    }
                }
            }
        }
    }
}

@Composable
private fun DashboardScreen(data: DashboardData) {
    val top = data.items.firstOrNull { it.id == data.topPickId } ?: data.items.firstOrNull()
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 14.dp),
        contentPadding = PaddingValues(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item { Spacer(Modifier.height(2.dp)) }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatusCard("Markt", data.marketLight, marketColor(data.marketLight), Modifier.weight(1f))
                StatusCard("Budget", "${data.budget} €", Color(0xFFD6F2DE), Modifier.weight(1f))
                StatusCard("Push", if (FirebaseBootstrap.isConfigured()) "AKTIV" else "SETUP", if (FirebaseBootstrap.isConfigured()) Color(0xFFD6F2DE) else Color(0xFFFFEFA6), Modifier.weight(1f))
            }
        }
        if (top != null) item {
            SectionTitle("🏆 Beste Wahl heute")
            Card(colors = CardDefaults.cardColors(containerColor = Color(0xFFD6F2DE)), shape = RoundedCornerShape(18.dp)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text(top.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text("${top.type} · ${top.ticker} · Risiko ${top.risk}/5")
                    Text("${top.allocation} € · ${top.status}", style = MaterialTheme.typography.titleMedium, color = Color(0xFF176B37), fontWeight = FontWeight.Bold)
                    Text(priceLine(top), fontWeight = FontWeight.SemiBold)
                    Text("Trade Republic: ${top.tradeRepublicName}")
                    Text("ISIN: ${top.isin}", fontWeight = FontWeight.Bold)
                }
            }
        }
        item { SectionTitle("💶 Dein 100-€-Kaufplan") }
        items(data.items.filter { it.allocation > 0 }) { item -> AllocationRow(item) }
        item {
            Text(
                "Keine automatische Order. Verkaufssignale sind Warnungen zur eigenen Entscheidung.",
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFF666666),
                modifier = Modifier.padding(top = 4.dp)
            )
        }
    }
}

@Composable
private fun RadarScreen(items: List<InvestmentItem>) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 14.dp),
        contentPadding = PaddingValues(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item { SectionTitle("Aktien + ETFs") }
        items(items) { item ->
            Card(shape = RoundedCornerShape(16.dp)) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(item.name, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                        StatusPill(item.status)
                    }
                    Text("${item.type} · ${item.ticker} · Risiko ${item.risk}/5")
                    Text(priceLine(item), fontWeight = FontWeight.SemiBold)
                    HorizontalDivider(Modifier.padding(vertical = 3.dp))
                    Text("Trade Republic: ${item.tradeRepublicName}")
                    Text("ISIN: ${item.isin}", fontWeight = FontWeight.Bold)
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
            SectionTitle("🔔 Alarmverlauf")
            if (alerts.isEmpty()) Text("Noch kein Verkaufs- oder Pruefsignal. Das ist ein gutes Zeichen.")
        }
        items(alerts) { alert ->
            Card(colors = CardDefaults.cardColors(containerColor = alertColor(alert.level)), shape = RoundedCornerShape(16.dp)) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(alert.title, fontWeight = FontWeight.Bold)
                    Text(alert.message)
                    Text(alert.createdAt, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

@Composable
private fun AllocationRow(item: InvestmentItem) {
    Card(shape = RoundedCornerShape(14.dp)) {
        Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(item.name, fontWeight = FontWeight.Bold)
                Text(priceLine(item), style = MaterialTheme.typography.bodySmall)
            }
            Text("${item.allocation} €", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
        }
    }
}

@Composable
private fun StatusCard(label: String, value: String, color: Color, modifier: Modifier = Modifier) {
    Card(modifier, colors = CardDefaults.cardColors(containerColor = color), shape = RoundedCornerShape(14.dp)) {
        Column(Modifier.fillMaxWidth().padding(vertical = 12.dp, horizontal = 6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(label, style = MaterialTheme.typography.labelMedium)
            Text(value, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun StatusPill(status: String) {
    val c = when (status.uppercase()) {
        "KAUFEN" -> Color(0xFFD6F2DE)
        "BEOBACHTEN" -> Color(0xFFFFEFA6)
        else -> Color(0xFFFFD9D7)
    }
    Surface(color = c, shape = RoundedCornerShape(50)) {
        Text(status, Modifier.padding(horizontal = 9.dp, vertical = 4.dp), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun SectionTitle(text: String) = Text(text, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 4.dp))

@Composable
private fun ErrorView(message: String, retry: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        Text("Keine Live-Verbindung", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(8.dp))
        Text(message)
        Spacer(Modifier.height(16.dp))
        Button(onClick = retry) { Text("Erneut versuchen") }
    }
}

private fun priceLine(item: InvestmentItem): String {
    val price = item.price?.let { String.format(Locale.GERMANY, "%.2f", it) } ?: "–"
    val change = item.percentChange?.let { String.format(Locale.GERMANY, "%+.2f%%", it) } ?: "–"
    return "Kurs: $price ${item.currency} · Heute: $change"
}

private fun marketColor(light: String) = when (light.uppercase()) {
    "GRÜN", "GRUEN", "GREEN" -> Color(0xFFD6F2DE)
    "ROT", "RED" -> Color(0xFFFFD9D7)
    else -> Color(0xFFFFEFA6)
}

private fun alertColor(level: String) = when (level.uppercase()) {
    "SELL" -> Color(0xFFFFD9D7)
    "REVIEW" -> Color(0xFFFFE8BF)
    "BUY" -> Color(0xFFD6F2DE)
    else -> Color(0xFFE2EEFC)
}
