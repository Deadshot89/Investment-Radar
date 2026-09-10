# Complete Data & Analysis Pipeline Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Vollständige, konsistente Markt-, Historien-, Fundamental-, Qualitäts- und Prognosedaten für Radar, Depot, Detailansicht und Alarmcenter bereitstellen, ohne fehlende Werte zu erfinden.

**Architecture:** Das Backend führt mehrere echte Provider über Adapter zusammen, normalisiert alle Eingaben in ein kanonisches Analysemodell und bewertet Quote-, History-, Fundamental- und Forecast-Abdeckung getrennt. Score, BUY-Gate, Prognose, Depot-Aktionscenter und Alarmtexte verwenden anschließend dasselbe Analyseobjekt; Android stellt diese Informationen konsistent dar und berechnet keine externen Finanzkennzahlen selbst.

**Tech Stack:** Node.js ESM Azure Functions Backend, Twelve Data, Yahoo Finance, SEC Companyfacts, bestehende Cache-Layer, Kotlin/Jetpack Compose Android, JVM-Tests, Shell-Regressionstests, GitHub Actions.

**Spec:** `docs/superpowers/specs/2026-09-10-complete-data-analysis-pipeline-design.md`

## Global Constraints

- Keine erfundenen Kennzahlen; fehlende Daten bleiben `null` und werden transparent ausgewiesen.
- Provider-Reihenfolge für Fundamentals: Twelve Data → Yahoo Finance → SEC Companyfacts → gültiger Cache → fehlend mit Diagnose.
- Historie: Twelve Data → Yahoo Finance → gültiger Cache → fehlend.
- BUY nur bei bestätigter Trade-Republic-Handelbarkeit, aktuellem Kurs, ausreichender Historie, ausreichenden Fundamentals bei Aktien, ausreichender Gesamt-/Forecast-Abdeckung und ohne kritische Datenkonflikte.
- Qualitätsklassen: HOCH >= 85 %, GUT 70–84 %, EINGESCHRÄNKT 50–69 %, UNVOLLSTÄNDIG < 50 % oder kritischer Pflichtblock fehlt.
- ETFs werden nicht künstlich mit Aktien-Fundamentals bewertet.
- Apple, Nel ASA und Samsung Electronics GDR sind verpflichtende Referenzfälle für Live-/Integrationsprüfungen.
- Radar, Depot, Detail und Alarmcenter müssen dasselbe aktuelle Analyseobjekt verwenden.

---

### Task 1: Kanonisches Analysemodell und Datenqualitätslogik

**Files:**
- Create: `backend/src/lib/analysisDataNormalizer.mjs`
- Create: `backend/src/lib/dataQuality.mjs`
- Create: `backend/test/analysisDataNormalizer.test.mjs`
- Create: `backend/test/dataQuality.test.mjs`

**Interfaces:**
- Produces: `normalizeAnalysisInput({ item, quote, history, fundamentals })`
- Produces: `evaluateDataQuality({ item, quote, history, fundamentals })`
- Produces quality fields: `quoteCoverage`, `historyCoverage`, `fundamentalCoverage`, `forecastInputCoverage`, `overallCoverage`, `qualityTier`, `missingBlocks`, `criticalConflicts`

- [ ] **Step 1: Write failing normalizer tests**

```js
import test from 'node:test';
import assert from 'node:assert/strict';
import { normalizeAnalysisInput } from '../src/lib/analysisDataNormalizer.mjs';

test('normalizer keeps missing values null instead of zero', () => {
  const out = normalizeAnalysisInput({
    item: { id: 'aapl', type: 'STOCK' },
    quote: { price: 200, currency: 'USD' },
    history: { m12: null },
    fundamentals: { metrics: { pe: null, roe: 0.25 } }
  });
  assert.equal(out.history.m12, null);
  assert.equal(out.fundamentals.pe, null);
  assert.equal(out.fundamentals.roe, 0.25);
});
```

- [ ] **Step 2: Run test and verify RED**

Run: `cd backend && node --test test/analysisDataNormalizer.test.mjs`
Expected: FAIL because module/function does not exist.

- [ ] **Step 3: Implement minimal normalizer**

