# Trade Republic Screenshot Import Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a production-ready Android flow that locally reads one or more Trade Republic depot screenshots, reconciles them against Investment Radar, lets the user correct every uncertain change, and applies only explicitly confirmed portfolio updates without inventing transactions, cash or budget movements.

**Architecture:** Keep OCR, Trade-Republic parsing, asset matching, reconciliation, persistence and Compose UI as separate units. Use the bundled on-device ML Kit Latin text recognizer, pure Kotlin parser/matcher/reconciliation layers, and a transactional local persistence coordinator with a single portfolio commit plus rollback for any companion custom-asset write. Existing market-data and advisor logic continue to use `PortfolioPosition.isActiveHolding()`; `SOLD_MARKED` makes a position inactive while preserving its history and allowing its current market price to be displayed separately.

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
- `NOT_FOUND` is generated only for active Trade-Republic-eligible stock/ETF holdings in the import scope. Festzins and unrelated custom/non-broker values are never treated as missing merely because they do not appear on Trade Republic screenshots.
- Deletion removes position-local history/snapshot data but must not rewrite the global budget journal.
- Current screenshot value without shares is labeled as the last confirmed Trade Republic value with timestamp, not as a live depot value.
- Existing backend contract remains `2.1.0` / API schema `2026-09-14.1`; this feature requires no backend contract change.
- Productive app change targets Android `2.5.42`, `versionCode = 112`; existing `v2.5.41` remains immutable. Immediately before release, re-check `main`; if a newer production version exists, increment from that version instead of reusing an existing tag/code.
- The feature is not 100% complete until merged to `main`, all release gates are green, the new release is published, and the production update is verified.

## Review Focus

- Incomplete screenshot set: eligible active positions absent from the selected images remain unchanged until the user explicitly chooses SOLD_MARKED or delete.
- Overlapping screenshots: exact duplicate observations merge once; conflicting values for the same asset block automatic apply.
- Previously sold asset reappears: it remains SOLD_MARKED until the user explicitly confirms reactivation.
- Legacy portfolio data: records without lifecycle/timestamp keys load as `ACTIVE` without losing purchases, sales, snapshot data or shares.
- Persistence failure: neither partial portfolio nor partial custom-asset changes survive; budget entries/reservations remain byte-for-byte unchanged.
- Non-TR/Festzins holdings: never become `NOT_FOUND` because the screenshot batch does not contain them.

---

### Task 1: Add explicit portfolio lifecycle and snapshot timestamp

**Files:**
- Modify: `android/app/src/main/java/de/tobias/investmentradar/PortfolioPosition.kt`
- Modify: `android/app/src/main/java/de/tobias/investmentradar/PortfolioStore.kt`
- Modify: `android/app/src/main/java/de/tobias/investmentradar/PortfolioMetrics.kt`
- Modify: `android/app/src/test/java/de/tobias/investmentradar/PortfolioSnapshotValueTest.kt`
- Modify: `android/app/src/test/java/de/tobias/investmentradar/PortfolioMetricsTest.kt`
- Create: `android/app/src/androidTest/java/de/tobias/investmentradar/PortfolioStoreMigrationInstrumentedTest.kt`

**Interfaces:**
- Produces: `enum class PortfolioLifecycleStatus { ACTIVE, SOLD_MARKED }`.
- Produces: `PortfolioPosition.lifecycleStatus: PortfolioLifecycleStatus = ACTIVE` and `snapshotCapturedAt: String? = null`.
- Produces: lifecycle-aware `isActiveHolding()`, `currentValue(...)`, `unrealizedProfitLoss(...)` and store serialization.

- [ ] **Step 1: Write failing lifecycle tests.**
  Assert that `SOLD_MARKED` returns `false` from `isActiveHolding()`, contributes no current value/unrealized P&L, and is excluded from `PortfolioMetrics.heldPositionCount` even when shares/snapshot values remain.
- [ ] **Step 2: Add the fixed-income lifecycle regression.**
  A custom Festzins position explicitly set to `SOLD_MARKED` must not remain active merely because `fixedPrincipalEur` exists.
- [ ] **Step 3: Write failing migration/persistence test.**
  Persist a legacy record without lifecycle/timestamp, assert it reads as `ACTIVE`; then persist `SOLD_MARKED` plus `snapshotCapturedAt = "2026-10-15T10:00:00Z"` and assert exact round-trip while purchases/sales stay identical.
- [ ] **Step 4: Run focused tests and verify RED.**
  Run `gradle --no-daemon :app:testDebugUnitTest --tests '*PortfolioSnapshotValueTest' --tests '*PortfolioMetricsTest'` plus the migration instrumented test with the repository's standard signing/build properties. Expected: FAIL because lifecycle/timestamp do not exist.
