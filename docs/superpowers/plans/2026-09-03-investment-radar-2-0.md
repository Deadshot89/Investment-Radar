# Investment Radar 2.0 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Upgrade Investment Radar from a ~43-item fixed dashboard to a scalable Trade-Republic-focused discovery radar that can expose roughly 1,000 stocks and ETFs without loading full analysis objects on the phone.

**Architecture:** Keep the existing dashboard endpoint for compatibility, add a canonical server-side universe and compact radar-summary/detail APIs, and extend Android with lightweight radar models, server-side filters, paging, search, discovery sections and lazy detail loading. Existing IDs, portfolio semantics, portfolio-only exclusions, recommendation concentration rules and release gates remain intact.

**Tech Stack:** Node.js ESM/Azure Functions backend, JSON seed data, Kotlin/Jetpack Compose Android app, JUnit/JVM tests, shell contract tests, GitHub Actions.

**Spec:** `docs/superpowers/specs/2026-09-03-investment-radar-2-0-design.md`

## Global Constraints

- Target release is **2.0.0**; no user-facing mini releases before the major release.
- Target universe is roughly **1,000 stocks and ETFs**, focused on instruments relevant to Trade Republic.
- No derivatives, warrants, leverage products or deliberately illiquid penny-stock candidates.
- `portfolioOnly == true` must never appear as an automatic new-buy candidate.
- Existing instrument IDs must remain stable.
- Existing portfolio snapshot/trackedShares semantics remain unchanged.
- Forecasts remain model/scenario estimates, never guarantees.
- A data-quality failure for one instrument must not break the complete radar response.

---

### Task 1: Canonical Universe and Validation

**Files:**
- Create: `backend/src/lib/universe.mjs`
- Create: `backend/test/universe.test.mjs`
- Modify: `backend/data/investments.json`

**Interfaces:**
- Produces: `loadUniverse()`, `validateUniverse(items)`, `normalizeUniverseInstrument(item)`.
- Instrument metadata includes `region`, `country`, `sector`, `industry`, `marketCapBucket`, `tradeRepublicEligible`, `universeActive`, `portfolioOnly`, `dataQualityTier`.

- [ ] Write failing tests proving stable IDs/ISIN uniqueness, portfolio-only preservation, inactive-item support and a near-1,000 active-universe gate.
- [ ] Run backend tests and confirm RED.
- [ ] Implement canonical normalization and validation while preserving current 43 IDs.
- [ ] Expand seed data through a maintainable generated/curated universe source; never fabricate companies or securities.
- [ ] Run tests and confirm GREEN.
- [ ] Commit.

### Task 2: Compact Radar Query Engine

**Files:**
- Create: `backend/src/lib/radar.mjs`
- Create: `backend/src/functions/radar.mjs`
- Create: `backend/src/functions/instrumentDetail.mjs`
- Modify: `backend/src/index.mjs`
- Create: `backend/test/radar.test.mjs`

**Interfaces:**
- Produces: `queryRadar({ query, type, region, country, sector, recommendation, riskMax, qualityTier, sort, page, pageSize })`.
- Radar response: `{ generatedAt, total, page, pageSize, items, facets }`.
- Detail response resolves one instrument by stable ID and returns the existing rich analysis shape.

- [ ] Write failing tests for search by name/ticker/ISIN, filters, sorting, paging, facets and portfolio-only/new-buy exclusion.
- [ ] Verify RED.
- [ ] Implement compact server-side radar summaries and lazy detail endpoint.
- [ ] Ensure one bad quote/analysis object cannot fail the page.
- [ ] Verify GREEN and commit.

### Task 3: Universe Screening and Discovery Buckets

**Files:**
- Create: `backend/src/lib/radarDiscovery.mjs`
- Create: `backend/test/radarDiscovery.test.mjs`
- Modify: `backend/src/lib/radar.mjs`

**Interfaces:**
- Produces discovery buckets: `topOpportunities`, `newInRadar`, `strongMomentum`, `attractiveValuation`, `qualityStocks`, `etfs`, `portfolioComplements`.

- [ ] Write failing deterministic bucket/ranking tests.
- [ ] Verify RED.
- [ ] Implement ranking from compact scores and data quality.
- [ ] Block inactive, insufficient-quality and portfolio-only instruments from new-buy buckets.
- [ ] Verify GREEN and commit.

