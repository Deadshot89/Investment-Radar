const YAHOO_BASE = 'https://query1.finance.yahoo.com/v10/finance/quoteSummary';
const SEC_BASE = 'https://data.sec.gov/api/xbrl/companyfacts';
const PROVIDER_FIELDS = [
  'pe', 'priceToSales', 'evToEbitda', 'freeCashFlowYield', 'revenueGrowth', 'epsGrowth',
  'operatingMargin', 'netMargin', 'roe', 'roic', 'debtToEquity', 'marketCap'
];

export async function loadFundamentalFallback(item, { fetchImpl = fetch } = {}) {
  const yahoo = await loadYahooFundamentals(item, fetchImpl);
  const sec = await loadSecFundamentals(item, fetchImpl);
  const merged = mergeFallbackResults(yahoo, sec);

  if (hasAny(merged.raw)) return merged;

  return {
    raw: null,
    source: merged.source,
    asOf: null,
    error: [yahoo.error, sec.error].filter(Boolean).join(' · ') || 'Keine Fundamental-Fallbackdaten verfügbar',
    fieldSources: {}
  };
}

function mergeFallbackResults(yahoo, sec) {
  const y = yahoo?.raw ?? {};
  const s = sec?.raw ?? {};
  const raw = {};
  const fieldSources = {};

  for (const key of PROVIDER_FIELDS) {
    const yv = finite(y[key]) ? Number(y[key]) : null;
    const sv = finite(s[key]) ? Number(s[key]) : null;
    if (yv != null) {
      raw[key] = yv;
      fieldSources[key] = yahoo?.fieldSources?.[key] || yahoo?.source || 'Yahoo Finance';
    } else if (sv != null) {
      raw[key] = sv;
      fieldSources[key] = sec?.fieldSources?.[key] || sec?.source || 'SEC Companyfacts';
    } else {
      raw[key] = null;
    }
  }

  const sources = [...new Set(Object.values(fieldSources).filter(Boolean))];
  return {
    raw: hasAny(raw) ? raw : null,
    source: sources.join(' + ') || yahoo?.source || sec?.source || '',
    asOf: newestDate(yahoo?.asOf, sec?.asOf),
    error: null,
    fieldSources
  };
}

async function loadYahooFundamentals(item, fetchImpl) {
  const symbol = String(item?.yahooSymbol || item?.ticker || '').trim();
  if (!symbol) return empty('Yahoo Finance', 'Kein Yahoo-Symbol verfügbar');
  try {
    const url = new URL(`${YAHOO_BASE}/${encodeURIComponent(symbol)}`);
    url.searchParams.set('modules', 'summaryDetail,defaultKeyStatistics,financialData');
    const response = await fetchImpl(url, {
      headers: { Accept: 'application/json', 'User-Agent': 'Mozilla/5.0 InvestmentRadar/2.4' },
      signal: AbortSignal.timeout(12_000)
    });
    if (!response.ok) return empty('Yahoo Finance', `Yahoo HTTP ${response.status}`);
    const json = await response.json();
    const row = json?.quoteSummary?.result?.[0];
    if (!row) return empty('Yahoo Finance', json?.quoteSummary?.error?.description || 'Yahoo Fundamentaldaten leer');
    const s = row.summaryDetail ?? {};
    const k = row.defaultKeyStatistics ?? {};
    const f = row.financialData ?? {};
    const marketCap = rawNumber(s.marketCap);
    const freeCashFlow = rawNumber(f.freeCashflow);
    const raw = {
      pe: firstFinite(rawNumber(k.forwardPE), rawNumber(s.trailingPE)),
      priceToSales: rawNumber(s.priceToSalesTrailing12Months),
      evToEbitda: rawNumber(k.enterpriseToEbitda),
      freeCashFlowYield: finite(freeCashFlow) && finite(marketCap) && marketCap !== 0 ? freeCashFlow / marketCap : null,
      revenueGrowth: rawNumber(f.revenueGrowth),
      epsGrowth: rawNumber(f.earningsGrowth),
      operatingMargin: rawNumber(f.operatingMargins),
      netMargin: rawNumber(f.profitMargins),
      roe: rawNumber(f.returnOnEquity),
      roic: null,
      debtToEquity: normalizeYahooDebtToEquity(rawNumber(f.debtToEquity)),
      marketCap
    };
    return result('Yahoo Finance', raw, new Date().toISOString());
  } catch (error) {
    return empty('Yahoo Finance', error instanceof Error ? error.message : 'Yahoo Fundamentaldatenfehler');
  }
}

