# Investment Radar 1.2.0 Radar Search & Filter Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the current single-filter Radar implementation with reusable local search, combinable filters and the approved sort options, while moving Radar logic out of `MainActivity.kt`.

**Architecture:** Pure filtering/sorting lives in a focused Kotlin engine with immutable state. Compose owns only transient session UI state and delegates all list calculation to the engine. `MainActivity` only passes data and navigation callbacks.

**Tech Stack:** Kotlin, Jetpack Compose Material 3, JUnit 4, GitHub Actions shell regression tests.

**Spec:** `docs/superpowers/specs/2026-09-02-investment-radar-1.2.0-release-expansion-design.md`

## Global Constraints

- Search covers only already-loaded Radar items and local custom investments; no external unrestricted symbol search.
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
- Create `android/app/src/main/java/de/tobias/investmentradar/RadarScreen.kt` — Compose Radar UI only.
- Create `android/app/src/test/java/de/tobias/investmentradar/RadarFilterStateTest.kt` — pure search/filter/sort tests.
- Modify `android/app/src/main/java/de/tobias/investmentradar/MainActivity.kt` — remove private Radar implementation and call extracted `RadarScreen`.
- Modify `android/tests/test-analysis-v2-ui.sh` — assert search/filter/sort controls live in the extracted screen.
- Modify `.github/workflows/android-contract-tests.yml` only if the new test script needs to be added explicitly.

### Task 1: Pure Radar filter model

**Files:**
- Create: `android/app/src/main/java/de/tobias/investmentradar/RadarFilterState.kt`
- Create: `android/app/src/test/java/de/tobias/investmentradar/RadarFilterStateTest.kt`

**Interfaces:**
- Consumes: `InvestmentItem`, `PersonalRecommendation`.
- Produces:
  - `enum class RadarRecommendationFilter { ALL, BUY, WATCH, NO_BUY, REVIEW }`
  - `enum class RadarTypeFilter { ALL, STOCK, ETF }`
  - `enum class RadarHoldingFilter { ALL, HELD, NOT_HELD }`
  - `enum class RadarDataQualityFilter { ALL, FULL, REDUCED, INSUFFICIENT }`
  - `enum class RadarRiskFilter { ALL, LOW, MEDIUM, HIGH }`
  - `enum class RadarSortOption { SCORE, ALLOCATION, MOMENTUM_6M, DAY_ASC, DAY_DESC, NAME }`
  - `data class RadarFilterState(...)`
  - `RadarFilterEngine.apply(items, state, holdingIds, watchlistIds, allocationById): List<InvestmentItem>`

- [ ] **Step 1: Write failing search/filter tests**

```kotlin
@Test
fun searchMatchesNameTickerIsinAndType() {
    val stock = fixtureItem(id = "msft", name = "Microsoft", ticker = "MSFT", isin = "US5949181045", type = "Aktie")
    val etf = fixtureItem(id = "spyi", name = "SPDR ACWI IMI", ticker = "SPYI", isin = "IE00B3YLTY66", type = "ETF")
    listOf("micro", "MSFT", "594918", "aktie").forEach { query ->
        val result = RadarFilterEngine.apply(listOf(stock, etf), RadarFilterState(query = query), emptySet(), emptySet(), emptyMap())
        assertEquals(listOf("msft"), result.map { it.id })
    }
}

@Test
fun filtersCombineInsteadOfReplacingEachOther() {
    val buyHeld = fixtureItem(id = "a", recommendation = "BUY", type = "ETF", coverage = 82, risk = 2)
    val buyNotHeld = fixtureItem(id = "b", recommendation = "BUY", type = "ETF", coverage = 82, risk = 2)
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

- [ ] **Step 2: Run the focused tests and confirm RED**

Run:
```bash
cd android && ./gradlew testReleaseUnitTest --tests 'de.tobias.investmentradar.RadarFilterStateTest'
```
Expected: compile failure because `RadarFilterState`/`RadarFilterEngine` do not exist.

- [ ] **Step 3: Implement immutable filter state and matching rules**

```kotlin
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
    ): List<InvestmentItem> = items
        .asSequence()
        .filter { matchesQuery(it, state.query) }
        .filter { matchesRecommendation(it, state.recommendation) }
        .filter { matchesType(it, state.type) }
        .filter { matchesHolding(it.id, state.holding, holdingIds) }
        .filter { !state.watchlistOnly || it.id in watchlistIds }
        .filter { matchesCoverage(it.coverage, state.dataQuality) }
        .filter { matchesRisk(it.risk, state.risk) }
        .toList()
        .let { sort(it, state.sort, allocationById) }
}
```

Implement `matchesQuery` with `trim()` and case-insensitive matching against `name`, `ticker`, `isin`, `type`. Use `RecommendationPresentation.effectiveRecommendation(item)` for recommendation matching so compatibility aliases behave exactly like the rest of the app.

- [ ] **Step 4: Add sorting tests**

```kotlin
@Test
fun sixMonthMomentumSortsNullLast() {
    val high = fixtureItem(id = "high", momentumM6 = 18.0)
    val low = fixtureItem(id = "low", momentumM6 = -3.0)
    val missing = fixtureItem(id = "missing", momentumM6 = null)
    val state = RadarFilterState(sort = RadarSortOption.MOMENTUM_6M)
    val result = RadarFilterEngine.apply(listOf(missing, low, high), state, emptySet(), emptySet(), emptyMap())
    assertEquals(listOf("high", "low", "missing"), result.map { it.id })
}

