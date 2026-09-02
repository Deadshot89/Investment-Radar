import { loadConfig as defaultLoadConfig } from "./config.mjs";
import { loadEurRateDetails as defaultLoadEurRateDetails, loadQuotes as defaultLoadQuotes, priceInEur } from "./market.mjs";
import { evaluateSignals } from "./signals.mjs";
import { loadState as defaultLoadState } from "./state.mjs";
import { cacheFromQuotes, loadQuoteCache as defaultLoadQuoteCache, mergeQuotesWithCache, saveQuoteCache as defaultSaveQuoteCache } from "./quoteCache.mjs";
import { cacheFromFxRates, loadFxCache as defaultLoadFxCache, mergeFxRatesWithCache, saveFxCache as defaultSaveFxCache } from "./fxCache.mjs";
import { loadHistory as defaultLoadHistory } from "./history.mjs";
import { loadFundamentals as defaultLoadFundamentals } from "./fundamentals.mjs";
import { scoreInvestment } from "./scoring.mjs";
import { buildCompatibilityAllocations, legacyStatus } from "./compatibility.mjs";

export async function buildAnalysisSnapshot(overrides = {}) {
  const deps = dependencies(overrides);
  const refreshAnalysis = Boolean(overrides.refreshAnalysis);
  const config = await deps.loadConfig();
  const [providerQuotes, history, fundamentals, state] = await Promise.all([
    deps.loadQuotes(config.items),
    deps.loadHistory(config.items, { refresh: refreshAnalysis }),
    deps.loadFundamentals(config.items, { refresh: refreshAnalysis }),
    deps.loadState()
  ]);

  const quoteCache = await deps.loadQuoteCache();
  const quotes = mergeQuotesWithCache(providerQuotes, quoteCache);
  const freshQuoteCache = cacheFromQuotes(providerQuotes);
  if (Object.keys(freshQuoteCache).length > 0) {
    await deps.saveQuoteCache({ ...quoteCache, ...freshQuoteCache });
  }

  const providerFxRates = await deps.loadEurRateDetails(quotes);
  const fxCache = await deps.loadFxCache();
  const fxDetails = mergeFxRatesWithCache(providerFxRates, fxCache);
  const freshFxCache = cacheFromFxRates(providerFxRates);
  if (Object.keys(freshFxCache).length > 0) {
    await deps.saveFxCache({ ...fxCache, ...freshFxCache });
  }
  const eurRates = new Map([...fxDetails.entries()]
    .filter(([, detail]) => Number.isFinite(detail?.rate))
    .map(([currency, detail]) => [currency, detail.rate]));

  const analyzed = config.items.map((item) => {
    const quote = quotes.get(item.id) ?? null;
    const momentum = history.get(item.id) ?? null;
    const fundamental = fundamentals.get(item.id) ?? null;
    const analysis = scoreInvestment({ item, fundamentals: fundamental, momentum, quote });
    return { item, quote, momentum, fundamental, analysis };
  });
  const compatibility = buildCompatibilityAllocations(
    analyzed.map(({ item, analysis }) => ({ id: item.id, ...analysis })),
    config.budget
  );

  const items = analyzed.map(({ item, quote, momentum, fundamental, analysis }) => {
    const currency = String(quote?.currency ?? "").trim().toUpperCase();
    const fxDetail = currency && currency !== "EUR" ? fxDetails.get(currency) : null;
    const fxRateToEur = currency && currency !== "EUR" ? fxDetail?.rate ?? null : currency === "EUR" ? 1 : null;
    return {
      id: item.id,
      type: item.type,
      name: item.name,
      ticker: item.ticker,
      isin: item.isin,
      tradeRepublicName: item.tradeRepublicName,
      status: legacyStatus(analysis.recommendation),
      allocation: compatibility.get(item.id) ?? 0,
      risk: item.risk,
      price: quote?.price ?? null,
      priceEur: priceInEur(quote, eurRates),
      currency,
      fxRateToEur,
      fxSource: fxDetail?.source ?? "",
      fxDelayed: Boolean(fxDetail?.delayed),
      fxAsOf: fxDetail?.asOf ?? null,
      percentChange: quote?.percentChange ?? null,
      marketOpen: quote?.marketOpen ?? null,
      dataSource: quote?.source ?? "",
      dataDelayed: Boolean(quote?.delayed),
      dataError: quote?.error ?? null,
      scoreTotal: analysis.scoreTotal,
      scoreQuality: analysis.scoreQuality,
      scoreValuation: analysis.scoreValuation,
      scoreGrowth: analysis.scoreGrowth,
      scoreMomentum: analysis.scoreMomentum,
      scoreRisk: analysis.scoreRisk,
      coverage: analysis.coverage,
      recommendation: analysis.recommendation,
      recommendationReasons: analysis.recommendationReasons,
      momentum: momentum ? {
        d1: momentum.d1 ?? null,
        m1: momentum.m1 ?? null,
        m3: momentum.m3 ?? null,
        m6: momentum.m6 ?? null,
        m12: momentum.m12 ?? null,
        score: momentum.score ?? null,
        coveragePct: momentum.coveragePct ?? 0,
        stale: Boolean(momentum.stale),
        source: momentum.source ?? "",
        asOf: momentum.asOf ?? null,
        error: momentum.error ?? null
      } : null,
      fundamentals: fundamental ? {
        ...(fundamental.metrics ?? {}),
        coveragePct: fundamental.coveragePct ?? 0,
        stale: Boolean(fundamental.stale),
        source: fundamental.source ?? "",
        asOf: fundamental.asOf ?? null,
        error: fundamental.error ?? null
      } : null,
      analysisAsOf: new Date().toISOString(),
      reviewDrop1dPct: item.reviewDrop1dPct,
      hardReviewBelow: item.hardReviewBelow ?? null,
      alertStatus: item.alertStatus ?? "",
      alertReason: item.alertReason ?? "",
      alertUpdatedAt: item.alertUpdatedAt ?? ""
    };
  });

  const ranked = [...items].sort((a, b) => {
    const group = (value) => value.recommendation === "BUY" ? 0 : value.recommendation === "WATCH" ? 1 : value.recommendation === "NO_BUY" ? 2 : 3;
    return group(a) - group(b) || (b.scoreTotal ?? 0) - (a.scoreTotal ?? 0);
  });

  return {
    generatedAt: new Date().toISOString(),
    marketLight: config.marketLight,
    budget: config.budget,
    topPickId: ranked[0]?.id ?? config.topPickId ?? items[0]?.id ?? "",
    items,
    quotes,
    state
  };
}

