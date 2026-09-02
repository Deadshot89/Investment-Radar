# Investment Radar 1.2.0 Investment Detail Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add one reusable investment-detail view that explains Analysis V2, portfolio context and data freshness without extra market-data requests, while correcting existing score UI so missing data is never presented as zero.

**Architecture:** `InvestmentDetailScreen` receives already-loaded `InvestmentItem` plus optional local `CustomInvestment` and portfolio state. Custom investments already have merged quote/fallback `InvestmentItem`s in `DashboardData.items`. `DataFreshness` is pure Kotlin. `MainActivity` owns only selected-detail navigation.

**Tech Stack:** Kotlin, Jetpack Compose Material 3, JUnit 4, existing Recommendation/Portfolio/Trade Republic modules.

**Spec:** `docs/superpowers/specs/2026-09-02-investment-radar-1.2.0-release-expansion-design.md`

## Global Constraints

- Detail opens from Radar, Portfolio and Alerts.
- Opening detail must not call `ApiClient` or a provider.
- Missing scores, coverage, fundamentals or momentum display “Nicht verfügbar”, never `0`.
- Momentum: 1D, 1M, 3M, 6M, 12M; render only non-null values.
- Trend label uses existing momentum only: 3M+6M both >0 = `Positiver Trend`; both <0 = `Negativer Trend`; any other case with at least one of 3M/6M available = `Gemischter Trend`; both missing = `Nicht verfügbar`.
- History fresh <=6h; fundamentals fresh <=24h; cache usable <=7d; >7d stale.
- Freshness precedence: STALE > PARTIAL > CACHED > CURRENT.
- Sources are shown only when actually present.
- Trade Republic remains explicit user action.
- Custom item uses local metadata plus merged quote item; Analysis V2 sections appear only when score data actually exists.

---

## File Structure

- Create `android/app/src/main/java/de/tobias/investmentradar/DataFreshness.kt`.
- Create `android/app/src/main/java/de/tobias/investmentradar/InvestmentDetailScreen.kt`.
- Create `android/app/src/test/java/de/tobias/investmentradar/DataFreshnessTest.kt`.
- Modify `android/app/src/main/java/de/tobias/investmentradar/ScoreBreakdownCard.kt`.
- Modify `android/app/src/main/java/de/tobias/investmentradar/MainActivity.kt`.
- Modify `android/app/src/main/java/de/tobias/investmentradar/RadarScreen.kt`.
- Create `android/tests/test-investment-detail-ui.sh`.
- Create `android/tests/test-score-null-display.sh`.
- Modify `.github/workflows/android-contract-tests.yml`.

### Task 1: Deterministic data freshness

**Interfaces:**
```kotlin
enum class FreshnessStatus { CURRENT, CACHED, PARTIAL, STALE }

data class DataFreshnessSummary(
    val status: FreshnessStatus,
    val label: String,
    val analysisAsOf: String?,
    val quoteSource: String?,
    val historySource: String?,
    val fundamentalSource: String?,
    val coverage: Int?
)

object DataFreshness {
    fun summarize(item: InvestmentItem, nowEpochMs: Long = System.currentTimeMillis()): DataFreshnessSummary
}
```

- [ ] **Step 1: Write failing tests with a local fixture**

```kotlin
private fun freshnessItem(
    coverage: Int? = 80,
    type: String = "Aktie",
    analysisAsOf: String? = "2026-09-02T09:30:00Z",
    momentumAsOf: String? = "2026-09-02T08:00:00Z",
    momentumStale: Boolean = false,
    fundamentalsAsOf: String? = "2026-09-01T18:00:00Z",
    fundamentalsStale: Boolean = false
): InvestmentItem = testInvestmentItem(id = "x", type = type, coverage = coverage).copy(
    analysisAsOf = analysisAsOf,
    dataSource = "Twelve Data",
    momentum = MomentumSnapshot(
        m6 = 5.0,
        source = "Twelve Data",
        asOf = momentumAsOf,
        stale = momentumStale
    ),
    fundamentals = FundamentalSnapshot(
        pe = 20.0,
        source = "Twelve Data",
        asOf = fundamentalsAsOf,
        stale = fundamentalsStale
    )
)
```

```kotlin
@Test
fun freshStockWithGoodCoverageIsCurrent() {
    val now = Instant.parse("2026-09-02T10:00:00Z").toEpochMilli()
    assertEquals(FreshnessStatus.CURRENT, DataFreshness.summarize(freshnessItem(), now).status)
}

@Test
fun lowCoverageBeatsCached() {
    val now = Instant.parse("2026-09-02T10:00:00Z").toEpochMilli()
    val item = freshnessItem(coverage = 60, momentumAsOf = "2026-09-02T00:00:00Z", momentumStale = true)
    assertEquals(FreshnessStatus.PARTIAL, DataFreshness.summarize(item, now).status)
}

@Test
fun olderThanSevenDaysIsStaleWithHighestPrecedence() {
    val now = Instant.parse("2026-09-10T10:00:00Z").toEpochMilli()
    val item = freshnessItem(coverage = 40, momentumAsOf = "2026-09-01T10:00:00Z", momentumStale = true)
    assertEquals(FreshnessStatus.STALE, DataFreshness.summarize(item, now).status)
}
```

