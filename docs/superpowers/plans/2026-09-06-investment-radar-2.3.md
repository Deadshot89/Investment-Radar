# Investment Radar 2.3 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build the 2.3 portfolio advisor that compares existing holdings with new radar opportunities, shows reliable 1/3/6/12-month target ranges, allocates a monthly budget with optional cash, accounts for savings plans, proposes conservative reallocations, stabilizes recommendations, and exposes a clear “Was soll ich jetzt tun?” workflow.

**Architecture:** Extend the existing 2.2 advisor pipeline instead of replacing it. Keep strategic instrument assessment pure and independent from portfolio weight, then add a separate pure portfolio-planning layer for candidate role, monthly allocation, savings-plan conflicts and reallocation suggestions. Persist only bounded material history and the latest plan; WorkManager and Compose consume the same domain outputs rather than duplicating decision rules.

**Tech Stack:** Kotlin, Jetpack Compose, WorkManager 2.10.0, SharedPreferences/JSON, Android notifications, JUnit 4, Gradle 8.13, Java 17, GitHub Actions.

**Spec:** `docs/superpowers/specs/2026-09-06-investment-radar-2.3-portfolio-advisor-design.md`

## Global Constraints

- Start from published Android `2.1.5`, `versionCode = 58`; the next release target is `2.1.6`, `versionCode = 59`.
- Backend contract remains exactly `2.1.0`; 2.3 must not require a backend version change.
- Existing release `v2.1.5` must remain untouched; the release workflow may create only the new version.
- Existing holdings and the first 20 verified BUY radar candidates are the bounded daily candidate universe; do not analyze the full 2000-item universe in background.
- Risk profile is balanced. Default monthly budget remains `100 €`, but the existing user-editable budget stays supported.
- Monthly buy allocations plus cash must equal the chosen monthly budget exactly and must never exceed it.
- Portfolio weight must not be a field in `AdvisorInput` and must never determine `NACHKAUFEN/HALTEN/REDUZIEREN/VERKAUFEN`.
- 6M and 12M drive strategic action. 1M and 3M may change allocation size only through a bounded timing factor and may not change the strategic signal by themselves.
- Never invent prices, target prices, metrics, ticker, ISIN, Trade Republic product identifiers, transaction data or Private-Equity mappings.
- No unofficial Trade Republic login/PIN/session scraping or private API use.
- Both unmapped Private-Equity savings plans remain `itemId = null` and cannot automatically post holdings.
- No order, sale or savings-plan edit is performed automatically; all such outputs are recommendations only.
- No generic visible “Nicht verfügbar” / “nicht verfügbar”; use a concrete cause-specific state.
- Use TDD for every behavioral task: RED test, observe the intended failure, minimal implementation, GREEN verification, then commit.
- Preserve the 2.2 root BackHandler/navigation behavior and existing savings-plan due notifications.

---

### Task 1: Multi-horizon advisor input and target-range reliability

**Files:**
- Modify: `android/app/src/main/java/de/tobias/investmentradar/AdvisorModels.kt`
- Modify: `android/app/src/main/java/de/tobias/investmentradar/AdvisorInputFactory.kt`
- Create: `android/app/src/main/java/de/tobias/investmentradar/AdvisorForecastPolicy.kt`
- Modify: `android/app/src/test/java/de/tobias/investmentradar/AdvisorInputFactoryTest.kt`
- Create: `android/app/src/test/java/de/tobias/investmentradar/AdvisorForecastPolicyTest.kt`

**Interfaces:**
- Add `data class AdvisorForecastRange(val horizon: ForecastHorizon, val expectedChangePct: Double, val lowerChangePct: Double, val upperChangePct: Double, val lowerTargetPriceEur: Double?, val upperTargetPriceEur: Double?, val confidencePct: Int, val reliable: Boolean, val reasons: List<String>)`.
- Replace the single `forecast12mPct` decision input with `forecastRanges: List<AdvisorForecastRange>` while preserving all existing normalized metric fields and the no-portfolio-weight invariant.
- Produce `AdvisorForecastPolicy.from(forecast: InvestmentForecast, freshness: DataFreshnessSummary): List<AdvisorForecastRange>`.

- [ ] **Step 1: Write RED tests** that require all four horizons in order, preserve null target prices when no EUR base price exists, mark stale or coverage `< 60` ranges unreliable, and keep target ranges hidden when lower/upper target price is missing.