```js
export function normalizeAnalysisInput({ item, quote, history, fundamentals }) {
  const metrics = fundamentals?.metrics ?? fundamentals ?? {};
  return {
    item,
    quote: quote ?? null,
    history: history ?? null,
    fundamentals: Object.fromEntries(Object.entries(metrics).map(([k, v]) => [k, finiteOrNull(v)]))
  };
}
function finiteOrNull(v) { const n = Number(v); return v == null || !Number.isFinite(n) ? null : n; }
```

- [ ] **Step 4: Add failing quality tests**

```js
test('stock with quote, full history and fundamentals gets high coverage', () => {
  const q = evaluateDataQuality({
    item: { type: 'STOCK' },
    quote: { price: 100, currency: 'EUR', percentChange: 1 },
    history: { m1: 1, m3: 2, m6: 4, m12: 10, pointsCount: 250 },
    fundamentals: { metrics: { pe: 20, priceToSales: 4, evToEbitda: 15, freeCashFlowYield: .03, revenueGrowth: .1, epsGrowth: .12, operatingMargin: .2, netMargin: .18, roe: .3, roic: .2, debtToEquity: 1 } }
  });
  assert.ok(q.overallCoverage >= 85);
  assert.equal(q.qualityTier, 'HOCH');
});
```

- [ ] **Step 5: Implement fixed quality weights from the spec**

Use explicit stock weights in `dataQuality.mjs`: Quote 20 %, History 30 %, Fundamentals 35 %, Forecast inputs 15 %. For ETFs use Quote 30 %, History 45 %, configured metadata/risk 25 %. Critical missing quote or insufficient history forces `UNVOLLSTÄNDIG`.

- [ ] **Step 6: Run both test files**

Run: `cd backend && node --test test/analysisDataNormalizer.test.mjs test/dataQuality.test.mjs`
Expected: PASS.

- [ ] **Step 7: Commit**

Commit message: `feat: add canonical analysis normalization and data quality`

---

### Task 2: Realer Fundamental-Fallback über Yahoo Finance und SEC

**Files:**
- Create: `backend/src/lib/fundamentalFallbackProvider.mjs`
- Create: `backend/test/fundamentalFallbackProvider.test.mjs`
- Modify: `backend/src/lib/fundamentals.mjs`
- Modify: `backend/test/fundamentals.test.mjs`

**Interfaces:**
- Produces: `loadFundamentalFallback(item, { fetchImpl })`
- Returns: `{ raw, source, asOf, error, fieldSources }`

- [ ] **Step 1: Write failing Yahoo fallback test**

Create a mocked Yahoo `quoteSummary` response containing `summaryDetail`, `defaultKeyStatistics` and `financialData`; assert that canonical fields such as `pe`, `priceToSales`, `debtToEquity`, `roe`, `revenueGrowth` are populated when present.

- [ ] **Step 2: Run and verify RED**

Run: `cd backend && node --test test/fundamentalFallbackProvider.test.mjs`
Expected: FAIL because provider does not exist.

- [ ] **Step 3: Implement Yahoo adapter**

Use `https://query1.finance.yahoo.com/v10/finance/quoteSummary/{symbol}` with modules `summaryDetail,defaultKeyStatistics,financialData`. Parse only explicit provider values; never derive missing metrics except `freeCashFlowYield` when both FCF and market cap are present.

- [ ] **Step 4: Add SEC Companyfacts fallback test**

Mock `https://data.sec.gov/api/xbrl/companyfacts/CIKXXXXXXXXXX.json`; assert extraction for US equities when Yahoo/Twelve lacks required values. Use only latest annual/quarterly USD facts with matching units.

- [ ] **Step 5: Implement SEC adapter and CIK lookup support**

Use item metadata `cik` if present. If absent, SEC is skipped instead of guessing identity.

- [ ] **Step 6: Integrate into `loadFundamentals`**

Change flow: fresh cache → Twelve Data → fallback adapter → merge complementary non-conflicting fields → stale cache → missing. Store `fieldSources`, `source`, `fetchedAt`, and conflicts in cache.

- [ ] **Step 7: Add conflict test**

Assert materially conflicting provider values are marked in `conflicts` and are not silently overwritten.

- [ ] **Step 8: Run provider/fundamental tests**

Run: `cd backend && node --test test/fundamentalFallbackProvider.test.mjs test/fundamentals.test.mjs`
Expected: PASS.

- [ ] **Step 9: Commit**

Commit message: `feat: add multi-source fundamental fallback`

