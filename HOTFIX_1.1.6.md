# Hotfix 1.1.6

## Ursache des App-Fehlers
Die APK wurde mit einer leeren `INVESTMENT_API_BASE_URL` gebaut. Der Workflow übergab ein leeres `-P`-Property und überschieb damit den Fallback. Daraus entstand `/api/dashboard` ohne Protokoll.

## Änderungen
- Leere Gradle Properties überschreiben Fallbacks nicht mehr.
- Die App lehnt fehlende/Platzhalter-Backend-URLs mit verständlicher Meldung ab.
- Android-Build leitet die API-URL aus `AZURE_FUNCTIONAPP_NAME` ab.
- Ohne echte HTTPS-Backend-URL wird keine APK gebaut.
- Backend-Deploy ist nur noch grün, wenn Azure tatsächlich konfiguriert und `/api/health` erreichbar ist.
