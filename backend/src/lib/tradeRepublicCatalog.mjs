const TR_WS_URL = "wss://api.traderepublic.com";
const DEFAULT_PAGE_SIZE = 100;
const MAX_PAGES_PER_TYPE = 12;

export async function loadTradeRepublicCatalog(options = {}) {
  const requestPage = options.requestPage ?? requestTradeRepublicPage;
  const target = Math.max(1, Number(options.target ?? 1200));
  const pageSize = Math.min(100, Math.max(20, Number(options.pageSize ?? DEFAULT_PAGE_SIZE)));
  const collected = [];

  for (const assetType of ["stock", "fund"]) {
    for (let page = 1; page <= MAX_PAGES_PER_TYPE && collected.length < target; page += 1) {
      const payload = await requestPage({ assetType, page, pageSize, jurisdiction: "DE" });
      const rows = extractSearchResults(payload);
      if (rows.length === 0) break;
      for (const row of rows) {
        const normalized = normalizeTradeRepublicResult(row, assetType);
        if (normalized) collected.push(normalized);
      }
      if (rows.length < pageSize) break;
    }
  }

  const unique = deduplicateTradeRepublicCatalog(collected);
  if (unique.length === 0) throw new Error("Trade-Republic-Katalog lieferte keine Aktien oder ETFs");
  return unique.slice(0, target);
}

export function normalizeTradeRepublicResult(raw, requestedType = "stock") {
  if (!raw || typeof raw !== "object") return null;
  const isin = firstString(raw.isin, raw.instrumentId, raw.instrument?.isin, raw.id).toUpperCase();
  if (!/^[A-Z]{2}[A-Z0-9]{10}$/.test(isin)) return null;

  const name = firstString(
    raw.name,
    raw.nextGenName,
    raw.shortName,
    raw.instrument?.name,
    raw.instrument?.shortName,
    isin
  );
  const ticker = firstString(
    raw.ticker,
    raw.symbol,
    raw.symbolAtExchange,
    raw.instrument?.ticker,
    raw.instrument?.symbol,
    isin.slice(0, 8)
  ).toUpperCase();
  const type = requestedType === "fund" || /\bETF\b/i.test(firstString(raw.type, raw.typeId, raw.instrument?.typeId))
    ? "ETF"
    : "AKTIE";

  const country = firstString(raw.country, raw.countryCode, raw.instrument?.country, isin.slice(0, 2)).toUpperCase();
  const region = firstString(raw.region, raw.instrument?.region, regionForCountry(country)).toUpperCase();
  const sector = firstString(raw.sector, raw.sectorName, raw.instrument?.sector);
  const industry = firstString(raw.industry, raw.industryName, raw.instrument?.industry);

  return {
    id: `tr-${isin.toLowerCase()}`,
    type,
    name,
    ticker,
    isin,
    tradeRepublicName: name,
    marketSymbol: ticker,
    yahooSymbol: "",
    risk: type === "ETF" ? 2 : 3,
    region,
    country,
    sector,
    industry,
    marketCapBucket: "",
    tradeRepublicEligible: true,
    universeActive: true,
    portfolioOnly: false,
    dataQualityTier: "B",
    universeSource: "TRADE_REPUBLIC_PUBLIC",
    tradeRepublicJurisdiction: "DE"
  };
}

export function extractSearchResults(payload) {
  if (!payload) return [];
  if (Array.isArray(payload)) return payload;
  if (Array.isArray(payload.results)) return payload.results;
  if (Array.isArray(payload.items)) return payload.items;
  if (Array.isArray(payload.data?.results)) return payload.data.results;
  if (Array.isArray(payload.data?.items)) return payload.data.items;
  return [];
}

export function deduplicateTradeRepublicCatalog(items) {
  const byIsin = new Map();
  for (const item of items) {
    if (!item?.isin || byIsin.has(item.isin)) continue;
    byIsin.set(item.isin, item);
  }
  return [...byIsin.values()];
}

export async function requestTradeRepublicPage({ assetType, page, pageSize, jurisdiction = "DE" }) {
  if (typeof WebSocket !== "function") throw new Error("WebSocket wird von dieser Node-Laufzeit nicht unterstützt");
  const message = {
    type: "neonSearch",
    data: {
      q: "",
      filter: [
        { key: "type", value: assetType },
        { key: "jurisdiction", value: jurisdiction }
      ],
      page,
      pageSize
    }
  };
  return websocketRequest(message);
}

function websocketRequest(payload) {
  return new Promise((resolve, reject) => {
    const socket = new WebSocket(TR_WS_URL);
    const timeout = setTimeout(() => finish(new Error("Trade-Republic-Katalog Timeout")), 12_000);
    let subscribed = false;
    let settled = false;

    function finish(error, value) {
      if (settled) return;
      settled = true;
      clearTimeout(timeout);
      try { socket.close(); } catch {}
      if (error) reject(error); else resolve(value);
    }

    socket.addEventListener("open", () => socket.send("connect 30"));
    socket.addEventListener("error", () => finish(new Error("Trade-Republic-Katalog WebSocket nicht erreichbar")));
    socket.addEventListener("message", (event) => {
      const text = typeof event.data === "string" ? event.data : String(event.data ?? "");
      if (!subscribed && /connected/i.test(text)) {
        subscribed = true;
        socket.send(`sub 1 ${JSON.stringify(payload)}`);
        return;
      }
      const parsed = parseSubscriptionResponse(text, 1);
      if (parsed !== undefined) finish(null, parsed);
    });
  });
}

export function parseSubscriptionResponse(text, subscriptionId) {
  const input = String(text ?? "").trim();
  const prefix = `${subscriptionId} `;
  if (!input.startsWith(prefix)) return undefined;
  const rest = input.slice(prefix.length).trim();
  const marker = rest.slice(0, 1);
  if (marker === "E") throw new Error(`Trade-Republic-Katalog Fehler: ${rest.slice(1).trim()}`);
  if (marker !== "A" && marker !== "D") return undefined;
  const json = rest.slice(1).trim();
  if (!json) return {};
  try { return JSON.parse(json); } catch { throw new Error("Trade-Republic-Katalog Antwort ist kein JSON"); }
}

function firstString(...values) {
  for (const value of values) {
    if (typeof value === "string" && value.trim()) return value.trim();
  }
  return "";
}

function regionForCountry(country) {
  if (["US", "CA", "MX"].includes(country)) return "NORTH_AMERICA";
  if (["DE", "AT", "CH", "FR", "NL", "BE", "LU", "IT", "ES", "PT", "IE", "DK", "SE", "NO", "FI", "PL", "CZ", "GB"].includes(country)) return "EUROPE";
  if (["JP", "CN", "HK", "TW", "KR", "SG", "IN"].includes(country)) return "ASIA";
  if (["AU", "NZ"].includes(country)) return "OCEANIA";
  return "GLOBAL";
}
