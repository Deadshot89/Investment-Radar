# Investment Radar 2.4 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Extend Investment Radar 2.3 into a verifiable event-aware action and execution advisor: verified market events can change analysis, the app creates durable concrete action plans, users record real executions after external broker use, and portfolio cost basis / realized and unrealized P&L update only from confirmed executions.

**Architecture:** Preserve the current pure Kotlin advisor/allocation pipeline and insert two new domain stages around it: verified `MarketEventImpact` before stability resolution, then persistent `ActionPlan` after advisor evaluation. Add a separate `TradeExecutionLedger` as the only new execution source; it projects confirmed executions into the existing `PortfolioPosition` average-cost engine instead of creating a parallel P&L implementation. Extend WorkManager, notification fingerprints and centralized navigation so UI/background work consume the same stored domain state.

**Tech Stack:** Kotlin, Jetpack Compose, WorkManager 2.10.0, SharedPreferences/JSON, Android notifications, JUnit 4, Gradle 8.13, Java 17, Node.js/Azure Functions backend, GitHub Actions.

**Spec:** `docs/superpowers/specs/2026-09-06-investment-radar-2.4-action-event-advisor-design.md`

## Global Constraints

- Start from the verified 2.3 commit `c1b0f8f145d6a1b069f61815bacdff2d01a1e975`; keep `main` untouched until full 2.4 verification.
- Android release target is `2.1.7`, `versionCode = 60`.
- Existing 2.2/2.3 contracts remain binding: no invented market/identity data, no automatic orders, no private Trade Republic APIs/scraping, stable back navigation, no generic visible `Nicht verfügbar` placeholder.
- `PortfolioPosition` remains the single cost-basis/P&L calculation implementation. The execution ledger may project transactions into it, but must not reimplement average-cost accounting in UI or a second calculator.
- Opening Trade Republic never counts as execution.
- Missing historical cost basis stays unknown; it must never become `0` merely to fill UI.
- Market events can influence a recommendation only when the event record is verified under the event policy.
- Because the current backend contract exposes no event feed, the backend event endpoint is treated as a separately versioned contract extension. Keep all current 2.1.0 endpoints backward compatible; bump the backend contract only after the RED contract test proves the new endpoint/fields are required.
- Use TDD for every behavioral task: RED test → verify intended failure → minimal implementation → focused GREEN → regression GREEN → commit.
- Do not merge/release on partial verification.

---

### Task 1: Market event domain model, verification and deterministic fingerprints

**Files:**
- Create: `android/app/src/main/java/de/tobias/investmentradar/MarketEventModels.kt`
- Create: `android/app/src/main/java/de/tobias/investmentradar/MarketEventEngine.kt`
- Create: `android/app/src/test/java/de/tobias/investmentradar/MarketEventEngineTest.kt`

**Interfaces:**

```kotlin
enum class MarketEventType {
    EARNINGS, GUIDANCE, PROFIT_WARNING, DIVIDEND, CAPITAL_ACTION,
    MANAGEMENT, M_AND_A, PRODUCT_OR_APPROVAL, REGULATORY_OR_LEGAL,
    CREDIT_RATING, MACRO, FUND_STRUCTURE
}

enum class MarketEventDirection { POSITIVE, NEGATIVE, MIXED, NEUTRAL }
enum class MarketEventHorizon { SHORT, MEDIUM, LONG }
enum class MarketEventVerification { VERIFIED_PRIMARY, VERIFIED_SECONDARY, UNVERIFIED }

data class MarketEvent(
    val eventId: String,
    val instrumentId: String,
    val type: MarketEventType,
    val title: String,
    val summary: String,
    val eventAt: String,
    val publishedAt: String,
    val sourceName: String,
    val sourceUrl: String,
    val verification: MarketEventVerification,
    val direction: MarketEventDirection,
    val horizon: MarketEventHorizon,
    val materiality: Int,
    val confidencePct: Int,
    val fingerprint: String
)

data class MarketEventImpact(
    val event: MarketEvent,
    val scoreAdjustment: Int,
    val criticalThesisBreak: Boolean,
    val reasons: List<String>
)
```

