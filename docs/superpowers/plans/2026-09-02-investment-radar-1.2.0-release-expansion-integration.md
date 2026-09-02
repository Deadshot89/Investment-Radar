# Investment Radar 1.2.0 Release Expansion Integration Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Integrate the Radar, detail, portfolio and alert-navigation expansion, prove the complete 1.2.0 release against the approved gates, merge PR #4 and publish the signed Android update only after production backend verification.

**Architecture:** All feature work remains on `ir120-implementation`. CI continues to build/test feature branches without publishing or deploying. Production deployment and APK publication occur only after the reviewed PR is merged to `main`.

**Tech Stack:** GitHub Actions, Azure Functions backend, Kotlin/Android Gradle, signed APK, GitHub Releases.

**Spec:** `docs/superpowers/specs/2026-09-02-investment-radar-1.2.0-release-expansion-design.md`

## Global Constraints

- Target Android version remains `versionName = "1.2.0"`, `versionCode = 31`.
- Backend version remains `1.2.0`.
- No automatic orders, brokerage login, Trade Republic scraping or server-side portfolio storage.
- Feature branch must not deploy backend or publish update APK.
- Release requires fresh verification evidence; do not reuse earlier green runs from before the expansion.
- Existing 1.1.29 clients must remain compatible with the 1.2.0 backend.
- Release asset must be the permanently signed APK from the main workflow.

---

## File Structure

- Modify `HOTFIX_1.2.0.md` — final user-visible release notes including the expansion.
- Modify `.github/workflows/android-contract-tests.yml` — include permanent new UI contracts.
- Modify `.github/workflows/android-build.yml` only if new contract scripts are not already executed in the full build.
- Modify `android/app/build.gradle.kts` only if verification finds version drift; expected values are already 1.2.0 / 31.
- Modify PR #4 body/title as final release summary; no production code change here.
- No temporary trigger files are allowed in the final diff.

### Task 1: Full local/CI regression gate on feature branch

**Files:** no code changes unless tests expose defects.

- [ ] **Step 1: Run all backend tests**

```bash
cd backend && npm test
```
Expected: exit 0; all backend tests pass, including scoring, history, fundamentals, cache, state, signals and push payload tests.

- [ ] **Step 2: Run all permanent Android shell contracts**

```bash
for f in \
  android/tests/test-update-resume.sh \
  android/tests/test-trade-republic-links.sh \
  android/tests/test-analysis-v2-ui.sh \
  android/tests/test-portfolio-allocation-ui.sh \
  android/tests/test-alert-policy-wiring.sh \
  android/tests/test-alert-center-ui.sh \
  android/tests/test-alert-center-wiring.sh \
  android/tests/test-update-status-ui.sh \
  android/tests/test-trade-republic-navigator-wiring.sh \
  android/tests/test-investment-detail-ui.sh \
  android/tests/test-portfolio-dashboard-ui.sh; do bash "$f"; done
```
Expected: every script exits 0.

- [ ] **Step 3: Run Android JVM unit suite**

```bash
cd android && ./gradlew testReleaseUnitTest
```
Expected: BUILD SUCCESSFUL with zero failed tests.

- [ ] **Step 4: Run signed release build and signature verification using the same commands as `.github/workflows/android-build.yml`**

The workflow must show:
- `Build signed release APK` = success
- `Verify APK signature` = success
- `Publish APK for in-app updates` = skipped on `ir120-implementation`
- `Upload APK` = success

- [ ] **Step 5: Do not move forward if any step is red. Apply systematic debugging + TDD for the specific failure and repeat the full gate from Step 1.**

### Task 2: Release-diff and requirement review

**Files:**
- Modify: `HOTFIX_1.2.0.md`
- PR #4 metadata.

- [ ] **Step 1: Compare `main...ir120-implementation` and list all changed files**

Require that changes are confined to:
- intended backend Analysis V2 files/tests/data,
- intended Android app/tests/workflows,
- `HOTFIX_1.2.0.md`,
- approved specs/plans.

Reject temporary files such as integration trigger text files, local build outputs, signing material, APK binaries committed to source, generated Gradle folders or scratch scripts.

- [ ] **Step 2: Review the five expansion acceptance groups line-by-line**

Checklist:
```text
[ ] Radar search matches name/ticker/ISIN/type
[ ] Recommendation/type/holding/watchlist/data-quality/risk filters combine
[ ] All approved sort options work with nulls last
[ ] Shared detail opens from Radar and Portfolio
[ ] Detail shows five scores, coverage, momentum, fundamentals, reasons and actions
[ ] Missing values display as unavailable rather than zero
[ ] Freshness follows 6h/24h/7d precedence rules
[ ] Portfolio header shows total/partial value, cost basis, P/L, held count and largest position
[ ] Missing price suppresses complete portfolio performance
[ ] Position cards show weight, score/recommendation, monthly allocation and concentration
[ ] Alert tap marks read and opens shared detail
[ ] Unknown alert item shows a safe message
[ ] Trade Republic flow remains explicit and tested
[ ] Manual update flow remains tested
[ ] No new provider request occurs when opening detail/filtering
```

- [ ] **Step 3: Update `HOTFIX_1.2.0.md`**

Release notes must include these headings:
```markdown
## Analyse V2
## Radar-Suche & Filter
## Wertpapier-Details
## Portfolio-Dashboard V2
## Alarmcenter & Direktnavigation
## Datenqualität & Quellen
## Trade Republic & Updates
## Sicherheit / Datenschutz
```

