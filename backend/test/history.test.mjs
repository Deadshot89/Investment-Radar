import test from "node:test";
import assert from "node:assert/strict";
import { calculateMomentum } from "../src/lib/historySupport.mjs";

const DAY = 86_400_000;
const now = Date.UTC(2026, 8, 2);

function marketLikeSeries(days) {
  const points = [];
  let cursor = now - 390 * DAY;
  let price = 80;
  while (cursor <= now && points.length < days) {
    const weekday = new Date(cursor).getUTCDay();
    if (weekday !== 0 && weekday !== 6) {
      points.push({ time: cursor, close: price });
      price += 0.18;
    }
    cursor += DAY;
  }
  if (points.at(-1)?.time < now) points.push({ time: now, close: price });
  return points;
}

const points = marketLikeSeries(280);

test("momentum exposes all five horizons with sufficient observations", () => {
  const result = calculateMomentum(points, now);
  assert.ok(result.d1 >= 0);
  assert.ok(result.m1 > 0);
  assert.ok(result.m3 > 0);
  assert.ok(result.m6 > 0);
  assert.ok(result.m12 > 0);
  assert.ok(result.coveragePct >= 85);
  assert.ok(result.pointsCount >= 250);
});

test("missing old history reduces coverage instead of inventing returns", () => {
  const result = calculateMomentum(points.slice(-20), now);
  assert.equal(result.m12, null);
  assert.equal(result.m6, null);
  assert.ok(result.coveragePct < 25);
});

test("broad positive momentum scores above neutral", () => {
  const result = calculateMomentum(points, now);
  assert.ok(result.score > 50);
});
