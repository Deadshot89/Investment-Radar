export function forecast12m(item) {
  const dataQuality = item?.dataQuality ?? null;
  const isEtf = String(item?.type ?? '').toUpperCase() === 'ETF';
  const forecastCoverage = finite(dataQuality?.forecastInputCoverage) ?? finite(item?.coverage) ?? inferInputCoverage(item);
  const missing = new Set(Array.isArray(dataQuality?.missingBlocks) ? dataQuality.missingBlocks : []);
  const conflicts = Array.isArray(dataQuality?.criticalConflicts) ? dataQuality.criticalConflicts.filter(Boolean) : [];
  const hasQualityContract = dataQuality != null;
  const criticalMissing = hasQualityContract && (
    missing.has('quote') ||
    missing.has('history') ||
    (!isEtf && missing.has('fundamentals')) ||
    conflicts.length > 0 ||
    (forecastCoverage ?? 0) < 50
  );
  const quality = criticalMissing ? 'NICHT_BELASTBAR' : qualityFromCoverage(forecastCoverage);
  const confidencePct = quality === 'NICHT_BELASTBAR' ? clamp(Math.round(forecastCoverage ?? 0), 0, 49) : confidenceFromQuality(forecastCoverage, item);
  const usedInputs = collectUsedInputs(item);
  const asOf = String(item?.analysisAsOf ?? item?.momentum?.asOf ?? new Date().toISOString());

  if (quality === 'NICHT_BELASTBAR') {
    return {
      expectedChangePct: null,
      bearChangePct: null,
      bullChangePct: null,
      direction: 'UNKNOWN',
      quality,
      confidencePct,
      reasons: nonActionableReasons({ missing, conflicts, forecastCoverage }),
      risks: buildRisks(item, { missing, conflicts, quality }),
      usedInputs,
      asOf
    };
  }

  const centered = (value, maxImpact) => {
    const n = finite(value);
    if (n == null) return 0;
    return ((Math.min(100, Math.max(0, n)) - 50) / 50) * maxImpact;
  };

  let drift = 0;
  drift += centered(item?.scoreQuality, 5);
  drift += centered(item?.scoreValuation, 5);
  drift += centered(item?.scoreGrowth, 8);
  drift += centered(item?.scoreRisk, 3);

  const fundamentals = item?.fundamentals ?? {};
  const revenueGrowth = finite(fundamentals.revenueGrowth ?? fundamentals.metrics?.revenueGrowth);
  const epsGrowth = finite(fundamentals.epsGrowth ?? fundamentals.metrics?.epsGrowth);
  const freeCashFlowYield = finite(fundamentals.freeCashFlowYield ?? fundamentals.metrics?.freeCashFlowYield);
  const debtToEquity = finite(fundamentals.debtToEquity ?? fundamentals.metrics?.debtToEquity);

  if (revenueGrowth != null) drift += clamp(revenueGrowth * 100 * 0.12, -4, 4);
  if (epsGrowth != null) drift += clamp(epsGrowth * 100 * 0.14, -5, 5);
  if (freeCashFlowYield != null) drift += clamp((freeCashFlowYield * 100) - 3, -2, 3) * 0.5;
  if (debtToEquity != null && debtToEquity > 2) drift -= Math.min((debtToEquity - 2) * 1.2, 4);
  drift = clamp(drift, -30, 35);

  const m12 = finite(item?.momentum?.m12);
  const expectedChangePct = round1(clamp(drift + (m12 ?? 0) * 0.34, -65, 65));
  const risk = clamp(Number(item?.risk ?? 1), 1, 5);
  const coverage = finite(item?.coverage) ?? forecastCoverage;
  const coveragePenalty = coverage == null ? 4 : coverage < 50 ? 6 : coverage < 70 ? 3.5 : coverage < 85 ? 1.5 : 0;
  const uncertainty = Math.max(3, 5 + risk * 2.4 + coveragePenalty);
  const bearChangePct = round1(Math.max(-80, expectedChangePct - uncertainty));
  const bullChangePct = round1(Math.min(120, expectedChangePct + uncertainty));
  const direction = expectedChangePct >= 2.5 ? "UP" : expectedChangePct <= -2.5 ? "DOWN" : "SIDEWAYS";
  const reasons = [];

  if (m12 != null) {
    if (m12 >= 5) reasons.push(`Positives 12M-Momentum (${signed1(m12)} %) stützt die Prognose.`);
    else if (m12 <= -5) reasons.push(`Negatives 12M-Momentum (${signed1(m12)} %) belastet die Prognose.`);
    else reasons.push(`12M-Momentum (${signed1(m12)} %) liefert aktuell wenig Richtung.`);
  }
  const growth = finite(item?.scoreGrowth);
  if (growth != null && growth >= 70) reasons.push(`Starker Wachstumsscore (${Math.round(growth)}/100) unterstützt das Potenzial.`);
  else if (growth != null && growth <= 40) reasons.push(`Schwacher Wachstumsscore (${Math.round(growth)}/100) begrenzt das Potenzial.`);
  const valuation = finite(item?.scoreValuation);
  if (valuation != null && valuation >= 70) reasons.push(`Attraktive Bewertung (${Math.round(valuation)}/100) schafft Spielraum.`);
  else if (valuation != null && valuation <= 40) reasons.push(`Anspruchsvolle Bewertung (${Math.round(valuation)}/100) erhöht das Rückschlagrisiko.`);
  if (epsGrowth != null && epsGrowth >= 0.10) reasons.push("Zweistelliges Gewinnwachstum stützt das Basisszenario.");
  else if (epsGrowth != null && epsGrowth < 0) reasons.push("Sinkende Gewinne belasten das Basisszenario.");
  if (revenueGrowth != null && revenueGrowth >= 0.10) reasons.push("Zweistelliges Umsatzwachstum unterstützt die operative Entwicklung.");
  if (reasons.length === 0) reasons.push("Die Richtung ergibt sich aus Qualität, Bewertung, Wachstum, Risiko und verfügbaren Kursdaten.");
  if (reasons.length === 1 && usedInputs.length > 1) reasons.push(`Die Prognose nutzt ${usedInputs.length} belegte Analysefaktoren.`);

  return {
    expectedChangePct,
    bearChangePct,
    bullChangePct,
    direction,
    quality,
    confidencePct,
    reasons: [...new Set(reasons)].slice(0, 4),
    risks: buildRisks(item, { missing, conflicts, quality }),
    usedInputs,
    asOf
  };
}

