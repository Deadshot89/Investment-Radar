import test from 'node:test';
import assert from 'node:assert/strict';
import { normalizeEcbDailyXml } from '../backend/src/lib/marketSupport.mjs';
import { loadEurRateDetails } from '../backend/src/lib/market.mjs';

test('ECB daily XML is converted from EUR base into currency-to-EUR rates', () => {
  const xml = `<?xml version="1.0"?><Envelope><Cube><Cube time="2026-09-01"><Cube currency="USD" rate="1.1712"/><Cube currency="GBP" rate="0.8654"/></Cube></Cube></Envelope>`;
  const result = normalizeEcbDailyXml(xml);
  assert.equal(result.date, '2026-09-01');
  assert.ok(Math.abs(result.rates.get('USD') - (1 / 1.1712)) < 1e-12);
  assert.ok(Math.abs(result.rates.get('GBP') - (1 / 0.8654)) < 1e-12);
});

test('FX falls back to ECB when Twelve Data exchange rate is unavailable', async () => {
  const previousFetch = global.fetch;
  const previousKey = process.env.TWELVE_DATA_API_KEY;
  process.env.TWELVE_DATA_API_KEY = 'test-key';
  const calls = [];
  global.fetch = async (input) => {
    const url = String(input);
    calls.push(url);
    if (url.includes('api.twelvedata.com/exchange_rate')) {
      return new Response(JSON.stringify({ status: 'error', message: 'credits exhausted' }), { status: 200 });
    }
    if (url.includes('ecb.europa.eu') && url.includes('eurofxref-daily.xml')) {
      return new Response(`<?xml version="1.0"?><Envelope><Cube><Cube time="2026-09-01"><Cube currency="USD" rate="1.1712"/></Cube></Cube></Envelope>`, { status: 200, headers: { 'content-type': 'application/xml' } });
    }
    throw new Error(`Unexpected URL: ${url}`);
  };

  try {
    const quotes = new Map([['msft', { price: 507.29, currency: 'USD' }]]);
    const details = await loadEurRateDetails(quotes);
    const usd = details.get('USD');
    assert.ok(Math.abs(usd.rate - (1 / 1.1712)) < 1e-12);
    assert.equal(usd.source, 'ECB');
    assert.equal(usd.delayed, true);
    assert.equal(usd.asOf, '2026-09-01');
    assert.ok(calls.some((url) => url.includes('api.twelvedata.com/exchange_rate')));
    assert.ok(calls.some((url) => url.includes('eurofxref-daily.xml')));
  } finally {
    global.fetch = previousFetch;
    if (previousKey == null) delete process.env.TWELVE_DATA_API_KEY;
    else process.env.TWELVE_DATA_API_KEY = previousKey;
  }
});

test('ECB FX still works when Twelve Data key is not configured', async () => {
  const previousFetch = global.fetch;
  const previousKey = process.env.TWELVE_DATA_API_KEY;
  delete process.env.TWELVE_DATA_API_KEY;
  global.fetch = async (input) => {
    const url = String(input);
    assert.ok(url.includes('eurofxref-daily.xml'));
    return new Response(`<?xml version="1.0"?><Envelope><Cube><Cube time="2026-09-01"><Cube currency="USD" rate="1.20"/></Cube></Cube></Envelope>`, { status: 200 });
  };

  try {
    const quotes = new Map([['msft', { price: 500, currency: 'USD' }]]);
    const details = await loadEurRateDetails(quotes);
    assert.equal(details.get('USD').rate, 1 / 1.20);
    assert.equal(details.get('USD').source, 'ECB');
  } finally {
    global.fetch = previousFetch;
    if (previousKey == null) delete process.env.TWELVE_DATA_API_KEY;
    else process.env.TWELVE_DATA_API_KEY = previousKey;
  }
});
