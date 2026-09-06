from pathlib import Path


def replace_once(path: str, old: str, new: str) -> None:
    p = Path(path)
    text = p.read_text(encoding="utf-8")
    if old not in text:
        raise SystemExit(f"missing expected block in {path}: {old[:120]!r}")
    p.write_text(text.replace(old, new, 1), encoding="utf-8")


main = "android/app/src/main/java/de/tobias/investmentradar/MainActivity.kt"
portfolio = "android/app/src/main/java/de/tobias/investmentradar/PortfolioDashboard.kt"
radar = "android/app/src/main/java/de/tobias/investmentradar/RadarScreen.kt"
detail = "android/app/src/main/java/de/tobias/investmentradar/InvestmentDetailScreen.kt"

# Root: one shared 2.3 advisor plan.
replace_once(main, '''                        val personalPlan = RecommendationEngine.plan(
                            s.data.items,
                            budget,
                            PortfolioAnalysis.values(s.data.items, positions, customItems)
                        )
                        val personalById = personalPlan.items.associateBy { it.itemId }
''', '''                        val portfolioValues = PortfolioAnalysis.values(s.data.items, positions, customItems)
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
                        val advisorPlan = PortfolioAdvisorEngine.allocate(advisorCandidates, budget)
                        val advisorById = advisorPlan.candidates.associateBy { it.itemId }
''')
replace_once(main, '''                                personalRecommendation = personalById[detailId],
                                isWatchlisted = detailId in watchlistIds,
''', '''                                advisorCandidate = advisorById[detailId],
                                advisorHistory = AdvisorStore.history(context, detailId),
                                isWatchlisted = detailId in watchlistIds,
''')
replace_once(main, '''                                personalPlan = personalPlan,
''', '''                                advisorPlan = advisorPlan,
''')
replace_once(main, '''                                personalById = personalById,
                                onToggleWatchlist = vm::toggleWatchlist,
''', '''                                advisorById = advisorById,
                                onToggleWatchlist = vm::toggleWatchlist,
''')
replace_once(main, '''                                personalById = personalById,
                                showSavingsPlans = showSavingsPlans,
''', '''                                advisorPlan = advisorPlan,
                                showSavingsPlans = showSavingsPlans,
''')

# DashboardScreen uses the same plan; portfolio weight is not an action gate.
replace_once(main, '''    personalPlan: PersonalPlan,
''', '''    advisorPlan: PortfolioAdvisorPlan,
''')
replace_once(main, '''    val cashAmount = personalPlan.cashAmount
    val personalById = personalPlan.items.associateBy { it.itemId }
    val allocations = personalPlan.items.associate { it.itemId to it.allocationEur }
''', '''    val cashAmount = advisorPlan.cashEur
    val advisorById = advisorPlan.candidates.associateBy { it.itemId }
    val allocations = advisorPlan.allocations.associate { it.itemId to it.amountEur }
''')
replace_once(main, '''    val concentrationWarning = personalPlan.items.maxByOrNull { it.currentWeightPct }
        ?.takeIf { it.currentWeightPct >= 40.0 }
        ?.let { row -> data.items.firstOrNull { it.id == row.itemId }?.let { it to row.currentWeightPct } }
''', '''    val concentrationWarning: Pair<InvestmentItem, Double>? = null
''')
replace_once(main, '''            val personal = personalById[top.id]
            val amount = personal?.allocationEur ?: 0
''', '''            val advisor = advisorById[top.id]
            val amount = allocations[top.id] ?: 0
''')
replace_once(main, '''                        "Depotanteil" to personal?.currentWeightPct?.let { String.format(Locale.GERMANY, "%.1f %%", it) }.orEmpty().ifBlank { "–" }
''', '''                        "Konfidenz" to advisor?.advisor?.confidencePct?.let { "$it %" }.orEmpty().ifBlank { "–" }
''')
replace_once(main, '''                    if (amount > 0) "Diesen Monat $amount € investieren" else personal?.explanation ?: "DIESEN MONAT WARTEN",
''', '''                    if (amount > 0) "Diesen Monat $amount € investieren" else advisor?.advisor?.reasons?.firstOrNull() ?: "DIESEN MONAT WARTEN",
''')
replace_once(main, '''                Text(personal?.explanation ?: RecommendationPresentation.topReasons(top).joinToString(" · ").ifBlank { "Analyse liegt vor." }, color = RadarText)
''', '''                Text(advisor?.advisor?.reasons?.joinToString(" · ") ?: RecommendationPresentation.topReasons(top).joinToString(" · ").ifBlank { "Analyse liegt vor." }, color = RadarText)
''')
replace_once(main, '''            RecommendationRow(item, personalById[item.id], positions[item.id]) { TradeRepublicNavigator.open(context, item) }
''', '''            RecommendationRow(item, null, positions[item.id]) { TradeRepublicNavigator.open(context, item) }
''')
replace_once(main, '''                "Nur objektive BUY-Signale erhalten neues Budget. Depotkonzentration und Risiko können die persönliche Zuteilung reduzieren oder blockieren. Keine automatische Order.",
''', '''                "Nur belastbare Beratersignale erhalten neues Budget. Depotgewicht bleibt Information und bestimmt nicht die Handlung. Keine automatische Order.",
''')
replace_once(main, '''                Text("Qualität, Bewertung, Wachstum, Momentum, Risiko und deine aktuelle Depotgewichtung fließen zusammen.", color = RadarMuted)
''', '''                Text("Qualität, Bewertung, Wachstum, Momentum, Risiko und Datenqualität bestimmen das Signal. Depotgewicht bleibt davon getrennt.", color = RadarMuted)
''')

