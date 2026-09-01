import { loadEurRateDetails, loadQuotes, priceInEur } from './market.mjs';

function slug(value) {
  return String(value ?? '').trim().toLowerCase().replace(/[^a-z0-9._-]+/g, '-').replace(/^-+|-+$/g, '').slice(0, 80);
}

export function normalizeCustomAssetInput(raw = {}) {
  const ticker = String(raw.ticker ?? '').trim().toUpperCase();
  if (!ticker) throw new Error('Ticker fehlt');
  const isin = String(raw.isin ?? '').trim().toUpperCase();
  const name = String(raw.name ?? ticker).trim() || ticker;
  const typeRaw = String(raw.type ?? 'Aktie').trim().toLowerCase();
  const type = typeRaw === 'etf' ? 'ETF' : 'Aktie';
  const id = String(raw.id ?? '').trim() || `custom-${slug(isin || ticker)}`;
  return {
    id,
    type,
    name,
    ticker,
    isin,
    tradeRepublicName: String(raw.tradeRepublicName ?? name).trim() || name,
    marketSymbol: ticker,
    yahooSymbol: ticker,
    status: 'EIGEN',
    allocation: 0,
    risk: Math.min(5, Math.max(1, Number(raw.risk) || 3))
  };
}

export function customAssetPayload(item, quote, fxDetail = null) {
  const currency = String(quote?.currency ?? '').trim().toUpperCase();
  const fxRateToEur = currency === 'EUR' ? 1 : (fxDetail?.rate ?? null);
  const rates = new Map();
  if (currency && currency !== 'EUR' && Number.isFinite(fxRateToEur)) rates.set(currency, fxRateToEur);
  return {
    id: item.id,
    type: item.type,
    name: item.name,
    ticker: item.ticker,
    isin: item.isin,
    tradeRepublicName: item.tradeRepublicName,
    status: 'EIGEN',
    allocation: 0,
    risk: item.risk,
    price: quote?.price ?? null,
    priceEur: priceInEur(quote, rates),
    currency,
    fxRateToEur,
    fxSource: fxDetail?.source ?? '',
    fxDelayed: Boolean(fxDetail?.delayed),
    fxAsOf: fxDetail?.asOf ?? null,
    percentChange: quote?.percentChange ?? null,
    marketOpen: quote?.marketOpen ?? null,
    dataSource: quote?.source ?? '',
    dataDelayed: Boolean(quote?.delayed),
    dataError: quote?.error ?? null
  };
}

export async function resolveCustomAssetQuote(raw) {
  const item = normalizeCustomAssetInput(raw);
  const quotes = await loadQuotes([item]);
  const quote = quotes.get(item.id);
  const fxDetails = await loadEurRateDetails(quotes);
  const currency = String(quote?.currency ?? '').trim().toUpperCase();
  const fxDetail = currency && currency !== 'EUR' ? fxDetails.get(currency) : null;
  return customAssetPayload(item, quote, fxDetail);
}
