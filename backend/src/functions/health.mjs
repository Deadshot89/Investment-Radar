import { app } from "@azure/functions";
import { getFirebaseEnvDiagnostics } from "../lib/envDiagnostics.mjs";

app.http("health", {
  methods: ["GET"],
  authLevel: "anonymous",
  route: "health",
  handler: async () => {
    const firebaseDiagnostics = getFirebaseEnvDiagnostics(process.env);
    return {
      status: 200,
      jsonBody: {
        ok: true,
        service: "investment-radar-live",
        backendVersion: "1.1.20",
        marketDataConfigured: Boolean(process.env.TWELVE_DATA_API_KEY),
        pushConfigured: Boolean(process.env.FIREBASE_SERVICE_ACCOUNT_JSON?.trim()),
        ...firebaseDiagnostics,
        time: new Date().toISOString()
      }
    };
  }
});
