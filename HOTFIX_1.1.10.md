# Investment Radar 1.1.10

## Android-Verbindung stabilisiert

- Azure-Flex-Cold-Starts bekommen mehr Zeit: 30 s Connect-Timeout, 45 s Read-Timeout.
- Bei einem echten Socket-Timeout erfolgt genau ein automatischer zweiter Versuch.
- Nach zwei Timeouts erscheint eine verständliche Meldung statt nur `timeout`.
- Wenn bereits Dashboard-Daten angezeigt werden, bleiben sie bei einem fehlgeschlagenen Refresh sichtbar.
- Bestehendes UI/Design und Backend wurden nicht verändert.
- Version: `versionCode 11`, `versionName 1.1.10`.
