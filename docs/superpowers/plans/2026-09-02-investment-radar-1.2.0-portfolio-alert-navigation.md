# Investment Radar 1.2.0 Portfolio Dashboard & Alert Navigation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Upgrade the portfolio into a KPI dashboard and make alert taps open the shared investment detail screen safely.

**Architecture:** Portfolio aggregation is pure Kotlin and explicitly tracks incomplete-price coverage. Compose renders summary KPIs and position rows from that model. Alert navigation reuses the detail-selection state created by the investment-detail plan; no second detail path is introduced.

**Tech Stack:** Kotlin, Jetpack Compose Material 3, JUnit 4, existing `PortfolioPosition`, `RecommendationEngine`, `AlertCenterState`.

**Spec:** `docs/superpowers/specs/2026-09-02-investment-radar-1.2.0-release-expansion-design.md`

## Global Constraints

- Portfolio data stays local on Android.
- Missing market prices must never be treated as zero for current value or performance.
- If one or more held positions lack a usable price, current portfolio value is labeled partial and total P/L percentage is not presented as complete.
- Position weighting is based only on calculable current values and must be marked incomplete when not all held positions have prices.
- Existing 40% concentration logic remains unchanged.
- Alert tap marks the alert read first, then opens the common detail screen when possible.
- Unknown/missing alert `itemId` must not crash; alert remains readable and user gets a clear message.

---

## File Structure

- Create `android/app/src/main/java/de/tobias/investmentradar/PortfolioMetrics.kt` — pure aggregate/position KPI model.
- Create `android/app/src/main/java/de/tobias/investmentradar/PortfolioDashboard.kt` — Compose KPI summary and position list.
- Create `android/app/src/test/java/de/tobias/investmentradar/PortfolioMetricsTest.kt`.
- Modify `android/app/src/main/java/de/tobias/investmentradar/MainActivity.kt` — replace private portfolio body and route alert opens to `selectedDetailId`.
- Modify `android/app/src/main/java/de/tobias/investmentradar/AlertsScreen.kt` only if a missing-item callback/message is cleaner there; do not duplicate item resolution.
- Add `android/tests/test-portfolio-dashboard-ui.sh`.
- Modify `android/tests/test-alert-center-wiring.sh`.
- Modify `.github/workflows/android-contract-tests.yml`.

### Task 1: Pure portfolio KPI aggregation

**Files:**
- Create: `android/app/src/main/java/de/tobias/investmentradar/PortfolioMetrics.kt`
- Create: `android/app/src/test/java/de/tobias/investmentradar/PortfolioMetricsTest.kt`

**Interfaces:**
- Consumes: `List<InvestmentItem>`, `Map<String, PortfolioPosition>`, `List<CustomInvestment>`.
- Produces:
```kotlin
data class PortfolioPositionMetrics(
    val itemId: String,
    val investedCostBasis: Double,
    val currentValue: Double?,
    val unrealizedProfitLoss: Double?,
    val unrealizedProfitLossPct: Double?,
    val realizedProfitLoss: Double,
    val totalProfitLoss: Double?,
    val totalProfitLossPct: Double?,
    val weightPct: Double?,
    val hasUsablePrice: Boolean
)

data class PortfolioMetricsSummary(
    val investedCostBasis: Double,
    val calculableCurrentValue: Double,
    val currentValueComplete: Boolean,
    val missingPriceCount: Int,
    val totalProfitLoss: Double?,
    val totalProfitLossPct: Double?,
    val heldPositionCount: Int,
    val largestPositionId: String?,
    val largestWeightPct: Double?,
    val positions: List<PortfolioPositionMetrics>
)

object PortfolioMetrics {
    fun calculate(
        items: List<InvestmentItem>,
        positions: Map<String, PortfolioPosition>,
        customItems: List<CustomInvestment>
    ): PortfolioMetricsSummary
}
```

- [ ] **Step 1: Write failing complete-portfolio test**

```kotlin
@Test
fun completePortfolioCalculatesValueProfitAndLargestWeight() {
    val a = fixtureItem(id = "a", priceEur = 20.0)
    val b = fixtureItem(id = "b", priceEur = 10.0)
    val positions = mapOf(
        "a" to PortfolioPosition("a", investedAmount = 100.0, shares = 10.0),
        "b" to PortfolioPosition("b", investedAmount = 50.0, shares = 5.0)
    )
    val result = PortfolioMetrics.calculate(listOf(a, b), positions, emptyList())
    assertTrue(result.currentValueComplete)
    assertEquals(250.0, result.calculableCurrentValue, 0.001)
    assertEquals(100.0, result.totalProfitLoss, 0.001)
    assertEquals("a", result.largestPositionId)
    assertEquals(80.0, result.largestWeightPct!!, 0.001)
}
```

