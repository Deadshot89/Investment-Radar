#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
fail=0
need_file() {
  local f="$1"
  if [ ! -f "$ROOT/$f" ]; then echo "MISSING: $f"; fail=1; fi
}
need_text() {
  local f="$1" p="$2"
  if [ ! -f "$ROOT/$f" ] || ! grep -Fq "$p" "$ROOT/$f"; then echo "MISSING TEXT in $f: $p"; fail=1; fi
}
need_file "backend/src/index.mjs"
need_file "backend/src/functions/testPush.mjs"
need_file "backend/src/lib/push.mjs"
need_file "backend/package.json"
need_file ".github/workflows/backend-deploy.yml"
need_text "backend/src/index.mjs" 'import "./functions/testPush.mjs";'
need_text "backend/src/functions/testPush.mjs" 'route: "admin/test-push"'
need_text "backend/src/functions/testPush.mjs" 'status: 401'
need_text ".github/workflows/backend-deploy.yml" '/api/admin/test-push'
need_text ".github/workflows/backend-deploy.yml" '401'
need_text ".github/workflows/backend-deploy.yml" 'POST'
if [ "$fail" -ne 0 ]; then exit 1; fi
echo "push route/deploy guard OK"