---

### Task 3: Historienabdeckung, Symbol-Fallbacks und echte Momentum-Diagnose

**Files:**
- Modify: `backend/src/lib/history.mjs`
- Modify: `backend/src/lib/historySupport.mjs`
- Modify: `backend/test/history.test.mjs`
- Create: `backend/test/historyCoverage.test.mjs`

**Interfaces:**
- `loadHistory()` returns momentum plus `pointsCount`, `oldestPointAt`, `newestPointAt`, `coveragePct`, `source`, `stale`, `error`.

- [ ] **Step 1: Write failing coverage tests**

Assert 230+ valid daily points over roughly one year yield high history coverage; a 20-point series does not produce a valid 12M value.

- [ ] **Step 2: Run RED**

Run: `cd backend && node --test test/history.test.mjs test/historyCoverage.test.mjs`

- [ ] **Step 3: Extend `calculateMomentum`**

Return `pointsCount`, `coveragePct`, and keep m1/m3/m6/m12 `null` when the relevant lookback cannot be supported by actual observations.

- [ ] **Step 4: Strengthen symbol fallback order**

Try explicit `yahooSymbol`, provider-specific configured symbol, then deterministic Xetra `.DE` fallback only where exchange metadata justifies it. Do not guess arbitrary suffixes.

- [ ] **Step 5: Run tests**

Expected: PASS.

- [ ] **Step 6: Commit**

Commit message: `feat: make history coverage explicit and reliable`

---

### Task 4: Einheitliche Analysepipeline im Radar und Detail-Endpunkt

**Files:**
- Modify: `backend/src/lib/radar.mjs`
- Modify: `backend/src/lib/scoring.mjs`
- Modify: `backend/test/radar.test.mjs`
- Modify: `backend/test/scoring.test.mjs`
- Create: `backend/test/dataGate.test.mjs`

**Interfaces:**
- Radar summary gains: `dataQuality`, `scoreBreakdown`, `forecast`, `diagnostics`.
- BUY gate consumes `dataQuality` and `criticalConflicts`.

- [ ] **Step 1: Write failing BUY-gate tests**

Test that `analysis.recommendation === 'BUY'` is downgraded when overall coverage < 70, forecast input coverage < 70, quote missing, history critical, stock fundamentals critical, or provider conflict exists.

- [ ] **Step 2: Run RED**

Run: `cd backend && node --test test/dataGate.test.mjs`

- [ ] **Step 3: Wire normalizer and quality evaluator into `analyzeSummaries`**

Load quote/history/fundamentals, normalize once, calculate scores, quality, forecast, and recommendation from the same object.

- [ ] **Step 4: Extend score breakdown**

For quality/valuation/growth/momentum/risk return `{ score, coverage, reasons, inputs }`; missing data must not be converted to score zero.

- [ ] **Step 5: Run radar/scoring tests**

Run: `cd backend && node --test test/radar.test.mjs test/scoring.test.mjs test/dataGate.test.mjs`
Expected: PASS.

- [ ] **Step 6: Commit**

Commit message: `feat: unify radar analysis and recommendation quality gates`

---

### Task 5: Vollständige 12M-Prognose mit Qualität, Treibern und Risiken

**Files:**
- Modify: `backend/src/lib/forecast12m.mjs`
- Create: `backend/test/forecast12m.test.mjs`

**Interfaces:**
- `forecast12m(item)` returns `{ expectedChangePct, bearChangePct, bullChangePct, direction, quality, confidencePct, reasons, risks, usedInputs, asOf }`.

- [ ] **Step 1: Write failing forecast-quality tests**

Test HOCH/MITTEL/NIEDRIG/NICHT_BELASTBAR based on forecast input coverage and missing critical history/fundamental blocks.

- [ ] **Step 2: Run RED**

Run: `cd backend && node --test test/forecast12m.test.mjs`

- [ ] **Step 3: Extend forecast implementation**

Preserve current scenario math but add quality/confidence, explicit `usedInputs`, at least two drivers where available and at least one risk where derivable. If `NICHT_BELASTBAR`, return `expectedChangePct`, `bearChangePct`, `bullChangePct` as `null`.

- [ ] **Step 4: Run tests**

Expected: PASS.

- [ ] **Step 5: Commit**

Commit message: `feat: add complete forecast confidence and scenarios`

