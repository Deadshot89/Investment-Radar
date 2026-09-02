import { cert, getApps, initializeApp } from "firebase-admin/app";
import { getMessaging } from "firebase-admin/messaging";

function ensureFirebase() {
  if (getApps().length > 0) return true;
  const raw = process.env.FIREBASE_SERVICE_ACCOUNT_JSON?.trim();
  if (!raw) return false;
  try {
    initializeApp({ credential: cert(JSON.parse(raw)) });
    return true;
  } catch (error) {
    console.error("Firebase init failed", error);
    return false;
  }
}

function safeTopicPart(value) {
  return String(value ?? "")
    .toLowerCase()
    .replace(/[^a-z0-9._~%-]+/g, "-")
    .slice(0, 80);
}

function targetTopic(alert) {
  if (["SELL", "REVIEW"].includes(String(alert.level).toUpperCase()) && alert.itemId) {
    return `holding-${safeTopicPart(alert.itemId)}`;
  }
  return process.env.ALERT_TOPIC?.trim() || "investment-alerts";
}

export function buildPushMessage(alert) {
  return {
    topic: targetTopic(alert),
    data: {
      alertId: String(alert.id ?? ""),
      itemId: String(alert.itemId ?? ""),
      level: String(alert.level ?? "INFO"),
      title: String(alert.title ?? "Investment Radar"),
      message: String(alert.message ?? ""),
      createdAt: String(alert.createdAt ?? new Date().toISOString())
    },
    android: { priority: "high" }
  };
}

export async function sendAlert(alert) {
  if (!ensureFirebase()) return false;
  await getMessaging().send(buildPushMessage(alert));
  return true;
}
