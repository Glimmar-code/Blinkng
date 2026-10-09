#!/usr/bin/env bash
# Test production Firebase config restoration with synthetic data, never real keys.
set -euo pipefail

root="$(cd "$(dirname "$0")/../.." && pwd)"
test_root="$(mktemp -d)"
trap 'rm -rf "$test_root"' EXIT
mkdir -p "$test_root/.github/scripts" "$test_root/app"
cp "$root/.github/scripts/restore_google_services.sh" "$test_root/.github/scripts/"
cp "$root/app/google-services.json.example" "$test_root/app/google-services.json.example"

# Deliberately fake values: they validate the mechanism, not a production project.
cat > "$test_root/test-client.json" <<'JSON'
{
  "project_info": {"project_id": "ci-only-restoration-test"},
  "client": [{
    "client_info": {"android_client_info": {"package_name": "com.aistudio.blink.appvtwo"}},
    "api_key": [{"current_key": "CI_FAKE_NOT_FOR_RELEASE"}]
  }]
}
JSON

cd "$test_root"
env -u GOOGLE_SERVICES_JSON_BASE64 \
  GOOGLE_SERVICES_JSON="$(cat test-client.json)" \
  bash .github/scripts/restore_google_services.sh production
python3 - <<'PY'
import json
from pathlib import Path

expected = json.loads(Path("test-client.json").read_text())
actual = json.loads(Path("app/google-services.json").read_text())
assert actual == expected, "Restored Firebase config differs from synthetic fixture"
PY

env -u GOOGLE_SERVICES_JSON \
  GOOGLE_SERVICES_JSON_BASE64="$(base64 < test-client.json | tr -d '\n')" \
  bash .github/scripts/restore_google_services.sh production
python3 - <<'PY'
import json
from pathlib import Path

expected = json.loads(Path("test-client.json").read_text())
actual = json.loads(Path("app/google-services.json").read_text())
assert actual == expected, "Restored Firebase config differs from synthetic fixture"
PY

rm app/google-services.json
if env -u GOOGLE_SERVICES_JSON -u GOOGLE_SERVICES_JSON_BASE64 \
  bash .github/scripts/restore_google_services.sh production > "$test_root/missing.log" 2>&1; then
  echo "ERROR: Production restoration accepted missing Firebase config." >&2
  exit 1
fi

echo "Firebase release secret restoration: raw, base64 and missing-secret tests passed."
