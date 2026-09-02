# Investment Radar 1.2.0 Integration and Release Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Integrate backend analysis, Android portfolio recommendations, alerts and UX into one verified 1.2.0 release with backward-compatible deployment order and signed in-app update publication.

**Architecture:** Backend changes land first because the dashboard schema is additive and old Android clients remain compatible. Android CI then runs JVM unit tests plus source regressions before signing. Production deployment is only from `main`; feature branches must build/test without publishing.

**Tech Stack:** GitHub Actions, Azure Functions, Node.js 22, Gradle 8.13, Java 17, Android SDK 36, apksigner, GitHub Releases.

**Spec:** `docs/superpowers/specs/2026-09-02-investment-radar-1.2.0-design.md`

## Global Constraints

- Final Android `versionName = "1.2.0"`.
- Final Android `versionCode = 31`.
- Final backend version is `1.2.0`.
- Backend deployment must be backward compatible with Android 1.1.29.
- Branch Android builds must never publish GitHub Releases.
- Branch backend builds must never deploy production unless existing workflow branch rules explicitly permit it.
- Main Android build must use the permanent signing key and verify APK signature.
- Existing in-app updater remains the upgrade path from 1.1.29.

---

### Task 1: Add all new Android tests to CI before release version bump

**Files:**
- Modify: `.github/workflows/android-build.yml`
- Verify: `android/tests/test-update-resume.sh`
- Verify: `android/tests/test-trade-republic-links.sh`
- Verify: `android/tests/test-analysis-v2-ui.sh`
- Verify: `android/tests/test-portfolio-allocation-ui.sh`
- Verify: `android/tests/test-alert-policy-wiring.sh`
- Verify: `android/tests/test-alert-center-ui.sh`

**Interfaces:**
- CI source regressions run before signing/build.
- JVM tests run after permanent keystore restoration so current Gradle signing configuration can be evaluated safely.

- [ ] **Step 1: Add source regression step list**

The workflow must run:

```bash
bash android/tests/test-update-resume.sh
bash android/tests/test-trade-republic-links.sh
bash android/tests/test-analysis-v2-ui.sh
bash android/tests/test-portfolio-allocation-ui.sh
bash android/tests/test-alert-policy-wiring.sh
bash android/tests/test-alert-center-ui.sh
```

- [ ] **Step 2: Add JVM unit test step after keystore restoration**

From `android/`, run the same build properties/signing properties required by the project:

```bash
gradle --no-daemon :app:testDebugUnitTest \
  -PINVESTMENT_API_BASE_URL="$API_URL" \
  -PFIREBASE_APP_ID="$FB_APP_ID" \
  -PFIREBASE_API_KEY="$FB_API_KEY" \
  -PFIREBASE_PROJECT_ID="$FB_PROJECT_ID" \
  -PFIREBASE_SENDER_ID="$FB_SENDER_ID" \
  -PGITHUB_REPOSITORY="$REPOSITORY_SLUG" \
  -PANDROID_KEYSTORE_PATH="$ANDROID_KEYSTORE_PATH" \
  -PANDROID_KEYSTORE_PASSWORD="$KEYSTORE_PASSWORD" \
  -PANDROID_KEY_ALIAS="$KEY_ALIAS" \
  -PANDROID_KEY_PASSWORD="$KEY_PASSWORD"
```

- [ ] **Step 3: Verify publication guard remains exact**

Keep:

```yaml
if: success() && github.ref == 'refs/heads/main'
```

on `Publish APK for in-app updates`.

- [ ] **Step 4: Commit CI gate**

```bash
git add .github/workflows/android-build.yml android/tests
git commit -m "ci: gate Android 1.2.0 with regression tests"
```

---

### Task 2: Bump Android release version only after feature tests are green

**Files:**
- Modify: `android/app/build.gradle.kts`
- Create: `HOTFIX_1.2.0.md`

**Interfaces:**
- BuildConfig exposes 1.2.0 to updater/user feedback.

- [ ] **Step 1: Change version values**

```kotlin
versionCode = 31
versionName = "1.2.0"
```

Update final build-trigger comment to `Investment Radar 1.2.0`.

- [ ] **Step 2: Write release notes**

`HOTFIX_1.2.0.md` must list, in German, the shipped user-visible changes: scoring V2, expanded universe, portfolio-aware allocation, momentum/fundamentals with coverage, review logic, alert settings/center, Trade Republic routing and manual update feedback. State explicitly that no orders are placed automatically.

- [ ] **Step 3: Run local/static verification available in the execution environment**

```bash
bash android/tests/test-update-resume.sh
bash android/tests/test-trade-republic-links.sh
bash android/tests/test-analysis-v2-ui.sh
bash android/tests/test-portfolio-allocation-ui.sh
bash android/tests/test-alert-policy-wiring.sh
bash android/tests/test-alert-center-ui.sh
```

- [ ] **Step 4: Commit version**

```bash
git add android/app/build.gradle.kts HOTFIX_1.2.0.md
git commit -m "chore: prepare Investment Radar 1.2.0"
```

---

### Task 3: Full feature-branch verification before PR

**Files:**
- No new product files; verification-only task.

**Interfaces:**
- Produces a known-green branch head suitable for PR review.

- [ ] **Step 1: Run backend suite**

```bash
cd backend
npm ci
npm run check
npm test
```

