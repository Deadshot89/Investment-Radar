# Investment Radar 1.2.0 Release Expansion Integration Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Integrate the Radar, detail, portfolio and alert-navigation expansion, prove the complete 1.2.0 release against the approved gates, merge PR #4 and publish the signed Android update only after production backend version 1.2.0 is live.

**Architecture:** All feature work remains on `ir120-implementation`. Feature-branch CI builds/tests but neither deploys Azure nor publishes an update. Because backend and Android workflows start in parallel after merge, the Android main workflow gains an explicit `/api/health` gate before GitHub Release publication.

**Tech Stack:** GitHub Actions, Azure Functions backend, Kotlin/Android Gradle, signed APK, GitHub Releases.

**Spec:** `docs/superpowers/specs/2026-09-02-investment-radar-1.2.0-release-expansion-design.md`

## Global Constraints

- Target Android version: `versionName = "1.2.0"`, `versionCode = 31`.
- Backend version: `1.2.0`.
- No automatic orders, brokerage login, Trade Republic scraping or server-side portfolio storage.
- Feature branch must not deploy backend or publish update APK.
- Main Android publication is forbidden until `${INVESTMENT_API_BASE_URL}/api/health` reports `backendVersion = 1.2.0`.
- Release requires fresh verification evidence from the exact final head SHA.
- Existing 1.1.29 clients remain compatible with the additive 1.2.0 backend.
- Release asset must be the permanently signed APK produced by the main workflow.
- Do not delete old repository branches as part of this release; report cleanup candidates separately after release.

---

## File Structure

- Create `android/tests/test-release-backend-gate.sh` — permanent workflow contract.
- Modify `.github/workflows/android-contract-tests.yml` — run new UI contracts and release-gate contract.
- Modify `.github/workflows/android-build.yml` — run new UI contracts and add production backend health gate before publish.
- Modify `HOTFIX_1.2.0.md` — final release notes.
- Modify PR #4 metadata after final verification.
- No temporary trigger files, signing material or generated APKs may be committed.

### Task 1: Add backend-health gate before Android publication

**Files:**
- Create: `android/tests/test-release-backend-gate.sh`
- Modify: `.github/workflows/android-build.yml`
- Modify: `.github/workflows/android-contract-tests.yml`

**Interfaces:**
- Consumes GitHub variable: `${{ vars.INVESTMENT_API_BASE_URL }}`.
- Requires endpoint: `${BASE_URL%/}/api/health`.
- Requires JSON field: `backendVersion` exactly `1.2.0`.
- Produces workflow invariant: release publication cannot run unless live backend health passed.

- [ ] **Step 1: Write failing workflow contract**

```bash
#!/usr/bin/env bash
set -euo pipefail
WF=".github/workflows/android-build.yml"
grep -q 'Verify live backend before Android publish' "$WF"
grep -q 'EXPECTED_BACKEND_VERSION: "1.2.0"' "$WF"
grep -q '/api/health' "$WF"
grep -q 'backendVersion' "$WF"
grep -q 'Publish APK for in-app updates' "$WF"
```

Also assert ordering by line number:
```bash
gate_line=$(grep -n 'Verify live backend before Android publish' "$WF" | head -1 | cut -d: -f1)
publish_line=$(grep -n 'Publish APK for in-app updates' "$WF" | head -1 | cut -d: -f1)
test "$gate_line" -lt "$publish_line"
```

- [ ] **Step 2: Run contract and confirm RED**

```bash
bash android/tests/test-release-backend-gate.sh
```
Expected: FAIL because the gate step does not yet exist.

- [ ] **Step 3: Add main-only live backend verification step before publish**

Insert after APK signature verification and before `Publish APK for in-app updates`:

