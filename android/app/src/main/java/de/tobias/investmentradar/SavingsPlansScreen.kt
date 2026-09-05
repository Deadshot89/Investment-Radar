package de.tobias.investmentradar

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun SavingsPlansScreen(
    items: List<InvestmentItem>,
    onBack: () -> Unit,
    vm: MainViewModel
) {
    val context = LocalContext.current
    var plans by remember { mutableStateOf(emptyList<SavingsPlan>()) }
    var executions by remember { mutableStateOf(emptyList<SavingsPlanExecution>()) }
    var editingPlan by remember { mutableStateOf<SavingsPlan?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    val today = remember { SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date()) }
    val itemById = remember(items) { items.associateBy { it.id } }

    fun reload() {
        SavingsPlanStore.ensureSeeded(context)
        SavingsPlanStore.ensureDueExecutions(context, today)
        plans = SavingsPlanStore.readPlans(context).sortedBy { it.name }
        executions = SavingsPlanStore.readExecutions(context).sortedByDescending { it.scheduledDate }
    }

    fun advance(plan: SavingsPlan, scheduledDate: String) {
        if (!plan.scheduleConfigured) return
        val next = SavingsPlanSchedule.nextDueDate(plan, scheduledDate)
        SavingsPlanStore.upsertPlan(context, plan.copy(nextDueDate = next))
    }

    LaunchedEffect(Unit) { reload() }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 14.dp),
        contentPadding = PaddingValues(bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column(Modifier.weight(1f)) {
                    Text("Sparpläne", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black)
                    Text("Trade-Republic-Pläne getrennt vom Depot verwalten", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                TextButton(onClick = onBack) { Text("Zurück") }
            }
        }

        item {
            SavingsPlanCard {
                Text("Monatlich geplant", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                val monthly = plans.sumOf { plan ->
                    when (plan.frequency) {
                        SavingsPlanFrequency.MONTHLY -> plan.amountEur
                        SavingsPlanFrequency.TWICE_MONTHLY -> plan.amountEur * 2.0
                    }
                }
                Text(String.format(Locale.GERMANY, "%.2f €", monthly), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Black)
                Text("Geplante Beträge verändern dein Depot nicht. Erst eine bestätigte Ausführung erzeugt einen Kauf.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        items(plans, key = { it.id }) { plan ->
            val planExecutions = executions.filter { it.planId == plan.id }
            val pending = planExecutions.firstOrNull { it.status == SavingsPlanExecutionStatus.PENDING }
            SavingsPlanCard {
                Text(plan.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black)
                Text(
                    when (plan.frequency) {
                        SavingsPlanFrequency.MONTHLY -> String.format(Locale.GERMANY, "%.2f € · monatlich", plan.amountEur)
                        SavingsPlanFrequency.TWICE_MONTHLY -> String.format(Locale.GERMANY, "%.2f € · zweimal im Monat", plan.amountEur)
                    },
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                HorizontalDivider()
                Text(
                    "Ausführungstage: " + when {
                        plan.dayOfMonth1 == null -> "noch nicht festgelegt"
                        plan.dayOfMonth2 == null -> "${plan.dayOfMonth1}."
                        else -> "${plan.dayOfMonth1}. und ${plan.dayOfMonth2}."
                    },
                    fontWeight = FontWeight.Bold
                )
                Text("Nächste Ausführung: ${plan.nextDueDate ?: "erst nach Festlegung der Tage"}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedButton(onClick = { editingPlan = plan }, modifier = Modifier.fillMaxWidth()) {
                    Text("Ausführungstage bearbeiten")
                }

                if (plan.itemId == null) {
                    Text(
                        "Instrument noch nicht eindeutig zugeordnet. Dieser Private-Equity-Sparplan wird nicht automatisch deinem Depot zugerechnet.",
                        color = MaterialTheme.colorScheme.error,
                        fontWeight = FontWeight.Bold
                    )
                }

                if (pending != null) {
                    Text("Fällige Ausführung · ${pending.scheduledDate}", fontWeight = FontWeight.Black)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            enabled = plan.itemId != null,
                            modifier = Modifier.weight(1f),
                            onClick = {
                                val service = SavingsPlanExecutionService(
                                    quoteProvider = object : SavingsPlanQuoteProvider {
                                        override fun currentPriceEur(itemId: String): Double? {
                                            val item = itemById[itemId] ?: return null
                                            return item.priceEur ?: item.price
                                        }
                                    },
                                    portfolioWriter = object : SavingsPlanPortfolioWriter {
                                        override fun persistPurchase(itemId: String, purchase: PortfolioPurchase): Boolean =
                                            vm.upsertPurchase(itemId, purchase)
                                    },
                                    executionRepository = object : SavingsPlanExecutionRepository {
                                        override fun getExecution(id: String): SavingsPlanExecution? =
                                            SavingsPlanStore.readExecutions(context).firstOrNull { it.id == id }

                                        override fun saveExecution(execution: SavingsPlanExecution) {
                                            SavingsPlanStore.upsertExecution(context, execution)
                                        }
                                    }
                                )
                                when (val result = service.confirm(plan, pending.id, today)) {
                                    is SavingsPlanConfirmationResult.Confirmed -> {
                                        advance(plan, pending.scheduledDate)
                                        message = String.format(Locale.GERMANY, "Ausführung gebucht: %.6f Stück zu %.2f €", result.shares, result.priceEur)
                                    }
                                    is SavingsPlanConfirmationResult.AlreadyConfirmed -> message = "Diese Ausführung wurde bereits gebucht."
                                    SavingsPlanConfirmationResult.InstrumentMissing -> message = "Instrument ist noch nicht eindeutig zugeordnet."
                                    SavingsPlanConfirmationResult.PriceUnavailable -> message = "Aktueller Kurs ist nicht verfügbar."
                                    SavingsPlanConfirmationResult.PurchasePersistenceFailed -> message = "Der Kauf konnte nicht im Depot gespeichert werden."
                                    SavingsPlanConfirmationResult.ExecutionMissing -> message = "Die Ausführung ist nicht mehr offen."
                                }
                                reload()
                            }
                        ) { Text("Ausgeführt") }
                        OutlinedButton(
                            modifier = Modifier.weight(1f),
                            onClick = {
                                val repo = object : SavingsPlanExecutionRepository {
                                    override fun getExecution(id: String): SavingsPlanExecution? =
                                        SavingsPlanStore.readExecutions(context).firstOrNull { it.id == id }
                                    override fun saveExecution(execution: SavingsPlanExecution) {
                                        SavingsPlanStore.upsertExecution(context, execution)
                                    }
                                }
                                val service = SavingsPlanExecutionService(
                                    quoteProvider = object : SavingsPlanQuoteProvider { override fun currentPriceEur(itemId: String): Double? = null },
                                    portfolioWriter = object : SavingsPlanPortfolioWriter { override fun persistPurchase(itemId: String, purchase: PortfolioPurchase): Boolean = false },
                                    executionRepository = repo
                                )
                                if (service.skip(pending.id)) {
                                    advance(plan, pending.scheduledDate)
                                    message = "Ausführung als nicht ausgeführt gespeichert."
                                }
                                reload()
                            }
                        ) { Text("Nicht ausgeführt") }
                    }
                }

                val last = planExecutions.firstOrNull { it.status != SavingsPlanExecutionStatus.PENDING }
                if (last != null) {
                    Text(
                        "Letzter Status: ${when (last.status) {
                            SavingsPlanExecutionStatus.CONFIRMED -> "ausgeführt"
                            SavingsPlanExecutionStatus.SKIPPED -> "nicht ausgeführt"
                            SavingsPlanExecutionStatus.PENDING -> "offen"
                        }} · ${last.scheduledDate}",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }

    editingPlan?.let { plan ->
        var day1 by remember(plan.id) { mutableStateOf(plan.dayOfMonth1?.toString().orEmpty()) }
        var day2 by remember(plan.id) { mutableStateOf(plan.dayOfMonth2?.toString().orEmpty()) }
        val parsed1 = day1.toIntOrNull()?.takeIf { it in 1..31 }
        val parsed2 = day2.toIntOrNull()?.takeIf { it in 1..31 }
        val valid = when (plan.frequency) {
            SavingsPlanFrequency.MONTHLY -> parsed1 != null
            SavingsPlanFrequency.TWICE_MONTHLY -> parsed1 != null && parsed2 != null && parsed1 != parsed2
        }
        AlertDialog(
            onDismissRequest = { editingPlan = null },
            title = { Text("Ausführungstage") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(plan.name, fontWeight = FontWeight.Bold)
                    OutlinedTextField(value = day1, onValueChange = { day1 = it.filter(Char::isDigit).take(2) }, label = { Text("Tag 1 (1–31)") }, singleLine = true)
                    if (plan.frequency == SavingsPlanFrequency.TWICE_MONTHLY) {
                        OutlinedTextField(value = day2, onValueChange = { day2 = it.filter(Char::isDigit).take(2) }, label = { Text("Tag 2 (1–31)") }, singleLine = true)
                    }
                    Text("Die Tage kannst du aus Trade Republic übernehmen. Wir leiten sie nicht aus dem Countdown ab.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            },
            confirmButton = {
                Button(enabled = valid, onClick = {
                    val configured = plan.copy(
                        dayOfMonth1 = parsed1,
                        dayOfMonth2 = if (plan.frequency == SavingsPlanFrequency.TWICE_MONTHLY) parsed2 else null,
                        nextDueDate = null
                    )
                    val next = SavingsPlanSchedule.nextDueDate(configured, today)
                    SavingsPlanStore.upsertPlan(context, configured.copy(nextDueDate = next))
                    editingPlan = null
                    reload()
                }) { Text("Speichern") }
            },
            dismissButton = { TextButton(onClick = { editingPlan = null }) { Text("Abbrechen") } }
        )
    }

    message?.let { value ->
        AlertDialog(
            onDismissRequest = { message = null },
            title = { Text("Sparplan") },
            text = { Text(value) },
            confirmButton = { TextButton(onClick = { message = null }) { Text("OK") } }
        )
    }
}

@Composable
private fun SavingsPlanCard(content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            content = content
        )
    }
}
