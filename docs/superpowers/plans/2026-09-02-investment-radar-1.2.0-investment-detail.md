# Investment Radar 1.2.0 Investment Detail Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add one reusable investment-detail view that explains the full Analysis V2 result, data freshness, momentum, fundamentals, portfolio context and actions without extra market-data requests.

**Architecture:** The detail screen receives already-loaded `InvestmentItem` plus optional local `CustomInvestment`/portfolio state. Custom investments are already merged into `DashboardData.items` by `MainViewModel`, so the common detail screen deliberately supports both `item` and `customItem` being present for the same id. Data freshness and presentation are computed by pure Kotlin helpers. `MainActivity` owns only the selected detail id and back navigation; the screen performs no provider/network call.

**Tech Stack:** Kotlin, Jetpack Compose Material 3, JUnit 4, existing Recommendation/Portfolio/Trade Republic modules.

**Spec:** `docs/superpowers/specs/2026-09-02-investment-radar-1.2.0-release-expansion-design.md`

## Global Constraints

- Detail opens from Radar, Portfolio and Alerts.
- No new provider request when detail opens.
- Missing score/fundamental/momentum values render as “Nicht verfügbar”, never `0`.
- Momentum horizons: 1D, 1M, 3M, 6M, 12M; show only values that exist.
- Show objective recommendation, total score, five component scores, coverage and top reasons when Analysis V2 fields exist.
- Show personal monthly allocation and current portfolio weighting when available.
- Show source/freshness information without inventing sources.
- History fresh <= 6h; fundamentals fresh <= 24h; cache may be reused <= 7d; >7d is stale.
- Overall freshness precedence: STALE > PARTIAL > CACHED > CURRENT.
- Trade Republic remains explicit user action.
- Custom/local investment detail must use its merged quote `InvestmentItem` when present, but degrade gracefully when Analysis V2 fields are unavailable.

---

## File Structure

- Create `android/app/src/main/java/de/tobias/investmentradar/DataFreshness.kt` — pure freshness/source model.
- Create `android/app/src/main/java/de/tobias/investmentradar/InvestmentDetailScreen.kt` — shared Compose detail view.
- Create `android/app/src/test/java/de/tobias/investmentradar/DataFreshnessTest.kt` — deterministic age/precedence tests.
- Modify `android/app/src/main/java/de/tobias/investmentradar/MainActivity.kt` — selected-detail navigation only.
- Modify `android/app/src/main/java/de/tobias/investmentradar/RadarScreen.kt` — call `onOpenDetail`.
- Create `android/tests/test-investment-detail-ui.sh`.
- Modify `.github/workflows/android-contract-tests.yml` to run the new detail contract.

### Task 1: Deterministic data-freshness model

**Files:**
- Create: `android/app/src/main/java/de/tobias/investmentradar/DataFreshness.kt`
- Create: `android/app/src/test/java/de/tobias/investmentradar/DataFreshnessTest.kt`

**Interfaces:**
- Consumes: `InvestmentItem`, `MomentumSnapshot`, `FundamentalSnapshot`.
- Produces:
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

- [ ] **Step 1: Write failing precedence and threshold tests**

```kotlin
@Test
fun freshStockWithGoodCoverageIsCurrent() {
    val now = Instant.parse("2026-09-02T10:00:00Z").toEpochMilli()
    val item = fixtureItem(
        coverage = 80,
        analysisAsOf = "2026-09-02T09:30:00Z",
        momentumAsOf = "2026-09-02T08:00:00Z",
        momentumStale = false,
        fundamentalsAsOf = "2026-09-01T18:00:00Z",
        fundamentalsStale = false,
        type = "Aktie"
    )
    assertEquals(FreshnessStatus.CURRENT, DataFreshness.summarize(item, now).status)
}

@Test
fun lowCoverageBeatsCachedStatus() {
    val now = Instant.parse("2026-09-02T10:00:00Z").toEpochMilli()
    val item = fixtureItem(coverage = 60, momentumAsOf = "2026-09-02T00:00:00Z", momentumStale = true)
    assertEquals(FreshnessStatus.PARTIAL, DataFreshness.summarize(item, now).status)
}

@Test
fun olderThanSevenDaysIsStaleAndHasHighestPrecedence() {
    val now = Instant.parse("2026-09-10T10:00:00Z").toEpochMilli()
    val item = fixtureItem(coverage = 40, momentumAsOf = "2026-09-01T10:00:00Z", momentumStale = true)
    assertEquals(FreshnessStatus.STALE, DataFreshness.summarize(item, now).status)
}
```

