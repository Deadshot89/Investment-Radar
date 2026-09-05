# Savings Plans Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add editable Trade Republic savings plans that require user confirmation before they become real portfolio purchases, with automatic quote-based share calculation.

**Architecture:** Keep scheduled plans and pending executions in a dedicated local store, separate from `PortfolioStore`. A confirmation service resolves the current quote, then persists one purchase through the existing portfolio transaction path and marks the stable execution id completed only after persistence succeeds. UI and alert integration expose due plans without letting planned EUR amounts affect portfolio totals.

**Tech Stack:** Kotlin, Android/Jetpack Compose, SharedPreferences/JSON patterns already used by the app, existing quote/API client and alert infrastructure, JUnit, shell contract tests, GitHub Actions.

**Spec:** `docs/superpowers/specs/2026-09-05-savings-plans-design.md`

## Global Constraints
- Planned savings-plan amounts never count as holdings before confirmation.
- Confirmation requires no manual price entry; use a valid positive current quote.
- Failed quote lookup leaves the execution pending and creates no purchase.
- Execution id is stable per plan and scheduled date; repeated confirmation cannot double-book.
- The two Private Equity plans remain separate and cannot book into an unrelated asset until their instrument identity is known.
- Android release must advance beyond 2.1.2; backend stays 2.1.0 unless a backend contract change is required.

---

### Task 1: Savings-plan domain and recurrence

**Files:**
- Create: `android/app/src/main/java/de/tobias/investmentradar/SavingsPlan.kt`
- Create: `android/app/src/test/java/de/tobias/investmentradar/SavingsPlanTest.kt`

**Interfaces:**
- Produces: `SavingsPlan`, `SavingsPlanFrequency`, `SavingsPlanExecution`, `SavingsPlanExecutionStatus`, and pure recurrence helpers for monthly/twice-monthly due dates.

- [ ] **Step 1:** Write failing JUnit tests proving monthly recurrence, two distinct monthly execution slots, stable `planId + scheduledDate` execution ids, and disabled plans producing no due execution.
- [ ] **Step 2:** Run `cd android && ./gradlew testDebugUnitTest --tests '*SavingsPlanTest'` and verify RED because the domain types do not exist.
- [ ] **Step 3:** Implement immutable domain types and pure recurrence functions. Twice-monthly plans store two editable day-of-month values; invalid end-of-month days clamp to the last calendar day.
- [ ] **Step 4:** Re-run the focused test and verify GREEN.
- [ ] **Step 5:** Commit domain plus tests.

### Task 2: Persistent plan/execution store and initial import

**Files:**
- Create: `android/app/src/main/java/de/tobias/investmentradar/SavingsPlanStore.kt`
- Create: `android/app/src/test/java/de/tobias/investmentradar/SavingsPlanStoreTest.kt`

**Interfaces:**
- Consumes: Task 1 domain types.
- Produces: load/save/update APIs for plans and execution records; one-time initial seed for Meta, Samsung, two distinct Private Equity plans, and Microsoft.

- [ ] **Step 1:** Write failing tests proving the initial five plan records, separate Private Equity ids, editable due dates, persistence, and that seed migration never duplicates existing plans.
- [ ] **Step 2:** Run the focused store tests and verify RED.
- [ ] **Step 3:** Implement the store using the repository's existing local persistence conventions. Map Meta/Samsung/Microsoft to known investment ids; leave Private Equity instrument id null rather than guessing.
- [ ] **Step 4:** Re-run focused tests and verify GREEN.
- [ ] **Step 5:** Commit store and seed.

### Task 3: Idempotent confirmation into the real portfolio

**Files:**
- Create: `android/app/src/main/java/de/tobias/investmentradar/SavingsPlanExecutionService.kt`
- Create: `android/app/src/test/java/de/tobias/investmentradar/SavingsPlanExecutionServiceTest.kt`
- Modify: `android/app/src/main/java/de/tobias/investmentradar/PortfolioStore.kt` only if a small reusable purchase-persistence entry point is required.

**Interfaces:**
- Consumes: a pending `SavingsPlanExecution`, its plan, current quote provider, and portfolio purchase persistence.
- Produces: `confirmExecution(executionId)` and `skipExecution(executionId)` results with used EUR amount, price and shares when confirmed.

