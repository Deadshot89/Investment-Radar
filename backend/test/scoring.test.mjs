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
