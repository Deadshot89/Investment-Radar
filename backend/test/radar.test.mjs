import test from "node:test";
import assert from "node:assert/strict";
import { applyFilters, buildFacets, queryRadar } from "../src/lib/radar.mjs";
import { resetRadarAnalysisCache } from "../src/lib/radarAnalysisCache.mjs";

const base = [
  { id: "a", type: "AKTIE", name: "Alpha Tech", ticker: "AAA", isin: "US0000000001", region: "NORTH_AMERICA", country: "US", sector: "Technology", risk: 2, dataQualityTier: "A", universeActive: true, portfolioOnly: false, tradeRepublicEligible: true },
  { id: "b", type: "ETF", name: "Beta World ETF", ticker: "BBB", isin: "IE0000000002", region: "GLOBAL", country: "IE", sector: "", risk: 1, dataQualityTier: "A", universeActive: true, portfolioOnly: false, tradeRepublicEligible: true },
  { id: "c", type: "AKTIE", name: "Gamma Bank", ticker: "CCC", isin: "DE0000000003", region: "EUROPE", country: "DE", sector: "Financials", risk: 4, dataQualityTier: "B", universeActive: true, portfolioOnly: false, tradeRepublicEligible: null },
  { id: "p", type: "AKTIE", name: "Portfolio Only", ticker: "PPP", isin: "NO0000000004", region: "EUROPE", country: "NO", sector: "Industrials", risk: 5, dataQualityTier: "A", universeActive: true, portfolioOnly: true, tradeRepublicEligible: true }
];

const strongFundamentals = {
  qualityScore: 88,
  valuationScore: 78,
  growthScore: 82,
  coveragePct: 100,
  metrics: {}
};
const strongMomentum = { d1: 2, m1: 8, m3: 15, m6: 20, m12: 30, score: 80, coveragePct: 100 };

test("applyFilters searches name ticker and ISIN and supports type/region/risk", () => {
  assert.deepEqual(applyFilters(base, { query: "gamma" }).map((x) => x.id), ["c"]);
  assert.deepEqual(applyFilters(base, { query: "BBB" }).map((x) => x.id), ["b"]);
  assert.deepEqual(applyFilters(base, { query: "DE0000000003" }).map((x) => x.id), ["c"]);
  assert.deepEqual(applyFilters(base, { type: "ETF" }).map((x) => x.id), ["b"]);
  assert.deepEqual(applyFilters(base, { region: "EUROPE", riskMax: 4 }).map((x) => x.id), ["c"]);
});

test("buildFacets exposes counts for discovery filters", () => {
  const facets = buildFacets(base.filter((x) => !x.portfolioOnly));
  assert.equal(facets.types.find((x) => x.value === "AKTIE").count, 2);
  assert.equal(facets.regions.find((x) => x.value === "EUROPE").count, 1);
});

test("queryRadar excludes portfolioOnly and pages without analyzing all universe items", async () => {
  const analyzedIds = [];
  const loadMap = async (items) => {
    analyzedIds.push(...items.map((x) => x.id));
    return new Map(items.map((item) => [item.id, { price: 100, currency: "EUR", percentChange: 1, source: "TEST", delayed: false }]));
  };
  const result = await queryRadar({ page: 1, pageSize: 2 }, {
    loadUniverse: async () => base,
    loadQuotes: loadMap,
    loadHistory: async (items) => new Map(items.map((item) => [item.id, { score: 50, coveragePct: 100 }])),
    loadFundamentals: async (items) => new Map(items.map((item) => [item.id, { metrics: {}, coveragePct: 100 }])),
    loadEurRateDetails: async () => new Map()
  });
  assert.equal(result.total, 3);
  assert.equal(result.items.length, 2);
  assert.equal(result.hasMore, true);
  assert.equal(analyzedIds.length, 2);
  assert.equal(result.items.some((x) => x.id === "p"), false);
});

test("unverified Trade Republic item cannot become purchaseEligible even if scoring says BUY", async () => {
  const candidate = [{ ...base[2], risk: 1 }];
  const result = await queryRadar({}, {
    loadUniverse: async () => candidate,
    loadQuotes: async () => new Map([["c", { price: 100, currency: "EUR", percentChange: 2, source: "TEST" }]]),
    loadHistory: async () => new Map([["c", strongMomentum]]),
    loadFundamentals: async () => new Map([["c", strongFundamentals]]),
    loadEurRateDetails: async () => new Map()
  });
  assert.equal(result.items[0].purchaseEligible, false);
  assert.notEqual(result.items[0].recommendation, "BUY");
});

