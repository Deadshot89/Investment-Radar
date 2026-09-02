import test from "node:test";
import assert from "node:assert/strict";
import { normalizeState } from "../src/lib/state.mjs";

test("v2 state preserves previous scores and recommendations", () => {
  const state = normalizeState({
    activeFingerprints: ["a"],
    recent: [{ id: "1" }],
    previousScores: { msft: 81 },
    previousRecommendations: { msft: "BUY" }
  });
  assert.equal(state.previousScores.msft, 81);
  assert.equal(state.previousRecommendations.msft, "BUY");
});

test("legacy state migrates with empty v2 maps", () => {
  const state = normalizeState({ fingerprints: { msft: "abc" }, recent: [] });
  assert.deepEqual(state.activeFingerprints, ["abc"]);
  assert.deepEqual(state.previousScores, {});
  assert.deepEqual(state.previousRecommendations, {});
});
