import fs from "node:fs/promises";
import path from "node:path";
import os from "node:os";
import { BlobServiceClient } from "@azure/storage-blob";

const EMPTY = { activeFingerprints: [], recent: [], previousScores: {}, previousRecommendations: {} };
const CONTAINER = "investment-radar";
const BLOB = "alert-state.json";

export async function loadState() {
  const connection = process.env.AzureWebJobsStorage;
  if (!connection || connection === "UseDevelopmentStorage=true") return normalizeState(await loadLocal());
  try {
    const service = BlobServiceClient.fromConnectionString(connection);
    const container = service.getContainerClient(CONTAINER);
    await container.createIfNotExists();
    const blob = container.getBlockBlobClient(BLOB);
    if (!(await blob.exists())) return structuredClone(EMPTY);
    const download = await blob.downloadToBuffer();
    return normalizeState(JSON.parse(download.toString("utf8")));
  } catch {
    return normalizeState(await loadLocal());
  }
}

export async function saveState(state) {
  const normalized = normalizeState(state);
  const connection = process.env.AzureWebJobsStorage;
  if (!connection || connection === "UseDevelopmentStorage=true") return saveLocal(normalized);
  try {
    const service = BlobServiceClient.fromConnectionString(connection);
    const container = service.getContainerClient(CONTAINER);
    await container.createIfNotExists();
    const blob = container.getBlockBlobClient(BLOB);
    const body = JSON.stringify(normalized, null, 2);
    await blob.upload(body, Buffer.byteLength(body), { blobHTTPHeaders: { blobContentType: "application/json" }, overwrite: true });
  } catch {
    await saveLocal(normalized);
  }
}

export function normalizeState(value) {
  const recent = Array.isArray(value?.recent) ? value.recent : [];
  let activeFingerprints = Array.isArray(value?.activeFingerprints) ? value.activeFingerprints : [];
  if (!activeFingerprints.length && value?.fingerprints && typeof value.fingerprints === "object") {
    activeFingerprints = Object.values(value.fingerprints).filter(Boolean);
  }
  return {
    activeFingerprints: [...new Set(activeFingerprints)],
    recent: recent.slice(0, 50),
    previousScores: normalizeNumberMap(value?.previousScores),
    previousRecommendations: normalizeStringMap(value?.previousRecommendations)
  };
}

function normalizeNumberMap(value) {
  if (!value || typeof value !== "object" || Array.isArray(value)) return {};
  return Object.fromEntries(Object.entries(value)
    .map(([key, raw]) => [key, Number(raw)])
    .filter(([, number]) => Number.isFinite(number)));
}
function normalizeStringMap(value) {
  if (!value || typeof value !== "object" || Array.isArray(value)) return {};
  return Object.fromEntries(Object.entries(value)
    .map(([key, raw]) => [key, String(raw ?? "").toUpperCase()])
    .filter(([, text]) => text));
}
async function loadLocal() {
  try { return JSON.parse(await fs.readFile(localPath(), "utf8")); }
  catch { return structuredClone(EMPTY); }
}
async function saveLocal(state) { await fs.writeFile(localPath(), JSON.stringify(state, null, 2), "utf8"); }
function localPath() { return path.join(os.tmpdir(), "investment-radar-alert-state.json"); }