@Test
fun allocationSortUsesPersonalPlanAmounts() {
    val a = fixtureItem(id = "a")
    val b = fixtureItem(id = "b")
    val state = RadarFilterState(sort = RadarSortOption.ALLOCATION)
    val result = RadarFilterEngine.apply(listOf(a, b), state, emptySet(), emptySet(), mapOf("a" to 10, "b" to 60))
    assertEquals(listOf("b", "a"), result.map { it.id })
}
```

- [ ] **Step 5: Run focused tests and confirm GREEN**

Run:
```bash
cd android && ./gradlew testReleaseUnitTest --tests 'de.tobias.investmentradar.RadarFilterStateTest'
```
Expected: PASS.

- [ ] **Step 6: Commit the pure model**

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
- Consumes: `RadarFilterEngine`, `PersonalRecommendation`, current `InvestmentItem` list.
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
    onOpenDetail: (InvestmentItem) -> Unit
)
```

- [ ] **Step 1: Update the shell contract to require the extracted screen and approved controls**

```bash
SRC="android/app/src/main/java/de/tobias/investmentradar/RadarScreen.kt"
test -f "$SRC"
grep -q 'Suchen nach Name, Ticker, ISIN oder Typ' "$SRC"
grep -q 'RadarRecommendationFilter' "$SRC"
grep -q 'RadarDataQualityFilter' "$SRC"
grep -q 'RadarRiskFilter' "$SRC"
grep -q 'RadarSortOption.ALLOCATION' "$SRC"
grep -q 'RadarSortOption.MOMENTUM_6M' "$SRC"
grep -q 'onOpenDetail' "$SRC"
```

- [ ] **Step 2: Run the contract and confirm RED**

Run:
```bash
bash android/tests/test-analysis-v2-ui.sh
```
Expected: FAIL because `RadarScreen.kt` does not exist.

- [ ] **Step 3: Move the current private `RadarScreen` out of `MainActivity.kt` and replace its one-at-a-time string filter**

Use `var filters by rememberSaveable { mutableStateOf(RadarFilterState()) }` only if every field is saveable; otherwise use `remember` because the spec requires session persistence only. Compute:

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

UI rules:
- Search field first.
- Recommendation chips: Alle, Kaufen, Beobachten, Nicht kaufen, Prüfen.
- Type chips: Alle, Aktie, ETF.
- Holding chips: Alle, Im Depot, Nicht im Depot.
- Watchlist chip is an independent toggle, not a mutually exclusive filter.
- Data chips: Alle, Vollständig, Reduziert, Unzureichend.
- Risk chips: Alle, Niedrig, Mittel, Hoch.
- Sort controls include Score, Monatskauf, 6M, Tag ↑, Tag ↓, Name.
- Each result card has a clear `Details` action calling `onOpenDetail(item)`.

- [ ] **Step 4: Change `MainActivity` to compute the personal plan once for Radar and pass `personalById`**

Inside `UiState.Ready`, derive current values with `PortfolioAnalysis.values(...)`, then:

```kotlin
val radarPlan = RecommendationEngine.plan(s.data.items, budget, currentValues)
val radarPersonalById = radarPlan.items.associateBy { it.itemId }
```

Pass the map into the extracted screen. Do not duplicate filtering logic in `MainActivity`.

- [ ] **Step 5: Run contract + JVM suite**

Run:
```bash
bash android/tests/test-analysis-v2-ui.sh
cd android && ./gradlew testReleaseUnitTest
```
Expected: PASS.

- [ ] **Step 6: Commit the extracted Radar UI**

```bash
git add android/app/src/main/java/de/tobias/investmentradar/RadarScreen.kt android/app/src/main/java/de/tobias/investmentradar/MainActivity.kt android/tests/test-analysis-v2-ui.sh
git commit -m "feat: add radar search filters and sorting"
```

### Task 3: Branch verification checkpoint

**Files:** no production changes unless a test exposes a regression.

- [ ] **Step 1: Run permanent contract workflow commands locally**

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

- [ ] **Step 3: Push branch and confirm GitHub Android Contract Tests + JVM Tests are green before moving to the detail plan.**
