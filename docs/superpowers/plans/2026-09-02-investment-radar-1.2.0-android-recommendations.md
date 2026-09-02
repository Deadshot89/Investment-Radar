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

### Task 1: Add JVM test support and V2 Android models

**Files:**
- Modify: `android/app/build.gradle.kts`
- Modify: `android/app/src/main/java/de/tobias/investmentradar/Models.kt`
- Modify: `android/app/src/main/java/de/tobias/investmentradar/ApiClient.kt`
- Create: `android/app/src/test/java/de/tobias/investmentradar/ApiModelDefaultsTest.kt`

**Interfaces:**
- `InvestmentItem` gains nullable component scores, coverage, recommendation, reasons, momentum and fundamentals.
- Parser defaults preserve compatibility when connecting to a pre-1.2.0 backend during rollout.

- [ ] **Step 1: Add JUnit dependency and failing parser/default tests**

Add:

```kotlin
testImplementation("junit:junit:4.13.2")
```

Create tests around pure default helpers rather than network calls:

```kotlin
@Test
fun legacyStatusMapsToRecommendationWhenNewFieldMissing() {
    assertEquals("BUY", recommendationFallback("", "KAUFEN"))
    assertEquals("WATCH", recommendationFallback("", "BEOBACHTEN"))
}

@Test
fun missingScoresStayNull() {
    val item = InvestmentItem.legacyForTest(id = "x", status = "KAUFEN")
    assertNull(item.scoreTotal)
    assertNull(item.scoreQuality)
}
```

- [ ] **Step 2: Run test and verify RED**

```bash
cd android
gradle --no-daemon :app:testDebugUnitTest
```

Expected: FAIL because V2 fields/helpers do not exist.

- [ ] **Step 3: Extend data classes**

Introduce focused nested models:

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

Add nullable `scoreTotal`, five component scores, `coverage`, `recommendation`, `recommendationReasons`, `momentum`, `fundamentals`, `analysisAsOf` to `InvestmentItem`.

- [ ] **Step 4: Make ApiClient additive and rollout-safe**

Parse the new fields when present. Use legacy mapping only when `recommendation` is blank:

```kotlin
internal fun recommendationFallback(recommendation: String, status: String): String =
    recommendation.ifBlank {
        when (status.trim().uppercase()) {
            "KAUFEN", "BUY" -> "BUY"
            "VERKAUFEN", "VERKAUF PRÜFEN", "DRINGEND_PRUEFEN", "REVIEW" -> "REVIEW"
            "NICHT KAUFEN", "NO_BUY" -> "NO_BUY"
            else -> "WATCH"
        }
    }
```

- [ ] **Step 5: Run unit tests and commit**

```bash
cd android
gradle --no-daemon :app:testDebugUnitTest
git add app/build.gradle.kts app/src/main/java/de/tobias/investmentradar/Models.kt app/src/main/java/de/tobias/investmentradar/ApiClient.kt app/src/test/java/de/tobias/investmentradar/ApiModelDefaultsTest.kt
git commit -m "feat: consume investment analysis v2"
```

---

### Task 2: Pure portfolio-aware RecommendationEngine

**Files:**
- Create: `android/app/src/main/java/de/tobias/investmentradar/RecommendationEngine.kt`
- Create: `android/app/src/test/java/de/tobias/investmentradar/RecommendationEngineTest.kt`

**Interfaces:**
- Consumes: backend analyzed items, selected monthly budget, `Map<String, PortfolioPosition>`, and EUR-comparable custom holdings.
- Produces: `PersonalPlan(items: List<PersonalRecommendation>, cashAmount: Int)`.
- `PersonalRecommendation` contains item id, objective recommendation, personal allocation, current portfolio weight, concentration label and explanation.

- [ ] **Step 1: Write failing concentration tests**

```kotlin
@Test
fun blocksNormalAllocationAboveFortyPercentConcentration() {
    val result = RecommendationEngine.plan(
        candidates = listOf(candidate("a", 90), candidate("b", 82)),
        budget = 100,
        currentValues = mapOf("a" to 600.0, "b" to 400.0)
    )
    assertEquals(0, result.items.first { it.itemId == "a" }.allocationEur)
    assertEquals(100, result.items.first { it.itemId == "b" }.allocationEur)
}

@Test
fun eligibleAllocationsAlwaysSumToBudget() {
    val result = RecommendationEngine.plan(
        candidates = listOf(candidate("a", 90), candidate("b", 80), candidate("c", 76)),
        budget = 137,
        currentValues = emptyMap()
    )
    assertEquals(137, result.items.sumOf { it.allocationEur })
    assertEquals(0, result.cashAmount)
}

@Test
fun noEligibleBuyKeepsCash() {
    val result = RecommendationEngine.plan(listOf(candidate("a", 70, "WATCH")), 100, emptyMap())
    assertEquals(100, result.cashAmount)
}
```

- [ ] **Step 2: Run tests and verify RED**

```bash
cd android
gradle --no-daemon :app:testDebugUnitTest --tests '*RecommendationEngineTest*'
```

- [ ] **Step 3: Implement concentration/risk weighting**

Use backend BUY candidates only. Weight by score excess over 74, then multiply by concentration factors:

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

If every BUY candidate is blocked only because one already-dominant position exists, keep cash rather than forcing more concentration. Integer-euro allocation uses largest-remainder distribution across positive weights.

- [ ] **Step 4: Count custom assets in portfolio total**

Provide `currentValues` using EUR market value where known and invested-value fallback where market value is unavailable. Custom assets affect denominator/concentration but are not auto-allocation targets unless they correspond to a backend-analyzed item.

