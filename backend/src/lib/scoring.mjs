const STOCK_WEIGHTS = Object.freeze({ quality: 25, valuation: 20, growth: 20, momentum: 20, risk: 15 });
const ETF_WEIGHTS = Object.freeze({ quality: 30, valuation: 10, growth: 10, momentum: 30, risk: 20 });

export function recommendationFromScore({ scoreTotal, coverage, hardReview = false }) {
  if (hardReview) return "REVIEW";
  const score = finite(scoreTotal) ?? 0;
  const dataCoverage = finite(coverage) ?? 0;
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
  const coverage = Math.round((availableWeight / Object.values(weights).reduce((a, b) => a + b, 0)) * 100);
  const scoreTotal = availableWeight > 0 ? Math.round(weighted / availableWeight) : 0;
  const hardReview = ["VERKAUFEN", "SELL", "DRINGEND_PRUEFEN", "DRINGEND PRÜFEN", "REVIEW"]
    .includes(String(item?.alertStatus ?? "").trim().toUpperCase());
  const recommendation = recommendationFromScore({ scoreTotal, coverage, hardReview });
  const recommendationReasons = buildReasons({ components, coverage, recommendation, fundamentals, momentum });

  return {
    scoreTotal,
    scoreQuality: toIntOrNull(components.quality),
    scoreValuation: toIntOrNull(components.valuation),
    scoreGrowth: toIntOrNull(components.growth),
    scoreMomentum: toIntOrNull(components.momentum),
    scoreRisk: toIntOrNull(components.risk),
    coverage,
    recommendation,
    recommendationReasons
  };
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