```kotlin
@Test
fun staleForecastKeepsHorizonButMarksRangeUnreliable() {
    val ranges = AdvisorForecastPolicy.from(
        forecast12mAndShortHorizons(priceAvailable = true, coverage = 85),
        freshness(FreshnessStatus.STALE, 85)
    )
    assertEquals(listOf(1, 3, 6, 12), ranges.map { it.horizon.months })
    assertTrue(ranges.none { it.reliable })
}
```

- [ ] **Step 2: Run** `cd android && gradle --no-daemon :app:testDebugUnitTest --tests "de.tobias.investmentradar.AdvisorForecastPolicyTest" --tests "de.tobias.investmentradar.AdvisorInputFactoryTest"` with the repository signing properties/environment used by `.github/workflows/android-unit-tests.yml`; confirm RED because the new types/policy do not exist.
- [ ] **Step 3: Implement the policy** with `MIN_FORECAST_COVERAGE = 60`; confidence is deterministic and bounded `0..100`, based on coverage and scenario width, but `reliable` requires fresh/non-stale data, coverage `>= 60`, and both target-range bounds when a price range is presented. Missing targets stay null rather than being synthesized.
- [ ] **Step 4: Update `AdvisorInputFactory.from`** to attach all four ranges and retain typgerechte stock/ETF/fixed-income normalization.
- [ ] **Step 5: Re-run focused tests plus existing `AdvisorInputFactoryTest`; require GREEN.**
- [ ] **Step 6: Commit** `feat: add reliable multi-horizon advisor forecasts`.

### Task 2: Strategic advisor score with separate short-term timing factor

**Files:**
- Modify: `android/app/src/main/java/de/tobias/investmentradar/AdvisorModels.kt`
- Modify: `android/app/src/main/java/de/tobias/investmentradar/AdvisorEngine.kt`
- Modify: `android/app/src/test/java/de/tobias/investmentradar/AdvisorEngineTest.kt`

**Interfaces:**
- Extend `AdvisorResult` with `confidencePct: Int` and `timingFactor: Double`.
- `AdvisorEngine.evaluate(input: AdvisorInput): AdvisorResult` remains the only strategic signal evaluator.
- Strategic forecast component uses 6M/12M; short-term timing factor is clamped to `0.80..1.10` and is never used in signal threshold selection.

- [ ] **Step 1: Add RED tests** proving identical 6M/12M data produces the same signal even when 1M/3M are strongly positive versus strongly negative, while `timingFactor` changes within `0.80..1.10`.

```kotlin
@Test
fun shortTermWeaknessChangesSizingButNotStrategicSignal() {
    val positive = AdvisorEngine.evaluate(stockInput(short1 = 10.0, short3 = 16.0, six = 12.0, twelve = 18.0))
    val negative = AdvisorEngine.evaluate(stockInput(short1 = -10.0, short3 = -16.0, six = 12.0, twelve = 18.0))
    assertEquals(positive.signal, negative.signal)
    assertTrue(negative.timingFactor < positive.timingFactor)
    assertTrue(negative.timingFactor >= 0.80)
    assertTrue(positive.timingFactor <= 1.10)
}
```

- [ ] **Step 2: Run focused test and confirm RED.**
- [ ] **Step 3: Implement strategic forecast score** as a weighted combination of reliable 6M and 12M expected changes, with 12M carrying the larger weight. If required strategic horizons are not reliable, return `KEINE_BELASTBARE_BEWERTUNG` rather than substituting zero.
- [ ] **Step 4: Implement `timingFactor`** from reliable 1M/3M ranges only, neutral `1.0` when they are unavailable, clamped to `0.80..1.10`.
- [ ] **Step 5: Re-run all `AdvisorEngineTest` tests and require GREEN, including reflection test proving no portfolio weight/allocation field exists in `AdvisorInput`.**
- [ ] **Step 6: Commit** `feat: separate strategic advisor and timing signal`.

### Task 3: Portfolio candidate roles and explicit 2.3 action model

**Files:**
- Create: `android/app/src/main/java/de/tobias/investmentradar/PortfolioAdvisorModels.kt`
- Create: `android/app/src/main/java/de/tobias/investmentradar/PortfolioAdvisorCandidateFactory.kt`
- Create: `android/app/src/test/java/de/tobias/investmentradar/PortfolioAdvisorCandidateFactoryTest.kt`

