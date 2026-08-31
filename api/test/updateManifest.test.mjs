import test from 'node:test';
import assert from 'node:assert/strict';
import { buildUpdateManifest } from '../src/lib/updateManifest.mjs';

test('builds public app update manifest from environment values', () => {
  const result = buildUpdateManifest({
    APP_UPDATE_VERSION_CODE: '13',
    APP_UPDATE_VERSION_NAME: '1.2.2',
    APP_UPDATE_APK_URL: 'https://example.test/app-release.apk',
    APP_UPDATE_NOTES: 'Update-Funktion'
  });
  assert.deepEqual(result, {
    versionCode: 13,
    versionName: '1.2.2',
    apkUrl: 'https://example.test/app-release.apk',
    notes: 'Update-Funktion'
  });
});
