import { app } from "@azure/functions";
import { sendAlert } from "../lib/push.mjs";

app.http("testPush", {
  methods: ["POST"], authLevel: "anonymous", route: "admin/test-push",
  handler: async (request) => {
    const configuredKey = process.env.ADMIN_API_KEY?.trim();
    const supplied = request.headers.get("x-admin-key")?.trim();
    if (!configuredKey || supplied !== configuredKey) return { status: 401, jsonBody: { error: "unauthorized" } };
    const now = new Date().toISOString();
    const stamp = Date.now();
    const alert = { id: `test-${stamp}`, itemId: "test", level: "INFO", title: "Investment Radar: Test", message: "Push-Benachrichtigungen funktionieren.", createdAt: now, fingerprint: `test-${stamp}` };
    const sent = await sendAlert(alert);
    return { status: sent ? 200 : 503, jsonBody: { sent } };
  }
});
