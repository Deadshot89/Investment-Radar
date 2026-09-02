import crypto from "node:crypto";
import { directionLabel, forecast12m } from "./forecast12m.mjs";

export function evaluateSignals(items, quotes = new Map(), context = {}) {
  const now = new Date().toISOString();
  const result = [];
  const previousScores = context.previousScores ?? {};
  const previousRecommendations = context.previousRecommendations ?? {};
  const previousForecast12m = context.previousForecast12m ?? {};
  const heldIds = context.heldIds instanceof Set ? context.heldIds : new Set(context.heldIds ?? []);

  for (const item of items) {
    const quote = quotes.get?.(item.id) ?? null;
    const price = finite(quote?.price) ?? finite(item?.price);
    const currency = String(quote?.currency ?? item?.currency ?? "");
    const percentChange = finite(quote?.percentChange) ?? finite(item?.percentChange);
    const manualStatus = String(item.alertStatus ?? "").trim().toUpperCase();

    if (manualStatus === "VERKAUFEN" || manualStatus === "SELL") {
      const reason = item.alertReason || "Der Radar hat ein manuelles Verkaufssignal gesetzt. Bitte Investmentthese und Orderentscheidung prüfen.";
      result.push(make(item, "SELL", `${item.name}: VERKAUFEN prüfen`, reason, now, `manual-sell-${item.alertUpdatedAt || "current"}`));
    } else if (["DRINGEND_PRUEFEN", "DRINGEND PRÜFEN", "REVIEW"].includes(manualStatus)) {
      const reason = item.alertReason || "Der Radar hat den Wert manuell auf DRINGEND PRÜFEN gesetzt.";
      result.push(make(item, "REVIEW", `${item.name}: dringend prüfen`, reason, now, `manual-review-${item.alertUpdatedAt || "current"}`));
    }

    if (price != null && item.hardReviewBelow != null && price <= Number(item.hardReviewBelow)) {
      result.push(make(
        item, "THRESHOLD", `${item.name}: Kurs-Schwelle erreicht`,
        `Kurs ${fmt(price)} ${currency} liegt unter der Prüfschwelle ${fmt(Number(item.hardReviewBelow))}. Kein automatischer Verkauf – Gründe prüfen.`,
        now, `below-${item.hardReviewBelow}`
      ));
    }

    const dropThreshold = Math.abs(Number(item.reviewDrop1dPct ?? 0));
    if (percentChange != null && dropThreshold > 0 && percentChange <= -dropThreshold) {
      const momentumReason = movementReason(item, percentChange);
      result.push(make(
        item, "THRESHOLD", `${item.name}: ungewöhnlicher Tagesverlust`,
        `Tagesbewegung ${percentChange.toFixed(2)} %. Schwelle: -${dropThreshold.toFixed(2)} %. Warum: ${momentumReason}`,
        now, `drop-threshold-${dropThreshold}`
      ));
    } else if (percentChange != null && dropThreshold > 0 && percentChange >= dropThreshold) {
      const momentumReason = movementReason(item, percentChange);
      result.push(make(
        item, "THRESHOLD", `${item.name}: ungewöhnlicher Tagesanstieg`,
        `Tagesbewegung +${percentChange.toFixed(2)} %. Schwelle: +${dropThreshold.toFixed(2)} %. Warum: ${momentumReason}`,
        now, `rise-threshold-${dropThreshold}`
      ));
    }

    const currentForecast = forecast12m(item);
    const previousForecast = previousForecast12m[item.id];
    if (previousForecast && previousForecast.direction && previousForecast.direction !== currentForecast.direction) {
      const prior = finite(previousForecast.expectedChangePct);
      const current = currentForecast.expectedChangePct;
      const from = directionLabel(previousForecast.direction);
      const to = directionLabel(currentForecast.direction);
      const level = currentForecast.direction === "UP" ? "BUY" : "REVIEW";
      const title = currentForecast.direction === "DOWN"
        ? `${item.name}: 12M-Prognose kippt nach unten`
        : currentForecast.direction === "UP"
          ? `${item.name}: 12M-Prognose dreht nach oben`
          : `${item.name}: 12M-Prognose wird neutral`;
      result.push(make(
        item,
        level,
        title,
        `12M-Prognose: ${from}${prior != null ? ` (${signed1(prior)} %)` : ""} → ${to} (${signed1(current)} %). Warum: ${currentForecast.reasons.join(" ")}`,
        now,
        `forecast-direction-${previousForecast.direction}-${currentForecast.direction}-${Math.round(current * 10)}`
      ));
    }

    const held = heldIds.has(item.id);
    const currentScore = finite(item.scoreTotal);
    const previousScore = finite(previousScores[item.id]);
    if (held && currentScore != null && previousScore != null && previousScore - currentScore >= 15) {
      result.push(make(
        item, "REVIEW", `${item.name}: Score deutlich gefallen`,
        `Der Investment-Score ist von ${Math.round(previousScore)} auf ${Math.round(currentScore)} gefallen. Prüfe Bewertung, Wachstum und Investmentthese.`,
        now, `score-drop-${Math.round(previousScore)}-${Math.round(currentScore)}`
      ));
    }
    if (held && currentScore != null && currentScore < 45) {
      result.push(make(
        item, "REVIEW", `${item.name}: schwacher Gesamtscore`,
        `Der aktuelle Gesamtscore liegt nur bei ${Math.round(currentScore)}/100. Position und Investmentthese prüfen.`,
        now, "score-review-floor-45"
      ));
    }

    const m3 = finite(item?.momentum?.m3);
    const m6 = finite(item?.momentum?.m6);
    if (held && m3 != null && m6 != null && m3 <= -10 && m6 <= -10) {
      result.push(make(
        item, "REVIEW", `${item.name}: mittelfristiger Trendbruch`,
        `3M (${m3.toFixed(1)} %) und 6M (${m6.toFixed(1)} %) sind gleichzeitig deutlich negativ.`,
        now, "momentum-breakdown-m3-m6"
      ));
    }

    const fundamentals = item?.fundamentals;
    const revenueGrowth = finite(fundamentals?.revenueGrowth ?? fundamentals?.metrics?.revenueGrowth);
    const epsGrowth = finite(fundamentals?.epsGrowth ?? fundamentals?.metrics?.epsGrowth);
    const fundamentalCoverage = finite(fundamentals?.coveragePct);
    const fundamentalDataSufficient = fundamentalCoverage != null && fundamentalCoverage >= 50 && fundamentals?.stale !== true;
    if (held && fundamentalDataSufficient && revenueGrowth != null && epsGrowth != null && revenueGrowth <= -0.10 && epsGrowth <= -0.20) {
      result.push(make(
        item, "REVIEW", `${item.name}: fundamentale Verschlechterung`,
        "Umsatz- und Gewinnwachstum sind gleichzeitig deutlich negativ. Investmentthese prüfen.",
        now, "fundamental-growth-breakdown"
      ));
    }

    const recommendation = String(item.recommendation ?? "").toUpperCase();
    const previousRecommendation = String(previousRecommendations[item.id] ?? "").toUpperCase();
    if (recommendation === "BUY" && previousRecommendation && previousRecommendation !== "BUY") {
      result.push(make(
        item, "BUY", `${item.name}: neue Kaufchance`,
        `Der Radar ist von ${previousRecommendation} auf BUY gewechselt${currentScore != null ? ` (Score ${Math.round(currentScore)}/100)` : ""}. 12M-Prognose: ${directionLabel(currentForecast.direction)} (${signed1(currentForecast.expectedChangePct)} %). Warum: ${currentForecast.reasons.join(" ")}`,
        now, `buy-transition-${previousRecommendation}-BUY`
      ));
    }
  }
  return dedupeByFingerprint(result);
}