- `MarketEventEngine.normalize(raw: List<MarketEvent>): List<MarketEvent>` returns stable de-duplicated events.
- `MarketEventEngine.impacts(events: List<MarketEvent>): List<MarketEventImpact>` ignores `UNVERIFIED` for action-changing impact.
- Fingerprints use normalized instrument + type + canonical publication/source identity; timestamps alone are insufficient.

- [ ] **Step 1: Write RED tests** proving duplicate source records collapse, blank/invalid events are dropped, unverified events create no score-changing impact, primary/secondary verified events remain distinguishable, and only a high-materiality verified negative event can set `criticalThesisBreak = true`.

```kotlin
@Test
fun unverifiedProfitWarningCannotChangeAdvisorImpact() {
    val impact = MarketEventEngine.impacts(
        listOf(event(type = MarketEventType.PROFIT_WARNING, verification = MarketEventVerification.UNVERIFIED))
    )
    assertTrue(impact.isEmpty())
}
```

- [ ] **Step 2: Run focused unit test and confirm RED** because the event model/engine does not exist.
- [ ] **Step 3: Implement deterministic normalization** with materiality/confidence clamped `0..100`, no synthetic source URLs or inferred verification.
- [ ] **Step 4: Implement bounded impact scoring**. Normal verified events may adjust advisor score only within a bounded range; critical bypass is restricted to verified negative events with materiality `>= 85` and confidence `>= 80` in thesis-breaking categories (`PROFIT_WARNING`, `REGULATORY_OR_LEGAL`, `CREDIT_RATING`, material `GUIDANCE`).
- [ ] **Step 5: Focused test GREEN; run existing advisor tests to prove no regression.**
- [ ] **Step 6: Commit** `feat: add verified market event engine`.

### Task 2: Backend event contract and trusted-source ingestion gate

**Files:**
- Create: `backend/src/lib/marketEvents.mjs`
- Create: `backend/src/functions/marketEvents.mjs`
- Create: `backend/test/marketEvents.test.mjs`
- Modify: `backend/src/functions/instrumentDetail.mjs`
- Modify: backend/API contract version source used by release checks
- Modify: `android/app/src/main/java/de/tobias/investmentradar/ApiClient.kt`
- Modify: `android/app/src/main/java/de/tobias/investmentradar/RadarModels.kt`
- Create: `android/app/src/test/java/de/tobias/investmentradar/MarketEventApiContractTest.kt`

**Contract:**

`GET /api/market-events?ids=msft,meta` returns:

```json
{
  "generatedAt": "2026-09-06T00:00:00.000Z",
  "items": [
    {
      "eventId": "source-event-id",
      "instrumentId": "msft",
      "type": "GUIDANCE",
      "title": "Guidance updated",
      "summary": "Verified issuer guidance update",
      "eventAt": "2026-09-05T12:00:00.000Z",
      "publishedAt": "2026-09-05T12:05:00.000Z",
      "sourceName": "issuer",
      "sourceUrl": "https://example.com/issuer-release",
      "verification": "VERIFIED_PRIMARY",
      "direction": "POSITIVE",
      "horizon": "MEDIUM",
      "materiality": 80,
      "confidencePct": 95,
      "fingerprint": "stable-source-fingerprint"
    }
  ]
}
```

- The production loader accepts only explicitly configured trusted feeds/providers and preserves each provider's original URL/id. It never upgrades an unknown source to primary/verified.
- Provider failure returns a concrete event-data error/empty verified event set without overwriting last good Android event state.
- Existing dashboard/radar/instrument endpoints stay backward compatible.