- [ ] **Step 2: Write failing missing-price test**

```kotlin
@Test
fun missingPriceProducesPartialValueAndNoCompletePortfolioPerformance() {
    val priced = fixtureItem(id = "a", priceEur = 20.0)
    val missing = fixtureItem(id = "b", priceEur = null)
    val positions = mapOf(
        "a" to PortfolioPosition("a", investedAmount = 100.0, shares = 10.0),
        "b" to PortfolioPosition("b", investedAmount = 50.0, shares = 5.0)
    )
    val result = PortfolioMetrics.calculate(listOf(priced, missing), positions, emptyList())
    assertFalse(result.currentValueComplete)
    assertEquals(1, result.missingPriceCount)
    assertEquals(200.0, result.calculableCurrentValue, 0.001)
    assertNull(result.totalProfitLoss)
    assertNull(result.totalProfitLossPct)
    assertNull(result.positions.first { it.itemId == "b" }.currentValue)
}
```

- [ ] **Step 3: Run focused tests and confirm RED**

```bash
cd android && ./gradlew testReleaseUnitTest --tests 'de.tobias.investmentradar.PortfolioMetricsTest'
```
Expected: compile failure because `PortfolioMetrics` does not exist.

- [ ] **Step 4: Implement calculation with explicit price usability**

Resolve price in this order:
```kotlin
val marketPrice = itemById[id]?.priceEur?.takeIf { it.isFinite() && it > 0.0 }
val manualPrice = customById[id]?.manualPriceEur?.takeIf { it.isFinite() && it > 0.0 }
val usablePrice = marketPrice ?: manualPrice
```

Do not fall back to `investedAmount` for `currentValue` in this new KPI model. A missing current price stays `null`.

Compute total portfolio P/L only when `missingPriceCount == 0`. Position-level realized P/L may still be shown because it does not require a current quote.

Weight denominator is `calculableCurrentValue`; positions without usable price have `weightPct = null`.

- [ ] **Step 5: Run focused tests and confirm GREEN**

```bash
cd android && ./gradlew testReleaseUnitTest --tests 'de.tobias.investmentradar.PortfolioMetricsTest'
```
Expected: PASS.

- [ ] **Step 6: Commit metrics**

```bash
git add android/app/src/main/java/de/tobias/investmentradar/PortfolioMetrics.kt android/app/src/test/java/de/tobias/investmentradar/PortfolioMetricsTest.kt
git commit -m "feat: add portfolio dashboard metrics"
```

### Task 2: Portfolio Dashboard Compose extraction

**Files:**
- Create: `android/app/src/main/java/de/tobias/investmentradar/PortfolioDashboard.kt`
- Add: `android/tests/test-portfolio-dashboard-ui.sh`
- Modify: `android/app/src/main/java/de/tobias/investmentradar/MainActivity.kt`
- Modify: `.github/workflows/android-contract-tests.yml`

**Interfaces:**
- Consumes: `PortfolioMetricsSummary`, market/custom items, `PersonalRecommendation` map.
- Produces:
```kotlin
@Composable
fun PortfolioDashboard(
    items: List<InvestmentItem>,
    positions: Map<String, PortfolioPosition>,
    customItems: List<CustomInvestment>,
    personalById: Map<String, PersonalRecommendation>,
    onOpenDetail: (String) -> Unit,
    onEdit: (InvestmentItem) -> Unit,
    onRemove: (String) -> Unit,
    onAddCustom: () -> Unit,
    onEditCustom: (CustomInvestment) -> Unit,
    onRemoveCustom: (String) -> Unit
)
```

- [ ] **Step 1: Write RED UI contract**

```bash
#!/usr/bin/env bash
set -euo pipefail
SRC="android/app/src/main/java/de/tobias/investmentradar/PortfolioDashboard.kt"
test -f "$SRC"
grep -q 'Depotwert' "$SRC"
grep -q 'Einstand' "$SRC"
grep -q 'Gewinn / Verlust' "$SRC"
grep -q 'Größte Position' "$SRC"
grep -q 'Teilwert' "$SRC"
grep -q 'Kurs fehlt' "$SRC"
grep -q 'onOpenDetail' "$SRC"
```

- [ ] **Step 2: Run and confirm RED**

```bash
bash android/tests/test-portfolio-dashboard-ui.sh
```
Expected: FAIL.

- [ ] **Step 3: Implement KPI header**

Use `PortfolioMetrics.calculate(...)` once per relevant state change via `remember(items, positions, customItems)`.

