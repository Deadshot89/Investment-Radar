# Einrichtung

## 1. GitHub + Azure Flex Consumption

Repository Settings -> Secrets and variables -> Actions:

**Variables**
- `AZURE_FUNCTIONAPP_NAME` = exakter Azure Ressourcenname der Function App, z. B. `InvestmentRadar`
- `INVESTMENT_API_BASE_URL` = vollständige **Standarddomäne** aus Azure inklusive `https://`, z. B. `https://investmentradar-....germanywestcentral-01.azurewebsites.net`

**Secret**
- `AZURE_FUNCTIONAPP_PUBLISH_PROFILE` = kompletter Inhalt des heruntergeladenen Azure Publish Profiles

Wichtig: Bei Flex Consumption darf die API-URL **nicht** als `https://<APP_NAME>.azurewebsites.net` geraten werden. Verwende immer die in Azure angezeigte Standarddomäne.

Der Backend-Workflow verwendet für Flex Consumption `sku: flexconsumption` und `remote-build: true`.

## 2. Marktdaten

Ein Twelve-Data-Konto anlegen und einen API-Key erzeugen. Der Key wird als Azure-App-Setting gespeichert:
- `TWELVE_DATA_API_KEY`

## 3. Firebase Push

GitHub Secrets für den Android-Build:
- `FIREBASE_APP_ID`
- `FIREBASE_API_KEY`
- `FIREBASE_PROJECT_ID`
- `FIREBASE_SENDER_ID`

Azure App Setting für serverseitige Pushs:
- `FIREBASE_SERVICE_ACCOUNT_JSON`

## 4. Azure App Settings

Zusätzlich später setzen:
- `TWELVE_DATA_API_KEY`
- `FIREBASE_SERVICE_ACCOUNT_JSON`
- `AzureWebJobsStorage`
- optional `ALERT_TOPIC=investment-alerts`
- optional `ADMIN_API_KEY=<langes-zufaelliges-passwort>`
- optional `GOOGLE_SHEET_ID=1unFY1i2X_mEYoxYKaP42hDPTl1lmsYhNdhj7Cg6W7xI`
- optional `GOOGLE_SERVICE_ACCOUNT_JSON`

## 5. Reihenfolge

1. GitHub Variablen/Secret setzen.
2. `Deploy Backend` starten.
3. Prüfen, dass `${INVESTMENT_API_BASE_URL}/api/health` erreichbar ist.
4. `Build Android APK` starten.
5. Artefakt `investment-radar-apk` installieren.

Die App führt keine Orders aus.
