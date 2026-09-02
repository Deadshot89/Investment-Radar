# Investment Radar 1.2.0 Radar Search & Filter Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the current single-filter Radar implementation with reusable local search, combinable filters and the approved sort options, while moving Radar logic out of `MainActivity.kt`.

**Architecture:** Pure filtering/sorting lives in a focused Kotlin engine with immutable state. Compose owns only transient session UI state and delegates all list calculation to the engine. `MainViewModel` already merges custom-investment quote/fallback items into `DashboardData.items`, so the same engine covers curated and local values. The extracted screen uses Material 3 directly and does not depend on file-private helpers in `MainActivity.kt`.

**Tech Stack:** Kotlin, Jetpack Compose Material 3, JUnit 4, GitHub Actions shell regression tests.

**Spec:** `docs/superpowers/specs/2026-09-02-investment-radar-1.2.0-release-expansion-design.md`

## Global Constraints

- Search only already-loaded `DashboardData.items`; no external symbol search.
- Search: name, ticker, ISIN, type.
- Combinable filters: recommendation, type, held/not-held, watchlist-only, data quality, risk.
- Data quality: FULL >=70, REDUCED 50..69, INSUFFICIENT <50 or null.
- Risk: LOW 1..2, MEDIUM 3, HIGH 4..5.
- Sort: score desc, monthly allocation desc, 6M momentum desc, day asc, day desc, name A-Z.
- Null numeric sort values always last.
- Default sort: score desc.
- UI state persists only for the current Compose session.
- Filtering/sorting must not call `ApiClient` or any provider.

---

## File Structure

- Create `android/app/src/main/java/de/tobias/investmentradar/RadarFilterState.kt`.
- Create `android/app/src/main/java/de/tobias/investmentradar/RadarScreen.kt`.
- Create `android/app/src/test/java/de/tobias/investmentradar/RadarFilterStateTest.kt`.
- Modify `android/app/src/main/java/de/tobias/investmentradar/MainActivity.kt`.
- Modify `android/tests/test-analysis-v2-ui.sh`.

### Task 1: Pure Radar filter model

**Interfaces:**
```kotlin
enum class RadarRecommendationFilter { ALL, BUY, WATCH, NO_BUY, REVIEW }
enum class RadarTypeFilter { ALL, STOCK, ETF }
enum class RadarHoldingFilter { ALL, HELD, NOT_HELD }
enum class RadarDataQualityFilter { ALL, FULL, REDUCED, INSUFFICIENT }
enum class RadarRiskFilter { ALL, LOW, MEDIUM, HIGH }
enum class RadarSortOption { SCORE, ALLOCATION, MOMENTUM_6M, DAY_ASC, DAY_DESC, NAME }

data class RadarFilterState(
    val query: String = "",
    val recommendation: RadarRecommendationFilter = RadarRecommendationFilter.ALL,
    val type: RadarTypeFilter = RadarTypeFilter.ALL,
    val holding: RadarHoldingFilter = RadarHoldingFilter.ALL,
    val watchlistOnly: Boolean = false,
    val dataQuality: RadarDataQualityFilter = RadarDataQualityFilter.ALL,
    val risk: RadarRiskFilter = RadarRiskFilter.ALL,
    val sort: RadarSortOption = RadarSortOption.SCORE
)

object RadarFilterEngine {
    fun apply(
        items: List<InvestmentItem>,
        state: RadarFilterState,
        holdingIds: Set<String>,
        watchlistIds: Set<String>,
        allocationById: Map<String, Int>
    ): List<InvestmentItem>
}
```

- [ ] **Step 1: Write failing tests with a local fixture helper**

```kotlin
private fun radarItem(
    id: String,
    name: String = id,
    ticker: String = id.uppercase(),
    isin: String = "",
    type: String = "AKTIE",
    recommendation: String = "WATCH",
    coverage: Int? = 100,
    risk: Int = 2,
    scoreTotal: Int? = 70,
    percentChange: Double? = null,
    momentumM6: Double? = null
): InvestmentItem = testInvestmentItem(
    id = id,
    type = type,
    recommendation = recommendation,
    scoreTotal = scoreTotal,
    coverage = coverage,
    risk = risk
).copy(
    name = name,
    ticker = ticker,
    isin = isin,
    percentChange = percentChange,
    momentum = MomentumSnapshot(m6 = momentumM6)
)
```

