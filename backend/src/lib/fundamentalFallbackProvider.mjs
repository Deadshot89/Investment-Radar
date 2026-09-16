const YAHOO_SUMMARY_BASE = 'https://query2.finance.yahoo.com/v10/finance/quoteSummary';
const YAHOO_SEARCH_BASE = 'https://query1.finance.yahoo.com/v1/finance/search';
const YAHOO_CRUMB_URL = 'https://query1.finance.yahoo.com/v1/test/getcrumb';
const YAHOO_COOKIE_URL = 'https://fc.yahoo.com';
const SEC_BASE = 'https://data.sec.gov/api/xbrl/companyfacts';
const YAHOO_SESSION_TTL_MS = 20 * 60 * 1000;
const YAHOO_SYMBOL_TTL_MS = 24 * 60 * 60 * 1000;
const USER_AGENT = 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 InvestmentRadar/2.5';

const PROVIDER_FIELDS = [
  'pe', 'priceToSales', 'evToEbitda', 'freeCashFlowYield', 'revenueGrowth', 'epsGrowth',
  'operatingMargin', 'netMargin', 'roe', 'roic', 'debtToEquity', 'marketCap'
];

const yahooSessionCache = new WeakMap();
const yahooSymbolCache = new Map();
let yahooThrottleChain = Promise.resolve();
let yahooNextRequestAt = 0;