export function directionLabel(direction) {
  if (direction === 'UNKNOWN') return 'Nicht belastbar';
  return direction === "UP" ? "Aufwärts" : direction === "DOWN" ? "Abwärts" : "Seitwärts";
}

function qualityFromCoverage(value) {
  const coverage = finite(value);
  if (coverage == null) return 'NIEDRIG';
  if (coverage >= 85) return 'HOCH';
  if (coverage >= 70) return 'MITTEL';
  if (coverage >= 50) return 'NIEDRIG';
  return 'NICHT_BELASTBAR';
}

function confidenceFromQuality(coverage, item) {
  const base = clamp(Math.round(finite(coverage) ?? 55), 0, 100);
  const risk = clamp(Number(item?.risk ?? 3), 1, 5);
  const stalePenalty = item?.momentum?.stale || item?.fundamentals?.stale ? 8 : 0;
  return clamp(Math.round(base - Math.max(0, risk - 3) * 2 - stalePenalty), 0, 100);
}

function inferInputCoverage(item) {
  const values = [
    item?.scoreQuality,
    item?.scoreValuation,
    item?.scoreGrowth,
    item?.scoreRisk,
    item?.momentum?.m6,
    item?.momentum?.m12,
    item?.fundamentals?.revenueGrowth ?? item?.fundamentals?.metrics?.revenueGrowth,
    item?.fundamentals?.epsGrowth ?? item?.fundamentals?.metrics?.epsGrowth
  ];
  return Math.round(values.filter((value) => finite(value) != null).length / values.length * 100);
}

