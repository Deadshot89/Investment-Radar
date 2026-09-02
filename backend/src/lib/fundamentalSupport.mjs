const METRIC_KEYS = [
  "pe", "priceToSales", "evToEbitda", "freeCashFlowYield",
  "revenueGrowth", "epsGrowth", "operatingMargin", "netMargin",
  "roe", "roic", "debtToEquity"
];

export function normalizeFundamentals(raw = {}) {
  const metrics = {
    pe: numberOrNull(raw.pe),
    priceToSales: numberOrNull(raw.priceToSales),
    evToEbitda: numberOrNull(raw.evToEbitda),
    freeCashFlowYield: ratioOrNull(raw.freeCashFlowYield),
    revenueGrowth: ratioOrNull(raw.revenueGrowth),
    epsGrowth: ratioOrNull(raw.epsGrowth),
    operatingMargin: ratioOrNull(raw.operatingMargin),
    netMargin: ratioOrNull(raw.netMargin),
    roe: ratioOrNull(raw.roe),
    roic: ratioOrNull(raw.roic),
    debtToEquity: numberOrNull(raw.debtToEquity)
  };

  const qualityScores = present([
    map(metrics.operatingMargin, -0.05, 0.35),
    map(metrics.netMargin, -0.05, 0.30),
    map(metrics.roe, 0.0, 0.30),
    map(metrics.roic, 0.0, 0.25),
    inverseMap(metrics.debtToEquity, 0.2, 2.5)
  ]);
  const valuationScores = present([
    inverseMap(metrics.pe, 15, 60),
    inverseMap(metrics.priceToSales, 2.5, 18),
    inverseMap(metrics.evToEbitda, 10, 40),
    map(metrics.freeCashFlowYield, 0.0, 0.08)
  ]);
  const growthScores = present([
    map(metrics.revenueGrowth, -0.15, 0.25),
    map(metrics.epsGrowth, -0.25, 0.35)
  ]);
  const presentCount = METRIC_KEYS.filter((key) => metrics[key] != null).length;

  return {
    metrics,
    qualityScore: averageOrNull(qualityScores),
    valuationScore: averageOrNull(valuationScores),
    growthScore: averageOrNull(growthScores),
    coveragePct: Math.round((presentCount / METRIC_KEYS.length) * 100),
    source: String(raw.source ?? ""),
    stale: Boolean(raw.stale),
    asOf: raw.asOf == null ? null : String(raw.asOf),
    error: raw.error == null ? null : String(raw.error)
  };
}

function map(value, low, high) {
  if (value == null) return null;
  if (high <= low) return null;
  return clamp(((value - low) / (high - low)) * 100, 0, 100);
}
function inverseMap(value, good, bad) {
  if (value == null) return null;
  if (bad <= good) return null;
  return clamp(100 - ((value - good) / (bad - good)) * 100, 0, 100);
}
function present(values) { return values.filter(Number.isFinite); }
function averageOrNull(values) { return values.length ? Math.round(values.reduce((a, b) => a + b, 0) / values.length) : null; }
function numberOrNull(value) {
  if (value == null || (typeof value === "string" && value.trim() === "")) return null;
  const n = typeof value === "number" ? value : Number(value);
  return Number.isFinite(n) ? n : null;
}
function ratioOrNull(value) {
  const n = numberOrNull(value);
  if (n == null) return null;
  return Math.abs(n) > 2 ? n / 100 : n;
}
function clamp(value, min, max) { return Math.max(min, Math.min(max, value)); }
