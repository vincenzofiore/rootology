#!/usr/bin/env bash
# The local Postgres of this checkout (#127): save it, put a saved copy back, or start again from an empty
# database. Dumps live outside the repository, in ~/rootology-dumps. Only local data: never the server's.
#
#   ./local-db.sh dump [name]       save the database (default name: the date and time)
#   ./local-db.sh list              show the saved dumps
#   ./local-db.sh restore <name>    replace the database with a dump (asks first; --yes skips the question)
#   ./local-db.sh reset             delete the database volume; the next run starts empty (asks first; --yes)
set -euo pipefail
cd "$(dirname "$0")"
repo="$(pwd)"
dumps="$HOME/rootology-dumps"
db=(-U rootology)

usage() { sed -n '5,8p' "$0" | sed 's/^# //' >&2; exit 1; }

confirm() {
  [ "${yes:-}" = 1 ] && return
  read -r -p "$1 [y/N] " answer
  [ "$answer" = y ] || [ "$answer" = Y ] || { echo "Nothing changed."; exit 1; }
}

# The app keeps connections open and Flyway checks the schema at startup: change the database only while it is stopped.
app_stopped() {
  local pid
  pid="$(lsof -ti tcp:8080 -sTCP:LISTEN 2>/dev/null | head -1 || true)"
  if [ -n "$pid" ] && ps -o command= -p "$pid" | grep -qF "$repo/target/classes"; then
    echo "Rootology is running (pid $pid): stop it first." >&2
    exit 1
  fi
}

postgres_up() {
  docker compose up -d --wait postgres >/dev/null 2>&1 || { echo "Could not start Postgres (docker compose up)." >&2; exit 1; }
  # Running is not ready: the first start of an empty volume initialises the database before it accepts connections.
  for _ in $(seq 30); do
    docker compose exec -T postgres pg_isready "${db[@]}" -d rootology >/dev/null 2>&1 && return
    sleep 1
  done
  echo "Postgres did not become ready." >&2; exit 1
}

command="${1:-}"; shift || true
yes=0
args=()
for arg in "$@"; do
  if [ "$arg" = --yes ]; then yes=1; else args+=("$arg"); fi
done

case "$command" in
  dump)
    name="${args[0]:-$(date +%Y%m%d-%H%M)}"
    [[ "$name" =~ ^[A-Za-z0-9._-]+$ ]] || { echo "Use letters, digits, dot, dash or underscore in the name." >&2; exit 1; }
    mkdir -p "$dumps"
    file="$dumps/$name.dump"
    [ -e "$file" ] && { echo "$file exists already." >&2; exit 1; }
    postgres_up
    trap 'rm -f "$file.partial"' EXIT
    docker compose exec -T postgres pg_dump "${db[@]}" -Fc rootology > "$file.partial"
    mv "$file.partial" "$file"
    echo "Saved $file ($(du -h "$file" | cut -f1))."
    ;;
  list)
    ls -lh "$dumps"/*.dump 2>/dev/null | awk '{print $5, $6, $7, $8, $9}' || echo "No dumps in $dumps."
    ;;
  restore)
    name="${args[0]:-}"
    [ -n "$name" ] || usage
    file="$dumps/${name%.dump}.dump"
    [ -f "$file" ] || { echo "No dump $file." >&2; exit 1; }
    app_stopped
    confirm "Replace the local database with $(basename "$file")? Its current data is lost."
    postgres_up
    docker compose exec -T postgres dropdb "${db[@]}" --force --if-exists rootology
    docker compose exec -T postgres createdb "${db[@]}" rootology
    docker compose exec -T postgres pg_restore "${db[@]}" -d rootology --no-owner < "$file"
    echo "Restored $(basename "$file"). Flyway applies any newer migration on the next ./run-local.sh."
    ;;
  reset)
    app_stopped
    confirm "Delete the local database volume? Its data, the admin grant included, is lost."
    # Stops this checkout's services and removes only the volumes compose.yaml declares (the Postgres one).
    docker compose down -v
    echo "Done. The next ./run-local.sh starts from an empty database; sign in, then grant ADMIN again (docs/operations.md, "First admin")."
    ;;
  *)
    usage
    ;;
esac
