#!/usr/bin/env python3
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


def read(path: str) -> str:
    return (ROOT / path).read_text(encoding="utf-8")


def write(path: str, content: str) -> None:
    (ROOT / path).write_text(content, encoding="utf-8")


def replace_once(path: str, old: str, new: str, marker: str | None = None) -> None:
    content = read(path)
    if marker and marker in content:
        return
    count = content.count(old)
    if count != 1:
        raise SystemExit(f"{path}: expected exactly one replacement target, found {count}: {old[:80]!r}")
    write(path, content.replace(old, new, 1))


def insert_after(path: str, anchor: str, addition: str, marker: str) -> None:
    content = read(path)
    if marker in content:
        return
    count = content.count(anchor)
    if count != 1:
        raise SystemExit(f"{path}: expected exactly one insertion anchor, found {count}: {anchor[:80]!r}")
    write(path, content.replace(anchor, anchor + addition, 1))


# Version metadata is intentionally NOT rewritten here. build.gradle.kts auto-merges from
# current main; the final release bump is a separate Task-10 step after the full regression.

# Keep both screenshot-import contracts and current production contracts.
workflow = ".github/workflows/android-contract-tests.yml"
insert_after(
    workflow,
    '            android/tests/test-portfolio-value-source-ui.sh \\\n',
    '            android/tests/test-portfolio-current-price-ui.sh \\\n',
    'android/tests/test-portfolio-current-price-ui.sh',
)
insert_after(
    workflow,
    '            android/tests/test-savings-plans-ui.sh \\\n',
    '            android/tests/test-legacy-ibond-cleanup.sh \\\n',
    'android/tests/test-legacy-ibond-cleanup.sh',
)

# Merge the retired-iBond cleanup into the feature-aware custom asset store.
custom = "android/app/src/main/java/de/tobias/investmentradar/CustomInvestmentStore.kt"
replace_once(custom, '        return runCatching {\n', '        val parsed = runCatching {\n', marker='        val parsed = runCatching {\n')
replace_once(
    custom,
    '        }.getOrDefault(emptyList())\n    }\n\n    fun promotedIds',
    '        }.getOrDefault(emptyList())\n'
    '        val sanitized = parsed.filterNot { it.id == LegacyPortfolioCleanup.RETIRED_IBOND_ID }\n'
    '        if (sanitized.size != parsed.size) write(context, sanitized)\n'
    '        return sanitized\n'
    '    }\n\n'
    '    fun promotedIds',
    marker='val sanitized = parsed.filterNot { it.id == LegacyPortfolioCleanup.RETIRED_IBOND_ID }',
)
insert_after(
    custom,
    '    fun save(context: Context, item: CustomInvestment) {\n',
    '        if (item.id == LegacyPortfolioCleanup.RETIRED_IBOND_ID) return\n',
    'if (item.id == LegacyPortfolioCleanup.RETIRED_IBOND_ID) return',
)

# Merge the cleanup into the lifecycle-aware portfolio store and remove all feature-only
# per-position metadata as well.
store = "android/app/src/main/java/de/tobias/investmentradar/PortfolioStore.kt"
replace_once(
    store,
    '        val ids = buildSet {\n'
    '            addAll(prefs.getStringSet(LEGACY_KEY, emptySet()).orEmpty())\n'
    '            addAll(prefs.getStringSet(POSITION_IDS_KEY, emptySet()).orEmpty())\n'
    '        }\n'
    '        return ids.associateWith { itemId ->\n',
    '        val storedIds = buildSet {\n'
    '            addAll(prefs.getStringSet(LEGACY_KEY, emptySet()).orEmpty())\n'
    '            addAll(prefs.getStringSet(POSITION_IDS_KEY, emptySet()).orEmpty())\n'
    '        }\n'
    '        val ids = storedIds.filterNot { it == LegacyPortfolioCleanup.RETIRED_IBOND_ID }.toSet()\n'
    '        if (ids.size != storedIds.size) {\n'
    '            val retiredId = LegacyPortfolioCleanup.RETIRED_IBOND_ID\n'
    '            prefs.edit()\n'
    '                .putStringSet(LEGACY_KEY, ids)\n'
    '                .putStringSet(POSITION_IDS_KEY, ids)\n'
    '                .remove(investedKey(retiredId))\n'
    '                .remove(sharesKey(retiredId))\n'
    '                .remove(snapshotKey(retiredId))\n'
    '                .remove(snapshotCostBasisKey(retiredId))\n'
    '                .remove(trackedSharesKey(retiredId))\n'
    '                .remove(purchasesKey(retiredId))\n'
    '                .remove(salesKey(retiredId))\n'
    '                .remove(lifecycleStatusKey(retiredId))\n'
    '                .remove(snapshotCapturedAtKey(retiredId))\n'
    '                .apply()\n'
    '        }\n'
    '        return ids.associateWith { itemId ->\n',
    marker='val ids = storedIds.filterNot { it == LegacyPortfolioCleanup.RETIRED_IBOND_ID }.toSet()',
)
# If Git auto-merged main's cleanup, augment it with the feature-only metadata.
insert_after(
    store,
    '                .remove(salesKey(retiredId))\n',
    '                .remove(lifecycleStatusKey(retiredId))\n'
    '                .remove(snapshotCapturedAtKey(retiredId))\n',
    '.remove(lifecycleStatusKey(retiredId))',
)
insert_after(
    store,
    '    fun add(context: Context, itemId: String) {\n',
    '        if (itemId == LegacyPortfolioCleanup.RETIRED_IBOND_ID) return\n',
    'if (itemId == LegacyPortfolioCleanup.RETIRED_IBOND_ID) return',
)
insert_after(
    store,
    '    fun save(context: Context, position: PortfolioPosition) {\n',
    '        if (position.itemId == LegacyPortfolioCleanup.RETIRED_IBOND_ID) return\n',
    'if (position.itemId == LegacyPortfolioCleanup.RETIRED_IBOND_ID) return',
)