- [ ] **Step 2: Run RED**

```bash
(cd android && ./gradlew testReleaseUnitTest --tests 'de.tobias.investmentradar.DataFreshnessTest')
```
Expected: compile failure because `DataFreshness` does not exist.

- [ ] **Step 3: Implement exact thresholds**

```kotlin
private const val HISTORY_FRESH_MS = 6L * 60 * 60 * 1000
private const val FUNDAMENTAL_FRESH_MS = 24L * 60 * 60 * 1000
private const val MAX_CACHE_MS = 7L * 24 * 60 * 60 * 1000
```

Parse ISO timestamps safely with `Instant.parse`; parse failure returns null and therefore contributes to PARTIAL when the component is expected.

Rules:
1. STALE if a used history/fundamental timestamp is older than 7d.
2. PARTIAL if coverage is null/<70 or an expected source/timestamp is missing.
3. CACHED if history is >6h or stale, or stock fundamentals are >24h or stale, while <=7d.
4. CURRENT otherwise.

ETF fundamentals are expected only when the ETF item actually has a nonblank fundamental source or any non-null fundamental metric.

- [ ] **Step 4: Run GREEN and commit**

```bash
(cd android && ./gradlew testReleaseUnitTest --tests 'de.tobias.investmentradar.DataFreshnessTest')
git add android/app/src/main/java/de/tobias/investmentradar/DataFreshness.kt android/app/src/test/java/de/tobias/investmentradar/DataFreshnessTest.kt
git commit -m "feat: classify investment data freshness"
```

### Task 2: Fix missing-score presentation globally

**Files:**
- Modify: `android/app/src/main/java/de/tobias/investmentradar/ScoreBreakdownCard.kt`
- Create: `android/tests/test-score-null-display.sh`
- Modify: `.github/workflows/android-contract-tests.yml`

- [ ] **Step 1: Write RED contract**

```bash
#!/usr/bin/env bash
set -euo pipefail
SRC="android/app/src/main/java/de/tobias/investmentradar/ScoreBreakdownCard.kt"
! grep -q 'item.coverage ?: 0' "$SRC"
grep -q 'Nicht verfügbar' "$SRC"
```

Run:
```bash
bash android/tests/test-score-null-display.sh
```
Expected: FAIL because current component contains `item.coverage ?: 0` and null score bars are drawn as zero.

- [ ] **Step 2: Implement null-safe shared score card**

Coverage label:
```kotlin
val coverageText = item.coverage?.let { "$it %" } ?: "Nicht verfügbar"
Text("Datenabdeckung $coverageText", style = MaterialTheme.typography.labelMedium)
```

Score row:
```kotlin
if (score == null) {
    Text("Nicht verfügbar", style = MaterialTheme.typography.bodySmall)
} else {
    Text("$score/100", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
    LinearProgressIndicator(progress = { score.coerceIn(0, 100) / 100f }, modifier = Modifier.fillMaxWidth())
}
```
Do not render a progress bar for null.

- [ ] **Step 3: Add contract to CI, run GREEN, commit**

```bash
bash android/tests/test-score-null-display.sh
git add android/app/src/main/java/de/tobias/investmentradar/ScoreBreakdownCard.kt android/tests/test-score-null-display.sh .github/workflows/android-contract-tests.yml
git commit -m "fix: distinguish missing scores from zero"
```

### Task 3: Shared investment detail screen

**Interface:**
```kotlin
@Composable
fun InvestmentDetailScreen(
    item: InvestmentItem?,
    customItem: CustomInvestment?,
    position: PortfolioPosition?,
    personalRecommendation: PersonalRecommendation?,
    isWatchlisted: Boolean,
    onBack: () -> Unit,
    onToggleWatchlist: (String) -> Unit,
    onEditPosition: (InvestmentItem) -> Unit,
    onOpenPortfolio: () -> Unit
)
```

Resolution:
- curated: `item != null`, `customItem == null`
- local custom after refresh: both non-null
- deleted/missing: both null

- [ ] **Step 1: Write RED UI contract**