```kotlin
@Test
fun searchMatchesNameTickerIsinAndType() {
    val stock = radarItem("msft", name = "Microsoft", ticker = "MSFT", isin = "US5949181045", type = "Aktie")
    val etf = radarItem("spyi", name = "SPDR ACWI IMI", ticker = "SPYI", isin = "IE00B3YLTY66", type = "ETF")
    listOf("micro", "MSFT", "594918", "aktie").forEach { query ->
        val result = RadarFilterEngine.apply(listOf(stock, etf), RadarFilterState(query = query), emptySet(), emptySet(), emptyMap())
        assertEquals(listOf("msft"), result.map { it.id })
    }
}

@Test
fun filtersCombineInsteadOfReplacingEachOther() {
    val a = radarItem("a", recommendation = "BUY", type = "ETF", coverage = 82, risk = 2)
    val b = radarItem("b", recommendation = "BUY", type = "ETF", coverage = 82, risk = 2)
    val state = RadarFilterState(
        recommendation = RadarRecommendationFilter.BUY,
        type = RadarTypeFilter.ETF,
        holding = RadarHoldingFilter.HELD,
        dataQuality = RadarDataQualityFilter.FULL,
        risk = RadarRiskFilter.LOW
    )
    assertEquals(
        listOf("a"),
        RadarFilterEngine.apply(listOf(a, b), state, setOf("a"), emptySet(), emptyMap()).map { it.id }
    )
}
```

- [ ] **Step 2: Run RED**

```bash
cd android && ./gradlew testReleaseUnitTest --tests 'de.tobias.investmentradar.RadarFilterStateTest'
```
Expected: compile failure because Radar filter types do not exist.

- [ ] **Step 3: Implement matching rules**

Search uses trimmed case-insensitive matching against `name`, `ticker`, `isin`, `type`.
Recommendation matching uses `RecommendationPresentation.effectiveRecommendation(item)`.
Type uses `item.type.equals("ETF", true)`; all non-ETF items are STOCK.
Coverage/risk use the exact global buckets.
Watchlist is an independent boolean gate.

- [ ] **Step 4: Add sorting tests**

```kotlin
@Test
fun momentumAndDaySortKeepNullLast() {
    val high = radarItem("high", momentumM6 = 18.0, percentChange = 3.0)
    val low = radarItem("low", momentumM6 = -3.0, percentChange = -2.0)
    val missing = radarItem("missing")

    val m6 = RadarFilterEngine.apply(listOf(missing, low, high), RadarFilterState(sort = RadarSortOption.MOMENTUM_6M), emptySet(), emptySet(), emptyMap())
    val asc = RadarFilterEngine.apply(listOf(missing, high, low), RadarFilterState(sort = RadarSortOption.DAY_ASC), emptySet(), emptySet(), emptyMap())
    val desc = RadarFilterEngine.apply(listOf(missing, low, high), RadarFilterState(sort = RadarSortOption.DAY_DESC), emptySet(), emptySet(), emptyMap())

    assertEquals(listOf("high", "low", "missing"), m6.map { it.id })
    assertEquals(listOf("low", "high", "missing"), asc.map { it.id })
    assertEquals(listOf("high", "low", "missing"), desc.map { it.id })
}

@Test
fun allocationSortUsesPersonalAmounts() {
    val a = radarItem("a")
    val b = radarItem("b")
    val result = RadarFilterEngine.apply(listOf(a, b), RadarFilterState(sort = RadarSortOption.ALLOCATION), emptySet(), emptySet(), mapOf("a" to 10, "b" to 60))
    assertEquals(listOf("b", "a"), result.map { it.id })
}
```

- [ ] **Step 5: Run GREEN**

