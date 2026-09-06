import test from "node:test";
import assert from "node:assert/strict";
import { createMarketEventsHandler } from "../src/functions/marketEvents.mjs";

const event = {
  providerId: "issuer-feed",
  providerEventId: "release-123",
  instrumentId: "msft",
  type: "GUIDANCE",
  title: "Guidance updated",
  summary: "Issuer updated its guidance.",
  eventAt: "2026-09-05T12:00:00.000Z",
  publishedAt: "2026-09-05T12:05:00.000Z",
  sourceName: "Issuer",
  sourceUrl: "https://issuer.example/release-123",
  direction: "POSITIVE",
  horizon: "MEDIUM",
  materiality: 82,
  confidencePct: 96
};

function request(ids) {
  return { url: `https://api.example/api/market-events?ids=${encodeURIComponent(ids)}` };
}

function context() {
  return { errors: [], error(value) { this.errors.push(value); } };
}

test("market events handler returns normalized contract for configured providers", async () => {
  const handler = createMarketEventsHandler({
    buildProviders: () => ({
      providers: [{ providerId: "issuer-feed", load: async () => [event] }],
      trustedSources: new Map([["issuer-feed", { verification: "VERIFIED_PRIMARY" }]])
    }),
    now: () => new Date("2026-09-06T17:10:00.000Z")
  });

  const response = await handler(request("msft"), context());

  assert.equal(response.status, 200);
  assert.equal(response.headers["Cache-Control"], "no-store");
  assert.equal(response.headers["Access-Control-Allow-Origin"], "*");
  assert.equal(response.jsonBody.generatedAt, "2026-09-06T17:10:00.000Z");
  assert.equal(response.jsonBody.items.length, 1);
  assert.equal(response.jsonBody.items[0].verification, "VERIFIED_PRIMARY");
  assert.deepEqual(response.jsonBody.errors, []);
});

test("market events handler rejects more than 40 requested instruments", async () => {
  const ids = Array.from({ length: 41 }, (_, index) => `id-${index}`).join(",");
  const handler = createMarketEventsHandler({
    buildProviders: () => ({ providers: [], trustedSources: new Map() })
  });

  const response = await handler(request(ids), context());

  assert.equal(response.status, 400);
  assert.match(response.jsonBody.error, /höchstens 40/i);
});

test("market events handler keeps provider failures explicit without fabricating events", async () => {
  const handler = createMarketEventsHandler({
    buildProviders: () => ({
      providers: [{
        providerId: "wire",
        load: async () => { throw new Error("wire timeout"); }
      }],
      trustedSources: new Map([["wire", { verification: "VERIFIED_SECONDARY" }]])
    }),
    now: () => new Date("2026-09-06T17:10:00.000Z")
  });

  const response = await handler(request("msft"), context());

  assert.equal(response.status, 200);
  assert.deepEqual(response.jsonBody.items, []);
  assert.deepEqual(response.jsonBody.errors, [
    { providerId: "wire", code: "PROVIDER_FAILED", message: "wire timeout" }
  ]);
});