### Task 4: Android Radar 2.0 Data Contract

**Files:**
- Modify: `android/app/src/main/java/de/tobias/investmentradar/Models.kt`
- Modify: `android/app/src/main/java/de/tobias/investmentradar/ApiClient.kt`
- Create: `android/app/src/main/java/de/tobias/investmentradar/RadarModels.kt`
- Create: `android/app/src/test/java/de/tobias/investmentradar/RadarApiContractTest.kt`

**Interfaces:**
- Produces Kotlin `RadarSummaryItem`, `RadarPage`, `RadarFacets`, `RadarQuery` and API methods for summary/detail.

- [ ] Write failing parser/query tests.
- [ ] Verify RED.
- [ ] Add metadata fields without breaking existing dashboard parsing.
- [ ] Add paged radar and detail client calls.
- [ ] Verify GREEN and commit.

### Task 5: Android Radar 2.0 State, Search and Filters

**Files:**
- Modify: `android/app/src/main/java/de/tobias/investmentradar/RadarFilterState.kt`
- Create: `android/app/src/main/java/de/tobias/investmentradar/RadarRepository.kt`
- Modify: `android/app/src/main/java/de/tobias/investmentradar/MainViewModel.kt`
- Create: `android/app/src/test/java/de/tobias/investmentradar/RadarPagingStateTest.kt`

**Interfaces:**
- Produces paged loading, refresh, load-more, search and server-backed filter state.

- [ ] Write failing state tests for first page, load-more, filter reset, search, error isolation and stale-request protection.
- [ ] Verify RED.
- [ ] Implement repository/state integration.
- [ ] Preserve existing portfolio/custom-asset promotion paths.
- [ ] Verify GREEN and commit.

### Task 6: Radar 2.0 Compose Discovery UI

**Files:**
- Modify/create the existing Radar screen Compose source used by `MainActivity`.
- Modify: `android/app/src/main/java/de/tobias/investmentradar/MainActivity.kt` only where navigation wiring is required.
- Create: `android/tests/test-radar-2-ui.sh`
- Modify: `.github/workflows/android-contract-tests.yml`

**Interfaces:**
- Shows discovery sections plus full result list with visible result count, search, active filters and incremental loading.

- [ ] Add failing UI source contract for required 2.0 labels and paging/filter wiring.
- [ ] Verify RED in CI.
- [ ] Implement dark-mode discovery UI with sections: Top Chancen, Neu im Radar, Starkes Momentum, Attraktive Bewertung, Qualitätsaktien, ETFs, Depot-Ergänzungen.
- [ ] Add filters for type, region/country, sector, risk, quality, score/recommendation and portfolio/watchlist where data is available.
- [ ] Verify GREEN and commit.

### Task 7: Monthly Buy Engine on Large Universe

**Files:**
- Modify: `android/app/src/main/java/de/tobias/investmentradar/RecommendationEngine.kt`
- Create: `android/app/src/test/java/de/tobias/investmentradar/LargeUniverseRecommendationTest.kt`

**Interfaces:**
- Consumes compact/eligible BUY candidates plus current portfolio weights.
- Preserves €100 budget behavior, concentration brake and `portfolioOnly` exclusion.

- [ ] Write failing 1,000-item recommendation tests including concentration and portfolio-only adversarial cases.
- [ ] Verify RED.
- [ ] Adapt engine/candidate feed only as required for large-universe input.
- [ ] Verify GREEN and commit.

### Task 8: 2.0 Release Gates and Publication

**Files:**
- Modify: `android/tests/test-release-backend-gate.sh`
- Modify: `android/app/build.gradle.kts`
- Modify backend CI tests/workflow only where needed for the new universe/radar gates.

**Interfaces:**
- Release version: `versionName = "2.0.0"`; versionCode increments from 45.
- Publish only after backend tests, Android JVM tests, contract tests, signed build and live-backend gate are green.

- [ ] Add/adjust release gate to require 2.0.0 and the new backend contract.
- [ ] Verify release gate RED before bump.
- [ ] Bump to 2.0.0.
- [ ] Run fresh backend, Android contract and JVM verification.
- [ ] Build and verify signed release APK.
- [ ] Run live backend gate and in-app publish.
- [ ] Record final workflow run IDs and commit SHA before claiming completion.
