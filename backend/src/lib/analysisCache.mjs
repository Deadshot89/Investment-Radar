import fs from "node:fs/promises";
import os from "node:os";
import path from "node:path";

const CONTAINER = "investment-radar";

export async function loadAnalysisCache(name) {
  const blobName = safeName(name);
  const connection = process.env.AzureWebJobsStorage;
  if (!connection || connection === "UseDevelopmentStorage=true") return loadLocal(blobName);
  try {
    const { BlobServiceClient } = await import("@azure/storage-blob");
    const service = BlobServiceClient.fromConnectionString(connection);
    const container = service.getContainerClient(CONTAINER);
    await container.createIfNotExists();
    const blob = container.getBlockBlobClient(blobName);
    if (!(await blob.exists())) return {};
    const download = await blob.downloadToBuffer();
    return normalizeObject(JSON.parse(download.toString("utf8")));
  } catch (error) {
    console.error(`Analysis cache ${blobName} load failed`, error);
    return loadLocal(blobName);
  }
}

export async function saveAnalysisCache(name, value) {
  const blobName = safeName(name);
  const normalized = normalizeObject(value);
  const body = JSON.stringify(normalized, null, 2);
  const connection = process.env.AzureWebJobsStorage;
  if (!connection || connection === "UseDevelopmentStorage=true") return saveLocal(blobName, normalized);
  try {
    const { BlobServiceClient } = await import("@azure/storage-blob");
    const service = BlobServiceClient.fromConnectionString(connection);
    const container = service.getContainerClient(CONTAINER);
    await container.createIfNotExists();
    const blob = container.getBlockBlobClient(blobName);
    await blob.upload(body, Buffer.byteLength(body), {
      overwrite: true,
      blobHTTPHeaders: { blobContentType: "application/json" }
    });
  } catch (error) {
    console.error(`Analysis cache ${blobName} save failed`, error);
    await saveLocal(blobName, normalized);
  }
}

export function isFresh(entry, maxAgeMs, now = Date.now()) {
  const fetchedAt = Date.parse(String(entry?.fetchedAt ?? ""));
  return Number.isFinite(fetchedAt) && now - fetchedAt >= 0 && now - fetchedAt <= maxAgeMs;
}

function safeName(name) {
  const clean = String(name ?? "analysis").replace(/[^a-z0-9._-]+/gi, "-").replace(/^-+|-+$/g, "");
  return `${clean || "analysis"}.json`;
}
function normalizeObject(value) { return value && typeof value === "object" && !Array.isArray(value) ? value : {}; }
async function loadLocal(blobName) {
  try { return normalizeObject(JSON.parse(await fs.readFile(localPath(blobName), "utf8"))); }
  catch { return {}; }
}
async function saveLocal(blobName, value) { await fs.writeFile(localPath(blobName), JSON.stringify(value, null, 2), "utf8"); }
function localPath(blobName) { return path.join(os.tmpdir(), `investment-radar-${blobName}`); }
