import test from "node:test";
import assert from "node:assert/strict";
import {
  buildConnectFrame,
  decodeSubscriptionFrame,
  deduplicateTradeRepublicCatalog,
  extractSearchResults,
  loadTradeRepublicCatalog,
  normalizeTradeRepublicResult,
  parseSubscriptionResponse
} from "../src/lib/tradeRepublicCatalog.mjs";

test("normalizes Trade Republic stock and fund results as verified German-jurisdiction instruments", () => {
  const stock = normalizeTradeRepublicResult({ isin: "DE000BASF111", name: "BASF", ticker: "BAS", country: "DE", sector: "Materials" }, "stock");
  const fund = normalizeTradeRepublicResult({ instrumentId: "IE00B3YLTY66", name: "SPDR MSCI ACWI IMI UCITS ETF", symbol: "SPYI", country: "IE" }, "fund");
  assert.equal(stock.type, "AKTIE");
  assert.equal(stock.tradeRepublicEligible, true);
  assert.equal(stock.tradeRepublicJurisdiction, "DE");
  assert.equal(fund.type, "ETF");
  assert.equal(fund.isin, "IE00B3YLTY66");
  assert.ok(Number.isFinite(fund.etfStructureScore));
  assert.ok(fund.etfStructureScore >= 70);
});

test("rejects rows without a valid ISIN instead of inventing securities", () => {
  assert.equal(normalizeTradeRepublicResult({ name: "Unknown", ticker: "X" }, "stock"), null);
});

test("uses the current Trade Republic websocket connect frame", () => {
  assert.equal(buildConnectFrame(), 'connect 34 {"locale":"en"}');
});

test("extracts known neonSearch response shapes and parses websocket answers", () => {
  const payload = { results: [{ isin: "DE000BASF111" }] };
  assert.deepEqual(extractSearchResults(payload), payload.results);
  assert.deepEqual(parseSubscriptionResponse('1 A {"results":[{"isin":"DE000BASF111"}]}', 1), payload);
  assert.equal(parseSubscriptionResponse('2 A {"results":[]}', 1), undefined);
});

test("turns Trade Republic error frames into rejected transport results instead of uncaught callback throws", () => {
  const decoded = decodeSubscriptionFrame('1 E SEARCH_UNAVAILABLE', 1);
  assert.equal(decoded.value, undefined);
  assert.match(decoded.error?.message ?? "", /SEARCH_UNAVAILABLE/);
});

test("deduplicates by ISIN", () => {
  const one = { isin: "DE000BASF111", ticker: "BAS" };
  assert.equal(deduplicateTradeRepublicCatalog([one, { ...one, ticker: "BASF" }]).length, 1);
});

test("loads stock and ETF pages through injectable public catalog transport", async () => {
  const calls = [];
  const requestPage = async ({ assetType, page, pageSize, jurisdiction }) => {
    calls.push({ assetType, page, pageSize, jurisdiction });
    if (page > 1) return { results: [] };
    if (assetType === "stock") return { results: [{ isin: "DE000BASF111", name: "BASF", ticker: "BAS", country: "DE" }] };
    return { results: [{ isin: "IE00B3YLTY66", name: "SPDR MSCI ACWI IMI UCITS ETF", ticker: "SPYI", country: "IE" }] };
  };
  const items = await loadTradeRepublicCatalog({ requestPage, target: 10, pageSize: 20 });
  assert.deepEqual(items.map((item) => item.isin), ["DE000BASF111", "IE00B3YLTY66"]);
  assert.deepEqual(calls.map((call) => call.assetType), ["stock", "fund"]);
  assert.ok(calls.every((call) => call.jurisdiction === "DE"));
});

test("a total target is split across stocks and ETFs instead of being exhausted by stocks", async () => {
  const requestPage = async ({ assetType, page }) => {
    if (page > 1) return { results: [] };
    const prefix = assetType === "stock" ? "DE" : "IE";
    return {
      results: Array.from({ length: 8 }, (_, index) => ({
        isin: `${prefix}${String(index).padStart(10, "0")}`,
        name: assetType === "stock" ? `Aktie ${index}` : `World UCITS ETF ${index}`,
        ticker: `${assetType === "stock" ? "S" : "E"}${index}`,
        country: assetType === "stock" ? "DE" : "IE"
      }))
    };
  };

  const items = await loadTradeRepublicCatalog({ requestPage, target: 10, pageSize: 20 });
  assert.equal(items.length, 10);
  assert.ok(items.filter((item) => item.type === "AKTIE").length >= 5);
  assert.ok(items.filter((item) => item.type === "ETF").length >= 3);
});
