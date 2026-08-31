# Hotfix 1.1.8

Diagnose-Hotfix ausschließlich für `/api/health`.

Neu ausgegeben werden nur sichere Metadaten zur Firebase-Umgebungsvariable:

- `firebaseEnvPresent`: ob `FIREBASE_SERVICE_ACCOUNT_JSON` zur Laufzeit existiert
- `firebaseEnvLength`: Zeichenanzahl des Werts, niemals der Wert selbst
- `firebaseMatchingKeys`: Namen vorhandener Umgebungsvariablen, die mit `FIREBASE` beginnen

Der Secret-Inhalt, private Schlüssel und JSON-Felder werden niemals über `/api/health` ausgegeben.
Die Push-Logik, Android-App und Azure-Deploylogik wurden nicht verändert.
