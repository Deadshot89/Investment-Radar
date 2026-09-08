import test from "node:test";
import assert from "node:assert/strict";
import { selectBuyFilterResults } from "../src/lib/radarBuyFallback.mjs";

test("BUY filter falls back to at most three strong verified WATCH candidates", () => {
  const analyzed = [
    { id: "blocked", recommendation: "NO_BUY", purchaseEligible: false, scoreTotal: 95, coverage: 100, risk: 1, tradeRepublicEligible: true, name: "Blocked", recommendationReasons: [] },
    { id: "best", recommendation: "WATCH", purchaseEligible: false, scoreTotal: 84, coverage: 90, risk: 2, tradeRepublicEligible: true, name: "Best", recommendationReasons: [] },
    { id: "second", recommendation: "WATCH", purchaseEligible: false, scoreTotal: 79, coverage: 80, risk: 3, tradeRepublicEligible: true, name: "Second", recommendationReasons: [] },
    { id: "third", recommendation: "WATCH", purchaseEligible: false, scoreTotal: 76, coverage: 75, risk: 2, tradeRepublicEligible: true, name: "Third", recommendationReasons: [] },
    { id: "weak", recommendation: "WATCH", purchaseEligible: false, scoreTotal: 61, coverage: 90, risk: 1, tradeRepublicEligible: true, name: "Weak", recommendationReasons: [] },
    { id: "unverified", recommendation: "WATCH", purchaseEligible: false, scoreTotal: 99, coverage: 99, risk: 1, tradeRepublicEligible: null, name: "Unverified", recommendationReasons: [] }
  ];

  const result = selectBuyFilterResults(analyzed);
  assert.equal(result.buyFallbackActive, true);
  assert.deepEqual(result.items.map((item) => item.id), ["best", "second", "third"]);
  assert.ok(result.items.every((item) => item.recommendationReasons.includes("Kaufkandidat – noch nicht bestätigt")));
});

test("BUY filter never mixes fallback WATCH values when a real BUY exists", () => {
  const result = selectBuyFilterResults([
    { id: "buy", recommendation: "BUY", purchaseEligible: true, scoreTotal: 70, coverage: 70, risk: 3, tradeRepublicEligible: true, name: "Buy", recommendationReasons: [] },
    { id: "watch", recommendation: "WATCH", purchaseEligible: false, scoreTotal: 99, coverage: 99, risk: 1, tradeRepublicEligible: true, name: "Watch", recommendationReasons: [] }
  ]);
  assert.equal(result.buyFallbackActive, false);
  assert.deepEqual(result.items.map((item) => item.id), ["buy"]);
});
