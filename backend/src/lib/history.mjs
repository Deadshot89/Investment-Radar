import { calculateMomentum } from "./historySupport.mjs";
import { isFresh, loadAnalysisCache, saveAnalysisCache } from "./analysisCache.mjs";
import { loadTradeRepublicHistories as defaultLoadTradeRepublicHistories } from "./tradeRepublicMarketData.mjs";
import { defaultResolveYahooSymbol } from "./fundamentalFallbackProvider.mjs";

const TWELVE_BASE = "https://api.twelvedata.com/time_series";
const YAHOO_BASES = [
  "https://query1.finance.yahoo.com/v8/finance/chart",
  "https://query2.finance.yahoo.com/v8/finance/chart"
];
const FRESH_MS = 6 * 60 * 60 * 1000;
const MAX_STALE_MS = 7 * 24 * 60 * 60 * 1000;

export async function loadHistory(items, {
  fetchImpl = fetch,
  now = Date.now(),
  refresh = true,
  loadTradeRepublicHistories = defaultLoadTradeRepublicHistories,
  resolveYahooSymbol = defaultResolveYahooSymbol
} = {}) {
  const cache = await loadAnalysisCache("history-cache");
  const key = process.env.TWELVE_DATA_API_KEY?.trim();
  const result = new Map();
  let changed = false;

  await mapLimit(items, 4, async (item) => {
    const cached = cache[item.id];
    if (shouldReuseFreshHistoryCache(cached, now, refresh)) {
      result.set(item.id, { ...calculateMomentum(cached.points, now), source: cached.source || "Cache", stale: false });
      return;
    }

    if (!refresh) {
      const age = now - Date.parse(String(cached?.fetchedAt ?? ""));
      if (Array.isArray(cached?.points) && Number.isFinite(age) && age <= MAX_STALE_MS) {
        result.set(item.id, {
          ...calculateMomentum(cached.points, now),
          source: `Cache · ${cached.source || "Historie"}`,
          stale: true,
          error: "Historie wird im Hintergrund aktualisiert"
        });
      } else {
        result.set(item.id, {
          ...calculateMomentum([], now),
          source: "",
          stale: false,
          error: "Historie wird im Hintergrund geladen"
        });
      }
      return;
    }

    const loaded = await loadProviderHistory(item, key, fetchImpl, loadTradeRepublicHistories, now, resolveYahooSymbol);
    if (loaded.points.length > 1) {
      cache[item.id] = { points: loaded.points, source: loaded.source, fetchedAt: new Date(now).toISOString() };
      changed = true;
      result.set(item.id, { ...calculateMomentum(loaded.points, now), source: loaded.source, stale: false });
      return;
    }

    const age = now - Date.parse(String(cached?.fetchedAt ?? ""));
    if (Array.isArray(cached?.points) && Number.isFinite(age) && age <= MAX_STALE_MS) {
      result.set(item.id, { ...calculateMomentum(cached.points, now), source: `Cache · ${cached.source || "Historie"}`, stale: true, error: loaded.error });
    } else {
      result.set(item.id, { ...calculateMomentum([], now), source: loaded.source || "", stale: false, error: loaded.error || "Keine Historie verfügbar" });
    }
  });

  if (changed) await saveAnalysisCache("history-cache", cache);
  return result;
}

export function shouldReuseFreshHistoryCache(cached, now = Date.now(), refresh = false) {
  return refresh !== true && Array.isArray(cached?.points) && isFresh(cached, FRESH_MS, now);
}

async function loadProviderHistory(item, key, fetchImpl, loadTradeRepublicHistories, now, resolveYahooSymbol) {
  const candidates = [];
  const errors = [];

  if (item?.tradeRepublicEligible === true && item?.isin) {
    try {
      const tradeRepublic = await loadTradeRepublicHistories([item]);
      const loaded = tradeRepublic.get(item.id);
      if (loaded?.points?.length > 1) {
        candidates.push(loaded);
        if (historyCoverage(loaded, now) >= 70) return loaded;
      } else if (loaded?.error) {
        errors.push(loaded.error);
      }
    } catch (error) {
      errors.push(error instanceof Error ? error.message : "Trade-Republic-Historienfehler");
    }
  }

  if (key && item.marketSymbol) {
    const twelve = await loadTwelveHistory(item.marketSymbol, key, fetchImpl);
    if (twelve.points.length > 1) {
      candidates.push(twelve);
      if (historyCoverage(twelve, now) >= 70) return bestHistoryCandidate(candidates, now);
    } else if (twelve.error) {
      errors.push(twelve.error);
    }
  }

  const yahooSymbol = await resolveYahooHistorySymbol(item, fetchImpl, resolveYahooSymbol);
  if (yahooSymbol) {
    const yahoo = await loadYahooHistory(yahooSymbol, fetchImpl);
    if (yahoo.points.length > 1) candidates.push(yahoo);
    else if (yahoo.error) errors.push(yahoo.error);
  }

  const best = bestHistoryCandidate(candidates, now);
  if (best) return best;
  return {
    points: [],
    source: "",
    error: [...new Set(errors.filter(Boolean))].join(" · ") || "Kein Historien-Symbol verfügbar"
  };
}

