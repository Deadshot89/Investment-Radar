# Investment Radar 1.2.0 Radar Search & Filter Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the current single-filter Radar implementation with reusable local search, combinable filters and the approved sort options, while moving Radar logic out of `MainActivity.kt`.

**Architecture:** Pure filtering/sorting lives in a focused Kotlin engine with immutable state. Compose owns only transient session UI state and delegates all list calculation to the engine. `MainViewModel` already merges custom-investment quote/fallback items into `DashboardData.items`, so the same engine covers the 40 curated values and local custom investments without another data path. The extracted Radar screen uses Material 3 directly and does not depend on file-private UI helpers in `MainActivity.kt`.

**Tech Stack:** Kotlin, Jetpack Compose Material 3, JUnit 4, GitHub Actions shell regression tests.

**Spec:** `docs/superpowers/specs/2026-09-02-investment-radar-1.2.0-release-expansion-design.md`

## Global Constraints

- Search covers only already-loaded `DashboardData.items`; this includes local custom investments after `MainViewModel.refresh` merges their quote/fallback item.
- No external unrestricted symbol search.
- Search fields: name, ticker/symbol, ISIN and type.
- Filters must be combinable: recommendation, type, held/not held, watchlist-only, data quality and risk.
- Data quality: FULL = coverage >= 70, REDUCED = coverage 50..69, INSUFFICIENT = coverage < 50 or null.
- Risk buckets: LOW = risk 1..2, MEDIUM = risk 3, HIGH = risk 4..5.
- Sort options: score descending, personal allocation descending, 6M momentum descending, day ascending, day descending, name A-Z.
- Null numeric sort values always sort last.
- Default sort is score descending.
- Filter/sort state survives recomposition during the current app session but need not persist across app restarts.
- No new market-data request may be triggered by search/filter/sort.

---

## File Structure

- Create `android/app/src/main/java/de/tobias/investmentradar/RadarFilterState.kt` — filter enums, state and pure `RadarFilterEngine`.
- Create `android/app/src/main/java/de/tobias/investmentradar/RadarScreen.kt` — self-contained Material 3 Radar UI.
- Create `android/app/src/test/java/de/tobias/investmentradar/RadarFilterStateTest.kt` — pure search/filter/sort tests with a local fixture helper.
- Modify `android/app/src/main/java/de/tobias/investmentradar/MainActivity.kt` — remove private Radar implementation and call extracted `RadarScreen`.
- Modify `android/tests/test-analysis-v2-ui.sh` — assert search/filter/sort controls live in the extracted screen.

### Task 1: Pure Radar filter model

**Files:**
- Create: `android/app/src/main/java/de/tobias/investmentradar/RadarFilterState.kt`
- Create: `android/app/src/test/java/de/tobias/investmentradar/RadarFilterStateTest.kt`

**Interfaces:**
- Consumes: `InvestmentItem` and `RecommendationPresentation.effectiveRecommendation(item)`.
- Produces:
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

- [ ] **Step 1: Write the test file with a local fixture helper and failing search/filter tests**

At the bottom of `RadarFilterStateTest.kt`, define:
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