State clearly that portfolio data stays on device and the app never places orders automatically.

- [ ] **Step 4: Update PR #4 body**

Add the expansion items and replace any old verification wording with the fresh final run IDs/results once available. Keep PR as Draft until Task 3 is complete.

- [ ] **Step 5: Commit documentation-only release-note changes**

```bash
git add HOTFIX_1.2.0.md
git commit -m "docs: finalize Investment Radar 1.2.0 release notes"
```

### Task 3: Final feature-branch verification after the last commit

**Files:** none unless failure occurs.

- [ ] **Step 1: Wait for the workflows attached to the exact final branch head SHA.**
- [ ] **Step 2: Confirm Android Contract Tests = success.**
- [ ] **Step 3: Confirm Android JVM Tests = success.**
- [ ] **Step 4: Confirm Build Android APK = success.**
- [ ] **Step 5: Inspect build job steps and confirm signed APK build + signature verification success and publish step skipped.**
- [ ] **Step 6: Confirm backend feature-branch workflow tests are success and Azure deployment steps are skipped.**
- [ ] **Step 7: Re-fetch PR #4 and verify `head_sha` equals the SHA used by all final green checks.**

No merge is allowed if the PR head moves after verification; rerun this task against the new head.

### Task 4: Final PR review and merge

**Files:** PR #4.

- [ ] **Step 1: Fetch the complete PR patch and review the high-risk files**

Mandatory high-risk review set:
```text
backend/src/lib/scoring.mjs
backend/src/lib/signals.mjs
backend/src/lib/dashboard.mjs
backend/src/functions/marketWatch.mjs
android/app/src/main/java/de/tobias/investmentradar/RecommendationEngine.kt
android/app/src/main/java/de/tobias/investmentradar/RadarFilterState.kt
android/app/src/main/java/de/tobias/investmentradar/DataFreshness.kt
android/app/src/main/java/de/tobias/investmentradar/PortfolioMetrics.kt
android/app/src/main/java/de/tobias/investmentradar/InvestmentDetailScreen.kt
android/app/src/main/java/de/tobias/investmentradar/PortfolioDashboard.kt
android/app/src/main/java/de/tobias/investmentradar/MainActivity.kt
android/app/src/main/java/de/tobias/investmentradar/InvestmentMessagingService.kt
```

- [ ] **Step 2: Confirm no unresolved PR review threads and no critical/important review findings remain.**
- [ ] **Step 3: Mark PR #4 ready for review.**
- [ ] **Step 4: Squash merge PR #4 with expected head SHA**

Commit title:
```text
Investment Radar 1.2.0 – Analysis V2, Radar Search, Detail & Portfolio Dashboard
```

Use `expected_head_sha` equal to the verified final branch head so GitHub rejects a moved target.

### Task 5: Production backend verification on main

**Files:** none unless deployment fails.

- [ ] **Step 1: Wait for `Deploy Backend` main workflow triggered by the merge.**
- [ ] **Step 2: Confirm backend syntax/tests success before deployment.**
- [ ] **Step 3: Confirm Azure deployment step success.**
- [ ] **Step 4: Fetch live `/health` and require backend version `1.2.0`.**
- [ ] **Step 5: Fetch live dashboard endpoint and verify additive schema on at least one investment**

Required fields:
```text
id, name, ticker, isin, price, recommendation,
scoreTotal, scoreQuality, scoreValuation, scoreGrowth,
scoreMomentum, scoreRisk, coverage, momentum,
fundamentals, analysisAsOf
```

- [ ] **Step 6: Verify missing optional market/fundamental values are JSON null/absent as designed, never fabricated zero values.**

If live backend verification fails, do not publish Android 1.2.0. Fix backend on a new branch/PR and repeat production verification.

### Task 6: Production Android build and GitHub release

**Files:** GitHub Actions/Release output only.

- [ ] **Step 1: Wait for `Build Android APK` on the merged `main` SHA.**
- [ ] **Step 2: Confirm all shell regressions and JVM tests are successful.**
- [ ] **Step 3: Confirm `Build signed release APK` successful.**
- [ ] **Step 4: Confirm `Verify APK signature` successful.**
- [ ] **Step 5: Confirm `Publish APK for in-app updates` runs on main and succeeds.**
- [ ] **Step 6: Inspect release `v1.2.0`**

Require:
```text
tag: v1.2.0
asset: investment-radar.apk
Android versionName: 1.2.0
Android versionCode: 31
```

- [ ] **Step 7: Record APK byte size and SHA-256 in the completion report.**
- [ ] **Step 8: Confirm the existing in-app updater sees `v1.2.0` as newer than 1.1.29.**

### Task 7: Post-release repository cleanup

**Files:** repository branches only; no product code.

- [ ] **Step 1: Verify `main` contains the squash-merged 1.2.0 commit and PR #4 is merged.**
- [ ] **Step 2: Remove `ir120-implementation` only after release verification is complete.**
- [ ] **Step 3: Remove `ir120-design` after confirming all approved spec/plan documents are present on `main`.**
- [ ] **Step 4: Review old `ir127-*`, `ir128-*`, `ir129-*` branches and delete only branches fully merged/superseded by main. Do not delete a branch with unique unmerged commits.**
- [ ] **Step 5: Final report must state release tag, merge SHA, backend workflow result, Android workflow result, APK SHA-256 and any remaining branches intentionally kept.**