Display:
- `Depotwert` when complete, otherwise `Teilwert`.
- `Einstand` from `investedCostBasis`.
- `Gewinn / Verlust` absolute + percent only when `totalProfitLoss` and `totalProfitLossPct` are non-null.
- held count.
- largest position name/ticker + weight when available.
- if `missingPriceCount > 0`, visible warning: `"${missingPriceCount} Position(en) ohne verwertbaren Kurs – Gesamtperformance unvollständig."`

- [ ] **Step 4: Implement position cards from metrics**

For each held position show current value when calculable, cost basis, P/L, weight, recommendation/score, personal monthly allocation, concentration label and a `Details` action. Do not show `0 €` for missing current value; show `Kurs fehlt`.

- [ ] **Step 5: Remove the old private portfolio body from `MainActivity.kt` and call `PortfolioDashboard`**

`MainActivity` resolves `personalById` once and passes callbacks. Do not leave duplicated KPI math in the activity.

- [ ] **Step 6: Add contract to CI and confirm GREEN**

```bash
bash android/tests/test-portfolio-dashboard-ui.sh
cd android && ./gradlew testReleaseUnitTest
```
Expected: PASS.

- [ ] **Step 7: Commit portfolio UI**

```bash
git add android/app/src/main/java/de/tobias/investmentradar/PortfolioDashboard.kt android/app/src/main/java/de/tobias/investmentradar/MainActivity.kt android/tests/test-portfolio-dashboard-ui.sh .github/workflows/android-contract-tests.yml
git commit -m "feat: add portfolio dashboard v2"
```

### Task 3: Alert-to-detail navigation

**Files:**
- Modify: `android/app/src/main/java/de/tobias/investmentradar/MainActivity.kt`
- Modify: `android/tests/test-alert-center-wiring.sh`
- Add test: `android/app/src/test/java/de/tobias/investmentradar/AlertNavigationTest.kt` only if a pure resolver helper is introduced.

**Interfaces:**
- Consumes: existing `StoredAlert.alert.itemId`, selected-detail navigation from the investment-detail plan.
- Produces optional pure resolver:
```kotlin
sealed interface AlertNavigationTarget {
    data class Market(val itemId: String) : AlertNavigationTarget
    data class Custom(val itemId: String) : AlertNavigationTarget
    data object Missing : AlertNavigationTarget
}

object AlertNavigation {
    fun resolve(itemId: String, marketIds: Set<String>, customIds: Set<String>): AlertNavigationTarget
}
```
Use this helper if it keeps `MainActivity` simpler; otherwise the exact same resolution may stay inline because it is only a few lines.

- [ ] **Step 1: Update alert wiring contract to require direct detail navigation**

```bash
grep -q 'markAlertRead' android/app/src/main/java/de/tobias/investmentradar/MainActivity.kt
grep -q 'selectedDetailId = stored.alert.itemId' android/app/src/main/java/de/tobias/investmentradar/MainActivity.kt
```
Also reject the old behavior if still present:
```bash
! grep -q 'radarFocusId = stored.alert.itemId' android/app/src/main/java/de/tobias/investmentradar/MainActivity.kt
```

- [ ] **Step 2: Run contract and confirm RED**

```bash
bash android/tests/test-alert-center-wiring.sh
```
Expected: FAIL until old Radar-focus navigation is replaced.

- [ ] **Step 3: Implement open behavior**

On alert tap:
```kotlin
vm.markAlertRead(stored.alert.id)
val id = stored.alert.itemId
when {
    s.data.items.any { it.id == id } -> selectedDetailId = id
    customItems.any { it.id == id } -> selectedDetailId = id
    else -> missingAlertItemMessage = "Das Wertpapier ist im aktuellen Radar nicht mehr verfügbar."
}
```

Add `var missingAlertItemMessage by rememberSaveable { mutableStateOf<String?>(null) }` and render an `AlertDialog` with only an OK action when non-null. The alert remains in the center and its text remains readable.

- [ ] **Step 4: Run alert contract + full JVM suite**

```bash
bash android/tests/test-alert-center-wiring.sh
cd android && ./gradlew testReleaseUnitTest
```
Expected: PASS.

- [ ] **Step 5: Commit alert navigation**

```bash
git add android/app/src/main/java/de/tobias/investmentradar/MainActivity.kt android/tests/test-alert-center-wiring.sh
git commit -m "feat: open investments directly from alerts"
```

### Task 4: Verification checkpoint

- [ ] **Step 1:** Run all contract scripts, including `test-investment-detail-ui.sh` and `test-portfolio-dashboard-ui.sh`.
- [ ] **Step 2:** Run `cd android && ./gradlew testReleaseUnitTest` and require zero failures.
- [ ] **Step 3:** Push and require Android Contract Tests + Android JVM Tests green before the final release plan.