- [ ] **Step 1:** Write failing tests for `shares = amountEur / quoteEur`, successful purchase persistence, quote <= 0 failure, missing instrument failure, skip-without-purchase, and repeated confirmation returning the existing result without a second purchase.
- [ ] **Step 2:** Run focused execution-service tests and verify RED.
- [ ] **Step 3:** Implement minimal confirmation orchestration. Persist the portfolio purchase before setting execution status to confirmed; on any failure keep it pending. Skip changes only execution state and recurrence.
- [ ] **Step 4:** Re-run focused tests and verify GREEN.
- [ ] **Step 5:** Commit confirmation service.

### Task 4: Savings-plan screen and confirmation flow

**Files:**
- Create: `android/app/src/main/java/de/tobias/investmentradar/SavingsPlansScreen.kt`
- Modify: `android/app/src/main/java/de/tobias/investmentradar/MainActivity.kt`
- Add/modify focused UI contract test under `android/tests/`.

**Interfaces:**
- Consumes: plan store and execution service.
- Produces: portfolio navigation entry, active-plan list, amount/frequency/next date editor, pending execution card, `Ausgeführt` and `Nicht ausgeführt` actions.

- [ ] **Step 1:** Add a failing source/UI contract requiring `Sparpläne`, `Ausgeführt`, `Nicht ausgeführt`, editable execution dates, and no direct portfolio-value addition from planned amounts.
- [ ] **Step 2:** Run the contract and verify RED.
- [ ] **Step 3:** Build the Compose screen following current visual components. Confirmation shows amount and instrument; price is automatic and is shown after successful booking. Private Equity without an instrument id disables `Ausgeführt` with a clear identification-required message.
- [ ] **Step 4:** Run the focused contract plus JVM tests and verify GREEN.
- [ ] **Step 5:** Commit UI/navigation.

### Task 5: Due execution alerts and notification routing

**Files:**
- Modify: `android/app/src/main/java/de/tobias/investmentradar/AlertCenterState.kt`
- Modify: `android/app/src/main/java/de/tobias/investmentradar/AlertStore.kt` and/or existing notification scheduler at the narrowest existing integration point.
- Modify: `android/app/src/main/java/de/tobias/investmentradar/AlertsScreen.kt` if routing needs an explicit action.
- Add/modify focused tests under `android/app/src/test/` and `android/tests/`.

**Interfaces:**
- Consumes: due pending executions.
- Produces: one user-visible `Sparplan zur Bestätigung` alert per execution id and navigation to the pending confirmation flow.

- [ ] **Step 1:** Write failing tests proving one alert per due execution, no alert for future/disabled/skipped/confirmed executions, and routing to Savings Plans.
- [ ] **Step 2:** Run focused tests and verify RED.
- [ ] **Step 3:** Integrate due executions with existing alert/notification infrastructure, deduplicated by stable execution id.
- [ ] **Step 4:** Re-run focused tests and verify GREEN.
- [ ] **Step 5:** Commit alert integration.

### Task 6: Release gate and complete verification

**Files:**
- Modify: `android/app/build.gradle.kts` or current Android version source.
- Modify: release contract tests that currently expect 2.1.2/code 55.

**Interfaces:**
- Produces: Android 2.1.3/code 56 candidate; backend expected version remains 2.1.0.

- [ ] **Step 1:** Change release contract expectation first to Android `2.1.3`, versionCode `56`, backend `2.1.0`, and verify RED against the still-2.1.2 app.
- [ ] **Step 2:** Bump Android version to 2.1.3/code 56 and verify the focused release contract GREEN.
- [ ] **Step 3:** Run all Android source contract tests.
- [ ] **Step 4:** Run `cd android && ./gradlew testDebugUnitTest` and verify GREEN.
- [ ] **Step 5:** Trigger/observe the signed APK workflow and require build, APK signature verification, backend gate, updater publication and artifact upload all GREEN.
- [ ] **Step 6:** Verify the immutable `v2.1.3` release contains `investment-radar.apk` before calling the feature complete.
