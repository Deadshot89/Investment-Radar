export function updateAnalysisMemory(state, items) {
  const previousScores = {};
  const previousRecommendations = {};
  for (const item of items ?? []) {
    const rawScore = item?.scoreTotal;
    if (rawScore != null && !(typeof rawScore === "string" && rawScore.trim() === "")) {
      const score = Number(rawScore);
      if (Number.isFinite(score)) previousScores[item.id] = score;
    }
    const recommendation = String(item?.recommendation ?? "").trim().toUpperCase();
    if (recommendation) previousRecommendations[item.id] = recommendation;
  }
  return {
    ...state,
    previousScores,
    previousRecommendations
  };
}

export function mergeRecentAlerts(newAlerts, existingAlerts) {
  const merged = [...(newAlerts ?? []), ...(existingAlerts ?? [])];
  return merged
    .filter((alert, index, all) => alert?.id && all.findIndex((candidate) => candidate?.id === alert.id) === index)
    .slice(0, 50);
}
