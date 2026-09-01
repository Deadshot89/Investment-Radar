import { loadConfig } from "./config.mjs";
import { loadEurRates, loadQuotes, priceInEur } from "./market.mjs";
import { evaluateSignals } from "./signals.mjs";
import { loadState } from "./state.mjs";

export async function buildDashboard() {
  const config = await loadConfig();
  const quotes = await loadQuotes(config.items);
  const eurRates = await loadEurRates(quotes);
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
      const fxRateToEur = currency && currency !== "EUR" ? eurRates.get(currency) ?? null : currency === "EUR" ? 1 : null;
      return {
        id: item.id, type: item.type, name: item.name, ticker: item.ticker, isin: item.isin,
        tradeRepublicName: item.tradeRepublicName, status: item.status, allocation: item.allocation, risk: item.risk,
        price: quote?.price ?? null, priceEur: priceInEur(quote, eurRates), currency,
        fxRateToEur, percentChange: quote?.percentChange ?? null,
        marketOpen: quote?.marketOpen ?? null, dataSource: quote?.source ?? "", dataDelayed: Boolean(quote?.delayed),
        dataError: quote?.error ?? null
      };
    }),
    alerts: recent
  };
}
