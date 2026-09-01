#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
WF="$ROOT/.github/workflows/backend-deploy.yml"

grep -q 'Create immutable deploy package' "$WF" || { echo 'FAIL: immutable deploy package step missing'; exit 1; }
grep -q 'sha256sum backend-deploy.zip' "$WF" || { echo 'FAIL: deploy SHA missing'; exit 1; }
grep -q 'backendVersion' "$WF" || { echo 'FAIL: backendVersion evidence missing'; exit 1; }
grep -q 'DEPLOY_ID' "$WF" || { echo 'FAIL: deploy id missing'; exit 1; }
grep -q 'package: backend-deploy.zip' "$WF" || { echo 'FAIL: deployment does not use immutable zip'; exit 1; }
grep -q 'for attempt in $(seq 1 24)' "$WF" || { echo 'FAIL: extended activation wait missing'; exit 1; }
grep -q 'sleep 15' "$WF" || { echo 'FAIL: activation polling interval missing'; exit 1; }
grep -q 'Azure hat das Deployment akzeptiert, liefert aber weiterhin' "$WF" || { echo 'FAIL: stale-host diagnosis missing'; exit 1; }

echo 'PASS: deploy diagnostics contract present'
