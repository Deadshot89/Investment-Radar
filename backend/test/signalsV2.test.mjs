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

test("missing price never becomes zero for hard review threshold", () => {
  const items = [{
    id: "missing", name: "Missing Quote", recommendation: "WATCH",
    hardReviewBelow: 100, price: null, percentChange: null
  }];
  const signals = evaluateSignals(items, new Map());
  assert.equal(signals.some((signal) => signal.title.includes("Kurs-Schwelle")), false);
});

test("missing prior score does not become zero", () => {
  const items = [{ id: "held", name: "Held", recommendation: "WATCH", scoreTotal: 10 }];
  const signals = evaluateSignals(items, new Map(), { previousScores: { held: null }, heldIds: new Set(["held"]) });
  assert.equal(signals.some((signal) => signal.title.includes("Score deutlich gefallen")), false);
});

test("absolute price threshold is categorized as THRESHOLD", () => {
  const items = [{ id: "x", name: "Asset X", recommendation: "WATCH", hardReviewBelow: 100, price: 90, currency: "EUR" }];
  const signals = evaluateSignals(items, new Map());
  assert.ok(signals.some((signal) => signal.level === "THRESHOLD" && signal.title.includes("Kurs-Schwelle")));
});

test("daily drop threshold is categorized as THRESHOLD", () => {
  const items = [{ id: "x", name: "Asset X", recommendation: "WATCH", reviewDrop1dPct: 7, percentChange: -8 }];
  const signals = evaluateSignals(items, new Map());
  assert.ok(signals.some((signal) => signal.level === "THRESHOLD" && signal.title.includes("Tagesverlust") && signal.message.includes("-8.00 %")));
});
