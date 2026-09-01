import { convertPriceToEur, normalizeYahooChart } from "./marketSupport.mjs";

const BASE = "https://api.twelvedata.com";
const YAHOO_BASES = [
  "https://query1.finance.yahoo.com/v8/finance/chart",
  "https://query2.finance.yahoo.com/v8/finance/chart"
];

export async function loadQuotes(items) {
  const key = process.env.TWELVE_DATA_API_KEY?.trim();
  let quotes = key
    ? await loadTwelveQuotes(items, key)
    : fallbackQuotes(items, "TWELVE_DATA_API_KEY fehlt");

  const unresolved = items.filter((item) => quotes.get(item.id)?.price == null && item.yahooSymbol);
  if (unresolved.length > 0) {
    const fallbacks = await Promise.all(unresolved.map(async (item) => [item.id, await loadYahooQuote(item.yahooSymbol)]));
    for (const [id, quote] of fallbacks) {
      if (quote.price != null) quotes.set(id, quote);
      else {
        const previous = quotes.get(id);
        quotes.set(id, {
          ...previous,
          error: [previous?.error, quote.error].filter(Boolean).join(" · ") || "Kein Kurs gefunden"
        });
      }
    }
  }
  return quotes;
}

export async function loadEurRates(quotes) {
  const key = process.env.TWELVE_DATA_API_KEY?.trim();
  const currencies = [...new Set([...quotes.values()]
    .filter((q) => q?.price != null)
    .map((q) => String(q?.currency ?? "").trim().toUpperCase())
    .filter((currency) => currency && currency !== "EUR"))];

  const rates = new Map();
  if (!key || currencies.length === 0) return rates;

  await Promise.all(currencies.map(async (currency) => {
    const rate = await loadExchangeRateToEur(currency, key);
    if (rate != null) rates.set(currency, rate);
  }));
  return rates;
}

export function priceInEur(quote, ratesToEur) {
  return convertPriceToEur(quote?.price ?? null, quote?.currency ?? "", ratesToEur);
}

async function loadTwelveQuotes(items, key) {
  const symbols = items.map((i) => i.marketSymbol).join(",");
  const url = new URL(`${BASE}/quote`);
  url.searchParams.set("symbol", symbols);
  url.searchParams.set("apikey", key);

  try {
    const response = await fetch(url, { headers: { Accept: "application/json" }, signal: AbortSignal.timeout(12_000) });
    if (!response.ok) throw new Error(`Twelve Data HTTP ${response.status}`);
    const json = await response.json();
    const map = new Map();

    if (items.length === 1 && "symbol" in json) {
      map.set(items[0].id, normalizeQuote(json, items[0].marketSymbol));
      return map;
    }

    for (const item of items) {
      const raw = json[item.marketSymbol] ?? json[item.ticker] ?? findBySymbol(json, item.ticker);
      map.set(item.id, normalizeQuote(raw, item.marketSymbol));
    }
    return map;
  } catch (error) {
    return fallbackQuotes(items, error instanceof Error ? error.message : "Marktdatenfehler");
  }
}

async function loadYahooQuote(symbol) {
  const attempts = [
    { range: "1d", interval: "5m" },
    { range: "1d", interval: "5m" },
    { range: "5d", interval: "1d" },
    { range: "5d", interval: "1d" }
  ];
  let lastError = "Yahoo-Marktdatenfehler";

  for (let index = 0; index < attempts.length; index++) {
    const base = YAHOO_BASES[index % YAHOO_BASES.length];
    const { range, interval } = attempts[index];
    const url = new URL(`${base}/${encodeURIComponent(symbol)}`);
    url.searchParams.set("range", range);
    url.searchParams.set("interval", interval);
    url.searchParams.set("includePrePost", "false");
    try {
      const response = await fetch(url, {
        headers: { Accept: "application/json", "User-Agent": "Mozilla/5.0 InvestmentRadar/1.1" },
        signal: AbortSignal.timeout(10_000)
      });
      if (!response.ok) {
        lastError = `Yahoo HTTP ${response.status}`;
        continue;
      }
      const quote = normalizeYahooChart(await response.json(), symbol);
      if (quote.price != null) return quote;
      lastError = quote.error || "Kein Yahoo-Kurs gefunden";
    } catch (error) {
      lastError = error instanceof Error ? error.message : "Yahoo-Marktdatenfehler";
    }
  }

  return {
    symbol,
    price: null,
    currency: "",
    percentChange: null,
    marketOpen: null,
    source: "Yahoo Finance",
    delayed: true,
    error: lastError
  };
}

async function loadExchangeRateToEur(currency, key) {
  const url = new URL(`${BASE}/exchange_rate`);
  url.searchParams.set("symbol", `${currency}/EUR`);
  url.searchParams.set("apikey", key);
  try {
    const response = await fetch(url, { headers: { Accept: "application/json" }, signal: AbortSignal.timeout(8_000) });
    if (!response.ok) return null;
    const json = await response.json();
    if (json?.status === "error") return null;
    const rate = Number(json?.rate);
    return Number.isFinite(rate) && rate > 0 ? rate : null;
  } catch {
    return null;
  }
}

function findBySymbol(json, ticker) {
  for (const value of Object.values(json)) {
    if (value && typeof value === "object" && "symbol" in value && String(value.symbol).toUpperCase() === ticker.toUpperCase()) return value;
  }
  return undefined;
}

function normalizeQuote(raw, fallbackSymbol = "") {
  if (!raw || typeof raw !== "object") return { symbol: fallbackSymbol, price: null, currency: "", percentChange: null, marketOpen: null, source: "Twelve Data", delayed: false, error: "Kein Kurs gefunden" };
  if (raw.status === "error") return { symbol: fallbackSymbol, price: null, currency: "", percentChange: null, marketOpen: null, source: "Twelve Data", delayed: false, error: String(raw.message ?? "Provider-Fehler") };
  return {
    symbol: String(raw.symbol ?? fallbackSymbol),
    name: raw.name ? String(raw.name) : undefined,
    price: numberOrNull(raw.close ?? raw.price),
    currency: String(raw.currency ?? ""),
    percentChange: numberOrNull(raw.percent_change),
    marketOpen: typeof raw.is_market_open === "boolean" ? raw.is_market_open : null,
    timestamp: numberOrUndefined(raw.timestamp),
    source: "Twelve Data",
    delayed: false,
    error: null
  };
}

function numberOrNull(value) {
  const n = typeof value === "number" ? value : Number(value);
  return Number.isFinite(n) ? n : null;
}
function numberOrUndefined(value) {
  const n = typeof value === "number" ? value : Number(value);
  return Number.isFinite(n) ? n : undefined;
}
function fallbackQuotes(items, error) {
  return new Map(items.map((item) => [item.id, { symbol: item.marketSymbol, price: null, currency: "", percentChange: null, marketOpen: null, source: "Twelve Data", delayed: false, error }]));
}
