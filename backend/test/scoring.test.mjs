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
  assert.equal(result.scoreBreakdown.valuation.score, null);
  assert.equal(result.scoreBreakdown.valuation.coverage, 0);
  assert.ok(result.coverage < 100);
  assert.ok(Number.isInteger(result.scoreTotal));
});

test("no available components produce null total instead of fabricated zero", () => {
  const result = scoreInvestment({ item: { id: "empty", type: "AKTIE" }, fundamentals: null, momentum: null, quote: null });
  assert.equal(result.scoreTotal, null);
  assert.equal(result.scoreQuality, null);
  assert.equal(result.scoreRisk, null);
  assert.equal(result.coverage, 0);
});

test("score breakdown explains each available pillar", () => {
  const result = scoreInvestment({
    item: { id: "quality", type: "AKTIE", risk: 2 },
    fundamentals: { qualityScore: 88, valuationScore: 78, growthScore: 82, coveragePct: 100, metrics: { roe: .3, pe: 22, revenueGrowth: .12 } },
    momentum: { score: 80, coveragePct: 100, m6: 18 },
    quote: { percentChange: 0.8 }
  });
  for (const key of ["quality", "valuation", "growth", "momentum", "risk"]) {
    assert.ok(result.scoreBreakdown[key]);
    assert.ok(Array.isArray(result.scoreBreakdown[key].reasons));
    assert.ok(result.scoreBreakdown[key].coverage > 0);
  }
});

test("strong stock inputs can produce BUY", () => {
  const result = scoreInvestment({
    item: { id: "quality", type: "AKTIE", risk: 2 },
    fundamentals: { qualityScore: 88, valuationScore: 78, growthScore: 82, coveragePct: 100 },
    momentum: { score: 80, coveragePct: 100 },
    quote: { percentChange: 0.8 }
  });
  assert.equal(result.recommendation, "BUY");
  assert.ok(result.scoreTotal >= 75);
});
