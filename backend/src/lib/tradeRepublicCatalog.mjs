const TR_WS_URL = "wss://api.traderepublic.com";
const TR_WS_CONNECT_VERSION = "34";
const DEFAULT_PAGE_SIZE = 100;
const MAX_PAGES_PER_TYPE = 20;

export async function loadTradeRepublicCatalog(options = {}) {
  const requestPage = options.requestPage ?? requestTradeRepublicPage;
  const requestedTotal = Math.max(1, Number(options.target ?? 2200));
  const explicitStockTarget = finitePositiveInt(options.stockTarget);
  const explicitFundTarget = finitePositiveInt(options.fundTarget);
  const defaultFundTarget = Math.max(1, Math.round(requestedTotal * 0.30));
  const stockTarget = explicitStockTarget ?? Math.max(1, requestedTotal - defaultFundTarget);
  const fundTarget = explicitFundTarget ?? Math.max(1, requestedTotal - stockTarget);
  const totalTarget = explicitStockTarget != null || explicitFundTarget != null
    ? stockTarget + fundTarget
    : requestedTotal;
  const pageSize = Math.min(100, Math.max(20, Number(options.pageSize ?? DEFAULT_PAGE_SIZE)));

  const stocks = await collectType({ requestPage, assetType: "stock", target: stockTarget, pageSize });
  const funds = await collectType({ requestPage, assetType: "fund", target: fundTarget, pageSize });
  const unique = deduplicateTradeRepublicCatalog([...stocks, ...funds]);
  if (unique.length === 0) throw new Error("Trade-Republic-Katalog lieferte keine Aktien oder ETFs");
  return unique.slice(0, totalTarget);
}

async function collectType({ requestPage, assetType, target, pageSize }) {
  const collected = [];
  for (let page = 1; page <= MAX_PAGES_PER_TYPE && collected.length < target; page += 1) {
    const payload = await requestPage({ assetType, page, pageSize, jurisdiction: "DE" });
    const rows = extractSearchResults(payload);
    if (rows.length === 0) break;
    for (const row of rows) {
      const normalized = normalizeTradeRepublicResult(row, assetType);
      if (normalized) collected.push(normalized);
      if (collected.length >= target) break;
    }
    if (rows.length < pageSize) break;
  }
  return collected.slice(0, target);
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
  const risk = type === "ETF" ? inferEtfRisk(name) : 3;
  const etfStructureScore = type === "ETF" ? inferEtfStructureScore(name) : undefined;

  return {
    id: `tr-${isin.toLowerCase()}`,
    type,
    name,
    ticker,
    isin,
    tradeRepublicName: name,
    marketSymbol: ticker,
    yahooSymbol: "",
    risk,
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
    tradeRepublicJurisdiction: "DE",
    ...(type === "ETF" ? { etfStructureScore } : {})
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

export function buildConnectFrame() {
  return `connect ${TR_WS_CONNECT_VERSION} ${JSON.stringify({ locale: "en" })}`;
}

export function decodeSubscriptionFrame(text, subscriptionId) {
  try {
    return { value: parseSubscriptionResponse(text, subscriptionId), error: null };
  } catch (error) {
    return { value: undefined, error: error instanceof Error ? error : new Error(String(error)) };
  }
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

    socket.addEventListener("open", () => socket.send(buildConnectFrame()));
    socket.addEventListener("error", () => finish(new Error("Trade-Republic-Katalog WebSocket nicht erreichbar")));
    socket.addEventListener("close", () => {
      if (!settled) finish(new Error("Trade-Republic-Katalog WebSocket wurde vor einer Antwort geschlossen"));
    });
    socket.addEventListener("message", (event) => {
      const text = typeof event.data === "string" ? event.data : String(event.data ?? "");
      if (!subscribed && /connected/i.test(text)) {
        subscribed = true;
        socket.send(`sub 1 ${JSON.stringify(payload)}`);
        return;
      }
      const decoded = decodeSubscriptionFrame(text, 1);
      if (decoded.error) {
        finish(decoded.error);
        return;
      }
      if (decoded.value !== undefined) finish(null, decoded.value);
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

function inferEtfStructureScore(name) {
  const value = String(name ?? "").toUpperCase();
  if (/\b(2X|3X|LEVERAGED|SHORT|INVERSE|DAILY SWAP)\b/.test(value)) return 45;
  let score = 72;
  if (/\bUCITS\b/.test(value)) score += 8;
  if (/MSCI WORLD|MSCI ACWI|ALL[- ]WORLD|FTSE ALL[- ]WORLD|S&P 500|STOXX 600|GLOBAL/.test(value)) score += 8;
  if (/ACC|ACCUMULATING|DISTRIBUTING|DIST\b/.test(value)) score += 2;
  return Math.max(40, Math.min(92, score));
}

function inferEtfRisk(name) {
  const value = String(name ?? "").toUpperCase();
  if (/\b(2X|3X|LEVERAGED|SHORT|INVERSE|DAILY SWAP)\b/.test(value)) return 5;
  if (/SECTOR|TECHNOLOGY|SEMICONDUCTOR|BIOTECH|COMMODIT|GOLD|OIL|CRYPTO|BITCOIN/.test(value)) return 3;
  return 2;
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

function finitePositiveInt(value) {
  const number = Math.round(Number(value));
  return Number.isFinite(number) && number > 0 ? number : null;
}
