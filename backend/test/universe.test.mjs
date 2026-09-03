import test from "node:test";
import assert from "node:assert/strict";
import { loadUniverse, mergeUniverse, parseNasdaqListed, validateUniverse } from "../src/lib/universe.mjs";

test("parseNasdaqListed returns real stock and ETF metadata without test issues", () => {
  const rows = parseNasdaqListed([
    "Symbol|Security Name|Market Category|Test Issue|Financial Status|Round Lot Size|ETF|NextShares",
    "AAPL|Apple Inc. - Common Stock|Q|N|N|100|N|N",
    "QQQ|Invesco QQQ ETF|G|N|N|100|Y|N",
    "TEST|Test Security|Q|Y|N|100|N|N",
    "File Creation Time: 09032026"
  ].join("\n"));
  assert.equal(rows[0].ticker, "AAPL");
  assert.equal(rows[0].type, "AKTIE");
  assert.equal(rows[1].type, "ETF");
  assert.equal(rows[2].testIssue, true);
});

test("mergeUniverse preserves curated identity and removes ticker duplicates", () => {
  const curated = [{ id: "aapl", ticker: "AAPL", isin: "US0378331005", universeActive: true }];
  const external = [{ id: "us-aapl", ticker: "AAPL", isin: "", universeActive: true }, { id: "us-msft", ticker: "MSFT", isin: "", universeActive: true }];
  const merged = mergeUniverse(curated, external);
  assert.deepEqual(merged.map((x) => x.id), ["aapl", "us-msft"]);
});

test("validateUniverse rejects duplicate ISINs", () => {
  assert.throws(() => validateUniverse([
    { id: "a", ticker: "AAA", isin: "US0000000001", universeActive: true },
    { id: "b", ticker: "BBB", isin: "US0000000001", universeActive: true }
  ]), /Duplicate universe ISIN/);
});

test("loadUniverse can expose a 1000 item active radar without fabricating securities", async () => {
  const header = "Symbol|Security Name|Market Category|Test Issue|Financial Status|Round Lot Size|ETF|NextShares";
  const rows = Array.from({ length: 1100 }, (_, i) => `T${String(i).padStart(4, "0")}|Company ${i} - Common Stock|Q|N|N|100|N|N`);
  const text = [header, ...rows, "File Creation Time: 09032026"].join("\n");
  const other = "ACT Symbol|Security Name|Exchange|CQS Symbol|ETF|Round Lot Size|Test Issue|NASDAQ Symbol\nFile Creation Time: 09032026";
  const fetchImpl = async (url) => ({ ok: true, text: async () => String(url).includes("nasdaqlisted") ? text : other });
  const core = { id: "aapl", type: "AKTIE", name: "Apple", ticker: "AAPL", marketSymbol: "AAPL:NASDAQ", isin: "US0378331005", tradeRepublicName: "Apple", risk: 2 };
  const items = await loadUniverse({ refresh: true, limit: 1000, loadConfig: async () => ({ items: [core] }), fetchImpl });
  assert.equal(items.length, 1000);
  assert.equal(items[0].id, "aapl");
  assert.equal(items.filter((x) => x.universeActive).length, 1000);
  assert.equal(items.filter((x) => x.universeSource === "NASDAQ_TRADER").length, 999);
});
