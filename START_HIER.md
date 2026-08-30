# START HIER – Investment Radar Live 1.1.0

Fuer die echte Live-Version brauchst du drei externe Bausteine. Die App-Dateien selbst sind bereits vorbereitet.

## A. Firebase fuer Push

1. Firebase Console oeffnen und ein neues Projekt anlegen, z. B. `investment-radar-live`.
2. Android-App hinzufuegen mit Paketname:
   `de.tobias.investmentradar`
3. Unter Projekteinstellungen die Werte fuer Android notieren:
   - App ID -> `FIREBASE_APP_ID`
   - Web API Key -> `FIREBASE_API_KEY`
   - Project ID -> `FIREBASE_PROJECT_ID`
   - Project number / Sender ID -> `FIREBASE_SENDER_ID`
4. Unter Projekteinstellungen -> Dienstkonten einen privaten Service-Account-Schluessel als JSON erzeugen.
   Dieses JSON niemals ins Repository legen. Es kommt spaeter als Azure App Setting `FIREBASE_SERVICE_ACCOUNT_JSON` hinein.

Die App initialisiert Firebase programmgesteuert; `google-services.json` muss deshalb nicht ins Repository eingecheckt werden.

## B. Marktdaten

1. Twelve Data Konto anlegen.
2. API-Key erzeugen.
3. Spaeter in Azure als `TWELVE_DATA_API_KEY` speichern.

## C. Azure Function App

Eine Node.js Azure Function App mit Functions Runtime 4 und Node.js 22 oder 24 anlegen.

App Settings:
- `TWELVE_DATA_API_KEY`
- `FIREBASE_SERVICE_ACCOUNT_JSON`
- `AzureWebJobsStorage`
- `GOOGLE_SHEET_ID=1unFY1i2X_mEYoxYKaP42hDPTl1lmsYhNdhj7Cg6W7xI`
- optional `GOOGLE_SERVICE_ACCOUNT_JSON` (wenn nicht derselbe Service Account wie Firebase genutzt wird)
- optional `ALERT_TOPIC=investment-alerts`
- `ADMIN_API_KEY=<langes-zufaelliges-passwort>`

Die Google-Tabelle muss dem verwendeten Service-Account-E-Mailkonto mindestens als **Leser** freigegeben werden. Der Server liest:
- `Rangliste!A:S`
- Dashboard-Marktampel

Die Spalten Q:S der Rangliste sind fuer die Live-App reserviert:
- Q = Alarmstatus
- R = Alarmgrund
- S = Alarmstand

## D. GitHub Secrets fuer die Android-App

Repository -> Settings -> Secrets and variables -> Actions:
- `INVESTMENT_API_BASE_URL` = z. B. `https://DEINE-APP.azurewebsites.net`
- `FIREBASE_APP_ID`
- `FIREBASE_API_KEY`
- `FIREBASE_PROJECT_ID`
- `FIREBASE_SENDER_ID`

Fuer Backend-Deploy zusaetzlich:
- Secret `AZURE_FUNCTIONAPP_PUBLISH_PROFILE`
- Variable `AZURE_FUNCTIONAPP_NAME`

## E. Reihenfolge

1. Workflow **Deploy Backend** starten.
2. Im Browser testen: `https://DEINE-APP.azurewebsites.net/api/health`
3. Danach Workflow **Build Android APK** starten.
4. Artefakt `investment-radar-apk` herunterladen.
5. APK auf dem Android-Handy installieren.
6. Benachrichtigungen erlauben.
7. Radar oeffnen und gekaufte Werte mit **Als gekauft markieren** ins Depot aufnehmen.

## So funktionieren Verkaufsalarme

Es gibt zwei Alarmwege:

1. **Server-Kursregeln alle 15 Minuten**
   - ungewoehnlich grosser Tagesverlust
   - definierte Pruefschwelle

2. **Fundamental-/News-Radar**
   - der Verkaufsradar pflegt Q:S im Google-Sheet
   - `DRINGEND_PRUEFEN` oder `VERKAUFEN` wird vom Azure-Backend erkannt
   - Push geht nur an Geraete, die diese Position im App-Depot markiert haben

Ein Alarm wird waehrend derselben Warnphase nur einmal gesendet. Nach Entwarnung wird er automatisch wieder scharf geschaltet.

---

## HOTFIX 1.1.4 – wichtig für GitHub Actions

Falls du zuvor `Failed to find package 'platforms;android-37'` gesehen hast: behoben. Die App verwendet jetzt Android API 36.

Falls du zuvor `app-name should not be empty` beim Azure-Deploy gesehen hast: behoben. Ohne Azure-Konfiguration wird der Deploy jetzt sauber übersprungen.

Für einen echten Azure-Deploy später in GitHub unter `Settings > Secrets and variables > Actions` anlegen:

- **Variable:** `AZURE_FUNCTIONAPP_NAME`
- **Secret:** `AZURE_FUNCTIONAPP_PUBLISH_PROFILE`

Siehe auch `HOTFIX_1.1.4.md`.
