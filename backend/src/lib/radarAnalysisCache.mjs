const DEFAULT_TTL_MS = 5 * 60 * 1000;

let snapshot = null;
let inFlight = null;

export async function getRadarAnalysisSnapshot({ key, load, now = Date.now(), ttlMs = DEFAULT_TTL_MS }) {
  if (typeof load !== "function") throw new Error("Radar analysis loader missing");
  const cacheKey = String(key ?? "");
  const cacheTtl = Math.max(0, Number(ttlMs) || 0);

  if (snapshot && snapshot.key === cacheKey && now - snapshot.createdAt <= cacheTtl) {
    return { items: snapshot.items, generatedAt: snapshot.generatedAt, cacheHit: true };
  }

  if (inFlight && inFlight.key === cacheKey) {
    const shared = await inFlight.promise;
    return { items: shared.items, generatedAt: shared.generatedAt, cacheHit: true };
  }

  const promise = Promise.resolve()
    .then(load)
    .then((items) => {
      const completed = {
        key: cacheKey,
        createdAt: now,
        generatedAt: new Date(now).toISOString(),
        items: Array.isArray(items) ? items : []
      };
      snapshot = completed;
      return completed;
    })
    .finally(() => {
      if (inFlight?.promise === promise) inFlight = null;
    });

  inFlight = { key: cacheKey, promise };
  const loaded = await promise;
  return { items: loaded.items, generatedAt: loaded.generatedAt, cacheHit: false };
}

export function resetRadarAnalysisCache() {
  snapshot = null;
  inFlight = null;
}

export function radarAnalysisKey(items) {
  return (Array.isArray(items) ? items : [])
    .map((item) => [item?.id, item?.type, item?.risk, item?.dataQualityTier, item?.tradeRepublicEligible].join(":"))
    .join("|");
}
