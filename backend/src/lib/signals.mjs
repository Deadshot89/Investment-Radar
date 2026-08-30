import crypto from "node:crypto";

export function evaluateSignals(items, quotes) {
  const now = new Date().toISOString();
  const result = [];
  for (const item of items) {
    const quote = quotes.get(item.id);
    if (item.status === "VERKAUFEN") {
      result.push(make(item, "SELL", `${item.name}: VERKAUFEN prüfen`, "Der Radar-Status wurde auf VERKAUFEN gesetzt. Bitte Investmentthese und Orderentscheidung prüfen.", now, "manual-sell"));
      continue;
    }
    if (item.status === "DRINGEND_PRUEFEN") {
      result.push(make(item, "REVIEW", `${item.name}: dringend prüfen`, "Der Radar-Status wurde auf DRINGEND PRÜFEN gesetzt.", now, "manual-review"));
    }
    if (!quote || quote.price == null) continue;
    if (item.hardReviewBelow != null && quote.price <= item.hardReviewBelow) {
      result.push(make(item, "REVIEW", `${item.name}: Kurs-Schwelle erreicht`, `Kurs ${fmt(quote.price)} ${quote.currency} liegt unter der Prüfschwelle ${fmt(item.hardReviewBelow)}. Kein automatischer Verkauf – Gründe prüfen.`, now, `below-${item.hardReviewBelow}`));
    }
    if (quote.percentChange != null && quote.percentChange <= -Math.abs(item.reviewDrop1dPct)) {
      result.push(make(item, "REVIEW", `${item.name}: ungewöhnlicher Tagesverlust`, `Tagesbewegung ${quote.percentChange.toFixed(2)} %. Prüfe Nachrichten und Investmentthese; normale Schwankungen unterhalb dieser Schwelle lösen keinen Alarm aus.`, now, `drop-${Math.floor(Math.abs(quote.percentChange))}`));
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
