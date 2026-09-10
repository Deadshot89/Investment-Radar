import test from "node:test";
import assert from "node:assert/strict";
import { normalizeFundamentals } from "../src/lib/fundamentalSupport.mjs";
import { mergeFundamentalSources } from "../src/lib/fundamentals.mjs";

test("missing provider fields remain null", () => {
  const result = normalizeFundamentals({ pe: 22, revenueGrowth: null, debtToEquity: undefined });
  assert.equal(result.metrics.revenueGrowth, null);
  assert.equal(result.metrics.debtToEquity, null);
  assert.ok(result.coveragePct < 100);
});

test("strong profitability and moderate leverage improve quality", () => {
  const result = normalizeFundamentals({
    operatingMargin: 0.30,
    netMargin: 0.22,
    roe: 0.25,
    roic: 0.18,
    debtToEquity: 0.4,
    pe: 24,
    revenueGrowth: 0.12,
    epsGrowth: 0.14
  });
  assert.ok(result.qualityScore >= 70);
});

test("moderate valuation scores better than extreme valuation", () => {
  const moderate = normalizeFundamentals({ pe: 22, priceToSales: 4, evToEbitda: 16, freeCashFlowYield: 0.035 });
  const extreme = normalizeFundamentals({ pe: 80, priceToSales: 25, evToEbitda: 55, freeCashFlowYield: 0.005 });
  assert.ok(moderate.valuationScore > extreme.valuationScore);
});

test("negative growth lowers growth score", () => {
  const positive = normalizeFundamentals({ revenueGrowth: 0.15, epsGrowth: 0.18 });
  const negative = normalizeFundamentals({ revenueGrowth: -0.10, epsGrowth: -0.20 });
  assert.ok(positive.growthScore > negative.growthScore);
});

test('merge keeps primary value and records material provider conflict', () => {
  const merged = mergeFundamentalSources(
    { raw: { pe: 20, revenueGrowth: null }, source: 'Twelve Data', fieldSources: { pe: 'Twelve Data' } },
    { raw: { pe: 40, revenueGrowth: 0.12 }, source: 'Yahoo Finance', fieldSources: { pe: 'Yahoo Finance', revenueGrowth: 'Yahoo Finance' } }
  );

  assert.equal(merged.raw.pe, 20);
  assert.equal(merged.raw.revenueGrowth, 0.12);
  assert.ok(merged.conflicts.includes('pe'));
  assert.equal(merged.fieldSources.pe, 'Twelve Data');
  assert.equal(merged.fieldSources.revenueGrowth, 'Yahoo Finance');
});

test('ratio and growth fields conflict above 20 percent relative deviation', () => {
  const merged = mergeFundamentalSources(
    { raw: { pe: 20, revenueGrowth: 0.10 }, source: 'Twelve Data' },
    { raw: { pe: 26, revenueGrowth: 0.13 }, source: 'Yahoo Finance' }
  );

  assert.ok(merged.conflicts.includes('pe'));
  assert.ok(merged.conflicts.includes('revenueGrowth'));
});

test('margin and return fields conflict above five percentage points', () => {
  const merged = mergeFundamentalSources(
    { raw: { operatingMargin: 0.20, roe: 0.18, roic: 0.14 }, source: 'Twelve Data' },
    { raw: { operatingMargin: 0.26, roe: 0.24, roic: 0.20 }, source: 'Yahoo Finance' }
  );

  assert.ok(merged.conflicts.includes('operatingMargin'));
  assert.ok(merged.conflicts.includes('roe'));
  assert.ok(merged.conflicts.includes('roic'));
});

test('exactly five percentage points is not yet a margin conflict', () => {
  const merged = mergeFundamentalSources(
    { raw: { netMargin: 0.20 }, source: 'Twelve Data' },
    { raw: { netMargin: 0.25 }, source: 'Yahoo Finance' }
  );

  assert.ok(!merged.conflicts.includes('netMargin'));
});
