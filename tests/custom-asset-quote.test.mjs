import test from 'node:test';
import assert from 'node:assert/strict';
import { normalizeCustomAssetInput, customAssetPayload } from '../backend/src/lib/customAsset.mjs';

test('normalizes a user supplied stock and produces a dashboard-compatible item', () => {
  const input = normalizeCustomAssetInput({
    ticker: ' msft ', name: 'Microsoft', isin: 'us5949181045', type: 'Aktie'
  });
  assert.equal(input.ticker, 'MSFT');
  assert.equal(input.isin, 'US5949181045');
  assert.equal(input.type, 'Aktie');
  assert.match(input.id, /^custom-/);

  const payload = customAssetPayload(input, {
    price: 500,
    currency: 'USD',
    percentChange: 1.5,
    marketOpen: true,
    source: 'Twelve Data',
    delayed: false,
    error: null
  }, {
    rate: 0.86,
    source: 'ECB',
    delayed: true,
    asOf: '2026-09-01'
  });

  assert.equal(payload.price, 500);
  assert.equal(payload.priceEur, 430);
  assert.equal(payload.currency, 'USD');
  assert.equal(payload.fxSource, 'ECB');
  assert.equal(payload.percentChange, 1.5);
  assert.equal(payload.status, 'EIGEN');
});

test('rejects missing ticker', () => {
  assert.throws(() => normalizeCustomAssetInput({ name: 'Ohne Ticker' }), /Ticker/);
});
