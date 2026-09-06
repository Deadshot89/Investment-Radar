import test from "node:test";
import assert from "node:assert/strict";
import {
  normalizeMarketEvents,
  buildEventFingerprint
} from "../src/lib/marketEvents.mjs";

const trustedSources = new Map([
  ["issuer-feed", { verification: "VERIFIED_PRIMARY" }],
  ["trusted-wire", { verification: "VERIFIED_SECONDARY" }]
]);

function event(overrides = {}) {
  return {
    providerId: "issuer-feed",
    providerEventId: "release-123",
    instrumentId: "msft",
    type: "GUIDANCE",
    title: "Guidance updated",
    summary: "Issuer updated its guidance.",
    eventAt: "2026-09-05T12:00:00.000Z",
    publishedAt: "2026-09-05T12:05:00.000Z",
    sourceName: "Microsoft Investor Relations",
    sourceUrl: "https://www.microsoft.com/en-us/Investor/release-123",
    direction: "POSITIVE",
    horizon: "MEDIUM",
    materiality: 82,
    confidencePct: 96,
    ...overrides
  };
}

test("stable provider identity creates the same fingerprint independent of title whitespace", () => {
  const first = buildEventFingerprint(event());
  const second = buildEventFingerprint(event({ title: "  Guidance updated again  " }));

  assert.equal(first, second);
  assert.match(first, /^msft\|guidance\|issuer-feed\|release-123$/);
});

test("normalization rejects events without instrument source identity or publication time", () => {
  const result = normalizeMarketEvents([
    event({ instrumentId: "" }),
    event({ providerEventId: "" }),
    event({ sourceUrl: "" }),
    event({ publishedAt: "" })
  ], { trustedSources });

  assert.deepEqual(result, []);
});

test("unknown source cannot promote itself to verified", () => {
  const [normalized] = normalizeMarketEvents([
    event({
      providerId: "unknown-blog",
      providerEventId: "claim-1",
      claimedVerification: "VERIFIED_PRIMARY",
      sourceName: "Unknown Blog",
      sourceUrl: "https://unknown.example/claim-1"
    })
  ], { trustedSources });

  assert.equal(normalized.verification, "UNVERIFIED");
});

test("trusted source policy assigns primary and secondary verification explicitly", () => {
  const normalized = normalizeMarketEvents([
    event(),
    event({
      providerId: "trusted-wire",
      providerEventId: "wire-55",
      sourceName: "Trusted Wire",
      sourceUrl: "https://wire.example/items/55"
    })
  ], { trustedSources });

  assert.deepEqual(
    normalized.map((item) => item.verification),
    ["VERIFIED_PRIMARY", "VERIFIED_SECONDARY"]
  );
});

test("duplicate provider event is stored once and keeps original source identity", () => {
  const source = event();
  const normalized = normalizeMarketEvents([
    source,
    { ...source, title: "Duplicate headline from same release" }
  ], { trustedSources });

  assert.equal(normalized.length, 1);
  assert.equal(normalized[0].eventId, "issuer-feed:release-123");
  assert.equal(normalized[0].sourceUrl, source.sourceUrl);
  assert.equal(normalized[0].fingerprint, "msft|guidance|issuer-feed|release-123");
});

test("normalization clamps materiality and confidence but does not invent content", () => {
  const [normalized] = normalizeMarketEvents([
    event({ materiality: 150, confidencePct: -8, summary: "" })
  ], { trustedSources });

  assert.equal(normalized.materiality, 100);
  assert.equal(normalized.confidencePct, 0);
  assert.equal(normalized.summary, "");
});
