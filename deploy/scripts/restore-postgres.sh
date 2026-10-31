#!/usr/bin/env bash
# Restore a dump made by backup-postgres.sh.
#
# Usage:
#   restore-postgres.sh [--yes] <dump file>
#       Replace the main database (POSTGRES_DB). Stops api and worker during
#       the restore and starts them again afterwards. Asks for confirmation
#       unless --yes.
#   restore-postgres.sh [--yes] --target-db <name> <dump file>
#       Restore into another database (dropped and re-created first), e.g. a
#       weekly restore test: --target-db restore_check. The live database and
#       services are not touched; drop it afterwards with
#       compose.sh exec postgres dropdb -U <user> restore_check
#
# The dump is streamed into the container over stdin; nothing is copied into
# the postgres volume. A dump fetched from R2 works the same way.
set -euo pipefail

SCRIPT_DIR="$(dirname "$(readlink -f "$0")")"
# shellcheck source=deploy/scripts/lib.sh
source "$SCRIPT_DIR/lib.sh"

usage() {
  sed -n '2,17p' "$0" | sed 's/^# \{0,1\}//'
  exit "${1:-0}"
}

YES=0
TARGET=""
FILE=""
while [[ $# -gt 0 ]]; do
  case "$1" in
    --yes) YES=1 ;;
    --target-db)
      [[ $# -ge 2 ]] || usage 2
      TARGET="$2"
      shift
      ;;
    -h | --help) usage 0 ;;
    -*) echo "unknown option: $1" >&2; usage 2 ;;
    *) FILE="$1" ;;
  esac
  shift
done
[[ -n "$FILE" ]] || usage 2
[[ -r "$FILE" ]] || die "cannot read $FILE"

export COMPOSE_PROFILES=backend
MAIN_DB="$(env_get "$TSP_ETC_DIR/backend.env" POSTGRES_DB)"
MAIN_DB="${MAIN_DB:-tspodcast}"
TARGET="${TARGET:-$MAIN_DB}"
[[ "$TARGET" =~ ^[a-z_][a-z0-9_]{0,62}$ ]] || die "invalid database name: $TARGET"

take_lock restore

[[ -n "$(compose ps -q postgres 2>/dev/null || true)" ]] || die "postgres is not running (compose.sh --profile backend up -d postgres)"

log "checking $FILE"
compose exec -T postgres pg_restore --list <"$FILE" >/dev/null || die "$FILE is not a readable pg_dump archive"

if [[ "$YES" != "1" ]]; then
  if [[ "$TARGET" == "$MAIN_DB" ]]; then
    echo "This REPLACES the live database '$TARGET' with $(basename "$FILE")." >&2
  else
    echo "This drops and re-creates database '$TARGET', then restores $(basename "$FILE") into it." >&2
  fi
  read -r -p "Type the database name to continue: " answer
  [[ "$answer" == "$TARGET" ]] || die "aborted"
fi

psql_admin() {
  # shellcheck disable=SC2016 # expanded inside the container
  compose exec -T postgres sh -c 'exec psql -v ON_ERROR_STOP=1 -U "$POSTGRES_USER" -d postgres -c "$1"' sh "$1"
}

STOPPED=0
restart_services() {
  if [[ "$STOPPED" == "1" ]]; then
    log "starting api and worker again"
    compose start api worker || warn "could not start api/worker: compose.sh --profile backend up -d"
  fi
}
trap restart_services EXIT

if [[ "$TARGET" == "$MAIN_DB" ]]; then
  log "stopping api and worker"
  compose stop api worker
  STOPPED=1
  # --clean drops each object before re-creating it, in one transaction:
  # on error nothing changes.
  # shellcheck disable=SC2016 # expanded inside the container
  compose exec -T postgres sh -c 'exec pg_restore -U "$POSTGRES_USER" -d "$1" --clean --if-exists --no-owner --single-transaction --exit-on-error' sh "$TARGET" <"$FILE"
else
  psql_admin "DROP DATABASE IF EXISTS \"$TARGET\" WITH (FORCE)"
  psql_admin "CREATE DATABASE \"$TARGET\""
  # shellcheck disable=SC2016 # expanded inside the container
  compose exec -T postgres sh -c 'exec pg_restore -U "$POSTGRES_USER" -d "$1" --no-owner --single-transaction --exit-on-error' sh "$TARGET" <"$FILE"
fi

log "restore of $(basename "$FILE") into '$TARGET' finished"
if [[ "$TARGET" != "$MAIN_DB" ]]; then
  # shellcheck disable=SC2016 # expanded inside the container
  compose exec -T postgres sh -c 'exec psql -U "$POSTGRES_USER" -d "$1" -Atc "$2"' sh "$TARGET" \
    "select count(*) || ' tables in schema public' from information_schema.tables where table_schema = 'public'"
fi
