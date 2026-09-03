import fs from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";
import { loadSheetConfig } from "./sheets.mjs";

const here = path.dirname(fileURLToPath(import.meta.url));
const configPath = path.resolve(here, "../../data/investments.json");
const ISIN = /^[A-Z]{2}[A-Z0-9]{9}[0-9]$/;

const PORTFOLIO_LIVE_ASSETS = Object.freeze([
  {
    id: "custom-nel-asa",
    type: "AKTIE",
    name: "Nel ASA",
    ticker: "NEL.OL",
    marketSymbol: "NEL:OSLO",
    isin: "NO0010081235",
    tradeRepublicName: "Nel ASA",
    status: "BEOBACHTEN",
    allocation: 0,
    risk: 5,
    reviewDrop1dPct: 10,
    yahooSymbol: "NEL.OL",
    portfolioOnly: true,
    alertStatus: "REVIEW",
    alertReason: "Nur Portfolio-Tracking; keine automatische Kaufempfehlung"
  },
  {
    id: "custom-samsung-gdr",
    type: "AKTIE",
    name: "Samsung Electronics GDR",
    ticker: "SMSN",
    marketSymbol: "SMSN:LSE",
    isin: "US7960508882",
    tradeRepublicName: "Samsung (GDR)",
    status: "BEOBACHTEN",
    allocation: 0,
    risk: 3,
    reviewDrop1dPct: 8,
    yahooSymbol: "SMSN.IL",
    portfolioOnly: true,
    alertStatus: "REVIEW",
    alertReason: "Nur Portfolio-Tracking; keine automatische Kaufempfehlung"
  }
]);

export function loadLocalConfig() {
  const parsed = JSON.parse(fs.readFileSync(configPath, "utf8"));
  const existingIds = new Set((parsed.items ?? []).map((item) => String(item?.id ?? "")));
  const additions = PORTFOLIO_LIVE_ASSETS.filter((item) => !existingIds.has(item.id));
  return { ...parsed, items: [...(parsed.items ?? []), ...additions] };
}

export async function loadConfig() {
  const local = validateConfig(loadLocalConfig());
  try {
    const sheet = await loadSheetConfig(local);
    if (sheet) return validateConfig(mergeSheetOverrides(local, sheet));
  } catch (error) {
    console.error("Google Sheet sync failed; local config used", error);
  }
  return local;
}

export function validateConfig(parsed) {
  if (!parsed || typeof parsed !== "object" || !Array.isArray(parsed.items) || parsed.items.length === 0) {
    throw new Error("Investment config requires items");
  }
  const seen = new Set();
  const items = parsed.items.map((item, index) => {
    const id = String(item?.id ?? "").trim();
    const type = String(item?.type ?? "").trim().toUpperCase();
    const ticker = String(item?.ticker ?? "").trim();
    const marketSymbol = String(item?.marketSymbol ?? "").trim();
    const isin = String(item?.isin ?? "").trim().toUpperCase();
    const risk = Math.round(Number(item?.risk));
    if (!id) throw new Error(`Item ${index + 1}: id missing`);
    if (seen.has(id)) throw new Error(`Duplicate investment id: ${id}`);
    seen.add(id);
    if (!new Set(["AKTIE", "ETF"]).has(type)) throw new Error(`Item ${id}: invalid type ${type}`);
    if (!ticker) throw new Error(`Item ${id}: ticker missing`);
    if (!marketSymbol) throw new Error(`Item ${id}: marketSymbol missing`);
    if (!Number.isFinite(risk) || risk < 1 || risk > 5) throw new Error(`Item ${id}: risk must be 1..5`);
    if (isin && !ISIN.test(isin)) throw new Error(`Item ${id}: invalid ISIN ${isin}`);
    return {
      ...item,
      id,
      type,
      ticker,
      marketSymbol,
      isin,
      risk,
      allocation: Number.isFinite(Number(item?.allocation)) ? Math.max(0, Math.round(Number(item.allocation))) : 0,
      status: String(item?.status ?? "BEOBACHTEN"),
      reviewDrop1dPct: Number.isFinite(Number(item?.reviewDrop1dPct)) ? Math.abs(Number(item.reviewDrop1dPct)) : (type === "ETF" ? 6 : 8)
    };
  });
  return {
    ...parsed,
    marketLight: String(parsed.marketLight ?? "GELB"),
    budget: Number.isFinite(Number(parsed.budget)) ? Math.max(0, Math.round(Number(parsed.budget))) : 100,
    topPickId: String(parsed.topPickId ?? items[0].id),
    items
  };
}

function mergeSheetOverrides(local, sheet) {
  const byIsin = new Map((sheet.items ?? []).filter((x) => x.isin).map((x) => [String(x.isin).toUpperCase(), x]));
  const byId = new Map((sheet.items ?? []).filter((x) => x.id).map((x) => [String(x.id), x]));
  const items = local.items.map((item) => {
    const override = (item.isin && byIsin.get(item.isin)) || byId.get(item.id);
    if (!override) return item;
    return {
      ...item,
      alertStatus: override.alertStatus || item.alertStatus || "",
      alertReason: override.alertReason || item.alertReason || "",
      alertUpdatedAt: override.alertUpdatedAt || item.alertUpdatedAt || "",
      reviewDrop1dPct: override.reviewDrop1dPct ?? item.reviewDrop1dPct,
      hardReviewBelow: override.hardReviewBelow ?? item.hardReviewBelow
    };
  });
  return {
    ...local,
    marketLight: sheet.marketLight || local.marketLight,
    budget: Number.isFinite(Number(sheet.budget)) && Number(sheet.budget) > 0 ? Math.round(Number(sheet.budget)) : local.budget,
    items
  };
}
