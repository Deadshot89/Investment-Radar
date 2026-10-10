#!/usr/bin/env bash
set -euo pipefail

base_ref="${1:?base ref required}"
head_ref="${2:-HEAD}"

# Keep an existing Android release immutable with respect to shippable app code,
# while allowing unit/instrumented regression tests to evolve without forcing a
# fake application version bump.
if git diff --quiet "$base_ref" "$head_ref" -- \
  android/app \
  ':(exclude)android/app/src/test/**' \
  ':(exclude)android/app/src/androidTest/**'; then
  exit 0
fi

exit 1
