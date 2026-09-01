# Hotfix 1.1.20

Diagnose- und Routing-Fix für den Push-Test:

- `/api/health` liefert `backendVersion: "1.1.20"`.
- Push-Test-Route vereinfacht auf `POST /api/test-push`.
- Route bleibt mit `x-admin-key` geschützt und liefert ohne korrekten Key HTTP 401.
- GitHub-Deploy prüft nach Veröffentlichung sowohl die exakte Backend-Version als auch HTTP 401 auf `/api/test-push`.
- Android-Version auf 1.1.20 / versionCode 21 erhöht.
