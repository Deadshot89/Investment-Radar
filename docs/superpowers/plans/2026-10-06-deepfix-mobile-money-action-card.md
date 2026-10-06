# Mobile Money Action Card Deepfix Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Fix the compact-screen money-action-card regression so instrument names and reasons remain readable while cash impact remains visible.

**Architecture:** Keep `MoneyManagementScreen` behavior unchanged and only make the action-card header responsive. Under 420dp, stack the cash impact below the instrument content; at wider widths, keep the existing row but bound the right-side cash impact width.

**Tech Stack:** Kotlin, Jetpack Compose, Bash source-contract tests, GitHub Actions.

**Spec:** Conversation `/deepfix` request and existing PR #56 (`Fix compact mobile money action cards`).

## Global Constraints

- Do not change financial calculations, action semantics, budget logic, sale-proceeds behavior, or API contracts.
- Preserve all existing money-management text and actions.
- New Android app code must ship under a new version; do not overwrite release v2.5.28 with different app code.

## Review Focus

- Width below 420dp: instrument name/reason must not compete horizontally with cash impact.
- Width at/above 420dp: keep compact row presentation.
- Long cash-impact strings: bound their width on wide layouts.
- Existing action buttons and explanatory text remain unchanged.
- Existing money-management contract remains green.

---

### Task 1: Lock the responsive layout contract

**Files:**
- Modify: `android/tests/test-money-management-ui.sh`

**Interfaces:**
- Consumes: `MoneyManagementScreen` source in `MainActivity.kt`.
- Produces: failing source contract on the current single-row implementation.

- [ ] **Step 1: Write the failing test**
  Add checks for `BoxWithConstraints`, `maxWidth < 420.dp`, compact `Column`, and bounded wide-layout cash-impact width.
- [ ] **Step 2: Run test to verify it fails**
  Run Android Contract Tests; expected failure on the compact-layout assertion.
- [ ] **Step 3: Commit the RED contract**

### Task 2: Implement the minimal responsive fix

**Files:**
- Modify: `android/app/src/main/java/de/tobias/investmentradar/MainActivity.kt`

**Interfaces:**
- Consumes: existing `DepotActionCenterItem` fields (`instrumentName`, `reason`, `cashImpactText`).
- Produces: responsive action-card header with unchanged action semantics.

- [ ] **Step 1: Replace only the action-card header row with `BoxWithConstraints`.**
- [ ] **Step 2: Stack content below 420dp and constrain `cashImpactText` to 180dp on wider layouts.**
- [ ] **Step 3: Run contract, JVM, build and instrumented UI tests.**

### Task 3: Ship as a new Android version

**Files:**
- Modify: `android/app/build.gradle.kts`
- Modify: `README.md`
- Modify: `android/README.md`
- Modify: `START_HIER.md`
- Modify: `SETUP.md`

**Interfaces:**
- Produces: Android 2.5.29 / versionCode 99 with docs synchronized to Gradle.

- [ ] **Step 1: Bump version from 2.5.28/98 to 2.5.29/99.**
- [ ] **Step 2: Synchronize current-version docs.**
- [ ] **Step 3: Re-run release contracts and full Android gates before merge.**
