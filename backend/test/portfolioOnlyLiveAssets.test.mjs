import test from "node:test";
import assert from "node:assert/strict";
import { loadLocalConfig } from "../src/lib/config.mjs";
import { buildAnalysisSnapshot } from "../src/lib/dashboard.mjs";

test("Nel and Samsung are live portfolio-only assets", async () => {
  const config = loadLocalConfig();
  const nel = config.items.find((item) => item.id === "custom-nel-asa");
  const samsung = config.items.find((item) => item.id === "custom-samsung-gdr");

  assert.ok(nel, "Nel ASA must be part of the backend investment universe");
  assert.equal(nel.yahooSymbol, "NEL.OL");
  assert.equal(nel.portfolioOnly, true);
  assert.ok(samsung, "Samsung GDR must be part of the backend investment universe");
  assert.equal(samsung.yahooSymbol, "SMSN.IL");
  assert.equal(samsung.portfolioOnly, true);

  const items = [nel, samsung];
  const snapshot = await buildAnalysisSnapshot({
    loadConfig: async () => ({ marketLight: "GELB", budget: 100, topPickId: nel.id, items }),
    loadQuotes: async () => new Map([
      [nel.id, { price: 2.5, currency: "NOK", percentChange: 1.0, source: "test" }],
      [samsung.id, { price: 1500, currency: "USD", percentChange: -0.5, source: "test" }]
    ]),
    loadHistory: async () => new Map(),
    loadFundamentals: async () => new Map(),
    loadEurRateDetails: async () => new Map([
      ["NOK", { rate: 0.085, source: "test", delayed: false }],
      ["USD", { rate: 0.86, source: "test", delayed: false }]
    ]),
    loadState: async () => ({ previousScores: {}, previousRecommendations: {}, recent: [] }),
    loadQuoteCache: async () => ({}),
    saveQuoteCache: async () => {},
    loadFxCache: async () => ({}),
    saveFxCache: async () => {}
  });

  for (const item of snapshot.items) {
    assert.ok(item.priceEur > 0, `${item.id} should receive a EUR live value`);
    assert.equal(item.recommendation, "REVIEW", `${item.id} must not become a buy recommendation`);
    assert.equal(item.allocation, 0, `${item.id} must not receive monthly buy allocation`);
  }
});
