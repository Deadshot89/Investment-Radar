# Investment Radar 2.2 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build the daily depot advisor, meaningful push notifications, background savings-plan checks, safe Trade Republic routing, concrete UI states, and native Android back navigation for release 2.1.5/code58.

**Architecture:** Add a pure advisor domain layer plus a small persisted result/history layer and one Android background coordinator. Keep existing portfolio/savings-plan stores as sources of truth. Centralize back navigation in a pure state reducer consumed by the root Compose UI so one back press removes exactly one UI layer.

**Tech Stack:** Kotlin, Jetpack Compose, AndroidX Activity BackHandler, WorkManager, SharedPreferences/JSON as already used by the app, Firebase Messaging/Android notifications, JUnit, Gradle/GitHub Actions.

**Spec:** `docs/superpowers/specs/2026-09-05-investment-radar-2.2-advisor-design.md`

## Global Constraints

- Android release: `2.1.5`, `versionCode = 58`; backend remains `2.1.0` unless a separately approved contract change is unavoidable.
- Never invent ticker, ISIN, market price, target price, fundamental metric, Trade Republic identifier, or execution data.
- No unofficial Trade Republic login, PIN/session scraping, or private API emulation.
- No generic visible copy `Nicht verfügbar` / `nicht verfügbar`; show the real cause.
- Planned savings amounts never alter holdings; only explicit `Ausgeführt` may book a transaction.
- Android back exits only from the root start state; child states unwind one level per press.
- Use TDD: every behavioral task starts RED and is verified GREEN before commit.
- Published v2.1.4 remains immutable.

---

### Task 1: Central Android back-state reducer and native BackHandler

**Files:**
- Create: `android/app/src/main/java/de/tobias/investmentradar/AppNavigationState.kt`
- Modify: `android/app/src/main/java/de/tobias/investmentradar/MainActivity.kt`
- Test: `android/app/src/test/java/de/tobias/investmentradar/AppNavigationStateTest.kt`

**Interfaces:**
- Produces: `AppNavigationState`, `BackResult`, `fun AppNavigationState.onBack(): BackResult`.
- Root Compose owns the state needed to close overlays, details, and child screens before delegating to Activity exit.

- [ ] **Step 1: Write RED reducer tests** covering dialog/overlay first, detail second, child screen third, root exit last, and exactly one transition per call.
- [ ] **Step 2: Run** `cd android && ./gradlew testDebugUnitTest --tests de.tobias.investmentradar.AppNavigationStateTest` and confirm RED because reducer does not exist.
- [ ] **Step 3: Implement the pure reducer** with explicit fields for root tab, detail id/return tab, savings-plan child state and root-owned overlays; return either `Consume(nextState)` or `ExitActivity`.
- [ ] **Step 4: Wire one root `BackHandler`** in `InvestmentRadarUi`; translate existing `tab`, `selectedDetailId`, dialogs/child-screen state into the reducer and apply only its single returned transition. Notification detail entry must set a valid return tab.
- [ ] **Step 5: Re-run the focused test and full JVM suite** with `./gradlew testDebugUnitTest`; require GREEN.
- [ ] **Step 6: Commit** `feat: add native in-app back navigation`.

### Task 2: Remove generic unavailable copy with cause-specific presentation

**Files:**
- Create: `android/app/src/main/java/de/tobias/investmentradar/AvailabilityPresentation.kt`
- Modify: `android/app/src/main/java/de/tobias/investmentradar/MainActivity.kt`
- Modify: `android/app/src/main/java/de/tobias/investmentradar/RadarScreen.kt`
- Modify: `android/app/src/main/java/de/tobias/investmentradar/InvestmentDetailScreen.kt`
- Modify: `android/app/src/main/java/de/tobias/investmentradar/AlertsScreen.kt`
- Modify other Kotlin UI files only where the forbidden phrase is found by repository scan.
- Test: `android/app/src/test/java/de/tobias/investmentradar/AvailabilityPresentationTest.kt`
- Test/contract: existing repository contract-test location that scans Android source.

**Interfaces:**
- Produces: `enum class DataUiState { NO_CURRENT_DATA, NO_ANALYSIS, NOT_IN_RADAR, NO_VERIFIED_TR_MAPPING, CONNECTION_FAILED }` and `fun DataUiState.userMessage(): String`.