Tests:
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
    val buyHeld = radarItem("a", recommendation = "BUY", type = "ETF", coverage = 82, risk = 2)
    val buyNotHeld = radarItem("b", recommendation = "BUY", type = "ETF", coverage = 82, risk = 2)
    val state = RadarFilterState(
        recommendation = RadarRecommendationFilter.BUY,
        type = RadarTypeFilter.ETF,
        holding = RadarHoldingFilter.HELD,
        dataQuality = RadarDataQualityFilter.FULL,
        risk = RadarRiskFilter.LOW
    )
    val result = RadarFilterEngine.apply(listOf(buyHeld, buyNotHeld), state, setOf("a"), emptySet(), emptyMap())
    assertEquals(listOf("a"), result.map { it.id })
}
```

- [ ] **Step 2: Run focused tests and confirm RED**

```bash
cd android && ./gradlew testReleaseUnitTest --tests 'de.tobias.investmentradar.RadarFilterStateTest'
```
Expected: compile failure because `RadarFilterState`/`RadarFilterEngine` do not exist.

- [ ] **Step 3: Implement immutable state and matching rules**

Implement `matchesQuery` with `trim()` and case-insensitive matching against `name`, `ticker`, `isin`, `type`.

Recommendation matching must use:
```kotlin
RecommendationPresentation.effectiveRecommendation(item)
```
Mapping:
```text
BUY -> BUY
WATCH -> WATCH
NO_BUY -> NO_BUY
REVIEW -> REVIEW
```

Type matching:
```kotlin
val isEtf = item.type.equals("ETF", ignoreCase = true)
```
Everything not ETF is STOCK for this filter.

Coverage/risk mapping follows the exact global constraints above.

- [ ] **Step 4: Add sorting tests**

```kotlin
@Test
fun sixMonthMomentumSortsNullLast() {
    val high = radarItem("high", momentumM6 = 18.0)
    val low = radarItem("low", momentumM6 = -3.0)
    val missing = radarItem("missing", momentumM6 = null)
    val result = RadarFilterEngine.apply(
        listOf(missing, low, high),
        RadarFilterState(sort = RadarSortOption.MOMENTUM_6M),
        emptySet(), emptySet(), emptyMap()
    )
    assertEquals(listOf("high", "low", "missing"), result.map { it.id })
}

@Test
fun allocationSortUsesPersonalPlanAmounts() {
    val a = radarItem("a")
    val b = radarItem("b")
    val result = RadarFilterEngine.apply(
        listOf(a, b),
        RadarFilterState(sort = RadarSortOption.ALLOCATION),
        emptySet(), emptySet(), mapOf("a" to 10, "b" to 60)
    )
    assertEquals(listOf("b", "a"), result.map { it.id })
}

@Test
fun dayAscendingAndDescendingKeepNullLast() {
    val up = radarItem("up", percentChange = 3.0)
    val down = radarItem("down", percentChange = -2.0)
    val missing = radarItem("missing", percentChange = null)
    val asc = RadarFilterEngine.apply(listOf(missing, up, down), RadarFilterState(sort = RadarSortOption.DAY_ASC), emptySet(), emptySet(), emptyMap())
    val desc = RadarFilterEngine.apply(listOf(missing, up, down), RadarFilterState(sort = RadarSortOption.DAY_DESC), emptySet(), emptySet(), emptyMap())
    assertEquals(listOf("down", "up", "missing"), asc.map { it.id })
    assertEquals(listOf("up", "down", "missing"), desc.map { it.id })
}
```

- [ ] **Step 5: Run focused tests and confirm GREEN**

```bash
cd android && ./gradlew testReleaseUnitTest --tests 'de.tobias.investmentradar.RadarFilterStateTest'
```
Expected: PASS.

- [ ] **Step 6: Commit pure model**

```bash
git add android/app/src/main/java/de/tobias/investmentradar/RadarFilterState.kt android/app/src/test/java/de/tobias/investmentradar/RadarFilterStateTest.kt
git commit -m "feat: add combinable radar filters"
```

### Task 2: Extract and upgrade Radar Compose screen

**Files:**
- Create: `android/app/src/main/java/de/tobias/investmentradar/RadarScreen.kt`
- Modify: `android/app/src/main/java/de/tobias/investmentradar/MainActivity.kt`
- Modify: `android/tests/test-analysis-v2-ui.sh`

**Interfaces:**
- Consumes: `RadarFilterEngine`, `PersonalRecommendation`, current merged `InvestmentItem` list.
- Produces:
```kotlin
@Composable
fun RadarScreen(
    items: List<InvestmentItem>,
    holdingIds: Set<String>,
    watchlistIds: Set<String>,
    personalById: Map<String, PersonalRecommendation>,
    onToggleWatchlist: (String) -> Unit,
    onBought: (InvestmentItem) -> Unit,
    onEditInvestment: (InvestmentItem) -> Unit,
    onOpenDetail: (String) -> Unit
)
```

- [ ] **Step 1: Update shell contract and confirm RED**

Required checks:
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
grep -q 'onOpenDetail' "$SRC"
```

