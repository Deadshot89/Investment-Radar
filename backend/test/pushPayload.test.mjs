import test from "node:test";
import assert from "node:assert/strict";
import { buildPushMessage } from "../src/lib/push.mjs";

test("push is data-focused so Android can apply local policy", () => {
  const message = buildPushMessage({
    id: "1",
    itemId: "msft",
    level: "REVIEW",
    title: "Prüfen",
    message: "Grund",
    createdAt: "2026-09-02T08:00:00Z"
  });
  assert.equal(message.notification, undefined);
  assert.equal(message.data.level, "REVIEW");
  assert.equal(message.data.itemId, "msft");
  assert.equal(message.topic, "holding-msft");
});

test("BUY events use the global topic", () => {
  const message = buildPushMessage({ id: "2", itemId: "msft", level: "BUY", title: "Kauf", message: "Chance", createdAt: "x" });
  assert.equal(message.topic, "investment-alerts");
});
