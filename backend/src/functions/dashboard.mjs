import { app } from "@azure/functions";
import { buildDashboard } from "../lib/dashboard.mjs";

app.http("dashboard", {
  methods: ["GET"], authLevel: "anonymous", route: "dashboard",
  handler: async (_request, context) => {
    try {
      return { status: 200, jsonBody: await buildDashboard(), headers: { "Cache-Control": "no-store", "Access-Control-Allow-Origin": "*" } };
    } catch (error) {
      context.error(error);
      return { status: 500, jsonBody: { error: error instanceof Error ? error.message : "Dashboard error" } };
    }
  }
});
