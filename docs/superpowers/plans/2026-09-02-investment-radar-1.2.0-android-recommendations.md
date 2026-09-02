# Investment Radar 1.2.0 Android Recommendations Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Consume backend analysis V2 in Android, show explainable component scores and make monthly allocations portfolio-aware without uploading the user's holdings.

**Architecture:** Extend the API/data model additively, keep objective market scoring on the backend, and introduce a pure `RecommendationEngine` on Android for personal concentration/risk overlays. UI reads a presentation-ready allocation result; local holdings, purchases, sales and custom assets stay unchanged.

**Tech Stack:** Kotlin, Android 23–36, Jetpack Compose Material 3, SharedPreferences/local JSON stores, JUnit 4 JVM unit tests, existing HttpURLConnection API client.

**Spec:** `docs/superpowers/specs/2026-09-02-investment-radar-1.2.0-design.md`

## Global Constraints

- Android `versionName` remains unchanged until the integration/release plan sets `1.2.0`; final `versionCode` is 31.
- User portfolio remains on-device and is never uploaded.
- No automatic orders.
- New Android client uses backend `recommendation`/scores; old `status` remains parse-compatible.
- Missing analysis values stay nullable and must not be shown as zero-quality data.
- Monthly allocations either sum exactly to the selected budget or explicitly recommend cash when no eligible BUY exists.
- Existing purchases, sales, custom assets and watchlist storage formats remain readable.

---

### Task 1: Add JVM test support, V2 Android models and shared test fixtures

**Files:**
- Modify: `android/app/build.gradle.kts`
- Modify: `android/app/src/main/java/de/tobias/investmentradar/Models.kt`
- Modify: `android/app/src/main/java/de/tobias/investmentradar/ApiClient.kt`
- Create: `android/app/src/test/java/de/tobias/investmentradar/TestFixtures.kt`
- Create: `android/app/src/test/java/de/tobias/investmentradar/ApiModelDefaultsTest.kt`

**Interfaces:**
- `InvestmentItem` gains nullable component scores, coverage, recommendation, reasons, momentum and fundamentals.
- `recommendationFallback(recommendation, status)` preserves compatibility when connecting to a pre-1.2.0 backend during rollout.
- `testInvestmentItem(...)` is the shared JVM fixture used by later plan tasks.

- [ ] **Step 1: Add JUnit dependency and failing compatibility test**

Add:

```kotlin
testImplementation("junit:junit:4.13.2")
```

Create:

```kotlin
@Test
fun legacyStatusMapsToRecommendationWhenNewFieldMissing() {
    assertEquals("BUY", recommendationFallback("", "KAUFEN"))
    assertEquals("WATCH", recommendationFallback("", "BEOBACHTEN"))
    assertEquals("REVIEW", recommendationFallback("", "VERKAUF PRÜFEN"))
}
```

- [ ] **Step 2: Run the new test and verify RED**

```bash
cd android
gradle --no-daemon :app:testDebugUnitTest --tests '*ApiModelDefaultsTest*'
```

Expected: FAIL because `recommendationFallback` and V2 fields do not exist.

- [ ] **Step 3: Extend data classes**

Introduce:

```kotlin
data class MomentumSnapshot(
    val d1: Double?, val m1: Double?, val m3: Double?, val m6: Double?, val m12: Double?,
    val score: Int?, val coveragePct: Int?
)

data class FundamentalSnapshot(
    val pe: Double?, val priceToSales: Double?, val evToEbitda: Double?, val freeCashFlowYield: Double?,
    val revenueGrowth: Double?, val epsGrowth: Double?, val operatingMargin: Double?, val netMargin: Double?,
    val roe: Double?, val roic: Double?, val debtToEquity: Double?, val stale: Boolean, val asOf: String?
)
```

Add to `InvestmentItem`:

