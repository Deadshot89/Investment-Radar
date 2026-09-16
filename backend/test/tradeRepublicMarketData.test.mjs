import test from "node:test";
import assert from "node:assert/strict";
import {
  loadTradeRepublicHistories,
  loadTradeRepublicQuotes,
  normalizeTradeRepublicHistory,
  normalizeTradeRepublicQuote
} from "../src/lib/tradeRepublicMarketData.mjs";

const item = {
  id: "basf",
  name: "BASF",
  isin: "DE000BASF111",
  ticker: "BAS",
  tradeRepublicEligible: true
};

test("normalizes Trade Republic realtime quote in EUR", () => {
  const quote = normalizeTradeRepublicQuote({
    last: { time: 1_700_000_000_000, price: "42.85" },
    pre: { price: "42.14" },
    qualityId: "realtime"
  }, item, "LSX");
  assert.equal(quote.price, 42.85);
  assert.equal(quote.currency, "EUR");
  assert.equal(quote.source, "Trade Republic");
  assert.equal(quote.delayed, false);
  assert.ok(quote.percentChange > 1.6 && quote.percentChange < 1.8);
});

test("normalizes daily Trade Republic history", () => {
  const history = normalizeTradeRepublicHistory({
    aggregates: [
      { time: 1_700_000_000_000, close: "40.0" },
      { time: 1_700_086_400_000, close: "41.0" }
    ]
  });
  assert.equal(history.points.length, 2);
  assert.equal(history.source, "Trade Republic");
});

test("loads quote directly by ISIN on LSX without a provider ticker", async () => {
  const requestBatch = async (requests) => new Map(requests.map((request) => [
    request.key,
    { data: { last: { time: 1_700_000_000_000, price: "42.85" }, pre: { price: "42.14" }, qualityId: "realtime" }, error: null }
  ]));
  const result = await loadTradeRepublicQuotes([
    { ...item, ticker: "US88579Y1010", marketSymbol: "", providerSymbolUnresolved: true }
  ], { requestBatch });
  assert.equal(result.get("basf").price, 42.85);
});

test("uses instrument metadata to retry on an alternate exchange", async () => {
  let call = 0;
  const requestBatch = async (requests) => {
    call += 1;
    if (call === 1) return new Map([["basf", { data: null, error: "LSX unavailable" }]]);
    if (call === 2) return new Map([["basf", {
      data: { exchangeIds: ["TDG"], exchanges: [{ slug: "TDG", active: true }] },
      error: null
    }]]);
    assert.equal(requests[0].payload.id, "DE000BASF111.TDG");
    return new Map([["basf", {
      data: { last: { price: "43.10" }, pre: { price: "42.00" }, qualityId: "realtime" },
      error: null
    }]]);
  };
  const result = await loadTradeRepublicQuotes([item], { requestBatch });
  assert.equal(result.get("basf").price, 43.10);
});

test("loads one year daily history directly from Trade Republic", async () => {
  const requestBatch = async (requests) => {
    assert.equal(requests[0].payload.type, "aggregateHistoryLight");
    assert.equal(requests[0].payload.id, "DE000BASF111.LSX");
    return new Map([["basf", { data: { aggregates: [
      { time: 1_700_000_000_000, close: "40.0" },
      { time: 1_700_086_400_000, close: "41.0" }
    ] }, error: null }]]);
  };
  const result = await loadTradeRepublicHistories([item], { requestBatch });
  assert.equal(result.get("basf").points.length, 2);
});
