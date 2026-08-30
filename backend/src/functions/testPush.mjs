import { app } from "@azure/functions";
import { sendAlert } from "../lib/push.mjs";

app.http("testPush", {
  methods: ["POST"], authLevel: "anonymous", route: "admin/test-push",
  handler: async (request) => {
    const configuredKey = process.env.ADMIN_API_KEY?.trim();
    const supplied = request.headers.get("x-admin-key")?.trim();
    if (!configuredKey || supplied !== configuredKey) return { status: 401, jsonBody: { error: "unauthorized" } };

    const body = await request.json().catch(() => ({}));
    const now = new Date().toISOString();
    const stamp = Date.now();
    const level = String(body?.level ?? "INFO").toUpperCase();
    const itemId = String(body?.itemId ?? "test");
    const alert = {
      id: `test-${stamp}`,
      itemId,
      level,
      title: level === "REVIEW" ? "Investment Radar: Portfolio-Test" : "Investment Radar: Test",
      message: level === "REVIEW" ? "Gezielter Portfolio-Push funktioniert." : "Push-Benachrichtigungen funktionieren.",
      createdAt: now,
      fingerprint: `test-${stamp}`
    };
    const sent = await sendAlert(alert);
    return { status: sent ? 200 : 503, jsonBody: { sent, level, itemId } };
  }
});
