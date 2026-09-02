import { normalizeFundamentals } from "./fundamentalSupport.mjs";
import { isFresh, loadAnalysisCache, saveAnalysisCache } from "./analysisCache.mjs";

const BASE = "https://api.twelvedata.com/statistics";
const FRESH_MS = 24 * 60 * 60 * 1000;
const MAX_STALE_MS = 7 * 24 * 60 * 60 * 1000;

export async function loadFundamentals(items, { fetchImpl = fetch, now = Date.now(), refresh = true } = {}) {
  const cache = await loadAnalysisCache("fundamentals-cache");
  const key = process.env.TWELVE_DATA_API_KEY?.trim();
  const result = new Map();
  let changed = false;

  await mapLimit(items, 3, async (item) => {
    if (String(item.type).toUpperCase() === "ETF") {
      result.set(item.id, normalizeFundamentals({ source: "ETF-Konfiguration", asOf: null }));
      return;
    }
    const cached = cache[item.id];
    if (isFresh(cached, FRESH_MS, now) && cached?.raw) {
      result.set(item.id, normalizeFundamentals({ ...cached.raw, source: cached.source || "Twelve Data", asOf: cached.fetchedAt }));
      return;
    }

    if (!refresh) {
      const age = now - Date.parse(String(cached?.fetchedAt ?? ""));
      if (cached?.raw && Number.isFinite(age) && age <= MAX_STALE_MS) {
        result.set(item.id, normalizeFundamentals({
          ...cached.raw,
          source: `Cache · ${cached.source || "Fundamentaldaten"}`,
          stale: true,
          asOf: cached.fetchedAt,
          error: "Fundamentaldaten werden im Hintergrund aktualisiert"
        }));
      } else {
        result.set(item.id, normalizeFundamentals({
          source: "",
          error: "Fundamentaldaten werden im Hintergrund geladen"
        }));
      }
      return;
    }

    const loaded = key && item.marketSymbol
      ? await loadStatistics(item.marketSymbol, key, fetchImpl)
      : { raw: null, source: "Twelve Data", error: "Fundamentaldaten im aktuellen Tarif/Setup nicht verfügbar" };

    if (loaded.raw) {
      const fetchedAt = new Date(now).toISOString();
      cache[item.id] = { raw: loaded.raw, source: loaded.source, fetchedAt };
      changed = true;
      result.set(item.id, normalizeFundamentals({ ...loaded.raw, source: loaded.source, asOf: fetchedAt }));
      return;
    }

    const age = now - Date.parse(String(cached?.fetchedAt ?? ""));
    if (cached?.raw && Number.isFinite(age) && age <= MAX_STALE_MS) {
      result.set(item.id, normalizeFundamentals({
        ...cached.raw,
        source: `Cache · ${cached.source || "Fundamentaldaten"}`,
        stale: true,
        asOf: cached.fetchedAt,
        error: loaded.error
      }));
    } else {
      result.set(item.id, normalizeFundamentals({ source: loaded.source, error: loaded.error }));
    }
  });

  if (changed) await saveAnalysisCache("fundamentals-cache", cache);
  return result;
}

async function loadStatistics(symbol, key, fetchImpl) {
  try {
    const url = new URL(BASE);
    url.searchParams.set("symbol", symbol);
    url.searchParams.set("apikey", key);
    const response = await fetchImpl(url, { headers: { Accept: "application/json" }, signal: AbortSignal.timeout(12_000) });
    if (!response.ok) return { raw: null, source: "Twelve Data", error: `Twelve Data statistics HTTP ${response.status}` };
    const json = await response.json();
    if (json?.status === "error" || json?.code) return { raw: null, source: "Twelve Data", error: String(json?.message || "Twelve Data statistics nicht verfügbar") };
    const raw = canonicalFromStatistics(json);
    const hasAny = Object.entries(raw).some(([keyName, value]) => keyName !== "source" && value != null);
    return hasAny
      ? { raw, source: "Twelve Data", error: null }
      : { raw: null, source: "Twelve Data", error: "Twelve Data statistics enthält keine nutzbaren Kennzahlen" };
  } catch (error) {
    return { raw: null, source: "Twelve Data", error: error instanceof Error ? error.message : "Fundamentaldatenfehler" };
  }
}

function canonicalFromStatistics(json) {
  const flat = flatten(json);
  const marketCap = pick(flat, ["marketcapitalization", "marketcap"]);
  const freeCashFlow = pick(flat, ["freecashflowttm", "freecashflow"]);
  return {
    pe: pick(flat, ["forwardpe", "trailingpe", "peratio", "pricetoearningsttm"]),
    priceToSales: pick(flat, ["pricetosales", "pricetosalesttm", "pricetosalesratio"]),
    evToEbitda: pick(flat, ["enterprisevaluetoebitda", "enterprisevalueebitda", "evtoebitda"]),
    freeCashFlowYield: Number.isFinite(freeCashFlow) && Number.isFinite(marketCap) && marketCap !== 0 ? freeCashFlow / marketCap : null,
    revenueGrowth: pick(flat, ["quarterlyrevenuegrowth", "revenuegrowth", "revenuegrowthttmyoy"]),
    epsGrowth: pick(flat, ["quarterlyearningsgrowth", "epsgrowth", "earningsgrowth"]),
    operatingMargin: pick(flat, ["operatingmargin", "operatingmarginttm"]),
    netMargin: pick(flat, ["profitmargin", "netmargin", "netprofitmargin"]),
    roe: pick(flat, ["returnonequity", "returnonequityttm", "roe"]),
    roic: pick(flat, ["returnoninvestedcapital", "roic"]),
    debtToEquity: pick(flat, ["totaldebttoequity", "debttoequity", "totaldebttoequitymrq"]),
    source: "Twelve Data"
  };
}

function flatten(value, out = {}) {
  if (Array.isArray(value)) {
    value.forEach((entry) => flatten(entry, out));
    return out;
  }
  if (!value || typeof value !== "object") return out;
  for (const [key, child] of Object.entries(value)) {
    const normalized = key.toLowerCase().replace(/[^a-z0-9]/g, "");
    if (child != null && typeof child !== "object") {
      const n = Number(String(child).replace(/,/g, ""));
      if (!(normalized in out) && Number.isFinite(n)) out[normalized] = n;
    } else {
      flatten(child, out);
    }
  }
  return out;
}
function pick(flat, aliases) {
  for (const alias of aliases) if (Number.isFinite(flat[alias])) return flat[alias];
  return null;
}
async function mapLimit(items, limit, worker) {
  const queue = [...items];
  const runners = Array.from({ length: Math.min(limit, queue.length) }, async () => {
    while (queue.length) await worker(queue.shift());
  });
  await Promise.all(runners);
}