test("BUY filter is applied after analysis and never promotes unverified candidates", async () => {
  resetRadarAnalysisCache();
  const candidates = [
    { ...base[0], risk: 1, tradeRepublicEligible: true },
    { ...base[2], risk: 1, tradeRepublicEligible: null }
  ];
  const result = await queryRadar({ recommendation: "BUY", page: 1, pageSize: 40 }, {
    loadUniverse: async () => candidates,
    loadQuotes: async (items) => new Map(items.map((item) => [item.id, { price: 100, currency: "EUR", percentChange: 0.8, source: "TEST" }])),
    loadHistory: async (items) => new Map(items.map((item) => [item.id, strongMomentum])),
    loadFundamentals: async (items) => new Map(items.map((item) => [item.id, strongFundamentals])),
    loadEurRateDetails: async () => new Map()
  });
  assert.equal(result.total, 1);
  assert.deepEqual(result.items.map((item) => item.id), ["a"]);
  assert.equal(result.items[0].recommendation, "BUY");
  assert.equal(result.items[0].purchaseEligible, true);
});

test("strong verified external ETF can become purchaseEligible without stock fundamentals", async () => {
  resetRadarAnalysisCache();
  const etf = { ...base[1], etfStructureScore: 88, risk: 1 };
  const result = await queryRadar({ recommendation: "BUY" }, {
    loadUniverse: async () => [etf],
    loadQuotes: async () => new Map([["b", { price: 100, currency: "EUR", percentChange: 0.5, source: "TEST" }]]),
    loadHistory: async () => new Map([["b", { ...strongMomentum, score: 90 }]]),
    loadFundamentals: async () => new Map([["b", { metrics: {}, coveragePct: 0 }]]),
    loadEurRateDetails: async () => new Map()
  });
  assert.equal(result.total, 1);
  assert.equal(result.items[0].type, "ETF");
  assert.equal(result.items[0].purchaseEligible, true);
  assert.equal(result.items[0].recommendation, "BUY");
  assert.ok(result.items[0].coverage >= 60);
});

test("recommendation queries reuse one analyzed universe snapshot inside TTL", async () => {
  resetRadarAnalysisCache();
  let quoteLoads = 0;
  const overrides = {
    loadUniverse: async () => [{ ...base[0], risk: 1 }],
    loadQuotes: async (items) => {
      quoteLoads += 1;
      return new Map(items.map((item) => [item.id, { price: 100, currency: "EUR", percentChange: 0.5, source: "TEST" }]));
    },
    loadHistory: async (items) => new Map(items.map((item) => [item.id, strongMomentum])),
    loadFundamentals: async (items) => new Map(items.map((item) => [item.id, strongFundamentals])),
    loadEurRateDetails: async () => new Map(),
    now: 1_000,
    analysisTtlMs: 10_000
  };

  await queryRadar({ recommendation: "BUY" }, overrides);
  await queryRadar({ recommendation: "WATCH" }, { ...overrides, now: 2_000 });

  assert.equal(quoteLoads, 1);
});

test("includeCounts returns total stock ETF buy watch and review counts for the filtered universe", async () => {
  resetRadarAnalysisCache();
  const candidates = [
    { ...base[0], risk: 1 },
    { ...base[1], etfStructureScore: 70, risk: 2 },
    base[2]
  ];
  const result = await queryRadar({ includeCounts: true, pageSize: 2 }, {
    loadUniverse: async () => candidates,
    loadQuotes: async (items) => new Map(items.map((item) => [item.id, { price: 100, currency: "EUR", percentChange: 0.5, source: "TEST" }])),
    loadHistory: async (items) => new Map(items.map((item) => [item.id, item.id === "a" ? strongMomentum : { ...strongMomentum, score: 50 }])),
    loadFundamentals: async (items) => new Map(items.map((item) => [item.id, item.id === "a" ? strongFundamentals : { metrics: {}, coveragePct: 0 }])),
    loadEurRateDetails: async () => new Map()
  });

  assert.deepEqual(result.counts, {
    total: 3,
    stocks: 2,
    etfs: 1,
    buy: 1,
    watch: 1,
    noBuy: 0,
    review: 1
  });
});
