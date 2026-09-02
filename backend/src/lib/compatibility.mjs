export function buildCompatibilityAllocations(items, budget = 100) {
  const safeBudget = Math.max(0, Math.round(Number(budget) || 0));
  const result = new Map(items.map((item) => [item.id, 0]));
  const eligible = items
    .filter((item) => item?.recommendation === "BUY" && Number(item?.scoreTotal) >= 75)
    .map((item) => ({ item, weight: Math.max(1, Number(item.scoreTotal) - 74) }));
  if (!safeBudget || !eligible.length) return result;

  const totalWeight = eligible.reduce((sum, row) => sum + row.weight, 0);
  const exact = eligible.map((row) => ({
    id: row.item.id,
    exact: safeBudget * row.weight / totalWeight
  }));
  let allocated = 0;
  for (const row of exact) {
    const floor = Math.floor(row.exact);
    result.set(row.id, floor);
    allocated += floor;
  }
  let remainder = safeBudget - allocated;
  for (const row of [...exact].sort((a, b) => (b.exact - Math.floor(b.exact)) - (a.exact - Math.floor(a.exact)))) {
    if (remainder <= 0) break;
    result.set(row.id, (result.get(row.id) || 0) + 1);
    remainder--;
  }
  return result;
}

export function legacyStatus(recommendation) {
  return ({ BUY: "KAUFEN", WATCH: "BEOBACHTEN", NO_BUY: "NICHT KAUFEN", REVIEW: "VERKAUF PRÜFEN" })[recommendation] || "BEOBACHTEN";
}
