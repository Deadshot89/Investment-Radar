import test from 'node:test';
import assert from 'node:assert/strict';
import { normalizeAnalysisInput } from '../src/lib/analysisDataNormalizer.mjs';

test('normalizer keeps missing values null instead of zero', () => {
  const out = normalizeAnalysisInput({
    item: { id: 'aapl', type: 'STOCK' },
    quote: { price: 200, currency: 'USD' },
    history: { m12: null },
    fundamentals: { metrics: { pe: null, roe: 0.25 } }
  });

  assert.equal(out.history.m12, null);
  assert.equal(out.fundamentals.pe, null);
  assert.equal(out.fundamentals.roe, 0.25);
});

test('normalizer converts invalid numeric provider values to null', () => {
  const out = normalizeAnalysisInput({
    item: { id: 'example', type: 'STOCK' },
    quote: { price: 'not-a-number', currency: 'EUR', percentChange: '' },
    history: { m1: undefined, m3: '4.5' },
    fundamentals: { metrics: { pe: 'n/a', debtToEquity: '1.2' } }
  });

  assert.equal(out.quote.price, null);
  assert.equal(out.quote.percentChange, null);
  assert.equal(out.history.m1, null);
  assert.equal(out.history.m3, 4.5);
  assert.equal(out.fundamentals.pe, null);
  assert.equal(out.fundamentals.debtToEquity, 1.2);
});

test('normalizer preserves provider metadata without fabricating metrics', () => {
  const out = normalizeAnalysisInput({
    item: { id: 'aapl', type: 'STOCK' },
    quote: null,
    history: null,
    fundamentals: {
      metrics: {},
      source: 'Yahoo Finance',
      asOf: '2026-09-10T10:00:00.000Z',
      stale: false,
      conflicts: ['pe']
    }
  });

  assert.equal(out.quote, null);
  assert.equal(out.history, null);
  assert.equal(out.fundamentals.source, 'Yahoo Finance');
  assert.equal(out.fundamentals.asOf, '2026-09-10T10:00:00.000Z');
  assert.deepEqual(out.fundamentals.conflicts, ['pe']);
  assert.equal(out.fundamentals.pe, undefined);
});
