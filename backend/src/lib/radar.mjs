import { loadUniverse as defaultLoadUniverse } from "./universe.mjs";
import { loadQuotes as defaultLoadQuotes, loadEurRateDetails as defaultLoadEurRateDetails, priceInEur } from "./market.mjs";
import { loadHistory as defaultLoadHistory } from "./history.mjs";
import { loadFundamentals as defaultLoadFundamentals } from "./fundamentals.mjs";
import { scoreInvestment } from "./scoring.mjs";
import { getRadarAnalysisSnapshot, radarAnalysisKey } from "./radarAnalysisCache.mjs";

const DEFAULT_PAGE_SIZE = 40;
const MAX_PAGE_SIZE = 100;

export async function queryRadar(query = {}, overrides = {}) {
  const loadUniverse = overrides.loadUniverse ?? defaultLoadUniverse;
  const universe = await loadUniverse({ refresh: Boolean(query.refresh), ...(overrides.universeOptions ?? {}) });
  const active = universe.filter((item) => item.universeActive !== false && !item.portfolioOnly);
  const filtered = applyFilters(active, query);
  const recommendation = upper(query.recommendation);
  const includeCounts = query.includeCounts === true || String(query.includeCounts) === "true";
  const pageSize = clampInt(query.pageSize ?? DEFAULT_PAGE_SIZE, 1, MAX_PAGE_SIZE);
  const needsSnapshot = Boolean(recommendation) || includeCounts;

  let analyzedFiltered = null;
  let unverifiedFiltered = null;
  let counts = null;

  if (needsSnapshot) {
    const verifiedActive = active.filter((item) => item.tradeRepublicEligible === true);
    const now = Number.isFinite(Number(overrides.now)) ? Number(overrides.now) : Date.now();
    const baseKey = radarAnalysisKey(verifiedActive);
    const cacheKey = query.refresh ? `${baseKey}|refresh:${now}` : baseKey;
    const snapshot = await getRadarAnalysisSnapshot({
      key: cacheKey,
      now,
      ttlMs: overrides.analysisTtlMs,
      load: () => analyzeSummaries(verifiedActive, overrides)
    });
    const filteredIds = new Set(filtered.map((item) => item.id));
    analyzedFiltered = snapshot.items.filter((item) => filteredIds.has(item.id));
    unverifiedFiltered = filtered
      .filter((item) => item.tradeRepublicEligible !== true)
      .map(unverifiedSummary);
    counts = buildRadarCounts(filtered, analyzedFiltered, unverifiedFiltered);
  }

  if (recommendation) {
    const analyzed = analyzedFiltered ?? [];
    const matchingVerified = recommendation === "BUY"
      ? analyzed.filter((item) => item.purchaseEligible === true && upper(item.recommendation) === "BUY")
      : analyzed.filter((item) => upper(item.recommendation) === recommendation);
    const matchingUnverified = recommendation === "REVIEW" ? (unverifiedFiltered ?? []) : [];
    const matching = sortSummaries([...matchingVerified, ...matchingUnverified], query.sort);
    const page = clampInt(query.page ?? 1, 1, Math.max(1, Math.ceil(matching.length / pageSize)));
    const start = (page - 1) * pageSize;
    return buildResponse({
      active,
      page,
      pageSize,
      total: matching.length,
      items: matching.slice(start, start + pageSize),
      hasMore: start + pageSize < matching.length,
      counts
    });
  }

  const sorted = sortUniverse(filtered, query.sort);
  const page = clampInt(query.page ?? 1, 1, Math.max(1, Math.ceil(sorted.length / pageSize)));
  const start = (page - 1) * pageSize;
  const pageItems = sorted.slice(start, start + pageSize);
  const analyzed = await analyzeSummaries(pageItems, overrides);

  return buildResponse({
    active,
    page,
    pageSize,
    total: sorted.length,
    items: sortSummaries(analyzed, query.sort),
    hasMore: start + pageSize < sorted.length,
    counts
  });
}

function buildResponse({ active, page, pageSize, total, items, hasMore, counts = null }) {
  return {
    generatedAt: new Date().toISOString(),
    total,
    universeTotal: active.length,
    page,
    pageSize,
    hasMore,
    items,
    facets: buildFacets(active),
    tradeRepublicVerifiedCount: active.filter((item) => item.tradeRepublicEligible === true).length,
    tradeRepublicUnverifiedCount: active.filter((item) => item.tradeRepublicEligible == null).length,
    ...(counts ? { counts } : {})
  };
}