```kotlin
val scoreTotal: Int?,
val scoreQuality: Int?,
val scoreValuation: Int?,
val scoreGrowth: Int?,
val scoreMomentum: Int?,
val scoreRisk: Int?,
val coverage: Int?,
val recommendation: String,
val recommendationReasons: List<String>,
val momentum: MomentumSnapshot?,
val fundamentals: FundamentalSnapshot?,
val analysisAsOf: String?
```

- [ ] **Step 4: Make ApiClient additive and rollout-safe**

Parse new fields when present. Use:

```kotlin
internal fun recommendationFallback(recommendation: String, status: String): String =
    recommendation.ifBlank {
        when (status.trim().uppercase()) {
            "KAUFEN", "BUY" -> "BUY"
            "VERKAUFEN", "SELL", "VERKAUF PRÜFEN", "DRINGEND PRÜFEN", "DRINGEND_PRUEFEN", "REVIEW" -> "REVIEW"
            "NICHT KAUFEN", "NO_BUY" -> "NO_BUY"
            else -> "WATCH"
        }
    }
```

- [ ] **Step 5: Create the shared test fixture with every constructor field explicit**

```kotlin
internal fun testInvestmentItem(
    id: String = "x",
    type: String = "AKTIE",
    status: String = "BEOBACHTEN",
    recommendation: String = "WATCH",
    scoreTotal: Int? = 70,
    coverage: Int? = 100,
    risk: Int = 2,
    priceEur: Double? = null
): InvestmentItem = InvestmentItem(
    id = id,
    type = type,
    name = id,
    ticker = id.uppercase(),
    isin = "",
    tradeRepublicName = id,
    status = status,
    allocation = 0,
    risk = risk,
    price = null,
    priceEur = priceEur,
    currency = "EUR",
    fxRateToEur = 1.0,
    fxSource = "",
    fxDelayed = false,
    fxAsOf = null,
    percentChange = null,
    marketOpen = null,
    dataSource = "",
    dataDelayed = false,
    dataError = null,
    scoreTotal = scoreTotal,
    scoreQuality = null,
    scoreValuation = null,
    scoreGrowth = null,
    scoreMomentum = null,
    scoreRisk = null,
    coverage = coverage,
    recommendation = recommendation,
    recommendationReasons = emptyList(),
    momentum = null,
    fundamentals = null,
    analysisAsOf = null
)
```

- [ ] **Step 6: Add missing-score assertion and run GREEN**

```kotlin
@Test
fun missingScoresStayNull() {
    val item = testInvestmentItem(scoreTotal = null)
    assertNull(item.scoreTotal)
    assertNull(item.scoreQuality)
}
```

Run:

```bash
cd android
gradle --no-daemon :app:testDebugUnitTest
```

Expected: PASS.

- [ ] **Step 7: Commit**

```bash
git add app/build.gradle.kts app/src/main/java/de/tobias/investmentradar/Models.kt app/src/main/java/de/tobias/investmentradar/ApiClient.kt app/src/test/java/de/tobias/investmentradar/TestFixtures.kt app/src/test/java/de/tobias/investmentradar/ApiModelDefaultsTest.kt
git commit -m "feat: consume investment analysis v2"
```

---

### Task 2: Pure portfolio-aware RecommendationEngine

**Files:**
- Create: `android/app/src/main/java/de/tobias/investmentradar/RecommendationEngine.kt`
- Create: `android/app/src/test/java/de/tobias/investmentradar/RecommendationEngineTest.kt`

**Interfaces:**
- `RecommendationEngine.plan(candidates: List<InvestmentItem>, budget: Int, currentValues: Map<String, Double>) -> PersonalPlan`.
- `PersonalPlan(items: List<PersonalRecommendation>, cashAmount: Int)`.
- `PersonalRecommendation(itemId, objectiveRecommendation, scoreTotal, allocationEur, currentWeightPct, concentrationLabel, explanation)`.

- [ ] **Step 1: Write failing concentration tests using the shared fixture**

