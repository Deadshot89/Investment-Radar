from pathlib import Path

path = Path("android/app/src/main/java/de/tobias/investmentradar/MainActivity.kt")
text = path.read_text(encoding="utf-8")

replacements = [
    (
        '            RecommendationRow(item, personalById[item.id]) { TradeRepublicNavigator.open(context, item) }',
        '            RecommendationRow(item, personalById[item.id], positions[item.id]) { TradeRepublicNavigator.open(context, item) }',
    ),
    (
        '            val personal = personalById[top.id]\n            val amount = personal?.allocationEur ?: 0\n            Text("HEUTIGE EMPFEHLUNG",',
        '            val personal = personalById[top.id]\n            val amount = personal?.allocationEur ?: 0\n            val topDepotValue = positions[top.id]?.takeIf { it.isActiveHolding() }?.currentValue(top.price)\n            Text("HEUTIGE EMPFEHLUNG",',
    ),
    (
        '                        "Monatskauf" to if (amount > 0) "$amount €" else "0 €",\n                        "Score" to RecommendationPresentation.scoreText(top.scoreTotal),',
        '                        "Monatskauf" to if (amount > 0) "$amount €" else "0 €",\n                        "Depotwert" to topDepotValue?.let(::formatMoney).orEmpty().ifBlank { "–" },\n                        "Score" to RecommendationPresentation.scoreText(top.scoreTotal),',
    ),
    (
        'private fun RecommendationRow(item: InvestmentItem, personal: PersonalRecommendation?, onOpen: () -> Unit) {\n    val label = RecommendationPresentation.label(item)\n    val amount = personal?.allocationEur ?: 0',
        'private fun RecommendationRow(item: InvestmentItem, personal: PersonalRecommendation?, position: PortfolioPosition?, onOpen: () -> Unit) {\n    val label = RecommendationPresentation.label(item)\n    val amount = personal?.allocationEur ?: 0\n    val depotValue = position?.takeIf { it.isActiveHolding() }?.currentValue(item.price)',
    ),
    (
        '                Text("${item.ticker} · Score ${RecommendationPresentation.scoreText(item.scoreTotal)} · Risiko ${item.risk}/5", color = RadarMuted, style = MaterialTheme.typography.bodySmall)\n                Text(\n                    if (amount > 0)',
        '                Text("${item.ticker} · Score ${RecommendationPresentation.scoreText(item.scoreTotal)} · Risiko ${item.risk}/5", color = RadarMuted, style = MaterialTheme.typography.bodySmall)\n                if (position?.isActiveHolding() == true) {\n                    Text(\n                        if (depotValue != null) "IM DEPOT · ${formatMoney(depotValue)}" else "IM DEPOT · Wert nicht verfügbar",\n                        color = RadarPurple,\n                        fontWeight = FontWeight.Black,\n                        style = MaterialTheme.typography.labelLarge\n                    )\n                }\n                Text(\n                    if (amount > 0)',
    ),
]

for old, new in replacements:
    count = text.count(old)
    if count != 1:
        raise SystemExit(f"Expected exactly one match, found {count}: {old[:100]!r}")
    text = text.replace(old, new, 1)

path.write_text(text, encoding="utf-8")
print("Patched Live depot values into MainActivity.kt")
