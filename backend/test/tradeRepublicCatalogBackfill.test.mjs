import test from "node:test";
import assert from "node:assert/strict";
import { loadTradeRepublicCatalog } from "../src/lib/tradeRepublicCatalog.mjs";

function rows(assetType, count) {
  const prefix = assetType === "stock" ? "DE" : "IE";
  return Array.from({ length: count }, (_, index) => ({
    isin: `${prefix}${String(index).padStart(10, "0")}`,
    name: assetType === "stock" ? `Aktie ${index}` : `World UCITS ETF ${index}`,
    ticker: `${assetType === "stock" ? "S" : "E"}${index}`,
    country: assetType === "stock" ? "DE" : "IE"
  }));
}

test("fills a missing ETF quota with additional verified stocks up to the total target", async () => {
  const stockRows = rows("stock", 20);
  const fundRows = rows("fund", 2);
  const requestPage = async ({ assetType, page, pageSize }) => {
    const source = assetType === "stock" ? stockRows : fundRows;
    const start = (page - 1) * pageSize;
    return { results: source.slice(start, start + pageSize) };
  };

  const items = await loadTradeRepublicCatalog({ requestPage, target: 10, pageSize: 20 });

  assert.equal(items.length, 10);
  assert.equal(items.filter((item) => item.type === "ETF").length, 2);
  assert.equal(items.filter((item) => item.type === "AKTIE").length, 8);
  assert.ok(items.every((item) => item.tradeRepublicEligible === true));
});

test("fills a missing stock quota with additional verified ETFs up to the total target", async () => {
  const stockRows = rows("stock", 2);
  const fundRows = rows("fund", 20);
  const requestPage = async ({ assetType, page, pageSize }) => {
    const source = assetType === "stock" ? stockRows : fundRows;
    const start = (page - 1) * pageSize;
    return { results: source.slice(start, start + pageSize) };
  };

  const items = await loadTradeRepublicCatalog({ requestPage, target: 10, pageSize: 20 });

  assert.equal(items.length, 10);
  assert.equal(items.filter((item) => item.type === "AKTIE").length, 2);
  assert.equal(items.filter((item) => item.type === "ETF").length, 8);
  assert.ok(items.every((item) => item.tradeRepublicEligible === true));
});
