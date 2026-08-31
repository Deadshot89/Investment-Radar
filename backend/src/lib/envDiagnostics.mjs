export function getFirebaseEnvDiagnostics(env = process.env) {
  const key = "FIREBASE_SERVICE_ACCOUNT_JSON";
  const raw = env[key];
  const firebaseMatchingKeys = Object.keys(env)
    .filter((name) => name.toUpperCase().startsWith("FIREBASE"))
    .sort();

  return {
    firebaseEnvPresent: Object.prototype.hasOwnProperty.call(env, key),
    firebaseEnvLength: typeof raw === "string" ? raw.length : 0,
    firebaseMatchingKeys
  };
}
