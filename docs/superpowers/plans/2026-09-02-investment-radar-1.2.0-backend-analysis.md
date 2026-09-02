# Investment Radar 1.2.0 Backend Analysis Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the partly preconfigured six-asset recommendation backend with a cached, data-driven analysis service that scores about 40 configured stocks/ETFs across quality, valuation, growth, momentum and risk while staying backward compatible with Android 1.1.29.

**Architecture:** Keep quotes/FX in their existing modules, add focused history, fundamentals, scoring and analysis-cache modules, and make `dashboard.mjs` orchestrate them. Scoring is pure and deterministic; provider adapters normalize into provider-independent data objects. Missing data reduces `coverage` and never produces invented fundamentals.

**Tech Stack:** Node.js 22 ESM, Azure Functions v4, Twelve Data HTTP APIs, existing Azure Blob cache utilities, Node built-in `node:test`/`assert`.

**Spec:** `docs/superpowers/specs/2026-09-02-investment-radar-1.2.0-design.md`

## Global Constraints

- Android target remains backward-compatible with existing 1.1.29 dashboard identity/quote fields.
- Backend target version is `1.2.0`.
- No automatic trading or brokerage login.
- Missing fundamentals/history stay `null`; never fabricate values.
- Quote cache target freshness: 5 minutes.
- Historical-series cache target freshness: 6 hours.
- Fundamentals cache target freshness: 24 hours.
- Coverage below 50 must never produce `BUY`.
- Existing manual sell/review overrides remain supported.
- About 40 assets are configured; unrestricted symbol search is out of scope.

---

### Task 1: Pure scoring model and recommendation thresholds

**Files:**
- Create: `backend/src/lib/scoring.mjs`
- Create: `backend/test/scoring.test.mjs`
- Modify: `backend/package.json`

**Interfaces:**
- Consumes: normalized item metadata, normalized fundamentals, normalized momentum and quote/risk fields.
- Produces: `scoreInvestment({ item, fundamentals, momentum, quote }) -> { scoreTotal, scoreQuality, scoreValuation, scoreGrowth, scoreMomentum, scoreRisk, coverage, recommendation, recommendationReasons }`.
- Produces: `recommendationFromScore({ scoreTotal, coverage, hardReview }) -> "BUY" | "WATCH" | "NO_BUY" | "REVIEW"`.

- [ ] **Step 1: Write failing scoring tests**

Create tests that prove weight renormalization, coverage gating and separate stock/ETF profiles:

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

test("missing components are renormalized without inventing values", () => {
  const result = scoreInvestment({
    item: { id: "x", type: "AKTIE", risk: 2 },
    fundamentals: { qualityScore: 80, valuationScore: null, growthScore: 70, coveragePct: 66.7 },
    momentum: { score: 60, coveragePct: 100 },
    quote: { percentChange: 1.0 }
  });
  assert.equal(result.scoreValuation, null);
  assert.ok(result.coverage < 100);
  assert.ok(Number.isInteger(result.scoreTotal));
});
```

- [ ] **Step 2: Run the new test and verify RED**

Run:

```bash
cd backend
npm test -- --test-name-pattern="coverage below 50|hard review|missing components"
```

Expected: FAIL because `scoring.mjs` does not exist.

- [ ] **Step 3: Implement the pure scoring module**

Use explicit weights and one normalization helper:

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

`scoreInvestment` must clamp component scores to 0–100, keep missing components `null`, renormalize only across available weighted components, compute `coverage` from expected inputs/components, and return at most three concise reasons ordered by strongest positive/negative contribution.

- [ ] **Step 4: Run all backend tests**

```bash
cd backend
npm test
npm run check
```

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add backend/src/lib/scoring.mjs backend/test/scoring.test.mjs backend/package.json
git commit -m "feat: add data-driven investment scoring"
```

---

### Task 2: Historical series and multi-horizon momentum

**Files:**
- Create: `backend/src/lib/history.mjs`
- Create: `backend/src/lib/historySupport.mjs`
- Create: `backend/test/history.test.mjs`
- Modify: `backend/package.json`

**Interfaces:**
- Produces: `calculateMomentum(points, now?) -> { d1, m1, m3, m6, m12, score, coveragePct }`.
- Produces: `loadHistory(items) -> Map<itemId, momentumResult>`.
- History points use `{ time: number, close: number }` sorted ascending.

