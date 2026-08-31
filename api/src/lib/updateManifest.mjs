export function buildUpdateManifest(env = process.env) {
  const versionCode = Number.parseInt(String(env.APP_UPDATE_VERSION_CODE ?? '').trim(), 10);
  const versionName = String(env.APP_UPDATE_VERSION_NAME ?? '').trim();
  const apkUrl = String(env.APP_UPDATE_APK_URL ?? '').trim();
  const notes = String(env.APP_UPDATE_NOTES ?? '').trim();

  if (!Number.isFinite(versionCode) || versionCode <= 0 || !versionName || !/^https:\/\//i.test(apkUrl)) {
    return null;
  }

  return { versionCode, versionName, apkUrl, notes };
}
