from pathlib import Path

path = Path('android/app/src/main/java/de/tobias/investmentradar/MainActivity.kt')
text = path.read_text()

replacements = []
replacements.append(('''    var budgetDialog by remember { mutableStateOf(false) }
    var investmentDialogItem by remember { mutableStateOf<InvestmentItem?>(null) }
''', '''    var budgetDialog by remember { mutableStateOf(false) }
    var moneyActionCenter by remember {
        mutableStateOf(
            DepotActionCenterState(
                summary = DepotActionCenterSummary(urgentActions = 0, plannedBuyEur = 0.0, cashEur = 0.0),
                items = emptyList()
            )
        )
    }
    var moneyActionItemsById by remember { mutableStateOf<Map<String, InvestmentItem>>(emptyMap()) }
    var investmentDialogItem by remember { mutableStateOf<InvestmentItem?>(null) }
'''))
replacements.append(('''                        val advisorPlan = PortfolioAdvisorEngine.allocate(advisorCandidates, budgetState.advisorBudgetEur)
                        val advisorById = advisorPlan.candidates.associateBy { it.itemId }
                        val itemsById = s.data.items.associateBy { it.id }
                        val detailId = selectedDetailId
''', '''                        val advisorPlan = PortfolioAdvisorEngine.allocate(advisorCandidates, budgetState.advisorBudgetEur)
                        val advisorById = advisorPlan.candidates.associateBy { it.itemId }
                        val itemsById = s.data.items.associateBy { it.id }
                        val liveActionPlan = ActionPlanEngine.build(
                            analysisDay = s.data.generatedAt.take(10).ifBlank { "current" },
                            advisorPlan = advisorPlan
                        ).copy(availableBudgetEur = budgetState.availableEur)
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
'''))
replacements.append(('''        BudgetDialog(
            current = budgetState,
            onDismiss = { budgetDialog = false },
            onSaveMonthly = vm::setMonthlyBudget,
            onAddExtra = { amount -> vm.addExtraFunding(amount) },
            onAdjustment = { amount, credit, note -> vm.addBudgetAdjustment(amount, credit, note) }
        )
''', '''        BudgetDialog(
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
                            investmentDialogEntryType = "BUY"
                            investmentDialogItem = item
                        } else {
                            missingAlertItemMessage = "Das Wertpapier ist nicht im aktuellen Radar enthalten."
                        }
                    }
                    ActionType.SELL, ActionType.REDUCE -> {
                        val item = moneyActionItemsById[action.instrumentId]
                        if (item != null) {
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
'''))
replacements.append(('''private fun BudgetDialog(
    current: InvestmentBudgetViewState,
    onDismiss: () -> Unit,
    onSaveMonthly: (Double) -> Boolean,
    onAddExtra: (Double) -> Boolean,
    onAdjustment: (Double, Boolean, String) -> Boolean
) {
''', '''private fun BudgetDialog(
    current: InvestmentBudgetViewState,
    actionCenter: DepotActionCenterState,
    onDismiss: () -> Unit,
    onSaveMonthly: (Double) -> Boolean,
    onAddExtra: (Double) -> Boolean,
    onAdjustment: (Double, Boolean, String) -> Boolean,
    onExecuteAction: (DepotActionCenterItem) -> Unit
) {
'''))
replacements.append(('title = { Text("Investmentbudget") },', 'title = { Text("Geldverwaltung") },'))
replacements.append(('''                Text("Budget-Cockpit · ${current.monthLabel}", color = RadarBlue, fontWeight = FontWeight.Black)
                NeonStatStrip(
                    entries = listOf(
                        "Monatsbudget" to formatMoney(current.monthlyBudgetEur),
                        "Rest Vormonate" to formatMoney(current.carryoverEur),
                        "Zusätzlich" to formatMoney(current.extraFundingEur),
                        "Depot-Einstand" to formatMoney(current.investedEur),
                        "Kontostand" to formatMoney(current.cashBalanceEur),
                        "Reserviert" to formatMoney(current.reservedEur),
                        "Verfügbar" to formatMoney(current.availableEur)
                    ),
                    accent = RadarBlue
                )
''', '''                Text("GELDVERWALTUNG · ${current.monthLabel}", color = RadarBlue, fontWeight = FontWeight.Black)
                Text("Verfügbar: ${formatMoney(current.availableEur)}", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black, color = RadarGreen)
                NeonStatStrip(
                    entries = listOf(
                        "Verfügbar" to formatMoney(current.availableEur),
                        "Monatsbudget" to formatMoney(current.monthlyBudgetEur),
                        "Diesen Monat investiert" to formatMoney(current.investedEur),
                        "Zusätzlich eingezahlt" to formatMoney(current.extraFundingEur),
                        "Rest Vormonate" to formatMoney(current.carryoverEur),
                        "Kontostand" to formatMoney(current.cashBalanceEur),
                        "Reserviert" to formatMoney(current.reservedEur)
                    ),
                    accent = RadarBlue
                )
'''))
replacements.append(('''                if (current.feesEur > 0.0) {
                    Text("Diesen Monat in Transaktionen ausgewiesene Gebühren: ${formatMoney(current.feesEur)}", color = RadarYellow, style = MaterialTheme.typography.bodySmall)
                }

                HorizontalDivider(color = RadarSurface2)
                Text("Monatsbudget ändern", fontWeight = FontWeight.Black)
''', '''                if (current.feesEur > 0.0) {
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
'''))
replacements.append(('''                Text("Zusatzgeld / Wechselgeld", fontWeight = FontWeight.Black)
                Text("Zusatzgeld erhöht den Kontostand zusätzlich zum Monatsbudget. Es wird nur einmal gebucht.", color = RadarMuted, style = MaterialTheme.typography.bodySmall)
                OutlinedTextField(
''', '''                Text("Wechselgeld / zusätzliches Geld", fontWeight = FontWeight.Black)
                Text("Zusatzgeld erhöht den Kontostand zusätzlich zum Monatsbudget. Es wird nur einmal gebucht.", color = RadarMuted, style = MaterialTheme.typography.bodySmall)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    AssistChip(onClick = { message = if (onAddExtra(5.0)) "5 € Wechselgeld hinzugefügt." else "Zusatzgeld konnte nicht gespeichert werden." }, label = { Text("+ 5 €") })
                    AssistChip(onClick = { message = if (onAddExtra(10.0)) "10 € Wechselgeld hinzugefügt." else "Zusatzgeld konnte nicht gespeichert werden." }, label = { Text("+ 10 €") })
                    AssistChip(onClick = { message = if (onAddExtra(20.0)) "20 € zusätzliches Geld hinzugefügt." else "Zusatzgeld konnte nicht gespeichert werden." }, label = { Text("+ 20 €") })
                }
                OutlinedTextField(
'''))
replacements.append(('''                Text("BUDGET-HISTORIE", color = RadarCyan, fontWeight = FontWeight.Black, style = MaterialTheme.typography.labelLarge)
                Text("Hier siehst du jede budgetwirksame Veränderung und warum sich dein verfügbarer Betrag geändert hat.", color = RadarMuted, style = MaterialTheme.typography.bodySmall)
''', '''                Text("Geldverlauf", color = RadarCyan, fontWeight = FontWeight.Black, style = MaterialTheme.typography.titleMedium)
                Text("Jede Einzahlung, jeder Kauf, Verkauf und jede Korrektur erklärt nachvollziehbar, warum sich dein verfügbares Geld verändert hat.", color = RadarMuted, style = MaterialTheme.typography.bodySmall)
'''))

for index, (old, new) in enumerate(replacements, start=1):
    assert old in text, f'anchor {index} missing'
    text = text.replace(old, new, 1)

path.write_text(text)
