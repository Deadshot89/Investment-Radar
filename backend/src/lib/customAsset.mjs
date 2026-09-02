import { loadEurRateDetails, loadQuotes, priceInEur } from './market.mjs';

const KNOWN_SYMBOLS_BY_ISIN = new Map([['US30303M1027', 'META']]);

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
    marketSymbol: KNOWN_SYMBOLS_BY_ISIN.get(isin) || ticker,
    yahooSymbol: KNOWN_SYMBOLS_BY_ISIN.get(isin) || ticker,
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
  const quoteCandidates = [...new Set([
    KNOWN_SYMBOLS_BY_ISIN.get(item.isin),
    item.ticker,
    `${item.ticker}.DE`,
    `${item.ticker}.F`
  ].filter(Boolean))];

  let selectedQuote = null;
  for (const symbol of quoteCandidates) {
    const candidate = { ...item, marketSymbol: symbol, yahooSymbol: symbol };
    const candidateQuotes = await loadQuotes([candidate]);
    const quote = candidateQuotes.get(candidate.id);
    selectedQuote = quote;
    if (quote?.price != null) break;
  }

  const quoteMap = new Map([[item.id, selectedQuote]]);
  const fxDetails = await loadEurRateDetails(quoteMap);
  const currency = String(selectedQuote?.currency ?? '').trim().toUpperCase();
  const fxDetail = currency && currency !== 'EUR' ? fxDetails.get(currency) : null;
  return customAssetPayload(item, selectedQuote, fxDetail);
}