# Portfolio dashboard: actionable 2.3 plan card and per-position advisor signal.
replace_once(portfolio, '''    personalById: Map<String, PersonalRecommendation>,
''', '''    advisorPlan: PortfolioAdvisorPlan,
''')
replace_once(portfolio, '''    val itemById = remember(items) { items.associateBy { it.id } }
''', '''    val itemById = remember(items) { items.associateBy { it.id } }
    val advisorById = remember(advisorPlan) { advisorPlan.candidates.associateBy { it.itemId } }
    val allocationById = remember(advisorPlan) { advisorPlan.allocations.associateBy { it.itemId } }
''')
replace_once(portfolio, '''        item {
            PortfolioDashboardCard {
                Text(
                    if (metrics.currentValueComplete) "Depotwert" else "Teilwert",
''', '''        item {
            PortfolioDashboardCard {
                Text("Was soll ich jetzt tun?", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
                val invested = advisorPlan.allocations.sumOf { it.amountEur }
                val shifted = advisorPlan.reallocations.sumOf { it.amountEur }
                Text("$invested € investieren · $shifted € Umschichten · ${advisorPlan.cashEur} € Cash halten", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                advisorPlan.allocations.take(4).forEach { allocation ->
                    val name = itemById[allocation.itemId]?.name ?: customById[allocation.itemId]?.name ?: allocation.itemId
                    PortfolioDashboardValue(name, "+${allocation.amountEur} € · ${portfolioAdvisorActionLabel(allocation.action)}")
                }
                advisorPlan.reallocations.take(3).forEach { suggestion ->
                    val from = itemById[suggestion.fromItemId]?.name ?: customById[suggestion.fromItemId]?.name ?: suggestion.fromItemId
                    val to = itemById[suggestion.toItemId]?.name ?: customById[suggestion.toItemId]?.name ?: suggestion.toItemId
                    Text("Umschichten: ${suggestion.amountEur} € · $from → $to", style = MaterialTheme.typography.bodySmall)
                }
                advisorPlan.savingsPlanConflicts.take(3).forEach { conflict ->
                    val name = itemById[conflict.itemId]?.name ?: customById[conflict.itemId]?.name ?: conflict.itemId
                    Text("Sparplan prüfen: $name · ${conflict.monthlySavingsEur} € · ${portfolioAdvisorActionLabel(conflict.action)}", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
                if (advisorPlan.allocations.isEmpty()) Text("Für neues Kapital ist aktuell Cash halten die belastbarere Entscheidung.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        item {
            PortfolioDashboardCard {
                Text(
                    if (metrics.currentValueComplete) "Depotwert" else "Teilwert",
''')
replace_once(portfolio, '''            val personal = personalById[row.itemId]
''', '''            val advisor = advisorById[row.itemId]
            val allocation = allocationById[row.itemId]
''')
replace_once(portfolio, '''                personal?.let {
                    PortfolioDashboardValue("Monatskauf", "${it.allocationEur} €")
                    PortfolioDashboardValue("Konzentration", it.concentrationLabel)
                    Text(it.explanation, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
''', '''                advisor?.let {
                    PortfolioDashboardValue("Berater", portfolioAdvisorActionLabel(it.action))
                    PortfolioDashboardValue("Zusatz diesen Monat", "${allocation?.amountEur ?: 0} €")
                    PortfolioDashboardValue("Konfidenz", "${it.advisor.confidencePct} %")
                    it.advisor.reasons.take(2).forEach { reason -> Text("• $reason", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall) }
                }
''')
# Append action label helper.
p = Path(portfolio)
text = p.read_text(encoding="utf-8")
text += '''\n\nprivate fun portfolioAdvisorActionLabel(action: PortfolioAdvisorAction): String = when (action) {\n    PortfolioAdvisorAction.NACHKAUFEN -> "Nachkaufen"\n    PortfolioAdvisorAction.HALTEN -> "Halten"\n    PortfolioAdvisorAction.REDUZIEREN -> "Reduzieren"\n    PortfolioAdvisorAction.VERKAUFEN -> "Verkaufen"\n    PortfolioAdvisorAction.NEU_AUFNEHMEN -> "Neu aufnehmen"\n    PortfolioAdvisorAction.NICHT_AUFNEHMEN -> "Nicht aufnehmen"\n    PortfolioAdvisorAction.KEINE_BELASTBARE_BEWERTUNG -> "Bewertung prüfen"\n}\n'''
p.write_text(text, encoding="utf-8")

