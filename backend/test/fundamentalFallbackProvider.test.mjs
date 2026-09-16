import test from 'node:test';
import assert from 'node:assert/strict';
import {
  defaultGetYahooSession,
  defaultResolveYahooSymbol,
  loadFundamentalFallback
} from '../src/lib/fundamentalFallbackProvider.mjs';

function jsonResponse(body, status = 200, extra = {}) {
  return {
    ok: status >= 200 && status < 300,
    status,
    json: async () => body,
    text: async () => typeof body === 'string' ? body : JSON.stringify(body),
    headers: extra.headers ?? { getSetCookie: () => [], get: () => null },
    ...extra
  };
}

const TEST_SESSION = async () => ({ cookie: 'A3=test', crumb: 'crumb-test', createdAt: Date.now() });

test('Yahoo quoteSummary maps explicit canonical fundamentals', async () => {
  const fetchImpl = async (url) => {
    assert.match(String(url), /query2\.finance\.yahoo\.com\/v10\/finance\/quoteSummary\/AAPL/);
    return jsonResponse({
      quoteSummary: { result: [{
        summaryDetail: { trailingPE: { raw: 31 }, priceToSalesTrailing12Months: { raw: 8.2 }, marketCap: { raw: 3500000000000 } },
        defaultKeyStatistics: { enterpriseToEbitda: { raw: 24 }, forwardPE: { raw: 28 } },
        financialData: {
          debtToEquity: { raw: 145 }, returnOnEquity: { raw: 1.5 }, revenueGrowth: { raw: 0.06 }, earningsGrowth: { raw: 0.08 },
          operatingMargins: { raw: 0.31 }, profitMargins: { raw: 0.25 }, freeCashflow: { raw: 100000000000 }
        }
      }] }
    });
  };

  const out = await loadFundamentalFallback({ yahooSymbol: 'AAPL', type: 'STOCK' }, { fetchImpl, getYahooSession: TEST_SESSION });
  assert.equal(out.raw.pe, 28);
  assert.equal(out.raw.priceToSales, 8.2);
  assert.equal(out.raw.evToEbitda, 24);
  assert.equal(out.raw.revenueGrowth, 0.06);
  assert.equal(out.raw.epsGrowth, 0.08);
  assert.equal(out.raw.operatingMargin, 0.31);
  assert.equal(out.raw.netMargin, 0.25);
  assert.equal(out.raw.roe, 1.5);
  assert.equal(out.raw.debtToEquity, 1.45);
  assert.ok(out.raw.freeCashFlowYield > 0);
  assert.equal(out.source, 'Yahoo Finance');
  assert.equal(out.fieldSources.pe, 'Yahoo Finance');
});

test('SEC Companyfacts fills explicit US facts when Yahoo is unavailable', async () => {
  const calls = [];
  const fetchImpl = async (url) => {
    calls.push(String(url));
    if (String(url).includes('query1.finance.yahoo.com')) return jsonResponse({}, 404);
    return jsonResponse({
      facts: { 'us-gaap': {
        Revenues: { units: { USD: [
          { fy: 2025, fp: 'FY', form: '10-K', filed: '2025-11-01', val: 400 },
          { fy: 2024, fp: 'FY', form: '10-K', filed: '2024-11-01', val: 360 }
        ] } },
        NetIncomeLoss: { units: { USD: [
          { fy: 2025, fp: 'FY', form: '10-K', filed: '2025-11-01', val: 100 },
          { fy: 2024, fp: 'FY', form: '10-K', filed: '2024-11-01', val: 90 }
        ] } },
        StockholdersEquity: { units: { USD: [
          { fy: 2025, fp: 'FY', form: '10-K', filed: '2025-11-01', val: 200 }
        ] } },
        LongTermDebtAndFinanceLeaseObligations: { units: { USD: [
          { fy: 2025, fp: 'FY', form: '10-K', filed: '2025-11-01', val: 300 }
        ] } }
      } }
    });
  };

  const out = await loadFundamentalFallback({ yahooSymbol: 'AAPL', cik: '320193', type: 'STOCK' }, { fetchImpl, getYahooSession: TEST_SESSION });
  assert.ok(calls.some((url) => url.includes('/CIK0000320193.json')));
  assert.equal(out.raw.revenueGrowth, (400 - 360) / 360);
  assert.equal(out.raw.netMargin, 0.25);
  assert.equal(out.raw.roe, 0.5);
  assert.equal(out.raw.debtToEquity, 1.5);
  assert.match(out.source, /SEC Companyfacts/);
});