**Interfaces:**
- Produce:

```kotlin
enum class PortfolioAdvisorAction {
    NACHKAUFEN, HALTEN, REDUZIEREN, VERKAUFEN,
    NEU_AUFNEHMEN, NICHT_AUFNEHMEN, KEINE_BELASTBARE_BEWERTUNG
}

data class PortfolioAdvisorCandidate(
    val itemId: String,
    val isHolding: Boolean,
    val action: PortfolioAdvisorAction,
    val advisor: AdvisorResult,
    val currentValueEur: Double?,
    val monthlySavingsEur: Int
)
```

- `PortfolioAdvisorCandidateFactory.create(item, isHolding, currentValueEur, monthlySavingsEur, freshness): PortfolioAdvisorCandidate`.
- Existing holding signals map 1:1 to four holding actions. A non-holding reliable `NACHKAUFEN` becomes `NEU_AUFNEHMEN`; other reliable non-holdings become `NICHT_AUFNEHMEN`; unreliable stays `KEINE_BELASTBARE_BEWERTUNG`.

- [ ] **Step 1: Write RED tests** for holding/non-holding mapping and unreliable data.
- [ ] **Step 2: Confirm RED.**
- [ ] **Step 3: Implement the factory** by calling `ForecastEngine.forecast`, `AdvisorForecastPolicy`, `AdvisorInputFactory`, then `AdvisorEngine`; do not read Android Context in this pure layer.
- [ ] **Step 4: Run focused tests; require GREEN.**
- [ ] **Step 5: Commit** `feat: model holdings and new advisor opportunities`.

### Task 4: Pure savings-plan monthly budget normalization

**Files:**
- Create: `android/app/src/main/java/de/tobias/investmentradar/SavingsPlanBudget.kt`
- Create: `android/app/src/test/java/de/tobias/investmentradar/SavingsPlanBudgetTest.kt`

**Interfaces:**
- Produce `SavingsPlanBudget.monthlyAmounts(plans: List<SavingsPlan>): Map<String, Int>`.
- Enabled monthly plan = one `amountEur`; enabled twice-monthly plan = two `amountEur`; disabled plans and `itemId == null` are excluded.
- Whole-euro normalization uses `roundToInt()` only after the monthly equivalent is calculated.

- [ ] **Step 1: Write RED tests** with Meta `10 €` twice monthly → `20`, Microsoft `10 €` monthly → `10`, disabled plan → absent, and both unmapped PE plans → absent.

```kotlin
assertEquals(20, result["meta"])
assertEquals(10, result["msft"])
assertFalse(result.containsKey(null))
```

- [ ] **Step 2: Confirm RED.**
- [ ] **Step 3: Implement the pure helper** without changing `SavingsPlanStore` persistence or PE identifiers.
- [ ] **Step 4: Run focused test and existing `SavingsPlanStoreTest`; require GREEN.**
- [ ] **Step 5: Commit** `feat: normalize savings plans for monthly advisor budget`.

### Task 5: Monthly allocation engine with optional cash

**Files:**
- Create: `android/app/src/main/java/de/tobias/investmentradar/PortfolioAdvisorEngine.kt`
- Create: `android/app/src/test/java/de/tobias/investmentradar/PortfolioAdvisorEngineTest.kt`
- Keep `RecommendationEngine.kt` temporarily for compatibility until UI migration in Task 9.

**Interfaces:**
- Add:

```kotlin
data class PortfolioAllocation(
    val itemId: String,
    val amountEur: Int,
    val action: PortfolioAdvisorAction,
    val reason: String
)

data class SavingsPlanConflict(
    val itemId: String,
    val monthlySavingsEur: Int,
    val action: PortfolioAdvisorAction
)

data class PortfolioAdvisorPlan(
    val budgetEur: Int,
    val allocations: List<PortfolioAllocation>,
    val cashEur: Int,
    val reallocations: List<ReallocationSuggestion>,
    val savingsPlanConflicts: List<SavingsPlanConflict>,
    val candidates: List<PortfolioAdvisorCandidate>
)
```

