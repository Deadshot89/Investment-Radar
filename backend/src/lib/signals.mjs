import crypto from "node:crypto";

export function evaluateSignals(items, quotes = new Map(), context = {}) {
  const now = new Date().toISOString();
  const result = [];
  const previousScores = context.previousScores ?? {};
  const previousRecommendations = context.previousRecommendations ?? {};
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
        item, "REVIEW", `${item.name}: Kurs-Schwelle erreicht`,
        `Kurs ${fmt(price)} ${currency} liegt unter der Prüfschwelle ${fmt(Number(item.hardReviewBelow))}. Kein automatischer Verkauf – Gründe prüfen.`,
        now, `below-${item.hardReviewBelow}`
      ));
    }

    const dropThreshold = Math.abs(Number(item.reviewDrop1dPct ?? 0));
    if (percentChange != null && dropThreshold > 0 && percentChange <= -dropThreshold) {
      result.push(make(
        item, "REVIEW", `${item.name}: ungewöhnlicher Tagesverlust`,
        `Tagesbewegung ${percentChange.toFixed(2)} %. Prüfe Nachrichten und Investmentthese; normale Schwankungen unterhalb dieser Schwelle lösen keinen Alarm aus.`,
        now, `drop-threshold-${dropThreshold}`
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

    const revenueGrowth = finite(item?.fundamentals?.revenueGrowth ?? item?.fundamentals?.metrics?.revenueGrowth);
    const epsGrowth = finite(item?.fundamentals?.epsGrowth ?? item?.fundamentals?.metrics?.epsGrowth);
    if (held && revenueGrowth != null && epsGrowth != null && revenueGrowth <= -0.10 && epsGrowth <= -0.20) {
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
        `Der Radar ist von ${previousRecommendation} auf BUY gewechselt${currentScore != null ? ` (Score ${Math.round(currentScore)}/100)` : ""}.`,
        now, `buy-transition-${previousRecommendation}-BUY`
      ));
    }
  }
  return dedupeByFingerprint(result);
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
  const n = typeof value === "number" ? value : Number(value);
  return Number.isFinite(n) ? n : null;
}
function fmt(n) { return Number(n).toFixed(2); }
