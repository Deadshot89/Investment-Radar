import fs from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";
import { loadSheetConfig } from "./sheets.mjs";

const here = path.dirname(fileURLToPath(import.meta.url));
const configPath = path.resolve(here, "../../data/investments.json");

export function loadLocalConfig() {
  return JSON.parse(fs.readFileSync(configPath, "utf8"));
}

export async function loadConfig() {
  const local = loadLocalConfig();
  try {
    const sheet = await loadSheetConfig(local);
    if (sheet) return validate(sheet);
  } catch (error) {
    console.error("Google Sheet sync failed; local config used", error);
  }
  return validate(local);
}

function validate(parsed) {
  const total = parsed.items.reduce((sum, item) => sum + Math.max(0, item.allocation), 0);
  if (total !== parsed.budget) throw new Error(`Allocation ${total} does not match budget ${parsed.budget}`);
  return parsed;
}
