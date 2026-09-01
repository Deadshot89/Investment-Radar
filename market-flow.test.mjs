import test from 'node:test';
import assert from 'node:assert/strict';
import { loadQuotes, loadEurRates, priceInEur } from '../backend/src/lib/market.mjs';

function jsonResponse(body, status = 200) {
  return new Response(JSON.stringify(body), { status, headers: { 'content-type': 'application/json' } });
}

test('market flow falls back for Xetra ETF and converts USD quote to EUR', async () => {
  const previousFetch = global.fetch;
  const previousKey = process.env.TWELVE_DATA_API_KEY;
  process.env.TWELVE_DATA_API_KEY = 'test-key';
  const calls = [];
  global.fetch = async (input) => {
    const url = String(input);
    calls.push(url);
    if (url.includes('api.twelvedata.com/quote')) {
      return jsonResponse({
        'MSFT:NASDAQ': { symbol: 'MSFT', close: '507.29', currency: 'USD', percent_change: '-1.22', is_market_open: false },
        'SPYI:XETR': { status: 'error', message: 'XETR not available on current plan' }
      });
    }
    if (url.includes('query1.finance.yahoo.com') && url.includes('SPYI.DE')) {
      return jsonResponse({ chart: { result: [{ meta: {
        symbol: 'SPYI.DE', currency: 'EUR', regularMarketPrice: 11.52,
        chartPreviousClose: 11.40, regularMarketTime: 1788252000, marketState: 'CLOSED'
      }}], error: null } });
    }
    if (url.includes('api.twelvedata.com/exchange_rate')) {
      return jsonResponse({ symbol: 'USD/EUR', rate: 0.86 });
    }
    throw new Error(`Unexpected URL: ${url}`);
  };

  try {
    const items = [
      { id: 'msft', ticker: 'MSFT', marketSymbol: 'MSFT:NASDAQ' },
      { id: 'spyi', ticker: 'SPYI', marketSymbol: 'SPYI:XETR', yahooSymbol: 'SPYI.DE' }
    ];
    const quotes = await loadQuotes(items);
    assert.equal(quotes.get('msft').source, 'Twelve Data');
    assert.equal(quotes.get('msft').currency, 'USD');
    assert.equal(quotes.get('spyi').source, 'Yahoo Finance');
    assert.equal(quotes.get('spyi').currency, 'EUR');
    assert.equal(quotes.get('spyi').price, 11.52);

    const rates = await loadEurRates(quotes);
    assert.equal(rates.get('USD'), 0.86);
    assert.equal(priceInEur(quotes.get('msft'), rates), 436.2694);
    assert.equal(priceInEur(quotes.get('spyi'), rates), 11.52);
    assert.ok(calls.some((url) => url.includes('SPYI.DE')));
    assert.ok(calls.some((url) => url.includes('USD%2FEUR') || url.includes('USD/EUR')));
  } finally {
    global.fetch = previousFetch;
    if (previousKey == null) delete process.env.TWELVE_DATA_API_KEY;
    else process.env.TWELVE_DATA_API_KEY = previousKey;
  }
});