# Preserve SOLD_MARKED as authoritative while exposing the current EUR unit price.
metrics = "android/app/src/main/java/de/tobias/investmentradar/PortfolioMetrics.kt"
insert_after(
    metrics,
    '    val costBasisKnown: Boolean,\n',
    '    val currentPriceEur: Double?,\n',
    'val currentPriceEur: Double?',
)
insert_after(
    metrics,
    '                costBasisKnown = costBasisKnown,\n',
    '                currentPriceEur = draft.usablePrice,\n',
    'currentPriceEur = draft.usablePrice',
)

# The feature has a richer snapshot-only block than main. Preserve it, but carry forward
# current production semantics: quote availability and value calculability are different.
dashboard = "android/app/src/main/java/de/tobias/investmentradar/PortfolioDashboard.kt"
insert_after(
    dashboard,
    '            val currentMarketPrice = canonicalItem?.priceEur?.takeIf { it.isFinite() && it > 0.0 }\n',
    '            val currentValueMissingReason = when {\n'
    '                row.currentPriceEur != null -> "Stückzahl fehlt"\n'
    '                else -> "Kurs fehlt"\n'
    '            }\n',
    'row.currentPriceEur != null -> "Stückzahl fehlt"',
)
replace_once(
    dashboard,
    '                        PortfolioDashboardValue("Aktueller Depotwert", "Nicht berechenbar · Stückzahl fehlt")\n',
    '                        PortfolioDashboardValue("Aktueller Depotwert", "Nicht berechenbar · $currentValueMissingReason")\n',
    marker='PortfolioDashboardValue("Aktueller Depotwert", "Nicht berechenbar · $currentValueMissingReason")',
)
replace_once(
    dashboard,
    '                        row.currentValue?.let(::portfolioMoney) ?: "Kurs fehlt"\n',
    '                        row.currentValue?.let(::portfolioMoney) ?: currentValueMissingReason\n',
    marker='row.currentValue?.let(::portfolioMoney) ?: currentValueMissingReason',
)
insert_after(
    dashboard,
    '                }\n                if (isFixedIncome) {\n',
    '                if (!isFixedIncome && !(importedSnapshot && !hasTrackedShares)) {\n'
    '                    PortfolioDashboardValue("Aktueller Kurs", row.currentPriceEur?.let(::portfolioMoney) ?: "Kurs fehlt")\n'
    '                }\n',
    'PortfolioDashboardValue("Aktueller Kurs", row.currentPriceEur?.let(::portfolioMoney) ?: "Kurs fehlt")',
)

# Preserve the current full-portfolio concentration context in the interactive plan.
main = "android/app/src/main/java/de/tobias/investmentradar/MainActivity.kt"
replace_once(
    main,
    '                        val advisorPlan = PortfolioAdvisorEngine.allocate(advisorCandidates, budgetState.advisorBudgetEur)\n',
    '                        val advisorPlan = PortfolioAdvisorEngine.allocate(\n'
    '                            candidates = advisorCandidates,\n'
    '                            budgetEur = budgetState.advisorBudgetEur,\n'
    '                            concentrationContext = PortfolioConcentrationContext(\n'
    '                                holdingIds = holdingIds,\n'
    '                                valuesEur = portfolioValues\n'
    '                            )\n'
    '                        )\n',
    marker='                                valuesEur = portfolioValues\n                            )\n                        )',
)

# Preserve the same context in the persisted advisor plan.
vm = "android/app/src/main/java/de/tobias/investmentradar/MainViewModel.kt"
replace_once(
    vm,
    '        val plan = PortfolioAdvisorEngine.allocate(candidates, advisorBudgetEur)\n',
    '        val plan = PortfolioAdvisorEngine.allocate(\n'
    '            candidates = candidates,\n'
    '            budgetEur = advisorBudgetEur,\n'
    '            concentrationContext = PortfolioConcentrationContext(\n'
    '                holdingIds = _holdingIds.value,\n'
    '                valuesEur = portfolioValues\n'
    '            )\n'
    '        )\n',
    marker='                holdingIds = _holdingIds.value,\n                valuesEur = portfolioValues',
)

print("Applied current-production hunks while preserving screenshot-import lifecycle changes.")