export async function getInstrumentDetail(id, overrides = {}) {
  const loadUniverse = overrides.loadUniverse ?? defaultLoadUniverse;
  const universe = await loadUniverse(overrides.universeOptions ?? {});
  const instrument = universe.find((item) => item.id === id);
  if (!instrument) return null;
  const [summary] = await analyzeSummaries([instrument], overrides, { includeDetails: true });
  return summary ?? compactFallback(instrument, "Analyse nicht verfügbar");
}

export function applyFilters(items, query = {}) {
  const search = String(query.query ?? "").trim().toLowerCase();
  const type = upper(query.type);
  const region = upper(query.region);
  const country = upper(query.country);
  const sector = String(query.sector ?? "").trim().toLowerCase();
  const qualityTier = upper(query.qualityTier);
  const riskMax = finiteNumber(query.riskMax);
  const verifiedOnly = query.tradeRepublicVerified === true || String(query.tradeRepublicVerified) === "true";

  return items.filter((item) => {
    if (search && ![item.name, item.ticker, item.isin].some((value) => String(value ?? "").toLowerCase().includes(search))) return false;
    if (type && upper(item.type) !== type) return false;
    if (region && upper(item.region) !== region) return false;
    if (country && upper(item.country) !== country) return false;
    if (sector && String(item.sector ?? "").toLowerCase() !== sector) return false;
    if (qualityTier && upper(item.dataQualityTier) !== qualityTier) return false;
    if (riskMax != null && Number(item.risk ?? 5) > riskMax) return false;
    if (verifiedOnly && item.tradeRepublicEligible !== true) return false;
    return true;
  });
}

export function buildFacets(items) {
  return {
    types: countBy(items, (item) => item.type),
    regions: countBy(items, (item) => item.region || "UNKNOWN"),
    countries: countBy(items, (item) => item.country || "UNKNOWN"),
    sectors: countBy(items, (item) => item.sector || "UNKNOWN"),
    qualityTiers: countBy(items, (item) => item.dataQualityTier || "UNKNOWN")
  };
}

export function buildRadarCounts(filtered, analyzedVerified = [], unverified = []) {
  const summaries = [...analyzedVerified, ...unverified];
  return {
    total: filtered.length,
    stocks: filtered.filter((item) => upper(item.type) !== "ETF").length,
    etfs: filtered.filter((item) => upper(item.type) === "ETF").length,
    buy: summaries.filter((item) => item.purchaseEligible === true && upper(item.recommendation) === "BUY").length,
    watch: summaries.filter((item) => upper(item.recommendation) === "WATCH").length,
    noBuy: summaries.filter((item) => upper(item.recommendation) === "NO_BUY").length,
    review: summaries.filter((item) => upper(item.recommendation) === "REVIEW").length
  };
}

async function analyzeSummaries(items, overrides, options = {}) {
  if (items.length === 0) return [];
  const loadQuotes = overrides.loadQuotes ?? defaultLoadQuotes;
  const loadHistory = overrides.loadHistory ?? defaultLoadHistory;
  const loadFundamentals = overrides.loadFundamentals ?? defaultLoadFundamentals;
  const loadEurRateDetails = overrides.loadEurRateDetails ?? defaultLoadEurRateDetails;
  try {
    const [quotes, history, fundamentals] = await Promise.all([
      loadQuotes(items),
      loadHistory(items, { refresh: false }),
      loadFundamentals(items, { refresh: false })
    ]);
    let fxDetails = new Map();
    try { fxDetails = await loadEurRateDetails(quotes); } catch { fxDetails = new Map(); }
    const eurRates = new Map([...fxDetails.entries()].filter(([, value]) => Number.isFinite(value?.rate)).map(([currency, value]) => [currency, value.rate]));
    return items.map((item) => {
      try {
        const quote = quotes.get(item.id) ?? null;
        const momentum = history.get(item.id) ?? null;
        const fundamental = fundamentals.get(item.id) ?? null;
        const analysis = scoreInvestment({ item, fundamentals: fundamental, momentum, quote });
        const canBuy = item.tradeRepublicEligible === true && item.universeActive !== false && !item.portfolioOnly && analysis.recommendation === "BUY" && Number(analysis.coverage ?? 0) >= 60;
        return {
          id: item.id,
          type: item.type,
          name: item.name,
          ticker: item.ticker,
          isin: item.isin,
          tradeRepublicName: item.tradeRepublicName,
          region: item.region,
          country: item.country,
          sector: item.sector,
          industry: item.industry,
          marketCapBucket: item.marketCapBucket,
          tradeRepublicEligible: item.tradeRepublicEligible,
          universeActive: item.universeActive,
          dataQualityTier: item.dataQualityTier,
          risk: item.risk,
          price: quote?.price ?? null,
          priceEur: priceInEur(quote, eurRates),
          currency: String(quote?.currency ?? "").toUpperCase(),
          percentChange: quote?.percentChange ?? null,
          scoreTotal: analysis.scoreTotal,
          scoreQuality: analysis.scoreQuality,
          scoreValuation: analysis.scoreValuation,
          scoreGrowth: analysis.scoreGrowth,
          scoreMomentum: analysis.scoreMomentum,
          scoreRisk: analysis.scoreRisk,
          coverage: analysis.coverage,
          recommendation: canBuy ? "BUY" : analysis.recommendation === "BUY" ? "WATCH" : analysis.recommendation,
          recommendationReasons: analysis.recommendationReasons,
          purchaseEligible: canBuy,
          dataSource: quote?.source ?? "",
          dataDelayed: Boolean(quote?.delayed),
          dataError: quote?.error ?? null,
          analysisAsOf: new Date().toISOString(),
          ...(options.includeDetails ? {
            momentum,
            fundamentals: fundamental ? { ...(fundamental.metrics ?? {}), coveragePct: fundamental.coveragePct ?? 0, stale: Boolean(fundamental.stale), source: fundamental.source ?? "", asOf: fundamental.asOf ?? null, error: fundamental.error ?? null } : null
          } : {})
        };
      } catch (error) {
        return compactFallback(item, error?.message ?? "Analyse fehlgeschlagen");
      }
    });
  } catch (error) {
    return items.map((item) => compactFallback(item, error?.message ?? "Marktdaten nicht verfügbar"));
  }
}

