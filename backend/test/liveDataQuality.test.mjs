import test from "node:test";
import assert from "node:assert/strict";
import { assessLiveRadarResponse } from "../src/lib/liveDataQuality.mjs";

function item(overrides = {}) {
  return {
    id: "msft",
    name: "Microsoft",
    ticker: "MSFT",
    isin: "US5949181045",
    type: "AKTIE",
    price: 420,
    currency: "USD",
    scoreTotal: 82,
    coverage: 88,
    recommendation: "WATCH",
    purchaseEligible: false,
    dataError: null,
    dataQuality: {
      overallCoverage: 88,
      missingBlocks: [],
      criticalConflicts: []
    },
    ...overrides
  };
}

test("live radar quality accepts healthy representative data", () => {
  const items = Array.from({ length: 10 }, (_, index) => item({ id: `id-${index}`, ticker: `T${index}`, isin: `US00000000${index}` }));
  const result = assessLiveRadarResponse({ universeTotal: 2400, items });
  assert.equal(result.ok, true);
  assert.equal(result.metrics.quoteCoveragePct, 100);
  assert.equal(result.metrics.analysisCoveragePct, 100);
  assert.equal(result.metrics.unsafeBuyCount, 0);
});

test("live radar quality rejects a mostly empty market-data sample", () => {
  const items = Array.from({ length: 10 }, (_, index) => item({
    id: `id-${index}`,
    ticker: `T${index}`,
    isin: `US00000000${index}`,
    price: index < 5 ? 100 : null,
    currency: index < 5 ? "EUR" : "",
    scoreTotal: index < 6 ? 70 : null,
    coverage: index < 6 ? 75 : 0
  }));
  const result = assessLiveRadarResponse({ universeTotal: 2400, items });
  assert.equal(result.ok, false);
  assert.ok(result.failures.some((value) => value.startsWith("quotes ")));
  assert.ok(result.failures.some((value) => value.startsWith("analysis ")));
});

test("purchase eligible items must never bypass the data-quality gate", () => {
  const items = Array.from({ length: 10 }, (_, index) => item({ id: `id-${index}`, ticker: `T${index}`, isin: `US00000000${index}` }));
  items[0] = item({
    id: "unsafe",
    ticker: "BAD",
    isin: "US0000000099",
    recommendation: "BUY",
    purchaseEligible: true,
    price: null,
    currency: "",
    dataQuality: { overallCoverage: 82, missingBlocks: ["quote"], criticalConflicts: [] }
  });
  const result = assessLiveRadarResponse({ universeTotal: 2400, items });
  assert.equal(result.ok, false);
  assert.equal(result.metrics.unsafeBuyCount, 1);
  assert.ok(result.failures.includes("unsafe BUY candidates 1"));
});
