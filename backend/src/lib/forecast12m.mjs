export function forecast12m(item) {
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
  if (reasons.length === 0) reasons.push("Die Richtung ergibt sich aus Qualität, Bewertung, Wachstum, Risiko und verfügbaren Kursdaten.");

  return { expectedChangePct, direction, reasons: [...new Set(reasons)].slice(0, 3) };
}

export function directionLabel(direction) {
  return direction === "UP" ? "Aufwärts" : direction === "DOWN" ? "Abwärts" : "Seitwärts";
}

function finite(value) {
  if (value == null || (typeof value === "string" && value.trim() === "")) return null;
  const n = Number(value);
  return Number.isFinite(n) ? n : null;
}
function clamp(value, min, max) { return Math.min(max, Math.max(min, value)); }
function round1(value) { return Math.round(value * 10) / 10; }
function signed1(value) { return `${value >= 0 ? "+" : ""}${Number(value).toFixed(1)}`; }
