# Trade Republic Screenshot Import Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a production-ready Android flow that locally reads one or more Trade Republic depot screenshots, reconciles them against Investment Radar, lets the user correct every uncertain change, and applies only explicitly confirmed portfolio updates without inventing transactions, cash or budget movements.

**Architecture:** Keep OCR, Trade-Republic parsing, asset matching, reconciliation, persistence and Compose UI as separate units. Use the bundled on-device ML Kit Latin text recognizer, pure Kotlin parser/matcher/reconciliation layers, and one atomic `PortfolioStore` import commit with rollback. Existing market-data and advisor logic continue to use `PortfolioPosition.isActiveHolding()`; `SOLD_MARKED` makes a position inactive while preserving its history and allowing its current market price to be displayed separately.

**Tech Stack:** Kotlin 2.3.21, Android API 23–36, Jetpack Compose, SharedPreferences, Kotlin coroutines/StateFlow, bundled ML Kit Text Recognition `com.google.mlkit:text-recognition:16.0.1`, JUnit 4, AndroidX instrumented Compose tests, GitHub Actions.

**Spec:** `docs/superpowers/specs/2026-10-15-trade-republic-screenshot-import-design.md`

## Global Constraints

- Scope is exclusively `Investment-Radar`; do not use KPI Logistik data, components or logic.
- First release supports Trade Republic screenshots only.
- OCR and screenshot processing stay on the Android device; no screenshot or OCR-text backend endpoint is added.
- A screenshot is a depot snapshot, never proof of a purchase, sale, cash movement or budget movement.
- Missing/uncertain values stay `null`; never replace missing financial data with `0`.
- Purchases and sales remain unchanged during snapshot updates.
- `SOLD_MARKED` creates no sale transaction, sale price, proceeds, cash credit or budget credit.
- A missing position is never automatically sold or deleted; the user must choose Behalten / Als verkauft markieren / Löschen.
- Deletion removes position-local history/snapshot data but must not rewrite the global budget journal.
- Current screenshot value without shares is labeled as the last confirmed Trade Republic value with timestamp, not as a live depot value.
- Existing backend contract remains `2.1.0` / API schema `2026-09-14.1`; this feature requires no backend contract change.
- Productive app change ships as Android `2.5.42`, `versionCode = 112`; existing `v2.5.41` remains immutable.
- The feature is not 100% complete until merged to `main`, all release gates are green, `v2.5.42` is published, and the production update is verified.

## Review Focus

- Incomplete screenshot set: active positions absent from the selected images remain unchanged until the user explicitly chooses SOLD_MARKED or delete.
- Overlapping screenshots: exact duplicate observations merge once; conflicting values for the same asset block automatic apply.
- Previously sold asset reappears: it remains SOLD_MARKED until the user explicitly confirms reactivation.
- Legacy portfolio data: records without lifecycle/timestamp keys load as `ACTIVE` without losing purchases, sales, snapshot data or shares.
- Failure during persistence: no partial import survives; portfolio is restored and budget entries/reservations are byte-for-byte unchanged.

---

### Task 1: Add explicit portfolio lifecycle and snapshot timestamp

**Files:**
- Modify: `android/app/src/main/java/de/tobias/investmentradar/PortfolioPosition.kt`
- Modify: `android/app/src/main/java/de/tobias/investmentradar/PortfolioStore.kt`
- Modify: `android/app/src/test/java/de/tobias/investmentradar/PortfolioSnapshotValueTest.kt`
- Modify: `android/app/src/test/java/de/tobias/investmentradar/PortfolioMetricsTest.kt`
- Create: `android/app/src/androidTest/java/de/tobias/investmentradar/PortfolioStoreMigrationInstrumentedTest.kt`

**Interfaces:**
- Produces: `enum class PortfolioLifecycleStatus { ACTIVE, SOLD_MARKED }`.
- Produces: `PortfolioPosition.lifecycleStatus: PortfolioLifecycleStatus = ACTIVE` and `snapshotCapturedAt: String? = null`.
- Produces: lifecycle-aware `isActiveHolding()`, `currentValue(...)`, `unrealizedProfitLoss(...)` and store serialization.

- [ ] **Step 1: Write failing lifecycle tests.**
  Assert that `SOLD_MARKED` returns `false` from `isActiveHolding()`, contributes no current value/unrealized P&L, and is excluded from `PortfolioMetrics.heldPositionCount` even when shares/snapshot values still exist.
- [ ] **Step 2: Write failing migration/persistence test.**
  Persist a legacy record without lifecycle/timestamp, assert it reads as `ACTIVE`; then persist `SOLD_MARKED` plus `snapshotCapturedAt = "2026-10-15T10:00:00Z"` and assert exact round-trip while purchases/sales stay identical.