function unverifiedSummary(item) {
  return {
    ...compactFallback(item, null),
    recommendation: "REVIEW",
    recommendationReasons: ["Trade-Republic-Handelbarkeit ist noch nicht bestätigt"],
    dataError: null
  };
}

function compactFallback(item, error) {
  return {
    id: item.id,
    type: item.type,
    name: item.name,
    ticker: item.ticker,
    isin: item.isin,
    tradeRepublicName: item.tradeRepublicName,
    region: item.region,
    country: item.country,
    sector: item.sector,
    industry: item.industry,
    marketCapBucket: item.marketCapBucket,
    tradeRepublicEligible: item.tradeRepublicEligible,
    universeActive: item.universeActive,
    dataQualityTier: item.dataQualityTier,
    risk: item.risk,
    price: null,
    priceEur: null,
    currency: "",
    percentChange: null,
    scoreTotal: null,
    scoreQuality: null,
    scoreValuation: null,
    scoreGrowth: null,
    scoreMomentum: null,
    scoreRisk: null,
    coverage: 0,
    recommendation: "REVIEW",
    recommendationReasons: ["Datenqualität reicht noch nicht für eine Kaufempfehlung"],
    purchaseEligible: false,
    dataSource: "",
    dataDelayed: false,
    dataError: error,
    analysisAsOf: new Date().toISOString()
  };
}

function sortUniverse(items, sort) {
  const mode = upper(sort || "NAME");
  return [...items].sort((a, b) => {
    if (mode === "TICKER") return a.ticker.localeCompare(b.ticker);
    if (mode === "RISK_ASC") return Number(a.risk ?? 5) - Number(b.risk ?? 5) || a.name.localeCompare(b.name);
    return a.name.localeCompare(b.name);
  });
}

function sortSummaries(items, sort) {
  const mode = upper(sort || "SCORE_DESC");
  if (mode === "SCORE_DESC") return [...items].sort((a, b) => (b.scoreTotal ?? -1) - (a.scoreTotal ?? -1) || a.name.localeCompare(b.name));
  if (mode === "MOMENTUM_DESC") return [...items].sort((a, b) => (b.scoreMomentum ?? -1) - (a.scoreMomentum ?? -1));
  if (mode === "VALUATION_DESC") return [...items].sort((a, b) => (b.scoreValuation ?? -1) - (a.scoreValuation ?? -1));
  return items;
}

function countBy(items, selector) {
  const counts = new Map();
  for (const item of items) {
    const key = String(selector(item) ?? "UNKNOWN");
    counts.set(key, (counts.get(key) ?? 0) + 1);
  }
  return [...counts.entries()].sort((a, b) => b[1] - a[1]).map(([value, count]) => ({ value, count }));
}

function upper(value) { return String(value ?? "").trim().toUpperCase(); }
function finiteNumber(value) { const n = Number(value); return Number.isFinite(n) ? n : null; }
function clampInt(value, min, max) { const n = Math.round(Number(value)); return Number.isFinite(n) ? Math.min(max, Math.max(min, n)) : min; }
