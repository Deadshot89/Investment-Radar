import { app } from "@azure/functions";
app.http("health", {
  methods: ["GET"], authLevel: "anonymous", route: "health",
  handler: async () => ({ status: 200, jsonBody: { ok: true, service: "investment-radar-live", marketDataConfigured: Boolean(process.env.TWELVE_DATA_API_KEY), pushConfigured: Boolean(process.env.FIREBASE_SERVICE_ACCOUNT_JSON), time: new Date().toISOString() } })
});
