# Investment Radar 1.2.0 Portfolio Dashboard & Alert Navigation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Upgrade the portfolio into a KPI dashboard and make alert taps open the shared investment detail screen safely.

**Architecture:** Portfolio aggregation is pure Kotlin and explicitly tracks incomplete-price coverage. Compose renders summary KPIs and position rows from that model with Material 3 primitives, independent of file-private helpers in `MainActivity.kt`. Alert navigation reuses `selectedDetailId` from the detail plan; no second navigation path or resolver subsystem is introduced.

**Tech Stack:** Kotlin, Jetpack Compose Material 3, JUnit 4, existing `PortfolioPosition`, `RecommendationEngine`, `AlertCenterState`.

**Spec:** `docs/superpowers/specs/2026-09-02-investment-radar-1.2.0-release-expansion-design.md`

## Global Constraints

- Portfolio data stays local on Android.
- Active/held position for KPI count means `position.shares > 0.0000001`.
- Fully sold positions may remain in transaction history but do not count as held, do not need a current quote and do not affect concentration weight.
- Missing market prices for active positions are never treated as zero.
- If one or more active positions lack usable price, current value is labeled partial and complete total P/L percentage is withheld.
- Position weighting is based only on calculable active current values and is visibly incomplete when any active position lacks price.
- Existing 40% concentration logic remains unchanged.
- Alert tap marks read first, then opens the common detail screen when the id exists.
- Unknown/missing alert item does not crash; alert remains readable and a clear message is shown.

---

## File Structure

- Create `android/app/src/main/java/de/tobias/investmentradar/PortfolioMetrics.kt`.
- Create `android/app/src/main/java/de/tobias/investmentradar/PortfolioDashboard.kt`.
- Create `android/app/src/test/java/de/tobias/investmentradar/PortfolioMetricsTest.kt`.
- Modify `android/app/src/main/java/de/tobias/investmentradar/MainActivity.kt`.
- Create `android/tests/test-portfolio-dashboard-ui.sh`.
- Modify `android/tests/test-alert-center-wiring.sh`.
- Modify `.github/workflows/android-contract-tests.yml`.

### Task 1: Pure portfolio KPI aggregation

**Interfaces:**
```kotlin
data class PortfolioPositionMetrics(
    val itemId: String,
    val active: Boolean,
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

- [ ] **Step 1: Write tests with an explicit local fixture**

```kotlin
private fun portfolioItem(id: String, priceEur: Double?): InvestmentItem =
    testInvestmentItem(id = id, priceEur = priceEur)
```

```kotlin
@Test
fun completePortfolioCalculatesValueProfitAndLargestWeight() {
    val a = portfolioItem("a", 20.0)
    val b = portfolioItem("b", 10.0)
    val positions = mapOf(
        "a" to PortfolioPosition("a", investedAmount = 100.0, shares = 10.0),
        "b" to PortfolioPosition("b", investedAmount = 50.0, shares = 5.0)
    )
    val result = PortfolioMetrics.calculate(listOf(a, b), positions, emptyList())
    assertTrue(result.currentValueComplete)
    assertEquals(250.0, result.calculableCurrentValue, 0.001)
    assertEquals(100.0, result.totalProfitLoss!!, 0.001)
    assertEquals(66.666, result.totalProfitLossPct!!, 0.01)
    assertEquals(2, result.heldPositionCount)
    assertEquals("a", result.largestPositionId)
    assertEquals(80.0, result.largestWeightPct!!, 0.001)
}

