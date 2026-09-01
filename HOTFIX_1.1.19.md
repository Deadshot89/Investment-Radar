# Hotfix 1.1.19 – Push-Test-Route wieder verbindlich deployen

## Ursache
Der Live-Test `POST /api/admin/test-push` lieferte HTTP 404. Damit war nicht Firebase selbst der erste Fehler, sondern die Push-Test-Route war im laufenden Azure-Backend nicht registriert.

## Änderung
- `backend/src/index.mjs` liegt wieder im Update und importiert `testPush.mjs` ausdrücklich.
- `backend/src/functions/testPush.mjs` liegt wieder im Update.
- `backend/src/lib/push.mjs` liegt wieder im Update.
- `backend/package.json` setzt `main` verbindlich auf `src/index.mjs` und prüft die Push-Dateien beim Syntax-Check.
- `backend-deploy.yml` prüft nach jedem Deploy nicht nur `/api/health`, sondern auch `POST /api/admin/test-push`.
- Ohne `x-admin-key` muss die Route HTTP 401 liefern. 404 lässt den Deploy ab jetzt fehlschlagen.

## Sicherheit
Der Deploy-Test sendet absichtlich keinen Admin-Key. Es wird kein Secret im Workflow ausgegeben.

## Version
- Android `versionCode`: 20
- Android `versionName`: 1.1.19