- [ ] **Step 5: Implement lifecycle/timestamp and backward-compatible store keys.**
  Add `position.<id>.lifecycleStatus` and `position.<id>.snapshotCapturedAt`; unknown/absent lifecycle values fall back to `ACTIVE`. In `PortfolioMetrics`, lifecycle overrides the fixed-income principal shortcut.
- [ ] **Step 6: Re-run focused tests.**
  Expected: PASS.
- [ ] **Step 7: Commit.**
  `git commit -am "feat: add portfolio lifecycle status"`

### Task 2: Build import commands, transactional persistence and budget isolation

**Files:**
- Create: `android/app/src/main/java/de/tobias/investmentradar/PortfolioImportApplier.kt`
- Create: `android/app/src/main/java/de/tobias/investmentradar/DepotImportPersistence.kt`
- Modify: `android/app/src/main/java/de/tobias/investmentradar/PortfolioStore.kt`
- Modify: `android/app/src/main/java/de/tobias/investmentradar/CustomInvestmentStore.kt`
- Create: `android/app/src/test/java/de/tobias/investmentradar/PortfolioImportApplierTest.kt`
- Create: `android/app/src/androidTest/java/de/tobias/investmentradar/DepotImportPersistenceInstrumentedTest.kt`

**Interfaces:**
- Produces: `enum class MissingPositionDecision { KEEP, MARK_SOLD, DELETE }`.
- Produces immutable `PortfolioImportChange` commands for UPDATE_SNAPSHOT, ADD_POSITION, REACTIVATE, MARK_SOLD and DELETE.
- Produces optional `CustomAssetDraft` for an explicitly approved unknown stock/ETF.
- Produces `PortfolioImportApplier.preview(existingPositions, existingCustomAssets, changes, capturedAt): PortfolioImportResult` with no Android I/O.
- Produces `PortfolioStore.replaceAllForImport(context, positions, metadata): Boolean` using one synchronous portfolio-preference `commit()`.
- Produces `DepotImportPersistence.commit(context, result): Boolean`, which snapshots both portfolio and custom-asset preference files, writes any approved custom asset, commits the complete portfolio state, and restores both snapshots if either write fails.

- [ ] **Step 1: Write failing pure applier tests.**
  Cover snapshot update preserving `purchases`/`sales`, known new position without fabricated transactions, KEEP no-op, MARK_SOLD preserving history, DELETE removing only the position, explicit reactivation, and explicitly approved unknown asset producing a custom-asset draft plus position.
- [ ] **Step 2: Add budget-isolation assertions.**
  Capture raw `investment_radar_budget` preferences before MARK_SOLD and DELETE and assert they are byte-for-byte unchanged after import persistence.
- [ ] **Step 3: Write failing cross-preference rollback tests.**
  Simulate custom-asset write failure and portfolio commit failure separately; in both cases portfolio/custom preferences must exactly equal the pre-import snapshots.
- [ ] **Step 4: Run tests and verify RED.**
- [ ] **Step 5: Implement pure applier and transactional persistence.**
  Do not loop through existing `PortfolioStore.save(...).apply()`. Portfolio replacement is one synchronous commit; custom asset and portfolio files are coordinated with explicit before-images and rollback.
- [ ] **Step 6: Re-run tests.**
  Expected: PASS, including budget byte equality.
- [ ] **Step 7: Commit.**
  `git commit -m "feat: apply depot imports transactionally"`

### Task 3: Parse Trade Republic OCR output deterministically

**Files:**
- Create: `android/app/src/main/java/de/tobias/investmentradar/TradeRepublicScreenshotParser.kt`
- Create: `android/app/src/test/java/de/tobias/investmentradar/TradeRepublicScreenshotParserTest.kt`

**Interfaces:**
- Consumes OCR blocks as platform-neutral `RecognizedTextBlock(text: String, left: Int, top: Int, right: Int, bottom: Int)`.
- Produces `TradeRepublicScreenshotObservation(name, valueEur, profitLossEur, profitLossPct, shares, derivedCostBasisEur, sourceIndex)`.
- Produces `TradeRepublicScreenshotParser.parse(blocks, sourceIndex): TradeRepublicParseResult`.

- [ ] **Step 1: Write failing number-normalization tests.**
  Pin `1.217,07 € -> 1217.07`, `+34,40 € -> 34.40`, `-3,71 € -> -3.71`, `+4,53 % -> 4.53`, `-2,17 % -> -2.17`, and garbled/missing values -> `null`.
