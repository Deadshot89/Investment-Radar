import fs from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";

export const API_SCHEMA_VERSION = "2026-09-14.1";

const here = path.dirname(fileURLToPath(import.meta.url));
const defaultPath = path.resolve(here, "../../build-info.json");

export function loadBuildInfo(filePath = defaultPath) {
  try {
    const parsed = JSON.parse(fs.readFileSync(filePath, "utf8"));
    return {
      sourceRevision: clean(parsed?.sourceRevision) || "development",
      deployId: clean(parsed?.deployId) || "development",
      apiSchemaVersion: API_SCHEMA_VERSION
    };
  } catch {
    return {
      sourceRevision: "development",
      deployId: "development",
      apiSchemaVersion: API_SCHEMA_VERSION
    };
  }
}

function clean(value) {
  return String(value ?? "").trim();
}