Run:
```bash
bash android/tests/test-analysis-v2-ui.sh
```
Expected: FAIL because `RadarScreen.kt` does not exist.

- [ ] **Step 2: Implement the extracted screen without using `MainActivity.kt` file-private helpers**

Use only Material 3 primitives (`Card`, `Text`, `FilterChip`, `OutlinedTextField`, `Button`, `OutlinedButton`) plus public modules such as `RecommendationPresentation`, `ScoreBreakdownCard` and `TradeRepublicNavigator`.

Use session state:
```kotlin
var filters by remember { mutableStateOf(RadarFilterState()) }
```
This intentionally satisfies the approved current-session requirement without adding persistence.

Compute:
```kotlin
val visibleItems = remember(items, filters, holdingIds, watchlistIds, personalById) {
    RadarFilterEngine.apply(
        items = items,
        state = filters,
        holdingIds = holdingIds,
        watchlistIds = watchlistIds,
        allocationById = personalById.mapValues { it.value.allocationEur }
    )
}
```

UI groups:
- Search field.
- Recommendation: Alle/Kaufen/Beobachten/Nicht kaufen/Prüfen.
- Type: Alle/Aktie/ETF.
- Depot: Alle/Im Depot/Nicht im Depot.
- Independent Watchlist-only toggle.
- Data: Alle/Vollständig/Reduziert/Unzureichend.
- Risk: Alle/Niedrig/Mittel/Hoch.
- Sort: Score/Monatskauf/6M/Tag ↑/Tag ↓/Name.

Each card shows name, ticker, recommendation, score, coverage, 6M momentum when available, day change when available, personal allocation and a `Details` action:
```kotlin
OutlinedButton(onClick = { onOpenDetail(item.id) }) { Text("Details") }
```

- [ ] **Step 3: Remove the old private `RadarSortOption` and private `RadarScreen` from `MainActivity.kt`**

Do not leave a second filtering implementation. The only Radar matching/sorting code after this task is `RadarFilterEngine`.

- [ ] **Step 4: Compute personal allocation once in the Ready branch and pass it**

```kotlin
val currentValues = PortfolioAnalysis.values(s.data.items, positions, customItems)
val radarPersonalById = RecommendationEngine
    .plan(s.data.items, budget, currentValues)
    .items
    .associateBy { it.itemId }
```

Call the extracted screen with `onOpenDetail = { id -> /* detail plan wires selectedDetailId */ }`. Until the detail plan runs, use a no-op lambda or existing Radar focus behavior only if needed for compilation; the detail plan replaces it immediately. Do not add new navigation logic to `RadarFilterEngine`.

- [ ] **Step 5: Run contract + JVM suite and confirm GREEN**

```bash
bash android/tests/test-analysis-v2-ui.sh
cd android && ./gradlew testReleaseUnitTest
```
Expected: PASS.

- [ ] **Step 6: Commit extracted Radar UI**

```bash
git add android/app/src/main/java/de/tobias/investmentradar/RadarScreen.kt android/app/src/main/java/de/tobias/investmentradar/MainActivity.kt android/tests/test-analysis-v2-ui.sh
git commit -m "feat: add radar search filters and sorting"
```

### Task 3: Branch verification checkpoint

- [ ] **Step 1: Run permanent contract scripts**

```bash
for f in \
  android/tests/test-analysis-v2-ui.sh \
  android/tests/test-portfolio-allocation-ui.sh \
  android/tests/test-alert-policy-wiring.sh \
  android/tests/test-alert-center-ui.sh \
  android/tests/test-alert-center-wiring.sh \
  android/tests/test-update-status-ui.sh \
  android/tests/test-trade-republic-navigator-wiring.sh; do bash "$f"; done
```
Expected: all PASS.

- [ ] **Step 2: Run Android unit suite**

```bash
cd android && ./gradlew testReleaseUnitTest
```
Expected: PASS with zero failed tests.

- [ ] **Step 3: Push branch and require GitHub Android Contract Tests + Android JVM Tests green before moving to the detail plan.**