```kotlin
@Test
fun blocksNormalAllocationAboveFortyPercentConcentration() {
    val result = RecommendationEngine.plan(
        candidates = listOf(
            testInvestmentItem(id = "a", recommendation = "BUY", scoreTotal = 90),
            testInvestmentItem(id = "b", recommendation = "BUY", scoreTotal = 82)
        ),
        budget = 100,
        currentValues = mapOf("a" to 600.0, "b" to 400.0)
    )
    assertEquals(0, result.items.first { it.itemId == "a" }.allocationEur)
    assertEquals(100, result.items.first { it.itemId == "b" }.allocationEur)
}

@Test
fun eligibleAllocationsAlwaysSumToBudget() {
    val result = RecommendationEngine.plan(
        candidates = listOf(
            testInvestmentItem(id = "a", recommendation = "BUY", scoreTotal = 90),
            testInvestmentItem(id = "b", recommendation = "BUY", scoreTotal = 80),
            testInvestmentItem(id = "c", recommendation = "BUY", scoreTotal = 76)
        ),
        budget = 137,
        currentValues = emptyMap()
    )
    assertEquals(137, result.items.sumOf { it.allocationEur })
    assertEquals(0, result.cashAmount)
}

@Test
fun noEligibleBuyKeepsCash() {
    val result = RecommendationEngine.plan(
        listOf(testInvestmentItem(id = "a", recommendation = "WATCH", scoreTotal = 70)),
        100,
        emptyMap()
    )
    assertEquals(100, result.cashAmount)
}
```

- [ ] **Step 2: Run tests and verify RED**

```bash
cd android
gradle --no-daemon :app:testDebugUnitTest --tests '*RecommendationEngineTest*'
```

- [ ] **Step 3: Implement concentration/risk weighting**

Use backend BUY candidates only. Weight by positive score excess over 74, then multiply by:

```kotlin
private fun concentrationFactor(weightPct: Double): Double = when {
    weightPct >= 40.0 -> 0.0
    weightPct >= 30.0 -> 0.25
    weightPct >= 20.0 -> 0.65
    else -> 1.0
}

private fun riskFactor(risk: Int): Double = when (risk.coerceIn(1, 5)) {
    5 -> 0.55
    4 -> 0.75
    else -> 1.0
}
```

If every BUY candidate has zero personal weight after concentration/risk rules, keep the whole budget as cash. Integer-euro allocation uses largest-remainder distribution across positive weights.

- [ ] **Step 4: Run tests and commit**

```bash
cd android
gradle --no-daemon :app:testDebugUnitTest
git add app/src/main/java/de/tobias/investmentradar/RecommendationEngine.kt app/src/test/java/de/tobias/investmentradar/RecommendationEngineTest.kt
git commit -m "feat: add portfolio-aware monthly allocation"
```

---

### Task 3: V2 recommendation presentation helpers

**Files:**
- Create: `android/app/src/main/java/de/tobias/investmentradar/RecommendationPresentation.kt`
- Create: `android/app/src/test/java/de/tobias/investmentradar/RecommendationPresentationTest.kt`
- Modify: `android/app/src/main/java/de/tobias/investmentradar/InvestmentPlanner.kt`

**Interfaces:**
- `RecommendationPresentation.label(item) -> String`.
- `RecommendationPresentation.confidence(item) -> String`.
- `RecommendationPresentation.topReasons(item) -> List<String>`.
- Existing `InvestmentPlanner` remains legacy/custom-only when V2 data is absent.

- [ ] **Step 1: Write failing mapping tests**

```kotlin
@Test
fun v2RecommendationTakesPrecedenceOverLegacyStatus() {
    val item = testInvestmentItem(status = "KAUFEN", recommendation = "NO_BUY", scoreTotal = 51)
    assertEquals("NICHT KAUFEN", RecommendationPresentation.label(item))
}

@Test
fun reducedCoverageIsVisibleInConfidence() {
    val item = testInvestmentItem(recommendation = "WATCH", coverage = 58)
    assertTrue(RecommendationPresentation.confidence(item).contains("DATEN"))
}
```

