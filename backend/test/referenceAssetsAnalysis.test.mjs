import test from "node:test";
import assert from "node:assert/strict";
import { loadLocalConfig } from "../src/lib/config.mjs";
import { buildAnalysisSnapshot } from "../src/lib/dashboard.mjs";

const completeHistory = {
  d1: 0.8, m1: 3.5, m3: 8.0, m6: 14.0, m12: 22.0,
  score: 78, coveragePct: 100, pointsCount: 250, source: "REFERENCE_HISTORY", stale: false
};

const completeFundamentals = {
  metrics: {
    pe: 24, priceToSales: 5.5, evToEbitda: 17, freeCashFlowYield: 0.04,
    revenueGrowth: 0.09, epsGrowth: 0.12, operatingMargin: 0.28, netMargin: 0.22,
    roe: 0.32, roic: 0.24, debtToEquity: 1.1
  },
  qualityScore: 84, valuationScore: 72, growthScore: 80,
  coveragePct: 100, source: "REFERENCE_FUNDAMENTALS", stale: false, conflicts: []
};

test("Apple Nel and Samsung receive complete analysis while portfolio-only assets can never become purchase eligible", async () => {
  const config = loadLocalConfig();
  const apple = config.items.find((entry) => entry.id === "aapl");
  const nel = config.items.find((entry) => entry.id === "custom-nel-asa");
  const samsung = config.items.find((entry) => entry.id === "custom-samsung-gdr");
  assert.ok(apple && nel && samsung, "all three reference assets must exist");

  const items = [
    { ...apple, tradeRepublicEligible: true, universeActive: true },
    { ...nel, tradeRepublicEligible: true, universeActive: true },
    { ...samsung, tradeRepublicEligible: true, universeActive: true }
  ];
  const quotes = new Map([
    [apple.id, { price: 220, currency: "USD", percentChange: 0.7, source: "REFERENCE_QUOTE" }],
    [nel.id, { price: 2.5, currency: "NOK", percentChange: 1.0, source: "REFERENCE_QUOTE" }],
    [samsung.id, { price: 1500, currency: "USD", percentChange: -0.5, source: "REFERENCE_QUOTE" }]
  ]);
  const histories = new Map(items.map((entry) => [entry.id, { ...completeHistory }]));
  const fundamentals = new Map(items.map((entry) => [entry.id, { ...completeFundamentals }]));

  const snapshot = await buildAnalysisSnapshot({
    loadConfig: async () => ({ marketLight: "GELB", budget: 100, topPickId: apple.id, items }),
    loadQuotes: async () => quotes,
    loadHistory: async () => histories,
    loadFundamentals: async () => fundamentals,
    loadEurRateDetails: async () => new Map([
      ["USD", { rate: 0.86, source: "REFERENCE_FX", delayed: false }],
      ["NOK", { rate: 0.085, source: "REFERENCE_FX", delayed: false }]
    ]),
    loadState: async () => ({ previousScores: {}, previousRecommendations: {}, recent: [] }),
    loadQuoteCache: async () => ({}), saveQuoteCache: async () => {},
    loadFxCache: async () => ({}), saveFxCache: async () => {}
  });

  const byId = new Map(snapshot.items.map((entry) => [entry.id, entry]));
  for (const id of [apple.id, nel.id, samsung.id]) {
    const result = byId.get(id);
    assert.ok(result, `${id} must be analyzed`);
    assert.ok(result.coverage >= 70, `${id} must not remain at low placeholder coverage`);
    assert.ok(result.dataQuality.historyCoverage >= 70, `${id} needs usable history`);
    assert.ok(result.dataQuality.fundamentalCoverage >= 70, `${id} needs usable fundamentals`);
    assert.notEqual(result.forecast.quality, "NICHT_BELASTBAR", `${id} needs a usable 12M forecast`);
    assert.equal(typeof result.forecast.expectedChangePct, "number", `${id} needs a numeric base scenario`);
    assert.equal(typeof result.forecast.bearChangePct, "number", `${id} needs a bear scenario`);
    assert.equal(typeof result.forecast.bullChangePct, "number", `${id} needs a bull scenario`);
    assert.ok(result.forecast.reasons.length > 0, `${id} needs forecast reasons`);
  }

  assert.equal(byId.get(nel.id).purchaseEligible, false, "Nel portfolio-only tracking can never auto-buy");
  assert.equal(byId.get(samsung.id).purchaseEligible, false, "Samsung portfolio-only tracking can never auto-buy");
  assert.notEqual(byId.get(nel.id).recommendation, "BUY");
  assert.notEqual(byId.get(samsung.id).recommendation, "BUY");
});
