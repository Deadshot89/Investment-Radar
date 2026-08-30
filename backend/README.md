# Backend

Azure Functions v4 / Node 22.

Endpoints:
- `GET /api/health`
- `GET /api/dashboard`
- `POST /api/admin/test-push` mit Header `x-admin-key`

Timer:
- `marketWatch`: alle 15 Minuten

Das Backend verwaltet nur Signale und Live-Daten; es fuehrt keine Brokerage-Orders aus.
