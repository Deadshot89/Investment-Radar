const EVENT_TYPES = new Set([
  "EARNINGS",
  "GUIDANCE",
  "PROFIT_WARNING",
  "DIVIDEND",
  "CAPITAL_ACTION",
  "MANAGEMENT",
  "M_AND_A",
  "PRODUCT_OR_APPROVAL",
  "REGULATORY_OR_LEGAL",
  "CREDIT_RATING",
  "MACRO",
  "FUND_STRUCTURE"
]);

const DIRECTIONS = new Set(["POSITIVE", "NEGATIVE", "MIXED", "NEUTRAL"]);
const HORIZONS = new Set(["SHORT", "MEDIUM", "LONG"]);
const VERIFICATIONS = new Set(["VERIFIED_PRIMARY", "VERIFIED_SECONDARY"]);
const MAX_MARKET_EVENT_IDS = 40;

function clean(value) {
  return typeof value === "string" ? value.trim() : "";
}

function clampPercent(value) {
  const number = Number(value);
  if (!Number.isFinite(number)) return 0;
  return Math.max(0, Math.min(100, Math.round(number)));
}

function validTimestamp(value) {
  const text = clean(value);
  return text !== "" && Number.isFinite(Date.parse(text));
}

function validSourceUrl(value) {
  const text = clean(value);
  if (!text) return false;
  try {
    const url = new URL(text);
    return url.protocol === "https:";
  } catch {
    return false;
  }
}

function sourceVerification(providerId, trustedSources) {
  const policy = trustedSources instanceof Map ? trustedSources.get(providerId) : undefined;
  const verification = clean(policy?.verification).toUpperCase();
  return VERIFICATIONS.has(verification) ? verification : "UNVERIFIED";
}

function providerError(providerId, error) {
  return {
    providerId: clean(providerId).toLowerCase(),
    code: "PROVIDER_FAILED",
    message: error instanceof Error && error.message ? error.message : "Market event provider failed"
  };
}

export function buildEventFingerprint(raw) {
  const instrumentId = clean(raw?.instrumentId).toLowerCase();
  const type = clean(raw?.type).toLowerCase();
  const providerId = clean(raw?.providerId).toLowerCase();
  const providerEventId = clean(raw?.providerEventId).toLowerCase();
  if (!instrumentId || !type || !providerId || !providerEventId) return "";
  return `${instrumentId}|${type}|${providerId}|${providerEventId}`;
}

export function parseMarketEventIds(value) {
  const input = Array.isArray(value) ? value : String(value ?? "").split(",");
  const ids = [...new Set(input.map((id) => clean(String(id)).toLowerCase()).filter(Boolean))];
  if (ids.length > MAX_MARKET_EVENT_IDS) {
    throw new Error(`Market Events unterstützen höchstens ${MAX_MARKET_EVENT_IDS} Instrumente pro Abruf`);
  }
  return ids;
}

export function normalizeMarketEvents(rawEvents, { trustedSources = new Map() } = {}) {
  const events = Array.isArray(rawEvents) ? rawEvents : [];
  const seen = new Set();
  const normalized = [];

  for (const raw of events) {
    const providerId = clean(raw?.providerId).toLowerCase();
    const providerEventId = clean(raw?.providerEventId);
    const instrumentId = clean(raw?.instrumentId).toLowerCase();
    const type = clean(raw?.type).toUpperCase();
    const title = clean(raw?.title);
    const eventAt = clean(raw?.eventAt);
    const publishedAt = clean(raw?.publishedAt);
    const sourceName = clean(raw?.sourceName);
    const sourceUrl = clean(raw?.sourceUrl);
    const direction = clean(raw?.direction).toUpperCase();
    const horizon = clean(raw?.horizon).toUpperCase();
    const fingerprint = buildEventFingerprint({
      instrumentId,
      type,
      providerId,
      providerEventId
    });

    if (
      !providerId ||
      !providerEventId ||
      !instrumentId ||
      !EVENT_TYPES.has(type) ||
      !title ||
      !validTimestamp(eventAt) ||
      !validTimestamp(publishedAt) ||
      !sourceName ||
      !validSourceUrl(sourceUrl) ||
      !DIRECTIONS.has(direction) ||
      !HORIZONS.has(horizon) ||
      !fingerprint ||
      seen.has(fingerprint)
    ) {
      continue;
    }

    seen.add(fingerprint);
    normalized.push({
      eventId: `${providerId}:${providerEventId}`,
      instrumentId,
      type,
      title,
      summary: clean(raw?.summary),
      eventAt,
      publishedAt,
      sourceName,
      sourceUrl,
      verification: sourceVerification(providerId, trustedSources),
      direction,
      horizon,
      materiality: clampPercent(raw?.materiality),
      confidencePct: clampPercent(raw?.confidencePct),
      fingerprint
    });
  }

  return normalized;
}

export async function queryMarketEvents(idsValue, {
  providers = [],
  trustedSources = new Map(),
  now = () => new Date()
} = {}) {
  const ids = parseMarketEventIds(idsValue);
  const rawEvents = [];
  const errors = [];

  for (const provider of Array.isArray(providers) ? providers : []) {
    const providerId = clean(provider?.providerId).toLowerCase();
    if (!providerId || typeof provider?.load !== "function") continue;
    try {
      const loaded = await provider.load(ids);
      if (Array.isArray(loaded)) rawEvents.push(...loaded);
    } catch (error) {
      errors.push(providerError(providerId, error));
    }
  }

  return {
    generatedAt: now().toISOString(),
    items: normalizeMarketEvents(rawEvents, { trustedSources }),
    errors
  };
}
