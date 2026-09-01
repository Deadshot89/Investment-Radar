import test from 'node:test';
import assert from 'node:assert/strict';
import { normalizeYahooChart, convertPriceToEur } from '../backend/src/lib/marketSupport.mjs';

test('normalizeYahooChart returns an EUR Xetra ETF quote', () => {
  const payload = {
    chart: {
      result: [{
        meta: {
          symbol: 'SPYI.DE',
          currency: 'EUR',
          regularMarketPrice: 11.52,
          chartPreviousClose: 11.40,
          regularMarketTime: 1788252000,
          marketState: 'REGULAR'
        }
      }],
      error: null
    }
  };
  const quote = normalizeYahooChart(payload, 'SPYI.DE');
  assert.equal(quote.price, 11.52);
  assert.equal(quote.currency, 'EUR');
  assert.equal(quote.source, 'Yahoo Finance');
  assert.equal(quote.delayed, true);
  assert.ok(quote.percentChange > 1.0 && quote.percentChange < 1.1);
});

test('convertPriceToEur converts USD using USD/EUR rate and leaves EUR unchanged', () => {
  assert.equal(convertPriceToEur(507.29, 'USD', new Map([['USD', 0.86]])), 436.2694);
  assert.equal(convertPriceToEur(70.35, 'EUR', new Map()), 70.35);
  assert.equal(convertPriceToEur(70.35, 'GBP', new Map()), null);
});
