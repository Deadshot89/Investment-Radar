const STOCK_WEIGHTS = Object.freeze({ quality: 25, valuation: 20, growth: 20, momentum: 20, risk: 15 });
const ETF_WEIGHTS = Object.freeze({ quality: 30, valuation: 10, growth: 10, momentum: 30, risk: 20 });

export function recommendationFromScore({ scoreTotal, coverage, hardReview = false }) {
  if (hardReview) return "REVIEW";
  const score = finite(scoreTotal);
  const dataCoverage = finite(coverage) ?? 0;
  if (score == null) return "REVIEW";
  if (dataCoverage < 50) return score < 55 ? "NO_BUY" : "WATCH";
  if (score >= 75) return "BUY";
  if (score >= 55) return "WATCH";
  return "NO_BUY";
}

export function scoreInvestment({ item, fundamentals = null, momentum = null, quote = null }) {
  const isEtf = String(item?.type ?? "").toUpperCase() === "ETF";
  const weights = isEtf ? ETF_WEIGHTS : STOCK_WEIGHTS;
  const components = isEtf
    ? {
        quality: finite(item?.etfStructureScore),
        valuation: finite(item?.etfValuationProxyScore),
        growth: finite(item?.etfGrowthProxyScore),
        momentum: finite(momentum?.score),
        risk: riskScore(item?.risk, quote, momentum)
      }
    : {
        quality: finite(fundamentals?.qualityScore),
        valuation: finite(fundamentals?.valuationScore),
        growth: finite(fundamentals?.growthScore),
        momentum: finite(momentum?.score),
        risk: riskScore(item?.risk, quote, momentum)
      };

  let availableWeight = 0;
  let weighted = 0;
  for (const [key, weight] of Object.entries(weights)) {
    const value = components[key];
    if (!Number.isFinite(value)) continue;
    availableWeight += weight;
    weighted += clamp(value, 0, 100) * weight;
  }
  const totalWeight = Object.values(weights).reduce((a, b) => a + b, 0);
  const coverage = Math.round((availableWeight / totalWeight) * 100);
  const scoreTotal = availableWeight > 0 ? Math.round(weighted / availableWeight) : null;
  const hardReview = ["VERKAUFEN", "SELL", "DRINGEND_PRUEFEN", "DRINGEND PRÜFEN", "REVIEW"]
    .includes(String(item?.alertStatus ?? "").trim().toUpperCase());
  const recommendation = recommendationFromScore({ scoreTotal, coverage, hardReview });
  const recommendationReasons = buildReasons({ components, coverage, recommendation, fundamentals, momentum });
  const scoreBreakdown = buildScoreBreakdown({ components, fundamentals, momentum, quote, item, isEtf });

  return {
    scoreTotal,
    scoreQuality: toIntOrNull(components.quality),
    scoreValuation: toIntOrNull(components.valuation),
    scoreGrowth: toIntOrNull(components.growth),
    scoreMomentum: toIntOrNull(components.momentum),
    scoreRisk: toIntOrNull(components.risk),
    scoreBreakdown,
    coverage,
    recommendation,
    recommendationReasons
  };
}