- [ ] **Step 2: Run focused tests and confirm RED**

```bash
cd android && ./gradlew testReleaseUnitTest --tests 'de.tobias.investmentradar.DataFreshnessTest'
```
Expected: compile failure because `DataFreshness` does not exist.

- [ ] **Step 3: Implement fixed age rules**

Use `Instant.parse(value).toEpochMilli()` inside a safe helper returning null on parse failure.

```kotlin
private const val HISTORY_FRESH_MS = 6L * 60 * 60 * 1000
private const val FUNDAMENTAL_FRESH_MS = 24L * 60 * 60 * 1000
private const val MAX_CACHE_MS = 7L * 24 * 60 * 60 * 1000
```

Classification rules:
1. STALE if any used history/fundamental timestamp age > `MAX_CACHE_MS`.
2. PARTIAL if coverage is null or <70, or an expected source is missing.
3. CACHED if history is older than 6h or marked stale, or stock fundamentals are older than 24h or marked stale, while each is <=7d.
4. CURRENT otherwise.

Treat ETF fundamentals as not expected unless the item actually has a nonblank fundamental source/value set.

Source strings:
```kotlin
quoteSource = item.dataSource.takeIf { it.isNotBlank() }
historySource = item.momentum?.source?.takeIf { it.isNotBlank() }
fundamentalSource = item.fundamentals?.source?.takeIf { it.isNotBlank() }
```
The UI substitutes “Quelle nicht verfügbar” only at render time.

- [ ] **Step 4: Run focused tests and confirm GREEN**

```bash
cd android && ./gradlew testReleaseUnitTest --tests 'de.tobias.investmentradar.DataFreshnessTest'
```
Expected: PASS.

- [ ] **Step 5: Commit freshness model**

```bash
git add android/app/src/main/java/de/tobias/investmentradar/DataFreshness.kt android/app/src/test/java/de/tobias/investmentradar/DataFreshnessTest.kt
git commit -m "feat: classify investment data freshness"
```

### Task 2: Shared investment detail screen

**Files:**
- Create: `android/app/src/main/java/de/tobias/investmentradar/InvestmentDetailScreen.kt`
- Create: `android/tests/test-investment-detail-ui.sh`
- Modify: `.github/workflows/android-contract-tests.yml`