export async function buildDashboard(overrides = {}) {
  const snapshot = await buildAnalysisSnapshot({ ...overrides, refreshAnalysis: overrides.refreshAnalysis ?? false });
  const liveAlerts = evaluateSignals(snapshot.items, snapshot.quotes, {
    previousScores: snapshot.state.previousScores,
    previousRecommendations: snapshot.state.previousRecommendations,
    heldIds: new Set()
  });
  const recent = [...liveAlerts, ...(snapshot.state.recent ?? [])]
    .filter((alert, index, all) => all.findIndex((candidate) => candidate.id === alert.id) === index)
    .slice(0, 20);
  return {
    generatedAt: snapshot.generatedAt,
    marketLight: snapshot.marketLight,
    budget: snapshot.budget,
    topPickId: snapshot.topPickId,
    items: snapshot.items,
    alerts: recent
  };
}

function dependencies(overrides) {
  return {
    loadConfig: overrides.loadConfig ?? defaultLoadConfig,
    loadQuotes: overrides.loadQuotes ?? defaultLoadQuotes,
    loadHistory: overrides.loadHistory ?? defaultLoadHistory,
    loadFundamentals: overrides.loadFundamentals ?? defaultLoadFundamentals,
    loadEurRateDetails: overrides.loadEurRateDetails ?? defaultLoadEurRateDetails,
    loadState: overrides.loadState ?? defaultLoadState,
    loadQuoteCache: overrides.loadQuoteCache ?? defaultLoadQuoteCache,
    saveQuoteCache: overrides.saveQuoteCache ?? defaultSaveQuoteCache,
    loadFxCache: overrides.loadFxCache ?? defaultLoadFxCache,
    saveFxCache: overrides.saveFxCache ?? defaultSaveFxCache
  };
}
