import test from 'node:test';
import assert from 'node:assert/strict';
import { evaluateDataQuality } from '../src/lib/dataQuality.mjs';

const fullStockFundamentals = {
  metrics: {
    pe: 20,
    priceToSales: 4,
    evToEbitda: 15,
    freeCashFlowYield: 0.03,
    revenueGrowth: 0.1,
    epsGrowth: 0.12,
    operatingMargin: 0.2,
    netMargin: 0.18,
    roe: 0.3,
    roic: 0.2,
    debtToEquity: 1
  }
};

test('stock with quote, full history and fundamentals gets high coverage', () => {
  const q = evaluateDataQuality({
    item: { type: 'STOCK', risk: 2 },
    quote: { price: 100, currency: 'EUR', percentChange: 1 },
    history: { m1: 1, m3: 2, m6: 4, m12: 10, pointsCount: 250, coveragePct: 100 },
    fundamentals: fullStockFundamentals
  });

  assert.ok(q.overallCoverage >= 85);
  assert.equal(q.qualityTier, 'HOCH');
  assert.deepEqual(q.missingBlocks, []);
  assert.deepEqual(q.criticalConflicts, []);
});

test('missing quote forces incomplete quality tier', () => {
  const q = evaluateDataQuality({
    item: { type: 'STOCK', risk: 2 },
    quote: null,
    history: { m1: 1, m3: 2, m6: 4, m12: 10, pointsCount: 250, coveragePct: 100 },
    fundamentals: fullStockFundamentals
  });

  assert.equal(q.quoteCoverage, 0);
  assert.equal(q.qualityTier, 'UNVOLLSTÄNDIG');
  assert.ok(q.missingBlocks.includes('quote'));
});

test('insufficient history forces incomplete quality tier', () => {
  const q = evaluateDataQuality({
    item: { type: 'STOCK', risk: 2 },
    quote: { price: 100, currency: 'EUR', percentChange: 1 },
    history: { m1: 1, m3: null, m6: null, m12: null, pointsCount: 20, coveragePct: 8 },
    fundamentals: fullStockFundamentals
  });

  assert.equal(q.qualityTier, 'UNVOLLSTÄNDIG');
  assert.ok(q.missingBlocks.includes('history'));
});

test('stock fundamental conflicts are surfaced as critical conflicts', () => {
  const q = evaluateDataQuality({
    item: { type: 'STOCK', risk: 2 },
    quote: { price: 100, currency: 'EUR', percentChange: 1 },
    history: { m1: 1, m3: 2, m6: 4, m12: 10, pointsCount: 250, coveragePct: 100 },
    fundamentals: { ...fullStockFundamentals, conflicts: ['pe'] }
  });

  assert.ok(q.criticalConflicts.includes('pe'));
  assert.equal(q.qualityTier, 'UNVOLLSTÄNDIG');
});

test('ETF quality does not require stock fundamentals', () => {
  const q = evaluateDataQuality({
    item: { type: 'ETF', risk: 2, region: 'WORLD', sector: 'Diversified' },
    quote: { price: 120, currency: 'EUR', percentChange: 0.3 },
    history: { m1: 1, m3: 2, m6: 5, m12: 9, pointsCount: 250, coveragePct: 100 },
    fundamentals: null
  });

  assert.ok(q.overallCoverage >= 85);
  assert.equal(q.qualityTier, 'HOCH');
  assert.ok(!q.missingBlocks.includes('fundamentals'));
});
