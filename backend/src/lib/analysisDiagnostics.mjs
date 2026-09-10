export function buildAnalysisDiagnostics({ item, quote, history, fundamentals, dataQuality } = {}) {
  const isEtf = upper(item?.type) === 'ETF';
  const quality = dataQuality ?? {};
  const missingBlocks = new Set(Array.isArray(quality.missingBlocks) ? quality.missingBlocks.map(lower) : []);
  const conflicts = Array.isArray(quality.criticalConflicts) ? quality.criticalConflicts.filter(Boolean) : [];

  const providerStatus = {
    quote: blockStatus(quote, {
      required: true,
      missing: missingBlocks.has('quote') || Number(quality.quoteCoverage ?? 0) <= 0
    }),
    history: blockStatus(history, {
      required: true,
      missing: missingBlocks.has('history') || Number(quality.historyCoverage ?? 0) <= 0
    }),
    fundamentals: isEtf
      ? { status: 'NOT_REQUIRED', source: '', asOf: null, stale: false, error: null }
      : blockStatus(fundamentals, {
          required: true,
          missing: missingBlocks.has('fundamentals') || Number(quality.fundamentalCoverage ?? 0) <= 0
        })
  };

  const coverageBreakdown = {
    quote: finiteCoverage(quality.quoteCoverage),
    history: finiteCoverage(quality.historyCoverage),
    fundamentals: finiteCoverage(quality.fundamentalCoverage),
    forecastInputs: finiteCoverage(quality.forecastInputCoverage),
    overall: finiteCoverage(quality.overallCoverage)
  };

  const missingData = [];
  if (missingBlocks.has('quote') || providerStatus.quote.status === 'MISSING') missingData.push('Kursdaten');
  if (missingBlocks.has('history') || providerStatus.history.status === 'MISSING') missingData.push('Historie');
  if (!isEtf && (missingBlocks.has('fundamentals') || providerStatus.fundamentals.status === 'MISSING')) missingData.push('Fundamentaldaten');
  if (missingBlocks.has('forecast') || Number(quality.forecastInputCoverage ?? 0) < 50) missingData.push('Prognose-Eingaben');

  const analysisWarnings = [];
  if (providerStatus.quote.stale) analysisWarnings.push('Kursdaten sind veraltet und werden aktualisiert.');
  if (providerStatus.history.stale) analysisWarnings.push('Historiedaten sind veraltet und werden aktualisiert.');
  if (!isEtf && providerStatus.fundamentals.stale) analysisWarnings.push('Fundamentaldaten sind veraltet und werden aktualisiert.');
  addProviderError(analysisWarnings, 'Kursdaten', providerStatus.quote.error);
  addProviderError(analysisWarnings, 'Historie', providerStatus.history.error);
  if (!isEtf) addProviderError(analysisWarnings, 'Fundamentaldaten', providerStatus.fundamentals.error);
  if (conflicts.length) analysisWarnings.push(`Providerkonflikt bei: ${conflicts.join(', ')}.`);
  for (const label of missingData) {
    const warning = `${label} fehlen oder sind nicht ausreichend vollständig.`;
    if (!analysisWarnings.includes(warning)) analysisWarnings.push(warning);
  }

  return {
    coverageBreakdown,
    missingData: [...new Set(missingData)],
    providerStatus,
    analysisWarnings: [...new Set(analysisWarnings)]
  };
}

function blockStatus(block, { required, missing }) {
  if (!required) return { status: 'NOT_REQUIRED', source: '', asOf: null, stale: false, error: null };
  const stale = Boolean(block?.stale);
  const error = textOrNull(block?.error);
  const source = String(block?.source ?? '').trim();
  const asOf = textOrNull(block?.asOf ?? block?.fetchedAt ?? block?.newestPointAt);
  const status = missing ? 'MISSING' : stale ? 'STALE' : 'OK';
  return { status, source, asOf, stale, error };
}

function addProviderError(warnings, label, error) {
  if (!error) return;
  warnings.push(`${label}: ${error}`);
}

function finiteCoverage(value) {
  const n = Number(value);
  return Number.isFinite(n) ? Math.max(0, Math.min(100, Math.round(n))) : 0;
}

function textOrNull(value) {
  const text = String(value ?? '').trim();
  return text || null;
}

function upper(value) { return String(value ?? '').trim().toUpperCase(); }
function lower(value) { return String(value ?? '').trim().toLowerCase(); }
