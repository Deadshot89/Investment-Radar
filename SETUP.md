# Technische Einrichtung

Die einfache Schritt-fuer-Schritt-Anleitung steht in `START_HIER.md`.

## Backend App Settings

Pflicht:
- `TWELVE_DATA_API_KEY`
- `FIREBASE_SERVICE_ACCOUNT_JSON`
- `AzureWebJobsStorage`

Google-Sheet-Sync:
- `GOOGLE_SHEET_ID=1unFY1i2X_mEYoxYKaP42hDPTl1lmsYhNdhj7Cg6W7xI`
- optional `GOOGLE_SERVICE_ACCOUNT_JSON`

Administration:
- `ADMIN_API_KEY`
- optional `ALERT_TOPIC=investment-alerts`

## Android Build Properties / GitHub Secrets

- `INVESTMENT_API_BASE_URL`
- `FIREBASE_APP_ID`
- `FIREBASE_API_KEY`
- `FIREBASE_PROJECT_ID`
- `FIREBASE_SENDER_ID`

## Push-Topics

- `investment-alerts`: allgemeine Test-/Informationsmeldungen
- `holding-<itemId>`: gezielte Verkaufs-/Pruefalarme fuer eine im App-Depot markierte Position

## Timer

`marketWatch` laeuft standardmaessig alle 15 Minuten.

## Signalregeln v1.1

- Google-Sheet Alarmstatus `VERKAUFEN`
- Google-Sheet Alarmstatus `DRINGEND_PRUEFEN`
- Kurs unter `hardReviewBelow`
- Tagesverlust groesser/gleich `reviewDrop1dPct`

Deduplizierung: Ein aktives Signal wird genau einmal gepusht. Verschwindet die Bedingung, wird das Signal fuer spaetere Wiederholungen erneut freigeschaltet.
