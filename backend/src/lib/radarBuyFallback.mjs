const MIN_SCORE = 75;
const MIN_COVERAGE = 70;
const MAX_RISK = 3;
const LIMIT = 3;

export function selectBuyFilterResults(items = []) {
  const realBuys = items.filter((item) => item.purchaseEligible === true && String(item.recommendation ?? "").toUpperCase() === "BUY");
  if (realBuys.length > 0) {
    return { items: sortByScore(realBuys).map(withDataGapReasons), buyFallbackActive: false };
  }

  const fallback = items
    .filter((item) => item.tradeRepublicEligible === true)
    .filter((item) => String(item.recommendation ?? "").toUpperCase() === "WATCH")
    .filter((item) => Number(item.scoreTotal ?? -1) >= MIN_SCORE)
    .filter((item) => Number(item.coverage ?? 0) >= MIN_COVERAGE)
    .filter((item) => Number(item.risk ?? 5) <= MAX_RISK)
    .sort((a, b) =>
      Number(b.scoreTotal ?? -1) - Number(a.scoreTotal ?? -1) ||
      Number(b.coverage ?? 0) - Number(a.coverage ?? 0) ||
      Number(a.risk ?? 5) - Number(b.risk ?? 5) ||
      String(a.name ?? "").localeCompare(String(b.name ?? ""))
    )
    .slice(0, LIMIT)
    .map((item) => withDataGapReasons({
      ...item,
      purchaseEligible: false,
      recommendation: "WATCH",
      recommendationReasons: [
        "Kaufkandidat – noch nicht bestätigt",
        ...(Array.isArray(item.recommendationReasons) ? item.recommendationReasons : [])
      ]
    }));

  return { items: fallback, buyFallbackActive: fallback.length > 0 };
}

export function withDataGapReasons(item = {}) {
  const issues = [];
  if (item.price == null && item.priceEur == null) issues.push("Kurs fehlt");
  if (item.scoreMomentum == null) issues.push("Historie fehlt");
  const type = String(item.type ?? "").toUpperCase();
  if (type !== "ETF" && item.scoreQuality == null && item.scoreValuation == null && item.scoreGrowth == null) {
    issues.push("Fundamentaldaten fehlen");
  }
  if (!item.analysisAsOf) issues.push("Analyse fehlt");
  if (item.coverage == null || Number(item.coverage) < 50) issues.push("Datenabdeckung unzureichend");

  return {
    ...item,
    recommendationReasons: [
      ...(Array.isArray(item.recommendationReasons) ? item.recommendationReasons : []),
      ...issues
    ].filter((value, index, list) => value && list.indexOf(value) === index)
  };
}

function sortByScore(items) {
  return [...items].sort((a, b) =>
    Number(b.scoreTotal ?? -1) - Number(a.scoreTotal ?? -1) ||
    String(a.name ?? "").localeCompare(String(b.name ?? ""))
  );
}
