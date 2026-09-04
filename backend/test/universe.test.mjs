import test from "node:test";
import assert from "node:assert/strict";
import { loadUniverse, mergeUniverse, parseNasdaqListed, validateUniverse } from "../src/lib/universe.mjs";

test("parseNasdaqListed remains available only as an explicit development fallback", () => {
  const rows = parseNasdaqListed([
    "Symbol|Security Name|Market Category|Test Issue|Financial Status|Round Lot Size|ETF|NextShares",
    "AAPL|Apple Inc. - Common Stock|Q|N|N|100|N|N",
    "QQQ|Invesco QQQ ETF|G|N|N|100|Y|N",
    "TEST|Test Security|Q|Y|N|100|N|N",
    "File Creation Time: 09032026"
  ].join("\n"));
  assert.equal(rows[0].tradeRepublicEligible, null);
  assert.equal(rows[1].type, "ETF");
  assert.equal(rows[2].testIssue, true);
});

test("mergeUniverse preserves curated identity and removes ISIN/ticker duplicates", () => {
  const curated = [{ id: "aapl", ticker: "AAPL", isin: "US0378331005", universeActive: true, tradeRepublicEligible: true }];
  const external = [
    { id: "tr-US0378331005", ticker: "AAPL", isin: "US0378331005", universeActive: true, tradeRepublicEligible: true },
    { id: "tr-US5949181045", ticker: "MSFT", isin: "US5949181045", universeActive: true, tradeRepublicEligible: true }
  ];
  const merged = mergeUniverse(curated, external);
  assert.deepEqual(merged.map((x) => x.id), ["aapl", "tr-US5949181045"]);
});

test("validateUniverse rejects duplicate ISINs and unverified production candidates", () => {
  assert.throws(() => validateUniverse([
    { id: "a", ticker: "AAA", isin: "US0000000001", universeActive: true },
    { id: "b", ticker: "BBB", isin: "US0000000001", universeActive: true }
  ]), /Duplicate universe ISIN/);
  assert.throws(() => validateUniverse([
    { id: "a", ticker: "AAA", isin: "US0000000001", universeActive: true, portfolioOnly: false, tradeRepublicEligible: null }
  ], { requireTradeRepublic: true }), /Trade Republic eligibility not verified/);
});

test("loadUniverse can expose 1000 Trade-Republic-verified stocks and ETFs", async () => {
  const core = { id: "aapl", type: "AKTIE", name: "Apple", ticker: "AAPL", marketSymbol: "AAPL:NASDAQ", isin: "US0378331005", tradeRepublicName: "Apple", risk: 2 };
  const catalog = Array.from({ length: 1100 }, (_, i) => ({
    id: `tr-DE${String(i).padStart(10, "0")}`,
    type: i % 5 === 0 ? "ETF" : "AKTIE",
    name: `Trade Republic Instrument ${i}`,
    ticker: `TR${String(i).padStart(4, "0")}`,
    isin: `DE${String(i).padStart(10, "0")}`,
    tradeRepublicName: `Trade Republic Instrument ${i}`,
    risk: 3,
    universeActive: true,
    portfolioOnly: false,
    tradeRepublicEligible: true,
    universeSource: "TRADE_REPUBLIC_PUBLIC"
  }));
  const items = await loadUniverse({
    refresh: true,
    limit: 1000,
    minActive: 1000,
    requireTradeRepublicEligibility: true,
    loadConfig: async () => ({ items: [core] }),
    loadTradeRepublicCatalog: async () => catalog
  });
  assert.equal(items.length, 1000);
  assert.equal(items[0].id, "aapl");
  assert.equal(items.filter((x) => x.universeActive).length, 1000);
  assert.equal(items.filter((x) => !x.portfolioOnly && x.tradeRepublicEligible !== true).length, 0);
  assert.ok(items.some((x) => x.type === "ETF"));
});

test("loadUniverse keeps a meaningful ETF share when a 2000-item production limit is applied", async () => {
  const catalog = [
    ...Array.from({ length: 1800 }, (_, i) => ({
      id: `stock-${i}`,
      type: "AKTIE",
      name: `Aktie ${i}`,
      ticker: `S${i}`,
      isin: `DE${String(i).padStart(10, "0")}`,
      tradeRepublicEligible: true,
      universeActive: true,
      portfolioOnly: false
    })),
    ...Array.from({ length: 800 }, (_, i) => ({
      id: `etf-${i}`,
      type: "ETF",
      name: `ETF ${i}`,
      ticker: `E${i}`,
      isin: `IE${String(i).padStart(10, "0")}`,
      tradeRepublicEligible: true,
      universeActive: true,
      portfolioOnly: false
    }))
  ];

  const items = await loadUniverse({
    refresh: true,
    limit: 2000,
    minActive: 2000,
    requireTradeRepublicEligibility: true,
    loadConfig: async () => ({ items: [] }),
    loadTradeRepublicCatalog: async () => catalog
  });

  assert.equal(items.length, 2000);
  assert.ok(items.filter((item) => item.type === "ETF").length >= 400);
  assert.ok(items.filter((item) => item.type === "AKTIE").length >= 1200);
});
