import test from "node:test";
import assert from "node:assert/strict";
import { buildDashboard } from "../src/lib/dashboard.mjs";

test("dashboard keeps legacy fields and adds analysis v2", async () => {
  const dashboard = await buildDashboard({
    loadConfig: async () => ({
      marketLight: "GELB", budget: 100, topPickId: "x",
      items: [{
        id: "x", type: "AKTIE", name: "Example", ticker: "EX", isin: "US0378331005",
        tradeRepublicName: "Example", marketSymbol: "EX:NYSE", risk: 2,
        reviewDrop1dPct: 7, hardReviewBelow: 10
      }]
    }),
    loadQuotes: async () => new Map([["x", { price: 100, currency: "EUR", percentChange: 1, marketOpen: true, source: "test", delayed: false, error: null }]]),
    loadHistory: async () => new Map([["x", { d1: 1, m1: 3, m3: 8, m6: 12, m12: 18, score: 75, coveragePct: 100, stale: false }]]),
    loadFundamentals: async () => new Map([["x", { metrics: {}, qualityScore: 85, valuationScore: 78, growthScore: 80, coveragePct: 100, source: "test", stale: false, asOf: "2026-09-02" }]]),
    loadEurRateDetails: async () => new Map(),
    loadState: async () => ({ activeFingerprints: [], recent: [], previousScores: {}, previousRecommendations: {} }),
    loadQuoteCache: async () => ({}),
    saveQuoteCache: async () => {},
    loadFxCache: async () => ({}),
    saveFxCache: async () => {}
  });
  const item = dashboard.items[0];
  assert.equal(item.status, "KAUFEN");
  assert.equal(item.recommendation, "BUY");
  assert.equal(typeof item.scoreTotal, "number");
  assert.equal(typeof item.coverage, "number");
  assert.ok("momentum" in item);
  assert.ok("fundamentals" in item);
  assert.ok(Array.isArray(item.recommendationReasons));
  assert.equal(typeof item.allocation, "number");
});