function movementReason(item, percentChange) {
  const m1 = finite(item?.momentum?.m1);
  const m3 = finite(item?.momentum?.m3);
  if (m1 != null && Math.sign(m1) === Math.sign(percentChange) && Math.abs(m1) >= 5) {
    return `Die Bewegung bestätigt das 1M-Momentum (${signed1(m1)} %). Nachrichten und Fundamentaldaten trotzdem prüfen.`;
  }
  if (m3 != null && Math.sign(m3) === Math.sign(percentChange) && Math.abs(m3) >= 5) {
    return `Die Bewegung passt zum 3M-Trend (${signed1(m3)} %). Nachrichten und Fundamentaldaten prüfen.`;
  }
  return "Die Tagesbewegung ist größer als die hinterlegte Alarmschwelle. Nachrichten, Volumen und Investmentthese prüfen.";
}

function make(item, level, title, message, createdAt, reason) {
  const fingerprint = crypto.createHash("sha256").update(`${item.id}|${level}|${reason}`).digest("hex").slice(0, 20);
  return { id: `${item.id}-${fingerprint}`, itemId: item.id, level, title, message, createdAt, fingerprint };
}
function dedupeByFingerprint(alerts) {
  const seen = new Set();
  return alerts.filter((a) => !seen.has(a.fingerprint) && seen.add(a.fingerprint));
}
function finite(value) {
  if (value == null || (typeof value === "string" && value.trim() === "")) return null;
  const n = typeof value === "number" ? value : Number(value);
  return Number.isFinite(n) ? n : null;
}
function fmt(n) { return Number(n).toFixed(2); }
function signed1(n) { return `${Number(n) >= 0 ? "+" : ""}${Number(n).toFixed(1)}`; }
