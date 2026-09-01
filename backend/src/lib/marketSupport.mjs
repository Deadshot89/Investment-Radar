export function normalizeYahooChart(payload, fallbackSymbol = "") {
  const result = payload?.chart?.result?.[0];
  const meta = result?.meta;
  if (!meta || typeof meta !== "object") {
    const message = payload?.chart?.error?.description || "Kein Yahoo-Kurs gefunden";
    return emptyQuote(fallbackSymbol, message);
  }

  const price = numberOrNull(meta.regularMarketPrice ?? meta.previousClose);
  const previous = numberOrNull(meta.chartPreviousClose ?? meta.previousClose);
  const percentChange = price != null && previous != null && previous !== 0
    ? ((price - previous) / previous) * 100
    : null;

  return {
    symbol: String(meta.symbol ?? fallbackSymbol),
    price,
    currency: String(meta.currency ?? ""),
    percentChange,
    marketOpen: typeof meta.marketState === "string" ? meta.marketState.toUpperCase() === "REGULAR" : null,
    timestamp: numberOrUndefined(meta.regularMarketTime),
    source: "Yahoo Finance",
    delayed: true,
    error: price == null ? "Kein Yahoo-Kurs gefunden" : null
  };
}

export function convertPriceToEur(price, currency, ratesToEur) {
  if (!Number.isFinite(price)) return null;
  const normalized = String(currency ?? "").trim().toUpperCase();
  if (!normalized || normalized === "EUR") return price;
  const rate = ratesToEur.get(normalized);
  if (!Number.isFinite(rate)) return null;
  return price * rate;
}

function emptyQuote(symbol, error) {
  return {
    symbol,
    price: null,
    currency: "",
    percentChange: null,
    marketOpen: null,
    source: "Yahoo Finance",
    delayed: true,
    error
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

export function normalizeEcbDailyXml(xml) {
  const text = String(xml ?? "");
  const dateMatch = text.match(/<Cube\b[^>]*\btime=["']([^"']+)["'][^>]*>/i);
  const rates = new Map();
  rates.set("EUR", 1);
  const pattern = /<Cube\b[^>]*\bcurrency=["']([A-Z]{3})["'][^>]*\brate=["']([0-9.]+)["'][^>]*\/?>(?:<\/Cube>)?/gi;
  for (const match of text.matchAll(pattern)) {
    const currency = String(match[1]).toUpperCase();
    const perEur = Number(match[2]);
    if (Number.isFinite(perEur) && perEur > 0) rates.set(currency, 1 / perEur);
  }
  return { date: dateMatch?.[1] ?? null, rates };
}
