import test from 'node:test';
import assert from 'node:assert/strict';
import { loadFundamentalFallback } from '../src/lib/fundamentalFallbackProvider.mjs';

function jsonResponse(body, status = 200) {
  return { ok: status >= 200 && status < 300, status, json: async () => body };
}

test('Yahoo quoteSummary maps explicit canonical fundamentals', async () => {
  const fetchImpl = async (url) => {
    assert.match(String(url), /query1\.finance\.yahoo\.com\/v10\/finance\/quoteSummary\/AAPL/);
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

  const out = await loadFundamentalFallback({ yahooSymbol: 'AAPL', type: 'STOCK' }, { fetchImpl });
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
        Assets: { units: { USD: [
          { fy: 2025, fp: 'FY', form: '10-K', filed: '2025-11-01', val: 500 }
        ] } },
        Liabilities: { units: { USD: [
          { fy: 2025, fp: 'FY', form: '10-K', filed: '2025-11-01', val: 300 }
        ] } }
      } }
    });
  };

  const out = await loadFundamentalFallback({ yahooSymbol: 'AAPL', cik: '320193', type: 'STOCK' }, { fetchImpl });
  assert.ok(calls.some((url) => url.includes('/CIK0000320193.json')));
  assert.equal(out.raw.revenueGrowth, (400 - 360) / 360);
  assert.equal(out.raw.netMargin, 0.25);
  assert.equal(out.raw.roe, 0.5);
  assert.equal(out.raw.debtToEquity, 1.5);
  assert.match(out.source, /SEC Companyfacts/);
});

test('SEC is skipped when CIK is absent instead of guessing identity', async () => {
  let calls = 0;
  const fetchImpl = async () => { calls += 1; return jsonResponse({}, 404); };
  const out = await loadFundamentalFallback({ ticker: 'UNKNOWN', type: 'STOCK' }, { fetchImpl });
  assert.equal(calls, 1);
  assert.equal(out.raw, null);
  assert.match(out.error, /Yahoo|Fundamental/i);
});