test('SEC supplements partial Yahoo data for safely matched US companies', async () => {
  const calls = [];
  const fetchImpl = async (url) => {
    const href = String(url);
    calls.push(href);
    if (href.includes('query2.finance.yahoo.com')) {
      return jsonResponse({
        quoteSummary: { result: [{
          summaryDetail: { trailingPE: { raw: 30 } },
          defaultKeyStatistics: {},
          financialData: {}
        }] }
      });
    }
    return jsonResponse({
      facts: { 'us-gaap': {
        Revenues: { units: { USD: [
          { fy: 2025, fp: 'FY', form: '10-K', filed: '2025-11-01', val: 400 },
          { fy: 2024, fp: 'FY', form: '10-K', filed: '2024-11-01', val: 320 }
        ] } },
        NetIncomeLoss: { units: { USD: [
          { fy: 2025, fp: 'FY', form: '10-K', filed: '2025-11-01', val: 80 },
          { fy: 2024, fp: 'FY', form: '10-K', filed: '2024-11-01', val: 64 }
        ] } },
        StockholdersEquity: { units: { USD: [
          { fy: 2025, fp: 'FY', form: '10-K', filed: '2025-11-01', val: 200 }
        ] } }
      } }
    });
  };

  const out = await loadFundamentalFallback({ yahooSymbol: 'AAPL', cik: '320193', type: 'STOCK' }, { fetchImpl, getYahooSession: TEST_SESSION });
  assert.ok(calls.some((url) => url.includes('query2.finance.yahoo.com')));
  assert.ok(calls.some((url) => url.includes('/CIK0000320193.json')));
  assert.equal(out.raw.pe, 30);
  assert.equal(out.raw.revenueGrowth, 0.25);
  assert.equal(out.raw.netMargin, 0.20);
  assert.equal(out.fieldSources.pe, 'Yahoo Finance');
  assert.equal(out.fieldSources.revenueGrowth, 'SEC Companyfacts');
  assert.match(out.source, /Yahoo Finance/);
  assert.match(out.source, /SEC Companyfacts/);
});

test('Yahoo direct null values remain missing instead of becoming zero', async () => {
  const fetchImpl = async () => jsonResponse({
    quoteSummary: { result: [{
      summaryDetail: { trailingPE: null, priceToSalesTrailing12Months: null, marketCap: null },
      defaultKeyStatistics: { forwardPE: null, enterpriseToEbitda: null },
      financialData: {
        revenueGrowth: null, earningsGrowth: null, operatingMargins: null,
        profitMargins: null, returnOnEquity: null, debtToEquity: null, freeCashflow: null
      }
    }] }
  });

  const out = await loadFundamentalFallback({ yahooSymbol: 'NULLS', type: 'STOCK' }, { fetchImpl, getYahooSession: TEST_SESSION });
  assert.equal(out.raw, null);
  assert.deepEqual(out.fieldSources, {});
});

test('SEC is skipped when CIK is absent instead of guessing identity', async () => {
  let calls = 0;
  const fetchImpl = async () => { calls += 1; return jsonResponse({}, 404); };
  const out = await loadFundamentalFallback({ ticker: 'UNKNOWN', type: 'STOCK' }, { fetchImpl, getYahooSession: TEST_SESSION });
  assert.equal(calls, 1);
  assert.equal(out.raw, null);
  assert.match(out.error, /Yahoo|Fundamental/i);
});


