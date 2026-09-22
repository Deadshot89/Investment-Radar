# Investment Radar Backend

**Runtime:** Azure Functions v4 / Node.js 22  
**Backend-Version:** 2.1.0  
**API-Schema:** 2026-09-14.1

## HTTP-Endpunkte

- `GET /api/health` – Laufzeit-, Konfigurations- und Release-Identität
- `GET /api/dashboard` – Dashboard-/Analyse-Daten
- `GET /api/radar` – Radar-Suche, Filter und Sortierung
- `GET /api/instrument/{id}` – Detaildaten eines Instruments
- `GET /api/custom-quote` – Kursauflösung für eigene Werte
- `GET /api/market-events` – Markt-/Unternehmensereignisse
- `POST /api/test-push` – administrativer Push-Test mit Header `x-admin-key`

## Timer

`marketWatch` läuft mit dem Azure-Schedule:

```text
0 */5 * * * *
```

Das entspricht **alle 5 Minuten**. Frische Quotes werden dabei geladen, Signale ausgewertet und neue Push-Ereignisse versendet. Langsamere Historien-/Fundamentaldaten bleiben an ihre jeweilige Cache-Policy gebunden.

## Konfiguration

Wesentliche Azure App Settings:
- `TWELVE_DATA_API_KEY`
- `FIREBASE_SERVICE_ACCOUNT_JSON`
- `AzureWebJobsStorage`

Optional:
- `ALERT_TOPIC`
- `ADMIN_API_KEY`
- `GOOGLE_SHEET_ID`
- `GOOGLE_SERVICE_ACCOUNT_JSON`

## Sicherheits- und Fachgrenzen

Das Backend liefert Marktdaten, Analysen, Signale und Push-Benachrichtigungen. Es führt **keine Brokerage-Orders** aus.

`/api/test-push` ist nur mit korrekt gesetztem `ADMIN_API_KEY` und passendem `x-admin-key` nutzbar.