Expected: PASS.

- [ ] **Step 2: Run Android source regressions**

```bash
cd ..
for test in android/tests/*.sh; do bash "$test"; done
```

Expected: PASS.

- [ ] **Step 3: Trigger feature-branch Android workflow**

Verify job outcomes include successful source regressions, unit tests, signing config validation, signed release APK build and signature verification; publication step must be skipped because branch is not `main`.

- [ ] **Step 4: Trigger feature-branch backend workflow**

Verify `npm run check` and `npm test` pass. Production deploy must remain skipped on the feature branch according to workflow guards.

- [ ] **Step 5: Compare feature branch to main**

Review changed filenames and diff. No temporary apply scripts/workflows, secrets, binary keystores, generated APKs, provider responses or local cache files may be committed.

---

### Task 4: PR review and merge backend-compatible 1.2.0

**Files:**
- PR metadata only.

**Interfaces:**
- One PR contains the complete reviewed 1.2.0 change set unless execution split branches are merged into an integration branch first.

- [ ] **Step 1: Open PR**

Title:

```text
Investment Radar 1.2.0 – Analyse, Portfolio und Alarme
```

Body must summarize architecture, backward compatibility, test evidence and deployment order.

- [ ] **Step 2: Review changed-file list and patches**

Confirm the implementation matches all four plan documents and the design spec. Check specifically that:
- static `KAUFEN` no longer drives V2 recommendations,
- missing fundamentals cannot become fake zero values,
- portfolio data has no new upload path,
- Trade Republic has no custom undocumented scheme,
- notification preferences are applied before system notification display,
- branch publishing is still blocked.

- [ ] **Step 3: Verify PR CI green**

Do not merge while either backend or Android verification is red.

- [ ] **Step 4: Squash merge to main**

Use a single release-oriented squash commit after review is complete.

---

### Task 5: Production backend verification after main deployment

**Files:**
- No source changes expected.

**Interfaces:**
- `/api/health` and `/api/dashboard` are live contracts for Android 1.1.29 and 1.2.0.

- [ ] **Step 1: Verify backend main workflow completed successfully**

Confirm check/test/deploy steps are green.

- [ ] **Step 2: Verify health endpoint**

Expected fields:

```json
{
  "ok": true,
  "service": "investment-radar-live",
  "backendVersion": "1.2.0"
}
```

Provider diagnostics may report optional capability limitations without changing `ok: true` when dashboard fallback remains available.

- [ ] **Step 3: Verify dashboard schema**

For at least one stock and one ETF, confirm old fields plus:

```text
scoreTotal
scoreQuality
scoreValuation
scoreGrowth
scoreMomentum
scoreRisk
coverage
recommendation
recommendationReasons
momentum
fundamentals
analysisAsOf
```

Confirm about 40 configured assets are returned and response time/cache behavior is acceptable across two consecutive requests.

- [ ] **Step 4: Compatibility check old-client fields**

Confirm `status` contains a legacy-compatible German label and `allocation` is numeric. No required 1.1.29 field is removed.

---

### Task 6: Production Android build and GitHub release verification

**Files:**
- No source changes expected.

**Interfaces:**
- Main Android workflow publishes `v1.2.0` with `investment-radar.apk`.

- [ ] **Step 1: Verify main Android workflow green**

Required green steps:
- updater regression
- Trade Republic regression
- analysis V2 UI regression
- portfolio allocation UI regression
- alert policy wiring regression
- alert center UI regression
- JVM unit tests
- build/signing configuration validation
- permanent signing key restore
- signed release APK build
- APK signature verification
- publish APK for in-app updates
- artifact upload

- [ ] **Step 2: Verify release metadata**

GitHub Release must be:

```text
tag: v1.2.0
title: Investment Radar 1.2.0
asset: investment-radar.apk
```

- [ ] **Step 3: Verify APK version/signature evidence**

Confirm workflow used `versionCode 31`, `versionName 1.2.0`, and apksigner verification succeeded with the existing permanent signing certificate.

- [ ] **Step 4: Verify updater path from 1.1.29**

The latest GitHub release is newer than 1.1.29, so the existing updater must discover 1.2.0. Unknown-source permission handling must still auto-resume after the user returns from Android settings.

---

### Task 7: Final acceptance audit against the design spec

**Files:**
- Read: `docs/superpowers/specs/2026-09-02-investment-radar-1.2.0-design.md`
- Read: all four 1.2.0 plan files.

**Interfaces:**
- Produces final completion evidence only; no speculative claims.

- [ ] **Step 1: Check all 14 acceptance criteria**

For each criterion in design section 22, point to implementation/test evidence. Any unmet criterion blocks the completion claim.

- [ ] **Step 2: Confirm explicit non-goals remain absent**

No automatic trading, brokerage credentials, Trade Republic scraping, unrestricted symbol search, server-side portfolio storage, adviser/guaranteed-return language or invented fundamentals.

- [ ] **Step 3: Record exact production identifiers**

Capture main merge commit, backend workflow run/job, Android workflow run/job, release ID, APK asset ID, APK byte size and SHA-256 when available.

- [ ] **Step 4: Only then report 1.2.0 complete**

Completion message must distinguish shipped functionality from provider-dependent data coverage. If Twelve Data plan limits fundamentals, state that scoring gracefully reduces coverage rather than claiming unavailable fundamentals are live.
