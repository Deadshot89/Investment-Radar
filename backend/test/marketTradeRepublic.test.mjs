import test from "node:test";
import assert from "node:assert/strict";
import { loadQuotes } from "../src/lib/market.mjs";

test("loadQuotes prefers Trade Republic data and does not require a fake market symbol", async () => {
  const item = {
    id: "mmm",
    name: "3M",
    ticker: "US88579Y1010",
    isin: "US88579Y1010",
    marketSymbol: "",
    yahooSymbol: "",
    providerSymbolUnresolved: true,
    tradeRepublicEligible: true
  };
  const loadTradeRepublicQuotes = async () => new Map([["mmm", {
    symbol: "US88579Y1010.LSX",
    price: 132.5,
    currency: "EUR",
    percentChange: 1.1,
    marketOpen: null,
    source: "Trade Republic",
    delayed: false,
    error: null
  }]]);
  const result = await loadQuotes([item], { loadTradeRepublicQuotes });
  assert.equal(result.get("mmm").price, 132.5);
  assert.equal(result.get("mmm").source, "Trade Republic");
});
