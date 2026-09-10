export function normalizeAnalysisInput({ item, quote, history, fundamentals }) {
  const metrics = fundamentals?.metrics ?? fundamentals ?? {};
  return {
    item: item ?? null,
    quote: normalizeObjectNumbers(quote),
    history: normalizeObjectNumbers(history),
    fundamentals: fundamentals == null
      ? null
      : {
          ...normalizeObjectNumbers(metrics),
          source: fundamentals?.source ?? metrics?.source ?? '',
          asOf: fundamentals?.asOf ?? null,
          stale: Boolean(fundamentals?.stale),
          error: fundamentals?.error ?? null,
          fieldSources: fundamentals?.fieldSources ?? {},
          conflicts: Array.isArray(fundamentals?.conflicts) ? [...fundamentals.conflicts] : []
        }
  };
}

function normalizeObjectNumbers(value) {
  if (value == null) return null;
  if (typeof value !== 'object' || Array.isArray(value)) return value;
  return Object.fromEntries(Object.entries(value).map(([key, child]) => {
    if (child == null) return [key, null];
    if (typeof child === 'number') return [key, Number.isFinite(child) ? child : null];
    if (typeof child === 'string') {
      if (isNumericField(key)) return [key, finiteOrNull(child)];
      return [key, child];
    }
    return [key, child];
  }));
}

function isNumericField(key) {
  return new Set([
    'price', 'priceEur', 'percentChange', 'change', 'dayHigh', 'dayLow', 'week52High', 'week52Low',
    'm1', 'm3', 'm6', 'm12', 'pointsCount', 'coveragePct', 'volatility',
    'pe', 'priceToSales', 'evToEbitda', 'freeCashFlowYield', 'revenueGrowth', 'epsGrowth',
    'operatingMargin', 'netMargin', 'roe', 'roic', 'debtToEquity', 'marketCap'
  ]).has(key);
}

function finiteOrNull(value) {
  if (value == null || (typeof value === 'string' && value.trim() === '')) return null;
  const n = Number(value);
  return Number.isFinite(n) ? n : null;
}
