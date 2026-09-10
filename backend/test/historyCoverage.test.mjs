import test from 'node:test';
import assert from 'node:assert/strict';
import { calculateMomentum } from '../src/lib/historySupport.mjs';

const DAY = 86_400_000;
const now = Date.UTC(2026, 8, 10);

function dailySeries(days, start = 100, dailyStep = 0.1) {
  return Array.from({ length: days }, (_, index) => ({
    time: now - (days - 1 - index) * DAY,
    close: start + index * dailyStep
  }));
}

test('230+ valid daily points over roughly one year yield high history coverage', () => {
  const result = calculateMomentum(dailySeries(260), now);
  assert.equal(result.pointsCount, 260);
  assert.ok(result.coveragePct >= 85);
  assert.ok(result.m6 != null);
  assert.ok(result.oldestPointAt);
  assert.ok(result.newestPointAt);
});

test('20-point series does not fabricate long-horizon momentum', () => {
  const result = calculateMomentum(dailySeries(20), now);
  assert.equal(result.pointsCount, 20);
  assert.equal(result.m3, null);
  assert.equal(result.m6, null);
  assert.equal(result.m12, null);
  assert.ok(result.coveragePct < 25);
});

test('12M return requires observations spanning almost the full lookback', () => {
  const result = calculateMomentum(dailySeries(250), now);
  assert.equal(result.m12, null);
});