- [ ] **Step 1: Write failing deterministic momentum tests**

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
  { time: now - 1 * DAY, close: 119 },
  { time: now, close: 120 }
];

test("momentum exposes 1D 1M 3M 6M 12M returns", () => {
  const result = calculateMomentum(points, now);
  assert.ok(result.d1 > 0);
  assert.ok(result.m1 > 0);
  assert.ok(result.m3 > 0);
  assert.ok(result.m6 > 0);
  assert.ok(result.m12 > 0);
  assert.equal(result.coveragePct, 100);
});
```

- [ ] **Step 2: Run test and verify RED**

```bash
cd backend
node --test test/history.test.mjs
```

Expected: FAIL because `historySupport.mjs` is missing.

- [ ] **Step 3: Implement history calculations and provider adapter**

`calculateMomentum` must choose the closest available close at or before target ages (1 day, 30, 91, 182, 365 days), calculate percentage returns against the latest close, and weight momentum score approximately `1D 5% / 1M 15% / 3M 30% / 6M 30% / 12M 20%` across available horizons.

`loadHistory(items)` must use Twelve Data `time_series` when `TWELVE_DATA_API_KEY` exists, request enough daily observations for 12 months, limit concurrency to avoid provider bursts, and return missing/null horizon values on provider failure rather than throwing the entire dashboard.

- [ ] **Step 4: Add 6-hour persistent cache behavior**

Use an `history-cache.json` blob key following the existing cache module pattern. Cache each item independently with `fetchedAt` and raw normalized daily points. A cached series younger than 6 hours must be reused without a provider call; an older series can be used as marked stale if the refresh fails.

- [ ] **Step 5: Run tests and checks**

```bash
cd backend
npm test
npm run check
```

Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add backend/src/lib/history.mjs backend/src/lib/historySupport.mjs backend/test/history.test.mjs backend/package.json
git commit -m "feat: add multi-horizon momentum analysis"
```

---

### Task 3: Fundamental normalization, scoring inputs and 24-hour cache

**Files:**
- Create: `backend/src/lib/fundamentals.mjs`
- Create: `backend/src/lib/fundamentalSupport.mjs`
- Create: `backend/test/fundamentals.test.mjs`
- Modify: `backend/package.json`

**Interfaces:**
- Produces: `normalizeFundamentals(raw) -> { metrics, qualityScore, valuationScore, growthScore, coveragePct, source, stale, asOf }`.
- Produces: `loadFundamentals(items) -> Map<itemId, normalizedFundamentals>`.

- [ ] **Step 1: Write failing normalization tests**

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

test("strong profitability and moderate leverage improve quality score", () => {
  const result = normalizeFundamentals({ operatingMargin: 0.30, roe: 0.25, debtToEquity: 0.4, pe: 24, revenueGrowth: 0.12, epsGrowth: 0.14 });
  assert.ok(result.qualityScore >= 70);
});
```

- [ ] **Step 2: Run test and verify RED**

```bash
cd backend
node --test test/fundamentals.test.mjs
```

Expected: FAIL because the support module is missing.

- [ ] **Step 3: Implement provider-independent normalization**

Normalize only metrics actually returned by the provider: P/E, price/sales or EV/EBITDA, FCF yield/cash-flow proxy, revenue growth, EPS growth, operating/net margin, ROE/ROIC and leverage. Score each metric through explicit bounded ranges; do not substitute sector medians unless they are explicitly supplied in configuration in a later release.

- [ ] **Step 4: Implement provider adapter and 24-hour cache**

`loadFundamentals(items)` must skip ETF company-fundamental calls unless an ETF-specific endpoint is explicitly available. Provider authorization/plan errors become a normalized missing-data result and are cached briefly enough to prevent request storms; valid fundamentals cache for 24 hours and stale last-known valid values are marked `stale: true` when used after a failed refresh.

- [ ] **Step 5: Run tests/checks and commit**

```bash
cd backend
npm test
npm run check
git add backend/src/lib/fundamentals.mjs backend/src/lib/fundamentalSupport.mjs backend/test/fundamentals.test.mjs backend/package.json
git commit -m "feat: add cached fundamental analysis"
```

---

### Task 4: Configuration-driven 40-asset universe and dynamic compatibility fields

**Files:**
- Modify: `backend/data/investments.json`
- Modify: `backend/src/lib/config.mjs`
- Create: `backend/src/lib/compatibility.mjs`
- Create: `backend/test/configCompatibility.test.mjs`

**Interfaces:**
- `loadConfig()` returns validated asset metadata without requiring static allocations to sum to 100.
- `buildCompatibilityAllocations(analyzedItems, budget=100) -> Map<itemId, integerAmount>` provides old-client `allocation` values from current BUY candidates.

- [ ] **Step 1: Write failing compatibility tests**

```js
import test from "node:test";
import assert from "node:assert/strict";
import { buildCompatibilityAllocations } from "../src/lib/compatibility.mjs";

