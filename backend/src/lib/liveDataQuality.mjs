function finite(value) {
  if (value == null || (typeof value === "string" && value.trim() === "")) return null;
  const n = Number(value);
  return Number.isFinite(n) ? n : null;
}

function pct(count, total) {
  return total > 0 ? Math.round((count / total) * 100) : 0;
}

export function assessLiveRadarResponse(payload, {
  minUniverse = 2000,
  minSample = 10,
  minQuoteCoveragePct = 80,
  minAnalysisCoveragePct = 70,
  minQualityMetadataPct = 100
} = {}) {
  const items = Array.isArray(payload?.items) ? payload.items : [];
  const universeTotal = Math.max(0, Math.round(finite(payload?.universeTotal) ?? 0));
  const sampleSize = items.length;

  let identityCount = 0;
  let quoteCount = 0;
  let analysisCount = 0;
  let qualityMetadataCount = 0;
  let unsafeBuyCount = 0;
  let explicitDataErrorCount = 0;

  for (const item of items) {
    const hasIdentity = [item?.id, item?.name, item?.ticker, item?.isin]
      .every((value) => String(value ?? "").trim().length > 0);
    if (hasIdentity) identityCount += 1;

    const price = finite(item?.price);
    const hasQuote = price != null && price > 0 && String(item?.currency ?? "").trim().length > 0;
    if (hasQuote) quoteCount += 1;

    const scoreTotal = finite(item?.scoreTotal);
    const coverage = finite(item?.coverage ?? item?.dataQuality?.overallCoverage);
    const hasAnalysis = scoreTotal != null && coverage != null && coverage >= 0;
    if (hasAnalysis) analysisCount += 1;

    const quality = item?.dataQuality;
    const qualityCoverage = finite(quality?.overallCoverage);
    const hasQualityMetadata = quality && typeof quality === "object" && qualityCoverage != null
      && Array.isArray(quality?.missingBlocks) && Array.isArray(quality?.criticalConflicts);
    if (hasQualityMetadata) qualityMetadataCount += 1;

    if (String(item?.dataError ?? "").trim()) explicitDataErrorCount += 1;

    if (item?.purchaseEligible === true) {
      const safeBuy = String(item?.recommendation ?? "").trim().toUpperCase() === "BUY"
        && hasQuote
        && qualityCoverage != null
        && qualityCoverage >= 70
        && !quality?.missingBlocks?.includes?.("quote")
        && !quality?.missingBlocks?.includes?.("history")
        && (String(item?.type ?? "").trim().toUpperCase() === "ETF" || !quality?.missingBlocks?.includes?.("fundamentals"))
        && (quality?.criticalConflicts?.length ?? 0) === 0;
      if (!safeBuy) unsafeBuyCount += 1;
    }
  }

  const metrics = {
    universeTotal,
    sampleSize,
    identityCoveragePct: pct(identityCount, sampleSize),
    quoteCoveragePct: pct(quoteCount, sampleSize),
    analysisCoveragePct: pct(analysisCount, sampleSize),
    qualityMetadataPct: pct(qualityMetadataCount, sampleSize),
    explicitDataErrorCount,
    unsafeBuyCount
  };

  const failures = [];
  if (universeTotal < minUniverse) failures.push(`universe ${universeTotal}/${minUniverse}`);
  if (sampleSize < minSample) failures.push(`sample ${sampleSize}/${minSample}`);
  if (metrics.identityCoveragePct < 100) failures.push(`identity ${metrics.identityCoveragePct}%/100%`);
  if (metrics.quoteCoveragePct < minQuoteCoveragePct) failures.push(`quotes ${metrics.quoteCoveragePct}%/${minQuoteCoveragePct}%`);
  if (metrics.analysisCoveragePct < minAnalysisCoveragePct) failures.push(`analysis ${metrics.analysisCoveragePct}%/${minAnalysisCoveragePct}%`);
  if (metrics.qualityMetadataPct < minQualityMetadataPct) failures.push(`quality metadata ${metrics.qualityMetadataPct}%/${minQualityMetadataPct}%`);
  if (unsafeBuyCount > 0) failures.push(`unsafe BUY candidates ${unsafeBuyCount}`);

  return {
    ok: failures.length === 0,
    metrics,
    failures
  };
}
