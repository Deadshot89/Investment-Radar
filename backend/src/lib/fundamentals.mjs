import { normalizeFundamentals } from "./fundamentalSupport.mjs";
import { loadFundamentalFallback as defaultLoadFundamentalFallback } from "./fundamentalFallbackProvider.mjs";
import { isFresh, loadAnalysisCache, saveAnalysisCache } from "./analysisCache.mjs";

const BASE = "https://api.twelvedata.com/statistics";
const FRESH_MS = 24 * 60 * 60 * 1000;
const MAX_STALE_MS = 7 * 24 * 60 * 60 * 1000;
const METRIC_KEYS = [
  "pe", "priceToSales", "evToEbitda", "freeCashFlowYield", "revenueGrowth", "epsGrowth",
  "operatingMargin", "netMargin", "roe", "roic", "debtToEquity", "marketCap"
];

export async function loadFundamentals(items, {
  fetchImpl = fetch,
  now = Date.now(),
  refresh = true,
  loadFundamentalFallback = defaultLoadFundamentalFallback
} = {}) {
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
      result.set(item.id, normalizeFundamentals({
        ...cached.raw,
        source: cached.source || "Fundamentaldaten",
        asOf: cached.fetchedAt,
        fieldSources: cached.fieldSources,
        conflicts: cached.conflicts
      }));
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
          error: "Fundamentaldaten werden im Hintergrund aktualisiert",
          fieldSources: cached.fieldSources,
          conflicts: cached.conflicts
        }));
      } else {
        result.set(item.id, normalizeFundamentals({
          source: "",
          error: "Fundamentaldaten werden im Hintergrund geladen"
        }));
      }
      return;
    }

    const primary = key && item.marketSymbol
      ? await loadStatistics(item.marketSymbol, key, fetchImpl)
      : { raw: null, source: "Twelve Data", error: "Fundamentaldaten im aktuellen Tarif/Setup nicht verfügbar", fieldSources: {} };
    const fallback = await loadFundamentalFallback(item, { fetchImpl });
    const loaded = mergeFundamentalSources(primary, fallback);

    if (loaded.raw && hasAnyMetric(loaded.raw)) {
      const fetchedAt = new Date(now).toISOString();
      cache[item.id] = {
        raw: loaded.raw,
        source: loaded.source,
        fetchedAt,
        fieldSources: loaded.fieldSources,
        conflicts: loaded.conflicts
      };
      changed = true;
      result.set(item.id, normalizeFundamentals({
        ...loaded.raw,
        source: loaded.source,
        asOf: loaded.asOf || fetchedAt,
        error: loaded.error,
        fieldSources: loaded.fieldSources,
        conflicts: loaded.conflicts
      }));
      return;
    }

    const age = now - Date.parse(String(cached?.fetchedAt ?? ""));
    if (cached?.raw && Number.isFinite(age) && age <= MAX_STALE_MS) {
      result.set(item.id, normalizeFundamentals({
        ...cached.raw,
        source: `Cache · ${cached.source || "Fundamentaldaten"}`,
        stale: true,
        asOf: cached.fetchedAt,
        error: loaded.error,
        fieldSources: cached.fieldSources,
        conflicts: cached.conflicts
      }));
    } else {
      result.set(item.id, normalizeFundamentals({ source: loaded.source, error: loaded.error }));
    }
  });

  if (changed) await saveAnalysisCache("fundamentals-cache", cache);
  return result;
}

export function mergeFundamentalSources(primary, fallback) {
  const p = primary?.raw ?? {};
  const f = fallback?.raw ?? {};
  const raw = {};
  const fieldSources = {};
  const conflicts = [];

  for (const key of METRIC_KEYS) {
    const pv = finiteOrNull(p[key]);
    const fv = finiteOrNull(f[key]);
    if (pv != null) {
      raw[key] = pv;
      fieldSources[key] = primary?.fieldSources?.[key] || primary?.source || '';
      if (fv != null && materiallyConflicts(pv, fv)) conflicts.push(key);
    } else if (fv != null) {
      raw[key] = fv;
      fieldSources[key] = fallback?.fieldSources?.[key] || fallback?.source || '';
    } else {
      raw[key] = null;
    }
  }

  const sources = [...new Set(Object.values(fieldSources).filter(Boolean))];
  const errors = [primary?.error, fallback?.error].filter(Boolean);
  return {
    raw: hasAnyMetric(raw) ? raw : null,
    source: sources.join(' + ') || primary?.source || fallback?.source || '',
    asOf: newestDate(primary?.asOf, fallback?.asOf),
    error: errors.length && !hasAnyMetric(raw) ? errors.join(' · ') : null,
    fieldSources,
    conflicts
  };
}

async function loadStatistics(symbol, key, fetchImpl) {
  try {
    const url = new URL(BASE);
    url.searchParams.set("symbol", symbol);
    url.searchParams.set("apikey", key);
    const response = await fetchImpl(url, { headers: { Accept: "application/json" }, signal: AbortSignal.timeout(12_000) });
    if (!response.ok) return { raw: null, source: "Twelve Data", error: `Twelve Data statistics HTTP ${response.status}`, fieldSources: {} };
    const json = await response.json();
    if (json?.status === "error" || json?.code) return { raw: null, source: "Twelve Data", error: String(json?.message || "Twelve Data statistics nicht verfügbar"), fieldSources: {} };
    const raw = canonicalFromStatistics(json);
    const hasAny = hasAnyMetric(raw);
    const fieldSources = Object.fromEntries(METRIC_KEYS.filter((name) => finiteOrNull(raw[name]) != null).map((name) => [name, 'Twelve Data']));
    return hasAny
      ? { raw, source: "Twelve Data", error: null, fieldSources, asOf: null }
      : { raw: null, source: "Twelve Data", error: "Twelve Data statistics enthält keine nutzbaren Kennzahlen", fieldSources: {} };
  } catch (error) {
    return { raw: null, source: "Twelve Data", error: error instanceof Error ? error.message : "Fundamentaldatenfehler", fieldSources: {} };
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
    marketCap
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
function finiteOrNull(value) {
  if (value == null || (typeof value === 'string' && value.trim() === '')) return null;
  const n = Number(value);
  return Number.isFinite(n) ? n : null;
}
function materiallyConflicts(a, b) {
  const scale = Math.max(Math.abs(a), Math.abs(b), 0.01);
  return Math.abs(a - b) / scale >= 0.35;
}
function hasAnyMetric(raw) { return raw && METRIC_KEYS.some((key) => finiteOrNull(raw[key]) != null); }
function newestDate(a, b) {
  const dates = [a, b].filter(Boolean).map(String).sort();
  return dates.at(-1) ?? null;
}
async function mapLimit(items, limit, worker) {
  const queue = [...items];
  const runners = Array.from({ length: Math.min(limit, queue.length) }, async () => {
    while (queue.length) await worker(queue.shift());
  });
  await Promise.all(runners);
}
