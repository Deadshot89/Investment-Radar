import { loadConfig as defaultLoadConfig } from "./config.mjs";

const NASDAQ_LISTED_URL = "https://www.nasdaqtrader.com/dynamic/SymDir/nasdaqlisted.txt";
const OTHER_LISTED_URL = "https://www.nasdaqtrader.com/dynamic/SymDir/otherlisted.txt";
const CACHE_TTL_MS = 6 * 60 * 60 * 1000;
const TARGET_EXTERNAL = 1200;

let memoryCache = null;
let memoryCacheAt = 0;

export function normalizeUniverseInstrument(item, defaults = {}) {
  const type = String(item?.type ?? defaults.type ?? "AKTIE").toUpperCase() === "ETF" ? "ETF" : "AKTIE";
  const ticker = String(item?.ticker ?? "").trim().toUpperCase();
  const id = String(item?.id ?? defaults.id ?? ticker.toLowerCase().replace(/[^a-z0-9]+/g, "-")).trim();
  return {
    ...item,
    id,
    type,
    name: String(item?.name ?? ticker).trim(),
    ticker,
    isin: String(item?.isin ?? "").trim().toUpperCase(),
    tradeRepublicName: String(item?.tradeRepublicName ?? item?.name ?? ticker).trim(),
    marketSymbol: String(item?.marketSymbol ?? ticker).trim(),
    yahooSymbol: String(item?.yahooSymbol ?? ticker).trim(),
    risk: clampInt(item?.risk ?? defaults.risk ?? (type === "ETF" ? 2 : 3), 1, 5),
    region: String(item?.region ?? defaults.region ?? "").trim().toUpperCase(),
    country: String(item?.country ?? defaults.country ?? "").trim().toUpperCase(),
    sector: String(item?.sector ?? defaults.sector ?? "").trim(),
    industry: String(item?.industry ?? defaults.industry ?? "").trim(),
    marketCapBucket: String(item?.marketCapBucket ?? defaults.marketCapBucket ?? "").trim().toUpperCase(),
    tradeRepublicEligible: item?.tradeRepublicEligible === true ? true : item?.tradeRepublicEligible === false ? false : null,
    universeActive: item?.universeActive !== false,
    portfolioOnly: Boolean(item?.portfolioOnly),
    dataQualityTier: String(item?.dataQualityTier ?? defaults.dataQualityTier ?? "B").trim().toUpperCase(),
    universeSource: String(item?.universeSource ?? defaults.universeSource ?? "CURATED").trim().toUpperCase()
  };
}

export function validateUniverse(items, { minActive = 1 } = {}) {
  if (!Array.isArray(items)) throw new Error("Universe must be an array");
  const ids = new Set();
  const isins = new Set();
  for (const item of items) {
    if (!item.id) throw new Error("Universe item id missing");
    if (!item.ticker) throw new Error(`Universe item ${item.id}: ticker missing`);
    if (ids.has(item.id)) throw new Error(`Duplicate universe id: ${item.id}`);
    ids.add(item.id);
    if (item.isin) {
      if (isins.has(item.isin)) throw new Error(`Duplicate universe ISIN: ${item.isin}`);
      isins.add(item.isin);
    }
  }
  const active = items.filter((item) => item.universeActive !== false).length;
  if (active < minActive) throw new Error(`Active universe too small: ${active} < ${minActive}`);
  return { total: items.length, active };
}

export async function loadUniverse(overrides = {}) {
  const now = Date.now();
  if (!overrides.refresh && memoryCache && now - memoryCacheAt < CACHE_TTL_MS) return memoryCache;

  const loadConfig = overrides.loadConfig ?? defaultLoadConfig;
  const fetchImpl = overrides.fetchImpl ?? globalThis.fetch;
  const config = await loadConfig();
  const curated = config.items.map((item) => normalizeUniverseInstrument({
    ...item,
    tradeRepublicEligible: item.tradeRepublicEligible ?? !item.portfolioOnly,
    universeActive: item.universeActive ?? true,
    dataQualityTier: item.dataQualityTier ?? "A",
    universeSource: "CURATED"
  }));

  let external = [];
  if (typeof fetchImpl === "function" && overrides.includeExternal !== false) {
    try {
      external = await loadPublicListedUniverse(fetchImpl);
    } catch (error) {
      console.error("Public universe refresh failed; curated universe used", error);
    }
  }

  const merged = mergeUniverse(curated, external).slice(0, Math.max(curated.length, Number(overrides.limit ?? 1000)));
  validateUniverse(merged, { minActive: Math.min(curated.length, 1) });
  memoryCache = merged;
  memoryCacheAt = now;
  return merged;
}