- [ ] **Step 2: Write failing structure tests.**
  Cover one position, several positions, positive/negative G/V, clipped row, non-Trade-Republic content, and `costBasis = value - G/V` only when both values are unambiguously in the same row.
- [ ] **Step 3: Run `gradle --no-daemon :app:testDebugUnitTest --tests '*TradeRepublicScreenshotParserTest'`.**
  Expected: FAIL because parser types do not exist.
- [ ] **Step 4: Implement signed-number normalization and geometry-aware row grouping.**
  Keep all financial interpretation independent from Android/ML Kit classes.
- [ ] **Step 5: Re-run parser tests.**
  Expected: PASS.
- [ ] **Step 6: Commit.**
  `git commit -m "feat: parse Trade Republic depot screenshots"`

### Task 4: Add conservative asset matching and scoped multi-screenshot reconciliation

**Files:**
- Create: `android/app/src/main/java/de/tobias/investmentradar/PortfolioAssetMatcher.kt`
- Modify: `android/app/src/main/java/de/tobias/investmentradar/ScreenshotDepotReconciliation.kt`
- Create: `android/app/src/test/java/de/tobias/investmentradar/PortfolioAssetMatcherTest.kt`
- Modify: `android/app/src/test/java/de/tobias/investmentradar/ScreenshotDepotReconciliationTest.kt`

**Interfaces:**
- Consumes parsed observations, `InvestmentItem`/eligible `CustomInvestment` candidates and current positions.
- Produces `AssetMatch.Exact`, `AssetMatch.Review(candidates)`, or `AssetMatch.Unknown`.
- Produces review statuses `READY`, `NEEDS_REVIEW`, `NEW_POSITION`, `NOT_FOUND`, `CONFLICT`, `INVALID`, `SOLD_REAPPEARED`.
- Produces one reconciliation model across all selected screenshots; it never persists data.
- Produces `isTradeRepublicImportEligible(item/custom): Boolean`; Festzins and custom assets with no Trade-Republic identity are outside missing-position detection.

- [ ] **Step 1: Write failing matcher tests.**
  Cover exact normalized `tradeRepublicName`, ticker/name alias, one conservative fuzzy candidate, ambiguous similar candidates -> review, and unknown asset -> no automatic match.
- [ ] **Step 2: Replace preliminary duplicate logic with multi-image tests.**
  Equal duplicate observations merge once; differing values for one asset become `CONFLICT`; eligible active asset absent from all screenshots becomes `NOT_FOUND`, never automatically sold/deleted.
- [ ] **Step 3: Add scope tests.**
  An active Festzins and an unrelated custom/non-broker position remain absent from reconciliation's `NOT_FOUND` rows.
- [ ] **Step 4: Add SOLD_REAPPEARED test.**
  Matched `SOLD_MARKED` item requires explicit reactivation.
- [ ] **Step 5: Run matcher/reconciliation tests and verify RED.**
- [ ] **Step 6: Implement matching, eligibility filtering and reconciliation.**
- [ ] **Step 7: Re-run tests and verify GREEN.**
- [ ] **Step 8: Commit.**
  `git commit -m "feat: reconcile screenshot holdings safely"`

### Task 5: Integrate bundled on-device OCR without screenshot upload

**Files:**
- Modify: `android/app/build.gradle.kts`
- Create: `android/app/src/main/java/de/tobias/investmentradar/TradeRepublicScreenshotImporter.kt`
- Create: `android/app/src/test/java/de/tobias/investmentradar/TradeRepublicScreenshotImporterTest.kt`
- Create: `android/tests/test-screenshot-import-privacy.sh`
- Modify: `.github/workflows/android-contract-tests.yml`

**Interfaces:**
- Produces platform-neutral `data class ScreenshotImageRef(val sourceId: String, val uriString: String)`.
- Produces `interface LocalScreenshotTextRecognizer { suspend fun recognize(image: ScreenshotImageRef): List<RecognizedTextBlock> }`.
- Produces `MlKitLocalScreenshotTextRecognizer(context)` backed by `TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)`; only this adapter converts `uriString` to `Uri`.
- Produces `TradeRepublicScreenshotImporter.import(images, recognizer, onProgress): ScreenshotImportBatch`, where one unreadable image is a per-image failure and does not cancel successful images.

- [ ] **Step 1: Add failing importer unit tests with fake recognizer.**
  Verify ordered progress `1..N`, partial-image failure, successful images continue, and importer itself performs no persistence.
- [ ] **Step 2: Add failing source privacy contract.**
  Require bundled `implementation("com.google.mlkit:text-recognition:16.0.1")`, forbid `play-services-mlkit-text-recognition`, and assert importer contains no `ApiClient`, `HttpURLConnection`, upload or backend screenshot route.
