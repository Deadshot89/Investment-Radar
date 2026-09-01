import fs from "node:fs/promises";
import os from "node:os";
import path from "node:path";

const CONTAINER = "investment-radar";
const BLOB = "quote-cache.json";
const EMPTY = {};

export async function loadQuoteCache() {
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
    console.error("Quote cache load failed; local fallback used", error);
    return normalizeCache(await loadLocal());
  }
}

export async function saveQuoteCache(cache) {
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
    console.error("Quote cache save failed; local fallback used", error);
    await saveLocal(normalized);
  }
}

export function cacheFromQuotes(quotes, cachedAt = new Date().toISOString()) {
  const result = {};
  for (const [id, quote] of quotes.entries()) {
    if (!Number.isFinite(quote?.price)) continue;
    result[id] = normalizeEntry({ ...quote, cachedAt });
  }
  return result;
}

export function mergeQuotesWithCache(quotes, cache) {
  const merged = new Map(quotes);
  for (const [id, current] of quotes.entries()) {
    if (Number.isFinite(current?.price)) continue;
    const cached = normalizeEntry(cache?.[id]);
    if (!cached) continue;
    merged.set(id, {
      ...cached,
      marketOpen: null,
      delayed: true,
      source: `Cache · ${cached.source || "letzter Kurs"}`,
      error: null
    });
  }
  return merged;
}

function normalizeCache(value) {
  if (!value || typeof value !== "object" || Array.isArray(value)) return structuredClone(EMPTY);
  const result = {};
  for (const [id, entry] of Object.entries(value)) {
    const normalized = normalizeEntry(entry);
    if (normalized) result[id] = normalized;
  }
  return result;
}

function normalizeEntry(entry) {
  if (!entry || typeof entry !== "object") return null;
  const price = Number(entry.price);
  if (!Number.isFinite(price)) return null;
  const percentChange = entry.percentChange == null ? null : Number(entry.percentChange);
  const timestamp = entry.timestamp == null ? undefined : Number(entry.timestamp);
  return {
    symbol: String(entry.symbol ?? ""),
    name: entry.name == null ? undefined : String(entry.name),
    price,
    currency: String(entry.currency ?? ""),
    percentChange: Number.isFinite(percentChange) ? percentChange : null,
    marketOpen: typeof entry.marketOpen === "boolean" ? entry.marketOpen : null,
    timestamp: Number.isFinite(timestamp) ? timestamp : undefined,
    source: String(entry.source ?? ""),
    delayed: Boolean(entry.delayed),
    error: null,
    cachedAt: entry.cachedAt == null ? undefined : String(entry.cachedAt)
  };
}

async function loadLocal() {
  try { return JSON.parse(await fs.readFile(localPath(), "utf8")); }
  catch { return {}; }
}
async function saveLocal(cache) { await fs.writeFile(localPath(), JSON.stringify(cache, null, 2), "utf8"); }
function localPath() { return path.join(os.tmpdir(), "investment-radar-quote-cache.json"); }
