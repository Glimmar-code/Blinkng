#!/bin/sh
# Container-only readiness. No network listener, credentials or user data.
set -eu
health_file="${BLINK_LIFE_HEALTH_FILE:-/var/lib/blinklife/server.health}"
[ -f "$health_file" ] || exit 1
[ "$(cat "$health_file")" = "ready" ] || exit 1
now="$(date +%s)"
last="$(stat -c %Y "$health_file")"
age="$((now - last))"
[ "$age" -ge 0 ] && [ "$age" -le 14 ]
