from pathlib import Path

patch_path = Path("scripts/apply-ir120-mainactivity.py")
source = patch_path.read_text(encoding="utf-8")
old_guard = "for forbidden in ('InvestmentPlanner.', 'private fun AlertsScreen(', 'private fun openInvestment(', 'TRADE_REPUBLIC_STOCK_BASE_URL'):"
new_guard = "for forbidden in ('private fun AlertsScreen(', 'private fun openInvestment(', 'TRADE_REPUBLIC_STOCK_BASE_URL'):"
if old_guard not in source:
    raise SystemExit("MainActivity migration guard shape changed")
source = source.replace(old_guard, new_guard, 1)
exec(compile(source, str(patch_path), "exec"), {"__name__": "__main__"})

main_path = Path("android/app/src/main/java/de/tobias/investmentradar/MainActivity.kt")
text = main_path.read_text(encoding="utf-8")

replacements = [
    (
        '            val reco = InvestmentPlanner.recommendation(item.toPlannerItem())',
        '            val label = RecommendationPresentation.label(item)'
    ),
    (
        '            NeonPanel(accent = recommendationColor(reco.label)) {',
        '            NeonPanel(accent = recommendationColor(label)) {'
    ),
    (
        '                    StatusPill(reco.label)',
        '                    StatusPill(label)'
    ),
    (
        '                Text("Score ${reco.score}/100 · Risiko ${item.risk}/5", color = recommendationColor(reco.label), fontWeight = FontWeight.Bold)',
        '                Text("Score ${RecommendationPresentation.scoreText(item.scoreTotal)} · Risiko ${item.risk}/5", color = recommendationColor(label), fontWeight = FontWeight.Bold)'
    ),
    (
        '                Text(reco.reason, style = MaterialTheme.typography.bodySmall)',
        '                Text(RecommendationPresentation.topReasons(item).joinToString(" · ").ifBlank { "Analyse V2 · keine Zusatzbegründung" }, style = MaterialTheme.typography.bodySmall)'
    )
]
for old, new in replacements:
    count = text.count(old)
    if count != 1:
        raise SystemExit(f"Portfolio V2 replacement expected once, found {count}: {old[:60]}")
    text = text.replace(old, new, 1)

for forbidden in ('InvestmentPlanner.', 'toPlannerItem()', 'private fun AlertsScreen(', 'private fun openInvestment(', 'TRADE_REPUBLIC_STOCK_BASE_URL'):
    if forbidden in text:
        raise SystemExit(f"legacy MainActivity code remains after portfolio migration: {forbidden}")

main_path.write_text(text, encoding="utf-8")