- [ ] **Step 3: Run unit/source contracts and verify RED.**
- [ ] **Step 4: Add bundled ML Kit dependency and production adapter.**
  Use `InputImage.fromFilePath(context, Uri.parse(image.uriString))`; map recognized lines/blocks to platform-neutral geometry. Do not persist selected URIs, bitmaps or OCR raw text.
- [ ] **Step 5: Register `android/tests/test-screenshot-import-privacy.sh` in Android Contract Tests.**
- [ ] **Step 6: Re-run tests/contracts.**
  Expected: PASS.
- [ ] **Step 7: Commit.**
  `git commit -m "feat: add local screenshot OCR"`

### Task 6: Add import state machine and safe post-import refresh

**Files:**
- Create: `android/app/src/main/java/de/tobias/investmentradar/DepotScreenshotImportViewModel.kt`
- Modify: `android/app/src/main/java/de/tobias/investmentradar/MainViewModel.kt`
- Modify: `android/app/src/main/java/de/tobias/investmentradar/InvestmentBudgetStore.kt`
- Create: `android/app/src/test/java/de/tobias/investmentradar/DepotScreenshotImportViewModelTest.kt`
- Modify: `android/app/src/test/java/de/tobias/investmentradar/InvestmentBudgetMoneyFlowTest.kt`

**Interfaces:**
- Produces sealed UI states `Selecting`, `Recognizing`, `Reviewing`, `Applying`, `Completed`, `Failed`.
- Produces actions to correct value/match, confirm known/custom new position, choose KEEP/MARK_SOLD/DELETE, confirm reactivation, retry failed image, cancel, and apply.
- Produces `InvestmentBudgetStore.viewStateReadOnly(context, activeInvestedEur, today)` that computes the UI view from already stored budget data without calling `reconcileCurrentMonthPortfolio` or writing preferences.
- Produces `MainViewModel.reloadPortfolioAfterImport(previousActiveHoldingIds)` that refreshes positions, active holding IDs/topics, custom assets, read-only budget view and advisor plan after successful persistence only.

- [ ] **Step 1: Write failing state-machine tests.**
  Verify multiple images -> progress -> review, one image failure remains reviewable, unresolved conflict disables Apply, cancel writes nothing, and summary counts updates/new/sold/deleted/kept exactly.
- [ ] **Step 2: Add persistence-failure test.**
  Failed persistence produces `Failed` and preserves prior portfolio/custom data.
- [ ] **Step 3: Add read-only budget refresh test.**
  Snapshot budget entries/reservations, call post-import refresh after MARK_SOLD/DELETE, and assert serialized budget entries/reservations are unchanged.
- [ ] **Step 4: Add active-topic semantics test.**
  `_holdingIds` used for portfolio alerts/topics contains only `isActiveHolding()` positions after import; MARK_SOLD/delete unsubscribes and new/reactivated positions subscribe when Firebase is configured.
- [ ] **Step 5: Run focused tests and verify RED.**
- [ ] **Step 6: Implement coordinator, read-only budget view and post-import reload.**
  Do not call buy/sale execution services or mutating budget reconciliation from this path.
- [ ] **Step 7: Re-run tests and verify GREEN.**
- [ ] **Step 8: Commit.**
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
- `PortfolioDashboard` adds `onUpdateDepot: () -> Unit` and button `Depot aktualisieren`.
- Import screen uses `ActivityResultContracts.OpenMultipleDocuments()` with MIME `image/*`; no storage permission is added.
- Navigation gets `AppChildScreen.DEPOT_SCREENSHOT_IMPORT`; Back closes dialogs/import flow before leaving Portfolio.

- [ ] **Step 1: Add failing navigation/source contract tests.**
  Assert depot button, multiple-image picker, no storage permission, child screen and `Änderungen übernehmen` exist.
- [ ] **Step 2: Add failing Compose UI tests for all four steps.**
  With deterministic fake states assert screenshot count, `3 von 5 Screenshots analysiert`, review-status rows, correction controls, known/new/custom asset assignment, missing-position Behalten/Verkauft/Löschen, delete confirmation and final summary.
- [ ] **Step 3: Add Apply-gating test.**
  `Änderungen übernehmen` is disabled while any row is unresolved/CONFLICT/INVALID and enabled after every decision is explicit.
