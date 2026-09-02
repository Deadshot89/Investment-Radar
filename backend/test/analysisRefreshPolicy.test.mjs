import test from "node:test";
import assert from "node:assert/strict";
import { loadHistory } from "../src/lib/history.mjs";
import { loadFundamentals } from "../src/lib/fundamentals.mjs";

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