- Produce `PortfolioAdvisorEngine.allocate(candidates: List<PortfolioAdvisorCandidate>, budgetEur: Int): PortfolioAdvisorPlan`.
- Allocation eligibility: reliable `NACHKAUFEN` or `NEU_AUFNEHMEN`; `HALTEN` is eligible only when score is `68..71` and no stronger eligible candidate exists; `REDUZIEREN`, `VERKAUFEN`, `NICHT_AUFNEHMEN`, unreliable = zero.
- Desired weight is based on advisor score and `timingFactor`; no portfolio-weight concentration factor is used.
- Existing monthly savings amount is subtracted from that candidate’s desired new monthly contribution before extra allocation; it can reduce extra allocation to zero but never below zero.
- Cash is retained when qualifying opportunity strength is insufficient; do not force the full budget into weak candidates.

- [ ] **Step 1: Write RED invariant tests** for `allocations.sumOf { it.amountEur } + cashEur == budgetEur`, all weak candidates → all cash, reduce/sell → zero, planned savings reducing extra purchase, and a high portfolio-weight holding receiving the same strategic eligibility as an otherwise identical low-weight holding.
- [ ] **Step 2: Confirm RED.**
- [ ] **Step 3: Implement deterministic allocation** in whole euros. Use opportunity deployment caps by strongest score: score `>= 82` permits up to 100% deployment, `76..81` up to 80%, `72..75` up to 60%, otherwise zero except the explicit borderline-Halten fallback. Distribute the deployable amount proportionally by `(score - 60) * timingFactor`, subtract monthly savings contribution per item, and leave any remainder as cash.
- [ ] **Step 4: Add savings-plan conflicts** whenever a candidate action is `REDUZIEREN` or `VERKAUFEN` and its normalized monthly savings amount is greater than zero; do not mutate the plan.
- [ ] **Step 5: Run focused tests; require GREEN.**
- [ ] **Step 6: Commit** `feat: allocate monthly advisor budget with cash reserve`.

### Task 6: Conservative reallocation policy and recommendation stability

**Files:**
- Create: `android/app/src/main/java/de/tobias/investmentradar/ReallocationPolicy.kt`
- Create: `android/app/src/main/java/de/tobias/investmentradar/AdvisorStabilityPolicy.kt`
- Modify: `android/app/src/main/java/de/tobias/investmentradar/PortfolioAdvisorEngine.kt`
- Create: `android/app/src/test/java/de/tobias/investmentradar/ReallocationPolicyTest.kt`
- Create: `android/app/src/test/java/de/tobias/investmentradar/AdvisorStabilityPolicyTest.kt`

**Interfaces:**
- `data class ReallocationSuggestion(val fromItemId: String, val toItemId: String, val amountEur: Int, val reason: String)`.
- `ReallocationPolicy.suggest(candidates): List<ReallocationSuggestion>` requires source holding action `REDUZIEREN` or `VERKAUFEN`, reliable target action `NACHKAUFEN`/`NEU_AUFNEHMEN`, target score at least `15` points above source, and a positive known source current value.
- Conservative amount: `REDUZIEREN` suggests `min(25% of source value, 50 €)`; `VERKAUFEN` suggests `min(50% of source value, 100 €)`, rounded down to whole euros. This is a recommendation amount only.
- `AdvisorStabilityPolicy.resolve(previous: AdvisorSnapshot, proposed: AdvisorResult): AdvisorResult` requires two consecutive daily occurrences before normal escalation to a materially worse action. Immediate deterioration is allowed only for reliable `VERKAUFEN` with score `<= 25` or a score drop of at least `25` from the last reliable result.

- [ ] **Step 1: Write RED tests** proving a 5-point better target causes no reallocation, a 15-point better target does, amounts are capped, and no known current value means no invented sale amount.
- [ ] **Step 2: Write RED stability tests** proving one ordinary `HALTEN → REDUZIEREN` proposal remains on the previous reliable action, the same proposal on the next day confirms it, and severe reliable deterioration may change immediately.
- [ ] **Step 3: Confirm both suites RED.**
- [ ] **Step 4: Implement pure policies** and connect reallocation output to `PortfolioAdvisorPlan`; never call portfolio transaction APIs.
- [ ] **Step 5: Run focused suites; require GREEN.**
- [ ] **Step 6: Commit** `feat: add stable conservative reallocation guidance`.

