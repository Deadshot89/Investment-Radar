import test from "node:test";
import assert from "node:assert/strict";
import { buildCompatibilityAllocations } from "../src/lib/compatibility.mjs";
import { validateConfig } from "../src/lib/config.mjs";

test("compatibility allocations sum to budget across BUY candidates", () => {
  const result = buildCompatibilityAllocations([
    { id: "a", recommendation: "BUY", scoreTotal: 90 },
    { id: "b", recommendation: "BUY", scoreTotal: 80 },
    { id: "c", recommendation: "WATCH", scoreTotal: 74 }
  ], 100);
  assert.equal([...result.values()].reduce((a, b) => a + b, 0), 100);
  assert.equal(result.get("c"), 0);
});

test("config no longer requires static allocation sum", () => {
  const parsed = validateConfig({
    marketLight: "GELB",
    budget: 100,
    items: [
      { id: "a", type: "AKTIE", ticker: "AAA", marketSymbol: "AAA:NYSE", isin: "US0378331005", risk: 2, allocation: 0 },
      { id: "b", type: "ETF", ticker: "BBB", marketSymbol: "BBB:XETR", isin: "IE00B3YLTY66", risk: 3, allocation: 0 }
    ]
  });
  assert.equal(parsed.items.length, 2);
});

test("duplicate ids are rejected", () => {
  assert.throws(() => validateConfig({
    marketLight: "GELB",
    budget: 100,
    items: [
      { id: "same", type: "AKTIE", ticker: "A", marketSymbol: "A:NYSE", risk: 2 },
      { id: "same", type: "AKTIE", ticker: "B", marketSymbol: "B:NYSE", risk: 2 }
    ]
  }), /duplicate/i);
});