- [ ] **Step 3: Run focused tests and verify RED.**
  Run `:app:testDebugUnitTest` plus `PortfolioStoreMigrationInstrumentedTest`; expected failure because lifecycle/timestamp fields and keys do not exist.
- [ ] **Step 4: Implement lifecycle and timestamp with backward-compatible store keys.**
  Add `position.<id>.lifecycleStatus` and `position.<id>.snapshotCapturedAt`; unknown/absent lifecycle values must safely fall back to `ACTIVE`.
- [ ] **Step 5: Run focused tests and verify GREEN.**
- [ ] **Step 6: Commit.**
  `git commit -am "feat: add portfolio lifecycle status"`

### Task 2: Build atomic portfolio import application and rollback

**Files:**
- Create: `android/app/src/main/java/de/tobias/investmentradar/PortfolioImportApplier.kt`
- Modify: `android/app/src/main/java/de/tobias/investmentradar/PortfolioStore.kt`
- Create: `android/app/src/test/java/de/tobias/investmentradar/PortfolioImportApplierTest.kt`
- Create: `android/app/src/androidTest/java/de/tobias/investmentradar/PortfolioImportStoreInstrumentedTest.kt`

**Interfaces:**
- Produces: `enum class MissingPositionDecision { KEEP, MARK_SOLD, DELETE }`.
- Produces: immutable `PortfolioImportChange` commands for UPDATE_SNAPSHOT, ADD_POSITION, REACTIVATE, MARK_SOLD and DELETE.
- Produces: `PortfolioImportApplier.preview(existing, changes, capturedAt): PortfolioImportResult` with no Android I/O.
- Produces: `PortfolioStore.commitImport(context, result, metadata): Boolean`, using one synchronous `SharedPreferences.Editor.commit()` for the complete portfolio preference state.

- [ ] **Step 1: Write failing pure applier tests.**
  Cover snapshot update preserving `purchases`/`sales`, new position without fabricated transactions, KEEP no-op, MARK_SOLD preserving history, DELETE removing only the position, and explicit SOLD_MARKED reactivation.
- [ ] **Step 2: Add Review Focus tests for budget isolation.**
  Capture budget entries/reservations before MARK_SOLD and DELETE; after portfolio commit assert the budget preference contents are unchanged.
- [ ] **Step 3: Write failing instrumented atomicity test.**
  Use a testable store writer/failure hook so a simulated commit failure returns false and leaves the previously serialized portfolio state intact.
- [ ] **Step 4: Run tests and verify RED.**
- [ ] **Step 5: Implement pure applier and atomic store commit.**
  Serialize a complete target portfolio state first; write ids, all position keys, lifecycle/timestamp and compact import metadata in one synchronous commit. Do not call the existing per-position `save(...).apply()` loop.
- [ ] **Step 6: Run tests and verify GREEN.**
- [ ] **Step 7: Commit.**
  `git commit -m "feat: apply depot imports atomically"`

### Task 3: Parse Trade Republic OCR output deterministically

**Files:**
- Create: `android/app/src/main/java/de/tobias/investmentradar/TradeRepublicScreenshotParser.kt`
- Create: `android/app/src/test/java/de/tobias/investmentradar/TradeRepublicScreenshotParserTest.kt`

**Interfaces:**
- Consumes: OCR blocks as a platform-neutral `RecognizedTextBlock(text, left, top, right, bottom)` list.
- Produces: `TradeRepublicScreenshotObservation(name, valueEur, profitLossEur, profitLossPct, shares, derivedCostBasisEur, sourceIndex)`.
- Produces: `TradeRepublicScreenshotParser.parse(blocks, sourceIndex): TradeRepublicParseResult`.

- [ ] **Step 1: Write failing number-normalization tests.**
  Pin `1.217,07 € -> 1217.07`, `+34,40 € -> 34.40`, `-3,71 € -> -3.71`, `+4,53 % -> 4.53`, `-2,17 % -> -2.17`, and missing/garbled amounts -> `null`.
- [ ] **Step 2: Write failing structure tests.**
  Cover one position, multiple positions, positive/negative G/V, clipped/incomplete row, non-Trade-Republic content, and value + absolute G/V deriving `costBasis = value - G/V` only when both belong unambiguously to the same row.
- [ ] **Step 3: Run parser tests and verify RED.**
- [ ] **Step 4: Implement normalization and geometry-aware row grouping.**
  Keep the parser independent from Android/ML Kit classes so all financial interpretation is JVM-testable.
