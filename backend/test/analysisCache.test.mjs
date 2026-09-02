import test from "node:test";
import assert from "node:assert/strict";
import { isFresh } from "../src/lib/analysisCache.mjs";

test("fresh cache entry is reused inside ttl", () => {
  const now = Date.UTC(2026, 8, 2, 10, 0, 0);
  const entry = { fetchedAt: new Date(now - 60_000).toISOString() };
  assert.equal(isFresh(entry, 5 * 60_000, now), true);
});

test("expired cache entry is not fresh", () => {
  const now = Date.UTC(2026, 8, 2, 10, 0, 0);
  const entry = { fetchedAt: new Date(now - 7 * 60_000).toISOString() };
  assert.equal(isFresh(entry, 5 * 60_000, now), false);
});
