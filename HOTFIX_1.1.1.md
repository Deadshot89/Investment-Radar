# InvestmentRadar Live 1.1.1 – GitHub Actions Hotfix

## Behobene Fehler

### 1. Android-Build: `Failed to find package 'platforms;android-37'`
Das Projekt und der GitHub-Workflow verwenden jetzt Android API 36:
- `compileSdk = 36`
- `targetSdk = 36`
- GitHub installiert `platforms;android-36`
- Build Tools `36.0.0`

### 2. Azure-Deploy: `app-name should not be empty`
Der Workflow prüft jetzt vor dem Deploy, ob die Azure-Konfiguration vorhanden ist.

Solange Azure noch nicht eingerichtet ist:
- `npm install` läuft
- Syntaxprüfung läuft
- Azure-Deploy wird übersprungen
- der Workflow endet erfolgreich statt mit Fehler

Sobald Azure eingerichtet ist, müssen in GitHub exakt diese Werte vorhanden sein:

**Repository variable**
- `AZURE_FUNCTIONAPP_NAME` = exakter Name deiner Azure Function App

**Repository secret**
- `AZURE_FUNCTIONAPP_PUBLISH_PROFILE` = kompletter Inhalt der aus Azure heruntergeladenen Publish-Profile-Datei

Pfad in GitHub:
`Settings > Secrets and variables > Actions`

## Danach
1. Dateien aus dieser Version ins Repository hochladen/ersetzen.
2. `Build Android APK` erneut starten.
3. Der Android-Job muss jetzt über `Install Android SDK 36` hinauslaufen.
4. `Deploy Backend` darf ohne Azure-Konfiguration nicht mehr rot werden.
5. Sobald Azure konfiguriert ist, führt derselbe Workflow automatisch den echten Deploy aus.