- [ ] **Step 1: Add RED Node contract tests** proving current backend has no market-event contract, rejecting events without instrument/source/timestamp, rejecting unknown source verification escalation, and deduplicating a provider record by stable identity.
- [ ] **Step 2: Confirm RED.**
- [ ] **Step 3: Implement `marketEvents.mjs` provider normalization/cache boundary**. Keep network-provider code behind injectable loader functions so tests use deterministic fixtures and no live internet.
- [ ] **Step 4: Add `marketEvents` Azure Function** with bounded `ids` count matching the existing daily candidate universe; return source-level failures explicitly rather than fabricated events.
- [ ] **Step 5: Extend Android API DTO parsing**. Malformed event entries are skipped; an endpoint failure leaves previously persisted verified events intact.
- [ ] **Step 6: Bump backend contract to `2.2.0` only here**, because this is an additive endpoint required for 2.4. Update release contract tests accordingly while proving all old 2.1.0 routes remain compatible.
- [ ] **Step 7: Run backend tests + Android API contract tests; require GREEN.**
- [ ] **Step 8: Commit** `feat: add verified market event API contract`.

### Task 3: Event-aware advisor resolution and critical-event stability bypass

**Files:**
- Modify: `android/app/src/main/java/de/tobias/investmentradar/AdvisorModels.kt`
- Create: `android/app/src/main/java/de/tobias/investmentradar/EventAwareAdvisorPolicy.kt`
- Modify: `android/app/src/main/java/de/tobias/investmentradar/AdvisorStabilityPolicy.kt`
- Modify: `android/app/src/main/java/de/tobias/investmentradar/DailyAnalysisCoordinator.kt`
- Create: `android/app/src/test/java/de/tobias/investmentradar/EventAwareAdvisorPolicyTest.kt`
- Modify: `android/app/src/test/java/de/tobias/investmentradar/AdvisorStabilityPolicyTest.kt`
- Modify: `android/app/src/test/java/de/tobias/investmentradar/DailyAnalysisCoordinatorTest.kt`

**Interfaces:**

```kotlin
data class AdvisorEventContext(
    val impacts: List<MarketEventImpact>,
    val fingerprint: String,
    val criticalThesisBreak: Boolean
)
```

- Keep `AdvisorEngine.evaluate(input)` pure and unchanged for baseline quality/valuation/growth/momentum/risk/forecast score.
- `EventAwareAdvisorPolicy.apply(base: AdvisorResult, context: AdvisorEventContext): AdvisorResult` adds bounded verified-event effects and event reasons.
- `AdvisorStabilityPolicy.resolve(..., criticalEvent: Boolean = false)` may bypass the normal multi-day worse-signal confirmation only when `criticalEvent` is true and the event-adjusted result is materially worse.

- [ ] **Step 1: RED tests:** same baseline with unverified event gives identical result; ordinary verified negative event adjusts score but still obeys normal stability; critical verified thesis break can immediately resolve to `REDUZIEREN`/`VERKAUFEN`; positive event cannot use the critical negative bypass.
- [ ] **Step 2: Confirm RED.**
- [ ] **Step 3: Implement bounded event application** while preserving 6M/12M strategic dominance outside the explicit critical exception.
- [ ] **Step 4: Extend `DailyAnalysisCoordinator.analyze`** with `eventsByInstrument: Map<String, List<MarketEvent>> = emptyMap()` and apply event context before stability resolution.
- [ ] **Step 5: Focused tests + full advisor regression GREEN.**
- [ ] **Step 6: Commit** `feat: integrate verified events into advisor stability`.

### Task 4: Durable ActionPlan domain and concrete source-target-cash planning

**Files:**
- Create: `android/app/src/main/java/de/tobias/investmentradar/ActionPlanModels.kt`
- Create: `android/app/src/main/java/de/tobias/investmentradar/ActionPlanEngine.kt`
- Create: `android/app/src/test/java/de/tobias/investmentradar/ActionPlanEngineTest.kt`
- Reuse: `PortfolioAdvisorEngine.kt`, `ReallocationPolicy.kt`, `SavingsPlanBudget.kt`

