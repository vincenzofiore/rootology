#!/usr/bin/env bash
# Starts Rootology locally (#127): email always goes to Mailpit, and an older local run of this repository is
# stopped first. Any other program on port 8080 is left alone. Prints no setting or secret.
set -euo pipefail
cd "$(dirname "$0")"
repo="$(pwd)"

# Without the SMTP settings Spring Boot uses Mailpit from compose.yaml, so no email reaches a real person.
for name in $(compgen -e | grep '^ROOTOLOGY_SMTP_' || true); do unset "$name"; done

pid="$(lsof -ti tcp:8080 -sTCP:LISTEN 2>/dev/null | head -1 || true)"
if [ -n "$pid" ]; then
  if ps -o command= -p "$pid" | grep -qF "$repo/target/classes"; then
    echo "Stopping the running Rootology (pid $pid)..."
    kill "$pid"
    while lsof -ti tcp:8080 -sTCP:LISTEN >/dev/null 2>&1; do sleep 1; done
  else
    echo "Port 8080 is used by another program; stop it first." >&2
    exit 1
  fi
fi

exec ./mvnw spring-boot:run "$@"
