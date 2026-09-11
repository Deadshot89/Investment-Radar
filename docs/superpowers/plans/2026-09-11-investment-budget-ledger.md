# Investment Budget Ledger Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the static monthly budget with a persisted cash ledger tied to executed investment trades and portfolio aggregation.

**Architecture:** Add pure Kotlin domain models/engines for budget and trades, then persistence stores using the app's existing SharedPreferences pattern. Portfolio aggregation reads ledger first and falls back to legacy snapshots. UI integration consumes one budget summary model and writes execution events only after user confirmation.

**Tech Stack:** Kotlin, Android SharedPreferences, existing PortfolioStore/Advisor flow, JUnit tests.

**Spec:** `docs/superpowers/specs/2026-09-11-investment-budget-ledger-design.md`

## Global Constraints
- Existing portfolio snapshots remain readable.
- Recommendations do not spend money until confirmed as executed.
- Buys decrease available cash; sells increase available cash.
- Extra deposits such as Wechselgeld increase available cash independently of the monthly budget.
- Same ISIN must aggregate into one position.
- MSCI reference total: 4.339524 + 0.560698 = 4.900222 shares.

---

### Task 1: Pure budget and trade domain

**Files:**
- Create: `android/app/src/main/java/de/tobias/investmentradar/InvestmentLedger.kt`
- Test: `android/app/src/test/java/de/tobias/investmentradar/InvestmentLedgerTest.kt`

**Interfaces:**
- Produces: `InvestmentTrade`, `CashEntry`, `BudgetSummary`, `InvestmentLedgerEngine.summarize(...)`, `InvestmentLedgerEngine.aggregatePosition(...)`.

- [ ] **Step 1: Write failing tests** covering buy decreases cash, sell increases cash, extra deposit increases cash, recommendation reservation does not alter invested amount, same asset aggregates shares, and MSCI shares equal `4.900222`.
- [ ] **Step 2: Run** `./gradlew :app:testDebugUnitTest --tests de.tobias.investmentradar.InvestmentLedgerTest` and verify RED.
- [ ] **Step 3: Implement minimal pure Kotlin engine** with immutable data classes and deterministic calculations.
- [ ] **Step 4: Run the test again** and verify GREEN.
- [ ] **Step 5: Commit** `feat: add investment cash and trade ledger engine`.

### Task 2: Persist ledger and budget movements

**Files:**
- Create: `android/app/src/main/java/de/tobias/investmentradar/InvestmentLedgerStore.kt`
- Test: `android/app/src/test/java/de/tobias/investmentradar/InvestmentLedgerStoreContractTest.kt`

**Interfaces:**
- Consumes: Task 1 models.
- Produces: `readTrades`, `appendTrade`, `readCashEntries`, `appendCashEntry`, stable JSON serialization.

- [ ] **Step 1: Write failing persistence contract tests** for round-trip fields and duplicate event IDs.
- [ ] **Step 2: Verify RED** with the targeted Gradle test.
- [ ] **Step 3: Implement SharedPreferences JSON persistence** following existing store patterns.
- [ ] **Step 4: Verify GREEN** and run all unit tests.
- [ ] **Step 5: Commit** `feat: persist investment ledger events`.

### Task 3: Connect advisor allocations to real available budget

**Files:**
- Modify: `android/app/src/main/java/de/tobias/investmentradar/PortfolioAdvisorEngine.kt`
- Test: corresponding advisor unit test file in `android/app/src/test/...`

**Interfaces:**
- Consumes: `BudgetSummary.availableEur` and `reservedEur`.
- Produces: allocations whose total never exceeds actual available cash.

- [ ] **Step 1: Add failing test**: after an executed €40 buy from €100 cash, advisor receives €60, not €100.
- [ ] **Step 2: Verify RED**.
- [ ] **Step 3: Add an overload/input model accepting live available cash** while preserving existing callers.
- [ ] **Step 4: Verify GREEN** and advisor regression tests.
- [ ] **Step 5: Commit** `feat: allocate advisor recommendations from live cash`.

### Task 4: Portfolio aggregation and legacy fallback

**Files:**
- Modify: `android/app/src/main/java/de/tobias/investmentradar/PortfolioStore.kt`
- Modify: `android/app/src/main/java/de/tobias/investmentradar/UserPortfolioSeed.kt`
- Test: portfolio store/seed tests.

**Interfaces:**
- Ledger trades override snapshot-derived shares/cost basis only for assets that have ledger trades.
- Legacy positions remain unchanged otherwise.

- [ ] **Step 1: Add failing migration tests**.
- [ ] **Step 2: Verify RED**.
- [ ] **Step 3: Implement ledger-first aggregation with snapshot fallback**.
- [ ] **Step 4: Verify GREEN**, including MSCI no-duplicate regression.
- [ ] **Step 5: Commit** `feat: derive portfolio positions from executed trades`.

### Task 5: Budget UI and execution actions

**Files:**
- Modify the existing portfolio/advisor Compose screen files discovered from current branch.
- Add targeted UI/state tests where available.

**Interfaces:**
- Displays `Monatsbudget`, `Zusätzlich`, `Investiert`, `Verfügbar`, `Reserviert`.
- `Kauf ausgeführt` appends a BUY trade and removes matching reservation.
- `Verkauf ausgeführt` appends a SELL trade and credits proceeds.

- [ ] **Step 1: Add failing state/UI contract tests** for labels and execution behavior.
- [ ] **Step 2: Verify RED**.
- [ ] **Step 3: Implement budget card, history entry point, and execution buttons**.
- [ ] **Step 4: Verify GREEN** and run `./gradlew :app:testDebugUnitTest :app:assembleDebug`.
- [ ] **Step 5: Commit** `feat: add managed investment budget workflow`.

### Task 6: Verify and integrate

- [ ] Run the complete Android unit-test suite.
- [ ] Build debug APK.
- [ ] Check MSCI reference aggregation and budget examples manually through deterministic tests.
- [ ] Open PR from `feature/investment-budget-ledger` to `main` with migration and user-visible behavior documented.
