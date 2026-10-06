# Investment Radar 2.5.28 Release Contract Autofix Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Remove the stale Android 2.5.27/code97 release-gate regression without changing application behavior, and restore a verifiable 2.5.28/code98 release pipeline.

**Architecture:** Keep `android/app/build.gradle.kts` as the source of truth for Android version metadata. Release contracts that only need version consistency derive `versionName`/`versionCode` from Gradle instead of pinning a historical version; unrelated UI contracts stop asserting the app version. Current release documentation is synchronized to the build version.

**Tech Stack:** Bash contract tests, Gradle/Kotlin Android project, GitHub Actions, Markdown documentation.

**Spec:** User `/autofix` request supplied in chat on 2026-10-06.

## Global Constraints

- No production business logic changes.
- No design, navigation, data, API, URL, integration, or financial-rule changes.
- Preserve backend contract at version 2.1.0 and API schema 2026-09-14.1.
- Current Android source of truth is `versionName = 2.5.28`, `versionCode = 98`.
- A fix is complete only after contract tests, JVM tests, build, and relevant UI tests are green.

## Review Focus

- Future Android version bumps must not fail because unrelated tests pin 2.5.28.
- Release candidate comment must stay consistent with Gradle `versionName`.
- Backend 2.1.0/schema checks must remain unchanged.
- Existing release immutability and main-only publish checks must remain intact.
- Documentation must report the exact Gradle version/code.

---

### Task 1: Make release contracts version-consistent instead of historically pinned

**Files:**
- Modify: `android/tests/test-version-contract.sh`
- Modify: `android/tests/test-history-card-glare.sh`
- Modify: `android/tests/test-release-backend-gate.sh`

- [ ] Confirm the current contracts fail because they expect 2.5.27/code97 while Gradle is 2.5.28/code98.
- [ ] Update `test-version-contract.sh` to derive current version metadata from Gradle and verify internal consistency plus backend 2.1.0 and temporary-artifact cleanup.
- [ ] Remove the unrelated Android-version assertion from `test-history-card-glare.sh`; keep only the glare regression assertions.
- [ ] Update `test-release-backend-gate.sh` to derive Android version metadata from Gradle while preserving all backend, release immutability, publish-scope, and release-note checks.
- [ ] Run the Android contract workflow and require all contract steps to pass.

### Task 2: Synchronize current release documentation

**Files:**
- Modify: `README.md`
- Modify: `android/README.md`
- Modify: `START_HIER.md`
- Modify: `SETUP.md`

- [ ] Replace only the current Android release metadata 2.5.27/code97 with 2.5.28/code98.
- [ ] Run the current-documentation contract and require PASS.

### Task 3: Full regression gate

- [ ] Run Android JVM unit tests.
- [ ] Run signed Android release build and live-backend compatibility gate.
- [ ] Run instrumented Android UI tests on configured phone and tablet profiles.
- [ ] Confirm no new P0/P1 regression and report remaining P2/P3/non-verified areas separately.
