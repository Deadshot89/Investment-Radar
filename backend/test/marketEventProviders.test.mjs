import test from "node:test";
import assert from "node:assert/strict";
import { buildConfiguredMarketEventProviders } from "../src/lib/marketEventProviders.mjs";

test("empty provider configuration produces no trusted providers", () => {
  const configured = buildConfiguredMarketEventProviders("");

  assert.deepEqual(configured.providers, []);
  assert.equal(configured.trustedSources.size, 0);
});

test("only explicit https providers with allowed verification are trusted", () => {
  const configured = buildConfiguredMarketEventProviders(JSON.stringify([
    {
      providerId: "primary-feed",
      endpoint: "https://events.example/api/events",
      verification: "VERIFIED_PRIMARY"
    },
    {
      providerId: "secondary-feed",
      endpoint: "https://wire.example/events",
      verification: "VERIFIED_SECONDARY"
    },
    {
      providerId: "unsafe-http",
      endpoint: "http://unsafe.example/events",
      verification: "VERIFIED_PRIMARY"
    },
    {
      providerId: "self-claimed",
      endpoint: "https://unknown.example/events",
      verification: "VERIFIED_SUPER_SOURCE"
    }
  ]));

  assert.deepEqual(configured.providers.map((item) => item.providerId), ["primary-feed", "secondary-feed"]);
  assert.equal(configured.trustedSources.get("primary-feed").verification, "VERIFIED_PRIMARY");
  assert.equal(configured.trustedSources.get("secondary-feed").verification, "VERIFIED_SECONDARY");
  assert.equal(configured.trustedSources.has("unsafe-http"), false);
  assert.equal(configured.trustedSources.has("self-claimed"), false);
});

test("configured provider forwards bounded ids and forces its own provider identity", async () => {
  const requests = [];
  const fetchImpl = async (url, options) => {
    requests.push({ url, options });
    return {
      ok: true,
      status: 200,
      json: async () => ({
        items: [
          {
            providerId: "spoofed-provider",
            providerEventId: "evt-1",
            instrumentId: "msft",
            type: "GUIDANCE",
            title: "Guidance updated",
            summary: "",
            eventAt: "2026-09-05T12:00:00.000Z",
            publishedAt: "2026-09-05T12:05:00.000Z",
            sourceName: "Issuer",
            sourceUrl: "https://issuer.example/release",
            direction: "POSITIVE",
            horizon: "MEDIUM",
            materiality: 80,
            confidencePct: 95
          }
        ]
      })
    };
  };

  const configured = buildConfiguredMarketEventProviders(JSON.stringify([
    {
      providerId: "issuer-feed",
      endpoint: "https://events.example/api/events",
      verification: "VERIFIED_PRIMARY"
    }
  ]), { fetchImpl });

  const items = await configured.providers[0].load(["msft", "meta"]);

  assert.equal(requests.length, 1);
  assert.match(requests[0].url, /^https:\/\/events\.example\/api\/events\?ids=msft%2Cmeta$/);
  assert.equal(requests[0].options.headers.Accept, "application/json");
  assert.equal(items[0].providerId, "issuer-feed");
});

test("provider non-2xx response becomes an explicit loader failure", async () => {
  const configured = buildConfiguredMarketEventProviders(JSON.stringify([
    {
      providerId: "wire",
      endpoint: "https://wire.example/events",
      verification: "VERIFIED_SECONDARY"
    }
  ]), {
    fetchImpl: async () => ({ ok: false, status: 503 })
  });

  await assert.rejects(
    configured.providers[0].load(["msft"]),
    /HTTP 503/
  );
});
