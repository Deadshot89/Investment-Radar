import test from "node:test";
import assert from "node:assert/strict";
import { calculateMomentum } from "../src/lib/historySupport.mjs";

const DAY = 86_400_000;
const now = Date.UTC(2026, 8, 2);
const points = [
  { time: now - 370 * DAY, close: 80 },
  { time: now - 185 * DAY, close: 90 },
  { time: now - 95 * DAY, close: 100 },
  { time: now - 32 * DAY, close: 110 },
  { time: now - DAY, close: 119 },
  { time: now, close: 120 }
];

test("momentum exposes all five horizons", () => {
  const result = calculateMomentum(points, now);
  assert.ok(result.d1 > 0);
  assert.ok(result.m1 > 0);
  assert.ok(result.m3 > 0);
  assert.ok(result.m6 > 0);
  assert.ok(result.m12 > 0);
  assert.equal(result.coveragePct, 100);
});

test("missing old history reduces coverage instead of inventing returns", () => {
  const result = calculateMomentum(points.slice(-3), now);
  assert.equal(result.m12, null);
  assert.ok(result.coveragePct < 100);
});

test("broad positive momentum scores above neutral", () => {
  const result = calculateMomentum(points, now);
  assert.ok(result.score > 50);
});
