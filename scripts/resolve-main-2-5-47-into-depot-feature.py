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


# Keep feature-local ML Kit while aligning the pre-release branch with production 2.5.47.
build = "android/app/build.gradle.kts"
replace_once(build, 'versionCode = 111', 'versionCode = 117', marker='versionCode = 117')
replace_once(build, 'versionName = "2.5.41"', 'versionName = "2.5.47"', marker='versionName = "2.5.47"')
replace_once(
    build,
    '// Release candidate: Investment Radar 2.5.41',
    '// Release candidate: Investment Radar 2.5.47',
    marker='// Release candidate: Investment Radar 2.5.47',
)

# Keep both screenshot-import contracts and the production 2.5.47 contracts.
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

# Merge the cleanup into the lifecycle-aware portfolio store and remove all new per-position metadata too.
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

# Preserve SOLD_MARKED as authoritative while exposing the same EUR unit price as production.
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

# Show the unit price for live/trackable rows without duplicating the explicit snapshot-only market-price row.
dashboard = "android/app/src/main/java/de/tobias/investmentradar/PortfolioDashboard.kt"
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

print("Applied deterministic 2.5.47 production hunks while preserving screenshot-import lifecycle changes.")
