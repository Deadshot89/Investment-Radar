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

  assert.equal(result.items.length, 1);
  const [out] = result.items;
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

test('radar page automatically repairs missing history and fundamentals when slow-data cache is empty', async () => {
  const item = {
    id: 'abbv', type: 'AKTIE', name: 'AbbVie', ticker: 'ABBV', isin: 'US00287Y1091',
    region: 'NORTH_AMERICA', country: 'US', sector: 'Healthcare', risk: 2,
    universeActive: true, portfolioOnly: false, tradeRepublicEligible: true
  };
  const historyRefreshCalls = [];
  const fundamentalRefreshCalls = [];

  const result = await queryRadar({ page: 1, pageSize: 1 }, {
    loadUniverse: async () => [item],
    loadQuotes: async () => new Map([['abbv', { price: 220, currency: 'USD', percentChange: 1.8, source: 'Twelve Data' }]]),
    loadHistory: async (_items, options = {}) => {
      historyRefreshCalls.push(Boolean(options.refresh));
      if (!options.refresh) return new Map([['abbv', {
        score: 50, coveragePct: 0, source: '', stale: false,
        error: 'Historie wird im Hintergrund geladen'
      }]]);
      return new Map([['abbv', {
        m1: 2, m3: 5, m6: 8, m12: 13, score: 72, coveragePct: 100, source: 'Yahoo Finance'
      }]]);
    },
    loadFundamentals: async (_items, options = {}) => {
      fundamentalRefreshCalls.push(Boolean(options.refresh));
      if (!options.refresh) return new Map([['abbv', {
        coveragePct: 0, source: '', stale: false, metrics: {},
        error: 'Fundamentaldaten werden im Hintergrund geladen'
      }]]);
      return new Map([['abbv', {
        coveragePct: 100,
        source: 'Twelve Data + SEC Companyfacts',
        metrics: {
          pe: 18, priceToSales: 5, evToEbitda: 14, freeCashFlowYield: 0.05,
          revenueGrowth: 0.08, epsGrowth: 0.10, operatingMargin: 0.31, netMargin: 0.24,
          roe: 0.55, roic: 0.16, debtToEquity: 3.5, marketCap: 390000000000
        }
      }]]);
    },
    loadEurRateDetails: async () => new Map([['USD', { rate: 0.9 }]])
  });

  assert.deepEqual(historyRefreshCalls, [false, true]);
  assert.deepEqual(fundamentalRefreshCalls, [false, true]);
  assert.equal(result.items[0].diagnostics.historySource, 'Yahoo Finance');
  assert.equal(result.items[0].diagnostics.fundamentalSource, 'Twelve Data + SEC Companyfacts');
  assert.ok(!result.items[0].dataQuality.missingBlocks.includes('history'));
  assert.ok(!result.items[0].dataQuality.missingBlocks.includes('fundamentals'));
});