- [ ] **Step 5: Run parser tests and verify GREEN.**
- [ ] **Step 6: Commit.**
  `git commit -m "feat: parse Trade Republic depot screenshots"`

### Task 4: Add conservative asset matching and multi-screenshot reconciliation

**Files:**
- Create: `android/app/src/main/java/de/tobias/investmentradar/PortfolioAssetMatcher.kt`
- Modify: `android/app/src/main/java/de/tobias/investmentradar/ScreenshotDepotReconciliation.kt`
- Create: `android/app/src/test/java/de/tobias/investmentradar/PortfolioAssetMatcherTest.kt`
- Modify: `android/app/src/test/java/de/tobias/investmentradar/ScreenshotDepotReconciliationTest.kt`

**Interfaces:**
- Consumes: parsed observations plus `InvestmentItem`/`CustomInvestment` candidates and current positions.
- Produces: `AssetMatch.Exact`, `AssetMatch.Review(candidates)`, or `AssetMatch.Unknown`.
- Produces review statuses: `READY`, `NEEDS_REVIEW`, `NEW_POSITION`, `NOT_FOUND`, `CONFLICT`, `INVALID`, `SOLD_REAPPEARED`.
- Produces one reconciliation model across all selected screenshots; it never persists data.

- [ ] **Step 1: Write failing matcher tests.**
  Cover exact normalized `tradeRepublicName`, ticker/name alias, one conservative fuzzy candidate, ambiguous similar candidates -> review, and unknown asset -> no automatic match.
- [ ] **Step 2: Replace the preliminary duplicate logic with multi-image tests.**
  Exact duplicate values for one item merge once; differing values for one item become `CONFLICT`; a position absent from all screenshots becomes `NOT_FOUND`, never automatically sold/deleted.
- [ ] **Step 3: Add SOLD_REAPPEARED test.**
  A matched `SOLD_MARKED` item must require explicit reactivation.
- [ ] **Step 4: Run tests and verify RED.**
- [ ] **Step 5: Implement matcher/reconciliation and keep user-confirmation state explicit.**
- [ ] **Step 6: Run tests and verify GREEN.**
- [ ] **Step 7: Commit.**
  `git commit -m "feat: reconcile screenshot holdings safely"`

### Task 5: Integrate bundled on-device OCR without screenshot upload

**Files:**
- Modify: `android/app/build.gradle.kts`
- Create: `android/app/src/main/java/de/tobias/investmentradar/TradeRepublicScreenshotImporter.kt`
- Create: `android/app/src/test/java/de/tobias/investmentradar/TradeRepublicScreenshotImporterTest.kt`
- Create: `android/tests/test-screenshot-import-privacy.sh`
- Modify: `.github/workflows/android-contract-tests.yml`

**Interfaces:**
- Produces: `interface LocalScreenshotTextRecognizer { suspend fun recognize(uri: Uri): List<RecognizedTextBlock> }`.
- Produces: `MlKitLocalScreenshotTextRecognizer(context)` backed by `TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)`.
- Produces: `TradeRepublicScreenshotImporter.import(uris, recognizer, onProgress): ScreenshotImportBatch` where one unreadable image becomes a per-image failure and does not cancel successful images.

- [ ] **Step 1: Add failing importer unit tests with a fake recognizer.**
  Verify ordered progress `1..N`, partial-image failure, successful images continue, and no persistence occurs in importer.
- [ ] **Step 2: Add failing source privacy contract.**
  Require bundled `implementation("com.google.mlkit:text-recognition:16.0.1")`, forbid `play-services-mlkit-text-recognition`, and assert importer contains no `ApiClient`/HTTP upload call.
- [ ] **Step 3: Run tests/contracts and verify RED.**
- [ ] **Step 4: Add bundled ML Kit dependency and production recognizer adapter.**
  Use `InputImage.fromFilePath(context, uri)` and map ML Kit text blocks/lines to platform-neutral blocks. Do not persist selected URIs or bitmap copies.
- [ ] **Step 5: Register `android/tests/test-screenshot-import-privacy.sh` in Android Contract Tests.**
- [ ] **Step 6: Run tests/contracts and verify GREEN.**
- [ ] **Step 7: Commit.**
  `git commit -m "feat: add local screenshot OCR"`

### Task 6: Add import state machine and final apply orchestration

**Files:**
- Create: `android/app/src/main/java/de/tobias/investmentradar/DepotScreenshotImportViewModel.kt`
- Modify: `android/app/src/main/java/de/tobias/investmentradar/MainViewModel.kt`
- Create: `android/app/src/test/java/de/tobias/investmentradar/DepotScreenshotImportViewModelTest.kt`

