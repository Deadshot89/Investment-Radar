import test from "node:test";
import assert from "node:assert/strict";
import {
  getRadarAnalysisSnapshot,
  resetRadarAnalysisCache
} from "../src/lib/radarAnalysisCache.mjs";

test("radar analysis snapshot is reused for the same universe inside TTL", async () => {
  resetRadarAnalysisCache();
  let loads = 0;
  const load = async () => {
    loads += 1;
    return [{ id: "a", recommendation: "BUY", purchaseEligible: true }];
  };

  const first = await getRadarAnalysisSnapshot({ key: "a|b", now: 1_000, ttlMs: 10_000, load });
  const second = await getRadarAnalysisSnapshot({ key: "a|b", now: 2_000, ttlMs: 10_000, load });

  assert.equal(loads, 1);
  assert.equal(first.cacheHit, false);
  assert.equal(second.cacheHit, true);
  assert.deepEqual(second.items.map((item) => item.id), ["a"]);
});

test("radar analysis snapshot reloads after TTL or when universe key changes", async () => {
  resetRadarAnalysisCache();
  let loads = 0;
  const load = async () => {
    loads += 1;
    return [{ id: `load-${loads}` }];
  };

  await getRadarAnalysisSnapshot({ key: "a", now: 1_000, ttlMs: 1_000, load });
  const expired = await getRadarAnalysisSnapshot({ key: "a", now: 2_001, ttlMs: 1_000, load });
  const changed = await getRadarAnalysisSnapshot({ key: "b", now: 2_100, ttlMs: 1_000, load });

  assert.equal(loads, 3);
  assert.equal(expired.cacheHit, false);
  assert.equal(changed.cacheHit, false);
});

test("parallel requests share one in-flight radar analysis", async () => {
  resetRadarAnalysisCache();
  let loads = 0;
  let release;
  const blocker = new Promise((resolve) => { release = resolve; });
  const load = async () => {
    loads += 1;
    await blocker;
    return [{ id: "shared" }];
  };

  const a = getRadarAnalysisSnapshot({ key: "same", now: 1_000, ttlMs: 10_000, load });
  const b = getRadarAnalysisSnapshot({ key: "same", now: 1_000, ttlMs: 10_000, load });
  release();
  const [first, second] = await Promise.all([a, b]);

  assert.equal(loads, 1);
  assert.deepEqual(first.items, second.items);
});