### Task 7: Bounded material advisor history and latest plan persistence

**Files:**
- Modify: `android/app/src/main/java/de/tobias/investmentradar/AdvisorStore.kt`
- Create: `android/app/src/main/java/de/tobias/investmentradar/PortfolioAdvisorStore.kt`
- Modify: `android/app/src/test/java/de/tobias/investmentradar/AdvisorStoreTest.kt`
- Create: `android/app/src/test/java/de/tobias/investmentradar/PortfolioAdvisorStoreTest.kt`

**Interfaces:**
- Add `data class AdvisorHistoryEntry(val analysisDay: String, val previousSignal: AdvisorSignal?, val newSignal: AdvisorSignal, val score: Int?, val reasons: List<String>)`.
- Store only material action/reliability changes, maximum `20` entries per instrument, newest first, under a new key `history_v2`; keep existing `snapshots_v1` readable.
- `PortfolioAdvisorStore` persists only the latest `PortfolioAdvisorPlan` plus analysis day under `latest_plan_v1`.

- [ ] **Step 1: Add RED codec/history tests** proving unchanged daily results do not grow history, 21 material changes retain exactly 20, existing v1 snapshot JSON still decodes, and latest plan round-trips allocations/cash/reallocations/conflicts.
- [ ] **Step 2: Confirm RED.**
- [ ] **Step 3: Implement additive persistence** without deleting or rewriting unrelated portfolio/savings preferences.
- [ ] **Step 4: Run focused tests plus existing `AdvisorStoreTest`; require GREEN.**
- [ ] **Step 5: Commit** `feat: persist bounded advisor history and latest plan`.

### Task 8: Daily background analysis for holdings plus verified radar opportunities

**Files:**
- Modify: `android/app/src/main/java/de/tobias/investmentradar/DailyAnalysisCoordinator.kt`
- Modify: `android/app/src/main/java/de/tobias/investmentradar/DailyAnalysisWorker.kt`
- Modify: `android/app/src/test/java/de/tobias/investmentradar/DailyAnalysisCoordinatorTest.kt`
- Create: `android/app/src/test/java/de/tobias/investmentradar/DailyCandidateUniverseTest.kt`

**Interfaces:**
- Change coordinator to accept `candidateIds: Set<String>` in addition to `holdingIds`, and evaluate exactly the union of those sets found in supplied `items`.
- Worker obtains top radar candidates with the same bounded query already used by `MainViewModel`: `RadarQuery(recommendation = "BUY", sort = "SCORE_DESC", page = 1, pageSize = 20, tradeRepublicVerified = true)`.
- Worker always keeps all known holdings in the universe even when they are not BUY candidates.
- Worker reads savings plans, current portfolio values and previous snapshots, applies stability, persists results/history/latest plan, then publishes only material events.

- [ ] **Step 1: Change coordinator tests RED** so holdings plus explicit radar candidate IDs are evaluated and a supplied outsider is ignored.
- [ ] **Step 2: Add RED universe test** asserting query size `20`, verified-only `true`, and no full-universe background paging.
- [ ] **Step 3: Confirm RED.**
- [ ] **Step 4: Implement worker loading** using dashboard + one radar page + missing holding detail/custom quote fallback. If radar candidate loading fails, still analyze successfully loaded holdings; retry only when no usable required holdings can be loaded.
- [ ] **Step 5: Build `PortfolioAdvisorPlan` in the worker** from stabilized candidates, `PortfolioAnalysis.values(...)`, and `SavingsPlanStore.readPlans(...)`, then save through `PortfolioAdvisorStore`.
- [ ] **Step 6: Run focused/full JVM tests; require GREEN.**
- [ ] **Step 7: Commit** `feat: analyze depot and radar opportunities daily`.

### Task 9: Material notification policy for 2.3

**Files:**
- Modify: `android/app/src/main/java/de/tobias/investmentradar/AdvisorChangePolicy.kt`
- Modify: `android/app/src/main/java/de/tobias/investmentradar/AdvisorNotificationManager.kt`
- Modify: `android/app/src/test/java/de/tobias/investmentradar/AdvisorChangePolicyTest.kt`
- Modify: `android/app/src/test/java/de/tobias/investmentradar/AdvisorNotificationPolicyTest.kt`

