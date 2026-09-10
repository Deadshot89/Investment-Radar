const STOCK_FUNDAMENTAL_FIELDS = [
  'pe', 'priceToSales', 'evToEbitda', 'freeCashFlowYield', 'revenueGrowth', 'epsGrowth',
  'operatingMargin', 'netMargin', 'roe', 'roic', 'debtToEquity'
];

export function evaluateDataQuality({ item, quote, history, fundamentals }) {
  const type = String(item?.type ?? '').toUpperCase();
  const isEtf = type === 'ETF';

  const quoteCoverage = coverageForFields(quote, ['price', 'currency', 'percentChange']);
  const historyCoverage = historyScore(history);
  const fundamentalCoverage = isEtf ? null : coverageForFundamentals(fundamentals);
  const forecastInputCoverage = forecastCoverage({ item, quote, history, fundamentals, isEtf });
  const missingBlocks = [];

  const quoteCritical = finite(quote?.price) == null || !String(quote?.currency ?? '').trim();
  const explicitPoints = finite(history?.pointsCount);
  const historyCritical = (explicitPoints != null ? explicitPoints < 120 : historyCoverage < 70) || finite(history?.m6) == null;
  const stockFundamentalsCritical = !isEtf && fundamentalCoverage < 50;

  if (quoteCritical) missingBlocks.push('quote');
  if (historyCritical) missingBlocks.push('history');
  if (stockFundamentalsCritical) missingBlocks.push('fundamentals');
  if (forecastInputCoverage < 70) missingBlocks.push('forecast');

  const criticalConflicts = !isEtf && Array.isArray(fundamentals?.conflicts)
    ? [...new Set(fundamentals.conflicts.filter(Boolean))]
    : [];

  const overallCoverage = isEtf
    ? round(quoteCoverage * 0.35 + historyCoverage * 0.45 + etfMetadataCoverage(item) * 0.20)
    : round(quoteCoverage * 0.20 + historyCoverage * 0.30 + fundamentalCoverage * 0.50);

  const hasCriticalFailure = quoteCritical || historyCritical || stockFundamentalsCritical || criticalConflicts.length > 0;
  const qualityTier = hasCriticalFailure
    ? 'UNVOLLSTÄNDIG'
    : overallCoverage >= 85
      ? 'HOCH'
      : overallCoverage >= 70
        ? 'GUT'
        : overallCoverage >= 50
          ? 'EINGESCHRÄNKT'
          : 'UNVOLLSTÄNDIG';

  return {
    quoteCoverage,
    historyCoverage,
    fundamentalCoverage: isEtf ? 100 : fundamentalCoverage,
    forecastInputCoverage,
    overallCoverage,
    qualityTier,
    missingBlocks: [...new Set(missingBlocks)],
    criticalConflicts
  };
}

function coverageForFields(obj, fields) {
  if (!obj) return 0;
  const present = fields.filter((field) => {
    const value = obj?.[field];
    if (typeof value === 'string') return value.trim().length > 0;
    return finite(value) != null;
  }).length;
  return round((present / fields.length) * 100);
}

function historyScore(history) {
  if (!history) return 0;
  const explicit = finite(history.coveragePct);
  if (explicit != null) return clamp(round(explicit), 0, 100);

  const points = Number(history.pointsCount ?? 0);
  const pointsPct = clamp((points / 230) * 100, 0, 100);
  const horizons = ['m1', 'm3', 'm6', 'm12'].filter((key) => finite(history?.[key]) != null).length;
  const horizonPct = (horizons / 4) * 100;
  return round(pointsPct * 0.6 + horizonPct * 0.4);
}

function coverageForFundamentals(fundamentals) {
  const explicit = finite(fundamentals?.coveragePct);
  if (explicit != null) return clamp(round(explicit), 0, 100);
  const metrics = fundamentals?.metrics ?? fundamentals ?? {};
  const present = STOCK_FUNDAMENTAL_FIELDS.filter((field) => finite(metrics?.[field]) != null).length;
  return round((present / STOCK_FUNDAMENTAL_FIELDS.length) * 100);
}

function forecastCoverage({ item, quote, history, fundamentals, isEtf }) {
  const inputs = [
    finite(quote?.price) != null,
    finite(history?.m3) != null,
    finite(history?.m6) != null,
    finite(history?.m12) != null,
    Number.isFinite(Number(item?.risk))
  ];

  if (!isEtf) {
    const metrics = fundamentals?.metrics ?? fundamentals ?? {};
    inputs.push(
      finite(metrics?.revenueGrowth) != null,
      finite(metrics?.epsGrowth) != null,
      finite(metrics?.pe) != null || finite(metrics?.evToEbitda) != null,
      finite(metrics?.roe) != null || finite(metrics?.roic) != null
    );
    const explicitFundamentals = finite(fundamentals?.coveragePct);
    if (explicitFundamentals != null && explicitFundamentals >= 70 && Object.keys(metrics).length === 0) {
      return round((inputs.slice(0, 5).filter(Boolean).length / 5) * 100);
    }
  }

  return round((inputs.filter(Boolean).length / inputs.length) * 100);
}

function etfMetadataCoverage(item) {
  const checks = [
    Number.isFinite(Number(item?.risk)),
    Boolean(String(item?.region ?? '').trim()),
    Boolean(String(item?.sector ?? item?.category ?? '').trim())
  ];
  return round((checks.filter(Boolean).length / checks.length) * 100);
}

function finite(value) {
  if (value == null || (typeof value === 'string' && value.trim() === '')) return null;
  const n = Number(value);
  return Number.isFinite(n) ? n : null;
}

function round(value) { return Math.round(value); }
function clamp(value, min, max) { return Math.min(max, Math.max(min, value)); }