# Radar: use shared candidates, never concentration as an action rule.
replace_once(radar, '''    personalById: Map<String, PersonalRecommendation>,
''', '''    advisorById: Map<String, PortfolioAdvisorCandidate>,
''')
replace_once(radar, '''                personal = personalById[summary.id],
''', '''                advisorCandidate = advisorById[summary.id],
''')
replace_once(radar, '''    personal: PersonalRecommendation?,
''', '''    advisorCandidate: PortfolioAdvisorCandidate?,
''')
replace_once(radar, '''            personal?.let { Text("Monatskauf ${it.allocationEur} € · Depot ${String.format(Locale.GERMANY, "%.1f", it.currentWeightPct)} % · ${it.concentrationLabel}", style = MaterialTheme.typography.bodySmall) }
''', '''            advisorCandidate?.let {
                Text("Berater: ${radarAdvisorActionLabel(it.action)} · Konfidenz ${it.advisor.confidencePct} %", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                Text("Sparplan ${it.monthlySavingsEur} € / Monat", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
''')
p = Path(radar)
text = p.read_text(encoding="utf-8")
text += '''\n\nprivate fun radarAdvisorActionLabel(action: PortfolioAdvisorAction): String = when (action) {\n    PortfolioAdvisorAction.NACHKAUFEN -> "Nachkaufen"\n    PortfolioAdvisorAction.HALTEN -> "Halten"\n    PortfolioAdvisorAction.REDUZIEREN -> "Reduzieren"\n    PortfolioAdvisorAction.VERKAUFEN -> "Verkaufen"\n    PortfolioAdvisorAction.NEU_AUFNEHMEN -> "Neu aufnehmen"\n    PortfolioAdvisorAction.NICHT_AUFNEHMEN -> "Nicht aufnehmen"\n    PortfolioAdvisorAction.KEINE_BELASTBARE_BEWERTUNG -> "Bewertung prüfen"\n}\n'''
p.write_text(text, encoding="utf-8")