**Interfaces:**
- Produces sealed UI states `Selecting`, `Recognizing`, `Reviewing`, `Applying`, `Completed`, `Failed`.
- Produces actions to correct value/match, confirm new position, choose KEEP/MARK_SOLD/DELETE, confirm reactivation, retry failed image, cancel, and apply.
- `MainViewModel.reloadPortfolioAfterImport(previousHoldingIds)` refreshes positions/holding topics, budget-derived view state and advisor plan after a successful atomic commit only.

- [ ] **Step 1: Write failing state-machine tests.**
  Verify multiple URIs -> progress -> review, one image failure remains reviewable, unresolved conflict disables Apply, cancel writes nothing, and apply summary counts updates/new/sold/deleted/kept exactly.
- [ ] **Step 2: Add persistence-failure test.**
  A failed `commitImport` must produce `Failed`, preserve prior positions and leave budget state unchanged.
- [ ] **Step 3: Run tests and verify RED.**
- [ ] **Step 4: Implement coordinator ViewModel with dependency-injected recognizer/store adapter.**
- [ ] **Step 5: Wire successful completion back into `MainViewModel` without using buy/sale execution services.**
- [ ] **Step 6: Run tests and verify GREEN.**
- [ ] **Step 7: Commit.**
  `git commit -m "feat: orchestrate screenshot depot updates"`

### Task 7: Build the four-step Compose import/review experience

**Files:**
- Create: `android/app/src/main/java/de/tobias/investmentradar/DepotScreenshotImportScreen.kt`
- Modify: `android/app/src/main/java/de/tobias/investmentradar/PortfolioDashboard.kt`
- Modify: `android/app/src/main/java/de/tobias/investmentradar/MainActivity.kt`
- Modify: `android/app/src/main/java/de/tobias/investmentradar/AppNavigationState.kt`
- Modify: `android/app/src/test/java/de/tobias/investmentradar/AppNavigationStateTest.kt`
- Modify: `android/app/src/androidTest/java/de/tobias/investmentradar/MainActivitySmokeTest.kt`
- Create: `android/app/src/androidTest/java/de/tobias/investmentradar/DepotScreenshotImportUiTest.kt`
- Create: `android/tests/test-screenshot-import-ui.sh`
- Modify: `.github/workflows/android-contract-tests.yml`

**Interfaces:**
- `PortfolioDashboard` adds `onUpdateDepot: () -> Unit` and button text `Depot aktualisieren`.
- Import screen uses `ActivityResultContracts.OpenMultipleDocuments()` with MIME `image/*`; no storage permission is added.
- Navigation gets `AppChildScreen.DEPOT_SCREENSHOT_IMPORT`; Back closes dialogs/import flow before leaving the Portfolio tab.

- [ ] **Step 1: Add failing navigation/source contract tests.**
  Assert depot button, multiple-image picker, no new storage permission, `DEPOT_SCREENSHOT_IMPORT`, and final text `Änderungen übernehmen` exist.
- [ ] **Step 2: Add failing Compose UI tests for the four steps.**
  Render deterministic fake states and assert selection count, `3 von 5 Screenshots analysiert`, status rows, correction controls, new-position assignment, missing-position Behalten/Verkauft/Löschen actions, delete confirmation, and final summary.
- [ ] **Step 3: Add Apply-gating test.**
  `Änderungen übernehmen` is disabled while any row is unresolved/CONFLICT/INVALID and enabled after all decisions are explicit.
- [ ] **Step 4: Implement the screen with current Material theme and responsive `LazyColumn`.**
- [ ] **Step 5: Wire dashboard entry and back navigation.**
- [ ] **Step 6: Register source contract and run unit/instrumented UI tests on phone/tablet profiles.**
- [ ] **Step 7: Commit.**
  `git commit -m "feat: add depot screenshot review UI"`

### Task 8: Separate sold positions and show current market price safely

**Files:**
- Modify: `android/app/src/main/java/de/tobias/investmentradar/PortfolioDashboard.kt`
- Modify: `android/app/src/main/java/de/tobias/investmentradar/PortfolioAnalysis.kt`
- Modify: `android/app/src/main/java/de/tobias/investmentradar/PortfolioMetrics.kt`
- Modify: `android/app/src/test/java/de/tobias/investmentradar/PortfolioAnalysisTest.kt`
- Modify: `android/app/src/test/java/de/tobias/investmentradar/PortfolioMetricsTest.kt`
- Modify: `android/app/src/test/java/de/tobias/investmentradar/PortfolioSnapshotValueTest.kt`
- Modify: `android/app/src/androidTest/java/de/tobias/investmentradar/DepotScreenshotImportUiTest.kt`

