import test from "node:test";
import assert from "node:assert/strict";
import { evaluateSignals } from "../src/lib/signals.mjs";

test("held asset score deterioration triggers REVIEW", () => {
  const items = [{
    id: "msft", name: "Microsoft", status: "BEOBACHTEN", recommendation: "WATCH",
    scoreTotal: 58, risk: 2, reviewDrop1dPct: 7, hardReviewBelow: 300,
    percentChange: -1, price: 450, currency: "USD",
    momentum: { m3: -8, m6: -10 }
  }];
  const signals = evaluateSignals(items, new Map(), { previousScores: { msft: 76 }, heldIds: new Set(["msft"]) });
  assert.ok(signals.some((signal) => signal.level === "REVIEW" && signal.itemId === "msft"));
});

test("new BUY transition can notify globally", () => {
  const items = [{
    id: "a", name: "Asset A", status: "KAUFEN", recommendation: "BUY",
    scoreTotal: 82, risk: 2, reviewDrop1dPct: 7, hardReviewBelow: null
  }];
  const signals = evaluateSignals(items, new Map(), { previousRecommendations: { a: "WATCH" } });
  assert.ok(signals.some((signal) => signal.level === "BUY" && signal.itemId === "a"));
});

test("existing BUY state does not emit a fresh BUY transition", () => {
  const items = [{ id: "a", name: "Asset A", status: "KAUFEN", recommendation: "BUY", scoreTotal: 82, risk: 2 }];
  const signals = evaluateSignals(items, new Map(), { previousRecommendations: { a: "BUY" } });
  assert.equal(signals.some((signal) => signal.level === "BUY"), false);
});
