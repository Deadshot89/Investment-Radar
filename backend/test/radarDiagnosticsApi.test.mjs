import test from 'node:test';
import assert from 'node:assert/strict';
import { queryRadar } from '../src/lib/radar.mjs';

test('radar item exposes coverageBreakdown missingData providerStatus and analysisWarnings', async () => {
  const item = {
    id: 'apple', type: 'AKTIE', name: 'Apple', ticker: 'AAPL', isin: 'US0378331005',
    region: 'NORTH_AMERICA', country: 'US', sector: 'Technology', risk: 2,
    universeActive: true, portfolioOnly: false, tradeRepublicEligible: true
  };
  const result = await queryRadar({ page: 1, pageSize: 1 }, {
    loadUniverse: async () => [item],
    loadQuotes: async () => new Map([['apple', { price: 200, currency: 'USD', percentChange: 1, source: 'Twelve Data' }]]),
    loadHistory: async () => new Map([['apple', { m1: 2, m3: 4, m6: 8, m12: 12, score: 70, coveragePct: 80, source: 'Yahoo Finance' }]]),
    loadFundamentals: async () => new Map([['apple', {
      coveragePct: 55,
      source: 'SEC Companyfacts',
      error: 'Teilweise Daten',
      metrics: { pe: 25, revenueGrowth: 0.08, netMargin: 0.20 }
    }]]),
    loadEurRateDetails: async () => new Map([['USD', { rate: 0.9 }]])
  });

  const out = result.items.single();
  assert.ok(out.coverageBreakdown);
  assert.equal(out.coverageBreakdown.quote, out.dataQuality.quoteCoverage);
  assert.equal(out.coverageBreakdown.history, out.dataQuality.historyCoverage);
  assert.ok(Array.isArray(out.missingData));
  assert.ok(out.providerStatus);
  assert.equal(out.providerStatus.quote.source, 'Twelve Data');
  assert.equal(out.providerStatus.history.source, 'Yahoo Finance');
  assert.ok(Array.isArray(out.analysisWarnings));
  assert.ok(out.analysisWarnings.some((warning) => warning.includes('Fundamentaldaten')));
});
