import test from 'node:test';
import assert from 'node:assert/strict';
import { applyPurchaseQualityGate } from '../src/lib/radar.mjs';

const base = {
  item: { tradeRepublicEligible: true, universeActive: true, portfolioOnly: false, type: 'STOCK' },
  analysis: { recommendation: 'BUY', scoreTotal: 86 },
  dataQuality: {
    quoteCoverage: 100,
    historyCoverage: 100,
    fundamentalCoverage: 100,
    forecastInputCoverage: 100,
    overallCoverage: 100,
    missingBlocks: [],
    criticalConflicts: []
  }
};

test('complete verified BUY remains purchase eligible', () => {
  const out = applyPurchaseQualityGate(base);
  assert.equal(out.recommendation, 'BUY');
  assert.equal(out.purchaseEligible, true);
});

for (const [name, qualityPatch] of [
  ['overall coverage below 70', { overallCoverage: 69 }],
  ['forecast input coverage below 70', { forecastInputCoverage: 69 }],
  ['missing quote', { missingBlocks: ['quote'] }],
  ['critical history', { missingBlocks: ['history'] }],
  ['critical stock fundamentals', { missingBlocks: ['fundamentals'] }],
  ['provider conflict', { criticalConflicts: ['pe'] }]
]) {
  test(`${name} downgrades BUY`, () => {
    const out = applyPurchaseQualityGate({
      ...base,
      dataQuality: { ...base.dataQuality, ...qualityPatch }
    });
    assert.equal(out.purchaseEligible, false);
    assert.notEqual(out.recommendation, 'BUY');
  });
}

test('ETF does not require stock fundamentals block', () => {
  const out = applyPurchaseQualityGate({
    ...base,
    item: { ...base.item, type: 'ETF' },
    dataQuality: { ...base.dataQuality, fundamentalCoverage: 0, missingBlocks: [] }
  });
  assert.equal(out.recommendation, 'BUY');
  assert.equal(out.purchaseEligible, true);
});
