#!/usr/bin/env bash
set -euo pipefail

mode="${1:-ci}"
dest="app/google-services.json"
example="${dest}.example"

if [[ -n "${GOOGLE_SERVICES_JSON_BASE64:-}" ]]; then
  printf '%s' "$GOOGLE_SERVICES_JSON_BASE64" | base64 --decode > "$dest"
  source_label="protected secret"
elif [[ "$mode" == "production" ]]; then
  echo "::error::GOOGLE_SERVICES_JSON_BASE64 is not configured. Refusing to build a production Firebase client."
  exit 1
else
  cp "$example" "$dest"
  source_label="safe placeholder"
fi

python3 - "$dest" "$mode" <<'PY'
import json
import sys
from pathlib import Path

path = Path(sys.argv[1])
mode = sys.argv[2]
expected_package = "com.aistudio.blink.appvtwo"

try:
    data = json.loads(path.read_text())
except Exception as exc:
    raise SystemExit(f"Invalid Firebase client JSON: {exc}")

clients = data.get("client") or []
packages = {
    (client.get("client_info") or {}).get("android_client_info", {}).get("package_name")
    for client in clients
}
if expected_package not in packages:
    raise SystemExit(f"Firebase client config has no Android client for {expected_package}")

keys = [
    item.get("current_key", "")
    for client in clients
    for item in (client.get("api_key") or [])
]
if mode == "production":
    if not any(key and not key.startswith("YOUR_") for key in keys):
        raise SystemExit("Production Firebase client config does not contain a real API key")
    project_id = (data.get("project_info") or {}).get("project_id", "")
    if not project_id or project_id.startswith("YOUR_"):
        raise SystemExit("Production Firebase client config does not contain a real project_id")

print(f"Firebase client config validated for {expected_package} ({mode}).")
PY

chmod 600 "$dest"
echo "Restored app/google-services.json from $source_label."
