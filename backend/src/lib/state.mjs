import fs from "node:fs/promises";
import path from "node:path";
import os from "node:os";
import { BlobServiceClient } from "@azure/storage-blob";

const EMPTY = { fingerprints: {}, recent: [] };
const CONTAINER = "investment-radar";
const BLOB = "alert-state.json";

export async function loadState() {
  const connection = process.env.AzureWebJobsStorage;
  if (!connection || connection === "UseDevelopmentStorage=true") return loadLocal();
  try {
    const service = BlobServiceClient.fromConnectionString(connection);
    const container = service.getContainerClient(CONTAINER);
    await container.createIfNotExists();
    const blob = container.getBlockBlobClient(BLOB);
    if (!(await blob.exists())) return structuredClone(EMPTY);
    const download = await blob.downloadToBuffer();
    return JSON.parse(download.toString("utf8"));
  } catch {
    return loadLocal();
  }
}

export async function saveState(state) {
  const connection = process.env.AzureWebJobsStorage;
  if (!connection || connection === "UseDevelopmentStorage=true") return saveLocal(state);
  try {
    const service = BlobServiceClient.fromConnectionString(connection);
    const container = service.getContainerClient(CONTAINER);
    await container.createIfNotExists();
    const blob = container.getBlockBlobClient(BLOB);
    const body = JSON.stringify(state, null, 2);
    await blob.upload(body, Buffer.byteLength(body), { blobHTTPHeaders: { blobContentType: "application/json" } });
  } catch {
    await saveLocal(state);
  }
}

async function loadLocal() {
  try { return JSON.parse(await fs.readFile(localPath(), "utf8")); }
  catch { return structuredClone(EMPTY); }
}
async function saveLocal(state) { await fs.writeFile(localPath(), JSON.stringify(state, null, 2), "utf8"); }
function localPath() { return path.join(os.tmpdir(), "investment-radar-alert-state.json"); }
