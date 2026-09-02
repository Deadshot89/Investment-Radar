# Investment Radar 1.2.0 Backend Analysis Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the partly preconfigured six-asset recommendation backend with a cached, data-driven analysis service that scores a curated 40-asset universe across quality, valuation, growth, momentum and risk while staying backward compatible with Android 1.1.29.

**Architecture:** Keep quotes/FX in their existing modules, add focused history, fundamentals, scoring and analysis-cache modules, and make `dashboard.mjs` orchestrate them. Scoring is pure and deterministic; provider adapters normalize into provider-independent objects. Missing data reduces `coverage` and never produces invented fundamentals.

**Tech Stack:** Node.js 22 ESM, Azure Functions v4, Twelve Data HTTP APIs, existing Azure Blob cache patterns, Node built-in `node:test`/`assert`.

**Spec:** `docs/superpowers/specs/2026-09-02-investment-radar-1.2.0-design.md`

## Global Constraints

- Backend target version is `1.2.0`.
- Existing Android 1.1.29 identity/quote fields stay present.
- No automatic trading or brokerage login.
- Missing fundamentals/history stay `null`; never fabricate values.
- Quote cache target freshness: 5 minutes.
- Historical-series cache target freshness: 6 hours.
- Fundamentals cache target freshness: 24 hours.
- Coverage below 50 must never produce `BUY`.
- Existing manual sell/review overrides remain supported.
- The universe is curated/configuration-driven; unrestricted symbol search is out of scope.

---

### Task 1: Pure scoring model and recommendation thresholds

**Files:**
- Create: `backend/src/lib/scoring.mjs`
- Create: `backend/test/scoring.test.mjs`
- Modify: `backend/package.json`

**Interfaces:**
- `scoreInvestment({ item, fundamentals, momentum, quote }) -> { scoreTotal, scoreQuality, scoreValuation, scoreGrowth, scoreMomentum, scoreRisk, coverage, recommendation, recommendationReasons }`.
- `recommendationFromScore({ scoreTotal, coverage, hardReview }) -> "BUY" | "WATCH" | "NO_BUY" | "REVIEW"`.

- [ ] **Step 1: Write failing scoring tests**

```js
import test from "node:test";
import assert from "node:assert/strict";
import { recommendationFromScore, scoreInvestment } from "../src/lib/scoring.mjs";

test("coverage below 50 never returns BUY", () => {
  assert.equal(recommendationFromScore({ scoreTotal: 90, coverage: 49, hardReview: false }), "WATCH");
});

test("hard review overrides a high score", () => {
  assert.equal(recommendationFromScore({ scoreTotal: 92, coverage: 100, hardReview: true }), "REVIEW");
});

test("missing components are renormalized without invented values", () => {
  const result = scoreInvestment({
    item: { id: "x", type: "AKTIE", risk: 2 },
    fundamentals: { qualityScore: 80, valuationScore: null, growthScore: 70, coveragePct: 66 },
    momentum: { score: 60, coveragePct: 100 },
    quote: { percentChange: 1.0 }
  });
  assert.equal(result.scoreValuation, null);
  assert.ok(result.coverage < 100);
  assert.ok(Number.isInteger(result.scoreTotal));
});
```

- [ ] **Step 2: Run RED**

```bash
cd backend
node --test test/scoring.test.mjs
```

Expected: FAIL because `scoring.mjs` does not exist.

- [ ] **Step 3: Implement stock/ETF profiles**

```js
const STOCK_WEIGHTS = { quality: 25, valuation: 20, growth: 20, momentum: 20, risk: 15 };
const ETF_WEIGHTS = { quality: 30, valuation: 10, growth: 10, momentum: 30, risk: 20 };

export function recommendationFromScore({ scoreTotal, coverage, hardReview }) {
  if (hardReview) return "REVIEW";
  if (coverage < 50) return scoreTotal < 55 ? "NO_BUY" : "WATCH";
  if (scoreTotal >= 75) return "BUY";
  if (scoreTotal >= 55) return "WATCH";
  return "NO_BUY";
}
```

