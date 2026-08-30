const BASE = "https://api.twelvedata.com";

export async function loadQuotes(items) {
  const key = process.env.TWELVE_DATA_API_KEY?.trim();
  if (!key) return fallbackQuotes(items, "TWELVE_DATA_API_KEY fehlt");

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
      map.set(items[0].id, normalizeQuote(json));
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

function findBySymbol(json, ticker) {
  for (const value of Object.values(json)) {
    if (value && typeof value === "object" && "symbol" in value && String(value.symbol).toUpperCase() === ticker.toUpperCase()) return value;
  }
  return undefined;
}

function normalizeQuote(raw, fallbackSymbol = "") {
  if (!raw || typeof raw !== "object") return { symbol: fallbackSymbol, price: null, currency: "", percentChange: null, marketOpen: null, error: "Kein Kurs gefunden" };
  if (raw.status === "error") return { symbol: fallbackSymbol, price: null, currency: "", percentChange: null, marketOpen: null, error: String(raw.message ?? "Provider-Fehler") };
  return {
    symbol: String(raw.symbol ?? fallbackSymbol),
    name: raw.name ? String(raw.name) : undefined,
    price: numberOrNull(raw.close ?? raw.price),
    currency: String(raw.currency ?? ""),
    percentChange: numberOrNull(raw.percent_change),
    marketOpen: typeof raw.is_market_open === "boolean" ? raw.is_market_open : null,
    timestamp: numberOrUndefined(raw.timestamp)
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
  return new Map(items.map((item) => [item.id, { symbol: item.marketSymbol, price: null, currency: "", percentChange: null, marketOpen: null, error }]));
}
