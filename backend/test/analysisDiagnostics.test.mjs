import test from 'node:test';
import assert from 'node:assert/strict';
import { buildAnalysisDiagnostics } from '../src/lib/analysisDiagnostics.mjs';

test('diagnostics expose provider status coverage missing data and warnings', () => {
  const result = buildAnalysisDiagnostics({
    item: { id: 'apple', type: 'STOCK' },
    quote: { source: 'Twelve Data', asOf: '2026-09-10T20:00:00Z', delayed: false },
    history: { source: 'Yahoo Finance', asOf: '2026-09-10T19:00:00Z', stale: false, coveragePct: 80 },
    fundamentals: {
      source: 'Twelve Data + SEC Companyfacts',
      asOf: '2026-09-09T00:00:00Z',
      stale: true,
      coveragePct: 55,
      error: 'Cache wird aktualisiert',
      conflicts: ['roe']
    },
    dataQuality: {
      quoteCoverage: 100,
      historyCoverage: 80,
      fundamentalCoverage: 55,
      forecastInputCoverage: 75,
      overallCoverage: 75,
      missingBlocks: [],
      criticalConflicts: ['roe']
    }
  });

  assert.deepEqual(result.coverageBreakdown, {
    quote: 100,
    history: 80,
    fundamentals: 55,
    forecastInputs: 75,
    overall: 75
  });
  assert.equal(result.providerStatus.quote.status, 'OK');
  assert.equal(result.providerStatus.history.status, 'OK');
  assert.equal(result.providerStatus.fundamentals.status, 'STALE');
  assert.equal(result.providerStatus.fundamentals.source, 'Twelve Data + SEC Companyfacts');
  assert.ok(result.analysisWarnings.some((value) => value.includes('Providerkonflikt')));
  assert.ok(result.analysisWarnings.some((value) => value.includes('Fundamentaldaten')));
});

test('missing provider blocks are explicit and never disguised as zero-value data', () => {
  const result = buildAnalysisDiagnostics({
    item: { id: 'nel', type: 'STOCK' },
    quote: { source: 'Twelve Data', asOf: '2026-09-10T20:00:00Z' },
    history: null,
    fundamentals: { source: '', error: 'Keine Fundamentaldaten verfügbar', coveragePct: 0 },
    dataQuality: {
      quoteCoverage: 100,
      historyCoverage: 0,
      fundamentalCoverage: 0,
      forecastInputCoverage: 20,
      overallCoverage: 20,
      missingBlocks: ['history', 'fundamentals', 'forecast'],
      criticalConflicts: []
    }
  });

  assert.equal(result.providerStatus.history.status, 'MISSING');
  assert.equal(result.providerStatus.fundamentals.status, 'MISSING');
  assert.deepEqual(result.missingData, ['Historie', 'Fundamentaldaten', 'Prognose-Eingaben']);
  assert.ok(result.analysisWarnings.some((value) => value.includes('Keine Fundamentaldaten')));
});

test('ETF diagnostics do not claim missing stock fundamentals', () => {
  const result = buildAnalysisDiagnostics({
    item: { id: 'etf', type: 'ETF' },
    quote: { source: 'Twelve Data' },
    history: { source: 'Yahoo Finance', coveragePct: 90 },
    fundamentals: null,
    dataQuality: {
      quoteCoverage: 100,
      historyCoverage: 90,
      fundamentalCoverage: 100,
      forecastInputCoverage: 80,
      overallCoverage: 90,
      missingBlocks: [],
      criticalConflicts: []
    }
  });

  assert.ok(!result.missingData.includes('Fundamentaldaten'));
  assert.equal(result.providerStatus.fundamentals.status, 'NOT_REQUIRED');
});
