import test from "node:test";
import assert from "node:assert/strict";
import { loadHistory } from "../src/lib/history.mjs";
import { loadFundamentals } from "../src/lib/fundamentals.mjs";
import { getInstrumentDetail } from "../src/lib/radar.mjs";

test("history cache-only mode never calls provider", async () => {
  const oldKey = process.env.TWELVE_DATA_API_KEY;
  process.env.TWELVE_DATA_API_KEY = "test-key";
  let called = false;
  try {
    await loadHistory([{ id: "cache-only-history", ticker: "X", marketSymbol: "X:NYSE", yahooSymbol: "X" }], {
      refresh: false,
      now: Date.UTC(2026, 8, 2),
      fetchImpl: async () => { called = true; throw new Error("provider should not be called"); }
    });
    assert.equal(called, false);
  } finally {
    if (oldKey == null) delete process.env.TWELVE_DATA_API_KEY; else process.env.TWELVE_DATA_API_KEY = oldKey;
  }
});

test("fundamental cache-only mode never calls provider", async () => {
  const oldKey = process.env.TWELVE_DATA_API_KEY;
  process.env.TWELVE_DATA_API_KEY = "test-key";
  let called = false;
  try {
    await loadFundamentals([{ id: "cache-only-fundamentals", type: "AKTIE", marketSymbol: "X:NYSE" }], {
      refresh: false,
      now: Date.UTC(2026, 8, 2),
      fetchImpl: async () => { called = true; throw new Error("provider should not be called"); }
    });
    assert.equal(called, false);
  } finally {
    if (oldKey == null) delete process.env.TWELVE_DATA_API_KEY; else process.env.TWELVE_DATA_API_KEY = oldKey;
  }
});

test('instrument detail forces slow data refresh instead of returning a permanent low-coverage cold-cache state', async () => {
  const instrument = { id: 'aapl', type: 'AKTIE', name: 'Apple', ticker: 'AAPL', risk: 2, tradeRepublicEligible: true, universeActive: true };
  const historyRefreshFlags = [];
  const fundamentalRefreshFlags = [];

  const detail = await getInstrumentDetail('aapl', {
    loadUniverse: async () => [instrument],
    loadQuotes: async () => new Map([['aapl', { price: 200, currency: 'USD', percentChange: 1, source: 'TEST' }]]),
    loadHistory: async (_items, options) => {
      historyRefreshFlags.push(options?.refresh);
      return new Map([['aapl', { m1: 2, m3: 5, m6: 10, m12: 18, score: 75, coveragePct: 100, pointsCount: 250 }]]);
    },
    loadFundamentals: async (_items, options) => {
      fundamentalRefreshFlags.push(options?.refresh);
      return new Map([['aapl', { qualityScore: 85, valuationScore: 70, growthScore: 80, coveragePct: 90, metrics: { pe: 25, revenueGrowth: .1, epsGrowth: .12, roe: .25 } }]]);
    },
    loadEurRateDetails: async () => new Map([['USD', { rate: 0.9 }]])
  });

  assert.deepEqual(historyRefreshFlags, [true]);
  assert.deepEqual(fundamentalRefreshFlags, [true]);
  assert.ok(detail.dataQuality.overallCoverage >= 70);
});