For ETFs, `item.etfStructureScore`, `item.etfValuationProxyScore` and `item.etfGrowthProxyScore` are used only when explicitly configured; otherwise those components remain null and weights renormalize. Risk score derives from configured 1–5 risk plus available volatility/momentum stress signals, clamped 0–100. Return at most three concise reasons ordered by strongest contribution.

- [ ] **Step 4: Run GREEN and commit**

```bash
cd backend
npm test
npm run check
git add src/lib/scoring.mjs test/scoring.test.mjs package.json
git commit -m "feat: add data-driven investment scoring"
```

---

### Task 2: Historical series and 1D/1M/3M/6M/12M momentum

**Files:**
- Create: `backend/src/lib/history.mjs`
- Create: `backend/src/lib/historySupport.mjs`
- Create: `backend/test/history.test.mjs`
- Modify: `backend/package.json`

**Interfaces:**
- History points: `{ time: number, close: number }`, ascending.
- `calculateMomentum(points, now?) -> { d1, m1, m3, m6, m12, score, coveragePct }`.
- `loadHistory(items) -> Map<itemId, momentumResult>`.

- [ ] **Step 1: Write failing deterministic test**

```js
import test from "node:test";
import assert from "node:assert/strict";
import { calculateMomentum } from "../src/lib/historySupport.mjs";

const DAY = 86_400_000;
const now = Date.UTC(2026, 8, 2);
const points = [
  { time: now - 370 * DAY, close: 80 },
  { time: now - 185 * DAY, close: 90 },
  { time: now - 95 * DAY, close: 100 },
  { time: now - 32 * DAY, close: 110 },
  { time: now - DAY, close: 119 },
  { time: now, close: 120 }
];

test("momentum exposes all five horizons", () => {
  const result = calculateMomentum(points, now);
  assert.ok(result.d1 > 0 && result.m1 > 0 && result.m3 > 0 && result.m6 > 0 && result.m12 > 0);
  assert.equal(result.coveragePct, 100);
});
```

- [ ] **Step 2: Run RED**

```bash
cd backend
node --test test/history.test.mjs
```

- [ ] **Step 3: Implement calculation**

Choose the closest close at or before target ages 1, 30, 91, 182 and 365 days. Weight available normalized horizon signals approximately `1D 5% / 1M 15% / 3M 30% / 6M 30% / 12M 20%`; missing horizons reduce coverage instead of becoming zero returns.

- [ ] **Step 4: Implement Twelve Data history adapter and 6-hour cache**

Use Twelve Data `time_series`, daily interval and enough output for 12 months. Limit concurrent requests. Cache each item independently with `fetchedAt` and normalized points using the existing persistent blob-storage pattern. Younger-than-6h cache skips provider call; stale last-known data can be used only with `stale: true` when refresh fails.

- [ ] **Step 5: Run tests/checks and commit**

```bash
cd backend
npm test
npm run check
git add src/lib/history.mjs src/lib/historySupport.mjs test/history.test.mjs package.json
git commit -m "feat: add multi-horizon momentum analysis"
```

---

### Task 3: Fundamental normalization and 24-hour cache

**Files:**
- Create: `backend/src/lib/fundamentals.mjs`
- Create: `backend/src/lib/fundamentalSupport.mjs`
- Create: `backend/test/fundamentals.test.mjs`
- Modify: `backend/package.json`

**Interfaces:**
- `normalizeFundamentals(raw) -> { metrics, qualityScore, valuationScore, growthScore, coveragePct, source, stale, asOf }`.
- `loadFundamentals(items) -> Map<itemId, normalizedFundamentals>`.

- [ ] **Step 1: Write failing tests**