**Interfaces:**
- Add event kinds `SIGNAL_CHANGE`, `NEW_STRONG_OPPORTUNITY`, `REALLOCATION`, `RELIABILITY_LOST` while preserving stable unique event IDs.
- A new non-holding opportunity is notification-worthy only when reliable advisor score is `>= 82`.
- Reallocation event is notification-worthy only when `amountEur >= 20` and the `(from,to,amount,analysisDay)` stable ID is new.
- Routine allocation rounding changes and unchanged signals create no event.

- [ ] **Step 1: Write RED tests** for unchanged daily plan/no event, newly reliable score-82 radar candidate/one event, score-80 candidate/no opportunity event, `15 €` reallocation/no event, `20 €` reallocation/one event, and reliability loss/one event.
- [ ] **Step 2: Confirm RED.**
- [ ] **Step 3: Implement event creation and existing ledger integration**; keep due savings-plan notifications unchanged.
- [ ] **Step 4: Verify repeated publishing of the same stable IDs remains idempotent.**
- [ ] **Step 5: Run focused/full tests; require GREEN.**
- [ ] **Step 6: Commit** `feat: notify only on material portfolio advisor changes`.

### Task 10: Replace legacy allocation wiring with one shared 2.3 plan

**Files:**
- Modify: `android/app/src/main/java/de/tobias/investmentradar/MainActivity.kt`
- Modify: `android/app/src/main/java/de/tobias/investmentradar/PortfolioDashboard.kt`
- Modify: `android/app/src/main/java/de/tobias/investmentradar/RadarScreen.kt`
- Modify: `android/app/src/main/java/de/tobias/investmentradar/InvestmentDetailScreen.kt`
- Modify: `android/tests/test-portfolio-allocation-ui.sh`
- Modify: `android/tests/test-portfolio-dashboard-ui.sh`
- Modify: `android/tests/test-forecast-ui.sh`
- Create: `android/tests/test-portfolio-advisor-2-3-ui.sh`
- Modify: `.github/workflows/android-contract-tests.yml` to run the new contract.

**Interfaces:**
- Root Compose computes exactly one `PortfolioAdvisorPlan` from `s.data.items`, `holdingIds`, `PortfolioAnalysis.values(...)`, `SavingsPlanStore.readPlans(context)` and current budget; the same object is passed to portfolio/radar/detail.
- Remove the root call to `RecommendationEngine.plan`; `RecommendationEngine.kt` can remain only if another compile-time caller still needs it, otherwise delete it after search proves zero callers.
- `PortfolioDashboard(..., advisorPlan: PortfolioAdvisorPlan, ...)`.
- `RadarScreenV2(..., advisorById: Map<String, PortfolioAdvisorCandidate>, ...)`.
- `InvestmentDetailScreen(..., advisorCandidate: PortfolioAdvisorCandidate?, advisorHistory: List<AdvisorHistoryEntry>, ...)`.

- [ ] **Step 1: Rewrite `test-portfolio-allocation-ui.sh` RED** to require exactly one `PortfolioAdvisorEngine` root planning call and reject root `RecommendationEngine.plan`.
- [ ] **Step 2: Add RED 2.3 UI contract** requiring literal `Was soll ich jetzt tun?`, visible `Cash halten`, `Umschichten`, `Sparplan prüfen`, actions `Neu aufnehmen/Reduzieren/Verkaufen`, `Konfidenz`, and `Änderungshistorie`.
- [ ] **Step 3: Confirm contracts RED before UI changes.**
- [ ] **Step 4: Implement the central portfolio card** directly below the portfolio header. Show `X € investieren · Y € umschichten · Z € Cash halten`, then prioritized allocations/reallocations/conflicts. Do not imply execution.
- [ ] **Step 5: Update position cards and radar cards** to show the candidate action and additional monthly amount. Existing depot weight can remain informational, but remove the old concentration text as a decision gate.
- [ ] **Step 6: Update detail screen forecast card** to render 1/3/6/12 horizons from `AdvisorForecastRange`; when a range is reliable and target bounds exist show `Zielbereich A–B €`, expected change and confidence. Otherwise show the concrete cause and no fabricated target. Add reasons/risks and bounded change history.
- [ ] **Step 7: Preserve root BackHandler** and existing explicit detail/savings child navigation; add no nested BackHandler.
- [ ] **Step 8: Run all Android shell contracts and full JVM tests; require GREEN.**
- [ ] **Step 9: Commit** `feat: add actionable 2.3 portfolio advisor UI`.