**Interfaces:**

```kotlin
enum class ActionType {
    BUY_MORE, OPEN_POSITION, REDUCE, SELL, KEEP_SAVINGS_PLAN,
    REVIEW_SAVINGS_PLAN, HOLD_CASH, NO_ACTION
}

enum class ActionStatus {
    OFFEN, IN_BEARBEITUNG, TEILWEISE_AUSGEFUEHRT, AUSGEFUEHRT,
    NICHT_AUSGEFUEHRT, VERALTET, ERSETZT
}

data class PlannedAction(
    val actionId: String,
    val planId: String,
    val instrumentId: String,
    val type: ActionType,
    val amountEur: Double,
    val plannedShares: Double?,
    val fromInstrumentId: String?,
    val toInstrumentId: String?,
    val priority: Int,
    val reason: String,
    val eventFingerprints: List<String>,
    val confidencePct: Int,
    val createdAt: String,
    val status: ActionStatus
)

data class ActionPlan(
    val planId: String,
    val analysisDay: String,
    val availableBudgetEur: Double,
    val actions: List<PlannedAction>,
    val cashEur: Double
)
```

- `ActionPlanEngine.build(...)` converts the existing `PortfolioAdvisorPlan`, current prices, savings plan state and verified event fingerprints into a complete plan.
- For a reallocation, explicit source reduction + one/more target purchase actions + remaining cash must reconcile mathematically.
- Planned shares are only created when current price is reliable and positive.

- [ ] **Step 1: RED invariant tests:** action use + cash never exceeds available capital; savings contribution not double counted; sell/reduce + active savings plan creates review action; reallocation has source/target linkage and reconciles; no reliable price → amount exists but `plannedShares == null`; weak plan → cash/no-action instead of forced purchase.
- [ ] **Step 2: Confirm RED.**
- [ ] **Step 3: Implement deterministic IDs** from plan day + instrument + action type + source/target + event fingerprint, not random UI UUIDs.
- [ ] **Step 4: Implement prioritization**: critical reduce/sell > normal reduce/sell > savings conflict > strong buy/open > hold cash/no action.
- [ ] **Step 5: Focused tests + existing `PortfolioAdvisorEngineTest`, `ReallocationPolicyTest`, `SavingsPlanBudgetTest` GREEN.**
- [ ] **Step 6: Commit** `feat: create concrete persistent action plans`.

### Task 5: ActionPlanStore supersession instead of silent overwrite

**Files:**
- Create: `android/app/src/main/java/de/tobias/investmentradar/ActionPlanStore.kt`
- Create: `android/app/src/test/java/de/tobias/investmentradar/ActionPlanStoreTest.kt`
- Modify: `android/app/src/main/java/de/tobias/investmentradar/PortfolioAdvisorStore.kt` only for migration/read compatibility if required

**Rules:**

- Keep a bounded history of plans/actions, not just `latest_plan_v1`.
- Same deterministic plan/action written twice is idempotent.
- Materially changed successor marks old open action `VERALTET` or `ERSETZT` and stores `replacedByActionId`.
- Executed historical actions are immutable except explicit execution-ledger correction linkage.

- [ ] **Step 1: RED tests** for repeat-save idempotency, superseding changed amount/action, preserving executed action, bounded history, JSON round-trip, corrupt-entry tolerance.
- [ ] **Step 2: Confirm RED.**
- [ ] **Step 3: Implement pure `ActionPlanHistoryState.merge(old, new)` first**, then SharedPreferences JSON adapter.
- [ ] **Step 4: Add migration path** from current `PortfolioAdvisorStore.latest()` so existing 2.3 users do not lose their last advisor plan on update.
- [ ] **Step 5: Tests GREEN.**
- [ ] **Step 6: Commit** `feat: persist and supersede advisor actions safely`.

