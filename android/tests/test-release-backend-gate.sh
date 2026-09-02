#!/usr/bin/env bash
# Final 1.2.0 branch verification trigger: keeps Android release gates in the exact-head test matrix.
set -euo pipefail

WF=".github/workflows/android-build.yml"

grep -q 'Verify live backend before Android publish' "$WF"
grep -q 'EXPECTED_BACKEND_VERSION: "1.2.0"' "$WF"
grep -Fq 'BASE_URL: ${{ vars.INVESTMENT_API_BASE_URL }}' "$WF"
grep -q "github.ref == 'refs/heads/main'" "$WF"
grep -q '/api/health' "$WF"
grep -q 'backendVersion' "$WF"
grep -q 'Publish APK for in-app updates' "$WF"

gate_line=$(grep -n 'Verify live backend before Android publish' "$WF" | head -1 | cut -d: -f1)
publish_line=$(grep -n 'Publish APK for in-app updates' "$WF" | head -1 | cut -d: -f1)
test -n "$gate_line"
test -n "$publish_line"
test "$gate_line" -lt "$publish_line"

echo "PASS Android publish is gated on live backend 1.2.0"
