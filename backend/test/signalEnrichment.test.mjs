import test from "node:test";
import assert from "node:assert/strict";
import { evaluateSignals } from "../src/lib/signals.mjs";
import { updateAnalysisMemory } from "../src/lib/marketWatchState.mjs";

test("large positive daily move creates an explained threshold alert", () => {
  const item = {
    id: "move", name: "Move AG", recommendation: "WATCH", scoreTotal: 62,
    reviewDrop1dPct: 7, percentChange: 8.4, currency: "EUR",
    momentum: { m1: 6.2, m3: 4.0, m6: 2.0, m12: 3.0 }
  };
  const signals = evaluateSignals([item], new Map());
  const alert = signals.find((signal) => signal.title.includes("Tagesanstieg"));
  assert.ok(alert);
  assert.equal(alert.level, "THRESHOLD");
  assert.match(alert.message, /\+8\.40 %/);
  assert.match(alert.message, /Warum:/);
});

test("12M forecast direction flip creates a dedicated explained alert", () => {
  const item = {
    id: "flip", name: "Flip SE", recommendation: "WATCH", scoreTotal: 55,
    scoreQuality: 50, scoreValuation: 50, scoreGrowth: 50, scoreRisk: 50,
    risk: 2, coverage: 90,
    momentum: { m12: -10 },
    fundamentals: { coveragePct: 90, stale: false }
  };
  const signals = evaluateSignals([item], new Map(), {
    previousForecast12m: { flip: { expectedChangePct: 5.0, direction: "UP" } }
  });
  const alert = signals.find((signal) => signal.title.includes("12M-Prognose"));
  assert.ok(alert);
  assert.equal(alert.level, "REVIEW");
  assert.match(alert.message, /\+5\.0 %/);
  assert.match(alert.message, /-3\.4 %/);
  assert.match(alert.message, /Pessimistisch:/);
  assert.match(alert.message, /Erwartet:/);
  assert.match(alert.message, /Optimistisch:/);
  assert.match(alert.message, /Warum:/);
  assert.match(alert.message, /Momentum/);
});

test("analysis memory stores the current 12M forecast for change detection", () => {
  const state = updateAnalysisMemory({}, [{
    id: "asset", recommendation: "WATCH", scoreTotal: 55,
    scoreQuality: 50, scoreValuation: 50, scoreGrowth: 50, scoreRisk: 50,
    risk: 2, coverage: 90, momentum: { m12: -10 }, fundamentals: null
  }]);
  assert.equal(state.previousForecast12m.asset.direction, "DOWN");
  assert.equal(state.previousForecast12m.asset.expectedChangePct, -3.4);
});