# Detail: advisor action, reliable forecast ranges, reasons/risks and bounded material history.
replace_once(detail, '''    personalRecommendation: PersonalRecommendation?,
    isWatchlisted: Boolean,
''', '''    advisorCandidate: PortfolioAdvisorCandidate?,
    advisorHistory: List<AdvisorHistoryEntry>,
    isWatchlisted: Boolean,
''')
replace_once(detail, '''    val forecast = ForecastEngine.forecast(effectiveItem)
''', '''    val forecast = ForecastEngine.forecast(effectiveItem)
    val advisorForecasts = AdvisorInputFactory.from(effectiveItem, forecast, freshness).forecastRanges.associateBy { it.horizon }
''')
replace_once(detail, '''                    DetailValueRow("Erwartet", detailForecastScenario(point.targetPriceEur, point.expectedChangePct))
                    DetailValueRow("Schwach", detailForecastScenario(point.bearTargetPriceEur, point.bearChangePct))
''', '''                    DetailValueRow("Erwartet", detailForecastScenario(point.targetPriceEur, point.expectedChangePct))
                    val advisorRange = advisorForecasts[point.horizon]
                    if (advisorRange?.reliable == true && advisorRange.lowerTargetPriceEur != null && advisorRange.upperTargetPriceEur != null) {
                        DetailValueRow("Zielbereich", "${detailMoney(advisorRange.lowerTargetPriceEur)} – ${detailMoney(advisorRange.upperTargetPriceEur)}")
                    } else {
                        Text("Zielbereich: ${advisorRange?.reasons?.firstOrNull() ?: "Datenbasis für diesen Horizont nicht ausreichend belastbar"}", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                    }
                    DetailValueRow("Konfidenz", "${advisorRange?.confidencePct ?: 0} %")
                    DetailValueRow("Schwach", detailForecastScenario(point.bearTargetPriceEur, point.bearChangePct))
''')
old_personal = '''        personalRecommendation?.let { personal ->
            item {
                DetailCard {
                    Text("Deine Einordnung", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black)
                    DetailValueRow("Monatskauf", "${personal.allocationEur} €")
                    DetailValueRow("Depotgewicht", String.format(Locale.GERMANY, "%.1f %%", personal.currentWeightPct))
                    DetailValueRow("Konzentration", personal.concentrationLabel)
                    Text(personal.explanation, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
'''
new_personal = '''        advisorCandidate?.let { candidate ->
            item {
                DetailCard {
                    Text("Deine Einordnung", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black)
                    DetailValueRow("Handlung", detailAdvisorActionLabel(candidate.action))
                    DetailValueRow("Konfidenz", "${candidate.advisor.confidencePct} %")
                    DetailValueRow("Sparplan", "${candidate.monthlySavingsEur} € / Monat")
                    candidate.advisor.reasons.take(3).forEach { Text("• $it", color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    candidate.advisor.risks.take(3).forEach { Text("Risiko: $it", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                }
            }
        }

        item {
            DetailCard {
                Text("Änderungshistorie", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black)
                if (advisorHistory.isEmpty()) {
                    Text("Noch keine materielle Signaländerung gespeichert.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    advisorHistory.take(20).forEach { entry ->
                        val before = entry.previousSignal?.let(::detailAdvisorSignalLabel) ?: "Erste Bewertung"
                        Text("${entry.analysisDay}: $before → ${detailAdvisorSignalLabel(entry.newSignal)}${entry.score?.let { " · Score $it" }.orEmpty()}", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
'''
replace_once(detail, old_personal, new_personal)
p = Path(detail)
text = p.read_text(encoding="utf-8")
text += '''\n\nprivate fun detailAdvisorActionLabel(action: PortfolioAdvisorAction): String = when (action) {\n    PortfolioAdvisorAction.NACHKAUFEN -> "Nachkaufen"\n    PortfolioAdvisorAction.HALTEN -> "Halten"\n    PortfolioAdvisorAction.REDUZIEREN -> "Reduzieren"\n    PortfolioAdvisorAction.VERKAUFEN -> "Verkaufen"\n    PortfolioAdvisorAction.NEU_AUFNEHMEN -> "Neu aufnehmen"\n    PortfolioAdvisorAction.NICHT_AUFNEHMEN -> "Nicht aufnehmen"\n    PortfolioAdvisorAction.KEINE_BELASTBARE_BEWERTUNG -> "Bewertung prüfen"\n}\n\nprivate fun detailAdvisorSignalLabel(signal: AdvisorSignal): String = when (signal) {\n    AdvisorSignal.NACHKAUFEN -> "Nachkaufen"\n    AdvisorSignal.HALTEN -> "Halten"\n    AdvisorSignal.REDUZIEREN -> "Reduzieren"\n    AdvisorSignal.VERKAUFEN -> "Verkaufen"\n    AdvisorSignal.KEINE_BELASTBARE_BEWERTUNG -> "Bewertung prüfen"\n}\n'''
p.write_text(text, encoding="utf-8")

print("Task 10 source patch applied")