- [ ] **Step 1: Write RED tests** asserting each state maps to its concrete German message and none contains `nicht verfügbar` case-insensitively.
- [ ] **Step 2: Add a source contract test** that scans user-visible Kotlin string literals/resources and fails on the forbidden phrase while allowing internal enum `KEINE_BELASTBARE_BEWERTUNG`.
- [ ] **Step 3: Run focused tests/contracts and confirm RED** against current UI copy.
- [ ] **Step 4: Implement presentation mapping and replace every generic occurrence** with the actual cause at its call site; do not hide missing data behind fabricated values.
- [ ] **Step 5: Re-run source scan and JVM suite** and require GREEN.
- [ ] **Step 6: Commit** `fix: replace generic unavailable states`.

### Task 3: Pure Advisor Engine and typgerechte inputs

**Files:**
- Create: `android/app/src/main/java/de/tobias/investmentradar/AdvisorModels.kt`
- Create: `android/app/src/main/java/de/tobias/investmentradar/AdvisorEngine.kt`
- Test: `android/app/src/test/java/de/tobias/investmentradar/AdvisorEngineTest.kt`

**Interfaces:**
- Produces: `AdvisorSignal`, `AdvisorDataFreshness`, `AdvisorInput`, `AdvisorResult`, `AdvisorEngine.evaluate(input: AdvisorInput): AdvisorResult`.
- `AdvisorInput` contains normalized quality, valuation, growth/earnings, fundamentals, momentum, risk, forecast range, freshness/completeness and instrument type; it deliberately contains no portfolio weight.

- [ ] **Step 1: Write RED tests** for NACHKAUFEN/HALTEN/REDUZIEREN/VERKAUFEN boundaries, missing-required-data fallback, stock-vs-ETF/fixed-income applicability, and two otherwise identical inputs with different hypothetical portfolio weights producing identical signals.
- [ ] **Step 2: Run focused tests and confirm RED.**
- [ ] **Step 3: Implement minimal deterministic scoring and rationale generation** using only supplied normalized metrics; required missing/stale data returns internal `KEINE_BELASTBARE_BEWERTUNG`.
- [ ] **Step 4: Run focused and full JVM tests; require GREEN.**
- [ ] **Step 5: Commit** `feat: add explainable depot advisor engine`.

### Task 4: Advisor normalization from existing radar/forecast data

**Files:**
- Create: `android/app/src/main/java/de/tobias/investmentradar/AdvisorInputFactory.kt`
- Modify only as necessary: `ForecastEngine.kt`, `DataFreshness.kt`, `RadarModels.kt`, `Models.kt`
- Test: `android/app/src/test/java/de/tobias/investmentradar/AdvisorInputFactoryTest.kt`

**Interfaces:**
- Consumes existing `InvestmentItem`, forecast and freshness data.
- Produces: `AdvisorInputFactory.from(item, forecast, freshness): AdvisorInput` without synthetic defaults.

- [ ] **Step 1: Write RED fixtures** for a stock, ETF, fixed-income instrument, and missing-data instrument.
- [ ] **Step 2: Confirm RED.**
- [ ] **Step 3: Implement explicit mapping** so unsupported metrics are absent rather than zero and no identifier/price is guessed.
- [ ] **Step 4: Run focused/full tests; require GREEN.**
- [ ] **Step 5: Commit** `feat: normalize radar data for advisor`.

### Task 5: Persist latest/previous advisor results and detect real changes

**Files:**
- Create: `android/app/src/main/java/de/tobias/investmentradar/AdvisorStore.kt`
- Create: `android/app/src/main/java/de/tobias/investmentradar/AdvisorChangePolicy.kt`
- Test: `android/app/src/test/java/de/tobias/investmentradar/AdvisorChangePolicyTest.kt`
- Test: `android/app/src/test/java/de/tobias/investmentradar/AdvisorStoreTest.kt`

**Interfaces:**
- Produces: `AdvisorSnapshot(current, previous)`, stable day key `itemId|yyyy-MM-dd`, and `AdvisorChangePolicy.notificationEvent(previous, current)` returning a stable event id or null.

