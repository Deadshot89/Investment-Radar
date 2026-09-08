const ALLOWED_VERIFICATIONS = new Set(["VERIFIED_PRIMARY", "VERIFIED_SECONDARY"]);

function clean(value) {
  return typeof value === "string" ? value.trim() : "";
}

function isHttpsUrl(value) {
  try {
    return new URL(value).protocol === "https:";
  } catch {
    return false;
  }
}

function parseProviderConfig(configValue) {
  const text = clean(configValue);
  if (!text) return [];
  let parsed;
  try {
    parsed = JSON.parse(text);
  } catch {
    throw new Error("MARKET_EVENT_PROVIDERS_JSON ist kein gültiges JSON");
  }
  if (!Array.isArray(parsed)) {
    throw new Error("MARKET_EVENT_PROVIDERS_JSON muss ein Array sein");
  }
  return parsed;
}

export function buildConfiguredMarketEventProviders(
  configValue = process.env.MARKET_EVENT_PROVIDERS_JSON ?? "",
  { fetchImpl = globalThis.fetch } = {}
) {
  const providers = [];
  const trustedSources = new Map();
  const seenProviderIds = new Set();

  for (const raw of parseProviderConfig(configValue)) {
    const providerId = clean(raw?.providerId).toLowerCase();
    const endpoint = clean(raw?.endpoint);
    const verification = clean(raw?.verification).toUpperCase();

    if (
      !providerId ||
      seenProviderIds.has(providerId) ||
      !isHttpsUrl(endpoint) ||
      !ALLOWED_VERIFICATIONS.has(verification)
    ) {
      continue;
    }

    seenProviderIds.add(providerId);
    trustedSources.set(providerId, { verification });
    providers.push({
      providerId,
      async load(ids) {
        if (typeof fetchImpl !== "function") {
          throw new Error(`Market event provider ${providerId}: fetch ist nicht verfügbar`);
        }
        const url = new URL(endpoint);
        url.searchParams.set("ids", (Array.isArray(ids) ? ids : []).join(","));
        const response = await fetchImpl(url.toString(), {
          method: "GET",
          headers: { Accept: "application/json" },
          signal: typeof AbortSignal?.timeout === "function" ? AbortSignal.timeout(12_000) : undefined
        });
        if (!response?.ok) {
          throw new Error(`Market event provider ${providerId} HTTP ${response?.status ?? "unknown"}`);
        }
        const payload = await response.json();
        const items = Array.isArray(payload) ? payload : payload?.items;
        if (!Array.isArray(items)) {
          throw new Error(`Market event provider ${providerId}: Antwort enthält keine items`);
        }
        return items.map((item) => ({ ...item, providerId }));
      }
    });
  }

  return { providers, trustedSources };
}
