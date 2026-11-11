#!/bin/sh
# Nightly backup of Postgres (#12, docs/operations.md): gardens, history, usage, proposals, everything people
# made. Neo4j needs none (rebuilt from the curated files), nor does the weather cache (fetched again).
# Keeps BACKUP_DAYS days in BACKUP_DIR and, when BACKUP_REMOTE is set, mirrors them off the server with rsync
# over SSH (for example a storage box: BACKUP_REMOTE=user@host:rootology, BACKUP_SSH="ssh -p 23").
# When BACKUP_PING_URL is set (a Healthchecks.io check, https://hc-ping.com/<uuid>), it reports the start, the
# end and any failure, so a backup that fails or never runs raises an alert; a failing ping never fails it.
set -eu
cd "$(dirname "$0")"

heartbeat() {
    if [ -n "${BACKUP_PING_URL:-}" ]; then
        curl -fsS -m 10 --retry 3 -o /dev/null "$BACKUP_PING_URL$1" || true
    fi
}
trap 'status=$?; [ "$status" -eq 0 ] || heartbeat /fail' EXIT
heartbeat /start

DIR=${BACKUP_DIR:-$HOME/backups}
DAYS=${BACKUP_DAYS:-14}
STAMP=$(date -u +%Y-%m-%dT%H%MZ)
mkdir -p "$DIR"
chmod 700 "$DIR"

docker compose -f compose.prod.yaml exec -T postgres pg_dump -U rootology -d rootology --format=custom \
    > "$DIR/rootology-$STAMP.dump.partial"
mv "$DIR/rootology-$STAMP.dump.partial" "$DIR/rootology-$STAMP.dump"
find "$DIR" -name 'rootology-*.dump' -mtime +"$DAYS" -delete

if [ -n "${BACKUP_REMOTE:-}" ]; then
    rsync -a --delete -e "${BACKUP_SSH:-ssh}" "$DIR/" "$BACKUP_REMOTE/"
fi
echo "Backup rootology-$STAMP.dump done"
heartbeat ""