async function loadSecFundamentals(item, fetchImpl) {
  const cikDigits = String(item?.cik ?? '').replace(/\D/g, '');
  if (!cikDigits) return empty('SEC Companyfacts', 'SEC übersprungen: keine CIK-Metadaten');
  const cik = cikDigits.padStart(10, '0');
  try {
    const response = await fetchImpl(`${SEC_BASE}/CIK${cik}.json`, {
      headers: {
        Accept: 'application/json',
        'User-Agent': 'InvestmentRadar/2.4 investment-radar@example.invalid'
      },
      signal: AbortSignal.timeout(12_000)
    });
    if (!response.ok) return empty('SEC Companyfacts', `SEC HTTP ${response.status}`);
    const json = await response.json();
    const facts = json?.facts?.['us-gaap'] ?? {};
    const revenue = annualSeries(facts, ['Revenues', 'RevenueFromContractWithCustomerExcludingAssessedTax', 'SalesRevenueNet']);
    const netIncome = annualSeries(facts, ['NetIncomeLoss', 'ProfitLoss']);
    const equity = latestAnnual(facts, ['StockholdersEquity', 'StockholdersEquityIncludingPortionAttributableToNoncontrollingInterest']);
    const debt = latestAnnual(facts, [
      'LongTermDebtAndFinanceLeaseObligations',
      'LongTermDebtCurrent',
      'LongTermDebtAndCapitalLeaseObligationsCurrent',
      'LongTermDebtNoncurrent'
    ]);
    const operatingIncome = latestAnnual(facts, ['OperatingIncomeLoss']);
    const currentRevenue = revenue[0]?.val ?? null;
    const previousRevenue = revenue[1]?.val ?? null;
    const currentNetIncome = netIncome[0]?.val ?? null;
    const previousNetIncome = netIncome[1]?.val ?? null;
    const raw = {
      pe: null,
      priceToSales: null,
      evToEbitda: null,
      freeCashFlowYield: null,
      revenueGrowth: growth(currentRevenue, previousRevenue),
      epsGrowth: growth(currentNetIncome, previousNetIncome),
      operatingMargin: ratio(operatingIncome?.val, currentRevenue),
      netMargin: ratio(currentNetIncome, currentRevenue),
      roe: ratio(currentNetIncome, equity?.val),
      roic: null,
      debtToEquity: ratio(debt?.val, equity?.val),
      marketCap: null
    };
    const asOf = newestFiled([revenue[0], netIncome[0], equity, debt, operatingIncome]);
    return result('SEC Companyfacts', raw, asOf);
  } catch (error) {
    return empty('SEC Companyfacts', error instanceof Error ? error.message : 'SEC Fundamentaldatenfehler');
  }
}

function annualSeries(facts, tags) {
  for (const tag of tags) {
    const entries = facts?.[tag]?.units?.USD;
    if (!Array.isArray(entries)) continue;
    const filtered = entries
      .filter((x) => ['10-K', '20-F', '40-F'].includes(String(x?.form ?? '')) && x?.fp === 'FY' && finite(x?.val))
      .sort(compareFacts)
      .filter(uniqueFiscalYear);
    if (filtered.length) return filtered;
  }
  return [];
}

function latestAnnual(facts, tags) {
  return annualSeries(facts, tags)[0] ?? null;
}

function compareFacts(a, b) {
  const ay = Number(a?.fy ?? 0);
  const by = Number(b?.fy ?? 0);
  if (ay !== by) return by - ay;
  return Date.parse(String(b?.filed ?? '')) - Date.parse(String(a?.filed ?? ''));
}

function uniqueFiscalYear(value, index, list) {
  return list.findIndex((x) => Number(x?.fy) === Number(value?.fy)) === index;
}

function newestFiled(entries) {
  const dates = entries.map((x) => x?.filed).filter(Boolean).map(String).sort();
  return dates.at(-1) ?? null;
}

function normalizeYahooDebtToEquity(value) {
  if (!finite(value)) return null;
  return Math.abs(value) > 10 ? value / 100 : value;
}

function rawNumber(value) {
  const raw = value?.raw ?? value;
  const n = Number(raw);
  return Number.isFinite(n) ? n : null;
}

function firstFinite(...values) { return values.find((v) => finite(v)) ?? null; }
function finite(value) { return Number.isFinite(Number(value)); }
function ratio(a, b) { return finite(a) && finite(b) && Number(b) !== 0 ? Number(a) / Number(b) : null; }
function growth(current, previous) { return finite(current) && finite(previous) && Number(previous) !== 0 ? (Number(current) - Number(previous)) / Math.abs(Number(previous)) : null; }
function hasAny(raw) { return raw && PROVIDER_FIELDS.some((key) => finite(raw[key])); }
function newestDate(a, b) {
  const dates = [a, b].filter(Boolean).map(String).sort();
  return dates.at(-1) ?? null;
}
function empty(source, error) { return { raw: null, source, asOf: null, error, fieldSources: {} }; }
function result(source, raw, asOf) {
  const fieldSources = Object.fromEntries(PROVIDER_FIELDS.filter((key) => finite(raw?.[key])).map((key) => [key, source]));
  return { raw, source, asOf, error: null, fieldSources };
}
