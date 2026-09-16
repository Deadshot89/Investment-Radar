import test from "node:test";
import assert from "node:assert/strict";
import { calculateMomentum } from "../src/lib/historySupport.mjs";
import { shouldReuseFreshHistoryCache } from "../src/lib/history.mjs";

const DAY = 86_400_000;
const now = Date.UTC(2026, 8, 2);

function marketLikeSeries(days) {
  const points = [];
  let cursor = now - 390 * DAY;
  let price = 80;
  while (cursor <= now && points.length < days) {
    const weekday = new Date(cursor).getUTCDay();
    if (weekday !== 0 && weekday !== 6) {
      points.push({ time: cursor, close: price });
      price += 0.18;
    }
    cursor += DAY;
  }
  if (points.at(-1)?.time < now) points.push({ time: now, close: price });
  return points;
}

const points = marketLikeSeries(280);

test("momentum exposes all five horizons with sufficient observations", () => {
  const result = calculateMomentum(points, now);
  assert.ok(result.d1 >= 0);
  assert.ok(result.m1 > 0);
  assert.ok(result.m3 > 0);
  assert.ok(result.m6 > 0);
  assert.ok(result.m12 > 0);
  assert.ok(result.coveragePct >= 85);
  assert.ok(result.pointsCount >= 250);
});

test("missing old history reduces coverage instead of inventing returns", () => {
  const result = calculateMomentum(points.slice(-20), now);
  assert.equal(result.m12, null);
  assert.equal(result.m6, null);
  assert.ok(result.coveragePct < 25);
});

test("broad positive momentum scores above neutral", () => {
  const result = calculateMomentum(points, now);
  assert.ok(result.score > 50);
});


test("accepts Trade Republic history for an unresolved provider ticker", async () => {
  const { loadHistory } = await import("../src/lib/history.mjs");
  const localNow = Date.UTC(2026, 8, 16);
  const series = marketLikeSeries(280);
  const item = {
    id: "mmm",
    name: "3M",
    ticker: "US88579Y1010",
    isin: "US88579Y1010",
    marketSymbol: "",
    yahooSymbol: "",
    providerSymbolUnresolved: true,
    tradeRepublicEligible: true,
    type: "AKTIE"
  };
  const loadTradeRepublicHistories = async () => new Map([[
    "mmm",
    { points: series, source: "Trade Republic", error: null }
  ]]);
  const result = await loadHistory([item], {
    now: localNow,
    refresh: true,
    loadTradeRepublicHistories
  });
  assert.equal(result.get("mmm").source, "Trade Republic");
  assert.ok(result.get("mmm").coveragePct >= 70);
});


test("explicit history refresh bypasses an otherwise fresh cache", () => {
  const localNow = Date.UTC(2026, 8, 16, 12, 0, 0);
  const cached = { points: [{ time: localNow, close: 100 }], fetchedAt: new Date(localNow - 60_000).toISOString() };
  assert.equal(shouldReuseFreshHistoryCache(cached, localNow, false), true);
  assert.equal(shouldReuseFreshHistoryCache(cached, localNow, true), false);
});


test("prefers Yahoo when Trade Republic history is below the quality threshold", async () => {
  const { loadHistory } = await import("../src/lib/history.mjs");
  const partial = points.slice(-80);
  const full = points;
  const item = {
    id: "history-fallback-blackrock-test",
    name: "BlackRock",
    ticker: "US09290D1019",
    isin: "US09290D1019",
    marketSymbol: "",
    yahooSymbol: "",
    providerSymbolUnresolved: true,
    tradeRepublicEligible: true,
    type: "AKTIE"
  };
  const loadTradeRepublicHistories = async () => new Map([[
    item.id,
    { points: partial, source: "Trade Republic", error: null }
  ]]);
  const fetchImpl = async () => ({
    ok: true,
    status: 200,
    json: async () => ({
      chart: {
        result: [{
          timestamp: full.map((p) => Math.floor(p.time / 1000)),
          indicators: { quote: [{ close: full.map((p) => p.close) }] }
        }]
      }
    })
  });

  const result = await loadHistory([item], {
    now,
    refresh: true,
    fetchImpl,
    loadTradeRepublicHistories,
    resolveYahooSymbol: async () => "BLK"
  });

  assert.equal(result.get(item.id).source, "Yahoo Finance");
  assert.ok(result.get(item.id).coveragePct >= 85);
});

test("best history candidate chooses real coverage before provider order", async () => {
  const { bestHistoryCandidate } = await import("../src/lib/history.mjs");
  const short = { points: points.slice(-70), source: "Trade Republic" };
  const complete = { points, source: "Yahoo Finance" };

  const selected = bestHistoryCandidate([short, complete], now);

  assert.equal(selected.source, "Yahoo Finance");
});