- [ ] **Step 1: Write RED tests** for first reliable signal, unchanged signal, real signal change, reliable-to-unreliable transition, same-day rerun and bounded history.
- [ ] **Step 2: Confirm RED.**
- [ ] **Step 3: Implement JSON persistence and idempotent change policy** preserving last reliable result when a new run is unreliable.
- [ ] **Step 4: Run focused/full tests; require GREEN.**
- [ ] **Step 5: Commit** `feat: persist advisor signal history`.

### Task 6: Daily background coordinator and savings-plan due generation

**Files:**
- Create: `android/app/src/main/java/de/tobias/investmentradar/DailyAnalysisWorker.kt`
- Create: `android/app/src/main/java/de/tobias/investmentradar/DailyAnalysisScheduler.kt`
- Create: `android/app/src/main/java/de/tobias/investmentradar/DailyAnalysisCoordinator.kt`
- Modify: `android/app/src/main/java/de/tobias/investmentradar/InvestmentRadarApp.kt`
- Modify: `android/app/build.gradle.kts` only if WorkManager is not already declared.
- Test: `android/app/src/test/java/de/tobias/investmentradar/DailyAnalysisCoordinatorTest.kt`

**Interfaces:**
- `DailyAnalysisCoordinator.run(day: String)` first calls `SavingsPlanStore.ensureDueExecutions`, then evaluates known holdings, stores results and emits only change events.
- Scheduler uses one unique periodic WorkManager job.

- [ ] **Step 1: Write RED coordinator tests** proving due plans are generated without `SavingsPlansScreen`, same-day reruns are idempotent, and network/data failure does not mutate holdings.
- [ ] **Step 2: Confirm RED.**
- [ ] **Step 3: Implement coordinator and unique daily scheduler**; initialize scheduler from `InvestmentRadarApp`.
- [ ] **Step 4: Run focused/full tests; require GREEN.**
- [ ] **Step 5: Commit** `feat: run daily depot analysis in background`.

### Task 7: Local actionable notifications for advisor changes and due plans

**Files:**
- Create: `android/app/src/main/java/de/tobias/investmentradar/AdvisorNotificationManager.kt`
- Modify: `android/app/src/main/java/de/tobias/investmentradar/DailyAnalysisCoordinator.kt`
- Modify: `android/app/src/main/java/de/tobias/investmentradar/MainActivity.kt`
- Reuse/modify: `InvestmentMessagingService.kt` only where shared deep-link intent construction belongs.
- Test: `android/app/src/test/java/de/tobias/investmentradar/AdvisorNotificationPolicyTest.kt`

**Interfaces:**
- Produces stable notification/event IDs and intents with `openItemId` or savings-plan destination.
- Consumes Advisor change events and pending savings executions.

- [ ] **Step 1: Write RED tests** for exactly-one notification on a real signal change, none for unchanged results, and one due-plan notification per plan/date.
- [ ] **Step 2: Confirm RED.**
- [ ] **Step 3: Implement notification manager and deep-link extras**; notification text includes instrument and old/new action plus concise reason where applicable.
- [ ] **Step 4: Verify notification deep-link state returns into a sensible app parent using Task 1 reducer tests.**
- [ ] **Step 5: Run full JVM suite; require GREEN.**
- [ ] **Step 6: Commit** `feat: notify on actionable depot changes`.

### Task 8: Advisor UI in depot and investment detail

**Files:**
- Modify: `android/app/src/main/java/de/tobias/investmentradar/PortfolioDashboard.kt`
- Modify: `android/app/src/main/java/de/tobias/investmentradar/InvestmentDetailScreen.kt`
- Modify: `android/app/src/main/java/de/tobias/investmentradar/MainActivity.kt`
- Test: `android/app/src/test/java/de/tobias/investmentradar/AdvisorPresentationTest.kt`

**Interfaces:**
- Consumes persisted `AdvisorResult`/last reliable snapshot.
- Produces presentation model containing action label, rationale, positives, risks, target range, analyzed-at and concrete data-state message.

- [ ] **Step 1: Write RED presentation tests** for all four actionable signals, stale last-reliable state, no-analysis state, and no forbidden generic unavailable copy.
- [ ] **Step 2: Confirm RED.**
- [ ] **Step 3: Add compact action signal to depot rows and full advisor block to detail screen** while keeping P/L visually separate.
- [ ] **Step 4: Run focused/full tests and source-copy contract; require GREEN.**
- [ ] **Step 5: Commit** `feat: show depot advisor guidance`.

