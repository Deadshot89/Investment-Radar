import test from 'node:test';
import assert from 'node:assert/strict';
import { mergeQuotesWithCache, cacheFromQuotes } from '../backend/src/lib/quoteCache.mjs';

test('cacheFromQuotes stores only successful quotes with timestamps', () => {
  const quotes = new Map([
    ['spyi', { symbol: 'SPYI.DE', price: 11.52, currency: 'EUR', percentChange: 0.7, marketOpen: false, source: 'Yahoo Finance', delayed: true, error: null }],
    ['is3s', { symbol: 'IS3S.DE', price: null, currency: '', percentChange: null, marketOpen: null, source: 'Yahoo Finance', delayed: true, error: 'HTTP 429' }]
  ]);
  const cached = cacheFromQuotes(quotes, '2026-09-01T08:00:00.000Z');
  assert.deepEqual(Object.keys(cached), ['spyi']);
  assert.equal(cached.spyi.price, 11.52);
  assert.equal(cached.spyi.cachedAt, '2026-09-01T08:00:00.000Z');
});

test('mergeQuotesWithCache uses last successful quote when provider has no price', () => {
  const live = new Map([
    ['spyi', { symbol: 'SPYI:XETR', price: null, currency: '', percentChange: null, marketOpen: null, source: 'Yahoo Finance', delayed: true, error: 'Yahoo HTTP 429' }],
    ['msft', { symbol: 'MSFT', price: 507.29, currency: 'USD', percentChange: -1.2, marketOpen: true, source: 'Twelve Data', delayed: false, error: null }]
  ]);
  const cache = {
    spyi: { symbol: 'SPYI.DE', price: 11.52, currency: 'EUR', percentChange: 1.05, marketOpen: false, source: 'Yahoo Finance', delayed: true, error: null, cachedAt: '2026-09-01T07:55:00.000Z' }
  };
  const merged = mergeQuotesWithCache(live, cache);
  assert.equal(merged.get('spyi').price, 11.52);
  assert.equal(merged.get('spyi').currency, 'EUR');
  assert.equal(merged.get('spyi').delayed, true);
  assert.match(merged.get('spyi').source, /Cache/);
  assert.match(merged.get('spyi').source, /Yahoo Finance/);
  assert.equal(merged.get('spyi').error, null);
  assert.equal(merged.get('msft').price, 507.29);
  assert.equal(merged.get('msft').source, 'Twelve Data');
});