function collectUsedInputs(item) {
  const candidates = [
    ['score.quality', item?.scoreQuality],
    ['score.valuation', item?.scoreValuation],
    ['score.growth', item?.scoreGrowth],
    ['score.risk', item?.scoreRisk],
    ['momentum.m3', item?.momentum?.m3],
    ['momentum.m6', item?.momentum?.m6],
    ['momentum.m12', item?.momentum?.m12],
    ['fundamentals.revenueGrowth', item?.fundamentals?.revenueGrowth ?? item?.fundamentals?.metrics?.revenueGrowth],
    ['fundamentals.epsGrowth', item?.fundamentals?.epsGrowth ?? item?.fundamentals?.metrics?.epsGrowth],
    ['fundamentals.freeCashFlowYield', item?.fundamentals?.freeCashFlowYield ?? item?.fundamentals?.metrics?.freeCashFlowYield],
    ['fundamentals.debtToEquity', item?.fundamentals?.debtToEquity ?? item?.fundamentals?.metrics?.debtToEquity],
    ['risk.level', item?.risk]
  ];
  return candidates.filter(([, value]) => finite(value) != null).map(([name]) => name);
}

function buildRisks(item, { missing, conflicts, quality }) {
  const risks = [];
  const riskLevel = finite(item?.risk);
  const debt = finite(item?.fundamentals?.debtToEquity ?? item?.fundamentals?.metrics?.debtToEquity);
  const m12 = finite(item?.momentum?.m12);
  const valuation = finite(item?.scoreValuation);

  if (riskLevel != null) risks.push(`Risikostufe ${Math.round(clamp(riskLevel, 1, 5))}/5 begrenzt die Prognosesicherheit.`);
  if (debt != null && debt > 2) risks.push(`Hohe Verschuldung (Debt/Equity ${debt.toFixed(1)}) erhöht das Rückschlagrisiko.`);
  if (m12 != null && m12 < -5) risks.push(`Negatives 12M-Momentum (${signed1(m12)} %) bleibt ein technischer Risikofaktor.`);
  if (valuation != null && valuation <= 40) risks.push(`Schwache Bewertungsnote (${Math.round(valuation)}/100) lässt wenig Sicherheitspuffer.`);
  if (quality === 'NIEDRIG') risks.push('Die reduzierte Datenabdeckung vergrößert die Bandbreite möglicher Ergebnisse.');
  if (missing.size) risks.push(`Fehlende Datenblöcke: ${[...missing].join(', ')}.`);
  if (conflicts.length) risks.push(`Providerkonflikte bei: ${conflicts.join(', ')}.`);
  if (risks.length === 0) risks.push('Markt- und Unternehmensentwicklung können vom berechneten Szenario abweichen.');
  return [...new Set(risks)].slice(0, 4);
}

function nonActionableReasons({ missing, conflicts, forecastCoverage }) {
  const reasons = [];
  if ((forecastCoverage ?? 0) < 50) reasons.push(`Prognose-Eingabedaten reichen nur zu ${Math.round(forecastCoverage ?? 0)} % aus.`);
  if (missing.has('quote')) reasons.push('Ein belastbarer aktueller Kurs fehlt.');
  if (missing.has('history')) reasons.push('Die Kurshistorie reicht für eine belastbare 12M-Prognose nicht aus.');
  if (missing.has('fundamentals')) reasons.push('Entscheidende Fundamentaldaten fehlen.');
  if (conflicts.length) reasons.push(`Widersprüchliche Providerdaten müssen zuerst geklärt werden: ${conflicts.join(', ')}.`);
  if (!reasons.length) reasons.push('Die vorhandenen Analysefaktoren reichen aktuell nicht für eine belastbare Prozentprognose.');
  return reasons.slice(0, 4);
}

function finite(value) {
  if (value == null || (typeof value === "string" && value.trim() === "")) return null;
  const n = Number(value);
  return Number.isFinite(n) ? n : null;
}
function clamp(value, min, max) { return Math.min(max, Math.max(min, value)); }
function round1(value) { return Math.round(value * 10) / 10; }
function signed1(value) { return `${value >= 0 ? "+" : ""}${Number(value).toFixed(1)}`; }