### Task 6: TradeExecutionLedger and projection into existing PortfolioPosition accounting

**Files:**
- Create: `android/app/src/main/java/de/tobias/investmentradar/TradeExecutionModels.kt`
- Create: `android/app/src/main/java/de/tobias/investmentradar/TradeExecutionLedger.kt`
- Create: `android/app/src/test/java/de/tobias/investmentradar/TradeExecutionLedgerTest.kt`
- Modify: `android/app/src/main/java/de/tobias/investmentradar/PortfolioPosition.kt`
- Modify: `android/app/src/test/java/de/tobias/investmentradar/PortfolioImportedPurchaseTest.kt`
- Modify/add focused transaction tests around `PortfolioPosition`

**Interfaces:**

```kotlin
enum class TradeSide { BUY, SELL }
enum class TradeExecutionStatus { CONFIRMED, CORRECTED, VOIDED }
enum class TradeExecutionSource { MANUAL, SAVINGS_PLAN }

data class TradeExecution(
    val executionId: String,
    val actionId: String?,
    val instrumentId: String,
    val side: TradeSide,
    val executedAt: String,
    val shares: Double,
    val priceEur: Double,
    val feesEur: Double,
    val taxesEur: Double?,
    val note: String,
    val source: TradeExecutionSource,
    val status: TradeExecutionStatus,
    val replacesExecutionId: String? = null
)
```

- `TradeExecutionLedger.apply(position, executions)` converts confirmed non-replaced BUYs to `PortfolioPurchase` and SELLs to `PortfolioSale` using the execution ID as the underlying transaction ID.
- BUY cost basis contribution = `shares * priceEur + feesEur`.
- SELL proceeds used for performance = `shares * priceEur - feesEur`; tax is stored/displayed but not silently mixed into the average-cost basis calculation.
- Oversell returns a domain rejection and never mutates the portfolio.

- [ ] **Step 1: RED tests** for one-time application, duplicate execution idempotency, partial fills, fees, oversell rejection, correction replacement, voided execution ignored, imported opening position materialization, and missing imported cost basis remaining unknown.
- [ ] **Step 2: Confirm RED.**
- [ ] **Step 3: Implement ledger normalization/correction resolution** as a pure function before projection.
- [ ] **Step 4: Project to current `PortfolioPosition.upsertPurchaseIfValid` / `upsertSale`**, preserving its existing average-cost and realized/unrealized P&L implementation.
- [ ] **Step 5: Run new tests plus existing `scripts/test-transaction-ledger.sh`, `PortfolioMetricsTest`, snapshot/cost-basis tests; require GREEN.**
- [ ] **Step 6: Commit** `feat: add confirmed trade execution ledger`.

### Task 7: Persistent execution store, partial-action reconciliation and ViewModel write path

**Files:**
- Create: `android/app/src/main/java/de/tobias/investmentradar/TradeExecutionStore.kt`
- Create: `android/app/src/test/java/de/tobias/investmentradar/TradeExecutionStoreTest.kt`
- Create: `android/app/src/main/java/de/tobias/investmentradar/ActionExecutionReconciler.kt`
- Create: `android/app/src/test/java/de/tobias/investmentradar/ActionExecutionReconcilerTest.kt`
- Modify: `android/app/src/main/java/de/tobias/investmentradar/MainViewModel.kt`
- Modify: `android/app/src/main/java/de/tobias/investmentradar/SavingsPlanExecutionService.kt`

**Rules:**

- Store executions independently from `PortfolioStore` and rebuild/projection must be idempotent.
- Recording a confirmed execution atomically validates projected position first, then persists execution and resulting position; reject invalid sell before either write.
- An action becomes `TEILWEISE_AUSGEFUEHRT` when confirmed executions cover more than zero but less than its planned amount/shares within tolerance.
- Action becomes `AUSGEFUEHRT` only after coverage reaches tolerance or explicit user completion.
- Existing manual purchase/sale UI must call the new ViewModel ledger command; it may not bypass the ledger.
- Confirmed savings-plan executions create a `TradeExecution` only when sufficient execution data (shares + actual price) has been entered/confirmed; otherwise the savings event remains a schedule confirmation without inventing shares.

