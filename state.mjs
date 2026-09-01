import fs from "node:fs/promises";
import path from "node:path";
import os from "node:os";
import { BlobServiceClient } from "@azure/storage-blob";

const EMPTY = { activeFingerprints: [], recent: [] };
const CONTAINER = "investment-radar";
const BLOB = "alert-state.json";

export async function loadState() {
  const connection = process.env.AzureWebJobsStorage;
  if (!connection || connection === "UseDevelopmentStorage=true") return normalize(await loadLocal());
  try {
    const service = BlobServiceClient.fromConnectionString(connection);
    const container = service.getContainerClient(CONTAINER);
    await container.createIfNotExists();
    const blob = container.getBlockBlobClient(BLOB);
    if (!(await blob.exists())) return structuredClone(EMPTY);
    const download = await blob.downloadToBuffer();
    return normalize(JSON.parse(download.toString("utf8")));
  } catch {
    return normalize(await loadLocal());
  }
}

export async function saveState(state) {
  const normalized = normalize(state);
  const connection = process.env.AzureWebJobsStorage;
  if (!connection || connection === "UseDevelopmentStorage=true") return saveLocal(normalized);
  try {
    const service = BlobServiceClient.fromConnectionString(connection);
    const container = service.getContainerClient(CONTAINER);
    await container.createIfNotExists();
    const blob = container.getBlockBlobClient(BLOB);
    const body = JSON.stringify(normalized, null, 2);
    await blob.upload(body, Buffer.byteLength(body), { blobHTTPHeaders: { blobContentType: "application/json" } });
  } catch {
    await saveLocal(normalized);
  }
}

function normalize(value) {
  const recent = Array.isArray(value?.recent) ? value.recent : [];
  let activeFingerprints = Array.isArray(value?.activeFingerprints) ? value.activeFingerprints : [];
  // Migration from v1.0 state format.
  if (!activeFingerprints.length && value?.fingerprints && typeof value.fingerprints === "object") {
    activeFingerprints = Object.values(value.fingerprints).filter(Boolean);
  }
  return { activeFingerprints: [...new Set(activeFingerprints)], recent: recent.slice(0, 50) };
}

async function loadLocal() {
  try { return JSON.parse(await fs.readFile(localPath(), "utf8")); }
  catch { return structuredClone(EMPTY); }
}
async function saveLocal(state) { await fs.writeFile(localPath(), JSON.stringify(state, null, 2), "utf8"); }
function localPath() { return path.join(os.tmpdir(), "investment-radar-alert-state.json"); }
