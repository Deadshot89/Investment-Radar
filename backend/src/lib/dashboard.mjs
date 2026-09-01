import { loadConfig } from "./config.mjs";
import { loadEurRateDetails, loadQuotes, priceInEur } from "./market.mjs";
import { evaluateSignals } from "./signals.mjs";
import { loadState } from "./state.mjs";
import { cacheFromQuotes, loadQuoteCache, mergeQuotesWithCache, saveQuoteCache } from "./quoteCache.mjs";
import { cacheFromFxRates, loadFxCache, mergeFxRatesWithCache, saveFxCache } from "./fxCache.mjs";

export async function buildDashboard() {
  const config = await loadConfig();
  const providerQuotes = await loadQuotes(config.items);
  const quoteCache = await loadQuoteCache();
  const quotes = mergeQuotesWithCache(providerQuotes, quoteCache);
  const freshCache = cacheFromQuotes(providerQuotes);
  if (Object.keys(freshCache).length > 0) {
    await saveQuoteCache({ ...quoteCache, ...freshCache });
  }
  const providerFxRates = await loadEurRateDetails(quotes);
  const fxCache = await loadFxCache();
  const fxDetails = mergeFxRatesWithCache(providerFxRates, fxCache);
  const freshFxCache = cacheFromFxRates(providerFxRates);
  if (Object.keys(freshFxCache).length > 0) {
    await saveFxCache({ ...fxCache, ...freshFxCache });
  }
  const eurRates = new Map([...fxDetails.entries()]
    .filter(([, detail]) => Number.isFinite(detail?.rate))
    .map(([currency, detail]) => [currency, detail.rate]));
  const state = await loadState();
  const liveAlerts = evaluateSignals(config.items, quotes);
  const recent = [...liveAlerts, ...state.recent].filter((a, i, arr) => arr.findIndex((x) => x.id === a.id) === i).slice(0, 20);
  return {
    generatedAt: new Date().toISOString(),
    marketLight: config.marketLight,
    budget: config.budget,
    topPickId: config.topPickId,
    items: config.items.map((item) => {
      const quote = quotes.get(item.id);
      const currency = String(quote?.currency ?? "").trim().toUpperCase();
      const fxDetail = currency && currency !== "EUR" ? fxDetails.get(currency) : null;
      const fxRateToEur = currency && currency !== "EUR" ? fxDetail?.rate ?? null : currency === "EUR" ? 1 : null;
      return {
        id: item.id, type: item.type, name: item.name, ticker: item.ticker, isin: item.isin,
        tradeRepublicName: item.tradeRepublicName, status: item.status, allocation: item.allocation, risk: item.risk,
        price: quote?.price ?? null, priceEur: priceInEur(quote, eurRates), currency,
        fxRateToEur, fxSource: fxDetail?.source ?? "", fxDelayed: Boolean(fxDetail?.delayed), fxAsOf: fxDetail?.asOf ?? null,
        percentChange: quote?.percentChange ?? null,
        marketOpen: quote?.marketOpen ?? null, dataSource: quote?.source ?? "", dataDelayed: Boolean(quote?.delayed),
        dataError: quote?.error ?? null
      };
    }),
    alerts: recent
  };
}
