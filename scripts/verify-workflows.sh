#!/usr/bin/env bash
set -euo pipefail
backend=.github/workflows/backend-deploy.yml
android=.github/workflows/android-build.yml

grep -q 'sku: flexconsumption' "$backend" || { echo 'FAIL: backend workflow missing sku flexconsumption'; exit 1; }
grep -q 'remote-build: true' "$backend" || { echo 'FAIL: backend workflow missing remote-build true'; exit 1; }
grep -q 'BASE_URL: \${{ vars.INVESTMENT_API_BASE_URL }}' "$backend" || { echo 'FAIL: backend health check not using INVESTMENT_API_BASE_URL'; exit 1; }
if grep -q 'https://\${APP_NAME}\.azurewebsites\.net' "$backend"; then
  echo 'FAIL: backend still infers legacy azurewebsites URL'; exit 1
fi

grep -q 'API_URL: \${{ vars.INVESTMENT_API_BASE_URL }}' "$android" || { echo 'FAIL: Android workflow does not require INVESTMENT_API_BASE_URL'; exit 1; }
if grep -q 'AZURE_APP_NAME:' "$android"; then
  echo 'FAIL: Android workflow still falls back to app-name based URL'; exit 1
fi

echo 'PASS: Flex Consumption workflow invariants satisfied'
