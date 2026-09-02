import test from "node:test";
import assert from "node:assert/strict";
import fs from "node:fs";

const source = fs.readFileSync(new URL("../src/functions/marketWatch.mjs", import.meta.url), "utf8");

test("market watch polls every five minutes", () => {
  assert.match(source, /schedule:\s*"0 \*\/5 \* \* \* \*"/);
});

test("market watch reuses cached slow analysis data", () => {
  assert.match(source, /buildAnalysisSnapshot\(\{\s*refreshAnalysis:\s*false\s*\}\)/);
});
