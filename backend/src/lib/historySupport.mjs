const DAY = 86_400_000;
const HORIZONS = Object.freeze({ d1: 1, m1: 30, m3: 91, m6: 182, m12: 365 });
const WEIGHTS = Object.freeze({ d1: 5, m1: 15, m3: 30, m6: 30, m12: 20 });

export function calculateMomentum(points, now = Date.now()) {
  const clean = (Array.isArray(points) ? points : [])
    .map((p) => ({ time: Number(p?.time), close: Number(p?.close) }))
    .filter((p) => Number.isFinite(p.time) && Number.isFinite(p.close) && p.close > 0 && p.time <= now)
    .sort((a, b) => a.time - b.time);
  const current = clean.at(-1);
  if (!current) return emptyMomentum();

  const returns = {};
  let availableWeight = 0;
  let weightedScore = 0;
  for (const [key, days] of Object.entries(HORIZONS)) {
    const base = pointAtOrBefore(clean, now - days * DAY);
    const value = base && base.close > 0 ? ((current.close - base.close) / base.close) * 100 : null;
    returns[key] = value;
    if (value == null) continue;
    const weight = WEIGHTS[key];
    availableWeight += weight;
    weightedScore += returnToScore(value, key) * weight;
  }

  return {
    ...returns,
    score: availableWeight > 0 ? Math.round(weightedScore / availableWeight) : null,
    coveragePct: Math.round(availableWeight),
    asOf: new Date(current.time).toISOString(),
    stale: false
  };
}

function pointAtOrBefore(points, target) {
  for (let i = points.length - 1; i >= 0; i--) {
    if (points[i].time <= target) return points[i];
  }
  return null;
}

function returnToScore(value, horizon) {
  const scale = ({ d1: 4, m1: 2.0, m3: 1.2, m6: 0.9, m12: 0.65 })[horizon] ?? 1;
  return clamp(50 + value * scale, 0, 100);
}
function emptyMomentum() {
  return { d1: null, m1: null, m3: null, m6: null, m12: null, score: null, coveragePct: 0, asOf: null, stale: false };
}
function clamp(value, min, max) { return Math.max(min, Math.min(max, value)); }