function buildScoreBreakdown({ components, fundamentals, momentum, quote, item, isEtf }) {
  const metrics = fundamentals?.metrics ?? {};
  const fundamentalCoverage = Number.isFinite(Number(fundamentals?.coveragePct)) ? clamp(Number(fundamentals.coveragePct), 0, 100) : 0;
  const momentumCoverage = Number.isFinite(Number(momentum?.coveragePct)) ? clamp(Number(momentum.coveragePct), 0, 100) : 0;

  return {
    quality: pillar(
      components.quality,
      components.quality == null ? 0 : (isEtf ? 100 : fundamentalCoverage),
      components.quality == null ? [] : [scoreReason(components.quality, "Qualität stark", "Qualität solide", "Qualität schwach")],
      isEtf ? compactInputs({ etfStructureScore: item?.etfStructureScore }) : compactInputs({ operatingMargin: metrics.operatingMargin, netMargin: metrics.netMargin, roe: metrics.roe, roic: metrics.roic, debtToEquity: metrics.debtToEquity })
    ),
    valuation: pillar(
      components.valuation,
      components.valuation == null ? 0 : (isEtf ? 100 : fundamentalCoverage),
      components.valuation == null ? [] : [scoreReason(components.valuation, "Bewertung attraktiv", "Bewertung fair", "Bewertung anspruchsvoll")],
      isEtf ? compactInputs({ etfValuationProxyScore: item?.etfValuationProxyScore }) : compactInputs({ pe: metrics.pe, priceToSales: metrics.priceToSales, evToEbitda: metrics.evToEbitda, freeCashFlowYield: metrics.freeCashFlowYield })
    ),
    growth: pillar(
      components.growth,
      components.growth == null ? 0 : (isEtf ? 100 : fundamentalCoverage),
      components.growth == null ? [] : [scoreReason(components.growth, "Wachstum stark", "Wachstum solide", "Wachstum schwach")],
      isEtf ? compactInputs({ etfGrowthProxyScore: item?.etfGrowthProxyScore }) : compactInputs({ revenueGrowth: metrics.revenueGrowth, epsGrowth: metrics.epsGrowth })
    ),
    momentum: pillar(
      components.momentum,
      components.momentum == null ? 0 : momentumCoverage,
      components.momentum == null ? [] : [finite(momentum?.m6) != null && Number(momentum.m6) >= 10 ? "6M-Momentum positiv" : scoreReason(components.momentum, "Momentum stark", "Momentum neutral", "Momentum schwach")],
      compactInputs({ m1: momentum?.m1, m3: momentum?.m3, m6: momentum?.m6, m12: momentum?.m12 })
    ),
    risk: pillar(
      components.risk,
      components.risk == null ? 0 : 100,
      components.risk == null ? [] : [scoreReason(components.risk, "Risiko günstig", "Risiko moderat", "Risiko erhöht")],
      compactInputs({ riskLevel: item?.risk, dailyChange: quote?.percentChange, m3: momentum?.m3, m6: momentum?.m6 })
    )
  };
}

function pillar(score, coverage, reasons, inputs) {
  return { score: toIntOrNull(score), coverage: Math.round(clamp(Number(coverage) || 0, 0, 100)), reasons, inputs };
}

function compactInputs(values) {
  return Object.fromEntries(Object.entries(values).filter(([, value]) => finite(value) != null));
}

function riskScore(risk, quote, momentum) {
  const level = finite(risk);
  if (level == null) return null;
  const base = ({ 1: 96, 2: 86, 3: 72, 4: 56, 5: 40 })[Math.max(1, Math.min(5, Math.round(level)))] ?? 60;
  let adjustment = 0;
  const day = finite(quote?.percentChange);
  if (day != null && day <= -8) adjustment -= 12;
  else if (day != null && day <= -5) adjustment -= 7;
  const m3 = finite(momentum?.m3);
  const m6 = finite(momentum?.m6);
  if (m3 != null && m6 != null && m3 < -10 && m6 < -10) adjustment -= 8;
  return clamp(base + adjustment, 0, 100);
}

function buildReasons({ components, coverage, recommendation, fundamentals, momentum }) {
  const reasons = [];
  if (coverage < 70) reasons.push(`Datenabdeckung reduziert (${coverage} %)`);
  if (components.quality != null) reasons.push(scoreReason(components.quality, "Qualität stark", "Qualität solide", "Qualität schwach"));
  if (components.valuation != null) reasons.push(scoreReason(components.valuation, "Bewertung attraktiv", "Bewertung fair", "Bewertung anspruchsvoll"));
  if (components.growth != null) reasons.push(scoreReason(components.growth, "Wachstum stark", "Wachstum solide", "Wachstum schwach"));
  if (components.momentum != null) {
    const horizon = finite(momentum?.m6);
    if (horizon != null && horizon >= 10) reasons.push("6M-Momentum positiv");
    else reasons.push(scoreReason(components.momentum, "Momentum stark", "Momentum neutral", "Momentum schwach"));
  }
  if (fundamentals?.stale) reasons.push("Fundamentaldaten sind zwischengespeichert");
  if (recommendation === "REVIEW") reasons.unshift("Prüfsignal hat Vorrang vor dem Gesamtscore");
  return [...new Set(reasons.filter(Boolean))].slice(0, 3);
}

function scoreReason(value, high, middle, low) {
  if (value >= 75) return high;
  if (value >= 55) return middle;
  return low;
}
function finite(value) {
  if (value == null || (typeof value === "string" && value.trim() === "")) return null;
  const n = typeof value === "number" ? value : Number(value);
  return Number.isFinite(n) ? n : null;
}
function toIntOrNull(value) { return Number.isFinite(value) ? Math.round(clamp(value, 0, 100)) : null; }
function clamp(value, min, max) { return Math.max(min, Math.min(max, value)); }