@Test
fun missingActivePriceProducesPartialValueAndNoCompletePerformance() {
    val priced = portfolioItem("a", 20.0)
    val missing = portfolioItem("b", null)
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

@Test
fun fullySoldPositionDoesNotRequireQuoteOrCountAsHeld() {
    val sold = PortfolioPosition(
        itemId = "sold",
        purchases = listOf(PortfolioPurchase("p", "01.01.2026", 100.0, 10.0)),
        sales = listOf(PortfolioSale("s", "02.01.2026", 120.0, 10.0))
    )
    val result = PortfolioMetrics.calculate(listOf(portfolioItem("sold", null)), mapOf("sold" to sold), emptyList())
    assertEquals(0, result.heldPositionCount)
    assertEquals(0, result.missingPriceCount)
    assertTrue(result.currentValueComplete)
}
```

- [ ] **Step 2: Run RED**

```bash
cd android && ./gradlew testReleaseUnitTest --tests 'de.tobias.investmentradar.PortfolioMetricsTest'
```
Expected: compile failure because `PortfolioMetrics` does not exist.

- [ ] **Step 3: Implement explicit active/price rules**

Resolve quote for active positions:
```kotlin
val marketPrice = itemById[id]?.priceEur?.takeIf { it.isFinite() && it > 0.0 }
val manualPrice = customById[id]?.manualPriceEur?.takeIf { it.isFinite() && it > 0.0 }
val usablePrice = marketPrice ?: manualPrice
val active = position.shares > 0.0000001
```

Rules:
- Active + no usable price -> `currentValue=null`, `hasUsablePrice=false`, increment missing count.
- Inactive/fully sold -> `currentValue=0.0`, `hasUsablePrice=true`, no missing count, weight 0/null.
- Never fall back to `investedAmount` for current value.
- `investedCostBasis` summary is sum of remaining cost basis (`position.investedAmount`) for active/open positions.
- Total absolute P/L when complete is sum of each `position.totalProfitLoss(usablePrice)` including realized P/L.
- Portfolio total P/L percent denominator is sum of `position.totalPurchasedAmount` across positions with transaction history; if denominator <=0, return null.
- Weight denominator is total calculable current value of active positions.

- [ ] **Step 4: Run GREEN**

```bash
cd android && ./gradlew testReleaseUnitTest --tests 'de.tobias.investmentradar.PortfolioMetricsTest'
```
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add android/app/src/main/java/de/tobias/investmentradar/PortfolioMetrics.kt android/app/src/test/java/de/tobias/investmentradar/PortfolioMetricsTest.kt
git commit -m "feat: add portfolio dashboard metrics"
```

### Task 2: Portfolio Dashboard Compose extraction

**Interfaces:**
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

- [ ] **Step 2: Run RED**

```bash
bash android/tests/test-portfolio-dashboard-ui.sh
```
Expected: FAIL because the file does not exist.

- [ ] **Step 3: Implement self-contained Material 3 dashboard**

Use `PortfolioMetrics.calculate(...)` once via:
```kotlin
val metrics = remember(items, positions, customItems) {
    PortfolioMetrics.calculate(items, positions, customItems)
}
```

Header:
- `Depotwert` if complete, `Teilwert` otherwise.
- `Einstand`.
- `Gewinn / Verlust` absolute + percent only when both are non-null.
- held count.
- largest active position + weight.
- warning when missing count >0: `"${metrics.missingPriceCount} Position(en) ohne verwertbaren Kurs – Gesamtperformance unvollständig."`

Use only Material 3 primitives and public modules; do not depend on `private` MainActivity helpers.

- [ ] **Step 4: Render position cards**

For each portfolio record show active/closed status, current value or `Kurs fehlt`, cost basis, P/L when computable, weight when computable, objective score/recommendation, personal monthly allocation, concentration label, `Details`, transaction management, and existing remove/custom-edit actions.

`Details` always calls:
```kotlin
onOpenDetail(itemId)
```

- [ ] **Step 5: Remove old private `PortfolioScreen` from `MainActivity.kt` and call `PortfolioDashboard`**

Compute `personalById` once from the same `PersonalPlan` used elsewhere; do not duplicate KPI math in MainActivity.

- [ ] **Step 6: Add contract to CI and run GREEN**

```bash
bash android/tests/test-portfolio-dashboard-ui.sh
cd android && ./gradlew testReleaseUnitTest
```
Expected: PASS.

- [ ] **Step 7: Commit**

```bash
git add android/app/src/main/java/de/tobias/investmentradar/PortfolioDashboard.kt android/app/src/main/java/de/tobias/investmentradar/MainActivity.kt android/tests/test-portfolio-dashboard-ui.sh .github/workflows/android-contract-tests.yml
git commit -m "feat: add portfolio dashboard v2"
```

### Task 3: Alert-to-detail navigation

**Interfaces:**
- Consumes `StoredAlert.alert.itemId`, `s.data.items`, `customItems`, and the existing `selectedDetailId` state from the detail plan.
- Produces no new navigation abstraction.

- [ ] **Step 1: Update wiring contract and confirm RED**

```bash
grep -q 'markAlertRead(stored.alert.id)' android/app/src/main/java/de/tobias/investmentradar/MainActivity.kt
grep -q 'selectedDetailId = id' android/app/src/main/java/de/tobias/investmentradar/MainActivity.kt
! grep -q 'radarFocusId = stored.alert.itemId' android/app/src/main/java/de/tobias/investmentradar/MainActivity.kt
```

Run:
```bash
bash android/tests/test-alert-center-wiring.sh
```
Expected: FAIL until old Radar-focus navigation is replaced.

- [ ] **Step 2: Implement exact open behavior**

Add:
```kotlin
var missingAlertItemMessage by rememberSaveable { mutableStateOf<String?>(null) }
```

On alert tap:
```kotlin
vm.markAlertRead(stored.alert.id)
val id = stored.alert.itemId
when {
    s.data.items.any { it.id == id } -> {
        detailReturnTab = 3
        selectedDetailId = id
    }
    customItems.any { it.id == id } -> {
        detailReturnTab = 3
        selectedDetailId = id
    }
    else -> missingAlertItemMessage = "Das Wertpapier ist im aktuellen Radar nicht mehr verfügbar."
}
```

Render an `AlertDialog` for `missingAlertItemMessage` with title `Wertpapier nicht verfügbar`, message text, and one `OK` button that clears the message. The stored alert is not deleted.

- [ ] **Step 3: Run GREEN**

```bash
bash android/tests/test-alert-center-wiring.sh
cd android && ./gradlew testReleaseUnitTest
```
Expected: PASS.

- [ ] **Step 4: Commit**

```bash
git add android/app/src/main/java/de/tobias/investmentradar/MainActivity.kt android/tests/test-alert-center-wiring.sh
git commit -m "feat: open investments directly from alerts"
```

### Task 4: Verification checkpoint

- [ ] Run all permanent contract scripts, including detail and portfolio dashboard contracts.
- [ ] Run `cd android && ./gradlew testReleaseUnitTest` with zero failures.
- [ ] Push and require Android Contract Tests + Android JVM Tests green before final release integration.
