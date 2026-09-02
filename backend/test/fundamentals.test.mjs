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