```bash
#!/usr/bin/env bash
set -euo pipefail
SRC="android/app/src/main/java/de/tobias/investmentradar/InvestmentDetailScreen.kt"
test -f "$SRC"
grep -q 'Quality' "$SRC"
grep -q 'Valuation' "$SRC"
grep -q 'Growth' "$SRC"
grep -q 'Momentum' "$SRC"
grep -q 'Risk' "$SRC"
grep -q 'Coverage' "$SRC"
for period in 1D 1M 3M 6M 12M; do grep -q "$period" "$SRC"; done
grep -q 'Positiver Trend' "$SRC"
grep -q 'Negativer Trend' "$SRC"
grep -q 'Gemischter Trend' "$SRC"
grep -q 'Datenquellen' "$SRC"
grep -q 'Trade Republic öffnen' "$SRC"
grep -q 'Nicht verfügbar' "$SRC"
```

- [ ] **Step 2: Run RED**

```bash
bash android/tests/test-investment-detail-ui.sh
```
Expected: FAIL because screen file does not exist.

- [ ] **Step 3: Implement curated-item layout**

Order:
1. Back + name/ticker/ISIN/type.
2. Price/day change + recommendation + total score.
3. Personal monthly allocation/current weight/concentration/explanation.
4. `ScoreBreakdownCard(item)`.
5. Coverage + `DataFreshness.summarize(item)` status.
6. Momentum cards only for non-null d1/m1/m3/m6/m12.
7. Compact trend label derived exactly from m3/m6 using the global rule; no new numeric threshold.
8. Fundamental rows only for non-null metrics; otherwise `Fundamentaldaten nicht verfügbar`.
9. Top reasons.
10. `Datenquellen`: quote/history/fundamental source or `Quelle nicht verfügbar`, plus available timestamps.
11. Watchlist, transaction/portfolio and Trade Republic actions.

Use Material 3 directly; do not depend on file-private MainActivity helpers. Do not call `ApiClient`.

- [ ] **Step 4: Implement merged custom-item behavior**

When `customItem != null`, local name/ticker/ISIN/type and saved TR URL are authoritative. Merged `item` supplies quote fields. If all six score fields (`scoreTotal` + five components) are null, show `Analyse V2 für diesen eigenen Wert nicht verfügbar` and omit score/fundamental confidence presentation. Show manual EUR price as fallback context when present.

If both item/customItem are null, show `Wertpapier nicht mehr verfügbar` plus Back only.

- [ ] **Step 5: Add contract to CI, run GREEN, commit**

```bash
bash android/tests/test-investment-detail-ui.sh
(cd android && ./gradlew testReleaseUnitTest)
```
Expected: PASS.

```bash
git add android/app/src/main/java/de/tobias/investmentradar/InvestmentDetailScreen.kt android/tests/test-investment-detail-ui.sh .github/workflows/android-contract-tests.yml
git commit -m "feat: add investment detail screen"
```

### Task 4: Detail navigation from Radar and Portfolio shell

**State:**
```kotlin
var selectedDetailId by rememberSaveable { mutableStateOf<String?>(null) }
var detailReturnTab by rememberSaveable { mutableIntStateOf(0) }
```

- [ ] **Step 1: Extend detail contract and confirm RED**

```bash
grep -q 'selectedDetailId' android/app/src/main/java/de/tobias/investmentradar/MainActivity.kt
grep -q 'onOpenDetail' android/app/src/main/java/de/tobias/investmentradar/RadarScreen.kt
```

- [ ] **Step 2: Wire common detail state**

Ready-state resolution:
```kotlin
val selectedMarketItem = s.data.items.firstOrNull { it.id == selectedDetailId }
val selectedCustomItem = customItems.firstOrNull { it.id == selectedDetailId }
val currentValues = PortfolioAnalysis.values(s.data.items, positions, customItems)
val personalById = RecommendationEngine.plan(s.data.items, budget, currentValues).items.associateBy { it.itemId }
```

When selected id is non-null, render `InvestmentDetailScreen` instead of tab body. Back clears id and restores `detailReturnTab`. Bottom-nav actions clear selected id before switching tabs.

Update Radar interface to:
```kotlin
onOpenDetail: (String) -> Unit
```
and add a visible `Details` button calling `onOpenDetail(item.id)`.

Update the existing portfolio card signature with the same id-based callback until the portfolio plan replaces that UI entirely.

- [ ] **Step 3: Run GREEN and commit**

```bash
bash android/tests/test-investment-detail-ui.sh
(cd android && ./gradlew testReleaseUnitTest)
git add android/app/src/main/java/de/tobias/investmentradar/MainActivity.kt android/app/src/main/java/de/tobias/investmentradar/RadarScreen.kt android/tests/test-investment-detail-ui.sh
git commit -m "feat: navigate to shared investment details"
```

### Task 5: Verification checkpoint

- [ ] Run all permanent Android contract scripts including detail and score-null contracts.
- [ ] Run `(cd android && ./gradlew testReleaseUnitTest)` with zero failures.
- [ ] Push and require Android Contract Tests + Android JVM Tests green before the portfolio plan.