test('resolves an unresolved Trade Republic ISIN to a verified Yahoo equity symbol', async () => {
  const calls = [];
  const fetchImpl = async (url) => {
    const href = String(url);
    calls.push(href);
    if (href.includes('/v1/finance/search')) {
      return jsonResponse({
        quotes: [
          { symbol: 'MMM', quoteType: 'EQUITY', shortname: '3M Company', exchange: 'NYQ' }
        ]
      });
    }
    if (href.includes('/v10/finance/quoteSummary/MMM')) {
      return jsonResponse({
        quoteSummary: { result: [{
          summaryDetail: {
            trailingPE: { raw: 28.9 },
            priceToSalesTrailing12Months: { raw: 3.3 },
            marketCap: { raw: 84_000_000_000 }
          },
          defaultKeyStatistics: {
            forwardPE: { raw: 16.6 },
            enterpriseToEbitda: { raw: 13.5 }
          },
          financialData: {
            revenueGrowth: { raw: 0.025 },
            earningsGrowth: { raw: 0.04 },
            operatingMargins: { raw: 0.20 },
            profitMargins: { raw: 0.16 },
            returnOnEquity: { raw: 0.81 },
            debtToEquity: { raw: 180 },
            freeCashflow: { raw: 4_200_000_000 }
          }
        }] }
      });
    }
    return jsonResponse({}, 404);
  };

  const item = {
    isin: 'US88579Y1010',
    ticker: 'US88579Y1010',
    type: 'AKTIE',
    providerSymbolUnresolved: true
  };
  const symbol = await defaultResolveYahooSymbol(item, fetchImpl);
  assert.equal(symbol, 'MMM');

  const out = await loadFundamentalFallback(item, {
    fetchImpl,
    getYahooSession: TEST_SESSION,
    resolveYahooSymbol: async () => symbol
  });
  assert.equal(out.raw.pe, 16.6);
  assert.equal(out.raw.priceToSales, 3.3);
  assert.equal(out.raw.evToEbitda, 13.5);
  assert.equal(out.raw.revenueGrowth, 0.025);
  assert.equal(out.raw.operatingMargin, 0.20);
  assert.equal(out.raw.roe, 0.81);
  assert.equal(out.raw.roic, null);
  assert.ok(calls.some((href) => href.includes('/v1/finance/search')));
  assert.ok(calls.some((href) => href.includes('/v10/finance/quoteSummary/MMM')));
});

test('Yahoo session uses cookie and crumb and can be renewed', async () => {
  let warmCalls = 0;
  let crumbCalls = 0;
  const fetchImpl = async (url, options = {}) => {
    const href = String(url);
    if (href === 'https://fc.yahoo.com') {
      warmCalls += 1;
      return jsonResponse('', 404, {
        headers: {
          getSetCookie: () => [`A3=session-${warmCalls}; Path=/; Secure`],
          get: () => null
        }
      });
    }
    if (href.includes('/v1/test/getcrumb')) {
      crumbCalls += 1;
      assert.match(String(options.headers?.Cookie ?? ''), /A3=session-/);
      return jsonResponse(`crumb-${crumbCalls}`, 200);
    }
    return jsonResponse({}, 404);
  };

  const first = await defaultGetYahooSession(fetchImpl, { forceRefresh: false });
  const reused = await defaultGetYahooSession(fetchImpl, { forceRefresh: false });
  const renewed = await defaultGetYahooSession(fetchImpl, { forceRefresh: true });

  assert.equal(first.crumb, 'crumb-1');
  assert.equal(reused.crumb, 'crumb-1');
  assert.equal(renewed.crumb, 'crumb-2');
  assert.equal(warmCalls, 2);
  assert.equal(crumbCalls, 2);
});

test('Yahoo symbol resolution rejects non-equity search results for stocks', async () => {
  const fetchImpl = async () => jsonResponse({
    quotes: [
      { symbol: 'MMM240', quoteType: 'OPTION' },
      { symbol: 'MMM=F', quoteType: 'FUTURE' }
    ]
  });
  const symbol = await defaultResolveYahooSymbol({
    isin: 'US88579Y1010',
    ticker: 'US88579Y1010',
    type: 'AKTIE',
    providerSymbolUnresolved: true
  }, fetchImpl);
  assert.equal(symbol, '');
});