export function bestHistoryCandidate(candidates, now = Date.now()) {
  const valid = (candidates ?? []).filter((candidate) => Array.isArray(candidate?.points) && candidate.points.length > 1);
  if (!valid.length) return null;
  return [...valid].sort((a, b) => {
    const coverageDelta = historyCoverage(b, now) - historyCoverage(a, now);
    if (coverageDelta !== 0) return coverageDelta;
    return b.points.length - a.points.length;
  })[0];
}

function historyCoverage(candidate, now) {
  return Number(calculateMomentum(candidate?.points ?? [], now)?.coveragePct ?? 0);
}

async function loadTwelveHistory(symbol, key, fetchImpl) {
  try {
    const url = new URL(TWELVE_BASE);
    url.searchParams.set("symbol", symbol);
    url.searchParams.set("interval", "1day");
    url.searchParams.set("outputsize", "400");
    url.searchParams.set("apikey", key);
    const response = await fetchImpl(url, { headers: { Accept: "application/json" }, signal: AbortSignal.timeout(12_000) });
    if (!response.ok) return { points: [], source: "Twelve Data", error: `Twelve Data HTTP ${response.status}` };
    const json = await response.json();
    if (json?.status === "error") return { points: [], source: "Twelve Data", error: String(json.message || "Twelve Data Historienfehler") };
    const points = (Array.isArray(json?.values) ? json.values : [])
      .map((value) => ({ time: Date.parse(`${value.datetime}T00:00:00Z`), close: Number(value.close) }))
      .filter((p) => Number.isFinite(p.time) && Number.isFinite(p.close) && p.close > 0)
      .sort((a, b) => a.time - b.time);
    return { points, source: "Twelve Data", error: points.length ? null : "Twelve Data Historie leer" };
  } catch (error) {
    return { points: [], source: "Twelve Data", error: error instanceof Error ? error.message : "Twelve Data Historienfehler" };
  }
}

async function loadYahooHistory(symbol, fetchImpl) {
  let lastError = "Yahoo Historienfehler";
  for (const base of YAHOO_BASES) {
    try {
      const url = new URL(`${base}/${encodeURIComponent(symbol)}`);
      url.searchParams.set("range", "1y");
      url.searchParams.set("interval", "1d");
      url.searchParams.set("includePrePost", "false");
      const response = await fetchImpl(url, {
        headers: { Accept: "application/json", "User-Agent": "Mozilla/5.0 InvestmentRadar/2.4" },
        signal: AbortSignal.timeout(12_000)
      });
      if (!response.ok) { lastError = `Yahoo HTTP ${response.status}`; continue; }
      const json = await response.json();
      const row = json?.chart?.result?.[0];
      const times = row?.timestamp ?? [];
      const closes = row?.indicators?.quote?.[0]?.close ?? [];
      const points = times.map((time, i) => ({ time: Number(time) * 1000, close: Number(closes[i]) }))
        .filter((p) => Number.isFinite(p.time) && Number.isFinite(p.close) && p.close > 0)
        .sort((a, b) => a.time - b.time);
      if (points.length > 1) return { points, source: "Yahoo Finance", error: null };
      lastError = json?.chart?.error?.description || "Yahoo Historie leer";
    } catch (error) {
      lastError = error instanceof Error ? error.message : "Yahoo Historienfehler";
    }
  }
  return { points: [], source: "Yahoo Finance", error: lastError };
}

async function resolveYahooHistorySymbol(item, fetchImpl, resolveYahooSymbol) {
  const explicit = String(item?.yahooSymbol ?? '').trim();
  if (explicit) return explicit;
  const configured = String(item?.providerSymbols?.yahoo ?? item?.historySymbol ?? '').trim();
  if (configured) return configured;

  try {
    const resolved = await resolveYahooSymbol(item, fetchImpl);
    if (resolved) return resolved;
  } catch {}

  if (item?.providerSymbolUnresolved === true) return "";
  const raw = String(item?.ticker ?? "").trim();
  if (!raw) return "";
  const market = String(item?.marketSymbol ?? "").toUpperCase();
  const exchange = String(item?.exchange ?? item?.market ?? "").toUpperCase();
  if (market.endsWith(":XETR") || exchange === 'XETRA') return `${raw}.DE`;
  return raw;
}

async function mapLimit(items, limit, worker) {
  const queue = [...items];
  const runners = Array.from({ length: Math.min(limit, queue.length) }, async () => {
    while (queue.length) await worker(queue.shift());
  });
  await Promise.all(runners);
}
