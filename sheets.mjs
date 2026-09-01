import { google } from "googleapis";

export async function loadSheetConfig(localConfig) {
  const spreadsheetId = process.env.GOOGLE_SHEET_ID?.trim();
  const rawCredentials = (process.env.GOOGLE_SERVICE_ACCOUNT_JSON || process.env.FIREBASE_SERVICE_ACCOUNT_JSON)?.trim();
  if (!spreadsheetId || !rawCredentials) return null;

  const credentials = JSON.parse(rawCredentials);
  const auth = new google.auth.GoogleAuth({
    credentials,
    scopes: ["https://www.googleapis.com/auth/spreadsheets.readonly"]
  });
  const sheets = google.sheets({ version: "v4", auth });
  const response = await sheets.spreadsheets.values.batchGet({
    spreadsheetId,
    ranges: ["Rangliste!A2:S50", "Dashboard!A1:B12"]
  });

  const rankingRows = response.data.valueRanges?.[0]?.values ?? [];
  const dashboardRows = response.data.valueRanges?.[1]?.values ?? [];
  const localByIsin = new Map(localConfig.items.map((item) => [item.isin, item]));

  const items = rankingRows
    .filter((row) => row[2] && row[15])
    .map((row, index) => {
      const type = String(row[1] ?? "AKTIE").toUpperCase() === "ETF" ? "ETF" : "AKTIE";
      const name = String(row[2] ?? "").trim();
      const tickerRaw = String(row[3] ?? "").split("·")[0].trim();
      const isin = String(row[15] ?? "").trim();
      const existing = localByIsin.get(isin);
      const ticker = tickerRaw || existing?.ticker || isin;
      return {
        id: existing?.id || slug(`${ticker}-${isin}`).slice(0, 40),
        type,
        name,
        ticker,
        marketSymbol: existing?.marketSymbol || inferMarketSymbol(type, ticker),
        yahooSymbol: existing?.yahooSymbol || inferYahooSymbol(type, ticker),
        isin,
        tradeRepublicName: String(row[14] ?? name).trim(),
        status: normalizeStatus(row[12]),
        allocation: integer(row[13]),
        risk: clamp(integer(row[11], 3), 1, 5),
        alertStatus: normalizeAlertStatus(row[16]),
        alertReason: String(row[17] ?? "").trim(),
        alertUpdatedAt: String(row[18] ?? "").trim(),
        reviewDrop1dPct: existing?.reviewDrop1dPct ?? (type === "ETF" ? 6 : 8),
        hardReviewBelow: existing?.hardReviewBelow,
        rank: integer(row[0], index + 1)
      };
    })
    .sort((a, b) => a.rank - b.rank)
    .map(({ rank: _rank, ...item }) => item);

  if (!items.length) return null;
  const marketLight = parseMarketLight(dashboardRows) || localConfig.marketLight;
  const budget = items.reduce((sum, item) => sum + Math.max(0, item.allocation), 0) || localConfig.budget;
  const topPickId = items[0].id;
  return { marketLight, budget, topPickId, items };
}

function inferMarketSymbol(type, ticker) {
  if (type === "ETF" && /^[A-Z0-9]{3,6}$/.test(ticker)) return `${ticker}:XETR`;
  return ticker;
}
function inferYahooSymbol(type, ticker) {
  if (type === "ETF" && /^[A-Z0-9]{3,6}$/.test(ticker)) return `${ticker}.DE`;
  return undefined;
}
function normalizeStatus(value) {
  const v = String(value ?? "BEOBACHTEN").trim().toUpperCase().replaceAll("Ü", "UE");
  if (v.includes("VERKAUF")) return "VERKAUFEN";
  if (v.includes("DRINGEND")) return "DRINGEND_PRUEFEN";
  if (v.includes("KAUF")) return "KAUFEN";
  if (v.includes("MEID")) return "MEIDEN";
  return "BEOBACHTEN";
}

function normalizeAlertStatus(value) {
  const v = String(value ?? "").trim().toUpperCase().replaceAll("Ü", "UE");
  if (v.includes("VERKAUF")) return "VERKAUFEN";
  if (v.includes("DRINGEND") || v.includes("PRUEF")) return "DRINGEND_PRUEFEN";
  return "";
}

function parseMarketLight(rows) {
  for (const row of rows) {
    for (const cell of row) {
      const value = String(cell ?? "").toUpperCase();
      if (!value.includes("MARKT")) continue;
      if (value.includes("GRÜN") || value.includes("GRUEN")) return "GRÜN";
      if (value.includes("ROT")) return "ROT";
      if (value.includes("GELB")) return "GELB";
    }
  }
  return null;
}
function integer(value, fallback = 0) {
  const n = Number(String(value ?? "").replace(",", "."));
  return Number.isFinite(n) ? Math.round(n) : fallback;
}
function clamp(n, min, max) { return Math.max(min, Math.min(max, n)); }
function slug(s) { return s.toLowerCase().replace(/[^a-z0-9]+/g, "-").replace(/^-|-$/g, ""); }