---

### Task 6: Cache/Refresh-Policy gegen dauerhafte 15%-Zustände

**Files:**
- Modify: `backend/src/lib/radar.mjs`
- Modify: `backend/src/lib/analysisCache.mjs`
- Modify: `backend/src/lib/radarAnalysisCache.mjs`
- Modify: `backend/test/analysisRefreshPolicy.test.mjs`
- Modify: `backend/test/radarAnalysisCache.test.mjs`

**Interfaces:**
- Explicit refresh forces provider access.
- Missing/stale visible or portfolio items are refreshed before a low-quality snapshot becomes authoritative.

- [ ] **Step 1: Write failing cold-cache test**

Assert a radar detail request on empty cache invokes history/fundamental providers instead of returning only “wird im Hintergrund geladen”.

- [ ] **Step 2: Run RED**

Run: `cd backend && node --test test/analysisRefreshPolicy.test.mjs test/radarAnalysisCache.test.mjs`

- [ ] **Step 3: Implement stale-while-revalidate policy**

Normal list can use fresh analysis cache; explicit detail/refresh and portfolio-priority paths request missing provider data immediately. Do not fan out the entire universe on every opening.

- [ ] **Step 4: Run tests**

Expected: PASS.

- [ ] **Step 5: Commit**

Commit message: `fix: refresh missing analysis blocks instead of persisting low coverage`

---

### Task 7: Android API-Modelle für vollständige Analyse

**Files:**
- Modify: `android/app/src/main/java/de/tobias/investmentradar/ApiClient.kt`
- Modify: `android/app/src/main/java/de/tobias/investmentradar/RadarModels.kt`
- Create: `android/app/src/test/java/de/tobias/investmentradar/CompleteAnalysisModelTest.kt`

**Interfaces:**
- Android models expose backend `dataQuality`, `scoreBreakdown`, `forecast`, and user-safe diagnostics.

- [ ] **Step 1: Write failing JSON/model parsing tests**

Use a fixture with Apple-style complete analysis and assert scenario percentages, quality tier, missing blocks, score pillar reasons and fundamental metrics are preserved.

- [ ] **Step 2: Run RED**

Run: `cd android && ./gradlew testDebugUnitTest --tests '*CompleteAnalysisModelTest'`

- [ ] **Step 3: Add nullable model types**

Create/extend Kotlin data classes with nullable numeric fields; never default absent finance values to zero.

- [ ] **Step 4: Run test**

Expected: PASS.

- [ ] **Step 5: Commit**

Commit message: `feat: parse complete backend analysis models`

---

### Task 8: Radar- und Detailansicht vervollständigen

**Files:**
- Modify: `android/app/src/main/java/de/tobias/investmentradar/RadarScreen.kt`
- Modify: `android/app/src/main/java/de/tobias/investmentradar/InvestmentDetailScreen.kt`
- Modify: `android/tests/test-radar-2-ui.sh`
- Modify: `android/tests/test-investment-detail-ui.sh`
- Modify: `android/tests/test-forecast-ui.sh`

**Interfaces:**
- UI shows consistent analysis sections from the new model.

- [ ] **Step 1: Extend UI regression contracts first**

Require visible labels for `Datenabdeckung`, `Kurs`, `Historie`, `Fundamentals`, `Prognosequalität`, `Basis`, `Bull`, `Bear`, `Treiber`, `Risiken` and each score pillar.

- [ ] **Step 2: Run RED**

Run the three shell tests above; expect failure on missing UI contracts.

- [ ] **Step 3: Implement compact cards**

Show current values when present; show `Nicht verfügbar` rather than `0` when absent. For `NICHT_BELASTBAR`, hide numeric scenario targets and show missing input blocks.

- [ ] **Step 4: Run UI tests and JVM tests**

Run shell tests plus `cd android && ./gradlew testDebugUnitTest`.
Expected: PASS.

- [ ] **Step 5: Commit**

Commit message: `feat: show complete radar and detail analysis`

---

### Task 9: Depot und Alarmcenter auf dasselbe Analyseobjekt umstellen

