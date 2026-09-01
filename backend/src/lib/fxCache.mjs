import fs from "node:fs/promises";
import os from "node:os";
import path from "node:path";

const CONTAINER = "investment-radar";
const BLOB = "fx-cache.json";

export async function loadFxCache() {
  const connection = process.env.AzureWebJobsStorage;
  if (!connection || connection === "UseDevelopmentStorage=true") return normalizeCache(await loadLocal());
  try {
    const { BlobServiceClient } = await import("@azure/storage-blob");
    const service = BlobServiceClient.fromConnectionString(connection);
    const container = service.getContainerClient(CONTAINER);
    await container.createIfNotExists();
    const blob = container.getBlockBlobClient(BLOB);
    if (!(await blob.exists())) return {};
    const download = await blob.downloadToBuffer();
    return normalizeCache(JSON.parse(download.toString("utf8")));
  } catch (error) {
    console.error("FX cache load failed; local fallback used", error);
    return normalizeCache(await loadLocal());
  }
}

export async function saveFxCache(cache) {
  const normalized = normalizeCache(cache);
  const body = JSON.stringify(normalized, null, 2);
  const connection = process.env.AzureWebJobsStorage;
  if (!connection || connection === "UseDevelopmentStorage=true") return saveLocal(normalized);
  try {
    const { BlobServiceClient } = await import("@azure/storage-blob");
    const service = BlobServiceClient.fromConnectionString(connection);
    const container = service.getContainerClient(CONTAINER);
    await container.createIfNotExists();
    const blob = container.getBlockBlobClient(BLOB);
    await blob.upload(body, Buffer.byteLength(body), {
      blobHTTPHeaders: { blobContentType: "application/json" },
      overwrite: true
    });
  } catch (error) {
    console.error("FX cache save failed; local fallback used", error);
    await saveLocal(normalized);
  }
}

export function cacheFromFxRates(rates, cachedAt = new Date().toISOString()) {
  const result = {};
  for (const [currency, detail] of rates.entries()) {
    if (!Number.isFinite(detail?.rate) || detail.rate <= 0) continue;
    result[currency] = normalizeEntry({ ...detail, cachedAt });
  }
  return result;
}

export function mergeFxRatesWithCache(rates, cache) {
  const merged = new Map(rates);
  for (const [currency, current] of rates.entries()) {
    if (Number.isFinite(current?.rate) && current.rate > 0) continue;
    const cached = normalizeEntry(cache?.[currency]);
    if (!cached) continue;
    merged.set(currency, {
      ...cached,
      source: `Cache · ${cached.source || "letzter FX-Kurs"}`,
      delayed: true,
      error: null
    });
  }
  return merged;
}

function normalizeCache(value) {
  if (!value || typeof value !== "object" || Array.isArray(value)) return {};
  const result = {};
  for (const [currency, entry] of Object.entries(value)) {
    const normalized = normalizeEntry(entry);
    if (normalized) result[String(currency).toUpperCase()] = normalized;
  }
  return result;
}

function normalizeEntry(entry) {
  if (!entry || typeof entry !== "object") return null;
  const rate = Number(entry.rate);
  if (!Number.isFinite(rate) || rate <= 0) return null;
  return {
    rate,
    source: String(entry.source ?? ""),
    delayed: Boolean(entry.delayed),
    asOf: entry.asOf == null ? null : String(entry.asOf),
    error: null,
    cachedAt: entry.cachedAt == null ? undefined : String(entry.cachedAt)
  };
}

async function loadLocal() {
  try { return JSON.parse(await fs.readFile(localPath(), "utf8")); }
  catch { return {}; }
}
async function saveLocal(cache) { await fs.writeFile(localPath(), JSON.stringify(cache, null, 2), "utf8"); }
function localPath() { return path.join(os.tmpdir(), "investment-radar-fx-cache.json"); }
