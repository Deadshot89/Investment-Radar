import test from "node:test";
import assert from "node:assert/strict";
import { buildAnalysisSnapshot, buildDashboard } from "../src/lib/dashboard.mjs";

const item = {
  id: "apple-test", type: "AKTIE", name: "Apple", ticker: "AAPL", isin: "US0378331005",
  tradeRepublicName: "Apple", risk: 2, tradeRepublicEligible: true, universeActive: true,
  portfolioOnly: false
};

const fundamentals = {
  metrics: { pe: 24, priceToSales: 6, evToEbitda: 18, freeCashFlowYield: 0.035, revenueGrowth: 0.09, epsGrowth: 0.12, operatingMargin: 0.31, netMargin: 0.25, roe: 0.45, roic: 0.28, debtToEquity: 1.2 },
  qualityScore: 88, valuationScore: 78, growthScore: 82, coveragePct: 100, source: "TEST", conflicts: []
};
const history = { d1: 1, m1: 4, m3: 9, m6: 15, m12: 24, score: 80, coveragePct: 100, pointsCount: 250, source: "TEST" };
const quote = { price: 220, currency: "USD", percentChange: 1.1, source: "TEST" };

function baseOverrides({ historyValue = history, fundamentalsValue = fundamentals, recent = [] } = {}) {
  return {
    loadConfig: async () => ({ items: [item], budget: 100, marketLight: "GRÜN" }),
    loadQuotes: async () => new Map([[item.id, quote]]),
    loadHistory: async () => new Map([[item.id, historyValue]]),
    loadFundamentals: async () => new Map([[item.id, fundamentalsValue]]),
    loadState: async () => ({ previousScores: {}, previousRecommendations: {}, recent }),
    loadQuoteCache: async () => ({}), saveQuoteCache: async () => {},
    loadEurRateDetails: async () => new Map([["USD", { rate: 0.9, source: "TEST" }]]),
    loadFxCache: async () => ({}), saveFxCache: async () => {}
  };
}

test("dashboard items expose the same quality gate, breakdown and forecast as radar", async () => {
  const snapshot = await buildAnalysisSnapshot(baseOverrides());
  const result = snapshot.items[0];
  assert.ok(result.dataQuality);
  assert.ok(result.scoreBreakdown);
  assert.ok(result.forecast);
  assert.equal(result.forecast.quality, "HOCH");
  assert.equal(result.recommendation, "BUY");
});

test("dashboard cannot publish BUY when analysis quality is incomplete", async () => {
  const snapshot = await buildAnalysisSnapshot(baseOverrides({
    historyValue: { score: 90, m1: 3, coveragePct: 15, pointsCount: 20 },
    fundamentalsValue: { qualityScore: 95, valuationScore: 90, growthScore: 95, metrics: {}, coveragePct: 15, conflicts: [] }
  }));
  const result = snapshot.items[0];
  assert.notEqual(result.recommendation, "BUY");
  assert.equal(result.forecast.quality, "NICHT_BELASTBAR");
  assert.equal(result.forecast.expectedChangePct, null);
});

test("dashboard drops stale BUY history when current analysis no longer permits BUY", async () => {
  const staleBuy = {
    id: "old-buy", itemId: item.id, level: "BUY", title: "Kaufchance", message: "Altes BUY-Signal", createdAt: "2026-09-09T08:00:00Z"
  };
  const dashboard = await buildDashboard(baseOverrides({
    historyValue: { score: 90, m1: 3, coveragePct: 15, pointsCount: 20 },
    fundamentalsValue: { qualityScore: 95, valuationScore: 90, growthScore: 95, metrics: {}, coveragePct: 15, conflicts: [] },
    recent: [staleBuy]
  }));

  assert.notEqual(dashboard.items[0].recommendation, "BUY");
  assert.equal(dashboard.alerts.some((alert) => alert.id === staleBuy.id && alert.level === "BUY"), false);
});
