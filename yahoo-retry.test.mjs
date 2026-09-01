import test from 'node:test';
import assert from 'node:assert/strict';
import { loadQuotes } from '../backend/src/lib/market.mjs';

function jsonResponse(body, status = 200) {
  return new Response(JSON.stringify(body), { status, headers: { 'content-type': 'application/json' } });
}

test('Yahoo ETF fallback retries query2 after query1 is rate limited', async () => {
  const previousFetch = global.fetch;
  const previousKey = process.env.TWELVE_DATA_API_KEY;
  delete process.env.TWELVE_DATA_API_KEY;
  const calls = [];
  global.fetch = async (input) => {
    const url = String(input);
    calls.push(url);
    if (url.includes('query1.finance.yahoo.com')) return jsonResponse({ chart: { result: null, error: { description: 'Too Many Requests' } } }, 429);
    if (url.includes('query2.finance.yahoo.com') && url.includes('SPYI.DE')) {
      return jsonResponse({ chart: { result: [{ meta: {
        symbol: 'SPYI.DE', currency: 'EUR', regularMarketPrice: 11.52,
        chartPreviousClose: 11.40, regularMarketTime: 1788252000, marketState: 'CLOSED'
      }}], error: null } });
    }
    throw new Error(`Unexpected URL: ${url}`);
  };
  try {
    const quotes = await loadQuotes([{ id: 'spyi', ticker: 'SPYI', marketSymbol: 'SPYI:XETR', yahooSymbol: 'SPYI.DE' }]);
    assert.equal(quotes.get('spyi').price, 11.52);
    assert.equal(quotes.get('spyi').source, 'Yahoo Finance');
    assert.ok(calls.some((u) => u.includes('query1.finance.yahoo.com')));
    assert.ok(calls.some((u) => u.includes('query2.finance.yahoo.com')));
  } finally {
    global.fetch = previousFetch;
    if (previousKey == null) delete process.env.TWELVE_DATA_API_KEY;
    else process.env.TWELVE_DATA_API_KEY = previousKey;
  }
});