- [ ] **Step 1: RED tests** for partial/complete coverage, multiple fills, duplicate command id, invalid sell atomic rejection, correction updating action reconciliation, and no execution generated from broker-link/open action.
- [ ] **Step 2: Confirm RED.**
- [ ] **Step 3: Implement SharedPreferences JSON store with bounded audit history** and explicit correction links.
- [ ] **Step 4: Replace `MainViewModel.upsertPurchase/upsertSale` write path** with execution commands while keeping a migration adapter for existing stored purchases/sales.
- [ ] **Step 5: Tests GREEN including current purchase/sale store contracts.**
- [ ] **Step 6: Commit** `feat: reconcile real executions with advisor actions`.

### Task 8: Push fingerprinting based on material cause, not analysis date

**Files:**
- Modify: `android/app/src/main/java/de/tobias/investmentradar/AdvisorChangePolicy.kt`
- Modify: `android/app/src/main/java/de/tobias/investmentradar/AdvisorNotificationManager.kt`
- Modify: `android/app/src/test/java/de/tobias/investmentradar/AdvisorNotificationPolicyTest.kt`
- Create: `android/app/src/test/java/de/tobias/investmentradar/NotificationFingerprintTest.kt`

**Interfaces:**

```kotlin
object NotificationFingerprint {
    fun forAction(
        instrumentId: String,
        action: PortfolioAdvisorAction,
        amountEur: Int?,
        eventFingerprints: List<String>,
        reliability: Boolean
    ): String
}
```

- Date/time is metadata, not the identity key.
- Amount is normalized to a material tolerance bucket so tiny price/allocation drift does not spam.
- A materially different action, critical event fingerprint, reliability state or amount bucket permits one new notification.

- [ ] **Step 1: RED tests:** same signal next day → same fingerprint/no new push; same reallocation with trivial amount drift → no new push; new critical event → new fingerprint; action change → new fingerprint; reliability loss → new fingerprint.
- [ ] **Step 2: Confirm RED.**
- [ ] **Step 3: Replace date-based `AdvisorNotificationEvent.id` construction** with stable fingerprints while keeping display `analysisDay`.
- [ ] **Step 4: Extend notification intent** with `openActionId` for actionable notifications.
- [ ] **Step 5: Focused tests + current notification ledger tests GREEN.**
- [ ] **Step 6: Commit** `feat: deduplicate advisor pushes by material event`.

### Task 9: Central navigation for Action Center, action detail and trade entry

**Files:**
- Modify: `android/app/src/main/java/de/tobias/investmentradar/AppNavigationState.kt`
- Modify: `android/app/src/main/java/de/tobias/investmentradar/PushNavigationTarget.kt`
- Modify: `android/app/src/main/java/de/tobias/investmentradar/MainActivity.kt`
- Modify: `android/app/src/test/java/de/tobias/investmentradar/AppNavigationStateTest.kt`
- Modify: `android/app/src/test/java/de/tobias/investmentradar/PushNavigationTargetTest.kt`
- Modify: `android/app/src/test/java/de/tobias/investmentradar/MainBackHandlerContractTest.kt`

**Navigation model additions:**

```kotlin
enum class AppChildScreen { NONE, SAVINGS_PLANS, ACTION_CENTER }

enum class AppOverlay {
    NONE, BUDGET, PURCHASE_HISTORY, CUSTOM_ASSET, EDIT_CUSTOM_ASSET,
    MISSING_ALERT_ITEM, UPDATE, UPDATE_STATUS, TRADE_EXECUTION
}
```

Add `actionId: String?` and `actionReturnTab`/return-child information to `AppNavigationState` rather than scattering extra local booleans.