**Interfaces:**
- Consumes: `InvestmentItem?`, `CustomInvestment?`, `PortfolioPosition?`, `PersonalRecommendation?`, `DataFreshnessSummary`.
- Produces:
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
Resolution rules are explicit:
- Backend/curated item: `item != null`, `customItem == null`.
- Local custom item after `MainViewModel.refresh`: `item != null` (merged quote/fallback `InvestmentItem`) and `customItem != null` (local metadata).
- Missing/deleted target: both null.

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
grep -q '1D' "$SRC"
grep -q '1M' "$SRC"
grep -q '3M' "$SRC"
grep -q '6M' "$SRC"
grep -q '12M' "$SRC"
grep -q 'Datenquellen' "$SRC"
grep -q 'Trade Republic öffnen' "$SRC"
```

- [ ] **Step 2: Run and confirm RED**

```bash
bash android/tests/test-investment-detail-ui.sh
```
Expected: FAIL because the file does not exist.

- [ ] **Step 3: Implement market-item detail layout**

Render sections in this order:
1. Back action + name/ticker/ISIN/type.
2. Current price/day change + recommendation + total score.
3. `PersonalRecommendation`: monthly allocation, current weight, concentration label, explanation.
4. Five score rows using helper `scoreOrUnavailable(value: Int?)`.
5. Coverage and `DataFreshness.summarize(item)` status pill.
6. Momentum row cards only for non-null horizons.
7. Fundamentals rows only for available normalized fields; if all are null, show “Fundamentaldaten nicht verfügbar”.
8. Top recommendation reasons.
9. Data sources and timestamps.
10. Watchlist, portfolio/transaction and Trade Republic actions.

Do not call `ApiClient`, `fetch`, or any provider method from this screen.

- [ ] **Step 4: Implement custom-item merged-detail behavior**

When `customItem != null`, use its local name/ticker/ISIN/type and saved Trade-Republic URL as authoritative metadata, while still using the merged `item` for current quote fields. If `item.scoreTotal == null` and all five score components are null, show “Analyse V2 für diesen eigenen Wert nicht verfügbar” instead of rendering zero score rows. Manual EUR price may be shown as local fallback context. Position stats remain available from `position`.

If both `item` and `customItem` are null, show “Wertpapier nicht mehr verfügbar” and an `Zurück` action; do not invoke any other callback.

- [ ] **Step 5: Add contract to workflow and confirm GREEN**

Add `android/tests/test-investment-detail-ui.sh` to the loop in `.github/workflows/android-contract-tests.yml`, then run:

```bash
bash android/tests/test-investment-detail-ui.sh
```
Expected: PASS.

- [ ] **Step 6: Commit detail UI**

```bash
git add android/app/src/main/java/de/tobias/investmentradar/InvestmentDetailScreen.kt android/tests/test-investment-detail-ui.sh .github/workflows/android-contract-tests.yml
git commit -m "feat: add investment detail screen"
```

### Task 3: Detail navigation from Radar and Portfolio shell

**Files:**
- Modify: `android/app/src/main/java/de/tobias/investmentradar/MainActivity.kt`
- Modify: `android/app/src/main/java/de/tobias/investmentradar/RadarScreen.kt`

**Interfaces:**
- Produces transient app state:
```kotlin
var selectedDetailId by rememberSaveable { mutableStateOf<String?>(null) }
var detailReturnTab by rememberSaveable { mutableIntStateOf(0) }
```

- [ ] **Step 1: Add failing wiring assertion**

Extend the detail contract:
```bash
grep -q 'selectedDetailId' android/app/src/main/java/de/tobias/investmentradar/MainActivity.kt
grep -q 'onOpenDetail' android/app/src/main/java/de/tobias/investmentradar/RadarScreen.kt
```

- [ ] **Step 2: Run and confirm RED**

```bash
bash android/tests/test-investment-detail-ui.sh
```
Expected: FAIL on missing `selectedDetailId` wiring.

- [ ] **Step 3: Wire selected id without adding navigation dependency**

Inside the Ready branch resolve both sources because custom items are also present in `s.data.items`:
```kotlin
val selectedMarketItem = s.data.items.firstOrNull { it.id == selectedDetailId }
val selectedCustomItem = customItems.firstOrNull { it.id == selectedDetailId }
```

If `selectedDetailId != null`, render `InvestmentDetailScreen` instead of the tab body and pass both resolved values. `onBack` clears the id and restores `detailReturnTab`. Any bottom-nav selection clears `selectedDetailId` before changing tabs.

For personal recommendation:
```kotlin
val currentValues = PortfolioAnalysis.values(s.data.items, positions, customItems)
val personalById = RecommendationEngine.plan(s.data.items, budget, currentValues).items.associateBy { it.itemId }
```

- [ ] **Step 4: Add visible Details action to Radar cards and the existing portfolio cards**

Use one id-based callback everywhere:
```kotlin
onOpenDetail: (String) -> Unit
```
Radar passes `item.id`. Existing portfolio cards, including custom items, pass their item id. `MainActivity` sets `detailReturnTab = tab` and `selectedDetailId = id`.

- [ ] **Step 5: Run contracts + unit suite**

```bash
bash android/tests/test-investment-detail-ui.sh
cd android && ./gradlew testReleaseUnitTest
```
Expected: PASS.

- [ ] **Step 6: Commit navigation**

```bash
git add android/app/src/main/java/de/tobias/investmentradar/MainActivity.kt android/app/src/main/java/de/tobias/investmentradar/RadarScreen.kt android/tests/test-investment-detail-ui.sh
git commit -m "feat: navigate to shared investment details"
```

### Task 4: Verification checkpoint

- [ ] **Step 1:** Run all Android contract scripts including the new detail contract.
- [ ] **Step 2:** Run `cd android && ./gradlew testReleaseUnitTest`.
- [ ] **Step 3:** Push and require both Android Contract Tests and Android JVM Tests to be green before starting the portfolio plan.
