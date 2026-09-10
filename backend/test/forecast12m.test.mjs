import test from 'node:test';
import assert from 'node:assert/strict';
import { forecast12m } from '../src/lib/forecast12m.mjs';

const strong = {
  scoreQuality: 85,
  scoreValuation: 76,
  scoreGrowth: 82,
  scoreRisk: 86,
  risk: 2,
  momentum: { m3: 8, m6: 15, m12: 24 },
  fundamentals: { revenueGrowth: 0.12, epsGrowth: 0.16, freeCashFlowYield: 0.04, debtToEquity: 0.6 },
  dataQuality: { forecastInputCoverage: 95, missingBlocks: [], criticalConflicts: [] },
  coverage: 92
};

test('high coverage forecast exposes scenarios confidence drivers and risks', () => {
  const out = forecast12m(strong);
  assert.equal(out.quality, 'HOCH');
  assert.ok(out.confidencePct >= 85);
  assert.equal(typeof out.expectedChangePct, 'number');
  assert.equal(typeof out.bearChangePct, 'number');
  assert.equal(typeof out.bullChangePct, 'number');
  assert.ok(out.bearChangePct < out.expectedChangePct);
  assert.ok(out.bullChangePct > out.expectedChangePct);
  assert.ok(out.reasons.length >= 2);
  assert.ok(out.risks.length >= 1);
  assert.ok(out.usedInputs.includes('momentum.m12'));
  assert.ok(out.asOf);
});

test('medium and low forecast quality follow input coverage', () => {
  assert.equal(forecast12m({ ...strong, dataQuality: { ...strong.dataQuality, forecastInputCoverage: 78 } }).quality, 'MITTEL');
  assert.equal(forecast12m({ ...strong, dataQuality: { ...strong.dataQuality, forecastInputCoverage: 62 } }).quality, 'NIEDRIG');
});

test('critical missing history makes forecast non-actionable and hides numeric scenarios', () => {
  const out = forecast12m({
    ...strong,
    dataQuality: { forecastInputCoverage: 80, missingBlocks: ['history'], criticalConflicts: [] }
  });
  assert.equal(out.quality, 'NICHT_BELASTBAR');
  assert.equal(out.expectedChangePct, null);
  assert.equal(out.bearChangePct, null);
  assert.equal(out.bullChangePct, null);
  assert.equal(out.direction, 'UNKNOWN');
});

test('stock fundamental gap or provider conflict makes forecast non-actionable', () => {
  const missing = forecast12m({ ...strong, type: 'STOCK', dataQuality: { forecastInputCoverage: 90, missingBlocks: ['fundamentals'], criticalConflicts: [] } });
  assert.equal(missing.quality, 'NICHT_BELASTBAR');
  const conflict = forecast12m({ ...strong, dataQuality: { forecastInputCoverage: 90, missingBlocks: [], criticalConflicts: ['pe'] } });
  assert.equal(conflict.quality, 'NICHT_BELASTBAR');
});

test('ETF does not become non-actionable only because stock fundamentals are absent', () => {
  const out = forecast12m({ ...strong, type: 'ETF', fundamentals: {}, dataQuality: { forecastInputCoverage: 85, missingBlocks: ['fundamentals'], criticalConflicts: [] } });
  assert.notEqual(out.quality, 'NICHT_BELASTBAR');
  assert.equal(typeof out.expectedChangePct, 'number');
});