- [ ] **Step 1: RED tests** for the exact back priority: trade overlay → action detail → originating Action Center/portfolio → root; investment detail preserves caller; one press removes exactly one level; notification deep-link to action has a valid route back.
- [ ] **Step 2: Confirm RED.**
- [ ] **Step 3: Extend pure navigation state first.**
- [ ] **Step 4: Wire `MainActivity` local state to the pure model** and add `openActionId` handling without changing existing savings/alert deep links.
- [ ] **Step 5: Tests GREEN.**
- [ ] **Step 6: Commit** `feat: add action center navigation and deep links`.

### Task 10: Action Center and three-layer detail UI

**Files:**
- Create: `android/app/src/main/java/de/tobias/investmentradar/ActionCenterScreen.kt`
- Create: `android/app/src/main/java/de/tobias/investmentradar/ActionDetailScreen.kt`
- Create: `android/app/src/main/java/de/tobias/investmentradar/TradeExecutionDialog.kt`
- Modify: `android/app/src/main/java/de/tobias/investmentradar/PortfolioDashboard.kt`
- Modify: `android/app/src/main/java/de/tobias/investmentradar/InvestmentDetailScreen.kt`
- Modify: `android/app/src/main/java/de/tobias/investmentradar/MainActivity.kt`
- Create: `android/tests/test-investment-radar-2-4-ui.sh`
- Modify: `android/app/src/test/java/de/tobias/investmentradar/VisibleCopyContractTest.kt`

**UI contract:**

- Dashboard primary card: `Was soll ich jetzt tun?` with invest / reallocate / cash / savings conflict / urgent event summary.
- Tapping an action opens Action Detail first; it does not launch Trade Republic directly.
- Action Detail clearly separates `Analyse`, `Empfehlung`, `Ausführung`.
- `Bei Trade Republic öffnen` only invokes existing verified `TradeRepublicNavigator`.
- After broker use, `Ausführung erfassen` asks for real timestamp/date, shares, execution price, optional fees/tax/note.
- Portfolio cards show current value, known cost basis, unrealized P/L and realized P/L only when calculable; unknown basis gets a concrete German explanation instead of `0`.
- Event cards show source, published time, verification and whether the recommendation changed.

- [ ] **Step 1: Add RED shell/UI contract** requiring Action Center strings/wiring, trade capture labels, event verification labels, no automatic execution on broker launch, and prohibiting generic `Nicht verfügbar` copy.
- [ ] **Step 2: Confirm RED.**
- [ ] **Step 3: Implement Action Center** from `ActionPlanStore`, sorted by priority/status.
- [ ] **Step 4: Implement Action Detail and execution dialog** using ViewModel ledger command; validate positive finite shares/price and reject oversell with concrete message.
- [ ] **Step 5: Restructure investment detail into three semantic sections** while reusing existing forecast/advisor/portfolio components.
- [ ] **Step 6: Run UI contract, VisibleCopy, TradeRepublic navigator and back-navigation contracts; require GREEN.**
- [ ] **Step 7: Commit** `feat: add action center and execution workflow`.

### Task 11: DailyAnalysisWorker orchestration, event cache and idempotent stored plan

**Files:**
- Modify: `android/app/src/main/java/de/tobias/investmentradar/DailyAnalysisWorker.kt`
- Create: `android/app/src/main/java/de/tobias/investmentradar/MarketEventStore.kt`
- Create: `android/app/src/test/java/de/tobias/investmentradar/MarketEventStoreTest.kt`
- Modify: `android/app/src/test/java/de/tobias/investmentradar/DailyAnalysisCoordinatorTest.kt`
- Add/update worker contract shell test if current JVM setup cannot instantiate WorkManager directly

**Pipeline:**

