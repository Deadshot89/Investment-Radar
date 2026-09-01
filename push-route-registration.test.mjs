import test from "node:test";
import assert from "node:assert/strict";
import fs from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), "..");

function read(relative) {
  return fs.readFileSync(path.join(root, relative), "utf8");
}

test("backend entrypoint registers protected push test route", () => {
  const index = read("backend/src/index.mjs");
  const route = read("backend/src/functions/testPush.mjs");
  assert.match(index, /import\s+["']\.\/functions\/testPush\.mjs["'];?/);
  assert.match(route, /route:\s*["']test-push["']/);
  assert.match(route, /methods:\s*\[\s*["']POST["']\s*\]/);
  assert.match(route, /status:\s*401/);
  assert.match(route, /x-admin-key/);
});

test("deploy workflow rejects missing push route", () => {
  const workflow = read(".github/workflows/backend-deploy.yml");
  assert.match(workflow, /api\/test-push/);
  assert.match(workflow, /-X POST/);
  assert.match(workflow, /STATUS.*401|401.*STATUS/s);
});