- [ ] **Step 2: Run tests and verify RED**

```bash
cd android
gradle --no-daemon :app:testDebugUnitTest --tests '*RecommendationPresentationTest*'
```

- [ ] **Step 3: Implement presentation mapping**

Map `BUY -> KAUFEN`, `WATCH -> BEOBACHTEN`, `NO_BUY -> NICHT KAUFEN`, `REVIEW -> VERKAUF PRÜFEN`. Display missing components as `—`. Confidence combines coverage and total score; coverage 50–69 must visibly mention reduced data coverage.

- [ ] **Step 4: Make InvestmentPlanner legacy-only for V2 items**

When `scoreTotal != null` or `recommendation.isNotBlank()`, callers use `RecommendationPresentation` and `RecommendationEngine`. Keep the existing calculation path for custom/legacy items so saved custom positions do not break.

- [ ] **Step 5: Run tests and commit**

```bash
cd android
gradle --no-daemon :app:testDebugUnitTest
git add app/src/main/java/de/tobias/investmentradar/RecommendationPresentation.kt app/src/test/java/de/tobias/investmentradar/RecommendationPresentationTest.kt app/src/main/java/de/tobias/investmentradar/InvestmentPlanner.kt
git commit -m "feat: present explainable v2 recommendations"
```

---

### Task 4: Derive local portfolio values without changing storage/networking

**Files:**
- Create: `android/app/src/main/java/de/tobias/investmentradar/PortfolioAnalysis.kt`
- Create: `android/app/src/test/java/de/tobias/investmentradar/PortfolioAnalysisTest.kt`
- Modify: `android/app/src/main/java/de/tobias/investmentradar/MainViewModel.kt`

**Interfaces:**
- `PortfolioAnalysis.values(items, positions, customItems) -> Map<String, Double>`.
- Market value uses EUR-comparable price when available; otherwise open-position cost basis.
- Custom assets contribute to total/concentration when their EUR-comparable value can be derived.

- [ ] **Step 1: Write failing portfolio-value fallback test**

```kotlin
@Test
fun usesMarketValueWhenQuoteExistsAndCostBasisOtherwise() {
    val values = PortfolioAnalysis.values(
        items = listOf(
            testInvestmentItem(id = "a", priceEur = 20.0),
            testInvestmentItem(id = "b", priceEur = null)
        ),
        positions = mapOf(
            "a" to PortfolioPosition("a", investedAmount = 100.0, shares = 10.0),
            "b" to PortfolioPosition("b", investedAmount = 75.0, shares = 3.0)
        ),
        customItems = emptyList()
    )
    assertEquals(200.0, values.getValue("a"), 0.001)
    assertEquals(75.0, values.getValue("b"), 0.001)
}
```

- [ ] **Step 2: Run RED**

```bash
cd android
gradle --no-daemon :app:testDebugUnitTest --tests '*PortfolioAnalysisTest*'
```

- [ ] **Step 3: Implement helper and keep refresh semantics intact**

Filter non-finite/negative values. Do not add a server portfolio endpoint. Existing `refresh()` still loads dashboard plus custom quotes every 60 seconds; personal analysis happens locally from current StateFlows.

- [ ] **Step 4: Run GREEN and commit**

```bash
cd android
gradle --no-daemon :app:testDebugUnitTest
git add app/src/main/java/de/tobias/investmentradar/PortfolioAnalysis.kt app/src/test/java/de/tobias/investmentradar/PortfolioAnalysisTest.kt app/src/main/java/de/tobias/investmentradar/MainViewModel.kt
git commit -m "feat: derive local portfolio analysis"
```

---

### Task 5: Dashboard and Radar use backend scores + personal allocations