**Interfaces:**
- Active portfolio metrics operate only on `ACTIVE` holdings.
- Portfolio UI renders `SOLD_MARKED` positions in `Verkaufte Positionen` and reads `InvestmentItem.priceEur` directly as `Aktueller Marktpreis`.
- Snapshot-only ACTIVE positions without shares show `Letzter bestätigter Trade-Republic-Wert` plus `snapshotCapturedAt`; they do not claim the snapshot is live.

- [ ] **Step 1: Write failing regression tests.**
  Assert SOLD_MARKED with remaining historical shares does not enter value/concentration/advisor inputs, while its `InvestmentItem.priceEur` can still be rendered as market price.
- [ ] **Step 2: Add snapshot-label UI test.**
  Without `trackedShares`, render timestamped last confirmed TR value and separate current single-security market price; never label snapshot as `Aktueller Wert`.
- [ ] **Step 3: Run tests and verify RED.**
- [ ] **Step 4: Implement sold section and snapshot-value wording without changing recommendation calculations for active holdings.**
- [ ] **Step 5: Run tests and verify GREEN.**
- [ ] **Step 6: Commit.**
  `git commit -m "feat: show sold holdings separately"`

### Task 9: Prove backup compatibility and full financial regressions

**Files:**
- Modify: `android/app/src/androidTest/java/de/tobias/investmentradar/UserDataBackupInstrumentedTest.kt`
- Modify: `android/tests/test-release-hardening.sh`
- Modify: `android/tests/test-portfolio-dashboard-ui.sh`

**Interfaces:**
- Existing `investment_radar_portfolio` backup preference remains the source of truth; no screenshot image/OCR raw-text preference is introduced.
- Backup/restore must round-trip lifecycle, timestamp and import metadata automatically because they live in the existing portfolio preference file.

- [ ] **Step 1: Extend backup test.**
  Export a portfolio containing SOLD_MARKED/status/timestamp/import metadata, clear preferences, restore, and assert exact state returns while no screenshot URI/OCR raw text exists in exported JSON.
- [ ] **Step 2: Add financial regression assertions.**
  Existing manual buys, manual sales, savings plans, advisor, concentration, exit strategies, update flow and the rule that private sale proceeds do not increase app cash/budget remain green.
- [ ] **Step 3: Run Android Contract Tests, complete JVM suite and instrumented UI suite.**
  Expected: all green on the branch.
- [ ] **Step 4: Commit any test-only hardening.**
  `git commit -m "test: harden screenshot depot import regressions"`

### Task 10: Version, documentation, release gates and production publication

**Files:**
- Modify: `android/app/build.gradle.kts`
- Modify: `android/tests/test-version-contract.sh`
- Modify: `README.md`
- Modify: `android/README.md`
- Modify: `START_HIER.md`
- Modify: `SETUP.md`
- Modify: `.github/workflows/android-build.yml`

**Interfaces:**
- Produces Android `2.5.42` / `versionCode 112`.
- Production publication remains the existing main-only signed APK workflow and GitHub release `v2.5.42`.

- [ ] **Step 1: Bump Gradle and version contract to `2.5.42` / `112`.**
- [ ] **Step 2: Synchronize README/Android README/START_HIER/SETUP.**
  Document local Trade Republic multi-screenshot import, explicit review, SOLD_MARKED semantics, no automatic transactions/cash changes, and bundled local OCR.
- [ ] **Step 3: Update release notes in `android-build.yml` for the screenshot-import release; do not change backend expectations.**
- [ ] **Step 4: Run `scripts/test-current-docs.sh` and every Android source contract.**
- [ ] **Step 5: Run complete Android JVM tests.**
- [ ] **Step 6: Run instrumented UI tests on configured phone and tablet profiles.**
- [ ] **Step 7: Build signed release APK and verify APK signature/live backend gate.**
- [ ] **Step 8: Review branch diff for accidental KPI/backend/screenshot-data upload changes and confirm no P0/P1 issue remains.**
- [ ] **Step 9: Merge the reviewed branch to `main` only after all pre-merge gates are green.**
- [ ] **Step 10: Wait for main release gates: Android Contract Tests, JVM Tests, Build Android APK, Android Instrumented UI Tests.**
- [ ] **Step 11: Verify GitHub release `v2.5.42` contains `investment-radar.apk` and that the in-app update check reports the new version.**
- [ ] **Step 12: Perform production smoke verification of Portfolio -> Depot aktualisieren -> multiple screenshot selection -> review screen without committing test data.**
- [ ] **Step 13: Only after Steps 9–12 succeed report production as 100% complete.**
