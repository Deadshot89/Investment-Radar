import test from "node:test";
import assert from "node:assert/strict";
import fs from "node:fs";
import os from "node:os";
import path from "node:path";
import { API_SCHEMA_VERSION, loadBuildInfo } from "../src/lib/buildInfo.mjs";

test("build info exposes immutable revision deploy id and schema", () => {
  const dir = fs.mkdtempSync(path.join(os.tmpdir(), "investment-radar-build-"));
  const file = path.join(dir, "build-info.json");
  fs.writeFileSync(file, JSON.stringify({
    sourceRevision: "abc123",
    deployId: "abc123-99-1"
  }));

  assert.deepEqual(loadBuildInfo(file), {
    sourceRevision: "abc123",
    deployId: "abc123-99-1",
    apiSchemaVersion: API_SCHEMA_VERSION
  });
});

test("missing build info remains explicit in local development", () => {
  const missing = path.join(os.tmpdir(), "investment-radar-missing-build-info.json");
  assert.deepEqual(loadBuildInfo(missing), {
    sourceRevision: "development",
    deployId: "development",
    apiSchemaVersion: API_SCHEMA_VERSION
  });
});