**Files:**
- Modify: `android/app/src/main/java/de/tobias/investmentradar/MainActivity.kt`
- Create: `android/app/src/main/java/de/tobias/investmentradar/ScoreBreakdownCard.kt`
- Create: `android/tests/test-analysis-v2-ui.sh`

**Interfaces:**
- Dashboard top recommendation comes from V2 eligible BUY candidates ordered by personal allocation then score.
- Radar rows show objective label, total score, coverage, top reasons and component breakdown.

- [ ] **Step 1: Write failing source regression test**

```bash
#!/usr/bin/env bash
set -euo pipefail
SRC="android/app/src/main/java/de/tobias/investmentradar/MainActivity.kt"
MODELS="android/app/src/main/java/de/tobias/investmentradar/Models.kt"
grep -q 'scoreTotal' "$MODELS"
grep -q 'RecommendationEngine' "$SRC"
grep -q 'Datenabdeckung' "$SRC"
grep -q 'Qualität' "$SRC"
grep -q 'Bewertung' "$SRC"
grep -q 'Wachstum' "$SRC"
grep -q 'Momentum' "$SRC"
```

- [ ] **Step 2: Run and verify RED**

```bash
bash android/tests/test-analysis-v2-ui.sh
```

- [ ] **Step 3: Replace static allocation calls in DashboardScreen**

Build the personal plan from `RecommendationEngine.plan(...)` using values from `PortfolioAnalysis.values(...)`. `HEUTIGE EMPFEHLUNG` uses highest personal allocation then score. If `cashAmount == budget`, show `DIESEN MONAT WARTEN` and explain that no candidate currently meets personal allocation rules.

- [ ] **Step 4: Add score breakdown UI component**

Move five-score display into `ScoreBreakdownCard.kt`; show total score prominently, component labels with `—` for missing, coverage percentage and up to three backend reasons. Keep existing dark/neon visual language.

- [ ] **Step 5: Update Radar sorting and row content**

Default sort: REVIEW first for held assets, then BUY descending by total score, WATCH, NO_BUY. Preserve watchlist and purchase/edit actions.

- [ ] **Step 6: Run regression/unit tests and commit**

```bash
bash android/tests/test-analysis-v2-ui.sh
cd android
gradle --no-daemon :app:testDebugUnitTest
git add app/src/main/java/de/tobias/investmentradar/MainActivity.kt app/src/main/java/de/tobias/investmentradar/ScoreBreakdownCard.kt ../android/tests/test-analysis-v2-ui.sh
git commit -m "feat: show scored recommendations in dashboard"
```

---

### Task 6: Portfolio screen explains concentration impact

**Files:**
- Modify: `android/app/src/main/java/de/tobias/investmentradar/MainActivity.kt`
- Create: `android/tests/test-portfolio-allocation-ui.sh`

**Interfaces:**
- Portfolio screen shows current portfolio share per analyzed holding and explains when concentration reduces/blocks the next monthly allocation.

- [ ] **Step 1: Write failing UI regression**

The script asserts source contains `Depotanteil`, `Monatskauf reduziert`, `Monatskauf pausiert` and references `PersonalRecommendation.allocationEur`.

- [ ] **Step 2: Run and verify RED**

```bash
bash android/tests/test-portfolio-allocation-ui.sh
```

- [ ] **Step 3: Add concentration messaging**

For 20–29.9% show mild reduction note; 30–39.9% strong reduction; >=40% show paused allocation. Do not alter purchase/sale ledger math.

- [ ] **Step 4: Run all Android unit/source tests and commit**

```bash
bash android/tests/test-portfolio-allocation-ui.sh
cd android
gradle --no-daemon :app:testDebugUnitTest
git add app/src/main/java/de/tobias/investmentradar/MainActivity.kt ../android/tests/test-portfolio-allocation-ui.sh
git commit -m "feat: explain portfolio concentration decisions"
```