```yaml
      - name: Verify live backend before Android publish
        if: success() && github.ref == 'refs/heads/main'
        env:
          BASE_URL: ${{ vars.INVESTMENT_API_BASE_URL }}
          EXPECTED_BACKEND_VERSION: "1.2.0"
        shell: bash
        run: |
          set -euo pipefail
          HEALTH_URL="${BASE_URL%/}/api/health"
          ok=false
          last_version=""
          for attempt in $(seq 1 24); do
            status=$(curl --silent --show-error --max-time 20 --output /tmp/android-health.json --write-out "%{http_code}" "$HEALTH_URL" || true)
            if [ "$status" = "200" ]; then
              last_version=$(node -e 'const fs=require("fs"); try { const j=JSON.parse(fs.readFileSync("/tmp/android-health.json","utf8")); process.stdout.write(String(j.backendVersion ?? "")); } catch { process.stdout.write(""); }')
              if [ "$last_version" = "$EXPECTED_BACKEND_VERSION" ]; then
                ok=true
                break
              fi
            fi
            sleep 15
          done
          if [ "$ok" != "true" ]; then
            echo "❌ Android-Publish blockiert: Backend ${last_version:-<fehlt>} statt $EXPECTED_BACKEND_VERSION." >> "$GITHUB_STEP_SUMMARY"
            exit 1
          fi
          echo "✅ Backend $EXPECTED_BACKEND_VERSION live; Android-Publish freigegeben." >> "$GITHUB_STEP_SUMMARY"
```

The step is skipped on feature branches. Because subsequent steps use `if: success() && github.ref == 'refs/heads/main'`, a failed gate blocks publication.

- [ ] **Step 4: Add release-gate contract to Android Contract Tests workflow**

Include:
```text
android/tests/test-release-backend-gate.sh
```
in the same contract loop as the other permanent shell tests.

- [ ] **Step 5: Run contract and confirm GREEN**

```bash
bash android/tests/test-release-backend-gate.sh
```
Expected: PASS.

- [ ] **Step 6: Commit the workflow guard**

```bash
git add .github/workflows/android-build.yml .github/workflows/android-contract-tests.yml android/tests/test-release-backend-gate.sh
git commit -m "ci: gate Android release on live backend"
```

### Task 2: Full regression gate on feature branch

**Files:** no production changes unless a test exposes a defect.

- [ ] **Step 1: Run all backend tests**

```bash
cd backend && npm test
```
Expected: exit 0; all backend tests pass.

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
  android/tests/test-portfolio-dashboard-ui.sh \
  android/tests/test-release-backend-gate.sh; do bash "$f"; done
```
Expected: every script exits 0.

- [ ] **Step 3: Run Android JVM unit suite**

```bash
cd android && ./gradlew testReleaseUnitTest
```
Expected: BUILD SUCCESSFUL with zero failed tests.

- [ ] **Step 4: Push branch and inspect full Android workflow**

Require:
```text
Updater regression test = success
Trade Republic link regression test = success
Analysis V2 UI regression test = success
Portfolio allocation UI regression test = success
Alert policy wiring regression test = success
Alert center UI regression test = success
all new 1.2.0 contracts = success
Android JVM unit tests = success
Build signed release APK = success
Verify APK signature = success
Verify live backend before Android publish = skipped on feature branch
Publish APK for in-app updates = skipped on feature branch
Upload APK = success
```

- [ ] **Step 5: If anything is red, use systematic debugging and TDD for that exact failure, then repeat Task 2 from Step 1.**

### Task 3: Release-diff and acceptance review

**Files:**
- Modify: `HOTFIX_1.2.0.md`
- PR #4 metadata.

- [ ] **Step 1: Compare `main...ir120-implementation` and reject unintended files**

Allowed groups:
- intended backend Analysis V2 files/tests/data,
- intended Android app/tests/workflows,
- `HOTFIX_1.2.0.md`,
- approved specs/plans.

Reject:
```text
integration trigger files
local build outputs
keystore/signing material
committed APK binaries
generated .gradle/build folders
scratch migration scripts not required at runtime/test time
```

- [ ] **Step 2: Check every expansion acceptance criterion**

```text
[ ] Search matches name/ticker/ISIN/type, including merged local custom investments
[ ] Recommendation/type/holding/watchlist/data-quality/risk filters combine
[ ] Score/allocation/6M/day up/day down/name sorting works with nulls last
[ ] Shared detail opens from Radar and Portfolio
[ ] Detail handles curated and local custom items
[ ] Detail shows five scores, coverage, momentum, fundamentals, reasons and actions when available
[ ] Missing values render unavailable, never zero
[ ] Freshness follows 6h/24h/7d precedence
[ ] Portfolio summary shows complete or partial value correctly
[ ] Missing current price suppresses complete total performance
[ ] Position cards show weight, score/recommendation, monthly allocation and concentration
[ ] Alert tap marks read and opens shared detail
[ ] Unknown alert item shows a safe message
[ ] Trade Republic and manual update flows remain tested
[ ] Detail/filter/sort cause no provider call
[ ] Android publication is gated on live backend 1.2.0
```

- [ ] **Step 3: Finalize `HOTFIX_1.2.0.md`**

Required headings:
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

State clearly: portfolio stays on device; no automatic orders or sales.

- [ ] **Step 4: Update PR #4 body with expansion scope and fresh verification run IDs**

Keep PR as Draft until Task 4 has passed against its exact final head SHA.

- [ ] **Step 5: Commit release notes**

```bash
git add HOTFIX_1.2.0.md
git commit -m "docs: finalize Investment Radar 1.2.0 release notes"
```

### Task 4: Final exact-head verification

**Files:** none unless failure occurs.

- [ ] **Step 1:** Record final `ir120-implementation` head SHA.
- [ ] **Step 2:** Confirm Android Contract Tests on that SHA = success.
- [ ] **Step 3:** Confirm Android JVM Tests on that SHA = success.
- [ ] **Step 4:** Confirm Build Android APK on that SHA = success.
- [ ] **Step 5:** Confirm signed APK + signature verification = success, backend gate + publish = skipped on feature branch.
- [ ] **Step 6:** Confirm backend feature-branch tests = success and all Azure deployment steps = skipped.
- [ ] **Step 7:** Re-fetch PR #4 and require its `head_sha` equals the recorded verified SHA.

If head SHA changes after any check, restart Task 4.

### Task 5: Final PR review and merge

**Files:** PR #4.

- [ ] **Step 1: Review complete PR patch with mandatory high-risk set**

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
.github/workflows/android-build.yml
```

