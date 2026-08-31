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

Für die einfachste Einrichtung mit diesem Projekt:

1. Azure Portal -> **Create a resource** -> **Function App**.
2. Hosting: **Consumption** wählen (Windows). Nicht Flex Consumption für diesen Publish-Profile-Workflow.
3. Runtime stack: **Node.js**.
4. Version: **22**.
5. Functions Runtime: **4.x**.
6. Einen weltweit eindeutigen Function-App-Namen vergeben und merken.
7. Function App erstellen.

Für den ersten Verbindungstest sind noch keine Twelve-Data-/Firebase-Schlüssel nötig. `/api/health` funktioniert bereits ohne sie; das Dashboard liefert dann zunächst die Radar-Daten ohne Live-Kurse.

Später unter Function App -> Settings/Configuration als App Settings ergänzen:
- `TWELVE_DATA_API_KEY`
- `FIREBASE_SERVICE_ACCOUNT_JSON`
- `GOOGLE_SHEET_ID=1unFY1i2X_mEYoxYKaP42hDPTl1lmsYhNdhj7Cg6W7xI`
- optional `GOOGLE_SERVICE_ACCOUNT_JSON`
- optional `ALERT_TOPIC=investment-alerts`
- `ADMIN_API_KEY=<langes-zufaelliges-passwort>`

Die Google-Tabelle muss dem verwendeten Google-Service-Account mindestens als Leser freigegeben werden. Der Server liest `Rangliste!A:S` und die Marktampel.

## D. GitHub mit Azure verbinden

Repository -> **Settings -> Secrets and variables -> Actions**:

**Variable**
- `AZURE_FUNCTIONAPP_NAME` = exakt der Azure Function-App-Name

**Secret**
- `AZURE_FUNCTIONAPP_PUBLISH_PROFILE` = kompletter Inhalt der aus Azure heruntergeladenen Publish-Profile-Datei

Den Publish Profile in Azure bei der Function App herunterladen und den XML-Inhalt vollständig als GitHub Secret einfügen.

Für Push später zusätzlich als GitHub Secrets:
- `FIREBASE_APP_ID`
- `FIREBASE_API_KEY`
- `FIREBASE_PROJECT_ID`
- `FIREBASE_SENDER_ID`

Eine separate `INVESTMENT_API_BASE_URL` ist normalerweise nicht mehr nötig: Der Android-Build leitet `https://<AZURE_FUNCTIONAPP_NAME>.azurewebsites.net` automatisch aus der Azure-Variable ab. Optional kann die Variable `INVESTMENT_API_BASE_URL` für einen eigenen Hostnamen gesetzt werden.

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

## HOTFIX 1.1.6 – wichtig für GitHub Actions

Falls du zuvor `Failed to find package 'platforms;android-37'` gesehen hast: behoben. Die App verwendet jetzt Android API 36.

Ohne echte Azure-Konfiguration wird der Backend-Deploy jetzt absichtlich rot und erklärt genau, welche Variable bzw. welches Secret fehlt. Grün bedeutet danach: Deployment plus `/api/health` waren erfolgreich.

Für den echten Azure-Deploy werden `AZURE_FUNCTIONAPP_NAME` und `AZURE_FUNCTIONAPP_PUBLISH_PROFILE` wie oben beschrieben benötigt.

Siehe auch `HOTFIX_1.1.6.md`.
