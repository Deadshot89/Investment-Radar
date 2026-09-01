import test from "node:test";
import assert from "node:assert/strict";
import fs from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), "..");
const read = (relative) => fs.readFileSync(path.join(root, relative), "utf8");

test("health exposes backend version 1.1.25", () => {
  const health = read("backend/src/functions/health.mjs");
  assert.match(health, /backendVersion:\s*["']1\.1\.25["']/);
});

test("push diagnostic route is the simple /api/test-push route", () => {
  const route = read("backend/src/functions/testPush.mjs");
  assert.match(route, /route:\s*["']test-push["']/);
  assert.doesNotMatch(route, /route:\s*["']admin\/test-push["']/);
  assert.match(route, /status:\s*401/);
});

test("deploy verifies both backend version and the simple push route", () => {
  const workflow = read(".github/workflows/backend-deploy.yml");
  assert.match(workflow, /EXPECTED_BACKEND_VERSION:\s*["']?1\.1\.25["']?/);
  assert.match(workflow, /api\/test-push/);
  assert.doesNotMatch(workflow, /api\/admin\/test-push/);
  assert.match(workflow, /backendVersion/);
  assert.match(workflow, /401/);
});


test("custom quote route is registered", () => {
  const index = read("backend/src/index.mjs");
  const route = read("backend/src/functions/customQuote.mjs");
  const workflow = read(".github/workflows/backend-deploy.yml");
  assert.match(index, /customQuote\.mjs/);
  assert.match(route, /route:\s*["']custom-quote["']/);
  assert.match(workflow, /api\/custom-quote/);
  assert.match(workflow, /CUSTOM_STATUS/);
  const [beforeVerify, verifyBlock] = workflow.split("- name: Verify live backend");
  assert.doesNotMatch(beforeVerify, /CUSTOM_URL|CUSTOM_STATUS/);
  assert.match(verifyBlock, /CUSTOM_URL/);
});
