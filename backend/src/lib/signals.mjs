import crypto from "node:crypto";

export function evaluateSignals(items, quotes) {
  const now = new Date().toISOString();
  const result = [];
  for (const item of items) {
    const quote = quotes.get(item.id);
    const sellStatus = item.alertStatus || item.status;
    if (sellStatus === "VERKAUFEN") {
      const reason = item.alertReason || "Der Radar hat ein belastbares Verkaufssignal gesetzt. Bitte Investmentthese und Orderentscheidung prüfen.";
      result.push(make(item, "SELL", `${item.name}: VERKAUFEN prüfen`, reason, now, `manual-sell-${item.alertUpdatedAt || "current"}`));
      continue;
    }
    if (sellStatus === "DRINGEND_PRUEFEN") {
      const reason = item.alertReason || "Der Radar hat den Wert auf DRINGEND PRÜFEN gesetzt.";
      result.push(make(item, "REVIEW", `${item.name}: dringend prüfen`, reason, now, `manual-review-${item.alertUpdatedAt || "current"}`));
    }
    if (!quote || quote.price == null) continue;
    if (item.hardReviewBelow != null && quote.price <= item.hardReviewBelow) {
      result.push(make(item, "REVIEW", `${item.name}: Kurs-Schwelle erreicht`, `Kurs ${fmt(quote.price)} ${quote.currency} liegt unter der Prüfschwelle ${fmt(item.hardReviewBelow)}. Kein automatischer Verkauf – Gründe prüfen.`, now, `below-${item.hardReviewBelow}`));
    }
    if (quote.percentChange != null && quote.percentChange <= -Math.abs(item.reviewDrop1dPct)) {
      result.push(make(item, "REVIEW", `${item.name}: ungewöhnlicher Tagesverlust`, `Tagesbewegung ${quote.percentChange.toFixed(2)} %. Prüfe Nachrichten und Investmentthese; normale Schwankungen unterhalb dieser Schwelle lösen keinen Alarm aus.`, now, `drop-threshold-${Math.abs(item.reviewDrop1dPct)}`));
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
function fmt(n) { return n.toFixed(2); }
