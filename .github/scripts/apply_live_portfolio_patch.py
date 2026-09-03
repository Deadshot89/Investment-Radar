from pathlib import Path

path = Path("android/app/src/main/java/de/tobias/investmentradar/MainActivity.kt")
text = path.read_text(encoding="utf-8")

if "LivePortfolioCard(" in text:
    print("Live portfolio patch already applied")
    raise SystemExit(0)

old_call = '''                            0 -> DashboardScreen(\n                                data = s.data,\n                                budget = budget,\n                                holdingIds = holdingIds,\n                                positions = positions,\n                                watchlistIds = watchlistIds,\n                                personalPlan = personalPlan,\n                                onEditBudget = { budgetDialog = true },\n                                onOpenRadar = { selectedDetailId = null; tab = 1 }\n                            )'''
new_call = '''                            0 -> DashboardScreen(\n                                data = s.data,\n                                budget = budget,\n                                holdingIds = holdingIds,\n                                positions = positions,\n                                customItems = customItems,\n                                watchlistIds = watchlistIds,\n                                personalPlan = personalPlan,\n                                onEditBudget = { budgetDialog = true },\n                                onOpenRadar = { selectedDetailId = null; tab = 1 },\n                                onOpenPortfolio = { selectedDetailId = null; tab = 2 }\n                            )'''

old_signature = '''private fun DashboardScreen(\n    data: DashboardData,\n    budget: Int,\n    holdingIds: Set<String>,\n    positions: Map<String, PortfolioPosition>,\n    watchlistIds: Set<String>,\n    personalPlan: PersonalPlan,\n    onEditBudget: () -> Unit,\n    onOpenRadar: () -> Unit\n) {'''
new_signature = '''private fun DashboardScreen(\n    data: DashboardData,\n    budget: Int,\n    holdingIds: Set<String>,\n    positions: Map<String, PortfolioPosition>,\n    customItems: List<CustomInvestment>,\n    watchlistIds: Set<String>,\n    personalPlan: PersonalPlan,\n    onEditBudget: () -> Unit,\n    onOpenRadar: () -> Unit,\n    onOpenPortfolio: () -> Unit\n) {'''

old_anchor = '''        item {\n            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {\n                DarkMetricCard("MARKT", data.marketLight.uppercase(), RadarYellow, Modifier.weight(1f))\n                DarkMetricCard("BUDGET", "$budget €", RadarBlue, Modifier.weight(1f), onClick = onEditBudget)\n                DarkMetricCard("SIGNAL", if (top != null) "AKTIV" else "WARTEN", RadarGreen, Modifier.weight(1f), valueStyle = if (top == null) MaterialTheme.typography.titleMedium else MaterialTheme.typography.titleLarge)\n            }\n        }\n\n        item {\n            NeonPanel(accent = if (reviewItems.isNotEmpty() || concentrationWarning != null) RadarYellow else RadarCyan) {'''
new_anchor = '''        item {\n            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {\n                DarkMetricCard("MARKT", data.marketLight.uppercase(), RadarYellow, Modifier.weight(1f))\n                DarkMetricCard("BUDGET", "$budget €", RadarBlue, Modifier.weight(1f), onClick = onEditBudget)\n                DarkMetricCard("SIGNAL", if (top != null) "AKTIV" else "WARTEN", RadarGreen, Modifier.weight(1f), valueStyle = if (top == null) MaterialTheme.typography.titleMedium else MaterialTheme.typography.titleLarge)\n            }\n        }\n\n        item {\n            LivePortfolioCard(\n                items = data.items,\n                positions = positions,\n                customItems = customItems,\n                onOpenPortfolio = onOpenPortfolio\n            )\n        }\n\n        item {\n            NeonPanel(accent = if (reviewItems.isNotEmpty() || concentrationWarning != null) RadarYellow else RadarCyan) {'''

for old, new, label in [
    (old_call, new_call, "DashboardScreen call"),
    (old_signature, new_signature, "DashboardScreen signature"),
    (old_anchor, new_anchor, "Live card anchor"),
]:
    if old not in text:
        raise SystemExit(f"Expected {label} not found; refusing unsafe patch")
    text = text.replace(old, new, 1)

path.write_text(text, encoding="utf-8")
print("Live portfolio patch applied")