test("compatibility allocations sum to 100 across BUY candidates", () => {
  const items = [
    { id: "a", recommendation: "BUY", scoreTotal: 90 },
    { id: "b", recommendation: "BUY", scoreTotal: 80 },
    { id: "c", recommendation: "WATCH", scoreTotal: 74 }
  ];
  const result = buildCompatibilityAllocations(items, 100);
  assert.equal([...result.values()].reduce((a, b) => a + b, 0), 100);
  assert.equal(result.get("c"), 0);
});
```

- [ ] **Step 2: Run and verify RED**

```bash
cd backend
node --test test/configCompatibility.test.mjs
```

- [ ] **Step 3: Replace allocation-sum validation with identity/schema validation**

`config.mjs` must require unique `id`, nonblank `ticker`, `type` in `AKTIE|ETF`, `risk` in 1–5, and valid `isin` when present. `allocation` and static `status` become optional compatibility/config override fields rather than the source of recommendations.

- [ ] **Step 4: Expand the curated universe**

Add about 40 liquid, diversified entries covering core ETFs, factor ETFs and large quality equities across technology, healthcare, financials, consumer, industrials and communications. Every item must have explicit `marketSymbol`, `ticker`, `type`, `risk`, `isin` where known, Trade Republic display name, and Yahoo fallback symbol where the existing provider fallback benefits from it.

- [ ] **Step 5: Implement compatibility allocation**

Use positive excess score above the BUY threshold as weights, allocate integer euros with largest-remainder distribution, and return zero for non-BUY assets. This keeps Android 1.1.29 functional while 1.2.0 Android switches to portfolio-aware local allocation.

- [ ] **Step 6: Run tests/checks and commit**

```bash
cd backend
npm test
npm run check
git add backend/data/investments.json backend/src/lib/config.mjs backend/src/lib/compatibility.mjs backend/test/configCompatibility.test.mjs
git commit -m "feat: expand curated investment universe"
```

---

### Task 5: Dashboard analysis orchestration and additive API schema

**Files:**
- Modify: `backend/src/lib/dashboard.mjs`
- Create: `backend/test/dashboardAnalysis.test.mjs`
- Modify: `backend/src/functions/dashboard.mjs`

**Interfaces:**
- Dashboard keeps existing fields and adds score/fundamental/momentum fields from the spec.
- Old `status` is returned as the German compatibility alias of objective `recommendation`.
- Old `allocation` is returned from `buildCompatibilityAllocations`.

- [ ] **Step 1: Write failing schema compatibility test**

Create a deterministic unit seam in `buildDashboard` by allowing injected loaders in tests. Assert one analyzed item includes both old and new fields:

```js
assert.equal(item.status, "KAUFEN");
assert.equal(item.recommendation, "BUY");
assert.equal(typeof item.scoreTotal, "number");
assert.equal(typeof item.coverage, "number");
assert.ok("momentum" in item);
assert.ok("fundamentals" in item);
assert.ok("recommendationReasons" in item);
```

- [ ] **Step 2: Run and verify RED**

```bash
cd backend
node --test test/dashboardAnalysis.test.mjs
```

- [ ] **Step 3: Orchestrate loaders without all-or-nothing failure**

Load config, quotes, FX, history and fundamentals; score each asset independently. A history/fundamental failure for one asset must not reject the dashboard promise. Build `topPickId` dynamically from the highest eligible BUY score, falling back to the best WATCH if no BUY exists.

- [ ] **Step 4: Preserve additive compatibility schema**

Keep `id,type,name,ticker,isin,tradeRepublicName,status,allocation,risk,price,priceEur,currency,fx*,percentChange,marketOpen,data*`. Add the V2 fields exactly as defined by the spec.

- [ ] **Step 5: Run tests/checks and commit**

```bash
cd backend
npm test
npm run check
git add backend/src/lib/dashboard.mjs backend/src/functions/dashboard.mjs backend/test/dashboardAnalysis.test.mjs
git commit -m "feat: serve scored dashboard analysis"
```

---

### Task 6: Sell/review logic V2 and score-deterioration state

**Files:**
- Modify: `backend/src/lib/signals.mjs`
- Modify: `backend/src/lib/state.mjs`
- Modify: `backend/src/functions/marketWatch.mjs`
- Create: `backend/test/signalsV2.test.mjs`

**Interfaces:**
- `evaluateSignals(items, quotes, analyses, previousScores)` returns deduplicated alert candidates.
- State gains `lastScores: { [itemId]: { scoreTotal, analysisAsOf } }` while retaining `recent` and `activeFingerprints`.

- [ ] **Step 1: Write failing deterioration tests**

```js
test("held-quality analysis can emit REVIEW after 15 point deterioration", () => {
  const alerts = evaluateSignals(
    [{ id: "msft", name: "Microsoft", alertStatus: "", reviewDrop1dPct: 7, hardReviewBelow: 300 }],
    new Map([["msft", { price: 450, percentChange: -1, currency: "USD" }]]),
    new Map([["msft", { scoreTotal: 61, momentum: { m3: -12, m6: -15 }, fundamentals: {} }]]),
    { msft: { scoreTotal: 78 } }
  );
  assert.ok(alerts.some((a) => a.level === "REVIEW"));
});
```

Also test manual override, absolute threshold, exceptional 1D loss, multi-horizon breakdown and fingerprint re-arming behavior.

- [ ] **Step 2: Run and verify RED**

```bash
cd backend
node --test test/signalsV2.test.mjs
```

- [ ] **Step 3: Implement V2 triggers**

Order triggers by severity. `REVIEW` is not an automatic sale. Reasons must explicitly identify score drop, trend breakdown, configured price threshold, daily shock, fundamental deterioration or manual override.

- [ ] **Step 4: Persist latest valid scores in marketWatch**

After processing signals, update `state.lastScores` only for analyses with finite score and acceptable coverage. Keep existing active-fingerprint clearing behavior so a condition can alert again only after it disappears and later recurs.

- [ ] **Step 5: Run tests/checks and commit**

```bash
cd backend
npm test
npm run check
git add backend/src/lib/signals.mjs backend/src/lib/state.mjs backend/src/functions/marketWatch.mjs backend/test/signalsV2.test.mjs
git commit -m "feat: add deterioration-based review signals"
```

---

### Task 7: Backend 1.2.0 version, health diagnostics and CI gate

**Files:**
- Modify: `backend/package.json`
- Modify: `backend/src/functions/health.mjs`
- Modify: `.github/workflows/backend-deploy.yml`
- Modify: `backend/test/healthDiagnostics.test.mjs`

**Interfaces:**
- `/api/health` returns `backendVersion: "1.2.0"` plus provider configuration diagnostics.

- [ ] **Step 1: Update health test first**

Assert `backendVersion === "1.2.0"`, `marketDataConfigured` remains boolean, and health stays HTTP 200 even when optional fundamental capability is unavailable.

- [ ] **Step 2: Run test and verify RED**

```bash
cd backend
npm test -- --test-name-pattern="health"
```

Expected: FAIL because health still reports 1.1.27.

- [ ] **Step 3: Update version/check script**

Set package version to `1.2.0`, health version to `1.2.0`, and add all new `.mjs` files to `npm run check`.

- [ ] **Step 4: Strengthen backend workflow**

Ensure workflow runs, in order:

```bash
npm ci
npm run check
npm test
```

before deployment. Branch runs must test but must not deploy production unless the workflow's existing production branch condition is satisfied.

- [ ] **Step 5: Run full backend verification**

```bash
cd backend
npm ci
npm run check
npm test
```

Expected: all PASS.

- [ ] **Step 6: Commit**

```bash
git add backend/package.json backend/src/functions/health.mjs backend/test/healthDiagnostics.test.mjs .github/workflows/backend-deploy.yml
git commit -m "chore: prepare backend 1.2.0 verification"
```
