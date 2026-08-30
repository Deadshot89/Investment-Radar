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

export async function sendAlert(alert) {
  if (!ensureFirebase()) return false;
  const topic = targetTopic(alert);
  await getMessaging().send({
    topic,
    notification: { title: alert.title, body: alert.message },
    data: {
      alertId: alert.id,
      itemId: alert.itemId,
      level: alert.level,
      title: alert.title,
      message: alert.message,
      createdAt: alert.createdAt
    },
    android: {
      priority: "high",
      notification: { channelId: "investment_alerts", priority: "high" }
    }
  });
  return true;
}