export async function loadPublicListedUniverse(fetchImpl = globalThis.fetch) {
  const [nasdaqResponse, otherResponse] = await Promise.all([
    fetchImpl(NASDAQ_LISTED_URL, { headers: { "user-agent": "Investment-Radar/2.0" } }),
    fetchImpl(OTHER_LISTED_URL, { headers: { "user-agent": "Investment-Radar/2.0" } })
  ]);
  if (!nasdaqResponse?.ok || !otherResponse?.ok) throw new Error("Listed-universe provider unavailable");
  const [nasdaqText, otherText] = await Promise.all([nasdaqResponse.text(), otherResponse.text()]);
  const parsed = [...parseNasdaqListed(nasdaqText), ...parseOtherListed(otherText)];
  return parsed
    .filter((item) => item.ticker && item.name && !item.testIssue)
    .sort((a, b) => a.ticker.localeCompare(b.ticker))
    .slice(0, TARGET_EXTERNAL)
    .map((item) => normalizeUniverseInstrument(item, {
      region: "NORTH_AMERICA",
      country: "US",
      dataQualityTier: "B",
      universeSource: "NASDAQ_TRADER"
    }));
}

export function parseNasdaqListed(text) {
  const rows = parsePipe(text);
  return rows.map((row) => ({
    id: `us-${slug(row.Symbol)}`,
    ticker: row.Symbol,
    name: cleanSecurityName(row["Security Name"]),
    type: inferType(row["Security Name"]),
    testIssue: row["Test Issue"] === "Y",
    tradeRepublicEligible: null,
    universeActive: true
  }));
}

export function parseOtherListed(text) {
  const rows = parsePipe(text);
  return rows.map((row) => ({
    id: `us-${slug(row["ACT Symbol"])}`,
    ticker: row["ACT Symbol"],
    name: cleanSecurityName(row["Security Name"]),
    type: inferType(row["Security Name"]),
    testIssue: row["Test Issue"] === "Y",
    tradeRepublicEligible: null,
    universeActive: true
  }));
}

export function mergeUniverse(curated, external) {
  const byId = new Map();
  const tickerKeys = new Set();
  for (const item of curated) {
    byId.set(item.id, item);
    tickerKeys.add(normalizeTicker(item.ticker));
  }
  for (const item of external) {
    const tickerKey = normalizeTicker(item.ticker);
    if (!tickerKey || tickerKeys.has(tickerKey) || byId.has(item.id)) continue;
    byId.set(item.id, item);
    tickerKeys.add(tickerKey);
  }
  return [...byId.values()];
}

function parsePipe(text) {
  const lines = String(text ?? "").split(/\r?\n/).filter(Boolean);
  if (lines.length < 2) return [];
  const headers = lines[0].split("|");
  return lines.slice(1)
    .filter((line) => !/^File Creation Time/i.test(line))
    .map((line) => {
      const values = line.split("|");
      return Object.fromEntries(headers.map((header, index) => [header, values[index] ?? ""]));
    });
}

function inferType(name) {
  const value = String(name ?? "").toUpperCase();
  return /\bETF\b|EXCHANGE TRADED|INDEX FUND/.test(value) ? "ETF" : "AKTIE";
}

function cleanSecurityName(name) {
  return String(name ?? "")
    .replace(/\s+-\s+(Common Stock|Ordinary Shares|American Depositary Shares.*|ETF)$/i, "")
    .trim();
}

function slug(value) {
  return String(value ?? "").toLowerCase().replace(/[^a-z0-9]+/g, "-").replace(/^-|-$/g, "");
}

function normalizeTicker(value) {
  return String(value ?? "").toUpperCase().replace(/[.\-\s]/g, "");
}

function clampInt(value, min, max) {
  const number = Math.round(Number(value));
  return Number.isFinite(number) ? Math.min(max, Math.max(min, number)) : min;
}
