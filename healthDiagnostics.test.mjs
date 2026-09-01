import test from "node:test";
import assert from "node:assert/strict";
import { getFirebaseEnvDiagnostics } from "../src/lib/envDiagnostics.mjs";

test("reports Firebase environment presence without exposing secret values", () => {
  const env = {
    FIREBASE_SERVICE_ACCOUNT_JSON: "  {\"private_key\":\"secret\"}  ",
    FIREBASE_PROJECT_ID: "investmentradar-37161",
    OTHER_SECRET: "must-not-appear"
  };

  const result = getFirebaseEnvDiagnostics(env);

  assert.equal(result.firebaseEnvPresent, true);
  assert.equal(result.firebaseEnvLength, env.FIREBASE_SERVICE_ACCOUNT_JSON.length);
  assert.deepEqual(result.firebaseMatchingKeys, [
    "FIREBASE_PROJECT_ID",
    "FIREBASE_SERVICE_ACCOUNT_JSON"
  ]);
  assert.equal(JSON.stringify(result).includes("private_key"), false);
  assert.equal(JSON.stringify(result).includes("secret"), false);
});

test("reports an absent Firebase service account as missing with zero length", () => {
  const result = getFirebaseEnvDiagnostics({ FIREBASE_PROJECT_ID: "investmentradar-37161" });

  assert.equal(result.firebaseEnvPresent, false);
  assert.equal(result.firebaseEnvLength, 0);
  assert.deepEqual(result.firebaseMatchingKeys, ["FIREBASE_PROJECT_ID"]);
});