- [ ] **Step 5: Run tests and commit**

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
- Produces: `recommendationLabel(item)`, `confidenceLabel(item)`, `scoreBreakdown(item)`, and `topReasons(item)`.
- Existing `InvestmentPlanner` remains for legacy/custom compatibility but must no longer override backend V2 recommendation when score data is present.

- [ ] **Step 1: Write failing mapping tests**

```kotlin
@Test
fun v2RecommendationTakesPrecedenceOverLegacyStatus() {
    val item = analyzedItem(recommendation = "NO_BUY", legacyStatus = "KAUFEN", score = 51)
    assertEquals("NICHT KAUFEN", RecommendationPresentation.label(item))
}

@Test
fun reducedCoverageIsVisibleInConfidence() {
    val item = analyzedItem(recommendation = "WATCH", coverage = 58)
    assertTrue(RecommendationPresentation.confidence(item).contains("DATEN"))
}
```

- [ ] **Step 2: Run tests and verify RED**

```bash
cd android
gradle --no-daemon :app:testDebugUnitTest --tests '*RecommendationPresentationTest*'
```

- [ ] **Step 3: Implement presentation mapping**

Map `BUY -> KAUFEN`, `WATCH -> BEOBACHTEN`, `NO_BUY -> NICHT KAUFEN`, `REVIEW -> VERKAUF PRÜFEN`. Display component value as `—` when null. Confidence combines coverage and total score rather than pretending missing data is a low score.

- [ ] **Step 4: Make InvestmentPlanner legacy-only for V2 items**

When `scoreTotal != null` or `recommendation.isNotBlank()`, derive headline from V2 data/personal plan. Keep the existing old calculation path for custom/legacy items so saved custom positions do not break.

- [ ] **Step 5: Run tests and commit**

```bash
cd android
gradle --no-daemon :app:testDebugUnitTest
git add app/src/main/java/de/tobias/investmentradar/RecommendationPresentation.kt app/src/test/java/de/tobias/investmentradar/RecommendationPresentationTest.kt app/src/main/java/de/tobias/investmentradar/InvestmentPlanner.kt
git commit -m "feat: present explainable v2 recommendations"
```

---

### Task 4: Integrate personal plan into ViewModel without changing storage

**Files:**
- Modify: `android/app/src/main/java/de/tobias/investmentradar/MainViewModel.kt`
- Create: `android/app/src/main/java/de/tobias/investmentradar/PortfolioAnalysis.kt`
- Create: `android/app/src/test/java/de/tobias/investmentradar/PortfolioAnalysisTest.kt`

**Interfaces:**
- Produces: `portfolioValues(items, positions, customItems) -> Map<String, Double>`.
- MainViewModel continues exposing existing StateFlows and adds a pure helper call path used by Compose; it does not upload portfolio data.

- [ ] **Step 1: Write failing portfolio-value fallback tests**

```kotlin
@Test
fun usesMarketValueWhenQuoteExistsAndCostBasisOtherwise() {
    val values = PortfolioAnalysis.values(
        items = listOf(item("a", priceEur = 20.0), item("b", priceEur = null)),
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

- [ ] **Step 2: Run RED, implement helper, run GREEN**

```bash
cd android
gradle --no-daemon :app:testDebugUnitTest --tests '*PortfolioAnalysisTest*'
```

Implement market-value-first, cost-basis fallback, and finite/nonnegative filtering.

- [ ] **Step 3: Keep refresh semantics intact**

Do not add server portfolio endpoints. Existing `refresh()` still loads dashboard plus custom quotes every 60 seconds; portfolio analysis happens locally from current StateFlows.

- [ ] **Step 4: Run full unit tests and commit**

```bash
cd android
gradle --no-daemon :app:testDebugUnitTest
git add app/src/main/java/de/tobias/investmentradar/MainViewModel.kt app/src/main/java/de/tobias/investmentradar/PortfolioAnalysis.kt app/src/test/java/de/tobias/investmentradar/PortfolioAnalysisTest.kt
git commit -m "feat: derive local portfolio analysis"
```

---

### Task 5: Dashboard and Radar use backend scores + personal allocations

**Files:**
- Modify: `android/app/src/main/java/de/tobias/investmentradar/MainActivity.kt`
- Create: `android/app/src/main/java/de/tobias/investmentradar/ScoreBreakdownCard.kt`
- Create: `android/tests/test-analysis-v2-ui.sh`

**Interfaces:**
- Dashboard top recommendation comes from V2 eligible BUY candidates ordered by personal allocation/score.
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

Build personal plan from `RecommendationEngine.plan(...)`. `HEUTIGE EMPFEHLUNG` uses the highest personal allocation, then score. If `cashAmount == budget`, show `DIESEN MONAT WARTEN` and explain that no candidate currently meets personal allocation rules.

- [ ] **Step 4: Add score breakdown UI component**

Move the five-score display into `ScoreBreakdownCard.kt`; show total score prominently, component labels with `—` for missing, coverage percentage and up to three backend reasons. Keep existing dark/neon visual language.

- [ ] **Step 5: Update Radar sorting and row content**

Default sort: REVIEW first for held assets, then BUY descending by total score, WATCH, NO_BUY. Preserve watchlist and purchase/edit actions.

- [ ] **Step 6: Run regression and build checks**

```bash
bash android/tests/test-analysis-v2-ui.sh
cd android
gradle --no-daemon :app:testDebugUnitTest
```

- [ ] **Step 7: Commit**

```bash
git add android/app/src/main/java/de/tobias/investmentradar/MainActivity.kt android/app/src/main/java/de/tobias/investmentradar/ScoreBreakdownCard.kt android/tests/test-analysis-v2-ui.sh
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

Assert source contains labels `Depotanteil`, `Monatskauf reduziert` and `Monatskauf pausiert` and references personal plan allocation.

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