- [ ] **Step 4: Implement responsive screen with the existing Material theme and `LazyColumn`.**
- [ ] **Step 5: Wire dashboard entry, picker and Back navigation.**
- [ ] **Step 6: Register source contract and run unit/instrumented tests on configured phone/tablet profiles.**
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
- UI renders `SOLD_MARKED` positions under `Verkaufte Positionen` and reads `InvestmentItem.priceEur` directly as `Aktueller Marktpreis`.
- Snapshot-only ACTIVE positions without shares show `Letzter bestätigter Trade-Republic-Wert` plus `snapshotCapturedAt`; they do not claim the snapshot is live.

- [ ] **Step 1: Write failing regression tests.**
  Assert SOLD_MARKED with historical shares does not enter current value/concentration/advisor inputs; fixed-income lifecycle override remains respected.
- [ ] **Step 2: Add sold-price UI test.**
  A sold row may display `InvestmentItem.priceEur` as market price but never as sale price; missing quote renders `Kurs nicht verfügbar`.
- [ ] **Step 3: Add snapshot-label UI test.**
  Without `trackedShares`, render timestamped last confirmed TR value and separate current single-security market price; never label the snapshot as current depot value.
- [ ] **Step 4: Run focused tests and verify RED.**
- [ ] **Step 5: Implement sold section and snapshot wording without changing calculations for active holdings.**
- [ ] **Step 6: Re-run tests and verify GREEN.**
- [ ] **Step 7: Commit.**
  `git commit -m "feat: show sold holdings separately"`

### Task 9: Prove backup compatibility and full financial regressions

**Files:**
- Modify: `android/app/src/androidTest/java/de/tobias/investmentradar/UserDataBackupInstrumentedTest.kt`
- Modify: `android/tests/test-release-hardening.sh`
- Modify: `android/tests/test-portfolio-dashboard-ui.sh`

**Interfaces:**
- Existing portfolio/custom-asset preference files remain in `UserDataBackupManager.preferenceFiles`.
- No screenshot image, URI or OCR raw-text preference is introduced.

- [ ] **Step 1: Extend backup/restore test.**
  Export data containing SOLD_MARKED/timestamp/import metadata plus a custom asset created via import, clear preferences, restore, and assert exact state returns.
- [ ] **Step 2: Add privacy assertion to backup.**
  Exported JSON contains no screenshot URI, bitmap path or OCR raw text.
- [ ] **Step 3: Run financial regression suite.**
  Existing manual buys/sales, savings plans, advisor, concentration, exit strategies, backup/restore, current market prices and app-update flow remain green; private sale proceeds still do not increase app cash/budget.
- [ ] **Step 4: Run all Android source contracts, JVM tests and instrumented UI tests.**
  Expected: all green on the branch.
- [ ] **Step 5: Commit any test-only hardening.**
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
- Target when `main` is still 2.5.41: Android `2.5.42` / `versionCode 112`.
- Production publication remains the existing main-only signed APK workflow and GitHub release tag matching the final version.

- [ ] **Step 1: Rebase/check current `main` and latest GitHub release before choosing final version.**
  If `2.5.41/111` is still current, use `2.5.42/112`; otherwise choose the next unused version/code. Never overwrite an existing release with different app code.
- [ ] **Step 2: Bump Gradle and version contract to the chosen version/code.**
- [ ] **Step 3: Synchronize README/Android README/START_HIER/SETUP.**
  Document local Trade Republic multi-screenshot import, explicit review, SOLD_MARKED semantics, no automatic transactions/cash changes, bundled local OCR and the non-TR exclusion rule.
- [ ] **Step 4: Update `android-build.yml` release notes; do not change backend expectations.**
- [ ] **Step 5: Run `scripts/test-current-docs.sh` and every Android source contract.**
  Expected: PASS.
- [ ] **Step 6: Run complete Android JVM tests.**
  Expected: PASS.
- [ ] **Step 7: Run instrumented UI tests on configured phone and tablet profiles.**
  Expected: PASS.
- [ ] **Step 8: Build signed release APK and verify APK signature/live backend gate.**
  Expected: PASS with backend `2.1.0`, schema `2026-09-14.1`.
- [ ] **Step 9: Review branch diff for accidental KPI/backend/screenshot-upload changes and confirm no P0/P1 issue remains.**
- [ ] **Step 10: Merge the reviewed branch to `main` only after all pre-merge gates are green.**
- [ ] **Step 11: Wait for main release gates: Android Contract Tests, JVM Tests, Build Android APK and Android Instrumented UI Tests.**
- [ ] **Step 12: Verify the final GitHub release contains `investment-radar.apk` and the in-app update check reports the new version.**
- [ ] **Step 13: Perform production smoke verification: Portfolio -> Depot aktualisieren -> multiple screenshot selection -> recognition/review screen; do not commit test data.**
- [ ] **Step 14: Only after Steps 10–13 succeed report production as 100% complete.**