export async function loadFundamentalFallback(item, {
  fetchImpl = fetch,
  getYahooSession = defaultGetYahooSession,
  resolveYahooSymbol = defaultResolveYahooSymbol
} = {}) {
  const yahoo = await loadYahooFundamentals(item, { fetchImpl, getYahooSession, resolveYahooSymbol });
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

async function loadYahooFundamentals(item, { fetchImpl, getYahooSession, resolveYahooSymbol }) {
  const symbol = await resolveYahooSymbol(item, fetchImpl);
  if (!symbol) return empty('Yahoo Finance', 'Kein verifiziertes Yahoo-Symbol verfügbar');

  try {
    let session = await getYahooSession(fetchImpl, { forceRefresh: false });
    let response = await fetchYahooSummary(symbol, fetchImpl, session);

    if (response.status === 401 || response.status === 403) {
      session = await getYahooSession(fetchImpl, { forceRefresh: true });
      response = await fetchYahooSummary(symbol, fetchImpl, session);
    }

    if (!response.ok) return empty('Yahoo Finance', `Yahoo HTTP ${response.status}`);
    const json = await response.json();
    const row = json?.quoteSummary?.result?.[0];
    if (!row) {
      return empty('Yahoo Finance', json?.quoteSummary?.error?.description || 'Yahoo Fundamentaldaten leer');
    }

    const s = row.summaryDetail ?? {};
    const k = row.defaultKeyStatistics ?? {};
    const f = row.financialData ?? {};
    const marketCap = firstFinite(rawNumber(s.marketCap), rawNumber(k.marketCap));
    const freeCashFlow = rawNumber(f.freeCashflow);
    const raw = {
      pe: firstFinite(rawNumber(k.forwardPE), rawNumber(s.trailingPE)),
      priceToSales: firstFinite(rawNumber(s.priceToSalesTrailing12Months), rawNumber(k.priceToSalesTrailing12Months)),
      evToEbitda: rawNumber(k.enterpriseToEbitda),
      freeCashFlowYield: finite(freeCashFlow) && finite(marketCap) && marketCap !== 0 ? freeCashFlow / marketCap : null,
      revenueGrowth: rawNumber(f.revenueGrowth),
      epsGrowth: firstFinite(rawNumber(f.earningsGrowth), rawNumber(k.earningsQuarterlyGrowth)),
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

async function fetchYahooSummary(symbol, fetchImpl, session) {
  if (fetchImpl === globalThis.fetch) await throttleYahoo();
  const url = new URL(`${YAHOO_SUMMARY_BASE}/${encodeURIComponent(symbol)}`);
  url.searchParams.set('modules', 'summaryDetail,defaultKeyStatistics,financialData');
  url.searchParams.set('crumb', session.crumb);
  return fetchImpl(url, {
    headers: {
      Accept: 'application/json',
      'User-Agent': USER_AGENT,
      Cookie: session.cookie
    },
    signal: AbortSignal.timeout(12_000)
  });
}

export async function defaultResolveYahooSymbol(item, fetchImpl = fetch) {
  const explicit = firstString(item?.yahooSymbol, item?.providerSymbols?.yahoo);
  if (explicit) return explicit.toUpperCase();

  const ticker = String(item?.ticker ?? '').trim().toUpperCase();
  const unresolved = item?.providerSymbolUnresolved === true || looksLikeIsin(ticker);
  if (!unresolved && ticker) return ticker;

  const isin = String(item?.isin ?? '').trim().toUpperCase();
  if (!looksLikeIsin(isin)) return '';

  const cached = yahooSymbolCache.get(isin);
  if (cached && Date.now() - cached.cachedAt <= YAHOO_SYMBOL_TTL_MS) return cached.symbol;

  try {
    const url = new URL(YAHOO_SEARCH_BASE);
    url.searchParams.set('q', isin);
    url.searchParams.set('quotesCount', '8');
    url.searchParams.set('newsCount', '0');
    if (fetchImpl === globalThis.fetch) await throttleYahoo();
    const response = await fetchImpl(url, {
      headers: { Accept: 'application/json', 'User-Agent': USER_AGENT },
      signal: AbortSignal.timeout(12_000)
    });
    if (!response.ok) return '';

    const json = await response.json();
    const candidates = Array.isArray(json?.quotes) ? json.quotes : [];
    const expectedType = String(item?.type ?? '').toUpperCase() === 'ETF' ? 'ETF' : 'EQUITY';
    const candidate = candidates.find((quote) =>
      String(quote?.quoteType ?? '').toUpperCase() === expectedType &&
      typeof quote?.symbol === 'string' &&
      quote.symbol.trim()
    );
    const symbol = String(candidate?.symbol ?? '').trim().toUpperCase();
    if (symbol) yahooSymbolCache.set(isin, { symbol, cachedAt: Date.now() });
    return symbol;
  } catch {
    return '';
  }
}

export async function defaultGetYahooSession(fetchImpl = fetch, { forceRefresh = false } = {}) {
  const now = Date.now();
  const existing = yahooSessionCache.get(fetchImpl);
  if (!forceRefresh && existing && now - existing.createdAt <= YAHOO_SESSION_TTL_MS) return existing;

  if (fetchImpl === globalThis.fetch) await throttleYahoo();
  const warm = await fetchImpl(YAHOO_COOKIE_URL, {
    headers: { 'User-Agent': USER_AGENT },
    redirect: 'manual',
    signal: AbortSignal.timeout(12_000)
  });
  const cookie = extractCookieHeader(warm?.headers);
  if (!cookie) throw new Error('Yahoo Session-Cookie fehlt');

  if (fetchImpl === globalThis.fetch) await throttleYahoo();
  const crumbResponse = await fetchImpl(YAHOO_CRUMB_URL, {
    headers: {
      Accept: 'text/plain,*/*',
      'User-Agent': USER_AGENT,
      Cookie: cookie
    },
    signal: AbortSignal.timeout(12_000)
  });
  if (!crumbResponse.ok) throw new Error(`Yahoo Crumb HTTP ${crumbResponse.status}`);
  const crumb = String(await crumbResponse.text()).trim();
  if (!crumb || /Unauthorized|Too Many Requests/i.test(crumb)) throw new Error('Yahoo Crumb ungültig');

  const session = { cookie, crumb, createdAt: now };
  yahooSessionCache.set(fetchImpl, session);
  return session;
}

async function throttleYahoo() {
  const previous = yahooThrottleChain;
  let release;
  yahooThrottleChain = new Promise((resolve) => { release = resolve; });
  await previous;
  try {
    const delay = Math.max(0, yahooNextRequestAt - Date.now());
    if (delay > 0) await new Promise((resolve) => setTimeout(resolve, delay));
    yahooNextRequestAt = Date.now() + 100;
  } finally {
    release();
  }
}

function extractCookieHeader(headers) {
  if (!headers) return '';
  const many = typeof headers.getSetCookie === 'function' ? headers.getSetCookie() : [];
  const raw = many.length
    ? many
    : typeof headers.get === 'function' && headers.get('set-cookie')
      ? [headers.get('set-cookie')]
      : [];
  return raw
    .map((value) => String(value ?? '').split(';')[0].trim())
    .filter(Boolean)
    .join('; ');
}

async function loadSecFundamentals(item, fetchImpl) {
  const cikDigits = String(item?.cik ?? '').replace(/\D/g, '');
  if (!cikDigits) return empty('SEC Companyfacts', 'SEC übersprungen: keine CIK-Metadaten');
  const cik = cikDigits.padStart(10, '0');
  try {
    const response = await fetchImpl(`${SEC_BASE}/CIK${cik}.json`, {
      headers: {
        Accept: 'application/json',
        'User-Agent': 'InvestmentRadar/2.5 investment-radar@example.invalid'
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
  if (raw == null || (typeof raw === 'string' && raw.trim() === '')) return null;
  const n = Number(raw);
  return Number.isFinite(n) ? n : null;
}

function firstString(...values) {
  for (const value of values) {
    if (typeof value === 'string' && value.trim()) return value.trim();
  }
  return '';
}
function looksLikeIsin(value) {
  return /^[A-Z]{2}[A-Z0-9]{10}$/.test(String(value ?? '').trim().toUpperCase());
}
function firstFinite(...values) { return values.find((v) => finite(v)) ?? null; }
function finite(value) {
  if (value == null || (typeof value === 'string' && value.trim() === '')) return false;
  return Number.isFinite(Number(value));
}
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