### Task 11: Release contracts for Android 2.1.6/code59 and regression verification

**Files:**
- Modify: `android/app/build.gradle.kts`
- Modify: `android/tests/test-version-contract.sh`
- Modify: `android/tests/test-history-card-glare.sh`
- Modify: `android/tests/test-release-backend-gate.sh`
- Do not modify backend version files.

**Interfaces:**
- Produce Android `2.1.6`, `versionCode = 59` with backend contract `2.1.0`.
- Existing release workflow must continue to reject overwriting an existing tag when `android/app` tree differs.

- [ ] **Step 1: Update `test-version-contract.sh` first** to expect code `59` / name `2.1.6` and continue to require backend `2.1.0`; run it and confirm RED against current 2.1.5/code58.
- [ ] **Step 2: Update the two other release contracts** to expect 2.1.6/code59 while preserving all backend/live gate and immutable-release assertions.
- [ ] **Step 3: Bump `android/app/build.gradle.kts`** to `versionCode = 59`, `versionName = "2.1.6"`, comment `Release candidate: Investment Radar 2.1.6`.
- [ ] **Step 4: Run all shell contracts** via the exact loop in `.github/workflows/android-contract-tests.yml`; require GREEN.
- [ ] **Step 5: Run complete JVM suite** through the same Gradle command/properties as `.github/workflows/android-unit-tests.yml`; require GREEN.
- [ ] **Step 6: Run signed release build workflow** and require Android JVM tests, signed release APK, APK signature verification, and artifact upload GREEN. On feature branch, backend publish/release publication must remain skipped where the workflow is main-gated.
- [ ] **Step 7: Commit** `chore: prepare Android 2.1.6 release`.

### Task 12: Integration, PR, main verification and immutable release evidence

**Files:**
- No production code unless a fresh integration test exposes a real defect; any defect follows a new RED→GREEN cycle before integration continues.

**Interfaces:**
- Feature branch must include the approved spec and this implementation plan before PR review.
- Final publish target: tag/release `v2.1.6` with `investment-radar.apk`; `v2.1.5` remains unchanged.

- [ ] **Step 1: Bring the spec/plan commits onto `feat/investment-radar-2.3`** without rewriting `main`; verify branch comparison contains only 2.3 work plus documentation.
- [ ] **Step 2: Run fresh feature-branch CI** for Android Contract Tests, Android JVM Tests and Android Build; all three must complete successfully on the final candidate SHA.
- [ ] **Step 3: Open one PR** titled `Investment Radar 2.3: Portfolio-Berater` and inspect changed files plus unresolved review threads before marking ready.
- [ ] **Step 4: Merge only after all required checks are GREEN.**
- [ ] **Step 5: Verify fresh `main` CI** on the merge commit: contract tests GREEN, JVM tests GREEN, signed Android build GREEN, APK signature verification GREEN, live backend `2.1.0` gate GREEN, publish step GREEN.
- [ ] **Step 6: Fetch release `v2.1.6`** and record release ID, asset ID, size, direct APK URL and SHA-256 digest. Confirm `v2.1.5` release/asset metadata has not changed.
- [ ] **Step 7: Report completion only with those fresh main/release results.**

## Self-review

- Spec coverage: all ten acceptance criteria are mapped: Tasks 1–3 handle typgerechte advisor/prognosis, Tasks 4–5 budget+savings+cash, Task 6 reallocation/stability, Tasks 7–9 history/background/notifications, Task 10 UI, Tasks 11–12 regression/release.
- Placeholder scan: no TBD/TODO/“implement later” steps remain.
- Type consistency: `AdvisorForecastRange` flows InputFactory → AdvisorEngine → PortfolioAdvisorCandidate → UI; `PortfolioAdvisorPlan` flows allocator/background store → root UI; savings plans are normalized by one pure helper; advisor strategic action remains independent from portfolio weight.
- Safety check: no automatic order/sale/savings-plan mutation, no invented identifiers, PE mapping remains blocked, Trade Republic 2.2 routing contract remains unchanged unless an actual regression requires a separately tested fix.