```js
import test from "node:test";
import assert from "node:assert/strict";
import { normalizeFundamentals } from "../src/lib/fundamentalSupport.mjs";

test("missing provider fields remain null", () => {
  const result = normalizeFundamentals({ pe: 22, revenueGrowth: null, debtToEquity: undefined });
  assert.equal(result.metrics.revenueGrowth, null);
  assert.equal(result.metrics.debtToEquity, null);
  assert.ok(result.coveragePct < 100);
});

test("strong profitability and moderate leverage improve quality", () => {
  const result = normalizeFundamentals({ operatingMargin: 0.30, roe: 0.25, debtToEquity: 0.4, pe: 24, revenueGrowth: 0.12, epsGrowth: 0.14 });
  assert.ok(result.qualityScore >= 70);
});
```

- [ ] **Step 2: Run RED**

```bash
cd backend
node --test test/fundamentals.test.mjs
```

- [ ] **Step 3: Implement provider-independent metric scoring**

Normalize only returned values: forward/trailing P/E, price/sales or EV/EBITDA, free-cash-flow yield/cash-flow proxy, revenue growth, EPS growth, operating/net margin, ROE/ROIC and leverage. Use bounded deterministic ranges. Do not substitute sector medians or synthetic values.

- [ ] **Step 4: Implement provider adapter and cache**

Use Twelve Data fundamental endpoints available to the configured plan. Authorization/plan errors produce a normalized unavailable result rather than dashboard failure. Valid fundamentals cache 24 hours. ETF company-fundamental calls are skipped unless a valid ETF endpoint is explicitly supported; ETF static proxy scores come only from config.

- [ ] **Step 5: Run GREEN and commit**

```bash
cd backend
npm test
npm run check
git add src/lib/fundamentals.mjs src/lib/fundamentalSupport.mjs test/fundamentals.test.mjs package.json
git commit -m "feat: add cached fundamental analysis"
```

---

### Task 4: Exact curated 40-asset universe and dynamic compatibility allocations

**Files:**
- Modify: `backend/data/investments.json`
- Modify: `backend/src/lib/config.mjs`
- Create: `backend/src/lib/compatibility.mjs`
- Create: `backend/test/configCompatibility.test.mjs`

**Interfaces:**
- `loadConfig()` validates metadata without requiring static allocations to total 100.
- `buildCompatibilityAllocations(analyzedItems, budget=100) -> Map<itemId, integerAmount>`.

- [ ] **Step 1: Write failing compatibility allocation test**

```js
import test from "node:test";
import assert from "node:assert/strict";
import { buildCompatibilityAllocations } from "../src/lib/compatibility.mjs";

test("compatibility allocations sum to budget across BUY candidates", () => {
  const result = buildCompatibilityAllocations([
    { id: "a", recommendation: "BUY", scoreTotal: 90 },
    { id: "b", recommendation: "BUY", scoreTotal: 80 },
    { id: "c", recommendation: "WATCH", scoreTotal: 74 }
  ], 100);
  assert.equal([...result.values()].reduce((a, b) => a + b, 0), 100);
  assert.equal(result.get("c"), 0);
});
```

- [ ] **Step 2: Run RED**

```bash
cd backend
node --test test/configCompatibility.test.mjs
```

- [ ] **Step 3: Replace allocation-sum validation with schema validation**

Require unique `id`, nonblank ticker/market symbol, `type` in `AKTIE|ETF`, risk 1–5, and syntactically valid ISIN when present. `allocation`/static `status` are no longer the decision source.

- [ ] **Step 4: Configure this exact 40-asset candidate universe**

Retain the six current assets and add the following, validating market symbols/Yahoo fallbacks/ISINs against provider data before commit:

```text
ETFs/current: SPYI, IS3S, IS3Q
Current stocks: MSFT, GOOGL, V
Technology/communications: AAPL, AMZN, META, NVDA, AVGO, ASML, TSM, SAP, ORCL, ADBE, CRM
Healthcare: LLY, NVO, JNJ, ABBV, MRK, UNH
Financials: JPM, MA, BRK.B, BLK
Consumer: COST, WMT, PG, KO, PEP, MCD, NKE
Industrials/materials: CAT, LIN, SIE.DE
Energy/utilities: XOM, CVX, NEE
```

That list contains exactly 40 configured assets. If provider validation shows a listed market symbol format is unsupported, fix only the provider symbol representation; do not silently replace the company with a different asset.

For SPYI/IS3S/IS3Q, add explicit ETF structure/proxy scores only where justified by configured metadata; otherwise leave those proxy fields absent/null.

- [ ] **Step 5: Implement old-client compatibility allocation**

Weight only BUY assets by positive score excess above 74, then use largest-remainder integer distribution. Non-BUY assets receive zero. This is only for Android 1.1.29 compatibility; Android 1.2.0 uses its local portfolio overlay.

- [ ] **Step 6: Run tests/checks and commit**

```bash
cd backend
npm test
npm run check
git add data/investments.json src/lib/config.mjs src/lib/compatibility.mjs test/configCompatibility.test.mjs
git commit -m "feat: expand curated investment universe"
```

---

### Task 5: Dashboard analysis orchestration and additive schema

**Files:**
- Modify: `backend/src/lib/dashboard.mjs`
- Create: `backend/test/dashboardAnalysis.test.mjs`
- Modify: `backend/src/functions/dashboard.mjs`

**Interfaces:**
- Existing identity/quote fields remain.
- Adds `scoreTotal`, five component scores, `coverage`, `recommendation`, `recommendationReasons`, `momentum`, `fundamentals`, `analysisAsOf`.
- Old `status` is German compatibility alias; old `allocation` comes from compatibility allocation.

- [ ] **Step 1: Write failing additive-schema test**

Inject deterministic loaders into `buildDashboard` for tests and assert:

```js
assert.equal(item.status, "KAUFEN");
assert.equal(item.recommendation, "BUY");
assert.equal(typeof item.scoreTotal, "number");
assert.equal(typeof item.coverage, "number");
assert.ok("momentum" in item);
assert.ok("fundamentals" in item);
assert.ok(Array.isArray(item.recommendationReasons));
```

- [ ] **Step 2: Run RED**

```bash
cd backend
node --test test/dashboardAnalysis.test.mjs
```

- [ ] **Step 3: Orchestrate independently failing data sources**

Load config, quotes, FX, history and fundamentals. Analyze each asset independently so one asset/provider failure does not reject the whole dashboard. Compute `topPickId` from highest eligible BUY score, otherwise best WATCH.

- [ ] **Step 4: Preserve old fields and add V2 fields**

Keep `id,type,name,ticker,isin,tradeRepublicName,status,allocation,risk,price,priceEur,currency,fx*,percentChange,marketOpen,data*` unchanged in type/meaning where possible. Add V2 fields without removing any old required field.

- [ ] **Step 5: Run GREEN and commit**

```bash
cd backend
npm test
npm run check
git add src/lib/dashboard.mjs src/functions/dashboard.mjs test/dashboardAnalysis.test.mjs
git commit -m "feat: serve scored dashboard analysis"
```

---

### Task 6: Review/sell logic V2 plus BUY transition alerts

**Files:**
- Modify: `backend/src/lib/signals.mjs`
- Modify: `backend/src/lib/state.mjs`
- Modify: `backend/src/functions/marketWatch.mjs`
- Create: `backend/test/signalsV2.test.mjs`

**Interfaces:**
- `evaluateSignals(items, quotes, analyses, previousState) -> SignalAlert[]`.
- State retains `recent`/`activeFingerprints` and adds `lastScores` and `lastRecommendations`.

- [ ] **Step 1: Write failing deterioration and BUY-transition tests**

