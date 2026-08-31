# Investment Radar 1.1.11

## Trade-Republic-Direktlink
- Google-Suchweiterleitung entfernt.
- Wertpapiere werden anhand ihrer ISIN geöffnet:
  `https://app.traderepublic.com/instrument/{ISIN}?timeframe=1d`
- Wenn Trade Republic installiert ist, wird die Android-App `de.traderepublic.app` bevorzugt geöffnet.
- Falls Trade Republic nicht installiert ist, wird dieselbe Trade-Republic-URL im Browser geöffnet.

## Kumulativ enthalten
- Netzwerk-Resilienz aus 1.1.10: längere Timeouts und einmaliger Retry.
- Aktuelles Dark-Design aus der vom Nutzer bereitgestellten MainActivity bleibt erhalten.

## Version
- versionCode: 12
- versionName: 1.1.11