```bash
cd android && ./gradlew testReleaseUnitTest --tests 'de.tobias.investmentradar.RadarFilterStateTest'
```
Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add android/app/src/main/java/de/tobias/investmentradar/RadarFilterState.kt android/app/src/test/java/de/tobias/investmentradar/RadarFilterStateTest.kt
git commit -m "feat: add combinable radar filters"
```

### Task 2: Extract and upgrade Radar Compose screen

**Interfaces:**
```kotlin
@Composable
fun RadarScreen(
    items: List<InvestmentItem>,
    holdingIds: Set<String>,
    watchlistIds: Set<String>,
    personalById: Map<String, PersonalRecommendation>,
    onToggleWatchlist: (String) -> Unit,
    onBought: (InvestmentItem) -> Unit,
    onEditInvestment: (InvestmentItem) -> Unit
)
```
The detail plan adds `onOpenDetail` later; this task must not ship a dead Details button.

- [ ] **Step 1: Make `test-analysis-v2-ui.sh` require the extracted screen and controls**

```bash
SRC="android/app/src/main/java/de/tobias/investmentradar/RadarScreen.kt"
test -f "$SRC"
grep -q 'Suchen nach Name, Ticker, ISIN oder Typ' "$SRC"
grep -q 'RadarRecommendationFilter' "$SRC"
grep -q 'RadarHoldingFilter' "$SRC"
grep -q 'RadarDataQualityFilter' "$SRC"
grep -q 'RadarRiskFilter' "$SRC"
grep -q 'RadarSortOption.ALLOCATION' "$SRC"
grep -q 'RadarSortOption.MOMENTUM_6M' "$SRC"
grep -q 'RadarSortOption.DAY_ASC' "$SRC"
grep -q 'RadarSortOption.DAY_DESC' "$SRC"
```

- [ ] **Step 2: Run contract and confirm RED**

```bash
bash android/tests/test-analysis-v2-ui.sh
```
Expected: FAIL because `RadarScreen.kt` does not exist.

- [ ] **Step 3: Implement self-contained Material 3 Radar UI**

Use `Card`, `Text`, `FilterChip`, `OutlinedTextField`, `Button`, `OutlinedButton`, `RecommendationPresentation`, `ScoreBreakdownCard`, `TradeRepublicNavigator`. Do not reference `private` colors/functions from `MainActivity.kt`.

Session state:
```kotlin
var filters by remember { mutableStateOf(RadarFilterState()) }
```

Visible list:
```kotlin
val visibleItems = remember(items, filters, holdingIds, watchlistIds, personalById) {
    RadarFilterEngine.apply(
        items,
        filters,
        holdingIds,
        watchlistIds,
        personalById.mapValues { it.value.allocationEur }
    )
}
```

UI groups: search; recommendation; type; depot; watchlist toggle; data quality; risk; sort. Each card shows identity, recommendation, score, coverage, 6M momentum if available, day move if available and personal monthly allocation.

- [ ] **Step 4: Remove old private `RadarSortOption` and private `RadarScreen` from `MainActivity.kt`**

`MainActivity` computes:
```kotlin
val currentValues = PortfolioAnalysis.values(s.data.items, positions, customItems)
val radarPersonalById = RecommendationEngine.plan(s.data.items, budget, currentValues).items.associateBy { it.itemId }
```
and passes that into the extracted screen. No filtering/sorting logic remains in `MainActivity`.

- [ ] **Step 5: Run GREEN**

```bash
bash android/tests/test-analysis-v2-ui.sh
cd android && ./gradlew testReleaseUnitTest
```
Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add android/app/src/main/java/de/tobias/investmentradar/RadarScreen.kt android/app/src/main/java/de/tobias/investmentradar/MainActivity.kt android/tests/test-analysis-v2-ui.sh
git commit -m "feat: add radar search filters and sorting"
```

### Task 3: Verification checkpoint

- [ ] Run all existing permanent Android contract scripts.
- [ ] Run `cd android && ./gradlew testReleaseUnitTest`.
- [ ] Push and require Android Contract Tests + Android JVM Tests green before the investment-detail plan.