**Files:**
- Modify: `android/app/src/main/java/de/tobias/investmentradar/PortfolioDashboard.kt`
- Modify: `android/app/src/main/java/de/tobias/investmentradar/AlertsScreen.kt`
- Modify: `android/app/src/main/java/de/tobias/investmentradar/DepotActionCenterMapper.kt`
- Modify: `android/app/src/test/java/de/tobias/investmentradar/DepotActionCenterMapperTest.kt`
- Modify: `android/tests/test-alert-center-ui.sh`
- Modify: `android/tests/test-portfolio-dashboard-ui.sh`

**Interfaces:**
- Depot action and alert copy consume the same `RadarItem`/analysis payload used by Radar/Detail.

- [ ] **Step 1: Write failing consistency tests**

Assert low-quality data can never produce simultaneous `KAUF BESTÄTIGT` and `UNVOLLSTÄNDIG`. Assert valid forecast scenarios appear in depot/alarm summary.

- [ ] **Step 2: Run RED**

Run the mapper JVM test and both shell contracts.

- [ ] **Step 3: Implement consistent mapping**

Use backend recommendation plus data-quality gate as authoritative. Reuse forecast and score breakdown instead of recomputing conflicting local approximations.

- [ ] **Step 4: Run tests**

Expected: PASS.

- [ ] **Step 5: Commit**

Commit message: `fix: keep depot and alerts consistent with analysis quality`

---

### Task 10: Referenzfälle Apple, Nel ASA und Samsung Electronics GDR

**Files:**
- Create: `backend/test/referenceAssets.test.mjs`
- Create: `android/app/src/test/java/de/tobias/investmentradar/ReferenceAssetPresentationTest.kt`
- Modify: `scripts/test-market-data-quality.sh`

**Interfaces:**
- Reference tests verify no fabricated values, provider fallback paths and coherent presentation.

- [ ] **Step 1: Add backend reference fixtures/tests**

Test Apple with full US fallback availability, Nel ASA with non-US symbol/fallback path, and Samsung Electronics GDR with partial-provider behavior. Assert missing values remain null and quality reflects reality.

- [ ] **Step 2: Run RED, then make only necessary fixes**

Run: `cd backend && node --test test/referenceAssets.test.mjs`

- [ ] **Step 3: Add Android presentation tests**

Ensure the three cases render complete, partial and non-belastbar states correctly without zero placeholders.

- [ ] **Step 4: Run backend + Android reference tests**

Expected: PASS.

- [ ] **Step 5: Commit**

Commit message: `test: cover Apple Nel and Samsung analysis quality`

---

### Task 11: Vollständige Verifikation, Versionierung und Release-Vorbereitung

**Files:**
- Modify only if release contract requires: `android/app/build.gradle.kts`
- Modify matching version contract tests if version is bumped.

**Interfaces:**
- Produces a releasable branch with green backend, Android, regression and build workflows.

- [ ] **Step 1: Run complete backend suite**

Run: `cd backend && npm test`
Expected: all tests PASS.

- [ ] **Step 2: Run complete Android JVM suite**

Run: `cd android && ./gradlew testDebugUnitTest`
Expected: PASS.

- [ ] **Step 3: Run all relevant shell regression tests**

At minimum: forecast, analysis-v2, radar, investment-detail, portfolio dashboard, alert center, market-data-quality, release-backend-gate.
Expected: PASS.

- [ ] **Step 4: Bump app version only once implementation is fully green**

Increment from 2.4.6 / 66 to the next release version/code and update exact version contracts in the same commit.

- [ ] **Step 5: Push branch and verify GitHub Actions**

Require backend tests, Android unit tests, contract tests and signed release APK workflow to complete successfully.

- [ ] **Step 6: Run live backend smoke checks**

Verify health, radar detail and the three reference assets against deployed backend. Confirm data coverage and forecast fields are populated where providers genuinely supply data.

- [ ] **Step 7: Commit any release-only version changes**

Commit message: `chore: prepare complete analysis release`

---

## Self-Review

- Spec coverage: Provider fallbacks, normalization, quality gates, score breakdown, forecast, refresh policy, Android UI, depot/alarm consistency, performance-sensitive cache behavior and reference assets are each assigned to explicit tasks.
- Placeholder scan: No TBD/TODO/“implement later” placeholders remain.
- Type consistency: `dataQuality`, `scoreBreakdown`, `forecast`, `missingBlocks` and `criticalConflicts` are defined once and reused across backend and Android tasks.
- Safety rule: All computed recommendations remain advisory; no automatic trading action is introduced.