```js
import test from "node:test";
import assert from "node:assert/strict";
import { evaluateSignals } from "../src/lib/signals.mjs";

test("15 point deterioration can emit REVIEW", () => {
  const alerts = evaluateSignals(
    [{ id: "msft", name: "Microsoft", reviewDrop1dPct: 7, hardReviewBelow: 300 }],
    new Map([["msft", { price: 450, percentChange: -1, currency: "USD" }]]),
    new Map([["msft", { scoreTotal: 61, recommendation: "WATCH", coverage: 90, momentum: { m3: -12, m6: -15 } }]]),
    { lastScores: { msft: { scoreTotal: 78 } }, lastRecommendations: { msft: "BUY" } }
  );
  assert.ok(alerts.some((a) => a.level === "REVIEW"));
});

test("new BUY transition emits one BUY opportunity", () => {
  const alerts = evaluateSignals(
    [{ id: "msft", name: "Microsoft", reviewDrop1dPct: 7 }],
    new Map([["msft", { price: 450, percentChange: 1, currency: "USD" }]]),
    new Map([["msft", { scoreTotal: 82, recommendation: "BUY", coverage: 90, momentum: {} }]]),
    { lastScores: {}, lastRecommendations: { msft: "WATCH" } }
  );
  assert.ok(alerts.some((a) => a.level === "BUY"));
});
```

Also test manual override, absolute threshold, exceptional 1D loss, multi-horizon breakdown and no BUY flood when previous recommendation is absent on first migration run.

- [ ] **Step 2: Run RED**

```bash
cd backend
node --test test/signalsV2.test.mjs
```

- [ ] **Step 3: Implement trigger ordering**

Manual SELL/REVIEW and hard price thresholds have highest severity. Add score deterioration >=15, low held-asset score floor, multi-horizon momentum breakdown and severe fresh fundamental deterioration. `REVIEW` remains a prompt to inspect the thesis, not an automatic sale.

Emit `BUY` only when a previous known recommendation exists and changes from non-BUY to BUY with coverage >=70. This avoids mass BUY pushes on the first 1.2.0 run.

- [ ] **Step 4: Persist score/recommendation snapshots**

After each successful marketWatch analysis, save finite acceptable score snapshots and current recommendations. Keep active-fingerprint clearing behavior so identical conditions re-arm only after disappearing.

- [ ] **Step 5: Run GREEN and commit**

```bash
cd backend
npm test
npm run check
git add src/lib/signals.mjs src/lib/state.mjs src/functions/marketWatch.mjs test/signalsV2.test.mjs
git commit -m "feat: add v2 investment alerts"
```

---

### Task 7: Backend 1.2.0 health/version and CI gate

**Files:**
- Modify: `backend/package.json`
- Modify: `backend/src/functions/health.mjs`
- Modify: `backend/test/healthDiagnostics.test.mjs`
- Modify: `.github/workflows/backend-deploy.yml`

**Interfaces:**
- `/api/health` reports `backendVersion: "1.2.0"` while optional fundamental capability limitations do not make health falsely red if fallback works.

- [ ] **Step 1: Update health test first**

Assert backend version 1.2.0, market-data configuration remains boolean and health is 200 without optional fundamental entitlement.

- [ ] **Step 2: Run RED**

```bash
cd backend
npm test -- --test-name-pattern="health"
```

- [ ] **Step 3: Update package/check script and health version**

Set package version and health version to 1.2.0. Add every new `.mjs` module to `npm run check`.

- [ ] **Step 4: Strengthen backend workflow gate**

Before deployment, workflow must run:

```bash
npm ci
npm run check
npm test
```

Feature-branch runs test only; production deploy remains guarded to the existing production branch condition.

- [ ] **Step 5: Run full backend verification and commit**

```bash
cd backend
npm ci
npm run check
npm test
git add package.json src/functions/health.mjs test/healthDiagnostics.test.mjs ../.github/workflows/backend-deploy.yml
git commit -m "chore: prepare backend 1.2.0 verification"
```