- [ ] **Step 2:** Confirm no unresolved review threads and no critical/important findings.
- [ ] **Step 3:** Mark PR #4 ready for review.
- [ ] **Step 4:** Squash merge PR #4 using `expected_head_sha` equal to the exact verified SHA.

Commit title:
```text
Investment Radar 1.2.0 – Analysis V2, Radar Search, Detail & Portfolio Dashboard
```

### Task 6: Production backend and Android release verification

**Files:** workflow/release outputs only.

- [ ] **Step 1: Monitor `Deploy Backend` on merged main SHA**

Require syntax check, backend tests, Azure deployment and existing `Verify live backend` step all success. That step already polls `${BASE_URL%/}/api/health` for `backendVersion=1.2.0`.

- [ ] **Step 2: Monitor `Build Android APK` on merged main SHA**

The workflow may run in parallel with backend deployment, but must stop at the new `Verify live backend before Android publish` step until 1.2.0 health succeeds.

Require:
```text
all regression/contract tests = success
Android JVM unit tests = success
Build signed release APK = success
Verify APK signature = success
Verify live backend before Android publish = success
Publish APK for in-app updates = success
Upload APK = success
```

- [ ] **Step 3: Verify live dashboard schema after backend health is green**

At least one item must expose:
```text
id, name, ticker, isin, price, recommendation,
scoreTotal, scoreQuality, scoreValuation, scoreGrowth,
scoreMomentum, scoreRisk, coverage, momentum,
fundamentals, analysisAsOf
```
Missing optional values must remain null/absent, not fabricated zero.

- [ ] **Step 4: Inspect release `v1.2.0`**

Require:
```text
tag: v1.2.0
asset: investment-radar.apk
Android versionName: 1.2.0
Android versionCode: 31
```

- [ ] **Step 5: Record final evidence**

Completion report must include:
```text
PR #4 merge SHA
backend workflow run/job result
Android workflow run/job result
release ID/tag
APK byte size
APK SHA-256
confirmation that 1.1.29 updater sees 1.2.0 as newer
```

### Task 7: Post-release branch report

**Files:** none.

- [ ] **Step 1:** Verify PR #4 merged and `main` contains the approved specs/plans.
- [ ] **Step 2:** List `ir120-implementation`, `ir120-design`, old `ir127-*`, `ir128-*`, `ir129-*` as cleanup candidates with whether each is merged/superseded.
- [ ] **Step 3:** Do not delete branches in this release task. Ask the user before destructive branch cleanup.