1. Ensure due savings executions.
2. Load portfolio + bounded radar candidate universe.
3. Load market events for those IDs; on failure retain last verified cached event set with freshness marker.
4. Normalize/verify events.
5. Run baseline advisor + event policy + stability.
6. Build `PortfolioAdvisorPlan`.
7. Build/merge durable `ActionPlan`.
8. Persist advisor/event/action state.
9. Generate stable material notification fingerprints.
10. Publish advisor/action/savings notifications.

- [ ] **Step 1: RED orchestration tests/contracts** proving event API failure cannot erase last good events or advisor state, repeated same-day run is idempotent, repeated next-day same material state does not duplicate action/push, and a new critical event creates a new action/push.
- [ ] **Step 2: Confirm RED.**
- [ ] **Step 3: Implement bounded event cache** keyed by instrument/fingerprint with explicit fetched-at/source status.
- [ ] **Step 4: Wire worker in the exact pipeline above.**
- [ ] **Step 5: Run DailyAnalysis, store, notification and action tests; require GREEN.**
- [ ] **Step 6: Commit** `feat: orchestrate daily event action analysis`.

### Task 12: Version, release contracts and full verification

**Files:**
- Modify: `android/app/build.gradle.kts`
- Modify: `android/tests/test-version-contract.sh`
- Modify: `android/tests/test-release-backend-gate.sh`
- Modify other release/readme metadata that explicitly pins Android/backend contract versions
- Do not change existing release tag/assets for 2.1.6.

**Version target:**

```kotlin
versionCode = 60
versionName = "2.1.7"
```

Backend contract target becomes `2.2.0` only if Task 2's additive event endpoint is implemented; otherwise the release gate must prove the app is still compatible with `2.1.0` and event functionality cannot be described as complete.

- [ ] **Step 1: Add RED version/release contract expectations** for Android `2.1.7` / code `60` and the actually implemented backend contract.
- [ ] **Step 2: Confirm RED on the old 2.1.6 metadata.**
- [ ] **Step 3: Update version metadata.**
- [ ] **Step 4: Run focused JVM suites:** event engine/API, event-aware advisor, action plan/store, execution ledger/store/reconciler, notification fingerprints, navigation, current 2.2/2.3 advisor/portfolio/savings tests.
- [ ] **Step 5: Run shell contracts:** transaction ledger, purchase/sale history, Trade Republic current depot/links/navigator wiring, 2.3 UI, new 2.4 UI, version contract, release backend gate.
- [ ] **Step 6: Run complete Android unit suite** `cd android && gradle --no-daemon :app:testDebugUnitTest` using the same signing properties/environment as `.github/workflows/android-unit-tests.yml`.
- [ ] **Step 7: Run backend test suite** and all backend contract tests.
- [ ] **Step 8: Build signed Android release APK/AAB through the repository workflow; verify build/signature artifact and exact version.**
- [ ] **Step 9: Verify GitHub Actions on the exact implementation head commit.** No rerun without a code/config cause when a job fails.
- [ ] **Step 10: Compare implementation branch against `main`; ensure only intentional 2.4 changes exist.**
- [ ] **Step 11: Only after all checks are GREEN, integrate to `main` and verify the post-merge commit/workflows again.**

## Definition of Done

2.4 is complete only when the repository proves all of the following on one exact commit:

- verified event ingestion + deterministic event policy works and unverified content cannot alter actions;
- critical-event bypass is narrow and tested;
- concrete action plans reconcile budget/reallocation/cash and persist without silent overwrite;
- broker launch is never treated as execution;
- confirmed trade executions are idempotent, correctable/auditable and project into the existing portfolio cost-basis engine;
- partial executions update action status correctly;
- cost basis and realized/unrealized P&L remain unknown when source data is insufficient instead of showing false zeroes;
- notification identity is based on material cause, not daily timestamps;
- Action Center, deep links and Android Back behavior meet the 2.4 hierarchy;
- existing 2.2/2.3 functionality remains regression-free;
- Android 2.1.7/code60 and the actual backend contract pass release gates;
- signed release build and GitHub workflows are green before `main` changes.