### Task 9: Safe Trade Republic app-link routing

**Files:**
- Create: `android/app/src/main/java/de/tobias/investmentradar/TradeRepublicLinkResolver.kt`
- Modify: current external-open call sites in `MainActivity.kt`, `RadarScreen.kt` and/or `InvestmentDetailScreen.kt` after exact repository inspection.
- Test: `android/app/src/test/java/de/tobias/investmentradar/TradeRepublicLinkResolverTest.kt`

**Interfaces:**
- Produces `ExternalTarget.VerifiedAppLink(uri)`, `ExternalTarget.Browser(uri)`, or `ExternalTarget.NoVerifiedMapping`.
- No URI scheme/package/product identifier may be added unless verified from an existing trustworthy source or existing confirmed app behavior.

- [ ] **Step 1: Write RED resolver tests** for verified app link, browser fallback, missing mapping and launch failure preserving current app state.
- [ ] **Step 2: Confirm RED.**
- [ ] **Step 3: Implement resolver using only verified mappings** and defensive intent resolution; use cause-specific message when no mapping exists.
- [ ] **Step 4: Run focused/full tests; require GREEN.**
- [ ] **Step 5: Commit** `fix: make Trade Republic routing defensive`.

### Task 10: Private Equity safety and one-time mapping readiness

**Files:**
- Modify: `SavingsPlanExecutionService.kt`
- Modify: `SavingsPlansScreen.kt`
- Test: existing/new `SavingsPlanExecutionServiceTest.kt`

**Interfaces:**
- Unmapped PE plans remain executable only after a verified real `itemId` is stored; no ticker/ISIN inference.

- [ ] **Step 1: Write/extend RED tests** proving both PE plans remain separate and cannot book holdings while unmapped.
- [ ] **Step 2: Confirm RED where current behavior is insufficient.**
- [ ] **Step 3: Keep execution hard-blocked for unmapped plans and show concrete `Keine verifizierte Produktzuordnung` copy; preserve an explicit future mapping seam without fake IDs.**
- [ ] **Step 4: Run savings-plan and full JVM tests; require GREEN.**
- [ ] **Step 5: Commit** `fix: keep private equity executions safely unmapped`.

### Task 11: Release version contracts and complete regression verification

**Files:**
- Modify: `android/app/build.gradle.kts` or repository version source to `versionName = "2.1.5"`, `versionCode = 58`.
- Modify: release/contract tests that currently expect 2.1.4/code57.
- Do not change backend expected version from `2.1.0`.

**Interfaces:**
- Produces release candidate 2.1.5/code58 only after all previous tasks are GREEN.

- [ ] **Step 1: Update version contract test first and run it RED** against 2.1.4/code57.
- [ ] **Step 2: Bump Android version only, then rerun version contract GREEN.**
- [ ] **Step 3: Run** `cd android && ./gradlew testDebugUnitTest` and require all JVM tests GREEN.
- [ ] **Step 4: Run repository contract/regression scripts** including source-copy scan, backend-version contract, portfolio/import/savings-plan contracts and release contracts; require GREEN.
- [ ] **Step 5: Run signed release build** using the repository's existing release workflow/build command; verify APK signing with the existing signature-verification step.
- [ ] **Step 6: Commit** `chore: prepare Android 2.1.5 release`.

### Task 12: Integration and immutable release evidence

**Files:**
- No feature code unless verification reveals a defect; any defect returns to the owning task with RED reproduction first.

- [ ] **Step 1: Review branch diff against the spec** and confirm every spec requirement maps to a completed task/test.
- [ ] **Step 2: Re-run fresh full verification after the final commit**; do not reuse earlier GREEN evidence.
- [ ] **Step 3: Open/merge the feature PR only when required checks are GREEN.**
- [ ] **Step 4: Confirm main CI is GREEN after merge.**
- [ ] **Step 5: Publish immutable `v2.1.5` APK through the existing release workflow and record workflow run, release URL, asset identity, size and SHA-256.**
- [ ] **Step 6: Do not call 2.2 complete until daily background analysis, actionable notifications, savings-plan background due checks, back navigation, forbidden-copy contract, signed build and release evidence are all freshly confirmed.**
