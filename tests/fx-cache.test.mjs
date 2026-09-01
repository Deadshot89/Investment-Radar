import test from 'node:test';
import assert from 'node:assert/strict';
import { cacheFromFxRates, mergeFxRatesWithCache } from '../backend/src/lib/fxCache.mjs';

test('FX cache stores successful provider rates', () => {
  const rates = new Map([
    ['USD', { rate: 0.854, source: 'ECB', delayed: true, asOf: '2026-09-01' }],
    ['GBP', { rate: null, source: 'ECB', delayed: true, asOf: '2026-09-01' }]
  ]);
  const cache = cacheFromFxRates(rates, '2026-09-01T10:00:00.000Z');
  assert.deepEqual(Object.keys(cache), ['USD']);
  assert.equal(cache.USD.rate, 0.854);
  assert.equal(cache.USD.source, 'ECB');
});

test('FX cache is used when providers fail', () => {
  const current = new Map([
    ['USD', { rate: null, source: '', delayed: true, asOf: null, error: 'provider failed' }]
  ]);
  const cache = {
    USD: { rate: 0.854, source: 'ECB', delayed: true, asOf: '2026-08-31', cachedAt: '2026-08-31T14:00:00.000Z' }
  };
  const merged = mergeFxRatesWithCache(current, cache);
  const usd = merged.get('USD');
  assert.equal(usd.rate, 0.854);
  assert.equal(usd.source, 'Cache · ECB');
  assert.equal(usd.delayed, true);
  assert.equal(usd.error, null);
});
