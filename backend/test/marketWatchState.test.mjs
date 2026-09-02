import test from "node:test";
import assert from "node:assert/strict";
import { updateAnalysisMemory, mergeRecentAlerts } from "../src/lib/marketWatchState.mjs";

test("current scores and recommendations become the next comparison baseline", () => {
  const next = updateAnalysisMemory(
    { activeFingerprints: ["old"], recent: [], previousScores: { a: 60 }, previousRecommendations: { a: "WATCH" } },
    [
      { id: "a", scoreTotal: 82, recommendation: "BUY" },
      { id: "b", scoreTotal: null, recommendation: "WATCH" }
    ]
  );
  assert.equal(next.previousScores.a, 82);
  assert.equal("b" in next.previousScores, false);
  assert.equal(next.previousRecommendations.a, "BUY");
  assert.equal(next.previousRecommendations.b, "WATCH");
});

test("recent alerts are deduplicated and capped", () => {
  const existing = Array.from({ length: 50 }, (_, index) => ({ id: `old-${index}` }));
  const merged = mergeRecentAlerts([{ id: "new" }, { id: "old-0" }], existing);
  assert.equal(merged[0].id, "new");
  assert.equal(merged.filter((alert) => alert.id === "old-0").length, 1);
  assert.equal(merged.length, 50);
});
