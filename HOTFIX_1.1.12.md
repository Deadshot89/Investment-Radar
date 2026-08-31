# Hotfix 1.1.12

## Trade Republic direkt öffnen

- Entfernt die `resolveActivity()`-Vorprüfung, die auf Android 11+ durch Package-Visibility trotz installierter Trade-Republic-App fehlschlagen kann.
- Startet `de.traderepublic.app` direkt mit dem ISIN-Link:
  `https://app.traderepublic.com/instrument/{ISIN}?timeframe=1d`
- Nur wenn Android `ActivityNotFoundException` meldet, wird derselbe Trade-Republic-Link im Browser geöffnet.
- Kein Google-Fallback.
- Dark Design bleibt unverändert.
- Timeout-/Retry-Fix aus 1.1.10 bleibt enthalten.
- Version: `1.1.12`, `versionCode 13`.
